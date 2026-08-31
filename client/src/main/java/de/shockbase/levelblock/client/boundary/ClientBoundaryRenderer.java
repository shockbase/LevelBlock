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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientBoundaryRenderer {

    public static final int LOBBY_TINT = 0x55FFFF;
    public static final int DEFAULT_TINT = 0xFFAA00;
    public static final int EXPANDABLE_TINT = 0x55FF55;
    public static final int BLOCKED_TINT = 0xFF5555;

    private static final int RENDER_RADIUS = 32;
    private static final double IMMEDIATE_TILE_DISTANCE = 0.8D;
    private static final double IMMEDIATE_TILE_DISTANCE_SQUARED
            = IMMEDIATE_TILE_DISTANCE * IMMEDIATE_TILE_DISTANCE;
    private static final int MAX_DISPLAYS = 4096;
    private static final int FLASH_HOLD_TICKS = 4;
    private static final int FLASH_FADE_TICKS = 16;
    private static final int FLASH_TOTAL_TICKS = FLASH_HOLD_TICKS + FLASH_FADE_TICKS;

    private final ClientBoundaryState state;
    private final Map<TileKey, RenderedTile> renderedTiles = new HashMap<>();
    private final Map<TintedModel, ItemStack> itemCache = new HashMap<>();
    private ClientLevel displayLevel;
    private long renderedRevision = Long.MIN_VALUE;
    private int renderedX = Integer.MIN_VALUE;
    private int renderedY = Integer.MIN_VALUE;
    private int renderedZ = Integer.MIN_VALUE;
    private long observedExpansionRevision = Long.MIN_VALUE;
    private int flashTicksRemaining;
    private int nextEntityId = -1_000_000;
    private boolean terrainDirty;

    public ClientBoundaryRenderer(ClientBoundaryState state) {
        this.state = state;
    }

    public void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            flashTicksRemaining = 0;
            observedExpansionRevision = state.expansionRevision();
            clear();
            return;
        }
        long expansionRevision = state.expansionRevision();
        if (observedExpansionRevision == Long.MIN_VALUE) {
            observedExpansionRevision = expansionRevision;
        } else if (expansionRevision > observedExpansionRevision) {
            observedExpansionRevision = expansionRevision;
            flashTicksRemaining = FLASH_TOTAL_TICKS;
        } else if (expansionRevision != observedExpansionRevision) {
            observedExpansionRevision = expansionRevision;
        }
        int x = minecraft.player.getBlockX();
        int y = minecraft.player.getBlockY();
        int z = minecraft.player.getBlockZ();
        int experienceLevel = minecraft.player.experienceLevel;
        boolean geometryChanged = displayLevel != minecraft.level
                || renderedRevision != state.revision()
                || terrainDirty
                || renderedX != x
                || renderedY != y
                || renderedZ != z;
        if (geometryChanged) {
            rebuild(minecraft.level, x, y, z);
        }
        updateBoundaryTints(
                minecraft.player.getX(),
                minecraft.player.getY() + 0.9D,
                minecraft.player.getZ(),
                experienceLevel,
                flashTicksRemaining
        );
        if (flashTicksRemaining > 0) {
            flashTicksRemaining--;
        }
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
            for (RenderedTile tile : renderedTiles.values()) {
                displayLevel.removeEntity(tile.display.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        renderedTiles.clear();
        displayLevel = null;
        renderedRevision = Long.MIN_VALUE;
        renderedX = Integer.MIN_VALUE;
        renderedY = Integer.MIN_VALUE;
        renderedZ = Integer.MIN_VALUE;
        terrainDirty = false;
    }

    private void rebuild(ClientLevel level, int playerX, int playerY, int playerZ) {
        if (displayLevel != level) {
            clear();
        }
        displayLevel = level;
        Map<TileKey, DesiredTile> desiredTiles = new LinkedHashMap<>();

        if (state.isActive(level)) {
            for (Edge edge : boundaryEdges(playerX, playerZ)) {
                addEdge(level, edge, DEFAULT_TINT, true, desiredTiles);
                if (desiredTiles.size() >= MAX_DISPLAYS) {
                    break;
                }
            }
        }
        String currentDimension = level.dimension().identifier().toString();
        for (LobbyArea lobby : state.lobbies()) {
            if (desiredTiles.size() >= MAX_DISPLAYS) {
                break;
            }
            if (!lobby.dimensionId().equals(currentDimension)
                    || distanceSquared(lobby.centerX(), lobby.centerZ(), playerX, playerZ)
                    > (RENDER_RADIUS + 4) * (RENDER_RADIUS + 4)) {
                continue;
            }
            for (Edge edge : lobbyEdges(lobby)) {
                addEdge(level, edge, LOBBY_TINT, false, desiredTiles);
                if (desiredTiles.size() >= MAX_DISPLAYS) {
                    break;
                }
            }
        }
        reconcile(level, desiredTiles);
        renderedRevision = state.revision();
        renderedX = playerX;
        renderedY = playerY;
        renderedZ = playerZ;
        terrainDirty = false;
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
            int tint,
            boolean sessionBoundary,
            Map<TileKey, DesiredTile> desiredTiles
    ) {
        int y = level.getMinY() + 1;
        int maximum = level.getMaxY() - 1;
        addAdjacentBlockFills(
                level, edge, tint, sessionBoundary, y, maximum, desiredTiles
        );
        while (y <= maximum && desiredTiles.size() < MAX_DISPLAYS) {
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
            spawnOpenLayer(
                    level, edge, startY, endY, tint, sessionBoundary, desiredTiles
            );
        }
    }

    private void addAdjacentBlockFills(
            ClientLevel level,
            Edge edge,
            int tint,
            boolean sessionBoundary,
            int minimum,
            int maximum,
            Map<TileKey, DesiredTile> desiredTiles
    ) {
        for (int y = minimum; y <= maximum && desiredTiles.size() < MAX_DISPLAYS; y++) {
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
                stageTile(
                        desiredTiles, edge, y, tint, ModelStyle.SOLID, sessionBoundary
                );
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

    private void spawnOpenLayer(
            ClientLevel level,
            Edge edge,
            int startY,
            int endY,
            int tint,
            boolean sessionBoundary,
            Map<TileKey, DesiredTile> desiredTiles
    ) {
        boolean hasGround = hasGround(level, edge, startY);
        boolean hasOverhang = hasOverhang(level, edge, endY);
        if (!hasGround && !hasOverhang) {
            return;
        }
        if (hasGround && hasOverhang && startY == endY) {
            stageTile(
                    desiredTiles, edge, startY, tint, ModelStyle.FADE_BOTH, sessionBoundary
            );
            return;
        }
        if (hasGround) {
            stageTile(
                    desiredTiles, edge, startY, tint, ModelStyle.FADE_UP, sessionBoundary
            );
        }
        if (hasOverhang && desiredTiles.size() < MAX_DISPLAYS) {
            stageTile(
                    desiredTiles, edge, endY, tint, ModelStyle.FADE_DOWN, sessionBoundary
            );
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

    private void updateBoundaryTints(
            double playerX,
            double playerY,
            double playerZ,
            int experienceLevel,
            int flashTicks
    ) {
        RenderedTile closestTile = null;
        if (flashTicks <= 0) {
            double closestDistance = IMMEDIATE_TILE_DISTANCE_SQUARED;
            for (RenderedTile tile : renderedTiles.values()) {
                if (!tile.sessionBoundary) {
                    continue;
                }
                double distance = tileDistanceSquared(tile, playerX, playerY, playerZ);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestTile = tile;
                }
            }
        }

        Edge highlightedEdge = closestTile == null ? null : closestTile.edge;
        int flashTint = flashTicks > 0 ? flashTint(flashTicks) : DEFAULT_TINT;
        for (RenderedTile tile : renderedTiles.values()) {
            if (!tile.sessionBoundary) {
                continue;
            }
            int tint = flashTicks > 0
                    ? flashTint
                    : tile.edge.equals(highlightedEdge)
                    ? experienceLevel > 0 ? EXPANDABLE_TINT : BLOCKED_TINT
                    : DEFAULT_TINT;
            setTint(tile, tint);
        }
    }

    private static double tileDistanceSquared(
            RenderedTile tile,
            double playerX,
            double playerY,
            double playerZ
    ) {
        double verticalDistance = intervalDistance(playerY, tile.y, tile.y + 1.0D);
        double xDistance;
        double zDistance;
        if (tile.edge.axis() == Axis.X) {
            xDistance = intervalDistance(
                    playerX, tile.edge.position(), tile.edge.position() + 1.0D
            );
            zDistance = Math.abs(playerZ - tile.edge.fixed());
        } else {
            xDistance = Math.abs(playerX - tile.edge.fixed());
            zDistance = intervalDistance(
                    playerZ, tile.edge.position(), tile.edge.position() + 1.0D
            );
        }
        return xDistance * xDistance
                + verticalDistance * verticalDistance
                + zDistance * zDistance;
    }

    private static double intervalDistance(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        if (value > maximum) {
            return value - maximum;
        }
        return 0.0D;
    }

    private static int flashTint(int flashTicks) {
        if (flashTicks > FLASH_FADE_TICKS) {
            return EXPANDABLE_TINT;
        }
        float greenAmount = (flashTicks - 1) / (float) (FLASH_FADE_TICKS - 1);
        return interpolateRgb(DEFAULT_TINT, EXPANDABLE_TINT, greenAmount);
    }

    private static int interpolateRgb(int from, int to, float amount) {
        int red = Math.round(
                ((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount
        );
        int green = Math.round(
                ((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount
        );
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return (red << 16) | (green << 8) | blue;
    }

    private static void stageTile(
            Map<TileKey, DesiredTile> desiredTiles,
            Edge edge,
            int y,
            int tint,
            ModelStyle style,
            boolean sessionBoundary
    ) {
        if (desiredTiles.size() >= MAX_DISPLAYS) {
            return;
        }
        TileKey key = new TileKey(edge, y, style, sessionBoundary);
        desiredTiles.putIfAbsent(
                key, new DesiredTile(edge, y, tint, style, sessionBoundary)
        );
    }

    private void reconcile(ClientLevel level, Map<TileKey, DesiredTile> desiredTiles) {
        Iterator<Map.Entry<TileKey, RenderedTile>> iterator
                = renderedTiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TileKey, RenderedTile> entry = iterator.next();
            if (desiredTiles.containsKey(entry.getKey())) {
                continue;
            }
            level.removeEntity(entry.getValue().display.getId(), Entity.RemovalReason.DISCARDED);
            iterator.remove();
        }

        for (Map.Entry<TileKey, DesiredTile> entry : desiredTiles.entrySet()) {
            RenderedTile existing = renderedTiles.get(entry.getKey());
            if (existing == null) {
                renderedTiles.put(entry.getKey(), spawn(level, entry.getValue()));
            } else if (!existing.sessionBoundary) {
                setTint(existing, entry.getValue().tint());
            }
        }
    }

    private RenderedTile spawn(ClientLevel level, DesiredTile tile) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.setId(nextEntityId--);
        double middle = tile.edge().position() + 0.5D;
        Quaternionf rotation = new Quaternionf();
        if (tile.edge().axis() == Axis.X) {
            display.setPos(middle, tile.y() + 0.5D, tile.edge().fixed());
        } else {
            display.setPos(tile.edge().fixed(), tile.y() + 0.5D, middle);
            rotation.rotateY((float) (Math.PI / 2.0D));
        }
        display.setItemStack(item(tile.style(), tile.tint()).copy());
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
        return new RenderedTile(
                display,
                tile.edge(),
                tile.y(),
                tile.style(),
                tile.sessionBoundary(),
                tile.tint()
        );
    }

    private void setTint(RenderedTile tile, int tint) {
        if (tile.tint == tint) {
            return;
        }
        tile.display.setItemStack(item(tile.style, tint).copy());
        tile.tint = tint;
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

    private record TileKey(Edge edge, int y, ModelStyle style, boolean sessionBoundary) {
    }

    private record DesiredTile(
            Edge edge,
            int y,
            int tint,
            ModelStyle style,
            boolean sessionBoundary
    ) {
    }

    private static final class RenderedTile {

        private final Display.ItemDisplay display;
        private final Edge edge;
        private final int y;
        private final ModelStyle style;
        private final boolean sessionBoundary;
        private int tint;

        private RenderedTile(
                Display.ItemDisplay display,
                Edge edge,
                int y,
                ModelStyle style,
                boolean sessionBoundary,
                int tint
        ) {
            this.display = display;
            this.edge = edge;
            this.y = y;
            this.style = style;
            this.sessionBoundary = sessionBoundary;
            this.tint = tint;
        }
    }
}
