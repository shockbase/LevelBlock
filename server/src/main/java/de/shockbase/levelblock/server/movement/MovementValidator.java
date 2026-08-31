package de.shockbase.levelblock.server.movement;

import de.shockbase.levelblock.server.SafePositionFinder;
import de.shockbase.levelblock.server.ServerDimension;
import de.shockbase.levelblock.server.session.SessionManager;
import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.ExpansionPlan;
import de.shockbase.levelblock.session.ExpansionPlanner;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.WorldProgress;
import de.shockbase.levelblock.util.MovementCollision;
import de.shockbase.levelblock.util.MovementPath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MovementValidator {

    private static final double MAX_MOVEMENT_PER_TICK = 128.0D;

    private final MinecraftServer server;
    private final SessionManager sessions;
    private final SafePositionFinder safePositions;
    private final Map<UUID, AcceptedPosition> accepted = new HashMap<>();

    public MovementValidator(MinecraftServer server, SessionManager sessions, SafePositionFinder safePositions) {
        this.server = server;
        this.sessions = sessions;
        this.safePositions = safePositions;
    }

    public void tick() {
        accepted.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            validate(player);
        }
    }

    private void validate(ServerPlayer player) {
        LevelBlockSession session = sessions.getActiveSession(player);
        if (session == null) {
            accepted.remove(player.getUUID());
            return;
        }

        String dimensionId = ServerDimension.id(player.level());
        WorldProgress progress = sessions.getOrCreateWorldProgress(session, player.level(), player.blockPosition());
        AcceptedPosition previous = accepted.get(player.getUUID());
        if (previous == null || !previous.dimensionId().equals(dimensionId)) {
            enforceCurrentColumn(player, progress);
            accept(player, dimensionId);
            sessions.syncPlayer(player);
            return;
        }

        double dx = player.getX() - previous.x();
        double dz = player.getZ() - previous.z();
        if (dx == 0.0D && dz == 0.0D) {
            return;
        }
        if (Math.abs(dx) > MAX_MOVEMENT_PER_TICK || Math.abs(dz) > MAX_MOVEMENT_PER_TICK) {
            deny(player, previous, progress);
            return;
        }

        List<BlockColumn> traversed = MovementPath.traversedColumns(
                previous.x(), previous.z(), player.getX(), player.getZ()
        );
        ExpansionPlan plan = ExpansionPlanner.plan(progress, traversed);
        if (plan instanceof ExpansionPlan.Rejected) {
            deny(player, previous, progress);
            return;
        }

        ExpansionPlan.Approved approved = (ExpansionPlan.Approved) plan;
        if (approved.cost() == 0) {
            if (!progress.isUnlocked(player.blockPosition().getX(), player.blockPosition().getZ())) {
                deny(player, previous, progress);
            } else {
                accept(player, dimensionId);
            }
            return;
        }
        if (player.experienceLevel < approved.cost()) {
            deny(player, previous, progress);
            return;
        }

        player.giveExperienceLevels(-approved.cost());
        sessions.unlock(session, progress, approved.columnsToUnlock());
        player.level().sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                24, 0.5D, 0.8D, 0.5D, 0.05D
        );
        accept(player, dimensionId);
    }

    private void enforceCurrentColumn(ServerPlayer player, WorldProgress progress) {
        if (progress.isUnlocked(player.blockPosition().getX(), player.blockPosition().getZ())) {
            return;
        }
        Vec3 safe = safePositions.findNearestAllowed(player.level(), player.position(), progress);
        player.teleportTo(safe.x, safe.y, safe.z);
    }

    private void deny(ServerPlayer player, AcceptedPosition previous, WorldProgress progress) {
        MovementCollision.Result resolved = MovementCollision.resolve(
                previous.x(), previous.z(), player.getX(), player.getZ(), player.getBbWidth(), progress::isUnlocked
        );
        player.teleportTo(resolved.x(), player.getY(), resolved.z());
        accept(player, previous.dimensionId());
    }

    private void accept(ServerPlayer player, String dimensionId) {
        accepted.put(player.getUUID(), new AcceptedPosition(
                dimensionId, player.getX(), player.getY(), player.getZ()
        ));
    }

    private record AcceptedPosition(String dimensionId, double x, double y, double z) {
    }
}
