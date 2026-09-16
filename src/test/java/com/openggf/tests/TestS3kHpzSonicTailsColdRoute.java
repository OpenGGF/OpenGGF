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
class TestS3kHpzSonicTailsColdRoute {
    static final Path BK2 = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2");
    static final int FIRST_LEVEL_FRAME = 441758;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
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
