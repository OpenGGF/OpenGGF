package com.openggf.mods;

import java.util.Objects;
import java.util.Optional;

/**
 * Thread-confined logical streamed-music player. Construct and invoke it only from
 * one presentation thread; {@link #mixInto(short[], int)} allocates nothing and does no I/O.
 */
public final class StreamedMusicPlayer {
    public static final int PAUSE_JINGLE = 1;
    public static final int PAUSE_APP = 2;
    public static final int PAUSE_REWIND = 4;
    private static final int KNOWN_PAUSE_MASK = PAUSE_JINGLE | PAUSE_APP | PAUSE_REWIND;

    private final int outputRate;
    private final Thread ownerThread;
    private StreamedTrack current;
    private int logicalMusicId;
    private int pauseMask;
    private int desiredMultiplier = 1;
    private float fadeGain = 1.0f;
    private int fadeRemainingSteps;
    private int fadeStepDelay;
    private int fadeDelayCounter;
    private float fadeStepAmount;
    private boolean closed;

    public StreamedMusicPlayer(int outputRate) {
        if (outputRate < 8_000 || outputRate > 192_000) {
            throw new IllegalArgumentException("Output rate must be in 8000..192000");
        }
        this.outputRate = outputRate;
        ownerThread = Thread.currentThread();
    }

    public void play(int musicId, PreparedTrack prepared) {
        playInternal(musicId, prepared, false);
    }

    /** Plays a namespaced track without assigning it a numeric stock-music id. */
    public void play(PreparedTrack prepared) {
        playInternal(-1, prepared, true);
    }

    private void playInternal(int musicId, PreparedTrack prepared, boolean namespaced) {
        requireUsable();
        if (musicId < 0 && !namespaced) throw new IllegalArgumentException("Logical music id must be nonnegative");
        Objects.requireNonNull(prepared, "prepared");
        if (prepared.pcm().sampleRate() != outputRate) {
            throw new IllegalArgumentException("Prepared PCM rate does not match player output rate");
        }
        if (current != null && logicalMusicId == musicId && current.data().key().equals(prepared.key())) return;
        StreamedTrack replacement = new StreamedTrack(new StreamedTrackData(prepared));
        current = replacement;
        logicalMusicId = musicId;
        clearFade();
    }

    public int mixInto(short[] output, int frames) {
        requireUsable();
        validateOutput(output, frames);
        if (current == null || pauseMask != 0) return 0;
        int mixed = current.mixInto(output, frames, fadeGain, activeRate());
        if (current.ended()) dropSource(true);
        return mixed;
    }

    public void pause(int reason) {
        requireUsable();
        requirePauseReason(reason);
        pauseMask |= reason;
    }

    public void resume(int reason) {
        requireUsable();
        requirePauseReason(reason);
        pauseMask &= ~reason;
    }

    public void setSpeedMultiplier(int multiplier) {
        requireUsable();
        if (multiplier <= 0) throw new IllegalArgumentException("Speed multiplier must be positive");
        desiredMultiplier = multiplier;
    }

    public void fadeOut(int steps, int stepDelay) {
        requireUsable();
        if (steps <= 0 || stepDelay < 0) throw new IllegalArgumentException("Invalid fade cadence");
        if (current == null) throw new IllegalStateException("No streamed track is playing");
        fadeRemainingSteps = steps;
        fadeStepDelay = stepDelay;
        fadeDelayCounter = stepDelay;
        fadeStepAmount = -fadeGain / steps;
    }

    public void fadeIn(int steps, int stepDelay) {
        requireUsable();
        if (steps <= 0 || stepDelay < 0) throw new IllegalArgumentException("Invalid fade cadence");
        if (current == null) throw new IllegalStateException("No streamed track is playing");
        fadeGain = 0;
        fadeRemainingSteps = steps;
        fadeStepDelay = stepDelay;
        fadeDelayCounter = stepDelay;
        fadeStepAmount = 1.0f / steps;
    }

    /** Advances one logical fade tick, independently of presentation buffer size. */
    public void advanceFade() {
        requireUsable();
        if (current == null || pauseMask != 0 || fadeRemainingSteps == 0) return;
        if (fadeDelayCounter > 0) {
            fadeDelayCounter--;
            return;
        }
        fadeGain += fadeStepAmount;
        fadeRemainingSteps--;
        fadeDelayCounter = fadeStepDelay;
        if (fadeRemainingSteps == 0) {
            if (fadeStepAmount < 0) {
                fadeGain = 0;
                current = null;
                logicalMusicId = 0;
            } else {
                clearFade();
            }
        }
    }

    public void stop() {
        requireUsable();
        dropSource(true);
    }

    public void reset() {
        requireUsable();
        dropSource(true);
        pauseMask = 0;
        desiredMultiplier = 1;
    }

    public void close() {
        requireOwner();
        if (closed) return;
        dropSource(true);
        pauseMask = 0;
        desiredMultiplier = 1;
        closed = true;
    }

    public boolean playing() { requireUsable(); return current != null; }
    public int outputRate() { requireUsable(); return outputRate; }
    public boolean paused() { requireUsable(); return pauseMask != 0; }
    public int pauseMask() { requireUsable(); return pauseMask; }
    public double position() { requireUsable(); return current == null ? 0 : current.position(); }
    public double rate() { requireUsable(); return current == null ? 1.0 : activeRate(); }
    public float fadeGain() { requireUsable(); return fadeGain; }
    public boolean fadeActive() { requireUsable(); return fadeRemainingSteps > 0; }
    public boolean fadeAtFullGain() { requireUsable(); return fadeRemainingSteps == 0 && fadeGain == 1.0f; }
    public boolean closed() { requireOwner(); return closed; }

    public Optional<StreamedPlaybackSnapshot> capture() {
        requireUsable();
        if (current == null) return Optional.empty();
        return Optional.of(new StreamedPlaybackSnapshot(current.data().key(), logicalMusicId, current.position(), pauseMask,
                new StreamedFadeSnapshot(fadeGain, fadeRemainingSteps, fadeStepDelay, fadeDelayCounter, fadeStepAmount),
                activeRate()));
    }

    public void restore(StreamedPlaybackSnapshot state, TrackResolver resolver) {
        requireUsable();
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(resolver, "resolver");
        if (state.key() == null) throw new IllegalArgumentException("Snapshot track key is required");
        if (state.logicalMusicId() < -1) throw new IllegalArgumentException("Logical music id must be -1 or nonnegative");
        validatePauseMask(state.pauseMask());
        validateFade(state.fade());
        if (!Double.isFinite(state.rate()) || (state.rate() != 1.0 && state.rate() != 1.25)) {
            throw new IllegalArgumentException("Unsupported streamed playback rate");
        }
        PreparedTrack prepared = resolver.resolve(state.key());
        if (prepared == null) throw new IllegalArgumentException("No prepared track resolves snapshot key");
        if (!prepared.key().equals(state.key())) throw new IllegalArgumentException("Resolved track key differs");
        if (prepared.pcm().sampleRate() != outputRate) throw new IllegalArgumentException("Restored PCM rate differs");
        if (state.rate() == 1.25 && !prepared.tempoEffects()) {
            throw new IllegalArgumentException("Track does not opt into tempo effects");
        }
        StreamedTrack replacement = new StreamedTrack(new StreamedTrackData(prepared));
        replacement.restorePosition(state.sourceFramePosition());

        current = replacement;
        logicalMusicId = state.logicalMusicId();
        pauseMask = state.pauseMask();
        StreamedFadeSnapshot fade = state.fade();
        fadeGain = fade.gain();
        fadeRemainingSteps = fade.remainingSteps();
        fadeStepDelay = fade.stepDelay();
        fadeDelayCounter = fade.delayCounter();
        fadeStepAmount = fade.stepAmount();
        desiredMultiplier = state.rate() == 1.25 ? 2 : 1;
    }

    private double activeRate() {
        return current != null && current.data().tempoEffects() && desiredMultiplier > 1 ? 1.25 : 1.0;
    }

    private void dropSource(boolean clearFade) {
        current = null;
        logicalMusicId = 0;
        if (clearFade) clearFade();
    }

    private void clearFade() {
        fadeGain = 1;
        fadeRemainingSteps = 0;
        fadeStepDelay = 0;
        fadeDelayCounter = 0;
        fadeStepAmount = 0;
    }

    private static void validateOutput(short[] output, int frames) {
        Objects.requireNonNull(output, "output");
        if (frames < 0 || frames > output.length / 2) {
            throw new IllegalArgumentException("Stereo output is too small for requested frames");
        }
    }

    private static void requirePauseReason(int reason) {
        if (reason != PAUSE_JINGLE && reason != PAUSE_APP && reason != PAUSE_REWIND) {
            throw new IllegalArgumentException("Pause reason must be exactly one known bit");
        }
    }

    private static void validatePauseMask(int mask) {
        if (mask < 0 || (mask & ~KNOWN_PAUSE_MASK) != 0) throw new IllegalArgumentException("Unknown pause bits");
    }

    private static void validateFade(StreamedFadeSnapshot fade) {
        if (fade == null) throw new IllegalArgumentException("Snapshot fade state is required");
        if (!Float.isFinite(fade.gain()) || fade.gain() < 0 || fade.gain() > 1
                || fade.remainingSteps() < 0 || fade.stepDelay() < 0
                || fade.delayCounter() < 0 || fade.delayCounter() > fade.stepDelay()
                || !Float.isFinite(fade.stepAmount())
                || (fade.remainingSteps() == 0 && fade.stepAmount() != 0)
                || (fade.remainingSteps() == 0 && (fade.gain() != 1.0f
                        || fade.stepDelay() != 0 || fade.delayCounter() != 0))
                || (fade.remainingSteps() > 0
                        && (fade.stepAmount() == 0
                        || Math.abs(fade.gain() + fade.stepAmount() * fade.remainingSteps()
                        - (fade.stepAmount() > 0 ? 1.0f : 0.0f)) > 0.00001f))) {
            throw new IllegalArgumentException("Invalid streamed fade state");
        }
    }

    private void requireUsable() {
        requireOwner();
        if (closed) throw new IllegalStateException("Streamed music player is closed");
    }

    private void requireOwner() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Streamed music player used from a non-owner thread");
        }
    }

    @FunctionalInterface
    public interface TrackResolver {
        PreparedTrack resolve(TrackKey key);
    }
}
