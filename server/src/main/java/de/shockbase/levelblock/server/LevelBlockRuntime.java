package de.shockbase.levelblock.server;

import de.shockbase.levelblock.server.lobby.LobbyManager;
import de.shockbase.levelblock.server.movement.MovementValidator;
import de.shockbase.levelblock.server.network.ServerNetwork;
import de.shockbase.levelblock.server.session.SessionManager;
import de.shockbase.levelblock.server.storage.SessionStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class LevelBlockRuntime {

    private final ServerNetwork network;
    private final SessionManager sessions;
    private final LobbyManager lobbies;
    private final MovementValidator movement;

    public LevelBlockRuntime(MinecraftServer server, ServerNetwork network, Logger logger) {
        this.network = network;
        SafePositionFinder safePositions = new SafePositionFinder();
        SessionStorage storage = new SessionStorage(
                FabricLoader.getInstance().getConfigDir().resolve("levelblock").resolve("sessions.json"),
                logger
        );
        this.sessions = new SessionManager(server, storage, network, logger);
        this.lobbies = new LobbyManager(server, sessions, network, safePositions);
        this.movement = new MovementValidator(server, sessions, safePositions);
        network.setInitialSync(player -> {
            sessions.syncPlayer(player);
            lobbies.syncPlayer(player);
        });
    }

    public SessionManager sessions() {
        return sessions;
    }

    public LobbyManager lobbies() {
        return lobbies;
    }

    public void tick(MinecraftServer server) {
        network.tick(server);
        lobbies.tick();
        movement.tick();
        sessions.tick();
    }

    public void close() {
        sessions.flush();
    }
}
