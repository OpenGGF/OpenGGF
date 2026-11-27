package com.openggf.level.objects;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loop search for NATIVE widescreen dormant poses: stationary loops play back, movers freeze. */
class TestDormantPoseLoop {
    /** A copy whose output after {@code n} updates is {@code output(n - 1)} and position {@code x(n - 1)}. */
    private static final class FakeCopy implements DormantPoseLoop.Copy {
        final IntUnaryOperator output;
        final IntUnaryOperator x;
        int updates;

        FakeCopy(IntUnaryOperator output, IntUnaryOperator x) {
            this.output = output;
            this.x = x;
        }

        @Override public void update(int tick) {
            assertEquals(updates, tick, "updates run in tick order");
            updates++;
        }
        @Override public Object signature() { return output.applyAsInt(updates - 1); }
        @Override public long position() { return x.applyAsInt(updates - 1); }
    }

    private final List<FakeCopy> built = new ArrayList<>();

    private DormantPoseLoop.Plan<FakeCopy> bake(IntUnaryOperator output, IntUnaryOperator x) {
        return DormantPoseLoop.bake(() -> {
            var copy = new FakeCopy(output, x);
            built.add(copy);
            return copy;
        }, 64);
    }

    @Test
    void staticObjectIsFrozenAfterTheWholeWindowAgrees() {
        var plan = bake(tick -> 7, tick -> 100);
        assertFalse(plan.looped());
        assertEquals(1, plan.frames().size());
        assertEquals(1, built.size(), "a static object needs no second copy");
        assertEquals(64, plan.updates(), "a long held frame could still change, so the whole window is watched");
    }

    @Test
    void stationaryAnimationKeepsOneCopyPerDistinctFrameOfItsLoop() {
        // Mapping frame advances every 4 updates through 3 frames: a 12-update loop whose
        // first frame is held, so the first repeat of frame 0 is not yet a loop.
        var plan = bake(tick -> (tick / 4) % 3, tick -> 100);
        assertTrue(plan.looped());
        assertEquals(12, plan.loopLength());
        assertArrayEquals(new int[]{0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2}, plan.segmentAt());
        assertEquals(3, plan.frames().size());
        // Each kept copy draws its segment's output.
        for (int tick = 0; tick < plan.loopLength(); tick++) {
            var copy = plan.frames().get(plan.segmentAt()[tick]);
            assertEquals((tick / 4) % 3, copy.signature(), "tick " + tick);
        }
        // Two full periods (24 updates) confirm the loop; the searching copy ends on frame 2.
        assertSame(built.getFirst(), plan.frames().get(2));
        assertEquals(3, built.size());
        assertEquals(24 + 1 + 5, plan.updates());
    }

    @Test
    void movingObjectFreezesOnAFreshFirstFramePose() {
        var plan = bake(tick -> tick % 2, tick -> 100 + tick);
        assertFalse(plan.looped());
        assertEquals(2, built.size());
        assertSame(built.get(1), plan.frames().getFirst());
        assertEquals(1, built.get(1).updates, "the frozen pose is the copy after exactly one update");
    }

    @Test
    void stationaryOutputThatNeverRepeatsFreezesOnTheFirstFrame() {
        var plan = bake(tick -> Math.min(tick, 40), tick -> 100);
        assertFalse(plan.looped());
        assertEquals(1, built.getLast().updates);
        assertEquals(64 + 1, plan.updates());
    }

    @Test
    void loopThatAFreshCopyCannotReproduceFallsBackToTheFrozenPose() {
        // Shared state (an RNG, a global counter) shifts every later copy's output.
        int[] constructions = {0};
        var plan = DormantPoseLoop.bake(() -> {
            int offset = constructions[0]++;
            return new FakeCopy(tick -> ((tick + 3 * offset) / 2) % 2, tick -> 100);
        }, 64);
        assertFalse(plan.looped());
        assertEquals(1, plan.frames().size());
    }

    @Test
    void uncomparableOutputAndMissingCopiesNeedNoLoopSearch() {
        var plan = bake(tick -> 0, tick -> 0);
        assertFalse(plan.looped());
        var noSignature = DormantPoseLoop.bake(() -> new DormantPoseLoop.Copy() {
            int updates;
            @Override public void update(int tick) { updates++; }
            @Override public Object signature() { return null; }
            @Override public long position() { return updates; }
        }, 64);
        assertEquals(1, noSignature.updates());
        assertNull(DormantPoseLoop.bake(() -> null, 64));
    }
}
