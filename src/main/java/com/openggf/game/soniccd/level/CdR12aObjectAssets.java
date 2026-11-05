package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.resources.CdLevelHud;
import com.openggf.game.soniccd.resources.CdLevelPaletteCycle;
import com.openggf.game.soniccd.resources.CdR11aPlcBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;

/**
 * Detached original-USA R12A object/HUD and complete finite PLC source metadata. Runtime bytes come only
 * from the existing terrain owner's immutable authenticated MMD; this owner never opens a host source.
 * Native1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Palmtree Panic/Data and Graphics Lists (Act2 Present),
 * Ring/Spring/Monitor/HUD data; General.Nemesis/LoadPLC:381-491,588-658,794-816.
 * Lazy products are immutable source data, not current patterns, art banks, queue/decoder service,
 * animation execution, renderer, programme lifetime, Frame or World admission.
 */
public final class CdR12aObjectAssets {
    public enum ReaderKind { JAVA, NATIVE }

    /** Canonical source blob, finite reader inputs and modelled Java fetch cursor are separate facts. */
    public record ReaderProfile(CdR12aProfile.Asset asset, CdR12aProfile.FileRange sourceBlobRange,
                                CdR12aProfile.FileRange javaInputRange, CdR12aProfile.FileRange nativeInputRange,
                                int outputBytes, int javaConsumedInputBytes) {
        public ReaderProfile {
            Objects.requireNonNull(asset); Objects.requireNonNull(sourceBlobRange);
            Objects.requireNonNull(javaInputRange); Objects.requireNonNull(nativeInputRange);
            if (!sourceBlobRange.equals(range(asset, asset.length()))
                    || javaInputRange.fileOffset() != asset.fileOffset() || nativeInputRange.fileOffset() != asset.fileOffset()
                    || javaInputRange.length() < 2 || nativeInputRange.length() < 2
                    || outputBytes <= 0 || outputBytes > 0x10000 || (outputBytes & 31) != 0
                    || javaConsumedInputBytes < 2 || javaConsumedInputBytes > javaInputRange.length())
                throw new IllegalArgumentException("Invalid finite R12A reader metadata");
        }
    }
    public record NemesisRead(ReaderProfile profile, int consumedInputBytes, CdAssetBytes bytes) {
        public NemesisRead {
            Objects.requireNonNull(profile); Objects.requireNonNull(bytes);
            if (consumedInputBytes != profile.javaConsumedInputBytes() || bytes.length() != profile.outputBytes())
                throw new IllegalArgumentException("Decoded R12A product differs from its finite reader contract");
        }
    }
    public record PlcEntry(CdR12aProfile.Asset source, CdR12aProfile.FileRange sourceRange, int destinationWord) {
        public PlcEntry {
            Objects.requireNonNull(source); Objects.requireNonNull(sourceRange);
            if (!sourceRange.equals(range(source, source.length())) || (destinationWord & ~0xFFFF) != 0
                    || (destinationWord & 31) != 0)
                throw new IllegalArgumentException("PLC source range or destination differs");
        }
        /** Immutable descriptor value only; no queue is created, appended or serviced. */
        public CdR11aPlcBuffer.Entry descriptor() { return new CdR11aPlcBuffer.Entry(sourceRange.nativeAddress(), destinationWord); }
    }
    public record PlcList(int nativeId, CdR12aProfile.FileRange bodyRange, List<PlcEntry> entries) {
        public PlcList { Objects.requireNonNull(bodyRange); entries = List.copyOf(entries); }
    }

    private static final List<ReaderProfile> READERS = List.of(
            reader(ART_ANIMALS, 640, 446, 446, 445),
            reader(ART_ANTON, 704, 484, 485, 484),
            reader(ART_BIG_RING, 3392, 1034, 1034, 1033),
            reader(ART_BIG_RING_FLASH, 2464, 384, 385, 384),
            reader(ART_BOULDER, 608, 270, 271, 270),
            reader(ART_CHECKPOINT, 384, 212, 212, 211),
            reader(ART_DIAGONAL_SPRING, 1472, 780, 780, 779),
            reader(ART_EXPLOSIONS, 2240, 1256, 1257, 1256),
            reader(ART_FLOAT_BLOCK, 480, 231, 231, 231),
            reader(ART_FLOWER, 1248, 646, 646, 645),
            reader(ART_GOAL_POST, 768, 448, 449, 448),
            reader(ART_HUD, 864, 300, 301, 300),
            reader(ART_LEVEL_TILES, 25952, 10723, 10723, 10723),
            reader(ART_LOG_SHADOW, 512, 24, 24, 23),
            reader(ART_MONITOR_TIME_POSTS, 2624, 1541, 1541, 1541),
            reader(ART_MOSQUI, 2240, 846, 847, 846),
            reader(ART_PATA_BATA, 1600, 714, 715, 714),
            reader(ART_PLATFORM, 1824, 452, 453, 452),
            reader(ART_POINTS, 160, 101, 101, 101),
            reader(ART_RESULTS, 3840, 1354, 1354, 1354),
            reader(ART_RINGS, 448, 244, 244, 244),
            reader(ART_ROBOT_GEN_WITHERED, 480, 332, 332, 332),
            reader(ART_SIGNPOST, 2432, 1500, 1501, 1500),
            reader(ART_SPIKES, 256, 86, 87, 86),
            reader(ART_SPINNING_DISC, 384, 167, 167, 167),
            reader(ART_SPRING_WHEEL, 256, 182, 182, 181),
            reader(ART_SPRINGBOARD, 672, 312, 312, 311),
            reader(ART_SPRINGS, 1152, 547, 547, 547),
            reader(ART_SWING, 448, 338, 338, 338),
            reader(ART_TAGA_TAGA, 1440, 782, 783, 782),
            reader(ART_TAMABBOH, 1888, 1142, 1143, 1142),
            reader(ART_TITLE_CARD, 3488, 940, 940, 939),
            reader(ART_TITLE_CARD_TEXT, 1664, 298, 298, 298),
            reader(ART_TUNNEL_DOOR, 384, 154, 154, 153),
            reader(ART_TUNNEL_DOOR_SPLASH, 2752, 687, 687, 687));
    private static final Map<CdR12aProfile.Asset, ReaderProfile> BY_ASSET;
    static {
        var values = new EnumMap<CdR12aProfile.Asset, ReaderProfile>(CdR12aProfile.Asset.class);
        for (var reader : READERS) if (values.put(reader.asset(), reader) != null)
            throw new IllegalStateException("Duplicate R12A source metadata");
        BY_ASSET = Map.copyOf(values);
    }

    private final CdR12aTerrainAssets terrain;
    private final CdMmdImage image;
    private final Map<CdR12aProfile.Asset, CdAssetBytes> views = new EnumMap<>(CdR12aProfile.Asset.class);
    private final Map<CdR12aProfile.Asset, CdAssetBytes> javaInputs = new EnumMap<>(CdR12aProfile.Asset.class);
    private final Map<CdR12aProfile.Asset, CdAssetBytes> nativeInputs = new EnumMap<>(CdR12aProfile.Asset.class);
    private final Map<CdR12aProfile.Asset, NemesisRead> decoded = new EnumMap<>(CdR12aProfile.Asset.class);
    private List<PlcList> plcs;
    private CdObjectPlacements placements;
    private List<CdR11aObjectAssets.Delta> deltas;
    private List<CdR11aObjectAssets.TouchSize> dimensions;
    private List<CdR11aLoadPlan.SectionArt> sections;
    private List<CdLevelPaletteCycle.Script> paletteCycles;
    private CdObjectVisualAssets rings, monitorTime, springVertical, springHorizontal, springDiagonal, titleCard, breakableWall, boulder, platform, anton, pataBataNormal, pataBataDamaged, tamabbohNormal, tamabbohDamaged, tamabbohMissile, floatBlock, checkpoint;
    private CdAssetBytes springMappings;
    private CdAssetBytes blocksNativeReader;
    private CdLevelHud.Assets hud;

    /** Only the actual authenticated terrain owner calls this; no public arbitrary-image ingress. */
    CdR12aObjectAssets(CdR12aTerrainAssets terrain, CdMmdImage image) {
        this.terrain = Objects.requireNonNull(terrain, "actual R12A terrain owner");
        this.image = Objects.requireNonNull(image, "retained original R12A image");
    }
    public CdR12aTerrainAssets.Identity identity() { return terrain.identity(); }
    public CdR12aProfile profile() { return terrain.profile(); }
    public synchronized CdAssetBytes view(CdR12aProfile.Asset asset) {
        Objects.requireNonNull(asset, "asset");
        return views.computeIfAbsent(asset, a -> profile().range(a).read(image));
    }
    public List<ReaderProfile> readerProfiles() { return READERS; }
    public ReaderProfile readerProfile(CdR12aProfile.Asset asset) {
        var value = BY_ASSET.get(Objects.requireNonNull(asset, "asset"));
        if (value == null) throw new IllegalArgumentException("No finite R12A PLC-art reader for " + asset);
        return value;
    }
    public synchronized CdAssetBytes readerView(CdR12aProfile.Asset asset, ReaderKind kind) {
        var reader = readerProfile(asset); Objects.requireNonNull(kind, "reader kind");
        var selected = kind == ReaderKind.JAVA ? reader.javaInputRange() : reader.nativeInputRange();
        if (selected.equals(reader.sourceBlobRange())) return view(asset);
        var cache = kind == ReaderKind.JAVA ? javaInputs : nativeInputs;
        return cache.computeIfAbsent(asset, a -> selected.read(image));
    }
    /** Shared Nemesis data decoder; no current/resident pattern value is created. */
    public synchronized NemesisRead decode(CdR12aProfile.Asset asset) throws IOException {
        var reader = readerProfile(asset); var cached = decoded.get(asset);
        if (cached != null) return cached;
        // Terrain has already decoded this exact source; borrow its actual immutable product.
        var result = asset == ART_LEVEL_TILES ? terrain.levelTilesDecode()
                : CdNemesisAsset.decode(readerView(asset, ReaderKind.JAVA).copy(), reader.outputBytes(), reader.outputBytes());
        if (result.consumedInputBytes() != reader.javaConsumedInputBytes())
            throw new IOException("R12A Nemesis Java cursor differs for " + asset);
        var value = new NemesisRead(reader, result.consumedInputBytes(), result.bytes()); decoded.put(asset, value); return value;
    }
    /**
     * Fixed native NemDecToRAM consumer view. Canonical LevelBlocks stays4600 bytes; the pinned
     * post-symbol refill model reaches the genuine first Art_LevelTiles byte at$23CAF2, so this
     * source-bound view is4601 bytes (SHA256231f95494373efc60532fa5c48aaf14c684a0c0f71e001fa814fa9fb6c5322eb).
     * General.NemDecToRAM/NemDecMain:395-491; PP Load Level Data:11-22, N1ecc5f3a.
     * No padding, arbitrary range read, decoder call, host reopen or current-pattern work occurs here.
     */
    public synchronized CdAssetBytes blocksNativeReaderView() {
        if (blocksNativeReader == null) blocksNativeReader = range(LEVEL_BLOCKS, 4601).read(image);
        return blocksNativeReader;
    }
    public synchronized List<PlcList> plcLists() {
        if (plcs == null) plcs = decodePlcLists(view(PLC_LISTS).copy());
        return plcs;
    }
    /**
     * Pure finite format seam, not media authority. Existing R11A/B PLC parsers hard-bind their source enums;
     * this source-local binding retains the same signed directory/count/6-byte row format without inventing
     * a foreign R11A/B profile or a generic asset framework. General.LoadPLC/LoadPLCImm:588-616,794-816.
     */
    public static List<PlcList> decodePlcLists(byte[] input) {
        var data = CdAssetBytes.copyOf(Objects.requireNonNull(input, "PLC input"));
        if (data.length() != PLC_LISTS.length()) throw new IllegalArgumentException("Exact R12A PLC directory/body extent required");
        var lists = new ArrayList<PlcList>();
        for (int id = 0; id < 19; id++) {
            int offset = (short) data.unsignedWord(id * 2);
            if (offset < 38 || (offset & 1) != 0 || offset > data.length() - 2)
                throw new IllegalArgumentException("R12A PLC signed directory pointer outside declared bodies");
            int count = data.unsignedWord(offset) + 1;
            if (count > 14 || 2 + count * 6 > data.length() - offset)
                throw new IllegalArgumentException("R12A PLC count/body exceeds its finite source");
            var entries = new ArrayList<PlcEntry>();
            for (int row = 0; row < count; row++) {
                long pointer = data.unsignedLong(offset + 2 + row * 6); var source = plcSource(pointer);
                int destination = data.unsignedWord(offset + 6 + row * 6);
                if ((destination & 31) != 0 || destination + source.outputBytes() > 0x10000)
                    throw new IllegalArgumentException("R12A PLC destination is unaligned or exceeds the pattern address range");
                entries.add(new PlcEntry(source.asset(), source.sourceBlobRange(), destination));
            }
            var body = new CdR12aProfile.FileRange(CdNativeFile.R12A, CdMmdImage.Domain.ORIGINAL_WORD_RAM,
                    PLC_LISTS.fileOffset() + offset, 2 + count * 6);
            lists.add(new PlcList(id, body, entries));
        }
        return List.copyOf(lists);
    }
    private static ReaderProfile plcSource(long pointer) {
        for (var source : READERS) if (source.sourceBlobRange().nativeAddress() == pointer) return source;
        throw new IllegalArgumentException("PLC pointer is not a known untagged R12A compressed source");
    }
    public synchronized CdObjectPlacements placements() {
        if (placements == null) placements = CdObjectPlacements.decodeLayout(view(OBJECT_LAYOUTS).copy());
        return placements;
    }
    public synchronized List<CdR11aObjectAssets.Delta> ringDeltas() {
        if (deltas == null) deltas = CdR11aObjectAssets.decodeRingDeltas(view(RING_DELTAS).copy());
        return deltas;
    }
    public synchronized List<CdR11aObjectAssets.TouchSize> touchDimensions() {
        if (dimensions == null) dimensions = CdR11aObjectAssets.decodeTouchDimensions(view(TOUCH_DIMENSIONS).copy());
        return dimensions;
    }
    public synchronized CdObjectVisualAssets rings() {
        if (rings == null) rings = CdObjectVisualAssets.decode(view(MAP_SPR_RING).copy(), 9, view(ANI_RING).copy(), 1, 14);
        return rings;
    }
    /** Nine native roots; title/text PLC destinations bound the relative pattern extent. */
    public synchronized CdObjectVisualAssets titleCard() {
        if (titleCard == null) titleCard = CdObjectVisualAssets.decodeStatic(view(TITLE_CARD_MAPPINGS).copy(), 9, 164);
        return titleCard;
    }
    /** Fourteen counted wall roots use the existing57-tile Platform source; no new art/reader/current value. */
    public synchronized CdObjectVisualAssets breakableWall() {
        if (breakableWall == null) breakableWall = CdObjectVisualAssets.decodeStatic(view(MAP_SPR_BREAKABLE_WALL).copy(), 14, 57);
        return breakableWall;
    }
    /** One complete counted root and its parent EVEN byte; existing nineteen-tile Boulder source. */
    public synchronized CdObjectVisualAssets boulder() {
        if (boulder == null) boulder = CdObjectVisualAssets.decodeStatic(view(MAP_SPR_BOULDER).copy(), 1, 19);
        return boulder;
    }
    /** All three native Init branches select this same three-root table; no animation or art decode. */
    public synchronized CdObjectVisualAssets platform() {
        if (platform == null) platform = CdObjectVisualAssets.decodeStatic(view(MAP_SPR_PLATFORM).copy(), 3, 57);
        return platform;
    }
    /** Complete four-frame/two-script Anton data over the existing twenty-two-tile art source. */
    public synchronized CdObjectVisualAssets anton() {
        if (anton == null) anton = CdObjectVisualAssets.decode(view(MAP_SPR_ANTON).copy(), 4, view(ANI_ANTON).copy(), 2, 22);
        return anton;
    }
    /** Normal and damaged three-root views share the same actual raw Ani_PataBata source. */
    public synchronized CdObjectVisualAssets pataBataNormal() {
        if (pataBataNormal == null) pataBataNormal = CdObjectVisualAssets.decode(view(MAP_SPR_PATA_BATA1).copy(), 3, view(ANI_PATA_BATA).copy(), 2, 50);
        return pataBataNormal;
    }
    public synchronized CdObjectVisualAssets pataBataDamaged() {
        if (pataBataDamaged == null) pataBataDamaged = CdObjectVisualAssets.decode(view(MAP_SPR_PATA_BATA2).copy(), 3, view(ANI_PATA_BATA).copy(), 2, 50);
        return pataBataDamaged;
    }
    /** Both body views retain the full62 include; normal frame0 has its actual twelve-byte nested-table tail. */
    public synchronized CdObjectVisualAssets tamabbohNormal() {
        if (tamabbohNormal == null) tamabbohNormal = CdObjectVisualAssets.decode(view(MAP_SPR_TAMABBOH).copy(), 3,
                view(ANI_TAMABBOH_NORMAL).copy(), 2, 59, Map.of(0, 12));
        return tamabbohNormal;
    }
    public synchronized CdObjectVisualAssets tamabbohDamaged() {
        if (tamabbohDamaged == null) tamabbohDamaged = CdObjectVisualAssets.decodeIndexed(view(MAP_SPR_TAMABBOH).copy(), 22, 3,
                28, 62, view(ANI_TAMABBOH_DAMAGED).copy(), 2, 59);
        return tamabbohDamaged;
    }
    public synchronized CdObjectVisualAssets tamabbohMissile() {
        if (tamabbohMissile == null) tamabbohMissile = CdObjectVisualAssets.decode(view(MAP_SPR_TAMABBOH_MISSILE).copy(), 4,
                view(ANI_TAMABBOH_MISSILE).copy(), 1, 59);
        return tamabbohMissile;
    }
    /** The native empty frame0 and all three FF/FC scripts remain complete original source values. */
    public synchronized CdObjectVisualAssets floatBlock() {
        if (floatBlock == null) floatBlock = CdObjectVisualAssets.decode(view(MAP_SPR_FLOAT_BLOCK).copy(), 6,
                view(ANI_FLOAT_BLOCK).copy(), 3, 15);
        return floatBlock;
    }
    /** Four counted roots and one complete FF script over the existing twelve-tile Checkpoint source. */
    public synchronized CdObjectVisualAssets checkpoint() {
        if (checkpoint == null) checkpoint = CdObjectVisualAssets.decode(view(MAP_SPR_CHECKPOINT).copy(), 4,
                view(ANI_CHECKPOINT).copy(), 1, 12);
        return checkpoint;
    }
    public synchronized CdObjectVisualAssets monitorTime() {
        if (monitorTime == null) monitorTime = CdObjectVisualAssets.decode(view(MAP_SPR_MONITOR_TIME).copy(), 20, view(ANI_MONITOR).copy(), 10, 82);
        return monitorTime;
    }
    /** Canonical include is199; this separately named consumer view retains its real parent EVEN byte. */
    public CdR12aProfile.FileRange springMappingsReadRange() { return range(MAP_SPRING, 200); }
    public synchronized CdAssetBytes springMappings() {
        if (springMappings == null) springMappings = springMappingsReadRange().read(image);
        return springMappings;
    }
    public synchronized CdObjectVisualAssets springVertical() {
        if (springVertical == null) springVertical = CdObjectVisualAssets.decodeIndexed(springMappings().copy(), 0, 3, 12, 46, view(ANI_SPRING).copy(), 1, 36);
        return springVertical;
    }
    public synchronized CdObjectVisualAssets springHorizontal() {
        if (springHorizontal == null) springHorizontal = CdObjectVisualAssets.decodeIndexed(springMappings().copy(), 6, 3, 46, 80, view(ANI_SPRING).copy(), 1, 36);
        return springHorizontal;
    }
    public synchronized CdObjectVisualAssets springDiagonal() {
        if (springDiagonal == null) springDiagonal = CdObjectVisualAssets.decodeIndexed(springMappings().copy(), 80, 3, 86, 200, view(ANI_SPRING).copy(), 1, 46);
        return springDiagonal;
    }
    public synchronized CdLevelHud.Assets hudAssets() {
        if (hud == null) hud = new CdLevelHud.Assets(view(ART_HUD_NUMBERS), view(HUD_RESET_TILES), view(HUD_DIVISORS));
        return hud;
    }
    /** SectionRanges/SectionUpdatePLCs/SectionInitPLCs are three separate five-WORD roots. */
    public synchronized List<CdR11aLoadPlan.SectionArt> sections() {
        if (sections == null) {
            var data = view(SECTION_DATA); var result = new ArrayList<CdR11aLoadPlan.SectionArt>();
            for (int i = 0; i < 5; i++) result.add(new CdR11aLoadPlan.SectionArt(data.unsignedWord(i * 2), data.unsignedWord(20 + i * 2), data.unsignedWord(10 + i * 2)));
            sections = List.copyOf(result);
        }
        return sections;
    }
    /** Six existing Present scripts, each8 bytes followed by its genuine6 colour bytes; no pass runs here. */
    public synchronized List<CdLevelPaletteCycle.Script> paletteCycles() {
        if (paletteCycles == null) {
            var data = view(PALETTE_CYCLE_DATA); var result = new ArrayList<CdLevelPaletteCycle.Script>();
            for (int i = 0; i < 6; i++) result.add(new CdLevelPaletteCycle.Script(data.slice(i * 14, 8), data.slice(i * 14 + 8, 6)));
            paletteCycles = List.copyOf(result);
        }
        return paletteCycles;
    }
    private static ReaderProfile reader(CdR12aProfile.Asset asset, int output, int javaLength, int nativeLength, int cursor) {
        return new ReaderProfile(asset, range(asset, asset.length()), range(asset, javaLength), range(asset, nativeLength), output, cursor);
    }
    private static CdR12aProfile.FileRange range(CdR12aProfile.Asset asset, int length) {
        return new CdR12aProfile.FileRange(CdNativeFile.R12A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, asset.fileOffset(), length);
    }
}
