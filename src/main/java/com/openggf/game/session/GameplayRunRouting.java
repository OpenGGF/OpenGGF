package com.openggf.game.session;

import com.openggf.level.SeamlessLevelTransitionRequest;

/**
 * Transition decisions derived from the session's {@link GameplayRunPolicy}. Kept out of
 * {@code GameLoop} so the branching is unit-testable without booting the engine.
 */
public final class GameplayRunRouting {
    private GameplayRunRouting() {
    }

    /** The current gameplay session's run policy, or the stock policy outside a session. */
    public static GameplayRunPolicy currentPolicy() {
        GameplayModeContext session = SessionManager.getCurrentGameplayMode();
        return session != null ? session.getRunPolicy() : GameplayRunPolicy.stock();
    }

    /**
     * An S3K seamless request ends a host-returning run only when it is a cross-act advance:
     * a {@code RELOAD_TARGET_LEVEL} whose target differs from the act the run started in.
     * Mid-act sequences ({@code MUTATE_ONLY}, {@code RELOAD_SAME_LEVEL}, or a reload that
     * targets the run's own act) stay with the ordinary transition path.
     */
    public static boolean endsRunOnSeamlessTransition(GameplayRunPolicy policy, int runZone, int runAct,
                                                      SeamlessLevelTransitionRequest.TransitionType type,
                                                      int targetZone, int targetAct) {
        if (policy == null || !policy.returnsToHostOnActCompletion()
                || type != SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL
                || runZone < 0) {
            return false;
        }
        return targetZone != runZone || targetAct != runAct;
    }
}
