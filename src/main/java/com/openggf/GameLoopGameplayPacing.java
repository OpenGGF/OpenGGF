package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.game.GameModule;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.level.Level;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Interactive presentation pump; the canonical simulation entry remains one whole native tick. */
final class GameLoopGameplayPacing {
    private GameLoopGameplayPacing() { }

    static boolean step(GameLoop loop, GameplayModeContext context, Supplier<Level> level,
                        GameModule module, InputHandler input, AudioManager audio,
                        BooleanSupplier eligible, Consumer<GameplayMutatorPacing> zeroStep,
                        boolean audioOwned) {
        Level levelAtStart = level.get();
        var runtime = context == null ? null : WorldSessionPolicyAccess.getService(
                context.getWorldSession(), GameplayMutatorPacing.class);
        boolean paced = module != null && eligible.getAsBoolean();
        boolean modified = paced && runtime != null && runtime.policy().gameplaySpeedPercent() != 100
                && module.gameplayFrameController() == null && !loop.resolveFadeManager().isActive();
        BooleanSupplier pumpAllowed = modified
                ? () -> eligible.getAsBoolean() && !loop.resolveFadeManager().isActive() : eligible;
        double rate = !paced ? 1.0 : modified
                ? (runtime.policy().audioFollowsSpeed() ? runtime.policy().gameplaySpeedPercent() / 100.0 : 1.0)
                : module.gameplayAudioPlaybackRate();
        rate = Double.isFinite(rate) ? Math.clamp(rate, modified ? 0.25 : 1.0, 32.0) : 1.0;
        if (rate != 1.0 || audioOwned) audio.setForwardPlaybackRate(rate);
        audioOwned = rate != 1.0;
        int steps = modified ? runtime.stepsForPresentation()
                : paced ? Math.clamp(module.gameplayStepsPerFrame(), 1, 32) : 1;
        boolean interrupted = !paced || runtime != null && !modified
                && (module.gameplayFrameController() != null || loop.resolveFadeManager().isActive());
        if (interrupted && runtime != null) runtime.clearPendingInput();
        if (interrupted) InputHandlerInternalAccess.discardRetainedGameplayInput(input);
        if (steps == 0) {
            zeroStep.accept(runtime);
        } else {
            if (runtime != null && runtime.hasPendingInput()) {
                InputHandlerInternalAccess.retainGameplayInput(input, runtime.pendingPlayer1(), runtime.pendingPlayer2());
                runtime.clearPendingInput();
            }
            loop.step();
            for (int i = 1; i < steps && sameBoundary(loop, context, level, levelAtStart, pumpAllowed); i++) loop.step();
        }
        if (!sameBoundary(loop, context, level, levelAtStart, pumpAllowed)) {
            if (runtime != null) runtime.clearPendingInput();
            InputHandlerInternalAccess.discardRetainedGameplayInput(input);
            if (audioOwned) audio.setForwardPlaybackRate(1.0);
            audioOwned = false;
        }
        return audioOwned;
    }

    private static boolean sameBoundary(GameLoop loop, GameplayModeContext context, Supplier<Level> level,
                                        Level started, BooleanSupplier eligible) {
        return loop.resolveGameplayModeContext() == context && level.get() == started && eligible.getAsBoolean();
    }
}
