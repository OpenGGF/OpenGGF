package com.openggf.audio.driver;

import com.openggf.audio.AudioStream;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.smps.SmpsSequencer;

import java.util.ArrayDeque;
import java.util.Deque;

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

    /** Whether the speed-shoes accelerated music mode is active. Read by
     *  music-start to seed the new sequencer; mutated by setSpeedShoes. */
    public boolean speedShoesEnabled;

    /** Active music speed multiplier. Seeds new music sequencers and is
     *  applied to the current sequencer when {@code setSpeedMultiplier}
     *  fires during play. Default 1 (no multiplier). */
    public int speedMultiplier = 1;

    /** Stack of music states pushed aside by music overrides; the most
     *  recently pushed entry is restored first. A caller (e.g. the live
     *  backend) may install its own {@link Deque} reference here so that
     *  mutations performed by the helper apply directly to the caller's
     *  stack. The reverse-resynth worker installs a fresh per-session
     *  stack and never shares mutable state with the live backend. */
    public Deque<MusicStackEntry> musicStack = new ArrayDeque<>();

    /** Music id of the currently-playing music, or {@code -1} when none.
     *  Used by override-push to remember which id is being saved. */
    public int currentMusicId = -1;

    /** Source descriptor for the currently-playing music, captured into
     *  {@link MusicStackEntry} on override push so {@code restoreMusic}
     *  can re-establish the same logical music source. */
    public AudioSourceDescriptor currentMusicDescriptor;
}

