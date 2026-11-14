package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.boss.CdBossArt;

import java.io.IOException;

/**
 * The Palmtree Panic boss's sheets on R13C/R13D. Its art arrives with PLC 4, which Eggman's init queues
 * ({@code moveq #4,d0; jsr AddGfxQueue}, objects/r1/boss.asm:307-309): the capsule at tile $481, the body
 * at $359 and Eggman at $3FD (the stage's PLCLists entry 4). The title card's PLC 2 (Title Card/Main.asm:145-148)
 * was decoded before, so a sheet is a window into level-start VRAM + PLC 2 + PLC 4. The decode queue is not
 * modelled: the sheets exist from the stage load, and nothing draws them before PLC 4 is queued.
 */
public final class PpzBossArt {
    static final String EGGMAN = "eggman", BODY = "body", THIGH = "thigh", CALF = "calf", FOOT = "foot",
            SHOULDER = "shoulders", ARM = "arm", FOREARM1 = "forearm1", FOREARM2 = "forearm2", BUMPER = "bumper";
    /** move.w #$3FD / #$359 / #$2359,obj.sprite_tile (objects/r1/boss.asm:317, 729, 1361). */
    static final int EGGMAN_TILE = 0x3FD, BODY_TILE = 0x359, PART_TILE = 0x2359;

    private PpzBossArt() { }

    /** VRAM after the level-start cues, PLC 2 and PLC 4 (shared by the capsule's sheet). */
    public static CdStageVram bossVram(CdObjectArtProvider art) throws IOException {
        return CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, PpzBossEggman.BOSS_PLC);
    }

    /** Registers the sheets on R13C/R13D ({@link PpzBossObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        CdStagePayload payload = art.payload();
        if (PpzBossCatalogue.offset(payload, "eggmanMappings") < 0) return;   // not a Palmtree Panic boss stage
        CdStageVram vram = bossVram(art);
        var reader = art.image().reader();
        sheet(art, reader, vram, EGGMAN, "eggmanMappings", EGGMAN_TILE);
        sheet(art, reader, vram, BODY, "bodyMappings", BODY_TILE);
        sheet(art, reader, vram, THIGH, "thighMappings", PART_TILE);
        sheet(art, reader, vram, CALF, "calfMappings", PART_TILE);
        sheet(art, reader, vram, FOOT, "footMappings", PART_TILE);
        sheet(art, reader, vram, SHOULDER, "shoulderMappings", PART_TILE);
        sheet(art, reader, vram, ARM, "armMappings", PART_TILE);
        sheet(art, reader, vram, FOREARM1, "forearmMappings1", PART_TILE);
        sheet(art, reader, vram, FOREARM2, "forearmMappings2", PART_TILE);
        sheet(art, reader, vram, BUMPER, "bumperMappings", PART_TILE);
    }

    private static void sheet(CdObjectArtProvider art, com.openggf.data.RomByteReader reader, CdStageVram vram,
                              String kind, String column, int tile) {
        CdBossArt.register(art, key(kind), PpzBossCatalogue.offset(art.payload(), column), tile, vram);
    }

    static String key(String kind) {
        return CdBossArt.key("ppz", kind);
    }

    /** The renderer key of a sheet, or {@code null} before art is loaded. */
    static String key(CdObjectArtProvider art, String kind) {
        return art == null ? null : key(kind);
    }
}
