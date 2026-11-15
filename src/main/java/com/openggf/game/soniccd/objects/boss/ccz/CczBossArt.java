package com.openggf.game.soniccd.objects.boss.ccz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.boss.CdBossArt;

import java.io.IOException;

/**
 * The Collision Chaos boss's sheets on R33C/R33D. Act 3's PLC 2, the title card's cue (Title Card/Main.asm:145-148),
 * is {@code PLC_Boss} (Collision Chaos/Graphics Lists (Act 3 Variant).asm:13-89): Eggman, the machine, the gears
 * and the entry barrier at $63C0 (tile $31E), the exit barrier at $8E80 ($474) and the bomb launcher and bombs at
 * $8F80 ($47C). The first hit queues PLC 4, {@code PLC_BossEscape} (:91-96): Eggman's escape art at $7F80 ($3FC)
 * and the capsule at $9020 (tile $481). The bomb's explosion uses the explosion art of the level-start
 * {@code PLC_Std} at $D000 ($680). The decode queue is not modelled: the sheets exist from the stage load and nothing
 * draws them before their cue is queued.
 */
public final class CczBossArt {
    static final String ENTRY = "entry", MACHINE = "machine", EGGMAN = "eggman", EGGMAN_ESCAPE = "eggmanEscape",
            GEAR = "gear", LAUNCHER = "launcher", BOMB = "bomb", EXPLOSION = "explosion", EXIT = "exit";
    /** move.w #$31E,obj.sprite_tile (objects/r3/boss.asm:60, 241, 750, 828). */
    static final int BOSS_TILE = 0x31E;
    /** move.w #$3FC,obj.sprite_tile(a1): Eggman after the last hit (objects/r3/boss.asm:442, 780). */
    static final int ESCAPE_TILE = 0x3FC;
    /** move.w #$47C,obj.sprite_tile (objects/r3/boss.asm:883, 995). */
    static final int BOMB_TILE = 0x47C;
    /** move.w #$680,obj.sprite_tile(a0): the bomb's explosion (objects/r3/boss.asm:1119). */
    static final int EXPLOSION_TILE = 0x680;
    /** move.w #$474,obj.sprite_tile(a0) (objects/r3/boss.asm:1249). */
    static final int EXIT_TILE = 0x474;
    /** BossMachine_HitPhase_LoadArt: moveq #4,d0; jsr AddGfxQueue (objects/r3/boss.asm:418-420). */
    static final int ESCAPE_PLC = 4;

    private CczBossArt() { }

    /** VRAM after the level-start cues and PLC 2 ({@code PLC_Boss}). */
    static CdStageVram bossVram(CdObjectArtProvider art) throws IOException {
        return CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC);
    }

    /** VRAM after PLC 2 and PLC 4 ({@code PLC_BossEscape}): Eggman's escape and the capsule. */
    static CdStageVram escapeVram(CdObjectArtProvider art) throws IOException {
        return CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, ESCAPE_PLC);
    }

    /** True on R33C/R33D. */
    static boolean stage(CdStagePayload payload) {
        return CczBossCatalogue.offset(payload, "machineMappings") >= 0;
    }

    /** Registers the sheets on R33C/R33D ({@link CczBossObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        if (!stage(art.payload())) return;
        CdStageVram vram = bossVram(art);
        CdStageVram escape = escapeVram(art);
        sheet(art, vram, ENTRY, "entryBarrierMappings", BOSS_TILE);
        sheet(art, vram, MACHINE, "machineMappings", BOSS_TILE);
        sheet(art, vram, EGGMAN, "eggmanMappings", BOSS_TILE);
        sheet(art, escape, EGGMAN_ESCAPE, "eggmanMappings", ESCAPE_TILE);
        sheet(art, vram, GEAR, "gearMappings", BOSS_TILE);
        sheet(art, vram, LAUNCHER, "launcherMappings", BOMB_TILE);
        sheet(art, vram, BOMB, "bombMappings", BOMB_TILE);
        sheet(art, vram, EXPLOSION, "bombExplosionMappings", EXPLOSION_TILE);
        sheet(art, vram, EXIT, "exitBarrierMappings", EXIT_TILE);
    }

    private static void sheet(CdObjectArtProvider art, CdStageVram vram, String kind, String column, int tile) {
        CdBossArt.register(art, key(kind), CczBossCatalogue.offset(art.payload(), column), tile, vram);
    }

    static String key(String kind) {
        return CdBossArt.key("ccz", kind);
    }
}
