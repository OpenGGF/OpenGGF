package com.openggf.game.rewind;

import com.openggf.LevelFrameStep;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;

import java.util.Objects;

/**
 * Replays one recorded live-input row through the normal level frame step.
 */
final class LiveRewindStepper implements RewindSeekAwareEngineStepper {

    private final LiveRewindInputSource inputs;
    private final SonicConfigurationService config;

    LiveRewindStepper(LiveRewindInputSource inputs, SonicConfigurationService config) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public void step(Bk2FrameInput input) {
        var sprites = GameServices.spritesOrNull();
        var level = GameServices.levelOrNull();
        var camera = GameServices.cameraOrNull();
        if (sprites == null || level == null || camera == null) {
            return;
        }
        Bk2FrameInput previous = inputs.read(Math.max(0, input.frameIndex() - 1));
        var replayInput = new RewindFrameInputHandler(config, input, previous);
        LevelFrameStep.execute(level, camera, () -> sprites.update(replayInput));
    }

    @Override
    public void restoreToFrame(int frame, Bk2FrameInput inputAtFrame) {
        // Live replay is fully driven by RewindFrameInputHandler rows, so no
        // persistent forced-input bridge needs priming after restore.
    }
}
