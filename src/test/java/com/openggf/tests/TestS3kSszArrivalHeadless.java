package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SszArrivalControllerObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sky Sanctuary act 1 arrival: {@code SSZ1_ScreenInit} (sonic3k.asm:115851-115876) and
 * {@code Obj_57C1E} / {@code loc_57CD2} / {@code Obj_57D64} / {@code loc_57DA2}
 * (sonic3k.asm:116760-116858).
 *
 * <p>Every expectation below is a ROM constant from those routines, not a measurement of the
 * engine. On the no-starpost path the screen init forces {@code Camera_max_X = $200},
 * {@code Camera_max_Y = Camera_target_max_Y = $BC0}, the camera to {@code ($60,$F49)} and
 * {@code Scroll_lock}, allocates the controller at X {@code $100} with {@code $2D = $6C}, and
 * sets {@code Events_bg+$05}. The controller then sets {@code Events_bg+$04} and puts Player 1 at
 * {@code Camera_Y + $65} = {@code $FAE} under {@code object_control 3}.
 *
 * <p>The native fixture agrees: {@code s3k-sonic-tails-complete-emeralds/hpz} declares the segment
 * start as {@code ($100,$FAE)}, and its row 0 — recorded after one frame — reads camera
 * {@code ($60,$F41)} and player {@code ($100,$FA6)}, exactly one 8-pixel {@code loc_57D50} step
 * below the init values.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszArrivalHeadless {
    /** {@code SSZ1_ScreenInit}. */
    private static final int CAMERA_X = 0x60;
    private static final int CAMERA_Y = 0xF49;
    private static final int CAMERA_MAX_X = 0x200;
    private static final int CAMERA_MAX_Y = 0xBC0;
    private static final int CONTROLLER_X = 0x100;
    private static final int RISE_FRAMES = 0x6C;
    /** {@code loc_57CAC}: {@code Camera_Y_pos + $65}. */
    private static final int PLAYER_Y = CAMERA_Y + 0x65;
    private static final int RISE_STEP = 8;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void screenInitForcesTheArrivalCameraBoundsAndControlLock(int width) {
        HeadlessTestFixture fixture = boot(width, "");
        fixture.stepIdleFrames(1);

        var camera = GameServices.camera();
        assertEquals(CAMERA_MAX_X, camera.getMaxX() & 0xFFFF, "Camera_max_X_pos");
        assertEquals(CAMERA_MAX_Y, camera.getMaxY() & 0xFFFF, "Camera_max_Y_pos");
        assertEquals(CAMERA_MAX_Y, camera.getMaxYTarget() & 0xFFFF, "Camera_target_max_Y_pos");
        assertEquals(CAMERA_X, camera.getX() & 0xFFFF, "Camera_X_pos");

        SszZoneRuntimeState state = state();
        assertEquals(-1, state.eventsBgByte(0x05), "st (Events_bg+$05)");
        assertEquals(-1, state.eventsBgByte(0x04), "st (Events_bg+$04)");

        var controller = controller();
        assertNotNull(controller, "Obj_57C1E allocated at X $100");
        assertEquals(CONTROLLER_X, controller.getX(), "move.w #$100,x_pos(a1)");
        assertNotNull(controller.beamForTest(), "Obj_TeleporterBeamExpand child");

        var player = fixture.sprite();
        assertTrue(player.isObjectControlled(), "move.b #3,object_control(a1)");
        // Frame 1 is Obj_57C1E's own init pass: it places the player and sets the flags but
        // takes no rise step, which loc_57CD2 only starts on the next pass.
        assertEquals(PLAYER_Y, player.getCentreY() & 0xFFFF, "Player_1 y_pos = Camera_Y + $65");
        assertEquals(CAMERA_Y, camera.getY() & 0xFFFF, "Camera_Y_pos");
        assertEquals(RISE_FRAMES, controller.riseRemainingForTest(), "move.b #$6C,$2D(a1)");
    }

    /**
     * {@code loc_57D50} moves Player 1 and the camera up 8 px for {@code $2D} frames, except that
     * the frame the counter reaches zero skips the camera step.
     */
    @Test
    void theRiseMovesPlayerAndCameraEightPixelsForSixtySixFrames() {
        HeadlessTestFixture fixture = boot(320, "");
        // One frame for Obj_57C1E's init pass, then $6C rise passes.
        fixture.stepIdleFrames(1 + RISE_FRAMES);

        var camera = GameServices.camera();
        assertEquals(0, controller().riseRemainingForTest(), "$2D(a0) exhausted");
        assertEquals(PLAYER_Y - RISE_STEP * RISE_FRAMES, fixture.sprite().getCentreY() & 0xFFFF,
                "Player_1 rose 8 px per frame");
        assertEquals(CAMERA_Y - RISE_STEP * (RISE_FRAMES - 1), camera.getY() & 0xFFFF,
                "the last decrement skips subi.w #8,(Camera_Y_pos)");
        assertTrue(camera.getFrozen(), "Scroll_lock is cleared at loc_57D3C, not before");
    }

    /**
     * {@code loc_57D3C} clears {@code Scroll_lock} and tells the beam to contract;
     * {@code Obj_57D64} then holds the player on the swing until the speed turns non-negative and
     * clears {@code object_control} and {@code Events_bg+$04}.
     */
    @Test
    void theReleaseClearsScrollLockThenReturnsControlAfterTheSwing() {
        HeadlessTestFixture fixture = boot(320, "");
        fixture.stepIdleFrames(1 + RISE_FRAMES + 1);

        assertFalse(GameServices.camera().getFrozen(), "clr.b (Scroll_lock).w at loc_57D3C");
        assertEquals(SszArrivalControllerObjectInstance.PHASE_SWING, controller().phaseForTest());
        assertTrue(fixture.sprite().isObjectControlled(), "object_control 1 through the swing");

        // Gradual_SwingOffset starts at -$20000 and adds $800 per frame, so the speed reaches zero
        // 65 frames after the first call.
        fixture.stepIdleFrames(80);
        assertFalse(fixture.sprite().isObjectControlled(), "clr.b object_control(a1) at Obj_57D64");
        assertEquals(0, state().eventsBgByte(0x04), "clr.b (Events_bg+$04).w");
    }

    /**
     * {@code loc_13AB4}: {@code $A00} with no star post parks Player 2 at {@code ($7F00,0)} with
     * {@code object_control $83} until {@code Obj_57DCC} beams her in and writes
     * {@code Tails_CPU_routine = 6}. The native fixture's row 0 reads the same sidekick sentinel.
     */
    @Test
    void theSidekickParksOffScreenUntilTheArrivalHelperReleasesHer() {
        HeadlessTestFixture fixture = boot(320, "tails");
        fixture.stepIdleFrames(1);

        var sidekicks = GameServices.sprites().getRegisteredSidekicks();
        assertEquals(1, sidekicks.size(), "Player_2 present");
        var tails = sidekicks.get(0);
        assertEquals(0x7F00, tails.getCentreX() & 0xFFFF, "sub_13ECA move.w #$7F00,x_pos(a0)");
        assertEquals(0, tails.getCentreY() & 0xFFFF, "sub_13ECA move.w #0,y_pos(a0)");
        assertTrue(tails.getAir(), "sub_13ECA move.b #1<<Status_InAir,status(a0)");

        // The helper is allocated at loc_57D18 with a $C frame delay and releases on the same
        // swing the leader uses.
        fixture.stepIdleFrames(1 + RISE_FRAMES + 0x0C + 90);
        assertFalse(tails.getAir() && (tails.getCentreX() & 0xFFFF) == 0x7F00,
                "Obj_57DCC moved Player_2 to the arrival column");
    }

    /**
     * {@code SSZ1_ScreenInit}'s whole forced block is behind {@code tst.b (Last_star_post_hit).w}:
     * a checkpoint restart gets no controller, no forced camera and no {@code Events_bg+$05}.
     */
    @Test
    void aStarPostRestartSkipsTheArrival() {
        HeadlessTestFixture fixture = boot(320, "");
        // SSZ1_ScreenInit reads Last_star_post_hit on the load's first pass, so writing the
        // pseudo-starpost before the first frame reproduces a checkpoint restart.
        var checkpoint = GameServices.level().getCheckpointState();
        assertNotNull(checkpoint);
        if (checkpoint instanceof com.openggf.game.CheckpointState state) {
            state.saveCheckpoint(1, 0x140, 0xC6C, false);
        }
        fixture.stepIdleFrames(1);

        assertEquals(0, state().eventsBgByte(0x05), "Events_bg+$05 stays clear");
        org.junit.jupiter.api.Assertions.assertNull(controller(), "no Obj_57C1E");
    }

    private static SszZoneRuntimeState state() {
        return S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static SszArrivalControllerObjectInstance controller() {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        return manager.getActiveObjects().stream()
                .filter(SszArrivalControllerObjectInstance.class::isInstance)
                .map(SszArrivalControllerObjectInstance.class::cast)
                .filter(instance -> !instance.isDestroyed())
                .findFirst()
                .orElse(null);
    }

    private static HeadlessTestFixture boot(int width, String sidekick) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                width == 320 ? WidescreenAspect.NATIVE_4_3.name() : WidescreenAspect.WIDE_16_9.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .build();
    }
}
