package de.shockbase.levelblock.util;

import de.shockbase.levelblock.session.BlockColumn;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public final class MovementPath {

    private static final int MAX_COLUMNS_PER_MOVE = 512;

    private MovementPath() {
    }

    public static List<BlockColumn> traversedColumns(Location from, Location to) {
        int x = floor(from.getX());
        int z = floor(from.getZ());
        int endX = floor(to.getX());
        int endZ = floor(to.getZ());

        if (x == endX && z == endZ) {
            return List.of();
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        int stepX = Integer.compare(endX, x);
        int stepZ = Integer.compare(endZ, z);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);
        double tMaxX = initialTMax(from.getX(), x, dx, stepX);
        double tMaxZ = initialTMax(from.getZ(), z, dz, stepZ);

        List<BlockColumn> result = new ArrayList<>();
        int guard = 0;
        while ((x != endX || z != endZ) && guard++ < MAX_COLUMNS_PER_MOVE) {
            if (tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxZ < tMaxX) {
                z += stepZ;
                tMaxZ += tDeltaZ;
            } else {
                x += stepX;
                z += stepZ;
                tMaxX += tDeltaX;
                tMaxZ += tDeltaZ;
            }
            result.add(new BlockColumn(x, z));
        }

        if (x != endX || z != endZ) {
            return List.of(new BlockColumn(endX, endZ));
        }
        return result;
    }

    private static double initialTMax(double coordinate, int block, double delta, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        if (step > 0) {
            return ((block + 1.0D) - coordinate) / delta;
        }
        return (coordinate - block) / -delta;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
