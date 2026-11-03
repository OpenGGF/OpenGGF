package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SszCollapsingBridgeDiagonalObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Bounded native bridge encounters; no trace state supplies gameplay. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszDiagonalTransfers {
    @ParameterizedTest
    @CsvSource({"sonic,tails,320", "tails,'',320", "sonic,tails,800", "tails,'',800"})
    void rollingTransferRetainsPoseAndReplays(String main, String sidekick, int width) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                java.util.Arrays.stream(WidescreenAspect.values())
                        .filter(a -> a.pixelWidth() == width).findFirst().orElseThrow().name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .startPosition((short) 0x9F2, (short) 0xC20).startPositionIsCentre().build();
        // SSZ's pseudo-starpost skips the cold arrival. Position this bounded
        // encounter above the native permanent bridge at ($9C0,$C78).
        ((CheckpointState) GameServices.level().getCheckpointState())
                .saveCheckpoint(1, 0x9F2, 0xC20, false);
        try {
            boolean transferred = false;
            fixture.stepIdleFrames(120);
            var initial = fixture.sprite();
            assertTrue(GameServices.level().getObjectManager().getRidingObject(initial)
                    instanceof SszCollapsingBridgeDiagonalObjectInstance, "encounter must begin on a native bridge");
            initial.setRolling(true);
            // Match the ordinary roll-entry caller: setRolling changes the box,
            // while its caller shifts top-left Y to keep the feet planted.
            initial.setY((short) (initial.getY() + initial.getRollHeightAdjustment()));
            initial.setXSpeed((short) 0xE7A);
            initial.setGSpeed((short) 0xE7A);
            for (int frame = 0; frame < 8; frame++) {
                var player = fixture.sprite();
                var manager = GameServices.level().getObjectManager();
                var old = manager.getRidingObject(player);
                boolean rolling = player.getRolling();
                fixture.stepFrame(false, false, false, true, false);
                var next = manager.getRidingObject(player);
                if (old instanceof SszCollapsingBridgeDiagonalObjectInstance
                        && next instanceof SszCollapsingBridgeDiagonalObjectInstance && old != next
                        && rolling && !player.isJumping()) {
                    assertTrue(player.getRolling(), "loc_44D98 clears InAir before the next slot's RideObject_SetRide");
                    assertFalse(player.getAir());
                    if (!transferred) {
                        transferred = true;
                        var registry = fixture.gameplayMode().getRewindRegistry();
                        var saved = registry.capture();
                        // Continue with ordinary right input so rewind does not depend on a movie cursor.
                        for (int i = 0; i < 15; i++) fixture.stepFrame(false, false, false, true, false);
                        var forward = registry.capture();
                        registry.restore(saved);
                        for (int i = 0; i < 15; i++) fixture.stepFrame(false, false, false, true, false);
                        var replay = registry.capture();
                        for (String key : forward.entries().keySet()) {
                            assertTrue(RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key)).isEmpty(), key);
                        }
                        registry.restore(saved);
                        fixture.stepFrame(false, false, false, true, true);
                        assertTrue(fixture.sprite().getAir(), "loc_44D98 must not reground an actual jump");
                        assertTrue(fixture.sprite().isJumping());
                        break;
                    }
                }
            }
            assertTrue(transferred, "encounter must exercise a rolling transfer between native bridge slots; final="
                    + fixture.sprite().getCentreX() + "," + fixture.sprite().getCentreY());
        } finally {
            config.clearSessionOverrides();
            CrossGameFeatureProvider.getInstance().resetState();
            SessionManager.clear();
        }
    }
}
