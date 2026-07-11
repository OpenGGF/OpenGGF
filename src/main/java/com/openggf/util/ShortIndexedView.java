package com.openggf.util;

/**
 * Read-only indexed access to signed 16-bit values without exposing mutable
 * primitive-array storage.
 */
@com.openggf.game.ModApi
public interface ShortIndexedView {
    int size();

    short get(int index);
}
