package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.level.objects.ObjectServices;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Act 1 Present intro's shared flag, {@code amyCaptured} (objects/r3/intro.asm:204-209: Metal Sonic's exit
 * and every early-out of Amy and the intro spikes set or read it). The ROM keeps it in work RAM; it is cleared
 * by the program before R31 runs (CdIpxProgress.runR3AfterSaveReturned, Store.AMY_CAPTURED_CLEAR). It lives here, per
 * loaded stage, because no level-state owner holds it yet: the Amy Rose lane reads and writes the same flag
 * through this class until a shared home exists.
 */
public final class CczIntroState {
    private static final Map<CdLevel, CczIntroState> STATES = new WeakHashMap<>();

    private boolean amyCaptured;

    /**
     * {@code AmyRoseLoadPalette} (objects/r3/intro.asm:478-496): sixteen words of {@code AmyRosePalette} (stage file) into palette
     * line 1, every frame Amy's init runs.
     */
    static void loadAmyPalette(ObjectServices services, CdLevel level, CdObjectArtProvider art) {
        int table = CczObjectCatalogue.offset(level.payload(), "amyRosePalette");
        if (table < 0 || art == null) return;
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) bytes[i] = (byte) art.image().u8(table + i);
        services.updatePalette(1, bytes);
    }

    private CczIntroState() { }

    /** The flag holder of {@code level}, created on first use. */
    public static synchronized CczIntroState of(CdLevel level) {
        return STATES.computeIfAbsent(level, l -> new CczIntroState());
    }

    /** {@code amy_captured}. */
    public boolean amyCaptured() { return amyCaptured; }

    /** MetalSonicCaptureAmy (objects/r3/intro.asm:204-208). */
    void captureAmy(ObjectServices services, CdLevel level, CdObjectArtProvider art) {
        amyCaptured = true;
        // PaletteTable (Level/Collision Chaos/Palette Data (Act 1 Present).asm:24-26) points to StagePalette.
        int table = CdStageCatalogue.entry(level.payload()).paletteTable();
        services.updatePalette(1, art.image().paletteEntryFirstLine(table, 4));
    }
}
