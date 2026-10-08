package com.openggf.audio.presentation;

/** Scoped full-screen scene source, mixed before speaker, capture and history publication. */
@FunctionalInterface
public interface ScenePcmSource {
    void render(short[] target, int stereoFrames);

    default void onHostPause() { }

    /** Output failed after playback began; a timed scene must retain its last audible coordinate. */
    default void onSpeakerFailure() { }

    /** A manually paused scene may keep the speaker paused when window focus returns. */
    default boolean onHostResume() { return true; }
}
