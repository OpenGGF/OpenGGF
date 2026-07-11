package com.openggf.game.recording;

@com.openggf.game.ModApi
public enum RecordingVersionWarning {
    NONE,
    MISSING_METADATA,
    OFFICIAL_VERSION_MISMATCH,
    PRERELEASE_BUILD_MISMATCH,
    DIRTY_BUILD
}
