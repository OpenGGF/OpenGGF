package com.openggf.util;

/** Read-only indexed access to integer values without exposing array capacity. */
@com.openggf.game.ModApi
public interface IntIndexedView {
    int size();

    int get(int index);
}
