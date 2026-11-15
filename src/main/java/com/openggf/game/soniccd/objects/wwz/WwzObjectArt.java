package com.openggf.game.soniccd.objects.wwz;

import com.openggf.data.RomByteReader;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.common.CdAnimator;

import java.io.IOException;

/**
 * Wacky Workbench object art, registered by {@link WwzObjects}. Table offsets come from
 * {@link WwzObjectCatalogue}; tile words are the immediates each object's init writes.
 *
 * <p>The sheets window the stage's VRAM after pattern load cue 2: the stage has no camera sections, so
 * its {@code InitSectionGfx} is {@code moveq #2,d0; jmp AddGfxQueue} (r6/section_gfx_1a.asm:3-5,
 * Level/Section Art Load (Null).asm via Act 1 Present.asm:62), and cue 2 is the stage's
 * {@code SectionGfxInitList0} (r6/gfx_lists_2a.asm:45-60: the freezer, piston, snake block, bouncing platform
 * and launcher art). Cue 1 (the badniks) is part of the level-start VRAM (CdObjectArtProvider.loadVram).
 */
public final class WwzObjectArt {
    public static final String LAUNCHER = "launcher", PISTON = "piston", BOUNCE_PLATFORM = "bounce",
            SPIN_PLATFORM = "spin", EGGMAN_STATUE = "statue", SPIKE_BOMB = "spikebomb", BEAMS = "beams",
            FREEZER = "freezer", ICE = "ice", BATA = "bata", BATA_DAMAGED = "bata2", MINOMUSI = "mino",
            MINOMUSI_DAMAGED = "mino2", SEMI = "semi", SEMI_DAMAGED = "semi2", SEMI_BOMB = "semibomb",
            SNAKE_BLOCKS = "snake";

    /** {@code move.w #imm,oTile(a0)} of each object's init. */
    public static final int LAUNCHER_TILE = 0x0400,        // ObjLauncher_Init (Launcher/Main.asm:33)
            PISTON_TILE = 0x0340,                          // ObjPiston_Init (Piston/Main.asm:45)
            BOUNCE_PLATFORM_TILE = 0x43E8,                 // ObjBouncePlatform_Init (Platform/Main (Bounce).asm:41)
            SPIN_PLATFORM_TILE = 0x436A,                   // ObjSpinPlatform_Init (Platform/Main (Spinning).asm:41)
            EGGMAN_STATUE_TILE = 0x44E8,                   // ObjEggmanStatue_Init (Eggman Statue/Main.asm:53)
            SPIKE_BOMB_TILE = 0x04C8,                      // ObjSpikeBomb_Init (Eggman Statue/Main.asm:222)
            BEAMS_TILE = 0x6358,                           // ObjElectricBeams_Init (Electric Beams/Main.asm:38)
            FREEZER_TILE = 0x0310,                         // ObjFreezer_Init (Freezer/Main.asm:37)
            ICE_TILE = 0x02E1,                             // ObjFreezer_FreezeSonic (Freezer/Main.asm:278)
            BATA_TILE = 0x2438,                            // ObjBataPyon_Init (Bata-pyon/Main.asm:43)
            MINOMUSI_TILE = 0x2488,                        // ObjMinomusi_Init (Minomusi/Main.asm:54)
            SEMI_TILE = 0xA4A8,                            // ObjSemi_Init (Semi/Main.asm:48)
            SEMI_BOMB_TILE = 0x84C8,                       // ObjSemiBomb_Init (Semi/Main.asm:238)
            SNAKE_BLOCKS_TILE = 0x03A8;                    // ObjSnakeMain_Init (Snake Blocks/Main.asm:45)

    /** The stage's cue 2 (see the class comment). */
    private static final int SECTION_INIT_PLC = 2;
    private static final String PREFIX = "cd.wwz.";

    private WwzObjectArt() { }

    static void register(CdObjectArtProvider art) throws IOException {
        CdStagePayload payload = art.payload();
        if (!payload.name().startsWith("R6")) return;
        CdStageVram vram = art.vram().copy();
        var stageEntry = CdStageCatalogue.entry(payload);
        if (CdStageVram.hasPlc(art.image(), stageEntry.plcLists(), SECTION_INIT_PLC)) {
            vram.loadPlc(art.image(), stageEntry.plcLists(), SECTION_INIT_PLC);
        }
        RomByteReader reader = art.image().reader();
        sheet(art, reader, vram, LAUNCHER, "launcherMappings", LAUNCHER_TILE);
        sheet(art, reader, vram, PISTON, "pistonMappings", PISTON_TILE);
        sheet(art, reader, vram, BOUNCE_PLATFORM, "bouncePlatformMappings", BOUNCE_PLATFORM_TILE);
        sheet(art, reader, vram, SPIN_PLATFORM, "spinPlatformMappings", SPIN_PLATFORM_TILE);
        sheet(art, reader, vram, EGGMAN_STATUE, "eggmanStatueMappings", EGGMAN_STATUE_TILE);
        sheet(art, reader, vram, SPIKE_BOMB, "spikeBombMappings", SPIKE_BOMB_TILE);
        sheet(art, reader, vram, BEAMS, "beamMappings", BEAMS_TILE);
        sheet(art, reader, vram, FREEZER, "freezerMappings", FREEZER_TILE);
        sheet(art, reader, vram, ICE, "freezerMappings", ICE_TILE);
        sheet(art, reader, vram, BATA, "bataMappings1", BATA_TILE);
        sheet(art, reader, vram, BATA_DAMAGED, "bataMappings2", BATA_TILE);
        sheet(art, reader, vram, MINOMUSI, "minoMappings1", MINOMUSI_TILE);
        sheet(art, reader, vram, MINOMUSI_DAMAGED, "minoMappings2", MINOMUSI_TILE);
        sheet(art, reader, vram, SEMI, "semiMappings1", SEMI_TILE);
        sheet(art, reader, vram, SEMI_DAMAGED, "semiMappings2", SEMI_TILE);
        sheet(art, reader, vram, SEMI_BOMB, "semiBombMappings", SEMI_BOMB_TILE);
        sheet(art, reader, vram, SNAKE_BLOCKS, "snakeMappings", SNAKE_BLOCKS_TILE);
    }

    private static void sheet(CdObjectArtProvider art, RomByteReader reader, CdStageVram vram, String kind,
                              String column, int tileWord) {
        int table = WwzObjectCatalogue.offset(art.payload(), column);
        if (table < 0) return;
        art.register(key(kind), art.sheet(reader, table, tileWord, vram));
    }

    /** The renderer key of a sheet kind. */
    public static String key(String kind) {
        return PREFIX + kind;
    }

    /** An animator over one of the stage's script tables, or {@code null} before art is loaded. */
    public static CdAnimator animator(CdObjectArtProvider art, String column, int anim) {
        if (art == null) return null;
        int table = WwzObjectCatalogue.offset(art.payload(), column);
        return table < 0 ? null : new CdAnimator(art.image(), table, anim);
    }
}
