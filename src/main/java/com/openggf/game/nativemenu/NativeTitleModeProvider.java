package com.openggf.game.nativemenu;

/** Optional game service. A null screen means that native product is unavailable. */
public interface NativeTitleModeProvider {
    NativeMenuScreen optionsScreen();

    /** Optional native options choice retained when returning through SEGA/title. */
    default NativeMenuScreen.Exit singlePlayerTitleExit() { return NativeMenuScreen.Exit.NONE; }

    /** Return a screen only when its actual session, arena and results flow is supported. */
    default NativeMenuScreen competitionScreen() { return null; }

    /** Explicit capabilities for a fresh visit; existing providers retain their behavior. */
    default NativeMenuScreen competitionScreen(NativeMenuHostContext context) {
        java.util.Objects.requireNonNull(context, "context");
        return competitionScreen();
    }
}
