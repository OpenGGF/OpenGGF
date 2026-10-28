package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;

/**
 * Immutable R11B native load metadata, not an executing loader or authenticated media capability.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Data (Act 1 Past).asm:LevelDataIndex/PLCLists; Functions (General).asm:794-816;
 * Load Level Data.asm:11-22,57-98; Section Art Load.asm:7-46; Scroll.asm:LevelSizeLoad.
 * Only CdR11bAssets.fromPrograms independently binds these formats to actual retained USA bytes.
 */
public final class CdR11bLoadPlan {
    public record TaggedPointer(long encoded) {
        public TaggedPointer {
            if (encoded < 0 || encoded > 0xFFFFFFFFL || (encoded & 0x00C00000) != 0
                    || (encoded >>> 24) > 18 || (encoded & 0x3FFFFF) < 0x200000
                    || (encoded & 0x3FFFFF) >= 0x240000) {
                throw new IllegalArgumentException("Not a finite R11B tagged local pointer");
            }
        }
        public int plcId() { return (int) (encoded >>> 24); }
        public int nativeAddress() { return (int) (encoded & 0x3FFFFF); }
    }

    public record LevelIndex(TaggedPointer levelArt, TaggedPointer blocks, int chunksNativeAddress,
                             CdAssetBytes uninterpretedTrailing, CdAssetBytes paletteIds) {
        public LevelIndex {
            Objects.requireNonNull(levelArt); Objects.requireNonNull(blocks);
            Objects.requireNonNull(uninterpretedTrailing); Objects.requireNonNull(paletteIds);
            if (chunksNativeAddress < 0x200000 || chunksNativeAddress >= 0x240000
                    || uninterpretedTrailing.length() != 2 || paletteIds.length() != 2) {
                throw new IllegalArgumentException("Invalid LevelDataIndex value widths");
            }
        }
    }

    public record InitialCentre(int centreX, int centreY) {
        public InitialCentre { word(centreX); word(centreY); }
    }
    public record Bounds(int unusedWord, int left, int right, int top, int bottom, int cameraCentreY) {
        public Bounds {
            word(unusedWord); word(left); word(right); word(top); word(bottom); word(cameraCentreY);
            if (left > right || top > bottom) { throw new IllegalArgumentException("Reversed R11B bounds"); }
        }
    }

    public record PlcEntry(CdR11bAssets.Asset source, CdR11bAssets.FileRange sourceRange, int vramAddress) {
        public PlcEntry {
            Objects.requireNonNull(source); Objects.requireNonNull(sourceRange); word(vramAddress);
            if (!source.isPlcSource() || !source.range().equals(sourceRange) || (vramAddress & 31) != 0
                    || source.readerProfile().orElseThrow().outputBytes() > 0x10000 - vramAddress) {
                throw new IllegalArgumentException("PLC row source, destination or bounded output differs");
            }
        }
    }
    public record PlcList(int nativeId, CdR11bAssets.FileRange bodyRange, List<PlcEntry> entries) {
        public PlcList {
            Objects.requireNonNull(bodyRange); entries = List.copyOf(entries);
            if (nativeId < 0 || nativeId >= 19 || entries.isEmpty() || entries.size() > 15
                    || bodyRange.length() != 2 + 6 * entries.size()) {
                throw new IllegalArgumentException("PLC metadata slot/count/extent");
            }
        }
    }
    public record SectionArt(int rightThresholdWord, int initialPlcId, int updatePlcId) {
        public SectionArt {
            word(rightThresholdWord);
            if (initialPlcId < 0 || initialPlcId >= 19 || updatePlcId < 0 || updatePlcId >= 19) {
                throw new IllegalArgumentException("Section PLC ID outside native directory");
            }
        }
    }
    public record PaletteBinding(int nativeId, int destinationWord, int longwordCounter,
                                 CdR11bAssets.Asset source, CdAssetBytes bytes) {
        public PaletteBinding {
            Objects.requireNonNull(source); Objects.requireNonNull(bytes);
            word(destinationWord); word(longwordCounter);
            if (nativeId < 0 || nativeId >= 6 || (source != PAL_SONIC && source != PAL_LEVEL)
                    || bytes.length() != source.length() || (longwordCounter + 1L) * 4 != bytes.length()) {
                throw new IllegalArgumentException("Palette metadata source/count/extent");
            }
        }
        public int colorCount() { return bytes.length() / 2; }
    }

    private record ExpectedEntry(CdR11bAssets.Asset source, int destination) { }
    private static final List<Integer> BODY_OFFSETS = List.of(0xF1BE, 0xF1C6, 0xF222, 0xF1BE, 0xF35E, 0xF366, 0xF25A, 0xF2A4, 0xF2D6, 0xF308, 0xF31C, 0xF342, 0xF356, 0xF1BE, 0xF1BE, 0xF1BE, 0xF36E, 0xF222, 0xF376);
    private static final List<List<ExpectedEntry>> BODY_ROWS = List.of(
            List.of(new ExpectedEntry(ART_LEVEL_TILES, 0x0)),
            List.of(new ExpectedEntry(ART_TUNNEL_DOOR, 0x6500), new ExpectedEntry(ART_FLOAT_BLOCK, 0x6680), new ExpectedEntry(ART_TITLE_CARD, 0x6C00), new ExpectedEntry(ART_TITLE_CARD_TEXT, 0x7A00), new ExpectedEntry(ART_CHECKPOINT, 0x9000), new ExpectedEntry(ART_DIAGONAL_SPRING, 0x9200), new ExpectedEntry(ART_PLATFORM, 0x97C0), new ExpectedEntry(ART_ANIMALS, 0x9EE0), new ExpectedEntry(ART_SPRINGS, 0xA400), new ExpectedEntry(ART_HUD, 0xAD00), new ExpectedEntry(ART_MONITOR_TIME_POSTS, 0xB500), new ExpectedEntry(ART_EXPLOSIONS, 0xD000), new ExpectedEntry(ART_POINTS, 0xD8C0), new ExpectedEntry(ART_FLOWER, 0xDAE0), new ExpectedEntry(ART_RINGS, 0xF5C0)),
            List.of(new ExpectedEntry(ART_SPIKES, 0x6400), new ExpectedEntry(ART_BOULDER, 0x68C0), new ExpectedEntry(ART_GOAL_POST, 0x6B40), new ExpectedEntry(ART_SPINNING_DISC, 0x6E40), new ExpectedEntry(ART_SPRINGBOARD, 0x7020), new ExpectedEntry(ART_MOSQUI, 0x72C0), new ExpectedEntry(ART_ANTON, 0x7DA0), new ExpectedEntry(ART_TAMABBOH, 0x8060), new ExpectedEntry(ART_PATA_BATA, 0x87E0)),
            List.of(new ExpectedEntry(ART_LEVEL_TILES, 0x0)),
            List.of(new ExpectedEntry(ART_ANIMALS, 0x9EE0)),
            List.of(new ExpectedEntry(ART_PROJ_ANIMALS, 0x9EE0)),
            List.of(new ExpectedEntry(ART_SPIKES, 0x6400), new ExpectedEntry(ART_BOULDER, 0x68C0), new ExpectedEntry(ART_GOAL_POST, 0x6B40), new ExpectedEntry(ART_SPINNING_DISC, 0x6E40), new ExpectedEntry(ART_SPRINGBOARD, 0x7020), new ExpectedEntry(ART_TUNNEL_WATERFALL, 0x73C0), new ExpectedEntry(ART_SONIC_HOLE, 0x7760), new ExpectedEntry(ART_LOG_SHADOW, 0x7960), new ExpectedEntry(ART_SCENERY, 0x7B60), new ExpectedEntry(ART_ANTON, 0x7DA0), new ExpectedEntry(ART_PROJECTOR, 0x8060), new ExpectedEntry(ART_PATA_BATA, 0x87E0)),
            List.of(new ExpectedEntry(ART_SPIKES, 0x6400), new ExpectedEntry(ART_BOULDER, 0x68C0), new ExpectedEntry(ART_SPINNING_DISC, 0x6E40), new ExpectedEntry(ART_SPRINGBOARD, 0x7020), new ExpectedEntry(ART_MOSQUI, 0x72C0), new ExpectedEntry(ART_TAMABBOH, 0x8060), new ExpectedEntry(ART_PATA_BATA, 0x87E0), new ExpectedEntry(ART_GOAL_POST, 0x6B40)),
            List.of(new ExpectedEntry(ART_SPIKES, 0x6400), new ExpectedEntry(ART_BOULDER, 0x68C0), new ExpectedEntry(ART_SPINNING_DISC, 0x6E40), new ExpectedEntry(ART_SPRINGBOARD, 0x7020), new ExpectedEntry(ART_MOSQUI, 0x72C0), new ExpectedEntry(ART_TAMABBOH, 0x8060), new ExpectedEntry(ART_ROBOT_GENERATOR, 0x87E0), new ExpectedEntry(ART_GOAL_POST, 0x6B40)),
            List.of(new ExpectedEntry(ART_MOSQUI, 0x72C0), new ExpectedEntry(ART_ANTON, 0x7DA0), new ExpectedEntry(ART_TAMABBOH, 0x8060)),
            List.of(new ExpectedEntry(ART_TUNNEL_WATERFALL, 0x73C0), new ExpectedEntry(ART_SONIC_HOLE, 0x7760), new ExpectedEntry(ART_LOG_SHADOW, 0x7960), new ExpectedEntry(ART_SCENERY, 0x7B60), new ExpectedEntry(ART_ANTON, 0x7DA0), new ExpectedEntry(ART_PROJECTOR, 0x8060)),
            List.of(new ExpectedEntry(ART_MOSQUI, 0x72C0), new ExpectedEntry(ART_TAMABBOH, 0x8060), new ExpectedEntry(ART_PATA_BATA, 0x87E0)),
            List.of(new ExpectedEntry(ART_ROBOT_GENERATOR, 0x87E0)),
            List.of(new ExpectedEntry(ART_LEVEL_TILES, 0x0)),
            List.of(new ExpectedEntry(ART_LEVEL_TILES, 0x0)),
            List.of(new ExpectedEntry(ART_LEVEL_TILES, 0x0)),
            List.of(new ExpectedEntry(ART_RESULTS, 0x7880)),
            List.of(new ExpectedEntry(ART_SPIKES, 0x6400), new ExpectedEntry(ART_BOULDER, 0x68C0), new ExpectedEntry(ART_GOAL_POST, 0x6B40), new ExpectedEntry(ART_SPINNING_DISC, 0x6E40), new ExpectedEntry(ART_SPRINGBOARD, 0x7020), new ExpectedEntry(ART_MOSQUI, 0x72C0), new ExpectedEntry(ART_ANTON, 0x7DA0), new ExpectedEntry(ART_TAMABBOH, 0x8060), new ExpectedEntry(ART_PATA_BATA, 0x87E0)),
            List.of(new ExpectedEntry(ART_SIGNPOST, 0x8780), new ExpectedEntry(ART_BIG_RING, 0x9100), new ExpectedEntry(ART_BIG_RING_FLASH, 0x7DE0))
    );
    private static final List<SectionArt> SECTIONS = List.of(new SectionArt(0x1100, 2, 9),
            new SectionArt(0x1A00, 6, 10), new SectionArt(0x2000, 7, 11), new SectionArt(0xFFFF, 8, 12));
    private static final CdMmdHeader HEADER = new CdMmdHeader(0, 0, 0, 0, 0x200100, 0x20010C, 0x200112);
    private final LevelIndex levelIndex;
    private final InitialCentre initialCentre;
    private final Bounds bounds;
    private final List<PlcList> plcs;
    private final List<SectionArt> sections;
    private final List<PaletteBinding> paletteBindings;
    private final CdAssetBytes initialPalette;

    private CdR11bLoadPlan(LevelIndex levelIndex, InitialCentre initialCentre, Bounds bounds, List<PlcList> plcs,
                           List<SectionArt> sections, List<PaletteBinding> paletteBindings, byte[] palette) {
        this.levelIndex = levelIndex; this.initialCentre = initialCentre; this.bounds = bounds;
        this.plcs = List.copyOf(plcs); this.sections = List.copyOf(sections);
        this.paletteBindings = List.copyOf(paletteBindings); this.initialPalette = CdAssetBytes.copyOf(palette);
    }

    /** Component metadata only. Header/shape agreement cannot authenticate an image as actual Past media. */
    public static CdR11bLoadPlan decode(CdMmdImage image) {
        Objects.requireNonNull(image);
        if (image.fileBytes() != 262144 || !HEADER.equals(image.header())
                || image.fileBackedCopyBytes() != 0 || image.unresolvedCopyBytes() != 0) {
            throw new IllegalArgumentException("R11B metadata requires its original finite image shape");
        }
        var index = decodeLevelIndex(read(image, LEVEL_DATA_INDEX).copy());
        var centre = decodeInitialCentre(read(image, LEVEL_START_LOC).copy());
        var bounds = decodeBounds(read(image, CAM_BOUNDS).copy());
        validateTerrainPointers(image);
        var plcs = decodePlcs(read(image, PLC_LISTS));
        var sections = decodeSections(read(image, SECTION_DATA));
        var table = read(image, PALETTE_TABLE);
        var sonic = palette(image, table, 3, PAL_SONIC, 0xFB00, 7);
        var level = palette(image, table, 5, PAL_LEVEL, 0xFB20, 23);
        byte[] colors = new byte[128];
        System.arraycopy(sonic.bytes().copy(), 0, colors, 0, 32);
        System.arraycopy(level.bytes().copy(), 0, colors, 32, 96);
        return new CdR11bLoadPlan(index, centre, bounds, plcs, sections, List.of(sonic, level), colors);
    }

    /** High tags remain PLC3/2; $0081 is retained without inventing its meaning. */
    public static LevelIndex decodeLevelIndex(byte[] input) {
        var data = exact(input, 16, "LevelDataIndex");
        var art = new TaggedPointer(data.unsignedLong(0));
        var blocks = new TaggedPointer(data.unsignedLong(4));
        if (art.plcId() != 3 || art.nativeAddress() != 0x23CABE || blocks.plcId() != 2
                || blocks.nativeAddress() != 0x23BA3A || data.unsignedLong(8) != 0x210000
                || data.unsignedWord(14) != 0x0505) {
            throw new IllegalArgumentException("Unverified R11B index tags, pointers or palette IDs");
        }
        if (data.unsignedWord(12) != 0x0081) { throw new UnsupportedOperationException("Unverified R11B trailing index bytes"); }
        return new LevelIndex(art, blocks, 0x210000, data.slice(12, 2), data.slice(14, 2));
    }

    /** Default data only; this does not claim the native live-spawn2 path reads it. */
    public static InitialCentre decodeInitialCentre(byte[] input) {
        var data = exact(input, 4, "LevelStartLoc");
        return new InitialCentre(data.unsignedWord(0), data.unsignedWord(2));
    }
    public static Bounds decodeBounds(byte[] input) {
        var data = exact(input, 12, "CamBounds");
        return new Bounds(data.unsignedWord(0), data.unsignedWord(2), data.unsignedWord(4),
                data.unsignedWord(6), data.unsignedWord(8), data.unsignedWord(10));
    }

    /** LoadPLCImm enters its row before DBF; $FFFF is not an empty-list sentinel. No art is decoded here. */
    public static List<PlcEntry> decodePlcBody(byte[] input) {
        Objects.requireNonNull(input);
        if (input.length < 2 || input.length > 92) { throw new IllegalArgumentException("PLC body extent"); }
        var data = CdAssetBytes.copyOf(input); int count = data.unsignedWord(0) + 1;
        if (count > 15 || input.length != 2 + 6 * count) { throw new IllegalArgumentException("PLC count/extent mismatch"); }
        List<PlcEntry> rows = new ArrayList<>();
        for (int row = 0; row < count; row++) {
            var source = sourceAt(data.unsignedLong(2 + 6 * row));
            rows.add(new PlcEntry(source, source.range(), data.unsignedWord(6 + 6 * row)));
        }
        return List.copyOf(rows);
    }

    private static CdR11bAssets.Asset sourceAt(long pointer) {
        for (var source : CdR11bAssets.Asset.values()) {
            if (source.isPlcSource() && source.range().nativeAddress() == pointer) { return source; }
        }
        throw new IllegalArgumentException("PLC pointer is not a known untagged R11B art source");
    }
    private static List<PlcList> decodePlcs(CdAssetBytes data) {
        List<PlcList> lists = new ArrayList<>(); int base = PLC_LISTS.fileOffset();
        for (int slot = 0; slot < 19; slot++) {
            int offset = (short) data.unsignedWord(2 * slot);
            if (offset < 38 || (offset & 1) != 0 || offset > data.length() - 2) {
                throw new IllegalArgumentException("PLC signed directory pointer outside reviewed span");
            }
            int count = data.unsignedWord(offset) + 1;
            if (count > 15 || 2 + 6 * count > data.length() - offset) {
                throw new IllegalArgumentException("PLC body exceeds reviewed R11B span");
            }
            int length = 2 + 6 * count;
            var entries = decodePlcBody(data.slice(offset, length).copy());
            var expected = BODY_ROWS.get(slot);
            if (base + offset != BODY_OFFSETS.get(slot) || entries.size() != expected.size()) {
                throw new IllegalArgumentException("R11B PLC slot, alias or row count differs");
            }
            for (int row = 0; row < entries.size(); row++) {
                if (entries.get(row).source() != expected.get(row).source()
                        || entries.get(row).vramAddress() != expected.get(row).destination()) {
                    throw new IllegalArgumentException("R11B PLC ordered source/destination differs");
                }
            }
            lists.add(new PlcList(slot, new CdR11bAssets.FileRange(com.openggf.game.soniccd.CdNativeFile.R11B,
                    CdMmdImage.Domain.ORIGINAL_WORD_RAM, base + offset, length), entries));
        }
        return List.copyOf(lists);
    }

    private static List<SectionArt> decodeSections(CdAssetBytes data) {
        List<SectionArt> sections = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            var section = new SectionArt(data.unsignedWord(row * 2), data.unsignedWord(16 + row * 2),
                    data.unsignedWord(8 + row * 2));
            if (!section.equals(SECTIONS.get(row))) { throw new IllegalArgumentException("R11B section table differs"); }
            sections.add(section);
        }
        return List.copyOf(sections);
    }

    private static PaletteBinding palette(CdMmdImage image, CdAssetBytes table, int id,
                                           CdR11bAssets.Asset source, int destination, int counter) {
        int row = id * 8;
        if (table.unsignedLong(row) != source.range().nativeAddress() || table.unsignedWord(row + 4) != destination
                || table.unsignedWord(row + 6) != counter) {
            throw new IllegalArgumentException("R11B palette binding differs");
        }
        return new PaletteBinding(id, destination, counter, source, read(image, source));
    }

    private static void validateTerrainPointers(CdMmdImage image) {
        var layouts = read(image, LEVEL_LAYOUTS); int base = LEVEL_LAYOUTS.fileOffset();
        if (base + (short) layouts.unsignedWord(0) != LAYOUT_FG.fileOffset()
                || base + (short) layouts.unsignedWord(2) != LAYOUT_BG.fileOffset()) {
            throw new IllegalArgumentException("R11B live layout pointers differ");
        }
        var collisions = read(image, LEVEL_COL_INDEX);
        for (int i = 0; i < 8; i++) {
            if (collisions.unsignedLong(i * 4) != LEVEL_COLLISION.range().nativeAddress()) {
                throw new IllegalArgumentException("R11B collision pointer differs");
            }
        }
    }

    public LevelIndex levelIndex() { return levelIndex; }
    public InitialCentre initialCentre() { return initialCentre; }
    public Bounds bounds() { return bounds; }
    public List<PlcList> plcs() { return plcs; }
    public List<SectionArt> sections() { return sections; }
    public List<PaletteBinding> paletteBindings() { return paletteBindings; }
    public CdAssetBytes initialPalette() { return initialPalette; }
    public int colorWord(int index) { Objects.checkIndex(index, 64); return initialPalette.unsignedWord(index * 2); }

    private static CdAssetBytes read(CdMmdImage image, CdR11bAssets.Asset source) {
        return CdAssetBytes.copyOf(image.readFile(source.fileOffset(), source.length()));
    }
    private static CdAssetBytes exact(byte[] input, int length, String name) {
        Objects.requireNonNull(input);
        if (input.length != length) { throw new IllegalArgumentException(name + " extent"); }
        return CdAssetBytes.copyOf(input);
    }
    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException("Not an unsigned WORD"); }
    }
}
