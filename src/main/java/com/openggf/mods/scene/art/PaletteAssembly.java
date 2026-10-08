package com.openggf.mods.scene.art;

import com.openggf.mods.scene.SceneRomArt;
import java.util.Objects;

/** Builds four independent palette lines from ROM reads or owned colours. */
@com.openggf.game.ModApi
public final class PaletteAssembly {
    private final int[] colors = new int[64];
    public PaletteAssembly put(int firstColor, int[] values) {
        Objects.requireNonNull(values, "colours");
        if (firstColor < 0 || firstColor > 64 || values.length > 64 - firstColor) {
            throw new IllegalArgumentException("Palette write leaves the four palette lines");
        }
        System.arraycopy(values, 0, colors, firstColor, values.length);
        return this;
    }
    public PaletteAssembly rom(SceneRomArt rom, int address, int firstColor, int count) {
        if (count < 1 || count > 64 || firstColor < 0 || firstColor > 64 - count) {
            throw new IllegalArgumentException("ROM palette placement leaves the four palette lines");
        }
        return put(firstColor, Objects.requireNonNull(rom, "ROM art").palette(address, count));
    }
    public PaletteAssembly line(int line, int[] values) {
        if (line < 0 || line > 3 || values.length != 16) throw new IllegalArgumentException("A palette line has 16 colours");
        return put(line * 16, values);
    }
    public int[] build() { return colors.clone(); }
}
