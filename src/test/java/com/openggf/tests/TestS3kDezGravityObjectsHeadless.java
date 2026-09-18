package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezGravitySwapObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
