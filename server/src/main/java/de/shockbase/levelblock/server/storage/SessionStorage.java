package de.shockbase.levelblock.server.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.SessionStatus;
import de.shockbase.levelblock.session.WorldProgress;
import de.shockbase.levelblock.storage.SessionRepository;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class SessionStorage implements SessionRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Logger logger;

    public SessionStorage(Path file, Logger logger) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Map<UUID, LevelBlockSession> loadSessions() {
        Map<UUID, LevelBlockSession> sessions = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return sessions;
        }
        try (Reader input = Files.newBufferedReader(file)) {
            JsonArray stored = JsonParser.parseReader(input).getAsJsonObject().getAsJsonArray("sessions");
            if (stored == null) {
                return sessions;
            }
            for (JsonElement element : stored) {
                try {
                    LevelBlockSession session = readSession(element.getAsJsonObject());
                    sessions.put(session.getId(), session);
                } catch (RuntimeException malformed) {
                    logger.error("Eine LevelBlock-Session konnte nicht geladen werden.", malformed);
                }
            }
        } catch (IOException | RuntimeException exception) {
            logger.error("sessions.json konnte nicht gelesen werden.", exception);
        }
        return sessions;
    }

    private LevelBlockSession readSession(JsonObject source) {
        UUID id = UUID.fromString(requiredString(source, "id"));
        UUID ownerId = UUID.fromString(requiredString(source, "ownerId"));
        Set<UUID> members = readUuids(source.getAsJsonArray("members"));
        Set<UUID> invites = readUuids(source.getAsJsonArray("invites"));
        Map<String, WorldProgress> dimensions = new LinkedHashMap<>();
        for (JsonElement element : source.getAsJsonArray("dimensions")) {
            JsonObject dimension = element.getAsJsonObject();
            String dimensionId = requiredString(dimension, "id");
            ArrayList<BlockColumn> unlocked = new ArrayList<>();
            for (JsonElement packed : dimension.getAsJsonArray("unlocked")) {
                unlocked.add(BlockColumn.fromPacked(packed.getAsLong()));
            }
            dimensions.put(dimensionId, new WorldProgress(
                    dimensionId,
                    dimension.get("originX").getAsInt(),
                    dimension.get("originZ").getAsInt(),
                    unlocked
            ));
        }
        return new LevelBlockSession(
                id,
                ownerId,
                requiredString(source, "ownerName"),
                source.get("createdAt").getAsLong(),
                SessionStatus.valueOf(requiredString(source, "status")),
                members,
                invites,
                dimensions
        );
    }

    @Override
    public void saveSessions(Collection<LevelBlockSession> sessions) {
        JsonObject root = new JsonObject();
        JsonArray stored = new JsonArray();
        sessions.stream()
                .sorted(Comparator.comparing(session -> session.getId().toString()))
                .map(this::writeSession)
                .forEach(stored::add);
        root.add("sessions", stored);

        Path parent = file.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "sessions-", ".json.tmp");
            try (Writer output = Files.newBufferedWriter(temporary)) {
                GSON.toJson(root, output);
            }
            replaceAtomically(temporary, file);
            temporary = null;
        } catch (IOException exception) {
            logger.error("sessions.json konnte nicht gespeichert werden.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    logger.warn("Temporäre Session-Datei konnte nicht entfernt werden.", cleanupFailure);
                }
            }
        }
    }

    private JsonObject writeSession(LevelBlockSession session) {
        JsonObject result = new JsonObject();
        result.addProperty("id", session.getId().toString());
        result.addProperty("ownerId", session.getOwnerId().toString());
        result.addProperty("ownerName", session.getOwnerName());
        result.addProperty("createdAt", session.getCreatedAt());
        result.addProperty("status", session.getStatus().name());
        result.add("members", writeUuids(session.getMembers()));
        result.add("invites", writeUuids(session.getInvites()));
        JsonArray dimensions = new JsonArray();
        session.getWorlds().values().stream()
                .sorted(Comparator.comparing(WorldProgress::getDimensionId))
                .map(this::writeDimension)
                .forEach(dimensions::add);
        result.add("dimensions", dimensions);
        return result;
    }

    private JsonObject writeDimension(WorldProgress progress) {
        JsonObject result = new JsonObject();
        result.addProperty("id", progress.getDimensionId());
        result.addProperty("originX", progress.getOriginX());
        result.addProperty("originZ", progress.getOriginZ());
        JsonArray unlocked = new JsonArray();
        progress.getUnlockedColumns().stream()
                .mapToLong(BlockColumn::packed)
                .sorted()
                .forEach(unlocked::add);
        result.add("unlocked", unlocked);
        return result;
    }

    private static Set<UUID> readUuids(JsonArray values) {
        Set<UUID> result = new LinkedHashSet<>();
        if (values != null) {
            values.forEach(value -> result.add(UUID.fromString(value.getAsString())));
        }
        return result;
    }

    private static JsonArray writeUuids(Collection<UUID> values) {
        JsonArray result = new JsonArray();
        values.stream().map(UUID::toString).sorted().forEach(result::add);
        return result;
    }

    private static String requiredString(JsonObject source, String key) {
        JsonElement value = source.get(key);
        if (value == null || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Pflichtfeld fehlt: " + key);
        }
        return value.getAsString();
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
