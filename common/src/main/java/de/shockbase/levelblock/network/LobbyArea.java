package de.shockbase.levelblock.network;

public record LobbyArea(String dimensionId, int centerX, int centerZ) {

    public boolean contains(int x, int z) {
        return Math.abs(x - centerX) <= 2 && Math.abs(z - centerZ) <= 2;
    }
}
