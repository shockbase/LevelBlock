package de.shockbase.levelblock.util;

import de.shockbase.levelblock.session.BlockColumn;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementPathTest {

    @Test
    void returnsNoColumnsInsideTheSameBlock() {
        assertTrue(MovementPath.traversedColumns(at(0.2, 0.2), at(0.8, 0.9)).isEmpty());
    }

    @Test
    void listsEveryCrossedColumnInOrder() {
        assertEquals(
                List.of(
                        new BlockColumn(1, 0),
                        new BlockColumn(2, 0),
                        new BlockColumn(3, 0)
                ),
                MovementPath.traversedColumns(at(0.5, 0.5), at(3.2, 0.5))
        );
    }

    @Test
    void handlesExactDiagonalCornerCrossings() {
        assertEquals(
                List.of(new BlockColumn(1, 1), new BlockColumn(2, 2)),
                MovementPath.traversedColumns(at(0.5, 0.5), at(2.5, 2.5))
        );
    }

    @Test
    void fallsBackToDestinationForPathologicalDistances() {
        assertEquals(
                List.of(new BlockColumn(600, 0)),
                MovementPath.traversedColumns(at(0.5, 0.5), at(600.5, 0.5))
        );
    }

    private static Location at(double x, double z) {
        return new Location(null, x, 64.0D, z);
    }
}
