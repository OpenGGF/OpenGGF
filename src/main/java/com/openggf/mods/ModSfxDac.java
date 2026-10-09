package com.openggf.mods;

/**
 * A retro DAC rendering of a mod SFX, derived by the host when it prepares the mod's audio
 * (manifest {@code dac:} on an SFX entry), so a mod can ship one clean recording and offer a
 * period-style version too. The input is low-passed at {@code lowpassHz}, held at
 * {@code sampleRate} (zero-order hold, like a sample-and-hold DAC), quantised to {@code bits}
 * signed bits, then mixed with {@code recoveryMix} of the input high-passed at
 * {@code recoveryHighpassHz}, which keeps consonants intelligible. This is an artistic effect,
 * not emulation of a particular chip.
 */
public record ModSfxDac(int sampleRate, int bits, int lowpassHz, float recoveryMix, int recoveryHighpassHz) {
    public ModSfxDac {
        if (sampleRate < 2000 || sampleRate > 32000) throw new IllegalArgumentException("dac sampleRate must be 2000..32000");
        if (bits < 2 || bits > 15) throw new IllegalArgumentException("dac bits must be 2..15");
        if (lowpassHz < 200 || lowpassHz > 16000) throw new IllegalArgumentException("dac lowpassHz must be 200..16000");
        if (!Float.isFinite(recoveryMix) || recoveryMix < 0 || recoveryMix > 1) {
            throw new IllegalArgumentException("dac recoveryMix must be 0..1");
        }
        if (recoveryHighpassHz < 200 || recoveryHighpassHz > 16000) {
            throw new IllegalArgumentException("dac recoveryHighpassHz must be 200..16000");
        }
    }

    /** Stable identity for preparation caches. */
    String fingerprint() {
        return "dac:" + sampleRate + ":" + bits + ":" + lowpassHz + ":" + Float.floatToIntBits(recoveryMix) + ":" + recoveryHighpassHz;
    }

    /** Renders {@code source} (any rate, interleaved channels) through the DAC; same rate and length. */
    PcmData apply(PcmData source) {
        int channels = source.channels();
        int rate = source.sampleRate();
        int frames = source.frameCount();
        short[] out = new short[source.sampleCount()];
        int nyquistGuard = rate / 2 - 1;
        Biquad lowpass = Biquad.lowpass(rate, Math.min(lowpassHz, nyquistGuard));
        Biquad highpass = Biquad.highpass(rate, Math.min(recoveryHighpassHz, nyquistGuard));
        int levels = 1 << (bits - 1);
        int fade = Math.max(1, rate / 200);
        for (int channel = 0; channel < channels; channel++) {
            lowpass.reset();
            highpass.reset();
            double held = 0;
            double phase = 1;
            double step = Math.min(1.0, (double) sampleRate / rate);
            for (int frame = 0; frame < frames; frame++) {
                double x = source.sampleAt(frame * channels + channel) / 32768.0;
                double low = lowpass.next(x);
                phase += step;
                if (phase >= 1) {
                    phase -= 1;
                    held = Math.max(-levels, Math.min(levels - 1, Math.round(low * levels))) / (double) levels;
                }
                double mixed = (1 - recoveryMix) * held + recoveryMix * highpass.next(x);
                double gain = Math.min(1.0, Math.min(frame + 1, frames - frame) / (double) fade);
                out[frame * channels + channel] = (short) Math.max(-32768, Math.min(32767, Math.round(mixed * gain * 32768)));
            }
        }
        return PcmData.takeOwnership(rate, channels, out);
    }

    /** A second-order Butterworth section (RBJ cookbook, Q = 1/sqrt(2)). */
    private static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0; this.a1 = a1 / a0; this.a2 = a2 / a0;
        }

        static Biquad lowpass(int rate, int cutoff) {
            double w = 2 * Math.PI * cutoff / rate, cos = Math.cos(w), alpha = Math.sin(w) / Math.sqrt(2);
            return new Biquad((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad highpass(int rate, int cutoff) {
            double w = 2 * Math.PI * cutoff / rate, cos = Math.cos(w), alpha = Math.sin(w) / Math.sqrt(2);
            return new Biquad((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        void reset() { x1 = x2 = y1 = y2 = 0; }

        double next(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }
    }
}
