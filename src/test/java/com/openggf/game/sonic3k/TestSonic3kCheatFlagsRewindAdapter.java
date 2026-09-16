package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Sonic3kCheatFlags} models {@code Level_select_flag} /
 * {@code Slow_motion_flag} / {@code Debug_cheat_flag}, which the MHZ pulley
 * lift writes from a button sequence entered during play. The adapter keeps
 * a backward rewind seek from leaving {@code Debug_cheat_flag} set while the
 * pulley's own sequence counter rolls back.
 */
class TestSonic3kCheatFlagsRewindAdapter {

    private Sonic3kCheatFlags flags;
    private Sonic3kCheatFlagsRewindAdapter adapter;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        flags = GameServices.module().getGameService(Sonic3kCheatFlags.class);
        flags.reset();
        adapter = new Sonic3kCheatFlagsRewindAdapter();
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void keyIsStable() {
        assertEquals("s3k-cheat-flags", adapter.key());
    }

    @Test
    void moduleServiceIsRegistered() {
        assertTrue(flags != null, "Sonic3kGameModule must expose Sonic3kCheatFlags as a game service");
    }

    @Test
    void levelSelectWordCoversBothBytes() {
        assertFalse(flags.isLevelSelectWordSet());
        flags.enableLevelSelectAndSlowMotion();
        assertTrue(flags.isLevelSelectEnabled(), "Level_select_flag byte");
        assertTrue(flags.isSlowMotionEnabled(), "Slow_motion_flag byte");
        assertTrue(flags.isLevelSelectWordSet(), "tst.w (Level_select_flag) spans both bytes");
    }

    @Test
    void snapshotRoundTripRestoresPreCheatState() {
        Sonic3kCheatFlags.Snapshot beforeCheat = adapter.capture();

        flags.enableLevelSelectAndSlowMotion();
        flags.enableDebugCheat();
        assertTrue(flags.isDebugCheatEntered());

        adapter.restore(beforeCheat);

        assertFalse(flags.isDebugCheatEntered(),
                "restoring a pre-cheat snapshot must clear Debug_cheat_flag");
        assertFalse(flags.isLevelSelectWordSet(),
                "restoring a pre-cheat snapshot must clear the Level_select_flag word");
    }

    @Test
    void missingSnapshotResetsToPowerOnState() {
        flags.enableDebugCheat();

        adapter.resetForMissingSnapshot();

        assertFalse(flags.isDebugCheatEntered());
    }
}
