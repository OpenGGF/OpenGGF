package com.openggf.game.rewind;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.control.InputHandler;
import com.openggf.control.RecordedInputSnapshots;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Replays one recorded live-input row through the normal level frame step.
 */
final class LiveRewindStepper implements RewindSeekAwareEngineStepper {

    private final LiveRewindInputSource inputs;
    private final Supplier<InputHandler> inputHandlerSupplier;
    private final Supplier<LevelFrameContext> frameContextSupplier;

    LiveRewindStepper(LiveRewindInputSource inputs,
                      Supplier<InputHandler> inputHandlerSupplier,
                      Supplier<LevelFrameContext> frameContextSupplier) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.inputHandlerSupplier = Objects.requireNonNull(inputHandlerSupplier, "inputHandlerSupplier");
        this.frameContextSupplier = Objects.requireNonNull(frameContextSupplier, "frameContextSupplier");
    }

    @Override
    public void step(Bk2FrameInput input) {
        var sprites = GameServices.spritesOrNull();
        var level = GameServices.levelOrNull();
        var camera = GameServices.cameraOrNull();
        if (sprites == null || level == null || camera == null) {
            return;
        }
        InputHandler liveInput = inputHandlerSupplier.get();
        if (liveInput == null) {
            return;
        }
        Bk2FrameInput previous = inputs.read(Math.max(inputs.earliestFrame(), input.frameIndex() - 1));
        liveInput.setLogicalOverride(RecordedInputSnapshots.fromBk2(input, previous));
        try {
            sprites.publishHeldInputForLevelEvents(liveInput);
            LevelFrameStep.execute(frameContextSupplier.get(), level, camera, () -> sprites.update(liveInput));
        } finally {
            liveInput.clearLogicalOverride();
        }
    }

    @Override
    public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
        // Live replay is fully driven by logical override snapshots, so no
        // persistent forced-input bridge needs priming after restore.
    }
}
