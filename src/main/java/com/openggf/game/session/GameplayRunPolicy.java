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
 * game would enter a stage or leave the act; the player's death routine consults it where the
 * shipped game would take a life.
 *
 * @param specialStageEntry whether special-stage entry (giant rings, S2 star-post stars, S3K
 *                          entry rings) is honoured
 * @param bonusStageEntry   whether bonus-stage entry (S3K star-post stars) is honoured
 * @param actCompletion     what happens when the results tally finishes
 * @param liveRewind        whether the player may rewind during the run
 * @param editorEntry       whether the level editor may be opened during the run
 * @param deathRestart      what a death of the main player leads to
 * @param livesCounter      whether the HUD draws the lives counter
 * @param zoneIntros        whether an act opens with its shipped intro cutscene (S3K Angel
 *                          Island 1's plane intro); false starts where the intro would hand
 *                          the player control, as the engine's skip-intros start does
 */
@ModApi
public record GameplayRunPolicy(boolean specialStageEntry,
                                boolean bonusStageEntry,
                                ActCompletion actCompletion,
                                boolean liveRewind,
                                boolean editorEntry,
                                DeathRestart deathRestart,
                                boolean livesCounter,
                                boolean zoneIntros) {

    /** What the engine does when a run's act is complete. */
    @ModApi
    public enum ActCompletion {
        /** Advance as the shipped game does: next act, next zone or the ending. */
        CONTINUE,
        /** End the run and return control to the host that launched it. */
        RETURN_TO_HOST
    }

    /** What the engine does when the run's main player dies. */
    @ModApi
    public enum DeathRestart {
        /**
         * As the shipped game: a life is lost, the last life or a time over ends in the
         * GAME OVER / TIME OVER card, and otherwise the act reloads behind a fade and its title
         * card at the last star post.
         */
        STOCK,
        /**
         * Unlimited attempts: no life is lost and no GAME OVER or TIME OVER card appears. The
         * death animation plays (a jump press may cut it short once the player has been dead for
         * {@link #FAST_RESTART_SKIP_FRAMES} steps), then the act reloads at once from its start,
         * with star-post progress cleared and no title card.
         */
        FAST_FROM_START
    }

    /**
     * Executed level steps the main player must have spent dead before a jump press may skip
     * the rest of a {@link DeathRestart#FAST_FROM_START} death animation (half a second).
     */
    public static final int FAST_RESTART_SKIP_FRAMES = 30;

    public GameplayRunPolicy {
        Objects.requireNonNull(actCompletion, "actCompletion");
        Objects.requireNonNull(deathRestart, "deathRestart");
    }

    /** The campaign: everything the shipped game allows. */
    public static GameplayRunPolicy stock() {
        return new GameplayRunPolicy(true, true, ActCompletion.CONTINUE, true, true,
                DeathRestart.STOCK, true, true);
    }

    /**
     * One act on its own: no special or bonus stages, the run ends at the results tally, and
     * neither rewind nor the editor can alter the attempt.
     */
    public static GameplayRunPolicy isolatedAct() {
        return new GameplayRunPolicy(false, false, ActCompletion.RETURN_TO_HOST, false, false,
                DeathRestart.STOCK, true, true);
    }

    /**
     * This policy with fast restarts: deaths reload the act from its start without costing a
     * life, and the lives counter is hidden because it no longer means anything.
     */
    public GameplayRunPolicy withFastRestarts() {
        return new GameplayRunPolicy(specialStageEntry, bonusStageEntry, actCompletion, liveRewind,
                editorEntry, DeathRestart.FAST_FROM_START, false, zoneIntros);
    }

    /**
     * This policy with acts starting where their intro cutscene would hand the player control,
     * on the first load and on every reload: a timed run then measures the act alone.
     */
    public GameplayRunPolicy withoutZoneIntros() {
        return new GameplayRunPolicy(specialStageEntry, bonusStageEntry, actCompletion, liveRewind,
                editorEntry, deathRestart, livesCounter, false);
    }

    /** True when a death reloads the act at once instead of following the shipped game. */
    public boolean fastDeathRestart() {
        return deathRestart == DeathRestart.FAST_FROM_START;
    }

    /** True when finishing the act hands control back to the run's host. */
    public boolean returnsToHostOnActCompletion() {
        return actCompletion == ActCompletion.RETURN_TO_HOST;
    }
}
