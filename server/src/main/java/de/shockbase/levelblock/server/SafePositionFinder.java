package de.shockbase.levelblock.server;

import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.WorldProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class SafePositionFinder {

    public Vec3 findNearestAllowed(ServerLevel level, Vec3 target, WorldProgress progress) {
        if (progress.getUnlockedColumns().isEmpty()) {
            throw new IllegalStateException("Session hat keine freigeschalteten Spalten in " + ServerDimension.id(level));
        }

        BlockColumn nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockColumn column : progress.getUnlockedColumns()) {
            double dx = column.x() + 0.5D - target.x;
            double dz = column.z() + 0.5D - target.z;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = column;
            }
        }
        if (nearest == null) {
            throw new IllegalStateException("Keine freigeschaltete Spalte gefunden.");
        }

        int safeY = findSafeY(level, nearest.x(), nearest.z(), (int) Math.floor(target.y));
        return new Vec3(nearest.x() + 0.5D, safeY, nearest.z() + 0.5D);
    }

    private int findSafeY(ServerLevel level, int x, int z, int preferredY) {
        int minFeetY = level.getMinY() + 1;
        int maxFeetY = level.getMaxY() - 1;
        int clamped = Math.max(minFeetY, Math.min(maxFeetY, preferredY));
        int maxOffset = Math.max(clamped - minFeetY, maxFeetY - clamped);
        for (int offset = 0; offset <= maxOffset; offset++) {
            int up = clamped + offset;
            if (up <= maxFeetY && isSafe(level, x, up, z)) {
                return up;
            }
            if (offset > 0) {
                int down = clamped - offset;
                if (down >= minFeetY && isSafe(level, x, down, z)) {
                    return down;
                }
            }
        }
        return clamped;
    }

    private boolean isSafe(ServerLevel level, int x, int feetY, int z) {
        BlockPos below = new BlockPos(x, feetY - 1, z);
        BlockPos feet = below.above();
        BlockPos head = feet.above();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }
}
