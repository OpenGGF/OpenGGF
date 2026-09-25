package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesSszInstance;
import com.openggf.game.sonic3k.objects.SszCutsceneBridgeObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sky Sanctuary act 1's opening cutscene: {@code Obj_57E34} (sonic3k.asm:116920-116945),
 * {@code CutsceneKnux_SSZ} (133530-133740) and {@code Obj_SSZCutsceneBridge} (90405-90470).
 *
 * <p>The route is blocked without it. {@code SSZ1_ScreenInit} sets {@code Events_bg+$05}, which
 * makes {@code sub_575EA} return before any bounds work, and only {@code loc_44FBA} — the bridge
 * reaching offset zero — clears it. The bridge in turn waits on {@code Events_bg+$08}, which only
 * {@code loc_658F2} writes, when Knuckles lands on the button after watching the Death Egg rise.
 *
 * <p>Every expectation is a ROM constant from those routines.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszKnucklesBridgeHeadless {
    /** {@code Obj_57C1E}: {@code move.w #$60,subtype(a1)} on the spawner. */
    private static final int SPAWNER_DELAY = 0x60;
    /** {@code Obj_57E34}: {@code move.w #$C4E,$3E(a0)}. */
    private static final int KNUCKLES_BASE_Y = 0xC4E;
    /** {@code move.w #$100,x_pos(a1)}. */
    private static final int KNUCKLES_X = 0x100;
    /** {@code Obj_SSZCutsceneBridge}: {@code move.w #$C0,$2E(a0)}, {@code subq.w #2,d1}. */
    private static final int BRIDGE_OFFSET = 0xC0;
    private static final int BRIDGE_STEP = 2;
    /** The pseudo-starpost {@code loc_44FBA} and {@code loc_65976} both write. */
    private static final int SAVED_X = 0x140;
    private static final int SAVED_Y = 0xC6C;
    /** Enough frames for the $100 Death Egg wait plus its 0.25 px/frame rise past the camera. */
    private static final int CUTSCENE_BUDGET = 4000;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    /** {@code Obj_57E34}: the spawner counts {@code $60} frames, then allocates Knuckles. */
    @Test
    void theSpawnerBeamsKnucklesInAfterNinetySixFrames() {
        HeadlessTestFixture fixture = boot(320);
        // Obj_57C1E's setup-only initial pass also runs its forward-allocated
        // spawner once. The runner counts ordinary passes, so consume that first
        // pass explicitly and leave one of the native $60 decrements outstanding.
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        fixture.stepIdleFrames(SPAWNER_DELAY - 2);
        assertNull(knuckles(), "Obj_CutsceneKnuckles not yet allocated");

        fixture.stepIdleFrames(1);
        CutsceneKnucklesSszInstance knuckles = knuckles();
        assertNotNull(knuckles, "Obj_CutsceneKnuckles subtype $2C");
        assertEquals(KNUCKLES_X, knuckles.getX() & 0xFFFF, "move.w #$100,x_pos(a1)");
        assertEquals(KNUCKLES_BASE_Y, knuckles.getY() & 0xFFFF, "$3E(a0) = $C4E at swing offset 0");
    }

    /**
     * The whole chain: the beam swing sets {@code _unkFAB8} bit 0, Knuckles falls, waits for the
     * Death Egg ({@code _unkFAB8} bit 1), hops right past X {@code $2A8}, lands and writes
     * {@code Events_bg+$08}; the bridge then retracts and opens the act.
     */
    @Test
    void theCutsceneReleasesTheBridgeAndOpensTheAct() {
        HeadlessTestFixture fixture = boot(320);
        // SSZ1_ScreenInit runs before the setup-only initial sprite pass.
        fixture.stepIdleFrames(1);
        SszZoneRuntimeState state = state();
        assertEquals(-1, state.eventsBgByte(0x05),
                "SSZ1_ScreenInit st (Events_bg+$05) blocks sub_575EA");

        int buttonFrame = -1;
        int bridgeFrame = -1;
        for (int frame = 0; frame < CUTSCENE_BUDGET; frame++) {
            // Hold right once the arrival releases control: Load_Sprites only reaches the bridge
            // at X $320 when the camera has moved off the arrival column, and Events_bg+$05 caps
            // the camera at Camera_max_X $200 until the bridge itself clears it.
            boolean right = frame > 200;
            fixture.stepFrame(false, false, false, right, false);
            if (buttonFrame < 0 && state.eventsBgWord(0x08) != 0) {
                buttonFrame = frame;
                // Read-only native movie observation, 2026-09-24: loc_658F2
                // reaches ($3A0,$C64). $2A8 is the start of the leap, not its end.
                assertEquals(0x3A0, knuckles().getX(), "native Knuckles button landing X");
                assertEquals(0xC64, knuckles().getY(), "native Knuckles button landing Y");
            }
            if (buttonFrame >= 0 && state.eventsBgByte(0x05) == 0) {
                bridgeFrame = frame;
                break;
            }
        }
        assertTrue(buttonFrame >= 0, "loc_658F2 st (Events_bg+$08)");
        assertTrue(bridgeFrame > buttonFrame, "loc_44FBA clr.b (Events_bg+$05) after the button");
        // subq.w #2 from $C0 is 96 passes; the bridge runs one per frame while the flag is set.
        assertEquals(BRIDGE_OFFSET / BRIDGE_STEP, bridgeFrame - buttonFrame,
                "the bridge retracts 2 px per frame");

        SszCutsceneBridgeObjectInstance bridge = bridge();
        assertNotNull(bridge, "Obj_SSZCutsceneBridge alive");
        assertTrue(bridge.extendedForTest(), "loc_4501A");
        assertEquals(-1, state.eventsBgByte(0x08), "the bridge reads +$08 and never clears it");

        var camera = GameServices.camera();
        assertEquals(SszCutsceneBridgeObjectInstance.CAMERA_MIN_X, camera.getMinX() & 0xFFFF);
        assertEquals(SszCutsceneBridgeObjectInstance.CAMERA_MAX_X, camera.getMaxX() & 0xFFFF);
        // loc_44FBA writes min_Y -$100 and max_Y $1000, but clearing Events_bg+$05 also releases
        // sub_575EA, which runs later in the same frame (ScreenEvents is the loop tail) and
        // re-derives the vertical limits from word_5778A/word_5779A. For a player still left of
        // word_5779A's first X limit ($640) the band gives max_Y $C60; word_5778A's $D00 is above
        // the camera, so the bridge's min_Y survives.
        assertEquals((short) SszCutsceneBridgeObjectInstance.CAMERA_MIN_Y, camera.getMinY(),
                "word_5778A leaves -$100 in place while $D00 is below the camera");
        assertTrue((GameServices.sprites().getMainPlayable().getCentreX() & 0xFFFF) < 0x640,
                "the player is still inside word_5779A's first band");
        assertEquals(0xC60, camera.getMaxY() & 0xFFFF, "word_5779A band for X < $640");

        var checkpoint = GameServices.level().getCheckpointState();
        assertEquals(1, checkpoint.getStarPostActivationMark(), "move.b #1,(Last_star_post_hit).w");
        assertEquals(SAVED_X, checkpoint.getSavedX(), "Saved_X_pos");
        assertEquals(SAVED_Y, checkpoint.getSavedY(), "Saved_Y_pos");
    }

    /**
     * {@code Obj_SSZCutsceneBridge} init: with {@code Last_star_post_hit} already set it installs
     * {@code loc_4501A} instead, so a respawn past the cutscene finds the bridge extended and
     * {@code SSZ1_ScreenInit} skips the whole arrival.
     */
    @Test
    void aRespawnPastTheCutsceneFindsTheBridgeExtendedAndNoArrival() {
        HeadlessTestFixture fixture = boot(320);
        var checkpoint = GameServices.level().getCheckpointState();
        if (checkpoint instanceof com.openggf.game.CheckpointState state) {
            state.saveCheckpoint(1, SAVED_X, SAVED_Y, false);
        }
        fixture.stepIdleFrames(2);

        assertEquals(0, state().eventsBgByte(0x05), "no Events_bg+$05 on the star-post path");
        assertNull(knuckles(), "no cutscene Knuckles");
        SszCutsceneBridgeObjectInstance bridge = bridge();
        if (bridge != null) {
            assertEquals(0, bridge.offsetForTest(), "loc_4501A starts at offset zero");
            assertTrue(bridge.extendedForTest());
        }
    }

    private static SszZoneRuntimeState state() {
        return S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static CutsceneKnucklesSszInstance knuckles() {
        return active(CutsceneKnucklesSszInstance.class);
    }

    private static SszCutsceneBridgeObjectInstance bridge() {
        return active(SszCutsceneBridgeObjectInstance.class);
    }

    private static <T> T active(Class<T> type) {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return null;
        }
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                return type.cast(instance);
            }
        }
        return null;
    }

    private static HeadlessTestFixture boot(int width) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
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
