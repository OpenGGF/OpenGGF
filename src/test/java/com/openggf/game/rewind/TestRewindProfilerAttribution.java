package com.openggf.game.rewind;

import com.openggf.debug.playback.Bk2FrameInput;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindProfilerAttribution {

    private static CompositeSnapshot snap(int marker) {
        var e = new LinkedHashMap<String, Object>();
        e.put("k", marker);
        return new CompositeSnapshot(e);
    }

    @Test
    void registryRestoreWrapsInRewindRestoreSection() {
        RecordingSectionProfiler prof = new RecordingSectionProfiler();
        RewindRegistry reg = new RewindRegistry(prof);
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "k"; }
            @Override public Integer capture() { return 1; }
            @Override public void restore(Integer s) { }
        });

        reg.restore(snap(42));

        List<String> transcript = prof.transcript();
        assertEquals(List.of("begin:rewind.restore", "end:rewind.restore"), transcript,
                "Expected exactly one balanced rewind.restore pair: " + transcript);
        assertNull(prof.activeSection(), "No section should be active after restore");
    }

    @Test
    void stepBackwardEmitsExpectedSectionsAndStaysBalanced() {
        RecordingSectionProfiler prof = new RecordingSectionProfiler();
        RewindRegistry reg = new RewindRegistry(prof);
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(120);
        AtomicInteger state = new AtomicInteger();
        EngineStepper stepper = (in) -> state.incrementAndGet();
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "k"; }
            @Override public Integer capture() { return state.get(); }
            @Override public void restore(Integer s) { state.set(s); }
        });

        RewindController rc = new RewindController(
                reg, keyframes, inputs, stepper, 5, null, prof);

        for (int i = 0; i < 7; i++) rc.step();
        prof.clearTranscript(); // Drop capture noise from forward stepping.

        boolean stepped = rc.stepBackward();
        assertTrue(stepped);

        List<String> beginsInOrder = prof.beginNames();
        assertTrue(beginsInOrder.contains("rewind.step"),
                "Expected rewind.step in begin order: " + beginsInOrder);
        assertTrue(beginsInOrder.contains("rewind.replay"),
                "Expected rewind.replay (cold-segment expansion): " + beginsInOrder);
        assertTrue(beginsInOrder.contains("rewind.restore"),
                "Expected rewind.restore: " + beginsInOrder);
        assertTrue(beginsInOrder.indexOf("rewind.step") < beginsInOrder.indexOf("rewind.replay"),
                "rewind.step must open before rewind.replay: " + beginsInOrder);
        assertNull(prof.activeSection(),
                "No section should be active after stepBackward: transcript=" + prof.transcript());
    }

    @Test
    void stepBackwardLeavesProfilerCleanWhenStepperThrows() {
        RecordingSectionProfiler prof = new RecordingSectionProfiler();
        RewindRegistry reg = new RewindRegistry(prof);
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(120);
        AtomicInteger state = new AtomicInteger();

        // Stepper throws on the second invocation, simulating a replay-frame failure
        // mid-segment-expansion (after at least one rewind.replay section has opened).
        final boolean[] poisoned = { false };
        EngineStepper throwingStepper = (in) -> {
            if (poisoned[0]) {
                throw new RuntimeException("simulated stepper failure");
            }
            state.incrementAndGet();
        };
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "k"; }
            @Override public Integer capture() { return state.get(); }
            @Override public void restore(Integer s) { state.set(s); }
        });

        RewindController rc = new RewindController(
                reg, keyframes, inputs, throwingStepper, 5, null, prof);

        for (int i = 0; i < 12; i++) rc.step();
        prof.clearTranscript();
        poisoned[0] = true;

        assertThrows(RuntimeException.class, rc::stepBackward,
                "Expected stepBackward to propagate the stepper's exception");
        List<String> transcript = prof.transcript();
        // Guard: assert the instrumentation actually opened rewind.replay before the
        // throw. Without this, the test would pass trivially before Task 5 wires the
        // section — the stepper would throw without any section ever being opened,
        // leaving activeSection == null for the wrong reason.
        assertTrue(transcript.contains("begin:rewind.replay"),
                "Expected rewind.replay to have been opened before the throw: " + transcript);
        assertNull(prof.activeSection(),
                "Profiler must have no dangling active section after exception: transcript="
                        + transcript);
    }

    private static final class FakeInputSource implements InputSource {
        private final int count;
        FakeInputSource(int count) { this.count = count; }
        @Override public int frameCount() { return count; }
        @Override public Bk2FrameInput read(int frame) {
            return new Bk2FrameInput(frame, 0, 0, false, "fake");
        }
    }
}
