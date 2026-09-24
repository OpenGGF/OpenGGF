package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tools.PlaneOpacityProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why the Lava Reef act 1 dome background lock produces no visible pixels.
 *
 * <p>Slice 5 landed {@code sub_56DAC}'s locked background and proved it runs -- the locked
 * {@code Camera_X_pos_BG_copy}/{@code Camera_Y_pos_BG_copy} words are right -- yet two 420-frame
 * captures built with and without the lock were byte-identical, and so was one built with the
 * locked camera forced to an absurd {@code ($123,$45)}. That ablation says plane B contributes
 * nothing; it does not say why, and a capture cannot tell "wrong pixels drawn" from "no pixels
 * reachable".
 *
 * <p>This settles it from ROM data instead of from a frame: Lava Reef act 1's <b>foreground</b>
 * plane has no transparent pixel anywhere the camera sits in or around the dome, so no background
 * pixel can reach the screen there whatever {@code SwScrlLrz} computes. The dome lock is therefore
 * not an SSZ-style background-window defect.
 *
 * <p>The control is the point of the test: Angel Island act 1's sky proves the same probe reports
 * see-through pixels when there are any, so a zero here is a fact about Lava Reef and not about
 * the measurement. Break it by pointing {@link #lavaReefAct1ForegroundIsOpaqueAroundTheDome} at
 * Angel Island, or the control at Lava Reef.
 */
@RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
class TestS3kLrzForegroundOpacity {

    private static final int FOREGROUND = 0;
    private static final int VIEWPORT_WIDTH = 320;
    private static final int VIEWPORT_HEIGHT = 224;

    /**
     * Camera top-left positions across the act 1 dome. {@code word_56F88} region 0 is X
     * {@code $1AC0}-{@code $1B40}, Y {@code $840}-{@code $8C0}; {@code ($1E00,$900)} is the
     * "deep inside the dome" position the s3k-known-bugs entry reproduces with.
     */
    private static final int[][] DOME_CAMERA_POSITIONS = {
            {0x1900, 0x0800},
            {0x1B00, 0x0800},
            {0x1D00, 0x0880},
            {0x1E00, 0x0900},
            {0x2200, 0x0900},
            {0x22C0, 0x0780},
    };

    @AfterEach
    void cleanup() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void lavaReefAct1ForegroundIsOpaqueAroundTheDome() {
        load(Sonic3kZoneIds.ZONE_LRZ, 0);
        Level level = GameServices.level().getCurrentLevel();
        Map map = level.getMap();
        for (int[] camera : DOME_CAMERA_POSITIONS) {
            PlaneOpacityProbe.Coverage coverage = PlaneOpacityProbe.coverage(
                    level, map, FOREGROUND, camera[0], camera[1],
                    VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
            assertEquals(VIEWPORT_WIDTH * VIEWPORT_HEIGHT, coverage.sampledPixels());
            assertEquals(0, coverage.seeThroughPixels(),
                    () -> String.format(
                            "Lava Reef act 1 foreground at ($%X,$%X) is no longer fully opaque: %s."
                                    + " The dome background lock may now be visible; re-check the"
                                    + " s3k-known-bugs entry.",
                            camera[0], camera[1], coverage));
        }
    }

    /**
     * The control. Angel Island act 1's sky is layout chunk 0, so the same probe over the same
     * viewport size must report see-through pixels. Without this a broken probe that always
     * returned an opaque pixel would pass the assertion above.
     */
    @Test
    void theProbeReportsSeeThroughPixelsWhereThePlaneIsOpen() {
        load(Sonic3kZoneIds.ZONE_AIZ, 0);
        Level level = GameServices.level().getCurrentLevel();
        PlaneOpacityProbe.Coverage coverage = PlaneOpacityProbe.coverage(
                level, level.getMap(), FOREGROUND, 0x0000, 0x0300,
                VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        assertTrue(coverage.seeThroughPixels() > 0,
                () -> "Angel Island act 1's sky must be see-through, but the probe reported "
                        + coverage + "; the probe cannot disagree and proves nothing about Lava Reef");
    }

    private static void load(int zone, int act) {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
    }
}
