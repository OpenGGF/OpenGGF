package com.openggf.audio.rewind;

import com.openggf.audio.smps.SmpsSequencer.Region;

import java.util.List;
import java.util.Objects;

public record SmpsSequencerSnapshot(
        Region region,
        boolean speedShoes,
        boolean sfxMode,
        int normalTempo,
        int commData,
        boolean fm6DacOff,
        int maxTicks,
        float pitch,
        int sfxPriority,
        boolean specialSfx,
        boolean sfx,
        int psgLatchChannel,
        int speedMultiplier,
        int speedupTimeout,
        int musicTempoPercent,
        int musicTempoPhase,
        FadeSnapshot fade,
        double sampleRate,
        double samplesPerFrame,
        double sampleCounter,
        int tempoWeight,
        int tempoAccumulator,
        int dividingTiming,
        boolean primed,
        List<SmpsTrackSnapshot> tracks) {

    public SmpsSequencerSnapshot {
        com.openggf.audio.MusicTempoPercent.requireValid(musicTempoPercent);
        if (musicTempoPhase < 0 || musicTempoPhase >= 100)
            throw new IllegalArgumentException("music tempo phase must be between 0 and 99");
        Objects.requireNonNull(region, "region");
        fade = Objects.requireNonNull(fade, "fade");
        tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
    }

    public record FadeSnapshot(
            int steps,
            int delayInit,
            int delayCounter,
            int addFm,
            int addPsg,
            boolean active,
            boolean fadeOut) {
    }
}
