package de.shockbase.levelblock.session;

public record BlockColumn(int x, int z) {

    public long packed() {
        return pack(x, z);
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static BlockColumn fromPacked(long value) {
        return new BlockColumn((int) (value >> 32), (int) value);
    }
}
