package com.openggf.editor.selection;

import com.openggf.editor.EditorCommand;
import com.openggf.editor.EditorSpawnFactory;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.MutableLevel;
import com.openggf.level.Level;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;

import java.util.*;

/**
 * World-cell authoring operations. Builders validate complete operations before a
 * command can reach history. Clipboard compositions are frozen; paste imports missing
 * definitions only into unreferenced slots and never edits a shared definition.
 * Native object-backed ring groups are indivisible, including boundary-crossing groups.
 */
public final class EditorRegionOperations {
    private final MutableLevel level;
    private final EditorSpawnFactory factory;

    public EditorRegionOperations(MutableLevel level, EditorSpawnFactory factory) {
        this.level = Objects.requireNonNull(level);
        this.factory = Objects.requireNonNull(factory);
    }

    /** Immutable source snapshot; object/ring coordinates remain source-world coordinates. */
    public static final class Clipboard {
        private final MutableLevel owner;
        private final Level library;
        private final java.util.Map<Integer, int[]> blockStates = new LinkedHashMap<>();
        private final java.util.Map<Integer, int[]> chunkStates = new LinkedHashMap<>();
        private final CellRectangle rectangle;
        private final List<Integer> blocks;
        private final List<ObjectSpawn> objects;
        private final List<RingSpawn> rings;
        private final java.util.Map<ObjectSpawn, List<RingSpawn>> groups;

        private Clipboard(MutableLevel owner, CellRectangle rectangle, List<Integer> blocks,
                          List<ObjectSpawn> objects, List<RingSpawn> rings,
                          java.util.Map<ObjectSpawn, List<RingSpawn>> groups) {
            this.owner = owner;
            this.library = MutableLevel.snapshot(owner);
            for (int index : blocks) {
                if (blockStates.containsKey(index)) continue;
                int[] state = library.getBlock(index).saveState();
                blockStates.put(index, state);
                for (int descriptor : state) {
                    int chunk = descriptor & 0x3FF;
                    chunkStates.computeIfAbsent(chunk, ignored -> library.getChunk(chunk).saveState());
                }
            }
            this.rectangle = rectangle;
            this.blocks = List.copyOf(blocks);
            this.objects = List.copyOf(objects);
            this.rings = List.copyOf(rings);
            this.groups = immutableGroups(groups);
        }

        /** Frozen library for ghost thumbnails; the live level may have since been undone. */
        public Level library() { return library; }
        public CellRectangle rectangle() { return rectangle; }
        public List<Integer> blocks() { return blocks; }
        public List<ObjectSpawn> objects() { return objects; }
        public List<RingSpawn> rings() { return rings; }
        public java.util.Map<ObjectSpawn, List<RingSpawn>> groups() { return groups; }
    }

    public record Cut(Clipboard clipboard, EditorCommand command) { }
    public record Preview(CellRectangle rectangle, List<Integer> blocks,
                          List<ObjectSpawn> objects, List<RingSpawn> rings) {
        public Preview {
            blocks = List.copyOf(blocks);
            objects = List.copyOf(objects);
            rings = List.copyOf(rings);
        }
    }
    private record Cell(int layer, int x, int y, int before, int after) { }
    private record Definition(boolean block, int index, int[] before, int[] after) {
        boolean imported() { return !Arrays.equals(before, after); }
    }
    private record ImportPlan(List<Integer> blocks, List<Definition> definitions) { }

    public Clipboard copy(int layer, CellRectangle rectangle) {
        requireRectangle(layer, rectangle);
        List<Integer> blocks = new ArrayList<>();
        for (int y = rectangle.y(); y < rectangle.y() + rectangle.height(); y++) {
            for (int x = rectangle.x(); x < rectangle.x() + rectangle.width(); x++) {
                blocks.add(value(layer, x, y));
            }
        }
        Set<Integer> objectIds = new HashSet<>();
        Set<Integer> ringIds = new HashSet<>();
        java.util.Map<ObjectSpawn, List<RingSpawn>> groups = new LinkedHashMap<>();
        for (var entry : level.ringObjectPlacementMapping().entrySet()) {
            if (contains(rectangle, entry.getKey().x(), entry.getKey().y())
                    || entry.getValue().stream().anyMatch(r -> contains(rectangle, r.x(), r.y()))) {
                groups.put(entry.getKey(), entry.getValue());
                objectIds.add(entry.getKey().layoutIndex());
                entry.getValue().forEach(r -> ringIds.add(r.placementId()));
            }
        }
        List<ObjectSpawn> objects = level.getObjects().stream()
                .filter(o -> objectIds.contains(o.layoutIndex()) || contains(rectangle, o.x(), o.y())).toList();
        List<RingSpawn> rings = level.getRings().stream()
                .filter(r -> ringIds.contains(r.placementId()) || contains(rectangle, r.x(), r.y())).toList();
        return new Clipboard(level, rectangle, blocks, objects, rings, groups);
    }

    public Cut cut(int layer, CellRectangle rectangle) {
        Clipboard clipboard = copy(layer, rectangle);
        return new Cut(clipboard, delete(layer, rectangle, clipboard));
    }

    public EditorCommand delete(int layer, CellRectangle rectangle) {
        return delete(layer, rectangle, copy(layer, rectangle));
    }

    private EditorCommand delete(int layer, CellRectangle rectangle, Clipboard selected) {
        int empty = findEmptyBlock();
        List<Cell> cells = rectangleCells(layer, rectangle, Collections.nCopies(selected.blocks.size(), empty));
        var before = level.snapshotObjectBackedRingState();
        Set<Integer> objects = new HashSet<>();
        selected.objects.forEach(o -> objects.add(o.layoutIndex()));
        Set<Integer> rings = new HashSet<>();
        selected.rings.forEach(r -> rings.add(r.placementId()));
        java.util.Map<ObjectSpawn, List<RingSpawn>> groups = new LinkedHashMap<>(before.mapping());
        groups.keySet().removeIf(o -> objects.contains(o.layoutIndex()));
        var after = state(before.objects().stream().filter(o -> !objects.contains(o.layoutIndex())).toList(),
                before.rings().stream().filter(r -> !rings.contains(r.placementId())).toList(), groups);
        return new RegionCommand(cells, before, after);
    }

    /** Validates a ghost without allocating placement IDs or modifying the level. */
    public Preview preview(Clipboard clipboard, int layer, int x, int y) {
        requireClipboard(clipboard);
        CellRectangle target = new CellRectangle(x, y, clipboard.rectangle.width(), clipboard.rectangle.height());
        requireRectangle(layer, target);
        clipboard.blocks.forEach(this::requireBlock);
        validateLibraryReferences(clipboard);
        int dx = Math.multiplyExact(x - clipboard.rectangle.x(), level.getBlockPixelSize());
        int dy = Math.multiplyExact(y - clipboard.rectangle.y(), level.getBlockPixelSize());
        Set<Integer> backingIds = new HashSet<>();
        clipboard.groups.keySet().forEach(o -> backingIds.add(o.layoutIndex()));
        List<ObjectSpawn> objects = new ArrayList<>();
        for (ObjectSpawn object : clipboard.objects) {
            if (object.objectKey() == null && !backingIds.contains(object.layoutIndex())
                    && !factory.canCreateObject(object.objectId())) {
                throw new IllegalArgumentException("Object ID cannot be authored for this game: " + object.objectId());
            }
            int px = Math.addExact(object.x(), dx), py = Math.addExact(object.y(), dy);
            requirePixel(px, py);
            ObjectSpawn moved = factory.moveObjectSpawn(object, px, py);
            objects.add(preserveEnvelope(object, moved));
        }
        List<RingSpawn> rings = new ArrayList<>();
        for (RingSpawn ring : clipboard.rings) {
            int px = Math.addExact(ring.x(), dx), py = Math.addExact(ring.y(), dy);
            requirePixel(px, py);
            rings.add(factory.moveRingSpawn(ring, px, py));
        }
        return new Preview(target, clipboard.blocks, objects, rings);
    }

    /** Overlapping paste reads the clipboard snapshot, never the cells being written. */
    public EditorCommand paste(Clipboard clipboard, int layer, int x, int y) {
        Preview preview = preview(clipboard, layer, x, y);
        ImportPlan imports = planImports(clipboard);
        List<Cell> cells = rectangleCells(layer, preview.rectangle, imports.blocks);
        var before = level.snapshotObjectBackedRingState();
        List<ObjectSpawn> objects = new ArrayList<>(before.objects());
        List<RingSpawn> rings = new ArrayList<>(before.rings());
        java.util.Map<Integer, ObjectSpawn> pastedObjects = new HashMap<>();
        java.util.Map<Integer, RingSpawn> pastedRings = new HashMap<>();
        for (ObjectSpawn object : preview.objects) {
            ObjectSpawn created = object.objectKey() == null
                    ? factory.createObjectSpawn(object.x(), object.y(), object.objectId(), object.subtype(),
                            object.renderFlags(), object.respawnTracked())
                    : factory.createKeyedObjectSpawn(object.x(), object.y(), object.objectKey(), object.subtype(),
                            object.renderFlags(), object.respawnTracked());
            created = preserveEnvelope(object, created);
            pastedObjects.put(object.layoutIndex(), created);
            objects.add(created);
        }
        for (RingSpawn ring : preview.rings) {
            RingSpawn created = factory.createRingSpawn(ring.x(), ring.y());
            pastedRings.put(ring.placementId(), created);
            rings.add(created);
        }
        // Stable sort preserves the native order within each 128-pixel placement column.
        objects.sort(Comparator.comparingInt(o -> o.x() & 0xFF80));
        rings.sort(Comparator.comparingInt(r -> r.x() & 0xFF80));
        java.util.Map<ObjectSpawn, List<RingSpawn>> groups = new LinkedHashMap<>(before.mapping());
        clipboard.groups.forEach((o, rs) -> groups.put(pastedObjects.get(o.layoutIndex()),
                rs.stream().map(r -> pastedRings.get(r.placementId())).toList()));
        var after = state(objects, rings, groups);
        validateIds(after);
        return new RegionCommand(cells, before, after, imports.definitions);
    }

    public EditorCommand rectangleFill(int layer, CellRectangle rectangle, int block) {
        requireRectangle(layer, rectangle);
        requireBlock(block);
        return new RegionCommand(rectangleCells(layer, rectangle,
                Collections.nCopies(Math.multiplyExact(rectangle.width(), rectangle.height()), block)), null, null);
    }

    /** Iterative four-neighbour flood fill, bounded by this map's active layer. */
    public EditorCommand floodFill(int layer, int x, int y, int block) {
        requireRectangle(layer, new CellRectangle(x, y, 1, 1));
        requireBlock(block);
        int original = value(layer, x, y);
        if (original == block) return new RegionCommand(List.of(), null, null);
        int width = level.getMap().getWidth(), height = level.getMap().getHeight();
        BitSet visited = new BitSet(Math.multiplyExact(width, height));
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        List<Cell> cells = new ArrayList<>();
        pending.add(y * width + x);
        visited.set(y * width + x);
        while (!pending.isEmpty()) {
            int index = pending.removeFirst(), cx = index % width, cy = index / width;
            if (value(layer, cx, cy) != original) continue;
            cells.add(new Cell(layer, cx, cy, original, block));
            if (cx > 0) enqueue(index - 1, visited, pending);
            if (cx + 1 < width) enqueue(index + 1, visited, pending);
            if (cy > 0) enqueue(index - width, visited, pending);
            if (cy + 1 < height) enqueue(index + width, visited, pending);
        }
        return new RegionCommand(cells, null, null);
    }

    /** Finds actual transparent, noncolliding art; index zero has no special status. */
    public int findEmptyBlock() {
        boolean[] transparent = new boolean[level.getPatternCount()];
        for (int i = 0; i < transparent.length; i++) {
            transparent[i] = true;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                if (level.getPattern(i).getPixel(x, y) != 0) transparent[i] = false;
            }
        }
        boolean[] chunks = new boolean[level.getChunkCount()];
        for (int i = 0; i < chunks.length; i++) {
            Chunk chunk = level.getChunk(i);
            chunks[i] = chunk.getSolidTileIndex() == 0 && chunk.getSolidTileAltIndex() == 0;
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                int pattern = chunk.getPatternDesc(x, y).getPatternIndex();
                if (pattern >= transparent.length || !transparent[pattern]) chunks[i] = false;
            }
        }
        for (int i = 0; i < Math.min(256, level.getBlockCount()); i++) {
            Block block = level.getBlock(i);
            boolean empty = true;
            for (int y = 0; y < block.getGridSide(); y++) for (int x = 0; x < block.getGridSide(); x++) {
                ChunkDesc desc = block.getChunkDesc(x, y);
                if ((desc.get() & 0xF000) != 0 || desc.getChunkIndex() >= chunks.length
                        || !chunks[desc.getChunkIndex()]) empty = false;
            }
            if (empty) return i;
        }
        throw new IllegalStateException("No transparent, noncolliding block in this library");
    }

    private static void enqueue(int index, BitSet visited, ArrayDeque<Integer> pending) {
        if (!visited.get(index)) { visited.set(index); pending.addLast(index); }
    }

    private void requireClipboard(Clipboard clipboard) {
        if (clipboard == null || clipboard.owner != level) {
            throw new IllegalArgumentException("Clipboard belongs to a different level library");
        }
    }

    private void requireRectangle(int layer, CellRectangle rectangle) {
        if (layer < 0 || layer >= level.getMap().getLayerCount()
                || (long) rectangle.x() + rectangle.width() > level.getMap().getWidth()
                || (long) rectangle.y() + rectangle.height() > level.getMap().getHeight()) {
            throw new IllegalArgumentException("Region exceeds active map layer bounds");
        }
    }

    private void requireBlock(int block) {
        if (block < 0 || block > 255 || block >= level.getBlockCount()) {
            throw new IllegalArgumentException("Block index exceeds map library range");
        }
    }

    private void requirePixel(int x, int y) {
        if (x < 0 || y < 0 || x >= (long) level.getMap().getWidth() * level.getBlockPixelSize()
                || y >= (long) level.getMap().getHeight() * level.getBlockPixelSize()) {
            throw new IllegalArgumentException("Placement exceeds map bounds");
        }
    }

    private boolean contains(CellRectangle rectangle, int x, int y) {
        return rectangle.containsPixel(x, y, level.getBlockPixelSize());
    }

    private int value(int layer, int x, int y) { return level.getMap().getValue(layer, x, y) & 0xFF; }

    private List<Cell> rectangleCells(int layer, CellRectangle rectangle, List<Integer> blocks) {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < rectangle.height(); y++) for (int x = 0; x < rectangle.width(); x++) {
            int before = value(layer, rectangle.x() + x, rectangle.y() + y);
            int after = blocks.get(y * rectangle.width() + x);
            if (before != after) cells.add(new Cell(layer, rectangle.x() + x, rectangle.y() + y, before, after));
        }
        return List.copyOf(cells);
    }

    private static ObjectSpawn preserveEnvelope(ObjectSpawn source, ObjectSpawn target) {
        return new ObjectSpawn(target.x(), target.y(), target.objectId(), target.subtype(), target.renderFlags(),
                target.respawnTracked(), source.rawFlags() | target.y(), target.layoutIndex(),
                target.ownerModId(), target.objectKey());
    }

    private static java.util.Map<ObjectSpawn, List<RingSpawn>> immutableGroups(
            java.util.Map<ObjectSpawn, List<RingSpawn>> groups) {
        java.util.Map<ObjectSpawn, List<RingSpawn>> copy = new LinkedHashMap<>();
        groups.forEach((o, rs) -> copy.put(o, List.copyOf(rs)));
        return Collections.unmodifiableMap(copy);
    }

    private static MutableLevel.ObjectBackedRingState state(List<ObjectSpawn> objects, List<RingSpawn> rings,
                                                           java.util.Map<ObjectSpawn, List<RingSpawn>> groups) {
        return new MutableLevel.ObjectBackedRingState(List.copyOf(objects), List.copyOf(rings), immutableGroups(groups));
    }

    private static void validateIds(MutableLevel.ObjectBackedRingState state) {
        Set<Integer> objects = new HashSet<>(), rings = new HashSet<>();
        for (ObjectSpawn object : state.objects()) {
            if (object.layoutIndex() < 0 || !objects.add(object.layoutIndex())) throw new IllegalArgumentException("Duplicate object identity");
        }
        for (RingSpawn ring : state.rings()) {
            if (ring.placementId() < 0 || !rings.add(ring.placementId())) throw new IllegalArgumentException("Duplicate ring identity");
        }
    }

    /** Validates lower library references without changing their native IDs. */
    private void validateLibraryReferences(Clipboard clipboard) {
        for (int[] state : clipboard.chunkStates.values()) {
            for (int i = 0; i < Chunk.PATTERNS_PER_CHUNK; i++) {
                PatternDesc desc = new PatternDesc(state[i]);
                if (desc.getPatternIndex() >= level.getPatternCount()
                        || desc.getPaletteIndex() >= level.getPaletteCount()) {
                    throw new IllegalArgumentException("Clipboard pattern or palette reference exceeds this library");
                }
            }
            for (int i = Chunk.PATTERNS_PER_CHUNK; i < state.length; i++) {
                // Zero is the native no-collision profile, including libraries without profiles.
                if (state[i] < 0 || (state[i] != 0 && state[i] >= level.getSolidTileCount())) {
                    throw new IllegalArgumentException("Clipboard collision profile exceeds this library");
                }
            }
        }
    }

    /** Plans every remap before IDs are allocated or a definition can be written. */
    private ImportPlan planImports(Clipboard clipboard) {
        List<Definition> definitions = new ArrayList<>();
        java.util.Map<Integer, Integer> chunks = new LinkedHashMap<>();
        java.util.Map<Integer, int[]> plannedChunks = new LinkedHashMap<>();
        for (var entry : clipboard.chunkStates.entrySet()) {
            int destination = findIdentical(false, entry.getValue(), plannedChunks, entry.getKey());
            if (destination < 0) destination = findFree(false, entry.getValue().length, plannedChunks.keySet());
            if (!plannedChunks.containsKey(destination)) {
                int[] before = level.getChunk(destination).saveState();
                plannedChunks.put(destination, entry.getValue());
                definitions.add(new Definition(false, destination, before, entry.getValue()));
            }
            chunks.put(entry.getKey(), destination);
        }
        java.util.Map<Integer, Integer> blocks = new LinkedHashMap<>();
        java.util.Map<Integer, int[]> plannedBlocks = new LinkedHashMap<>();
        for (var entry : clipboard.blockStates.entrySet()) {
            int[] remapped = entry.getValue().clone();
            for (int i = 0; i < remapped.length; i++) {
                remapped[i] = (remapped[i] & ~0x3FF) | chunks.get(remapped[i] & 0x3FF);
            }
            int destination = findIdentical(true, remapped, plannedBlocks, entry.getKey());
            if (destination < 0) destination = findFree(true, remapped.length, plannedBlocks.keySet());
            if (!plannedBlocks.containsKey(destination)) {
                int[] before = level.getBlock(destination).saveState();
                plannedBlocks.put(destination, remapped);
                definitions.add(new Definition(true, destination, before, remapped));
            }
            blocks.put(entry.getKey(), destination);
        }
        return new ImportPlan(clipboard.blocks.stream().map(blocks::get).toList(), List.copyOf(definitions));
    }

    private int findIdentical(boolean block, int[] desired, java.util.Map<Integer, int[]> planned, int preferred) {
        // Retain an unchanged source slot before considering an equivalent duplicate.
        int[] preferredState = planned.get(preferred);
        if (preferredState != null && Arrays.equals(desired, preferredState)) return preferred;
        if (preferredState == null && Arrays.equals(desired, definitionState(block, preferred))) return preferred;
        for (var entry : planned.entrySet()) {
            if (Arrays.equals(desired, entry.getValue())) return entry.getKey();
        }
        int limit = Math.min(block ? 256 : 1024, block ? level.getBlockCount() : level.getChunkCount());
        for (int i = 0; i < limit; i++) {
            if (!planned.containsKey(i) && Arrays.equals(desired, definitionState(block, i))) return i;
        }
        return -1;
    }

    private int findFree(boolean block, int stateLength, Set<Integer> reserved) {
        int limit = Math.min(block ? 256 : 1024, block ? level.getBlockCount() : level.getChunkCount());
        for (int i = 0; i < limit; i++) {
            boolean referenced = block ? level.isBlockReferencedInMap(i) : level.isChunkReferencedInBlocks(i);
            if (!reserved.contains(i) && !referenced && definitionState(block, i).length == stateLength) return i;
        }
        throw new IllegalStateException("No free " + (block ? "block" : "chunk") + " slots for clipboard composition");
    }

    private int[] definitionState(boolean block, int index) {
        return block ? level.getBlock(index).saveState() : level.getChunk(index).saveState();
    }

    private void restoreDefinition(Definition definition, boolean undo) {
        int[] state = undo ? definition.before : definition.after;
        if (definition.block) level.restoreBlockState(definition.index, state);
        else level.restoreChunkState(definition.index, state);
    }

    /** One history entry; stale state is rejected before any mutation. */
    private final class RegionCommand implements EditorCommand {
        private final List<Cell> cells;
        private final MutableLevel.ObjectBackedRingState before, after;
        private final List<Definition> definitions;
        private RegionCommand(List<Cell> cells, MutableLevel.ObjectBackedRingState before,
                              MutableLevel.ObjectBackedRingState after) {
            this(cells, before, after, List.of());
        }
        private RegionCommand(List<Cell> cells, MutableLevel.ObjectBackedRingState before,
                              MutableLevel.ObjectBackedRingState after, List<Definition> definitions) {
            this.cells = List.copyOf(cells);
            this.before = before;
            this.after = after;
            this.definitions = List.copyOf(definitions);
        }
        @Override public void apply() { write(false); }
        @Override public void undo() { write(true); }
        private void validateDefinitions(boolean undo) {
            Set<Integer> importedBlocks = new HashSet<>(), importedChunks = new HashSet<>();
            for (Definition definition : definitions) {
                if (!Arrays.equals(definitionState(definition.block, definition.index),
                        undo ? definition.after : definition.before)) {
                    throw new IllegalStateException("Clipboard destination definition changed since command creation");
                }
                if (!definition.imported()) continue;
                if (!undo) {
                    if (definition.block ? level.isBlockReferencedInMap(definition.index)
                            : level.isChunkReferencedInBlocks(definition.index)) {
                        throw new IllegalStateException("Clipboard destination definition is now referenced");
                    }
                } else if (definition.block) importedBlocks.add(definition.index);
                else importedChunks.add(definition.index);
            }
            if (!undo) return;
            if (!importedBlocks.isEmpty()) {
                var map = level.getMap();
                java.util.Map<Long, Integer> ownedCells = new HashMap<>();
                long layerSize = (long) map.getWidth() * map.getHeight();
                for (Cell cell : cells) ownedCells.put(cell.layer * layerSize + (long) cell.y * map.getWidth() + cell.x, cell.after);
                for (int layer = 0; layer < map.getLayerCount(); layer++) {
                    for (int y = 0; y < map.getHeight(); y++) for (int x = 0; x < map.getWidth(); x++) {
                        int block = value(layer, x, y);
                        if (importedBlocks.contains(block) && !Objects.equals(block,
                                ownedCells.get(layer * layerSize + (long) y * map.getWidth() + x))) {
                            throw new IllegalStateException("Imported clipboard block is now used outside the paste");
                        }
                    }
                }
            }
            if (!importedChunks.isEmpty()) {
                for (int i = 0; i < level.getBlockCount(); i++) {
                    if (importedBlocks.contains(i)) continue;
                    for (int descriptor : level.getBlock(i).saveState()) {
                        if (importedChunks.contains(descriptor & 0x3FF)) {
                            throw new IllegalStateException("Imported clipboard chunk is now used outside the paste");
                        }
                    }
                }
            }
        }
        private void write(boolean undo) {
            var expected = undo ? after : before;
            var replacement = undo ? before : after;
            for (Cell cell : cells) {
                if (value(cell.layer, cell.x, cell.y) != (undo ? cell.after : cell.before)) {
                    throw new IllegalStateException("Region terrain changed since command creation");
                }
            }
            if (expected != null && !level.snapshotObjectBackedRingState().equals(expected)) {
                throw new IllegalStateException("Placements changed since command creation");
            }
            validateDefinitions(undo);
            boolean modified = level.isModifiedSinceLastSave();
            int written = 0;
            List<Definition> writtenDefinitions = new ArrayList<>();
            try {
                if (!undo) writeDefinitions(false, writtenDefinitions);
                for (Cell cell : cells) {
                    level.setBlockInMap(cell.layer, cell.x, cell.y, undo ? cell.before : cell.after);
                    written++;
                }
                if (undo) writeDefinitions(true, writtenDefinitions);
                if (replacement != null && !replacement.equals(expected)) level.restoreObjectBackedRingState(replacement);
            } catch (RuntimeException failure) {
                for (int i = writtenDefinitions.size() - 1; i >= 0; i--) restoreDefinition(writtenDefinitions.get(i), !undo);
                for (int i = written - 1; i >= 0; i--) {
                    Cell cell = cells.get(i);
                    level.setBlockInMap(cell.layer, cell.x, cell.y, undo ? cell.after : cell.before);
                }
                if (expected != null) level.restoreObjectBackedRingState(expected);
                if (!modified) level.markSaved();
                throw failure;
            }
        }
        private void writeDefinitions(boolean undo, List<Definition> written) {
            for (int i = 0; i < definitions.size(); i++) {
                Definition definition = definitions.get(undo ? definitions.size() - 1 - i : i);
                if (definition.imported()) {
                    restoreDefinition(definition, undo);
                    written.add(definition);
                }
            }
        }
    }
}
