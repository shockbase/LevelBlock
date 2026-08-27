package de.shockbase.levelblock.session;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProgressTest {

    @Test
    void createsNineInitialColumnsAroundOrigin() {
        WorldProgress progress = WorldProgress.createInitial(UUID.randomUUID(), "world", 10, -4);

        assertEquals(9, progress.getUnlockedCount());
        assertTrue(progress.isUnlocked(9, -5));
        assertTrue(progress.isUnlocked(11, -3));
        assertFalse(progress.isUnlocked(12, -4));
    }

    @Test
    void recognizesCardinalAndDiagonalNeighbors() {
        WorldProgress progress = progressWith(new BlockColumn(0, 0));

        assertTrue(progress.hasAdjacentUnlockedNeighbor(new BlockColumn(1, 0)));
        assertTrue(progress.hasAdjacentUnlockedNeighbor(new BlockColumn(1, 1)));
        assertFalse(progress.hasAdjacentUnlockedNeighbor(new BlockColumn(2, 0)));
    }

    @Test
    void exposesAnUnmodifiableColumnView() {
        WorldProgress progress = progressWith(new BlockColumn(0, 0));

        assertThrows(
                UnsupportedOperationException.class,
                () -> progress.getUnlockedColumns().add(new BlockColumn(1, 0))
        );
    }

    private static WorldProgress progressWith(BlockColumn... columns) {
        return new WorldProgress(UUID.randomUUID(), "world", 0, 0, List.of(columns));
    }
}
