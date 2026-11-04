package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.level.animation.SeamlessTransitionAnimationClock;
import com.openggf.sprites.managers.SpriteManager;

import java.io.IOException;

/** Owns top-level dispatch and frame-boundary handling for seamless transitions. */
final class LevelSeamlessTransitionExecutor {
    enum Host { FRAME_BOUNDARY, LEVEL_FRAME }

    private final LevelManager levelManager;
    private final LevelTransitionCoordinator transitions;
    private boolean frameCounterBridgeActive;
    private boolean frameCounterBridgePrimed;
    private boolean executedDuringFrame;
    private boolean rewindBoundaryDuringFrame;
    private boolean oscillationAdvancedDuringFrame;

    LevelSeamlessTransitionExecutor(
            LevelManager levelManager,
            LevelTransitionCoordinator transitions) {
        this.levelManager = levelManager;
        this.transitions = transitions;
    }

    void executeSynchronousScreenEvent(SeamlessLevelTransitionRequest request) {
        if (request == null) {
            return;
        }
        // The owning ScreenEvents routine marks the post boundary only after
        // its native post-Load_Level tail has completed. Marking here would
        // expose a half-finished FBZ1BGE_Normal frame to LiveRewindManager.
        transitions.beginSynchronousScreenEventTransition(levelManager.currentZone, levelManager.currentAct, request);
        try {
            execute(request, Host.LEVEL_FRAME);
        } finally {
            transitions.endSynchronousScreenEventTransition();
        }
    }

    void execute(SeamlessLevelTransitionRequest request) {
        execute(request, Host.FRAME_BOUNDARY);
    }

    void execute(SeamlessLevelTransitionRequest request,
                                         Host host) {
        if (request == null) {
            return;
        }

        // BEGIN HCZ1_DIRECT_FF_ENCLOSING_RELOAD: only the original selected RELOAD arm owns this fence.
        boolean plcReloadSelected = false;
        boolean plcReloadCompleted = false;
        // END HCZ1_DIRECT_FF_ENCLOSING_RELOAD
        try {
            transitions.setSpecialStageReturnLevelReloadRequested(false);
            switch (request.type()) {
                case MUTATE_ONLY -> levelManager.applySeamlessMutation(request.mutationKey());
                case RELOAD_SAME_LEVEL -> {
                    // BEGIN HCZ1_DIRECT_FF_RELOAD_SAME_SELECTED
                    plcReloadSelected = true;
                    // END HCZ1_DIRECT_FF_RELOAD_SAME_SELECTED
                    executeReload(request.retargetedForReload(levelManager.currentZone, levelManager.currentAct), host);
                    // BEGIN HCZ1_DIRECT_FF_RELOAD_SAME_COMPLETED
                    plcReloadCompleted = true;
                    // END HCZ1_DIRECT_FF_RELOAD_SAME_COMPLETED
                }
                case RELOAD_TARGET_LEVEL -> {
                    // BEGIN HCZ1_DIRECT_FF_RELOAD_TARGET_SELECTED
                    plcReloadSelected = true;
                    // END HCZ1_DIRECT_FF_RELOAD_TARGET_SELECTED
                    executeReload(request, host);
                    // BEGIN HCZ1_DIRECT_FF_RELOAD_TARGET_COMPLETED
                    plcReloadCompleted = true;
                    // END HCZ1_DIRECT_FF_RELOAD_TARGET_COMPLETED
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to apply seamless transition", e);
        } finally {
            // BEGIN HCZ1_DIRECT_FF_ENCLOSING_RELOAD_FINISH: include the original oscillator/frame and cleanup tail.
            boolean plcReloadCleanupCompleted = false;
            try {
                transitions.setLevelInactiveForTransition(false);
                plcReloadCleanupCompleted = true;
            } finally {
                if (plcReloadSelected && (!plcReloadCompleted || !plcReloadCleanupCompleted))
                    levelManager.revokeNativePlcTargetAdmission();
            }
            // END HCZ1_DIRECT_FF_ENCLOSING_RELOAD_FINISH
        }
    }


    /** Only the public in-frame entry publishes completion to level-loop consumers. */
    void publishLevelFrameCompletion(LevelActTransitionExecutor.Completed completed) {
        if (completed == null) return;
        executedDuringFrame = true;
        rewindBoundaryDuringFrame = true;
        oscillationAdvancedDuringFrame |= completed.loopTailOscillationAdvanced();
    }

    boolean consumeExecutedDuringFrame() {
        boolean executed = executedDuringFrame;
        executedDuringFrame = false;
        return executed;
    }

    boolean consumeRewindBoundaryDuringFrame() {
        boolean boundary = rewindBoundaryDuringFrame;
        rewindBoundaryDuringFrame = false;
        return boundary;
    }

    boolean consumeOscillationAdvancedDuringFrame() {
        boolean advanced = oscillationAdvancedDuringFrame;
        oscillationAdvancedDuringFrame = false;
        return advanced;
    }

    void resetFrameMarkers() {
        executedDuringFrame = false;
        rewindBoundaryDuringFrame = false;
        oscillationAdvancedDuringFrame = false;
    }

    private void executeReload(SeamlessLevelTransitionRequest request, Host host) throws IOException {
        switch (host) {
            case FRAME_BOUNDARY -> {
                levelManager.reloadActAtFrameBoundary(request);
                advanceFrameCounterAcrossReload();
            }
            case LEVEL_FRAME -> levelManager.executeActTransition(request);
        }
    }

    /**
     * ROM {@code Level_frame_counter} (incremented in VInt_0_Main every gameplay
     * frame) keeps ticking through the act-reload frame; the engine's
     * {@code GameLoop} and headless test runner both {@code return} after
     * applying a seamless reload transition, skipping
     * {@code SpriteManager.update()} (which is where
     * {@code SpriteManager.frameCounter} normally increments). Bump it
     * explicitly here so sidekick AI gates that read
     * {@code (Level_frame_counter & MASK)} — e.g. sonic3k.asm:26815 loc_13E9C
     * 64-frame jump-cadence check — fire on the same frames as the ROM after
     * AIZ act 1 → act 2 reload.
     *
     * <p>S3K sidekick CPU now resolves this gate through the stored
     * {@code LevelManager.frameCounter}, so keep that counter aligned with the
     * sprite-manager gameplay counter here.
     *
     * <p>Only applies to RELOAD transitions: MUTATE_ONLY runs in places
     * (e.g. AIZ1 fire-transition art overlay) that may execute mid-frame
     * without skipping the rest of the gameplay loop.
     */
    void advanceFrameCounterAcrossReload() {
        // The outer transition is consumed at the frame boundary, so the
        // driver returns before the ordinary level-loop tail can run. Preserve
        // the native OscillateNumDo dispatch for this transition-only row at
        // the same post-Level_frame_counter boundary as LevelLoop. Using the
        // old counter value here is deduplicated after the preceding tail and
        // drops one oscillator tick across the AIZ reload (sonic3k.asm:7921,
        // 7931).
        levelManager.advanceGlobalOscillationAtLevelLoopTail();
        // This boundary-owned reload returns before the ordinary level update,
        // so preserve the adjacent ChangeRingFrame dispatch here. In-frame
        // event reloads continue into LevelManager.update() and advance there;
        // advancing inside the shared act-transition executor would tick those
        // paths twice (notably AIZ1BGE_Finish).
        if (levelManager.animatedPatternManager
                instanceof SeamlessTransitionAnimationClock clock) {
            clock.advanceForSeamlessTransition();
        }

        // The pending seamless reload is consumed at frame top, so this row
        // returns before ObjectManager.update() can perform its normal V-int
        // clock increment. V_int_run_count is global work RAM and still ticks
        // in the ROM on that transition-only VBlank.
        if (levelManager.objectManager != null) {
            // V-blank-only row: see the exactly-one-tick-per-serviced-V-blank invariant on ObjectManager.vblaCounter.
            levelManager.objectManager.advanceVblaCounter();
        }

        // ROM keeps Level_frame_counter ticking through AIZ's reload frame
        // (docs/skdisasm/sonic3k.asm:7916-7926); S3K Tails CPU reads it for
        // loc_13E9C's 64-frame auto-jump gate (docs/skdisasm/sonic3k.asm:26815-26822).
        levelManager.frameCounter++;
        levelManager.markSidekickRomVisibleReloadFrameCounterBridge();
        SpriteManager spriteManager = GameServices.spritesOrNull();
        if (spriteManager != null) {
            spriteManager.setFrameCounter(spriteManager.getFrameCounter() + 1);
        }
    }

    boolean isFrameCounterBridgeActive() {
        return frameCounterBridgeActive;
    }

    void markFrameCounterBridge() {
        frameCounterBridgeActive = true;
        frameCounterBridgePrimed = true;
    }

    void clearFrameCounterBridge() {
        frameCounterBridgeActive = false;
    }

    void resetFrameCounterBridge() {
        frameCounterBridgeActive = false;
        frameCounterBridgePrimed = false;
    }

    boolean isPostReloadFrameCounterBridgeStillVisible() {
        if (!frameCounterBridgePrimed) return false;
        SpriteManager sprites = GameServices.spritesOrNull();
        return sprites != null && sprites.getFrameCounter() == levelManager.frameCounter + 1;
    }

}
