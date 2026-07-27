package com.openggf.game.rewind;

import com.openggf.LevelFrameResult;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.SpecialStageInputMapper;
import com.openggf.game.SpecialStageProvider;

import java.util.Objects;
import java.util.function.Supplier;

final class SpecialStageStepper implements RewindSeekAwareEngineStepper {

    private final LiveRewindInputSource inputs;
    private final Supplier<InputHandler> inputHandlerSupplier;
    private final Supplier<SpecialStageProvider> providerSupplier;

    SpecialStageStepper(LiveRewindInputSource inputs,
                        Supplier<InputHandler> inputHandlerSupplier,
                        Supplier<SpecialStageProvider> providerSupplier) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.inputHandlerSupplier = Objects.requireNonNull(inputHandlerSupplier, "inputHandlerSupplier");
        this.providerSupplier = Objects.requireNonNull(providerSupplier, "providerSupplier");
    }

    @Override
    public LevelFrameResult step(Bk2FrameInput input) {
        InputHandler liveInput = inputHandlerSupplier.get();
        SpecialStageProvider provider = providerSupplier.get();
        if (liveInput == null || provider == null) {
            return LevelFrameResult.PAUSED;
        }
        Bk2FrameInput previous = inputs.read(Math.max(inputs.earliestFrame(), input.frameIndex() - 1));
        liveInput.setLogicalOverride(RecordedInputSnapshots.fromBk2(input, previous));
        try {
            SpecialStageInputMapper.MappedInput mapped =
                    SpecialStageInputMapper.map(liveInput.logical());
            provider.handleInput(mapped.p1Held(), mapped.p1Pressed());
            provider.handlePlayer2Input(mapped.p2Held(), mapped.p2Logical());
            provider.update();
            return LevelFrameResult.GAMEPLAY_FRAME;
        } finally {
            liveInput.clearLogicalOverride();
        }
    }

    @Override
    public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
    }
}
