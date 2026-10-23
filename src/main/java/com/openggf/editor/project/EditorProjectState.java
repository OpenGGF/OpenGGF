package com.openggf.editor.project;

import com.openggf.game.ModKeySyntax;
import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RingObjectPlacementMapping;
import com.openggf.level.rings.RingSpawn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Arrays;
import java.util.Set;

/** Complete on-disk data. Arrays are private to the project codec, never a runtime asset fallback. */
record EditorProjectState(int zoneIndex, int blockGridSide, byte[][] patterns, int[][] chunks,
        int[][] blocks, SolidState[] solids, byte[][] palettes, int layers, int width, int height,
        byte[] map, List<ObjectState> objects, List<RingState> rings, List<BackingState> ringBacking,
        int minX, int maxX, int minY, int maxY) {
    // Native snapshots may include unused RAM/art beyond the portable export's smaller domains.
    static final int MAX_PATTERNS = 8192;
    static final int MAX_CHUNKS = 4096;
    static final int MAX_SOLIDS = 1024;
    static final int MAX_SPAWNS = 65535;

    static EditorProjectState capture(Level source) { return capture(source, true); }
    static EditorProjectState captureWithoutBacking(Level source) { return capture(source, false); }
    private static EditorProjectState capture(Level source, boolean includeBacking) {
        return capture(source, includeBacking, false, 0, 0).state();
    }

    /** Blank authoring retains only resolvable definitions, without importing native layout geometry. */
    static BlankCapture captureBlankLibrary(Level source, int width, int height) {
        EditorProjectLevel.validateDimensions(source.getChunksPerBlockSide(), width, height);
        return capture(source, false, true, width, height);
    }

    record BlankCapture(EditorProjectState state, int omittedChunks, int omittedBlocks) {}

    private static BlankCapture capture(Level source, boolean includeBacking, boolean blank, int width, int height) {
        checkCount(source.getPatternCount(), MAX_PATTERNS, "patterns");
        checkCount(source.getChunkCount(), MAX_CHUNKS, "chunks");
        checkCount(source.getBlockCount(), 256, "blocks");
        checkCount(source.getSolidTileCount(), MAX_SOLIDS, "solid profiles");
        if (source.getPaletteCount() != 4) throw new IllegalArgumentException("Projects require the complete four-line palette bank");
        if (!blank) EditorProjectLevel.validateDimensions(source.getChunksPerBlockSide(), source.getMap().getWidth(), source.getMap().getHeight());
        byte[][] patterns = new byte[source.getPatternCount()][64];
        for (int p = 0; p < patterns.length; p++) source.getPattern(p).copyInto(patterns[p], 0);
        int[][] chunks = new int[source.getChunkCount()][];
        for (int i = 0; i < chunks.length; i++) chunks[i] = source.getChunk(i).saveState();
        int[][] blocks = new int[source.getBlockCount()][];
        for (int i = 0; i < blocks.length; i++) {
            if (source.getBlock(i).getGridSide() != source.getChunksPerBlockSide()) {
                throw new IllegalArgumentException("Project blocks have inconsistent geometry");
            }
            blocks[i] = source.getBlock(i).saveState();
        }
        SolidState[] solids = new SolidState[source.getSolidTileCount()];
        for (int i = 0; i < solids.length; i++) {
            SolidTile tile = source.getSolidTile(i);
            solids[i] = new SolidState(tile.getIndex(), tile.heights.clone(), tile.widths.clone(), Byte.toUnsignedInt(tile.getAngle()));
        }
        byte[][] palettes = new byte[source.getPaletteCount()][48];
        for (int p = 0; p < palettes.length; p++) for (int c = 0; c < 16; c++) {
            Palette.Color color = source.getPalette(p).getColor(c);
            palettes[p][c * 3] = color.r; palettes[p][c * 3 + 1] = color.g; palettes[p][c * 3 + 2] = color.b;
        }
        List<ObjectState> objects = blank ? List.of() : source.getObjects().stream().map(ObjectState::capture).toList();
        List<RingState> rings = blank ? List.of() : source.getRings().stream().map(ring -> new RingState(ring.x(), ring.y(), ring.placementId())).toList();
        List<BackingState> backing = new ArrayList<>();
        if (includeBacking && source instanceof RingObjectPlacementMapping provider) {
            provider.ringObjectPlacementMapping().forEach((object, group) -> {
                if (!source.getObjects().contains(object) || !source.getRings().containsAll(group)) {
                    throw new IllegalArgumentException("Ring backing mapping references absent placements");
                }
                backing.add(new BackingState(object.layoutIndex(), group.stream().mapToInt(RingSpawn::placementId).toArray()));
            });
        }
        int omittedChunks = 0, omittedBlocks = 0;
        if (blank) {
            // ROM libraries can share definitions with another zone's VRAM bank. A blank
            // cannot invent their missing art, and its discarded stock map gives them no
            // authored identity to preserve. Compact the resolvable definitions together.
            int[] chunkIndices = new int[chunks.length];
            Arrays.fill(chunkIndices, -1);
            List<int[]> usableChunks = new ArrayList<>();
            for (int i = 0; i < chunks.length; i++) {
                int[] chunk = chunks[i];
                if (chunk == null || chunk.length != 6) throw new IllegalArgumentException("Invalid project chunk length");
                boolean available = true;
                for (int j = 0; j < 4; j++) {
                    unsigned(chunk[j], 0xFFFF, "pattern descriptor");
                    available &= (chunk[j] & 0x7FF) < patterns.length;
                }
                for (int j = 4; j < 6; j++) {
                    unsigned(chunk[j], 0xFFFF, "collision index");
                    available &= chunk[j] < solids.length;
                }
                if (available) { chunkIndices[i] = usableChunks.size(); usableChunks.add(chunk); }
                else omittedChunks++;
            }
            List<int[]> usableBlocks = new ArrayList<>();
            for (int[] block : blocks) {
                int[] remapped = block.clone();
                boolean available = true;
                for (int i = 0; i < block.length; i++) {
                    unsigned(block[i], 0xFFFF, "chunk descriptor");
                    int index = block[i] & 0x3FF;
                    if (index >= chunkIndices.length || chunkIndices[index] < 0) available = false;
                    else remapped[i] = (block[i] & ~0x3FF) | chunkIndices[index];
                }
                if (available) usableBlocks.add(remapped);
                else omittedBlocks++;
            }
            if (usableChunks.isEmpty()) {
                int transparent = -1;
                for (int p = 0; p < Math.min(2048, patterns.length) && transparent < 0; p++) {
                    boolean empty = true;
                    for (byte pixel : patterns[p]) empty &= pixel == 0;
                    if (empty) transparent = p;
                }
                if (transparent < 0) {
                    if (patterns.length >= 2048) throw new IllegalArgumentException("Asset library has no transparent pattern or free pattern slot");
                    transparent = patterns.length;
                    patterns = Arrays.copyOf(patterns, patterns.length + 1);
                    patterns[transparent] = new byte[64];
                }
                // This is an explicitly authored empty definition, never a substitute
                // for any omitted native chunk or its unavailable art.
                usableChunks.add(new int[] {transparent, transparent, transparent, transparent, 0, 0});
            }
            if (usableBlocks.isEmpty()) usableBlocks.add(new int[source.getChunksPerBlockSide() * source.getChunksPerBlockSide()]);
            chunks = usableChunks.toArray(int[][]::new);
            blocks = usableBlocks.toArray(int[][]::new);
        } else {
            patterns = neutralizeUnplacedUnavailableArt(source, patterns, chunks, blocks);
        }
        int side = source.getChunksPerBlockSide();
        EditorProjectState state = blank
                ? new EditorProjectState(source.getZoneIndex(), side, patterns, chunks, blocks, solids, palettes,
                    2, width, height, new byte[2 * width * height], List.of(), List.of(), List.of(),
                    0, width * side * 16 - 1, 0, height * side * 16 - 1)
                : new EditorProjectState(source.getZoneIndex(), side, patterns, chunks, blocks, solids, palettes,
                    source.getMap().getLayerCount(), source.getMap().getWidth(), source.getMap().getHeight(), source.getMap().getData().clone(),
                    objects, rings, List.copyOf(backing), source.getMinX(), source.getMaxX(), source.getMinY(), source.getMaxY());
        state.validate();
        return new BlankCapture(state, omittedChunks, omittedBlocks);
    }

    /**
     * A native library can define chunks whose tiles live outside its decoded pattern table:
     * stock Sonic 2 Emerald Hill has 32 such references, in chunks that no placed block uses.
     * The layout never draws them, so a clone keeps their index and collision and points the
     * unavailable tiles at a transparent pattern. A placed chunk with unavailable art still
     * fails, so the clone never changes what the layout shows.
     */
    private static byte[][] neutralizeUnplacedUnavailableArt(Level source, byte[][] patterns, int[][] chunks, int[][] blocks) {
        boolean[] placedBlocks = new boolean[blocks.length];
        for (byte cell : source.getMap().getData()) {
            int block = Byte.toUnsignedInt(cell);
            if (block < placedBlocks.length) placedBlocks[block] = true;
        }
        boolean[] placed = new boolean[chunks.length];
        for (int b = 0; b < blocks.length; b++) {
            if (!placedBlocks[b]) continue;
            for (int descriptor : blocks[b]) {
                int chunk = descriptor & 0x3FF;
                if (chunk < placed.length) placed[chunk] = true;
            }
        }
        int transparent = -1;
        for (int i = 0; i < chunks.length; i++) {
            int[] chunk = chunks[i];
            if (chunk == null || chunk.length != 6) continue;
            for (int j = 0; j < 4; j++) {
                if ((chunk[j] & 0x7FF) < patterns.length) continue;
                if (placed[i]) throw new IllegalArgumentException("Placed chunk " + i + " references missing pattern " + (chunk[j] & 0x7FF));
                if (transparent < 0) {
                    for (int p = 0; p < Math.min(2048, patterns.length) && transparent < 0; p++) {
                        boolean empty = true;
                        for (byte pixel : patterns[p]) empty &= pixel == 0;
                        if (empty) transparent = p;
                    }
                    if (transparent < 0) {
                        if (patterns.length >= 2048) throw new IllegalArgumentException("Asset library has no transparent pattern or free pattern slot");
                        transparent = patterns.length;
                        patterns = Arrays.copyOf(patterns, patterns.length + 1);
                        patterns[transparent] = new byte[64];
                    }
                }
                chunk[j] = transparent;
            }
        }
        return patterns;
    }

    void validate() {
        if (zoneIndex < 0) throw new IllegalArgumentException("Invalid project zone identity");
        EditorProjectLevel.validateDimensions(blockGridSide, width, height);
        EditorProjectLevel.validateBounds(minX, maxX, minY, maxY);
        if (layers < 1 || layers > 2 || map == null || map.length != (long) layers * width * height) {
            throw new IllegalArgumentException("Project map shape/length is invalid");
        }
        if (maxX >= (long) width * blockGridSide * 16 || maxY >= (long) height * blockGridSide * 16) {
            throw new IllegalArgumentException("Project bounds lie outside its map");
        }
        checkCount(patterns == null ? 0 : patterns.length, MAX_PATTERNS, "patterns");
        checkCount(chunks == null ? 0 : chunks.length, MAX_CHUNKS, "chunks");
        checkCount(blocks == null ? 0 : blocks.length, 256, "blocks");
        checkCount(solids == null ? 0 : solids.length, MAX_SOLIDS, "solid profiles");
        if (palettes == null || palettes.length != 4) throw new IllegalArgumentException("Projects require the complete four-line palette bank");
        for (byte[] pattern : patterns) {
            requireLength(pattern, 64, "pattern pixels");
            for (byte pixel : pattern) if (Byte.toUnsignedInt(pixel) > 15) throw new IllegalArgumentException("Invalid 4-bit pattern pixel");
        }
        for (int[] chunk : chunks) {
            if (chunk == null || chunk.length != 6) throw new IllegalArgumentException("Invalid project chunk length");
            for (int i = 0; i < 4; i++) {
                unsigned(chunk[i], 0xFFFF, "pattern descriptor");
                if ((chunk[i] & 0x7FF) >= patterns.length) throw new IllegalArgumentException("Chunk references missing pattern");
            }
            for (int i = 4; i < 6; i++) {
                unsigned(chunk[i], 0xFFFF, "collision index");
                if (chunk[i] >= solids.length) throw new IllegalArgumentException("Chunk references missing solid profile");
            }
        }
        for (int[] block : blocks) {
            if (block == null || block.length != blockGridSide * blockGridSide) throw new IllegalArgumentException("Invalid project block length");
            for (int value : block) {
                unsigned(value, 0xFFFF, "chunk descriptor");
                if ((value & 0x3FF) >= chunks.length) throw new IllegalArgumentException("Block references missing chunk");
            }
        }
        for (byte cell : map) if (Byte.toUnsignedInt(cell) >= blocks.length) throw new IllegalArgumentException("Map references missing block");
        for (SolidState solid : solids) {
            if (solid == null) throw new IllegalArgumentException("Missing solid profile");
            unsigned(solid.index(), 0xFFFF, "solid profile index");
            unsigned(solid.angle(), 0xFF, "solid profile angle");
            requireLength(solid.heights(), 16, "solid heights"); requireLength(solid.widths(), 16, "solid widths");
        }
        for (byte[] palette : palettes) requireLength(palette, 48, "palette RGB");
        if (objects == null || rings == null || ringBacking == null || objects.size() > MAX_SPAWNS
                || rings.size() > MAX_SPAWNS || ringBacking.size() > objects.size()) {
            throw new IllegalArgumentException("Project placement count is invalid");
        }
        Set<Integer> objectIds = new HashSet<>();
        for (ObjectState object : objects) {
            if (object == null) throw new IllegalArgumentException("Missing object placement");
            object.validate();
            if (!objectIds.add(object.placementId())) throw new IllegalArgumentException("Duplicate object placement id");
        }
        Set<Integer> ringIds = new HashSet<>();
        for (RingState ring : rings) {
            if (ring == null) throw new IllegalArgumentException("Missing ring placement");
            unsigned(ring.x(), 0xFFFF, "ring x"); unsigned(ring.y(), 0xFFFF, "ring y");
            if (ring.placementId() < 0 || !ringIds.add(ring.placementId())) throw new IllegalArgumentException("Invalid or duplicate ring placement id");
        }
        Set<Integer> backingObjects = new HashSet<>(), backedRings = new HashSet<>();
        for (BackingState backing : ringBacking) {
            if (backing == null || !objectIds.contains(backing.objectPlacementId())
                    || !backingObjects.add(backing.objectPlacementId()) || backing.ringPlacementIds() == null
                    || backing.ringPlacementIds().length == 0 || backing.ringPlacementIds().length > rings.size()) {
                throw new IllegalArgumentException("Invalid ring backing object");
            }
            for (int id : backing.ringPlacementIds()) if (!ringIds.contains(id) || !backedRings.add(id)) {
                throw new IllegalArgumentException("Missing or multiply backed ring placement");
            }
        }
    }

    static void checkCount(int value, int maximum, String label) {
        if (value < 1 || value > maximum) throw new IllegalArgumentException("Project " + label + " count must be 1.." + maximum);
    }
    private static void requireLength(byte[] values, int length, String label) {
        if (values == null || values.length != length) throw new IllegalArgumentException("Invalid " + label + " length");
    }
    private static void unsigned(int value, int maximum, String label) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException("Project " + label + " exceeds unsigned range");
    }

    record SolidState(int index, byte[] heights, byte[] widths, int angle) {}
    record BackingState(int objectPlacementId, int[] ringPlacementIds) {}
    record RingState(int x, int y, int placementId) {}
    record ObjectState(int placementId, int x, int y, int objectId, int subtype, int renderFlags,
            boolean respawnTracked, int rawYWord, String ownerModId, String objectKey,
            com.fasterxml.jackson.databind.JsonNode authoredProperties) {
        ObjectState(int placementId, int x, int y, int objectId, int subtype, int renderFlags,
                    boolean respawnTracked, int rawYWord, String ownerModId, String objectKey) {
            this(placementId, x, y, objectId, subtype, renderFlags, respawnTracked, rawYWord, ownerModId, objectKey, null);
        }
        static ObjectState capture(ObjectSpawn spawn) {
            return new ObjectState(spawn.layoutIndex(), spawn.x(), spawn.y(), spawn.objectId(), spawn.subtype(),
                    spawn.renderFlags(), spawn.respawnTracked(), spawn.rawYWord(), spawn.ownerModId(), spawn.objectKey(),
                    spawn.authoredProperties() == null ? null : com.openggf.level.objects.authored.AuthoredObjectPropertiesJson.toJson(spawn.authoredProperties()));
        }
        void validate() {
            if (placementId < 0) throw new IllegalArgumentException("Invalid object placement id");
            unsigned(x, 0xFFFF, "object x"); unsigned(y, 0xFFFF, "object y");
            unsigned(objectId, 0xFF, "object id"); unsigned(subtype, 0xFF, "object subtype");
            unsigned(renderFlags, 3, "object render flags"); unsigned(rawYWord, 0xFFFF, "object raw Y word");
            if ((ownerModId == null) != (objectKey == null)) throw new IllegalArgumentException("Object owner/key identity is incomplete");
            if (objectKey != null) {
                ModKeySyntax.requireManifestId(ownerModId); ModKeySyntax.requireDisplayKey(objectKey);
                if (!objectKey.startsWith(ownerModId + ":")) throw new IllegalArgumentException("Object key owner mismatch");
            }
            spawn(); // Decode typed properties and validate their identity/geometry before admission.
        }
        ObjectSpawn spawn() {
            try {
                return new ObjectSpawn(x, y, objectId, subtype, renderFlags, respawnTracked, rawYWord, placementId, ownerModId, objectKey,
                        com.openggf.level.objects.authored.AuthoredObjectPropertiesJson.parse(authoredProperties));
            } catch (java.io.IOException error) { throw new IllegalArgumentException("Invalid project authored properties: " + error.getMessage(), error); }
        }
    }
}
