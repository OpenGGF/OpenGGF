package com.openggf.game.timing;

import java.util.Objects;
import java.util.function.Consumer;

public final class LoadTimeProfileFactory {
    private LoadTimeProfileFactory() {
    }

    public static LoadTimeProfile resolve(
            LoadTimeSimulationMode mode,
            LoadTimeProfile profiled,
            Consumer<String> warningSink) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(profiled, "profiled");
        Objects.requireNonNull(warningSink, "warningSink");
        return switch (mode) {
            case NONE -> LoadTimeProfile.IMMEDIATE;
            case PROFILED -> profiled;
            case FAST -> {
                warningSink.accept(
                        "FAST load-time simulation is not implemented; using NONE");
                yield LoadTimeProfile.IMMEDIATE;
            }
            case REALISTIC -> {
                warningSink.accept(
                        "REALISTIC load-time simulation is not implemented; using PROFILED");
                yield profiled;
            }
        };
    }
}
