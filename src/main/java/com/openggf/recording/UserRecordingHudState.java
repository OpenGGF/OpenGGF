package com.openggf.recording;

public record UserRecordingHudState(
        boolean visible,
        String primaryText,
        String secondaryText,
        int frame,
        boolean amberWarning,
        boolean redWarning
) {
}
