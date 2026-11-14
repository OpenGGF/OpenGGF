package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdStagePayload;

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
        return new CdLevel(payload, image, CdStageCatalogue.entry(payload), zoneIndex, publishGraphics);
    }
}
