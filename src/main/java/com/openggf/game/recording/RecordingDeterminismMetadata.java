package com.openggf.game.recording;

@com.openggf.game.ModApi
public record RecordingDeterminismMetadata(
        Integer initialLevelFrameCounter,
        Long initialRngSeed
) {
}
