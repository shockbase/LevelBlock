package de.shockbase.levelblock.session;

import org.bukkit.World;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class WorldProgress {

    private final UUID worldId;
    private final String worldName;
    private final int originX;
    private final int originZ;
    private final Set<BlockColumn> unlockedColumns;

    public WorldProgress(
            UUID worldId,
            String worldName,
            int originX,
            int originZ,
            Collection<BlockColumn> unlockedColumns
    ) {
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.worldName = requireText(worldName, "worldName");
        this.originX = originX;
        this.originZ = originZ;
        this.unlockedColumns = new HashSet<>(Objects.requireNonNull(unlockedColumns, "unlockedColumns"));
        if (this.unlockedColumns.contains(null)) {
            throw new IllegalArgumentException("unlockedColumns must not contain null");
        }
    }

    public static WorldProgress createInitial(World world, int centerX, int centerZ) {
        Objects.requireNonNull(world, "world");
        return createInitial(world.getUID(), world.getName(), centerX, centerZ);
    }

    public static WorldProgress createInitial(
            UUID worldId,
            String worldName,
            int centerX,
            int centerZ
    ) {
        Set<BlockColumn> columns = new HashSet<>();
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                columns.add(new BlockColumn(x, z));
            }
        }
        return new WorldProgress(worldId, worldName, centerX, centerZ, columns);
    }

    public UUID getWorldId() {
        return worldId;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginZ() {
        return originZ;
    }

    public Set<BlockColumn> getUnlockedColumns() {
        return Collections.unmodifiableSet(unlockedColumns);
    }

    public int getUnlockedCount() {
        return unlockedColumns.size();
    }

    public boolean isUnlocked(int x, int z) {
        return unlockedColumns.contains(new BlockColumn(x, z));
    }

    public boolean isUnlocked(BlockColumn column) {
        return unlockedColumns.contains(column);
    }

    public boolean hasAdjacentUnlockedNeighbor(BlockColumn column) {
        int x = column.x();
        int z = column.z();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (isUnlocked(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void unlock(BlockColumn column) {
        unlockedColumns.add(column);
    }

    public void unlockAll(Collection<BlockColumn> columns) {
        Objects.requireNonNull(columns, "columns");
        if (columns.contains(null)) {
            throw new IllegalArgumentException("columns must not contain null");
        }
        unlockedColumns.addAll(columns);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
