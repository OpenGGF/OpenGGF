package com.openggf.tests.trace;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.OscillationManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFreshPlayableDispatch {

    @Test
    void freshMainDispatchInitializesOnceWithoutSuppressingTheRestOfTheFrame() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withZoneAndAct(0, 0)
                    .build();
            AbstractPlayableSprite main = fixture.sprite();
            SpriteManager sprites = GameServices.sprites();
            AbstractPlayableSprite sidekick = sprites.getSidekicks().getFirst();
            ObjectManager objects = GameServices.level().getObjectManager();

            main.setCentreX((short) 0x1000);
            main.setCentreY((short) 0x0200);
            main.setXSpeed((short) 0x0100);
            main.setYSpeed((short) 0x0080);
            main.setGSpeed((short) 0x0100);
            main.setAir(true);
            main.setControlLocked(false);
            main.setAnimationId(0);
            main.forceAnimationRestart();

            short initialX = main.getCentreX();
            short initialY = main.getCentreY();
            short initialXSpeed = main.getXSpeed();
            short initialYSpeed = main.getYSpeed();
            short initialGSpeed = main.getGSpeed();
            int initialMainHistory = main.historyPos();
            int initialSidekickHistory = sidekick.historyPos();
            int initialObjectFrame = objects.getFrameCounter();
            int[] initialOscillation = OscillationManager.valuesForTest();

            fixture.stepFrame(false, false, false, true, false);

            assertEquals(initialX, main.getCentreX(), "fresh main X position");
            assertEquals(initialY, main.getCentreY(), "fresh main Y position");
            assertEquals(initialXSpeed, main.getXSpeed(), "fresh main X velocity");
            assertEquals(initialYSpeed, main.getYSpeed(), "fresh main Y velocity");
            assertEquals(initialGSpeed, main.getGSpeed(), "fresh main ground velocity");
            assertNotEquals(-1,
                    main.getAnimationManager().captureRewindState().lastAnimationId(),
                    "fresh main dispatch must still initialize animation");
            assertEquals((initialMainHistory + 1) & 0x3F, main.historyPos(),
                    "fresh main dispatch must close its follower-history tick");
            assertTrue((main.getInputHistory(0) & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                    "fresh main dispatch must publish queued logical controls");
            assertEquals((initialSidekickHistory + 1) & 0x3F, sidekick.historyPos(),
                    "fresh main dispatch must not suppress the sidekick update");
            assertEquals(initialObjectFrame + 1, objects.getFrameCounter(),
                    "fresh main dispatch must not suppress object execution");
            assertFalse(Arrays.equals(initialOscillation, OscillationManager.valuesForTest()),
                    "fresh main dispatch must not suppress the global oscillator");

            int firstDispatchHistory = main.historyPos();
            fixture.stepFrame(false, false, false, true, false);

            assertNotEquals(initialX, main.getCentreX(),
                    "the second ordinary dispatch must run normal main movement");
            assertEquals((firstDispatchHistory + 1) & 0x3F, main.historyPos(),
                    "end-of-tick closure must allow the next history write");
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkipIntros != null ? oldSkipIntros : false);
        }
    }

    @Test
    void rewindToArmedFrameRestoresFreshMainInitializationDispatch() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withZoneAndAct(0, 0)
                    .build();
            AbstractPlayableSprite main = fixture.sprite();
            SpriteManager sprites = GameServices.sprites();

            main.setCentreX((short) 0x1000);
            main.setCentreY((short) 0x0200);
            main.setXSpeed((short) 0x0100);
            main.setYSpeed((short) 0x0080);
            main.setGSpeed((short) 0x0100);
            main.setAir(true);
            main.setControlLocked(false);

            short armedX = main.getCentreX();
            short armedY = main.getCentreY();
            var rewind = sprites.rewindSnapshottable();
            var armedSnapshot = rewind.capture();

            fixture.stepFrame(false, false, false, true, false);
            assertEquals(armedX, main.getCentreX(), "initial fresh dispatch must hold X");
            assertEquals(armedY, main.getCentreY(), "initial fresh dispatch must hold Y");

            fixture.stepFrame(false, false, false, true, false);
            assertNotEquals(armedX, main.getCentreX(),
                    "the live one-shot must be consumed before rewind");

            rewind.restore(armedSnapshot);
            assertEquals(armedX, main.getCentreX(), "rewind must restore the armed X position");
            assertEquals(armedY, main.getCentreY(), "rewind must restore the armed Y position");

            fixture.stepFrame(false, false, false, true, false);

            assertEquals(armedX, main.getCentreX(),
                    "replay from the armed frame must initialize without X movement");
            assertEquals(armedY, main.getCentreY(),
                    "replay from the armed frame must initialize without Y movement");
        } finally {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkipIntros != null ? oldSkipIntros : false);
        }
    }

    @Test
    void onlyS3kEnablesFreshMainInitializationDispatch() {
        assertFalse(new Sonic1GameModule().getLevelInitProfile()
                .firstMainPlayableDispatchInitializesWithoutMovement());
        assertFalse(new Sonic2GameModule().getLevelInitProfile()
                .firstMainPlayableDispatchInitializesWithoutMovement());
        assertTrue(new Sonic3kGameModule().getLevelInitProfile()
                .firstMainPlayableDispatchInitializesWithoutMovement());
    }
}
