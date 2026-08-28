package de.shockbase.levelblock.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record LobbySyncPayload(List<LobbyArea> lobbies) implements CustomPacketPayload {

    public static final Type<LobbySyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(NetworkProtocol.MOD_NAMESPACE, "lobby_sync")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbySyncPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.lobbies().size());
                for (LobbyArea lobby : payload.lobbies()) {
                    buffer.writeUtf(lobby.dimensionId(), 256);
                    buffer.writeInt(lobby.centerX());
                    buffer.writeInt(lobby.centerZ());
                }
            },
            buffer -> {
                int count = buffer.readVarInt();
                if (count < 0 || count > NetworkProtocol.MAX_LOBBIES) {
                    throw new IllegalArgumentException("Invalid LevelBlock lobby count: " + count);
                }
                List<LobbyArea> lobbies = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    lobbies.add(new LobbyArea(buffer.readUtf(256), buffer.readInt(), buffer.readInt()));
                }
                return new LobbySyncPayload(lobbies);
            }
    );

    public LobbySyncPayload {
        lobbies = List.copyOf(lobbies);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
