package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezEnergyBridgeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKL {@code $55}, {@code Obj_DEZEnergyBridge} (sonic3k.asm:93909-93990) and its subtype
 * decoder {@code sub_47DDE} (:93879-93902).
 *
 * <p>Every expected number is a literal from the ROM listing or from the decoded
 * {@code DEZ2_Sprites} layout. The period table, the phase step and the on-duration are
 * written out rather than recomputed from the object's own fields.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezEnergyBridgeHeadless {

    /** {@code DEZ2_Sprites} record 5: {@code $0400,$03E8}, subtype {@code $01}, no flips. */
    private static final int OBJECT_X = 0x0400;
    private static final int OBJECT_Y = 0x03E8;
    private static final int SUBTYPE = 0x01;
    /** {@code ((1 & 3) + 2) << 5}. */
    private static final int SUBTYPE_01_ON_DURATION = 0x60;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * {@code sub_47DDE} :93884-93896. Bits 2-3 pick the period out of {@code word_47DD6}
     * ({@code $7F}, {@code $FF}, {@code $1FF}, {@code $3FF}), bits 4-7 are a phase index scaled
     * by a sixteenth of the period, and bits 0-1 give the on-duration {@code ((n + 2) << 5)}.
     */
    @Test
    void theSubtypeDecodesIntoAPeriodAPhaseAndAnOnDuration() {
        HeadlessTestFixture fixture = fixture();
        try {
            assertDecode(0x00, 0x7F, 0x000, 0x40);
            assertDecode(0x01, 0x7F, 0x000, 0x60);
            assertDecode(0x02, 0x7F, 0x000, 0x80);
            assertDecode(0x03, 0x7F, 0x000, 0xA0);
            assertDecode(0x04, 0xFF, 0x000, 0x40);
            assertDecode(0x08, 0x1FF, 0x000, 0x40);
            assertDecode(0x0C, 0x3FF, 0x000, 0x40);
            // High nibble 3, period $7F: a sixteenth of $80 is 8, so the phase is $18.
            assertDecode(0x31, 0x7F, 0x018, 0x60);
            // High nibble $F, period $1FF: a sixteenth of $200 is $20, so the phase is $1E0.
            assertDecode(0xF8, 0x1FF, 0x1E0, 0x40);
        } finally {
            SessionManager.clear();
        }
    }

    /** {@code move.b #$40,width_pixels(a0)} and {@code move.w #9,d3} (:93911, :93941). */
    @Test
    void theSolidBoxIsTheRomsArgumentsToSolidObjectTop() {
        HeadlessTestFixture fixture = fixture();
        try {
            S3kDezEnergyBridgeObjectInstance bridge = place(SUBTYPE, 0x2000);
            assertEquals(0x40, bridge.getSolidParams().halfWidth(), "d1 = width_pixels = $40");
            assertEquals(9, bridge.getSolidParams().airHalfHeight(), "d3 = 9");
            assertEquals(9, bridge.getSolidParams().groundHalfHeight(), "d3 = 9");
            assertTrue(bridge.isTopSolidOnly(), "jsr (SolidObjectTop).l (:93942)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_1E45A} :42000-42007. {@code cmpi.w #-$10,d0 / blo} is an unsigned compare, so
     * it rejects every {@code d0} below {@code $FFF0} — including zero. The accepted window is
     * {@code -$10 <= d0 <= -1} and the exact surface boundary is not a landing. This is the
     * frame the DEZ act 2 route turns on: native row 20299 leaves the player airborne with
     * their feet exactly level with the surface.
     */
    @Test
    void theExactSurfaceBoundaryIsNotALanding() {
        HeadlessTestFixture fixture = fixture();
        try {
            assertTrue(place(SUBTYPE, 0x2000).rejectsZeroDistanceTopSolidLanding(),
                    "cmpi.w #-$10,d0 / blo rejects d0 == 0");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code Obj_DEZEnergyBridge} :93914-93925. {@code sub.w d1,d0 / bcc} is an unsigned
     * subtraction of the on-duration from the phase, so a borrow means the phase is still
     * inside the on window; the object then enters that window part-used and decrements on the
     * same update, without waiting a frame.
     */
    @Test
    void aBridgeThatSpawnsInsideItsWindowStartsPartUsed() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            // Level_frame_counter $2010: phase $10, so $60 - $10 = $50 frames remain, less the
            // decrement the same update.
            S3kDezEnergyBridgeObjectInstance bridge = place(SUBTYPE, 0x2010);
            bridge.update(0, sprite);
            assertTrue(bridge.isOnForTest(), "the borrow path installs loc_47E8C");
            assertEquals(0x4F, bridge.onFramesLeftForTest(),
                    "$34 = $60 - $10, then subq.w #1 on the same update");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_47E5C} / {@code loc_47E62} :93927-93933. A phase at or past the on-duration
     * installs the off routine, which falls straight through and runs on the same update; it
     * then waits for the phase to reach zero.
     */
    @Test
    void aBridgeThatSpawnsOutsideItsWindowWaitsForThePhaseToWrap() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            // $2060: phase $60, exactly the on-duration, which is the no-borrow path.
            S3kDezEnergyBridgeObjectInstance bridge = place(SUBTYPE, 0x2060);
            for (int counter = 0x2060; counter < 0x2080; counter++) {
                step(bridge, sprite, counter);
                assertFalse(bridge.isOnForTest(), "counter " + Integer.toHexString(counter));
            }
            step(bridge, sprite, 0x2080);
            assertTrue(bridge.isOnForTest(), "phase $00 at $2080 turns it on");
            assertEquals(SUBTYPE_01_ON_DURATION - 1, bridge.onFramesLeftForTest(),
                    "loc_47E76 falls through into loc_47E8C, so it also decrements");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_47E8C} :93942-93944. The update on which {@code $34} reaches zero branches
     * past the {@code SolidObjectTop} call, so a {@code $60}-frame window is solid for
     * {@code $5F} updates and not the last one.
     */
    @Test
    void theWindowIsSolidForEveryUpdateButTheOneThatEndsIt() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezEnergyBridgeObjectInstance bridge = place(SUBTYPE, 0x2000);
            int solidUpdates = 0;
            for (int counter = 0x2000; counter < 0x2080; counter++) {
                step(bridge, sprite, counter);
                if (bridge.isSolidFor(sprite)) {
                    solidUpdates++;
                }
            }
            assertEquals(SUBTYPE_01_ON_DURATION - 1, solidUpdates,
                    "$60 - 1 solid updates out of the $80-frame period");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code sub_47EE8} :93977-93984: the object clears its own standing bit and, only if it
     * was set, clears the player's {@code Status_OnObj} and sets {@code Status_InAir}.
     */
    @Test
    void theWindowEndingPushesItsRidersOff() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezEnergyBridgeObjectInstance bridge = place(SUBTYPE, 0x2000);
            // $34 starts at $60 and the update that places the bridge also decrements it, so
            // it reaches 1 after 95 updates and zero on the 96th, at counter $205F.
            for (int counter = 0x2000; counter < 0x205F; counter++) {
                step(bridge, sprite, counter);
                bridge.onSolidContact(sprite, new SolidContact(true, false, false, true, false), 0);
            }
            sprite.setAir(false);
            sprite.setOnObject(true);
            assertTrue(bridge.isOnForTest(), "precondition: still inside the window");
            assertEquals(1, bridge.onFramesLeftForTest(), "precondition: one update left");

            step(bridge, sprite, 0x205F);

            assertFalse(bridge.isOnForTest(), "$34 reached zero");
            assertTrue(bridge.drawPublishedForTest(), "expiry still reaches Sprite_OnScreen_Test");
            assertTrue(sprite.getAir(), "bset #Status_InAir,status(a1) (:93981)");
            assertFalse(sprite.isOnObject(), "bclr #Status_OnObj,status(a1) (:93980)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_47EBE} :93945-93947: {@code Level_frame_counter+1} is the counter's low byte,
     * so the frame is the counter and 3. {@code Map_DEZEnergyBridge} has exactly four.
     */
    @Test
    void theMappingFrameIsTheLevelFrameCounterAndThree() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezEnergyBridgeObjectInstance bridge = place(SUBTYPE, 0x2000);
            for (int counter = 0x2000; counter < 0x2008; counter++) {
                step(bridge, sprite, counter);
                assertEquals(counter & 3, bridge.mappingFrameForTest(),
                        "counter " + Integer.toHexString(counter));
            }
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_47EBE} :93948-93953: {@code sfx_EnergyZap} ({@code $A8}) every eighth frame
     * while the object is on screen. The off routine never reaches this code, so a bridge
     * outside its window is silent.
     */
    @Test
    void theZapSoundsEveryEighthFrameWhileTheBridgeIsOnAndSilentWhileItIsOff() {
        HeadlessTestFixture fixture = fixture();
        AudioManager audioManager = AudioManager.getInstance();
        List<Integer> requested = new ArrayList<>();
        try {
            audioManager.setRequestObserver((requestClass, rawSoundId) -> requested.add(rawSoundId));
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezEnergyBridgeObjectInstance bridge = onScreenBridge(sprite, 0x2000);
            for (int counter = 0x2000; counter < 0x2010; counter++) {
                step(bridge, sprite, counter);
            }
            assertEquals(List.of(Sonic3kSfx.ENERGY_ZAP.id), requested,
                    "initial carried render flag is clear; only $2008 zaps after rendering");

            // A second bridge that spawns outside the window. $34 counts down independently of
            // the phase, so the first one is still mid-window and cannot answer this.
            requested.clear();
            S3kDezEnergyBridgeObjectInstance offBridge = onScreenBridge(sprite, 0x2060);
            for (int counter = 0x2060; counter < 0x2080; counter++) {
                step(offBridge, sprite, counter);
            }
            assertFalse(offBridge.isOnForTest(), "precondition: it never opened");
            assertEquals(List.of(), requested, "the off routine returns before loc_47EBE");
        } finally {
            audioManager.setRequestObserver(null);
            SessionManager.clear();
        }
    }

    /**
     * The off routine jumps to {@code Delete_Sprite_If_Not_In_Range} (:93933) without reaching
     * the {@code SolidObjectTop} call.
     *
     * <p>Coverage limit, stated rather than faked: the ROM also skips the display on that path,
     * and the class records a separate draw-publication bit, but a headless fixture
     * has no pattern renderer, so an assertion that the command list is empty would pass
     * whatever the gate did. This suite checks publication, including the distinct
     * expiry dispatch; ROM-art checks and captures cover the renderer.
     */
    @Test
    void anOffBridgeIsNotSolid() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezEnergyBridgeObjectInstance bridge = place(SUBTYPE, 0x2060);
            step(bridge, sprite, 0x2060);
            assertFalse(bridge.isOnForTest(), "the phase is outside the window");
            assertFalse(bridge.isSolidFor(sprite), "no SolidObjectTop call while off");
            assertFalse(bridge.drawPublishedForTest());
        } finally {
            SessionManager.clear();
        }
    }

    /** The routine, the countdown and the mapping frame are object state a rewind carries. */
    @Test
    void theWindowStateSurvivesARewind() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezEnergyBridgeObjectInstance bridge = place(SUBTYPE, 0x2000);
            for (int counter = 0x2000; counter < 0x2010; counter++) {
                step(bridge, sprite, counter);
            }
            assertEquals(SUBTYPE_01_ON_DURATION - 0x10, bridge.onFramesLeftForTest(),
                    "precondition: sixteen updates in");
            CompositeSnapshot halfway =
                    TestEnvironment.activeGameplayMode().getRewindRegistry().capture();

            for (int counter = 0x2010; counter < 0x2030; counter++) {
                step(bridge, sprite, counter);
            }
            assertEquals(SUBTYPE_01_ON_DURATION - 0x30, bridge.onFramesLeftForTest(),
                    "precondition: the forward run moved on");

            TestEnvironment.activeGameplayMode().getRewindRegistry().restore(halfway);
            S3kDezEnergyBridgeObjectInstance back = restored(bridge);
            assertEquals(SUBTYPE_01_ON_DURATION - 0x10, back.onFramesLeftForTest(),
                    "$34 comes back");
            assertTrue(back.isOnForTest(), "and so does the installed routine");
        } finally {
            SessionManager.clear();
        }
    }

    // --- helpers ---

    private void assertDecode(int subtype, int mask, int phase, int onDuration) {
        S3kDezEnergyBridgeObjectInstance bridge = place(subtype, 0x2000);
        String label = "subtype $" + Integer.toHexString(subtype);
        assertEquals(mask, bridge.periodMaskForTest(), label + " word_47DD6 entry");
        assertEquals(phase, bridge.phaseOffsetForTest(), label + " $30(a0)");
        assertEquals(onDuration, bridge.onDurationForTest(), label + " on-duration");
    }

    private void step(S3kDezEnergyBridgeObjectInstance bridge, AbstractPlayableSprite sprite,
                      int levelFrameCounter) {
        GameServices.level().setFrameCounter(levelFrameCounter);
        bridge.update(levelFrameCounter, sprite);
        bridge.refreshPostCameraRenderState();
    }

    private S3kDezEnergyBridgeObjectInstance place(int subtype, int levelFrameCounter) {
        GameServices.level().setFrameCounter(levelFrameCounter);
        return place(OBJECT_X, OBJECT_Y, subtype);
    }

    /** A bridge in the middle of the camera, so the {@code render_flags} bit 7 zap gate passes. */
    private S3kDezEnergyBridgeObjectInstance onScreenBridge(AbstractPlayableSprite sprite,
                                                            int levelFrameCounter) {
        GameServices.level().setFrameCounter(levelFrameCounter);
        com.openggf.level.objects.AbstractObjectInstance
                .updateCameraBounds(0, 0, 320, 224, 0);
        return place(160, 112, SUBTYPE);
    }

    private S3kDezEnergyBridgeObjectInstance place(int x, int y, int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(x, y, 0x55, subtype, 0, false, y, -1);
        S3kDezEnergyBridgeObjectInstance bridge = new S3kDezEnergyBridgeObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(bridge);
        return bridge;
    }

    private S3kDezEnergyBridgeObjectInstance restored(S3kDezEnergyBridgeObjectInstance original) {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezEnergyBridgeObjectInstance bridge) {
                return bridge;
            }
        }
        return original;
    }

    private HeadlessTestFixture fixture() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
    }
}
