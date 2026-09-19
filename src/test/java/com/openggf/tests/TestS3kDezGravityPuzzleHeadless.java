package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezGravityPuzzleObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SKL {@code $61}, {@code Obj_DEZGravityPuzzle} (sonic3k.asm:96087-96245): the bobbing shaft in
 * Death Egg act 1's turbine room, with six pressable marker panels and a bumper launch.
 *
 * <p>Every expected number here is a literal from the ROM listing, not a call back into the
 * object's own arithmetic: the {@code $5F} session found two assertions that reused the object's
 * helper with the test's own copies of its constants and could therefore never disagree with it.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezGravityPuzzleHeadless {

    /** {@code DEZ1_Sprites} record 301: the act 1 placement, subtype {@code $00}. */
    private static final int OBJECT_X = 0x2690;
    private static final int OBJECT_Y = 0x0840;

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    /**
     * {@code move.w #$23,d1 / move.w #$30,d2 / move.w #$31,d3 / jsr (SolidObjectFull2).l}
     * (:96121-96124). {@code d3} is one greater than {@code d2}, which is what a
     * {@code SolidObjectFull} caller passes so the grounded box reaches a pixel further down.
     */
    @Test
    void theSolidBoxIsTheRomsArgumentsToSolidObjectFull2() {
        HeadlessTestFixture fixture = fixture();
        try {
            S3kDezGravityPuzzleObjectInstance puzzle = place();
            assertEquals(0x23, puzzle.getSolidParams().halfWidth(), "d1 = $23");
            assertEquals(0x30, puzzle.getSolidParams().airHalfHeight(), "d2 = $30");
            assertEquals(0x31, puzzle.getSolidParams().groundHalfHeight(), "d3 = $31");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_49986} :96104-96110. The sine of the <em>current</em> angle is taken and the
     * angle is advanced afterwards, so the first update sits at the stored centre. The ROM sine
     * table's first entries are 0, 6, 12, 18, 25, 31 (Levels/Misc/sine.bin), and
     * {@code asr.w #2} floors each of them: 0, 1, 3, 4, 6, 7.
     */
    @Test
    void theShaftBobsOnTheQuarterScaledSineTable() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityPuzzleObjectInstance puzzle = place();
            int[] expected = { 0, 1, 3, 4, 6, 7 };
            for (int update = 0; update < expected.length; update++) {
                puzzle.update(update, sprite);
                assertEquals(OBJECT_Y + expected[update], puzzle.getY(),
                        "update " + update + ": sine.bin entry " + update + " asr 2");
            }
            assertEquals(expected.length, puzzle.bobAngleForTest(),
                    "addq.b #1,angle(a0) once per update (:96105)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code sub_49A0E} :96201-96218. The row is {@code y_pos(a1) - y_pos(a0) + $30} floored at
     * zero and shifted right by five; the column adds 3 when the unsigned
     * {@code x_pos(a1) - x_pos(a0)} does not borrow, so a player exactly level with the shaft
     * counts as being on its right.
     */
    @Test
    void thePanelIsTheClampedRowPlusThreeOnTheRight() {
        assertEquals(0, pushedPanelBit(-0x40, -1), "dy -$40 floors at 0: top left");
        assertEquals(0, pushedPanelBit(-0x30, -1), "dy -$30 is exactly 0: top left");
        assertEquals(0, pushedPanelBit(-0x11, -1), "dy -$11 gives $1F, still row 0");
        assertEquals(1, pushedPanelBit(-0x10, -1), "dy -$10 gives $20: row 1");
        assertEquals(1, pushedPanelBit(0x00, -1), "level with the shaft is row 1");
        assertEquals(2, pushedPanelBit(0x10, -1), "dy $10 gives $40: row 2");
        assertEquals(3, pushedPanelBit(-0x40, 0), "dx 0 does not borrow, so it is the right column");
        assertEquals(4, pushedPanelBit(0x00, +1), "right column, row 1");
        assertEquals(5, pushedPanelBit(0x10, +1), "right column, row 2");
    }

    /**
     * {@code cmpi.w #$60,d0 / blo.s loc_49A34 / moveq #$40,d0} (:96212-96214). The replacement
     * is {@code $40}, <em>not</em> the limit: {@code $60 >> 5} would be 3, which is the right
     * column's first panel, so a low player on the left would mark a right-hand panel.
     */
    @Test
    void theRowClampIsFortyNotSixty() {
        assertEquals(2, pushedPanelBit(0x30, -1),
                "dy $30 gives exactly $60 and is replaced with $40: row 2, left column");
        assertEquals(2, pushedPanelBit(0x200, -1),
                "and so is anything further down");
        assertNotEquals(3, pushedPanelBit(0x30, -1),
                "a $60 replacement would have crossed into the right column");
    }

    /**
     * {@code bset d0,(MHZ_pollen_counter).w} (:96229) with the {@code cmpi.b #3 / blo} frame
     * guard (:96232-96233). Mushroom Hill's particle counter is the puzzle's panel bitfield;
     * a second push on the same panel leaves it exactly as it was.
     */
    @Test
    void aSecondPushOnTheSamePanelChangesNothing() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityPuzzleObjectInstance puzzle = place();
            moveTo(sprite, OBJECT_X - 1, OBJECT_Y);
            push(puzzle, sprite);
            assertEquals(1 << 1, puzzle.panelBitsForTest(), "row 1, left column");
            assertEquals(1, puzzle.frameForPiece(1), "frame 3 - 2 = 1, the left marker");

            push(puzzle, sprite);
            assertEquals(1 << 1, puzzle.panelBitsForTest(), "the bit is already set");
            assertEquals(1, puzzle.frameForPiece(1), "and the frame is not lowered twice");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code Map_DEZGravityPuzzle} (Levels/DEZ/Misc Object Data/Map - Gravity Puzzle.asm):
     * frames 3 and 4 both point at {@code word_49AAC}, which has zero pieces, and frames 1 and
     * 2 are the single mirrored 16x16 marker. So an <em>unpressed</em> panel draws nothing and
     * pressing one is what makes its marker appear.
     */
    @Test
    void anUnpressedPanelCarriesTheEmptyFrameAndAPressedOneTheMarker() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityPuzzleObjectInstance puzzle = place();
            for (int piece = 0; piece < 3; piece++) {
                assertEquals(3, puzzle.frameForPiece(piece), "left column starts on frame 3");
            }
            for (int piece = 3; piece < 6; piece++) {
                assertEquals(4, puzzle.frameForPiece(piece), "right column starts on frame 4");
            }

            moveTo(sprite, OBJECT_X, OBJECT_Y + 0x10);
            push(puzzle, sprite);
            assertEquals(2, puzzle.frameForPiece(5), "frame 4 - 2 = 2, the mirrored marker");
            assertEquals(4, puzzle.frameForPiece(3), "its neighbours are untouched");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_49850} :96050-96063. {@code x_vel = $C00} away from the shaft, the facing bit
     * set only for a player on its left, and {@code ground_vel = 1} negated to match
     * (:96064, :96078-96079).
     */
    @Test
    void thePushFiresThePlayerAwayAtTwelveHundred() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityPuzzleObjectInstance puzzle = place();

            moveTo(sprite, OBJECT_X + 0x20, OBJECT_Y);
            push(puzzle, sprite);
            assertEquals(0x0C00, sprite.getXSpeed(), "a player on the right is fired right");
            assertEquals(1, sprite.getGSpeed(), "move.w #1,ground_vel (:96064)");

            moveTo(sprite, OBJECT_X - 0x20, OBJECT_Y);
            push(puzzle, sprite);
            assertEquals((short) -0x0C00, sprite.getXSpeed(), "and one on the left is fired left");
            assertEquals(-1, sprite.getGSpeed(), "neg.w ground_vel for a left-facing player");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * The rest of {@code loc_49850} (:96057-96077): airborne, not pushing, no double jump, no
     * roll jump, not jumping, and the endless tumble {@code flips_remaining = -1} with
     * {@code flip_speed = 4}.
     */
    @Test
    void thePushLeavesThePlayerTumblingInTheAir() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityPuzzleObjectInstance puzzle = place();
            moveTo(sprite, OBJECT_X + 0x20, OBJECT_Y);
            sprite.setAir(false);
            sprite.setJumping(true);
            sprite.setFlipAngle(0);

            push(puzzle, sprite);

            assertTrue(sprite.getAir(), "bset #Status_InAir (:96058)");
            assertFalse(sprite.isJumping(), "clr.b jumping (:96063)");
            assertEquals(1, sprite.getFlipAngle() & 0xFF, "move.b #1,flip_angle (:96067)");
            assertEquals(0xFF, sprite.getFlipsRemaining() & 0xFF,
                    "move.b #-1,flips_remaining (:96070)");
            assertEquals(4, sprite.getFlipSpeed(), "move.b #4,flip_speed (:96071)");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code tst.b flip_angle(a1) / bne.s loc_498A2} (:96065-96066): a player already part way
     * through a tumble keeps the angle they had rather than restarting it from upright.
     */
    @Test
    void aTumbleAlreadyInProgressIsNotRestarted() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityPuzzleObjectInstance puzzle = place();
            moveTo(sprite, OBJECT_X + 0x20, OBJECT_Y);
            sprite.setFlipAngle(0x40);

            push(puzzle, sprite);

            assertEquals(0x40, sprite.getFlipAngle() & 0xFF,
                    "the non-zero flip_angle survives the launch");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * {@code sub_49A02} :96188-96191: {@code sfx_TunnelBooster} ({@code $74}) plays on the push
     * and falls straight into {@code loc_49850}. Nothing else in the object calls
     * {@code Play_SFX}, so the bob is silent.
     */
    @Test
    void thePushPlaysTheTunnelBoosterSoundAndTheBobIsSilent() {
        HeadlessTestFixture fixture = fixture();
        AudioManager audioManager = AudioManager.getInstance();
        List<Integer> requested = new ArrayList<>();
        try {
            audioManager.setRequestObserver((requestClass, rawSoundId) -> requested.add(rawSoundId));
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityPuzzleObjectInstance puzzle = place();
            for (int update = 0; update < 16; update++) {
                puzzle.update(update, sprite);
            }
            assertEquals(List.of(), requested, "sixteen updates of bobbing are silent");

            moveTo(sprite, OBJECT_X + 0x20, OBJECT_Y);
            push(puzzle, sprite);
            assertEquals(List.of(Sonic3kSfx.TUNNEL_BOOSTER.id), requested,
                    "the push plays sfx_TunnelBooster ($74) exactly once");
        } finally {
            audioManager.setRequestObserver(null);
            SessionManager.clear();
        }
    }

    /**
     * {@code loc_499EC} :96177-96179 with {@code FixBugs = 0}. Player 1's branch loads
     * {@code a1} before calling {@code sub_49A0E} (:96170-96171); Player 2's calls it first and
     * loads {@code a1} afterwards. So when both push on the same update, Player 2's push marks
     * the panel under <em>Player 1</em> and Player 2's own row is never recorded. The fixed
     * branch would mark Player 2's row instead.
     */
    @Test
    void whenBothPlayersPushTheSecondMarksPlayerOnesPanel() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            AbstractPlayableSprite sidekick = sidekick();
            Assumptions.assumeTrue(sidekick != null,
                    "no sidekick registered in this fixture; the two-player branch is unrun");
            S3kDezGravityPuzzleObjectInstance puzzle = place();

            // Player 1 at the shaft's top left, Player 2 at its bottom right: different rows
            // and different columns, so the two panels cannot be confused.
            moveTo(sprite, OBJECT_X - 0x20, OBJECT_Y - 0x30);
            moveTo(sidekick, OBJECT_X + 0x20, OBJECT_Y + 0x10);
            push(puzzle, sprite);
            push(puzzle, sidekick);

            assertEquals(1, puzzle.panelBitsForTest(),
                    "only Player 1's top-left panel is set; Player 2's bottom-right one is not");
        } finally {
            SessionManager.clear();
        }
    }

    /**
     * The bob angle is object state and the panel bits are zone state; both have to come back.
     * The panel bitfield lives in {@code S3kDezZoneRuntimeState} because the ROM keeps it in
     * {@code MHZ_pollen_counter}, a level RAM byte rather than an object field.
     */
    @Test
    void rewindRestoresTheBobAngleAndThePanelBits() {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityPuzzleObjectInstance puzzle = place();
            for (int update = 0; update < 5; update++) {
                puzzle.update(update, sprite);
            }
            moveTo(sprite, OBJECT_X - 1, OBJECT_Y - 0x30);
            push(puzzle, sprite);
            assertEquals(5, puzzle.bobAngleForTest(), "precondition: five updates of bob");
            assertEquals(1, puzzle.panelBitsForTest(), "precondition: the top-left panel");
            CompositeSnapshot pressed =
                    TestEnvironment.activeGameplayMode().getRewindRegistry().capture();

            for (int update = 0; update < 9; update++) {
                puzzle.update(update, sprite);
            }
            moveTo(sprite, OBJECT_X + 1, OBJECT_Y + 0x10);
            push(puzzle, sprite);
            assertEquals(14, puzzle.bobAngleForTest(), "precondition: the forward run moved on");
            // sine.bin entry 13 is 80, so the shaft has bobbed 20 px down by this update:
            // the player $10 below the placement centre is 4 px *above* the shaft, which is
            // row 1 of the right column, bit 4.
            assertEquals(0x11, puzzle.panelBitsForTest(),
                    "precondition: and pressed a right-hand panel too");

            TestEnvironment.activeGameplayMode().getRewindRegistry().restore(pressed);
            S3kDezGravityPuzzleObjectInstance back = restored(puzzle);
            assertEquals(5, back.bobAngleForTest(), "the bob angle comes back");
            assertEquals(1, back.panelBitsForTest(), "and so does the panel bitfield");
        } finally {
            SessionManager.clear();
        }
    }

    // --- helpers ---

    /**
     * Places the player at the given offset, pushes, and returns the single bit index the push
     * set. Fails loudly if the push set more or fewer than one bit.
     */
    private int pushedPanelBit(int dy, int dx) {
        HeadlessTestFixture fixture = fixture();
        try {
            AbstractPlayableSprite sprite = fixture.sprite();
            S3kDezGravityPuzzleObjectInstance puzzle = place();
            moveTo(sprite, OBJECT_X + dx, OBJECT_Y + dy);
            push(puzzle, sprite);
            int bits = puzzle.panelBitsForTest();
            assertEquals(1, Integer.bitCount(bits), "exactly one panel bit for dy " + dy);
            return Integer.numberOfTrailingZeros(bits);
        } finally {
            SessionManager.clear();
        }
    }

    /** {@code swap d6 / andi.w #1|2,d6} (:96126-96127): the object's own pushing bit. */
    private void push(S3kDezGravityPuzzleObjectInstance puzzle, AbstractPlayableSprite sprite) {
        puzzle.onSolidContact(sprite, new SolidContact(false, true, false, false, true), 0);
    }

    private S3kDezGravityPuzzleObjectInstance place() {
        ObjectSpawn spawn = new ObjectSpawn(OBJECT_X, OBJECT_Y, 0x61, 0, 0,
                false, OBJECT_Y, -1);
        S3kDezGravityPuzzleObjectInstance puzzle = new S3kDezGravityPuzzleObjectInstance(spawn);
        GameServices.level().getObjectManager().addDynamicObject(puzzle);
        return puzzle;
    }

    private S3kDezGravityPuzzleObjectInstance restored(S3kDezGravityPuzzleObjectInstance original) {
        for (var instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof S3kDezGravityPuzzleObjectInstance puzzle) {
                return puzzle;
            }
        }
        return original;
    }

    private AbstractPlayableSprite sidekick() {
        var sidekicks = GameServices.sprites().getSidekicks();
        return sidekicks.isEmpty() ? null : sidekicks.getFirst();
    }

    private void moveTo(AbstractPlayableSprite sprite, int centreX, int centreY) {
        NativePositionOps.writeXPosResetSubpixel(sprite, centreX);
        NativePositionOps.writeYPosResetSubpixel(sprite, centreY);
    }

    private HeadlessTestFixture fixture() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .build();
    }
}
