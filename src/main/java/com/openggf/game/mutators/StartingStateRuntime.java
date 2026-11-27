package com.openggf.game.mutators;

import com.openggf.game.GameStateManager;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelState;
import com.openggf.game.NativeGameStateOps;
import com.openggf.game.rewind.RewindSnapshottable;

/**
 * World-owned starting-state seeds. A full assembly names the start it begins; a native
 * level-state replacement without an assembly (the Sonic 3 &amp; Knuckles results screen's
 * next-act reset) is a fresh act start. The first active native tick of that start applies
 * the seeds through the native counter owners, so the first gameplay frame already shows them.
 *
 * <p>Stage returns, checkpoint restores, seamless handoffs and editor swaps begin no start:
 * the native owners restore their own saved counters. A checkpoint restart grants rings
 * but keeps the native saved checkpoint time. Score and lives are written once, at the first
 * start of the world's game session. Native continues keep their own score/lives reset.
 */
public final class StartingStateRuntime implements RewindSnapshottable<StartingStateRuntime.Snapshot> {
    /** What the coming first active tick begins. */
    public enum Start { NONE, FRESH_ACT, LIFE_RESTART }

    private boolean sessionSeedPending = true;
    private boolean startPending;
    private Start pendingStart = Start.NONE;
    /** Native ticks until a delayed ring grant; -1 when none is waiting. */
    private int ringDelayRemaining = -1;
    /** Structural identity of the observed native level state; not historical state. */
    private LevelState observedLevelState;

    /** Records the start a qualifying assembly begins; decode and preview loads are ignored. */
    public void beforeAssembly(LevelLoadCause cause) {
        if (cause == null || cause == LevelLoadCause.DECODE_ONLY || cause == LevelLoadCause.PREVIEW) return;
        startPending = true;
        pendingStart = switch (cause) {
            case FULL_LEVEL_ASSEMBLY, FULL_RESTART -> Start.FRESH_ACT;
            case FULL_DEATH_RELOAD -> Start.LIFE_RESTART;
            default -> Start.NONE;
        };
        // A new assembly never inherits a grant timed from the old one.
        ringDelayRemaining = -1;
    }

    /**
     * Runs before the native tick. Returns the start this tick began, so other per-life
     * runtimes can reset with it.
     *
     * @param checkpointRestart whether the native respawn placed the player at a checkpoint
     * @param ringsAllowed whether main-level ring awards are allowed (No Rings dominates)
     */
    public Start beforeNativeTick(StartingStateMutatorPolicy policy, LevelState level, GameStateManager game,
                                  boolean checkpointRestart, boolean ringsAllowed) {
        if (level == null) return Start.NONE;
        Start start = Start.NONE;
        if (startPending) {
            startPending = false;
            start = pendingStart;
        } else if (observedLevelState != null && level != observedLevelState) {
            start = Start.FRESH_ACT;
            ringDelayRemaining = -1;
        }
        observedLevelState = level;
        if (start != Start.NONE) begin(start, policy, level, game, checkpointRestart, ringsAllowed);
        else tickDelayedGrant(policy, level, ringsAllowed);
        return start;
    }

    private void begin(Start start, StartingStateMutatorPolicy policy, LevelState level, GameStateManager game,
                       boolean checkpointRestart, boolean ringsAllowed) {
        if (sessionSeedPending) {
            sessionSeedPending = false;
            if (game != null && policy.score() >= 0) NativeGameStateOps.restoreScore(game, policy.score());
            if (game != null && policy.lives() > 0) NativeGameStateOps.restoreLives(game, policy.lives());
        }
        if (policy.seedsTime() && !(start == Start.LIFE_RESTART && checkpointRestart)) {
            // Seconds join whatever the native load already counted (normally zero frames).
            level.setTimerFrames(level.getTimerFrames() + policy.timeSeconds() * 60L);
        }
        ringDelayRemaining = -1;
        if (policy.grantsRings()) {
            if (policy.ringDelaySeconds() == 0) grant(policy.rings(), level, ringsAllowed);
            else ringDelayRemaining = policy.ringDelaySeconds() * 60;
        }
    }

    private void tickDelayedGrant(StartingStateMutatorPolicy policy, LevelState level, boolean ringsAllowed) {
        if (ringDelayRemaining < 0) return;
        // Turning the mutator off, or to zero rings, abandons the grant this life was waiting for.
        if (!policy.grantsRings()) { ringDelayRemaining = -1; return; }
        if (--ringDelayRemaining > 0) return;
        ringDelayRemaining = -1;
        grant(policy.rings(), level, ringsAllowed);
    }

    /**
     * Writes Ring_count directly, as a script would, rather than through the collection
     * routine: the 100/200 thresholds the grant reaches are marked in Extra_life_flags as
     * already paid, so a seeded count awards no extra life now or on the next pickup.
     * Later collection still awards the thresholds the grant did not reach.
     */
    static void grant(int amount, LevelState level, boolean ringsAllowed) {
        if (!ringsAllowed || amount <= 0) return;
        int held = level.getRings();
        int next = Math.min(StartingStateLimits.MAX_RINGS, held + amount);
        int flags = level.getRingExtraLifeFlags();
        if (held < 100 && next >= 100) flags |= 0x02;
        if (held < 200 && next >= 200) flags |= 0x04;
        level.setRings(next);
        level.setRingExtraLifeFlags(flags);
    }

    /** Ticks left before a delayed grant, or -1. Observation only. */
    public int ringDelayRemaining() { return ringDelayRemaining; }

    @Override public String key() { return "mutator-starting-state"; }
    @Override public Snapshot capture() {
        return new Snapshot(sessionSeedPending, startPending, pendingStart, ringDelayRemaining);
    }
    @Override public void restore(Snapshot snapshot) {
        sessionSeedPending = snapshot.sessionSeedPending();
        startPending = snapshot.startPending();
        pendingStart = snapshot.pendingStart();
        ringDelayRemaining = snapshot.ringDelayRemaining();
    }

    public record Snapshot(boolean sessionSeedPending, boolean startPending, Start pendingStart,
                           int ringDelayRemaining) {
        public Snapshot { java.util.Objects.requireNonNull(pendingStart, "pendingStart"); }
    }

    /** Host counter bounds shared with the creator-facing records. */
    static final class StartingStateLimits {
        static final int MAX_RINGS = 999;
        private StartingStateLimits() { }
    }
}
