package com.openggf.game.internal;

/** An ending whose production level continuation owns its initial demo assembly. */
public interface NativeEndingDemoAssembly {
    void beginDemoLoad();
    boolean ownsInitialDemoAssembly();
}
