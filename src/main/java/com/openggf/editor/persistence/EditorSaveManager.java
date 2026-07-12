package com.openggf.editor.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameId;
import com.openggf.game.ModApi;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static java.security.MessageDigest.getInstance;

@com.openggf.game.ModApi
public final class EditorSaveManager {
    private static final Logger LOG = Logger.getLogger(EditorSaveManager.class.getName());
    private static final int VERSION = 3;

    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper();
    private final EditorSaveReader reader;
    private ApplyResult lastApplyResult = ApplyResult.NONE;
    private final java.util.function.Predicate<String> objectKeyExists;
    private final java.util.function.Consumer<EditorApplyFinding> findingSink;
    private final int maxReadableVersion;

    public EditorSaveManager(Path root) {
        this(root, key -> false, finding -> LOG.warning(finding.code() + ": " + finding.message()));
    }

    public EditorSaveManager(Path root, java.util.function.Predicate<String> objectKeyExists,
                             java.util.function.Consumer<EditorApplyFinding> findingSink) {
        this(root, objectKeyExists, findingSink, VERSION);
    }

    private EditorSaveManager(Path root, java.util.function.Predicate<String> objectKeyExists,
                              java.util.function.Consumer<EditorApplyFinding> findingSink,
                              int maxReadableVersion) {
        this.root = root;
        this.reader = file -> mapper.readTree(file.toFile());
        this.objectKeyExists = java.util.Objects.requireNonNull(objectKeyExists, "objectKeyExists");
        this.findingSink = java.util.Objects.requireNonNull(findingSink, "findingSink");
        this.maxReadableVersion = maxReadableVersion;
    }

    EditorSaveManager(Path root, EditorSaveReader reader) {
        this.root = root;
        this.reader = reader;
        this.objectKeyExists = key -> false;
        this.findingSink = finding -> LOG.warning(finding.code() + ": " + finding.message());
        this.maxReadableVersion = VERSION;
    }

    static EditorSaveManager legacyV2Reader(Path root) {
        return new EditorSaveManager(root, key -> false, finding -> {}, 2);
    }

    public SaveResult save(GameId gameId, ObjectPlacementEncoding placementEncoding,
                           int zone, int act, MutableLevel level) throws IOException {
        Path file = editPath(gameId, zone, act);
        Files.createDirectories(file.getParent());
        EditorSavePayload payload = buildPayload(gameId, level);
        boolean keyed = level.getObjects().stream().anyMatch(spawn -> spawn.objectKey() != null);
        Object persistedPayload;
        int persistedVersion;
        if (keyed) {
            persistedPayload = buildPayloadV3(payload, level);
            validatePayloadV3ForSave((EditorSavePayloadV3) persistedPayload, placementEncoding);
            persistedVersion = 3;
        } else {
            decodeSpawns(payload, placementEncoding);
            persistedPayload = payload;
            persistedVersion = 2;
        }
        String payloadJson = mapper.writeValueAsString(persistedPayload);
        Object envelope = keyed
                ? new EditorSaveEnvelopeV3(3, gameId.code(), zone, act, Instant.now().toString(),
                        (EditorSavePayloadV3) persistedPayload, sha256(payloadJson))
                : new EditorSaveEnvelope(persistedVersion, gameId.code(), zone, act,
                        Instant.now().toString(), payload, sha256(payloadJson));

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            mapper.writeValue(tmp.toFile(), envelope);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            Files.deleteIfExists(tmp);
            throw ex;
        }
        level.markSaved();
        ApplyResult persistenceStatus = supportsRuntimeEditApply(gameId)
                ? ApplyResult.APPLIED
                : ApplyResult.UNSUPPORTED;
        return new SaveResult(true, file, sha256(payloadJson), persistenceStatus);
    }

    public ApplyResult tryApplyEdits(GameId gameId, ObjectPlacementEncoding placementEncoding,
                                     int zone, int act, MutableLevel level) {
        Path file = editPath(gameId, zone, act);
        if (!Files.exists(file)) {
            return remember(ApplyResult.NONE);
        }
        try {
            JsonNode envelope = reader.read(file);
            int version = requiredInt(envelope, "version");
            if (version < 1 || version > maxReadableVersion) {
                quarantine(file, "unsupported editor save version " + version);
                return remember(ApplyResult.QUARANTINED);
            }
            if (!gameId.code().equals(requiredText(envelope, "gameCode"))
                    || requiredInt(envelope, "zone") != zone || requiredInt(envelope, "act") != act) {
                return remember(ApplyResult.MISMATCH);
            }
            if (!supportsRuntimeEditApply(gameId)) {
                LOG.fine("Skipping persisted " + gameId
                        + " editor edits until runtime overlays support MutableLevel");
                return remember(ApplyResult.UNSUPPORTED);
            }
            JsonNode rawPayload = envelope.get("payload");
            if (rawPayload == null || !rawPayload.isObject()) {
                throw new IllegalArgumentException("Missing editor payload object");
            }
            String actual = sha256(mapper.writeValueAsString(rawPayload));
            if (!actual.equals(requiredText(envelope, "hash"))) {
                quarantine(file, "hash mismatch");
                return remember(ApplyResult.QUARANTINED);
            }
            if (version == 1) {
                EditorSavePayloadV1 payload = mapper.treeToValue(rawPayload, EditorSavePayloadV1.class);
                applyPayloadV3(EditorSavePayloadMigrator.fromV1(payload), level, placementEncoding, false);
            } else if (version == 2) {
                EditorSavePayload payload = mapper.treeToValue(rawPayload, EditorSavePayload.class);
                applyPayloadV3(EditorSavePayloadMigrator.fromV2(payload), level, placementEncoding, true);
            } else {
                EditorSavePayloadV3 payload = mapper.treeToValue(rawPayload, EditorSavePayloadV3.class);
                applyPayloadV3(payload, level, placementEncoding, true);
            }
            level.markSaved();
            return remember(ApplyResult.APPLIED);
        } catch (JsonProcessingException | RuntimeException ex) {
            try {
                quarantine(file, ex.getMessage());
            } catch (IOException quarantineError) {
                LOG.warning("Failed to quarantine editor save " + file + ": " + quarantineError.getMessage());
            }
            return remember(ApplyResult.QUARANTINED);
        } catch (IOException ex) {
            LOG.warning("Transient I/O while reading editor save " + file + "; leaving it in place: "
                    + ex.getMessage());
            return remember(ApplyResult.TRANSIENT_FAILURE);
        }
    }

    public ApplyResult lastApplyResult() {
        return lastApplyResult;
    }

    public Path editPath(GameId gameId, int zone, int act) {
        return root.resolve(gameId.code()).resolve("edits").resolve("zone_" + zone + "_act_" + act + ".json");
    }

    public boolean supportsRuntimeEditApply(GameId gameId) {
        return gameId != GameId.S3K;
    }

    private EditorSavePayload buildPayload(GameId gameId, MutableLevel level) {
        List<EditorSavePayload.BlockState> blocks = new ArrayList<>();
        BitSet modifiedBlocks = level.modifiedBlocksSinceBaseline();
        for (int index = modifiedBlocks.nextSetBit(0); index >= 0; index = modifiedBlocks.nextSetBit(index + 1)) {
            if (index < level.getBlockCount()) {
                blocks.add(new EditorSavePayload.BlockState(index, level.editorSaveBlockState(index)));
            }
        }

        List<EditorSavePayload.ChunkState> chunks = new ArrayList<>();
        BitSet modifiedChunks = level.modifiedChunksSinceBaseline();
        for (int index = modifiedChunks.nextSetBit(0); index >= 0; index = modifiedChunks.nextSetBit(index + 1)) {
            if (index < level.getChunkCount()) {
                chunks.add(new EditorSavePayload.ChunkState(index, level.editorSaveChunkState(index)));
            }
        }

        List<EditorSavePayload.MapCell> mapCells = new ArrayList<>();
        BitSet modifiedMapCells = level.modifiedMapCellsSinceBaseline();
        for (int index = modifiedMapCells.nextSetBit(0); index >= 0; index = modifiedMapCells.nextSetBit(index + 1)) {
            int[] cell = level.delinearizeMapCell(index);
            int layer = cell[0];
            int x = cell[1];
            int y = cell[2];
            mapCells.add(new EditorSavePayload.MapCell(
                    layer, x, y, level.editorSaveMapCellValue(index)));
        }
        List<EditorSavePayload.ObjectState> objects = level.getObjects().stream()
                .map(spawn -> new EditorSavePayload.ObjectState(spawn.layoutIndex(), spawn.x(), spawn.y(),
                        spawn.objectId(), spawn.subtype(), spawn.renderFlags(), spawn.respawnTracked(),
                        spawn.rawYWord()))
                .toList();
        Map<Integer, Integer> ringBackingIds = ringBackingIds(level);
        List<EditorSavePayload.RingState> rings = level.getRings().stream()
                .map(ring -> new EditorSavePayload.RingState(ring.placementId(), ring.x(), ring.y(),
                        ringBackingIds.get(ring.placementId())))
                .toList();
        return new EditorSavePayload(blocks, chunks, mapCells, objects, rings);
    }

    private EditorSavePayloadV3 buildPayloadV3(EditorSavePayload base, MutableLevel level) {
        List<ObjectSpawnStateV3> objects = level.getObjects().stream().map(spawn ->
                new ObjectSpawnStateV3(spawn.layoutIndex(), spawn.x(), spawn.y(),
                        spawn.objectKey() == null ? spawn.objectId() : null, spawn.objectKey(),
                        spawn.subtype(), spawn.renderFlags(), spawn.respawnTracked(), spawn.rawYWord())).toList();
        return new EditorSavePayloadV3(base.blocks(), base.chunks(), base.mapCells(), objects, base.rings());
    }

    /** Native encoding preflight for live keyed saves; availability is deliberately irrelevant. */
    private void validatePayloadV3ForSave(EditorSavePayloadV3 payload, ObjectPlacementEncoding encoding) {
        Set<Integer> placementIds = new HashSet<>();
        for (ObjectSpawnStateV3 state : payload.objects()) {
            if (!placementIds.add(state.placementId())) {
                throw new IllegalArgumentException("Duplicate object placement id " + state.placementId());
            }
            ObjectSpawn encoded = state.objectKey() == null
                    ? encoding.create(state.x(), state.y(), state.stockObjectId(), state.subtype(),
                            state.renderFlags(), state.respawnTracked(), state.placementId())
                    : encoding.createKeyed(state.x(), state.y(), state.objectKey(), state.subtype(),
                            state.renderFlags(), state.respawnTracked(), state.placementId());
            if (encoded.rawYWord() != state.rawYWord()) {
                throw new IllegalArgumentException("Object placement raw word does not match encoded fields for id "
                        + state.placementId());
            }
        }
        Set<Integer> ringIds = new HashSet<>();
        for (EditorSavePayload.RingState ring : payload.rings()) {
            if (ring.placementId() < 0 || !ringIds.add(ring.placementId())
                    || ring.x() < 0 || ring.x() > 0xFFFF || ring.y() < 0 || ring.y() > 0xFFFF
                    || (ring.backingObjectPlacementId() != null
                    && !placementIds.contains(ring.backingObjectPlacementId()))) {
                throw new IllegalArgumentException("Invalid keyed-save ring placement " + ring.placementId());
            }
        }
    }

    private void applyPayloadV3(EditorSavePayloadV3 payload, MutableLevel level,
                                ObjectPlacementEncoding encoding, boolean replaceSpawns) {
        EditorSavePayload terrain = new EditorSavePayload(payload.blocks(), payload.chunks(),
                payload.mapCells(), List.of(), payload.rings());
        validatePayload(terrain, level);
        if (!replaceSpawns) {
            applyTerrain(terrain, level);
            return;
        }
        List<ObjectSpawn> objects = new ArrayList<>();
        Map<Integer, ObjectSpawn> objectsById = new LinkedHashMap<>();
        Set<Integer> skippedObjectIds = new HashSet<>();
        for (ObjectSpawnStateV3 state : payload.objects()) {
            if (objectsById.containsKey(state.placementId()) || skippedObjectIds.contains(state.placementId())) {
                throw new IllegalArgumentException("Duplicate object placement id " + state.placementId());
            }
            if (state.objectKey() != null) {
                if (!objectKeyExists.test(state.objectKey())) {
                    skippedObjectIds.add(state.placementId());
                    continue;
                }
                ObjectSpawn spawn = encoding.createKeyed(state.x(), state.y(), state.objectKey(), state.subtype(),
                        state.renderFlags(), state.respawnTracked(), state.placementId());
                if (spawn.rawYWord() != state.rawYWord()) {
                    throw new IllegalArgumentException("Object placement raw word does not match encoded fields for id "
                            + state.placementId());
                }
                objects.add(spawn); objectsById.put(spawn.layoutIndex(), spawn);
            } else {
                ObjectSpawn spawn = encoding.create(state.x(), state.y(), state.stockObjectId(), state.subtype(),
                        state.renderFlags(), state.respawnTracked(), state.placementId());
                if (spawn.rawYWord() != state.rawYWord()) {
                    throw new IllegalArgumentException("Object placement raw word does not match encoded fields for id "
                            + state.placementId());
                }
                objects.add(spawn); objectsById.put(spawn.layoutIndex(), spawn);
            }
        }
        List<RingSpawn> rings = new ArrayList<>();
        Set<Integer> ringIds = new HashSet<>();
        Map<ObjectSpawn, List<RingSpawn>> mapping = new LinkedHashMap<>();
        int skippedRings = 0;
        for (EditorSavePayload.RingState state : payload.rings()) {
            if (state.x() < 0 || state.x() > 0xFFFF || state.y() < 0 || state.y() > 0xFFFF
                    || state.placementId() < 0 || !ringIds.add(state.placementId())) {
                throw new IllegalArgumentException("Invalid or duplicate ring placement " + state.placementId());
            }
            ObjectSpawn backing = state.backingObjectPlacementId() == null ? null
                    : objectsById.get(state.backingObjectPlacementId());
            if (state.backingObjectPlacementId() != null && backing == null) {
                if (skippedObjectIds.contains(state.backingObjectPlacementId())) {
                    skippedRings++;
                    continue;
                }
                throw new IllegalArgumentException("Unknown ring backing object placement id "
                        + state.backingObjectPlacementId());
            }
            RingSpawn ring = new RingSpawn(state.x(), state.y(), state.placementId());
            rings.add(ring);
            if (backing != null) mapping.computeIfAbsent(backing, ignored -> new ArrayList<>()).add(ring);
        }
        int skipped = skippedObjectIds.size() + skippedRings;
        if (skipped > 0) findingSink.accept(new EditorApplyFinding("EDITOR_MOD_OBJECT_KEYS_MISSING",
                "Skipped " + skippedObjectIds.size() + " object spawn(s) and " + skippedRings
                        + " backed ring spawn(s) whose mod keys are unavailable", skipped));
        // Mutation starts only after the entire v3 payload has decoded and validated.
        applyTerrain(terrain, level);
        level.replaceSpawnsPersisted(objects, rings, mapping);
    }

    private void applyPayload(EditorSavePayload payload, MutableLevel level,
                              ObjectPlacementEncoding placementEncoding, boolean replaceSpawns) {
        if (payload == null) {
            return;
        }
        validatePayload(payload, level);
        SpawnReplacement spawnReplacement = replaceSpawns ? decodeSpawns(payload, placementEncoding) : null;
        applyTerrain(payload, level);
        if (spawnReplacement != null) {
            level.replaceSpawnsPersisted(spawnReplacement.objects(), spawnReplacement.rings(),
                    spawnReplacement.mapping());
        }
    }

    private void applyTerrain(EditorSavePayload payload, MutableLevel level) {
        for (EditorSavePayload.ChunkState chunkState : payload.chunks()) {
            if (chunkState.index() >= 0 && chunkState.index() < level.getChunkCount()) {
                level.restoreChunkState(chunkState.index(), chunkState.state());
            }
        }
        for (EditorSavePayload.BlockState blockState : payload.blocks()) {
            if (blockState.index() >= 0 && blockState.index() < level.getBlockCount()) {
                level.restoreBlockState(blockState.index(), blockState.state());
            }
        }
        for (EditorSavePayload.MapCell mapCell : payload.mapCells()) {
            if (mapCell.layer() >= 0 && mapCell.layer() < level.getMap().getLayerCount()
                    && mapCell.x() >= 0 && mapCell.x() < level.getMap().getWidth()
                    && mapCell.y() >= 0 && mapCell.y() < level.getMap().getHeight()) {
                if (mapCell.blockIndex() < 0 || mapCell.blockIndex() >= level.getBlockCount()
                        || mapCell.blockIndex() > 0xFF) {
                    throw new IllegalArgumentException("Invalid editor map block index "
                            + mapCell.blockIndex() + " at layer=" + mapCell.layer()
                            + " x=" + mapCell.x() + " y=" + mapCell.y());
                }
                level.setBlockInMap(mapCell.layer(), mapCell.x(), mapCell.y(), mapCell.blockIndex());
            }
        }
    }

    private void validatePayload(EditorSavePayload payload, MutableLevel level) {
        for (EditorSavePayload.ChunkState chunkState : payload.chunks()) {
            if (chunkState.index() < 0 || chunkState.index() >= level.getChunkCount()) {
                throw new IllegalArgumentException("Editor chunk index is outside the level: " + chunkState.index());
            }
            if (chunkState.state() == null) {
                throw new IllegalArgumentException("Missing editor chunk state at index " + chunkState.index());
            }
            if (chunkState.index() >= 0 && chunkState.index() < level.getChunkCount()
                    && chunkState.state().length != new Chunk().saveState().length) {
                throw new IllegalArgumentException("Invalid editor chunk state length "
                        + chunkState.state().length + " at index " + chunkState.index());
            }
        }
        for (EditorSavePayload.BlockState blockState : payload.blocks()) {
            if (blockState.index() < 0 || blockState.index() >= level.getBlockCount()) {
                throw new IllegalArgumentException("Editor block index is outside the level: " + blockState.index());
            }
            if (blockState.state() == null) {
                throw new IllegalArgumentException("Missing editor block state at index " + blockState.index());
            }
            if (blockState.index() >= 0 && blockState.index() < level.getBlockCount()) {
                Block block = level.getBlock(blockState.index());
                if (blockState.state().length != block.saveState().length) {
                    throw new IllegalArgumentException("Invalid editor block state length "
                            + blockState.state().length + " at index " + blockState.index());
                }
            }
        }
        for (EditorSavePayload.MapCell mapCell : payload.mapCells()) {
            if (mapCell.layer() < 0 || mapCell.layer() >= level.getMap().getLayerCount()
                    || mapCell.x() < 0 || mapCell.x() >= level.getMap().getWidth()
                    || mapCell.y() < 0 || mapCell.y() >= level.getMap().getHeight()) {
                throw new IllegalArgumentException("Editor map cell is outside the level at layer="
                        + mapCell.layer() + " x=" + mapCell.x() + " y=" + mapCell.y());
            }
            if (mapCell.blockIndex() < 0 || mapCell.blockIndex() >= level.getBlockCount()
                    || mapCell.blockIndex() > 0xFF) {
                throw new IllegalArgumentException("Invalid editor map block index "
                        + mapCell.blockIndex() + " at layer=" + mapCell.layer()
                        + " x=" + mapCell.x() + " y=" + mapCell.y());
            }
        }
    }

    private SpawnReplacement decodeSpawns(EditorSavePayload payload, ObjectPlacementEncoding encoding) {
        List<ObjectSpawn> objects = new ArrayList<>(payload.objects().size());
        Map<Integer, ObjectSpawn> objectsById = new HashMap<>();
        for (EditorSavePayload.ObjectState state : payload.objects()) {
            ObjectSpawn spawn = encoding.create(state.x(), state.y(), state.objectId(), state.subtype(),
                    state.renderFlags(), state.respawnTracked(), state.placementId());
            if (spawn.rawYWord() != state.rawYWord()) {
                throw new IllegalArgumentException("Object placement raw word does not match encoded fields for id "
                        + state.placementId());
            }
            if (objectsById.put(spawn.layoutIndex(), spawn) != null) {
                throw new IllegalArgumentException("Duplicate object placement id " + spawn.layoutIndex());
            }
            objects.add(spawn);
        }

        List<RingSpawn> rings = new ArrayList<>(payload.rings().size());
        Set<Integer> ringIds = new HashSet<>();
        Map<ObjectSpawn, List<RingSpawn>> mapping = new LinkedHashMap<>();
        for (EditorSavePayload.RingState state : payload.rings()) {
            if (state.x() < 0 || state.x() > 0xFFFF || state.y() < 0 || state.y() > 0xFFFF
                    || state.placementId() < 0 || !ringIds.add(state.placementId())) {
                throw new IllegalArgumentException("Invalid or duplicate ring placement " + state.placementId());
            }
            RingSpawn ring = new RingSpawn(state.x(), state.y(), state.placementId());
            rings.add(ring);
            if (state.backingObjectPlacementId() != null) {
                ObjectSpawn backing = objectsById.get(state.backingObjectPlacementId());
                if (backing == null) {
                    throw new IllegalArgumentException("Unknown ring backing object placement id "
                            + state.backingObjectPlacementId());
                }
                mapping.computeIfAbsent(backing, ignored -> new ArrayList<>()).add(ring);
            }
        }
        Map<ObjectSpawn, List<RingSpawn>> immutableMapping = new LinkedHashMap<>();
        mapping.forEach((object, groupedRings) -> immutableMapping.put(object, List.copyOf(groupedRings)));
        return new SpawnReplacement(List.copyOf(objects), List.copyOf(rings), Map.copyOf(immutableMapping));
    }

    private static Map<Integer, Integer> ringBackingIds(MutableLevel level) {
        Map<Integer, Integer> backingIds = new HashMap<>();
        level.ringObjectPlacementMapping().forEach((object, rings) -> {
            for (RingSpawn ring : rings) {
                Integer previous = backingIds.put(ring.placementId(), object.layoutIndex());
                if (previous != null && previous != object.layoutIndex()) {
                    throw new IllegalArgumentException("Ring placement has multiple backing objects: "
                            + ring.placementId());
                }
            }
        });
        return backingIds;
    }

    private static int requiredInt(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Missing or invalid editor envelope field " + field);
        }
        return value.intValue();
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("Missing or invalid editor envelope field " + field);
        }
        return value.textValue();
    }

    private ApplyResult remember(ApplyResult result) {
        lastApplyResult = result;
        return result;
    }

    private void quarantine(Path file, String reason) throws IOException {
        LOG.warning("Quarantining corrupt editor save " + file + ": " + reason);
        Files.move(file, uniqueCorruptSibling(file));
    }

    private Path uniqueCorruptSibling(Path file) {
        Path base = file.resolveSibling(file.getFileName() + ".corrupt");
        if (!Files.exists(base)) {
            return base;
        }
        for (int suffix = 1; suffix < Integer.MAX_VALUE; suffix++) {
            Path candidate = file.resolveSibling(file.getFileName() + ".corrupt." + suffix);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No available quarantine filename for " + file);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @com.openggf.game.ModApi
    public record SaveResult(boolean ok, Path file, String hash, ApplyResult persistenceStatus) {
    }

    @ModApi
    public record EditorApplyFinding(String code, String message, int skippedCount) {}

    private record SpawnReplacement(List<ObjectSpawn> objects,
                                    List<RingSpawn> rings,
                                    Map<ObjectSpawn, List<RingSpawn>> mapping) {
    }

    @com.openggf.game.ModApi
    public enum ApplyResult {
        NONE,
        APPLIED,
        QUARANTINED,
        MISMATCH,
        TRANSIENT_FAILURE,
        UNSUPPORTED
    }
}
