package de.shockbase.levelblock.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementCollisionTest {

    private static final double EPSILON = 0.000001D;

    @Test
    void stopsAtWallAndPreservesTangentialMovement() {
        Set<String> unlocked = Set.of("0,0", "0,1");
        MovementCollision.Result result = MovementCollision.resolve(
                0.5D, 0.5D, 1.2D, 1.2D, 0.6D,
                (x, z) -> unlocked.contains(x + "," + z)
        );
        assertEquals(0.7D, result.x(), EPSILON);
        assertEquals(1.2D, result.z(), EPSILON);
    }

    @Test
    void handlesNegativeBoundary() {
        MovementCollision.Result result = MovementCollision.resolve(
                0.5D, 0.5D, -0.2D, 0.5D, 0.6D,
                (x, z) -> x == 0 && z == 0
        );
        assertEquals(0.3D, result.x(), EPSILON);
    }
}
