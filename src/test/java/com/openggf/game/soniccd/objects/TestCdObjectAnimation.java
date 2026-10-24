package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests authored before the production port, with literal source-format expectations.
 * Literal format fixtures, never native comparison rows or runtime disassembly inputs.
 * Source pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Object Animate.asm:14-95 (AnimateObject through .End2).
 */
class TestCdObjectAnimation {
    @Test
    void changedIdResetsBeforeDecrementEvenWhenBothIdsAliasTheSameScript() {
        var table = table(0, 2, 0, 4, 0, 4, 3, 0x25, 0xFF);
        var input = state(1, 0, 0xF0, 0x7F, 0x1F, 0xBC, 0xA2, 0xC7, 0xD8);

        assertEquals(state(1, 1, 1, 3, 5, 0xBF, 0xA2, 0xC7, 0xD8),
                CdObjectAnimation.step(input, table));
        assertEquals(state(1, 0, 0xF0, 0x7F, 0x1F, 0xBC, 0xA2, 0xC7, 0xD8), input);
    }

    @Test
    void waitingDoesNotPublishAFrameOrFacingAndDoesNotReadAnInvalidSelection() {
        var input = state(0xFF, 0xFF, 0xFE, 1, 0xAB, 0xFD, 0xA3, 7, 9);
        assertEquals(state(0xFF, 0xFF, 0xFE, 0, 0xAB, 0xFD, 0xA3, 7, 9),
                CdObjectAnimation.step(input, single(3, 9)));
        assertThrows(UnsupportedOperationException.class,
                () -> CdObjectAnimation.step(
                        state(0xFF, 0xFF, 0, 0, 0, 0, 0, 0, 0), single(3, 9)));
    }

    @Test
    void allRawTimerBytesDecrementWithByteSignIncluding80To7fAnd81To80() {
        var table = single(0x37, 0x62);
        for (int timer = 0; timer <= 0xFF; timer++) {
            var input = state(0, 0, 0, timer, 0xA1, 0xA7, 1, 2, 4);
            int decremented = (timer - 1) & 0xFF;
            var expected = decremented < 0x80
                    ? state(0, 0, 0, decremented, 0xA1, 0xA7, 1, 2, 4)
                    : state(0, 0, 1, 0x37, 2, 0xA6, 1, 2, 4);
            assertEquals(expected, CdObjectAnimation.step(input, table), "raw timer " + timer);
        }
    }

    @Test
    void negativeDelayIsAByteReloadRatherThanAPlayerAnimationHandler() {
        var table = single(0x80, 9, 0xFF);
        var first = CdObjectAnimation.step(state(0, 0, 0, 0, 3, 0xFF, 0, 2, 4), table);
        assertEquals(state(0, 0, 1, 0x80, 9, 0xFC, 0, 2, 4), first);
        assertEquals(state(0, 0, 1, 0x7F, 9, 0xFC, 0, 2, 4),
                CdObjectAnimation.step(first, table));
        var immediatelyExpired = CdObjectAnimation.step(
                state(0, 0, 1, 0x81, 9, 0xFC, 0, 2, 4), single(0x81, 9, 0xFF));
        assertEquals(state(0, 0, 1, 0x81, 9, 0xFC, 0, 2, 4), immediatelyExpired);
    }

    @Test
    void frameMaskAndRolThreeXorRetainEveryUpperSpriteAndStatusFlagBit() {
        int[][] framesAndFlipBits = {{0x1F, 0}, {0x3F, 1}, {0x5F, 2}, {0x7F, 3}};
        for (int[] c : framesAndFlipBits) {
            var table = single(0, c[0]);
            for (int status = 0; status <= 0xFF; status++) {
                for (int sprite = 0; sprite <= 0xFF; sprite++) {
                    assertEquals(state(0, 0, 1, 0, 0x1F,
                                    (sprite & 0xFC) | ((status ^ c[1]) & 3), status, 0xFE, 0xFD),
                            CdObjectAnimation.step(
                                    state(0, 0, 0, 0, 0xE1, sprite, status, 0xFE, 0xFD), table));
                }
            }
        }
    }

    @Test
    void zeroIsAFrameAndIndexIsZeroExtendedThenIncrementedAsAByte() {
        byte[] bytes = new byte[259];
        bytes[1] = 2;
        bytes[2] = 1;
        bytes[3 + 0x80] = 17;
        bytes[3 + 0xFF] = 31;
        var table = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes), 0, 1);
        assertEquals(state(0, 0, 1, 1, 0, 0xFC, 0, 2, 4),
                CdObjectAnimation.step(state(0, 0, 0, 0, 9, 0xFF, 0, 2, 4), table));
        assertEquals(state(0, 0, 0x81, 1, 17, 0xFC, 0, 2, 4),
                CdObjectAnimation.step(state(0, 0, 0x80, 0, 9, 0xFF, 0, 2, 4), table));
        assertEquals(state(0, 0, 0, 1, 31, 0xFC, 0, 2, 4),
                CdObjectAnimation.step(state(0, 0, 0xFF, 0, 9, 0xFF, 0, 2, 4), table));
    }

    @Test
    void selectedWordIsRelativeToTheOriginalTableBaseRatherThanItsOwnEntry() {
        var table = table(0, 3, 0, 6, 0, 9, 0, 6, 1, 4, 0xFF, 2, 7, 0xFF);
        assertEquals(state(1, 1, 1, 2, 7, 0, 0, 2, 4),
                CdObjectAnimation.step(state(1, 1, 0, 0, 9, 0, 0, 2, 4), table));
        assertEquals(state(2, 2, 1, 1, 4, 0, 0, 2, 4),
                CdObjectAnimation.step(state(2, 2, 0, 0, 9, 0, 0, 2, 4), table));
    }

    @Test
    void signedNegativeWordOffsetCanSelectBytesBeforeThePointerTable() {
        var table = table(4, 1, 2, 0x46, 0xFF, 0, 0xFF, 0xFC);
        assertEquals(state(0, 0, 1, 2, 6, 0xF9, 3, 2, 4),
                CdObjectAnimation.step(state(0, 0, 0, 0, 9, 0xF8, 3, 2, 4), table));
    }

    @Test
    void mostNegativeWordOffsetIsSignedAndBounded() {
        byte[] bytes = new byte[0x8002];
        bytes[0] = 4;
        bytes[1] = 11;
        bytes[0x8000] = (byte) 0x80;
        var table = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes), 0x8000, 1);
        assertEquals(state(0, 0, 1, 4, 11, 0, 0, 2, 4),
                CdObjectAnimation.step(state(0, 0, 0, 0, 9, 0, 0, 2, 4), table));
    }

    @Test
    void ffRestartsAndPublishesEvenANegativeSelectedByteWithoutCommandRecursion() {
        for (int[] c : new int[][]{{0xFE, 30}, {0xFF, 31}, {0xFC, 28}, {0x80, 0}}) {
            var table = single(3, c[0], 0xFF);
            int flip = c[0] == 0x80 ? 0 : 3;
            assertEquals(state(0, 0, 1, 3, c[1], 0xB8 | ((2 ^ flip) & 3), 0xA2, 0xC2, 0xD4),
                    CdObjectAnimation.step(
                            state(0, 0, 1, 0, 7, 0xBB, 0xA2, 0xC2, 0xD4), table));
        }
    }

    @Test
    void feSubtractsOperandAndImmediatelyPublishesTheSelectedByte() {
        var table = single(6, 0x63, 0x45, 0xFE, 2);
        assertEquals(state(0, 0, 1, 6, 3, 0xB9, 0xA2, 2, 4),
                CdObjectAnimation.step(state(0, 0, 2, 0, 9, 0xBB, 0xA2, 2, 4), table));
        // Offset zero reselects FE as DATA, not a second command to execute.
        assertEquals(state(0, 0, 1, 6, 30, 0xB9, 0xA2, 2, 4),
                CdObjectAnimation.step(state(0, 0, 0, 0, 9, 0xBB, 0xA2, 2, 4), single(6, 0xFE, 0)));
    }

    @Test
    void feByteSubtractionWrapsBothIndicesBeforeItsZeroExtendedRead() {
        byte[] bytes = new byte[259];
        bytes[1] = 2;
        bytes[2] = 7;
        bytes[3] = (byte) 0xFE;
        bytes[4] = 1;
        bytes[258] = 0x62;
        var table = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes), 0, 1);
        assertEquals(state(0, 0, 0, 7, 2, 0xA6, 1, 2, 4),
                CdObjectAnimation.step(state(0, 0, 0, 0, 9, 0xA7, 1, 2, 4), table));
    }

    @Test
    void feSelectedFcIsFrameDataWithFlipsAndDoesNotIncrementRoutine() {
        assertEquals(state(0, 0, 1, 1, 28, 0xA7, 0, 0xFE, 9),
                CdObjectAnimation.step(state(0, 0, 1, 0, 7, 0xA4, 0, 0xFE, 9),
                        single(1, 0xFC, 0xFE, 1)));
    }

    @Test
    void fdWritesOnlyAnimationAndTheNextCallPerformsTheChangedIdReset() {
        var table = table(0, 2, 0, 4, 0, 8, 5, 9, 0xFD, 1, 2, 0x45, 0xFF);
        var selected = CdObjectAnimation.step(state(0, 0, 1, 0, 0xE1, 0xFD, 3, 8, 0xFE), table);
        assertEquals(state(1, 0, 1, 5, 0xE1, 0xFD, 3, 8, 0xFE), selected);
        assertEquals(state(1, 1, 1, 2, 5, 0xFD, 3, 8, 0xFE),
                CdObjectAnimation.step(selected, table));
    }

    @Test
    void fdOperandDoesNotReplaceTheFallthroughRegisterOrExecuteAnotherCommand() {
        for (int id = 0xFA; id <= 0xFF; id++) {
            assertEquals(state(id, 0, 0, 7, 0xE1, 0xFD, 3, 0xFE, 0xFF),
                    CdObjectAnimation.step(state(0, 0, 0, 0, 0xE1, 0xFD, 3, 0xFE, 0xFF),
                            single(7, 0xFD, id)), "FD animation byte " + id);
        }
    }

    @Test
    void fcIncrementsRoutineAsAByteAndLeavesIndexFrameFlagsAndSecondaryAlone() {
        for (int[] c : new int[][]{{0xFE, 0}, {0xFF, 1}, {6, 8}}) {
            assertEquals(state(0, 0, 1, 5, 7, 0xFD, 3, c[1], 0xFF),
                    CdObjectAnimation.step(state(0, 0, 1, 0, 7, 0xFD, 3, c[0], 0xFF),
                            single(5, 9, 0xFC)));
        }
    }

    @Test
    void fbResetsIndexAndSecondaryWithoutFallingThroughToItsIncrement() {
        var table = single(2, 9, 0xFB);
        var reset = CdObjectAnimation.step(state(0, 0, 1, 0, 0xE1, 0xFF, 3, 8, 0xFF), table);
        assertEquals(state(0, 0, 0, 2, 0xE1, 0xFF, 3, 8, 0), reset);
        var waitOne = CdObjectAnimation.step(reset, table);
        assertEquals(state(0, 0, 0, 1, 0xE1, 0xFF, 3, 8, 0), waitOne);
        var waitZero = CdObjectAnimation.step(waitOne, table);
        assertEquals(state(0, 0, 0, 0, 0xE1, 0xFF, 3, 8, 0), waitZero);
        assertEquals(state(0, 0, 1, 2, 9, 0xFF, 3, 8, 0), CdObjectAnimation.step(waitZero, table));
    }

    @Test
    void faIncrementsOnlyTheSecondaryRoutineAndWrapsItsByte() {
        for (int[] c : new int[][]{{0xFE, 0}, {0xFF, 1}, {6, 8}}) {
            assertEquals(state(0, 0, 1, 2, 0xE1, 0xFF, 3, 8, c[1]),
                    CdObjectAnimation.step(state(0, 0, 1, 0, 0xE1, 0xFF, 3, 8, c[0]), single(2, 9, 0xFA)));
        }
    }

    @Test
    void otherNegativeCommandsReturnAfterTimerReloadWithoutInventedErrorsOrFrames() {
        for (int command = 0x80; command < 0xFA; command++) {
            assertEquals(state(0, 0, 1, 0x83, 0xE1, 0xFF, 0xFC, 0xFE, 0xFD),
                    CdObjectAnimation.step(state(0, 0, 1, 0, 0xE1, 0xFF, 0xFC, 0xFE, 0xFD),
                            single(0x83, 9, command)), "negative command " + command);
        }
    }

    @Test
    void ringSparklePublishesFourFramesThenRoutineEightForTheNextDeleteDispatch() {
        // Ani_Ring/.Sparkle: Level/_Objects/Ring/Data/Animations.asm:1-7;
        // Collect/Sparkle/Destroy: Level/_Objects/Ring/Main.asm:237-286.
        var ring = table(0, 1, 0, 2, 5, 4, 5, 6, 7, 0xFC);
        var current = state(0, 0, 0, 0, 2, 0x87, 0, 6, 0);
        for (int frame : new int[]{4, 5, 6, 7}) {
            current = CdObjectAnimation.step(current, ring);
            assertEquals(frame, current.mapFrame());
            assertEquals(5, current.timer());
            assertEquals(frame - 3, current.scriptIndex());
            assertEquals(6, current.routine());
            assertEquals(0x84, current.spriteFlags());
            for (int remaining : new int[]{4, 3, 2, 1, 0}) {
                var waiting = CdObjectAnimation.step(current, ring);
                assertEquals(state(0, 0, frame - 3, remaining, frame, 0x84, 0, 6, 0), waiting);
                current = waiting;
            }
        }
        var boundary = CdObjectAnimation.step(current, ring);
        assertEquals(state(0, 0, 4, 5, 7, 0x84, 0, 8, 0), boundary);
        // This function has no ID/pool/delete owner. The caller must next dispatch Destroy,
        // not run another sparkle step; the terminal frame remains caller-visible here.
        assertEquals(state(0, 0, 4, 0, 7, 0x84, 0, 6, 0), current);
    }

    @Test
    void verifiedMosquiNormalAndDamagedPatrolByteViewsKeepTheirOwnDelays() {
        // Both Data/Animations files: .Anim and byte_20B6CE/byte_20B6DC, lines 1-5.
        var normal = table(0, 2, 0, 4, 0, 8, 1, 0, 1, 0xFF, 6, 2, 3, 4, 0xFC, 0);
        var damaged = table(0, 2, 0, 4, 0, 8, 4, 0, 1, 0xFF, 14, 2, 3, 4, 0xFC);
        assertPatrol(normal, 1);
        assertPatrol(damaged, 4);
        assertDiveAnimationBoundary(normal, 6);
        assertDiveAnimationBoundary(damaged, 14);
    }

    @Test
    void immutableAssetBytesAndValueStatesAllowIndependentReplayAndInterleaving() {
        byte[] inputBytes = {0, 2, 1, 5, 6, (byte) 0xFF};
        var table = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(inputBytes), 0, 1);
        Arrays.fill(inputBytes, (byte) 0xFA);
        Arrays.fill(table.bytes().copy(), (byte) 0xFC);
        var input = state(0, 0, 0, 0, 9, 0xFF, 1, 6, 8);
        var first = CdObjectAnimation.step(input, table);
        assertEquals(state(0, 0, 1, 1, 5, 0xFD, 1, 6, 8), first);
        CdObjectAnimation.step(state(0, 0, 0, 0, 1, 0, 0, 0, 0), single(0, 0xFA));
        assertEquals(first, CdObjectAnimation.step(input, table));
        assertEquals(state(0, 0, 0, 0, 9, 0xFF, 1, 6, 8), input);
        assertEquals(CdObjectAnimation.step(first, table),
                CdObjectAnimation.step(first, single(1, 5, 6, 0xFF)));
    }

    @Test
    void byteStateFieldsRejectNoncanonicalInputsIncludingHeldUnmaskedMapping() {
        for (int field = 0; field < 9; field++) {
            for (int invalid : new int[]{-1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                int[] fields = new int[9];
                fields[field] = invalid;
                assertThrows(IllegalArgumentException.class, () -> state(fields[0], fields[1], fields[2],
                        fields[3], fields[4], fields[5], fields[6], fields[7], fields[8]));
            }
        }
    }

    @Test
    void tableDirectoryBoundsAndExplicitScriptCountAreCheckedWithoutIntegerOverflow() {
        var bytes = CdAssetBytes.copyOf(new byte[]{0, 2, 1, 9});
        for (int count : new int[]{-1, 0, 257, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdObjectAnimation.AnimationTable(bytes, 0, count));
        }
        for (int base : new int[]{-1, 3, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CdObjectAnimation.AnimationTable(bytes, base, 1));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new CdObjectAnimation.AnimationTable(bytes, 0, 3));
        assertThrows(NullPointerException.class,
                () -> new CdObjectAnimation.AnimationTable(null, 0, 1));
    }

    @Test
    void all256AnimationIdsCanSelectTheirOwnWordIncludingTheLastOne() {
        byte[] bytes = new byte[1024];
        for (int id = 0; id < 256; id++) {
            int offset = 512 + 2 * id;
            bytes[2 * id] = (byte) (offset >>> 8);
            bytes[2 * id + 1] = (byte) offset;
            bytes[offset] = (byte) (id & 7);
            bytes[offset + 1] = (byte) ((7 * id) & 0x7F);
        }
        var table = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes), 0, 256);
        for (int id = 0; id < 256; id++) {
            int encodedFrame = (7 * id) & 0x7F;
            assertEquals(state(id, id, 1, id & 7, encodedFrame & 31, encodedFrame >>> 5, 0, 2, 4),
                    CdObjectAnimation.step(state(id, id, 0, 0, 9, 0, 0, 2, 4), table),
                    "selected animation " + id);
        }
    }

    @Test
    void scriptDirectoryEndIsNotAnotherEntryEvenWhenItsBytesWouldSelectAValidScript() {
        // Bytes 2..3 look like another usable pointer, but only entry 0 is admitted.
        var table = table(0, 1, 0, 4, 0, 4, 1, 7);
        assertThrows(UnsupportedOperationException.class,
                () -> CdObjectAnimation.step(state(1, 1, 0, 0, 0, 0, 0, 0, 0), table));
    }

    @Test
    void negativeAndPositivePointerEscapesFailBeforeReturningAnyState() {
        var input = state(0, 9, 0xFE, 0x7F, 0xE1, 0xFF, 3, 8, 9);
        for (var table : new CdObjectAnimation.AnimationTable[]{
                table(0, 1, 0xFF, 0xFF), table(0, 1, 0x80, 0),
                table(0, 1, 0x7F, 0xFF), table(0, 1, 0, 2)}) {
            var failure = assertThrows(UnsupportedOperationException.class,
                    () -> CdObjectAnimation.step(input, table));
            assertTrue(failure.getMessage().contains("animation 0"));
            assertEquals(state(0, 9, 0xFE, 0x7F, 0xE1, 0xFF, 3, 8, 9), input);
        }
    }

    @Test
    void initialFrameCommandOperandAndFeTargetReadsStayInsideTheSuppliedView() {
        var input = state(0, 0, 0, 0, 0xE1, 0xFF, 3, 8, 9);
        for (var table : new CdObjectAnimation.AnimationTable[]{
                single(3), single(3, 0xFD), single(3, 0xFE), single(3, 0xFE, 1)}) {
            var failure = assertThrows(UnsupportedOperationException.class,
                    () -> CdObjectAnimation.step(input, table));
            assertTrue(failure.getMessage().contains("animation 0"));
            assertTrue(failure.getMessage().contains("byte"));
            assertEquals(state(0, 0, 0, 0, 0xE1, 0xFF, 3, 8, 9), input);
        }
        // An immutable slice must not read the perfectly usable frame just outside it.
        var slice = CdAssetBytes.copyOf(new byte[]{0, 2, 3, 9}).slice(0, 3);
        assertThrows(UnsupportedOperationException.class, () -> CdObjectAnimation.step(input,
                new CdObjectAnimation.AnimationTable(slice, 0, 1)));
        assertThrows(UnsupportedOperationException.class,
                () -> CdObjectAnimation.step(state(0, 0, 255, 0, 0, 0, 0, 0, 0), single(0, 9)));
    }

    private static void assertPatrol(CdObjectAnimation.AnimationTable table, int delay) {
        var current = state(0, 0, 0, 0, 9, 0xFF, 1, 2, 4);
        for (int frame : new int[]{0, 1, 0, 1, 0}) {
            current = CdObjectAnimation.step(current, table);
            assertEquals(frame, current.mapFrame());
            assertEquals(delay, current.timer());
            assertEquals(0xFD, current.spriteFlags());
            assertEquals(2, current.routine());
            for (int timer = delay - 1; timer >= 0; timer--) {
                current = CdObjectAnimation.step(current, table);
                assertEquals(frame, current.mapFrame());
                assertEquals(timer, current.timer());
            }
        }
    }

    private static void assertDiveAnimationBoundary(CdObjectAnimation.AnimationTable table, int delay) {
        var current = state(1, 0, 0xFF, 0x7F, 9, 0xFF, 1, 4, 7);
        for (int frame : new int[]{2, 3, 4}) {
            current = CdObjectAnimation.step(current, table);
            assertEquals(frame, current.mapFrame());
            assertEquals(delay, current.timer());
            assertEquals(4, current.routine());
            for (int timer = delay - 1; timer >= 0; timer--) {
                current = CdObjectAnimation.step(current, table);
                assertEquals(frame, current.mapFrame());
                assertEquals(timer, current.timer());
            }
        }
        assertEquals(state(1, 1, 3, delay, 4, 0xFD, 1, 6, 7), CdObjectAnimation.step(current, table));
    }

    private static CdObjectAnimation.AnimationTable single(int delay, int... script) {
        byte[] bytes = new byte[3 + script.length];
        bytes[1] = 2;
        bytes[2] = (byte) delay;
        for (int i = 0; i < script.length; i++) {
            bytes[3 + i] = (byte) script[i];
        }
        return new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes), 0, 1);
    }

    private static CdObjectAnimation.AnimationTable table(int base, int count, int... literals) {
        byte[] bytes = new byte[literals.length];
        for (int i = 0; i < literals.length; i++) {
            bytes[i] = (byte) literals[i];
        }
        return new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes), base, count);
    }

    private static CdObjectAnimation.State state(int animation, int previous, int index, int timer,
                                                  int frame, int sprite, int status, int routine, int routine2) {
        return new CdObjectAnimation.State(animation, previous, index, timer, frame, sprite, status, routine, routine2);
    }
}
