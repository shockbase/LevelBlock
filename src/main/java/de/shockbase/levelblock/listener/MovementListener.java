package de.shockbase.levelblock.listener;

import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.ExpansionPlan;
import de.shockbase.levelblock.session.ExpansionPlanner;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.SessionManager;
import de.shockbase.levelblock.session.WorldProgress;
import de.shockbase.levelblock.util.MovementCollision;
import de.shockbase.levelblock.util.MovementPath;
import de.shockbase.levelblock.util.SafeLocationFinder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public final class MovementListener implements Listener {

    private static final long MESSAGE_COOLDOWN_MS = 1000L;

    private final SessionManager sessionManager;
    private final SafeLocationFinder safeLocationFinder;
    private final java.util.Map<UUID, Long> lastDeniedMessage = new java.util.HashMap<>();

    public MovementListener(SessionManager sessionManager, SafeLocationFinder safeLocationFinder) {
        this.sessionManager = sessionManager;
        this.safeLocationFinder = safeLocationFinder;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || to.getWorld() == null) {
            return;
        }
        if (!from.getWorld().getUID().equals(to.getWorld().getUID())) {
            return;
        }
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) {
            return;
        }

        Player player = event.getPlayer();
        LevelBlockSession session = sessionManager.getActiveSession(player);
        if (session == null) {
            return;
        }

        WorldProgress progress = sessionManager.getOrCreateWorldProgress(session, from);
        BlockColumn startColumn = new BlockColumn(from.getBlockX(), from.getBlockZ());
        if (!progress.isUnlocked(startColumn)) {
            event.setTo(safeLocationFinder.findNearestAllowed(from, progress));
            return;
        }

        if (Math.abs(to.getX() - from.getX()) > 128.0D || Math.abs(to.getZ() - from.getZ()) > 128.0D) {
            deny(event, player, from, to, progress, "Diese Bewegung ist zu weit und wurde blockiert.");
            return;
        }

        List<BlockColumn> traversed = MovementPath.traversedColumns(from, to);
        if (traversed.isEmpty()) {
            if (player.getLevel() <= 0) {
                deny(
                        event,
                        player,
                        from,
                        to,
                        progress,
                        "Du brauchst 1 Level fuer die naechste Saeule."
                );
            }
            return;
        }

        ExpansionPlan plan = ExpansionPlanner.plan(progress, traversed);
        if (plan instanceof ExpansionPlan.Rejected) {
            deny(
                    event,
                    player,
                    from,
                    to,
                    progress,
                    "Diese Saeule grenzt nicht an euren freigeschalteten Bereich."
            );
            return;
        }

        List<BlockColumn> toUnlock = ((ExpansionPlan.Approved) plan).columnsToUnlock();
        if (toUnlock.isEmpty()) {
            if (player.getLevel() <= 0) {
                deny(
                        event,
                        player,
                        from,
                        to,
                        progress,
                        "Du brauchst 1 Level fuer die naechste Saeule."
                );
            }
            return;
        }

        int cost = toUnlock.size();
        if (player.getLevel() < cost) {
            deny(
                    event,
                    player,
                    from,
                    to,
                    progress,
                    cost == 1
                            ? "Du brauchst 1 Level fuer die naechste Saeule."
                            : "Du brauchst " + cost + " Level fuer diese Bewegung."
            );
            return;
        }

        player.setLevel(player.getLevel() - cost);
        sessionManager.unlock(session, progress, toUnlock);
        player.getWorld().spawnParticle(
                Particle.INSTANT_EFFECT,
                to.clone().add(0.0D, 1.0D, 0.0D),
                30,
                0.5D,
                1.0D,
                0.5D,
                0.1D,
                new Particle.Spell(Color.fromRGB(64, 255, 128), 1.0F)
        );
        player.sendActionBar(Component.text(
                cost == 1
                        ? "1 Level bezahlt - 1 neue Saeule freigeschaltet."
                        : cost + " Level bezahlt - " + cost + " neue Saeulen freigeschaltet.",
                NamedTextColor.GREEN
        ));
    }

    private void deny(
            PlayerMoveEvent event,
            Player player,
            Location from,
            Location to,
            WorldProgress progress,
            String message
    ) {
        MovementCollision.Result collision = MovementCollision.resolve(
                from.getX(),
                from.getZ(),
                to.getX(),
                to.getZ(),
                progress::isUnlocked
        );
        boolean blockedX = collision.x() != to.getX();
        boolean blockedZ = collision.z() != to.getZ();
        if (!blockedX && !blockedZ) {
            return;
        }

        Location corrected = to.clone();
        corrected.setX(collision.x());
        corrected.setZ(collision.z());
        event.setTo(corrected);

        Vector velocity = player.getVelocity();
        if (blockedX) {
            velocity.setX(0.0D);
        }
        if (blockedZ) {
            velocity.setZ(0.0D);
        }
        player.setVelocity(velocity);

        long now = System.currentTimeMillis();
        long last = lastDeniedMessage.getOrDefault(player.getUniqueId(), 0L);
        if (now - last >= MESSAGE_COOLDOWN_MS) {
            player.sendActionBar(Component.text(message, NamedTextColor.RED));
            lastDeniedMessage.put(player.getUniqueId(), now);
        }
    }
}
