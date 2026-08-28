package de.shockbase.levelblock.server.lobby;

import de.shockbase.levelblock.network.LobbyArea;
import de.shockbase.levelblock.server.SafePositionFinder;
import de.shockbase.levelblock.server.ServerDimension;
import de.shockbase.levelblock.server.network.ServerNetwork;
import de.shockbase.levelblock.server.session.SessionManager;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.WorldProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LobbyManager {

    private final MinecraftServer server;
    private final SessionManager sessions;
    private final ServerNetwork network;
    private final SafePositionFinder safePositions;
    private final Map<UUID, Lobby> lobbies = new HashMap<>();

    public LobbyManager(
            MinecraftServer server,
            SessionManager sessions,
            ServerNetwork network,
            SafePositionFinder safePositions
    ) {
        this.server = server;
        this.sessions = sessions;
        this.network = network;
        this.safePositions = safePositions;
    }

    public Lobby create(ServerPlayer owner) {
        Lobby existing = lobbies.get(owner.getUUID());
        if (existing != null && existing.isStarting()) {
            throw new IllegalStateException("Der Countdown deiner Lobby läuft bereits.");
        }
        Lobby lobby = new Lobby(
                owner.getUUID(),
                ServerDimension.id(owner.level()),
                owner.blockPosition().getX(),
                owner.blockPosition().getZ()
        );
        lobbies.put(owner.getUUID(), lobby);
        syncAll();
        return lobby;
    }

    public Lobby get(UUID ownerId) {
        return lobbies.get(ownerId);
    }

    public void start(ServerPlayer owner) {
        Lobby lobby = lobbies.get(owner.getUUID());
        if (lobby == null) {
            throw new IllegalStateException("Du hast keine Lobby. Nutze zuerst /levelblock lobby.");
        }
        if (lobby.isStarting()) {
            throw new IllegalStateException("Der Countdown läuft bereits.");
        }
        if (sessions.getActiveSession(owner) != null) {
            throw new IllegalStateException("Du bist bereits in einer aktiven Session.");
        }
        if (!lobby.contains(owner)) {
            throw new IllegalStateException("Du musst dich in deinem 5x5-Lobbybereich befinden.");
        }
        lobby.start(server.getTickCount());
        syncAll();
    }

    public void tick() {
        List<Lobby> due = lobbies.values().stream()
                .filter(lobby -> lobby.countdownDue(server.getTickCount()))
                .toList();
        for (Lobby lobby : due) {
            tickCountdown(lobby);
        }
    }

    private void tickCountdown(Lobby lobby) {
        ServerPlayer owner = server.getPlayerList().getPlayer(lobby.ownerId());
        if (owner == null || !lobby.contains(owner)) {
            cancel(lobby, owner, "Lobby-Countdown abgebrochen.");
            return;
        }
        int count = lobby.consumeCountdownStep();
        List<ServerPlayer> inside = playersInside(lobby);
        if (count > 0) {
            showTitle(inside, Component.literal(Integer.toString(count)).withStyle(ChatFormatting.GOLD));
            return;
        }
        startSession(lobby, owner, inside);
    }

    private void startSession(Lobby lobby, ServerPlayer owner, List<ServerPlayer> candidates) {
        List<ServerPlayer> participants = new ArrayList<>();
        for (ServerPlayer candidate : candidates) {
            if (sessions.getActiveSession(candidate) == null) {
                participants.add(candidate);
            } else if (!candidate.getUUID().equals(owner.getUUID())) {
                candidate.sendSystemMessage(Component.literal(
                        "Du bist bereits in einer aktiven Session und wurdest nicht aufgenommen."
                ).withStyle(ChatFormatting.RED));
            }
        }
        if (!participants.contains(owner)) {
            participants.add(owner);
        }

        LevelBlockSession session;
        try {
            session = sessions.createSession(
                    owner,
                    participants,
                    owner.level(),
                    new BlockPos(lobby.centerX(), owner.blockPosition().getY(), lobby.centerZ())
            );
        } catch (RuntimeException exception) {
            cancel(lobby, owner, exception.getMessage());
            return;
        }

        WorldProgress progress = session.getWorldProgress(lobby.dimensionId());
        for (ServerPlayer participant : participants) {
            showTitle(List.of(participant), Component.literal("LEVELBLOCK").withStyle(ChatFormatting.GREEN));
            if (ServerDimension.id(participant.level()).equals(lobby.dimensionId())
                    && !progress.isUnlocked(participant.blockPosition().getX(), participant.blockPosition().getZ())) {
                Vec3 safe = safePositions.findNearestAllowed(participant.level(), participant.position(), progress);
                participant.teleportTo(safe.x, safe.y, safe.z);
            }
            participant.sendSystemMessage(Component.literal(
                    "LevelBlock-Session gestartet: " + session.getId()
            ).withStyle(ChatFormatting.GREEN));
        }
        lobbies.remove(lobby.ownerId());
        syncAll();
    }

    private void cancel(Lobby lobby, ServerPlayer owner, String message) {
        lobbies.remove(lobby.ownerId());
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        }
        syncAll();
    }

    public List<ServerPlayer> playersInside(Lobby lobby) {
        return server.getPlayerList().getPlayers().stream().filter(lobby::contains).toList();
    }

    public void syncPlayer(ServerPlayer player) {
        network.sendLobbies(player, areas());
    }

    public void syncAll() {
        List<LobbyArea> areas = areas();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            network.sendLobbies(player, areas);
        }
    }

    private List<LobbyArea> areas() {
        return lobbies.values().stream().map(Lobby::area).toList();
    }

    private static void showTitle(List<ServerPlayer> players, Component title) {
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(1, 16, 3));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }
}
