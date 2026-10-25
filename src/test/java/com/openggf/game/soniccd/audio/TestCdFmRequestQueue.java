package com.openggf.game.soniccd.audio;

/**
 * ROM-free source-derived vectors, authored and hash-frozen before production.
 * Native pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Source: src/Level/Functions (General).asm, PlayFMSound:284-310,
 * UpdateFMQueues:316-345, StopZ80:234-242, StartZ80:248-251;
 * src/_Include/Sound.inc:16-18; src/Sound Drivers/FM/_Variables.inc:70-74.
 * The admitted branch is USA retail / the same non-demo source branch.
 * These assertions specify uncommitted plans, not completed native effects.
 */
class TestCdFmRequestQueue {
    private static final CdFmRequestQueue.Slot Q1 = CdFmRequestQueue.Slot.QUEUE1;
    private static final CdFmRequestQueue.Slot Q2 = CdFmRequestQueue.Slot.QUEUE2;
    private static final CdFmRequestQueue.Slot Q3 = CdFmRequestQueue.Slot.QUEUE3;
    private static final CdFmRequestQueue.PlannedControl RTS =
            CdFmRequestQueue.PlannedControl.PLAY_FM_SOUND_RTS;
    private static final CdFmRequestQueue.PlannedControl STOP =
            CdFmRequestQueue.PlannedControl.STOP_Z80_JSR_RTS;
    private static final CdFmRequestQueue.PlannedControl START_TAIL =
            CdFmRequestQueue.PlannedControl.START_Z80_TAIL_BRANCH_RTS;

    @org.junit.jupiter.api.Test
    void emptyIsAnExplicitAllZeroValueWithoutAnInitializationClaim() {
        org.junit.jupiter.api.Assertions.assertEquals(state(0, 0, 0), CdFmRequestQueue.empty());
    }

    @org.junit.jupiter.api.Test
    void fourSourceOrderedRequestsKeepFirstThreeAndDropFourth() {
        var initial = state(0, 0, 0);
        var ring = CdFmRequestQueue.playFmSound(initial, 0x95);
        assertPlan(ring, state(0x95, 0, 0), main(Q1, 0x95), RTS);
        var spring = CdFmRequestQueue.playFmSound(ring.plannedState(), 0x98);
        assertPlan(spring, state(0x95, 0x98, 0), main(Q2, 0x98), RTS);
        var destroy = CdFmRequestQueue.playFmSound(spring.plannedState(), 0x96);
        assertPlan(destroy, state(0x95, 0x98, 0x96), main(Q3, 0x96), RTS);
        var hurt = CdFmRequestQueue.playFmSound(destroy.plannedState(), 0x93);
        assertPlan(hurt, state(0x95, 0x98, 0x96), RTS);
        org.junit.jupiter.api.Assertions.assertEquals(state(0, 0, 0), initial);
    }

    @org.junit.jupiter.api.Test
    void fullFlushPlansOneBracketAndInterleavedDestinationWriteThenSourceClear() {
        var initial = state(0x95, 0x98, 0x96);
        assertPlan(CdFmRequestQueue.updateFmQueues(initial), state(0, 0, 0),
                STOP,
                z80(0x1C09, 0x95), main(Q1, 0),
                z80(0x1C0A, 0x98), main(Q2, 0),
                z80(0x1C0B, 0x96), main(Q3, 0),
                START_TAIL);
        org.junit.jupiter.api.Assertions.assertEquals(state(0x95, 0x98, 0x96), initial);
    }

    @org.junit.jupiter.api.Test
    void enqueueFindsFirstZeroWithoutCompactingOtherSlots() {
        assertPlan(CdFmRequestQueue.playFmSound(state(0, 0x98, 0x96), 0x93),
                state(0x93, 0x98, 0x96), main(Q1, 0x93), RTS);
        assertPlan(CdFmRequestQueue.playFmSound(state(0x95, 0, 0x96), 0x93),
                state(0x95, 0x93, 0x96), main(Q2, 0x93), RTS);
        assertPlan(CdFmRequestQueue.playFmSound(state(0x95, 0x98, 0), 0x93),
                state(0x95, 0x98, 0x93), main(Q3, 0x93), RTS);
        assertPlan(CdFmRequestQueue.playFmSound(state(0, 0x98, 0), 0x93),
                state(0x93, 0x98, 0), main(Q1, 0x93), RTS);
    }

    @org.junit.jupiter.api.Test
    void flushSkipsZeroCellsWithoutCompactingOrClearingThemAgain() {
        assertPlan(CdFmRequestQueue.updateFmQueues(state(0, 0x98, 0x96)), state(0, 0, 0),
                STOP, z80(0x1C0A, 0x98), main(Q2, 0),
                z80(0x1C0B, 0x96), main(Q3, 0), START_TAIL);
        assertPlan(CdFmRequestQueue.updateFmQueues(state(0x95, 0, 0x96)), state(0, 0, 0),
                STOP, z80(0x1C09, 0x95), main(Q1, 0),
                z80(0x1C0B, 0x96), main(Q3, 0), START_TAIL);
        assertPlan(CdFmRequestQueue.updateFmQueues(state(0x95, 0x98, 0)), state(0, 0, 0),
                STOP, z80(0x1C09, 0x95), main(Q1, 0),
                z80(0x1C0A, 0x98), main(Q2, 0), START_TAIL);
        assertPlan(CdFmRequestQueue.updateFmQueues(state(0, 0x98, 0)), state(0, 0, 0),
                STOP, z80(0x1C0A, 0x98), main(Q2, 0), START_TAIL);
    }

    @org.junit.jupiter.api.Test
    void emptyAndRepeatedFlushStillPlanStopAndTailStartWithoutWrites() {
        var emptyFlush = CdFmRequestQueue.updateFmQueues(state(0, 0, 0));
        assertPlan(emptyFlush, state(0, 0, 0), STOP, START_TAIL);
        var populatedFlush = CdFmRequestQueue.updateFmQueues(state(0x95, 0, 0));
        assertPlan(CdFmRequestQueue.updateFmQueues(populatedFlush.plannedState()),
                state(0, 0, 0), STOP, START_TAIL);
    }

    @org.junit.jupiter.api.Test
    void zeroIdPlansSameValueByteWriteAndDoesNotReserveAQueueCell() {
        var initial = state(0, 0, 0);
        var zero = CdFmRequestQueue.playFmSound(initial, 0);
        assertPlan(zero, initial, main(Q1, 0), RTS);
        assertPlan(CdFmRequestQueue.playFmSound(zero.plannedState(), 0x95),
                state(0x95, 0, 0), main(Q1, 0x95), RTS);
        assertPlan(CdFmRequestQueue.updateFmQueues(zero.plannedState()),
                state(0, 0, 0), STOP, START_TAIL);
        assertPlan(CdFmRequestQueue.playFmSound(state(0, 0x98, 0x96), 0),
                state(0, 0x98, 0x96), main(Q1, 0), RTS);
        assertPlan(CdFmRequestQueue.playFmSound(state(0x95, 0, 0x96), 0),
                state(0x95, 0, 0x96), main(Q2, 0), RTS);
        assertPlan(CdFmRequestQueue.playFmSound(state(0x95, 0x98, 0), 0),
                state(0x95, 0x98, 0), main(Q3, 0), RTS);
    }

    @org.junit.jupiter.api.Test
    void moveByteUsesOnlyLowEightBitsOfAnyThirtyTwoBitD0() {
        int[][] vectors = {
                {0, 0}, {0x95, 0x95}, {0x195, 0x95}, {0x1234AB95, 0x95},
                {-107, 0x95}, {-1, 0xFF}, {-256, 0},
                {Integer.MIN_VALUE, 0}, {Integer.MAX_VALUE, 0xFF}
        };
        for (int[] vector : vectors) {
            assertPlan(CdFmRequestQueue.playFmSound(state(0, 0, 0), vector[0]),
                    state(vector[1], 0, 0), main(Q1, vector[1]), RTS);
            assertPlan(CdFmRequestQueue.playFmSound(state(1, 0, 2), vector[0]),
                    state(1, vector[1], 2), main(Q2, vector[1]), RTS);
            assertPlan(CdFmRequestQueue.playFmSound(state(1, 2, 0), vector[0]),
                    state(1, 2, vector[1]), main(Q3, vector[1]), RTS);
        }
    }

    @org.junit.jupiter.api.Test
    void identicalSoundIdsAreStoredInSeparateEmptyCellsRatherThanCoalesced() {
        var second = CdFmRequestQueue.playFmSound(state(0x95, 0, 0), 0x95);
        assertPlan(second, state(0x95, 0x95, 0), main(Q2, 0x95), RTS);
        var third = CdFmRequestQueue.playFmSound(second.plannedState(), 0x95);
        assertPlan(third, state(0x95, 0x95, 0x95), main(Q3, 0x95), RTS);
        assertPlan(CdFmRequestQueue.playFmSound(third.plannedState(), 0x95),
                state(0x95, 0x95, 0x95), RTS);
    }

    @org.junit.jupiter.api.Test
    void fullQueueDropsEvenZeroSameValueAndForeignHighBitsWithoutAnyWrite() {
        var full = state(0x95, 0x98, 0x96);
        for (int d0 : new int[]{0x93, 0, 0x95, 0x1234AB95, -1}) {
            assertPlan(CdFmRequestQueue.playFmSound(full, d0), full, RTS);
        }
    }

    @org.junit.jupiter.api.Test
    void independentPlansLeavePriorStatesAndEarlierResultsImmutable() {
        var prior = state(0, 0x98, 0);
        var left = CdFmRequestQueue.playFmSound(prior, 0x95);
        var right = CdFmRequestQueue.playFmSound(prior, 0x96);
        var flushPrior = CdFmRequestQueue.updateFmQueues(prior);
        assertPlan(left, state(0x95, 0x98, 0), main(Q1, 0x95), RTS);
        assertPlan(right, state(0x96, 0x98, 0), main(Q1, 0x96), RTS);
        assertPlan(flushPrior, state(0, 0, 0),
                STOP, z80(0x1C0A, 0x98), main(Q2, 0), START_TAIL);
        org.junit.jupiter.api.Assertions.assertEquals(state(0, 0x98, 0), prior);
        var flushLeft = CdFmRequestQueue.updateFmQueues(left.plannedState());
        assertPlan(flushLeft, state(0, 0, 0),
                STOP, z80(0x1C09, 0x95), main(Q1, 0),
                z80(0x1C0A, 0x98), main(Q2, 0), START_TAIL);
        assertPlan(left, state(0x95, 0x98, 0), main(Q1, 0x95), RTS);
        assertPlan(right, state(0x96, 0x98, 0), main(Q1, 0x96), RTS);
    }

    @org.junit.jupiter.api.Test
    void replayOfSameValueAndFlushSequenceIsDeterministic() {
        var first = replaySequence(state(0, 0, 0));
        var second = replaySequence(state(0, 0, 0));
        org.junit.jupiter.api.Assertions.assertEquals(first, second);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(
                state(0x95, 0, 0), state(0x95, 0, 0), state(0x95, 0x98, 0),
                state(0, 0, 0), state(0x96, 0, 0), state(0x96, 0x93, 0),
                state(0x96, 0x93, 0x98), state(0x96, 0x93, 0x98), state(0, 0, 0)),
                first.stream().map(CdFmRequestQueue.Result::plannedState).toList());
        assertPlan(first.get(3), state(0, 0, 0), STOP,
                z80(0x1C09, 0x95), main(Q1, 0),
                z80(0x1C0A, 0x98), main(Q2, 0), START_TAIL);
        assertPlan(first.get(8), state(0, 0, 0), STOP,
                z80(0x1C09, 0x96), main(Q1, 0),
                z80(0x1C0A, 0x93), main(Q2, 0),
                z80(0x1C0B, 0x98), main(Q3, 0), START_TAIL);
    }

    @org.junit.jupiter.api.Test
    void plannedStepListsAreDefensivelyCopiedAndUnmodifiable() {
        var input = new java.util.ArrayList<CdFmRequestQueue.PlannedStep>();
        input.add(main(Q1, 0));
        input.add(RTS);
        var result = new CdFmRequestQueue.Result(state(0, 0, 0), input);
        input.clear();
        assertPlan(result, state(0, 0, 0), main(Q1, 0), RTS);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> result.plannedSteps().add(STOP));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> result.plannedSteps().set(0, STOP));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> result.plannedSteps().remove(0));
        var flush = CdFmRequestQueue.updateFmQueues(state(0x95, 0, 0));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> flush.plannedSteps().clear());
    }

    @org.junit.jupiter.api.Test
    void stateRejectsForeignByteWidthsRatherThanSilentlyTruncating() {
        for (int invalid : new int[]{-1, 0x100, 0xFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> state(invalid, 0, 0));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> state(0, invalid, 0));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> state(0, 0, invalid));
        }
        org.junit.jupiter.api.Assertions.assertEquals(state(0, 0xFF, 0x80),
                new CdFmRequestQueue.State(0, 0xFF, 0x80));
    }

    @org.junit.jupiter.api.Test
    void plannedByteWritesRejectForeignValueWidths() {
        for (int invalid : new int[]{-1, 0x100, 0xFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new CdFmRequestQueue.PlannedMainByteWrite(Q1, invalid));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new CdFmRequestQueue.PlannedZ80ByteWrite(0x1C09, invalid));
        }
        org.junit.jupiter.api.Assertions.assertEquals(0xFF,
                new CdFmRequestQueue.PlannedMainByteWrite(Q3, 0xFF).value());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                new CdFmRequestQueue.PlannedZ80ByteWrite(0x1C0B, 0).value());
    }

    @org.junit.jupiter.api.Test
    void addressesUseZ80RamCoordinatesAndRejectThirdArbitrationCell() {
        org.junit.jupiter.api.Assertions.assertEquals(0x1C09, Q1.z80Address());
        org.junit.jupiter.api.Assertions.assertEquals(0x1C0A, Q2.z80Address());
        org.junit.jupiter.api.Assertions.assertEquals(0x1C0B, Q3.z80Address());
        for (int foreign : new int[]{-1, 0, 0x1C08, 0x1C0C, 0x2000, 0xA01C09, 0x10001C09}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new CdFmRequestQueue.PlannedZ80ByteWrite(foreign, 0x95));
        }
    }

    @org.junit.jupiter.api.Test
    void nullStatesAndNullPlanPartsAreRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> CdFmRequestQueue.playFmSound(null, 0x95));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> CdFmRequestQueue.updateFmQueues(null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new CdFmRequestQueue.PlannedMainByteWrite(null, 0));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new CdFmRequestQueue.Result(null, java.util.List.of()));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new CdFmRequestQueue.Result(state(0, 0, 0), null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new CdFmRequestQueue.Result(state(0, 0, 0),
                        java.util.Collections.singletonList(null)));
    }

    @org.junit.jupiter.api.Test
    void noFlushWritesThirdArbitrationCellOrAZeroDestinationByte() {
        var vectors = java.util.List.of(
                state(0, 0, 0), state(0x95, 0, 0), state(0, 0x98, 0), state(0, 0, 0x96),
                state(0x95, 0x98, 0), state(0x95, 0, 0x96), state(0, 0x98, 0x96),
                state(0x95, 0x98, 0x96));
        for (var initial : vectors) {
            var flush = CdFmRequestQueue.updateFmQueues(initial);
            for (var step : flush.plannedSteps()) {
                if (step instanceof CdFmRequestQueue.PlannedZ80ByteWrite write) {
                    org.junit.jupiter.api.Assertions.assertNotEquals(0x1C0C, write.address());
                    org.junit.jupiter.api.Assertions.assertNotEquals(0, write.value());
                }
            }
        }
    }

    private static java.util.List<CdFmRequestQueue.Result> replaySequence(CdFmRequestQueue.State initial) {
        var a = CdFmRequestQueue.playFmSound(initial, 0x95);
        var b = CdFmRequestQueue.playFmSound(a.plannedState(), 0);
        var c = CdFmRequestQueue.playFmSound(b.plannedState(), 0x98);
        var d = CdFmRequestQueue.updateFmQueues(c.plannedState());
        var e = CdFmRequestQueue.playFmSound(d.plannedState(), 0x96);
        var f = CdFmRequestQueue.playFmSound(e.plannedState(), 0x93);
        var g = CdFmRequestQueue.playFmSound(f.plannedState(), 0x98);
        var h = CdFmRequestQueue.playFmSound(g.plannedState(), 0x95);
        var i = CdFmRequestQueue.updateFmQueues(h.plannedState());
        return java.util.List.of(a, b, c, d, e, f, g, h, i);
    }

    private static CdFmRequestQueue.State state(int q1, int q2, int q3) {
        return new CdFmRequestQueue.State(q1, q2, q3);
    }

    private static CdFmRequestQueue.PlannedMainByteWrite main(CdFmRequestQueue.Slot slot, int value) {
        return new CdFmRequestQueue.PlannedMainByteWrite(slot, value);
    }

    private static CdFmRequestQueue.PlannedZ80ByteWrite z80(int address, int value) {
        return new CdFmRequestQueue.PlannedZ80ByteWrite(address, value);
    }

    private static void assertPlan(CdFmRequestQueue.Result actual,
                                   CdFmRequestQueue.State plannedState,
                                   CdFmRequestQueue.PlannedStep... plannedSteps) {
        org.junit.jupiter.api.Assertions.assertEquals(plannedState, actual.plannedState());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(plannedSteps), actual.plannedSteps());
    }
}
