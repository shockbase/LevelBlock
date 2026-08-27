package de.shockbase.levelblock.listener;

import de.shockbase.levelblock.boundary.BoundaryRenderer;
import de.shockbase.levelblock.lobby.LobbyManager;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.SessionManager;
import de.shockbase.levelblock.session.WorldProgress;
import de.shockbase.levelblock.util.SafeLocationFinder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerStateListener implements Listener {

    private final JavaPlugin plugin;
    private final SessionManager sessionManager;
    private final SafeLocationFinder safeLocationFinder;
    private final BoundaryRenderer boundaryRenderer;
    private final LobbyManager lobbyManager;

    public PlayerStateListener(
            JavaPlugin plugin,
            SessionManager sessionManager,
            SafeLocationFinder safeLocationFinder,
            BoundaryRenderer boundaryRenderer,
            LobbyManager lobbyManager
    ) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.safeLocationFinder = safeLocationFinder;
        this.boundaryRenderer = boundaryRenderer;
        this.lobbyManager = lobbyManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        LevelBlockSession session = sessionManager.getActiveSession(player);
        if (session == null) {
            return;
        }

        Location target = event.getTo();
        if (target == null || target.getWorld() == null) {
            return;
        }

        WorldProgress progress = sessionManager.getOrCreateWorldProgress(session, target);
        if (!progress.isUnlocked(target.getBlockX(), target.getBlockZ())) {
            event.setTo(safeLocationFinder.findNearestAllowed(target, progress));
            player.sendActionBar(Component.text(
                    "Teleport auf die naechste freigeschaltete Saeule korrigiert.",
                    NamedTextColor.YELLOW
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        LevelBlockSession session = sessionManager.getActiveSession(player);
        if (session == null) {
            return;
        }

        Location target = event.getRespawnLocation();
        WorldProgress progress = sessionManager.getOrCreateWorldProgress(session, target);
        if (!progress.isUnlocked(target.getBlockX(), target.getBlockZ())) {
            event.setRespawnLocation(safeLocationFinder.findNearestAllowed(target, progress));
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> enforceCurrentLocation(player));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> enforceCurrentLocation(player));
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> enforceCurrentLocation(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null
                || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        lobbyManager.syncPlayerBoundary(player, to);
        LevelBlockSession session = sessionManager.getActiveSession(player);
        if (session == null) {
            return;
        }
        WorldProgress progress = sessionManager.getOrCreateWorldProgress(session, to);
        boundaryRenderer.ensureVisible(session, progress, player, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        refreshTerrainAfterBlockChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        refreshTerrainAfterBlockChange(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lobbyManager.hidePlayerBoundary(event.getPlayer());
        LevelBlockSession session = sessionManager.getActiveSession(event.getPlayer());
        if (session != null) {
            boundaryRenderer.hide(session, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevelChange(PlayerLevelChangeEvent event) {
        LevelBlockSession session = sessionManager.getActiveSession(event.getPlayer());
        if (session != null) {
            boundaryRenderer.updateAvailability(session, event.getPlayer(), event.getNewLevel());
        }
    }

    private void enforceCurrentLocation(Player player) {
        if (!player.isOnline()) {
            return;
        }
        lobbyManager.syncPlayerBoundary(player);
        LevelBlockSession session = sessionManager.getActiveSession(player);
        if (session == null) {
            return;
        }

        Location current = player.getLocation();
        WorldProgress progress = sessionManager.getOrCreateWorldProgress(session, current);
        boundaryRenderer.ensureVisible(session, progress, player);
        if (!progress.isUnlocked(current.getBlockX(), current.getBlockZ())) {
            player.teleport(safeLocationFinder.findNearestAllowed(current, progress));
        }
    }

    private void refreshTerrainAfterBlockChange(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            lobbyManager.refreshBoundaries();
            LevelBlockSession session = sessionManager.getActiveSession(player);
            if (session == null) {
                return;
            }
            WorldProgress progress = session.getWorldProgress(player.getWorld().getUID());
            if (progress != null) {
                boundaryRenderer.refresh(session, progress);
            }
        });
    }
}
