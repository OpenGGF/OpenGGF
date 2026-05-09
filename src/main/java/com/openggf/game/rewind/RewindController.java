package com.openggf.game.rewind;

import com.openggf.audio.AudioManager;
import com.openggf.audio.rewind.AudioKeyframeStore;
import com.openggf.audio.rewind.AudioPresentationPolicy;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.rewind.AudioReplayScope;
import com.openggf.debug.playback.Bk2FrameInput;

import java.util.Objects;

public final class RewindController {

    private final RewindRegistry registry;
    private final KeyframeStore keyframes;
    private final InputSource inputs;
    private final EngineStepper engineStepper;
    private final SegmentCache segmentCache;
    private final int keyframeInterval;
    private final AudioManager audioManager;
    private final AudioKeyframeStore audioKeyframes;

    private int currentFrame;

    public RewindController(
            RewindRegistry registry,
            KeyframeStore keyframes,
            InputSource inputs,
            EngineStepper engineStepper,
            int keyframeInterval) {
        this(registry, keyframes, inputs, engineStepper, keyframeInterval, null);
    }

    public RewindController(
            RewindRegistry registry,
            KeyframeStore keyframes,
            InputSource inputs,
            EngineStepper engineStepper,
            int keyframeInterval,
            AudioManager audioManager) {
        this.registry = Objects.requireNonNull(registry);
        this.keyframes = Objects.requireNonNull(keyframes);
        this.inputs = Objects.requireNonNull(inputs);
        this.engineStepper = Objects.requireNonNull(engineStepper);
        if (keyframeInterval <= 0) {
            throw new IllegalArgumentException(
                    "keyframeInterval must be > 0, got " + keyframeInterval);
        }
        this.keyframeInterval = keyframeInterval;
        this.audioManager = audioManager;
        this.audioKeyframes = audioManager != null ? new AudioKeyframeStore() : null;
        this.segmentCache = new SegmentCache(keyframeInterval);
        this.currentFrame = 0;
        // Capture frame 0 so seekTo(0) always has a base.
        keyframes.put(0, registry.capture());
        captureAudioKeyframe(0);
    }

    public int currentFrame() { return currentFrame; }

    public int earliestAvailableFrame() {
        // v1: trace mode — earliest accessible frame is whatever the
        // earliest stored keyframe is (typically 0).
        int e = keyframes.earliestFrame();
        return e < 0 ? 0 : e;
    }

    /**
     * Re-roots rewind history at the current frame after a committed level or
     * act boundary. This prevents seeks from replaying across incompatible
     * level-load state while keeping rewind available in the new segment.
     */
    public void resetBufferAtCurrentFrame() {
        segmentCache.invalidate();
        keyframes.clear();
        keyframes.put(currentFrame, registry.capture());
        if (audioKeyframes != null) {
            audioKeyframes.clear();
            captureAudioKeyframe(currentFrame);
        }
    }

    /** Steps forward one frame, capturing a keyframe at the boundary. */
    public void step() {
        if (currentFrame + 1 >= inputs.frameCount()) {
            return;   // end of trace
        }
        beginAudioFrame(currentFrame + 1);
        Bk2FrameInput in = inputs.read(currentFrame + 1);
        engineStepper.step(in);
        currentFrame++;
        if (currentFrame % keyframeInterval == 0) {
            keyframes.put(currentFrame, registry.capture());
            captureAudioKeyframe(currentFrame);
        }
    }

    /**
     * Records that the host visual loop has already advanced the engine by one
     * input frame. This keeps rewind history in sync without recursively
     * invoking {@link EngineStepper#step(com.openggf.debug.playback.Bk2FrameInput)}.
     *
     * @return true when the controller advanced its cursor, false at input end
     */
    public boolean recordExternalStep() {
        if (currentFrame + 1 >= inputs.frameCount()) {
            return false;
        }
        currentFrame++;
        beginAudioFrame(currentFrame);
        segmentCache.invalidate();
        if (currentFrame % keyframeInterval == 0) {
            keyframes.put(currentFrame, registry.capture());
            captureAudioKeyframe(currentFrame);
        }
        return true;
    }

    /**
     * Seeks to {@code targetFrame} by restoring the latest keyframe at or
     * before it, then stepping forward. Held-rewind callers should use
     * {@link #stepBackward()} for steady-state O(1) cost.
     */
    public void seekTo(int targetFrame) {
        if (targetFrame == currentFrame) return;
        if (targetFrame < earliestAvailableFrame()) {
            targetFrame = earliestAvailableFrame();
        }
        final int clampedTarget = targetFrame;
        var floor = keyframes.latestAtOrBefore(clampedTarget).orElseThrow(
                () -> new IllegalStateException(
                        "no keyframe at or before " + clampedTarget));
        int originalFrame = currentFrame;
        try (AudioReplayScope ignored = beginAudioReplay(
                originalFrame, clampedTarget, AudioReplayReason.SEEK)) {
            segmentCache.invalidate();
            registry.restore(floor.snapshot());
            currentFrame = floor.frame();
            primeStepperAtFrame(currentFrame);
            while (currentFrame < clampedTarget) {
                Bk2FrameInput in = inputs.read(currentFrame + 1);
                engineStepper.step(in);
                currentFrame++;
            }
            keyframes.discardAfter(currentFrame);
            discardAudioAfter(currentFrame);
            restoreAudioLogicalState(currentFrame);
            beginAudioFrame(currentFrame);
            primeStepperAtFrame(currentFrame);
            afterAudioRestore(AudioPresentationPolicy.SUPPRESSED_INTERNAL_RESTORE);
        }
    }

    /**
     * Rewinds one frame using the segment cache for amortised O(1) cost.
     * Returns false if already at {@code earliestAvailableFrame}.
     */
    public boolean canStepBackward() {
        return currentFrame > earliestAvailableFrame();
    }

    public boolean stepBackward() {
        if (!canStepBackward()) return false;
        int originalFrame = currentFrame;
        int target = currentFrame - 1;
        int keyframeFrame = (target / keyframeInterval) * keyframeInterval;
        final var floor = keyframes.latestAtOrBefore(keyframeFrame).orElseThrow();
        final int keyframeSnapshot = floor.frame();
        final var restoreSnapshot = floor.snapshot();
        // Use int[] wrapper to allow mutation within lambdas
        final int[] pos = { currentFrame };
        try (AudioReplayScope ignored = beginAudioReplay(
                originalFrame, target, AudioReplayReason.STEP_BACKWARD)) {
            CompositeSnapshot snap = segmentCache.snapshotAt(
                    target,
                    restoreSnapshot,
                    keyframeSnapshot,
                    () -> {
                        registry.restore(restoreSnapshot);
                        pos[0] = keyframeSnapshot;
                        primeStepperAtFrame(pos[0]);
                    },
                    () -> {
                        Bk2FrameInput in = inputs.read(pos[0] + 1);
                        engineStepper.step(in);
                        pos[0]++;
                        return registry.capture();
                    });
            registry.restore(snap);
            currentFrame = target;
            keyframes.discardAfter(currentFrame);
            discardAudioAfter(currentFrame);
            restoreAudioLogicalState(currentFrame);
            beginAudioFrame(currentFrame);
            primeStepperAtFrame(currentFrame);
            afterAudioRestore(AudioPresentationPolicy.SUPPRESSED_INTERNAL_RESTORE);
        }
        return true;
    }

    private AudioReplayScope beginAudioReplay(int fromFrame, int targetFrame, AudioReplayReason reason) {
        if (audioManager == null) {
            return () -> {};
        }
        return audioManager.beginRewindReplay(fromFrame, targetFrame, reason);
    }

    private void afterAudioRestore(AudioPresentationPolicy policy) {
        if (audioManager != null) {
            audioManager.afterRewindRestore(currentFrame, policy);
        }
    }

    private void beginAudioFrame(int frame) {
        if (audioManager != null) {
            audioManager.beginCommandTimelineFrame(frame);
        }
    }

    private void discardAudioAfter(int frame) {
        if (audioManager != null) {
            audioManager.discardAudioCommandsAfter(frame);
            audioKeyframes.discardAfter(frame);
        }
    }

    private void captureAudioKeyframe(int frame) {
        if (audioKeyframes != null) {
            audioKeyframes.capture(frame, audioManager);
        }
    }

    private void restoreAudioLogicalState(int frame) {
        if (audioKeyframes != null) {
            audioKeyframes.replayToLogicalState(audioManager, frame);
        }
    }

    private void primeStepperAtFrame(int frame) {
        if (engineStepper instanceof RewindSeekAwareEngineStepper seekAware) {
            seekAware.restoreToFrame(frame, inputs.read(frame));
        }
    }
}
