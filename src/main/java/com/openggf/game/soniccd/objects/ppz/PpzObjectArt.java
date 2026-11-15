package com.openggf.game.soniccd.objects.ppz;

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
import java.util.function.ToIntFunction;

/**
 * Palmtree Panic object art (3D ramp and plant, scenery, log shadow, waterfall, badniks), registered by
 * {@link PpzObjects} through the art provider's fragment API. Per-load state lives in the provider's
 * zone-art slot. Table offsets come from {@link PpzObjectCatalogue}.
 *
 * These objects' tiles arrive through later pattern load cues, not the level-start cues: the title
 * card's end loads PLC 2 (Title Card/Main.asm:145-148) and each camera section loads its own list
 * (LoadSectionArt/UpdateSectionArt, Section Art Load.asm:7-48). A sheet is therefore a window into the
 * VRAM of the section the camera is in. Section k's VRAM is modelled as the level-start VRAM plus
 * section k's init list (Full list); the decode queue and the incremental update lists are not modelled
 * (CdStageVram). Stages without sections (Section Art Load (Null).asm:7-10, Act 3) use PLC 2 alone.
 */
public final class PpzObjectArt {

    /** Sheet kinds registered per (kind, tile word, section); see {@link #ppzKey}. */
    public static final String PPZ_PLANT = "plant", PPZ_RAMP = "ramp", PPZ_SCENERY = "scenery",
            PPZ_LOG_SHADOW = "logshadow", PPZ_WATERFALL = "waterfall",
            PPZ_MOSQUI1 = "mosqui1", PPZ_MOSQUI2 = "mosqui2", PPZ_ANTON = "anton",
            PPZ_PATA_BATA1 = "patabata1", PPZ_PATA_BATA2 = "patabata2",
            PPZ_TAMABBOH1 = "tamabboh1", PPZ_TAMABBOH2 = "tamabboh2", PPZ_MISSILE = "tamamissile",
            PPZ_TAGA_TAGA1 = "tagataga1", PPZ_TAGA_TAGA2 = "tagataga2",
            PPZ_COLLAPSE1 = "collapse1", PPZ_COLLAPSE2 = "collapse2", PPZ_COLLAPSE3 = "collapse3",
            PPZ_COLLAPSE4 = "collapse4", PPZ_WALL = "wall", PPZ_PLATFORM = "platform", PPZ_DISC = "disc", PPZ_SPRINGBOARD = "springboard",
            PPZ_HOLE = "hole", PPZ_SWING = "swing";
    /** SetObjectTileID IDs of the spinning disc and the springboard (Spinning Disc/Main.asm:42-43, Springboard/Main.asm:122-123). */
    public static final int TILE_ID_SPINNING_DISC = 6, TILE_ID_SPRINGBOARD = 7;
    /** moveq #$11,d0; jsr SetObjectTileID (Swing/Main.asm:88-89). */
    public static final int TILE_ID_SWING = 0x11;
    /** Collapsing platform, its pieces and the breakable wall: move.w #$44BE,oTile (Collapsing Platform/Main.asm:44, Breakable Wall/Main.asm:21). */
    public static final int PLATFORM_TILE = 0x44BE;
    /** ObjSonicHole_Init: move.w #$3A0,oTile(a0), $3BB in the past (Tunnel Path/Main.asm:27-31). */
    public static final int HOLE_TILE = 0x3A0, HOLE_PAST_TILE = 0x3BB;
    /** SetObjectTileID IDs of the badniks (Mosqui/Main.asm:35-36, Pata-Bata/Main.asm:21-22, Anton/Main.asm:33-34, Taga-Taga/Main.asm:35-36, Tamabboh/Main.asm:38-39). */
    public static final int TILE_ID_MOSQUI = 0, TILE_ID_PATA_BATA = 1, TILE_ID_ANTON = 2, TILE_ID_TAGA_TAGA = 3,
            TILE_ID_TAMABBOH = 4;
    /** Obj3DPlant_Init move.w #$4424,oTile (3D Ramp/Main.asm:27); Obj3DRamp_Init move.w #$441 (3D Ramp/Main.asm:217). */
    public static final int PLANT_TILE = 0x4424, RAMP_TILE = 0x0441;
    /** ObjWaterfall_Init: move.w #$3BA,oTile(a0) (Effects/Main.asm:28). */
    public static final int WATERFALL_TILE = 0x03BA;
    /** PLC 2: the title card's slide-out cue (Title Card/Main.asm:145-148). */
    private static final int TITLE_CARD_PLC = 2;

    private final CdStagePayload loaded;
    private final PpzObjectCatalogue.Entry entry;
    private final List<CdStageVram> sectionVram = new ArrayList<>();
    private int[] sectionBounds = new int[0];
    private final Map<String, String> ppzSheets = new HashMap<>();

    private PpzObjectArt(CdStagePayload loaded) {
        this.loaded = loaded;
        this.entry = PpzObjectCatalogue.entry(loaded);
    }

    /** Registers the Palmtree Panic sheets for the loaded stage ({@link PpzObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        art.zoneArt(PpzObjectArt.class, () -> new PpzObjectArt(art.payload())).registerPpzArt(art, art.image(), art.image().reader());
    }

    /** The renderer key of a Palmtree Panic sheet for the camera's section, or {@code null} when not loaded. */
    public static String key(CdObjectArtProvider art, String kind, int tileWord, int cameraX) {
        PpzObjectArt state = art == null ? null : art.zoneArt(PpzObjectArt.class);
        return state == null ? null : state.ppzKey(kind, tileWord, cameraX);
    }

    /** An animator over one of the Palmtree Panic script tables, or {@code null} before art is loaded. */
    public static CdAnimator animator(CdObjectArtProvider art, ToIntFunction<PpzObjectCatalogue.Entry> table, int anim) {
        return art == null ? null : new CdAnimator(art.image(), table.applyAsInt(PpzObjectCatalogue.entry(art.payload())), anim);
    }

    private void registerPpzArt(CdObjectArtProvider art, CdStageImage stage, RomByteReader reader) throws IOException {
        if (entry.mosquiMappings1() < 0 && entry.plantMappings() < 0 && entry.sceneryMappings() < 0
                && entry.logShadowMappings() < 0 && entry.waterfallMappings() < 0) {
            return;                                                  // not a Palmtree Panic stage
        }
        var stageEntry = CdStageCatalogue.entry(loaded);
        if (entry.sectionRanges() >= 0) {
            // SectionRanges, SectionUpdatePLCs, SectionInitPLCs: equal-length word lists, the first ending
            // in $FFFF (Section Data (Act 1 Present).asm:7-25; LoadSectionArt, Section Art Load.asm:7-10).
            List<Integer> bounds = new ArrayList<>();
            int words = 0;
            while (true) {
                int value = stage.u16(entry.sectionRanges() + words * 2);
                words++;
                bounds.add(value);
                if (value == 0xFFFF || words > 16) break;
            }
            sectionBounds = bounds.stream().mapToInt(Integer::intValue).toArray();
            int init = entry.sectionRanges() + 4 * words;
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

        ppz(art, reader, PPZ_PLANT, entry.plantMappings(), PLANT_TILE);
        ppz(art, reader, PPZ_RAMP, entry.rampMappings(), RAMP_TILE);
        for (int tile : sceneryTiles()) ppz(art, reader, PPZ_SCENERY, entry.sceneryMappings(), tile);
        for (int tile : logShadowTiles()) ppz(art, reader, PPZ_LOG_SHADOW, entry.logShadowMappings(), tile);
        if (entry.waterfallMappings() >= 0 && entry.waterfallAnimations() >= 0) {
            ppz(art, reader, PPZ_WATERFALL, entry.waterfallMappings(), WATERFALL_TILE);
        }
        for (int tile : art.objectTileIds(TILE_ID_MOSQUI)) {
            ppz(art, reader, PPZ_MOSQUI1, entry.mosquiMappings1(), tile);
            ppz(art, reader, PPZ_MOSQUI2, entry.mosquiMappings2(), tile);
        }
        for (int tile : art.objectTileIds(TILE_ID_ANTON)) ppz(art, reader, PPZ_ANTON, entry.antonMappings(), tile);
        for (int tile : art.objectTileIds(TILE_ID_PATA_BATA)) {
            ppz(art, reader, PPZ_PATA_BATA1, entry.pataBataMappings1(), tile);
            ppz(art, reader, PPZ_PATA_BATA2, entry.pataBataMappings2(), tile);
        }
        for (int tile : art.objectTileIds(TILE_ID_TAMABBOH)) {
            ppz(art, reader, PPZ_TAMABBOH1, entry.tamabbohMappings1(), tile);
            ppz(art, reader, PPZ_TAMABBOH2, entry.tamabbohMappings2(), tile);
            // The missile copies its parent's tile word (ObjTamabboh_InitMissile, Tamabboh/Main.asm:192).
            ppz(art, reader, PPZ_MISSILE, entry.tamabbohMissileMappings(), tile);
        }
        ppz(art, reader, PPZ_COLLAPSE1, entry.collapseMappings1(), PLATFORM_TILE);
        ppz(art, reader, PPZ_COLLAPSE2, entry.collapseMappings2(), PLATFORM_TILE);
        ppz(art, reader, PPZ_COLLAPSE3, entry.collapseMappings3(), PLATFORM_TILE);
        ppz(art, reader, PPZ_COLLAPSE4, entry.collapseMappings4(), PLATFORM_TILE);
        ppz(art, reader, PPZ_WALL, entry.breakableWallMappings(), PLATFORM_TILE);
        ppz(art, reader, PPZ_PLATFORM, entry.platformMappings(), PLATFORM_TILE);
        for (int tile : art.objectTileIds(TILE_ID_SPINNING_DISC)) ppz(art, reader, PPZ_DISC, entry.spinningDiscMappings(), tile);
        for (int tile : art.objectTileIds(TILE_ID_SPRINGBOARD)) ppz(art, reader, PPZ_SPRINGBOARD, entry.springBoardMappings(), tile);
        for (int tile : art.objectTileIds(TILE_ID_SWING)) ppz(art, reader, PPZ_SWING, entry.swingMappings(), tile);
        ppz(art, reader, PPZ_HOLE, entry.sonicHoleMappings(), HOLE_TILE);
        ppz(art, reader, PPZ_HOLE, entry.sonicHoleMappings(), HOLE_PAST_TILE);
        for (int tile : art.objectTileIds(TILE_ID_TAGA_TAGA)) {
            ppz(art, reader, PPZ_TAGA_TAGA1, entry.tagaTagaMappings1(), tile);
            ppz(art, reader, PPZ_TAGA_TAGA2, entry.tagaTagaMappings2(), tile);
        }
    }

    /** Registers the kind's sheet for every section, sharing one renderer between sections with equal tiles. */
    private void ppz(CdObjectArtProvider art, RomByteReader reader, String kind, int table, int tileWord) {
        if (table < 0 || tileWord == 0) return;
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
            if (same < 0) {
                String key = PPZ_PREFIX + kind + "." + Integer.toHexString(tileWord & 0xFFFF) + "." + k;
                art.register(key, new ObjectSpriteSheet(window, frames, CdObjectArtProvider.tilePalette(tileWord), 1));
                seen.add(bytes);
                keys.add(key);
                ppzSheets.put(ppzIndex(kind, tileWord, k), key);
            } else {
                ppzSheets.put(ppzIndex(kind, tileWord, k), keys.get(same));
                seen.add(bytes);
                keys.add(keys.get(same));
            }
        }
    }

    private static final String PPZ_PREFIX = "cd.ppz.";

    private static String ppzIndex(String kind, int tileWord, int section) {
        return kind + "#" + (tileWord & 0xFFFF) + "#" + section;
    }

    /** The section the camera is in: the first {@code SectionRanges} bound above it (Section Art Load.asm:11-17). */
    private int ppzSection(int cameraX) {
        int section = 0;
        while (section < sectionBounds.length - 1 && (cameraX & 0xFFFF) >= sectionBounds[section]) section++;
        return Math.min(section, Math.max(0, sectionVram.size() - 1));
    }

    private String ppzKey(String kind, int tileWord, int cameraX) {
        return ppzSheets.get(ppzIndex(kind, tileWord, ppzSection(cameraX)));
    }

    /**
     * ObjScenery_BaseTileList rows past, present, good future with the act in the low index bit, each
     * {@code ori.w #$4000} (Scenery/Main.asm:40-62); a bad future uses the present row ({@code moveq #1,d0;
     * add.b goodFuture,d0} when timeZone is 2).
     */
    private List<Integer> sceneryTiles() {
        return baseTiles(new int[] {0x3DB, 0x46E, 0x438, 0x39F, 0x438, 0x38F}, 1);
    }

    /** ObjLogShadow_BaseTileList (Log Shadow/Main.asm:41-64): a bad future row is 2, a good future row 3. */
    private List<Integer> logShadowTiles() {
        return baseTiles(new int[] {0x3CB, 0x45E, 0x418, 0x3F0, 0x428, 0x38F, 0x428, 0x37F}, 2);
    }

    private List<Integer> baseTiles(int[] list, int futureBase) {
        var period = loaded.stage().period();
        int timeZone = CdLevel.nativeTimeZone(period);
        boolean goodFuture = period == com.openggf.game.soniccd.CdPeriod.GOOD_FUTURE;
        int row = timeZone == 2 ? futureBase + (goodFuture ? 1 : 0) : timeZone;
        int act = loaded.name().charAt(2) - '1';
        List<Integer> out = new ArrayList<>();
        int index = row * 2 + act;
        if (act >= 0 && act < 2 && index < list.length) out.add(list[index] | 0x4000);
        return out;
    }

    /** The camera's section ({@code SectionRanges}, Section Art Load.asm:11-17); 0 before art is loaded. */
    public static int section(CdObjectArtProvider art, int cameraX) {
        PpzObjectArt state = art == null ? null : art.zoneArt(PpzObjectArt.class);
        return state == null ? 0 : state.ppzSection(cameraX);
    }

    /** ObjScenery_BaseTileList's tile for the loaded stage (see {@link #sceneryTiles()}). */
    public static List<Integer> sceneryTiles(CdObjectArtProvider art) {
        return new PpzObjectArt(art.payload()).sceneryTiles();
    }

    /** ObjLogShadow_BaseTileList's tile for the loaded stage (see {@link #logShadowTiles()}). */
    public static List<Integer> logShadowTiles(CdObjectArtProvider art) {
        return new PpzObjectArt(art.payload()).logShadowTiles();
    }
}
