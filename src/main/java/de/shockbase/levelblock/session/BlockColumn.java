package de.shockbase.levelblock.session;

import java.util.Objects;

public record BlockColumn(int x, int z) {

    public String serialize() {
        return x + "," + z;
    }

    public static BlockColumn parse(String value) {
        Objects.requireNonNull(value, "value");
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Ungueltige X/Z-Spalte: " + value);
        }
        try {
            return new BlockColumn(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim())
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Ungueltige X/Z-Spalte: " + value, exception);
        }
    }
}
