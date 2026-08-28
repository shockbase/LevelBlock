package de.shockbase.levelblock.util;

import de.shockbase.levelblock.session.BlockColumn;

import java.util.ArrayList;
import java.util.List;

public final class MovementPath {

    private static final int MAX_COLUMNS_PER_MOVE = 512;

    private MovementPath() {
    }

    public static List<BlockColumn> traversedColumns(
            double fromX,
            double fromZ,
            double toX,
            double toZ
    ) {
        int x = floor(fromX);
        int z = floor(fromZ);
        int endX = floor(toX);
        int endZ = floor(toZ);
        if (x == endX && z == endZ) {
            return List.of();
        }

        double dx = toX - fromX;
        double dz = toZ - fromZ;
        int stepX = Integer.compare(endX, x);
        int stepZ = Integer.compare(endZ, z);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);
        double tMaxX = initialTMax(fromX, x, dx, stepX);
        double tMaxZ = initialTMax(fromZ, z, dz, stepZ);

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
        return x == endX && z == endZ
                ? result
                : List.of(new BlockColumn(endX, endZ));
    }

    private static double initialTMax(double coordinate, int block, double delta, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return step > 0
                ? ((block + 1.0D) - coordinate) / delta
                : (coordinate - block) / -delta;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
