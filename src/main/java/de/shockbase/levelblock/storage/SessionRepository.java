package de.shockbase.levelblock.storage;

import de.shockbase.levelblock.session.LevelBlockSession;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Persistence boundary for LevelBlock sessions. */
public interface SessionRepository {

    Map<UUID, LevelBlockSession> loadSessions();

    void saveSessions(Collection<LevelBlockSession> sessions);
}
