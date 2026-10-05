package infinite;

import com.openggf.game.rewind.RewindSnapshottable;

/** Session-owned pacing and active-play clock; all state rewinds with the course. */
public final class ChallengeClock implements RewindSnapshottable<ChallengeClock.Snapshot> {
    /**
     * Mod design: the live rate glides toward each new stage instead of snapping, as the
     * rewind tape coast ramps its step rate. One 0.25x stage takes 60 presentation frames.
     */
    static final double RAMP_PER_FRAME = 0.25 / 60;
    // Mod design: a run resumed by CONTINUE sets off at 1x and climbs back to its stage in 90 frames.
    static final int RESUME_RAMP_FRAMES = 90;
    private double elapsed;
    private double fraction;
    private int stage;
    private boolean ended;
    // The pacing rate actually applied; it trails displayMultiplier() during a ramp.
    private double rate = 1.0;
    // How far the rate moves per frame: RAMP_PER_FRAME, or faster while catching up after CONTINUE.
    private double step = RAMP_PER_FRAME;
    /** The live, ramped pace that steps the game and the audio; 1x once the run has ended. */
    public double multiplier() { return ended ? 1.0 : rate; }
    /** Each 30-second stage adds 0.25x: 1x, 1.25x, 1.5x, 1.75x, 2x ... up to the 32x host ceiling. */
    public double displayMultiplier() { return target(stage); }
    private static double target(int stage) { return Math.min(32.0, 1.0 + 0.25 * stage); }
    public int secondsRemaining() { return (int) Math.ceil((1800.0 - elapsed) / 60.0 - 1e-9); }
    public void tick() { tick(60); }
    public void tick(int baseFramesPerSecond) {
        if (ended) return;
        elapsed += 60.0 / (Math.max(1, baseFramesPerSecond) * multiplier());
        if (elapsed >= 1800.0 - 1e-8) { elapsed = Math.max(0.0, elapsed - 1800.0); stage++; }
    }
    /** Called once per eligible presentation frame: advances the ramp, then the step budget. */
    public int nextFrameSteps() {
        if (!ended) {
            double goal = displayMultiplier();
            rate = rate < goal ? Math.min(goal, rate + step) : Math.max(goal, rate - step);
            if (rate == goal) step = RAMP_PER_FRAME;
        }
        fraction += multiplier();
        int steps = (int) fraction;
        fraction -= steps;
        return steps;
    }
    public void end() { ended = true; }
    /** Restores a run's clock for CONTINUE, setting off from 1x and easing back up to its stage. */
    public void resumeFrom(Snapshot snapshot) {
        restore(snapshot);
        ended = false;
        rate = 1.0;
        step = Math.max(RAMP_PER_FRAME, (displayMultiplier() - 1.0) / RESUME_RAMP_FRAMES);
    }
    public void reset() { elapsed = 0; fraction = 0; stage = 0; ended = false; rate = 1.0; step = RAMP_PER_FRAME; }
    @Override public String key() { return "infinite-sonic:clock"; }
    @Override public Snapshot capture() { return new Snapshot(elapsed, fraction, stage, ended, rate, step); }
    @Override public void restore(Snapshot snapshot) {
        elapsed = snapshot.elapsed(); fraction = snapshot.fraction();
        stage = snapshot.stage(); ended = snapshot.ended(); rate = snapshot.rate(); step = snapshot.step();
    }
    @Override public void resetForMissingSnapshot() { reset(); }
    public record Snapshot(double elapsed, double fraction, int stage, boolean ended, double rate, double step) {
        /** A settled clock: the live rate already matches the stage. */
        public Snapshot(double elapsed, double fraction, int stage, boolean ended) {
            this(elapsed, fraction, stage, ended, target(stage), RAMP_PER_FRAME);
        }
    }
}
