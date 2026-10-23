package com.openggf.audio.session;

/** Native pause latch and a request awaiting the next runnable driver service. */
public record SmpsNativePauseState(boolean paused, Boolean request) {
    public static final SmpsNativePauseState RUNNING = new SmpsNativePauseState(false, null);
}
