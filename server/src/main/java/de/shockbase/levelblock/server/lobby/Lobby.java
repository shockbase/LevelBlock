package de.shockbase.levelblock.server.lobby;

import de.shockbase.levelblock.network.LobbyArea;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class Lobby {

    private final UUID ownerId;
    private final String dimensionId;
    private final int centerX;
    private final int centerZ;
    private int countdown = -1;
    private int nextCountdownTick;

    public Lobby(UUID ownerId, String dimensionId, int centerX, int centerZ) {
        this.ownerId = ownerId;
        this.dimensionId = dimensionId;
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public boolean isStarting() {
        return countdown >= 0;
    }

    public void start(int currentTick) {
        countdown = 5;
        nextCountdownTick = currentTick;
    }

    public boolean countdownDue(int currentTick) {
        return isStarting() && currentTick >= nextCountdownTick;
    }

    public int consumeCountdownStep() {
        int value = countdown--;
        nextCountdownTick += 20;
        return value;
    }

    public boolean contains(ServerPlayer player) {
        return player.level().dimension().identifier().toString().equals(dimensionId)
                && Math.abs(player.blockPosition().getX() - centerX) <= 2
                && Math.abs(player.blockPosition().getZ() - centerZ) <= 2;
    }

    public LobbyArea area() {
        return new LobbyArea(dimensionId, centerX, centerZ);
    }
}
