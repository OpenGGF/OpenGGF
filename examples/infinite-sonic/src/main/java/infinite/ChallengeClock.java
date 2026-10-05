package infinite;

import com.openggf.game.rewind.RewindSnapshottable;

/** Session-owned pacing and active-play clock; all state rewinds with the course. */
public final class ChallengeClock implements RewindSnapshottable<ChallengeClock.Snapshot> {
    private double elapsed;
    private double fraction;
    private int stage;
    private boolean ended;
    /** Each 30-second stage adds 0.25x: 1x, 1.25x, 1.5x, 1.75x, 2x ... up to the 32x host ceiling. */
    public double multiplier() { return ended ? 1.0 : displayMultiplier(); }
    public double displayMultiplier() { return Math.min(32.0, 1.0 + 0.25 * stage); }
    public int secondsRemaining() { return (int) Math.ceil((1800.0 - elapsed) / 60.0 - 1e-9); }
    public void tick() { tick(60); }
    public void tick(int baseFramesPerSecond) {
        if (ended) return;
        elapsed += 60.0 / (Math.max(1, baseFramesPerSecond) * multiplier());
        if (elapsed >= 1800.0 - 1e-8) { elapsed = Math.max(0.0, elapsed - 1800.0); stage++; }
    }
    public int nextFrameSteps() {
        fraction += multiplier();
        int steps = (int) fraction;
        fraction -= steps;
        return steps;
    }
    public void end() { ended = true; }
    public void reset() { elapsed = 0; fraction = 0; stage = 0; ended = false; }
    @Override public String key() { return "infinite-sonic:clock"; }
    @Override public Snapshot capture() { return new Snapshot(elapsed, fraction, stage, ended); }
    @Override public void restore(Snapshot snapshot) {
        elapsed = snapshot.elapsed(); fraction = snapshot.fraction();
        stage = snapshot.stage(); ended = snapshot.ended();
    }
    @Override public void resetForMissingSnapshot() { reset(); }
    public record Snapshot(double elapsed, double fraction, int stage, boolean ended) { }
}
