package de.shockbase.levelblock.server.storage;

import de.shockbase.levelblock.session.BlockColumn;
import de.shockbase.levelblock.session.LevelBlockSession;
import de.shockbase.levelblock.session.WorldProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsFreshFabricSessionFormat() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        LevelBlockSession session = LevelBlockSession.create(
                owner,
                "Owner",
                List.of(owner, member),
                "minecraft:overworld",
                12,
                -7
        );
        session.addInvite(invited);
        WorldProgress nether = session.createWorldProgress("minecraft:the_nether", 3, 4);
        nether.unlock(new BlockColumn(5, 4));

        Path file = temporaryDirectory.resolve("sessions.json");
        SessionStorage storage = new SessionStorage(file, LoggerFactory.getLogger("test"));
        storage.saveSessions(List.of(session));
        Map<UUID, LevelBlockSession> loaded = storage.loadSessions();

        LevelBlockSession copy = loaded.get(session.getId());
        assertEquals(owner, copy.getOwnerId());
        assertEquals(session.getMembers(), copy.getMembers());
        assertEquals(session.getInvites(), copy.getInvites());
        assertTrue(copy.getWorldProgress("minecraft:the_nether").isUnlocked(5, 4));
        assertTrue(Files.readString(file).contains("\"dimensions\""));
    }
}
