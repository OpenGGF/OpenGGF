package com.openggf.audio.smps;

import java.util.Objects;
import java.util.function.Supplier;

/** Internal presentation binding; keeps session handlers out of shared settings. */
public final class SmpsConfigBinding {
    private SmpsConfigBinding() {}

    /** Freeze caller-owned end flags before resolving a potentially stateful handler factory. */
    public static SmpsSequencerConfig bind(SmpsSequencerConfig source, Supplier<CoordFlagHandler> handler) {
        return new SmpsSequencerConfig(Objects.requireNonNull(source, "source"), handler);
    }
}
