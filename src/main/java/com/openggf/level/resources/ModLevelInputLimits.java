package com.openggf.level.resources;

import com.openggf.io.ModInputLimits;

/** Primitive production limits exposed to level decoders without leaking the I/O layer. */
public record ModLevelInputLimits(
        int maxMapWidth,
        int maxMapHeight,
        long maxMapCells,
        int maxLevelObjects,
        int maxLevelRings) {

    public static ModLevelInputLimits production() {
        ModInputLimits limits = ModInputLimits.production();
        return new ModLevelInputLimits(
                limits.maxMapWidth(),
                limits.maxMapHeight(),
                limits.maxMapCells(),
                limits.maxLevelObjects(),
                limits.maxLevelRings());
    }
}
