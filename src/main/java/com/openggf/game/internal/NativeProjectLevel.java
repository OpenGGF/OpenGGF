package com.openggf.game.internal;

/** Native level capability. Its portable editor view is never a runtime replacement. */
public interface NativeProjectLevel extends com.openggf.level.Level {
    NativeProjectSource nativeProjectSource();
    int[] nativeProjectStart();
    /** Reject canvas sizes that cannot be addressed by this stage's layout. */
    void validateProjectDimensions(int width, int height) throws java.io.IOException;
}
