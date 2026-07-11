package com.openggf.game.recording;

@com.openggf.game.ModApi
public enum UserRecordingStopReason {
    UNKNOWN,
    USER_STOPPED,
    LEVEL_ENDED,
    MOVIE_ENDED,
    IO_ERROR,
    ABORTED_BEFORE_GAMEPLAY
}
