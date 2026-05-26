package com.openggf.game.rewind;

import com.openggf.debug.playback.Bk2FrameInput;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void stepBackwardAttributesKeyframeRestorePrimerToRewindStep() {
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
        prof.clearTranscript();

        rc.stepBackward();

        // After registry.restore inside the keyframe-restore lambda, the section is
        // closed. Before rewind.replay opens, primeStepperAtFrame runs — that work
        // must credit to rewind.step, not fall into the unattributed gap.
        List<String> transcript = prof.transcript();
        int firstRestoreEnd = transcript.indexOf("end:rewind.restore");
        int firstReplayBegin = transcript.indexOf("begin:rewind.replay");
        assertTrue(firstRestoreEnd >= 0, "Expected first end:rewind.restore: " + transcript);
        assertTrue(firstReplayBegin > firstRestoreEnd,
                "Expected begin:rewind.replay after first end:rewind.restore: " + transcript);
        List<String> gap = transcript.subList(firstRestoreEnd + 1, firstReplayBegin);
        assertTrue(gap.contains("begin:rewind.step"),
                "Expected begin:rewind.step between keyframe-restore close and replay open "
                        + "(primer work attribution gap): gap=" + gap + " transcript=" + transcript);
    }

    @Test
    void stepBackwardLeavesProfilerCleanWhenStepperThrows() {
        RecordingSectionProfiler prof = new RecordingSectionProfiler();
        RewindRegistry reg = new RewindRegistry(prof);
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(120);
        AtomicInteger state = new AtomicInteger();

        // Stepper throws on the first invocation after poisoned=true. The lambda opens
        // rewind.replay BEFORE calling engineStepper.step, so the guard assertion that
        // begin:rewind.replay appears in the transcript is satisfied even on the first throw.
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

    @Test
    void seekToEmitsExpectedSectionsAndStaysBalanced() {
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
        prof.clearTranscript();

        rc.seekTo(3);

        List<String> beginsInOrder = prof.beginNames();
        assertTrue(beginsInOrder.contains("rewind.seek"), "Expected rewind.seek: " + beginsInOrder);
        assertTrue(beginsInOrder.contains("rewind.replay"),
                "Expected rewind.replay (forward stepping in seek): " + beginsInOrder);
        assertTrue(beginsInOrder.contains("rewind.restore"),
                "Expected rewind.restore: " + beginsInOrder);
        assertTrue(beginsInOrder.indexOf("rewind.seek") < beginsInOrder.indexOf("rewind.replay"),
                "rewind.seek must open before rewind.replay: " + beginsInOrder);
        assertNull(prof.activeSection(),
                "No section should be active after seekTo: transcript=" + prof.transcript());

        // Stronger ordering: verify rewind.seek is re-opened AFTER rewind.restore.
        // Without this, both seek beginSection calls could be at the start and the
        // post-restore re-open could be missing, but the test above would still pass.
        List<String> transcript = prof.transcript();
        int firstSeekBegin = transcript.indexOf("begin:rewind.seek");
        int seekRestoreEnd = transcript.indexOf("end:rewind.restore");
        // Find second occurrence of begin:rewind.seek after firstSeekBegin.
        int secondSeekBegin = firstSeekBegin >= 0
                ? transcript.subList(firstSeekBegin + 1, transcript.size())
                             .indexOf("begin:rewind.seek")
                : -1;
        if (secondSeekBegin >= 0) secondSeekBegin += firstSeekBegin + 1;
        assertTrue(firstSeekBegin >= 0, "Expected first begin:rewind.seek: " + transcript);
        assertTrue(seekRestoreEnd > firstSeekBegin,
                "Expected end:rewind.restore after first begin:rewind.seek: " + transcript);
        assertTrue(secondSeekBegin > seekRestoreEnd,
                "Expected second begin:rewind.seek AFTER end:rewind.restore "
                        + "(re-open after registry.restore): " + transcript);
    }

    @Test
    void seekToLeavesProfilerCleanWhenStepperThrows() {
        RecordingSectionProfiler prof = new RecordingSectionProfiler();
        RewindRegistry reg = new RewindRegistry(prof);
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(120);
        AtomicInteger state = new AtomicInteger();

        // Stepper throws on the first invocation after poisoned=true. The seekTo loop
        // opens rewind.replay BEFORE calling engineStepper.step inside its try/finally,
        // so the guard assertion that begin:rewind.replay appears in the transcript is
        // satisfied even on the first throw.
        final boolean[] poisoned = { false };
        EngineStepper throwingStepper = (in) -> {
            if (poisoned[0]) {
                throw new RuntimeException("simulated seek stepper failure");
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

        for (int i = 0; i < 7; i++) rc.step();
        prof.clearTranscript();
        poisoned[0] = true;

        // seekTo(4) forces a backward seek that crosses a keyframe boundary
        // (floor keyframe = 0), so the forward-replay loop runs at least once
        // and the poisoned stepper throws. seekTo(5) would short-circuit the
        // loop (target == floor frame) and never invoke the stepper.
        assertThrows(RuntimeException.class, () -> rc.seekTo(4),
                "Expected seekTo to propagate the stepper's exception");
        List<String> transcript = prof.transcript();
        // Guard: assert rewind.replay was actually opened before the throw, otherwise
        // this test would pass trivially before Task 6 wires the section.
        assertTrue(transcript.contains("begin:rewind.replay"),
                "Expected rewind.replay to have been opened before the throw: " + transcript);
        assertNull(prof.activeSection(),
                "Profiler must have no dangling active section after seek exception: transcript="
                        + transcript);
    }

    @Test
    void rewindControllerWorksWithoutProfiler() {
        RewindRegistry reg = new RewindRegistry(); // no profiler
        InMemoryKeyframeStore keyframes = new InMemoryKeyframeStore();
        InputSource inputs = new FakeInputSource(120);
        AtomicInteger state = new AtomicInteger();
        EngineStepper stepper = (in) -> state.incrementAndGet();
        reg.register(new RewindSnapshottable<Integer>() {
            @Override public String key() { return "k"; }
            @Override public Integer capture() { return state.get(); }
            @Override public void restore(Integer s) { state.set(s); }
        });

        // Five-arg constructor (no profiler, no audio manager).
        RewindController rc = new RewindController(reg, keyframes, inputs, stepper, 5);
        for (int i = 0; i < 7; i++) rc.step();
        rc.seekTo(3);
        for (int i = 0; i < 2; i++) rc.stepBackward();
        // Reaching this line with no exception is the assertion.
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
