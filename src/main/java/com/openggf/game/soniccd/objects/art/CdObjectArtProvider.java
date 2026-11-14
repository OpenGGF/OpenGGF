package com.openggf.game.soniccd.objects.art;

import com.openggf.data.RomByteReader;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.common.CommonSpriteDataLoader;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.rings.RingMappingFrames;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sonic CD object art on the shared object-art owner. Each sheet is the VRAM window an
 * object's base tile word selects ({@code oTile}), filled by the stage's pattern load cues
 * ({@link CdStageVram}); mappings and animation scripts are read from the stage file through
 * {@link CdObjectCatalogue}. Mappings use Sonic 1's format (byte piece count, five-byte
 * pieces, signed word offsets: DrawObjects, Object Functions.asm:333-340,394-412), so the
 * Sonic 1 loader reads them. An object draws its sheet with its tile word's palette line;
 * {@link #tilePalette(int)} and {@link #tilePriority(int)} split that word.
 *
 * <p>The HUD is drawn by objects ({@code ObjHUD}), not by the shared overlay, so the HUD
 * pattern getters return {@code null}.
 */
public final class CdObjectArtProvider implements ObjectArtProvider, com.openggf.game.internal.ObjectDrawnHud {
    public static final String RING = "cd.ring";
    public static final String MONITOR = "cd.monitor";
    public static final String MONITOR_ITEM = "cd.monitor.item";
    public static final String SPRING_UP = "cd.spring.up";
    public static final String SPRING_SIDE = "cd.spring.side";
    public static final String EXPLOSION = "cd.explosion";
    public static final String POINTS = "cd.points";
    public static final String HUD = "cd.hud";
    public static final String TITLE_CARD = "cd.titlecard";
    private static final String SPRING_DIAGONAL = "cd.spring.diagonal.";
    private static final String SPIKES = "cd.spikes.";

    /** move.w #$A7AE,oTile(a1): ObjRing_Init (Ring/Main.asm:157) and ObjLostRing_Init (Ring/Main.asm:361). */
    public static final int RING_TILE = 0xA7AE;
    /** ObjMonitor_Init: move.w #$5A8,oTile(a0) (Monitor and Time Post/Main.asm:305). */
    public static final int MONITOR_TILE = 0x05A8;
    /** ObjMonitorItem_Init: move.w #$85A8,oTile(a0) (Monitor and Time Post/Main.asm:440). */
    public static final int MONITOR_ITEM_TILE = 0x85A8;
    /** ObjSpring_Init: move.w #$520,oTile(a0) (Spring/Main.asm:189). */
    public static final int SPRING_TILE = 0x0520;
    /** ObjPoints_Init: move.w #$6C6,oTile(a0) (HUD and Points/Main.asm:36). */
    public static final int POINTS_TILE = 0x06C6;
    /** ObjHUD_Init: move.w #$8568,oTile(a0) (HUD and Points/Main.asm:90). */
    public static final int HUD_TILE = 0x8568;
    /** ObjTitleCard_Init: move.w #$8360,oTile(a0) (Title Card/Main.asm:46). */
    public static final int TITLE_CARD_TILE = 0x8360;
    /** SetObjectTileID IDs read by the spikes and the diagonal spring (Spikes/Main.asm:24; Spring/Main.asm:218). */
    public static final int TILE_ID_SPIKES = 0x0A;
    public static final int TILE_ID_DIAGONAL_SPRING = 0x0F;
    /** Main.asm:600 LoadLifeIcon: VDP command $74200002 writes VRAM $B420 (tile $5A1), 4 tiles per time zone. */
    private static final int LIFE_ICON_TILE = 0x5A1;
    private static final int LIFE_ICON_BYTES = 0x80;
    /** LevelStart .LoadStdPLCs: moveq #1,d0; bsr.w LoadPLCImm (Level/Main.asm:163-165). */
    private static final int STANDARD_PLC = 1;

    private final Supplier<CdLevel> levels;
    private final Supplier<CdStageImage> images;
    private final Map<String, ObjectSpriteSheet> sheets = new LinkedHashMap<>();
    private final Map<String, PatternSpriteRenderer> renderers = new HashMap<>();
    private final Map<String, SpriteAnimationSet> animations = new HashMap<>();
    private CdStagePayload loaded;
    private CdStageImage image;
    private CdObjectCatalogue.Entry entry;
    private CdStageVram vram;

    public CdObjectArtProvider(Supplier<CdLevel> levels, Supplier<CdStageImage> images) {
        this.levels = levels;
        this.images = images;
    }

    @Override
    public void loadArtForZone(int zoneIndex) throws IOException {
        CdLevel level = levels.get();
        CdStageImage stage = images.get();
        if (level == null || stage == null) throw new IllegalStateException("Sonic CD object art needs a loaded stage");
        if (level.payload() == loaded && stage == image) return;
        sheets.clear();
        renderers.clear();
        animations.clear();
        image = stage;
        loaded = level.payload();
        entry = CdObjectCatalogue.entry(loaded);
        vram = loadVram(stage, loaded);
        RomByteReader reader = stage.reader();

        register(RING, sheet(reader, entry.ringMappings(), RING_TILE));
        animations.put(RING, animationSet(reader, entry.ringAnimations()));
        register(MONITOR, sheet(reader, entry.monitorMappings(), MONITOR_TILE));
        animations.put(MONITOR, animationSet(reader, entry.monitorAnimations()));
        register(MONITOR_ITEM, monitorItemSheet(reader));
        register(SPRING_UP, sheet(reader, entry.springMappings1(), SPRING_TILE));
        register(SPRING_SIDE, sheet(reader, entry.springMappings2(), SPRING_TILE));
        animations.put(SPRING_UP, animationSet(reader, entry.springAnimations()));
        for (int tile : objectTileIds(TILE_ID_DIAGONAL_SPRING)) {
            register(SPRING_DIAGONAL + tile, sheet(reader, entry.springMappings3(), tile));
        }
        if (entry.hasSpikes()) {
            for (int tile : objectTileIds(TILE_ID_SPIKES)) register(SPIKES + tile, sheet(reader, entry.spikesMappings(), tile));
        }
        register(EXPLOSION, sheet(reader, entry.explosionMappings(), entry.explosionTile()));
        animations.put(EXPLOSION, animationSet(reader, entry.explosionAnimations()));
        register(POINTS, sheet(reader, entry.pointsMappings(), POINTS_TILE));
        register(HUD, sheet(reader, entry.hudMappings(), HUD_TILE));
        register(TITLE_CARD, sheet(reader, entry.titleCardMappings(), TITLE_CARD_TILE));
        // Per-owner sheets: the common fragment, then the loaded zone's fragment (each lane owns its own).
        zoneArt.clear();
        for (var fragment : com.openggf.game.soniccd.objects.SonicCdObjectRegistry.artFragments(loaded.stage().zone())) {
            fragment.registerArt(this);
        }
    }

    // --- Fragment art API: object fragments register their own sheets through these. ---

    /** The stage payload whose art is loaded. */
    public CdStagePayload payload() { return loaded; }

    /** VRAM after the level-start cues; sheets for later cues are built from {@code vram().copy()}. */
    public CdStageVram vram() { return vram; }

    /** Per-owner art state for this load (e.g. Palmtree Panic's section VRAM), cleared on every load. */
    public <T> T zoneArt(Class<T> type, java.util.function.Supplier<T> create) {
        return type.cast(zoneArt.computeIfAbsent(type, t -> create.get()));
    }

    /** The owner state registered by {@link #zoneArt(Class, java.util.function.Supplier)}, or {@code null}. */
    public <T> T zoneArt(Class<T> type) { return type.cast(zoneArt.get(type)); }

    private final Map<Class<?>, Object> zoneArt = new HashMap<>();


    /** VRAM after the level's immediate cues (Level/Main.asm:156-166, 267) and LoadLifeIcon (Level/Main.asm:599-614). */
    public static CdStageVram loadVram(CdStageImage stage, CdStagePayload payload) throws IOException {
        var stageEntry = CdStageCatalogue.entry(payload);
        CdStageVram vram = new CdStageVram();
        int levelPlc = stage.u8(stageEntry.levelDataIndex());
        if (levelPlc != 0) vram.loadPlc(stage, stageEntry.plcLists(), levelPlc);
        vram.loadPlc(stage, stageEntry.plcLists(), STANDARD_PLC);
        // LoadLifeIcon: lsl.w #7 of timeZone (bit 7 cleared) selects the period's icon.
        int timeZone = CdLevel.nativeTimeZone(payload.stage().period());
        var objects = CdObjectCatalogue.entry(payload);
        vram.write(LIFE_ICON_TILE, stage.slice(objects.lifeIconArt() + timeZone * LIFE_ICON_BYTES, LIFE_ICON_BYTES));
        return vram;
    }

    /** The shared ring owner's sheet: Ani_Ring sparkle is frames 4-7 at delay 5 (Ring/Data/Animations.asm). */
    public static RingSpriteSheet ringSheet(CdStageImage stage, CdStagePayload payload, CdStageVram vram) {
        var objects = CdObjectCatalogue.entry(payload);
        List<SpriteMappingFrame> frames = frames(stage.reader(), objects.ringMappings());
        Pattern[] patterns = vram.window(RING_TILE & 0x7FF, span(frames));
        // UpdateGlobalAnims .Rings: timer 7 (8 frames per step), 4 frames (Level/Main.asm:521-525).
        return new RingSpriteSheet(patterns, RingMappingFrames.fromMappings(withTilePriority(frames, RING_TILE)),
                tilePalette(RING_TILE), 8, 6, 4, 4);
    }

    public static int tilePalette(int tileWord) { return (tileWord >> 13) & 3; }
    public static boolean tilePriority(int tileWord) { return (tileWord & 0x8000) != 0; }
    public static String spikesKey(int tileWord) { return SPIKES + (tileWord & 0xFFFF); }
    public static String diagonalSpringKey(int tileWord) { return SPRING_DIAGONAL + (tileWord & 0xFFFF); }

    /** The stage file and catalogue entry this art came from (HUD digit art is read from it). */
    public CdStageImage image() { return image; }
    public CdObjectCatalogue.Entry entry() { return entry; }

    /**
     * SetObjectTileID (Object Tile ID Set.asm:7-17): the tile word for an object's tile-ID
     * index and secondary subtype, or -1 when the stage links the null variant.
     */
    public int objectTileId(int index, int subtype2) {
        List<Integer> tiles = objectTileIds(index);
        return subtype2 < tiles.size() ? tiles.get(subtype2) : -1;
    }

    public List<Integer> objectTileIds(int index) {
        List<Integer> out = new ArrayList<>();
        if (!entry.hasObjectTileIds()) return out;
        int table = entry.objectTileIds();
        int count = image.u16(table) / 2;
        int start = table + image.u16(table + index * 2);
        // A list runs to the next distinct list start (ObjectTileIDs word lists).
        int end = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int other = table + image.u16(table + i * 2);
            if (other > start && other < end) end = other;
        }
        if (end == Integer.MAX_VALUE) end = start + 2;
        for (int at = start; at < end; at += 2) out.add(image.u16(at));
        return out;
    }

    public ObjectSpriteSheet sheet(RomByteReader reader, int table, int tileWord) {
        return sheet(reader, table, tileWord, vram);
    }

    /** A sheet over {@code from}'s window at the tile word (a VRAM state after a later cue). */
    public ObjectSpriteSheet sheet(RomByteReader reader, int table, int tileWord, CdStageVram from) {
        List<SpriteMappingFrame> frames = frames(reader, table);
        return new ObjectSpriteSheet(from.window(tileWord & 0x7FF, span(frames)), frames, tilePalette(tileWord), 1);
    }

    /**
     * ObjMonitorItem_Init points oMap at frame {@code oAnim}'s first piece and keeps
     * {@code oMapFrame = oAnim} (Monitor and Time Post/Main.asm:451-459), so item frame k is
     * the body at that piece address plus its own k-th signed word.
     */
    private ObjectSpriteSheet monitorItemSheet(RomByteReader reader) {
        int table = entry.monitorMappings();
        List<SpriteMappingFrame> frames = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            int pieces = table + (short) reader.readU16BE(table + k * 2) + 1;
            frames.add(CommonSpriteDataLoader.loadVer1MappingFramesWithSignedOffsets(reader, pieces, k + 1).get(k));
        }
        return new ObjectSpriteSheet(vram.window(MONITOR_ITEM_TILE & 0x7FF, span(frames)), frames,
                tilePalette(MONITOR_ITEM_TILE), 1);
    }

    /** A table's frame count is its lowest positive word offset over two. */
    public static List<SpriteMappingFrame> frames(RomByteReader reader, int table) {
        int count = 256;
        for (int i = 0; i < count; i++) {
            int offset = (short) reader.readU16BE(table + i * 2);
            if (offset > 0) count = Math.min(count, offset / 2);
        }
        return CommonSpriteDataLoader.loadVer1MappingFramesWithSignedOffsets(reader, table, count);
    }

    public static int span(List<SpriteMappingFrame> frames) {
        int span = 1;
        for (SpriteMappingFrame frame : frames) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                span = Math.max(span, piece.tileIndex() + piece.widthTiles() * piece.heightTiles());
            }
        }
        return Math.min(span, CdStageVram.TILE_COUNT);
    }

    private static List<SpriteMappingFrame> withTilePriority(List<SpriteMappingFrame> frames, int tileWord) {
        if (!tilePriority(tileWord)) return frames;
        List<SpriteMappingFrame> out = new ArrayList<>(frames.size());
        for (SpriteMappingFrame frame : frames) {
            List<SpriteMappingPiece> pieces = new ArrayList<>();
            for (SpriteMappingPiece p : frame.pieces()) {
                // Pattern word + oTile: bit 15 adds modulo 16 bits, so it toggles (DrawObjects).
                pieces.add(new SpriteMappingPiece(p.xOffset(), p.yOffset(), p.widthTiles(), p.heightTiles(),
                        p.tileIndex(), p.hFlip(), p.vFlip(), p.paletteIndex(), !p.priority()));
            }
            out.add(new SpriteMappingFrame(pieces));
        }
        return out;
    }

    private static SpriteAnimationSet animationSet(RomByteReader reader, int table) {
        return CommonSpriteDataLoader.loadAnimationSet(reader, table, reader.readU16BE(table) / 2);
    }

    public void register(String key, ObjectSpriteSheet sheet) {
        sheets.put(key, sheet);
        renderers.put(key, new PatternSpriteRenderer(sheet));
    }

    @Override public PatternSpriteRenderer getRenderer(String key) { return renderers.get(key); }
    @Override public ObjectSpriteSheet getSheet(String key) { return sheets.get(key); }
    @Override public SpriteAnimationSet getAnimations(String key) { return animations.get(key); }
    @Override public int getZoneData(String key, int zoneIndex) { return -1; }
    @Override public Pattern[] getHudDigitPatterns() { return null; }
    @Override public Pattern[] getHudTextPatterns() { return null; }
    @Override public Pattern[] getHudLivesPatterns() { return null; }
    @Override public Pattern[] getHudLivesNumbers() { return null; }
    @Override public List<String> getRendererKeys() { return new ArrayList<>(sheets.keySet()); }

    @Override
    public int getRegularPatternCount() {
        return sheets.values().stream().mapToInt(sheet -> sheet.getPatterns().length).sum();
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
        int next = baseIndex;
        for (var e : sheets.entrySet()) {
            renderers.get(e.getKey()).ensurePatternsCached(graphicsManager, next);
            next += e.getValue().getPatterns().length;
        }
        return next;
    }

    @Override public boolean isReady() { return loaded != null; }
}
