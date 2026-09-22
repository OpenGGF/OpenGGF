package com.openggf.level;

import com.openggf.game.LevelState;
import com.openggf.game.ShieldType;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Engine-internal state for a fresh load that continues a running level without
 * entering its initial presentation loop. The game chooses the saved values and
 * consumes the counter bank at its destination screen-event boundary.
 *
 * <p>S3K loc_63C14 / loc_7F310 save Act3_ring_count, Act3_timer and the shield;
 * loc_62B6 skips the whole title owner and locked loop, not just its rendering.
 * This is deliberately outside the published Mod API.
 */
public final class LevelContinuationCarry {
    private LevelContinuationCarry() { }

    enum Phase { REQUESTED, LOADING, LOADED }
    record State(int zone, int act, int rings, long timerFrames, ShieldType shield,
                 Phase phase, boolean countersPending) {
        State phase(Phase next) {
            return new State(zone, act, rings, timerFrames, shield, next, countersPending);
        }
    }

    public static void request(LevelManager manager, int zone, int act,
                               int rings, long timerFrames, ShieldType shield) {
        var transitions = manager.getTransitions();
        transitions.requestZoneAndAct(zone, act, true);
        transitions.continuationCarry = new State(zone, act, rings, timerFrames, shield,
                Phase.REQUESTED, true);
    }

    static void beginLoad(LevelTransitionCoordinator transitions, int zone, int act) {
        State state = transitions.continuationCarry;
        transitions.continuationCarry = state != null && state.phase == Phase.REQUESTED
                && state.zone == zone && state.act == act ? state.phase(Phase.LOADING) : null;
    }

    static boolean bypassInitialPresentation(LevelTransitionCoordinator transitions) {
        State state = transitions.continuationCarry;
        return state != null && state.phase == Phase.LOADING;
    }

    static void restoreShield(LevelTransitionCoordinator transitions, AbstractPlayableSprite player) {
        State state = transitions.continuationCarry;
        if (state != null && state.phase == Phase.LOADING && state.shield != null) {
            player.giveShield(state.shield);
        }
    }

    static void finishLoad(LevelTransitionCoordinator transitions, boolean succeeded) {
        State state = transitions.continuationCarry;
        transitions.continuationCarry = succeeded && state != null && state.countersPending
                ? state.phase(Phase.LOADED) : null;
    }

    /** One-shot destination screen-event bank, distinct from the fresh-player shield restore. */
    public static void restoreCounters(LevelManager manager) {
        var transitions = manager.getTransitions();
        State state = transitions.continuationCarry;
        if (state == null || state.phase == Phase.REQUESTED || !state.countersPending
                || manager.getCurrentZone() != state.zone || manager.getCurrentAct() != state.act) return;
        LevelState level = manager.getLevelGamestate();
        if (level == null) return;
        // loc_59B1C and DEZ3's counterpart leave zero banks untouched.
        if (state.rings != 0) level.setRings(state.rings);
        if (state.timerFrames != 0) {
            level.setTimerFrames(state.timerFrames);
            level.resumeTimer();
        }
        transitions.continuationCarry = state.phase == Phase.LOADING
                ? new State(state.zone, state.act, state.rings, state.timerFrames,
                        state.shield, state.phase, false) : null;
    }
}
