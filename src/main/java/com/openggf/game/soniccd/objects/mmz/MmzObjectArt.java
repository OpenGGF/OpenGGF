package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Metallic Madness object sheets, registered by {@link MmzObjects#registerArt}. Table offsets come from
 * {@link MmzObjectCatalogue}.
 *
 * <p>These objects' tiles do not arrive with the level PLC: the title card's end loads PLC 2 and each camera section
 * loads its own list ({@code InitSectionGfx}/{@code UpdateSectionGfx}, r8/section_gfx_1.asm:3-50), so a sheet is a
 * window into the VRAM of the section the camera is in. Section k's VRAM is modelled as the level-start VRAM plus section
 * k's init list; the decode queue and the incremental update lists are not modelled ({@code CdStageVram}).
 * Stages whose {@code InitSectionGfx} is a bare {@code rts} (r8/section_gfx_3.asm:6-8) use PLC 2 alone.
 * One sheet is registered per section under {@code kind#section}; objects draw through {@link MmzObject#drawSheet}.
 */
public final class MmzObjectArt {
    /** PLC 2: the title card's slide-out cue (Title Card/Main.asm:145-148). */
    private static final int TITLE_CARD_PLC = 2;

    private final CdStagePayload loaded;
    private final List<CdStageVram> sectionVram = new ArrayList<>();
    private int[] sectionBounds = new int[0];
    private final Map<String, Boolean> registered = new HashMap<>();

    private MmzObjectArt(CdStagePayload loaded) {
        this.loaded = loaded;
    }


    public static final String HV_PLATFORM = "cd.mmz.hvPlatform";
    /** HVPlatformObject_0_Routine0 (objects/r8/hv_platform.asm:27-34): move.w #$41B,obj.sprite_tile. */
    public static final int HV_PLATFORM_TILE = 0x041B;

    public static final String SPIKE_WALL = "cd.mmz.spikeWall";
    /** SpikeWallObject_0_Routine0: move.w #$8440,obj.sprite_tile (objects/r8/spike_wall.asm:18-19). */
    public static final int SPIKE_WALL_TILE = 0x8440;
    public static final String VANISH_PLATFORM = "cd.mmz.vanishPlatform";
    /** sub_20D606: move.w #$398,obj.sprite_tile(a6) (objects/r8/vanish_platform.asm:93). */
    public static final int VANISH_PLATFORM_TILE = 0x0398;

    public static final String MECHA_BU = "cd.mmz.mechaBu";
    public static final String MECHA_BU_SAW = "cd.mmz.mechaBuSaw";
    /** MechaBuObject_0_Routine0 (objects/r8/mecha_bu.asm:29-36) and MechaBuObject_1_Routine0 (objects/r8/mecha_bu.asm:119-126): move.w #$244E,obj.sprite_tile. */
    public static final int MECHA_BU_TILE = 0x244E;

    /** Dango, the shared badnik of act 2: one tile word for every period (objects/r8/dango.asm:41); section art. */
    public static final String DANGO_1 = "cd.mmz.dango1";
    public static final String DANGO_2 = "cd.mmz.dango2";

    public static final String BUZZSAW = "cd.mmz.buzzsaw";
    public static final String BUZZSAW_HANDLE = "cd.mmz.buzzsawHandle";
    /** BuzzsawObject_0_Routine0: move.w #$4316,obj.sprite_tile (objects/r8/buzzsaw.asm:29); the handle copies it (:62). */
    public static final int BUZZSAW_TILE = 0x4316;

    public static final String SPIN_PLATFORM = "cd.mmz.spinPlatform";
    /** sub_20D264: move.w #$34C,obj.sprite_tile(a6) (objects/r8/spin_platform_1.asm:87). */
    public static final int SPIN_PLATFORM_TILE = 0x034C;

    public static final String MOVE_SPRING = "cd.mmz.moveSpring.";
    /** SetObjectTileID index of the moving spring: moveq #$E,d0 (objects/r8/move_spring.asm:30-31). */
    public static final int TILE_ID_MOVE_SPRING = 0x0E;

    public static String moveSpringKey(int tileWord) {
        return MOVE_SPRING + (tileWord & 0xFFFF);
    }

    public static final String TRAP_BLOCK = "cd.mmz.trapBlock";
    public static final String LAYER_PLATFORM = "cd.mmz.layerPlatform";
    private static final String TRAP_SPIKES = "cd.mmz.trapSpikes.";
    /** SpikesObject_1_Routine0: move.w #$43F0,obj.sprite_tile (objects/r8/trap_1.asm:45-46). */
    public static final int TRAP_BLOCK_TILE = 0x43F0;
    /** SpikesObject_0_Routine0: move.w #$488,obj.sprite_tile (objects/r8/trap_1.asm:146-147); trap_2.asm's layered spikes also use $8488 (loc_20D346). */
    public static final int TRAP_SPIKES_TILE = 0x0488;
    public static final int TRAP_SPIKES_LAYER_TILE = 0x8488;
    /** loc_20D408: move.w #$44D8,obj.sprite_tile (objects/r8/trap_2.asm:716-720). */
    public static final int LAYER_PLATFORM_TILE = 0x44D8;

    public static String trapSpikesKey(int tileWord) {
        return TRAP_SPIKES + (tileWord & 0xFFFF);
    }

    public static final String TRAP_DOOR = "cd.mmz.trapDoor";
    /** TrapDoorObject_0_Routine0: move.w #$33C,obj.sprite_tile (objects/r8/trap_1.asm:262-280). */
    public static final int TRAP_DOOR_TILE = 0x033C;

    public static final String LASER_ONE = "cd.mmz.laser1";
    public static final String LASER_TWO = "cd.mmz.laser2";
    /** LaserObject_0_Routine0: move.w #$3E0,obj.sprite_tile (objects/r8/laser.asm:23-27). */
    public static final int LASER_TILE = 0x03E0;
    public static final String PROPELLER_BASE = "cd.mmz.propellerBase";
    public static final String PROPELLER_BLADE = "cd.mmz.propellerBlade";
    /** PropellerObject_0_Routine0 and sub_20CD3A: move.w #$36A,obj.sprite_tile (objects/r8/propeller.asm:30-85). */
    public static final int PROPELLER_TILE = 0x036A;

    public static final String SPIKE_CRUSHER = "cd.mmz.spikeCrusher";
    public static final String SPIKE_CRUSHER_TIP = "cd.mmz.spikeCrusherTip";
    /** SpikeCrusherObject_0_Routine0: move.w #$400,obj.sprite_tile (objects/r8/trap_2.asm:81-88). */
    public static final int SPIKE_CRUSHER_TILE = 0x0400;
    /** loc_20D0BE: move.w #$4400,obj.sprite_tile (objects/r8/trap_2.asm:430-436). */
    public static final int SPIKE_CRUSHER_TIP_TILE = 0x4400;

    public static final String BIGBOM = "cd.mmz.bigbom";
    public static final String BIGBOM_FUSE = "cd.mmz.bigbomFuse";
    public static final String BIGBOM_EXPLODE = "cd.mmz.bigbomExplode";
    public static final String BIGBOM_DEBRIS = "cd.mmz.bigbomDebris";
    /** BigbomObject_2_Routine0 (objects/r8/bigbom.asm:231-239), BigbomObject_3_Routine0 (objects/r8/bigbom.asm:471-479) and BigbomObject_5_Routine0 (objects/r8/bigbom.asm:587-596): move.w #$8340,obj.sprite_tile. */
    public static final int BIGBOM_TILE = 0x8340;
    /** BigbomObject_4_Routine0: move.w #$8680,obj.sprite_tile (objects/r8/bigbom.asm:541-547). */
    public static final int BIGBOM_EXPLODE_TILE = 0x8680;

    /** Door sheets {@code DOOR + 1..3} (DoorSprites1-3 by subtype, off_20D02C, objects/r8/door.asm:67-70). */
    public static final String DOOR = "cd.mmz.door";
    /** sub_20CFF6: move.w #$47E,obj.sprite_tile(a1) (objects/r8/door.asm:55). */
    public static final int DOOR_TILE = 0x047E;

    public static final String BUMPER = "cd.mmz.bumper";
    /** BumperObject_0_Routine0: move.w #$4CE,obj.sprite_tile (objects/r3/bumper_flipper.asm:20); BumperGfx loads at $99C0 (r8/gfx_lists_3c.asm:41). */
    public static final int BUMPER_TILE = 0x04CE;

    public static final String COLLAPSE_FLOOR = "cd.mmz.collapseFloor";
    /** CollapseFloorObject_1_Routine0: move.w #$44D8,d0 (objects/r8/collapse_floor.asm:26); act 3 (cmpi.b #2,act) uses #$437A (:27-29). */
    public static final int COLLAPSE_FLOOR_TILE = 0x44D8, COLLAPSE_FLOOR_TILE_ACT3 = 0x437A;

    public static final String TWIN_WALKER = "cd.mmz.twinWalker";
    /** TwinWalkerObject_0_Routine0: move.w #$318,obj.sprite_tile(a1) (objects/r8/twin_walker.asm:46-52). */
    public static final int TWIN_WALKER_TILE = 0x0318;

    public static final String SCARAB = "cd.mmz.scarab";
    /** ScarabObject_1_Routine0: move.w #$243F,obj.sprite_tile (objects/r8/scarab.asm:225-240). */
    public static final int SCARAB_TILE = 0x243F;

    public static final String REVOLVE_DOOR = "cd.mmz.revolveDoor";
    /** RevolveDoorObject_0_Routine0: move.w #$41B,obj.sprite_tile (objects/r8/revolve_door.asm:39-60). */
    public static final int REVOLVE_DOOR_TILE = 0x041B;

    /** Registers the Metallic Madness sheets for the loaded stage ({@link MmzObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        MmzObjectArt state = art.zoneArt(MmzObjectArt.class, () -> new MmzObjectArt(art.payload()));
        state.loadSections(art);
        sheet(art, HV_PLATFORM, "hvPlatformMappings", HV_PLATFORM_TILE);
        sheet(art, SPIKE_WALL, "spikeWallMappings", SPIKE_WALL_TILE);
        sheet(art, VANISH_PLATFORM, "vanishPlatformMappings", VANISH_PLATFORM_TILE);
        sheet(art, MECHA_BU, "mechaBuMappings", MECHA_BU_TILE);
        sheet(art, MECHA_BU_SAW, "mechaBuSawMappings", MECHA_BU_TILE);
        sheet(art, BUZZSAW, "buzzsawMappings", BUZZSAW_TILE);
        int dangoTile = com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue.offset(art.payload(), "mmzDangoTile");
        if (dangoTile >= 0) {
            sheetAt(art, DANGO_1, com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue.offset(art.payload(), "mmzDangoMappings1"), dangoTile);
            sheetAt(art, DANGO_2, com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue.offset(art.payload(), "mmzDangoMappings2"), dangoTile);
        }
        sheet(art, SPIN_PLATFORM, "spinPlatformMappings", SPIN_PLATFORM_TILE);
        sheet(art, TRAP_BLOCK, "trapBlockMappings", TRAP_BLOCK_TILE);
        sheet(art, REVOLVE_DOOR, "revolveDoorMappings", REVOLVE_DOOR_TILE);
        sheet(art, LASER_ONE, "laserMappings1", LASER_TILE);
        sheet(art, LASER_TWO, "laserMappings2", LASER_TILE);
        sheet(art, PROPELLER_BASE, "propellerBaseMappings", PROPELLER_TILE);
        sheet(art, PROPELLER_BLADE, "propellerBladeMappings", PROPELLER_TILE);
        sheet(art, TRAP_DOOR, "trapDoorMappings", TRAP_DOOR_TILE);
        sheet(art, LAYER_PLATFORM, "layerPlatformMappings", LAYER_PLATFORM_TILE);
        sheet(art, trapSpikesKey(TRAP_SPIKES_TILE), "trapSpikesMappings", TRAP_SPIKES_TILE);
        sheet(art, trapSpikesKey(TRAP_SPIKES_LAYER_TILE), "trapSpikesMappings", TRAP_SPIKES_LAYER_TILE);
        sheet(art, SPIKE_CRUSHER, "spikeCrusherMappings", SPIKE_CRUSHER_TILE);
        sheet(art, SPIKE_CRUSHER_TIP, "spikeCrusherMappings", SPIKE_CRUSHER_TIP_TILE);
        sheet(art, BIGBOM, "bigbomMappings", BIGBOM_TILE);
        sheet(art, BIGBOM_FUSE, "bigbomFuseMappings", BIGBOM_TILE);
        sheet(art, BIGBOM_EXPLODE, "bigbomExplodeMappings", BIGBOM_EXPLODE_TILE);
        sheet(art, BIGBOM_DEBRIS, "bigbomDebrisMappings", BIGBOM_TILE);
        sheet(art, COLLAPSE_FLOOR, "collapseFloorMappings",
                art.payload().stage().actNumber() == 3 ? COLLAPSE_FLOOR_TILE_ACT3 : COLLAPSE_FLOOR_TILE);
        int doors = MmzObjectCatalogue.offset(art.payload(), "doorMappingTable");
        if (doors >= 0) {
            for (int i = 0; i < 3; i++) {
                sheetAt(art, DOOR + (i + 1), art.image().offsetOf(art.image().u32(doors + i * 4)), DOOR_TILE);
            }
        }
        if (MmzBumper.indexedIn(art.payload())) sheet(art, BUMPER, "bumperMappings", BUMPER_TILE);
        sheet(art, TWIN_WALKER, "twinWalkerMappings", TWIN_WALKER_TILE);
        sheet(art, SCARAB, "scarabMappings", SCARAB_TILE);
        sheet(art, BUZZSAW_HANDLE, "buzzsawHandleMappings", BUZZSAW_TILE);
        int moveSpring = MmzObjectCatalogue.offset(art.payload(), "moveSpringMappings");
        if (moveSpring >= 0) sheet(art, moveSpringKey(moveSpringTile(art.payload())), "moveSpringMappings", moveSpringTile(art.payload()));
    }

    /**
     * SetObjectSpriteTile with d0 = $E (r8/section_gfx_1.asm:76-83, section_gfx_2.asm:72-80, section_gfx_3.asm:12-14): $4EA in
     * R81x and R82x, $372 in R83x.
     */
    public static int moveSpringTile(CdStagePayload payload) {
        return payload.stage().actNumber() == 3 ? 0x372 : 0x4EA;
    }

    private void loadSections(CdObjectArtProvider art) throws IOException {
        CdStageImage stage = art.image();
        var stageEntry = CdStageCatalogue.entry(loaded);
        int ranges = MmzObjectCatalogue.offset(loaded, "sectionRanges");
        if (ranges >= 0) {
            // SectionGfxRanges, SectionGfxUpdateLists, SectionGfxInitLists: equal-length word lists, the first ending in $FFFF.
            List<Integer> bounds = new ArrayList<>();
            int words = 0;
            while (true) {
                int value = stage.u16(ranges + words * 2);
                words++;
                bounds.add(value);
                if (value == 0xFFFF || words > 16) break;
            }
            sectionBounds = bounds.stream().mapToInt(Integer::intValue).toArray();
            int init = ranges + 4 * words;
            for (int k = 0; k < words; k++) {
                CdStageVram vram = CdObjectArtProvider.loadVram(stage, loaded);
                vram.loadPlc(stage, stageEntry.plcLists(), stage.u16(init + k * 2));
                sectionVram.add(vram);
            }
        } else {
            CdStageVram vram = CdObjectArtProvider.loadVram(stage, loaded);
            vram.loadPlc(stage, stageEntry.plcLists(), TITLE_CARD_PLC);
            sectionVram.add(vram);
        }
    }

    /** Registers {@code kind}'s sheet for every section when the stage links the routine ({@code column} offset present). */
    private static void sheet(CdObjectArtProvider art, String kind, String column, int tileWord) {
        MmzObjectArt state = art.zoneArt(MmzObjectArt.class);
        int table = MmzObjectCatalogue.offset(art.payload(), column);
        if (state == null || table < 0) return;
        sheetAt(art, kind, table, tileWord);
    }

    /** As {@link #sheet}, from a mapping table offset already known (a pointer table entry). */
    private static void sheetAt(CdObjectArtProvider art, String kind, int table, int tileWord) {
        MmzObjectArt state = art.zoneArt(MmzObjectArt.class);
        if (state == null || table < 0) return;
        var reader = art.image().reader();
        List<SpriteMappingFrame> frames = CdObjectArtProvider.frames(reader, table);
        int span = CdObjectArtProvider.span(frames);
        for (int k = 0; k < state.sectionVram.size(); k++) {
            Pattern[] window = state.sectionVram.get(k).window(tileWord & 0x7FF, span);
            art.register(kind + "#" + k,
                    new ObjectSpriteSheet(window, frames, CdObjectArtProvider.tilePalette(tileWord), 1));
        }
        state.registered.put(kind, true);
    }

    /** The section the camera is in: the first bound of {@code SectionGfxRanges} (r8/section_gfx_1.asm:52-58) above it, as InitSectionGfx counts them (:3-18). */
    private int section(int cameraX) {
        int section = 0;
        while (section < sectionBounds.length - 1 && (cameraX & 0xFFFF) >= sectionBounds[section]) section++;
        return Math.min(section, Math.max(0, sectionVram.size() - 1));
    }

    /** The renderer key of {@code kind} for the camera's section, or {@code null} when it is not registered. */
    public static String key(CdObjectArtProvider art, String kind, int cameraX) {
        MmzObjectArt state = art == null ? null : art.zoneArt(MmzObjectArt.class);
        return state == null || !state.registered.containsKey(kind) ? null : kind + "#" + state.section(cameraX);
    }
}
