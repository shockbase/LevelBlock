package de.shockbase.levelblock.boundary;

import de.shockbase.levelblock.lobby.Lobby;
import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.WorldProgress;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BoundaryRenderer {

    public static final int LOBBY_TINT = 0x20A0FF;
    public static final int DEFAULT_TINT = 0xFF3030;
    public static final int EXPANDABLE_TINT = 0x40FF80;

    private static final int FLASH_DURATION_TICKS = 20;
    private static final int WALL_CLIMB_HEIGHT = 8;
    private static final double EDGE_OFFSET = 0.01D;
    private static final String ENTITY_TAG = "levelblock-boundary";
    private static final ModelVariant FORCEFIELD_VARIANT = new ModelVariant(0, 0);

    private final JavaPlugin plugin;
    private final Map<TintedModel, ItemStack> forcefieldItems = new HashMap<>();
    private final Map<PlayerBoundary, BoundaryView> views = new HashMap<>();
    private final Map<LobbyBoundary, BoundaryView> lobbyViews = new HashMap<>();
    private final Map<BoundaryWorld, BukkitTask> flashTasks = new HashMap<>();

    public BoundaryRenderer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh(LevelBlockSession session, WorldProgress progress) {
        List<PlayerBoundary> staleViews = views.entrySet().stream()
                .filter(entry -> entry.getKey().sessionId().equals(session.getId()))
                .filter(entry -> entry.getValue().worldId().equals(progress.getWorldId()))
                .map(Map.Entry::getKey)
                .toList();
        staleViews.forEach(this::removeView);

        if (!session.isActive()) {
            return;
        }

        session.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.getWorld().getUID().equals(progress.getWorldId()))
                .forEach(player -> ensureVisible(session, progress, player));
    }

    public void ensureVisible(LevelBlockSession session, WorldProgress progress, Player player) {
        ensureVisible(session, progress, player, player.getLocation());
    }

    public void ensureVisible(
            LevelBlockSession session,
            WorldProgress progress,
            Player player,
            Location terrainReference
    ) {
        World world = Bukkit.getWorld(progress.getWorldId());
        if (world == null || !player.getWorld().getUID().equals(progress.getWorldId())) {
            return;
        }

        int terrainLayerY = findTerrainBaseY(
                world,
                terrainReference.getBlockX(),
                terrainReference.getBlockZ(),
                terrainReference.getBlockY()
        );
        PlayerBoundary key = new PlayerBoundary(session.getId(), player.getUniqueId());
        BoundaryView existing = views.get(key);
        if (existing != null
                && existing.worldId().equals(progress.getWorldId())
                && existing.terrainLayerY() == terrainLayerY
                && existing.displays().stream().map(BoundaryDisplay::entity).allMatch(ItemDisplay::isValid)) {
            return;
        }

        removeView(key);
        rebuildForPlayer(session, progress, player, terrainLayerY);
    }

    public void updateAvailability(LevelBlockSession session, Player player, int level) {
        PlayerBoundary key = new PlayerBoundary(session.getId(), player.getUniqueId());
        BoundaryView view = views.get(key);
        if (view == null || flashTasks.containsKey(new BoundaryWorld(session.getId(), view.worldId()))) {
            return;
        }
        setTint(view, level > 0 ? EXPANDABLE_TINT : DEFAULT_TINT);
    }

    public void flashUnlock(LevelBlockSession session, WorldProgress progress) {
        BoundaryWorld key = new BoundaryWorld(session.getId(), progress.getWorldId());
        BukkitTask previous = flashTasks.remove(key);
        if (previous != null) {
            previous.cancel();
        }

        BukkitRunnable runnable = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                float progressValue = tick / (float) FLASH_DURATION_TICKS;
                views.forEach((viewKey, view) -> {
                    if (!viewKey.sessionId().equals(session.getId())
                            || !view.worldId().equals(progress.getWorldId())) {
                        return;
                    }
                    Player player = Bukkit.getPlayer(viewKey.playerId());
                    int targetTint = player != null && player.getLevel() > 0
                            ? EXPANDABLE_TINT
                            : DEFAULT_TINT;
                    setTint(view, blend(EXPANDABLE_TINT, targetTint, progressValue));
                });

                if (tick >= FLASH_DURATION_TICKS) {
                    flashTasks.remove(key);
                    cancel();
                    return;
                }
                tick++;
            }
        };
        flashTasks.put(key, runnable.runTaskTimer(plugin, 0L, 1L));
    }

    public void showLobby(Lobby lobby, Player viewer) {
        showLobby(lobby, viewer, viewer.getLocation());
    }

    public void showLobby(Lobby lobby, Player viewer, Location terrainReference) {
        LobbyBoundary key = new LobbyBoundary(lobby.getOwnerId(), viewer.getUniqueId());
        if (!viewer.getWorld().getUID().equals(lobby.getWorldId())) {
            removeLobbyView(key);
            return;
        }

        World world = Bukkit.getWorld(lobby.getWorldId());
        if (world == null) {
            return;
        }

        int terrainLayerY = findTerrainBaseY(
                world,
                terrainReference.getBlockX(),
                terrainReference.getBlockZ(),
                terrainReference.getBlockY()
        );
        BoundaryView existing = lobbyViews.get(key);
        if (existing != null
                && existing.worldId().equals(lobby.getWorldId())
                && existing.terrainLayerY() == terrainLayerY
                && existing.displays().stream().map(BoundaryDisplay::entity).allMatch(ItemDisplay::isValid)) {
            return;
        }

        removeLobbyView(key);

        List<BoundaryDisplay> created = new ArrayList<>();
        int minX = lobby.getCenterX() - 2;
        int maxX = lobby.getCenterX() + 2;
        int minZ = lobby.getCenterZ() - 2;
        int maxZ = lobby.getCenterZ() + 2;
        for (int x = minX; x <= maxX; x++) {
            addTerrainDisplays(
                    created,
                    world,
                    new EdgeLine(Axis.X, minZ, -1),
                    x,
                    terrainLayerY,
                    viewer,
                    LOBBY_TINT
            );
            addTerrainDisplays(
                    created,
                    world,
                    new EdgeLine(Axis.X, maxZ + 1, 1),
                    x,
                    terrainLayerY,
                    viewer,
                    LOBBY_TINT
            );
        }
        for (int z = minZ; z <= maxZ; z++) {
            addTerrainDisplays(
                    created,
                    world,
                    new EdgeLine(Axis.Z, minX, -1),
                    z,
                    terrainLayerY,
                    viewer,
                    LOBBY_TINT
            );
            addTerrainDisplays(
                    created,
                    world,
                    new EdgeLine(Axis.Z, maxX + 1, 1),
                    z,
                    terrainLayerY,
                    viewer,
                    LOBBY_TINT
            );
        }
        lobbyViews.put(key, new BoundaryView(lobby.getWorldId(), terrainLayerY, created));
    }

    public void hideLobby(Lobby lobby, Player viewer) {
        removeLobbyView(new LobbyBoundary(lobby.getOwnerId(), viewer.getUniqueId()));
    }

    public void hideLobbyViewer(Player viewer) {
        List<LobbyBoundary> keys = lobbyViews.keySet().stream()
                .filter(key -> key.viewerId().equals(viewer.getUniqueId()))
                .toList();
        keys.forEach(this::removeLobbyView);
    }

    public void removeLobby(Lobby lobby) {
        List<LobbyBoundary> keys = lobbyViews.keySet().stream()
                .filter(key -> key.ownerId().equals(lobby.getOwnerId()))
                .toList();
        keys.forEach(this::removeLobbyView);
    }

    public void hide(LevelBlockSession session, Player player) {
        removeView(new PlayerBoundary(session.getId(), player.getUniqueId()));
    }

    public void removeSession(LevelBlockSession session) {
        List<PlayerBoundary> keys = views.keySet().stream()
                .filter(key -> key.sessionId().equals(session.getId()))
                .toList();
        keys.forEach(this::removeView);

        List<BoundaryWorld> flashKeys = flashTasks.keySet().stream()
                .filter(key -> key.sessionId().equals(session.getId()))
                .toList();
        flashKeys.forEach(key -> flashTasks.remove(key).cancel());
    }

    public void shutdown() {
        views.values().forEach(this::removeDisplays);
        lobbyViews.values().forEach(this::removeDisplays);
        flashTasks.values().forEach(BukkitTask::cancel);
        views.clear();
        lobbyViews.clear();
        flashTasks.clear();
        forcefieldItems.clear();
    }

    private void rebuildForPlayer(
            LevelBlockSession session,
            WorldProgress progress,
            Player player,
            int terrainLayerY
    ) {
        World world = Bukkit.getWorld(progress.getWorldId());
        if (world == null || !player.getWorld().getUID().equals(progress.getWorldId())) {
            return;
        }

        int tint = player.getLevel() > 0 ? EXPANDABLE_TINT : DEFAULT_TINT;
        List<BoundaryDisplay> created = new ArrayList<>();
        Map<EdgeLine, Set<Integer>> units = collectBoundaryUnits(progress);
        for (Map.Entry<EdgeLine, Set<Integer>> entry : units.entrySet()) {
            for (int position : entry.getValue()) {
                addTerrainDisplays(
                        created,
                        world,
                        entry.getKey(),
                        position,
                        terrainLayerY,
                        player,
                        tint
                );
            }
        }

        PlayerBoundary key = new PlayerBoundary(session.getId(), player.getUniqueId());
        views.put(key, new BoundaryView(progress.getWorldId(), terrainLayerY, created));
    }

    private Map<EdgeLine, Set<Integer>> collectBoundaryUnits(WorldProgress progress) {
        Map<EdgeLine, Set<Integer>> units = new HashMap<>();
        for (BlockColumn column : progress.getUnlockedColumns()) {
            int x = column.x();
            int z = column.z();
            if (!progress.isUnlocked(x, z - 1)) {
                addUnit(units, new EdgeLine(Axis.X, z, -1), x);
            }
            if (!progress.isUnlocked(x, z + 1)) {
                addUnit(units, new EdgeLine(Axis.X, z + 1, 1), x);
            }
            if (!progress.isUnlocked(x - 1, z)) {
                addUnit(units, new EdgeLine(Axis.Z, x, -1), z);
            }
            if (!progress.isUnlocked(x + 1, z)) {
                addUnit(units, new EdgeLine(Axis.Z, x + 1, 1), z);
            }
        }
        return units;
    }

    private void addTerrainDisplays(
            List<BoundaryDisplay> displays,
            World world,
            EdgeLine line,
            int position,
            int terrainLayerY,
            Player viewer,
            int tint
    ) {
        BoundaryCells cells = boundaryCells(line, position);
        int baseY = findTerrainBaseY(world, cells.insideX(), cells.insideZ(), terrainLayerY);
        displays.add(spawnDisplay(world, line, position, baseY, viewer, tint));

        for (int y = baseY + 1; y <= baseY + WALL_CLIMB_HEIGHT; y++) {
            if (!isExposedWall(world, cells, y)) {
                break;
            }
            displays.add(spawnDisplay(world, line, position, y, viewer, tint));
        }
    }

    private int findTerrainBaseY(World world, int x, int z, int referenceY) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int clampedReferenceY = Math.max(minY + 1, Math.min(maxY - 1, referenceY));

        if (isBoundaryOpen(world, x, clampedReferenceY, z)) {
            for (int y = clampedReferenceY; y > minY; y--) {
                if (isBoundaryOpen(world, x, y, z)
                        && !isBoundaryOpen(world, x, y - 1, z)) {
                    return y;
                }
            }
        } else {
            for (int y = clampedReferenceY + 1; y < maxY; y++) {
                if (isBoundaryOpen(world, x, y, z)
                        && !isBoundaryOpen(world, x, y - 1, z)) {
                    return y;
                }
            }
        }

        return clampedReferenceY;
    }

    private boolean isBoundaryOpen(World world, int x, int y, int z) {
        var block = world.getBlockAt(x, y, z);
        return block.isPassable() && !block.isLiquid();
    }

    private boolean isExposedWall(World world, BoundaryCells cells, int y) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }

        return isBoundaryOpen(world, cells.insideX(), y, cells.insideZ())
                && !isBoundaryOpen(world, cells.outsideX(), y, cells.outsideZ());
    }

    private BoundaryCells boundaryCells(EdgeLine line, int position) {
        if (line.axis() == Axis.X) {
            int insideZ = line.fixedCoordinate() - Math.max(0, line.outwardDirection());
            return new BoundaryCells(
                    position,
                    insideZ,
                    position,
                    insideZ + line.outwardDirection()
            );
        }

        int insideX = line.fixedCoordinate() - Math.max(0, line.outwardDirection());
        return new BoundaryCells(
                insideX,
                position,
                insideX + line.outwardDirection(),
                position
        );
    }

    private void addUnit(Map<EdgeLine, Set<Integer>> units, EdgeLine line, int position) {
        units.computeIfAbsent(line, ignored -> new HashSet<>()).add(position);
    }

    private BoundaryDisplay spawnDisplay(
            World world,
            EdgeLine line,
            int position,
            int baseY,
            Player viewer,
            int tint
    ) {
        double middle = position + 0.5D;
        Location location;
        Quaternionf rotation = new Quaternionf();
        if (line.axis() == Axis.X) {
            location = new Location(
                    world,
                    middle,
                    baseY + 0.5D,
                    line.fixedCoordinate() - line.outwardDirection() * EDGE_OFFSET
            );
        } else {
            location = new Location(
                    world,
                    line.fixedCoordinate() - line.outwardDirection() * EDGE_OFFSET,
                    baseY + 0.5D,
                    middle
            );
            rotation.rotateY((float) (Math.PI / 2.0D));
        }

        ModelVariant variant = FORCEFIELD_VARIANT;
        ItemDisplay display = world.spawn(location, ItemDisplay.class, spawned -> {
            spawned.setItemStack(forcefieldItem(variant, tint));
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setTransformation(new Transformation(
                    new Vector3f(),
                    rotation,
                    new Vector3f(1.0F),
                    new Quaternionf()
            ));
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setDisplayWidth(1.0F);
            spawned.setDisplayHeight(1.0F);
            spawned.setViewRange(64.0F);
            spawned.setShadowRadius(0.0F);
            spawned.setShadowStrength(0.0F);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(false);
            spawned.addScoreboardTag(ENTITY_TAG);
        });
        viewer.showEntity(plugin, display);
        return new BoundaryDisplay(display, variant);
    }

    private void setTint(BoundaryView view, int tint) {
        for (BoundaryDisplay display : view.displays()) {
            if (display.entity().isValid()) {
                display.entity().setItemStack(forcefieldItem(display.variant(), tint));
            }
        }
    }

    private ItemStack forcefieldItem(ModelVariant variant, int tint) {
        TintedModel key = new TintedModel(variant, tint);
        return forcefieldItems.computeIfAbsent(key, ignored -> createForcefieldItem(variant, tint)).clone();
    }

    private void removeView(PlayerBoundary key) {
        BoundaryView removed = views.remove(key);
        if (removed != null) {
            removeDisplays(removed);
        }
    }

    private void removeLobbyView(LobbyBoundary key) {
        BoundaryView removed = lobbyViews.remove(key);
        if (removed != null) {
            removeDisplays(removed);
        }
    }

    private void removeDisplays(BoundaryView view) {
        view.displays().stream().map(BoundaryDisplay::entity).forEach(ItemDisplay::remove);
    }

    private ItemStack createForcefieldItem(ModelVariant variant, int tint) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("levelblock", variant.modelName()));
        var customModelData = meta.getCustomModelDataComponent();
        customModelData.setColors(List.of(Color.fromRGB(tint)));
        meta.setCustomModelDataComponent(customModelData);
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    private int blend(int from, int to, float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * clamped);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * clamped);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
        return (red << 16) | (green << 8) | blue;
    }

    private enum Axis {
        X,
        Z
    }

    private record ModelVariant(int uPhase, int vPhase) {
        public String modelName() {
            return "forcefield_u" + uPhase + "_v" + vPhase;
        }
    }

    private record TintedModel(ModelVariant variant, int tint) {
    }

    private record EdgeLine(Axis axis, int fixedCoordinate, int outwardDirection) {
    }

    private record BoundaryCells(int insideX, int insideZ, int outsideX, int outsideZ) {
    }

    private record BoundaryDisplay(ItemDisplay entity, ModelVariant variant) {
    }

    private record PlayerBoundary(UUID sessionId, UUID playerId) {
    }

    private record LobbyBoundary(UUID ownerId, UUID viewerId) {
    }

    private record BoundaryWorld(UUID sessionId, UUID worldId) {
    }

    private record BoundaryView(UUID worldId, int terrainLayerY, List<BoundaryDisplay> displays) {
    }
}
