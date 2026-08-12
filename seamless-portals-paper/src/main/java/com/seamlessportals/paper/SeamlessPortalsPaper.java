package com.seamlessportals.paper;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Optional Paper server component for the enhanced Fabric client.
 *
 * <p>The plugin deliberately exposes only a fixed, validated box around the
 * Vanilla-derived destination of a nearby Nether portal. It never trusts a
 * client-selected world, target coordinate or radius.</p>
 */
public final class SeamlessPortalsPaper extends JavaPlugin implements Listener, PluginMessageListener {
    // Must exactly match the Fabric CustomPacketPayload id:
    // seamless-portals:sync (the mod id contains a hyphen).
    private static final String CHANNEL = "seamless-portals:sync";
    private static final short PROTOCOL_VERSION = 1;
    private static final int HELLO = 0x01;
    private static final int SNAPSHOT_REQUEST = 0x02;
    private static final int SNAPSHOT_PART = 0x10;
    private static final int STATUS = 0x11;
    private static final int STATUS_READY = 0;
    private static final int STATUS_INVALID_PORTAL = 1;
    private static final int STATUS_DESTINATION_UNAVAILABLE = 2;
    private static final long REQUEST_COOLDOWN_MILLIS = 700L;
    private static final int HORIZONTAL_RADIUS = 12;
    private static final int VERTICAL_RADIUS = 9;
    private static final int MAX_VOXELS = 6_000;
    private static final int MAX_VOXELS_PER_PART = 1_000;

    private final Set<UUID> enhancedPlayers = new HashSet<>();
    private final Map<UUID, Long> lastRequestAt = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        // Paper pushes a validated nearby snapshot every second as well as
        // answering requests. This makes the integration work with clients
        // whose channel-registration timing differs from Fabric's default.
        getServer().getScheduler().runTaskTimer(this, this::pushNearbySnapshots, 20L, 20L);
        getLogger().info("Seamless Portals live-terrain integration enabled.");
    }

    @Override
    public void onDisable() {
        enhancedPlayers.clear();
        lastRequestAt.clear();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message.length < 3) {
            return;
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            int type = input.readUnsignedByte();
            short protocol = input.readShort();
            if (protocol != PROTOCOL_VERSION) {
                return;
            }
            if (type == HELLO) {
                enhancedPlayers.add(player.getUniqueId());
                sendStatus(player, STATUS_READY);
                return;
            }
            if (type == SNAPSHOT_REQUEST) {
                handleSnapshotRequest(player, input);
            }
        } catch (IOException ignored) {
            // A malformed plugin message is dropped without affecting the server.
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        enhancedPlayers.remove(playerId);
        lastRequestAt.remove(playerId);
    }

    private void pushNearbySnapshots() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Block portal = findNearbyPortal(player);
            if (portal == null) {
                continue;
            }
            Destination destination = calculateDestination(
                player.getWorld(), portal.getX(), portal.getY(), portal.getZ()
            );
            if (destination == null) {
                continue;
            }
            Axis axis = portal.getBlockData() instanceof Orientable orientable ? orientable.getAxis() : Axis.X;
            enhancedPlayers.add(player.getUniqueId());
            sendStatus(player, STATUS_READY);
            sendSnapshot(player, portal.getX(), portal.getY(), portal.getZ(), axis, destination);
        }
    }

    private Block findNearbyPortal(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        int centerX = origin.getBlockX();
        int centerY = origin.getBlockY();
        int centerZ = origin.getBlockZ();
        for (int y = -8; y <= 8; y++) {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    Block candidate = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                    if (candidate.getType() == Material.NETHER_PORTAL) {
                        return canonicalPortalBlock(candidate);
                    }
                }
            }
        }
        return null;
    }

    private Block canonicalPortalBlock(Block start) {
        Axis axis = start.getBlockData() instanceof Orientable orientable ? orientable.getAxis() : Axis.X;
        List<Block> queue = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);
        Block minimum = start;
        for (int index = 0; index < queue.size(); index++) {
            Block current = queue.get(index);
            String key = current.getX() + ":" + current.getY() + ":" + current.getZ();
            if (!visited.add(key) || current.getType() != Material.NETHER_PORTAL) {
                continue;
            }
            if (current.getY() < minimum.getY()
                || (current.getY() == minimum.getY() && current.getX() < minimum.getX())
                || (current.getY() == minimum.getY() && current.getX() == minimum.getX() && current.getZ() < minimum.getZ())) {
                minimum = current;
            }
            queue.add(current.getRelative(BlockFace.UP));
            queue.add(current.getRelative(BlockFace.DOWN));
            if (axis == Axis.X) {
                queue.add(current.getRelative(BlockFace.EAST));
                queue.add(current.getRelative(BlockFace.WEST));
            } else {
                queue.add(current.getRelative(BlockFace.NORTH));
                queue.add(current.getRelative(BlockFace.SOUTH));
            }
        }
        return minimum;
    }

    private void handleSnapshotRequest(Player player, DataInputStream input) throws IOException {
        // A client may join before the custom channel advertisement reaches
        // Fabric. The request itself is fully validated below, so it is a
        // safe implicit handshake and cannot be lost like a one-shot HELLO.
        enhancedPlayers.add(player.getUniqueId());
        long now = System.currentTimeMillis();
        long lastRequest = lastRequestAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastRequest < REQUEST_COOLDOWN_MILLIS) {
            return;
        }
        lastRequestAt.put(player.getUniqueId(), now);

        int sourceX = input.readInt();
        int sourceY = input.readInt();
        int sourceZ = input.readInt();
        Block sourceBlock = player.getWorld().getBlockAt(sourceX, sourceY, sourceZ);
        if (sourceBlock.getType() != Material.NETHER_PORTAL
            || sourceBlock.getLocation().distanceSquared(player.getLocation()) > 36.0D * 36.0D) {
            sendStatus(player, STATUS_INVALID_PORTAL);
            return;
        }

        Destination destination = calculateDestination(player.getWorld(), sourceX, sourceY, sourceZ);
        if (destination == null) {
            sendStatus(player, STATUS_DESTINATION_UNAVAILABLE);
            return;
        }
        Axis axis = sourceBlock.getBlockData() instanceof Orientable orientable ? orientable.getAxis() : Axis.X;
        sendSnapshot(player, sourceX, sourceY, sourceZ, axis, destination);
    }

    private Destination calculateDestination(World source, int sourceX, int sourceY, int sourceZ) {
        World.Environment wanted;
        double scale;
        if (source.getEnvironment() == World.Environment.NORMAL) {
            wanted = World.Environment.NETHER;
            scale = 0.125D;
        } else if (source.getEnvironment() == World.Environment.NETHER) {
            wanted = World.Environment.NORMAL;
            scale = 8.0D;
        } else {
            wanted = World.Environment.NORMAL;
            scale = 1.0D;
        }
        World targetWorld = Bukkit.getWorlds().stream()
            .filter(world -> world.getEnvironment() == wanted)
            .findFirst()
            .orElse(null);
        if (targetWorld == null) {
            return null;
        }
        return new Destination(
            targetWorld,
            (int) Math.floor(sourceX * scale),
            sourceY,
            (int) Math.floor(sourceZ * scale)
        );
    }

    private void sendSnapshot(Player player, int sourceX, int sourceY, int sourceZ, Axis sourceAxis, Destination target) {
        List<Voxel> voxels = sampleTerrain(target);
        int partCount = Math.max(1, (int) Math.ceil(voxels.size() / (double) MAX_VOXELS_PER_PART));
        for (int partIndex = 0; partIndex < partCount; partIndex++) {
            int from = partIndex * MAX_VOXELS_PER_PART;
            int to = Math.min(voxels.size(), from + MAX_VOXELS_PER_PART);
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                 DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(SNAPSHOT_PART);
                output.writeShort(PROTOCOL_VERSION);
                output.writeInt(sourceX);
                output.writeInt(sourceY);
                output.writeInt(sourceZ);
                output.writeInt(target.x());
                output.writeInt(target.y());
                output.writeInt(target.z());
                output.writeByte(sourceAxis == Axis.X ? 0 : 1);
                output.writeUTF(dimensionId(target.world()));
                output.writeLong(target.world().getTime());
                output.writeBoolean(target.world().hasStorm());
                output.writeByte(partIndex);
                output.writeByte(partCount);
                output.writeShort(to - from);
                for (int index = from; index < to; index++) {
                    Voxel voxel = voxels.get(index);
                    output.writeByte(voxel.x());
                    output.writeByte(voxel.y());
                    output.writeByte(voxel.z());
                    output.writeByte(voxel.material());
                }
                output.flush();
                player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
            } catch (IOException exception) {
                getLogger().warning("Unable to encode portal terrain snapshot: " + exception.getMessage());
                return;
            }
        }
    }

    private static String dimensionId(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "minecraft:the_nether";
            case THE_END -> "minecraft:the_end";
            default -> "minecraft:overworld";
        };
    }

    private List<Voxel> sampleTerrain(Destination target) {
        List<Voxel> result = new ArrayList<>();
        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS && result.size() < MAX_VOXELS; dx++) {
            for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS && result.size() < MAX_VOXELS; dy++) {
                for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS && result.size() < MAX_VOXELS; dz++) {
                    int blockX = target.x() + dx;
                    int blockY = target.y() + dy;
                    int blockZ = target.z() + dz;
                    Material material = target.world().getBlockAt(blockX, blockY, blockZ).getType();
                    byte palette = classify(material);
                    if (palette != Palette.AIR && isExposed(target.world(), blockX, blockY, blockZ)) {
                        result.add(new Voxel((byte) dx, (byte) dy, (byte) dz, palette));
                    }
                }
            }
        }
        return result;
    }

    private static boolean isExposed(World world, int x, int y, int z) {
        return world.getBlockAt(x + 1, y, z).getType().isAir()
            || world.getBlockAt(x - 1, y, z).getType().isAir()
            || world.getBlockAt(x, y + 1, z).getType().isAir()
            || world.getBlockAt(x, y - 1, z).getType().isAir()
            || world.getBlockAt(x, y, z + 1).getType().isAir()
            || world.getBlockAt(x, y, z - 1).getType().isAir();
    }

    private void sendStatus(Player player, int status) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(STATUS);
            output.writeShort(PROTOCOL_VERSION);
            output.writeByte(status);
            output.flush();
            player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
        } catch (IOException ignored) {
            // In-memory streams do not normally throw, and status is optional.
        }
    }

    private static byte classify(Material material) {
        if (material.isAir()) return Palette.AIR;
        String name = material.name();
        if (name.contains("WATER")) return Palette.WATER;
        if (name.contains("LAVA")) return Palette.LAVA;
        if (name.contains("NETHERRACK")) return Palette.NETHERRACK;
        if (name.contains("BASALT")) return Palette.BASALT;
        if (name.contains("BLACKSTONE")) return Palette.BLACKSTONE;
        if (name.contains("QUARTZ")) return Palette.QUARTZ;
        if (name.contains("NETHER_BRICK")) return Palette.NETHER_BRICKS;
        if (name.contains("END_STONE")) return Palette.END_STONE;
        if (name.contains("DEEPSLATE")) return Palette.DEEPSLATE;
        if (name.contains("LEAVES") || name.contains("VINE")) return Palette.LEAVES;
        if (name.contains("LOG") || name.contains("WOOD") || name.contains("STEM")) return Palette.WOOD;
        if (name.contains("GRASS") || name.contains("MOSS")) return Palette.GRASS;
        if (name.contains("DIRT") || name.contains("PODZOL") || name.contains("MYCELIUM")) return Palette.DIRT;
        if (name.contains("SAND") || name.contains("GRAVEL")) return Palette.SAND;
        if (name.contains("SNOW")) return Palette.SNOW;
        if (name.contains("ICE")) return Palette.ICE;
        if (name.contains("GLASS")) return Palette.GLASS;
        if (name.contains("ORE")) return Palette.ORE;
        if (name.contains("STONE") || name.contains("COBBLE") || name.contains("TUFF") || name.contains("ANDESITE")
            || name.contains("DIORITE") || name.contains("GRANITE")) return Palette.STONE;
        return Palette.OTHER;
    }

    private record Destination(World world, int x, int y, int z) {
    }

    private record Voxel(byte x, byte y, byte z, byte material) {
    }

    /** Keep values identical to the Fabric client palette. */
    private static final class Palette {
        private static final byte AIR = 0;
        private static final byte GRASS = 1;
        private static final byte DIRT = 2;
        private static final byte STONE = 3;
        private static final byte WATER = 4;
        private static final byte LEAVES = 5;
        private static final byte WOOD = 6;
        private static final byte SAND = 7;
        private static final byte SNOW = 8;
        private static final byte ICE = 9;
        private static final byte LAVA = 10;
        private static final byte NETHERRACK = 11;
        private static final byte BASALT = 12;
        private static final byte BLACKSTONE = 13;
        private static final byte QUARTZ = 14;
        private static final byte NETHER_BRICKS = 15;
        private static final byte END_STONE = 16;
        private static final byte DEEPSLATE = 17;
        private static final byte ORE = 19;
        private static final byte GLASS = 20;
        private static final byte OTHER = 21;
    }
}
