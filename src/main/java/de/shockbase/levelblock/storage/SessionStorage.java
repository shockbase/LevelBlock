package de.shockbase.levelblock.storage;

import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.SessionStatus;
import de.shockbase.levelblock.session.WorldProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** YAML repository with schema protection and atomic file replacement. */
public final class SessionStorage implements SessionRepository {

    static final int CURRENT_SCHEMA_VERSION = 1;

    private final Path file;
    private final Logger logger;
    private boolean writesEnabled = true;

    public SessionStorage(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("sessions.yml"), plugin.getLogger());
    }

    public SessionStorage(Path file, Logger logger) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Map<UUID, LevelBlockSession> loadSessions() {
        Map<UUID, LevelBlockSession> sessions = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return sessions;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        int schemaVersion = config.getInt("schema-version", 0);
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            writesEnabled = false;
            logger.severe(
                    "sessions.yml verwendet Schema " + schemaVersion
                            + ", unterstuetzt wird nur bis " + CURRENT_SCHEMA_VERSION
                            + ". Speichern ist zum Schutz der Datei deaktiviert."
            );
            return sessions;
        }

        ConfigurationSection root = config.getConfigurationSection("sessions");
        if (root == null) {
            return sessions;
        }

        for (String key : root.getKeys(false)) {
            try {
                UUID sessionId = UUID.fromString(key);
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                UUID ownerId = UUID.fromString(requireString(section, "owner"));
                String ownerName = section.getString("owner-name", ownerId.toString());
                long createdAt = section.getLong("created-at", System.currentTimeMillis());
                SessionStatus status = SessionStatus.valueOf(
                        section.getString("status", SessionStatus.STOPPED.name())
                );

                Set<UUID> members = parseUuidSet(section.getStringList("members"), "members", key);
                Set<UUID> invites = parseUuidSet(section.getStringList("invites"), "invites", key);
                Map<UUID, WorldProgress> worlds = loadWorlds(section.getConfigurationSection("worlds"));

                LevelBlockSession session = new LevelBlockSession(
                        sessionId,
                        ownerId,
                        ownerName,
                        createdAt,
                        status,
                        members,
                        invites,
                        worlds
                );
                sessions.put(sessionId, session);
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Session '" + key + "' konnte nicht geladen werden.", exception);
            }
        }

        return sessions;
    }

    private Map<UUID, WorldProgress> loadWorlds(ConfigurationSection section) {
        Map<UUID, WorldProgress> worlds = new HashMap<>();
        if (section == null) {
            return worlds;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID worldId = UUID.fromString(key);
                ConfigurationSection worldSection = section.getConfigurationSection(key);
                if (worldSection == null) {
                    continue;
                }

                String worldName = worldSection.getString("name", key);
                int originX = worldSection.getInt("origin-x");
                int originZ = worldSection.getInt("origin-z");
                List<BlockColumn> unlocked = new ArrayList<>();
                for (String serialized : worldSection.getStringList("unlocked")) {
                    try {
                        unlocked.add(BlockColumn.parse(serialized));
                    } catch (RuntimeException malformedColumn) {
                        logger.warning("Ungueltige Spalte in Welt " + worldName + ": " + serialized);
                    }
                }

                worlds.put(worldId, new WorldProgress(worldId, worldName, originX, originZ, unlocked));
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Weltfortschritt '" + key + "' konnte nicht geladen werden.", exception);
            }
        }
        return worlds;
    }

    @Override
    public void saveSessions(Collection<LevelBlockSession> sessions) {
        Objects.requireNonNull(sessions, "sessions");
        if (!writesEnabled) {
            logger.severe("sessions.yml wurde wegen einer neueren Schema-Version nicht ueberschrieben.");
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("schema-version", CURRENT_SCHEMA_VERSION);

        sessions.stream()
                .sorted(Comparator.comparing(session -> session.getId().toString()))
                .forEach(session -> writeSession(config, session));

        Path parent = file.getParent();
        Path temporaryFile = null;
        try {
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, "sessions-", ".yml.tmp");
            config.save(temporaryFile.toFile());
            replaceAtomically(temporaryFile, file);
            temporaryFile = null;
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "sessions.yml konnte nicht gespeichert werden.", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupFailure) {
                    logger.log(Level.WARNING, "Temporaere Session-Datei konnte nicht entfernt werden.", cleanupFailure);
                }
            }
        }
    }

    private void writeSession(YamlConfiguration config, LevelBlockSession session) {
        String base = "sessions." + session.getId();
        config.set(base + ".owner", session.getOwnerId().toString());
        config.set(base + ".owner-name", session.getOwnerName());
        config.set(base + ".created-at", session.getCreatedAt());
        config.set(base + ".status", session.getStatus().name());
        config.set(base + ".members", toStrings(session.getMembers()));
        config.set(base + ".invites", toStrings(session.getInvites()));

        session.getWorlds().values().stream()
                .sorted(Comparator.comparing(progress -> progress.getWorldId().toString()))
                .forEach(progress -> writeWorld(config, base, progress));
    }

    private void writeWorld(YamlConfiguration config, String sessionBase, WorldProgress progress) {
        String worldBase = sessionBase + ".worlds." + progress.getWorldId();
        config.set(worldBase + ".name", progress.getWorldName());
        config.set(worldBase + ".origin-x", progress.getOriginX());
        config.set(worldBase + ".origin-z", progress.getOriginZ());
        config.set(
                worldBase + ".unlocked",
                progress.getUnlockedColumns().stream()
                        .map(BlockColumn::serialize)
                        .sorted()
                        .toList()
        );
    }

    private void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Set<UUID> parseUuidSet(List<String> values, String field, String sessionId) {
        Set<UUID> result = new HashSet<>();
        for (String value : values) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException exception) {
                logger.warning(
                        "Ungueltige UUID in Session " + sessionId + " (" + field + "): " + value
                );
            }
        }
        return result;
    }

    private static String requireString(ConfigurationSection section, String path) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Pflichtfeld fehlt: " + path);
        }
        return value;
    }

    private static List<String> toStrings(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).sorted().toList();
    }
}
