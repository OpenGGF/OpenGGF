package com.openggf.audio.smps;

import java.util.Objects;
import java.util.function.Supplier;

/** Internal sequencer configuration and presentation binding. */
public final class SmpsConfigBinding {
    private SmpsConfigBinding() {}

    public static SmpsSequencerConfig.Builder withFullPalUpdateReload(
            SmpsSequencerConfig.Builder builder, int reload) {
        return builder.fullPalUpdateReload(reload);
    }

    public static int fullPalUpdateReload(SmpsSequencerConfig config) {
        return config.fullPalUpdateReload();
    }

    /** Freeze caller-owned end flags before resolving a potentially stateful handler factory. */
    public static SmpsSequencerConfig bind(SmpsSequencerConfig source, Supplier<CoordFlagHandler> handler) {
        return new SmpsSequencerConfig(Objects.requireNonNull(source, "source"), handler);
    }
}
