package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdStageDemo;

import java.io.IOException;
import java.util.Objects;

/**
 * The one stage loader: reads a stage file from the disc and decodes it through the
 * generated {@link CdStageCatalogue}. Every stage payload takes the same path; nothing
 * here depends on which zone, act or period is loaded.
 */
public final class CdStageLoader {
    private final CdDiscDataSource disc;

    public CdStageLoader(CdDiscDataSource disc) {
        this.disc = Objects.requireNonNull(disc, "disc");
    }

    public CdStageImage readStage(CdStagePayload payload) throws IOException {
        return new CdStageImage(disc.readFile(payload.file()));
    }

    /** Decodes a stage; {@code publishGraphics} uploads patterns and palettes to the live renderer. */
    public CdLevel load(CdStagePayload payload, int zoneIndex, boolean publishGraphics) throws IOException {
        return load(payload, readStage(payload), zoneIndex, publishGraphics);
    }

    /** Decodes an already-read stage image. */
    public CdLevel load(CdStagePayload payload, CdStageImage image, int zoneIndex, boolean publishGraphics)
            throws IOException {
        return new CdLevel(payload, image, CdStageCatalogue.entry(payload), zoneIndex, publishGraphics, disc.identity(), image, null);
    }
    public CdStageImage readDemo(CdStageDemo demo) throws IOException {
        return new CdStageImage(disc.readFile(demo.file()));
    }

    public CdLevel loadDemo(CdStageDemo demo, CdStageImage image, CdStageImage objectImage, int zoneIndex)
            throws IOException {
        return new CdLevel(demo.payload(), image, CdStageCatalogue.entry(demo), zoneIndex, true, disc.identity(),
                objectImage, demo);
    }

    /** Reject foreign media and inconsistent payload coordinates before reading a stage. */
    public CdLevel load(com.openggf.game.internal.NativeProjectSource source, int zoneIndex,
                        boolean publishGraphics) throws IOException {
        return load(validate(source), zoneIndex, publishGraphics);
    }

    public CdStagePayload validate(com.openggf.game.internal.NativeProjectSource source) throws IOException {
        if (!disc.identity().equals(source.mediaIdentity()))
            throw new IOException("Native project disc identity does not match the opened disc");
        final CdStagePayload payload;
        try { payload = CdStagePayload.valueOf(source.stage()); }
        catch (IllegalArgumentException error) { throw new IOException("Unknown native project stage", error); }
        var stage = payload.stage();
        if (source.zone() != stage.zone().nativeRound() || source.act() != stage.actNumber()
                || !source.period().equals(stage.period().name()) || !source.program().equals(payload.file().isoName())
                || source.blockGridSide() != 16)
            throw new IOException("Native project stage coordinates/program disagree with the selected payload");
        return payload;
    }
}
