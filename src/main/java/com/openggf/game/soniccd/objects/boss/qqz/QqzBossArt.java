package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.boss.CdBossArt;

import java.io.IOException;

/**
 * The Quartz Quadrant boss's sheets on R53C/R53D. The boss art arrives with PLC 4, which the spike wall queues once
 * the player passes X $C50 ({@code moveq #4,d0; jsr AddGfxQueue}, objects/r5/boss.asm:1090-1093), after the title
 * card's PLC 2 (Title Card/Main.asm:145-148): a sheet is a window into level-start VRAM + PLC 2 + PLC 4. The decode
 * queue is not modelled: the sheets exist from the stage load, and nothing draws them before PLC 4 is queued.
 */
public final class QqzBossArt {
    static final String EGGMAN = "eggman", TOP = "top", BOOTH = "booth", BOOTH_BACK = "boothBack", PIPES = "pipes",
            DROPPER = "dropper", BOMB = "bomb", SPARKS = "sparks", SPIKE_WALL = "spikeWall", EXPLOSION = "explosion";
    /** move.w #$2BC / #$22BC / #$42BC / #$680,obj.sprite_tile (objects/r5/boss.asm:31, 124, 1077, 1517). */
    static final int EGGMAN_TILE = 0x2BC, PART_TILE = 0x22BC, SPIKE_WALL_TILE = 0x42BC, EXPLOSION_TILE = 0x680;
    /** SpikeWallObject_0_Routine2: moveq #4,d0; jsr AddGfxQueue (objects/r5/boss.asm:1090-1092). */
    static final int BOSS_PLC = 4;

    private QqzBossArt() { }

    /** VRAM after the level-start cues, PLC 2 and PLC 4 (shared by the capsule's sheet). */
    public static CdStageVram bossVram(CdObjectArtProvider art) throws IOException {
        return CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, BOSS_PLC);
    }

    /** Registers the sheets on R53C/R53D ({@link QqzBossObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        CdStagePayload payload = art.payload();
        if (QqzBossCatalogue.offset(payload, "boothMappings") < 0) return;   // not a Quartz Quadrant boss stage
        CdStageVram vram = bossVram(art);
        sheet(art, vram, EGGMAN, "eggmanMappings", EGGMAN_TILE);
        sheet(art, vram, TOP, "topMappings", PART_TILE);
        sheet(art, vram, BOOTH, "boothMappings", PART_TILE);
        sheet(art, vram, BOOTH_BACK, "boothBackMappings", PART_TILE);
        sheet(art, vram, PIPES, "pipesMappings", PART_TILE);
        sheet(art, vram, DROPPER, "dropperMappings", EGGMAN_TILE);
        sheet(art, vram, BOMB, "bombMappings", PART_TILE);
        sheet(art, vram, SPARKS, "sparksMappings", PART_TILE);
        sheet(art, vram, SPIKE_WALL, "spikeWallMappings", SPIKE_WALL_TILE);
        sheet(art, vram, EXPLOSION, "explosionMappings", EXPLOSION_TILE);
    }

    private static void sheet(CdObjectArtProvider art, CdStageVram vram, String kind, String column, int tile) {
        CdBossArt.register(art, key(kind), QqzBossCatalogue.offset(art.payload(), column), tile, vram);
    }

    static String key(String kind) {
        return CdBossArt.key("qqz", kind);
    }
}
