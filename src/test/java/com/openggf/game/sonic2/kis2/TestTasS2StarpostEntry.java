package com.openggf.game.sonic2.kis2;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.*;
import com.openggf.game.sonic2.objects.CheckpointObjectInstance;
import com.openggf.game.sonic2.objects.CheckpointStarInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 100% routes: a real post plus recorded jump enters a halfpipe at 50 rings; 49 cannot. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2StarpostEntry {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }
    @ParameterizedTest @CsvSource({"sonic,49", "sonic,50", "knuckles,49", "knuckles,50"})
    void fiftyRingPostAndRealJumpEnterTheSpecialStage(String character, int rings) throws Exception {
        TestEnvironment.resetAll();
        Kis2TestRoms.configureExplicitCatalogue(SonicConfigurationService.getInstance());
        assumeTrue(Kis2TestRoms.lockOnDumpOrNull() != null);
        var settings = new GameplayCaptureSession.Settings(320, character, "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            int waits = 0;
            do { com.openggf.tests.reel.TestReelCapture.step(session,null); session.renderFrame(); assertTrue(++waits < 600); }
            while (session.loop().getCurrentGameMode() != GameMode.LEVEL
                    || GameServices.level().hasPendingFreshLevelTransitionBoundary());
            var player = GameServices.sprites().getMainPlayable();
            while (player.getAir() && waits++ < 800) com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertFalse(player.getAir());
            player.setRingCount(rings); // declared pre-collected ring bank, never seed the invitation
            var objects = GameServices.level().getObjectManager();
            objects.addDynamicObject(new CheckpointObjectInstance(new ObjectSpawn(player.getCentreX(),
                    player.getCentreY(), 0x79, 1, 0, false, 0), "Starpost"));
            com.openggf.tests.reel.TestReelCapture.step(session,null);
            assertEquals(1, GameServices.level().getCheckpointState().getLastCheckpointIndex());
            assertEquals(rings == 50 ? 4 : 0, objects.getActiveObjects().stream()
                    .filter(CheckpointStarInstance.class::isInstance).count());
            for (int i = 0; i < 130; i++) com.openggf.tests.reel.TestReelCapture.step(session,null);
            for (int i = 0; i < 240 && session.loop().getCurrentGameMode() == GameMode.LEVEL; i++) {
                boolean jump = !player.getAir() && (i & 1) == 0;
                com.openggf.tests.reel.TestReelCapture.step(session,new Bk2FrameInput(i, jump ? AbstractPlayableSprite.INPUT_JUMP : 0, jump ? 1 : 0, false, "post jump"));
                session.renderFrame();
            }
            assertEquals(rings == 50 ? GameMode.SPECIAL_STAGE : GameMode.LEVEL, session.loop().getCurrentGameMode());
            if (rings == 50) {
                assertNotNull(session.loop().getActiveSpecialStageProvider());
                if (character.equals("knuckles")) assertInstanceOf(Kis2SpecialStageProvider.class,
                        session.loop().getActiveSpecialStageProvider());
            }
        }
    }
}
