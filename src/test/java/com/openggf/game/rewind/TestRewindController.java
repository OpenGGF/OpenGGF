package com.openggf.game.rewind;

import com.openggf.debug.playback.Bk2FrameInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindController {

    private static CompositeSnapshot snap(int marker) {
        var e = new LinkedHashMap<String, Object>();
        e.put("marker", marker);
        return new CompositeSnapshot(e);
    }

    @Test
    void stepForwardCapturesKeyframesAtIntervals() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        AtomicInteger stepCount = new AtomicInteger();
        EngineStepper stepper = (in) -> stepCount.incrementAndGet();

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 3);
        assertEquals(0, rc.currentFrame());

        // Step to frame 3 — should capture keyframe
        for (int i = 0; i < 3; i++) rc.step();
        assertEquals(3, rc.currentFrame());
        assertTrue(keyframes.latestAtOrBefore(3).isPresent());

        // Step to frame 6 — should capture another keyframe
        for (int i = 0; i < 3; i++) rc.step();
        assertEquals(6, rc.currentFrame());
        assertTrue(keyframes.latestAtOrBefore(6).isPresent());
    }

    @Test
    void seekToRestoresStateAndStepsForward() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        AtomicInteger state = new AtomicInteger(0);
        EngineStepper stepper = (in) -> state.incrementAndGet();

        // Register a simple snapshottable
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "state"; }
            @Override public Integer capture() { return state.get(); }
            @Override public void restore(Integer s) { state.set(s); }
        });

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 5);

        // Step to frame 5
        for (int i = 0; i < 5; i++) rc.step();
        assertEquals(5, state.get());

        // Seek to frame 3: should restore to state 0, step 3 times
        rc.seekTo(3);
        assertEquals(3, rc.currentFrame());
        assertEquals(3, state.get());
    }

    @Test
    void seekToPrimesSeekAwareStepperAtRestoredKeyframeBeforeReplay() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        SeekAwareStepper stepper = new SeekAwareStepper();

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 3);
        for (int i = 0; i < 5; i++) {
            rc.step();
        }
        stepper.steppedFrames.clear();

        rc.seekTo(4);

        assertEquals(3, stepper.firstRestoredFrame,
                "Stepper must be primed to the restored keyframe before replaying target frames");
        assertEquals(3, stepper.firstRestoredInputFrame);
        assertEquals(List.of(4), stepper.steppedFrames);
    }

    @Test
    void stepBackwardWithinSegmentIsCacheHit() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(100);
        AtomicInteger stepInvocations = new AtomicInteger();
        EngineStepper stepper = (in) -> stepInvocations.incrementAndGet();

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 10);

        // Step forward to frame 15
        for (int i = 0; i < 15; i++) rc.step();
        assertEquals(15, rc.currentFrame());
        int stepsAfterForward = stepInvocations.get();

        // Step backward to frame 12 (same segment): should not step forward more times
        rc.stepBackward();
        rc.stepBackward();
        rc.stepBackward();
        assertEquals(12, rc.currentFrame());
        // stepInvocations should only increase by the segment expansion cost,
        // not by 3 additional steps for the 3 backward calls
        int stepsAfterBackward = stepInvocations.get();
        // Expansion of segment [10, 20): need to step from frame 10 to target frame 12
        // Forward steps: 0->1...1->15 (15 steps), segment expansion: 10->12 (2 steps to 12)
        // Actually: initial 15 forward steps + 4 steps to expand to offset 4 (steps 11-14)
        assertTrue(stepsAfterBackward < stepsAfterForward + 10,
                "segment cache should cost O(1) per backward step, not O(n)");
    }

    @Test
    void stepBackwardAcrossSegmentBoundaryRebuilds() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(100);
        AtomicInteger stepInvocations = new AtomicInteger();
        EngineStepper stepper = (in) -> stepInvocations.incrementAndGet();

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 10);

        // Step forward to frame 15
        for (int i = 0; i < 15; i++) rc.step();

        // Step backward across segment boundary to frame 5
        rc.seekTo(5);
        assertEquals(5, rc.currentFrame());
    }

    @Test
    void seekToEarlierFrameDiscardsFutureKeyframes() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(20);
        EngineStepper stepper = (in) -> {};

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 3);
        for (int i = 0; i < 6; i++) rc.step();
        assertEquals(6, keyframes.latestAtOrBefore(99).orElseThrow().frame());

        rc.seekTo(4);

        assertEquals(3, keyframes.latestAtOrBefore(99).orElseThrow().frame(),
                "rewinding abandons keyframes from the previous future branch");
    }

    @Test
    void stepBackwardDiscardsFutureKeyframes() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(20);
        EngineStepper stepper = (in) -> {};

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 3);
        for (int i = 0; i < 6; i++) rc.step();
        assertEquals(6, keyframes.latestAtOrBefore(99).orElseThrow().frame());

        assertTrue(rc.stepBackward());

        assertEquals(3, keyframes.latestAtOrBefore(99).orElseThrow().frame(),
                "one-frame rewind abandons keyframes beyond the restored frame");
    }

    @Test
    void earliestAvailableFrameClampsSeekTo() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        EngineStepper stepper = (in) -> {};

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 5);
        rc.seekTo(-5);  // Request before earliest frame
        assertEquals(0, rc.currentFrame());
    }

    @Test
    void stepBackwardReturnsFalseAtEarliestFrame() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        EngineStepper stepper = (in) -> {};

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 5);
        assertFalse(rc.stepBackward(), "should return false at earliest frame");
    }

    @Test
    void resetBufferAtBoundaryMakesCurrentFrameEarliestAvailable() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(100);
        EngineStepper stepper = (in) -> {};

        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 10);
        for (int i = 0; i < 25; i++) rc.step();

        rc.resetBufferAtCurrentFrame();

        assertEquals(25, rc.earliestAvailableFrame());
        assertFalse(rc.stepBackward(), "level and act boundaries must not rewind into the previous buffer");
    }

    @Test
    void recordExternalStepAdvancesWithoutInvokingStepper() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);
        AtomicInteger stepInvocations = new AtomicInteger();

        RewindController rc = new RewindController(
                reg,
                keyframes,
                inputs,
                in -> stepInvocations.incrementAndGet(),
                3);

        assertTrue(rc.recordExternalStep());
        assertEquals(1, rc.currentFrame());
        assertEquals(0, stepInvocations.get(),
                "external recording must not recurse through the engine stepper");
    }

    @Test
    void recordExternalStepCapturesKeyframesAtIntervals() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(10);

        RewindController rc = new RewindController(reg, keyframes, inputs, in -> {}, 3);

        assertTrue(rc.recordExternalStep());
        assertTrue(rc.recordExternalStep());
        assertTrue(rc.recordExternalStep());

        assertEquals(3, rc.currentFrame());
        assertTrue(keyframes.latestAtOrBefore(3).isPresent(),
                "externally advanced visual playback still needs periodic rewind keyframes");
    }

    @Test
    void pruneHistoryAlignsToEarlierKeyframeBoundary() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(200);

        RewindController rc = new RewindController(reg, keyframes, inputs, in -> {}, 10);
        for (int i = 0; i < 35; i++) {
            rc.step();
        }

        int earliest = rc.pruneHistoryToRetainFrames(12);

        assertEquals(20, earliest,
                "retention must keep the keyframe at or before the requested horizon");
        assertTrue(keyframes.latestAtOrBefore(19).isEmpty());
        assertEquals(20, keyframes.latestAtOrBefore(20).orElseThrow().frame());
        assertEquals(30, keyframes.latestAtOrBefore(99).orElseThrow().frame());
    }

    @Test
    void pruneHistoryDoesNotDiscardWhenWithinRetentionWindow() {
        RewindRegistry reg = new RewindRegistry();
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(20);

        RewindController rc = new RewindController(reg, keyframes, inputs, in -> {}, 10);
        for (int i = 0; i < 5; i++) {
            rc.step();
        }

        assertEquals(0, rc.pruneHistoryToRetainFrames(60));
        assertEquals(0, keyframes.earliestFrame());
    }

    private static class FakeInputSource implements InputSource {
        private final int count;

        FakeInputSource(int count) {
            this.count = count;
        }

        @Override
        public int frameCount() {
            return count;
        }

        @Override
        public Bk2FrameInput read(int frame) {
            return new Bk2FrameInput(frame, 0, 0, false, "fake");
        }
    }

    private static final class SeekAwareStepper implements RewindSeekAwareEngineStepper {
        int restoredFrame = -1;
        int restoredInputFrame = -1;
        int firstRestoredFrame = -1;
        int firstRestoredInputFrame = -1;
        final List<Integer> steppedFrames = new ArrayList<>();

        @Override
        public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
            if (firstRestoredFrame < 0) {
                firstRestoredFrame = frame;
                firstRestoredInputFrame = inputAtFrame.frameIndex();
            }
            restoredFrame = frame;
            restoredInputFrame = inputAtFrame.frameIndex();
        }

        @Override
        public void step(Bk2FrameInput inputs) {
            steppedFrames.add(inputs.frameIndex());
        }
    }
}
