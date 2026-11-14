package com.openggf.game.soniccd.objects.ssz;

import com.openggf.data.RomByteReader;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdObjectCatalogue;
import com.openggf.game.soniccd.objects.art.CdStageVram;
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
 * Stardust Speedway object art, registered by {@link SszObjects} through the art provider's fragment
 * API; per-load state lives in the provider's zone-art slot. Table offsets come from
 * {@link SszObjectCatalogue}.
 *
 * <p>These objects' tiles arrive through later pattern load cues, not the level-start ones: the title
 * card's end loads PLC 2 (Title Card/Main.asm:145-148) and the camera's section loads its own list
 * (InitSectionGfx/UpdateSectionGfx, r7/section_gfx_1a.asm:3-58). As for Palmtree Panic, section k's VRAM is
 * modelled as the level-start VRAM plus section k's init list; the decode queue and the incremental update
 * lists are not modelled (CdStageVram). The Good and Bad Future acts 1 and 2 queue PLC 2 alone
 * (r7/section_gfx_1c.asm:3-6) and act 3 has no section code (r7/section_gfx_3c.asm:3-5), so those stages
 * use PLC 2 only.
 */
public final class SszObjectArt {
    /** Sheet kinds registered per (kind, tile word, section). */
    public static final String MESH = "mesh", SPARKS = "sparks", LAUNCH = "launch", PATH_SWAP = "pathswap",
            LAMP = "lamp", SEARCHLIGHT = "searchlight", LIGHT_EDGE = "lightedge", SPIKE_LAUNCH = "spikelaunch",
            JUMP_SPIKE_BALL = "jumpspikeball", KANABUN1 = "kanabun1", KANABUN2 = "kanabun2",
            KABASIRA = "kabasira", DANGO1 = "dango1", DANGO2 = "dango2", HOTARU1 = "hotaru1", HOTARU2 = "hotaru2", SPRING_V = "springv", SPRING_H = "springh", SPRING_45 = "spring45";

    /** The tile words each object loads into {@code oTile} (cited on each class). */
    public static final int MESH_TILE = 0x42F8, SPARKS_TILE = 0x8338, LAUNCH_TILE = 0x0300, PATH_SWAP_TILE = 0x034D,
            LAMP_TILE = 0x451C, LIGHT_EDGE_TILE = 0x82FC, SPIKE_LAUNCH_TILE = 0x038E, JUMP_SPIKE_BALL_TILE = 0x0386,
            KANABUN_TILE = 0x23E9, KABASIRA_TILE = 0x23C6;
    /** HotaruObject_2_Routine0 (objects/r7/hotaru.asm:44-49): $845D, or $8320 when {@code act} is 2. */
    public static final int HOTARU_TILE = 0x845D, HOTARU_TILE_ACT3 = 0x8320;
    /** SpringLauncherObject's spring pieces: tile $520 or $4F2 (objects/r7/launch.asm:197-204). */
    public static final int SPRING_TILE_A = 0x0520, SPRING_TILE_B = 0x04F2;
    /** SearchlightObject_0_Routine0 (objects/r7/searchlight.asm:30-37). */
    public static final int[] SEARCHLIGHT_TILES = {0xA4AF, 0x24AF, 0x44AF};
    /** PLC 2: the title card's slide-out cue (Title Card/Main.asm:145-148). */
    private static final int TITLE_CARD_PLC = 2;
    private static final String PREFIX = "cd.ssz.";

    private final CdStagePayload loaded;
    private final SszObjectCatalogue.Entry entry;
    private final List<CdStageVram> sectionVram = new ArrayList<>();
    private int[] sectionBounds = new int[0];
    private final Map<String, String> sheets = new HashMap<>();

    private SszObjectArt(CdStagePayload loaded) {
        this.loaded = loaded;
        this.entry = SszObjectCatalogue.entry(loaded);
    }

    /** Registers the Stardust Speedway sheets for the loaded stage ({@link SszObjects#registerArt}). */
    static void register(CdObjectArtProvider art) throws IOException {
        art.zoneArt(SszObjectArt.class, () -> new SszObjectArt(art.payload())).registerAll(art, art.image(), art.image().reader());
    }

    /** The renderer key of a Stardust Speedway sheet for the camera's section, or {@code null} when not loaded. */
    public static String key(CdObjectArtProvider art, String kind, int tileWord, int cameraX) {
        SszObjectArt state = art == null ? null : art.zoneArt(SszObjectArt.class);
        return state == null ? null : state.sheets.get(index(kind, tileWord, state.section(cameraX)));
    }

    /** An animator over one of the Stardust Speedway script tables, or {@code null} before art is loaded. */
    static SszAnimator animator(CdObjectArtProvider art, ToIntFunction<SszObjectCatalogue.Entry> table, int anim) {
        return art == null ? null : new SszAnimator(art.image(), table.applyAsInt(SszObjectCatalogue.entry(art.payload())), anim);
    }

    private void registerAll(CdObjectArtProvider art, CdStageImage stage, RomByteReader reader) throws IOException {
        if (entry.meshMappings() < 0 && entry.boosterMappings() < 0 && entry.pathSwapMappings() < 0) {
            return;                                                  // not a Stardust Speedway stage
        }
        var stageEntry = CdStageCatalogue.entry(loaded);
        if (entry.sectionRanges() >= 0) {
            // SectionGfxRanges, SectionGfxUpdateLists, SectionGfxInitLists: equal-length word lists, the
            // first ending in $FFFF (r7/section_gfx_1a.asm:48-58; InitSectionGfx :3-11).
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
        sheet(art, reader, MESH, entry.meshMappings(), MESH_TILE);
        sheet(art, reader, SPARKS, entry.sparksMappings(), SPARKS_TILE);
        sheet(art, reader, LAUNCH, entry.boosterMappings(), LAUNCH_TILE);
        sheet(art, reader, PATH_SWAP, entry.pathSwapMappings(), PATH_SWAP_TILE);
        sheet(art, reader, LAMP, entry.lampMappings(), LAMP_TILE);
        for (int tile : SEARCHLIGHT_TILES) sheet(art, reader, SEARCHLIGHT, entry.searchlightMappings(), tile);
        sheet(art, reader, LIGHT_EDGE, entry.lightEdgeMappings(), LIGHT_EDGE_TILE);
        sheet(art, reader, SPIKE_LAUNCH, entry.spikeLaunchMappings(), SPIKE_LAUNCH_TILE);
        sheet(art, reader, JUMP_SPIKE_BALL, entry.jumpSpikeBallMappings(), JUMP_SPIKE_BALL_TILE);
        sheet(art, reader, KANABUN1, entry.kanabunMappings1(), KANABUN_TILE);
        sheet(art, reader, KANABUN2, entry.kanabunMappings2(), KANABUN_TILE);
        sheet(art, reader, KABASIRA, entry.kabasiraMappings(), KABASIRA_TILE);
        // Dango, the shared badnik: its tiles ($245D, or $23E9 in the past, objects/r7/dango.asm:46-50) are section art too.
        for (String column : new String[] {"dangoTile", "dangoTilePast"}) {
            int tile = com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue.offset(loaded, column);
            if (tile < 0) continue;
            sheet(art, reader, DANGO1, com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue.offset(loaded, "dangoMappings1"), tile);
            sheet(art, reader, DANGO2, com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue.offset(loaded, "dangoMappings2"), tile);
        }
        for (int tile : new int[] {HOTARU_TILE, HOTARU_TILE_ACT3}) {
            sheet(art, reader, HOTARU1, entry.hotaruMappings1(), tile);
            sheet(art, reader, HOTARU2, entry.hotaruMappings2(), tile);
        }
        // The spring launcher's arm pieces draw the shared spring tables (SpringHSprites, SpringVSprites,
        // Spring45Sprites: objects/r7/launch.asm:226-241), which the common catalogue locates.
        CdObjectCatalogue.Entry common = art.entry();
        for (int tile : new int[] {SPRING_TILE_A, SPRING_TILE_B}) {
            sheet(art, reader, SPRING_V, common.springMappings1(), tile);
            sheet(art, reader, SPRING_H, common.springMappings2(), tile);
            sheet(art, reader, SPRING_45, common.springMappings3(), tile);
        }
    }

    /** Registers the kind's sheet for every section, sharing one renderer between sections with equal tiles. */
    private void sheet(CdObjectArtProvider art, RomByteReader reader, String kind, int table, int tileWord) {
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
            if (same < 0) {
                String key = PREFIX + kind + "." + Integer.toHexString(tileWord & 0xFFFF) + "." + k;
                art.register(key, new ObjectSpriteSheet(window, frames, CdObjectArtProvider.tilePalette(tileWord), 1));
                sheets.put(index(kind, tileWord, k), key);
                keys.add(key);
            } else {
                sheets.put(index(kind, tileWord, k), keys.get(same));
                keys.add(keys.get(same));
            }
            seen.add(bytes);
        }
    }

    /** Sheets are keyed by tile index and palette line; the priority bit is the object's own. */
    private static String index(String kind, int tileWord, int section) {
        return kind + "#" + (tileWord & 0x67FF) + "#" + section;
    }

    /** The section the camera is in: the first range bound above it (InitSectionGfx, r7/section_gfx_1a.asm:3-11). */
    private int section(int cameraX) {
        int section = 0;
        while (section < sectionBounds.length - 1 && (cameraX & 0xFFFF) >= sectionBounds[section]) section++;
        return Math.min(section, Math.max(0, sectionVram.size() - 1));
    }
}
