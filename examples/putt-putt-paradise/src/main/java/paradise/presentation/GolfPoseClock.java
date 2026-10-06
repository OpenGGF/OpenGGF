package paradise.presentation;

import com.openggf.game.presentation.PlayerPresentationPose;

/** Elapsed native-pose time, independent of the course, networking and rewind clocks. */
public final class GolfPoseClock {
    public record State(PlayerPresentationPose.Kind kind, long elapsed) { }

    private PlayerPresentationPose.Kind kind = PlayerPresentationPose.Kind.NATIVE;
    private long elapsed;

    public void update(PlayerPresentationPose.Kind next, boolean running) {
        if (next != kind) {
            kind = next;
            elapsed = 0;
        } else if (running) {
            elapsed++;
        }
    }

    public PlayerPresentationPose pose(int facing) {
        return new PlayerPresentationPose(kind, elapsed, facing);
    }

    public State snapshot() { return new State(kind, elapsed); }
    public void restore(State state) { kind = state.kind(); elapsed = state.elapsed(); }
    public void reset() { kind = PlayerPresentationPose.Kind.NATIVE; elapsed = 0; }
}
