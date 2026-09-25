package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezGravitySwapObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezGravitySwitchObjectInstance;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.graphics.RenderPriority;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 3's first writer: SKL {@code $5B}, {@code Obj_DEZGravitySwap}
 * (sonic3k.asm:95472-95543).
 *
 * <p>Eleven of these sit in Death Egg act 2's open corridors, six unflipped and five
 * X-flipped. They are the objects that make {@code Reverse_gravity_flag} reachable in
 * ordinary play: everything slice 2 implemented was until now only observable through a
 * test-only call to {@code setReverseGravityActive}.
 *
 * <p>Expectations come from the two crossing bodies, which are near-copies of each other
 * with two branch conditions inverted:
 *
 * <ul>
 *   <li>{@code sub_49228} (:95489-95517), the left-to-right crossing, writes
 *       {@code Reverse_gravity_flag = 0} and then {@code = 1} only when
 *       {@code btst #0,render_flags(a0)} is <em>clear</em> ({@code bne.s locret}).</li>
 *   <li>{@code loc_49270} (:95518-95542), the right-to-left crossing, writes 0 and then 1
 *       only when the same bit is <em>set</em> ({@code beq.s locret}).</li>
 * </ul>
 *
 * <p>So one placement does both jobs: crossing it one way turns gravity on and crossing
 * it back turns it off, and the flip bit chooses which way is which. It is a write, never
 * a toggle — the {@code move.b #0} runs first on both paths.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityObjectsHeadless {

    /** An open stretch of Death Egg act 2, clear of the measured corridor terrain. */
    private static final int OBJECT_X = 0x1900;
    private static final int OBJECT_Y = 0x0540;

    /** ROM {@code move.w #$20,$30(a0)} (:95473). */
    private static final int BAND_HALF_HEIGHT = 0x20;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * Unflipped, left to right: {@code btst #0,render_flags(a0)} is clear, so
     * {@code sub_49228} falls through to {@code move.b #1,(Reverse_gravity_flag).w}.
     */
    @Test
    void anUnflippedSwapTurnsGravityOnWhenCrossedLeftToRight() {
        assertTrue(cross(0, Direction.LEFT_TO_RIGHT, 0, false),
                "sub_49228 :95514 sets the flag when render_flags bit 0 is clear");
    }

    /** And crossing the same placement back clears it: {@code loc_49270}'s {@code beq.s locret}. */
    @Test
    void anUnflippedSwapTurnsGravityOffWhenCrossedRightToLeft() {
        assertFalse(cross(0, Direction.RIGHT_TO_LEFT, 0, true),
                "loc_49270 :95536 writes 0 and skips the set when bit 0 is clear");
    }

    /** X-flipped, the two directions swap roles. */
    @Test
    void anXFlippedSwapTurnsGravityOnWhenCrossedRightToLeft() {
        assertTrue(cross(1, Direction.RIGHT_TO_LEFT, 0, false),
                "loc_49270 :95539 sets the flag when render_flags bit 0 is set");
    }

    @Test
    void anXFlippedSwapTurnsGravityOffWhenCrossedLeftToRight() {
        assertFalse(cross(1, Direction.LEFT_TO_RIGHT, 0, true),
                "sub_49228 :95511 writes 0 and skips the set when bit 0 is set");
    }

    /**
     * The Y band. {@code d2 = y_pos - $20}, {@code d3 = y_pos + $20}, and
     * {@code cmp.w d2,d4 / blt} with {@code cmp.w d3,d4 / bge} (:95495-95507) return
     * without touching the flag outside {@code [y_pos - $20, y_pos + $20)}. A player who
     * runs past on another floor leaves the corridor's gravity alone.
     */
    @Test
    void crossingOutsideTheYBandDoesNotWriteTheFlag() {
        assertFalse(cross(0, Direction.LEFT_TO_RIGHT, BAND_HALF_HEIGHT, false),
                "y_pos + $20 is excluded by the bge");
        assertFalse(cross(0, Direction.LEFT_TO_RIGHT, -BAND_HALF_HEIGHT - 1, false),
                "one pixel above y_pos - $20 is excluded by the blt");
    }

    /** The band's own edges, so the test fails for an off-by-one in either direction. */
    @Test
    void theBandIncludesItsTopEdgeAndExcludesItsBottomEdge() {
        assertTrue(cross(0, Direction.LEFT_TO_RIGHT, -BAND_HALF_HEIGHT, false),
                "blt: y_pos - $20 exactly is inside the band");
        assertTrue(cross(0, Direction.LEFT_TO_RIGHT, BAND_HALF_HEIGHT - 1, false),
                "bge: y_pos + $1F is the last row inside it");
    }

    /**
     * The side latch is consumed whether or not the flag is written: {@code move.b #1,-1(a2)}
     * sits <em>before</em> the Y-band test (:95492-95495). A player who crosses out of the
     * band and then crosses back <em>in</em> it runs the opposite body, not a repeat of the
     * first one.
     */
    @Test
    void aCrossingOutsideTheBandStillConsumesTheSideLatch() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwapObjectInstance swap = place(0);

            // Seed on the left, then cross to the right well outside the band.
            moveTo(sprite, OBJECT_X - 0x40, OBJECT_Y);
            swap.update(0, sprite);
            moveTo(sprite, OBJECT_X + 0x40, OBJECT_Y + 0x200);
            swap.update(0, sprite);
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "precondition: the out-of-band crossing wrote nothing");

            // Now come back to the left, inside the band. If the latch had not been
            // consumed this would run sub_49228 again and set the flag instead.
            moveTo(sprite, OBJECT_X - 0x40, OBJECT_Y);
            swap.update(0, sprite);
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "loc_49270 ran, not sub_49228: the right-to-left body clears an unflipped swap");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * Player 2 is not watched. {@code Obj_DEZGravitySwap} passes
     * {@code lea (Player_1).w,a1} and has no second call (:95217-95219), unlike {@code $58},
     * which at least reads Player 2's contact bits. A sidekick crossing writes nothing.
     */
    @Test
    void theSidekickCrossingTheSwapWritesNothing() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite leader = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwapObjectInstance swap = place(0);

            // The leader is parked on the left for the whole test and never crosses.
            moveTo(leader, OBJECT_X - 0x40, OBJECT_Y);
            swap.update(0, leader);

            AbstractPlayableSprite sidekick = sidekickOf(fixture);
            assertNotNull(sidekick,
                    "the default Death Egg configuration must have a sidekick, or this test proves nothing");
            moveTo(sidekick, OBJECT_X + 0x40, OBJECT_Y);
            swap.update(0, leader);

            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "only Player_1's x_pos reaches sub_49228");
        } finally {
            SessionManager.clear();
        }
    }

    /** The flag is written, not toggled: two crossings the same way leave the same value. */
    @Test
    void theSwapWritesRatherThanToggles() {
        assertTrue(cross(0, Direction.LEFT_TO_RIGHT, 0, false), "first crossing sets it");
        assertTrue(cross(0, Direction.LEFT_TO_RIGHT, 0, true),
                "and a second left-to-right crossing sets it again, rather than toggling it off");
    }

    /**
     * The rewind spot for slice 3's first writer: capture <em>after</em> the object has set
     * the flag, run forward across a second crossing that clears it, restore, and replay the
     * same crossing. The restored state has to carry both halves — the global
     * {@code Reverse_gravity_flag} and the object's own {@code $32} side latch — or the replay
     * diverges: a restore that returned the flag but not the latch would run
     * {@code sub_49228} again and set the flag instead of clearing it.
     */
    @Test
    void theSwapsFlagAndSideLatchBothSurviveACaptureAndRestore() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwapObjectInstance swap = place(0);

            moveTo(sprite, OBJECT_X - 0x40, OBJECT_Y);
            swap.update(0, sprite);
            moveTo(sprite, OBJECT_X + 0x40, OBJECT_Y);
            swap.update(0, sprite);
            assertTrue(GameServices.gameState().isReverseGravityActive(),
                    "precondition: the object has set the flag before the capture");

            CompositeSnapshot checkpoint =
                    TestEnvironment.activeGameplayMode().getRewindRegistry().capture();

            // Forward: cross back, which clears the flag and flips the latch.
            moveTo(sprite, OBJECT_X - 0x40, OBJECT_Y);
            swap.update(0, sprite);
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "the forward run must actually change the state being restored");

            TestEnvironment.activeGameplayMode().getRewindRegistry().restore(checkpoint);
            assertTrue(GameServices.gameState().isReverseGravityActive(),
                    "restore must return the flag the object had written");

            // Replay the same crossing from the restored state.
            S3kDezGravitySwapObjectInstance restored = restoredSwap(swap);
            moveTo(sprite, OBJECT_X - 0x40, OBJECT_Y);
            restored.update(0, sprite);
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "forward replay from the restored latch clears the flag again, as the first run did");
        } finally {
            SessionManager.clear();
        }
    }

    /** The restore may hand back a recreated instance; find whichever one is live now. */
    private S3kDezGravitySwapObjectInstance restoredSwap(S3kDezGravitySwapObjectInstance original) {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezGravitySwapObjectInstance swap) {
                return swap;
            }
        }
        return original;
    }

    // ---------------------------------------------------------------- $58 gravity switch

    /**
     * SKL {@code $58}, {@code Obj_DEZGravitySwitch}: the five act 2 pressure pads.
     *
     * <p>The pad <strong>toggles</strong> rather than writes — {@code eori.b #1,
     * (Reverse_gravity_flag).w} at {@code loc_48B7E} (sonic3k.asm:94879) — and it does so
     * on the <em>fourth</em> update after the press, not the press frame:
     * {@code move.w #3,$30(a0)} (:94822) then {@code subq.w #1,$30 / bpl} (:94877-94878),
     * so the counter runs 3, 2, 1, 0 and toggles on the update that takes it negative.
     */
    @Test
    void theGravitySwitchTogglesOnTheFourthUpdateAfterThePress() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);

            pressPad(pad, sprite);
            assertTrue(pad.isPressed(), "the press must move the pad out of its armed routine");

            for (int update = 1; update <= 3; update++) {
                pad.update(0, sprite);
                assertFalse(GameServices.gameState().isReverseGravityActive(),
                        "update " + update + " after the press must not have toggled yet");
            }
            pad.update(0, sprite);
            assertTrue(GameServices.gameState().isReverseGravityActive(),
                    "loc_48B7E toggles on the fourth update, when $30 goes negative");
        } finally {
            SessionManager.clear();
        }
    }

    /** And it is a toggle: a second press turns gravity back off. */
    @Test
    void asecondPressOfTheGravitySwitchTogglesBack() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(true);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);

            pressPad(pad, sprite);
            for (int update = 0; update < 4; update++) {
                pad.update(0, sprite);
            }
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "eori.b #1 on a set flag clears it; $5B would have written a fixed value");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * The rearm cannot start while the pad is still occupied. {@code loc_48B9C}
     * (:94892-94896) tests {@code Status_OnObj(a0)} and resets {@code $30} to 0 every frame
     * it is set, so a player who presses the pad and never leaves keeps it down.
     */
    @Test
    void theGravitySwitchDoesNotRearmWhileItIsStillOccupied() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);

            pressPad(pad, sprite);
            for (int update = 0; update < 4; update++) {
                pad.update(0, sprite);
            }
            assertTrue(GameServices.gameState().isReverseGravityActive(), "precondition: toggled");

            // Keep reporting contact for far longer than the 20-frame rearm, and count the
            // flag's transitions rather than sampling it at the end. Sampling cannot tell
            // "never rearmed" from "rearmed, was pressed again and toggled twice more": both
            // leave isPressed() true, and with a 24-frame cycle both can leave the flag true.
            int transitions = 0;
            boolean previous = GameServices.gameState().isReverseGravityActive();
            for (int frame = 0; frame < 120; frame++) {
                pad.onSolidContact(sprite, STANDING_ON_PAD, frame);
                pad.update(0, sprite);
                boolean now = GameServices.gameState().isReverseGravityActive();
                if (now != previous) {
                    transitions++;
                    previous = now;
                }
            }
            assertEquals(0, transitions,
                    "$30 is reset to 0 every frame the pad is stood on, so it never rearms "
                            + "and never toggles again while it is held down");
            assertTrue(pad.isPressed(), "and it stays in its pressed routine throughout");
        } finally {
            SessionManager.clear();
        }
    }

    /** Once clear, the pad rearms twenty frames later ({@code move.w #20-1,$30}, :94880). */
    @Test
    void theGravitySwitchRearmsTwentyFramesAfterItIsFree() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);

            pressPad(pad, sprite);
            for (int update = 0; update < 4; update++) {
                pad.update(0, sprite);
            }
            for (int frame = 0; frame < 19; frame++) {
                pad.update(0, sprite);
                assertTrue(pad.isPressed(), "frame " + frame + " is still inside the 20-frame rearm");
            }
            pad.update(0, sprite);
            assertFalse(pad.isPressed(), "the twentieth free update rearms it");
            assertEquals(0, pad.mappingFrameForTest(), "and restores mapping_frame 0");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * The press releases the rider: {@code sub_48B40} (:94848-94861) zeroes
     * {@code ground_vel}, {@code x_vel} and {@code y_vel}, sets {@code Status_InAir} and
     * clears {@code Status_OnObj} — so the player falls off the pad it just pressed, which
     * is what stops it being pressed again on the next frame.
     */
    @Test
    void theGravitySwitchReleasesTheRiderThatPressedIt() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);

            sprite.setGSpeed((short) 0x0600);
            sprite.setXSpeed((short) 0x0600);
            sprite.setYSpeed((short) 0x0200);
            sprite.setAir(false);
            sprite.setOnObject(true);

            pressPad(pad, sprite);

            assertEquals(0, sprite.getGSpeed(), "sub_48B40: move.w d1,ground_vel(a1)");
            assertEquals(0, sprite.getXSpeed(), "sub_48B40: move.w d1,x_vel(a1)");
            assertEquals(0, sprite.getYSpeed(), "sub_48B40: move.w d1,y_vel(a1)");
            assertTrue(sprite.getAir(), "sub_48B40: bset #Status_InAir,status(a1)");
            assertFalse(sprite.isOnObject(), "sub_48B40: bclr #Status_OnObj,status(a1)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * The pad answers to its underside too. {@code andi.w #$14,d0} (:94817) is Player 1's
     * top <em>and</em> bottom contact bits, which is the whole reason a gravity switch
     * works at all: once gravity is inverted the player reaches it from the other face.
     */
    @Test
    void theGravitySwitchIsPressedFromItsUndersideAsWellAsItsTop() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);

            pad.onSolidContact(sprite, BONKED_FROM_BELOW, 0);
            pad.update(0, sprite);
            assertTrue(pad.isPressed(), "$14 covers the bottom bit, not just the top one");
        } finally {
            SessionManager.clear();
        }
    }

    /** A side scrape is not a press: {@code $14} has no side bit. */
    @Test
    void brushingTheGravitySwitchSideDoesNotPressIt() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);

            pad.onSolidContact(sprite, PUSHED_SIDEWAYS, 0);
            for (int update = 0; update < 6; update++) {
                pad.update(0, sprite);
            }
            assertFalse(pad.isPressed(), "a side contact is outside the $14 mask");
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "and nothing toggled");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * Contact callbacks can remain pending at the frame boundary. Both the initial
     * press and the occupied rearm need to survive restore before the next update.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void gravityPadContactAwaitingItsNextUpdateSurvivesRestore(boolean rearming) {
        HeadlessTestFixture fixture = fixture();
        try {
            var sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            var pad = placeSwitch(2);
            if (rearming) {
                pressPad(pad, sprite);
                for (int i = 0; i < 4; i++) pad.update(0, sprite);
                pad.onSolidContact(sprite, STANDING_ON_PAD, 0);
                pad.update(0, sprite); // occupied rearm counter is now zero
            }
            // Solid callbacks can run after this object's update. These contact
            // latches are then live across a frame-boundary snapshot.
            pad.onSolidContact(sprite, STANDING_ON_PAD, 1);
            var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
            var saved = registry.capture();
            pad.update(0, sprite);
            int expectedY = pad.getY();
            assertTrue(pad.isPressed());
            registry.restore(saved);
            var restored = restoredSwitch(pad);
            restored.update(0, sprite);
            assertTrue(restored.isPressed(),
                    "restore must retain the queued press/occupied contact, not rearm or miss a press");
            assertEquals(expectedY, restored.getY());
            assertEquals(rearming, GameServices.gameState().isReverseGravityActive());
        } finally {
            SessionManager.clear();
        }
    }

    @Test
    void theGravitySwitchCounterSurvivesACaptureAndRestore() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);

            pressPad(pad, sprite);
            pad.update(0, sprite);   // one of the three pre-toggle updates
            CompositeSnapshot midCount =
                    TestEnvironment.activeGameplayMode().getRewindRegistry().capture();

            for (int update = 0; update < 3; update++) {
                pad.update(0, sprite);
            }
            assertTrue(GameServices.gameState().isReverseGravityActive(),
                    "the forward run must cross the toggle before the restore");

            TestEnvironment.activeGameplayMode().getRewindRegistry().restore(midCount);
            assertFalse(GameServices.gameState().isReverseGravityActive(),
                    "restore must return the pre-toggle flag");

            S3kDezGravitySwitchObjectInstance restored = restoredSwitch(pad);
            for (int update = 0; update < 2; update++) {
                restored.update(0, sprite);
                assertFalse(GameServices.gameState().isReverseGravityActive(),
                        "replay must still need the same number of updates");
            }
            restored.update(0, sprite);
            assertTrue(GameServices.gameState().isReverseGravityActive(),
                    "and toggles on the same update of the replay as of the first run");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code moveq #signextendB(sfx_Transporter),d0 / jsr (Play_SFX).l} (:94832-94833),
     * inside the press branch and nowhere else. {@code sfx_Transporter} is {@code $73}
     * (sonic3k.constants.asm:1560), which the engine already carries as
     * {@link com.openggf.game.sonic3k.audio.Sonic3kSfx#TRANSPORTER}.
     *
     * <p>The press frame is the only frame that plays it: the toggle four updates later is
     * silent, and so is the rearm.
     */
    @Test
    void theGravitySwitchPlaysTheTransporterSoundOnThePressFrameOnly() {
        HeadlessTestFixture fixture = fixture();
        AudioManager audioManager = AudioManager.getInstance();
        List<Integer> requested = new ArrayList<>();
        try {
            audioManager.setRequestObserver((requestClass, rawSoundId) -> requested.add(rawSoundId));
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);

            pressPad(pad, sprite);
            assertEquals(List.of(Sonic3kSfx.TRANSPORTER.id), requested,
                    "the press frame plays sfx_Transporter ($73) exactly once");

            requested.clear();
            for (int update = 0; update < 24; update++) {
                pad.update(0, sprite);
            }
            assertTrue(GameServices.gameState().isReverseGravityActive(),
                    "precondition: those updates crossed the toggle");
            assertEquals(List.of(), requested,
                    "the toggle and the rearm are silent; only the press calls Play_SFX");
        } finally {
            audioManager.setRequestObserver(null);
            SessionManager.clear();
        }
    }

    /**
     * {@code move.b #1,mapping_frame(a0)} on the press (:94821) and back to 0 when the pad
     * rises (:94902). Frame 1 is {@code word_48C08}, the two-piece pressed pose.
     */
    @Test
    void theGravitySwitchShowsItsPressedMappingFrameWhilePressed() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(false);
            S3kDezGravitySwitchObjectInstance pad = placeSwitch(0);
            assertEquals(0, pad.mappingFrameForTest(), "armed is frame 0");

            pressPad(pad, sprite);
            assertEquals(1, pad.mappingFrameForTest(), "the press shows the sunken pose");
            assertEquals(RenderPriority.fromS3kWord(0x280), pad.getPriorityBucket(),
                    "move.w #$280,priority(a0) (:94805)");

            for (int update = 0; update < 40 && pad.isPressed(); update++) {
                pad.update(0, sprite);
            }
            assertFalse(pad.isPressed(), "precondition: the pad rearmed");
            assertEquals(0, pad.mappingFrameForTest(), "and is drawn armed again");
        } finally {
            SessionManager.clear();
        }
    }

    private static final SolidContact STANDING_ON_PAD =
            new SolidContact(true, false, false, true, false);
    private static final SolidContact BONKED_FROM_BELOW =
            new SolidContact(false, false, true, false, false);
    private static final SolidContact PUSHED_SIDEWAYS =
            new SolidContact(false, true, false, false, true);

    /** Reports a standing contact and runs the pad's update, which is the press frame. */
    private void pressPad(S3kDezGravitySwitchObjectInstance pad, AbstractPlayableSprite sprite) {
        pad.onSolidContact(sprite, STANDING_ON_PAD, 0);
        pad.update(0, sprite);
    }

    private S3kDezGravitySwitchObjectInstance placeSwitch(int renderFlags) {
        ObjectSpawn spawn = new ObjectSpawn(0x1AAC, 0x0588, 0x58, 0, renderFlags,
                false, 0x0588, -1);
        S3kDezGravitySwitchObjectInstance pad = new S3kDezGravitySwitchObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(pad);
        return pad;
    }

    private S3kDezGravitySwitchObjectInstance restoredSwitch(
            S3kDezGravitySwitchObjectInstance original) {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezGravitySwitchObjectInstance pad) {
                return pad;
            }
        }
        return original;
    }

    private enum Direction { LEFT_TO_RIGHT, RIGHT_TO_LEFT }

    /**
     * Seeds the latch on the starting side, crosses, and returns the flag afterwards.
     *
     * @param initialFlag the value the flag holds before the crossing, so that a body that
     *                    wrote nothing is distinguishable from one that wrote the same value
     */
    private boolean cross(int renderFlags, Direction direction, int yOffset, boolean initialFlag) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            GameServices.gameState().setReverseGravityActive(initialFlag);
            S3kDezGravitySwapObjectInstance swap = place(renderFlags);

            int startX = direction == Direction.LEFT_TO_RIGHT ? OBJECT_X - 0x40 : OBJECT_X + 0x40;
            int endX = direction == Direction.LEFT_TO_RIGHT ? OBJECT_X + 0x40 : OBJECT_X - 0x40;

            moveTo(sprite, startX, OBJECT_Y + yOffset);
            swap.update(0, sprite);
            assertEquals(initialFlag, GameServices.gameState().isReverseGravityActive(),
                    "the seeding frame must not write the flag");

            moveTo(sprite, endX, OBJECT_Y + yOffset);
            swap.update(0, sprite);
            return GameServices.gameState().isReverseGravityActive();
        } finally {
            SessionManager.clear();
        }
    }

    private S3kDezGravitySwapObjectInstance place(int renderFlags) {
        ObjectSpawn spawn = new ObjectSpawn(OBJECT_X, OBJECT_Y, 0x5B, 0, renderFlags,
                false, OBJECT_Y, -1);
        S3kDezGravitySwapObjectInstance swap = new S3kDezGravitySwapObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(swap);
        return swap;
    }

    private void moveTo(AbstractPlayableSprite sprite, int centreX, int centreY) {
        NativePositionOps.writeXPosResetSubpixel(sprite, centreX);
        NativePositionOps.writeYPosResetSubpixel(sprite, centreY);
    }

    private AbstractPlayableSprite sidekickOf(HeadlessTestFixture fixture) {
        var sidekicks = GameServices.sprites().getSidekicks();
        return sidekicks.isEmpty() ? null : sidekicks.getFirst();
    }

    private HeadlessTestFixture fixture() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .build();
    }
}
