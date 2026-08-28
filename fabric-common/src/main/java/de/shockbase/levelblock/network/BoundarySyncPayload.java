package de.shockbase.levelblock.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BoundarySyncPayload(boolean active, String dimensionId, long[] unlockedColumns)
        implements CustomPacketPayload {

    public static final Type<BoundarySyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(NetworkProtocol.MOD_NAMESPACE, "boundary_sync")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BoundarySyncPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.active());
                buffer.writeUtf(payload.dimensionId(), 256);
                buffer.writeVarInt(payload.unlockedColumns().length);
                for (long column : payload.unlockedColumns()) {
                    buffer.writeLong(column);
                }
            },
            buffer -> {
                boolean active = buffer.readBoolean();
                String dimensionId = buffer.readUtf(256);
                int count = buffer.readVarInt();
                if (count < 0 || count > NetworkProtocol.MAX_COLUMNS) {
                    throw new IllegalArgumentException("Invalid LevelBlock column count: " + count);
                }
                long[] columns = new long[count];
                for (int index = 0; index < count; index++) {
                    columns[index] = buffer.readLong();
                }
                return new BoundarySyncPayload(active, dimensionId, columns);
            }
    );

    public static BoundarySyncPayload inactive() {
        return new BoundarySyncPayload(false, "", new long[0]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
