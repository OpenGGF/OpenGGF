package com.openggf.game.soniccd.objects.ccz;

import com.openggf.data.RomByteReader;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collision Chaos object art, registered by {@link CczObjects} through the art provider's fragment API.
 * Per-load state lives in the provider's zone-art slot; table offsets come from {@link CczObjectCatalogue}.
 *
 * <p>Collision Chaos' object tiles arrive through the section pattern load cues, not the level-start
 * cues: the title card's end loads PLC 2 (Level/_Objects/Title Card/Main.asm:145-148) and each camera section loads its
 * own list (LoadSectionArt/UpdateSectionArt, Section Art Load.asm:7-48; Collision Chaos/Section Data
 * (Act 1 Present).asm:12-31). A stage that links the nullified section routines
 * (Section Art Load (Null).asm:7-10) uses PLC 2 alone. A sheet is therefore a window into the VRAM of the
 * section the camera is in, modelled as the level-start VRAM plus the section's init list (the decode
 * queue and the incremental update lists are not modelled, see {@link CdStageVram}).
 */
public final class CczObjectArt {
    public static final String INTRO_SPIKES = "introspikes", METAL_SONIC = "metalsonic",
            METAL_PLATFORM = "metalplatform", DRUM_PLATFORM = "drumplatform", FIRE_SHOOT = "fireshoot",
            BUMPER_PLATFORM = "bumperplatform", GLASS_BREAK = "glassbreak", GA1 = "ga1", GA2 = "ga2",
            POCKET1 = "pocket1", POCKET2 = "pocket2", TENTOU1 = "tentou1", TENTOU2 = "tentou2",
            TENTOU_BOMB = "tentoubomb", KAMA_KAMA1 = "kamakama1", KAMA_KAMA2 = "kamakama2",
            KAMA_SICKLE = "kamasickle", DRAIN_BLOCK = "drainblock", DRAIN_LIGHT = "drainlight",
            DRAIN_SWITCH = "drainswitch", BUMPER = "bumper", BLOCK = "block", HIDDEN_BLOCK = "hiddenblock", SPIKES = "spikes", FLIPPER = "flipper", AMY = "amy", HEART = "heart",
            ANIMAL_FLYING = "animalflying", ANIMAL_GROUND = "animalground", DOOR = "door";

    /** ObjDoor_Init: move.w #$371,oTile(a0) (Level/Collision Chaos/Objects/Door/Main.asm, ObjDoor_Init). */
    public static final int DOOR_TILE = 0x0371;

    /** ObjBumper_Init: move.w #$4CE,oTile(a0); ObjFlipper_Init: move.w #$49D,oTile(a0) (Bumper and Flipper/Main.asm). */
    public static final int BUMPER_TILE = 0x04CE, FLIPPER_TILE = 0x049D;
    /** AmyRoseInit: move.w #$235E; AmyHeartInit: move.w #$35E (objects/r3/intro.asm:774-781, 774-781). */
    public static final int AMY_TILE = 0x235E, HEART_TILE = 0x035E;

    /** IntroSpikesInit: move.w #$31E,obj.sprite_tile (objects/r3/intro.asm). */
    public static final int INTRO_SPIKES_TILE = 0x031E;
    /** MetalSonicInit and MetalSonicExhaustInit: move.w #$3D0 (objects/r3/intro.asm). */
    public static final int METAL_SONIC_TILE = 0x03D0;
    /** ObjMetalPlatform_Init: move.w #$34E,oTile(a0) (Metal Platform/Main.asm). */
    public static final int METAL_PLATFORM_TILE = 0x034E;
    /** ObjDrumPlatform_Init: move.w #$36A,oTile(a0) (Drum Platform/Main.asm). */
    public static final int DRUM_PLATFORM_TILE = 0x036A;
    /** ObjFireShooter_*_Init: move.w #$379,oTile(a0) (Fire Shooter/Main.asm). */
    public static final int FIRE_SHOOT_TILE = 0x0379;
    /** ObjBumperPlatform_Init: move.w #$435E,oTile(a0) (Bumper Platform/Main.asm). */
    public static final int BUMPER_PLATFORM_TILE = 0x435E;
    /** ObjGlassShatter_Init: $638B, or $642A in act 3 (Glass Shatter/Main.asm-46). */
    public static final int GLASS_TILE = 0x638B, GLASS_TILE_ACT3 = 0x642A;
    /** ObjGa_Init: move.w #$A421,oTile(a0) (Ga/Main.asm). */
    public static final int GA_TILE = 0xA421;
    /** ObjPocket_Init: move.w #$3AF; ObjPocket_Child_Init: move.w #$43AF (Pocket/Main.asm). */
    public static final int POCKET_TILE = 0x03AF, POCKET_CHILD_TILE = 0x43AF;
    /** ObjTentou_Init: move.w #$A46A; ObjTentou_Bomb_Init: move.w #$495 (Tentou/Main.asm). */
    public static final int TENTOU_TILE = 0xA46A, TENTOU_BOMB_TILE = 0x0495;
    /** ObjKamaKama_Init and ObjKamaSickle_Init: move.w #$23F2,oTile(a0) (KamaKama/Main.asm). */
    public static final int KAMA_TILE = 0x23F2;
    /** DrainBlock_Init, DrainLight_Init, DrainSwitch_Init (objects/r3/drain_*.asm). */
    public static final int DRAIN_BLOCK_TILE = 0x043E, DRAIN_LIGHT_TILE = 0x643E, DRAIN_SWITCH_TILE = 0x443E;
    /** PLC 2: the title card's slide-out cue (Level/_Objects/Title Card/Main.asm:145-148). */
    private static final int TITLE_CARD_PLC = 2;
    /** Amy Rose's init queues PLC $B, Collision Chaos' PLC_Intro (objects/r3/intro.asm:271-277; Graphics Lists (Act 1 Present).asm). */
    private static final int INTRO_PLC = 0xB;
    private static final String PREFIX = "cd.ccz.";

    private final CdStagePayload loaded;
    private final List<CdStageVram> sectionVram = new ArrayList<>();
    private int[] sectionBounds = new int[0];
    private final Map<String, String> sheets = new HashMap<>();

    private CczObjectArt(CdStagePayload loaded) {
        this.loaded = loaded;
    }

    /** Registers the Collision Chaos sheets for the loaded stage ({@link CczObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        art.zoneArt(CczObjectArt.class, () -> new CczObjectArt(art.payload())).registerAll(art, art.image(), art.image().reader());
    }

    /** The renderer key of a sheet for the camera's section, or {@code null} when it is not loaded. */
    public static String key(CdObjectArtProvider art, String kind, int tileWord, int cameraX) {
        CczObjectArt state = art == null ? null : art.zoneArt(CczObjectArt.class);
        return state == null ? null : state.sheets.get(index(kind, tileWord, state.section(cameraX)));
    }

    /** The camera's section ({@code SectionRanges}, Section Art Load.asm:11-17); 0 before art is loaded. */
    public static int section(CdObjectArtProvider art, int cameraX) {
        CczObjectArt state = art == null ? null : art.zoneArt(CczObjectArt.class);
        return state == null ? 0 : state.section(cameraX);
    }

    /** An animator over one of the Collision Chaos script tables, or {@code null} where the stage has none. */
    public static CdAnimator animator(CdObjectArtProvider art, String column, int anim) {
        if (art == null) return null;
        int table = CczObjectCatalogue.offset(art.payload(), column);
        return table < 0 ? null : new CdAnimator(art.image(), table, anim);
    }

    private void registerAll(CdObjectArtProvider art, CdStageImage stage, RomByteReader reader) throws IOException {
        var stageEntry = CdStageCatalogue.entry(loaded);
        int ranges = CczObjectCatalogue.offset(loaded, "sectionRanges");
        if (ranges >= 0) {
            // SectionRanges, SectionUpdatePLCs, SectionInitPLCs: equal-length word lists, the first ending
            // in $FFFF (Section Data (Act 1 Present).asm:12-31; LoadSectionArt, Section Art Load.asm:7-10).
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
        int act = loaded.name().charAt(2) - '1';
        sheet(art, reader, INTRO_SPIKES, "introSpikesMappings", INTRO_SPIKES_TILE, -1);
        sheet(art, reader, METAL_SONIC, "metalSonicMappings", METAL_SONIC_TILE, INTRO_PLC);
        sheet(art, reader, METAL_PLATFORM, "metalPlatformMappings", METAL_PLATFORM_TILE, -1);
        sheet(art, reader, DRUM_PLATFORM, "drumPlatformMappings", DRUM_PLATFORM_TILE, -1);
        sheet(art, reader, FIRE_SHOOT, "fireShootMappings", FIRE_SHOOT_TILE, -1);
        sheet(art, reader, BUMPER_PLATFORM, "bumperPlatformMappings", BUMPER_PLATFORM_TILE, -1);
        sheet(art, reader, GLASS_BREAK, "glassBreakMappings", act == 2 ? GLASS_TILE_ACT3 : GLASS_TILE, -1);
        sheet(art, reader, GA1, "gaMappings1", GA_TILE, -1);
        sheet(art, reader, GA2, "gaMappings2", GA_TILE, -1);
        sheet(art, reader, POCKET1, "pocketMappings1", POCKET_TILE, -1);
        sheet(art, reader, POCKET2, "pocketMappings2", POCKET_CHILD_TILE, -1);
        sheet(art, reader, TENTOU1, "tentouMappings1", TENTOU_TILE, -1);
        sheet(art, reader, TENTOU2, "tentouMappings2", TENTOU_TILE, -1);
        sheet(art, reader, TENTOU_BOMB, "tentouBombMappings", TENTOU_BOMB_TILE, -1);
        sheet(art, reader, KAMA_KAMA1, "kamaKamaMappings1", KAMA_TILE, -1);
        sheet(art, reader, KAMA_KAMA2, "kamaKamaMappings2", KAMA_TILE, -1);
        sheet(art, reader, KAMA_SICKLE, "kamaSickleMappings", KAMA_TILE, -1);
        sheet(art, reader, DOOR, "doorMappings", DOOR_TILE, -1);
        sheet(art, reader, BUMPER, "bumperMappings", BUMPER_TILE, -1);
        // Block, hidden block and spikes (Block/Main.asm:70, 257; Spikes/Main.asm:70-73).
        sheet(art, reader, BLOCK, "blockMappings", CczBlock.TILE, -1);
        sheet(art, reader, HIDDEN_BLOCK, "hiddenBlockMappings", CczHiddenBlock.TILE, -1);
        sheet(art, reader, SPIKES, "spikesMappings", act == 2 ? 0x3FC : 0x31E, -1);
        sheet(art, reader, FLIPPER, "flipperMappings", FLIPPER_TILE, -1);
        sheet(art, reader, AMY, "amyRoseMappings", AMY_TILE, INTRO_PLC);
        sheet(art, reader, HEART, "amyRoseMappings", HEART_TILE, INTRO_PLC);
        int animalTile = animalBaseTile(stage, act);
        if (animalTile != 0) {
            sheet(art, reader, ANIMAL_FLYING, "animalFlyingMappings", animalTile, -1);
            sheet(art, reader, ANIMAL_GROUND, "animalGroundMappings", animalTile, -1);
        }
        sheet(art, reader, DRAIN_BLOCK, "drainBlockMappings", DRAIN_BLOCK_TILE, -1);
        sheet(art, reader, DRAIN_LIGHT, "drainLightMappings", DRAIN_LIGHT_TILE, -1);
        sheet(art, reader, DRAIN_SWITCH, "drainSwitchMappings", DRAIN_SWITCH_TILE, -1);
    }

    /**
     * ObjCCAnimal_SetBaseTile (Level/Collision Chaos/Objects/Animal/Main.asm, ObjCCAnimal_SetBaseTile): the word at {@code ObjCCAnimal_BaseTileList[act * 4 + timeZone]}
     * ({@code asl.w #2,d0; add.b timeZone,d0; add.w d0,d0}); 0 where the stage has no animals.
     */
    private int animalBaseTile(CdStageImage stage, int act) {
        int list = CczObjectCatalogue.offset(loaded, "animalBaseTiles");
        if (list < 0) return 0;
        int timeZone = com.openggf.game.soniccd.level.CdLevel.nativeTimeZone(loaded.stage().period());
        return stage.u16(list + (act * 4 + timeZone) * 2);
    }

    /** The animal tile word of the loaded stage (see {@link #animalBaseTile}); 0 when none. */
    public static int animalTile(CdObjectArtProvider art) {
        CczObjectArt state = art == null ? null : art.zoneArt(CczObjectArt.class);
        return state == null ? 0 : state.animalBaseTile(art.image(), state.loaded.name().charAt(2) - '1');
    }

    /** Registers the kind's sheet for every section, sharing one renderer between sections with equal tiles. */
    private void sheet(CdObjectArtProvider art, RomByteReader reader, String kind, String column, int tileWord,
                       int extraPlc) throws IOException {
        int table = CczObjectCatalogue.offset(loaded, column);
        if (table < 0) return;
        List<SpriteMappingFrame> frames = CdObjectArtProvider.frames(reader, table);
        int span = CdObjectArtProvider.span(frames);
        List<byte[]> seen = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        var stageEntry = CdStageCatalogue.entry(loaded);
        for (int k = 0; k < sectionVram.size(); k++) {
            CdStageVram vram = sectionVram.get(k);
            if (extraPlc >= 0) {
                vram = vram.copy();
                vram.loadPlc(art.image(), stageEntry.plcLists(), extraPlc);
            }
            Pattern[] window = vram.window(tileWord & 0x7FF, span);
            byte[] bytes = new byte[window.length * Pattern.PATTERN_SIZE_IN_MEM];
            for (int i = 0; i < window.length; i++) window[i].copyInto(bytes, i * Pattern.PATTERN_SIZE_IN_MEM);
            int same = -1;
            for (int i = 0; i < seen.size(); i++) {
                if (java.util.Arrays.equals(seen.get(i), bytes)) { same = i; break; }
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

    /** The section the camera is in: the first {@code SectionRanges} bound above it (Section Art Load.asm:11-17). */
    private int section(int cameraX) {
        int section = 0;
        while (section < sectionBounds.length - 1 && (cameraX & 0xFFFF) >= sectionBounds[section]) section++;
        return Math.min(section, Math.max(0, sectionVram.size() - 1));
    }
}
