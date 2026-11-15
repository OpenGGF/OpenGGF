package com.openggf.game.soniccd.objects.common;

import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.level.resources.PlcParser;

/**
 * How long a queued PLC keeps {@code plcBuffer} non-empty. LoadPLC appends the list's entries
 * (Functions (General).asm:588-610); ProcessPLCs starts the next entry from the main loop once
 * the previous one has finished (:664-695); the level V-int decodes 3 tiles of it per frame, or 18
 * while {@code scrollLock} is set (DecompPLCSlow, :721-760), and an entry's last batch ends at its
 * last tile. Only the level V-int decodes; the lag V-int does not (VInt_Lag, Level/Main.asm:738-770;
 * VInt_Level .NoArtLoad, :836-846), so the queue is empty after the sum of each entry's
 * {@code ceil(tiles / batch)} level frames, whatever lag frames fall between them.
 */
public final class CdPlcQueue {
    /** DecompPLCSlow: move.w #3,plcProcTileCnt.w (Functions (General).asm:727). */
    public static final int SLOW_BATCH = 3;
    /** DecompPLCFast_Large: move.w #18,plcProcTileCnt.w (Functions (General).asm:711). */
    public static final int FAST_BATCH = 18;

    private CdPlcQueue() { }

    /** Level frames from the queueing frame until the queue is empty again. */
    public static int framesToDrain(CdStageImage image, CdStagePayload payload, int plcId, int batch) {
        int plcLists = CdStageCatalogue.entry(payload).plcLists();
        if (!com.openggf.game.soniccd.objects.art.CdStageVram.hasPlc(image, plcLists, plcId)) return 0;
        var definition = PlcParser.parse(image.reader(), plcLists, plcId);
        int frames = 0;
        for (var entry : definition.entries()) {
            // ProcessPLCs: move.w (a0)+,d2; andi.w #$7FFF,d2 is the entry's tile count (:672-679).
            int tiles = image.u16(image.offsetOf(entry.romAddr())) & 0x7FFF;
            frames += (tiles + batch - 1) / batch;
        }
        return frames;
    }
}
