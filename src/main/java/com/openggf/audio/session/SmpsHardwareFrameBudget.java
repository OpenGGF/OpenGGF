package com.openggf.audio.session;

/**
 * Sound-hardware time per presentation. A presented frame is one ROM
 * hardware frame, so the sound chips must advance one hardware frame of
 * master clocks per presentation, not one {@code 1 / frameRate} second of
 * wall time.
 *
 * <p>NTSC: 3420 master clocks per line, 262 lines, 896,040 master clocks per
 * frame at 53,693,175 Hz (59.9227 Hz). The chips render output samples at
 * their own internal rates, so the device is driven at the synthesis rate
 * {@code sampleRate * 53,693,175 / (frameRate * 896,040)}. One presentation of
 * {@code sampleRate / frameRate} output samples then spans exactly one
 * hardware frame of chip time. For example, 800 samples at 48 kHz cover
 * 21,334.29 YM2612 internal clocks, which is 896,040 / 42. The presentation
 * still emits {@code sampleRate / frameRate} samples per frame. Played at the
 * presentation's frame rate, that is about 1.29 per mille higher in pitch and
 * tempo than a 59.92 Hz console, and identical per frame. Measured by the
 * native-compatible stage: before this budget, 600 presentations completed 599
 * NTSC frames.
 *
 * <p>PAL is left unchanged (the synthesis rate equals the sample rate). The
 * engine's chips use the NTSC master clock throughout, so a PAL hardware-frame
 * budget (3420 x 313 at 53,203,424 Hz) would also need PAL chip clocks. That
 * remains open.
 */
public final class SmpsHardwareFrameBudget {
    /** NTSC master clock, Hz. */
    public static final double NTSC_MASTER_CLOCK = 53_693_175.0;
    /** NTSC master clocks per frame: 3420 per line, 262 lines. */
    public static final int NTSC_MASTER_CLOCKS_PER_FRAME = 3420 * 262;

    private SmpsHardwareFrameBudget() {
    }

    /**
     * An exact rational rate in hertz ({@code numerator / denominator}, reduced).
     * Consumers that count whole cycles, such as the SEGA PCM transport's Z80
     * byte cadence, use it instead of the rounded double.
     */
    public record Rate(long numerator, long denominator) {
        public Rate {
            if (numerator <= 0 || denominator <= 0) {
                throw new IllegalArgumentException("rate terms must be positive");
            }
            long divisor = java.math.BigInteger.valueOf(numerator).gcd(java.math.BigInteger.valueOf(denominator))
                    .longValueExact();
            numerator /= divisor;
            denominator /= divisor;
        }

        public double hz() {
            return (double) numerator / denominator;
        }
    }

    /** {@link #synthesisRate} as an exact rational: {@code sampleRate * 53,693,175 / (frameRate * 896,040)}. */
    public static Rate exactSynthesisRate(int sampleRate, int frameRate, boolean palRegion) {
        if (sampleRate <= 0 || frameRate <= 0) {
            throw new IllegalArgumentException("sample and frame rates must be positive");
        }
        if (palRegion) {
            return new Rate(sampleRate, 1);
        }
        return new Rate((long) sampleRate * (long) NTSC_MASTER_CLOCK,
                (long) frameRate * NTSC_MASTER_CLOCKS_PER_FRAME);
    }

    /** The live presentation's sound-device settings: the exact hardware-frame synthesis rate. */
    public static SmpsPhysicalDevice.Settings presentationSettings(int sampleRate, int frameRate, boolean palRegion,
            boolean dacInterpolate, com.openggf.audio.synth.FmCoreSelection fmCore,
            com.openggf.audio.synth.NativeCompatiblePcmOutput.Config nativePcmOutput) {
        return new SmpsPhysicalDevice.Settings(exactSynthesisRate(sampleRate, frameRate, palRegion),
                dacInterpolate, fmCore, nativePcmOutput);
    }

    /**
     * The rate the sound device must synthesize at so that one presentation
     * of {@code sampleRate / frameRate} output samples spans one hardware frame.
     */
    public static double synthesisRate(int sampleRate, int frameRate, boolean palRegion) {
        if (sampleRate <= 0 || frameRate <= 0) {
            throw new IllegalArgumentException("sample and frame rates must be positive");
        }
        return exactSynthesisRate(sampleRate, frameRate, palRegion).hz();
    }
}
