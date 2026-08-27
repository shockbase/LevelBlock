package de.shockbase.levelblock.util;

import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.WorldProgress;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class SafeLocationFinder {

    public Location findNearestAllowed(Location target, WorldProgress progress) {
        World world = target.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Zielposition hat keine Welt.");
        }
        if (progress.getUnlockedColumns().isEmpty()) {
            throw new IllegalStateException("Session hat keine freigeschalteten Spalten in " + world.getName());
        }

        BlockColumn nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockColumn column : progress.getUnlockedColumns()) {
            double dx = (column.x() + 0.5D) - target.getX();
            double dz = (column.z() + 0.5D) - target.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = column;
            }
        }

        if (nearest == null) {
            throw new IllegalStateException("Keine freigeschaltete Spalte gefunden.");
        }

        int safeY = findSafeY(world, nearest.x(), nearest.z(), target.getBlockY());
        return new Location(
                world,
                nearest.x() + 0.5D,
                safeY,
                nearest.z() + 0.5D,
                target.getYaw(),
                target.getPitch()
        );
    }

    private int findSafeY(World world, int x, int z, int preferredY) {
        int minFeetY = world.getMinHeight() + 1;
        int maxFeetY = world.getMaxHeight() - 2;
        int clamped = Math.max(minFeetY, Math.min(maxFeetY, preferredY));

        int maxOffset = Math.max(clamped - minFeetY, maxFeetY - clamped);
        for (int offset = 0; offset <= maxOffset; offset++) {
            int up = clamped + offset;
            if (up <= maxFeetY && isSafeStandingPosition(world, x, up, z)) {
                return up;
            }
            if (offset > 0) {
                int down = clamped - offset;
                if (down >= minFeetY && isSafeStandingPosition(world, x, down, z)) {
                    return down;
                }
            }
        }

        return clamped;
    }

    private boolean isSafeStandingPosition(World world, int x, int feetY, int z) {
        Block below = world.getBlockAt(x, feetY - 1, z);
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, feetY + 1, z);
        return below.getType().isSolid() && feet.isPassable() && head.isPassable();
    }
}
