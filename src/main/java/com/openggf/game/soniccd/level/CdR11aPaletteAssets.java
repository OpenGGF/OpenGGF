package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.resources.CdLevelPaletteCycle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detached authenticated USA R11A Present final palette-cycle sources, not current palette knowledge.
 * Source pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Palmtree Panic/Palette Cycle (Present).asm:86-171.
 * The actual USA entry at file$184 branches over the dead prototype to PalCycle_Do at$228.
 * Its six absolute LEA operand pairs at$230/$236 +14*n bind script roots$2C0+14*n and colours+8.
 * Runtime bytes come exclusively from the retained original R11A MMD, never the source checkout.
 */
public final class CdR11aPaletteAssets {
    public record View(int cycle, CdR11aProfile.FileRange scriptRange, CdR11aProfile.FileRange coloursRange,
                       CdLevelPaletteCycle.Script data) { }

    private final CdR11aAssets.Identity identity;
    private final List<View> views;
    private final List<CdLevelPaletteCycle.Script> scripts;

    private CdR11aPaletteAssets(CdR11aAssets.Identity identity, List<View> views) {
        this.identity = identity; this.views = List.copyOf(views);
        this.scripts = this.views.stream().map(View::data).toList();
    }

    /** Package-owned authenticated binding; public metadata records alone cannot publish a source owner. */
    static CdR11aPaletteAssets load(CdR11aProfile profile, CdR11aAssets.Identity identity, CdMmdImage image) throws IOException {
        Objects.requireNonNull(profile, "profile"); Objects.requireNonNull(identity, "identity");
        if (profile.variant() != CdReleaseVariant.USA || identity.variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("Palette-cycle sources are reviewed only for USA R11A Present");
        }
        if (!profile.stage().equals(identity.stage()) || profile.file() != identity.file() || profile.domain() != identity.domain()
                || !profile.expectedMmdSha256().equals(identity.mmdSha256())) {
            throw new IllegalArgumentException("Palette source owner/profile identity mismatch");
        }
        // Verify the entire actual immutable MMD before reading any source view. Asserted metadata or matching
        // operand/header bytes alone cannot authenticate altered payloads or a different regional image.
        profile.verifyProgram(identity.file(), identity.domain(), image);
        List<View> views = new ArrayList<>();
        for (int cycle = 0; cycle < 6; cycle++) {
            int offset = 0x2C0 + 14 * cycle;
            var script = range(offset, 8); var colours = range(offset + 8, 6);
            views.add(new View(cycle, script, colours, new CdLevelPaletteCycle.Script(script.read(image), colours.read(image))));
        }
        return new CdR11aPaletteAssets(identity, views);
    }

    public CdR11aAssets.Identity identity() { return identity; }
    public List<View> views() { return views; }
    public List<CdLevelPaletteCycle.Script> scripts() { return scripts; }

    private static CdR11aProfile.FileRange range(int offset, int length) {
        return new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, offset, length);
    }
}
