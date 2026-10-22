package com.openggf.game.nativemenu;

/** Optional game service. A null screen means that native product is unavailable. */
public interface NativeTitleModeProvider {
    NativeMenuScreen optionsScreen();

    /** Return a screen only when its actual session, arena and results flow is supported. */
    default NativeMenuScreen competitionScreen() { return null; }
}
