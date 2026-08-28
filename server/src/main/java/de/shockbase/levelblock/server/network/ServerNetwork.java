package de.shockbase.levelblock.server.network;

import de.shockbase.levelblock.network.BoundarySyncPayload;
import de.shockbase.levelblock.network.HelloPayload;
import de.shockbase.levelblock.network.LobbyArea;
import de.shockbase.levelblock.network.LobbySyncPayload;
import de.shockbase.levelblock.network.NetworkProtocol;
import de.shockbase.levelblock.session.WorldProgress;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class ServerNetwork {

    private static final int HANDSHAKE_TIMEOUT_TICKS = 100;
    private static final int MAX_BOUNDARY_PACKET_BYTES = 8 * 1024 * 1024;

    private final Set<UUID> verifiedClients = new HashSet<>();
    private final Map<UUID, Integer> pendingClients = new HashMap<>();
    private Consumer<ServerPlayer> initialSync = ignored -> {
    };

    public void register() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(
                BoundarySyncPayload.TYPE,
                BoundarySyncPayload.CODEC,
                MAX_BOUNDARY_PACKET_BYTES
        );
        PayloadTypeRegistry.clientboundPlay().register(LobbySyncPayload.TYPE, LobbySyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (payload.protocolVersion() != NetworkProtocol.VERSION) {
                player.connection.disconnect(Component.literal(
                        "LevelBlock Client/Server-Protokoll ist nicht kompatibel."
                ));
                return;
            }
            pendingClients.remove(player.getUUID());
            verifiedClients.add(player.getUUID());
            initialSync.accept(player);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                pendingClients.put(handler.player.getUUID(), server.getTickCount())
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.player.getUUID();
            pendingClients.remove(playerId);
            verifiedClients.remove(playerId);
        });
    }

    public void setInitialSync(Consumer<ServerPlayer> initialSync) {
        this.initialSync = initialSync;
    }

    public boolean isVerified(ServerPlayer player) {
        return verifiedClients.contains(player.getUUID());
    }

    public void tick(MinecraftServer server) {
        int now = server.getTickCount();
        pendingClients.entrySet().removeIf(entry -> {
            if (now - entry.getValue() < HANDSHAKE_TIMEOUT_TICKS) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.connection.disconnect(Component.literal(
                        "Für diesen Server wird die LevelBlock-Client-Mod benötigt."
                ));
            }
            return true;
        });
    }

    public void sendBoundary(ServerPlayer player, String dimensionId, WorldProgress progress) {
        if (!isVerified(player) || !ServerPlayNetworking.canSend(player, BoundarySyncPayload.TYPE)) {
            return;
        }
        long[] columns = progress.getUnlockedColumns().stream()
                .mapToLong(column -> column.packed())
                .sorted()
                .toArray();
        ServerPlayNetworking.send(player, new BoundarySyncPayload(true, dimensionId, columns));
    }

    public void clearBoundary(ServerPlayer player) {
        if (isVerified(player) && ServerPlayNetworking.canSend(player, BoundarySyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, BoundarySyncPayload.inactive());
        }
    }

    public void sendLobbies(ServerPlayer player, Collection<LobbyArea> lobbies) {
        if (isVerified(player) && ServerPlayNetworking.canSend(player, LobbySyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new LobbySyncPayload(lobbies.stream().toList()));
        }
    }
}
