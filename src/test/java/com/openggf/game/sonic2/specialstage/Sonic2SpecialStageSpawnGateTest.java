package com.openggf.game.sonic2.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Sonic2SpecialStageSpawnGateTest {

    private Rom rom;
    private Sonic2SpecialStageManager manager;

    @BeforeEach
    void bootSpecialStage() throws Exception {
        Path romPath = Path.of("s2.gen");
        assumeTrue(Files.isRegularFile(romPath), "s2.gen ROM required for spawn-gate tests");

        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();

        rom = new Rom();
        assertTrue(rom.open(romPath.toAbsolutePath().toString()), "s2.gen should open");
        TestEnvironment.configureRomFixture(rom);
        GraphicsManager.getInstance().initHeadless();

        GameServices.configuration()
                .setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration()
                .setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

        Sonic2SpecialStageProvider provider = new Sonic2SpecialStageProvider();
        provider.initializeStage(0);
        provider.setLagCompensation(0);
        manager = provider.getManager();
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
        if (rom != null) {
            rom.close();
        }
    }

    @Test
    void exposesPlayersOnFirstComparisonAfterRomObjectCreationTick() {
        assertEquals(2, manager.getPlayers().size(),
                "players stay constructed while their ROM object slots are absent");
        assertTrue(manager.getPlayers().stream().noneMatch(Sonic2SpecialStagePlayer::isSpawned));

        for (int frame = 0; frame < Sonic2SpecialStageIntro.PRE_ROLL_FRAMES; frame++) {
            manager.update();

            Sonic2SpecialStageComparisonState state = manager.captureComparisonState();
            assertNull(state.sonic(), "Sonic must be absent after suppressed update " + frame);
            assertNull(state.tails(), "Tails must be absent after suppressed update " + frame);
        }

        manager.update();

        Sonic2SpecialStageComparisonState firstPresent = manager.captureComparisonState();
        assertNotNull(firstPresent.sonic(), "Sonic appears at the first post-pre-roll comparison");
        assertNotNull(firstPresent.tails(), "Tails appears at the first post-pre-roll comparison");
        assertTrue(manager.getPlayers().stream().allMatch(Sonic2SpecialStagePlayer::isSpawned));
    }

    @Test
    void rewindIntoPreRollRestoresUnspawnedPlayersWithoutChangingTopology() {
        for (int frame = 0; frame < 7; frame++) {
            manager.update();
        }

        Sonic2SpecialStagePlayer sonic = manager.getSonicPlayer();
        Sonic2SpecialStagePlayer tails = manager.getTailsPlayer();
        List<Sonic2SpecialStagePlayer.PlayerType> topology = manager.getPlayers().stream()
                .map(Sonic2SpecialStagePlayer::getPlayerType)
                .toList();
        Sonic2SpecialStageSnapshot snapshot = manager.captureRewindSnapshot();

        for (int frame = 7; frame <= Sonic2SpecialStageIntro.PRE_ROLL_FRAMES; frame++) {
            manager.update();
        }
        assertNotNull(manager.captureComparisonState().sonic());
        assertNotNull(manager.captureComparisonState().tails());

        manager.restoreRewindSnapshot(snapshot);

        assertEquals(2, manager.getPlayers().size());
        assertEquals(topology, manager.getPlayers().stream()
                .map(Sonic2SpecialStagePlayer::getPlayerType)
                .toList());
        assertSame(sonic, manager.getSonicPlayer(), "rewind must preserve Sonic's constructed slot");
        assertSame(tails, manager.getTailsPlayer(), "rewind must preserve Tails' constructed slot");
        assertTrue(manager.getPlayers().stream().noneMatch(Sonic2SpecialStagePlayer::isSpawned));
        assertNull(manager.captureComparisonState().sonic());
        assertNull(manager.captureComparisonState().tails());

        for (int frame = 7; frame < Sonic2SpecialStageIntro.PRE_ROLL_FRAMES; frame++) {
            manager.update();
        }
        assertNull(manager.captureComparisonState().sonic(),
                "restored final pre-roll update must still compare absent");
        assertNull(manager.captureComparisonState().tails(),
                "restored final pre-roll update must still compare absent");

        manager.update();
        assertNotNull(manager.captureComparisonState().sonic());
        assertNotNull(manager.captureComparisonState().tails());
    }

    @Test
    void playerSpawnBoundaryIsIndependentFromInitialSpeedPromotion() throws Exception {
        Field speedPromotionPending = Sonic2SpecialStageManager.class
                .getDeclaredField("initialSpeedPromotionPending");
        speedPromotionPending.setAccessible(true);
        speedPromotionPending.setBoolean(manager, false);

        for (int frame = 0; frame <= Sonic2SpecialStageIntro.PRE_ROLL_FRAMES; frame++) {
            manager.update();
        }

        assertNotNull(manager.captureComparisonState().sonic());
        assertNotNull(manager.captureComparisonState().tails());
        assertEquals(0, manager.captureComparisonState().speedFactor(),
                "consuming the speed latch must not consume the player-spawn boundary");
    }
}
