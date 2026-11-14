package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Tidal Tempest object art, registered by {@link TtzObjects} through the art provider's fragment API.
 *
 * <p>The objects' tiles arrive through later pattern load cues, not the level-start cues: the title card's end
 * loads PLC 2 (Level/_Objects/Title Card/Main.asm:145-148) and a stage with camera sections loads each section's init
 * list as the camera enters it (InitSectionGfx/UpdateSectionGfx, src/r4/section_gfx_2a.asm:3-50). A sheet is
 * therefore a window into the VRAM of the section the camera is in. Section k's VRAM is modelled as the
 * level-start VRAM plus section k's init list; the decode queue and the incremental update lists are not
 * modelled (CdStageVram). Stages without sections use the title card's PLC 2 alone. Same model as the
 * Palmtree Panic owner's sheets, whose class this follows.
 */
public final class TtzObjectArt {
    /** PLC 2: the title card's slide-out cue (Level/_Objects/Title Card/Main.asm:145-148). */
    private static final int TITLE_CARD_PLC = 2;
    private static final String PREFIX = "cd.ttz.";

    /** One sheet kind: the catalogue column of its mappings table and the tile words it is drawn with. */
    private record Spec(String kind, String mappingsColumn, int[] tileWords) { }

    /** Sheet kinds (see {@link #SPECS}); each object class draws its kind with its tile word. */
    public static final String MOVE_WALL = "movewall", ROTATE_BLOCK = "rotateblock", SHOOTER = "shooter",
            MISSILE = "missile", BIG_DOOR = "bigdoor", MACE = "mace", MACE_CHAIN = "macechain2",
            MACE_BALL = "macechain1", WATER_SURFACE = "watersurface", SPLASH = "splash",
            FALL_SPIKES = "fallspikes", BUBBLE = "bubble", TONBO1 = "tonbo1", TONBO2 = "tonbo2",
            CIRCLE_SPIKE = "circlespike", FAN_AB1 = "fanab1", FAN_CD1 = "fancd1", FAN2 = "fan2", ELEVATOR = "elevator", TWIN_MOVE = "twinmove", PRESSURE = "pressure",
            CRUSH = "crush", GEYSER = "geyser", AMENBO1 = "amenbo1", AMENBO2 = "amenbo2",
            PLATFORM_CHAIN = "platformchain", DOOR = "door", SWITCH = "switch", YAGO1 = "yago1", YAGO2 = "yago2", COLLAPSE_FLOOR = "collapsefloor",
            HMOVE1 = "hmove1", HMOVE2 = "hmove2", HMOVE3 = "hmove3", HMOVE4 = "hmove4", HMOVE5 = "hmove5", HMOVE6 = "hmove6",
            TAGA1 = "taga1", TAGA2 = "taga2", TAGA_MISSILE = "tagamissile", ANIMAL1 = "animal1", ANIMAL2 = "animal2",
            SPIKES = "spikes", WOOD_BLOCK = "woodblock", PRIZE_SWITCH = "prizeswitch", RING_PRIZE = "ringprize", POINTS_PRIZE = "pointsprize";

    /** The tile words the routines load (cited on each class): move.w #tile,obj.sprite_tile. */
    public static final int MOVE_WALL_TILE = 0x446A,      // objects/r4/move_wall.asm:27
            ROTATE_BLOCK_TILE = 0x4300,                    // objects/r4/rotate_block.asm:38
            SHOOTER_TILE = 0x4318,                         // objects/r4/shooter.asm:14
            MISSILE_TILE = 0x0318,                         // objects/r4/shooter.asm:47
            BIG_DOOR_TILE = 0x446A,                        // objects/r4/big_door.asm:28
            MACE_TILE = 0x0368,                            // objects/r4/mace.asm:33
            MACE_BALL_TILE = 0x0370,                       // objects/r4/mace.asm:223
            WATER_SURFACE_TILE = 0x8280,                   // objects/r4/water_surface.asm:20
            SPLASH_TILE = 0x0298,                          // objects/r4/splash.asm:23
            FALL_SPIKES_TILE = 0x03A0,                     // objects/r4/fall_spikes.asm:47
            BUBBLE_TILE = 0x848A,                          // objects/r4/bubble_spawner.asm:25
            TONBO_TILE = 0x2416,                           // objects/r4/tonbo.asm:29
            CIRCLE_SPIKE_TILE = 0x0370,                    // objects/r4/circle_spike_ball.asm:98
            FAN_TILE = 0x4380,                             // objects/r4/fan_a_b.asm:11
            ELEVATOR_TILE = 0x435C,                        // objects/r4/elevator.asm:60
            TWIN_MOVE_TILE = 0x446A,                       // objects/r4/twin_move_block.asm:39
            PRESSURE_TILE = 0x4310,                        // objects/r4/pressure_elevator.asm:42
            CRUSH_TILE = 0x446A,                           // objects/r4/crush_blocks.asm:24
            GEYSER_TILE = 0x6300,                          // objects/r4/geyser.asm:37
            AMENBO_TILE = 0x23B0,                          // objects/r4/amenbo.asm:27
            YAGO_TILE = 0x23F0,                            // objects/r4/yago.asm:41
            COLLAPSE_FLOOR_TILE = 0x4364,                  // objects/r4/collapse_floor.asm:24
            HMOVE_TILE = 0x446A,                           // objects/r4/h_move_block.asm:73 (every subtype)
            TAGA_TILE = 0x23D0,                            // objects/r4/taga_taga.asm:44,70
            ANIMAL_TILE = 0x0450;                          // word_20E5C4 (objects/r4/animal.asm:155-158), every Tidal Tempest act and zone

    private static final List<Spec> SPECS = List.of(
            new Spec(MOVE_WALL, "moveWallMappings", new int[] {MOVE_WALL_TILE}),
            new Spec(DOOR, "doorMappings", new int[] {TtzDoor.TILE}),
            new Spec(SWITCH, "switchMappings", new int[] {TtzSwitch.TILE}),
            new Spec(ROTATE_BLOCK, "rotateBlockMappings", new int[] {ROTATE_BLOCK_TILE}),
            new Spec(SHOOTER, "shooterMappings", new int[] {SHOOTER_TILE}),
            new Spec(MISSILE, "missileMappings", new int[] {MISSILE_TILE}),
            new Spec(BIG_DOOR, "bigDoorMappings", new int[] {BIG_DOOR_TILE}),
            new Spec(MACE, "maceMappings", new int[] {MACE_TILE}),
            new Spec(MACE_CHAIN, "maceChain2Mappings", new int[] {MACE_TILE}),
            new Spec(MACE_BALL, "maceChain1Mappings", new int[] {MACE_BALL_TILE}),
            new Spec(WATER_SURFACE, "waterSurfaceMappings", new int[] {WATER_SURFACE_TILE}),
            new Spec(SPLASH, "splashMappings", new int[] {SPLASH_TILE}),
            new Spec(FALL_SPIKES, "fallSpikesMappings", new int[] {FALL_SPIKES_TILE}),
            new Spec(BUBBLE, "bubbleMappings", new int[] {BUBBLE_TILE}),
            new Spec(TONBO1, "tonboMappings1", new int[] {TONBO_TILE}),
            new Spec(TONBO2, "tonboMappings2", new int[] {TONBO_TILE}),
            new Spec(CIRCLE_SPIKE, "circleSpikeMappings", new int[] {CIRCLE_SPIKE_TILE}),
            new Spec(FAN_AB1, "fanABMappings1", new int[] {FAN_TILE}),
            new Spec(FAN_CD1, "fanCDMappings1", new int[] {FAN_TILE}),
            new Spec(FAN2, "fanMappings2", new int[] {FAN_TILE}),
            new Spec(ELEVATOR, "elevatorMappings", new int[] {ELEVATOR_TILE}),
            new Spec(TWIN_MOVE, "twinMoveMappings", new int[] {TWIN_MOVE_TILE}),
            new Spec(PRESSURE, "pressureMappings", new int[] {PRESSURE_TILE}),
            new Spec(CRUSH, "crushMappings", new int[] {CRUSH_TILE}),
            new Spec(GEYSER, "geyserMappings", new int[] {GEYSER_TILE}),
            new Spec(AMENBO1, "amenboMappings1", new int[] {AMENBO_TILE}),
            new Spec(AMENBO2, "amenboMappings2", new int[] {AMENBO_TILE}),
            new Spec(YAGO1, "yagoMappings1", new int[] {YAGO_TILE}),
            new Spec(YAGO2, "yagoMappings2", new int[] {YAGO_TILE}),
            new Spec(COLLAPSE_FLOOR, "collapseFloorMappings", new int[] {COLLAPSE_FLOOR_TILE}),
            new Spec(HMOVE1, "hMoveMappings1", new int[] {HMOVE_TILE}),
            new Spec(HMOVE2, "hMoveMappings2", new int[] {HMOVE_TILE}),
            new Spec(HMOVE3, "hMoveMappings3", new int[] {HMOVE_TILE}),
            new Spec(HMOVE4, "hMoveMappings4", new int[] {HMOVE_TILE}),
            new Spec(HMOVE5, "hMoveMappings5", new int[] {HMOVE_TILE}),
            new Spec(HMOVE6, "hMoveMappings6", new int[] {HMOVE_TILE}),
            new Spec(TAGA1, "tagaMappings1", new int[] {TAGA_TILE}),
            new Spec(TAGA2, "tagaMappings2", new int[] {TAGA_TILE}),
            new Spec(TAGA_MISSILE, "tagaMissileMappings", new int[] {TAGA_TILE}),
            new Spec(SPIKES, "spikesMappings", new int[] {ObjSpikes.TILE}),
            new Spec(WOOD_BLOCK, "woodBlockMappings", new int[] {ObjWoodBlock.TILE}),
            new Spec(PRIZE_SWITCH, "prizeSwitchMappings", new int[] {ObjPrizeSwitch.TILE}),
            new Spec(RING_PRIZE, "ringPrizeMappings", new int[] {ObjRingPrize.TILE}),
            new Spec(POINTS_PRIZE, "pointsPrizeMappings", new int[] {ObjPointsPrize.TILE}),
            new Spec(ANIMAL1, "animalMappings1", new int[] {ANIMAL_TILE}),
            new Spec(ANIMAL2, "animalMappings2", new int[] {ANIMAL_TILE + 0x2000}),
            // objects/r4/platform_chain.asm:62-65,87-88: $4340 ($4400 in act 3); links draw with $4000 less
            new Spec(PLATFORM_CHAIN, "platformChainMappings", new int[] {0x4340, 0x4400, 0x0340, 0x0400})
    );

    private final CdStagePayload loaded;
    private final List<CdStageVram> sectionVram = new ArrayList<>();
    private int[] sectionBounds = new int[0];
    private final Map<String, String> sheets = new HashMap<>();

    private TtzObjectArt(CdStagePayload loaded) {
        this.loaded = loaded;
    }

    /** Registers the Tidal Tempest sheets for the loaded stage ({@link TtzObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        if (!art.payload().name().startsWith("R4")) return;
        TtzObjectArt state = art.zoneArt(TtzObjectArt.class, () -> new TtzObjectArt(art.payload()));
        state.registerAll(art, art.image());
    }

    /** The renderer key of a sheet for the camera's section, or {@code null} when not loaded. */
    public static String key(CdObjectArtProvider art, String kind, int tileWord, int cameraX) {
        TtzObjectArt state = art == null ? null : art.zoneArt(TtzObjectArt.class);
        return state == null ? null : state.sheets.get(index(kind, tileWord, state.section(cameraX)));
    }

    /** An animator over one of the stage's script tables, or {@code null} before art is loaded. */
    public static CdAnimator animator(CdObjectArtProvider art, String column, int anim) {
        return art == null ? null : new CdAnimator(art.image(), TtzObjectCatalogue.offset(art.payload(), column), anim);
    }

    private void registerAll(CdObjectArtProvider art, CdStageImage stage) throws IOException {
        var stageEntry = CdStageCatalogue.entry(loaded);
        int ranges = TtzObjectCatalogue.offset(loaded, "sectionRanges");
        if (ranges >= 0) {
            // SectionGfxRanges, SectionGfxUpdateLists, SectionGfxInitLists: equal-length word lists, the first
            // ending in $FFFF (src/r4/section_gfx_2a.asm:52-70).
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
        for (Spec spec : SPECS) {
            for (int tile : spec.tileWords()) add(art, spec.kind(), TtzObjectCatalogue.offset(loaded, spec.mappingsColumn()), tile);
        }
    }

    /** Registers the kind's sheet for every section, sharing one renderer between sections with equal tiles. */
    private void add(CdObjectArtProvider art, String kind, int table, int tileWord) {
        if (table < 0 || tileWord == 0) return;
        List<SpriteMappingFrame> frames = CdObjectArtProvider.frames(art.image().reader(), table);
        int span = CdObjectArtProvider.span(frames);
        List<byte[]> seen = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int k = 0; k < sectionVram.size(); k++) {
            Pattern[] window = sectionVram.get(k).window(tileWord & 0x7FF, span);
            byte[] bytes = new byte[window.length * Pattern.PATTERN_SIZE_IN_MEM];
            for (int i = 0; i < window.length; i++) window[i].copyInto(bytes, i * Pattern.PATTERN_SIZE_IN_MEM);
            int same = -1;
            for (int i = 0; i < seen.size(); i++) {
                if (Arrays.equals(seen.get(i), bytes)) { same = i; break; }
            }
            if (same < 0) {
                String key = PREFIX + kind + "." + Integer.toHexString(tileWord & 0xFFFF) + "." + k;
                art.register(key, new ObjectSpriteSheet(window, frames, CdObjectArtProvider.tilePalette(tileWord), 1));
                keys.add(key);
            } else {
                keys.add(keys.get(same));
            }
            seen.add(bytes);
            sheets.put(index(kind, tileWord, k), keys.get(k));
        }
    }

    private static String index(String kind, int tileWord, int section) {
        return kind + "#" + (tileWord & 0xFFFF) + "#" + section;
    }

    /** The section the camera is in: the first range bound above it (InitSectionGfx, src/r4/section_gfx_2a.asm:3-15). */
    private int section(int cameraX) {
        int section = 0;
        while (section < sectionBounds.length - 1 && (cameraX & 0xFFFF) >= sectionBounds[section]) section++;
        return Math.min(section, Math.max(0, sectionVram.size() - 1));
    }

    /** The camera's section; 0 before art is loaded. */
    public static int section(CdObjectArtProvider art, int cameraX) {
        TtzObjectArt state = art == null ? null : art.zoneArt(TtzObjectArt.class);
        return state == null ? 0 : state.section(cameraX);
    }
}
