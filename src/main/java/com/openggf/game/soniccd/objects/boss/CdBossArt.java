package com.openggf.game.soniccd.objects.boss;

import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;

import java.io.IOException;

/**
 * Boss sheets: a boss's art arrives with the cue its init queues ({@code AddGfxQueue}), after the title card's
 * PLC 2 (Title Card/Main.asm:145-148), so a sheet is a window into level-start VRAM plus those cues in order. The
 * decode queue is not modelled: the sheets exist from the stage load and nothing draws them before the cue is
 * queued. Keys are {@code cd.boss.<zone>.<kind>}.
 */
public final class CdBossArt {
    /** PLC 2: the title card's slide-out cue (Title Card/Main.asm:145-148). */
    public static final int TITLE_CARD_PLC = 2;

    private CdBossArt() { }

    /** Level-start VRAM after {@code plcs} in order (usually {@link #TITLE_CARD_PLC} then the boss's cue). */
    public static CdStageVram vramAfter(CdObjectArtProvider art, int... plcs) throws IOException {
        int plcLists = CdStageCatalogue.entry(art.payload()).plcLists();
        CdStageVram vram = art.vram().copy();
        for (int plc : plcs) vram.loadPlc(art.image(), plcLists, plc);
        return vram;
    }

    /** Registers the mapping table at stage-file offset {@code table} over {@code vram} at {@code tileWord}. */
    public static void register(CdObjectArtProvider art, String key, int table, int tileWord, CdStageVram vram) {
        if (table >= 0) art.register(key, art.sheet(art.image().reader(), table, tileWord, vram));
    }

    /** The renderer key of a zone's boss sheet. */
    public static String key(String zone, String kind) {
        return "cd.boss." + zone + "." + kind;
    }
}
