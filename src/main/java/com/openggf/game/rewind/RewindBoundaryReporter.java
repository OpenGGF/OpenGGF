package com.openggf.game.rewind;

@FunctionalInterface
@com.openggf.game.ModApi
public interface RewindBoundaryReporter {
    RewindBoundaryReporter NO_OP = boundary -> {
    };

    void markBoundary(RewindBoundary boundary);
}
