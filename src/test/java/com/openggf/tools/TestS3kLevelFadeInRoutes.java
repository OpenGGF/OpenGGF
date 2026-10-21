package com.openggf.tools;

import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.dataselect.AbstractDataSelectProvider;
import com.openggf.game.save.SavePaths;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every S3K entry that passes through the ROM's {@code Level} routine ends with
 * {@code Level/loc_64DC} (docs/skdisasm/sonic3k.asm:7906-7910):
 * {@code Palette_fade_info = $202F} (lines 1-3), {@code Pal_FillBlack} and
 * {@code Palette_fade_timer = $16}. {@code Animate_Palette} (5018-5028) then
 * spends one {@code Pal_FromBlack} pass per LevelLoop frame, so lines 1-3 hold
 * {@code Level_frame_counter} passes and are complete at 21, while line 0 is
 * never faded. The data select's own {@code Pal_FadeToBlack} has already left
 * the screen black and the level loads line 0 with {@code LoadPalette_Immediate}
 * (7666), so no whole-screen reveal follows.
 *
 * <p>These are the real-play routes outside the fresh zone/act request:
 * a data-select new game, a direct boot that presents the card, and a direct
 * boot that omits it.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLevelFadeInRoutes {
    @TempDir Path saves;

    @Test
    void dataSelectNewGameFadesLinesOneToThreeOnceWithoutAHostReveal() throws Exception {
        String previous = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, true, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.startAtTitle();
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            var menu = (AbstractDataSelectProvider) session.loop().getDataSelectProvider();
            menu.initialize();
            session.loop().setGameMode(GameMode.DATA_SELECT);
            Map<Integer, String> observed = new TreeMap<>();
            try (var input = MutatorGameplayCaptureTool.PresentationInput.install(session.loop())) {
                for (int frame = 0; frame < 60; frame++) session.stepPresentation(null, input);
                session.stepPresentation(press(AbstractPlayableSprite.INPUT_DOWN, 0), input);
                session.stepPresentation(null, input);
                session.stepPresentation(press(0, InputActionMasks.ACTION_A), input);
                for (int frame = 0; frame < 600 && !observed.containsKey(23); frame++) {
                    session.stepPresentation(null, input);
                    record(session.loop().getCurrentGameMode(), observed);
                }
            }
            assertNativeLevelFade(observed);
        } finally {
            if (previous == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, previous);
        }
    }

    @Test
    void directBootWithTheCardFadesTheLevelInOnceLevelLoopRuns() throws Exception {
        assertNativeLevelFade(directBoot(true));
    }

    @Test
    void directBootWithoutTheCardFadesTheLevelInFromItsFirstFrame() throws Exception {
        assertNativeLevelFade(directBoot(false));
    }

    private static Map<Integer, String> directBoot(boolean card) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off",
                null, null, null, null, card, false);
        Map<Integer, String> observed = new TreeMap<>();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 22, 1, settings);
            record(session.loop().getCurrentGameMode(), observed);
            for (int frame = 0; frame < 400 && !observed.containsKey(23); frame++) {
                session.step(null);
                record(session.loop().getCurrentGameMode(), observed);
            }
        }
        return observed;
    }

    /** One observation per Level_frame_counter, taken on frames the level presents. */
    private static void record(GameMode mode, Map<Integer, String> observed) {
        if (mode != GameMode.LEVEL) {
            return;
        }
        var fade = GameServices.graphics().getPaletteFadePresentation();
        String value = "fade=" + (fade.isActive() ? fade.mode() + "/" + fade.steps() + "/" + fade.lineMask() : "none")
                + " host=" + GameServices.fade().getState();
        int counter = GameServices.level().getFrameCounter();
        String first = observed.putIfAbsent(counter, value);
        if (first != null && !first.equals(value)) {
            observed.put(counter, first + " | " + value);
        }
    }

    private static void assertNativeLevelFade(Map<Integer, String> observed) {
        assertTrue(observed.containsKey(22), "the level ran past its fade: " + observed);
        for (int counter = 0; counter <= 22; counter++) {
            String fade = counter < PaletteFadePresentation.COLOR_STEPS
                    ? PaletteFadePresentation.Mode.FROM_BLACK + "/" + counter + "/" + PaletteFadePresentation.LINES_1_TO_3
                    : "none";
            assertEquals("fade=" + fade + " host=" + FadeManager.FadeState.NONE, observed.get(counter),
                    "Level_frame_counter " + counter + " of " + observed);
        }
    }

    private static Bk2FrameInput press(int direction, int action) {
        return new Bk2FrameInput(0, direction, action, false, "S3K level fade-in data select route");
    }
}
