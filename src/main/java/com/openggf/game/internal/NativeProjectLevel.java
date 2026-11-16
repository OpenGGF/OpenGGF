package com.openggf.game.internal;

/** Native level capability. Its portable editor view is never a runtime replacement. */
public interface NativeProjectLevel {
    NativeProjectSource nativeProjectSource();
    int[] nativeProjectStart();
}
