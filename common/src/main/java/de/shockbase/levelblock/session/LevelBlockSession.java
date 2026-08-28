package de.shockbase.levelblock.session;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class LevelBlockSession {

    private final UUID id;
    private final UUID ownerId;
    private final String ownerName;
    private final long createdAt;
    private final Set<UUID> members;
    private final Set<UUID> invites;
    private final Map<String, WorldProgress> worlds;
    private SessionStatus status;

    public LevelBlockSession(
            UUID id,
            UUID ownerId,
            String ownerName,
            long createdAt,
            SessionStatus status,
            Collection<UUID> members,
            Collection<UUID> invites,
            Map<String, WorldProgress> worlds
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ownerName = requireText(ownerName, "ownerName");
        this.createdAt = createdAt;
        this.status = Objects.requireNonNull(status, "status");
        this.members = new HashSet<>(Objects.requireNonNull(members, "members"));
        this.invites = new HashSet<>(Objects.requireNonNull(invites, "invites"));
        this.worlds = new HashMap<>(Objects.requireNonNull(worlds, "worlds"));
        if (this.members.contains(null) || this.invites.contains(null)
                || this.worlds.containsKey(null) || this.worlds.containsValue(null)) {
            throw new IllegalArgumentException("Session collections must not contain null");
        }
        this.members.add(ownerId);
        this.invites.removeAll(this.members);
    }

    public static LevelBlockSession create(
            UUID ownerId,
            String ownerName,
            Collection<UUID> members,
            String dimensionId,
            int startX,
            int startZ
    ) {
        WorldProgress initial = WorldProgress.createInitial(dimensionId, startX, startZ);
        return new LevelBlockSession(
                UUID.randomUUID(),
                ownerId,
                ownerName,
                System.currentTimeMillis(),
                SessionStatus.ACTIVE,
                members,
                Set.of(),
                Map.of(initial.getDimensionId(), initial)
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void stop() {
        status = SessionStatus.STOPPED;
        invites.clear();
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public Set<UUID> getInvites() {
        return Collections.unmodifiableSet(invites);
    }

    public Map<String, WorldProgress> getWorlds() {
        return Collections.unmodifiableMap(worlds);
    }

    public boolean isOwner(UUID playerId) {
        return ownerId.equals(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isInvited(UUID playerId) {
        return invites.contains(playerId);
    }

    public void addMember(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        members.add(playerId);
        invites.remove(playerId);
    }

    public void removeMember(UUID playerId) {
        if (!ownerId.equals(playerId)) {
            members.remove(playerId);
        }
    }

    public void addInvite(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!members.contains(playerId)) {
            invites.add(playerId);
        }
    }

    public WorldProgress getWorldProgress(String dimensionId) {
        return worlds.get(dimensionId);
    }

    public WorldProgress createWorldProgress(String dimensionId, int centerX, int centerZ) {
        WorldProgress progress = WorldProgress.createInitial(dimensionId, centerX, centerZ);
        worlds.putIfAbsent(progress.getDimensionId(), progress);
        return worlds.get(progress.getDimensionId());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
