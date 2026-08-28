package de.shockbase.levelblock.util;

import java.util.function.BiPredicate;

public final class MovementCollision {

    private static final double COLLISION_EPSILON = 1.0E-7D;

    private MovementCollision() {
    }

    public static Result resolve(
            double fromX,
            double fromZ,
            double toX,
            double toZ,
            double entityWidth,
            BiPredicate<Integer, Integer> isUnlocked
    ) {
        if (!Double.isFinite(entityWidth) || entityWidth <= 0.0D) {
            throw new IllegalArgumentException("entityWidth must be finite and positive");
        }

        double radius = entityWidth * 0.5D;
        double movementX = toX - fromX;
        double movementZ = toZ - fromZ;
        Bounds bounds = new Bounds(fromX - radius, fromZ - radius, fromX + radius, fromZ + radius);
        double resolvedX;
        double resolvedZ;
        if (Math.abs(movementX) < Math.abs(movementZ)) {
            resolvedZ = collideZ(bounds, movementZ, isUnlocked);
            bounds = bounds.move(0.0D, resolvedZ);
            resolvedX = collideX(bounds, movementX, isUnlocked);
        } else {
            resolvedX = collideX(bounds, movementX, isUnlocked);
            bounds = bounds.move(resolvedX, 0.0D);
            resolvedZ = collideZ(bounds, movementZ, isUnlocked);
        }
        return new Result(fromX + resolvedX, fromZ + resolvedZ);
    }

    private static double collideX(Bounds bounds, double movement, BiPredicate<Integer, Integer> isUnlocked) {
        if (Math.abs(movement) < COLLISION_EPSILON) {
            return 0.0D;
        }
        int minX = floor(Math.min(bounds.minX(), bounds.minX() + movement)) - 1;
        int maxX = floor(Math.max(bounds.maxX(), bounds.maxX() + movement)) + 1;
        int minZ = floor(bounds.minZ() + COLLISION_EPSILON);
        int maxZ = floor(bounds.maxZ() - COLLISION_EPSILON);
        double resolved = movement;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (isUnlocked.test(x, z)) {
                    continue;
                }
                double distance = movement > 0.0D ? x - bounds.maxX() : x + 1.0D - bounds.minX();
                if (movement > 0.0D && distance >= -COLLISION_EPSILON && distance < resolved
                        || movement < 0.0D && distance <= COLLISION_EPSILON && distance > resolved) {
                    resolved = distance;
                }
            }
        }
        return resolved;
    }

    private static double collideZ(Bounds bounds, double movement, BiPredicate<Integer, Integer> isUnlocked) {
        if (Math.abs(movement) < COLLISION_EPSILON) {
            return 0.0D;
        }
        int minX = floor(bounds.minX() + COLLISION_EPSILON);
        int maxX = floor(bounds.maxX() - COLLISION_EPSILON);
        int minZ = floor(Math.min(bounds.minZ(), bounds.minZ() + movement)) - 1;
        int maxZ = floor(Math.max(bounds.maxZ(), bounds.maxZ() + movement)) + 1;
        double resolved = movement;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (isUnlocked.test(x, z)) {
                    continue;
                }
                double distance = movement > 0.0D ? z - bounds.maxZ() : z + 1.0D - bounds.minZ();
                if (movement > 0.0D && distance >= -COLLISION_EPSILON && distance < resolved
                        || movement < 0.0D && distance <= COLLISION_EPSILON && distance > resolved) {
                    resolved = distance;
                }
            }
        }
        return resolved;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private record Bounds(double minX, double minZ, double maxX, double maxZ) {
        private Bounds move(double x, double z) {
            return new Bounds(minX + x, minZ + z, maxX + x, maxZ + z);
        }
    }

    public record Result(double x, double z) {
    }
}
