package paradise.presentation;

import com.openggf.game.presentation.SceneReplay;
import com.openggf.game.presentation.ScenePresentationFrame;
import paradise.model.RewindAllowance;

/** Golf's duration policy over the engine's bounded value-scene replay. No world restoration here. */
public final class ShotReplay {
    private final SceneReplay replay = new SceneReplay();
    public SceneReplay.State snapshot() { return replay.snapshot(); }
    public void restore(SceneReplay.State saved) { replay.restore(saved); }
    public void clear() { replay.clear(); }
    public boolean wantsSample(int tick) { return replay.wantsSample(tick); }
    public void record(int tick, ScenePresentationFrame scene) { replay.record(tick, scene); }
    public void begin(int ticks, ScenePresentationFrame latest) {
        replay.begin(ticks, RewindAllowance.replaySpeed(ticks), latest);
    }
    public boolean playing() { return replay.playing(); }
    public int speed() { return replay.speed(); }
    public boolean atOrigin() { return replay.atOrigin(); }
    public boolean step() { return replay.step(); }
    public ScenePresentationFrame frame(long revision) { return replay.frame(revision); }
    public static ScenePresentationFrame revision(ScenePresentationFrame frame, long revision) {
        return SceneReplay.revision(frame, revision);
    }
}
