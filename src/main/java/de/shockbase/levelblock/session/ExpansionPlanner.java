package de.shockbase.levelblock.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Pure domain service that decides which crossed columns may be unlocked. */
public final class ExpansionPlanner {

    private ExpansionPlanner() {
    }

    public static ExpansionPlan plan(WorldProgress progress, Collection<BlockColumn> traversedColumns) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(traversedColumns, "traversedColumns");

        var columnsToUnlock = new ArrayList<BlockColumn>();
        Set<BlockColumn> prospectiveUnlocks = new HashSet<>();

        for (BlockColumn column : traversedColumns) {
            Objects.requireNonNull(column, "traversedColumns must not contain null");
            if (progress.isUnlocked(column) || !prospectiveUnlocks.add(column)) {
                continue;
            }
            if (!hasAdjacentNeighbor(progress, prospectiveUnlocks, column)) {
                return new ExpansionPlan.Rejected(column);
            }
            columnsToUnlock.add(column);
        }

        return new ExpansionPlan.Approved(columnsToUnlock);
    }

    private static boolean hasAdjacentNeighbor(
            WorldProgress progress,
            Set<BlockColumn> prospectiveUnlocks,
            BlockColumn column
    ) {
        if (progress.hasAdjacentUnlockedNeighbor(column)) {
            return true;
        }
        int x = column.x();
        int z = column.z();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0)
                        && prospectiveUnlocks.contains(new BlockColumn(x + dx, z + dz))) {
                    return true;
                }
            }
        }
        return false;
    }
}
