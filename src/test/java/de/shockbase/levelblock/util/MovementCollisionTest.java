package de.shockbase.levelblock.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementCollisionTest {

    private static final double EPSILON = 0.000001D;

    @Test
    void leavesMovementInsideUnlockedColumnsUntouched() {
        Set<String> unlocked = Set.of("0,0", "1,0");

        MovementCollision.Result result = MovementCollision.resolve(
                0.5D,
                0.5D,
                1.2D,
                0.5D,
                (x, z) -> unlocked.contains(x + "," + z)
        );

        assertEquals(1.2D, result.x(), EPSILON);
        assertEquals(0.5D, result.z(), EPSILON);
    }

    @Test
    void stopsAtThePositiveBoundaryUsingPlayerRadius() {
        MovementCollision.Result result = MovementCollision.resolve(
                0.5D,
                0.5D,
                1.2D,
                0.5D,
                (x, z) -> x == 0 && z == 0
        );

        assertEquals(0.6999D, result.x(), EPSILON);
        assertEquals(0.5D, result.z(), EPSILON);
    }

    @Test
    void stopsAtTheNegativeBoundaryUsingPlayerRadius() {
        MovementCollision.Result result = MovementCollision.resolve(
                0.5D,
                0.5D,
                -0.2D,
                0.5D,
                (x, z) -> x == 0 && z == 0
        );

        assertEquals(0.3001D, result.x(), EPSILON);
        assertEquals(0.5D, result.z(), EPSILON);
    }
}
