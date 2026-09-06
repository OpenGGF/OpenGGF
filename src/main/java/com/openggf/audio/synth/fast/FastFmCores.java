package com.openggf.audio.synth.fast;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Binding point for the fast FM DSP implementation.
 *
 * <p>The facade and configuration were delivered ahead of the clean-room DSP;
 * until an implementation is bound, selecting the fast core fails loudly at
 * synthesizer construction instead of producing silence.
 */
public final class FastFmCores {
    private static volatile Supplier<FmDsp> factory;

    private FastFmCores() {
    }

    /** Installs the production DSP factory; called once from static wiring. */
    public static void bind(Supplier<FmDsp> dspFactory) {
        factory = Objects.requireNonNull(dspFactory, "dspFactory");
    }

    /** Whether a fast DSP is available in this build. */
    public static boolean available() {
        return factory != null;
    }

    /** A reset DSP instance. */
    public static FmDsp newDsp() {
        Supplier<FmDsp> bound = factory;
        if (bound == null) {
            throw new IllegalStateException(
                    "audio.fmCore=fast selected but no fast FM DSP is bound in this build;"
                            + " use audio.fmCore=accurate");
        }
        return Objects.requireNonNull(bound.get(), "fast FM DSP");
    }
}
