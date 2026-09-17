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

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cold Sonic + Tails Hidden Palace route driven by the controller inputs of the committed
 * complete-run movie, from the ROM's first level frame at movie frame 441758
 * (segment {@code hpz22_2} row {@code $1E09}, where Level_frame_counter starts).
 * Only BK2 controller input is consumed; no trace state is read.
 *
 * <p>Current frontier: the Knuckles fight near X {@code $11C0}. Extend the milestones as
 * route slices land.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzColdRoutes {
    static final Path BK2 = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2");
    static final int FIRST_LEVEL_FRAME = 441758;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    /**
     * Knuckles complete-run segment {@code hpz22}: row 0 is Level_frame_counter 1 at movie
     * frame 411496; the ROM reaches the {@code $A01} load (zone_act_state) at row 859 after
     * rising on the {@code $B40} pad from about row {@code $2B8}.
     */
    @Test
    void knucklesRecordedInputsLeaveForSkySanctuaryActTwo() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .withFreshLevelStartLifecycle()
                .withRecording(Path.of(
                        "src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/s3k-knuckles-complete-superemeralds.bk2"))
                .withRecordingStartFrame(411496)
                .build();
        var player = fixture.sprite();
        assertEquals("knuckles", player.getCode());
        var manager = GameServices.level().getObjectManager();
        var loop = new com.openggf.GameLoop(new com.openggf.control.InputHandler());
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(com.openggf.game.GameMode.LEVEL);
        try {
            int requestFrame = -1;
            for (int frame = 0; frame < 1000 && requestFrame < 0; frame++) {
                fixture.stepFrameFromRecording();
                assertFalse(player.getDead(), "Knuckles died at route frame " + frame);
                if ((GameServices.camera().getY() & 0xFFFF) < 0x240 && (player.getCentreX() & 0xFFFF) >= 0xB00) {
                    requestFrame = frame;
                }
            }
            assertTrue(requestFrame > 0, "Knuckles must reach the loc_45B94 exit condition");
            assertTrue(Math.abs(requestFrame - 0x35C) <= 60,
                    "exit condition near the ROM's row $35C, was " + requestFrame);
            boolean loaded = false;
            for (int i = 0; i < 600 && !loaded; i++) {
                fixture.gameplayMode().getFadeManager().update();
                loop.step();
                loaded = GameServices.level().getObjectManager() != manager
                        || GameServices.level().getCurrentZone() != Sonic3kZoneIds.ZONE_HPZ;
            }
            assertTrue(loaded);
            assertEquals(Sonic3kZoneIds.ZONE_SSZ, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
        } finally {
            loop.closePresence();
        }
    }

    /**
     * Native BizHawk observations of the same movie (capture_hpz_route_reference.lua, plan
     * {@code route}, 2026-09-17): movie frame, camera X/Y, Player_1 X/Y, Player_2 X/Y. They cover
     * the Obj_LevelIntro_PlayerRun run-in (camera held at $40 until Sonic passes $E0 - $10), the
     * lower teleporter approach (no landing on the pad's sloped edge), charge, lift, settle and
     * unroll, and the first four Knuckles hits. Native camera X is already $28 on the load frames
     * (history carried from the preceding act through the load-time DeformBgLayer), so a cold load
     * trails it for two frames; checkpoints start once both reach $40.
     */
    static final int[][] NATIVE_CHECKPOINTS = {
            {441761, 64, 2700, 48, 2796, 16, 2800},
            {441842, 64, 2700, 215, 2796, 128, 2800},
            {441850, 88, 2700, 248, 2796, 164, 2800},
            {443079, 2686, 2129, 2846, 2257, 2750, 2069},
            {443340, 2734, 2131, 2878, 2227, 2930, 2288},
            {443431, 2734, 2126, 2878, 2227, 2878, 2232},
            {443565, 2734, 923, 2880, 1014, 2827, 2166},
            {443631, 2734, 848, 2880, 949, 2801, 2288},
            {444437, 4239, 918, 4399, 1068, 4309, 1072},
            {444883, 4320, 896, 4575, 1068, 4520, 1007},
            {445001, 4320, 896, 4542, 1066, 4484, 1044},
    };

    @Test
    void recordedInputsMatchNativeCheckpointsThroughTheFirstKnucklesHits() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .withFreshLevelStartLifecycle()
                .withRecording(BK2)
                .withRecordingStartFrame(FIRST_LEVEL_FRAME)
                .build();
        var p1 = fixture.sprite();
        var p2 = GameServices.sprites().getRegisteredSidekicks().getFirst();
        int last = NATIVE_CHECKPOINTS[NATIVE_CHECKPOINTS.length - 1][0];
        int next = 0;
        // The fixture's first step lands on native movie frame FIRST_LEVEL_FRAME + 1.
        for (int movieFrame = FIRST_LEVEL_FRAME + 1; movieFrame <= last; movieFrame++) {
            fixture.stepFrameFromRecording();
            if (movieFrame == FIRST_LEVEL_FRAME + 1) {
                assertTrue(p1.isControlLocked(), "Obj_LevelIntro_PlayerRun locks Ctrl_1 from the first frame");
            }
            int[] expected = NATIVE_CHECKPOINTS[next];
            if (movieFrame != expected[0]) {
                continue;
            }
            int[] actual = {movieFrame, GameServices.camera().getX() & 0xFFFF, GameServices.camera().getY() & 0xFFFF,
                    p1.getCentreX() & 0xFFFF, p1.getCentreY() & 0xFFFF,
                    p2.getCentreX() & 0xFFFF, p2.getCentreY() & 0xFFFF};
            assertArrayEquals(expected, actual, "camera, Player_1 and Player_2 at movie frame " + movieFrame);
            next++;
        }
        assertEquals(NATIVE_CHECKPOINTS.length, next);
        assertFalse(p1.isControlLocked(), "the run-in releases Ctrl_1");
    }

    @Test
    void recordedInputsReachTheKnucklesFightThroughTheLowerTeleporter() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .withFreshLevelStartLifecycle()
                .withRecording(BK2)
                .withRecordingStartFrame(FIRST_LEVEL_FRAME)
                .build();
        var player = fixture.sprite();
        assertEquals("sonic", player.getCode());
        assertEquals(1, GameServices.sprites().getRegisteredSidekicks().size());
        assertEquals(320, fixture.camera().getWidth() & 0xFFFF);

        int upperFloorFrame = -1;
        int fightFrame = -1;
        for (int frame = 0; frame < 3000 && fightFrame < 0; frame++) {
            fixture.stepFrameFromRecording();
            assertFalse(player.getDead(), "Sonic died at route frame " + frame);
            assertEquals(Sonic3kZoneIds.ZONE_HPZ, GameServices.level().getCurrentZone());
            int x = player.getCentreX() & 0xFFFF;
            int y = player.getCentreY() & 0xFFFF;
            if (upperFloorFrame < 0 && x >= 0xB00 && y < 0x480 && !player.isObjectControlled()) {
                upperFloorFrame = frame;
            }
            if (upperFloorFrame >= 0 && x >= 0x1100 && y >= 0x400 && y <= 0x440) {
                fightFrame = frame;
            }
        }
        // ROM: teleported to ($B40,$3EE) by row $251C; fight floor reached by row $2904.
        assertTrue(upperFloorFrame > 0, "the $B40 teleporter must carry Sonic to the upper floor");
        assertTrue(fightFrame > upperFloorFrame, "the upper corridor must reach the Knuckles fight floor");
    }
}
