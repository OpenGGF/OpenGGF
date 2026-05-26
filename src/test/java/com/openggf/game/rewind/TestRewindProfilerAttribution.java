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

    private static final class FakeInputSource implements InputSource {
        private final int count;
        FakeInputSource(int count) { this.count = count; }
        @Override public int frameCount() { return count; }
        @Override public Bk2FrameInput read(int frame) {
            return new Bk2FrameInput(frame, 0, 0, false, "fake");
        }
    }
}
