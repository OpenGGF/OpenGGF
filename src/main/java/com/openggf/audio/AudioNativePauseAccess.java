package com.openggf.audio;

import java.util.Objects;

/** Engine-internal native pause ingress; never suspends speaker or chip time. */
public final class AudioNativePauseAccess {
    private AudioNativePauseAccess() { }

    /** Bound to the caller's injected manager, with no global audio lookup. */
    public interface Port {
        void requestPause();
        void requestResume();
    }

    /** Optional game-owned mailbox semantics, outside the creator-facing API. */
    public interface Mailbox {
        void submitNativePause(boolean paused);
    }

    public static Port bind(AudioManager manager) {
        Objects.requireNonNull(manager, "manager");
        return new Port() {
            @Override public void requestPause() { manager.requestNativePause(true); }
            @Override public void requestResume() { manager.requestNativePause(false); }
        };
    }

    /** Replays only a committed internal event; does not append another history edge. */
    public static void replay(AudioManager manager, boolean paused,
            com.openggf.audio.presentation.AudioPresentationForwardService.Snapshot forward) {
        Objects.requireNonNull(manager, "manager").replayNativePause(paused, forward);
    }

    public static void advanceHistoryCursor(AudioManager manager, int cursor) {
        Objects.requireNonNull(manager, "manager").advanceNativeHistoryCursor(cursor);
    }
}
