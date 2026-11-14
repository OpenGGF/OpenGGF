package com.openggf.game.soniccd.objects.qqz;

import com.openggf.data.RomByteReader;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdLevel;
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
 * Quartz Quadrant object art, registered by {@link QqzObjects}. Mapping and animation tables come
 * from {@link QqzObjectCatalogue}; tiles come from VRAM, never from files.
 *
 * <p>The bridge, circle platform and conveyor switch tiles ($6000, $F2E0) arrive with PLC 1
 * (MainGfxList, r5/gfx_lists_1a.asm:34-50), so they are in the level-start VRAM. Every badnik, the wall
 * pieces, the sine platform and the stalactite ($6840-$94E0) arrive with the title card's PLC 2
 * (SectionGfxInitList0, r5/gfx_lists_1a.asm:52-68; Level/_Objects/Title Card/Main.asm:145-148) or, in the Past,
 * with the init list of the camera's section ({@code InitSectionGfx}, r5/section_gfx_1a.asm:5-21;
 * {@code time_zone & 3} equal to zero selects the section). Section k's VRAM is modelled as the
 * level-start VRAM plus section k's init list; the decode queue and the update lists are not
 * (CdStageVram), as for Palmtree Panic.
 */
public final class QqzObjectArt {
    public static final String NORO1 = "noro1", NORO2 = "noro2", NORO_SPIKES = "norospikes",
            LARGE_BREAK_WALL = "largebreakwall", BREAK_WALL = "breakwall", KEMUSI = "kemusi",
            SASORI1 = "sasori1", SASORI2 = "sasori2", SASORI_GUN = "sasorigun", SASORI_MISSILE = "sasorimissile",
            KUMO1 = "kumo1", KUMO2 = "kumo2", KUMO_WEB = "kumoweb", STALACTITE = "stalactite",
            CONVEYOR_SWITCH = "conveyorswitch", SINE_PLATFORM = "sineplatform",
            CIRCLE_PLATFORM = "circleplatform", BRIDGE = "bridge", SWING = "swing";
    /** move.w #n,obj.sprite_tile(a0) of each routine's init (cited on the object classes). */
    public static final int NORO_TILE = 0x2390, LARGE_BREAK_WALL_TILE = 0x446B, BREAK_WALL_TILE = 0x44A7,
            KEMUSI_TILE = 0x23FE, SASORI_TILE = 0x23B6, KUMO_TILE = 0x2414, CONVEYOR_SWITCH_TILE = 0x4797,
            SINE_PLATFORM_TILE = 0x4487, CIRCLE_PLATFORM_TILE = 0x4300, BRIDGE_TILE = 0x4300,
            SWING_TILE = 0x0358, SWING_PLATFORM_TILE = 0x431C;   // SwingInit (objects/r5/swing.asm:30-37), SwingPlatformInit (:192-203)
    /** StalactiteObject_0_Routine0 (objects/r5/stalactite.asm:25-43): {@code $342}, or {@code $4342} (palette line 2). */
    public static final int STALACTITE_TILE = 0x0342, STALACTITE_TILE_PAST = 0x4342;
    /** PLC 2: the title card's slide-out cue (Level/_Objects/Title Card/Main.asm:145-148). */
    private static final int TITLE_CARD_PLC = 2;
    private static final String PREFIX = "cd.qqz.";

    private final CdStagePayload loaded;
    private final List<CdStageVram> sectionVram = new ArrayList<>();
    private int[] sectionBounds = new int[0];
    private final Map<String, String> sheets = new HashMap<>();

    private QqzObjectArt(CdStagePayload loaded) {
        this.loaded = loaded;
    }

    static void register(CdObjectArtProvider art) throws IOException {
        art.zoneArt(QqzObjectArt.class, () -> new QqzObjectArt(art.payload())).registerAll(art, art.image(), art.image().reader());
    }

    /** The renderer key of a sheet for the camera's section, or {@code null} when not loaded. */
    public static String key(CdObjectArtProvider art, String kind, int tileWord, int cameraX) {
        QqzObjectArt state = art == null ? null : art.zoneArt(QqzObjectArt.class);
        return state == null ? null : state.sheets.get(index(kind, tileWord, state.section(cameraX)));
    }

    /** An animator over one of the catalogue's script tables, or {@code null} when the stage has none. */
    public static CdAnimator animator(CdObjectArtProvider art, String column, int anim) {
        if (art == null) return null;
        int table = QqzObjectCatalogue.offset(art.payload(), column);
        return table < 0 ? null : new CdAnimator(art.image(), table, anim);
    }

    private int table(String column) {
        return QqzObjectCatalogue.offset(loaded, column);
    }

    private void registerAll(CdObjectArtProvider art, CdStageImage stage, RomByteReader reader) throws IOException {
        if (table("bridgeMappings") < 0 && table("noroMappings1") < 0) return;     // not a Quartz Quadrant stage
        var stageEntry = CdStageCatalogue.entry(loaded);
        int ranges = table("sectionRanges");
        if (ranges >= 0 && CdLevel.nativeTimeZone(loaded.stage().period()) == 0) {
            // SectionGfxRanges, SectionGfxUpdateLists, SectionGfxInitLists: equal-length word lists, the first
            // ending in $FFFF (r5/section_gfx_1a.asm:65-79).
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
        add(art, reader, NORO1, "noroMappings1", NORO_TILE);
        add(art, reader, NORO2, "noroMappings2", NORO_TILE);
        add(art, reader, NORO_SPIKES, "noroSpikesMappings", NORO_TILE);
        add(art, reader, LARGE_BREAK_WALL, "largeBreakWallMappings", LARGE_BREAK_WALL_TILE);
        add(art, reader, BREAK_WALL, "breakWallMappings", BREAK_WALL_TILE);
        add(art, reader, KEMUSI, "kemusiMappings", KEMUSI_TILE);
        add(art, reader, SASORI1, "sasoriMappings1", SASORI_TILE);
        add(art, reader, SASORI2, "sasoriMappings2", SASORI_TILE);
        add(art, reader, SASORI_GUN, "sasoriGunMappings", SASORI_TILE);
        add(art, reader, SASORI_MISSILE, "sasoriMissileMappings", SASORI_TILE);
        add(art, reader, KUMO1, "kumoMappings1", KUMO_TILE);
        add(art, reader, KUMO2, "kumoMappings2", KUMO_TILE);
        add(art, reader, KUMO_WEB, "kumoWebMappings", KUMO_TILE);
        add(art, reader, STALACTITE, "stalactiteMappings", STALACTITE_TILE);
        add(art, reader, STALACTITE, "stalactiteMappings", STALACTITE_TILE_PAST);
        add(art, reader, CONVEYOR_SWITCH, "conveyorSwitchMappings", CONVEYOR_SWITCH_TILE);
        add(art, reader, SINE_PLATFORM, "sinePlatformMappings", SINE_PLATFORM_TILE);
        add(art, reader, CIRCLE_PLATFORM, "circlePlatformMappings", CIRCLE_PLATFORM_TILE);
        add(art, reader, BRIDGE, "bridgeMappings", BRIDGE_TILE);
        add(art, reader, SWING, "swingMappings", SWING_TILE);
        add(art, reader, SWING, "swingMappings", SWING_PLATFORM_TILE);
    }

    /** Registers the kind's sheet for every section, sharing one renderer between sections with equal tiles. */
    private void add(CdObjectArtProvider art, RomByteReader reader, String kind, String column, int tileWord) {
        int table = table(column);
        if (table < 0) return;
        List<SpriteMappingFrame> frames = CdObjectArtProvider.frames(reader, table);
        int span = CdObjectArtProvider.span(frames);
        List<byte[]> seen = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int k = 0; k < sectionVram.size(); k++) {
            Pattern[] window = sectionVram.get(k).window(tileWord & 0x7FF, span);
            byte[] bytes = new byte[window.length * Pattern.PATTERN_SIZE_IN_MEM];
            for (int i = 0; i < window.length; i++) window[i].copyInto(bytes, i * Pattern.PATTERN_SIZE_IN_MEM);
            int same = -1;
            for (int i = 0; i < seen.size(); i++) {
                if (java.util.Arrays.equals(seen.get(i), bytes)) { same = i; break; }
            }
            String key;
            if (same < 0) {
                key = PREFIX + kind + "." + Integer.toHexString(tileWord & 0xFFFF) + "." + k;
                art.register(key, new ObjectSpriteSheet(window, frames, CdObjectArtProvider.tilePalette(tileWord), 1));
            } else {
                key = keys.get(same);
            }
            seen.add(bytes);
            keys.add(key);
            sheets.put(index(kind, tileWord, k), key);
        }
    }

    private static String index(String kind, int tileWord, int section) {
        return kind + "#" + (tileWord & 0xFFFF) + "#" + section;
    }

    /** The section the camera is in: the first range bound above it (InitSectionGfx, r5/section_gfx_1a.asm:5-21). */
    private int section(int cameraX) {
        int section = 0;
        while (section < sectionBounds.length - 1 && (cameraX & 0xFFFF) >= sectionBounds[section]) section++;
        return Math.min(section, Math.max(0, sectionVram.size() - 1));
    }
}
