package de.shockbase.levelblock.session;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldProgressTest {

    @Test
    void createsNineInitialColumnsAroundOrigin() {
        WorldProgress progress = WorldProgress.createInitial("minecraft:overworld", 10, -4);
        assertEquals(9, progress.getUnlockedCount());
        assertTrue(progress.isUnlocked(9, -5));
        assertTrue(progress.isUnlocked(11, -3));
        assertFalse(progress.isUnlocked(12, -4));
    }

    @Test
    void rejectsNullColumnWithoutPartialUnlock() {
        WorldProgress progress = new WorldProgress(
                "minecraft:overworld", 0, 0, List.of(new BlockColumn(0, 0))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> progress.unlockAll(Arrays.asList(new BlockColumn(1, 0), null))
        );
        assertFalse(progress.isUnlocked(1, 0));
    }
}
