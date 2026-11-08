package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.OscillationManager;
import com.openggf.game.titlecard.TitleCardLoopTail;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Engine-internal native locked-title iteration shared by ordinary and controlled hosts.
 * The host retains entry/release lifetime and input policy; this operation owns no state. */
public final class NativeTitleCardIteration {
    private NativeTitleCardIteration() { }

    public static boolean run(TitleCardProvider titleCard, PlcLifecycleFrame frame,
            GameplayModeContext gameplayMode, LevelManager levelManager,
            SpriteManager spriteManager, Camera camera,
            Runnable beginAudioFrame, Runnable advanceAudioFrame,
            Consumer<PlcLifecyclePhase> preparePhase, LevelFrameStep.StepWrapper wrapper,
            Runnable publishHeldInput, Runnable playerUpdate, Runnable playerPrelude,
            IntConsumer release) {
        var loadScan = GameLoopPlcLifecycle.scanTitleLoad(titleCard, frame, gameplayMode, levelManager);
        if (loadScan.parked()) return false;
        boolean preparedByFrameStep = loadScan.preparedByFrameStep();

        // Publish while the provider still owns its fresh mode; completing
        // the title handoff below clears that mode and its terrain-ready gate.
        if (levelManager.hasPendingFreshLevelTransitionBoundary() && titleCard != null
                && (titleCard instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication boundary
                        ? boundary.shouldPublishFreshLevelTransitionInitialBoundary()
                        : titleCard.shouldCompleteFreshLevelTransitionBoundary())) {
            levelManager.publishFreshLevelTransitionInitialBoundary();
        }

        if (titleCard == null || titleCard.shouldCompleteFreshLevelTransitionBoundary()) {
            int preludePasses = titleCard != null
                    ? runReleasePrelude(titleCard, loadScan, gameplayMode, levelManager, playerPrelude) : 0;
            if (!preparedByFrameStep) {
                preparePhase.accept(PlcLifecyclePhase.LEVEL_TITLE_CARD);
            }
            // GM_Level clears Level_frame_counter on every non-demo level entry
            // (docs/s2disasm/s2.asm:4771-4773), and none of the pre-Level_MainLoop
            // object passes advance it -- the only increment is Level_MainLoop's
            // own addq at s2.asm:5092. The counter lives in CrossResetRAM, so
            // Level_ClrRam does not clear it; this store is the sole reset and it
            // runs for a special-stage return exactly as for a fresh entry.
            TitleCardLevelClock.resetAtRelease(titleCard, spriteManager, levelManager);
            release.accept(preludePasses);
            return true;
        }

        beginAudioFrame.run();
        // ROM: the title-card wait loops (docs/s2disasm/s2.asm:4914-4924 and
        // 5060-5066; docs/s1disasm/sonic.asm Level_TtlCardLoop 2811-2839) run
        // RunObjects but NOT OscillateNumDo -- the global oscillator only
        // advances inside Level_MainLoop (s2.asm:5108). Suppress the oscillator
        // advance for this locked title-card object pass so it holds at its
        // OscillateNumInit baseline until gameplay unlocks.
        if (titleCard.shouldRunPlayerPhysics()) {
            OscillationManager.suppressNextFrames(1);
            publishHeldInput.run();
            LevelFrameStep.execute(LevelFrameContext.from(gameplayMode), frame,
                    PlcLifecyclePhase.LEVEL_TITLE_CARD, levelManager, camera,
                    playerUpdate, wrapper);
            preparedByFrameStep = true;
        } else {
            if (titleCard.shouldRunLevelObjectsDuringLockedPhase()) {
                OscillationManager.suppressNextFrames(1);
                levelManager.updateObjectPositions();
                camera.updatePosition(true);
            } else if (titleCard.shouldAdvanceVblankClockDuringLockedPhase()) {
                if (!loadScan.clockOwned()) levelManager.advanceTitleCardVblankOnly();
            } else {
                camera.updatePosition(true);
            }
        }
        advanceAudioFrame.run();
        if (!preparedByFrameStep) {
            preparePhase.accept(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        }
        if (titleCard instanceof TitleCardLoopTail loopTail) {
            loopTail.completeLockedIteration();
            if (titleCard.shouldReleaseControl()) {
                TitleCardLevelClock.resetAtRelease(titleCard, spriteManager, levelManager);
                release.accept(1);
                return true;
            }
        }
        return false;
    }

    /**
     * Runs the title owner's release prelude and returns its object-pass count.
     * Level/loc_64DC hands the surviving title owner to LevelLoop with a
     * rewritten wait timer after the title and terrain queues drain.
     */
    private static int runReleasePrelude(TitleCardProvider titleCard, GameLoopPlcLifecycle.TitleLoadScan scan,
            GameplayModeContext gameplayMode, LevelManager levelManager, Runnable playerPrelude) {
        if (scan.preparedByFrameStep() && !scan.clockOwned()
                && titleCard instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication loadLoop
                && loadLoop.releaseScanServicesLoadLoopVBlank()) {
            // loc_7870's final Wait_VSync is already counted by a native load
            // scan before its queue tail; legacy scans count that release here.
            levelManager.advanceTitleCardVblankOnly();
        }
        titleCard.completeFreshLevelRuntimeArtHandoff();
        int preludePasses = titleCard.levelObjectPreludePassesAtRelease();
        boolean ranPlayerPrelude = false;
        for (int i = 0; i < preludePasses; i++) {
            OscillationManager.suppressNextFrames(1);
            if (titleCard.shouldRunPlayerPreludeAtRelease()) {
                playerPrelude.run();
                ranPlayerPrelude = true;
            }
            levelManager.updateObjectPositionsWithoutTouches();
        }
        if (ranPlayerPrelude) {
            LevelInitProfile initProfile = scan.frameContext().gameModule().getLevelInitProfile();
            DynamicArtLifecycleService artLifecycle = gameplayMode.dynamicArtLifecycle();
            int tailRows = initProfile != null
                    ? initProfile.preLevelMainLoopDelayFrames() : 0;
            if (tailRows > 0 && artLifecycle != null && artLifecycle.isRunActive()) {
                // S1 Level_LoadObj (docs/s1disasm/sonic.asm:2895-2897) stages
                // Sonic's DPLC after the title wait drains. Its next V-int
                // belongs to Level_Delay (sonic.asm:2957-2966) / the palette
                // fade, before Level_MainLoop (sonic.asm:2998-3003).
                // Hold the real release preparation for that counted tail;
                // ordinary zero-tail owners keep their next VBlank service.
                artLifecycle.holdPendingPlayerPreparationForPreMainLoopTail(tailRows);
            }
        }
        return preludePasses;
    }

}
