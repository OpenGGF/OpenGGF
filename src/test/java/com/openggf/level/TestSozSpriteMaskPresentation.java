package com.openggf.level;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SozAct1ArenaController;
import com.openggf.graphics.SpritePresentation;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Map_SOZ1EndDoor must mask the production presentation, not just exist as an object. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozSpriteMaskPresentation {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 400, 512, 640, 800})
    void openingArenaDoorIsClippedAtTheNativeSandSurface(int width) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(8, 0)
                .startPosition((short) 0x43B0, (short) 0x9D4).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        assertEquals(width, GameServices.camera().getWidth());
        for (int frame = 0; frame < 1600; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            var level = GameServices.level();
            var doors = level.getObjectManager().activeObjectsOfType(SozAct1ArenaController.Door.class);
            if (doors.isEmpty() || doors.getFirst().getY() < 0x9F4) continue;
            assertFalse(level.getObjectManager().activeObjectsOfType(SozAct1ArenaController.Mask.class).isEmpty());
            var camera = GameServices.camera();
            var tiles = level.spritePresentationRenderer().spriteTables.capture().prepared().tiles().stream()
                    .filter(t -> t.layer() == SpritePresentation.Layer.OBJECT)
                    .filter(t -> t.x() + camera.getX() >= 0x4378 && t.x() + camera.getX() < 0x43C0)
                    .toList();
            assertTrue(tiles.stream().anyMatch(t -> t.y() + camera.getY() < 0xA00),
                    "the opening door must submit visible art above the mask");
            assertTrue(tiles.stream().noneMatch(t -> t.y() + camera.getY() + t.rowEnd() > 0xA00),
                    "loc_55F98's mask must remove door pixels below the sand surface");
            return;
        }
        fail("ordinary arena entry must open the door far enough to test masking");
    }
}
