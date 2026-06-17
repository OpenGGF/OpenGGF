package com.openggf.level.rewind;

import com.openggf.game.GameServices;
import com.openggf.game.LevelState;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;

/**
 * Rewind adapter for mutable level geometry plus level-scoped HUD and checkpoint state.
 */
public final class LevelRewindSnapshotAdapter implements RewindSnapshottable<LevelSnapshot> {
    private final LevelManager manager;

    private LevelRewindSnapshotAdapter(LevelManager manager) {
        this.manager = manager;
    }

    public static RewindSnapshottable<LevelSnapshot> create(LevelManager manager) {
        return new LevelRewindSnapshotAdapter(manager);
    }

    @Override
    public String key() {
        return "level";
    }

    @Override
    public LevelSnapshot capture() {
        AbstractLevel level = currentAbstractLevel();
        LevelState levelState = manager.getLevelGamestate();

        // Capture establishes a new COW boundary: future production map writes
        // must clone away from the byte array referenced by this keyframe.
        level.bumpEpoch();

        return new LevelSnapshot(
                level.currentEpoch(),
                level.blocksReference(),
                level.chunksReference(),
                level.patternsReference(),
                level.getMap().getData(),
                manager.getFrameCounter(),
                levelState != null,
                levelState != null ? levelState.getRings() : 0,
                levelState != null ? levelState.getTimerFrames() : 0,
                levelState != null && levelState.isTimerPaused(),
                manager.isRespawnRequestedForRewind(),
                manager.captureCheckpointStateForRewind()
        );
    }

    @Override
    public void restore(LevelSnapshot snapshot) {
        AbstractLevel level = currentAbstractLevel();
        boolean geometryReferencesChanged =
                level.blocksReference() != snapshot.blocks()
                        || level.chunksReference() != snapshot.chunks()
                        || level.getMap().getData() != snapshot.mapData();

        restorePatterns(level, snapshot.patterns());

        level.replaceBlocks(snapshot.blocks());
        level.replaceChunks(snapshot.chunks());
        level.getMap().restoreData(snapshot.mapData());
        level.bumpEpoch();
        if (geometryReferencesChanged) {
            // Rewind can restore prior tile arrays by reference; rebuild manager-owned caches only on swaps.
            manager.invalidateAllTilemaps();
        }
        manager.setFrameCounter(snapshot.frameCounter());
        restoreLevelHudState(snapshot);
        manager.restoreRespawnRequestedForRewind(snapshot.respawnRequested());
        manager.restoreCheckpointStateForRewind(snapshot.checkpointState());
    }

    /**
     * Restores the captured patterns array and re-uploads only the atlas slots
     * whose Pattern instance differs from the snapshot. applyPatternOverlay
     * writes copy-on-write (fresh Pattern instances for touched slots and a
     * cloned array), so identity inequality precisely marks the overlay-edited
     * tiles that the GL atlas currently holds with post-overlay pixels.
     */
    private void restorePatterns(AbstractLevel level, Pattern[] snapshotPatterns) {
        if (snapshotPatterns == null) {
            return;
        }
        Pattern[] live = level.patternsReference();
        if (live == snapshotPatterns) {
            return;
        }

        GraphicsManager graphics = GameServices.graphics();
        boolean reupload = graphics != null && graphics.isGlInitialized();
        if (reupload) {
            graphics.beginPatternAtlasBatch();
        }
        try {
            level.replacePatterns(snapshotPatterns);
            if (!reupload) {
                return;
            }
            // Re-cache only the slots the atlas currently disagrees with: any
            // index present in both arrays but holding a different Pattern
            // instance is an overlay-edited tile whose atlas pixels are stale.
            int overlap = Math.min(live.length, snapshotPatterns.length);
            for (int i = 0; i < overlap; i++) {
                if (live[i] != snapshotPatterns[i] && snapshotPatterns[i] != null) {
                    graphics.cachePatternTexture(snapshotPatterns[i], i);
                }
            }
        } finally {
            if (reupload) {
                graphics.endPatternAtlasBatch();
            }
        }
    }

    private void restoreLevelHudState(LevelSnapshot snapshot) {
        LevelState levelState = manager.getLevelGamestate();
        if (!snapshot.hasLevelHudState() || levelState == null) {
            return;
        }

        levelState.setRings(snapshot.levelRings());
        levelState.setTimerFrames(snapshot.levelTimerFrames());
        if (snapshot.levelTimerPaused()) {
            levelState.pauseTimer();
        } else {
            levelState.resumeTimer();
        }
    }

    private AbstractLevel currentAbstractLevel() {
        Level currentLevel = manager.getCurrentLevel();
        if (!(currentLevel instanceof AbstractLevel level)) {
            throw new IllegalStateException(
                    "Current level is not an AbstractLevel: " + currentLevel.getClass().getName());
        }
        return level;
    }
}
