package com.seamlessportals.client.network;

import com.seamlessportals.client.SeamlessPortalsClient;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Raw, size-limited bridge payload shared with the optional Paper plugin.
 *
 * <p>The Paper plugin messaging API transports an opaque byte array, therefore
 * this codec deliberately contains no client-only Minecraft registries. The
 * payload body is versioned by {@link PortalWorldSync}.</p>
 */
public record PortalSyncPayload(byte[] data) implements CustomPacketPayload {
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SeamlessPortalsClient.MOD_ID, "sync");
    public static final Type<PortalSyncPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PortalSyncPayload> CODEC = new StreamCodec<>() {
        @Override
        public PortalSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            return new PortalSyncPayload(buffer.readByteArray(MAX_PAYLOAD_BYTES));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PortalSyncPayload payload) {
            if (payload.data.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Portal sync payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
            }
            buffer.writeByteArray(payload.data);
        }
    };

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
    }

    @Override
    public Type<PortalSyncPayload> type() {
        return TYPE;
    }
}
