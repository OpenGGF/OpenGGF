package com.openggf.level;

import com.openggf.game.BonusStageType;

/**
 * Holds all transition request/consume state that was previously scattered
 * across LevelManager fields.  LevelManager owns a single instance and
 * exposes it via {@code getTransitions()}.
 * <p>
 * This is a pure state holder — it never calls back into LevelManager or
 * any other singleton.
 */
public class LevelTransitionCoordinator {

    // ── Special stage ──────────────────────────────────────────────────
    private boolean specialStageRequestedFromCheckpoint;
    private boolean specialStageEntryRoutineArmed;
    private boolean specialStageEntryAdvancesLevel;
    private boolean specialStageReturnLevelReloadRequested;

    // ── S3K big ring return (ROM: Saved2_* variables) ──────────
    private BigRingReturnState bigRingReturn;

    // ── Bonus stage ───────────────────────────────────────────────────
    private BonusStageType bonusStageRequested;
    private int bonusStageReturnCheckpointIndex = -1;

    // ── Title card ─────────────────────────────────────────────────────
    private boolean titleCardRequested;
    private int titleCardZone = -1;
    private int titleCardAct = -1;
    private boolean inLevelTitleCardRequested;
    private int inLevelTitleCardZone = -1;
    private int inLevelTitleCardAct = -1;
    private boolean inLevelTitleCardLevelGamestateResetRequested;
    private int inLevelTitleCardResetAdditionalDispatches;
    private int inLevelTitleCardResetPhaseOneDispatchOverlap;
    private boolean inLevelTitleCardPlayerControlLockRequested;
    private int inLevelTitleCardExitAdditionalDispatches;
    private int inLevelTitleCardExitPhaseOneDispatchOverlap;

    // ── Transition request flags (for fade-coordinated transitions) ────
    private boolean respawnRequested;
    private boolean nextActRequested;
    private boolean nextZoneRequested;
    private boolean specificZoneActRequested;
    private int requestedZone = -1;
    private int requestedAct = -1;
    private int requestedMusicId = -1;

    // ── Seamless transitions ───────────────────────────────────────────
    private boolean seamlessTransitionRequested;
    private SeamlessLevelTransitionRequest pendingSeamlessTransitionRequest;

    // ── Credits ────────────────────────────────────────────────────────
    private boolean creditsRequested;

    // ── HUD / music suppression ────────────────────────────────────────
    private boolean forceHudSuppressed;
    private boolean suppressNextMusicChange;

    // ── Level inactive flag ────────────────────────────────────────────
    private boolean levelInactiveForTransition;

    // ================================================================
    //  Special stage requests
    // ================================================================

    /**
     * Advances an end-of-act results card to {@code Got_NextLevel}, whose body
     * runs on the FOLLOWING frame and both advances the level and enters the
     * special stage.
     * <p>
     * ROM: {@code Got_Wait} ("_incObj/3A Got Through Card.asm":112-116) spends
     * the frame its post-tally delay expires doing nothing but
     * {@code addq.b #2,obRoutine}; {@code Got_NextLevel} (same file, 175-202)
     * executes one frame later, reading the next level out of
     * {@code LevelOrder} into {@code v_zone_act} and then, for a collected
     * giant ring, writing {@code v_gamemode = id_Special}. Doing either on the
     * expiry frame instead changes the level a frame early, which a whole-run
     * replay sees as the source segment losing ownership of its final
     * represented frame.
     */
    public void advanceToSpecialStageEntryRoutine() {
        this.specialStageEntryRoutineArmed = true;
    }

    /**
     * Request entry to special stage using the current game's access method.
     * Used by owners whose ROM counterpart writes the game mode in the same
     * object tick as the trigger (S2 {@code Obj79_Star}, S3K
     * {@code SSEntryFlash_GoSS}).
     */
    public void requestSpecialStageEntry() {
        this.specialStageRequestedFromCheckpoint = true;
    }

    /**
     * Consumes and clears the special stage request flag. A routine advance
     * armed by {@link #advanceToSpecialStageEntryRoutine()} becomes the
     * pending request here, so the entry is serviced by the next frame's
     * consume rather than this one.
     *
     * @return true if a special stage was requested since last check
     */
    public boolean consumeSpecialStageRequest() {
        boolean requested = specialStageRequestedFromCheckpoint;
        specialStageRequestedFromCheckpoint = false;
        if (!requested && specialStageEntryRoutineArmed) {
            specialStageEntryRoutineArmed = false;
            specialStageRequestedFromCheckpoint = true;
            specialStageEntryAdvancesLevel = true;
        }
        return requested;
    }

    /**
     * True exactly once, on the frame the armed {@code Got_NextLevel} body
     * runs, for the {@code v_zone_act} write that precedes its mode change.
     */
    public boolean consumeSpecialStageEntryLevelAdvance() {
        boolean advance = specialStageEntryAdvancesLevel;
        specialStageEntryAdvancesLevel = false;
        return advance;
    }

    /**
     * Non-consuming peek at a pending special-stage entry request. The LEVEL
     * tick's {@link #consumeSpecialStageRequest()} remains the only consumer;
     * this exists so trace-run replay can observe an organically raised
     * transition without swallowing it (spec 2026-07-18, addition #1).
     */
    public boolean isSpecialStageRequested() {
        return specialStageRequestedFromCheckpoint
                || specialStageEntryRoutineArmed;
    }

    /**
     * Consumes and clears the pending level-reload request for special-stage
     * return.
     *
     * @return true if the next act should be loaded before resuming gameplay
     */
    public boolean consumeSpecialStageReturnLevelReloadRequest() {
        boolean requested = specialStageReturnLevelReloadRequested;
        specialStageReturnLevelReloadRequested = false;
        return requested;
    }

    /**
     * Sets the special-stage return level reload flag.
     * Called by LevelManager when advancing to the next act after a special
     * stage return, and cleared at the start of seamless/level-load transitions.
     */
    public void setSpecialStageReturnLevelReloadRequested(boolean requested) {
        this.specialStageReturnLevelReloadRequested = requested;
    }

    // ================================================================
    //  Big ring return position
    // ================================================================

    /**
     * Saves the big ring return state (ROM: Save_Level_Data2 -> Saved2_*).
     */
    public void saveBigRingReturn(BigRingReturnState state) {
        this.bigRingReturn = state;
    }

    /** Returns true if a big ring return state is saved. */
    public boolean hasBigRingReturn() {
        return bigRingReturn != null;
    }

    /** Returns the saved big ring return state, or null if none. */
    public BigRingReturnState getBigRingReturn() {
        return bigRingReturn;
    }

    /** Clears the big ring return state. */
    public void clearBigRingReturn() {
        this.bigRingReturn = null;
    }

    // ================================================================
    //  Bonus stage requests
    // ================================================================

    /**
     * Request entry to a bonus stage from a star post bonus star.
     * Called by Sonic3kStarPostBonusStarChild on player touch.
     */
    public void requestBonusStageEntry(BonusStageType type) {
        this.bonusStageRequested = type;
    }

    /**
     * Consumes and clears the bonus stage request.
     * @return the requested bonus stage type, or null if none requested
     */
    public BonusStageType consumeBonusStageRequest() {
        BonusStageType requested = bonusStageRequested;
        bonusStageRequested = null;
        return requested;
    }

    /**
     * Non-consuming peek at a pending bonus-stage entry request; null when
     * none is pending. Mirrors {@link #isRespawnRequested()}.
     */
    public BonusStageType peekBonusStageRequest() {
        return bonusStageRequested;
    }

    /**
     * Signals that the next level load is a bonus stage return.
     * Set before {@code loadZoneAndAct()} so that {@code onInitLevel()} can
     * detect the return and skip intros. The checkpoint index is restored
     * to {@code CheckpointState} after the load completes.
     *
     * @param checkpointIndex the Last_star_post_hit value saved before bonus entry
     */
    public void setBonusStageReturnCheckpointIndex(int checkpointIndex) {
        this.bonusStageReturnCheckpointIndex = checkpointIndex;
    }

    /** Returns true if this level load is a bonus stage return. */
    public boolean isBonusStageReturn() {
        return bonusStageReturnCheckpointIndex >= 0;
    }

    /** Returns the checkpoint index for bonus stage return, or -1 if not returning. */
    public int getBonusStageReturnCheckpointIndex() {
        return bonusStageReturnCheckpointIndex;
    }

    /** Clears the bonus stage return signal. */
    public void clearBonusStageReturn() {
        this.bonusStageReturnCheckpointIndex = -1;
    }

    // ================================================================
    //  Title card requests
    // ================================================================

    /**
     * Requests a title card to be shown for the current zone/act.
     * Called when a new level is loaded.
     *
     * @param zone Zone index (0-10)
     * @param act  Act index (0-2)
     */
    public void requestTitleCard(int zone, int act) {
        this.titleCardRequested = true;
        this.titleCardZone = zone;
        this.titleCardAct = act;
    }

    /**
     * Requests an in-level (transparent) title card overlay.
     */
    public void requestInLevelTitleCard(int zone, int act) {
        requestInLevelTitleCard(zone, act, false);
    }

    public void requestInLevelTitleCard(int zone, int act, boolean resetLevelGamestateAtDisplay) {
        requestInLevelTitleCard(zone, act, resetLevelGamestateAtDisplay, 0);
    }

    public void requestInLevelTitleCard(int zone, int act, boolean resetLevelGamestateAtDisplay,
                                        int resetAdditionalDispatches) {
        requestInLevelTitleCard(zone, act, resetLevelGamestateAtDisplay,
                resetAdditionalDispatches, false);
    }

    public void requestInLevelTitleCard(int zone, int act, boolean resetLevelGamestateAtDisplay,
                                        int resetAdditionalDispatches, boolean lockPlayerControl) {
        requestInLevelTitleCard(zone, act, resetLevelGamestateAtDisplay,
                resetAdditionalDispatches, lockPlayerControl, 0);
    }

    public void requestInLevelTitleCard(int zone, int act, boolean resetLevelGamestateAtDisplay,
                                        int resetAdditionalDispatches, boolean lockPlayerControl,
                                        int exitAdditionalDispatches) {
        requestInLevelTitleCard(zone, act, resetLevelGamestateAtDisplay,
                resetAdditionalDispatches, 0, lockPlayerControl, exitAdditionalDispatches);
    }

    public void requestInLevelTitleCard(int zone, int act, boolean resetLevelGamestateAtDisplay,
                                        int resetAdditionalDispatches,
                                        int resetPhaseOneDispatchOverlap,
                                        boolean lockPlayerControl,
                                        int exitAdditionalDispatches) {
        requestInLevelTitleCard(zone, act, resetLevelGamestateAtDisplay,
                resetAdditionalDispatches, resetPhaseOneDispatchOverlap,
                lockPlayerControl, exitAdditionalDispatches, 0);
    }

    public void requestInLevelTitleCard(int zone, int act, boolean resetLevelGamestateAtDisplay,
                                        int resetAdditionalDispatches,
                                        int resetPhaseOneDispatchOverlap,
                                        boolean lockPlayerControl,
                                        int exitAdditionalDispatches,
                                        int exitPhaseOneDispatchOverlap) {
        this.inLevelTitleCardRequested = true;
        this.inLevelTitleCardZone = zone;
        this.inLevelTitleCardAct = act;
        this.inLevelTitleCardLevelGamestateResetRequested = resetLevelGamestateAtDisplay;
        this.inLevelTitleCardResetAdditionalDispatches = Math.max(0, resetAdditionalDispatches);
        this.inLevelTitleCardResetPhaseOneDispatchOverlap =
                Math.max(0, resetPhaseOneDispatchOverlap);
        this.inLevelTitleCardPlayerControlLockRequested = lockPlayerControl;
        this.inLevelTitleCardExitAdditionalDispatches = Math.max(0, exitAdditionalDispatches);
        this.inLevelTitleCardExitPhaseOneDispatchOverlap =
                Math.max(0, exitPhaseOneDispatchOverlap);
    }

    /**
     * Checks if a title card has been requested.
     *
     * @return true if a title card was requested since last check
     */
    public boolean isTitleCardRequested() {
        return titleCardRequested;
    }

    /**
     * Consumes and clears the title card request flag.
     *
     * @return true if a title card was requested since last check
     */
    public boolean consumeTitleCardRequest() {
        boolean requested = titleCardRequested;
        titleCardRequested = false;
        return requested;
    }

    /**
     * Consumes and clears the in-level title card request flag.
     */
    public boolean consumeInLevelTitleCardRequest() {
        boolean requested = inLevelTitleCardRequested;
        inLevelTitleCardRequested = false;
        return requested;
    }

    public boolean consumeInLevelTitleCardLevelGamestateResetRequest() {
        boolean requested = inLevelTitleCardLevelGamestateResetRequested;
        inLevelTitleCardLevelGamestateResetRequested = false;
        return requested;
    }

    public boolean hasPendingInLevelTitleCardHeldCounterDispatch() {
        return inLevelTitleCardRequested && inLevelTitleCardLevelGamestateResetRequested;
    }

    public int consumeInLevelTitleCardResetAdditionalDispatches() {
        int dispatches = inLevelTitleCardResetAdditionalDispatches;
        inLevelTitleCardResetAdditionalDispatches = 0;
        return dispatches;
    }

    public int consumeInLevelTitleCardResetPhaseOneDispatchOverlap() {
        int dispatches = inLevelTitleCardResetPhaseOneDispatchOverlap;
        inLevelTitleCardResetPhaseOneDispatchOverlap = 0;
        return dispatches;
    }

    public boolean consumeInLevelTitleCardPlayerControlLockRequest() {
        boolean requested = inLevelTitleCardPlayerControlLockRequested;
        inLevelTitleCardPlayerControlLockRequested = false;
        return requested;
    }

    public int consumeInLevelTitleCardExitAdditionalDispatches() {
        int dispatches = inLevelTitleCardExitAdditionalDispatches;
        inLevelTitleCardExitAdditionalDispatches = 0;
        return dispatches;
    }

    public int consumeInLevelTitleCardExitPhaseOneDispatchOverlap() {
        int dispatches = inLevelTitleCardExitPhaseOneDispatchOverlap;
        inLevelTitleCardExitPhaseOneDispatchOverlap = 0;
        return dispatches;
    }

    /**
     * Gets the zone index for the requested title card.
     *
     * @return zone index, or -1 if none requested
     */
    public int getTitleCardZone() {
        return titleCardZone;
    }

    /**
     * Gets the act index for the requested title card.
     *
     * @return act index, or -1 if none requested
     */
    public int getTitleCardAct() {
        return titleCardAct;
    }

    public int getInLevelTitleCardZone() {
        return inLevelTitleCardZone;
    }

    public int getInLevelTitleCardAct() {
        return inLevelTitleCardAct;
    }

    // ================================================================
    //  Transition requests (fade-coordinated)
    // ================================================================

    /**
     * Request a respawn (death). GameLoop will handle the fade transition.
     */
    public void requestRespawn() {
        this.respawnRequested = true;
    }

    /**
     * Check and consume respawn request.
     *
     * @return true if respawn was requested
     */
    public boolean consumeRespawnRequest() {
        boolean requested = respawnRequested;
        respawnRequested = false;
        return requested;
    }

    public boolean isRespawnRequested() {
        return respawnRequested;
    }

    public void restoreRespawnRequested(boolean respawnRequested) {
        this.respawnRequested = respawnRequested;
    }

    /**
     * Request transition to next act. GameLoop will handle the fade transition.
     */
    public void requestNextAct() {
        this.nextActRequested = true;
    }

    /**
     * Check and consume next act request.
     *
     * @return true if next act was requested
     */
    public boolean consumeNextActRequest() {
        boolean requested = nextActRequested;
        nextActRequested = false;
        return requested;
    }

    /**
     * Request transition to next zone. GameLoop will handle the fade transition.
     */
    public void requestNextZone() {
        this.nextZoneRequested = true;
    }

    /**
     * Check and consume next zone request.
     *
     * @return true if next zone was requested
     */
    public boolean consumeNextZoneRequest() {
        boolean requested = nextZoneRequested;
        nextZoneRequested = false;
        return requested;
    }

    /**
     * Request transition to a specific zone and act. GameLoop will handle the fade transition.
     *
     * @param zone the zone index (0-based)
     * @param act the act index (0-based)
     */
    public void requestZoneAndAct(int zone, int act) {
        requestZoneAndAct(zone, act, false);
    }

    /**
     * Request transition to a specific zone and act with optional level deactivation
     * during the pending fade.
     *
     * @param zone                the zone index (0-based)
     * @param act                 the act index (0-based)
     * @param deactivateLevelNow  true to freeze level updates until the transition completes
     */
    public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {
        requestZoneAndAct(zone, act, deactivateLevelNow, -1);
    }

    /**
     * Request a zone/act transition with a track that must be started only
     * after the destination level has finished loading.
     *
     * <p>This is for cutscenes whose source track is still fading while they
     * hand control to a new zone.  Deferring the destination track prevents a
     * source-side fade command from silencing it.
     */
    public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow, int musicId) {
        this.requestedZone = zone;
        this.requestedAct = act;
        this.requestedMusicId = musicId;
        this.specificZoneActRequested = true;
        this.levelInactiveForTransition = deactivateLevelNow;
    }

    /**
     * Check and consume specific zone/act request.
     *
     * @return true if a specific zone/act was requested
     */
    public boolean consumeZoneActRequest() {
        boolean requested = specificZoneActRequested;
        specificZoneActRequested = false;
        return requested;
    }

    /**
     * Get the requested zone index. Only valid after consumeZoneActRequest() returns true.
     *
     * @return the requested zone index
     */
    public int getRequestedZone() {
        return requestedZone;
    }

    /**
     * Get the requested act index. Only valid after consumeZoneActRequest() returns true.
     *
     * @return the requested act index
     */
    public int getRequestedAct() {
        return requestedAct;
    }

    /**
     * @return a post-load music ID for the consumed zone/act request, or -1
     * when the destination should use its ordinary level-load music.
     */
    public int getRequestedMusicId() {
        return requestedMusicId;
    }

    // ================================================================
    //  Seamless transitions
    // ================================================================

    /**
     * Request an in-place seamless transition. GameLoop will execute it directly
     * without fade.
     */
    public void requestSeamlessTransition(SeamlessLevelTransitionRequest request) {
        if (request == null) {
            return;
        }
        this.pendingSeamlessTransitionRequest = request;
        this.seamlessTransitionRequested = true;
        this.levelInactiveForTransition = request.deactivateLevelNow();
    }

    /**
     * Consumes the pending seamless transition request.
     */
    public SeamlessLevelTransitionRequest consumeSeamlessTransitionRequest() {
        if (!seamlessTransitionRequested) {
            return null;
        }
        seamlessTransitionRequested = false;
        SeamlessLevelTransitionRequest request = pendingSeamlessTransitionRequest;
        pendingSeamlessTransitionRequest = null;
        return request;
    }

    // ================================================================
    //  Credits
    // ================================================================

    /**
     * Request transition to ending credits sequence.
     * Called by Sonic1EndingSTHObjectInstance after the STH logo timer expires.
     */
    public void requestCreditsTransition() {
        this.creditsRequested = true;
    }

    /**
     * Check and consume credits transition request.
     *
     * @return true if credits were requested
     */
    public boolean consumeCreditsRequest() {
        boolean requested = creditsRequested;
        creditsRequested = false;
        return requested;
    }

    // ================================================================
    //  HUD / music suppression
    // ================================================================

    /**
     * Force-suppress HUD rendering. Used during credits demo playback
     * where the HUD should not appear regardless of zone settings.
     */
    public void setForceHudSuppressed(boolean suppressed) {
        this.forceHudSuppressed = suppressed;
    }

    /** Returns true if HUD rendering is force-suppressed. */
    public boolean isForceHudSuppressed() {
        return forceHudSuppressed;
    }

    /**
     * Suppresses the zone music that normally plays on the next loadLevel() call.
     * Resets after one use. Used by credits sequence to prevent zone music from
     * overriding the credits music.
     */
    public void setSuppressNextMusicChange(boolean suppress) {
        this.suppressNextMusicChange = suppress;
    }

    /** Returns true if the next music change should be suppressed. */
    public boolean isSuppressNextMusicChange() {
        return suppressNextMusicChange;
    }

    /**
     * Reads and clears the suppress-next-music flag.
     * <p>
     * The flag is strictly single-use: a caller that sets it but never reaches
     * the audio init step (preview-capture loads, a load that fails early, an
     * in-place act transition) must not leave it latched for a later level load,
     * which would silence that level's music until the next load or a respawn.
     *
     * @return true if the caller should skip the level music change
     */
    public boolean consumeSuppressNextMusicChange() {
        boolean suppress = suppressNextMusicChange;
        suppressNextMusicChange = false;
        return suppress;
    }

    // ================================================================
    //  Level inactive flag
    // ================================================================

    /**
     * Returns true while the current level should be treated as inactive for a
     * pending zone/act transition.
     */
    public boolean isLevelInactiveForTransition() {
        return levelInactiveForTransition;
    }

    /**
     * Sets the level-inactive-for-transition flag.
     */
    public void setLevelInactiveForTransition(boolean inactive) {
        this.levelInactiveForTransition = inactive;
    }

    // ================================================================
    //  Bulk reset
    // ================================================================

    /**
     * Clears all transition-related state.
     * Called from {@code LevelManager.resetState()}.
     */
    public void resetState() {
        specialStageRequestedFromCheckpoint = false;
        specialStageEntryRoutineArmed = false;
        specialStageEntryAdvancesLevel = false;
        specialStageReturnLevelReloadRequested = false;
        bigRingReturn = null;
        bonusStageRequested = null;
        bonusStageReturnCheckpointIndex = -1;
        titleCardRequested = false;
        titleCardZone = -1;
        titleCardAct = -1;
        inLevelTitleCardRequested = false;
        inLevelTitleCardZone = -1;
        inLevelTitleCardAct = -1;
        inLevelTitleCardLevelGamestateResetRequested = false;
        inLevelTitleCardResetAdditionalDispatches = 0;
        inLevelTitleCardResetPhaseOneDispatchOverlap = 0;
        inLevelTitleCardPlayerControlLockRequested = false;
        inLevelTitleCardExitAdditionalDispatches = 0;
        inLevelTitleCardExitPhaseOneDispatchOverlap = 0;
        respawnRequested = false;
        nextActRequested = false;
        nextZoneRequested = false;
        specificZoneActRequested = false;
        seamlessTransitionRequested = false;
        creditsRequested = false;
        forceHudSuppressed = false;
        suppressNextMusicChange = false;
        levelInactiveForTransition = false;
        requestedZone = -1;
        requestedAct = -1;
        requestedMusicId = -1;
        pendingSeamlessTransitionRequest = null;
    }
}
