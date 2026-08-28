package de.shockbase.levelblock.client.boundary;

import com.mojang.math.Transformation;
import de.shockbase.levelblock.network.LobbyArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientBoundaryRenderer {

    public static final int LOBBY_TINT = 0x20A0FF;
    public static final int BLOCKED_TINT = 0xFF3030;
    public static final int EXPANDABLE_TINT = 0x40FF80;

    private static final int RENDER_RADIUS = 32;
    private static final int MAX_DISPLAYS = 4096;

    private final ClientBoundaryState state;
    private final List<Integer> displayIds = new ArrayList<>();
    private final Map<TintedModel, ItemStack> itemCache = new HashMap<>();
    private ClientLevel displayLevel;
    private long renderedRevision = Long.MIN_VALUE;
    private int renderedX = Integer.MIN_VALUE;
    private int renderedZ = Integer.MIN_VALUE;
    private int renderedLevel = Integer.MIN_VALUE;
    private int nextEntityId = -1_000_000;
    private boolean terrainDirty;

    public ClientBoundaryRenderer(ClientBoundaryState state) {
        this.state = state;
    }

    public void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }
        int x = minecraft.player.getBlockX();
        int z = minecraft.player.getBlockZ();
        int experienceLevel = minecraft.player.experienceLevel;
        if (displayLevel == minecraft.level
                && renderedRevision == state.revision()
                && !terrainDirty
                && Math.abs(renderedX - x) < 4
                && Math.abs(renderedZ - z) < 4
                && (renderedLevel > 0) == (experienceLevel > 0)) {
            return;
        }
        rebuild(minecraft.level, x, z, experienceLevel);
    }

    public void terrainChanged(ClientLevel level, BlockPos pos) {
        if (displayLevel != level) {
            return;
        }
        int range = RENDER_RADIUS + 2;
        if (Math.abs((long) pos.getX() - renderedX) <= range
                && Math.abs((long) pos.getZ() - renderedZ) <= range) {
            terrainDirty = true;
        }
    }

    public void clear() {
        if (displayLevel != null) {
            for (int id : displayIds) {
                displayLevel.removeEntity(id, Entity.RemovalReason.DISCARDED);
            }
        }
        displayIds.clear();
        displayLevel = null;
        renderedRevision = Long.MIN_VALUE;
        terrainDirty = false;
    }

    private void rebuild(ClientLevel level, int playerX, int playerZ, int experienceLevel) {
        clear();
        displayLevel = level;
        renderedRevision = state.revision();
        renderedX = playerX;
        renderedZ = playerZ;
        renderedLevel = experienceLevel;

        if (state.isActive(level)) {
            int tint = experienceLevel > 0 ? EXPANDABLE_TINT : BLOCKED_TINT;
            for (Edge edge : boundaryEdges(playerX, playerZ)) {
                addEdge(level, edge, tint);
                if (displayIds.size() >= MAX_DISPLAYS) {
                    return;
                }
            }
        }
        String currentDimension = level.dimension().identifier().toString();
        for (LobbyArea lobby : state.lobbies()) {
            if (!lobby.dimensionId().equals(currentDimension)
                    || distanceSquared(lobby.centerX(), lobby.centerZ(), playerX, playerZ)
                    > (RENDER_RADIUS + 4) * (RENDER_RADIUS + 4)) {
                continue;
            }
            for (Edge edge : lobbyEdges(lobby)) {
                addEdge(level, edge, LOBBY_TINT);
                if (displayIds.size() >= MAX_DISPLAYS) {
                    return;
                }
            }
        }
    }

    private Set<Edge> boundaryEdges(int playerX, int playerZ) {
        Set<Edge> edges = new HashSet<>();
        int radiusSquared = RENDER_RADIUS * RENDER_RADIUS;
        for (int x = playerX - RENDER_RADIUS; x <= playerX + RENDER_RADIUS; x++) {
            for (int z = playerZ - RENDER_RADIUS; z <= playerZ + RENDER_RADIUS; z++) {
                if (distanceSquared(x, z, playerX, playerZ) > radiusSquared || !state.isUnlocked(x, z)) {
                    continue;
                }
                if (!state.isUnlocked(x, z - 1)) {
                    edges.add(new Edge(Axis.X, z, x, -1, x, z));
                }
                if (!state.isUnlocked(x, z + 1)) {
                    edges.add(new Edge(Axis.X, z + 1, x, 1, x, z));
                }
                if (!state.isUnlocked(x - 1, z)) {
                    edges.add(new Edge(Axis.Z, x, z, -1, x, z));
                }
                if (!state.isUnlocked(x + 1, z)) {
                    edges.add(new Edge(Axis.Z, x + 1, z, 1, x, z));
                }
            }
        }
        return edges;
    }

    private static List<Edge> lobbyEdges(LobbyArea lobby) {
        List<Edge> edges = new ArrayList<>(20);
        int minX = lobby.centerX() - 2;
        int maxX = lobby.centerX() + 2;
        int minZ = lobby.centerZ() - 2;
        int maxZ = lobby.centerZ() + 2;
        for (int x = minX; x <= maxX; x++) {
            edges.add(new Edge(Axis.X, minZ, x, -1, x, minZ));
            edges.add(new Edge(Axis.X, maxZ + 1, x, 1, x, maxZ));
        }
        for (int z = minZ; z <= maxZ; z++) {
            edges.add(new Edge(Axis.Z, minX, z, -1, minX, z));
            edges.add(new Edge(Axis.Z, maxX + 1, z, 1, maxX, z));
        }
        return edges;
    }

    private void addEdge(
            ClientLevel level,
            Edge edge,
            int tint
    ) {
        int y = level.getMinY() + 1;
        int maximum = level.getMaxY() - 1;
        addAdjacentBlockFills(level, edge, tint, y, maximum);
        while (y <= maximum && displayIds.size() < MAX_DISPLAYS) {
            while (y <= maximum && !isBoundaryOpen(level, edge, y)) {
                y++;
            }
            if (y > maximum) {
                break;
            }
            int startY = y;
            while (y <= maximum && isBoundaryOpen(level, edge, y)) {
                y++;
            }
            int endY = y - 1;
            spawnOpenLayer(level, edge, startY, endY, tint);
        }
    }

    private void addAdjacentBlockFills(
            ClientLevel level,
            Edge edge,
            int tint,
            int minimum,
            int maximum
    ) {
        for (int y = minimum; y <= maximum && displayIds.size() < MAX_DISPLAYS; y++) {
            boolean insideOpen = isOpen(level, edge.insideX(), y, edge.insideZ());
            boolean outsideOpen = isOpen(level, edge.outsideX(), y, edge.outsideZ());
            if (insideOpen == outsideOpen) {
                continue;
            }
            boolean blockOutside = insideOpen;
            int blockX = blockOutside ? edge.outsideX() : edge.insideX();
            int blockZ = blockOutside ? edge.outsideZ() : edge.insideZ();
            Direction visibleFace = blockOutside
                    ? edge.worldDirection().getOpposite()
                    : edge.worldDirection();
            if (isRenderableBlockFace(level, blockX, y, blockZ, visibleFace)) {
                spawn(level, edge, y, tint, ModelStyle.SOLID);
            }
        }
    }

    private static boolean isRenderableBlockFace(
            ClientLevel level,
            int x,
            int y,
            int z,
            Direction visibleFace
    ) {
        BlockPos pos = new BlockPos(x, y, z);
        var block = level.getBlockState(pos);
        return !block.is(BlockTags.LEAVES)
                && !block.is(BlockTags.LOGS)
                && block.isFaceSturdy(level, pos, visibleFace);
    }

    private void spawnOpenLayer(ClientLevel level, Edge edge, int startY, int endY, int tint) {
        boolean hasGround = hasGround(level, edge, startY);
        boolean hasOverhang = hasOverhang(level, edge, endY);
        if (!hasGround && !hasOverhang) {
            return;
        }
        if (hasGround && hasOverhang && startY == endY) {
            spawn(level, edge, startY, tint, ModelStyle.FADE_BOTH);
            return;
        }
        if (hasGround) {
            spawn(level, edge, startY, tint, ModelStyle.FADE_UP);
        }
        if (hasOverhang && displayIds.size() < MAX_DISPLAYS) {
            spawn(level, edge, endY, tint, ModelStyle.FADE_DOWN);
        }
    }

    private static boolean isBoundaryOpen(ClientLevel level, Edge edge, int y) {
        return isOpen(level, edge.insideX(), y, edge.insideZ())
                && isOpen(level, edge.outsideX(), y, edge.outsideZ());
    }

    private static boolean hasGround(ClientLevel level, Edge edge, int y) {
        return isGround(level, edge.insideX(), y, edge.insideZ())
                || isGround(level, edge.outsideX(), y, edge.outsideZ());
    }

    private static boolean isGround(ClientLevel level, int x, int y, int z) {
        BlockPos below = new BlockPos(x, y - 1, z);
        var floor = level.getBlockState(below);
        return !floor.is(BlockTags.LEAVES)
                && !floor.is(BlockTags.LOGS)
                && floor.isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean hasOverhang(ClientLevel level, Edge edge, int y) {
        int aboveY = y + 1;
        return isOverhang(level, edge.insideX(), aboveY, edge.insideZ())
                || isOverhang(level, edge.outsideX(), aboveY, edge.outsideZ());
    }

    private static boolean isOverhang(ClientLevel level, int x, int y, int z) {
        if (y >= level.getMaxY()) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        var block = level.getBlockState(pos);
        return !block.is(BlockTags.LEAVES)
                && !block.is(BlockTags.LOGS)
                && !block.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isOpen(ClientLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    private void spawn(ClientLevel level, Edge edge, int y, int tint, ModelStyle style) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.setId(nextEntityId--);
        double middle = edge.position() + 0.5D;
        Quaternionf rotation = new Quaternionf();
        if (edge.axis() == Axis.X) {
            display.setPos(middle, y + 0.5D, edge.fixed());
        } else {
            display.setPos(edge.fixed(), y + 0.5D, middle);
            rotation.rotateY((float) (Math.PI / 2.0D));
        }
        display.setItemStack(item(style, tint).copy());
        display.setItemTransform(ItemDisplayContext.NONE);
        display.setTransformation(new Transformation(
                new Vector3f(), rotation, new Vector3f(1.0F), new Quaternionf()
        ));
        display.setBrightnessOverride(Brightness.FULL_BRIGHT);
        display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        display.setWidth(1.0F);
        display.setHeight(1.0F);
        display.setViewRange(64.0F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        level.addEntity(display);
        displayIds.add(display.getId());
    }

    private ItemStack item(ModelStyle style, int tint) {
        return itemCache.computeIfAbsent(new TintedModel(style, tint), ignored -> {
            ItemStack result = new ItemStack(Items.PAPER);
            result.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("levelblock", style.model));
            result.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    List.of(), List.of(), List.of(), List.of(tint)
            ));
            return result;
        });
    }

    private static int distanceSquared(int x, int z, int otherX, int otherZ) {
        int dx = x - otherX;
        int dz = z - otherZ;
        return dx * dx + dz * dz;
    }

    private enum Axis {
        X,
        Z
    }

    private enum ModelStyle {
        FADE_UP("forcefield_u0_v0"),
        SOLID("forcefield_solid_u0_v0"),
        FADE_DOWN("forcefield_fade_down_u0_v0"),
        FADE_BOTH("forcefield_fade_both_u0_v0");

        private final String model;

        ModelStyle(String model) {
            this.model = model;
        }
    }

    private record Edge(Axis axis, int fixed, int position, int direction, int insideX, int insideZ) {

        private Direction worldDirection() {
            if (axis == Axis.X) {
                return direction < 0 ? Direction.NORTH : Direction.SOUTH;
            }
            return direction < 0 ? Direction.WEST : Direction.EAST;
        }

        private int outsideX() {
            return axis == Axis.Z ? insideX + direction : insideX;
        }

        private int outsideZ() {
            return axis == Axis.X ? insideZ + direction : insideZ;
        }
    }

    private record TintedModel(ModelStyle style, int tint) {
    }
}
