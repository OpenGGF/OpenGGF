package com.openggf.game.sonic2;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.objects.badniks.NebulaBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Controller 2 assistance: a real manually commanded Tails jump defeats a badnik while P1 waits. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2ManualTails {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void manualP2JumpHitsTheEnemyAboveTailsWhileNeutralP2DoesNot(boolean jump) throws Exception {
        TestEnvironment.resetAll();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(Path.of(System.getProperty("sonic2.rom.path")), 0, 0, settings);
            int waits = 0;
            do {
                session.step(null); session.renderFrame();
                assertTrue(++waits < 600);
            } while (session.loop().getCurrentGameMode() != GameMode.LEVEL
                    || GameServices.level().hasPendingFreshLevelTransitionBoundary());
            var sonic = GameServices.sprites().getMainPlayable();
            var tails = GameServices.sprites().getSidekicks().getFirst();
            while (tails.getAir() && waits++ < 800) session.step(null);
            assertFalse(tails.getAir(), "Tails must stand on the real EHZ floor before input");
            assertTrue(tails.isCpuControlled());
            // Authored enemy in the clear space above the follower, away from Sonic.
            var enemy = new NebulaBadnikInstance(new ObjectSpawn(tails.getCentreX(),
                    tails.getCentreY() - 45, 0x99, 0x12, 0, false, 0));
            GameServices.level().getObjectManager().addDynamicObject(enemy);
            int score = GameServices.gameState().getScore();
            int sonicY = sonic.getCentreY();
            for (int i = 0; i < 5; i++) session.step(new Bk2FrameInput(i, 0, 0, false,
                    jump ? AbstractPlayableSprite.INPUT_JUMP : 0, jump ? 1 : 0, false, "P2 assistance"));
            assertEquals(jump, enemy.isDestroyed());
            assertEquals(jump ? 100 : 0, GameServices.gameState().getScore() - score);
            assertEquals(jump, tails.getAir());
            assertFalse(sonic.isJumping());
            assertEquals(sonicY, sonic.getCentreY());
        }
    }
}
