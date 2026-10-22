package com.openggf.game.internal;

import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.IntToDoubleFunction;

/** Frozen event-owned descriptor image and pure camera-dependent nametable addressing. */
public record BackgroundBuildProjection(long descriptorRevision, IntBinaryOperator descriptors,
                                        IntToDoubleFunction nametableBase) {
    public BackgroundBuildProjection {
        Objects.requireNonNull(nametableBase);
        if (descriptorRevision != 0) Objects.requireNonNull(descriptors);
    }
}
