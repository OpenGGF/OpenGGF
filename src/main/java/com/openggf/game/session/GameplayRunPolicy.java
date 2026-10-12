package com.openggf.game.session;

import com.openggf.game.ModApi;

import java.util.Objects;

/**
 * What a launched gameplay run permits beyond its starting act. The stock policy reproduces
 * the campaign: special and bonus stages can be entered, act completion advances to the next
 * act, zone or ending, and live rewind and editor entry are available. A host that launches an
 * isolated run (a timed attempt, a practice drill) narrows it.
 *
 * <p>The policy is session data, not a game rule: it is fixed when the run starts, survives a
 * retry's level reload, and is recorded with any input recording that replays the run, so a
 * replay applies the same restrictions. ROM objects consult it through
 * {@link com.openggf.level.objects.ObjectServices#runPolicy()} at the point where the shipped
 * game would enter a stage or leave the act.
 *
 * @param specialStageEntry whether special-stage entry (giant rings, S2 star-post stars, S3K
 *                          entry rings) is honoured
 * @param bonusStageEntry   whether bonus-stage entry (S3K star-post stars) is honoured
 * @param actCompletion     what happens when the results tally finishes
 * @param liveRewind        whether the player may rewind during the run
 * @param editorEntry       whether the level editor may be opened during the run
 */
@ModApi
public record GameplayRunPolicy(boolean specialStageEntry,
                                boolean bonusStageEntry,
                                ActCompletion actCompletion,
                                boolean liveRewind,
                                boolean editorEntry) {

    /** What the engine does when a run's act is complete. */
    @ModApi
    public enum ActCompletion {
        /** Advance as the shipped game does: next act, next zone or the ending. */
        CONTINUE,
        /** End the run and return control to the host that launched it. */
        RETURN_TO_HOST
    }

    public GameplayRunPolicy {
        Objects.requireNonNull(actCompletion, "actCompletion");
    }

    /** The campaign: everything the shipped game allows. */
    public static GameplayRunPolicy stock() {
        return new GameplayRunPolicy(true, true, ActCompletion.CONTINUE, true, true);
    }

    /**
     * One act on its own: no special or bonus stages, the run ends at the results tally, and
     * neither rewind nor the editor can alter the attempt.
     */
    public static GameplayRunPolicy isolatedAct() {
        return new GameplayRunPolicy(false, false, ActCompletion.RETURN_TO_HOST, false, false);
    }

    /** True when finishing the act hands control back to the run's host. */
    public boolean returnsToHostOnActCompletion() {
        return actCompletion == ActCompletion.RETURN_TO_HOST;
    }
}
