package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code Level/loc_64DC} sets {@code Palette_fade_timer = $16} before LevelLoop, and
 * {@code Animate_Palette} spends each of those level frames on the fade instead of AnPal_Load.
 * Native CNZ1 (Sonic + Tails complete run, movie 131870, {@code probe-cnzfade}): the timer reads
 * 21 at Level_frame_counter 1 and 0 at 22, and the {@code AnPal_CNZ} bumper colours
 * ({@code Normal_palette_line_4+$12}, every fourth frame) first change at counter 23, then 27, 31.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kPaletteFadeTimerGatesPaletteCycles {

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test
    void cnzBumperCycleStartsAfterTheFreshLevelFade() {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .withFreshLevelStartLifecycle()
                .build();
        List<Integer> changes = new ArrayList<>();
        String previous = lineThree();
        for (int frame = 0; frame < 40; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            String current = lineThree();
            if (!current.equals(previous)) {
                changes.add(GameServices.level().getFrameCounter());
            }
            previous = current;
        }
        assertEquals(List.of(23, 27, 31, 35, 39), changes);
    }

    private static String lineThree() {
        var palette = GameServices.level().getCurrentLevel().getPalette(3);
        StringBuilder colours = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            var c = palette.getColor(i);
            colours.append(c.r).append(c.g).append(c.b).append(';');
        }
        return colours.toString();
    }
}
