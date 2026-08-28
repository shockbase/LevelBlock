package de.shockbase.levelblock.server.session;

import de.shockbase.levelblock.server.ServerDimension;
import de.shockbase.levelblock.server.network.ServerNetwork;
import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.WorldProgress;
import de.shockbase.levelblock.storage.SessionRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SessionManager {

    private final MinecraftServer server;
    private final SessionRepository repository;
    private final ServerNetwork network;
    private final Logger logger;
    private final Map<UUID, LevelBlockSession> sessions;
    private final Map<UUID, UUID> activeMembership = new HashMap<>();
    private int saveAtTick = -1;

    public SessionManager(
            MinecraftServer server,
            SessionRepository repository,
            ServerNetwork network,
            Logger logger
    ) {
        this.server = server;
        this.repository = repository;
        this.network = network;
        this.logger = logger;
        this.sessions = new HashMap<>(repository.loadSessions());
        rebuildActiveMembership();
    }

    private void rebuildActiveMembership() {
        sessions.values().stream()
                .filter(LevelBlockSession::isActive)
                .sorted(Comparator.comparingLong(LevelBlockSession::getCreatedAt))
                .forEach(session -> session.getMembers().forEach(member -> {
                    UUID existing = activeMembership.putIfAbsent(member, session.getId());
                    if (existing != null && !existing.equals(session.getId())) {
                        logger.warn(
                                "Spieler {} ist in mehreren aktiven LevelBlock-Sessions. Verwende Session {}.",
                                member,
                                existing
                        );
                    }
                }));
    }

    public Collection<LevelBlockSession> getSessions() {
        return List.copyOf(sessions.values());
    }

    public Optional<LevelBlockSession> getSession(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public LevelBlockSession getActiveSession(UUID playerId) {
        UUID sessionId = activeMembership.get(playerId);
        LevelBlockSession session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null || !session.isActive() || !session.isMember(playerId)) {
            activeMembership.remove(playerId);
            return null;
        }
        return session;
    }

    public LevelBlockSession getActiveSession(ServerPlayer player) {
        return getActiveSession(player.getUUID());
    }

    public LevelBlockSession createSession(
            ServerPlayer owner,
            Collection<ServerPlayer> participants,
            ServerLevel level,
            BlockPos start
    ) {
        if (getActiveSession(owner) != null) {
            throw new IllegalStateException("Der Besitzer ist bereits in einer aktiven Session.");
        }
        List<UUID> memberIds = new ArrayList<>();
        for (ServerPlayer participant : participants) {
            if (getActiveSession(participant) == null) {
                memberIds.add(participant.getUUID());
            }
        }
        if (!memberIds.contains(owner.getUUID())) {
            memberIds.add(owner.getUUID());
        }
        LevelBlockSession session = LevelBlockSession.create(
                owner.getUUID(),
                owner.getGameProfile().name(),
                memberIds,
                ServerDimension.id(level),
                start.getX(),
                start.getZ()
        );
        sessions.put(session.getId(), session);
        session.getMembers().forEach(member -> activeMembership.put(member, session.getId()));
        markDirty();
        syncSession(session);
        return session;
    }

    public void stopSession(LevelBlockSession session) {
        session.stop();
        activeMembership.entrySet().removeIf(entry -> entry.getValue().equals(session.getId()));
        session.getMembers().forEach(member -> {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                network.clearBoundary(player);
            }
        });
        markDirty();
    }

    public void deleteSession(LevelBlockSession session) {
        session.getMembers().forEach(member -> {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                network.clearBoundary(player);
            }
        });
        activeMembership.entrySet().removeIf(entry -> entry.getValue().equals(session.getId()));
        sessions.remove(session.getId());
        markDirty();
    }

    public boolean invite(LevelBlockSession session, UUID playerId) {
        if (session.isMember(playerId)) {
            return false;
        }
        session.addInvite(playerId);
        markDirty();
        return true;
    }

    public boolean join(LevelBlockSession session, UUID playerId) {
        if (!session.isActive() || !session.isInvited(playerId) || getActiveSession(playerId) != null) {
            return false;
        }
        session.addMember(playerId);
        activeMembership.put(playerId, session.getId());
        markDirty();
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            syncPlayer(player);
        }
        return true;
    }

    public boolean leave(LevelBlockSession session, UUID playerId) {
        if (session.isOwner(playerId) || !session.isMember(playerId)) {
            return false;
        }
        session.removeMember(playerId);
        activeMembership.remove(playerId, session.getId());
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            network.clearBoundary(player);
        }
        markDirty();
        return true;
    }

    public List<LevelBlockSession> getInvitedSessions(UUID playerId) {
        return sessions.values().stream()
                .filter(LevelBlockSession::isActive)
                .filter(session -> session.isInvited(playerId))
                .sorted(Comparator.comparingLong(LevelBlockSession::getCreatedAt).reversed())
                .toList();
    }

    public Optional<LevelBlockSession> findSession(String token) {
        try {
            return getSession(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
            return sessions.values().stream()
                    .filter(session -> session.getOwnerName().equalsIgnoreCase(token))
                    .sorted(Comparator.comparingLong(LevelBlockSession::getCreatedAt).reversed())
                    .findFirst();
        }
    }

    public Optional<LevelBlockSession> findInvitedSession(UUID playerId, String token) {
        return findSession(token)
                .filter(LevelBlockSession::isActive)
                .filter(session -> session.isInvited(playerId));
    }

    public WorldProgress getOrCreateWorldProgress(
            LevelBlockSession session,
            ServerLevel level,
            BlockPos entry
    ) {
        String dimensionId = ServerDimension.id(level);
        WorldProgress progress = session.getWorldProgress(dimensionId);
        if (progress == null) {
            progress = session.createWorldProgress(dimensionId, entry.getX(), entry.getZ());
            markDirty();
        }
        return progress;
    }

    public void unlock(LevelBlockSession session, WorldProgress progress, Collection<BlockColumn> columns) {
        if (columns.isEmpty()) {
            return;
        }
        progress.unlockAll(columns);
        markDirty();
        syncSession(session);
    }

    public int countUnlocked(LevelBlockSession session) {
        return session.getWorlds().values().stream().mapToInt(WorldProgress::getUnlockedCount).sum();
    }

    public void syncPlayer(ServerPlayer player) {
        LevelBlockSession session = getActiveSession(player);
        if (session == null) {
            network.clearBoundary(player);
            return;
        }
        ServerLevel level = player.level();
        WorldProgress progress = getOrCreateWorldProgress(session, level, player.blockPosition());
        network.sendBoundary(player, ServerDimension.id(level), progress);
    }

    public void syncSession(LevelBlockSession session) {
        for (UUID member : session.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                syncPlayer(player);
            }
        }
    }

    public void tick() {
        if (saveAtTick >= 0 && server.getTickCount() >= saveAtTick) {
            saveAtTick = -1;
            repository.saveSessions(sessions.values());
        }
    }

    public void markDirty() {
        if (saveAtTick < 0) {
            saveAtTick = server.getTickCount() + 20;
        }
    }

    public void flush() {
        saveAtTick = -1;
        repository.saveSessions(sessions.values());
    }
}
