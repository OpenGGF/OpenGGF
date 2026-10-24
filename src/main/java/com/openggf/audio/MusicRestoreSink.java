package com.openggf.audio;

/**
 * Receives an SMPS request to restore the music beneath an override.
 * A backend retains its supplied destination for its usable lifetime; changing
 * the configured root does not transfer it. This port does not own other backend
 * mutations or change existing owner-thread and retirement requirements.
 */
@FunctionalInterface
public interface MusicRestoreSink {
    void restoreMusic();
}
