package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

/**
 * Cold Sonic Doomsday route driven only by the controller input of the committed Sonic + Tails
 * complete-run movie from movie frame 514214 (segment {@code zone0c} row 0). No trace state is read.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDdzColdRoutes {
    static final Path BK2 = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2");
    static final int FIRST_LEVEL_FRAME = 514214;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test
    void diagnostic() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0)
                .withFreshLevelStartLifecycle()
                .withRecording(BK2)
                .withRecordingStartFrame(FIRST_LEVEL_FRAME)
                .build();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        var player = fixture.sprite();
        var ddz = S3kRuntimeStates.currentDdz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        for (int frame = 1; frame <= 3808; frame++) {
            fixture.stepFrameFromRecording();
            if ((frame >= 3658 && frame <= 3664) || (frame >= 3803 && frame <= 3808)) {
                StringBuilder objects = new StringBuilder();
                for (var o : GameServices.level().getObjectManager().getActiveObjects()) {
                    String n = o.getClass().getSimpleName();
                    if (n.equals("DdzMissileObjectInstance") && o.getSpawn().subtype() == 1
                            || n.equals("DdzEndBossLauncherObjectInstance") || n.equals("DdzEndBossObjectInstance")) {
                        objects.append(' ').append(n.substring(3, 8)).append('#')
                                .append(((com.openggf.level.objects.AbstractObjectInstance) o).getSlotIndex())
                                .append('(').append(Integer.toHexString(o.getX())).append(',')
                                .append(Integer.toHexString(o.getY())).append(')');
                    }
                }
                System.out.printf("f%d x=%X y=%X inv=%d%s%n", frame, player.getCentreX() & 0xFFFF,
                        player.getCentreY() & 0xFFFF, player.getInvulnerableFrames(), objects);
            }
        }
    }
}
