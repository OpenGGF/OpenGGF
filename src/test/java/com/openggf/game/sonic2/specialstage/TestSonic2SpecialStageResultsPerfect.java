package com.openggf.game.sonic2.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.game.sonic2.kis2.Kis2SpecialStageProvider;
import com.openggf.graphics.FadeManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SpecialStageResultsPerfect {
    @AfterEach
    void cleanup() {
        SessionManager.clear();
        TestEnvironment.resetAll();
    }

    @ParameterizedTest(name = "lock-on={0}, perfect={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void resultsTallyPerfectOnlyWhenAllSpawnedRingsWereKept(boolean lockOn, boolean perfect) throws Exception {
        TestEnvironment.resetAll();
        if (lockOn) {
            String lockOnPath = System.getProperty("kis2.rom.path");
            assumeTrue(lockOnPath != null && !lockOnPath.isBlank(), "KiS2 lock-on dump required");
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.ROMS_DIRECTORY, Path.of(lockOnPath).toAbsolutePath()
                    .getParent().toString());
            config.setConfigValue(SonicConfiguration.SONIC_2_ROM, System.getProperty("sonic2.rom.path"));
        }
        var settings = new GameplayCaptureSession.Settings(
                320, lockOn ? "knuckles" : "sonic", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            var loop = capture.loop();
            loop.enterSpecialStage();
            var provider = assertInstanceOf(Sonic2SpecialStageProvider.class, loop.getActiveSpecialStageProvider());
            assertEquals(lockOn, provider instanceof Kis2SpecialStageProvider);
            for (int i = 0; i < 240; i++) capture.step(null);
            assertTrue(provider.isEntryPresentationReady());
            assertFalse(GameServices.fade().isActive());

            var manager = provider.getManager();
            var objects = manager.getObjectManager();
            loop.debugCompleteSpecialStageWithEmerald();
            var fade = GameServices.fade();
            for (int i = 0; i < 300 && fade.getState() != FadeManager.FadeState.HOLD_WHITE; i++) {
                capture.step(null);
            }
            assertEquals(FadeManager.FadeState.HOLD_WHITE, fade.getState());
            assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode());

            // Declare the end-of-stage state using the ROM object stream and
            // the live ring words read by the production handoff.
            for (int segment = 0; segment < 32 && objects.getPerfectRingsTotal() < 2; segment++) {
                objects.processSegment(segment, Sonic2SpecialStageConstants.SEGMENT_STRAIGHT);
            }
            int spawned = objects.getPerfectRingsTotal();
            assertTrue(spawned >= 2, "test must admit rings from the ROM stream");
            int retained = spawned - (perfect ? 0 : 1);
            var leader = manager.getPlayers().getFirst();
            for (int i = leader.getRings(); i < retained; i++) objects.collectRing(leader);
            assertEquals(retained, leader.getRings());
            int initialScore = GameServices.gameState().getScore();
            capture.step(null);
            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode());

            for (int i = 0; i < 350 && GameServices.gameState().getScore() == initialScore; i++) {
                capture.step(null);
            }
            assertTrue(GameServices.gameState().getScore() > initialScore, "normal tally started");
            for (int i = 1; i < Math.max(100, retained); i++) capture.step(null);
            int normalScore = initialScore + retained * 100 + 10_000;
            assertEquals(normalScore, GameServices.gameState().getScore());
            capture.step(null); // Exhausted Obj6F_TallyScore pass.

            var results = loop.getResultsScreen();
            if (!perfect) {
                for (int i = 0; i < 122 && !results.isComplete(); i++) capture.step(null);
                assertTrue(results.isComplete(), "ordinary results leave after the short hold");
                assertEquals(normalScore, GameServices.gameState().getScore());
                return;
            }

            // $24 and $26 each wait $5A passes. $28 starts on the next pass.
            for (int i = 0; i < 180; i++) capture.step(null);
            assertFalse(results.isComplete(), "perfect results wait for a second tally");
            assertEquals(normalScore, GameServices.gameState().getScore());
            var clock = EngineTiming.vIntRunCounter();
            int beforeTallyVint = clock.lowByte();
            capture.step(null);
            assertEquals(normalScore + 200, GameServices.gameState().getScore(), "first perfect tally pass");
            assertEquals((beforeTallyVint + 1) & 0xFF, clock.lowByte(),
                    "the live results loop services one V-int before its tally pass");
            for (int i = 1; i < 250; i++) capture.step(null);
            assertEquals(normalScore + 50_000, GameServices.gameState().getScore());
            assertFalse(results.isComplete(), "the exhausted pass and final hold still follow");
            capture.step(null);
            for (int i = 0; i < 120; i++) capture.step(null);
            assertFalse(results.isComplete(), "TimedDisplay has only selected DisplayOnly");
            capture.step(null);
            assertTrue(results.isComplete());
        }
    }
}
