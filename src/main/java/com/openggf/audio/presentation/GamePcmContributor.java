package com.openggf.audio.presentation;

/**
 * Optional capability of a game-owned request service whose game also has sample sources
 * outside SMPS (Sonic CD's disc audio tracks and RF5C164 PCM). The producer mixes the
 * contribution into each forward packet on the output clock, after the SMPS session and
 * before PCM history, capture and the speaker, so rewind playback and recordings include it.
 */
public interface GamePcmContributor {
    /** Called once when the presentation producer is built, before any render. */
    void bindOutputRate(int sampleRate);

    /** Adds this game's samples into interleaved stereo {@code target}. */
    void render(short[] target, int stereoFrames);

    /**
     * The player's music and sound-effect volumes, in {@link AudioPresentationMixer#UNITY_GAIN}
     * units, pushed beside the SMPS session's own gains whenever the mix is applied (repeated
     * values included). The contributor applies each to the sources the game itself treats as
     * music or effects. Presentation only: at unity on both the contribution must be
     * bit-identical to an ungained render, and nothing the game simulates may depend on it.
     */
    default void setMixGains(int musicGain, int sfxGain) { }
}
