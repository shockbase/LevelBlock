package de.shockbase.levelblock.lobby;

import de.shockbase.levelblock.boundary.BoundaryRenderer;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.SessionManager;
import de.shockbase.levelblock.session.WorldProgress;
import de.shockbase.levelblock.util.SafeLocationFinder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LobbyManager {

    private final JavaPlugin plugin;
    private final SessionManager sessionManager;
    private final SafeLocationFinder safeLocationFinder;
    private final BoundaryRenderer boundaryRenderer;
    private final Map<UUID, Lobby> lobbies = new HashMap<>();
    private final Map<UUID, BukkitTask> countdownTasks = new HashMap<>();

    public LobbyManager(
            JavaPlugin plugin,
            SessionManager sessionManager,
            SafeLocationFinder safeLocationFinder,
            BoundaryRenderer boundaryRenderer
    ) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.safeLocationFinder = safeLocationFinder;
        this.boundaryRenderer = boundaryRenderer;
    }

    public Lobby createLobby(Player owner) {
        Lobby existing = lobbies.get(owner.getUniqueId());
        if (existing != null && existing.isStarting()) {
            throw new IllegalStateException("Der Countdown dieser Lobby laeuft bereits.");
        }
        if (existing != null) {
            boundaryRenderer.removeLobby(existing);
        }
        Lobby lobby = new Lobby(owner);
        lobbies.put(owner.getUniqueId(), lobby);
        syncAllPlayers();
        return lobby;
    }

    public Lobby getLobby(UUID ownerId) {
        return lobbies.get(ownerId);
    }

    public void startCountdown(Player owner) {
        Lobby lobby = lobbies.get(owner.getUniqueId());
        if (lobby == null) {
            throw new IllegalStateException("Du hast keine Lobby. Nutze zuerst /levelblock lobby.");
        }
        if (lobby.isStarting()) {
            throw new IllegalStateException("Der Countdown laeuft bereits.");
        }
        if (sessionManager.getActiveSession(owner) != null) {
            throw new IllegalStateException("Du bist bereits in einer aktiven LevelBlock-Session.");
        }
        if (!lobby.contains(owner)) {
            throw new IllegalStateException("Du musst dich zum Starten in deinem 5x5-Lobbybereich befinden.");
        }

        Location sessionStart = owner.getLocation().clone();
        lobby.setStarting(true);

        BukkitRunnable runnable = new BukkitRunnable() {
            private int count = 5;

            @Override
            public void run() {
                syncAllPlayers();
                if (!owner.isOnline() || !lobbies.containsKey(owner.getUniqueId())) {
                    lobby.setStarting(false);
                    countdownTasks.remove(owner.getUniqueId());
                    cancel();
                    return;
                }

                if (count > 0) {
                    showTitle(getPlayersInLobby(lobby), Component.text(Integer.toString(count), NamedTextColor.GOLD));
                    count--;
                    return;
                }

                List<Player> candidates = getPlayersInLobby(lobby);
                if (!candidates.contains(owner)) {
                    candidates.add(owner);
                }

                List<Player> participants = new ArrayList<>();
                for (Player candidate : candidates) {
                    if (sessionManager.getActiveSession(candidate) == null) {
                        participants.add(candidate);
                    } else if (!candidate.getUniqueId().equals(owner.getUniqueId())) {
                        candidate.sendMessage(Component.text(
                                "Du wurdest nicht aufgenommen, weil du bereits in einer aktiven Session bist.",
                                NamedTextColor.RED
                        ));
                    }
                }

                LevelBlockSession session;
                try {
                    session = sessionManager.createSession(owner, participants, sessionStart);
                } catch (RuntimeException exception) {
                    lobby.setStarting(false);
                    countdownTasks.remove(owner.getUniqueId());
                    owner.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
                    cancel();
                    return;
                }

                WorldProgress initialProgress = session.getWorldProgress(sessionStart.getWorld().getUID());
                for (Player participant : participants) {
                    showTitle(List.of(participant), Component.text("LEVELBLOCK", NamedTextColor.GREEN));
                    if (participant.getWorld().getUID().equals(initialProgress.getWorldId())
                            && !initialProgress.isUnlocked(participant.getLocation().getBlockX(), participant.getLocation().getBlockZ())) {
                        participant.teleport(safeLocationFinder.findNearestAllowed(participant.getLocation(), initialProgress));
                    }
                    participant.sendMessage(Component.text(
                            "LevelBlock-Session gestartet: " + session.getId(),
                            NamedTextColor.GREEN
                    ));
                }

                boundaryRenderer.removeLobby(lobby);
                lobbies.remove(owner.getUniqueId());
                countdownTasks.remove(owner.getUniqueId());
                cancel();
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, 0L, 20L);
        countdownTasks.put(owner.getUniqueId(), task);
    }

    public List<Player> getPlayersInLobby(Lobby lobby) {
        List<Player> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (lobby.contains(player)) {
                players.add(player);
            }
        }
        return players;
    }

    public void syncPlayerBoundary(Player player) {
        syncPlayerBoundary(player, player.getLocation());
    }

    public void syncPlayerBoundary(Player player, Location terrainReference) {
        for (Lobby lobby : lobbies.values()) {
            if (player.getWorld().getUID().equals(lobby.getWorldId())) {
                boundaryRenderer.showLobby(lobby, player, terrainReference);
            } else {
                boundaryRenderer.hideLobby(lobby, player);
            }
        }
    }

    public void refreshBoundaries() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boundaryRenderer.hideLobbyViewer(player);
            syncPlayerBoundary(player);
        }
    }

    public void hidePlayerBoundary(Player player) {
        boundaryRenderer.hideLobbyViewer(player);
    }

    private void syncAllPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            syncPlayerBoundary(player);
        }
    }

    private void showTitle(List<Player> players, Component titleText) {
        Title title = Title.title(
                titleText,
                Component.empty(),
                Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(800), Duration.ofMillis(150))
        );
        for (Player player : players) {
            player.showTitle(title);
        }
    }

    public void shutdown() {
        for (BukkitTask task : countdownTasks.values()) {
            task.cancel();
        }
        countdownTasks.clear();
        lobbies.values().forEach(boundaryRenderer::removeLobby);
        lobbies.clear();
    }
}
