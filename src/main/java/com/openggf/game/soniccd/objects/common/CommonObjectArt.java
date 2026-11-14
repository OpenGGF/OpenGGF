package com.openggf.game.soniccd.objects.common;

import com.openggf.data.RomByteReader;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;

import java.io.IOException;
import java.util.List;

/**
 * Sheets of the second common-object batch, registered by the common fragment on every load
 * (CdObjectRegistration.registerArt). Table offsets come from the common catalogue
 * ({@code object-catalogue-common.tsv}).
 */
public final class CommonObjectArt {
    private CommonObjectArt() { }

    public static final String BOULDER = "cd.boulder.";
    public static final String FLOAT_BLOCK = "cd.floatBlock.";
    public static final String CHECKPOINT = "cd.checkpoint";
    public static final String FLOWER = "cd.flower";
    public static final String POWERUP = "cd.powerup";
    /** {@code Art_TimeStars}: ShieldArtIndex entry 2 (Powerup/Main.asm:181-184) under MapSpr_Powerup. */
    public static final String TIME_STARS = "cd.timeStars";
    public static final String TEST_BADNIK = "cd.testBadnik";
    /** SetObjectTileID IDs of the boulder and floating block (Boulder/Main.asm:34-35; Floating Block/Main.asm:43-44). */
    public static final int TILE_ID_BOULDER = 0x0B;
    public static final int TILE_ID_FLOAT_BLOCK = 0x05;
    /** ObjCheckpoint_Init: move.w #$6CB,oTile(a0) (Checkpoint/Main.asm:75). */
    public static final int CHECKPOINT_TILE = 0x06CB;
    /** ObjFlower_Init: move.w #$A6D7,oTile(a0) (Flower/Main.asm:36). */
    public static final int FLOWER_TILE = 0xA6D7;
    /** ObjPowerup_Init: move.w #$544,oTile(a0) (Powerup/Main.asm:27). */
    public static final int POWERUP_TILE = 0x0544;
    /** ObjTestBadnik_Init: move.w #$541,oTile(a0) (Test Badnik/Main.asm:27-36). */
    public static final int TEST_BADNIK_TILE = 0x0541;

    /** The common objects' sheets beyond the level-start set, registered by {@link CommonObjects}. */
    static void register(CdObjectArtProvider art) throws IOException {
        RomByteReader reader = art.image().reader();
        for (int tile : art.objectTileIds(TILE_ID_BOULDER)) {
            art.register(BOULDER + tile, art.sheet(reader, art.entry().offset("boulderMappings"), tile));
        }
        if (art.entry().offset("floatBlockMappings") >= 0) {
            for (int tile : art.objectTileIds(TILE_ID_FLOAT_BLOCK)) {
                art.register(FLOAT_BLOCK + tile, art.sheet(reader, art.entry().offset("floatBlockMappings"), tile));
            }
        }
        art.register(CHECKPOINT, art.sheet(reader, art.entry().offset("checkpointMappings"), CHECKPOINT_TILE));
        if (art.entry().offset("flowerMappings") >= 0) {
            art.register(FLOWER, art.sheet(reader, art.entry().offset("flowerMappings"), FLOWER_TILE));
        }
        art.register(POWERUP, art.sheet(reader, art.entry().offset("powerupMappings"), POWERUP_TILE));
        art.register(TEST_BADNIK, art.sheet(reader, art.entry().offset("powerupMappings"), TEST_BADNIK_TILE));
        // LoadShieldArt copies $100 longs of ShieldArtIndex[powerup/2 - 1] to VRAM $A880 (tile $544)
        // (Powerup/Main.asm:157-176); the shared shield and stars owners draw these sheets.
        art.register(com.openggf.level.objects.ObjectArtKeys.SHIELD, powerupSheet(art, reader, 0));
        art.register(com.openggf.level.objects.ObjectArtKeys.INVINCIBILITY_STARS, powerupSheet(art, reader, 1));
        art.register(TIME_STARS, powerupSheet(art, reader, 2));
        com.openggf.game.soniccd.objects.projector.ProjectorArt.register(art);    // Metal Sonic's projector (its own package)
        registerLevelEnd(art, reader);
        int generatorTiles = art.entry().offset("robotGeneratorBaseTiles");
        if (generatorTiles >= 0 && art.entry().offset("robotGeneratorMappings") >= 0) {
            art.register(ROBOT_GENERATOR, art.sheet(reader, art.entry().offset("robotGeneratorMappings"),
                    robotGeneratorTile(art.image(), art.payload(), generatorTiles)));
        }
    }

    public static final String ROBOT_GENERATOR = "cd.robotGenerator";
    public static final String GOAL_POST = "cd.goalPost";
    public static final String SIGNPOST = "cd.signpost";
    public static final String BIG_RING = "cd.bigRing";
    public static final String BIG_RING_FLASH = "cd.bigRing.flash";
    /** ObjBigRingFlash_Init: move.w #$3EF,oTile(a0) (Level End/Main.asm:404-407). */
    public static final int BIG_RING_FLASH_TILE = 0x03EF;
    public static final String RESULTS_BAD = "cd.results.bad";
    public static final String RESULTS_GOOD = "cd.results.good";
    /** Stardust Speedway act 3's own results mappings (MapSpr_ResultsBadSSZ3/GoodSSZ3, Results/Main.asm:116-124). */
    public static final String RESULTS_BAD_SSZ3 = "cd.results.bad.ssz3";
    public static final String RESULTS_GOOD_SSZ3 = "cd.results.good.ssz3";
    public static final String GAME_OVER = "cd.gameOver";
    public static final String TIME_OVER = "cd.timeOver";
    /** ObjSignpost_Init: move.w #$43C,oTile(a0) (Level End/Main.asm:739-746). */
    public static final int SIGNPOST_TILE = 0x043C;
    /** ObjBigRing_Init: move.w #$2488,oTile(a0) (Level End/Main.asm:465-478). */
    public static final int BIG_RING_TILE = 0x2488;
    /** ObjResults .InitLoop: move.w #$83C4,oTile(a1) (Results/Main.asm:106-114). */
    public static final int RESULTS_TILE = 0x83C4;
    /**
     * ObjResults .InitLoop in Stardust Speedway act 3 ({@code cmpi.w #$502,zoneAct}): {@code move.w #$82F2,oTile(a1)}
     * (Results/Main.asm:116-119); that act's results art loads at $5E40 (ResultsGfxList, r7/gfx_lists_3c.asm:55-58).
     */
    public static final int RESULTS_TILE_SSZ3 = 0x82F2;
    /** ObjGameOver_Init: move.w #$8544,oTile(a0) (Game Over/Main.asm:35). */
    public static final int GAME_OVER_TILE = 0x8544;
    /** ObjGoalPost_Main: moveq #$12,d0; jmp LoadPLC (Level End/Main.asm:626-648). */
    private static final int SIGNPOST_PLC = 0x12;
    /** ObjResults_Init: moveq #$10,d0; jsr LoadPLC (Results/Main.asm:61-68). */
    private static final int RESULTS_PLC = 0x10;

    /**
     * Level-end sheets. The goal post's tile is in the level's art; the signpost and big ring
     * draw from PLC $12 and the results from PLC $10, which their owners queue at the level end
     * over tiles the level no longer needs, so those sheets read a VRAM copy with both decoded.
     * GAME OVER and TIME OVER are ShieldArtIndex entries 3 and 4 at tile $544 (Powerup/Main.asm:181-186).
     */
    private static void registerLevelEnd(CdObjectArtProvider art, RomByteReader reader) throws IOException {
        int goalTiles = art.entry().offset("goalPostTileIds");
        int signpostMaps = art.entry().offset("goalSignpostMappings");
        if (goalTiles >= 0 && signpostMaps >= 0) {
            art.register(GOAL_POST, art.sheet(reader, signpostMaps, goalPostTile(art.image(), art.payload(), goalTiles,
                    art.payload().stage().period() == com.openggf.game.soniccd.CdPeriod.GOOD_FUTURE)));
        }
        var stageEntry = CdStageCatalogue.entry(art.payload());
        CdStageVram end = art.vram().copy();
        // Act 3 stages have no goal post: their PLC index may end before $12 or $10.
        for (int plc : new int[] {SIGNPOST_PLC, RESULTS_PLC}) {
            if (CdStageVram.hasPlc(art.image(), stageEntry.plcLists(), plc)) end.loadPlc(art.image(), stageEntry.plcLists(), plc);
        }
        if (signpostMaps >= 0) art.register(SIGNPOST, art.sheet(reader, signpostMaps, SIGNPOST_TILE, end));
        art.register(BIG_RING, art.sheet(reader, art.entry().offset("bigRingMappings"), BIG_RING_TILE, end));
        if (art.entry().offset("bigRingFlashMappings") >= 0) {
            art.register(BIG_RING_FLASH, art.sheet(reader, art.entry().offset("bigRingFlashMappings"), BIG_RING_FLASH_TILE, end));
        }
        if (art.entry().offset("resultsBadMappings") >= 0) {
            art.register(RESULTS_BAD, art.sheet(reader, art.entry().offset("resultsBadMappings"), RESULTS_TILE, end));
            art.register(RESULTS_GOOD, art.sheet(reader, art.entry().offset("resultsGoodMappings"), RESULTS_TILE, end));
        }
        if (art.entry().offset("resultsBadSsz3Mappings") >= 0) {
            art.register(RESULTS_BAD_SSZ3, art.sheet(reader, art.entry().offset("resultsBadSsz3Mappings"),
                    RESULTS_TILE_SSZ3, end));
            art.register(RESULTS_GOOD_SSZ3, art.sheet(reader, art.entry().offset("resultsGoodSsz3Mappings"),
                    RESULTS_TILE_SSZ3, end));
        }
        art.register(GAME_OVER, artIndexSheet(art, reader, art.entry().offset("gameOverMappings"), 3, GAME_OVER_TILE));
        art.register(TIME_OVER, artIndexSheet(art, reader, art.entry().offset("timeOverMappings"), 4, GAME_OVER_TILE));
    }

    /** ObjGoalPost_SetBaseTile (Level End/Main.asm:664-703): zone*16 + (act & 1)*8 + 2*time zone. */
    public static int goalPostTile(CdStageImage stage, CdStagePayload payload, int table, boolean goodFuture) {
        int zone = payload.stage().zone().ordinal();
        int act = payload.stage().actNumber() - 1;
        int timeZone = CdLevel.nativeTimeZone(payload.stage().period());
        if (timeZone == 2 && goodFuture) timeZone++;                 // add.b goodFuture,d1
        int tile = stage.u16(table + zone * 16 + (act & 1) * 8 + timeZone * 2);
        return zone == 3 ? tile : tile | 0x8000;                    // Quartz Quadrant keeps low priority
    }

    private static ObjectSpriteSheet artIndexSheet(CdObjectArtProvider art, RomByteReader reader, int mappings, int index, int tileWord) throws IOException {
        int bytes = art.image().offsetOf(art.image().u32(art.entry().offset("shieldArtIndex") + index * 4));
        CdStageVram copy = art.vram().copy();
        copy.write(tileWord & 0x7FF, art.image().slice(bytes, SHIELD_ART_BYTES));
        return art.sheet(reader, mappings, tileWord, copy);
    }
    /** dbf d0 from $FF: $100 longs (Powerup/Main.asm:167-171). */
    private static final int SHIELD_ART_BYTES = 0x400;

    /** MapSpr_Powerup over a VRAM copy holding ShieldArtIndex entry {@code index} at tile $544. */
    private static ObjectSpriteSheet powerupSheet(CdObjectArtProvider art, RomByteReader reader, int index) throws IOException {
        int bytes = art.image().offsetOf(art.image().u32(art.entry().offset("shieldArtIndex") + index * 4));
        CdStageVram copy = art.vram().copy();
        copy.write(POWERUP_TILE, art.image().slice(bytes, SHIELD_ART_BYTES));
        List<SpriteMappingFrame> frames = CdObjectArtProvider.frames(reader, art.entry().offset("powerupMappings"));
        return new ObjectSpriteSheet(copy.window(POWERUP_TILE & 0x7FF, CdObjectArtProvider.span(frames)), frames, CdObjectArtProvider.tilePalette(POWERUP_TILE), 1);
    }

    /** ObjRobotGenerator_Init: act*4 + timeZone words into the base tile list (Robot Generator/Main.asm:56-62). */
    public static int robotGeneratorTile(CdStageImage stage, CdStagePayload payload, int table) {
        int act = payload.stage().actNumber() - 1;
        int timeZone = CdLevel.nativeTimeZone(payload.stage().period());
        return stage.u16(table + ((act << 2) + timeZone) * 2);
    }

    public static String boulderKey(int tileWord) { return BOULDER + (tileWord & 0xFFFF); }
    public static String floatBlockKey(int tileWord) { return FLOAT_BLOCK + (tileWord & 0xFFFF); }
}
