package de.shockbase.levelblock;

import de.shockbase.levelblock.boundary.BoundaryRenderer;
import de.shockbase.levelblock.command.LevelBlockCommand;
import de.shockbase.levelblock.listener.MovementListener;
import de.shockbase.levelblock.listener.PlayerStateListener;
import de.shockbase.levelblock.lobby.LobbyManager;
import de.shockbase.levelblock.session.SessionManager;
import de.shockbase.levelblock.storage.SessionStorage;
import de.shockbase.levelblock.util.SafeLocationFinder;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LevelBlockPlugin extends JavaPlugin {

    private SessionStorage sessionStorage;
    private SessionManager sessionManager;
    private LobbyManager lobbyManager;
    private SafeLocationFinder safeLocationFinder;
    private BoundaryRenderer boundaryRenderer;

    @Override
    public void onEnable() {
        this.sessionStorage = new SessionStorage(this);
        this.boundaryRenderer = new BoundaryRenderer(this);
        this.sessionManager = new SessionManager(
                this,
                sessionStorage,
                sessionStorage.loadSessions(),
                boundaryRenderer
        );
        this.safeLocationFinder = new SafeLocationFinder();
        this.lobbyManager = new LobbyManager(this, sessionManager, safeLocationFinder, boundaryRenderer);

        LevelBlockCommand levelBlockCommand = new LevelBlockCommand(sessionManager, lobbyManager, safeLocationFinder);
        PluginCommand command = getCommand("levelblock");
        if (command == null) {
            throw new IllegalStateException("Command 'levelblock' fehlt in plugin.yml");
        }
        command.setExecutor(levelBlockCommand);
        command.setTabCompleter(levelBlockCommand);

        getServer().getPluginManager().registerEvents(
                new MovementListener(sessionManager, safeLocationFinder), this
        );
        getServer().getPluginManager().registerEvents(
                new PlayerStateListener(this, sessionManager, safeLocationFinder, boundaryRenderer, lobbyManager), this
        );

        getServer().getScheduler().runTask(this, () -> getServer().getOnlinePlayers().forEach(player -> {
            var session = sessionManager.getActiveSession(player);
            if (session != null) {
                var progress = sessionManager.getOrCreateWorldProgress(session, player.getLocation());
                boundaryRenderer.ensureVisible(session, progress, player);
            }
        }));

        getLogger().info("LevelBlock geladen: " + sessionManager.getSessions().size() + " gespeicherte Session(s).");
    }

    @Override
    public void onDisable() {
        if (lobbyManager != null) {
            lobbyManager.shutdown();
        }
        if (boundaryRenderer != null) {
            boundaryRenderer.shutdown();
        }
        if (sessionManager != null) {
            sessionManager.flush();
        }
    }
}
