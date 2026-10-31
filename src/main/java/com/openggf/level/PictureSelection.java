package com.openggf.level;

/** An explicit picture request. Exact consumers never need an interpolation phase. */
public sealed interface PictureSelection {
    record Latest() implements PictureSelection { }
    record Between(double alpha) implements PictureSelection {
        public Between {
            if (!Double.isFinite(alpha) || alpha < 0 || alpha >= 1)
                throw new IllegalArgumentException("Picture alpha must be finite and in [0, 1)");
        }
    }
    PictureSelection LATEST = new Latest();
    static PictureSelection between(double alpha) {
        if (!Double.isFinite(alpha) || alpha < 0) throw new IllegalArgumentException("Invalid picture alpha");
        return alpha >= 1 ? LATEST : new Between(alpha);
    }
}
