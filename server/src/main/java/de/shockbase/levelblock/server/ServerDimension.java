package de.shockbase.levelblock.server;

import net.minecraft.server.level.ServerLevel;

public final class ServerDimension {

    private ServerDimension() {
    }

    public static String id(ServerLevel level) {
        return level.dimension().identifier().toString();
    }
}
