package com.openggf.game.soniccd;

import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.soniccd.objects.shared.SharedSwitchFlags;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code switchFlags} (Level/_Definitions.inc:336) as the module owns it: one game service that the switches and
 * doors share, cleared by every level start (LevelStart's camera-RAM clear, Level/Main.asm:210-216) and captured
 * by rewind. ROM-free.
 */
class TestCdSwitchFlagsService {
    @Test
    void moduleServesOneSwitchFlagsOwnerRegisteredForRewind() {
        SonicCdGameModule module = new SonicCdGameModule();
        SharedSwitchFlags flags = module.getGameService(SharedSwitchFlags.class);
        assertNotNull(flags);
        assertSame(flags, module.getGameService(SharedSwitchFlags.class));
        assertTrue(module.rewindAdapters().stream().anyMatch(adapter -> adapter == flags));
    }

    @Test
    void rewindRestoresTheCapturedBytes() {
        SharedSwitchFlags flags = new SharedSwitchFlags();
        flags.set(3, 0xC0);
        SharedSwitchFlags.Snapshot snapshot = flags.capture();
        flags.set(3, 0x20);                       // a later press does not reach the snapshot
        flags.set(5, 0x80);
        flags.restore(snapshot);
        assertEquals(0xC0, flags.get(3));
        assertEquals(0, flags.get(5));
        flags.resetForMissingSnapshot();
        assertEquals(0, flags.get(3));
    }

    @Test
    void everyLevelLoadClearsTheFlagsFirst() {
        SonicCdGameModule module = new SonicCdGameModule();
        SharedSwitchFlags flags = module.getGameService(SharedSwitchFlags.class);
        flags.set(1, 0xE0);
        List<InitStep> steps = module.getLevelInitProfile().levelLoadSteps(new LevelLoadContext());
        assertEquals("ClearCdSwitchFlags", steps.getFirst().name());
        steps.getFirst().execute();
        assertEquals(0, flags.get(1));
    }
}
