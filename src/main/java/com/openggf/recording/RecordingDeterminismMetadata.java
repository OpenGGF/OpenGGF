package com.openggf.recording;

public record RecordingDeterminismMetadata(
        Integer initialLevelFrameCounter,
        Long initialRngSeed
) {
}
