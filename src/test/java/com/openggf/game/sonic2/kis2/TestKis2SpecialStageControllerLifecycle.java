package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Declared local checkpoint/progress, real patched entry/results/return; no campaign claim. */
@RequiresRom(SonicGame.SONIC_2)
class TestKis2SpecialStageControllerLifecycle {
    @AfterEach void cleanup() { SessionManager.clear(); TestEnvironment.resetAll(); }

    @Test void entryResultsReturnKeepsChipProvider() throws Exception {
        TestEnvironment.resetAll();
        Kis2TestRoms.configureExplicitCatalogue(SonicConfigurationService.getInstance());
        assumeTrue(Kis2TestRoms.lockOnDumpOrNull() != null, "KiS2 lock-on dump required");
        assertNotNull(Kis2TestRoms.lockOnDumpOrNull(), "required KiS2 lock-on image");
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            var loop = capture.loop();
            var module = assertInstanceOf(DelegatingGameModule.class, GameServices.module());
            assertEquals("kis2", module.patchId());
            var provider = assertInstanceOf(Kis2SpecialStageProvider.class, module.getSpecialStageProvider());
            var player = GameServices.sprites().getMainPlayable();
            var checkpoint = assertInstanceOf(CheckpointState.class, GameServices.level().getCheckpointState());
            player.setRingCount(47); checkpoint.saveCheckpoint(1, 0x100, 0x290, false);
            int emeralds = GameServices.gameState().getEmeraldCount();
            loop.enterSpecialStage();
            assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode());
            assertSame(provider, loop.getActiveSpecialStageProvider());
            assertInstanceOf(Kis2SpecialStageDataLoader.class, provider.getManager().getDataLoader());
            for (int i = 0; i < 240; i++) capture.step(null);
            assertTrue(provider.isEntryPresentationReady());
            assertFalse(GameServices.fade().isActive());
            int stage = provider.getCurrentStage();
            loop.debugCompleteSpecialStageWithEmerald(); // declared outcome stimulus
            for (int i = 0; i < 300 && loop.getCurrentGameMode() != GameMode.SPECIAL_STAGE_RESULTS; i++) capture.step(null);
            assertEquals(GameMode.SPECIAL_STAGE_RESULTS, loop.getCurrentGameMode());
            assertSame(provider, loop.getActiveSpecialStageProvider());
            assertEquals(stage, loop.recordedSpecialStageIdentity(GameMode.SPECIAL_STAGE_RESULTS));
            assertNotNull(loop.getResultsScreen());
            assertEquals(emeralds + 1, GameServices.gameState().getEmeraldCount());
            boolean title = false;
            for (int i = 0; i < 2400 && loop.getCurrentGameMode() != GameMode.LEVEL; i++) {
                capture.step(null); title |= loop.getCurrentGameMode() == GameMode.TITLE_CARD;
            }
            assertTrue(title, "real return title was dispatched");
            assertEquals(GameMode.LEVEL, loop.getCurrentGameMode()); assertNull(loop.getResultsScreen());
            assertSame(provider, module.getSpecialStageProvider());
            assertEquals(1, ((CheckpointState)GameServices.level().getCheckpointState()).getLastCheckpointIndex());
            assertEquals(47, GameServices.sprites().getMainPlayable().getRingCount());
            assertFalse(GameServices.sprites().getMainPlayable().isObjectControlled());
        }
    }
}
