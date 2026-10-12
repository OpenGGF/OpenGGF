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
        var steps = profile.levelLoadSteps(context);
        if (steps.isEmpty()) {
            throw new IllegalStateException("No level load steps defined for " + module.getClass().getSimpleName()
                    + ". All game modules must implement levelLoadSteps().");
        }
        for (InitStep step : steps) {
            long start = System.nanoTime();
            try {
                step.execute();
            } catch (ObjectCallbackAbortException aborted) {
                deferredStepAbort = aborted;
                throw aborted;
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            LevelManager.LOGGER.fine(() -> String.format("  [%s] %dms — %s", step.name(), elapsed, step.romRoutine()));
        }
    }

    IOException failure(Exception failure, Supplier<GameModule> moduleSource, Runnable discardSetup, int levelIndex) {
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
