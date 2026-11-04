package com.openggf.level;

import com.openggf.game.GameModule;
import com.openggf.game.InitStep;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.level.objects.ObjectCallbackAbortException;
import com.openggf.sprites.managers.SpriteManager;
import java.io.IOException;
import java.util.function.Supplier;
import static java.util.logging.Level.SEVERE;

/** One load attempt's policy admission, ordered profile execution, and typed failure recovery. */
final class LevelLoadAttempt {
    private final WorldSession world;
    private final SpriteManager sprites;
    private final LevelLoadMode mode;
    private final LevelLoadContext context;
    private final LevelLoadCause admissionCause;
    private final java.util.function.Consumer<LevelLoadCause> afterAdmission;
    private ObjectCallbackAbortException deferredStepAbort;
    private com.openggf.game.internal.NativeLevelLoadPlan continuation;
    private com.openggf.game.internal.NativeFreshLevelLoadProfile nativeProfile;
    private com.openggf.game.internal.NativeLevelLoadPhaseAdmission.PreparedPhase preparedPhase;
    private int sectionIndex, phaseOrdinal;
    private boolean sectionPrepared, complete, failed;
    private long loadOrdinal;

    LevelLoadAttempt(WorldSession world, SpriteManager sprites, LevelLoadMode mode, LevelLoadContext context) {
        this(world, sprites, mode, context, ignored -> { });
    }

    LevelLoadAttempt(WorldSession world, SpriteManager sprites, LevelLoadMode mode, LevelLoadContext context,
                     java.util.function.Consumer<LevelLoadCause> afterAdmission) {
        this.afterAdmission = afterAdmission;
        this.world = world;
        this.sprites = sprites;
        this.mode = mode;
        this.context = context;
        // A cause cannot turn a decode or preview into configuration admission.
        admissionCause = mode == LevelLoadMode.PREVIEW_CAPTURE ? LevelLoadCause.PREVIEW
                : context.isIncludePostLoadAssembly() && context.getAssemblyKind() == LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY
                    ? context.getLoadCause() : LevelLoadCause.DECODE_ONLY;
    }

    void execute(Supplier<GameModule> moduleSource, int levelIndex) {
        execute(moduleSource, levelIndex, () -> 1, com.openggf.game.internal.NativeLevelLoadPhaseAdmission.IMMEDIATE);
    }

    void execute(Supplier<GameModule> moduleSource, int levelIndex, java.util.function.LongSupplier nextLoadOrdinal,
                 com.openggf.game.internal.NativeLevelLoadPhaseAdmission admission) {
        if (world != null) com.openggf.game.LevelLoadRuntimeAccess.bindParticipants(context, world, sprites);
        try (var physicalLoad = com.openggf.game.internal.NativePhysicalLevelLoad.begin(
                java.lang.invoke.MethodHandles.lookup(), world, context, mode)) {
            WorldSessionPolicyAccess.beforeAssembly(world, admissionCause);
            afterAdmission.accept(admissionCause);
            // Replay/bootstrap resets retire structural sprite bindings. Reinject the world
            // owner before any assembly recreates or dispatches players.
            WorldSessionPolicyAccess.bindRoster(world, sprites);
            context.resetInitialProcessSpritesRequestForLoadAttempt();
            GameModule module = moduleSource.get();
            LevelInitProfile profile = module.getLevelInitProfile();
            context.setLevelIndex(levelIndex);
            context.setLoadMode(mode);
            if (profile instanceof com.openggf.game.internal.NativeFreshLevelLoadProfile nativeLoad) {
                continuation = nativeLoad.freshLevelLoadPlan(context);
                if (continuation != null) nativeProfile = nativeLoad;
            }
            if (continuation != null) {
                loadOrdinal = nextLoadOrdinal.getAsLong();
                // The execution scope closes before returning to any host frame.
                advance(admission);
                return;
            }
            var steps = profile.levelLoadSteps(context);
            if (steps.isEmpty()) {
                throw new IllegalStateException("No level load steps defined for " + module.getClass().getSimpleName()
                        + ". All game modules must implement levelLoadSteps().");
            }
            for (InitStep step : steps) {
                executeStep(step);
            }
            complete = true;
        } catch (RuntimeException | Error failure) {
            failed = true;
            preparedPhase = null;
            throw failure;
        }
    }

    void resume(com.openggf.game.internal.NativeLevelLoadPhaseAdmission admission) {
        if (failed) throw new IllegalStateException("Failed native level load cannot resume");
        if (complete) return;
        try (var physicalLoad = com.openggf.game.internal.NativePhysicalLevelLoad.begin(
                java.lang.invoke.MethodHandles.lookup(), world, context, mode)) {
            advance(admission);
        } catch (RuntimeException | Error failure) {
            failed = true;
            preparedPhase = null;
            throw failure;
        }
    }

    private void advance(com.openggf.game.internal.NativeLevelLoadPhaseAdmission admission) {
        while (sectionIndex < continuation.sections().size()) {
            var section = continuation.sections().get(sectionIndex);
            if (!section.ready().getAsBoolean()) return;
            if (!sectionPrepared) {
                for (InitStep step : section.prepare()) executeStep(step);
                sectionPrepared = true;
                if (section.phase() != null) preparedPhase =
                        new com.openggf.game.internal.NativeLevelLoadPhaseAdmission.PreparedPhase(
                                loadOrdinal, Math.incrementExact(phaseOrdinal), section.phase(), section.inputs().get());
                if (preparedPhase != null) phaseOrdinal = preparedPhase.phaseOrdinal();
            }
            if (preparedPhase != null) {
                while (true) {
                    var decision = java.util.Objects.requireNonNull(admission.next(preparedPhase), "phase admission");
                    if (decision == com.openggf.game.internal.NativeLevelLoadPhaseAdmission.Decision.HOLD) return;
                    if (decision == com.openggf.game.internal.NativeLevelLoadPhaseAdmission.Decision.RESUME) break;
                    nativeProfile.serviceLoadZeroRoutineVInt();
                }
            }
            for (InitStep step : section.commit()) executeStep(step);
            preparedPhase = null;
            sectionPrepared = false;
            sectionIndex++;
        }
        complete = true;
    }

    private void executeStep(InitStep step) {
        long start = System.nanoTime();
        try (var physicalStep = com.openggf.game.internal.NativePhysicalLevelLoad.step(
                java.lang.invoke.MethodHandles.lookup(), step)) {
            step.execute();
        } catch (ObjectCallbackAbortException aborted) {
            deferredStepAbort = aborted;
            throw aborted;
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        LevelManager.LOGGER.fine(() -> String.format("  [%s] %dms — %s", step.name(), elapsed, step.romRoutine()));
    }

    boolean isComplete() { return complete; }
    com.openggf.game.internal.NativeLevelLoadPhaseAdmission.PreparedPhase preparedPhase() { return preparedPhase; }

    IOException failure(Exception failure, Supplier<GameModule> moduleSource, Runnable discardSetup, int levelIndex) {
        failed = true;
        preparedPhase = null;
        WorldSessionPolicyAccess.failedAssembly(world, admissionCause);
        discardSetup.run();
        try {
            moduleSource.get().getLevelInitProfile().cancelPendingLevelLoadWork();
        } catch (ObjectCallbackAbortException quarantined) {
            if (!(failure instanceof ObjectCallbackAbortException)) failure.addSuppressed(quarantined);
        }
        if (failure instanceof ObjectCallbackAbortException aborted) {
            // Supplier/profile/policy/roster aborts remain direct. Deferred step callbacks
            // keep loadLevel's checked contract, with the exact abort retained for recovery.
            if (aborted != deferredStepAbort) throw aborted;
            return new DeferredLevelLoadException(aborted);
        }
        Throwable cause = failure.getCause();
        if (cause instanceof IOException ioe) {
            LevelManager.LOGGER.log(SEVERE, "Failed to load level " + levelIndex, ioe);
            return ioe;
        }
        LevelManager.LOGGER.log(SEVERE, "Unexpected error while loading level " + levelIndex, failure);
        return new IOException("Failed to load level due to unexpected error.", failure);
    }
}
