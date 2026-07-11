package com.openggf.mods;

/** Logical fade cadence for a streamed foreground track. */
public record StreamedFadeSnapshot(float gain, int remainingSteps, int stepDelay,
                                   int delayCounter, float stepAmount) {
    public StreamedFadeSnapshot {
        if (!Float.isFinite(gain) || gain < 0 || gain > 1
                || remainingSteps < 0 || stepDelay < 0
                || delayCounter < 0 || delayCounter > stepDelay
                || !Float.isFinite(stepAmount)) {
            throw new IllegalArgumentException("Invalid streamed fade snapshot");
        }
        if (remainingSteps == 0) {
            if (gain != 1 || stepDelay != 0 || delayCounter != 0 || stepAmount != 0) {
                throw new IllegalArgumentException("Idle streamed fade snapshot must be canonical");
            }
        } else {
            if (stepAmount == 0) throw new IllegalArgumentException("Active fade requires a signed step");
            float endpoint = gain + stepAmount * remainingSteps;
            float expected = stepAmount < 0 ? 0 : 1;
            if (Math.abs(endpoint - expected) > 0.00001f) {
                throw new IllegalArgumentException("Streamed fade snapshot cannot reach its endpoint");
            }
        }
    }
}
