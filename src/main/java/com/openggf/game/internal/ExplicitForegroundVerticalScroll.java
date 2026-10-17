package com.openggf.game.internal;

/**
 * Internal: a scroll handler whose foreground V-scroll is the pass's {@code V_scroll_value}
 * even when that word is 0. The parallax owner otherwise reads a zero handler value as
 * "follow the camera".
 */
public interface ExplicitForegroundVerticalScroll {
    /** Whether {@code getVscrollFactorFG()} is authoritative this pass, including 0. */
    boolean foregroundVerticalScrollExplicit();
}
