package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.boss.CdBossArt;

import java.io.IOException;

/**
 * The Metallic Madness boss's sheets on R83C/R83D. The boss art arrives with PLC 4, which Eggman's intro queues
 * ({@code moveq #4,d0; jsr AddGfxQueue}, EggmanObject_0_Routine4, objects/r8/boss.asm:329-330): the panels at tile
 * $220, $2D9 and $3BF and the machine at $3DE. Amy's art is PLC 5, queued at the third hit (loc_20B378,
 * objects/r8/boss.asm:158-160), over the first panel art at $220. The Hotaru and the boss door come with the title
 * card's PLC 2 (Title Card/Main.asm:145-148); the door's art is level-start VRAM. A sheet is a window into
 * level-start VRAM plus those cues in order; the decode queue is not modelled (the sheets exist from the stage
 * load and nothing draws them before the cue is queued).
 */
final class MmzBossArt {
    static final String EGGMAN = "eggman", EGG_MOBILE = "eggmobile", SPARKS = "sparks", AMY = "amy",
            BOSS_DOOR = "bossdoor", HOTARU = "hotaru", PANEL = "panel";
    /** move.w #$3DE / #$23DE / #$2220 / #$8797 / #$23B0,obj.sprite_tile (boss.asm:271, 1958, 2343;
     *  boss_door.asm:61; hotaru.asm:33). */
    static final int EGGMAN_TILE = 0x3DE, EGG_MOBILE_TILE = 0x23DE, PANEL_TILE = 0x2220, AMY_TILE = 0x2220,
            BOSS_DOOR_TILE = 0x8797, HOTARU_TILE = 0x23B0;
    /** AddGfxQueue #4 (boss) and #5 (Amy): objects/r8/boss.asm:329-330, 158-160. */
    static final int BOSS_PLC = 4, AMY_PLC = 5;
    /** BossPanelSprites1-6: the panel tables (the six entries of BossPanelSprites, loc_20C75E). */
    static final int PANEL_SETS = 6;

    private MmzBossArt() { }

    /** Registers the sheets on R83C/R83D ({@link MmzBossObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        CdStagePayload payload = art.payload();
        if (MmzBossCatalogue.offset(payload, "eggmanMappings") < 0) return;   // not a Metallic Madness boss stage
        CdStageVram titleCard = CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC);
        CdStageVram boss = CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, BOSS_PLC);
        CdStageVram amy = CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, BOSS_PLC, AMY_PLC);
        sheet(art, EGGMAN, "eggmanMappings", EGGMAN_TILE, boss);
        sheet(art, EGG_MOBILE, "eggMobileMappings", EGG_MOBILE_TILE, boss);
        sheet(art, SPARKS, "sparksMappings", EGGMAN_TILE, boss);
        for (int set = 1; set <= PANEL_SETS; set++) {
            sheet(art, PANEL + set, "panelMappings" + set, PANEL_TILE, boss);
            // After PLC 5 the first panel art is Amy's: a panel drawn with it shows Amy's tiles, as on hardware.
            sheet(art, PANEL + set + "a", "panelMappings" + set, PANEL_TILE, amy);
        }
        sheet(art, AMY, "amyMappings", AMY_TILE, amy);
        sheet(art, BOSS_DOOR, "bossDoorMappings", BOSS_DOOR_TILE, titleCard);
        sheet(art, HOTARU, "hotaruMappings", HOTARU_TILE, titleCard);
    }

    private static void sheet(CdObjectArtProvider art, String kind, String column, int tile, CdStageVram vram) {
        CdBossArt.register(art, key(kind), MmzBossCatalogue.offset(art.payload(), column), tile, vram);
    }

    static String key(String kind) {
        return CdBossArt.key("mmz", kind);
    }
}
