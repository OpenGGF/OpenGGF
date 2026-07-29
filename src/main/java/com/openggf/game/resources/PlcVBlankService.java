package com.openggf.game.resources;

/** Performs the active game's ordinary-level PLC work at its selected VBlank boundary. */
public interface PlcVBlankService {
    void serviceLevelVBlank();
}
