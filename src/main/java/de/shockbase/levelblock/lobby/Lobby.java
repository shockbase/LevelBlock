package de.shockbase.levelblock.lobby;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class Lobby {

    private final UUID ownerId;
    private final UUID worldId;
    private final String worldName;
    private final int centerX;
    private final int centerZ;
    private boolean starting;

    public Lobby(Player owner) {
        Location location = owner.getLocation();
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Spieler hat keine Welt.");
        }
        this.ownerId = owner.getUniqueId();
        this.worldId = world.getUID();
        this.worldName = world.getName();
        this.centerX = location.getBlockX();
        this.centerZ = location.getBlockZ();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getWorldId() {
        return worldId;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public boolean isStarting() {
        return starting;
    }

    public void setStarting(boolean starting) {
        this.starting = starting;
    }

    public boolean contains(Player player) {
        World world = player.getWorld();
        if (!world.getUID().equals(worldId)) {
            return false;
        }
        Location location = player.getLocation();
        return Math.abs(location.getBlockX() - centerX) <= 2
                && Math.abs(location.getBlockZ() - centerZ) <= 2;
    }
}
