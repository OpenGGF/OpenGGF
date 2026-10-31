package com.openggf;

import java.util.Objects;

/** A capture interval is always exact; only a window picture may select a phase. */
sealed interface LivePictureRequest {
    record CaptureLatest() implements LivePictureRequest { }
    record Window(Selection selection) implements LivePictureRequest {
        public Window { Objects.requireNonNull(selection); }
    }
    default boolean capture() { return this instanceof CaptureLatest; }
    default Selection selection() { return this instanceof Window w ? w.selection() : Selection.LATEST; }

    sealed interface Selection {
        Selection LATEST = new Latest();
        record Latest() implements Selection { }
        record Between(double alpha) implements Selection {
            public Between {
                if (!Double.isFinite(alpha) || alpha < 0 || alpha >= 1) {
                    throw new IllegalArgumentException("alpha must be in [0,1)");
                }
            }
        }
        static Selection between(double alpha) {
            return alpha >= 1 ? LATEST : new Between(alpha);
        }
    }
}
