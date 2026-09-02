package de.shockbase.levelblock.client.boundary;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import de.shockbase.levelblock.network.LobbyArea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

public final class ClientBoundaryRenderer implements AutoCloseable {

    public static final int LOBBY_TINT = 0x55FFFF;
    public static final int DEFAULT_TINT = 0xFFAA00;
    public static final int EXPANDABLE_TINT = 0x55FF55;
    public static final int BLOCKED_TINT = 0xFF5555;

    private static final int RENDER_RADIUS = 32;
    private static final double IMMEDIATE_TILE_DISTANCE = 0.8D;
    private static final double IMMEDIATE_TILE_DISTANCE_SQUARED
            = IMMEDIATE_TILE_DISTANCE * IMMEDIATE_TILE_DISTANCE;
    private static final int MAX_TILES = 4096;
    private static final int FLASH_HOLD_TICKS = 4;
    private static final int FLASH_FADE_TICKS = 16;
    private static final int FLASH_TOTAL_TICKS = FLASH_HOLD_TICKS + FLASH_FADE_TICKS;
    private static final float OPACITY = 0.30F;
    private static final float UV_PER_BLOCK = 2.0F;
    private static final long ANIMATION_PERIOD_MILLIS = 3_000L;
    private static final Identifier FORCEFIELD_SHADER = Identifier.fromNamespaceAndPath(
            "levelblock", "core/forcefield_color"
    );
    private static final RenderPipeline FORCEFIELD_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                    .withLocation(Identifier.fromNamespaceAndPath(
                            "levelblock", "pipeline/forcefield"
                    ))
                    .withVertexShader(FORCEFIELD_SHADER)
                    .withFragmentShader(FORCEFIELD_SHADER)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
                    .withCull(false)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withDepthStencilState(new DepthStencilState(
                            CompareOp.GREATER_THAN_OR_EQUAL, true, 3.0F, 3.0F
                    ))
                    .build()
    );

    private final ClientBoundaryState state;
    private final Map<TileKey, RenderedTile> renderedTiles = new HashMap<>();
    private RenderSystem.AutoStorageIndexBuffer indices;
    private ClientLevel renderedLevel;
    private GpuBuffer vertexBuffer;
    private int indexCount;
    private double meshOriginX;
    private double meshOriginY;
    private double meshOriginZ;
    private boolean meshDirty;
    private long renderedRevision = Long.MIN_VALUE;
    private int renderedX = Integer.MIN_VALUE;
    private int renderedY = Integer.MIN_VALUE;
    private int renderedZ = Integer.MIN_VALUE;
    private long observedExpansionRevision = Long.MIN_VALUE;
    private int flashTicksRemaining;
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
        boolean geometryChanged = renderedLevel != minecraft.level
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
        if (renderedLevel != level) {
            return;
        }
        int range = RENDER_RADIUS + 2;
        if (Math.abs((long) pos.getX() - renderedX) <= range
                && Math.abs((long) pos.getZ() - renderedZ) <= range) {
            terrainDirty = true;
        }
    }

    public void clear() {
        renderedTiles.clear();
        discardMesh();
        renderedLevel = null;
        renderedRevision = Long.MIN_VALUE;
        renderedX = Integer.MIN_VALUE;
        renderedY = Integer.MIN_VALUE;
        renderedZ = Integer.MIN_VALUE;
        terrainDirty = false;
    }

    @Override
    public void close() {
        clear();
    }

    public void render(Vec3 cameraPos) {
        if (renderedTiles.isEmpty()) {
            return;
        }
        if (meshDirty) {
            rebuildMesh();
        }
        if (vertexBuffer == null || indexCount == 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        AbstractTexture forcefield = minecraft.getTextureManager().getTexture(
                WorldBorderRenderer.FORCEFIELD_LOCATION
        );
        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
        RenderTarget weatherTarget = minecraft.levelRenderer.weatherTarget();
        GpuTextureView colorTexture = weatherTarget == null
                ? mainTarget.getColorTextureView()
                : weatherTarget.getColorTextureView();
        GpuTextureView depthTexture = weatherTarget == null
                ? mainTarget.getDepthTextureView()
                : weatherTarget.getDepthTextureView();

        float offset = (float) (Util.getMillis() % ANIMATION_PERIOD_MILLIS)
                / ANIMATION_PERIOD_MILLIS;
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(),
                new Vector4f(1.0F),
                new Vector3f(
                        (float) (meshOriginX - cameraPos.x),
                        (float) (meshOriginY - cameraPos.y),
                        (float) (meshOriginZ - cameraPos.z)
                ),
                new Matrix4f().translation(offset, offset, 0.0F)
        );
        if (indices == null) {
            indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        }
        GpuBuffer indexBuffer = indices.getBuffer(indexCount);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "LevelBlock forcefield",
                        colorTexture,
                        Optional.empty(),
                        depthTexture,
                        OptionalDouble.empty()
                )) {
            renderPass.setPipeline(FORCEFIELD_PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setIndexBuffer(indexBuffer, indices.type());
            renderPass.bindTexture(
                    "Sampler0", forcefield.getTextureView(), forcefield.getSampler()
            );
            renderPass.setVertexBuffer(0, vertexBuffer.slice());
            renderPass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
    }

    private void rebuildMesh() {
        int quadCount = 0;
        for (RenderedTile tile : renderedTiles.values()) {
            quadCount += tile.style.quadCount;
        }
        if (quadCount == 0) {
            discardMesh();
            return;
        }

        meshOriginX = renderedX;
        meshOriginY = renderedY;
        meshOriginZ = renderedZ;
        int vertexCount = quadCount * 4;
        int bufferSize = Math.multiplyExact(
                vertexCount, DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize()
        );
        GpuBuffer replacement;
        try (ByteBufferBuilder byteBuffer = ByteBufferBuilder.exactlySized(bufferSize)) {
            BufferBuilder builder = new BufferBuilder(
                    byteBuffer,
                    PrimitiveTopology.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR
            );
            for (RenderedTile tile : renderedTiles.values()) {
                emitTile(builder, tile);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                replacement = RenderSystem.getDevice().createBuffer(
                        () -> "LevelBlock forcefield vertex buffer",
                        GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer()
                );
            }
        }

        if (vertexBuffer != null) {
            vertexBuffer.close();
        }
        vertexBuffer = replacement;
        indexCount = quadCount * 6;
        meshDirty = false;
    }

    private void emitTile(BufferBuilder builder, RenderedTile tile) {
        float bottom = (float) (tile.y - meshOriginY);
        float middle = bottom + 0.5F;
        float top = bottom + 1.0F;
        float v0 = textureStart(tile.y);
        float vm = v0 + UV_PER_BLOCK * 0.5F;
        float v1 = v0 + UV_PER_BLOCK;

        switch (tile.style) {
            case SOLID -> emitQuad(builder, tile, bottom, top, v0, v1, 1.0F, 1.0F);
            case FADE_UP -> emitQuad(builder, tile, bottom, middle, v0, vm, 1.0F, 0.0F);
            case FADE_DOWN -> emitQuad(builder, tile, middle, top, vm, v1, 0.0F, 1.0F);
            case FADE_BOTH -> {
                emitQuad(builder, tile, bottom, middle, v0, vm, 1.0F, 0.0F);
                emitQuad(builder, tile, middle, top, vm, v1, 0.0F, 1.0F);
            }
        }
    }

    private void emitQuad(
            BufferBuilder builder,
            RenderedTile tile,
            float bottom,
            float top,
            float v0,
            float v1,
            float bottomAlpha,
            float topAlpha
    ) {
        float start = (float) (tile.edge.position - horizontalOrigin(tile.edge));
        float end = start + 1.0F;
        float fixed = (float) (tile.edge.fixed - fixedOrigin(tile.edge));
        float u0 = textureStart(tile.edge.position);
        float u1 = u0 + UV_PER_BLOCK;

        if (tile.edge.axis == Axis.X) {
            emitVertex(builder, start, bottom, fixed, u0, v0, tile.tint, bottomAlpha);
            emitVertex(builder, end, bottom, fixed, u1, v0, tile.tint, bottomAlpha);
            emitVertex(builder, end, top, fixed, u1, v1, tile.tint, topAlpha);
            emitVertex(builder, start, top, fixed, u0, v1, tile.tint, topAlpha);
        } else {
            emitVertex(builder, fixed, bottom, start, u0, v0, tile.tint, bottomAlpha);
            emitVertex(builder, fixed, bottom, end, u1, v0, tile.tint, bottomAlpha);
            emitVertex(builder, fixed, top, end, u1, v1, tile.tint, topAlpha);
            emitVertex(builder, fixed, top, start, u0, v1, tile.tint, topAlpha);
        }
    }

    private static void emitVertex(
            BufferBuilder builder,
            float x,
            float y,
            float z,
            float u,
            float v,
            int tint,
            float alpha
    ) {
        builder.addVertex(x, y, z)
                .setUv(u, v)
                .setColor(
                        (tint >> 16) & 0xFF,
                        (tint >> 8) & 0xFF,
                        tint & 0xFF,
                        Math.round(255.0F * OPACITY * alpha)
                );
    }

    private double horizontalOrigin(Edge edge) {
        return edge.axis == Axis.X ? meshOriginX : meshOriginZ;
    }

    private double fixedOrigin(Edge edge) {
        return edge.axis == Axis.X ? meshOriginZ : meshOriginX;
    }

    private static float textureStart(int coordinate) {
        double scaled = coordinate * (double) UV_PER_BLOCK;
        return (float) (scaled - Math.floor(scaled));
    }

    private void discardMesh() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        indexCount = 0;
        meshDirty = false;
    }

    private void rebuild(ClientLevel level, int playerX, int playerY, int playerZ) {
        if (renderedLevel != level) {
            clear();
        }
        boolean originChanged = renderedX != playerX
                || renderedY != playerY
                || renderedZ != playerZ;
        renderedLevel = level;
        Map<TileKey, DesiredTile> desiredTiles = new LinkedHashMap<>();

        if (state.isActive(level)) {
            for (Edge edge : boundaryEdges(playerX, playerZ)) {
                addEdge(level, edge, DEFAULT_TINT, true, desiredTiles);
                if (desiredTiles.size() >= MAX_TILES) {
                    break;
                }
            }
        }
        String currentDimension = level.dimension().identifier().toString();
        for (LobbyArea lobby : state.lobbies()) {
            if (desiredTiles.size() >= MAX_TILES) {
                break;
            }
            if (!lobby.dimensionId().equals(currentDimension)
                    || distanceSquared(lobby.centerX(), lobby.centerZ(), playerX, playerZ)
                    > (RENDER_RADIUS + 4) * (RENDER_RADIUS + 4)) {
                continue;
            }
            for (Edge edge : lobbyEdges(lobby)) {
                addEdge(level, edge, LOBBY_TINT, false, desiredTiles);
                if (desiredTiles.size() >= MAX_TILES) {
                    break;
                }
            }
        }
        reconcile(desiredTiles);
        renderedRevision = state.revision();
        renderedX = playerX;
        renderedY = playerY;
        renderedZ = playerZ;
        if (originChanged && !renderedTiles.isEmpty()) {
            meshDirty = true;
        }
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
        while (y <= maximum && desiredTiles.size() < MAX_TILES) {
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
        for (int y = minimum; y <= maximum && desiredTiles.size() < MAX_TILES; y++) {
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
        if (hasOverhang && desiredTiles.size() < MAX_TILES) {
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
        if (desiredTiles.size() >= MAX_TILES) {
            return;
        }
        TileKey key = new TileKey(edge, y, style, sessionBoundary);
        desiredTiles.putIfAbsent(
                key, new DesiredTile(edge, y, tint, style, sessionBoundary)
        );
    }

    private void reconcile(Map<TileKey, DesiredTile> desiredTiles) {
        boolean changed = false;
        Iterator<Map.Entry<TileKey, RenderedTile>> iterator
                = renderedTiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TileKey, RenderedTile> entry = iterator.next();
            if (desiredTiles.containsKey(entry.getKey())) {
                continue;
            }
            iterator.remove();
            changed = true;
        }

        for (Map.Entry<TileKey, DesiredTile> entry : desiredTiles.entrySet()) {
            RenderedTile existing = renderedTiles.get(entry.getKey());
            if (existing == null) {
                DesiredTile tile = entry.getValue();
                renderedTiles.put(entry.getKey(), new RenderedTile(
                        tile.edge(),
                        tile.y(),
                        tile.style(),
                        tile.sessionBoundary(),
                        tile.tint()
                ));
                changed = true;
            } else if (!existing.sessionBoundary) {
                setTint(existing, entry.getValue().tint());
            }
        }
        if (renderedTiles.isEmpty()) {
            discardMesh();
        } else if (changed) {
            meshDirty = true;
        }
    }

    private void setTint(RenderedTile tile, int tint) {
        if (tile.tint == tint) {
            return;
        }
        tile.tint = tint;
        meshDirty = true;
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
        FADE_UP(1),
        SOLID(1),
        FADE_DOWN(1),
        FADE_BOTH(2);

        private final int quadCount;

        ModelStyle(int quadCount) {
            this.quadCount = quadCount;
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

        private final Edge edge;
        private final int y;
        private final ModelStyle style;
        private final boolean sessionBoundary;
        private int tint;

        private RenderedTile(
                Edge edge,
                int y,
                ModelStyle style,
                boolean sessionBoundary,
                int tint
        ) {
            this.edge = edge;
            this.y = y;
            this.style = style;
            this.sessionBoundary = sessionBoundary;
            this.tint = tint;
        }
    }
}
