package de.shockbase.levelblock.session;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelBlockSessionTest {

    @Test
    void ownerIsAlwaysAMemberAndNeverAnInvite() {
        UUID owner = UUID.randomUUID();
        LevelBlockSession session = session(owner, Set.of(), Set.of(owner));

        assertTrue(session.isOwner(owner));
        assertTrue(session.isMember(owner));
        assertFalse(session.isInvited(owner));
    }

    @Test
    void joiningConsumesTheInvitation() {
        UUID owner = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        LevelBlockSession session = session(owner, Set.of(owner), Set.of(player));

        session.addMember(player);

        assertTrue(session.isMember(player));
        assertFalse(session.isInvited(player));
    }

    @Test
    void ownerCannotBeRemoved() {
        UUID owner = UUID.randomUUID();
        LevelBlockSession session = session(owner, Set.of(owner), Set.of());

        session.removeMember(owner);

        assertTrue(session.isMember(owner));
    }

    @Test
    void stoppingClearsPendingInvitations() {
        UUID owner = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        LevelBlockSession session = session(owner, Set.of(owner), Set.of(invited));

        session.stop();

        assertFalse(session.isActive());
        assertFalse(session.isInvited(invited));
    }

    @Test
    void collectionViewsCannotMutateTheSession() {
        UUID owner = UUID.randomUUID();
        LevelBlockSession session = session(owner, Set.of(owner), Set.of());

        assertThrows(UnsupportedOperationException.class, () -> session.getMembers().clear());
        assertThrows(UnsupportedOperationException.class, () -> session.getWorlds().clear());
    }

    private static LevelBlockSession session(UUID owner, Set<UUID> members, Set<UUID> invites) {
        UUID worldId = UUID.randomUUID();
        WorldProgress progress = new WorldProgress(
                worldId,
                "world",
                0,
                0,
                List.of(new BlockColumn(0, 0))
        );
        return new LevelBlockSession(
                UUID.randomUUID(),
                owner,
                "Owner",
                1L,
                SessionStatus.ACTIVE,
                members,
                invites,
                Map.of(worldId, progress)
        );
    }
}
