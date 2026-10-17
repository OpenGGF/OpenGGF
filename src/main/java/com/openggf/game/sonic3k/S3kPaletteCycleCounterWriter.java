package com.openggf.game.sonic3k;

/**
 * Object writes to the ROM {@code Palette_cycle_counters} bytes of the shared AnPal
 * bank. Objects run after Animate_Palette in LevelLoop, so a write takes effect after
 * the current frame's palette call.
 */
public interface S3kPaletteCycleCounterWriter {
    void writePaletteCycleCountersByte(int offset, int value);
}
