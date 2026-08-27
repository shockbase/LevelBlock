package de.shockbase.levelblock.session;

import de.shockbase.levelblock.boundary.BoundaryRenderer;
import de.shockbase.levelblock.storage.SessionRepository;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SessionManager {

    private final JavaPlugin plugin;
    private final SessionRepository sessionRepository;
    private final BoundaryRenderer boundaryRenderer;
    private final Map<UUID, LevelBlockSession> sessions;
    private final Map<UUID, UUID> activeMembership = new HashMap<>();
    private BukkitTask pendingSave;

    public SessionManager(
            JavaPlugin plugin,
            SessionRepository sessionRepository,
            Map<UUID, LevelBlockSession> loadedSessions,
            BoundaryRenderer boundaryRenderer
    ) {
        this.plugin = plugin;
        this.sessionRepository = sessionRepository;
        this.boundaryRenderer = boundaryRenderer;
        this.sessions = new HashMap<>(loadedSessions);
        rebuildActiveMembership();
    }

    private void rebuildActiveMembership() {
        sessions.values().stream()
                .filter(LevelBlockSession::isActive)
                .sorted(Comparator.comparingLong(LevelBlockSession::getCreatedAt))
                .forEach(session -> {
                    for (UUID member : session.getMembers()) {
                        UUID existing = activeMembership.putIfAbsent(member, session.getId());
                        if (existing != null && !existing.equals(session.getId())) {
                            plugin.getLogger().warning(
                                    "Spieler " + member + " ist in mehreren aktiven LevelBlock-Sessions. "
                                            + "Verwende Session " + existing + "."
                            );
                        }
                    }
                });
    }

    public Collection<LevelBlockSession> getSessions() {
        return List.copyOf(sessions.values());
    }

    public Optional<LevelBlockSession> getSession(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public LevelBlockSession getActiveSession(UUID playerId) {
        UUID sessionId = activeMembership.get(playerId);
        if (sessionId == null) {
            return null;
        }
        LevelBlockSession session = sessions.get(sessionId);
        if (session == null || !session.isActive() || !session.isMember(playerId)) {
            activeMembership.remove(playerId);
            return null;
        }
        return session;
    }

    public LevelBlockSession getActiveSession(Player player) {
        return getActiveSession(player.getUniqueId());
    }

    public LevelBlockSession createSession(Player owner, Collection<Player> participants, Location startLocation) {
        if (getActiveSession(owner) != null) {
            throw new IllegalStateException("Der Besitzer ist bereits in einer aktiven Session.");
        }

        List<UUID> memberIds = new ArrayList<>();
        for (Player participant : participants) {
            if (getActiveSession(participant) == null) {
                memberIds.add(participant.getUniqueId());
            }
        }
        if (!memberIds.contains(owner.getUniqueId())) {
            memberIds.add(owner.getUniqueId());
        }

        LevelBlockSession session = LevelBlockSession.create(
                owner.getUniqueId(),
                owner.getName(),
                memberIds,
                startLocation
        );
        sessions.put(session.getId(), session);
        for (UUID memberId : session.getMembers()) {
            activeMembership.put(memberId, session.getId());
        }
        boundaryRenderer.refresh(session, session.getWorldProgress(startLocation.getWorld().getUID()));
        markDirty();
        return session;
    }

    public void stopSession(LevelBlockSession session) {
        boundaryRenderer.removeSession(session);
        session.stop();
        activeMembership.entrySet().removeIf(entry -> entry.getValue().equals(session.getId()));
        markDirty();
    }

    public void deleteSession(LevelBlockSession session) {
        boundaryRenderer.removeSession(session);
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
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            WorldProgress progress = getOrCreateWorldProgress(session, player.getLocation());
            boundaryRenderer.ensureVisible(session, progress, player);
        }
        markDirty();
        return true;
    }

    public boolean leave(LevelBlockSession session, UUID playerId) {
        if (session.isOwner(playerId) || !session.isMember(playerId)) {
            return false;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            boundaryRenderer.hide(session, player);
        }
        session.removeMember(playerId);
        activeMembership.remove(playerId, session.getId());
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
            UUID id = UUID.fromString(token);
            return getSession(id);
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

    public WorldProgress getOrCreateWorldProgress(LevelBlockSession session, Location entryLocation) {
        World world = entryLocation.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Position hat keine Welt.");
        }
        WorldProgress existing = session.getWorldProgress(world.getUID());
        if (existing != null) {
            return existing;
        }
        WorldProgress created = session.createWorldProgress(world, entryLocation.getBlockX(), entryLocation.getBlockZ());
        boundaryRenderer.refresh(session, created);
        markDirty();
        return created;
    }

    public void unlock(LevelBlockSession session, WorldProgress progress, Collection<BlockColumn> columns) {
        if (columns.isEmpty()) {
            return;
        }
        progress.unlockAll(columns);
        boundaryRenderer.refresh(session, progress);
        boundaryRenderer.flashUnlock(session, progress);
        markDirty();
    }

    public int countUnlocked(LevelBlockSession session) {
        return session.getWorlds().values().stream().mapToInt(WorldProgress::getUnlockedCount).sum();
    }

    public void markDirty() {
        if (pendingSave != null) {
            return;
        }
        pendingSave = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingSave = null;
            sessionRepository.saveSessions(sessions.values());
        }, 20L);
    }

    public void flush() {
        if (pendingSave != null) {
            pendingSave.cancel();
            pendingSave = null;
        }
        sessionRepository.saveSessions(sessions.values());
    }
}
