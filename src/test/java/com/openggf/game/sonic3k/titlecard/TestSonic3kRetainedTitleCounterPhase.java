package com.openggf.game.sonic3k.titlecard;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kRetainedTitleCounterPhase {

    @Test
    void retainedOwnerSwitchesFromHistoryProjectionToPostResetCpuPhase() throws Exception {
        Sonic3kTitleCardManager manager = new Sonic3kTitleCardManager();
        setBoolean(manager, "inLevelMode", true);
        setBoolean(manager, "retainedResultsHeldLevelCounterOwned", true);
        setBoolean(manager, "resetLevelGamestateOnInLevelDisplay", true);

        assertTrue(manager.projectsPreResetRetainedResultsSpriteCadence());
        assertEquals(-1, manager.retainedResultsHeldLevelCounterCpuPhase());

        setBoolean(manager, "resetLevelGamestateOnInLevelDisplay", false);

        assertFalse(manager.projectsPreResetRetainedResultsSpriteCadence());
        assertEquals(1, manager.retainedResultsHeldLevelCounterCpuPhase(),
                "the held zero counter is increment-visible to player slots before the later title owner runs");
    }

    private static void setBoolean(Sonic3kTitleCardManager manager, String name, boolean value)
            throws ReflectiveOperationException {
        Field field = Sonic3kTitleCardManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(manager, value);
    }
}
