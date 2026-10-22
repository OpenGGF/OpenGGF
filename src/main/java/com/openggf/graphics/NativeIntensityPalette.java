package com.openggf.graphics;

import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;

/**
 * Engine-only native intensity recipe. The source remains the owner's unfaded
 * CRAM palette; neither the native fade nor the display profile is baked into it.
 */
public final class NativeIntensityPalette implements PaletteView {
    private final PaletteView source;
    private final int nativeSourceLine;

    private NativeIntensityPalette(PaletteView source, int nativeSourceLine) {
        this.source = java.util.Objects.requireNonNull(source, "source palette");
        if (source instanceof NativeIntensityPalette || nativeSourceLine < 0 || nativeSourceLine > 3)
            throw new IllegalArgumentException("Native shadow needs one unwrapped native palette line (0..3)");
        this.nativeSourceLine = nativeSourceLine;
    }

    public static NativeIntensityPalette shadow(PaletteView source, int nativeSourceLine) {
        return new NativeIntensityPalette(source, nativeSourceLine);
    }

    @Override public byte red(int index) { return source.red(index); }
    @Override public byte green(int index) { return source.green(index); }
    @Override public byte blue(int index) { return source.blue(index); }

    int nativeSourceLine() { return nativeSourceLine; }

    int displayedRgb(int index, PaletteFadePresentation fade, DisplayColorProfile profile,
                     DisplayColorExpansion expansion) {
        int r = red(index) & 255, g = green(index) & 255, b = blue(index) & 255;
        if (fade.affects(nativeSourceLine)) {
            int faded = fade.fadeRgb(r, g, b);
            r = (faded >>> 16) & 255;
            g = (faded >>> 8) & 255;
            b = faded & 255;
        }
        return DisplayColorConverter.toNativeShadowPackedRgb(PaletteFadePresentation.toGenesisChannel(r),
                PaletteFadePresentation.toGenesisChannel(g), PaletteFadePresentation.toGenesisChannel(b),
                profile, expansion);
    }
}
