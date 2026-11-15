package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.boss.CdBossArt;

import java.io.IOException;

/**
 * The Wacky Workbench boss's sheets on R63C/R63D. Eggman's init queues cue $13 ({@code moveq #$13,d0; jsr AddGfxQueue},
 * objects/r6/boss.asm:122-123): R6BossGfxList, the machine at tile $300, Eggman at $3EC and the floor art at $440
 * (r6/gfx_lists_3c.asm:79-84, gfx_lists_3d.asm:79-84; the floor art differs between the futures). The goal post queued
 * cue $12 before it (the capsule at $481, SignpostGfxList, gfx_lists_3c.asm:73-76; GoalMain, objects/stage_end.asm:446-466),
 * and the conductors' and the electricity's tiles ($458, $470) come with the level's section list (BossPiecesGfx,
 * gfx_lists_3c.asm:53-66). At the defeat Eggman queues cue $14 (R6BossEscapeGfxList: the escape pod over the machine at
 * $300 and the broken machine at $393, gfx_lists_3c.asm:86-90; EggmanDefeatSequenceSpawnCapsule, objects/r6/boss.asm:
 * 541-553), so the pod's and the broken machine's sheets are windows into VRAM after cue $14 as well. The decode queue is
 * not modelled: the sheets exist from the stage load.
 */
final class WwzBossArt {
    static final String EGGMAN = "eggman", POD = "pod", MACHINE = "machine", BROKEN_MACHINE = "brokenMachine",
            SPIKES = "spikes", EXHAUST = "exhaust", FALL_SPIKE = "fallSpike", SMOKE = "smoke", CONDUCTOR1 = "conductor1",
            CONDUCTOR2 = "conductor2", DEBRIS1 = "debris1", DEBRIS2 = "debris2", FLOOR_PIECE = "floorPiece",
            ELECTRICITY = "electricity";
    /** The cues: R6BossGfxList ($13), R6BossEscapeGfxList ($14), SignpostGfxList ($12). */
    static final int BOSS_PLC = 0x13, ESCAPE_PLC = 0x14, CAPSULE_PLC = 0x12;
    /** move.w #$3EC / #$300 / #$2300 / #$2393 / #$4458 / #$4440 / #$440 / #$4470,obj.sprite_tile (objects/r6/boss.asm:126, 547, 677, 694, 1413, 1039, 1127, 1080). */
    static final int EGGMAN_TILE = 0x3EC, POD_TILE = 0x300, PART_TILE = 0x2300, BROKEN_TILE = 0x2393,
            CONDUCTOR_TILE = 0x4458, DEBRIS_TILE = 0x4440, FLOOR_PIECE_TILE = 0x440, ELECTRICITY_TILE = 0x4470;

    private WwzBossArt() { }

    /** VRAM after the level-start cues, the title card's, the goal post's and Eggman's. */
    static CdStageVram bossVram(CdObjectArtProvider art) throws IOException {
        return CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, CAPSULE_PLC, BOSS_PLC);
    }

    /** Registers the sheets on R63C/R63D ({@link WwzBossObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        CdStagePayload payload = art.payload();
        if (WwzBossCatalogue.offset(payload, "eggmanMappings") < 0) return;   // not a Wacky Workbench boss stage
        CdStageVram vram = bossVram(art);
        CdStageVram escape = CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, CAPSULE_PLC, BOSS_PLC, ESCAPE_PLC);
        sheet(art, vram, EGGMAN, "eggmanMappings", EGGMAN_TILE);
        sheet(art, escape, POD, "eggmanMappings", POD_TILE);
        sheet(art, vram, MACHINE, "machineMappings", PART_TILE);
        sheet(art, escape, BROKEN_MACHINE, "machineMappings", BROKEN_TILE);
        sheet(art, vram, SPIKES, "spikesMappings", PART_TILE);
        sheet(art, vram, EXHAUST, "exhaustMappings", PART_TILE);
        sheet(art, vram, FALL_SPIKE, "fallSpikeMappings", PART_TILE);
        sheet(art, vram, SMOKE, "smokeMappings", PART_TILE);
        sheet(art, vram, CONDUCTOR1, "conductorMappings1", CONDUCTOR_TILE);
        sheet(art, vram, CONDUCTOR2, "conductorMappings2", CONDUCTOR_TILE);
        sheet(art, vram, DEBRIS1, "debrisMappings1", DEBRIS_TILE);
        sheet(art, vram, DEBRIS2, "debrisMappings2", DEBRIS_TILE);
        sheet(art, vram, FLOOR_PIECE, "floorPieceMappings", FLOOR_PIECE_TILE);
        sheet(art, vram, ELECTRICITY, "electricityMappings", ELECTRICITY_TILE);
    }

    private static void sheet(CdObjectArtProvider art, CdStageVram vram, String kind, String column, int tile) {
        CdBossArt.register(art, key(kind), WwzBossCatalogue.offset(art.payload(), column), tile, vram);
    }

    static String key(String kind) {
        return CdBossArt.key("wwz", kind);
    }
}
