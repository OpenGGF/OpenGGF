package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Level/loc_64DC} (docs/skdisasm/sonic3k.asm:7874-7878) ends a fresh S3K level load
 * with {@code Palette_fade_info = $202F} (lines 1-3), {@code Pal_FillBlack},
 * {@code Palette_fade_timer = $16} and the surviving title card's hold {@code $2E = $16}.
 * {@code Animate_Palette} (5018-5028) then spends each LevelLoop frame on one
 * {@code Pal_FromBlack} pass while the card holds, so the level fades in behind the card
 * and line 0 (players, HUD, card) stays at full colour.
 *
 * <p>Native HPZ (Knuckles complete run, campaign-visual native {@code hpz-entry} RAM/CRAM
 * dumps, inputs 411495-411517): at {@code Level_frame_counter} 0 lines 1-3 are black with
 * the timer and {@code $2E} both 22, counter k holds k passes, the lines are complete at 21
 * and {@code $2E} reaches 0 at 22. This drives the production zone/act request through
 * the fresh title card, with the card actually presented.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFreshTitleCardLevelFadeIn {
    @AfterEach
    void tearDown() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"22, 1, knuckles", "9, 0, sonic"})
    void levelFadesInBehindTheHoldingCard(int zone, int act, String character) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        HeadlessTestFixture.builder().withZoneAndAct(9, 1).build();
        GameLoop loop = new GameLoop(new InputHandler());
        loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
        var level = SessionManager.getCurrentGameplayMode().getLevelManager();
        level.requestZoneAndAct(zone, act, true);

        boolean presented = false;
        Map<Integer, String> released = new TreeMap<>();
        for (int step = 0; step < 900 && !released.containsKey(23); step++) {
            loop.step();
            boolean atDestination = level.getCurrentZone() == zone && level.getCurrentAct() == act;
            presented |= atDestination && loop.getCurrentGameMode() == GameMode.TITLE_CARD;
            // Counter 0 is the loc_64DC release and the held first boundary, before LevelLoop.
            if (!presented || loop.getCurrentGameMode() != GameMode.LEVEL) {
                continue;
            }
            var card = (Sonic3kTitleCardManager) loop.getTitleCardProvider();
            var fade = GameServices.graphics().getPaletteFadePresentation();
            int counter = level.getFrameCounter();
            String observed = card.getStateName() + " backdrop=" + card.drawsOpaqueLevelBackdrop()
                    + " overlay=" + GameServices.fade().getState()
                    + " fade=" + (fade.isActive() ? fade.mode() + "/" + fade.steps() + "/" + fade.lineMask() : "none");
            released.putIfAbsent(counter, observed);
        }
        assertTrue(presented, "the destination title card was presented");
        for (int counter = 0; counter <= 22; counter++) {
            String expectedFade = counter < PaletteFadePresentation.COLOR_STEPS
                    ? PaletteFadePresentation.Mode.FROM_BLACK + "/" + counter + "/" + PaletteFadePresentation.LINES_1_TO_3
                    : "none";
            String expectedState = counter < 22 ? "DISPLAY" : released.getOrDefault(counter, "?").split(" ")[0];
            assertEquals(expectedState + " backdrop=false overlay=" + FadeManager.FadeState.NONE + " fade=" + expectedFade,
                    released.get(counter), "Level_frame_counter " + counter + " of " + released);
        }
        assertFalse(released.isEmpty());
    }
}
