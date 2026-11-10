package com.openggf.game.soniccd.level;

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
    static CdR11aPaletteAssets load(CdR11aAssets owner, CdProgrammeReader reader) throws IOException {
        Objects.requireNonNull(owner, "actual R11A aggregate");
        var profile=Objects.requireNonNull(owner.profile(), "profile");
        var identity=Objects.requireNonNull(owner.identity(), "identity");
        if (profile.variant() != CdReleaseVariant.USA || identity.variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("Palette-cycle sources are reviewed only for USA R11A Present");
        }
        if (!profile.stage().equals(identity.stage()) || profile.file() != identity.file() || profile.domain() != identity.domain()
                || !profile.expectedMmdSha256().equals(identity.mmdSha256())) {
            throw new IllegalArgumentException("Palette source owner/profile identity mismatch");
        }
        // Exact loader-established reader/image/aggregate custody precedes every source borrow.
        // The common table parser owns extraction; this adapter only retains legacy typed products.
        owner.requireProgrammeReader(reader);
        var parsed=reader.loadTables();var declarations=reader.definition().load().cycles();
        if(parsed.cycleProgramme()!=CdProgrammeDefinition.CycleProgramme.STANDARD_CONFIGURED
                || !parsed.directCycles().isEmpty() || parsed.cycles().size()!=6 || declarations.size()!=6)
            throw new IllegalArgumentException("Original R11A requires six configured palette scripts");
        List<View> views = new ArrayList<>();
        for (int cycle = 0; cycle < parsed.cycles().size(); cycle++) {
            var source=parsed.cycles().get(cycle);var declaration=declarations.get(cycle);
            if(source.kind()!=CdProgrammeDefinition.CycleKind.STANDARD)
                throw new IllegalArgumentException("Original R11A palette script format differs");
            views.add(new View(cycle, range(reader,declaration.script()), range(reader,declaration.colors()),
                    new CdLevelPaletteCycle.Script(source.script(),source.colors())));
        }
        return new CdR11aPaletteAssets(identity, views);
    }

    public CdR11aAssets.Identity identity() { return identity; }
    public List<View> views() { return views; }
    public List<CdLevelPaletteCycle.Script> scripts() { return scripts; }

    private static CdR11aProfile.FileRange range(CdProgrammeReader reader, CdProgrammeDefinition.Slice source) {
        return new CdR11aProfile.FileRange(reader.file(), CdMmdImage.Domain.ORIGINAL_WORD_RAM,
                reader.fileOffset(source.key())+source.offset(), source.length());
    }
}
