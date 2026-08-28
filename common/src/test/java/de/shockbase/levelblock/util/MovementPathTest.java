package de.shockbase.levelblock.util;

import de.shockbase.levelblock.session.BlockColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementPathTest {

    @Test
    void returnsNoColumnsInsideSameBlock() {
        assertTrue(MovementPath.traversedColumns(0.2D, 0.2D, 0.8D, 0.9D).isEmpty());
    }

    @Test
    void listsCrossedColumnsInOrder() {
        assertEquals(
                List.of(new BlockColumn(1, 0), new BlockColumn(2, 0), new BlockColumn(3, 0)),
                MovementPath.traversedColumns(0.5D, 0.5D, 3.2D, 0.5D)
        );
    }

    @Test
    void handlesExactDiagonalCornerCrossings() {
        assertEquals(
                List.of(new BlockColumn(1, 1), new BlockColumn(2, 2)),
                MovementPath.traversedColumns(0.5D, 0.5D, 2.5D, 2.5D)
        );
    }
}
