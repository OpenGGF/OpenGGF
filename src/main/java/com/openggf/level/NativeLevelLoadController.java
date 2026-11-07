package com.openggf.level;

import com.openggf.game.InitialProcessSpritesLifecycle;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission;
import com.openggf.game.session.SessionManager;
import com.openggf.level.LevelManager.NativePlcLoadAdmission;
import java.io.IOException;

/** Owns one production Level continuation, its prepared phase and terminal cleanup. */
final class NativeLevelLoadController {
    NativeLevelLoadPhaseAdmission admission = NativeLevelLoadPhaseAdmission.IMMEDIATE;
    PendingLoad pending;
    PendingInitialization initialization;
    private record PendingInitialization(LevelLoadAttempt attempt, Runnable completion, Runnable cleanup) { }
    boolean prefix;
    boolean titlePrefixReady;
    long ordinal;

    static final class PendingLoad {
        final LevelLoadAttempt attempt;
        final LevelLoadContext context;
        final int index;
        final LevelLoadMode mode;
        final NativePlcLoadAdmission plc;
        Runnable completion = () -> { };
        Runnable cleanup = () -> { };
        Runnable playerAssemblyReady = () -> { };
        Runnable onTitlePrefix = () -> { };
        Runnable succeeded = () -> { };
        java.util.function.Consumer<Boolean> zoneLoadFinished;
        PendingLoad(LevelLoadAttempt attempt, LevelLoadContext context, int index,
                               LevelLoadMode mode, NativePlcLoadAdmission plc) {
            this.attempt = attempt; this.context = context; this.index = index; this.mode = mode; this.plc = plc;
        }
    }

    void execute(LevelManager manager, int levelIndex, LevelLoadMode loadMode, LevelLoadContext ctx) throws IOException {
        if (pending != null || initialization != null) throw new IllegalStateException("A native initialization is already pending");
        titlePrefixReady = false;
        manager.discardInitialProcessSpritesLifecycle();
        var attempt = new LevelLoadAttempt(manager.getWorldSession(), manager.spriteManager, loadMode, ctx,
                cause -> manager.checkpointCoordinator.admitLoadPolicies(manager.getWorldSession(), cause, ctx, manager.getTransitions()));
        // BEGIN HCZ1_DIRECT_FF_PROFILE_ADMISSION_BEGIN: all profile modes use their actual outcome.
        var previousPlcAdmission = manager.nativePlcLoadAdmission;
        var plcAdmission = manager.beginNativePlcLoadAdmission();
        manager.nativePlcLoadAdmission = plcAdmission;
        boolean plcLoadCompleted = false;
        // END HCZ1_DIRECT_FF_PROFILE_ADMISSION_BEGIN
        try {
            attempt.execute(manager::activeGameModule, levelIndex,
                    () -> ordinal = Math.incrementExact(ordinal), admission);
            if (!attempt.isComplete()) {
                pending = new PendingLoad(attempt, ctx, levelIndex, loadMode, plcAdmission);
                return;
            }
            // The LoadLevelData step stores the result in ctx
            if (ctx.getLevel() != null) {
                manager.writeCurrentLevel(ctx.getLevel());
            }
            manager.markRewindLevelLoadBoundary();
            InitialProcessSpritesLifecycle requestedSetup =
                    ctx.requestedInitialProcessSpritesLifecycle();
            if (requestedSetup != InitialProcessSpritesLifecycle.NONE) {
                manager.spriteManager.setFrameCounter(0);
            }
            manager.publishInitialProcessSpritesLifecycle(requestedSetup);
            if (loadMode == LevelLoadMode.FULL) {
                manager.completedProductionLoadGeneration = Math.incrementExact(
                        manager.completedProductionLoadGeneration);
            }
            // BEGIN HCZ1_DIRECT_FF_PROFILE_ADMISSION_SUCCESS: no predicted generation is authority.
            plcLoadCompleted = true;
            // END HCZ1_DIRECT_FF_PROFILE_ADMISSION_SUCCESS
        } catch (Exception e) {
            // BEGIN HCZ1_DIRECT_FF_PROFILE_ADMISSION_FAILURE: before failure policies can throw.
            manager.finishNativePlcLoadAdmission(plcAdmission, false);
            // END HCZ1_DIRECT_FF_PROFILE_ADMISSION_FAILURE
            throw attempt.failure(e, manager::activeGameModule, manager::discardInitialProcessSpritesLifecycle, levelIndex);
        } finally {
            // BEGIN HCZ1_DIRECT_FF_PROFILE_ADMISSION_FINISH: include the original finally outcome.
            boolean plcCleanupCompleted = false;
            try {
                // The suppress flag belongs to this load only. A load that fails before
                // ScheduleLevelMusic — or a preview capture, which has no music step — must not
                // leave it latched for the next level, which would start that level silent.
                if (pending == null) {
                    prefix = false;
                    manager.getTransitions().setSuppressNextMusicChange(false);
                    manager.levelEntryBegun = false;
                }
                plcCleanupCompleted = true;
            } finally {
                if (pending == null)
                    manager.finishNativePlcLoadAdmission(plcAdmission, plcLoadCompleted && plcCleanupCompleted);
                manager.nativePlcLoadAdmission = previousPlcAdmission;
            }
            // END HCZ1_DIRECT_FF_PROFILE_ADMISSION_FINISH
        }
    }

    void beginInitialization(LevelManager manager, com.openggf.game.internal.NativeLevelLoadPlan plan,
            Runnable zeroRoutineVInt, Runnable completion, Runnable cleanup) {
        if (pending != null || initialization != null) throw new IllegalStateException("A native initialization is already pending");
        var context = new LevelLoadContext();
        var attempt = new LevelLoadAttempt(manager.getWorldSession(), manager.spriteManager, LevelLoadMode.FULL, context);
        initialization = new PendingInitialization(attempt, completion, cleanup);
        try {
            attempt.executeInitializationPlan(plan, zeroRoutineVInt, ordinal = Math.incrementExact(ordinal), admission);
            finishInitialization();
        } catch (RuntimeException | Error failure) {
            closeInitialization(() -> { throw failure; }); throw failure;
        }
    }

    @FunctionalInterface
    private interface InitializationCleanup extends AutoCloseable {
        @Override void close();
    }

    /** Detach once; structured cleanup preserves a terminal failure and suppresses cleanup failure. */
    private void closeInitialization(Runnable terminalAction) {
        var current = initialization;
        initialization = null;
        try (InitializationCleanup cleanup = () -> { if (current != null) current.cleanup.run(); }) {
            terminalAction.run();
        }
    }

    private void finishInitialization() {
        var current = initialization;
        if (current == null || !current.attempt.isComplete()) return;
        closeInitialization(current.completion);
    }

    com.openggf.game.internal.NativeLevelLoadPhaseAdmission.PreparedPhase preparedPhase() {
        return initialization != null ? initialization.attempt.preparedPhase()
                : pending == null ? null : pending.attempt.preparedPhase();
    }

    void resume(LevelManager manager) {
        if (initialization != null) {
            try { initialization.attempt.resume(admission); finishInitialization(); }
            catch (RuntimeException | Error failure) { closeInitialization(() -> { throw failure; }); throw failure; }
            return;
        }
        PendingLoad current = pending;
        if (current == null) return;
        var previous = manager.nativePlcLoadAdmission;
        manager.nativePlcLoadAdmission = current.plc;
        boolean success = false;
        boolean cleanupStarted = false;
        Throwable primaryFailure = null;
        try {
            current.attempt.resume(admission);
            if (!current.attempt.isComplete()) return;
            manager.writeCurrentLevel(current.context.getLevel());
            manager.publishInitialProcessSpritesLifecycle(current.context.requestedInitialProcessSpritesLifecycle());
            current.completion.run();
            cleanupStarted = true;
            current.cleanup.run();
            manager.freshLevelTransitionBoundary.completeNativeContinuation();
            pending = null;
            manager.markRewindLevelLoadBoundary();
            if (current.mode == LevelLoadMode.FULL)
                manager.completedProductionLoadGeneration = Math.incrementExact(manager.completedProductionLoadGeneration);
            success = true;
        } catch (Exception failure) {
            primaryFailure = failure;
            manager.finishNativePlcLoadAdmission(current.plc, false);
            pending = null;
            manager.freshLevelTransitionBoundary.abortNativeContinuation();
            try { if (!cleanupStarted) current.cleanup.run(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw new java.io.UncheckedIOException(current.attempt.failure(failure,
                    manager::activeGameModule, manager::discardInitialProcessSpritesLifecycle, current.index));
        } catch (Error failure) {
            primaryFailure = failure;
            pending = null;
            manager.freshLevelTransitionBoundary.abortNativeContinuation();
            manager.discardInitialProcessSpritesLifecycle();
            try { manager.activeGameModule().getLevelInitProfile().cancelPendingLevelLoadWork(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            try { if (!cleanupStarted) current.cleanup.run(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        } finally {
            try {
                if (success || pending == null) finishAttempt(manager, current, success, primaryFailure);
            } finally {
                manager.nativePlcLoadAdmission = previous;
            }
        }
        if (success) current.succeeded.run();
    }

    private void finishAttempt(LevelManager manager, PendingLoad current, boolean success, Throwable primaryFailure) {
        boolean finalized = false;
        Throwable failure = primaryFailure;
        try {
            prefix = false;
            pending = null;
            manager.getTransitions().setSuppressNextMusicChange(false);
            manager.levelEntryBegun = false;
            manager.finishNativePlcLoadAdmission(current.plc, success);
            finalized = success;
        } catch (RuntimeException | Error cleanup) {
            if (primaryFailure == null) { failure = cleanup; throw cleanup; }
            primaryFailure.addSuppressed(cleanup);
        } finally {
            if (current.zoneLoadFinished != null) {
                try { current.zoneLoadFinished.accept(finalized); }
                catch (RuntimeException | Error cleanup) {
                    if (failure == null) throw cleanup;
                    failure.addSuppressed(cleanup);
                }
            }
        }
    }

    boolean deferRuntimeCompletion(LevelManager manager, LevelLoadContext context, LevelLoadMode mode,
                                   com.openggf.game.LevelLoadCause cause) {
        if (pending == null) return false;
        pending.completion = () -> {
            if (mode != LevelLoadMode.PREVIEW_CAPTURE) manager.applyPersistedEditorEdits();
            manager.restoreCheckpointRuntimeState(context);
            // The prefix has serviced real V-ints; never restore the pre-load clock.
            manager.frameCounter = 0;
            manager.resetNativeRuntimeFrameBridge();
            if (cause == com.openggf.game.LevelLoadCause.FULL_DEATH_RELOAD) manager.markNativeRespawnBoundary();
        };
        pending.cleanup = manager::clearNativeRuntimeAssembly;
        // Activation observes the completed generation and terminal cleanup,
        // just as the synchronous outer load does before publishing its receipt.
        pending.succeeded = manager::activateScheduledPlaybackForLoadedLevel;
        return true;
    }

    void installPrefix(LevelManager manager, Level loaded) {
        prefix = true;
        manager.discardPreparedLevelLoad();
        manager.writeCurrentLevel(loaded);
        manager.zoneFeatureProvider = manager.activeGameModule().getZoneFeatureProvider();
        manager.ringManager = null;
        manager.resetNativePrefixSpriteTable();
        // Object_RAM is cleared at loc_60DE before Obj_TitleCard's first wait
        // (sonic3k.asm:7649-7651). Source comparisons can still observe this
        // prefix; destination placement has not happened yet.
        FreshLevelTransitionBoundaryController.clearPlayableSlots(manager);
        // Camera/deformation needs native dimensions before terrain decoding;
        // these metadata reads allocate no geometry renderer or GPU resources.
        manager.blockPixelSize = loaded.getBlockPixelSize();
        manager.chunksPerBlockSide = loaded.getChunksPerBlockSide();
        manager.cacheLevelDimensions();
        manager.installHudProfile();
    }

    void prepareInitialObjects(LevelManager manager) {
        prefix = false;
        var respawn = manager.checkpointCoordinator.consumePersistentRespawn();
        if (respawn != null) {
            if (manager.ringManager != null) manager.ringManager.restoreRingStatusTable(respawn.ringStatusBits());
        }
        com.openggf.level.objects.ObjectManagerInternalAccess.prepareInitialPlacementWindow(
                manager.objectManager, manager.level.getObjects(), manager.camera.getX(), respawn);
        var runtime = SessionManager.getCurrentGameplayMode();
        if (runtime != null) runtime.registerLevelAdapters(manager);
    }

}
