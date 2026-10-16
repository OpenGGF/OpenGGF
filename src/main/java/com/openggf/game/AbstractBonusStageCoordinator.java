package com.openggf.game;

import java.util.logging.Logger;

/**
 * Shared base class for bonus stage lifecycle coordination.
 * Manages entry/exit state, ring persistence, and object communication.
 * Subclasses provide game-specific zone/music mapping.
 * Methods are non-final to allow overrides (e.g., Slots player sprite swap).
 */
public abstract class AbstractBonusStageCoordinator implements BonusStageProvider, com.openggf.game.internal.NativeBonusStagePacing,
        com.openggf.game.internal.NativeBonusRuntimeRewindOwner {

    private static final Logger LOGGER = Logger.getLogger(AbstractBonusStageCoordinator.class.getName());

    private long pacingEntryEpoch;
    @Override public long pacingEntryEpoch() { return pacingEntryEpoch; }
    private BonusStageState savedState;
    private BonusStageType activeType = BonusStageType.NONE;
    private boolean exitRequested;
    private int ringsCollected;
    private int livesAwarded;
    private ShieldType awardedShield; // null if none

    @Override
    public boolean hasBonusStages() { return true; }

    @Override
    public boolean supportsRewind() {
        return activeType == BonusStageType.GUMBALL
                || activeType == BonusStageType.GLOWING_SPHERE;
    }

    @Override
    public void onEnter(BonusStageType type, BonusStageState savedState) {
        pacingEntryEpoch++;
        this.savedState = savedState;
        this.activeType = type;
        this.exitRequested = false;
        this.ringsCollected = 0;
        this.livesAwarded = 0;
        this.awardedShield = null;
        LOGGER.info("Entering bonus stage: " + type + " (zone 0x"
                + Integer.toHexString(getZoneId(type)) + ")");
    }

    @Override
    public void onExit() {
        pacingEntryEpoch++;
        // An interior may delete a follower's object (Gumball loc_61050 clears
        // Player_2); the origin reload recreates Player_2, so configured
        // companions rejoin before the return load positions them.
        var sprites = GameServices.spritesOrNull();
        if (sprites != null) {
            com.openggf.sprites.managers.SpriteManagerInternalAccess.restoreRetiredSidekicksForReturn(sprites);
        }
        LOGGER.info("Exiting bonus stage: " + activeType
                + " (rings collected: " + ringsCollected + ")");
        activeType = BonusStageType.NONE;
        exitRequested = false;
    }

    @Override
    public void onFrameUpdate() {
        // Default: no per-frame work. Subclasses may override.
    }

    @Override
    public void onDeferredSetupComplete() {
        // Default: no deferred setup. Subclasses may override.
    }

    @Override
    public boolean isStageComplete() { return exitRequested; }

    @Override
    public void requestExit() { exitRequested = true; }

    @Override
    public BonusStageRewards getRewards() {
        boolean basic = false, fire = false, lightning = false, bubble = false;
        if (awardedShield != null) {
            switch (awardedShield) {
                case FIRE -> fire = true;
                case BUBBLE -> bubble = true;
                case LIGHTNING -> lightning = true;
                default -> basic = true;
            }
        }
        return new BonusStageRewards(ringsCollected, livesAwarded,
                basic, fire, lightning, bubble);
    }

    @Override
    public BonusStageState getSavedState() { return savedState; }

    @Override
    public BonusStageType getActiveType() { return activeType; }

    /** Accumulate rings during the bonus stage. Called by gumball item objects. */
    @Override
    public void addRings(int count) { ringsCollected += count; }

    /** Accumulate lives during the bonus stage. */
    @Override
    public void addLife() { livesAwarded++; }

    /**
     * Records the shield awarded by a bonus-stage gumball.
     * ROM awards overwrite any previous shield with the latest pickup,
     * so we track only the most recent. Surfaces as the matching flag in
     * {@link #getRewards()} so {@code doExitBonusStage} can restore it
     * after the level reload clears the player state.
     */
    @Override
    public void setAwardedShield(ShieldType type) {
        this.awardedShield = type;
    }

    /**
     * Immutable capture of the reward accumulators that objects mutate across
     * frames during a bonus stage. Held rewind restores these so a backward
     * seek that un-collects a gumball item rolls the pending reward totals back
     * in lockstep with the item objects' own restored state.
     */
    public record BonusStageAccumulatorSnapshot(int rings, int lives, ShieldType shield) {}

    private record RuntimeState(long entryEpoch, BonusStageType type, boolean exitRequested,
                                Object stageState) { }

    @Override
    public final com.openggf.game.rewind.RewindSnapshottable<?> bonusRuntimeRewindAdapter() {
        return new com.openggf.game.rewind.RewindSnapshottable<RuntimeState>() {
            @Override public String key() { return com.openggf.game.internal.BonusStageRuntimeRewindAccess.KEY; }
            @Override public RuntimeState capture() {
                return new RuntimeState(pacingEntryEpoch, activeType, exitRequested, captureBonusRuntimeState());
            }
            @Override public void restore(RuntimeState state) {
                // Full loads retire their history. A manually retained snapshot cannot
                // restore another entry's RAM or revive a completed bonus visit.
                if (state.entryEpoch() != pacingEntryEpoch || state.type() != activeType)
                    throw new IllegalStateException("Bonus runtime snapshot belongs to a different entry");
                restoreBonusRuntimeState(state.stageState());
                exitRequested = state.exitRequested();
            }
            @Override public void resetForMissingSnapshot() {
                resetBonusRuntimeStateForMissingSnapshot();
                exitRequested = false;
            }
        };
    }

    protected Object captureBonusRuntimeState() { return null; }
    protected void restoreBonusRuntimeState(Object state) {
        if (state != null) throw new IllegalArgumentException("Unexpected bonus runtime state");
    }
    protected void resetBonusRuntimeStateForMissingSnapshot() { }
    @Override public void afterBonusRuntimeRewindRestore() { }

    /** Snapshots the live reward accumulators for rewind capture. */
    public BonusStageAccumulatorSnapshot captureAccumulators() {
        return new BonusStageAccumulatorSnapshot(ringsCollected, livesAwarded, awardedShield);
    }

    /** Restores reward accumulators from a rewind snapshot. */
    public void restoreAccumulators(BonusStageAccumulatorSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        this.ringsCollected = snapshot.rings();
        this.livesAwarded = snapshot.lives();
        this.awardedShield = snapshot.shield();
    }
}
