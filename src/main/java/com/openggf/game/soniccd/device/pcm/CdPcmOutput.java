package com.openggf.game.soniccd.device.pcm;

import java.util.List;
import java.util.Objects;

/**
 * Bounded immutable chip output, one step per requested native sample. Values are the clipped
 * signed 16-bit digital mix at unity gain, before host volume, resampling or an audio sink.
 * There are no timestamps: the caller owns sample scheduling and the block's placement.
 */
public record CdPcmOutput(List<Step> steps) {
    public CdPcmOutput {
        Objects.requireNonNull(steps, "steps");
        if (steps.size() > CdPcmDevice.MAX_SAMPLES_PER_ADVANCE) {
            throw new IllegalArgumentException("PCM output exceeds the per-request sample bound");
        }
        steps = List.copyOf(steps);
    }

    /**
     * Deltas are full-width differences from the previous output, including the preceding block.
     * {@code hasDelta} describes the pinned sample core's delta submission: every enabled step,
     * even an unchanged output; while disabled, only the first step that mutes a nonzero output.
     * This is returned data, not a callback to an authoritative sink.
     */
    public record Step(int left, int right, int leftDelta, int rightDelta, boolean hasDelta) {
        public Step {
            CdPcmState.requireOutput(left);
            CdPcmState.requireOutput(right);
            if (leftDelta < -65535 || leftDelta > 65535 || rightDelta < -65535 || rightDelta > 65535) {
                throw new IllegalArgumentException("PCM deltas must fit the difference of two signed 16-bit outputs");
            }
            if (!hasDelta && (leftDelta != 0 || rightDelta != 0)) {
                throw new IllegalArgumentException("An absent PCM delta must be zero");
            }
        }
    }
}
