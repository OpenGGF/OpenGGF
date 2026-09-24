package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cold Sky Sanctuary act-1 route, driven by the controller inputs of the committed
 * Sonic + Tails complete-run movie. Only BK2 controller input is consumed; no trace state is read.
 *
 * <p>The fixture that carries SSZ act 1 is the one named {@code hpz_completerun}: its metadata says
 * {@code zone_id 10} — {@code ZONE_SSZ} — with start {@code ($100,$FAE)}, the arrival position
 * {@code SSZ1_ScreenInit} forces. The {@code ssz}-named fixtures are {@code zone_id 11}, Death Egg.
 * That swap is recorded in the bring-up plan's scope table and is the reason this class reads an
 * {@code hpz} offset for a Sky Sanctuary route.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszColdRoutes {
    @Test void authoredContinuationTraversesThePermanentDiagonalStaircase() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle().withRecording(MOVIE)
                .withRecordingStartFrame(FIRST_LEVEL_FRAME).build();
        for (int frame = 0; frame < 2501; frame++) fixture.stepFrameFromRecording();
        var player = fixture.sprite();
        boolean rodeSlope = false;
        boolean replayed = false;
        for (int frame = 0; frame < 350 && player.getCentreX() < 2960; frame++) {
            boolean jump = frame < 24 || (frame >= 84 && frame < 108);
            fixture.stepFrame(false, false, false, true, jump);
            assertFalse(player.getDead(), "cold continuation death at input " + frame);
            var ride = GameServices.level().getObjectManager().getRidingObject(player);
            if (ride instanceof com.openggf.game.sonic3k.objects.SszCollapsingBridgeDiagonalObjectInstance slope) {
                rodeSlope = true;
                assertTrue(slope.isHighPriority(), "ROM art_tile priority is independent of SAT bucket 3");
                if (!replayed && frame > 108) {
                    replayed = true;
                    var registry = fixture.gameplayMode().getRewindRegistry();
                    var saved = registry.capture();
                    for (int i = 0; i < 15; i++) fixture.stepFrame(false, false, false, true, false);
                    var forward = registry.capture();
                    slope.setDestroyed(true); fixture.stepIdleFrames(1);
                    registry.restore(saved);
                    for (int i = 0; i < 15; i++) fixture.stepFrame(false, false, false, true, false);
                    var replay = registry.capture();
                    for (String key : forward.entries().keySet()) {
                        var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, forward.get(key), replay.get(key));
                        assertTrue(differences.isEmpty(), key + ": " + differences);
                    }
                    frame += 15;
                    continue; // the recreated slope may have handed the player to the next piece.
                }
                int index = ((player.getCentreX() - slope.getX() + slope.halfWidthForTest()) & 0xFFFF) >> 1;
                if (index >= 0 && index < 64) {
                    int surface = slope.getY() - slope.sampleSlopeByte(index);
                    assertTrue(Math.abs(player.getCentreY() + player.getYRadius() - surface) <= 4,
                            "riding must follow the signed ROM slope, x=" + player.getCentreX());
                }
            }
        }
        assertTrue(replayed, "capture/recreate/forward replay on a live slope");
        assertTrue(rodeSlope, "production contact must enter the diagonal slope path");
        assertTrue(player.getCentreX() >= 2960, "reach the top of the four permanent walkway pieces");
        assertTrue(player.getCentreY() < 3080, "the staircase raises the player rather than allowing a fall underneath");
    }

    /** {@code hpz_completerun/metadata.json}: {@code bk2_frame_offset}. */
    private static final int FIRST_LEVEL_FRAME = 396720;
    private static final Path MOVIE =
            Path.of("src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2");
    /** {@code Obj_SSZCutsceneBridge} on reaching zero: {@code move.w #$19A0,Camera_max_X}. */
    private static final int OPEN_ROUTE_MAX_X = 0x19A0;
    /** The pseudo-starpost the bridge writes: {@code Saved_X/Y = $140,$C6C}. */
    private static final int BRIDGE_X = 0x140;
    /**
     * The frontier as measured on 2026-09-18: the bridge finishes at route frame 1390 and the
     * recorded inputs carry Sonic to X {@code $6EB} inside 6000 frames without anyone dying. The
     * floor asserted here is a little under that so an ordinary physics wobble does not fail the
     * case, but a regression that stops the route at the bridge or the ledge will.
     */
    private static final int MEASURED_FRONTIER_X = 0x680;
    private static final int MEASURED_BRIDGE_OPEN_FRAME = 1390;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    /**
     * The recorded inputs run the arrival, the Knuckles cutscene and the bridge, and then carry the
     * player off the arrival ledge onto the route the bridge opens. The frontier this reaches is
     * recorded in the act-1 matrix; what is asserted here is only what the ROM guarantees at the
     * point the bridge finishes — that it clears {@code Events_bg+$05}, opens the camera to
     * {@code $19A0} and leaves its pseudo-starpost — plus that nobody dies getting there.
     */
    @Test
    void recordedInputsRunTheArrivalAndOpenTheRoute() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .withRecording(MOVIE)
                .withRecordingStartFrame(FIRST_LEVEL_FRAME)
                .build();
        var player = fixture.sprite();

        int routeOpenedAt = -1;
        int furthestX = 0;
        for (int frame = 0; frame < 6000; frame++) {
            fixture.stepFrameFromRecording();
            assertFalse(player.getDead(), "the leader died at route frame " + frame);
            furthestX = Math.max(furthestX, player.getCentreX() & 0xFFFF);
            if (routeOpenedAt < 0 && (GameServices.camera().getMaxX() & 0xFFFF) == OPEN_ROUTE_MAX_X) {
                routeOpenedAt = frame;
            }
        }

        assertTrue(routeOpenedAt > 0,
                "the bridge finishes and opens Camera_max_X to $19A0 on the recorded inputs");
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        assertTrue(state.eventsBgByte(0x05) == 0,
                "the bridge clears Events_bg+$05 so sub_575EA's bounds machine runs again");
        assertTrue(furthestX > BRIDGE_X,
                "the route carries the player past the bridge's X, reached $"
                        + Integer.toHexString(furthestX));
        assertTrue(routeOpenedAt <= MEASURED_BRIDGE_OPEN_FRAME + 120,
                "the bridge finished at route frame " + routeOpenedAt + ", measured at "
                        + MEASURED_BRIDGE_OPEN_FRAME);
        assertTrue(furthestX >= MEASURED_FRONTIER_X,
                "the route reached $" + Integer.toHexString(furthestX)
                        + ", short of the measured frontier $"
                        + Integer.toHexString(MEASURED_FRONTIER_X));
    }
}
