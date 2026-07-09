package com.openggf.game.sonic2.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies {@link Sonic2SpecialStageManager#captureComparisonState()} assembles a
 * read-only snapshot of the fields a trace replay harness compares against a
 * recorded ROM trace. Constructed ROM-free, mirroring
 * {@link Sonic2SpecialStageTeamSetupTest}'s package-private ctor pattern.
 */
class Sonic2SpecialStageComparisonStateTest {

    @TempDir
    Path tempDir;

    @Test
    void capturesDefaultStateWithNoPlayersSetUp() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);

        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager(
                new Sonic2SpecialStageSpriteDebug(), config, null);

        Sonic2SpecialStageComparisonState state = manager.captureComparisonState();

        assertNotNull(state, "captureComparisonState() should never return null");
        assertFalse(state.finished(), "Freshly constructed manager should not be finished");
        assertEquals(12, state.speedFactor(), "Default speed factor should be 12 (track animator not yet initialized)");
        assertNull(state.sonic(), "Sonic sub-record should be null before setupPlayers() runs");
        assertNull(state.tails(), "Tails sub-record should be null before setupPlayers() runs");
    }

    @Test
    void capturesPlayerSubRecordsWhenPlayersArePresent() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager(
                new Sonic2SpecialStageSpriteDebug(), config, null);
        manager.setupPlayersForTest();

        Sonic2SpecialStageComparisonState state = manager.captureComparisonState();

        assertNotNull(state.sonic(), "Sonic sub-record should be present once setupPlayers() spawns Sonic");
        assertNotNull(state.tails(), "Tails sub-record should be present once setupPlayers() spawns Tails");

        Sonic2SpecialStagePlayer sonicPlayer = manager.getSonicPlayer();
        assertEquals(sonicPlayer.getSSXPos(), state.sonic().ssX());
        assertEquals(sonicPlayer.getSSYPos(), state.sonic().ssY());
        assertEquals(sonicPlayer.getSSZPos(), state.sonic().ssZ());
        assertEquals(sonicPlayer.getAngle(), state.sonic().angle());
        assertEquals(sonicPlayer.getRoutine().name(), state.sonic().routine());
        assertEquals(sonicPlayer.isHurt() ? 2 : 0, state.sonic().routineSecondary());
        assertEquals(sonicPlayer.getAnim(), state.sonic().anim());
        assertEquals(sonicPlayer.getAnimFrame(), state.sonic().animFrame());
    }

    @Test
    void capturesManagerLevelFields() {
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager();

        Sonic2SpecialStageComparisonState state = manager.captureComparisonState();

        assertEquals(manager.getRingsCollected(), state.combinedRings());
        assertEquals(manager.isFinished(), state.finished());
    }
}
