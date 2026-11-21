package com.openggf.audio.smps;

/** Presentation parameters carried by native creator songs and frozen with their program. */
public record SmpsMusicParameters(float gain, boolean tempoEffects, Integer speedUpTempo) {
    public SmpsMusicParameters(float gain, boolean tempoEffects) {
        this(gain, tempoEffects, null);
    }

    private static final SmpsMusicParameters STOCK = new SmpsMusicParameters(1.0f, true);

    public interface Source {
        SmpsMusicParameters musicParameters();
    }

    static boolean isCreatorMusic(AbstractSmpsData data) {
        return of(data) != STOCK;
    }

    public static SmpsMusicParameters of(AbstractSmpsData data) {
        return data instanceof Source source ? source.musicParameters() : STOCK;
    }
}
