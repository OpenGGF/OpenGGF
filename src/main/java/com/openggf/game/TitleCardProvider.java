package com.openggf.game;

/**
 * Interface for title card display management.
 * Title cards appear when a level first loads, after player respawns,
 * and when returning from special stages.
 */
public interface TitleCardProvider {
    /**
     * Initializes the title card for a zone/act.
     *
     * @param zoneIndex Zone index (0-10)
     * @param actIndex  Act index (0-2)
     */
    void initialize(int zoneIndex, int actIndex);

    /**
     * Initializes the title card in in-level overlay mode.
     * Default implementation falls back to normal title card init.
     */
    default void initializeInLevel(int zoneIndex, int actIndex) {
        initialize(zoneIndex, actIndex);
    }

    /**
     * Defers a fresh level-gamestate install to the native in-level title-card
     * display boundary. Games without that handoff can ignore the request.
     */
    default void requestLevelGamestateResetAtInLevelDisplay() {
        // No-op for games without S3K's in-level act-title handoff.
    }

    default void requestLevelGamestateResetAtInLevelDisplay(int additionalDispatches) {
        requestLevelGamestateResetAtInLevelDisplay();
    }

    default void requestLevelGamestateResetAtInLevelDisplay(
            int additionalDispatches, int phaseOneDispatchOverlap) {
        requestLevelGamestateResetAtInLevelDisplay(additionalDispatches);
    }

    /**
     * Arms a fresh-level runtime-art producer for the title-card handoff.
     * Games whose level assembly owns a later hardware queue can retain the
     * level index here and publish that work when the title-card owner reaches
     * its native completion boundary.
     */
    default void requestFreshLevelRuntimeArtHandoff(int levelIndex) {
        // No-op for games without a post-title-card runtime-art handoff.
    }

    default void requestInLevelPlayerControlLock() {
        // No-op unless an in-level title card owns the native controller lock.
    }

    default boolean ownsInLevelPlayerControlLock() {
        return false;
    }

    default boolean shouldLockPlayerControlForInLevelOverlay() {
        return false;
    }

    /** Releases an in-level lock after its ROM object owner takes over. */
    default void releaseInLevelPlayerControlLockOwnership() {
    }

    default void requestInLevelExitAdditionalDispatches(int dispatches) {
        // No-op unless the title card models SST child retirement dispatches.
    }

    default void requestInLevelExitAdditionalDispatches(
            int dispatches, int phaseOneDispatchOverlap) {
        requestInLevelExitAdditionalDispatches(dispatches);
    }

    /**
     * Whether an active in-level title owner still dispatches on replay rows
     * whose level gameplay counter is held. Normal level-title overlays do not.
     */
    default boolean advancesOnHeldLevelCounter() {
        return false;
    }

    /** Whether an in-level title owner still owns the native held-counter phase. */
    default boolean ownsHeldLevelCounter() {
        return false;
    }

    /** Whether that held-counter phase came from a retained results owner mutating into a title card. */
    default boolean ownsRetainedResultsHeldLevelCounter() {
        return false;
    }

    /** Whether retained fixed-object cadence follows the playable history ring. */
    default boolean projectsRetainedResultsSpriteCadence() {
        return false;
    }

    /**
     * Hands the title card off to its gameplay-phase object lifetime when the
     * locked presentation itself was omitted.
     *
     * <p>The native locked title-card loop exits as soon as the zone-name piece
     * has reached its target and the pattern load cue is empty, but the title
     * card <em>objects</em> survive that exit and keep running on ordinary
     * gameplay frames: they idle for {@code anim_frame_duration} frames, then
     * slide back out and load the standard-water plus per-zone animal art on
     * the frame they leave. Omitting the presentation must not skip that tail,
     * because it is where the native game reclaims the VRAM the card occupied.
     *
     * <p>docs/s2disasm/s2.asm:4914-4925 (Level_TtlCard exit condition),
     * docs/s2disasm/s2.asm:5066-5080 (routine $16 + $2D handed to the pieces
     * immediately before the main level loop),
     * docs/s2disasm/s2.asm:27605-27637 (Obj34_WaitAndGoAway →
     * Obj34_LoadStandardWaterAndAnimalArt).
     *
     * <p>Games whose omitted-presentation art boundary is already reached
     * eagerly leave this a no-op.
     *
     * @param zoneIndex Zone index the card would have shown (the art the tail
     *                  loads is per-zone)
     * @param actIndex  Act index the card would have shown
     */
    default void beginOmittedPresentationExitTail(int zoneIndex, int actIndex) {
        // No-op unless the game models a gameplay-phase title-card exit tail.
    }

    /**
     * Initializes the title card for a bonus stage entry.
     * S3K shows "BONUS STAGE" text; S1/S2 have no bonus stages so this is a no-op.
     */
    default void initializeBonus() {
        // No-op for games without bonus stages
    }

    /**
     * Updates the title card animation.
     * Call this once per frame while in TITLE_CARD mode.
     */
    void update();

    /**
     * Returns true if player control should be released.
     * control is released at the start of TEXT_WAIT phase,
     * allowing the player to move while text is still visible.
     *
     * @return true if control should be released
     */
    boolean shouldReleaseControl();

    /**
     * Returns true if the title card overlay should still be drawn.
     * The overlay remains visible during TEXT_WAIT and TEXT_EXIT phases,
     * even though player control has been released.
     *
     * @return true if overlay is active
     */
    boolean isOverlayActive();

    /**
     * Returns true if the title card animation is fully complete.
     *
     * @return true if complete
     */
    boolean isComplete();

    /**
     * Renders the title card.
     * Call this from Engine.draw() when in TITLE_CARD mode.
     */
    void draw();

    /**
     * Returns true if player movement physics should run during the
     * title card's locked phase.
     *
     * <p>S1 ROM: title card is a blocking routine; player physics does NOT
     * run, so Sonic stays at his spawn position until the title card ends.
     * This is important for SBZ3 where Sonic spawns at Y=0 and must fall
     * after the title card.
     *
     * <p>S2 ROM: player physics runs during the title card so Sonic can
     * settle onto the Tornado in SCZ, and onto ground in other zones.
     *
     * @return true to run player physics during lock, false to skip
     */
    default boolean shouldRunPlayerPhysics() {
        return true;
    }

    /**
     * Returns whether the engine's already-loaded level objects should execute
     * during the locked title-card phase.
     *
     * <p>This is distinct from whether the native game calls its generic object
     * dispatcher while the card is visible. Sonic 1's
     * {@code Level_TtlCardLoop} does call {@code ExecuteObjects}, but object RAM
     * still contains the title-card objects at that point. {@code ObjPosLoad}
     * populates the level objects only after the wait loop, immediately before
     * the one pre-gameplay {@code ExecuteObjects} pass. The engine renders title
     * cards through this provider instead of putting them in level object RAM,
     * so advancing {@code ObjectManager} during that wait would incorrectly age
     * the level objects.
     */
    default boolean shouldRunLevelObjectsDuringLockedPhase() {
        return true;
    }

    /**
     * Returns whether a locked title-card frame advances the production VBlank
     * clock without dispatching the engine's already-loaded level objects.
     */
    default boolean shouldAdvanceVblankClockDuringLockedPhase() {
        return false;
    }

    /**
     * Number of object-only passes to run immediately before the title card
     * releases into the first normal level frame.
     *
     * <p>Sonic 1 performs {@code ObjPosLoad} and one {@code ExecuteObjects}
     * pass after the title-card wait and before {@code Level_MainLoop}. The
     * engine's title-card renderer does not occupy native object RAM, so this
     * explicit handoff pass reproduces that otherwise-missing lifecycle step.
     */
    default int levelObjectPreludePassesAtRelease() {
        return 0;
    }

    /**
     * Whether each release prelude pass also dispatches the playable slots.
     * Sonic 1's native {@code ExecuteObjects} includes Sonic in slot 0 before
     * the first {@code Level_MainLoop}; engines that split players from level
     * objects must opt that half of the dispatch back in explicitly.
     */
    default boolean shouldRunPlayerPreludeAtRelease() {
        return false;
    }

    /**
     * Resets the manager state.
     */
    void reset();

    /**
     * Gets the current zone index.
     * @return zone index
     */
    int getCurrentZone();

    /**
     * Gets the current act index.
     * @return act index
     */
    int getCurrentAct();
}
