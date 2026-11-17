package com.openggf.level;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestCdInitialMotionArenaMask {
    @Test
    void initialCdMotionCapturesAndDrawsBoundsBeforeTheFirstSpritePreparation() throws Exception {
        var config = GameServices.configuration();
        boolean audio = config.getBoolean(SonicConfiguration.AUDIO_ENABLED);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            boot.boot(disc, new SonicCdGameModule(), 0, 0);
            var level = GameServices.level();
            var renderer = level.spritePresentationRenderer();
            assertEquals(1, level.getFrameCounter(), "The load itself ran CD main-loop iteration one");
            assertTrue(renderer.supportsMotion());
            assertFalse(LevelSpritePresentation.enabled(level));
            assertNull(renderer.boundsMask.sample(), "CD load has not run the first level sprite preparation");
            var transition = renderer.boundsMask.capture();
            var expected = LevelScrollPresentation.captureArenaMask(level);
            LevelMotionAccess.observeService(level, GameServices.sprites(), true);
            var captured = renderer.motionHistory.offer(PictureSelection.LATEST).scroll();
            assertEquals(expected, captured.registers().arenaMask(),
                    "The initial live picture must carry its camera generation's arena mask");
            var displayed = LevelRenderer.class.getDeclaredField("currentScrollPresentation");
            displayed.setAccessible(true);
            LevelMotionAccess.present(level, PictureSelection.between(.5), () -> {
                level.drawWithRenderOptions(GameServices.sprites(), LevelManager.LevelRenderOptions.gameplay());
                try {
                    assertSame(captured, displayed.get(renderer), "The gameplay draw must adopt the captured scroll");
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
                GameServices.graphics().flush();
            });
            assertEquals(transition, renderer.boundsMask.capture(), "Observation and drawing cannot advance the fade");
            LevelSpritePresentation.prepare(level, GameServices.sprites());
            var sampled = renderer.boundsMask.sample();
            LevelMotionAccess.observeService(level, GameServices.sprites(), true);
            assertSame(sampled, renderer.motionHistory.offer(PictureSelection.LATEST).scroll().registers().arenaMask(),
                    "Prepared frames retain their native fade sample");
        } finally {
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, audio);
        }
    }
}
