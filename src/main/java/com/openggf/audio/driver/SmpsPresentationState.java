package com.openggf.audio.driver;

import com.openggf.audio.AudioStream;
import com.openggf.audio.smps.SmpsSequencer;

/**
 * Mutable container for the per-session SMPS presentation state that
 * {@link SmpsPresentationReplay} reads from and writes to.
 *
 * <p>Holds the SMPS-logical state that is currently scattered as fields on
 * {@code LWJGLAudioBackend}. The same state shape is used both by the live
 * backend path (one shared instance) and by the offline reverse-resynth
 * worker (one private instance per session). Centralising the fields here
 * lets the two paths share a single replay implementation without the worker
 * having to touch backend OpenAL/runtime state.
 *
 * <p>This class deliberately holds no OpenAL or runtime-stream-binding
 * concerns. Those remain backend responsibilities layered around calls to
 * {@link SmpsPresentationReplay}.
 */
public final class SmpsPresentationState {
    /** Active music driver, or {@code null} when no SMPS music is playing. */
    public SmpsDriver musicDriver;

    /** Stream currently routed to the music output; usually equal to
     *  {@link #musicDriver} when music is SMPS. Used to gate continuous-SFX
     *  extension on the music driver — only extend if the music driver is
     *  the actively-rendered stream. */
    public AudioStream activeMusicStream;

    /** Active music sequencer (first sequencer on the music driver); used to
     *  source fallback voice data for SFX sequencers. */
    public SmpsSequencer activeMusicSequencer;

    /** Stream for standalone SFX playback (when no music driver exists);
     *  typically a {@link SmpsDriver}. */
    public AudioStream sfxStream;

    /** True while a music override (e.g. 1-up jingle) is gating SFX. */
    public boolean sfxBlocked;
}
