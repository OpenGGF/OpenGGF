package com.openggf.game.rewind;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.SpecialStageInputMapper;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;

import java.util.Objects;
import java.util.function.Supplier;

final class SpecialStageStepper implements RewindSeekAwareEngineStepper {

    private final LiveRewindInputSource inputs;
    private final Supplier<InputHandler> inputHandlerSupplier;
    private final Supplier<SpecialStageProvider> providerSupplier;
    private final Supplier<GameplayModeContext> gameplayModeSupplier;

    SpecialStageStepper(LiveRewindInputSource inputs,
                        Supplier<InputHandler> inputHandlerSupplier,
                        Supplier<SpecialStageProvider> providerSupplier,
                        Supplier<GameplayModeContext> gameplayModeSupplier) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.inputHandlerSupplier = Objects.requireNonNull(inputHandlerSupplier, "inputHandlerSupplier");
        this.providerSupplier = Objects.requireNonNull(providerSupplier, "providerSupplier");
        this.gameplayModeSupplier =
                Objects.requireNonNull(gameplayModeSupplier, "gameplayModeSupplier");
    }

    @Override
    public LevelFrameResult step(Bk2FrameInput input) {
        InputHandler liveInput = inputHandlerSupplier.get();
        SpecialStageProvider provider = providerSupplier.get();
        GameplayModeContext gameplayMode = gameplayModeSupplier.get();
        if (liveInput == null || provider == null || gameplayMode == null) {
            return LevelFrameResult.PAUSED;
        }
        return gameplayMode.plcFrameLifecycle().runReplayedLogicalIteration(
                gameplayMode.getFadeManager()::update,
                frame -> step(input, liveInput, provider, gameplayMode, frame));
    }

    private LevelFrameResult step(
            Bk2FrameInput input,
            InputHandler liveInput,
            SpecialStageProvider provider,
            GameplayModeContext gameplayMode,
            com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame frame) {
        Bk2FrameInput previous = inputs.read(Math.max(inputs.earliestFrame(), input.frameIndex() - 1));
        var metadata = inputs.pacingFrame(input.frameIndex());
        var recorded = RecordedInputSnapshots.fromBk2(input, previous);
        liveInput.setLogicalOverride(metadata == null ? recorded : metadata.admitted(recorded, gameplayMode, provider));
        try {
            LevelFrameStep.executeHardwareTimedObjectScan(
                    LevelFrameContext.from(gameplayMode), frame,
                    PlcLifecyclePhase.SPECIAL_STAGE, () -> {
                        com.openggf.game.internal.NativeSpecialStageFrame.replay(
                                provider, gameplayMode.getWorldSession(), liveInput);
                    });
            if (metadata != null) metadata.restoreAfterTick(gameplayMode, provider);
            return LevelFrameResult.GAMEPLAY_FRAME;
        } finally {
            liveInput.clearLogicalOverride();
        }
    }

    @Override
    public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
    }
}
