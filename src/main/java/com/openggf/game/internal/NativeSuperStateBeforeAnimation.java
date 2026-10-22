package com.openggf.game.internal;

/**
 * Marks a controller whose native powered-form routine runs in the normal
 * player control body, after movement/display and before history/water/animation.
 * Other controllers retain their status-tail dispatch.
 */
public interface NativeSuperStateBeforeAnimation {
}
