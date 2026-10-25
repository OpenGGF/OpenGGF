package typinghog.play;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArt;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.ObjectVisualGraphicsAccess;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.metadata.ObjectArtPreviewSource;
import com.openggf.level.objects.AnimalType;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.resources.CompressionType;
import com.openggf.level.resources.PlcParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The selected section's detached ROM sprite sheets. Bosses that normally install their art at
 * the far end of an act are decoded through the engine's ROM loaders, without submitting PLCs,
 * changing native VRAM, or borrowing another zone's boss. The course owns this presentation data.
 *
 * <p>Prepare after the selected world's initial presentation is ready, install with
 * {@link Stagehand#sectionArt(SectionArt)}, and call {@link Stagehand#clearSectionArt()} on load,
 * leave and session close. Stand/retry clears retain it. GPU residency is lazy and uses an owned
 * presentation bank; a graphics reset reacquires that bank, while close releases only our bank.
 */
public final class SectionArt implements AutoCloseable {
    /** One actual table/effect frame; shared by preparation and the ROM-backed art gate. */
    public record Use(String source, String artKey, int frame) { }

    private final Map<String, ObjectSpriteSheet> sheets;
    private final List<Use> uses;
    private final Map<String, PatternSpriteRenderer> renderers = new LinkedHashMap<>();
    private ObjectVisualGraphicsAccess.ArtBank bank;
    private GraphicsManager graphics;
    private boolean closed;

    private SectionArt(Map<String, ObjectSpriteSheet> sheets, List<Use> uses) {
        this.sheets = Map.copyOf(sheets);
        this.uses = List.copyOf(uses);
    }

    /** Decodes and checks every frame this section can draw, before presenting its first fight. */
    public static SectionArt prepare(ObjectServices services, String game, int zone, int act,
                                     Cast cast, Props props) {
        var render = services.renderManager();
        ObjectArtProvider provider = render.getArtProvider();
        List<Use> uses = uses(cast, props, zone, act, render.getAnimalTypeA(), render.getAnimalTypeB());
        Set<String> wanted = new LinkedHashSet<>();
        for (Use use : uses) wanted.add(use.artKey());
        Map<String, ObjectSpriteSheet> sources = new LinkedHashMap<>();
        for (String key : wanted) {
            ObjectSpriteSheet sheet = provider.getSheet(key);
            if (sheet != null) sources.put(key, sheet);
        }
        try {
            if (!sources.keySet().containsAll(wanted) && provider instanceof ObjectArtPreviewSource preview) {
                preview.editorPreviewSheets(services.currentLevel()).forEach((key, sheet) -> {
                    if (wanted.contains(key) && sheet != null) sources.putIfAbsent(key, sheet);
                });
            }
            if (game.equals("s3k")) {
                loadS3k(sources, wanted, services.currentLevel(), services.rom(), services.romReader(), zone, act);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot prepare " + game + ":" + zone + ":" + act + " ROM art", failure);
        }
        List<String> problems = new ArrayList<>();
        for (Use use : uses) {
            String issue = problem(sources.get(use.artKey()), use.frame());
            if (issue != null) problems.add(use.source() + " " + use.artKey() + "[" + use.frame() + "]: " + issue);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Section " + game + ":" + zone + ":" + act
                    + " cannot draw its ROM art:\n" + String.join("\n", problems));
        }
        Map<String, ObjectSpriteSheet> copies = new LinkedHashMap<>();
        for (String key : wanted) copies.put(key, copy(sources.get(key)));
        return new SectionArt(copies, uses);
    }

    private static List<Use> uses(Cast cast, Props props, int zone, int act, int animalA, int animalB) {
        Set<Use> uses = new LinkedHashSet<>();
        for (Species species : cast.zone(zone, act)) {
            for (int frame : species.frames()) uses.add(new Use("cast " + species.name(), species.artKey(), frame));
        }
        BossRig boss = cast.boss(zone, act);
        if (boss == null || boss.parts().isEmpty()) throw new IllegalArgumentException("Section has no boss rig");
        for (BossRig.Part part : boss.parts()) {
            for (BossRig.Mood mood : BossRig.Mood.values()) {
                BossRig.Anim anim = part.anim(mood);
                if (anim != null) {
                    for (int frame : anim.frames()) uses.add(new Use("boss " + part.name(), part.artKey(), frame));
                }
            }
            if (part.links() != null) {
                uses.add(new Use("boss " + part.name() + " links", part.links().artKey(), part.links().frame()));
            }
        }
        for (String name : List.of("monitor_eggman", "monitor_life", "monitor_shoes", "monitor_shield",
                "monitor_invincible", "monitor_rings")) {
            Props.Art prop = props.get(name);
            if (prop != null) uses.add(new Use("prop " + name, prop.artKey(), prop.frame()));
        }
        for (int frame = 0; frame < Effects.EXPLOSION_FRAMES; frame++) uses.add(new Use("effect", "explosion", frame));
        for (int frame = 0; frame < 4; frame++) uses.add(new Use("effect", "points", frame));
        int[] animals = {animalA, animalB};
        for (int variant = 0; variant < animals.length; variant++) {
            int mapping = AnimalType.fromIndex(animals[variant]).mappingSet().ordinal();
            for (int pose = 0; pose < 3; pose++) {
                uses.add(new Use("effect animal", "animal", (mapping * 2 + variant) * 3 + pose));
            }
        }
        return List.copyOf(uses);
    }

    private static void loadS3k(Map<String, ObjectSpriteSheet> sheets, Set<String> wanted, Level level,
                                Rom rom, RomByteReader reader, int zone, int act) throws IOException {
        var art = new Sonic3kObjectArt(level, reader);
        for (var entry : Sonic3kPlcArtRegistry.getPlan(zone, act).standaloneArt()) {
            if (wanted.contains(entry.key())) sheets.put(entry.key(), art.loadStandaloneSheet(rom, entry));
        }
        if (wanted.contains("robotnik_ship")) {
            standalone(sheets, art, rom, "robotnik_ship", Sonic3kConstants.ART_NEM_ROBOTNIK_SHIP_ADDR,
                    CompressionType.NEMESIS, Sonic3kConstants.MAP_ROBOTNIK_SHIP_ADDR, 0, -1);
        }
        // CNZ's ordinary provider intentionally waits for the real arena. Reading that PLC's
        // bytes here creates only a private sheet, never a native load/admission request.
        if (wanted.contains("cnz_end_boss")) {
            var parts = PlcParser.decompressAll(rom,
                    Sonic3kPlcLoader.parsePlc(rom, Sonic3kConstants.PLC_CNZ_END_BOSS));
            if (!parts.isEmpty()) sheets.put("cnz_end_boss", new ObjectSpriteSheet(parts.getFirst(),
                    S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_CNZ_END_BOSS_ADDR), 1, 1));
        }
        // Lava Reef's act-2 fight lives in the dedicated $1600 boss resource profile.
        if (wanted.contains("lrz_end_boss")) {
            for (var entry : Sonic3kPlcArtRegistry.getPlan(0x16, 0).standaloneArt()) {
                if (entry.key().equals("lrz_end_boss")) sheets.put(entry.key(), art.loadStandaloneSheet(rom, entry));
            }
        }
        // DEZ publishes these mappings against runtime DMA windows. Cold level pixels in those
        // windows may belong to terrain, so always decode the owning ROM archive for our sheet.
        if (wanted.contains("dez_end_boss")) {
            standalone(sheets, art, rom, "dez_end_boss", Sonic3kConstants.ART_KOSM_DEZ_END_BOSS_ADDR,
                    CompressionType.KOSINSKI_MODULED, Sonic3kConstants.MAP_DEZ_END_BOSS_ADDR, 1, 40);
        }
        if (wanted.contains("dez_miniboss")) {
            standalone(sheets, art, rom, "dez_miniboss", Sonic3kConstants.ART_KOSM_DEZ_MINIBOSS_ADDR,
                    CompressionType.KOSINSKI_MODULED, Sonic3kConstants.MAP_DEZ_MINIBOSS_ADDR, 1, 39);
        }
        if (wanted.contains("dez_robotnik_run")) {
            standalone(sheets, art, rom, "dez_robotnik_run", Sonic3kConstants.ART_NEM_FBZ_ROBOTNIK_RUN_ADDR,
                    CompressionType.NEMESIS, Sonic3kConstants.MAP_FBZ_ROBOTNIK_RUN_ADDR, 0, 7);
        }
        if (wanted.contains("typing_ddz_hull")) sheets.put("typing_ddz_hull", ddzHull(level));
        if (zone == 0x0C && wanted.contains("ddz_misc")) {
            ObjectSpriteSheet misc = sheets.get("ddz_misc");
            sheets.put("ddz_misc", new ObjectSpriteSheet(misc.getPatterns(), frames(misc), 2, misc.getFrameDelay()));
        }
        // The asteroid uses the same DDZ_MISC ROM mappings/art as the turrets, with palette 1.
        if (wanted.contains("typing_ddz_asteroid")) {
            ObjectSpriteSheet misc = art.loadStandaloneSheet(rom,
                    Sonic3kPlcArtRegistry.getPlan(0x0C, 0).standaloneArt().stream()
                            .filter(entry -> entry.key().equals("ddz_misc")).findFirst().orElseThrow());
            sheets.put("typing_ddz_asteroid", new ObjectSpriteSheet(misc.getPatterns(),
                    frames(misc), 1, misc.getFrameDelay()));
        }
    }

    private record Tile(Pattern pixels, boolean flipX, boolean flipY, int palette, boolean priority) { }

    /**
     * DDZ's phase-1 hull is foreground plane art, not a sprite mapping. SwScrlDdz/sub_59648
     * selects the ROM layout window at ($200,$100). Read its four 128px cells and repack their
     * decoded tiles into four independently drawn quadrants. No copied ROM bytes or native
     * plane/VRAM writes are involved. Palette and priority remain per piece; tile flips are baked.
     * A 32px patch splits at palette/priority boundaries to stay below the renderer's limits.
     */
    private static ObjectSpriteSheet ddzHull(Level level) {
        List<Pattern> patterns = new ArrayList<>();
        List<SpriteMappingFrame> frames = new ArrayList<>();
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            int sourceX = 0x200 + (quadrant & 1) * 128;
            int sourceY = 0x100 + (quadrant >> 1) * 128;
            List<SpriteMappingPiece> pieces = new ArrayList<>();
            for (int py = 0; py < 128; py += 32) for (int px = 0; px < 128; px += 32) {
                Tile[][] tiles = new Tile[4][4];
                Set<Integer> attributes = new LinkedHashSet<>();
                for (int tx = 0; tx < 4; tx++) for (int ty = 0; ty < 4; ty++) {
                    Tile tile = planeTile(level, sourceX + px + tx * 8, sourceY + py + ty * 8);
                    tiles[tx][ty] = tile;
                    if (hasPixels(tile.pixels())) attributes.add(tile.palette() | (tile.priority() ? 4 : 0));
                }
                for (int attribute : attributes) {
                    int base = patterns.size();
                    for (int tx = 0; tx < 4; tx++) for (int ty = 0; ty < 4; ty++) {
                        Tile tile = tiles[tx][ty];
                        Pattern packed = new Pattern();
                        if (tile.pixels() != null && (tile.palette() | (tile.priority() ? 4 : 0)) == attribute) {
                            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                                packed.setPixel(x, y, tile.pixels().getPixel(tile.flipX() ? 7 - x : x,
                                        tile.flipY() ? 7 - y : y));
                            }
                        }
                        patterns.add(packed);
                    }
                    pieces.add(new SpriteMappingPiece(px, py, 4, 4, base, false, false,
                            attribute & 3, (attribute & 4) != 0));
                }
            }
            frames.add(new SpriteMappingFrame(List.copyOf(pieces)));
        }
        return new ObjectSpriteSheet(patterns.toArray(Pattern[]::new), List.copyOf(frames), 0, 1);
    }

    private static Tile planeTile(Level level, int x, int y) {
        int block = level.getMap().getValue(0, x / 128, y / 128) & 0xFF;
        var chunk = level.getBlock(block).getChunkDesc((x & 127) / 16, (y & 127) / 16);
        int tx = (x & 15) / 8, ty = (y & 15) / 8;
        var pattern = level.getChunk(chunk.getChunkIndex()).getPatternDesc(
                chunk.getHFlip() ? 1 - tx : tx, chunk.getVFlip() ? 1 - ty : ty);
        return new Tile(level.getPattern(pattern.getPatternIndex()),
                chunk.getHFlip() ^ pattern.getHFlip(), chunk.getVFlip() ^ pattern.getVFlip(),
                pattern.getPaletteIndex(), pattern.getPriority());
    }

    private static boolean hasPixels(Pattern pattern) {
        if (pattern == null) return false;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) if (pattern.getPixel(x, y) != 0) return true;
        return false;
    }

    private static List<SpriteMappingFrame> frames(ObjectSpriteSheet sheet) {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        for (int i = 0; i < sheet.getFrameCount(); i++) frames.add(sheet.getFrame(i));
        return List.copyOf(frames);
    }

    private static void standalone(Map<String, ObjectSpriteSheet> sheets, Sonic3kObjectArt art, Rom rom,
                                    String key, int address, CompressionType compression, int map, int palette,
                                    int frames) throws IOException {
        sheets.put(key, art.loadStandaloneSheet(rom, new Sonic3kPlcArtRegistry.StandaloneArtEntry(
                key, address, compression, 0, map, palette, -1, frames)));
    }

    /** Null only when the requested frame has valid mappings and visible ROM pixels. */
    public static String problem(ObjectSpriteSheet sheet, int frame) {
        if (sheet == null) return "missing sheet";
        if (frame < 0 || frame >= sheet.getFrameCount()) return "frame outside " + sheet.getFrameCount();
        if (sheet.getFrame(frame).pieces().isEmpty()) return "empty mapping";
        if (sheet.getFrame(frame).pieces().size() > 80) return "mapping exceeds renderer piece budget";
        Pattern[] patterns = sheet.getPatterns();
        boolean visible = false;
        int tiles = 0;
        for (var piece : sheet.getFrame(frame).pieces()) {
            int count = piece.widthTiles() * piece.heightTiles();
            tiles += count;
            if (tiles > 512) return "mapping exceeds renderer tile budget";
            if (piece.widthTiles() < 1 || piece.widthTiles() > 4 || piece.heightTiles() < 1
                    || piece.heightTiles() > 4 || piece.tileIndex() < 0
                    || (long) piece.tileIndex() + count > patterns.length) return "mapping outside pattern bank";
            for (int tile = piece.tileIndex(); tile < piece.tileIndex() + count; tile++) {
                Pattern pattern = patterns[tile];
                if (pattern == null) return "missing mapped pattern " + tile;
                for (int y = 0; y < 8 && !visible; y++) {
                    for (int x = 0; x < 8; x++) if (pattern.getPixel(x, y) != 0) { visible = true; break; }
                }
            }
        }
        return visible ? null : "transparent mapped pixels";
    }

    private static ObjectSpriteSheet copy(ObjectSpriteSheet source) {
        Pattern[] patterns = new Pattern[source.getPatterns().length];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = new Pattern();
            patterns[i].copyFrom(source.getPatterns()[i]);
        }
        List<SpriteMappingFrame> frames = new ArrayList<>();
        for (int i = 0; i < source.getFrameCount(); i++) {
            frames.add(new SpriteMappingFrame(List.copyOf(source.getFrame(i).pieces())));
        }
        return new ObjectSpriteSheet(patterns, List.copyOf(frames), source.getPaletteIndex(), source.getFrameDelay());
    }

    public Map<String, ObjectSpriteSheet> sheets() { return sheets; }
    public List<Use> uses() { return uses; }

    /** Draws through the ordinary native sprite path, preserving per-piece plane occlusion. */
    boolean draw(String key, int frame, int x, int y, boolean flipX, boolean flipY, int palette,
                 boolean ownerHigh, boolean priorityClass, GraphicsManager host) {
        PatternSpriteRenderer renderer = renderer(key, host);
        if (renderer == null) return false;
        draw(renderer, frame, x, y, flipX, flipY, palette, ownerHigh, priorityClass, host);
        return true;
    }

    /** The visual/part owns bit 15; a shared sheet supplies mapping-piece bits independently. */
    static void draw(PatternSpriteRenderer renderer, int frame, int x, int y, boolean flipX, boolean flipY,
                     int palette, boolean ownerHigh, boolean priorityClass, GraphicsManager host) {
        if (ownerHigh && !priorityClass) return;
        boolean previousHigh = host.getCurrentSpriteHighPriority();
        int previousMask = host.getCurrentSpriteTileOcclusionPaletteMask();
        try {
            host.setCurrentSpriteHighPriority(ownerHigh);
            mask(host, priorityClass ? 0 : 0xF);
            if (ownerHigh) {
                renderer.drawFrameIndex(frame, x, y, flipX, flipY, palette);
            } else {
                renderer.drawFrameIndexFilteredByPriority(frame, x, y, flipX, flipY, palette, priorityClass);
            }
        } finally {
            host.setCurrentSpriteHighPriority(previousHigh);
            mask(host, previousMask);
        }
    }

    private static void mask(GraphicsManager host, int mask) {
        if (host.getCurrentSpriteTileOcclusionPaletteMask() == mask) return;
        host.flushPatternBatch();
        host.setCurrentSpriteTileOcclusionPaletteMask(mask);
        host.beginPatternBatch();
    }

    PatternSpriteRenderer renderer(String key, GraphicsManager host) {
        if (closed || !sheets.containsKey(key)) return null;
        if (!ObjectVisualGraphicsAccess.validArt(bank, host, this)) {
            release();
            graphics = host;
            int count = sheets.values().stream().mapToInt(sheet -> sheet.getPatterns().length).sum();
            bank = ObjectVisualGraphicsAccess.acquireArt(host, this, count);
            int base = ObjectVisualGraphicsAccess.artBase(bank, host, this);
            host.beginPatternAtlasBatch();
            try {
                for (var entry : sheets.entrySet()) {
                    var renderer = new PatternSpriteRenderer(entry.getValue(), host);
                    renderer.ensurePatternsCached(host, base);
                    renderers.put(entry.getKey(), renderer);
                    base += entry.getValue().getPatterns().length;
                }
            } catch (RuntimeException failure) {
                release();
                throw failure;
            } finally {
                host.endPatternAtlasBatch();
            }
        }
        return renderers.get(key);
    }

    private void release() {
        if (ObjectVisualGraphicsAccess.validArt(bank, graphics, this)) {
            int base = ObjectVisualGraphicsAccess.artBase(bank, graphics, this);
            int count = sheets.values().stream().mapToInt(sheet -> sheet.getPatterns().length).sum();
            for (int i = 0; i < count; i++) graphics.uncachePattern(base + i);
        }
        ObjectVisualGraphicsAccess.releaseArt(bank, this);
        bank = null;
        graphics = null;
        renderers.clear();
    }

    @Override public void close() {
        release();
        closed = true;
    }
}
