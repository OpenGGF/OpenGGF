package com.openggf.debug;

@com.openggf.game.ModApi
public enum DebugState {
    NONE,
    PATTERNS_VIEW,
    BLOCKS_VIEW;

    private static final DebugState[] vals = values();

    public DebugState next() {
        return vals[(this.ordinal() + 1) % vals.length];
    }
}
