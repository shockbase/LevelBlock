package de.shockbase.levelblock.session;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class WorldProgress {

    private final String dimensionId;
    private final int originX;
    private final int originZ;
    private final Set<BlockColumn> unlockedColumns;

    public WorldProgress(
            String dimensionId,
            int originX,
            int originZ,
            Collection<BlockColumn> unlockedColumns
    ) {
        this.dimensionId = requireText(dimensionId, "dimensionId");
        this.originX = originX;
        this.originZ = originZ;
        this.unlockedColumns = new HashSet<>(Objects.requireNonNull(unlockedColumns, "unlockedColumns"));
        if (this.unlockedColumns.contains(null)) {
            throw new IllegalArgumentException("unlockedColumns must not contain null");
        }
    }

    public static WorldProgress createInitial(String dimensionId, int centerX, int centerZ) {
        Set<BlockColumn> columns = new HashSet<>();
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                columns.add(new BlockColumn(x, z));
            }
        }
        return new WorldProgress(dimensionId, centerX, centerZ, columns);
    }

    public String getDimensionId() {
        return dimensionId;
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
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && isUnlocked(column.x() + dx, column.z() + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void unlock(BlockColumn column) {
        unlockedColumns.add(Objects.requireNonNull(column, "column"));
    }

    public void unlockAll(Collection<BlockColumn> columns) {
        Objects.requireNonNull(columns, "columns");
        for (BlockColumn column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("columns must not contain null");
            }
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
