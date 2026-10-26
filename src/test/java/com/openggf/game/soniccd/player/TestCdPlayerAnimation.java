package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.player.CdPlayerAnimation.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-derived transition cases with constructed format inputs only, never runtime fallbacks.
 * Authored during the run-81600 freeze; these Jupiter tests have NOT been compiled or executed.
 */
class TestCdPlayerAnimation {
    @Test
    void allByteTimerContentsFollowSubqBThenBplForNormalAndSpecialHeaders() {
        CdPlayerAnimation animation = new Fixture().owner();
        for (int timer = 0; timer < 256; timer++) {
            int decremented = (timer - 1) & 0xFF;
            boolean waits = decremented < 0x80;
            Result normal = animation.step(new State(5, 0, timer, 99), input(5, 0, 0, 1, 0xAE, 0, 0));
            assertEquals(new State(5, waits ? 0 : 1, waits ? decremented : 0, waits ? 99 : 0x80),
                    normal.state(), "normal timer " + timer);
            assertEquals(OptionalInt.of(0xAD), normal.effects().spriteFlagsWrite());

            Result special = animation.step(new State(0, 0, timer, 99), input(0, 0, 0, 0, 0xAF, 0, 0));
            assertEquals(new State(0, waits ? 0 : 1, waits ? decremented : 8, waits ? 99 : 0x35),
                    special.state(), "special timer " + timer);
            assertEquals(waits ? OptionalInt.empty() : OptionalInt.of(0xAC), special.effects().spriteFlagsWrite());
            assertEquals(OptionalInt.empty(), special.effects().animationWrite());
            assertFalse(special.effects().miniRollHitboxWrite());
        }
    }

    @Test
    void originalAnimationChangeResetsBeforeRemapButMiniToggleAloneDoesNotReset() {
        CdPlayerAnimation animation = new Fixture().owner();
        State captured = new State(4, 200, 127, 99);
        Result changed = animation.step(captured, input(5, 0, 0, 0, 0, 0, 1)); // mini[5]=8
        assertEquals(new State(5, 1, 0, 30), changed.state());
        assertEquals(new State(4, 200, 127, 99), captured);

        State ongoing = new State(0, 1, 2, 99);
        Result toggled = animation.step(ongoing, input(0, 0, 0, 0, 0xFF, 0, 1));
        assertEquals(new State(0, 1, 1, 99), toggled.state());
        assertEquals(OptionalInt.empty(), toggled.effects().spriteFlagsWrite());
        Result expired = animation.step(new State(0, 1, 0, 99), input(0, 0, 0, 0, 0, 0, 1));
        assertEquals(0x74, expired.state().mapFrame()); // Retains second index through remap.
        assertEquals(2, expired.state().scriptIndex());
    }

    @Test
    void differentOriginalIdsResetEvenWhenTheirMiniScriptsAlias() {
        Fixture fixture = new Fixture();
        fixture.mini[1] = 0x21;
        Result result = fixture.owner().step(new State(0, 1, 100, 99), input(1, 0, 0, 0, 0, 0, 1));
        assertEquals(new State(1, 1, 8, 0x73), result.state());
    }

    @Test
    void zeroAndHighBitFrameBytesAreDataAndFfRestartsWithIndexOne() {
        CdPlayerAnimation animation = new Fixture().owner();
        State state = new State(5, 0, 0, 99);
        int[] expectedFrames = {0x80, 0xC5, 0, 0x80};
        int[] expectedIndices = {1, 2, 3, 1};
        for (int i = 0; i < expectedFrames.length; i++) {
            Result result = animation.step(state, input(5, 0, 0, 0, 0, 0, 0));
            assertEquals(expectedFrames[i], result.state().mapFrame());
            assertEquals(expectedIndices[i], result.state().scriptIndex());
            assertTrue(result.effects().animationWrite().isEmpty());
            state = result.state();
        }
    }

    @Test
    void feSubtractsCountFromCurrentByteIndexAndPublishesLoopTargetImmediately() {
        CdPlayerAnimation animation = new Fixture().owner();
        State state = new State(6, 0, 0, 99);
        int[] expectedFrames = {10, 11, 12, 11, 12, 11};
        int[] expectedIndices = {1, 2, 3, 2, 3, 2};
        for (int i = 0; i < expectedFrames.length; i++) {
            Result result = animation.step(state, input(6, 0, 0, 0, 0, 0, 0));
            assertEquals(new State(6, expectedIndices[i], 0, expectedFrames[i]), result.state());
            state = result.state();
        }
    }

    @Test
    void fdWritesOnlyAnimationIdThenNextCallResetsAndRunsNewScript() {
        CdPlayerAnimation animation = new Fixture().owner();
        Result command = animation.step(new State(7, 1, 0, 99), input(7, 0, 0, 1, 0xFE, 0, 0));
        assertEquals(new State(7, 1, 0, 99), command.state());
        assertEquals(OptionalInt.of(8), command.effects().animationWrite());
        assertEquals(OptionalInt.of(0xFD), command.effects().spriteFlagsWrite());
        Result next = animation.step(command.state(), input(8, 0, 0, 1, 0xFD, 0, 0));
        assertEquals(new State(8, 1, 0, 30), next.state());
        assertTrue(next.effects().animationWrite().isEmpty());
    }

    @Test
    void positiveDelayCountsAdditionalCallsWithoutRunningScript() {
        Fixture fixture = new Fixture();
        fixture.replace(8, 2, 30, 0xFF);
        CdPlayerAnimation animation = fixture.owner();
        Result first = animation.step(new State(0, 99, 99, 0), input(8, 0, 0, 0, 0, 0, 0));
        assertEquals(new State(8, 1, 2, 30), first.state());
        Result second = animation.step(first.state(), input(8, 0, 0, 0, 0, 0, 0));
        Result third = animation.step(second.state(), input(8, 0, 0, 0, 0, 0, 0));
        assertEquals(new State(8, 1, 1, 30), second.state());
        assertEquals(new State(8, 1, 0, 30), third.state());
        assertEquals(first.state(), animation.step(third.state(), input(8, 0, 0, 0, 0, 0, 0)).state());
    }

    @Test
    void walkRunPeeloutUnsignedThresholdsAndSignedWordDelayIncludeNegationOverflow() {
        CdPlayerAnimation animation = new Fixture().owner();
        // Ground word, selected first map frame, timer. Values straddle each native threshold.
        int[][] cases = {
                {0, 0x35, 8}, {1, 0x35, 7}, {-1, 0x35, 7},
                {0x5FF, 0x35, 2}, {-0x5FF, 0x35, 2},
                {0x600, 0x4B, 2}, {-0x600, 0x4B, 2},
                {0x7FF, 0x4B, 0}, {0x800, 0x4B, 0}, {0x9FF, 0x4B, 0},
                {0xA00, 0x94, 0}, {-0xA00, 0x94, 0},
                {0x7FFF, 0x94, 0}, {-0x7FFF, 0x94, 0}, {Short.MIN_VALUE, 0x94, 0}
        };
        for (int[] row : cases) {
            Result result = animation.step(new State(0, 0, 0, 99), input(0, row[0], 0, 0, 0, 0, 0));
            assertEquals(new State(0, 1, row[2], row[1]), result.state(), "gvel " + row[0]);
        }
    }

    @Test
    void walkRunAndPeeloutApplyDistinctAngleStridesAndPreserveUpperSpriteFlags() {
        CdPlayerAnimation animation = new Fixture().owner();
        // angle, facing bit, walking offset, running/Peel-Out offset, final low flip bits.
        int[][] cases = {
                {0, 0, 0, 0, 0}, {0, 1, 0, 0, 1},
                {0x20, 0, 18, 12, 3}, {0x20, 1, 6, 4, 1},
                {0x40, 0, 12, 8, 3}, {0x40, 1, 12, 8, 1},
                {0x80, 0, 0, 0, 3}, {0x80, 1, 0, 0, 2},
                {0xC0, 0, 12, 8, 0}, {0xC0, 1, 12, 8, 2},
                {0xF0, 0, 0, 0, 0}, {0xF0, 1, 0, 0, 1}
        };
        for (int[] row : cases) {
            for (int speed : new int[]{0, 0x600, 0xA00}) {
                Result result = animation.step(new State(0, 0, 0, 99),
                        input(0, speed, row[0], row[1], 0xFF, 0, 0));
                int base = speed == 0 ? 0x35 : speed == 0x600 ? 0x4B : 0x94;
                assertEquals(base + row[speed == 0 ? 2 : 3], result.state().mapFrame());
                assertEquals(OptionalInt.of(0xFC | row[4]), result.effects().spriteFlagsWrite());
            }
        }
    }

    @Test
    void rampRunUsesEightUnitCenteringAndAllEightAnglePairsAtEverySpeed() {
        CdPlayerAnimation animation = new Fixture().owner();
        // angle, facing, offset, low flip bits; includes byte-add wrap and maximum frame $C5.
        int[][] cases = {
                {0, 0, 0, 0}, {0, 1, 0, 1},
                {8, 0, 28, 3}, {8, 1, 4, 1},
                {0x40, 0, 16, 3}, {0x40, 1, 16, 1},
                {0x80, 0, 0, 3}, {0x80, 1, 0, 2},
                {0xF8, 0, 0, 0}, {0xF8, 1, 0, 1}
        };
        for (int[] row : cases) {
            for (int speed : new int[]{0, 0x600, 0xA00, Short.MIN_VALUE}) {
                Result result = animation.step(new State(0, 0, 0, 99),
                        input(0, speed, row[0], row[1], 0xA7, 2, 0));
                assertEquals(0xA6 + row[2], result.state().mapFrame());
                assertEquals(OptionalInt.of(0xA4 | row[3]), result.effects().spriteFlagsWrite());
            }
        }
        // Last Run3D base byte $A9 plus maximum offset $1C is the admitted upper bound $C5.
        Result maximum = animation.step(new State(0, 3, 0, 99), input(0, 0, 8, 0, 0, 2, 0));
        assertEquals(197, maximum.state().mapFrame());
        for (int pair = 0; pair < 8; pair++) {
            Result result = animation.step(new State(0, 0, 0, 99), input(0, 0, pair * 16, 1, 0, 2, 0));
            assertEquals(0xA6 + pair * 4, result.state().mapFrame());
        }
    }

    @Test
    void allFfHeadersSelectNativeMovementScriptsInsteadOfPlayingTheirOwnBody() {
        CdPlayerAnimation animation = new Fixture().owner();
        for (int id : new int[]{0, 1, 0x21, 0x22, 0x2E, 0x2F, 0x31, 0x35}) {
            Result result = animation.step(new State(id, 0, 0, 99), input(id, 0, 0, 0, 0, 0, 0));
            assertEquals(0x35, result.state().mapFrame(), "header at ID " + id);
        }
    }

    @Test
    void speedChangeRetainsIndexAndConsumesAdmittedDuplicateFfPadding() {
        CdPlayerAnimation animation = new Fixture().owner();
        // Walk has six frames. Run has four plus duplicate FF bytes at the same native offsets.
        for (int index : new int[]{4, 5, 6}) {
            Result result = animation.step(new State(0, index, 0, 99), input(0, 0x600, 0, 0, 0, 0, 0));
            assertEquals(new State(0, 1, 2, 0x4B), result.state());
        }
        // Mini walk's four-frame index also remains valid in the shorter padded mini run script.
        Result mini = animation.step(new State(0, 4, 0, 99), input(0, 0x600, 0, 0, 0, 0, 1));
        assertEquals(new State(0, 1, 2, 0x76), mini.state());
    }

    @Test
    void nativeIndexAliasesStillCompareOriginalIdsBeforeReset() {
        CdPlayerAnimation animation = new Fixture().owner();
        Result rollAlias = animation.step(new State(0x36, 3, 100, 99), input(0x3A, 0, 0x30, 0, 0, 2, 0));
        assertEquals(new State(0x3A, 1, 4, 0x20), rollAlias.state());
        Result fallAlias = animation.step(new State(0x37, 1, 100, 99), input(0x3B, 0, 0, 0, 0, 0, 0));
        assertEquals(new State(0x3B, 1, 1, 0xA4), fallAlias.state());
    }

    @Test
    void rollThresholdAndRampAngleBranchesIgnoreFacingForScriptSelection() {
        CdPlayerAnimation animation = new Fixture().owner();
        // Selected scripts differ at index two: regular $2F, fast $31, ramp $23.
        int[][] cases = {
                {0, 0, 0, 0x2F, 4}, {1, 0, 0, 0x2F, 3}, {-1, 0, 0, 0x2F, 3},
                {0x5FF, 0, 0, 0x2F, 0}, {0x600, 0, 0, 0x31, 0}, {-0x600, 0, 0, 0x31, 0},
                {Short.MIN_VALUE, 0, 0, 0x31, 0},
                {0, 0, 2, 0x31, 4}, {0, 0x2F, 2, 0x31, 4},
                {0, 0x30, 2, 0x23, 4}, {0, 0xEF, 2, 0x23, 4}, {0, 0xF0, 2, 0x31, 4}
        };
        for (int[] row : cases) {
            for (int facing = 0; facing <= 1; facing++) {
                Result result = animation.step(new State(2, 2, 0, 99),
                        input(2, row[0], row[1], facing, 0xFF, row[2], 0));
                assertEquals(new State(2, 3, row[4], row[3]), result.state());
                assertEquals(OptionalInt.of(0xFC | facing), result.effects().spriteFlagsWrite());
                assertFalse(result.effects().miniRollHitboxWrite());
            }
        }
    }

    @Test
    void pushUsesSignedWordDurationHorizontalFlagsAndNativeMiniScript() {
        CdPlayerAnimation animation = new Fixture().owner();
        int[][] cases = {{0, 32}, {1, 31}, {-1, 31}, {0x600, 8}, {-0x600, 8},
                {0x800, 0}, {-0x800, 0}, {0x7FFF, 0}, {Short.MIN_VALUE, 0}};
        for (int[] row : cases) {
            Result result = animation.step(new State(4, 0, 0, 99), input(4, row[0], 0x80, 1, 0xFE, 2, 0));
            assertEquals(new State(4, 1, row[1], 0x64), result.state());
            assertEquals(OptionalInt.of(0xFD), result.effects().spriteFlagsWrite());
        }
        // FF's pushing branch replaces the already computed angled/ramp flips with facing alone.
        Result viaWalk = animation.step(new State(0, 0, 0, 99), input(0, 0, 0x80, 0x21, 0xFF, 2, 0));
        assertEquals(new State(0, 1, 32, 0x64), viaWalk.state());
        assertEquals(OptionalInt.of(0xFD), viaWalk.effects().spriteFlagsWrite());
        Result mini = animation.step(new State(4, 0, 0, 99), input(4, 0, 0, 0, 0xFF, 2, 0x80));
        assertEquals(new State(4, 1, 32, 0x73), mini.state());
        assertFalse(mini.effects().miniRollHitboxWrite());
    }

    @Test
    void specialDelaySuppressesRollPushFrozenAndMiniHitboxEffects() {
        CdPlayerAnimation animation = new Fixture().owner();
        for (Input input : new Input[]{input(2, 0, 0, 1, 0xFF, 2, 0),
                input(4, 0, 0, 1, 0xFF, 2, 0), input(0x2C, 0, 0, 1, 0xFF, 2, 0),
                input(0, 0, 0x40, 1, 0xFF, 2, 1)}) {
            State state = new State(input.animation(), 0, 1, 99);
            Result result = animation.step(state, input);
            assertEquals(new State(input.animation(), 0, 0, 99), result.state());
            assertEquals(new Effects(OptionalInt.empty(), OptionalInt.empty(), false), result.effects());
        }
    }

    @Test
    void frozenHeaderUsesCallerIndexWithoutAdvancingOrInterpretingCommands() {
        CdPlayerAnimation animation = new Fixture().owner();
        for (int i = 0; i < 2; i++) {
            State selected = new State(0x2C, i, 0, 99);
            Result result = animation.step(selected, input(0x2C, 0, 0, 1, 0xFF, 0, 0));
            assertEquals(new State(0x2C, i, 0, 40 + i), result.state());
            assertEquals(new Effects(OptionalInt.empty(), OptionalInt.empty(), false), result.effects());
            assertEquals(result, animation.step(result.state(), input(0x2C, 0, 0, 1, 0xFF, 0, 0)));
        }
        var command = assertThrows(UnsupportedOperationException.class, () -> animation.step(
                new State(0x2C, 2, 0, 99), input(0x2C, 0, 0, 0, 0, 0, 0)));
        assertTrue(command.getMessage().contains("Map frame 255"));
        // PP Spinning Disc writes Balance3D's six external indices (its Main.asm:96,111).
        int[] balanceFrames = {0x1A, 0x1B, 0x1C, 0x1F, 0x1D, 0x1E};
        for (int index = 0; index < balanceFrames.length; index++) {
            Result selected = animation.step(new State(0x2D, index, 0, 99), input(0x2D, 0, 0, 1, 0xFF, 1, 0));
            assertEquals(new State(0x2D, index, 0, balanceFrames[index]), selected.state());
            assertEquals(new Effects(OptionalInt.empty(), OptionalInt.empty(), false), selected.effects());
        }
    }

    @Test
    void miniatureFloorBoundsRetainFixBugsZeroNoVerticalFlipAndIgnoreRampAndPushing() {
        CdPlayerAnimation animation = new Fixture().owner();
        // Exact unsigned ADD.B/CMP.B floor interval for each facing.
        for (int facing = 0; facing <= 1; facing++) {
            int[] floorAngles = facing == 0 ? new int[]{0xE0, 0xFF, 0, 0x3F} : new int[]{0xC0, 0xFF, 0, 0x1F};
            for (int angle : floorAngles) {
                for (int speed : new int[]{0, 0x5FF, 0x600, Short.MIN_VALUE}) {
                    Result result = animation.step(new State(0, 0, 0, 99),
                            input(0, speed, angle, 0x20 | facing, 0xFF, 2, 1));
                    assertEquals(speed == 0 || speed == 0x5FF ? 0x73 : 0x76, result.state().mapFrame());
                    assertEquals(OptionalInt.of(0xFC | facing), result.effects().spriteFlagsWrite());
                    assertFalse(result.effects().miniRollHitboxWrite());
                }
            }
        }
    }

    @Test
    void miniatureOffFloorPublishesOnlySourceRollingHitboxEffectAndRollMiniFrames() {
        CdPlayerAnimation animation = new Fixture().owner();
        for (int facing = 0; facing <= 1; facing++) {
            int[] offFloor = facing == 0 ? new int[]{0xDF, 0x40, 0x80} : new int[]{0xBF, 0x20, 0x80};
            for (int angle : offFloor) {
                Result result = animation.step(new State(0, 0, 0, 99), input(0, 0, angle, facing, 0xFF, 2, 1));
                assertEquals(new State(0, 1, 4, 0x7C), result.state());
                assertEquals(OptionalInt.of(0xFC | facing), result.effects().spriteFlagsWrite());
                assertTrue(result.effects().miniRollHitboxWrite()); // BSET bit 2, Y=$A, X=5; no position write.
            }
        }
        Result directRoll = animation.step(new State(2, 0, 0, 99), input(2, 0, 0x80, 0, 0, 2, 1));
        assertEquals(0x7C, directRoll.state().mapFrame());
        assertFalse(directRoll.effects().miniRollHitboxWrite()); // Only MiniSonicRun owns the resize.
    }

    @Test
    void angledRunStillAddsOffsetAfterFdWithoutResettingIndexOrBaseMap() {
        Fixture fixture = new Fixture();
        fixture.replace(0, 0xFF, 0x35, 0xFD, 8);
        Result result = fixture.owner().step(new State(0, 1, 0, 100), input(0, 0, 0x20, 0, 0, 0, 0));
        assertEquals(new State(0, 1, 8, 118), result.state());
        assertEquals(OptionalInt.of(8), result.effects().animationWrite());
    }

    @Test
    void boundsFailExplicitlyWithoutFollowingAdjacentScriptsOrMappingFamilies() {
        CdPlayerAnimation animation = new Fixture().owner();
        State snapshot = new State(5, 255, 0, 99);
        var missing = assertThrows(UnsupportedOperationException.class,
                () -> animation.step(snapshot, input(5, 0, 0, 0, 0, 0, 0)));
        assertTrue(missing.getMessage().contains("body offset 256"));
        assertTrue(missing.getMessage().contains("adjacent resident bytes"));
        assertEquals(new State(5, 255, 0, 99), snapshot);
        // Changing only the mini flag does not reset the native index. No permission to read
        // beyond WalkMini's six-byte body into the next script/resident memory is invented.
        var miniExtent = assertThrows(UnsupportedOperationException.class, () -> animation.step(
                new State(0, 6, 0, 99), input(0, 0, 0, 0, 0, 0, 1)));
        assertTrue(miniExtent.getMessage().contains("script 33, body offset 7"));
        Fixture fixture = new Fixture();
        fixture.replace(0, 0xFF, 0xC5, 0xFF);
        var frame = assertThrows(UnsupportedOperationException.class, () -> fixture.owner().step(
                new State(0, 0, 0, 99), input(0, 0, 0x20, 0, 0, 0, 0)));
        assertTrue(frame.getMessage().contains("Map frame 215"));
        assertThrows(UnsupportedOperationException.class, () -> animation.step(
                new State(5, 0, 1, 198), input(5, 0, 0, 0, 0, 0, 0)));
    }

    @Test
    void captureRestoreReplayIsImmutableAndIndependentOfOtherSequences() {
        CdPlayerAnimation animation = new Fixture().owner();
        State checkpoint = new State(5, 2, 0, 99);
        Input[] route = {
                input(5, 0, 0, 0, 0xFF, 0, 0), input(6, 0, 0, 1, 0xFF, 0, 0),
                input(0, 0x600, 0x40, 0, 0xFF, 2, 0), input(0, 0x600, 0x40, 1, 0xFF, 0, 1),
                input(4, -1, 0, 0, 0xFF, 0, 0)
        };
        Result[] expected = replay(animation, checkpoint, route);
        // No session-local cursor, flags, map frame or hidden previous ID can leak between routes.
        replay(animation, new State(0, 0, 0, 0), new Input[]{input(0, 0xA00, 0, 0, 0, 0, 0)});
        assertArrayEquals(expected, replay(new Fixture().owner(), checkpoint, route));
        assertArrayEquals(expected, replay(animation, checkpoint, route));
        assertEquals(new State(5, 2, 0, 99), checkpoint);
        assertEquals(0, new State(0, 0, 0, 0).mapFrame()); // Constructing state never primes a frame or art.
    }

    @Test
    void constructorInputsRequireNativeWidthsAndAssetsRatherThanDefaults() {
        assertThrows(NullPointerException.class, () -> new CdPlayerAnimation(null));
        for (int value : new int[]{-1, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new State(value, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new State(0, value, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new State(0, 0, value, 0));
            assertThrows(IllegalArgumentException.class, () -> new State(0, 0, 0, value));
            assertThrows(IllegalArgumentException.class, () -> input(0, 0, value, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> input(0, 0, 0, value, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> input(0, 0, 0, 0, value, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> input(0, 0, 0, 0, 0, value, 0));
            assertThrows(IllegalArgumentException.class, () -> input(0, 0, 0, 0, 0, 0, value));
        }
        for (int id : new int[]{-1, 60, 255, 256}) {
            assertThrows(IllegalArgumentException.class, () -> input(id, 0, 0, 0, 0, 0, 0));
        }
        for (int velocity : new int[]{-32769, 32768, 65535}) {
            assertThrows(IllegalArgumentException.class, () -> input(0, velocity, 0, 0, 0, 0, 0));
        }
    }

    private static Input input(int animation, int groundVelocity, int angle, int flags,
                               int spriteFlags, int playerControl, int miniSonic) {
        return new Input(animation, groundVelocity, angle, flags, spriteFlags, playerControl, miniSonic);
    }

    private static Result[] replay(CdPlayerAnimation animation, State state, Input[] inputs) {
        Result[] results = new Result[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            results[i] = animation.step(state, inputs[i]);
            state = results[i].state();
        }
        return results;
    }

    private static void word(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    /** Pure test data, decoded through the reviewed prerequisite API without another parser. */
    private static final class Fixture {
        final byte[] animations = new byte[578];
        final byte[] mini = new byte[60];
        final int[] offsets = new int[60];
        final int[] lengths = new int[60];
        int next = 124;

        Fixture() {
            animations[120] = 0;
            animations[121] = 1;
            animations[122] = (byte) 0xFF;
            for (int id = 0; id < 60; id++) {
                word(animations, id * 2, 120);
                offsets[id] = 120;
                mini[id] = (byte) id;
            }
            body(0, 0xFF, 0x35, 0x36, 0x37, 0x38, 0x33, 0x34, 0xFF);
            body(1, 0xFF, 0x4B, 0x4C, 0x4D, 0x4E, 0xFF, 0xFF, 0xFF);
            body(2, 0xFE, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0xFF, 0xFF);
            body(3, 0xFE, 0x2D, 0x2E, 0x31, 0x2F, 0x30, 0x31, 0xFF);
            body(4, 0xFD, 0x64, 0x65, 0x66, 0x67, 0xFF, 0xFF, 0xFF);
            body(5, 0, 0x80, 0xC5, 0, 0xFF);
            body(6, 0, 10, 11, 12, 0xFE, 2);
            body(7, 0, 20, 0xFD, 8);
            body(8, 0, 30, 0xFF);
            body(0x21, 0xFF, 0x73, 0x74, 0x75, 0x74, 0xFF);
            body(0x22, 0xFF, 0x76, 0x77, 0xFF, 0xFF, 0xFF);
            body(0x23, 0xFE, 0x7C, 0x7D, 0x7E, 0xFF, 0xFF);
            body(0x27, 0xFD, 0x73, 0x74, 0x75, 0xFF, 0xFF, 0xFF, 0);
            body(0x2C, 0xFC, 40, 41, 0xFF);
            body(0x2D, 0xFC, 0x1A, 0x1B, 0x1C, 0x1F, 0x1D, 0x1E, 0xFF);
            body(0x2E, 0xFF, 0xD, 0xE, 0xF, 0x10, 0xB, 0xC, 0xFF);
            body(0x2F, 0xFF, 0x61, 0x62, 0x63, 0xFF, 0);
            body(0x31, 0xFF, 0x94, 0x95, 0x96, 0x97, 0xFF, 0xFF, 0xFF);
            body(0x35, 0xFF, 0xA6, 0xA7, 0xA8, 0xA9, 0xFF, 0xFF, 0xFF);
            body(0x36, 0xFE, 0x20, 0x21, 0x23, 0x24, 0x25, 0x31, 0xFF);
            body(0x37, 1, 0xA4, 0xA5, 0xFF);
            word(animations, 0x3A * 2, offsets[0x36]);
            word(animations, 0x3B * 2, offsets[0x37]);
            mini[0] = 0x21;
            mini[1] = 0x22;
            mini[2] = 0x23;
            mini[3] = 0x23;
            mini[4] = 0x27;
            mini[5] = 8;
        }

        void body(int id, int... bytes) {
            offsets[id] = next;
            lengths[id] = (bytes.length + 1) & ~1;
            word(animations, id * 2, next);
            for (int i = 0; i < bytes.length; i++) { animations[next + i] = (byte) bytes[i]; }
            next += lengths[id];
        }

        void replace(int id, int... bytes) {
            if (bytes.length > lengths[id]) { throw new IllegalArgumentException("Test replacement exceeds body"); }
            Arrays.fill(animations, offsets[id], offsets[id] + lengths[id], (byte) 0);
            for (int i = 0; i < bytes.length; i++) { animations[offsets[id] + i] = (byte) bytes[i]; }
        }

        CdPlayerAnimation owner() {
            byte[] mappings = new byte[398];
            byte[] dplcs = new byte[398];
            for (int frame = 0; frame < 198; frame++) {
                word(mappings, frame * 2, 396);
                word(dplcs, frame * 2, 396);
            }
            CdSonicVisualAssets visuals = CdSonicVisualAssets.decode(mappings, dplcs, new byte[69472]);
            byte[] sine = new byte[640];
            word(sine, 64 * 2, 0x100);
            word(sine, 192 * 2, 0xFF00);
            byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) { atan[i] = (byte) (i / 8); }
            return new CdPlayerAnimation(CdR11aPlayerAssets.decode(animations, mini,
                    new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[]{0x7F, 0x7F, 0x7F, 0x7F}, sine, atan, visuals));
        }
    }
}
