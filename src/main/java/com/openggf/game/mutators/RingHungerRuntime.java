package com.openggf.game.mutators;

import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * World-owned ring hunger. Every act start and life restart begins a fresh grace countdown;
 * when it ends, {@code ringsLost} rings leave the native ring counter every period. Holding
 * the threshold or fewer for the starvation time calls the native kill routine.
 *
 * <p>The clocks advance only on active main-level native ticks while the level timer runs and
 * the leader has control, so pause, title cards, stages, results, signposts, scripted control
 * and boss fights (the native boss flag) hold them. Stage returns and checkpoint restores keep
 * the captured state; the whole state is rewind history.
 */
public final class RingHungerRuntime implements RewindSnapshottable<RingHungerRuntime.Snapshot> {
    public enum Phase { OFF, GRACE, DRAIN, STARVING }

    /** HUD readout: the phase and the share of its countdown that remains, in sixteenths (0 while draining). */
    public record Indicator(Phase phase, int remainingSixteenths) {
        public static final Indicator NONE = new Indicator(Phase.OFF, 0);
        public Indicator {
            java.util.Objects.requireNonNull(phase, "phase");
            if (remainingSixteenths < 0 || remainingSixteenths > 16)
                throw new IllegalArgumentException("Indicator fill is 0..16 sixteenths");
        }
    }

    private Phase phase = Phase.OFF;
    private int graceRemaining;
    private int drainElapsed;
    private int starveElapsed;
    /** Last admitted timing, kept so the HUD can scale its readout without the policy. */
    private int graceTicks = 1, periodTicks = 60, starveTicks = 60;

    /** A new act or life begins a new grace countdown at its first active tick. */
    public void beginLife() { phase = Phase.OFF; graceRemaining = drainElapsed = starveElapsed = 0; }

    public void beforeNativeTick(RingHungerMutatorPolicy policy, AbstractPlayableSprite leader,
                                 LevelState level, GameStateManager game) {
        if (!policy.active()) { beginLife(); return; }
        graceTicks = Math.max(1, policy.graceTicks());
        periodTicks = policy.periodTicks();
        starveTicks = policy.starveTicks();
        if (phase == Phase.OFF) {
            phase = policy.graceTicks() == 0 ? Phase.DRAIN : Phase.GRACE;
            graceRemaining = policy.graceTicks();
            drainElapsed = starveElapsed = 0;
        }
        if (held(leader, level, game)) return;
        if (phase == Phase.GRACE) {
            if (--graceRemaining > 0) return;
            phase = Phase.DRAIN;
            drainElapsed = starveElapsed = 0;
        }
        // An edit through Resume can shorten a period below the elapsed count.
        drainElapsed = Math.min(drainElapsed, periodTicks - 1);
        if (++drainElapsed >= periodTicks) {
            drainElapsed = 0;
            int rings = level.getRings();
            // The native counter owner; a drain is not a hurt, so the extra-life bank stays.
            if (rings > 0) level.setRings(Math.max(0, rings - policy.ringsLost()));
        }
        if (level.getRings() > policy.threshold()) {
            phase = Phase.DRAIN;
            starveElapsed = 0;
            return;
        }
        phase = Phase.STARVING;
        starveElapsed = Math.min(starveElapsed, starveTicks - 1);
        if (++starveElapsed >= starveTicks && !leader.isDebugMode()) {
            // KillCharacter / KillSonic / Kill_Character: unconditional, like a crush,
            // bypassing rings, shields and invincibility. The cause only selects its sound.
            leader.applyCrushDeath();
        }
    }

    /** Whether the native game is holding the act's own clock or the player's control. */
    private static boolean held(AbstractPlayableSprite leader, LevelState level, GameStateManager game) {
        return leader == null || level == null || leader.getDead() || leader.isObjectControlled()
                || level.isTimerPaused() || game != null && (game.isBossFightActive() || game.isEndOfLevelActive());
    }

    public Phase phase() { return phase; }

    public Indicator indicator() {
        return switch (phase) {
            case OFF -> Indicator.NONE;
            case GRACE -> new Indicator(phase, sixteenths(graceRemaining, graceTicks));
            // The grace is spent: an empty dial while rings drain above the starving count.
            case DRAIN -> new Indicator(phase, 0);
            case STARVING -> new Indicator(phase, sixteenths(starveTicks - starveElapsed, starveTicks));
        };
    }

    private static int sixteenths(int remaining, int total) {
        if (total <= 0 || remaining <= 0) return 0;
        return (int) Math.min(16, ((long) remaining * 16 + total - 1) / total);
    }

    @Override public String key() { return "mutator-ring-hunger"; }
    @Override public Snapshot capture() {
        return new Snapshot(phase, graceRemaining, drainElapsed, starveElapsed, graceTicks, periodTicks, starveTicks);
    }
    @Override public void restore(Snapshot s) {
        phase = s.phase(); graceRemaining = s.graceRemaining(); drainElapsed = s.drainElapsed();
        starveElapsed = s.starveElapsed(); graceTicks = s.graceTicks(); periodTicks = s.periodTicks();
        starveTicks = s.starveTicks();
    }

    public record Snapshot(Phase phase, int graceRemaining, int drainElapsed, int starveElapsed,
                           int graceTicks, int periodTicks, int starveTicks) {
        public Snapshot { java.util.Objects.requireNonNull(phase, "phase"); }
    }
}
