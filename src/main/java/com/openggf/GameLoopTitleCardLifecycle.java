package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
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
                    levelManager.suppressGlobalOscillationForTitleCardPass();
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
        if (titleCard.shouldRunPlayerPhysics()) {
            levelManager.suppressGlobalOscillationForTitleCardPass();
            spriteManager.publishHeldInputForLevelEvents(input);
            LevelFrameStep.execute(LevelFrameContext.from(gameplayMode), frame,
                    PlcLifecyclePhase.LEVEL_TITLE_CARD, levelManager, camera,
                    () -> spriteManager.update(input), wrapper);
            preparedByFrameStep = true;
        } else {
            if (titleCard.shouldRunLevelObjectsDuringLockedPhase()) {
                levelManager.suppressGlobalOscillationForTitleCardPass();
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
