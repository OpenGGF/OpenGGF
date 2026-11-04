package com.openggf.level;

import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.ZoneFeatureProvider;

/** Immutable title selection retained across a production load's display-clear hold. */
record LevelTitleRequestPolicy(boolean bypass, boolean show, boolean suppressed,
                             boolean freshArt, boolean keepPresentation,
                             boolean omittedFreshArt, int zone, int apparentAct) {
    static LevelTitleRequestPolicy select(LevelManager level, LevelLoadContext ctx, ZoneFeatureProvider features) {
        var transitions = level.getTransitions();
        int currentZone = level.getCurrentZone(), currentAct = level.getCurrentAct(), apparentAct = level.getApparentAct();
        var graphicsManager = level.graphicsManager;
        if (LevelContinuationCarry.bypassInitialPresentation(transitions))
            return new LevelTitleRequestPolicy(true, false, false, false, false, false, currentZone, apparentAct);
        if (!ctx.isShowTitleCard())
            return new LevelTitleRequestPolicy(false, false, false, false, false,
                    ctx.isQueueFreshLevelRuntimeArt(), currentZone, apparentAct);
        boolean callerOwned = transitions.isBonusStageReturn() || transitions.isResultsReturnCardOwnedByCaller();
        boolean suppressed = !callerOwned && features != null
                && features.shouldSuppressInitialTitleCard(currentZone, currentAct);
        var suppressedOwner = level.activeGameModule().getTitleCardProvider();
        boolean deletedTitleWait = suppressed && ctx.isQueueFreshLevelRuntimeArt()
                && suppressedOwner instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication boundary
                && boundary.freshLevelTitleOwnerDeletesAtInit(currentZone, currentAct);
        if (suppressed && !deletedTitleWait)
            return new LevelTitleRequestPolicy(false, true, true, false, false,
                    ctx.isQueueFreshLevelRuntimeArt(), currentZone, apparentAct);
        var bonus = GameServices.bonusStageOrNull();
        boolean bonusActive = bonus != null && bonus.getActiveType() != null
                && bonus.getActiveType() != BonusStageType.NONE;
        boolean headlessWholeRun = graphicsManager.isHeadlessMode()
                && GameServices.playbackDebug().hasScheduledLevelLoadSession();
        return new LevelTitleRequestPolicy(false, true, false,
                ctx.isQueueFreshLevelRuntimeArt() || transitions.isBonusStageReturn() || bonusActive,
                deletedTitleWait || !graphicsManager.isHeadlessMode() || headlessWholeRun
                        || ctx.isTitleCardRequiredInHeadlessMode() || callerOwned || transitions.isLevelRoutineReentry(),
                ctx.isQueueFreshLevelRuntimeArt(), currentZone, apparentAct);
    }
}
