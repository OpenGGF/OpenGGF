package com.openggf;

import com.openggf.level.PictureSelection;
import java.util.Objects;

/** A capture interval is always exact; only a window picture may select a phase. */
sealed interface LivePictureRequest {
    record CaptureLatest() implements LivePictureRequest { }
    record Window(PictureSelection selection) implements LivePictureRequest {
        public Window { Objects.requireNonNull(selection); }
    }
    default boolean capture() { return this instanceof CaptureLatest; }
    default PictureSelection selection() { return this instanceof Window w ? w.selection() : PictureSelection.LATEST; }
}
