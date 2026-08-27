package de.shockbase.levelblock.storage;

import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.SessionStatus;
import de.shockbase.levelblock.session.WorldProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsACompleteSession() throws IOException {
        Path file = temporaryDirectory.resolve("data").resolve("sessions.yml");
        SessionStorage storage = storage(file);
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        WorldProgress progress = new WorldProgress(
                worldId,
                "test-world",
                -3,
                7,
                List.of(new BlockColumn(-3, 7), new BlockColumn(-2, 7))
        );
        LevelBlockSession original = new LevelBlockSession(
                UUID.randomUUID(),
                owner,
                "OwnerName",
                123456789L,
                SessionStatus.ACTIVE,
                Set.of(owner, member),
                Set.of(invited),
                Map.of(worldId, progress)
        );

        storage.saveSessions(List.of(original));
        LevelBlockSession loaded = storage(file).loadSessions().get(original.getId());

        assertTrue(Files.isRegularFile(file));
        assertEquals(SessionStorage.CURRENT_SCHEMA_VERSION + "", firstSchemaValue(file));
        assertEquals(original.getOwnerId(), loaded.getOwnerId());
        assertEquals(original.getOwnerName(), loaded.getOwnerName());
        assertEquals(original.getCreatedAt(), loaded.getCreatedAt());
        assertEquals(original.getMembers(), loaded.getMembers());
        assertEquals(original.getInvites(), loaded.getInvites());
        assertEquals(progress.getUnlockedColumns(), loaded.getWorldProgress(worldId).getUnlockedColumns());
        try (var files = Files.list(file.getParent())) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    void refusesToOverwriteANewerSchema() throws IOException {
        Path file = temporaryDirectory.resolve("sessions.yml");
        String futureData = "schema-version: 999\nsessions: {}\n";
        Files.writeString(file, futureData);
        SessionStorage storage = storage(file);

        assertTrue(storage.loadSessions().isEmpty());
        storage.saveSessions(List.of());

        assertEquals(futureData, Files.readString(file));
    }

    @Test
    void returnsAnEmptyMapWhenNoFileExists() {
        SessionStorage storage = storage(temporaryDirectory.resolve("missing.yml"));

        assertFalse(Files.exists(temporaryDirectory.resolve("missing.yml")));
        assertTrue(storage.loadSessions().isEmpty());
    }

    private SessionStorage storage(Path file) {
        return new SessionStorage(file, Logger.getLogger("LevelBlockTest"));
    }

    private String firstSchemaValue(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .filter(line -> line.startsWith("schema-version:"))
                .findFirst()
                .orElseThrow()
                .substring("schema-version:".length())
                .trim();
    }
}
