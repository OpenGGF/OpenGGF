package com.openggf.level;

import com.openggf.game.LevelLoadContext;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission;

/** Internal continuation access; deliberately outside the creator LevelManager API. */
public final class NativeLevelLoadAccess {
    private NativeLevelLoadAccess() { }
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
        return state(level).pending == null ? null : state(level).pending.attempt.preparedPhase();
    }
    public static boolean isPending(LevelManager level) { return state(level).pending != null; }
    public static void resume(LevelManager level) { state(level).resume(level); }
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
