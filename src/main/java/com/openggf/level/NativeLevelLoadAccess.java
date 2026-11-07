package com.openggf.level;

import com.openggf.game.LevelLoadContext;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission;

/** Internal continuation access; deliberately outside the creator LevelManager API. */
public final class NativeLevelLoadAccess {
    private NativeLevelLoadAccess() { }

    /** Return from a blocking palette routine after its final counted interrupt. */
    public static void finishBlockingLevelReveal(LevelManager manager) {
        var lifecycle = com.openggf.game.GameServices.plcFrameLifecycleOrNull();
        if (lifecycle == null) manager.setLevelInactiveForTransition(false);
        else lifecycle.afterConsumedIteration(() -> manager.setLevelInactiveForTransition(false));
    }
    private static NativeLevelLoadController state(LevelManager level) {
        if (level.nativeLoads == null) level.nativeLoads = new NativeLevelLoadController();
        return level.nativeLoads;
    }
    /** Establishes the explicit run-local ordinal origin before any represented load starts. */
    public static void beginRepresentedTimingRun(LevelManager level, NativeLevelLoadPhaseAdmission admission) {
        requireComplete(level);
        java.util.Objects.requireNonNull(admission, "admission");
        state(level).ordinal = 0;
        state(level).admission = admission;
    }
    /** The default synchronous lifecycle keeps its existing rewindable title boundary. */
    public static boolean hasPhaseAdmission(LevelManager level) {
        return state(level).admission != NativeLevelLoadPhaseAdmission.IMMEDIATE;
    }
    public static void setAdmission(LevelManager level, NativeLevelLoadPhaseAdmission admission) {
        state(level).admission = java.util.Objects.requireNonNull(admission);
    }
    public static NativeLevelLoadPhaseAdmission.PreparedPhase pendingPhase(LevelManager level) {
        return state(level).preparedPhase();
    }
    public static boolean isPending(LevelManager level) { return state(level).pending != null || state(level).initialization != null; }
    public static void resume(LevelManager level) { state(level).resume(level); }
    /** Starts independently selected production work; the timing port cannot call this method. */
    public static void beginScreenInitialization(LevelManager level,
            com.openggf.game.internal.NativeLevelLoadPlan plan, Runnable zeroRoutineVInt,
            Runnable completion, Runnable cleanup) {
        state(level).beginInitialization(level, plan, zeroRoutineVInt, completion, cleanup);
    }
    public static boolean resumeScreenInitialization(LevelManager level) {
        if (state(level).initialization == null) return false;
        resume(level);
        return true;
    }
    /** Zero-routine interrupt semantics shared by actual S1 initialization owners. */
    public static void serviceZeroRoutineInitializationVInt(LevelManager level) {
        var objects = level.getObjectManager();
        if (objects != null) objects.advanceVblaCounter();
        else com.openggf.game.session.EngineTiming.vIntRunCounter().serviceRepresentedVBlank();
    }
    private static com.openggf.game.LevelInitProfile profile(LevelManager level) {
        var module = level.activeGameModule();
        return module == null ? null : module.getLevelInitProfile();
    }
    private static com.openggf.game.internal.NativeTitlelessLevelLoad titlelessOwner(LevelManager level) {
        var profile = profile(level);
        return profile instanceof com.openggf.game.internal.NativeTitlelessLevelLoad owner
                && owner.titlelessLoadSelected() ? owner : null;
    }
    /** Production loop tail, after its native PLC preparation; no timing input selects this work. */
    public static void dispatchLoopTailModeSwitch(LevelManager level, Runnable consumeRequest) {
        if (!level.getTransitions().hasZoneActRequest()) return;
        var profile = profile(level);
        if (!(profile instanceof com.openggf.game.internal.NativeTitlelessLevelLoad owner)
                || !owner.switchesAtLoopTail(level.getRequestedZone(), level.getRequestedAct())) return;
        var lifecycle = com.openggf.game.GameServices.plcFrameLifecycleOrNull();
        if (lifecycle == null) consumeRequest.run();
        else lifecycle.afterConsumedIteration(consumeRequest);
    }
    /** Bind only while the real load callback executes, never across outgoing fade rows. */
    public static com.openggf.game.internal.NativeLevelLoadCallerScope beginModeSwitchLoad(
            LevelManager level, int zone, int act) {
        var profile = profile(level);
        return profile instanceof com.openggf.game.internal.NativeTitlelessLevelLoad owner
                && owner.switchesAtLoopTail(zone, act) ? owner.beginModeSwitchLoad()
                : com.openggf.game.internal.NativeLevelLoadCallerScope.NONE;
    }
    public static void dispatchLoopTailCredits(LevelManager level, Runnable consumeRequest) {
        if (!level.getTransitions().hasCreditsRequest()) return;
        var profile = profile(level);
        if (!(profile instanceof com.openggf.game.internal.NativeTitlelessLevelLoad owner)
                || !owner.creditsSwitchesAtLoopTail()) return;
        var lifecycle = com.openggf.game.GameServices.plcFrameLifecycleOrNull();
        if (lifecycle == null) consumeRequest.run(); else lifecycle.afterConsumedIteration(consumeRequest);
    }

    /** GM_Credits clears the existing screen/object owners without running another object pass. */
    public static void clearScreenObjects(LevelManager level) {
        if (level.objectManager != null)
            com.openggf.level.objects.ObjectManagerInternalAccess.clearNativeScreen(level.objectManager);
        FreshLevelTransitionBoundaryController.clearPlayableSlots(level);
        var player = level.spriteManager.getMainPlayable();
        if (player != null) { player.setNativeSlotPresent(false); player.setHidden(true); }
        level.resetNativePrefixSpriteTable();
    }

    public static boolean isTitlelessPending(LevelManager level) {
        return isPending(level) && titlelessOwner(level) != null;
    }
    public static boolean titlelessOwnsEntryReveal(LevelManager level) {
        var owner = titlelessOwner(level);
        return owner != null && owner.ownsEntryReveal();
    }
    /** Actual no-title entry loop; timing input never services or invokes this owner. */
    public static boolean resumeTitleless(LevelManager level,
            com.openggf.game.session.GameplayModeContext context,
            com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame frame) {
        if (!isPending(level)) return false;
        var owner = titlelessOwner(level);
        if (owner == null) return false;
        boolean phaseAtEntry = pendingPhase(level) != null;
        boolean servicedWait = !phaseAtEntry && owner.readyForEntryVBlank() && frame.ownerPhase() == null;
        if (servicedWait) {
            com.openggf.LevelFrameStep.executeHardwareTimedObjectScan(
                    com.openggf.LevelFrameContext.from(context), frame,
                    com.openggf.game.resources.PlcLifecyclePhase.ENDING, () -> {
                        var objects = level.getObjectManager();
                        if (objects != null) objects.advanceVblaCounter();
                    });
            publishInitialCpuSprites(level);
            owner.entryVBlankServiced();
        }
        resume(level);
        // A counted entry/fade interrupt already spent this row. Returning
        // from PaletteFadeIn only reaches End_MainLoop's next WaitForVBlank;
        // it cannot execute Sonic again on the fade's final interrupt.
        return phaseAtEntry || servicedWait || isPending(level) || frame.ownerPhase() != null;
    }
    public record TitleScan(boolean parked, boolean completedBeforeScan, boolean clockOwned) { }
    /** Both live and recording loops share the phase fence around their real title scan. */
    public static TitleScan beginTitleScan(LevelManager level) {
        boolean wasPending = isPending(level);
        var phaseAtEntry = pendingPhase(level);
        resume(level);
        // Returning from the display-clear interrupt only arms Wait_VSync;
        // it cannot spend the next title-loop interrupt on this same row.
        if (pendingPhase(level) != null || (phaseAtEntry != null && isPending(level)))
            return new TitleScan(true, false, false);
        // loc_62B6 installs the title owner before loc_62CC, but its routine-0
        // initialization/submission runs on the first actual title-object scan.
        if (isPending(level) && !state(level).titlePrefixReady) titlePrefixPrepared(level);
        boolean completed = wasPending && !isPending(level);
        return new TitleScan(false, completed, !completed && isPending(level));
    }
    public static boolean finishTitleScan(LevelManager level) {
        resume(level);
        return pendingPhase(level) != null;
    }
    /** A caller-owned title binds only after this load prepared its art lease. */
    public static void afterTitlePrefix(LevelManager level, Runnable action) {
        java.util.Objects.requireNonNull(action);
        var load = state(level);
        if (load.pending == null || load.titlePrefixReady) { action.run(); return; }
        Runnable previous = load.pending.onTitlePrefix;
        load.pending.onTitlePrefix = () -> { previous.run(); action.run(); };
    }
    public static void titlePrefixPrepared(LevelManager level) {
        var load = state(level);
        load.titlePrefixReady = true;
        if (load.pending == null) return;
        Runnable action = load.pending.onTitlePrefix;
        load.pending.onTitlePrefix = () -> { };
        action.run();
    }
    public static void afterPlayerAssembly(LevelManager level, Runnable action) {
        java.util.Objects.requireNonNull(action);
        if (state(level).pending == null) { action.run(); return; }
        var prepared = state(level).pending.attempt.preparedPhase();
        if (prepared != null && (prepared.phase() == NativeLevelLoadPhaseAdmission.Phase.INITIAL_PLACEMENT
                || prepared.phase() == NativeLevelLoadPhaseAdmission.Phase.INITIAL_OBJECTS
                || prepared.phase() == NativeLevelLoadPhaseAdmission.Phase.INITIAL_PRESENTATION)) {
            // A bypassed title can already have assembled players before its
            // load caller regains control at a pending placement/object/presentation gate.
            action.run();
            return;
        }
        Runnable previous = state(level).pending.playerAssemblyReady;
        state(level).pending.playerAssemblyReady = () -> { previous.run(); action.run(); };
    }
    /** Runs caller publication only after successful completion and terminal cleanup. */
    public static void afterCompletion(LevelManager level, Runnable action) {
        java.util.Objects.requireNonNull(action);
        if (state(level).pending == null) { action.run(); return; }
        Runnable previous = state(level).pending.succeeded;
        state(level).pending.succeeded = () -> { previous.run(); action.run(); };
    }

    /** Retains the caller's finally cleanup until this exact attempt succeeds or aborts. */
    public static void afterAttempt(LevelManager level, Runnable action) {
        java.util.Objects.requireNonNull(action);
        if (state(level).pending == null) { action.run(); return; }
        Runnable previous = state(level).pending.cleanup;
        state(level).pending.cleanup = () -> {
            try { previous.run(); } finally { action.run(); }
        };
    }
    public static Runnable selectTitleRequest(LevelManager level, LevelLoadContext context) {
        return level.selectNativeTitleRequest(context);
    }
    public static boolean retainsTitlePresentation(LevelManager level, LevelLoadContext context) {
        return level.retainsNativeTitlePresentation(context);
    }
    public static void playerAssemblyReady(LevelManager level) {
        if (state(level).pending == null) return;
        Runnable action = state(level).pending.playerAssemblyReady;
        state(level).pending.playerAssemblyReady = () -> { };
        action.run();
    }
    public static void requireComplete(LevelManager level) {
        if (isPending(level)) throw new IllegalStateException("Active native Level initialization is not rewindable");
    }
    public static void installPrefix(LevelManager manager, Level level) {
        state(manager).installPrefix(manager, level);
    }
    public static void terrainMaterialized(LevelManager manager) { manager.rebuildLevelDerivedState(); }
    /** Clear inherited body geometry before writing native centre coordinates. */
    public static void preparePlayerStart(LevelManager manager) { manager.resetPlayerState(); }
    public static void initializeObjectArt(LevelManager manager) { manager.initObjectArt(); }
    public static void initializePlayableArt(LevelManager manager) { manager.initPlayableArt(); }
    /** Actual CPU sprite construction of an initialization operation, without VInt publication. */
    public static void prepareInitialCpuSprites(LevelManager manager) {
        manager.spritePresentationRenderer().prepareSpritePresentation(manager.spriteManager);
    }
    /** The explicit native first screen-transfer wait publishes the retained CPU table. */
    public static void publishInitialCpuSprites(LevelManager manager) {
        manager.spritePresentationRenderer().spriteTables.publish();
    }
    public static void prepareInitialObjects(LevelManager manager) {
        state(manager).prepareInitialObjects(manager);
    }
    public static InitialPresentation completeInitialObjects(LevelManager manager, LevelLoadContext context) {
        manager.publishInitialProcessSpritesLifecycle(context.requestedInitialProcessSpritesLifecycle());
        var tail = manager.consumeNativeInitialObjectPass();
        // The real pass consumed the only authority; successful outer publication must not rearm it.
        context.resetInitialProcessSpritesRequestForLoadAttempt();
        return new InitialPresentation(manager, tail);
    }

    /** Actual CPU sprite-table preparation followed by the retained native animation work. */
    public static final class InitialPresentation {
        private final LevelManager manager;
        private final InitialProcessSpritesExecutor.PresentationTail tail;
        private InitialPresentation(LevelManager manager, InitialProcessSpritesExecutor.PresentationTail tail) {
            this.manager = manager;
            this.tail = tail;
        }
        public void prepare() {
            // CPU Render_Sprites only. VInt_0 must not publish the SAT or run ordinary queue service.
            tail.prepare(() -> LevelSpritePresentation.prepare(manager, manager.spriteManager));
        }
        public void complete() { tail.finish(); }
    }
}
