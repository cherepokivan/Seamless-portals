package com.seamlessportals.client.network;

import com.seamlessportals.client.portal.PortalData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side protocol endpoint for Paper-provided destination terrain.
 *
 * <p>All data is bounded before allocation. The client only asks the server to
 * describe a portal it has already detected locally; the Paper plugin performs
 * the authoritative distance, world and destination validation.</p>
 */
public final class PortalWorldSync {
    public static final short PROTOCOL_VERSION = 1;
    public static final int HELLO = 0x01;
    public static final int SNAPSHOT_REQUEST = 0x02;
    public static final int SNAPSHOT_PART = 0x10;
    public static final int STATUS = 0x11;
    private static final long REQUEST_INTERVAL_MILLIS = 750L;
    private static final int MAX_PARTS = 32;
    private static final int MAX_VOXELS_PER_PART = 1_000;

    private static final Map<BlockPos, RemotePortalSnapshot> SNAPSHOTS = new HashMap<>();
    private static final Map<BlockPos, SnapshotAssembly> ASSEMBLIES = new HashMap<>();
    private static final Map<BlockPos, Long> LAST_REQUESTS = new HashMap<>();
    private static final long HELLO_INTERVAL_MILLIS = 2_000L;
    private static boolean initialized;
    private static long lastHelloAt;
    private static volatile String diagnostic = "Waiting for Paper channel";

    private PortalWorldSync() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        PortalSyncPayload.register();
        ClientPlayNetworking.registerGlobalReceiver(PortalSyncPayload.TYPE, (payload, context) ->
            accept(payload.data())
        );
    }

    public static void sendHello() {
        send(out -> {
            out.writeByte(HELLO);
            out.writeShort(PROTOCOL_VERSION);
        });
    }

    public static void tick(List<PortalData> portals) {
        long now = System.currentTimeMillis();
        SNAPSHOTS.entrySet().removeIf(entry -> !entry.getValue().isFresh(now));
        LAST_REQUESTS.entrySet().removeIf(entry -> now - entry.getValue() > 30_000L);

        if (!isConnected()) {
            diagnostic = "Waiting for Paper connection";
            return;
        }
        if (now - lastHelloAt >= HELLO_INTERVAL_MILLIS) {
            lastHelloAt = now;
            sendHello();
        }
        diagnostic = portals.isEmpty() ? "Paper channel ready; no nearby portal" : "Requesting live terrain";
        for (PortalData portal : portals) {
            BlockPos source = portal.pos.immutable();
            long lastRequest = LAST_REQUESTS.getOrDefault(source, 0L);
            if (now - lastRequest < REQUEST_INTERVAL_MILLIS) {
                continue;
            }
            LAST_REQUESTS.put(source, now);
            send(out -> {
                out.writeByte(SNAPSHOT_REQUEST);
                out.writeShort(PROTOCOL_VERSION);
                out.writeInt(source.getX());
                out.writeInt(source.getY());
                out.writeInt(source.getZ());
            });
        }
    }

    public static RemotePortalSnapshot getSnapshot(PortalData portal) {
        RemotePortalSnapshot snapshot = SNAPSHOTS.get(portal.pos);
        return snapshot != null && snapshot.isFresh(System.currentTimeMillis()) ? snapshot : null;
    }

    public static boolean hasSnapshot(PortalData portal) {
        return getSnapshot(portal) != null;
    }

    public static String diagnostic() {
        return diagnostic;
    }

    public static void reset() {
        SNAPSHOTS.clear();
        ASSEMBLIES.clear();
        LAST_REQUESTS.clear();
        lastHelloAt = 0L;
        diagnostic = "Waiting for Paper channel";
    }

    private static void accept(byte[] data) {
        if (data.length < 3 || data.length > PortalSyncPayload.MAX_PAYLOAD_BYTES) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            int type = input.readUnsignedByte();
            short protocol = input.readShort();
            if (protocol != PROTOCOL_VERSION) {
                return;
            }
            if (type == SNAPSHOT_PART) {
                readSnapshotPart(input);
            } else if (type == STATUS && input.available() > 0) {
                diagnostic = "Paper status " + input.readUnsignedByte();
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Invalid custom payloads are intentionally ignored. The server is
            // queried again later, and malformed data must not break rendering.
        }
    }

    private static void readSnapshotPart(DataInputStream input) throws IOException {
        BlockPos source = new BlockPos(input.readInt(), input.readInt(), input.readInt());
        BlockPos target = new BlockPos(input.readInt(), input.readInt(), input.readInt());
        Direction.Axis axis = input.readByte() == 0 ? Direction.Axis.X : Direction.Axis.Z;
        Identifier dimension = Identifier.parse(input.readUTF());
        long dayTime = input.readLong();
        boolean storming = input.readBoolean();
        int partIndex = input.readUnsignedByte();
        int partCount = input.readUnsignedByte();
        int voxelCount = input.readUnsignedShort();
        if (partCount < 1 || partCount > MAX_PARTS || partIndex >= partCount
            || voxelCount > MAX_VOXELS_PER_PART) {
            return;
        }

        SnapshotAssembly assembly = ASSEMBLIES.compute(source, (ignored, existing) ->
            existing == null || !existing.matches(target, dayTime, partCount)
                ? new SnapshotAssembly(source, target, axis, dimension, dayTime, storming, partCount)
                : existing
        );
        List<RemotePortalSnapshot.Voxel> voxels = new ArrayList<>(voxelCount);
        for (int i = 0; i < voxelCount; i++) {
            voxels.add(new RemotePortalSnapshot.Voxel(
                input.readByte(), input.readByte(), input.readByte(), input.readByte()
            ));
        }
        assembly.add(partIndex, voxels);
        if (assembly.complete()) {
            RemotePortalSnapshot snapshot = assembly.finish();
            SNAPSHOTS.put(source, snapshot);
            ASSEMBLIES.remove(source);
            diagnostic = "Live terrain active: " + snapshot.voxels().size() + " voxels";
        } else {
            diagnostic = "Receiving terrain " + (partIndex + 1) + "/" + partCount;
        }
    }

    private static boolean isConnected() {
        var handler = Minecraft.getInstance().getConnection();
        return handler != null && handler.getConnection().isConnected();
    }

    private static void send(IoWriter writer) {
        if (!isConnected()) {
            return;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            writer.write(output);
            output.flush();
            Minecraft.getInstance().getConnection().getConnection().send(
                new ServerboundCustomPayloadPacket(new PortalSyncPayload(bytes.toByteArray()))
            );
        } catch (IOException ignored) {
            // ByteArrayOutputStream does not normally throw. Keep transport
            // failures isolated from the client rendering loop.
        }
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream output) throws IOException;
    }

    private static final class SnapshotAssembly {
        private final BlockPos source;
        private final BlockPos target;
        private final Direction.Axis axis;
        private final Identifier dimension;
        private final long dayTime;
        private final boolean storming;
        private final int partCount;
        private final BitSet receivedParts;
        private final List<RemotePortalSnapshot.Voxel> voxels = new ArrayList<>();

        private SnapshotAssembly(BlockPos source, BlockPos target, Direction.Axis axis, Identifier dimension,
                                 long dayTime, boolean storming, int partCount) {
            this.source = source;
            this.target = target;
            this.axis = axis;
            this.dimension = dimension;
            this.dayTime = dayTime;
            this.storming = storming;
            this.partCount = partCount;
            this.receivedParts = new BitSet(partCount);
        }

        private boolean matches(BlockPos target, long dayTime, int partCount) {
            return this.target.equals(target) && this.dayTime == dayTime && this.partCount == partCount;
        }

        private void add(int partIndex, List<RemotePortalSnapshot.Voxel> partVoxels) {
            if (receivedParts.get(partIndex) || voxels.size() + partVoxels.size() > RemotePortalSnapshot.MAX_VOXELS) {
                return;
            }
            receivedParts.set(partIndex);
            voxels.addAll(partVoxels);
        }

        private boolean complete() {
            return receivedParts.cardinality() == partCount;
        }

        private RemotePortalSnapshot finish() {
            return new RemotePortalSnapshot(
                source, target, axis, dimension, dayTime, storming,
                voxels, System.currentTimeMillis()
            );
        }
    }
}
