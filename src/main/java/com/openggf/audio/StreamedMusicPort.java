package com.openggf.audio;

import java.util.Objects;
import java.util.Optional;

/**
 * Audio-owned boundary for launch-prepared streamed music. Implementations may
 * live in the mod runtime; the audio layer never depends on mod-domain types.
 * Calls are confined to the presentation thread.
 */
@com.openggf.game.ModApi
public interface StreamedMusicPort extends AutoCloseable {
    int PAUSE_JINGLE = 1;
    int PAUSE_APP = 2;
    int PAUSE_REWIND = 4;
    int KNOWN_PAUSE_MASK = PAUSE_JINGLE | PAUSE_APP | PAUSE_REWIND;

    StreamedMusicPort EMPTY = new StreamedMusicPort() {
        @Override public int outputRate() { return 0; }
        @Override public boolean hasStockOverride(int musicId) { return false; }
        @Override public boolean isCurrentStockOverride(int musicId) { return false; }
        @Override public void playStockOverride(int musicId) { }
        @Override public boolean hasTrack(TrackRef track) { Objects.requireNonNull(track, "track"); return false; }
        @Override public void playTrack(TrackRef track) {
            throw new IllegalArgumentException("No streamed track is installed: " + track);
        }
        @Override public boolean hasSource() { return false; }
        @Override public int mixInto(short[] output, int frames) {
            validateOutput(output, frames);
            return 0;
        }
        @Override public void pause(int reason) { validatePauseReason(reason); }
        @Override public void resume(int reason) { validatePauseReason(reason); }
        @Override public void fadeOut(int steps, int stepDelay) { validateFadeRequest(steps, stepDelay); }
        @Override public void fadeIn(int steps, int stepDelay) { validateFadeRequest(steps, stepDelay); }
        @Override public void advanceFade() { }
        @Override public boolean fadeActive() { return false; }
        @Override public boolean fadeAtFullGain() { return true; }
        @Override public void setSpeedMultiplier(int multiplier) {
            if (multiplier <= 0) throw new IllegalArgumentException("Speed multiplier must be positive");
        }
        @Override public void stop() { }
        @Override public void reset() { }
        @Override public Optional<State> captureState() { return Optional.empty(); }
        @Override public boolean restoreState(State state) {
            Objects.requireNonNull(state, "state");
            return false;
        }
        @Override public void close() { }
    };

    @com.openggf.game.ModApi
    record TrackRef(String owner, String name) {
        public TrackRef {
            if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Track owner is required");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Track name is required");
        }
    }

    @com.openggf.game.ModApi
    record FadeState(float gain, int remainingSteps, int stepDelay,
                     int delayCounter, float stepAmount) {
        public FadeState {
            if (!Float.isFinite(gain) || gain < 0 || gain > 1
                    || remainingSteps < 0 || stepDelay < 0
                    || delayCounter < 0 || delayCounter > stepDelay
                    || !Float.isFinite(stepAmount)) {
                throw new IllegalArgumentException("Invalid streamed fade state");
            }
            if (remainingSteps == 0 && (stepDelay != 0 || delayCounter != 0 || stepAmount != 0)) {
                throw new IllegalArgumentException("Idle streamed fade must have zero cadence");
            }
            if (remainingSteps == 0 && gain != 1.0f) {
                throw new IllegalArgumentException("Idle streamed fade must have full gain");
            }
            if (remainingSteps > 0 && stepAmount == 0) {
                throw new IllegalArgumentException("Active streamed fade requires a signed step");
            }
            if (remainingSteps > 0) {
                float endpoint = gain + stepAmount * remainingSteps;
                float expected = stepAmount < 0 ? 0 : 1;
                if (Math.abs(endpoint - expected) > 0.00001f) {
                    throw new IllegalArgumentException("Streamed fade cadence has an unreachable endpoint");
                }
            }
        }

        public static FadeState idle() {
            return new FadeState(1, 0, 0, 0, 0);
        }
    }

    @com.openggf.game.ModApi
    record State(TrackRef track, int logicalMusicId, double sourceFramePosition,
                 int pauseMask, FadeState fade, double rate) {
        public State {
            Objects.requireNonNull(track, "track");
            Objects.requireNonNull(fade, "fade");
            if (logicalMusicId < -1) throw new IllegalArgumentException("Logical music id must be -1 or nonnegative");
            if (!Double.isFinite(sourceFramePosition) || sourceFramePosition < 0) {
                throw new IllegalArgumentException("Source frame position must be finite and nonnegative");
            }
            if (pauseMask < 0 || (pauseMask & ~KNOWN_PAUSE_MASK) != 0) {
                throw new IllegalArgumentException("Unknown streamed pause bits");
            }
            if (!Double.isFinite(rate) || (rate != 1.0 && rate != 1.25)) {
                throw new IllegalArgumentException("Unsupported streamed playback rate");
            }
        }
    }

    int outputRate();
    boolean hasStockOverride(int musicId);
    boolean isCurrentStockOverride(int musicId);
    void playStockOverride(int musicId);
    default boolean hasTrack(TrackRef track) {
        Objects.requireNonNull(track, "track");
        return false;
    }

    /** Plays an exact namespaced key; implementations must fail rather than use stock fallback. */
    default void playTrack(TrackRef track) {
        throw new IllegalArgumentException("Unknown namespaced streamed track: "
                + Objects.requireNonNull(track, "track"));
    }
    boolean hasSource();
    int mixInto(short[] output, int frames);
    void pause(int reason);
    void resume(int reason);
    void fadeOut(int steps, int stepDelay);
    void fadeIn(int steps, int stepDelay);
    void advanceFade();
    boolean fadeActive();
    boolean fadeAtFullGain();
    void setSpeedMultiplier(int multiplier);
    void stop();
    void reset();
    Optional<State> captureState();

    /** Returns false when the captured track is unavailable in this launch session. */
    boolean restoreState(State state);

    @Override
    void close();

    private static void validateOutput(short[] output, int frames) {
        Objects.requireNonNull(output, "output");
        if (frames < 0 || frames > output.length / 2) {
            throw new IllegalArgumentException("Stereo output is too small for requested frames");
        }
    }

    private static void validatePauseReason(int reason) {
        if (reason != PAUSE_JINGLE && reason != PAUSE_APP && reason != PAUSE_REWIND) {
            throw new IllegalArgumentException("Pause reason must be exactly one known bit");
        }
    }

    private static void validateFadeRequest(int steps, int delay) {
        if (steps <= 0 || delay < 0) {
            throw new IllegalArgumentException("Invalid fade cadence");
        }
    }
}
