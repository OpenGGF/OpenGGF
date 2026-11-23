package com.openggf.game.internal;

import com.openggf.LevelFrameResult;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PaletteFadePresentation;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Advances the ROM demo program on admitted level iterations, including its closing object pass. */
public final class AttractDemoPlayback {
    public enum ReturnTarget { BOOT, TITLE }

    public record Transition(int fadeStepPeriod, ReturnTarget returnTarget) {
        public static final Transition NONE = new Transition(0, null);
    }

    public interface Program {
        void begin();
        LogicalInputSnapshot controllers(LogicalInputSnapshot physical);
        void serviceLevelWait();
        Transition finishObjects(boolean levelInactive);
        void reload();
    }

    private final Program program;
    private boolean started;
    private int fadeStepPeriod;
    private int fadeTicks;
    private ReturnTarget returnTarget;

    public AttractDemoPlayback(Program program) {
        this.program = Objects.requireNonNull(program);
    }

    public boolean isFading() { return fadeStepPeriod > 0; }
    public ReturnTarget returnTarget() { return returnTarget; }

    public LevelFrameResult step(InputHandler input, GraphicsManager graphics, BooleanSupplier levelInactive,
            Supplier<LevelFrameResult> world) {
        if (!started) {
            program.begin();
            started = true;
        }
        var controllers = program.controllers(input.logical());
        LevelFrameResult result = LevelDemoPlayback.withControllers(input, controllers, world);
        if (result != LevelFrameResult.GAMEPLAY_FRAME) {
            throw new IllegalStateException("A demo script sample requires a gameplay iteration: " + result);
        }
        // This is the admitted level wait's timer debit. It has no gameplay readers;
        // consume it here so a VBlank-only hold or title-card iteration spends nothing.
        program.serviceLevelWait();
        Transition transition = program.finishObjects(levelInactive.getAsBoolean());
        if (isFading()) {
            graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.TO_BLACK,
                    1 + fadeTicks++ / fadeStepPeriod, PaletteFadePresentation.ALL_LINES);
        }
        if (transition.fadeStepPeriod() != 0) fadeStepPeriod = transition.fadeStepPeriod();
        returnTarget = transition.returnTarget();
        return result;
    }

    public void reload() {
        program.reload();
    }
}
