package com.openggf.game.internal;

import com.openggf.data.Rom;

/** Internal composition hook: bind stock revision choices before exposing module providers. */
public interface RetailRomAware {
    void bindRetailRom(Rom rom);
}
