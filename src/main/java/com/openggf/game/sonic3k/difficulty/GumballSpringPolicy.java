package com.openggf.game.sonic3k.difficulty;

/** Optional bounded recovery-spring arrangement, admitted when the native machine initializes. */
@com.openggf.game.ModApi
@FunctionalInterface
public interface GumballSpringPolicy {
    /** Returns 2..4 increasing symmetric native offsets, with normal strength and REP recovery. */
    int[] springOffsets();
}
