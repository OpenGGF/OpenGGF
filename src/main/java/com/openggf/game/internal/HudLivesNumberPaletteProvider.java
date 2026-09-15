package com.openggf.game.internal;

/** Engine-owned palette selection for HUD life-count digits, independent of icon art. */
@FunctionalInterface
public interface HudLivesNumberPaletteProvider {
    int paletteLine();
}
