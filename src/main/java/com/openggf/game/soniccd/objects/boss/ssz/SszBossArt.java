package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.boss.CdBossArt;

import java.io.IOException;

/**
 * The Stardust Speedway race's sheets on R73C/R73D. The title card's PLC 2 (Title Card/Main.asm:145-148) is the act
 * 3 section list here, {@code SectionGfxInitList0}: EggmanGfx at $6000 (tile $300) and MetalSonicGfx at $6F80 (tile
 * $37C) (r7/gfx_lists_3c.asm:5-24,52-55). The laser and exhaust use Eggman's tiles on palette line 1 ($2300); the
 * laser's impact the explosion's ($680, ExplosionGfx at $D000), the falling debris the lamp's ($451C, LampGfxC at
 * $A380), the blocks BlockGfxC ($450C, $A180) and the spikes SpikesV4Gfx ($4FC, $9F80), all from the level-start
 * list (r7/gfx_lists_3c.asm:30-44). Amy and the heart ($3CB) arrive with PLC 4, AmyRoseGfx at $7960
 * (r7/gfx_lists_3c.asm:66-69), which Eggman queues after the race is won ({@code moveq #4,d0; jsr AddGfxQueue},
 * objects/r7/boss.asm:1083-1084). The decode queue is not modelled: the sheets exist from the stage load.
 */
public final class SszBossArt {
    static final String EGGMAN = "eggman", METAL = "metal", SHIELD = "shield", LASER = "laser",
            IMPACT = "impact", EXHAUST = "exhaust", METAL_DEBRIS = "metalDebris", DEBRIS = "debris", BLOCK = "block",
            SPIKES = "spikes", AMY = "amy", AMY_EARLY = "amyEarly", HEART = "heart";
    /** {@code AddGfxQueue} cue that brings Amy (objects/r7/boss.asm:1083-1084). */
    static final int AMY_PLC = 4;

    private SszBossArt() { }

    /** Registers the sheets on R73C/R73D ({@link SszBossObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        CdStagePayload payload = art.payload();
        if (SszBossCatalogue.offset(payload, "eggmanMappings") < 0) return;   // not a Stardust Speedway act 3 stage
        CdStageVram vram = CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC);
        CdStageVram amyVram = CdBossArt.vramAfter(art, CdBossArt.TITLE_CARD_PLC, AMY_PLC);
        // move.w #tile,obj.sprite_tile at each init (objects/r7/boss.asm:776, 37, 637, 1484, 1496, 1687, 1758, 1631;
        // boss_block.asm:9; spikes.asm:22; amy_rose.asm:35, 548).
        sheet(art, vram, EGGMAN, "eggmanMappings", 0x300);
        sheet(art, vram, METAL, "metalMappings", 0x37C);
        sheet(art, vram, SHIELD, "shieldMappings", 0x37C);
        sheet(art, vram, LASER, "laserMappings", 0x2300);
        sheet(art, vram, IMPACT, "laserImpactMappings", 0x680);
        sheet(art, vram, EXHAUST, "exhaustMappings", 0x2300);
        sheet(art, vram, METAL_DEBRIS, "metalDebrisMappings", 0x37C);
        sheet(art, vram, DEBRIS, "debrisMappings", 0x451C);
        sheet(art, vram, BLOCK, "blockMappings", 0x450C);
        sheet(art, vram, SPIKES, "spikesMappings", 0x4FC);
        sheet(art, amyVram, AMY, "amyMappings", 0xA3CB);
        // Before Eggman queues PLC 4, Amy is drawn over whatever the level-start lists left at tile $3CB.
        sheet(art, vram, AMY_EARLY, "amyMappings", 0xA3CB);
        sheet(art, amyVram, HEART, "amyMappings", 0x3CB);
    }

    private static void sheet(CdObjectArtProvider art, CdStageVram vram, String kind, String column, int tile) {
        CdBossArt.register(art, key(kind), SszBossCatalogue.offset(art.payload(), column), tile, vram);
    }

    static String key(String kind) {
        return CdBossArt.key("ssz", kind);
    }
}
