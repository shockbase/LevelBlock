package de.shockbase.levelblock.util;

import java.util.function.BiPredicate;

public final class MovementCollision {

    private static final double PLAYER_RADIUS = 0.3D;
    private static final double CONTACT_EPSILON = 0.0001D;

    private MovementCollision() {
    }

    public static Result resolve(
            double fromX,
            double fromZ,
            double toX,
            double toZ,
            BiPredicate<Integer, Integer> isUnlocked
    ) {
        double resolvedX = resolveX(fromX, toX, fromZ, isUnlocked);
        double resolvedZ = resolveZ(fromZ, toZ, resolvedX, isUnlocked);
        return new Result(resolvedX, resolvedZ);
    }

    private static double resolveX(
            double fromX,
            double toX,
            double fixedZ,
            BiPredicate<Integer, Integer> isUnlocked
    ) {
        if (toX > fromX) {
            int firstEnteredX = floor(fromX) + 1;
            int lastEnteredX = floor(toX + PLAYER_RADIUS - CONTACT_EPSILON);
            for (int x = firstEnteredX; x <= lastEnteredX; x++) {
                if (hasLockedColumnAtX(x, fixedZ, isUnlocked)) {
                    return Math.min(toX, x - PLAYER_RADIUS - CONTACT_EPSILON);
                }
            }
        } else if (toX < fromX) {
            int firstEnteredX = floor(fromX) - 1;
            int lastEnteredX = floor(toX - PLAYER_RADIUS + CONTACT_EPSILON);
            for (int x = firstEnteredX; x >= lastEnteredX; x--) {
                if (hasLockedColumnAtX(x, fixedZ, isUnlocked)) {
                    return Math.max(toX, x + 1.0D + PLAYER_RADIUS + CONTACT_EPSILON);
                }
            }
        }
        return toX;
    }

    private static double resolveZ(
            double fromZ,
            double toZ,
            double fixedX,
            BiPredicate<Integer, Integer> isUnlocked
    ) {
        if (toZ > fromZ) {
            int firstEnteredZ = floor(fromZ) + 1;
            int lastEnteredZ = floor(toZ + PLAYER_RADIUS - CONTACT_EPSILON);
            for (int z = firstEnteredZ; z <= lastEnteredZ; z++) {
                if (hasLockedColumnAtZ(z, fixedX, isUnlocked)) {
                    return Math.min(toZ, z - PLAYER_RADIUS - CONTACT_EPSILON);
                }
            }
        } else if (toZ < fromZ) {
            int firstEnteredZ = floor(fromZ) - 1;
            int lastEnteredZ = floor(toZ - PLAYER_RADIUS + CONTACT_EPSILON);
            for (int z = firstEnteredZ; z >= lastEnteredZ; z--) {
                if (hasLockedColumnAtZ(z, fixedX, isUnlocked)) {
                    return Math.max(toZ, z + 1.0D + PLAYER_RADIUS + CONTACT_EPSILON);
                }
            }
        }
        return toZ;
    }

    private static boolean hasLockedColumnAtX(
            int x,
            double centerZ,
            BiPredicate<Integer, Integer> isUnlocked
    ) {
        int minZ = floor(centerZ - PLAYER_RADIUS + CONTACT_EPSILON);
        int maxZ = floor(centerZ + PLAYER_RADIUS - CONTACT_EPSILON);
        for (int z = minZ; z <= maxZ; z++) {
            if (!isUnlocked.test(x, z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLockedColumnAtZ(
            int z,
            double centerX,
            BiPredicate<Integer, Integer> isUnlocked
    ) {
        int minX = floor(centerX - PLAYER_RADIUS + CONTACT_EPSILON);
        int maxX = floor(centerX + PLAYER_RADIUS - CONTACT_EPSILON);
        for (int x = minX; x <= maxX; x++) {
            if (!isUnlocked.test(x, z)) {
                return true;
            }
        }
        return false;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    public record Result(double x, double z) {
    }
}
