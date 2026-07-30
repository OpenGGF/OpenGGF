package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.game.OscillationManager;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;

import java.util.function.Consumer;

/** Owns the ROM-specific title-card wait loop and its release handoff. */
final class GameLoopTitleCardLifecycle {
    private GameLoopTitleCardLifecycle() {
    }

    static boolean update(
            boolean doFrameStep,
            TitleCardProvider titleCard,
            PlcLifecycleFrame frame,
            GameplayModeContext gameplayMode,
            LevelManager levelManager,
            SpriteManager spriteManager,
            Camera camera,
            InputHandler input,
            Runnable playerPrelude,
            PostTitleCardDestination destination,
            Runnable exitTitleCard,
            Consumer<LevelFrameResult> releaseResult,
            Runnable beginAudioFrame,
            Runnable advanceAudioFrame,
            Consumer<PlcLifecyclePhase> preparePhase,
            LevelFrameStep.StepWrapper wrapper) {
        frame.claim(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        boolean hardwareTimedProviderScan = titleCard != null
                && titleCard.shouldAdvanceVblankClockDuringLockedPhase();
        boolean preparedByFrameStep = hardwareTimedProviderScan;
        if (hardwareTimedProviderScan) {
            LevelFrameStep.executeHardwareTimedObjectScan(
                    LevelFrameContext.from(gameplayMode), frame,
                    PlcLifecyclePhase.LEVEL_TITLE_CARD, titleCard::update);
        } else if (titleCard != null) {
            titleCard.update();
        }

        if (titleCard == null || titleCard.shouldReleaseControl()) {
            if (titleCard != null) {
                int preludePasses = titleCard.levelObjectPreludePassesAtRelease();
                for (int i = 0; i < preludePasses; i++) {
                    OscillationManager.suppressNextFrames(1);
                    if (titleCard.shouldRunPlayerPreludeAtRelease()) {
                        playerPrelude.run();
                    }
                    levelManager.updateObjectPositionsWithoutTouches();
                }
            }
            if (!preparedByFrameStep) {
                preparePhase.accept(PlcLifecyclePhase.LEVEL_TITLE_CARD);
            }
            releaseResult.accept(destination.completeRelease(levelManager, exitTitleCard));
            return true;
        }

        beginAudioFrame.run();
        // ROM: the title-card wait loops (docs/s2disasm/s2.asm:4914-4924 and
        // 5060-5066; docs/s1disasm/sonic.asm Level_TtlCardLoop 2811-2839) run
        // RunObjects but NOT OscillateNumDo -- the global oscillator only
        // advances inside Level_MainLoop (s2.asm:5108). Suppress the oscillator
        // advance for this locked title-card object pass so it holds at its
        // OscillateNumInit baseline until gameplay unlocks.
        OscillationManager.suppressNextFrames(1);
        if (titleCard.shouldRunPlayerPhysics()) {
            OscillationManager.suppressNextFrames(1);
            spriteManager.publishHeldInputForLevelEvents(input);
            LevelFrameStep.execute(LevelFrameContext.from(gameplayMode), frame,
                    PlcLifecyclePhase.LEVEL_TITLE_CARD, levelManager, camera,
                    () -> spriteManager.update(input), wrapper);
            preparedByFrameStep = true;
        } else {
            if (titleCard.shouldRunLevelObjectsDuringLockedPhase()) {
                OscillationManager.suppressNextFrames(1);
                levelManager.updateObjectPositions();
                camera.updatePosition(true);
            } else if (titleCard.shouldAdvanceVblankClockDuringLockedPhase()) {
                levelManager.advanceTitleCardVblankOnly();
            } else {
                camera.updatePosition(true);
            }
        }
        advanceAudioFrame.run();
        if (!preparedByFrameStep) {
            preparePhase.accept(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        }
        return false;
    }
}
