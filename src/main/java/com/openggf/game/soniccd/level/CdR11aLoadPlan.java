package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;

/** Immutable native load prerequisites. No camera, player, PLC queue or palette animation is executed. */
public final class CdR11aLoadPlan {
    public record TaggedPointer(long encoded) {
        public TaggedPointer {
            if (encoded < 0 || encoded > 0xFFFFFFFFL || (encoded & 0x00C00000) != 0
                    || (encoded >>> 24) > 18 || (encoded & 0x3FFFFF) < 0x200000
                    || (encoded & 0x3FFFFF) >= 0x240000) {
                throw new IllegalArgumentException("Not a supported R11A tagged program-local pointer");
            }
        }
        public int plcId() { return (int) (encoded >>> 24); }
        public int nativeAddress() { return (int) (encoded & 0x3FFFFF); }
    }
    public record LevelIndex(TaggedPointer levelArt, TaggedPointer blocks, int chunksNativeAddress,
                              CdAssetBytes uninterpretedTrailing, CdAssetBytes paletteIds) { }
    public record InitialCentre(int centreX, int centreY) { }
    public record Bounds(int unusedWord, int left, int right, int top, int bottom, int cameraCentreY) { }
    public record PlcEntry(CdR11aAsset source, CdR11aProfile.FileRange sourceRange, int vramAddress) { }
    public record PlcList(int nativeId, CdR11aProfile.FileRange bodyRange, List<PlcEntry> entries) {
        public PlcList { entries = List.copyOf(entries); }
    }
    public record SectionArt(int rightThresholdWord, int initialPlcId, int updatePlcId) { }
    public record PaletteBinding(int nativeId, int destinationWord, int longwordCounter,
                                  CdR11aAsset source, CdAssetBytes bytes) {
        public int colorCount() { return bytes.length() / 2; }
    }

    /** Pure immutable entry facts, never a LevelStart/service permit. */
    record Entry(LevelIndex index,InitialCentre centre,Bounds bounds) { }
    private final Entry entry;
    private final LevelIndex levelIndex;
    private final InitialCentre initialCentre;
    private final Bounds bounds;
    private final List<PlcList> plcs;
    private final List<SectionArt> sections;
    private final List<PaletteBinding> paletteBindings;
    private final CdAssetBytes initialPalette;

    private CdR11aLoadPlan(LevelIndex levelIndex, InitialCentre centre, Bounds bounds, List<PlcList> plcs,
                           List<SectionArt> sections, List<PaletteBinding> paletteBindings, byte[] palette) {
        this.entry = new Entry(levelIndex,centre,bounds);
        this.levelIndex = levelIndex;
        this.initialCentre = centre;
        this.bounds = bounds;
        this.plcs = List.copyOf(plcs);
        this.sections = List.copyOf(sections);
        this.paletteBindings = List.copyOf(paletteBindings);
        this.initialPalette = CdAssetBytes.copyOf(palette);
    }

    /**
     * Original USA R11A borrows the one common table product from its actual retained reader.
     * The component decoder below remains for the existing regional and constructed callers.
     * This only projects immutable values; it does not invoke a PLC, palette or camera owner.
     */
    static CdR11aLoadPlan fromOriginalReader(CdProgrammeReader actual) {
        var image = java.util.Objects.requireNonNull(actual, "actual retained reader").retainedOriginalR11Image();
        var profile = CdR11aProfile.forStage(com.openggf.game.soniccd.CdReleaseVariant.USA,
                CdPpzDataLayout.Programme.R11A.payload().stage());
        var programme = CdPpzDataLayout.Programme.R11A;
        var decodedEntry = readEntry(()->actual.view(programme.range("LevelDataIndex")),
                ()->actual.view(programme.range("LevelStartLoc")),()->actual.view(programme.range("CamBounds")),
                CdR11aLoadPlan::decodeLevelIndex,()->validateTerrainPointers(profile,image));
        var index=decodedEntry.index();var centre=decodedEntry.centre();var bounds=decodedEntry.bounds();
        var tables = actual.loadTables();
        List<PlcList> plcs = new ArrayList<>();
        for (var plc : tables.plcs()) {
            List<PlcEntry> entries = new ArrayList<>();
            for (var entry : plc.body().entries()) {
                var source = sourceAt(profile, entry.sourceNativeAddress());
                entries.add(new PlcEntry(source, profile.range(source), entry.vramAddress()));
            }
            var body = new CdR11aProfile.FileRange(profile.file(), profile.domain(),
                    plc.body().fileOffset(), 2 + plc.body().entries().size() * 6);
            plcs.add(new PlcList(plc.nativeId(), body, entries));
        }
        if (!(tables.sectionProgramme() instanceof CdProgrammeDefinition.CameraSections camera)) {
            throw new IllegalArgumentException("Original R11A camera-section product differs");
        }
        List<SectionArt> sections = new ArrayList<>();
        for (var row : camera.rows()) {
            sections.add(new SectionArt(row.threshold(), row.initial(), row.update()));
        }
        PaletteBinding sonic = originalPalette(profile, tables, 3, PAL_SONIC, 0xFB00, 7);
        PaletteBinding level = originalPalette(profile, tables, 5, PAL_LEVEL, 0xFB20, 23);
        byte[] colors = new byte[128];
        System.arraycopy(sonic.bytes().copy(), 0, colors, 0, 32);
        System.arraycopy(level.bytes().copy(), 0, colors, 32, 96);
        return new CdR11aLoadPlan(index, centre, bounds, plcs, sections, List.of(sonic, level), colors);
    }

    /** Validate the retained compatibility binding before borrowing its already parsed source. */
    private static PaletteBinding originalPalette(CdR11aProfile profile, CdProgrammeLoadTables tables, int id,
                                                   CdR11aAsset source, int destination, int counter) {
        var palette = tables.palettes().get(id);
        if (palette.nativeId() != id || palette.sourceNativeAddress() != profile.range(source).nativeAddress()
                || palette.destinationWord() != destination || palette.longwordCounter() != counter
                || (counter + 1) * 4 != source.length()) {
            throw new IllegalArgumentException("Unverified live palette binding");
        }
        return new PaletteBinding(id, destination, counter, source, palette.bytes());
    }

    /** Component decoder; only CdR11aAssetLoader binds this to authenticated supplied media. */
    public static CdR11aLoadPlan decode(CdR11aProfile profile, CdMmdImage image) {
        LevelIndex index = decodeLevelIndex(profile.range(LEVEL_DATA_INDEX).read(image).copy());
        InitialCentre centre = decodeInitialCentre(profile.range(LEVEL_START_LOC).read(image).copy());
        Bounds bounds = decodeBounds(profile.range(CAM_BOUNDS).read(image).copy());
        validateTerrainPointers(profile, image);
        List<PlcList> plcs = decodePlcs(profile, image);
        List<SectionArt> sections = decodeSections(profile, image);
        var table = profile.range(PALETTE_TABLE).read(image);
        PaletteBinding sonic = palette(profile, image, table, 3, PAL_SONIC, 0xFB00, 7);
        PaletteBinding level = palette(profile, image, table, 5, PAL_LEVEL, 0xFB20, 23);
        byte[] colors = new byte[128];
        System.arraycopy(sonic.bytes().copy(), 0, colors, 0, 32);
        System.arraycopy(level.bytes().copy(), 0, colors, 32, 96);
        return new CdR11aLoadPlan(index, centre, bounds, plcs, sections, List.of(sonic, level), colors);
    }

    /**
     * LevelDataIndex (Data (Act 1 Present).asm:11) high bytes select PLC 3 and 2.
     * LoadLevelData (Load Level Data.asm:11-22) masks the block pointer with $3FFFFF.
     * The following $0081 is retained without inventing a selector or load behavior.
     */
    public static LevelIndex decodeLevelIndex(byte[] input) {
        return decodeLevelIndex(input,0x23C4EE,0x23B0A8,0x210000,0x81,0x0505);
    }
    /** Same tagged-pointer format, checked against this authenticated programme's own sources. */
    static LevelIndex decodeLevelIndex(byte[] input,int artAddress,int blocksAddress,int chunksAddress,
                                      int trailingWord,int paletteIds) {
        var data = exact(input, 16, "LevelDataIndex");
        var art = new TaggedPointer(data.unsignedLong(0));
        var blocks = new TaggedPointer(data.unsignedLong(4));
        if (art.plcId() != 3 || art.nativeAddress() != artAddress
                || blocks.plcId() != 2 || blocks.nativeAddress() != blocksAddress
                || data.unsignedLong(8) != Integer.toUnsignedLong(chunksAddress) || data.unsignedWord(14) != paletteIds) {
            throw new IllegalArgumentException("LevelDataIndex has unverified tags, pointers or palette IDs");
        }
        if (data.unsignedWord(12) != trailingWord) {
            throw new UnsupportedOperationException("Unverified LevelDataIndex trailing bytes");
        }
        return new LevelIndex(art, blocks, chunksAddress, data.slice(12, 2), data.slice(14, 2));
    }
    /** Decode each reached input before reading the next. A refusal never reaches a later source. */
    static Entry readEntry(java.util.function.Supplier<CdAssetBytes> index,
            java.util.function.Supplier<CdAssetBytes> centre,java.util.function.Supplier<CdAssetBytes> bounds,
            java.util.function.Function<byte[],LevelIndex> decodeIndex,Runnable validatePointers){
        var selectedIndex=decodeIndex.apply(index.get().copy());
        var selectedCentre=decodeInitialCentre(centre.get().copy());
        var selectedBounds=decodeBounds(bounds.get().copy());
        validatePointers.run();return new Entry(selectedIndex,selectedCentre,selectedBounds);
    }
    Entry entry(){return entry;}

    public static InitialCentre decodeInitialCentre(byte[] input) {
        var bytes = exact(input, 4, "LevelStartLoc");
        return new InitialCentre(bytes.unsignedWord(0), bytes.unsignedWord(2));
    }
    public static Bounds decodeBounds(byte[] input) {
        var bytes = exact(input, 12, "CamBounds");
        if (bytes.unsignedWord(2) > bytes.unsignedWord(4) || bytes.unsignedWord(6) > bytes.unsignedWord(8)) {
            throw new IllegalArgumentException("Reversed R11A bounds");
        }
        return new Bounds(bytes.unsignedWord(0), bytes.unsignedWord(2), bytes.unsignedWord(4),
                bytes.unsignedWord(6), bytes.unsignedWord(8), bytes.unsignedWord(10));
    }

    /** LoadPLCImm (Functions (General).asm:794) consumes DBF count then (absolute art long, VRAM word). */
    public static List<PlcEntry> decodePlcBody(CdR11aProfile profile, byte[] input) {
        if (input.length < 2 || input.length > 86) { throw new IllegalArgumentException("PLC body extent"); }
        var bytes = CdAssetBytes.copyOf(input);
        // LoadPLCImm enters .Load before DBF, so $FFFF is not an empty-list sentinel.
        int count = bytes.unsignedWord(0) + 1;
        if (count > 14 || input.length != 2 + count * 6) { throw new IllegalArgumentException("PLC count/extent mismatch"); }
        List<PlcEntry> entries = new ArrayList<>();
        for (int row = 0; row < count; row++) {
            long pointer = bytes.unsignedLong(2 + row * 6);
            CdR11aAsset source = sourceAt(profile, pointer);
            if (source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS) {
                throw new UnsupportedOperationException("PLC Nemesis consumer is unverified for " + source.nativeLabel());
            }
            int vram = bytes.unsignedWord(6 + row * 6);
            if ((vram & 31) != 0) { throw new IllegalArgumentException("PLC VRAM destination is not tile aligned"); }
            entries.add(new PlcEntry(source, profile.range(source), vram));
        }
        return List.copyOf(entries);
    }

    private static CdR11aAsset sourceAt(CdR11aProfile profile, long nativeAddress) {
        for (CdR11aAsset asset : CdR11aAsset.values()) {
            if (asset.kind() == CdR11aAsset.Kind.SOURCE_BLOB && profile.range(asset).nativeAddress() == nativeAddress) {
                return asset;
            }
        }
        throw new IllegalArgumentException("PLC pointer is not a known untagged R11A source blob");
    }

    private static List<PlcList> decodePlcs(CdR11aProfile profile, CdMmdImage image) {
        int base = profile.range(PLC_LISTS).fileOffset();
        // Entire reviewed index and list-body span; all 19 slots, including intentional aliases.
        var bytes = new CdR11aProfile.FileRange(profile.file(), profile.domain(), base, 534).read(image);
        List<PlcList> plcs = new ArrayList<>();
        for (int slot = 0; slot < 19; slot++) {
            int offset = (short) bytes.unsignedWord(slot * 2);
            if (offset < 38 || offset > bytes.length() - 2 || (offset & 1) != 0) {
                throw new IllegalArgumentException("PLC signed relative pointer outside reviewed list bodies");
            }
            int count = bytes.unsignedWord(offset) + 1;
            if (count > 14 || 2 + count * 6 > bytes.length() - offset) {
                throw new IllegalArgumentException("PLC body exceeds program-local reviewed span");
            }
            int length = 2 + count * 6;
            var range = new CdR11aProfile.FileRange(profile.file(), profile.domain(), base + offset, length);
            plcs.add(new PlcList(slot, range, decodePlcBody(profile, bytes.slice(offset, length).copy())));
        }
        if (!plcs.get(0).bodyRange().equals(plcs.get(3).bodyRange())) {
            throw new IllegalArgumentException("PLC 3 must retain the reviewed PLC_Level alias of slot 0");
        }
        return List.copyOf(plcs);
    }

    private static List<SectionArt> decodeSections(CdR11aProfile profile, CdMmdImage image) {
        var ranges = profile.range(SECTION_RANGES).read(image);
        var initial = profile.range(SECTION_INIT_PLCS).read(image);
        var update = profile.range(SECTION_UPDATE_PLCS).read(image);
        List<SectionArt> sections = new ArrayList<>();
        int previous = -1;
        for (int i = 0; i < 5; i++) {
            int threshold = ranges.unsignedWord(i * 2);
            int initId = initial.unsignedWord(i * 2);
            int updateId = update.unsignedWord(i * 2);
            if (threshold <= previous || initId >= 19 || updateId >= 19 || (i == 4 && threshold != 0xFFFF)) {
                throw new IllegalArgumentException("Unverified section-art table");
            }
            sections.add(new SectionArt(threshold, initId, updateId));
            previous = threshold;
        }
        return List.copyOf(sections);
    }

    private static PaletteBinding palette(CdR11aProfile profile, CdMmdImage image, CdAssetBytes table, int id,
                                           CdR11aAsset source, int destination, int counter) {
        int row = id * 8;
        if (table.unsignedLong(row) != profile.range(source).nativeAddress()
                || table.unsignedWord(row + 4) != destination || table.unsignedWord(row + 6) != counter
                || (counter + 1) * 4 != source.length()) {
            throw new IllegalArgumentException("Unverified live palette binding");
        }
        return new PaletteBinding(id, destination, counter, source, profile.range(source).read(image));
    }

    private static void validateTerrainPointers(CdR11aProfile profile, CdMmdImage image) {
        var layouts = profile.range(LEVEL_LAYOUTS).read(image);
        int base = profile.range(LEVEL_LAYOUTS).fileOffset();
        validatePlanePointers(layouts,base,profile.range(LAYOUT_FG).fileOffset(),profile.range(LAYOUT_BG).fileOffset());
        var indices = profile.range(LEVEL_COL_INDEX).read(image);
        validateCollisionPointers(indices,profile.range(LEVEL_COLLISION).nativeAddress());
    }

    static void validatePlanePointers(CdAssetBytes layouts,int base,int foreground,int background){
        if(base+(short)layouts.unsignedWord(0)!=foreground||base+(short)layouts.unsignedWord(2)!=background)
            throw new IllegalArgumentException("Live R11A plane pointers differ");
    }
    static void validateCollisionPointers(CdAssetBytes indices,int collisionAddress){
        for(int i=0;i<8;i++)if(indices.unsignedLong(i*4)!=Integer.toUnsignedLong(collisionAddress))
            throw new IllegalArgumentException("LevelColIndex pointer differs");
    }
    public LevelIndex levelIndex() { return levelIndex; }
    public InitialCentre initialCentre() { return initialCentre; }
    public Bounds bounds() { return bounds; }
    public List<PlcList> plcs() { return plcs; }
    public List<SectionArt> sections() { return sections; }
    public List<PaletteBinding> paletteBindings() { return paletteBindings; }
    public CdAssetBytes initialPalette() { return initialPalette; }
    public int colorWord(int index) { java.util.Objects.checkIndex(index, 64); return initialPalette.unsignedWord(index * 2); }

    private static CdAssetBytes exact(byte[] bytes, int length, String name) {
        if (bytes.length != length) { throw new IllegalArgumentException(name + " extent"); }
        return CdAssetBytes.copyOf(bytes);
    }
}
