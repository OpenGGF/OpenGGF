package com.openggf;

import com.openggf.game.nativemenu.NativeMenuScreen;

/** Internal shared presentation access for production Engine and capture hosts. */
public final class NativeMenuPresentationAccess {
    private NativeMenuPresentationAccess() { }
    public static NativeMenuScreen screen(GameLoop loop) { return loop.getNativeMenuScreen(); }
}
