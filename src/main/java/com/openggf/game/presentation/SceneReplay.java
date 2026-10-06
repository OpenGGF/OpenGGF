package com.openggf.game.presentation;

import com.openggf.game.ModApi;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded reverse view of immutable ROM-backed scenes, independent of gameplay rollback.
 * Record at a mod-owned tick, begin with a mod-selected speed, and hold the world while stepping.
 * At the origin, present that final row (including its audio) before restoring a checkpoint.
 * Scores, permissions and replay duration policy stay with the caller.
 */
@ModApi
public final class SceneReplay {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_SAMPLES = 128;

    @ModApi
    public record Sample(int tick, byte[] scene) {
        public Sample {
            if (tick < 0 || scene == null || scene.length > SceneFrameCodec.MAX_BYTES)
                throw new IllegalArgumentException("Invalid scene replay sample");
            scene = scene.clone();
        }
        @Override public byte[] scene() { return scene.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Sample sample && tick == sample.tick
                    && java.util.Arrays.equals(scene, sample.scene);
        }
        @Override public int hashCode() { return 31 * tick + java.util.Arrays.hashCode(scene); }
    }

    @ModApi
    public record State(List<Sample> samples, int stride, int cursor, int speed, boolean playing) {
        public State {
            samples = List.copyOf(samples);
            if (stride < 1 || cursor < 0 || speed < 1 || samples.size() > MAX_SAMPLES)
                throw new IllegalArgumentException("Invalid scene replay state");
            int previous = -1, bytes = 0;
            for (var sample : samples) {
                if (sample.tick() <= previous) throw new IllegalArgumentException("Replay ticks must increase");
                previous = sample.tick(); bytes += sample.scene.length;
            }
            if (bytes > MAX_BYTES) throw new IllegalArgumentException("Scene replay exceeds byte budget");
        }
    }

    private final ArrayList<Sample> samples = new ArrayList<>();
    private int stride = 3, cursor, speed = 1, bytes;
    private boolean playing;

    public State snapshot() { return new State(samples, stride, cursor, speed, playing); }
    public void restore(State saved) {
        java.util.Objects.requireNonNull(saved);
        samples.clear(); samples.addAll(saved.samples()); stride = saved.stride(); cursor = saved.cursor();
        speed = saved.speed(); playing = saved.playing(); bytes = samples.stream().mapToInt(s -> s.scene.length).sum();
    }
    public void clear() { samples.clear(); bytes = cursor = 0; stride = 3; speed = 1; playing = false; }
    public boolean wantsSample(int tick) {
        if (tick < 0) throw new IllegalArgumentException("Negative replay tick");
        return samples.isEmpty() || tick % stride == 0;
    }
    public void record(int tick, ScenePresentationFrame scene) {
        if (tick < 0 || !samples.isEmpty() && tick < samples.getLast().tick())
            throw new IllegalArgumentException("Replay ticks must not go backwards");
        try {
            byte[] encoded = SceneFrameCodec.encode(scene);
            if (!samples.isEmpty() && samples.getLast().tick() == tick) bytes -= samples.removeLast().scene.length;
            samples.add(new Sample(tick, encoded)); bytes += encoded.length;
            while (samples.size() > MAX_SAMPLES || bytes > MAX_BYTES) {
                // Both endpoints survive; codec bounds guarantee they fit together.
                for (int i = samples.size() - 2; i > 0; i -= 2) bytes -= samples.remove(i).scene.length;
                stride = (int) Math.min(Integer.MAX_VALUE, (long) stride * 2);
            }
        } catch (IOException invalid) { throw new IllegalArgumentException("Cannot record bounded scene", invalid); }
    }
    public void begin(int ticks, int speed, ScenePresentationFrame latest) {
        if (speed < 1) throw new IllegalArgumentException("Replay speed must be positive");
        record(ticks, latest); cursor = ticks; this.speed = speed; playing = true;
    }
    public boolean playing() { return playing; }
    public int speed() { return speed; }
    public boolean atOrigin() { return playing && cursor == 0; }
    /** Reaches the origin without ending playback; release it on the following presented row. */
    public boolean step() { cursor = Math.max(0, cursor - speed); return cursor == 0; }
    public ScenePresentationFrame frame(long revision) {
        if (!playing || samples.isEmpty()) return null;
        Sample chosen = samples.getFirst();
        for (var sample : samples) { if (sample.tick() > cursor) break; chosen = sample; }
        try { return revision(SceneFrameCodec.decode(chosen.scene), revision); }
        catch (IOException invalid) { throw new IllegalStateException("Recorded scene became invalid", invalid); }
    }
    public static ScenePresentationFrame revision(ScenePresentationFrame frame, long revision) {
        return new ScenePresentationFrame(revision, frame.act(), frame.width(), frame.height(), frame.cameraX(), frame.cameraY(),
                frame.backdropArgb(), frame.paletteArgb(), frame.tiles(), frame.primitives());
    }
}
