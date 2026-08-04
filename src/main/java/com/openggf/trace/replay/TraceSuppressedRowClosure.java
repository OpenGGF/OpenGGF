package com.openggf.trace.replay;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.level.LevelManager;
import com.openggf.trace.timing.TraceHardwareTimingBoundaryObserver;

import java.util.Objects;
import java.util.function.Consumer;

/** Production closure for one trace row whose ordinary gameplay body is held. */
public final class TraceSuppressedRowClosure {

    private TraceSuppressedRowClosure() {
    }

    /** Runs title-card work not owned by a represented suppressed closure. */
    public static void executeUnownedTitleCardWork(
            boolean rowSuppressed,
            TitleCardProvider provider,
            Runnable startPendingInLevelTitleCard,
            Consumer<Boolean> applyInLevelTitleCardControlLock) {
        boolean deferOverlay = rowSuppressed && provider != null
                && provider.advancesOnHeldLevelCounter();
        if (provider != null && provider.isOverlayActive() && !deferOverlay) {
            provider.update();
            if (provider.ownsInLevelPlayerControlLock()) {
                applyInLevelTitleCardControlLock.accept(
                        provider.shouldLockPlayerControlForInLevelOverlay());
            }
        }
        if (!rowSuppressed) {
            startPendingInLevelTitleCard.run();
        }
    }

    /** Executes the represented closure, rejecting an impossible multi-close row. */
    public static void executeRepresented(
            int closureCount,
            LevelFrameContext context,
            PlcLifecycleFrame lifecycleFrame,
            LevelManager levelManager,
            Runnable startPendingInLevelTitleCard,
            Consumer<Boolean> applyInLevelTitleCardControlLock) {
        if (closureCount < 0 || closureCount > 1) {
            throw new IllegalArgumentException(
                    "one stored trace row cannot own " + closureCount
                            + " VBlank closures");
        }
        if (closureCount == 1) {
            execute(context, lifecycleFrame, levelManager,
                    startPendingInLevelTitleCard,
                    applyInLevelTitleCardControlLock);
        }
    }

    /**
     * Executes the one hardware/VBlank closure represented by a suppressed
     * stored row. Expected trace state never crosses this boundary.
     */
    public static void execute(
            LevelFrameContext context,
            PlcLifecycleFrame lifecycleFrame,
            LevelManager levelManager,
            Runnable startPendingInLevelTitleCard,
            Consumer<Boolean> applyInLevelTitleCardControlLock) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(lifecycleFrame, "lifecycleFrame");
        Objects.requireNonNull(levelManager, "levelManager");
        Objects.requireNonNull(startPendingInLevelTitleCard,
                "startPendingInLevelTitleCard");
        Objects.requireNonNull(applyInLevelTitleCardControlLock,
                "applyInLevelTitleCardControlLock");

        TitleCardProvider titleCardProvider =
                context.gameModule().getTitleCardProvider();
        LevelEventProvider levelEvents = context.levelEventProvider();
        if (titleCardProvider != null
                && titleCardProvider.advancesOnHeldLevelCounter()) {
            LevelFrameStep.executeHardwareTimedObjectScan(
                    context,
                    lifecycleFrame,
                    PlcLifecyclePhase.LEVEL_TITLE_CARD,
                    () -> {
                        titleCardProvider.update();
                        if (titleCardProvider
                                .ownsRetainedResultsHeldLevelCounter()
                                && levelEvents != null) {
                            levelEvents.updateFixedInLevelObjects();
                        }
                        if (titleCardProvider
                                .ownsRetainedResultsHeldLevelCounter()
                                && context.gameModule().getObjectArtProvider() != null) {
                            // Retained Obj_TitleCardWait2 reaches LoadEnemyArt
                            // during this held object dispatch. Pump the
                            // production ROM-backed provider before the loop
                            // tail services the admitted queue work.
                            context.gameModule().getObjectArtProvider()
                                    .processRuntimeArtQueue();
                        }
                    });
            if (titleCardProvider.ownsInLevelPlayerControlLock()) {
                applyInLevelTitleCardControlLock.accept(
                        titleCardProvider
                                .shouldLockPlayerControlForInLevelOverlay());
            }
        } else {
            LevelFrameStep.serviceVBlankOnly(
                    context, lifecycleFrame, PlcLifecyclePhase.LAG);
            if (context.runtimeArtCoordinator().ownsHeldLevelCounterHardwareTail()) {
                LevelFrameStep.serviceHardwarePostObjectsOnly(context);
                LevelFrameStep.serviceHardwarePreMainLoopOnly(context);
            } else if (context.hardwareTimingBoundaryObserver()
                    instanceof TraceHardwareTimingBoundaryObserver replayObserver) {
                if (replayObserver.hasPendingCompletionAtCurrentRawFrame(
                        HardwareServiceBoundary.POST_OBJECTS)) {
                    LevelFrameStep.serviceHardwarePostObjectsOnly(context);
                } else if (replayObserver.applySuppressedRowCompletion()) {
                    context.runtimeArtCoordinator().afterTimingService(
                            HardwareServiceBoundary.PRE_MAIN_LOOP);
                }
            }
        }

        if (levelManager.hasPendingInLevelTitleCardHeldCounterDispatch()) {
            startPendingInLevelTitleCard.run();
        }
        if (levelEvents != null) {
            levelEvents.advanceVblankOnlyState();
        }
        levelManager.getObjectManager().advanceVblaCounter();
    }
}
