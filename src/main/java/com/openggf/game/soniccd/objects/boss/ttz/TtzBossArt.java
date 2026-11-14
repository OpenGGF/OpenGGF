package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.boss.CdBossArt;

import java.io.IOException;

/**
 * The Tidal Tempest boss's sheets on R43C/R43D (gfx_lists_3c/3d.asm). The title card's PLC 2 ({@code SectionGfxInitList0})
 * brings the chase art ({@code BossChaseGfx}) to tile $31E and the door's block art to tile $46A; PLC 4
 * ({@code word_20D2C2}), which the falling Egg Mobile queues ({@code moveq #4,d0; jsr AddGfxQueue},
 * objects/r4/boss_chase.asm:679-680), replaces tile $31E with the boss art ({@code BossBubbleGfx}); the goal's
 * PLC $12 ({@code SignpostGfxList}) brings the capsule to tile $481. So the chase and door sheets are windows into
 * level-start VRAM + PLC 2, the boss's into VRAM + PLC 2 + PLC 4, the capsule's into VRAM + PLC 2 + PLC 4 + PLC $12.
 * The decode queue is not modelled: the sheets exist from the stage load, and nothing draws them before the cue.
 */
public final class TtzBossArt {
    static final String DOOR = "door", CHASE_MOBILE = "chaseMobile", CHASE_EGGMAN = "chaseEggman", EGGMAN = "eggman",
            EGG_MOBILE = "eggMobile", MISSILE = "missile", BUBBLE = "bubble";
    /** BossDoorObject_0_Routine0: move.w #$446A,obj.sprite_tile (objects/r4/boss_door.asm:29). */
    static final int DOOR_TILE = 0x446A;
    /** move.w #$31E,obj.sprite_tile in every chase and boss init (e.g. objects/r4/boss_chase.asm:44). */
    static final int BOSS_TILE = 0x31E;
    /** EggMobileChaseObject_0_Routine4: moveq #4,d0; jsr AddGfxQueue (objects/r4/boss_chase.asm:679-680). */
    static final int BOSS_PLC = 4;
    /** GoalMain: moveq #$12,d0; jmp AddGfxQueue (objects/stage_end.asm:440-458), the capsule's art. */
    static final int CAPSULE_PLC = 0x12;

    private TtzBossArt() { }

    /** Registers the sheets on R43C/R43D ({@link TtzBossObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        CdStagePayload payload = art.payload();
        if (TtzBossCatalogue.offset(payload, "eggmanMappings") < 0) return;   // not a Tidal Tempest boss stage
        CdStageVram chase = CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC);
        CdStageVram boss = CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, BOSS_PLC);
        sheet(art, chase, DOOR, "bossDoorMappings", DOOR_TILE);
        sheet(art, chase, CHASE_MOBILE, "chaseMobileMappings", BOSS_TILE);
        sheet(art, chase, CHASE_EGGMAN, "chaseEggmanMappings", BOSS_TILE);
        sheet(art, boss, EGGMAN, "eggmanMappings", BOSS_TILE);
        sheet(art, boss, EGG_MOBILE, "eggMobileMappings", BOSS_TILE);
        sheet(art, boss, MISSILE, "missileMappings", BOSS_TILE);
        sheet(art, boss, BUBBLE, "bubbleMappings", BOSS_TILE);
    }

    /** VRAM after PLC 2, PLC 4 and PLC $12: the capsule's sheet. */
    static CdStageVram capsuleVram(CdObjectArtProvider art) throws IOException {
        return CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, BOSS_PLC, CAPSULE_PLC);
    }

    private static void sheet(CdObjectArtProvider art, CdStageVram vram, String kind, String column, int tile) {
        CdBossArt.register(art, key(kind), TtzBossCatalogue.offset(art.payload(), column), tile, vram);
    }

    static String key(String kind) {
        return CdBossArt.key("ttz", kind);
    }
}
