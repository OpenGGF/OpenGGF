package com.openggf.recording;

public record UserRecordingVerificationResult(
        boolean clean,
        int comparedFrames,
        int firstMismatchFrame,
        String firstMismatchField,
        String expectedValue,
        String actualValue
) {
    public static UserRecordingVerificationResult clean(int comparedFrames) {
        return new UserRecordingVerificationResult(true, comparedFrames, -1, "", "", "");
    }
}
