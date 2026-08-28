package de.shockbase.levelblock.client.boundary;

import de.shockbase.levelblock.network.BoundarySyncPayload;
import de.shockbase.levelblock.network.LobbyArea;
import de.shockbase.levelblock.network.LobbySyncPayload;
import de.shockbase.levelblock.session.BlockColumn;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class ClientBoundaryState {

    private static final int MAX_COLLISION_COLUMNS = 4096;
    private static final ClientBoundaryState INSTANCE = new ClientBoundaryState();

    private boolean active;
    private String dimensionId = "";
    private LongSet unlocked = new LongOpenHashSet();
    private List<LobbyArea> lobbies = List.of();
    private long revision;

    private ClientBoundaryState() {
    }

    public static ClientBoundaryState instance() {
        return INSTANCE;
    }

    public void apply(BoundarySyncPayload payload) {
        if (!payload.active()) {
            active = false;
            dimensionId = "";
            unlocked = new LongOpenHashSet();
        } else {
            LongSet columns = new LongOpenHashSet(payload.unlockedColumns().length);
            for (long packed : payload.unlockedColumns()) {
                columns.add(packed);
            }
            active = true;
            dimensionId = payload.dimensionId();
            unlocked = columns;
        }
        revision++;
    }

    public void apply(LobbySyncPayload payload) {
        lobbies = payload.lobbies();
        revision++;
    }

    public void clear() {
        active = false;
        dimensionId = "";
        unlocked = new LongOpenHashSet();
        lobbies = List.of();
        revision++;
    }

    public boolean isActive(Level level) {
        return active && level.dimension().identifier().toString().equals(dimensionId);
    }

    public boolean isUnlocked(int x, int z) {
        return unlocked.contains(BlockColumn.pack(x, z));
    }

    public List<LobbyArea> lobbies() {
        return lobbies;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public long revision() {
        return revision;
    }

    public List<VoxelShape> collisionShapes(Entity source, Level level, AABB sweptBounds) {
        Minecraft minecraft = Minecraft.getInstance();
        if (source != minecraft.player
                || minecraft.player == null
                || minecraft.player.isSpectator()
                || minecraft.player.experienceLevel > 0
                || !isActive(level)) {
            return List.of();
        }

        int minX = (int) Math.floor(sweptBounds.minX) - 1;
        int maxX = (int) Math.floor(sweptBounds.maxX) + 1;
        int minZ = (int) Math.floor(sweptBounds.minZ) - 1;
        int maxZ = (int) Math.floor(sweptBounds.maxZ) + 1;
        long count = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (count > MAX_COLLISION_COLUMNS) {
            return List.of();
        }

        List<VoxelShape> result = new ArrayList<>();
        double minY = level.getMinY() - 16.0D;
        double maxY = level.getMaxY() + 16.0D;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!isUnlocked(x, z)) {
                    result.add(Shapes.create(new AABB(x, minY, z, x + 1.0D, maxY, z + 1.0D)));
                }
            }
        }
        return result;
    }
}
