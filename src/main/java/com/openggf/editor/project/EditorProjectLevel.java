package com.openggf.editor.project;

import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RingObjectPlacementMapping;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/** Complete editor data. Game-owned runtime presentation is supplied separately by the host. */
public final class EditorProjectLevel extends AbstractLevel implements RingObjectPlacementMapping {
    private final int side;
    private final java.util.Map<ObjectSpawn, List<RingSpawn>> backing;
    private final int omittedNativeChunks;
    private final int omittedNativeBlocks;

    EditorProjectLevel(EditorProjectState state, RingSpriteSheet sheet) {
        this(state, sheet, 0, 0);
    }

    private EditorProjectLevel(EditorProjectState state, RingSpriteSheet sheet, int omittedNativeChunks, int omittedNativeBlocks) {
        super(state.zoneIndex());
        state.validate();
        side = state.blockGridSide();
        this.omittedNativeChunks = omittedNativeChunks;
        this.omittedNativeBlocks = omittedNativeBlocks;
        patterns = new Pattern[state.patterns().length]; patternCount = patterns.length;
        for (int p = 0; p < patterns.length; p++) {
            patterns[p] = new Pattern();
            for (int i = 0; i < 64; i++) patterns[p].setPixel(i % 8, i / 8, state.patterns()[p][i]);
        }
        chunks = new Chunk[state.chunks().length]; chunkCount = chunks.length;
        for (int i = 0; i < chunks.length; i++) { chunks[i] = new Chunk(); chunks[i].restoreState(state.chunks()[i]); }
        blocks = new Block[state.blocks().length]; blockCount = blocks.length;
        for (int i = 0; i < blocks.length; i++) { blocks[i] = new Block(side); blocks[i].restoreState(state.blocks()[i]); }
        solidTiles = new SolidTile[state.solids().length]; solidTileCount = solidTiles.length;
        for (int i = 0; i < solidTiles.length; i++) {
            EditorProjectState.SolidState solid = state.solids()[i];
            solidTiles[i] = new SolidTile(solid.index(), solid.heights().clone(), solid.widths().clone(), (byte) solid.angle());
        }
        palettes = new Palette[state.palettes().length];
        for (int p = 0; p < palettes.length; p++) {
            palettes[p] = new Palette();
            for (int i = 0; i < 16; i++) palettes[p].setColor(i, new Palette.Color(state.palettes()[p][i * 3],
                    state.palettes()[p][i * 3 + 1], state.palettes()[p][i * 3 + 2]));
        }
        map = new com.openggf.level.Map(state.layers(), state.width(), state.height(), state.map());
        objects = state.objects().stream().map(EditorProjectState.ObjectState::spawn).toList();
        rings = state.rings().stream().map(r -> new RingSpawn(r.x(), r.y(), r.placementId())).toList();
        java.util.Map<Integer, ObjectSpawn> objectsById = new java.util.HashMap<>();
        java.util.Map<Integer, RingSpawn> ringsById = new java.util.HashMap<>();
        objects.forEach(o -> objectsById.put(o.layoutIndex(), o)); rings.forEach(r -> ringsById.put(r.placementId(), r));
        LinkedHashMap<ObjectSpawn, List<RingSpawn>> groups = new LinkedHashMap<>();
        for (EditorProjectState.BackingState group : state.ringBacking()) {
            groups.put(objectsById.get(group.objectPlacementId()), Arrays.stream(group.ringPlacementIds()).mapToObj(ringsById::get).toList());
        }
        backing = java.util.Collections.unmodifiableMap(groups);
        ringSpriteSheet = sheet;
        minX = state.minX(); maxX = state.maxX(); minY = state.minY(); maxY = state.maxY();
    }

    /** Exact deep copy; native layout semantics that cannot be represented are rejected. */
    public static EditorProjectLevel copyOf(Level source) {
        Objects.requireNonNull(source, "source");
        rejectNativeCollisionRemaps(source);
        return LevelObjectArt.inherit(new EditorProjectLevel(EditorProjectState.capture(source), source.getRingSpriteSheet()), source);
    }

    /** A blank layout keeps the asset library and discards stock placements/native layout features. */
    public static EditorProjectLevel blank(Level library, int width, int height) {
        Objects.requireNonNull(library, "library");
        validateDimensions(library.getChunksPerBlockSide(), width, height);
        // Asset extraction uses authored geometry immediately: native storage/camera
        // bounds and placements belong to the layout this operation discards.
        EditorProjectState.BlankCapture captured = EditorProjectState.captureBlankLibrary(library, width, height);
        EditorProjectLevel result = LevelObjectArt.inherit(new EditorProjectLevel(captured.state(), library.getRingSpriteSheet()), library);
        // Required empty terrain gets first claim on capacity, before optional spare slots.
        int emptyBlock = result.ensureEmptyBlock();
        result = withSpareSlots(result);
        result.map = new com.openggf.level.Map(2, width, height);
        Arrays.fill(result.map.getData(), (byte) emptyBlock);
        result.objects = List.of(); result.rings = List.of();
        result.minX = 0; result.maxX = width * result.getBlockPixelSize() - 1;
        result.minY = 0; result.maxY = height * result.getBlockPixelSize() - 1;
        // Reconstruct to drop discarded ring groups and isolate every data array.
        EditorProjectState state = EditorProjectState.captureWithoutBacking(result);
        return LevelObjectArt.inherit(new EditorProjectLevel(state, result.ringSpriteSheet, captured.omittedChunks(), captured.omittedBlocks()), library);
    }

    public int omittedNativeChunkCount() { return omittedNativeChunks; }
    public int omittedNativeBlockCount() { return omittedNativeBlocks; }

    /** Creation feedback; snapshots persist the resulting valid library, not unavailable native definitions. */
    public String blankLibraryNotice() {
        return omittedNativeChunks == 0 && omittedNativeBlocks == 0 ? ""
                : "Omitted " + omittedNativeChunks + " native chunks and " + omittedNativeBlocks
                    + " blocks with unavailable art or composition data. Choose another asset library for those definitions.";
    }

    static EditorProjectLevel withSpareSlots(EditorProjectLevel source) {
        int chunkCount = source.chunkCount < 1024 ? Math.min(1024, source.chunkCount + 64) : source.chunkCount;
        int blockCount = Math.min(256, source.blockCount + 16);
        EditorProjectState base = EditorProjectState.capture(source);
        // Native libraries can contain additional sprite-only art beyond the terrain
        // descriptor's 11-bit address space. Retain it; only append authoring slots
        // when there is still addressable capacity, and never overwrite stock art.
        int patternCount = base.patterns().length < 2048 ? Math.min(2048, base.patterns().length + 256) : base.patterns().length;
        byte[][] patterns = Arrays.copyOf(base.patterns(), patternCount);
        for (int i = base.patterns().length; i < patterns.length; i++) patterns[i] = new byte[64];
        int[][] chunks = Arrays.copyOf(base.chunks(), chunkCount);
        for (int i = base.chunks().length; i < chunks.length; i++) chunks[i] = new int[6];
        int[][] blocks = Arrays.copyOf(base.blocks(), blockCount);
        for (int i = base.blocks().length; i < blocks.length; i++) blocks[i] = new int[source.side * source.side];
        return LevelObjectArt.inherit(new EditorProjectLevel(new EditorProjectState(base.zoneIndex(), base.blockGridSide(), patterns, chunks, blocks,
                base.solids(), base.palettes(), base.layers(), base.width(), base.height(), base.map(), base.objects(), base.rings(), base.ringBacking(),
                base.minX(), base.maxX(), base.minY(), base.maxY()), source.ringSpriteSheet), source);
    }

    /** Shrinking requires explicit crop authorization. Entire object-backed ring groups crop together. */
    public static EditorProjectLevel resized(Level source, int width, int height, boolean allowCrop) {
        validateDimensions(source.getChunksPerBlockSide(), width, height);
        boolean shrinking = width < source.getMap().getWidth() || height < source.getMap().getHeight();
        if (!allowCrop && shrinking) {
            throw new IllegalArgumentException("Shrinking a project requires explicit crop authorization");
        }
        EditorProjectLevel library = copyOf(source);
        int emptyBlock = 0;
        if (width > source.getMap().getWidth() || height > source.getMap().getHeight()) {
            emptyBlock = library.ensureEmptyBlock();
        }
        EditorProjectState base = EditorProjectState.capture(library);
        com.openggf.level.Map resized = new com.openggf.level.Map(base.layers(), width, height);
        Arrays.fill(resized.getData(), (byte) emptyBlock);
        for (int l = 0; l < base.layers(); l++) for (int y = 0; y < Math.min(height, base.height()); y++)
            for (int x = 0; x < Math.min(width, base.width()); x++) resized.setValue(l, x, y, source.getMap().getValue(l, x, y));
        int pixelWidth = width * source.getBlockPixelSize(), pixelHeight = height * source.getBlockPixelSize();
        Set<Integer> removedObjects = new HashSet<>(), removedRings = new HashSet<>();
        if (shrinking && allowCrop) {
            base.objects().stream().filter(o -> o.x() >= pixelWidth || o.y() >= pixelHeight).forEach(o -> removedObjects.add(o.placementId()));
            base.rings().stream().filter(r -> r.x() >= pixelWidth || r.y() >= pixelHeight).forEach(r -> removedRings.add(r.placementId()));
            for (EditorProjectState.BackingState group : base.ringBacking()) {
                if (removedObjects.contains(group.objectPlacementId()) || Arrays.stream(group.ringPlacementIds()).anyMatch(removedRings::contains)) {
                    removedObjects.add(group.objectPlacementId()); for (int id : group.ringPlacementIds()) removedRings.add(id);
                }
            }
        }
        List<EditorProjectState.ObjectState> objects = base.objects().stream().filter(o -> !removedObjects.contains(o.placementId())).toList();
        List<EditorProjectState.RingState> rings = base.rings().stream().filter(r -> !removedRings.contains(r.placementId())).toList();
        List<EditorProjectState.BackingState> backing = base.ringBacking().stream().filter(g -> !removedObjects.contains(g.objectPlacementId())).toList();
        return LevelObjectArt.inherit(new EditorProjectLevel(new EditorProjectState(base.zoneIndex(), base.blockGridSide(), base.patterns(), base.chunks(), base.blocks(),
                base.solids(), base.palettes(), base.layers(), width, height, resized.getData(), objects, rings, backing,
                Math.min(base.minX(), pixelWidth - 1), pixelWidth - 1, Math.min(base.minY(), pixelHeight - 1), pixelHeight - 1), source.getRingSpriteSheet()), source);
    }

    /** Finds actual empty terrain, allocating definitions only when the library has none. */
    private int ensureEmptyBlock() {
        boolean[] transparent = new boolean[Math.min(patternCount, 2048)];
        int emptyPattern = -1;
        for (int p = 0; p < transparent.length; p++) {
            transparent[p] = true;
            for (int y = 0; y < 8 && transparent[p]; y++) for (int x = 0; x < 8; x++) {
                if (patterns[p].getPixel(x, y) != 0) { transparent[p] = false; break; }
            }
            if (transparent[p] && emptyPattern < 0) emptyPattern = p;
        }
        boolean[] emptyChunks = new boolean[Math.min(chunkCount, 1024)];
        for (int c = 0; c < emptyChunks.length; c++) {
            emptyChunks[c] = true;
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                int pattern = chunks[c].getPatternDesc(x, y).getPatternIndex();
                if (!transparent[pattern]) emptyChunks[c] = false;
            }
        }
        for (int b = 0; b < blockCount; b++) {
            boolean empty = true;
            for (int y = 0; y < side && empty; y++) for (int x = 0; x < side; x++) {
                ChunkDesc descriptor = blocks[b].getChunkDesc(x, y);
                if (descriptor.hasPrimarySolidity() || descriptor.hasSecondarySolidity()
                        || !emptyChunks[descriptor.getChunkIndex()]) { empty = false; break; }
            }
            if (empty) return b;
        }
        if (blockCount >= 256) throw new IllegalArgumentException("Asset library has no empty block or free composition slot");
        if (emptyPattern < 0) {
            if (patternCount >= 2048) throw new IllegalArgumentException("Asset library has no transparent pattern or free pattern slot");
            emptyPattern = patternCount++;
            patterns = Arrays.copyOf(patterns, patternCount); patterns[emptyPattern] = new Pattern();
        }
        int[] emptyChunkState = {emptyPattern, emptyPattern, emptyPattern, emptyPattern, 0, 0};
        int emptyChunk = -1;
        for (int c = 0; c < Math.min(chunkCount, 1024); c++) if (Arrays.equals(chunks[c].saveState(), emptyChunkState)) { emptyChunk = c; break; }
        if (emptyChunk < 0) {
            if (chunkCount >= 1024) throw new IllegalArgumentException("Asset library has no empty chunk or free composition slot");
            emptyChunk = chunkCount++;
            chunks = Arrays.copyOf(chunks, chunkCount); chunks[emptyChunk] = new Chunk(); chunks[emptyChunk].restoreState(emptyChunkState);
        }
        int emptyBlock = blockCount++;
        blocks = Arrays.copyOf(blocks, blockCount); blocks[emptyBlock] = new Block(side);
        int[] state = new int[side * side]; Arrays.fill(state, emptyChunk); blocks[emptyBlock].restoreState(state);
        return emptyBlock;
    }

    public static EditorProjectLevel withBounds(Level source, int minX, int maxX, int minY, int maxY) {
        EditorProjectState base = EditorProjectState.capture(copyOf(source));
        return LevelObjectArt.inherit(new EditorProjectLevel(new EditorProjectState(base.zoneIndex(), base.blockGridSide(), base.patterns(), base.chunks(), base.blocks(),
                base.solids(), base.palettes(), base.layers(), base.width(), base.height(), base.map(), base.objects(), base.rings(), base.ringBacking(),
                minX, maxX, minY, maxY), source.getRingSpriteSheet()), source);
    }

    static void validateDimensions(int side, int width, int height) {
        if ((side != 8 && side != 16) || width < 1 || height < 1
                || (long) width * side * 16 > 65536 || (long) height * side * 16 > 32768) {
            throw new IllegalArgumentException("Project dimensions must fit native 16-bit coordinates and 8x8/16x16 block geometry");
        }
    }

    static void validateBounds(int minX, int maxX, int minY, int maxY) {
        if (minX < 0 || maxX > 65535 || minX > maxX || minY < Short.MIN_VALUE || maxY > Short.MAX_VALUE || minY > maxY) {
            throw new IllegalArgumentException("Project bounds must be ordered native unsigned-X/signed-Y coordinates");
        }
    }

    private static void rejectNativeCollisionRemaps(Level source) {
        validateDimensions(source.getChunksPerBlockSide(), source.getMap().getWidth(), source.getMap().getHeight());
        for (int y = 0; y < source.getMap().getHeight(); y++) for (int x = 0; x < source.getMap().getWidth(); x++) {
            int block = Byte.toUnsignedInt(source.getMap().getValue(0, x, y));
            if (source.resolveCollisionBlockIndex(block, x, y) != block) {
                throw new IllegalArgumentException("Native collision remap at " + x + "," + y
                        + " cannot be cloned into a flat authored level; create a blank project with this asset library");
            }
        }
    }

    /** Complete comparison catches edits outside MutableLevel's legacy sidecar dirty tracking. */
    boolean contentEquals(Level other) {
        if (other == null || side != other.getChunksPerBlockSide() || zoneIndex != other.getZoneIndex()
                || patternCount != other.getPatternCount() || chunkCount != other.getChunkCount()
                || blockCount != other.getBlockCount() || solidTileCount != other.getSolidTileCount()
                || palettes.length != other.getPaletteCount() || map.getWidth() != other.getMap().getWidth()
                || map.getHeight() != other.getMap().getHeight() || map.getLayerCount() != other.getMap().getLayerCount()
                || minX != other.getMinX() || maxX != other.getMaxX() || minY != other.getMinY() || maxY != other.getMaxY()
                || !objects.equals(other.getObjects()) || !rings.equals(other.getRings()) || !Arrays.equals(map.getData(), other.getMap().getData())) return false;
        for (int p = 0; p < patternCount; p++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            if (patterns[p].getPixel(x, y) != other.getPattern(p).getPixel(x, y)) return false;
        for (int i = 0; i < chunkCount; i++) if (!Arrays.equals(chunks[i].saveState(), other.getChunk(i).saveState())) return false;
        for (int i = 0; i < blockCount; i++) if (!Arrays.equals(blocks[i].saveState(), other.getBlock(i).saveState())) return false;
        for (int i = 0; i < solidTileCount; i++) {
            SolidTile tile = other.getSolidTile(i);
            if (solidTiles[i].getIndex() != tile.getIndex() || solidTiles[i].getAngle() != tile.getAngle()
                    || !Arrays.equals(solidTiles[i].heights, tile.heights) || !Arrays.equals(solidTiles[i].widths, tile.widths)) return false;
        }
        for (int i = 0; i < palettes.length; i++) if (!palettes[i].dataEquals(other.getPalette(i))) return false;
        java.util.Map<ObjectSpawn, List<RingSpawn>> otherBacking = other instanceof RingObjectPlacementMapping provider
                ? provider.ringObjectPlacementMapping() : java.util.Map.of();
        return backing.equals(otherBacking);
    }

    @Override public int getChunksPerBlockSide() { return side; }
    @Override public int getBlockPixelSize() { return side * 16; }
    @Override public int getPaletteCount() { return palettes.length; }
    @Override public java.util.Map<ObjectSpawn, List<RingSpawn>> ringObjectPlacementMapping() { return backing; }
}
