package de.shockbase.levelblock.network;

import de.shockbase.levelblock.session.BlockColumn;

import java.util.Collection;
import java.util.Set;

public record BoundarySnapshot(String dimensionId, Set<BlockColumn> unlockedColumns) {

    public BoundarySnapshot {
        unlockedColumns = Set.copyOf(unlockedColumns);
    }

    public static BoundarySnapshot of(String dimensionId, Collection<BlockColumn> columns) {
        return new BoundarySnapshot(dimensionId, Set.copyOf(columns));
    }
}
