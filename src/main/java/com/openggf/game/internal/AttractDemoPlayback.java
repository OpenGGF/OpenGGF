package com.openggf.game.internal;

import com.openggf.LevelFrameResult;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.SpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PaletteFadePresentation;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Advances ROM controllers and timer on the destination's admitted native waits. */
public final class AttractDemoPlayback {
    public enum ReturnTarget { BOOT, TITLE }

    public record Transition(int fadeStepPeriod, ReturnTarget returnTarget,
                             PaletteFadePresentation.Mode fadeMode) {
        public static final Transition NONE = new Transition(0, null);

        public Transition(int fadeStepPeriod, ReturnTarget returnTarget) {
            this(fadeStepPeriod, returnTarget, PaletteFadePresentation.Mode.TO_BLACK);
        }
    }

    public interface Program {
        void begin();
        LogicalInputSnapshot controllers(LogicalInputSnapshot physical);
        void serviceWait();
        Transition finishIteration(boolean destinationFinished);
        void reload();

        /** A stage program may wait for its native setup body to release the demo loop. */
        default boolean specialStageMainLoopReady(SpecialStageProvider provider) { return true; }
    }

    private final Program program;
    private boolean started;
    private int fadeStepPeriod;
    private int fadeTicks;
    private PaletteFadePresentation.Mode fadeMode;
    private ReturnTarget returnTarget;

    public AttractDemoPlayback(Program program) {
        this.program = Objects.requireNonNull(program);
    }

    public boolean isFading() { return fadeStepPeriod > 0; }
    public ReturnTarget returnTarget() { return returnTarget; }

    public LevelFrameResult step(InputHandler input, GraphicsManager graphics, BooleanSupplier levelInactive,
            Supplier<LevelFrameResult> world) {
        LevelFrameResult result = LevelDemoPlayback.withControllers(input, controllers(input), world);
        if (result != LevelFrameResult.GAMEPLAY_FRAME) {
            throw new IllegalStateException("A demo script sample requires a gameplay iteration: " + result);
        }
        completeWait(graphics, levelInactive.getAsBoolean());
        return result;
    }

    /** Entry presentation leaves the stage's native input neutral without spending a demo wait. */
    public void stepSpecialStage(InputHandler input, GraphicsManager graphics, boolean entryRevealed,
                                 SpecialStageProvider provider, Runnable stage) {
        boolean admitted = started || (entryRevealed && program.specialStageMainLoopReady(provider));
        var sample = admitted ? controllers(input) : LogicalInputSnapshot.neutral();
        LevelDemoPlayback.withControllers(input, sample, () -> {
            stage.run();
            return null;
        });
        if (admitted) completeWait(graphics, provider.isFinished());
    }

    private LogicalInputSnapshot controllers(InputHandler input) {
        if (!started) {
            program.begin();
            started = true;
        }
        return program.controllers(input.logical());
    }

    private void completeWait(GraphicsManager graphics, boolean destinationFinished) {
        // Debit only the destination's admitted loop wait. Entry and held rows spend nothing.
        program.serviceWait();
        Transition transition = program.finishIteration(destinationFinished);
        if (isFading()) {
            graphics.setPaletteFadePresentation(fadeMode,
                    1 + fadeTicks++ / fadeStepPeriod, PaletteFadePresentation.ALL_LINES);
        }
        if (transition.fadeStepPeriod() > 0) {
            fadeStepPeriod = transition.fadeStepPeriod();
            fadeMode = transition.fadeMode();
        }
        returnTarget = transition.returnTarget();
    }

    public void reload() {
        program.reload();
    }
}
