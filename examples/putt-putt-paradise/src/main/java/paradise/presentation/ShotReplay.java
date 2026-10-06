package paradise.presentation;

import com.openggf.game.presentation.SceneFrameCodec;
import com.openggf.game.presentation.ScenePresentationFrame;
import paradise.model.RewindAllowance;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Bounded view-only history. Course physics is held; one opaque checkpoint restores the whole world. */
public final class ShotReplay {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_SAMPLES = 128;
    public record Sample(int tick, byte[] scene) {
        public Sample { scene = scene.clone(); }
        @Override public byte[] scene() { return scene.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Sample sample && tick == sample.tick && java.util.Arrays.equals(scene, sample.scene);
        }
        @Override public int hashCode() { return 31 * tick + java.util.Arrays.hashCode(scene); }
    }
    public record State(List<Sample> samples, int stride, int cursor, int speed, boolean playing) {
        public State { samples = List.copyOf(samples); }
    }
    private final ArrayList<Sample> samples = new ArrayList<>();
    private int stride = 3, cursor, speed = 1, bytes;
    private boolean playing;
    public State snapshot() { return new State(samples, stride, cursor, speed, playing); }
    public void restore(State saved) {
        samples.clear(); samples.addAll(saved.samples()); stride = saved.stride(); cursor = saved.cursor();
        speed = saved.speed(); playing = saved.playing(); bytes = samples.stream().mapToInt(s -> s.scene.length).sum();
    }
    public void clear() { samples.clear(); bytes = cursor = 0; stride = 3; speed = 1; playing = false; }
    public boolean wantsSample(int tick) { return samples.isEmpty() || tick % stride == 0; }
    public void record(int tick, ScenePresentationFrame scene) {
        try {
            byte[] encoded = SceneFrameCodec.encode(scene);
            if (!samples.isEmpty() && samples.getLast().tick() == tick) { bytes -= samples.removeLast().scene.length; }
            samples.add(new Sample(tick, encoded)); bytes += encoded.length;
            while (samples.size() > MAX_SAMPLES || bytes > MAX_BYTES) {
                // Preserve both ends, repeatedly halve interior samples and their future capture rate.
                for (int i = samples.size() - 2; i > 0; i -= 2) bytes -= samples.remove(i).scene.length;
                stride *= 2;
            }
        } catch (IOException impossible) { throw new IllegalStateException("Cannot record bounded course view", impossible); }
    }
    public void begin(int ticks, ScenePresentationFrame latest) {
        record(ticks, latest); cursor = ticks; speed = RewindAllowance.replaySpeed(ticks); playing = true;
    }
    public boolean playing() { return playing; }
    public int speed() { return speed; }
    /** Returns true at the origin. No gameplay manager is restored during visual playback. */
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
