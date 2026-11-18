package com.openggf.editor.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.editor.project.EditorProjectLevel.LayoutSize;
import com.openggf.game.internal.NativeProjectLevel;
import com.openggf.game.internal.NativeProjectSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Portable native project. The matching native stage supplies the unchanged editor data. */
public final class NativeEditorProjectPackage {
    public static final String ENTRY = "native-project.json";
    public static final int MAX_BYTES = EditorProjectStore.MAX_BYTES;
    private static final int VERSION = 1;
    private static final int RESIZED_VERSION = 2;
    private static final String FORMAT = "OpenGGFNativeEditorProject";
    private static final ObjectMapper STATE_MAPPER = EditorProjectStore.projectMapper();
    private static final ObjectMapper MAPPER = STATE_MAPPER.copy()
            .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);

    private final Payload payload;
    private final EditorProjectMetadata metadata;

    private NativeEditorProjectPackage(Payload payload, EditorProjectMetadata metadata) {
        this.payload = payload;
        this.metadata = metadata;
    }

    public EditorProjectMetadata metadata() { return metadata; }
    public NativeProjectSource source() { return payload.source(); }

    public static Path export(Path destination, EditorProjectDocument document, NativeProjectLevel nativeLevel) throws IOException {
        var source = Objects.requireNonNull(document.nativeSource(), "native project source");
        var original = captureBase(nativeLevel, source);
        var edited = EditorProjectState.capture(document.snapshot());
        if (original.layers() != edited.layers() || original.blockGridSide() != edited.blockGridSide())
            throw new IOException("Native project export requires the source layout layers and grid");
        var dimensions = new LayoutSize(edited.width(), edited.height());
        LayoutSize resized = layoutSize(original).equals(dimensions) ? null : dimensions;
        var base = indexed(original);
        String baseChecksum = checksum(base);
        if (resized != null) {
            nativeLevel.validateProjectDimensions(resized.width(), resized.height());
            reshapeBaseline(base, original, resized);
        }
        var payload = new Payload(source, EditorProjectStore.MetadataState.capture(document.metadata()),
                document.assets(), baseChecksum, EditorProjectEdits.between(base, indexed(edited)), resized);
        int version = resized == null ? VERSION : RESIZED_VERSION;
        byte[] bytes = MAPPER.writeValueAsBytes(new Envelope(FORMAT, version, payload, checksum(MAPPER.valueToTree(payload))));
        if (bytes.length > MAX_BYTES) throw new IOException("Native project exceeds the 64 MiB storage limit");
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || Files.exists(target, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Export target already exists: " + target);
        Files.createDirectories(parent);
        boolean zipped = target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
        Path staging = zipped ? Files.createTempFile(parent, ".native-project-", ".zip")
                : Files.createTempDirectory(parent, ".native-project-");
        try {
            NativeEditorProjectPackage staged;
            if (zipped) {
                try (var output = new ZipOutputStream(Files.newOutputStream(staging))) {
                    output.putNextEntry(new ZipEntry(ENTRY));
                    output.write(bytes);
                }
                try (var archive = new ZipFile(staging.toFile())) {
                    ZipEntry entry = archive.getEntry(ENTRY);
                    if (archive.size() != 1 || entry == null) {
                        throw new IOException("Invalid staged native project archive");
                    }
                    try (var input = archive.getInputStream(entry)) {
                        staged = decode(EditorProjectStore.readBounded(input));
                    }
                }
            } else {
                Path file = staging.resolve(ENTRY);
                Files.write(file, bytes);
                staged = decode(EditorProjectStore.readBounded(file));
            }
            var roundTrip = staged.materialize(nativeLevel);
            if (!roundTrip.metadata().equals(document.metadata()) || !roundTrip.assets().equals(document.assets())
                    || !roundTrip.snapshot().contentEquals(document.level())) {
                throw new IOException("Native project export did not round-trip its authored data");
            }
            Files.move(staging, target);
            return target;
        } finally {
            if (!zipped) Files.deleteIfExists(staging.resolve(ENTRY));
            Files.deleteIfExists(staging);
        }
    }

    public static NativeEditorProjectPackage decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Native project exceeds the 64 MiB storage limit");
        try {
            var node = MAPPER.readTree(bytes);
            if (node == null || !node.isObject() || node.size() != 4
                    || !FORMAT.equals(node.path("format").textValue())
                    || !node.path("version").isIntegralNumber() || !node.path("version").canConvertToInt())
                throw new IOException("Unsupported native project format or version");
            int version = node.path("version").intValue();
            if (version != VERSION && version != RESIZED_VERSION)
                throw new IOException("Unsupported native project format or version");
            boolean hasResize = version == RESIZED_VERSION;
            var rawPayload = node.path("payload");
            if (!rawPayload.isObject() || rawPayload.size() != (hasResize ? 6 : 5)
                    || !rawPayload.has("edits") || rawPayload.has("resizedLayout") != hasResize
                    || !checksum(rawPayload).equals(node.path("checksum").textValue()))
                throw new IOException("Native project checksum does not match its payload");
            EditorProjectAssets.validateJson(rawPayload.get("assets"));
            var metadataState = STATE_MAPPER.treeToValue(rawPayload.path("metadata"), EditorProjectStore.MetadataState.class);
            var source = STATE_MAPPER.treeToValue(rawPayload.path("source"), NativeProjectSource.class);
            var assets = STATE_MAPPER.treeToValue(rawPayload.path("assets"), EditorProjectAssets.class);
            var baseChecksum = rawPayload.path("baseChecksum").textValue();
            var edits = MAPPER.treeToValue(rawPayload.get("edits"), EditorProjectEdits.class);
            LayoutSize resized = null;
            if (hasResize) {
                var size = rawPayload.get("resizedLayout");
                if (!size.isObject() || !isInteger(size.get("width")) || !isInteger(size.get("height")))
                    throw new IOException("Invalid native project layout size");
                resized = STATE_MAPPER.treeToValue(size, LayoutSize.class);
            }
            var payload = new Payload(source, metadataState, assets, baseChecksum, edits, resized);
            var metadata = metadataState.metadata();
            if (!metadata.sourceFingerprint().equals(payload.source().mediaIdentity())
                    || payload.baseChecksum() == null || !payload.baseChecksum().matches("[a-f0-9]{64}"))
                throw new IOException("Invalid native project source or baseline checksum");
            return new NativeEditorProjectPackage(payload, metadata);
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new IOException("Invalid native project: " + failure.getMessage(), failure);
        }
    }

    public EditorProjectDocument materialize(NativeProjectLevel nativeLevel) throws IOException {
        try {
            var original = captureBase(nativeLevel, source());
            var base = indexed(original);
            if (!payload.baseChecksum().equals(checksum(base)))
                throw new IOException("Native project requires the same decoded stage revision");
            LayoutSize dimensions = layoutSize(original);
            if (payload.resizedLayout() != null) {
                dimensions = payload.resizedLayout();
                nativeLevel.validateProjectDimensions(dimensions.width(), dimensions.height());
                reshapeBaseline(base, original, dimensions);
            }
            var result = payload.edits() == null ? base : payload.edits().apply(base);
            if (!(result instanceof ObjectNode level)) throw new IOException("Native project must produce an editor level");
            EditorProjectEdits.restorePlacements(level, "objects", "placementId");
            EditorProjectEdits.restorePlacements(level, "rings", "placementId");
            EditorProjectEdits.restorePlacements(level, "ringBacking", "objectPlacementId");
            var state = STATE_MAPPER.treeToValue(level, EditorProjectState.class);
            state.validate();
            if (!dimensions.equals(layoutSize(state)) || original.layers() != state.layers()
                    || original.blockGridSide() != state.blockGridSide() || original.zoneIndex() != state.zoneIndex())
                throw new IOException("Native project edits disagree with the declared layout or source grid");
            if (state.minX() != metadata.minX() || state.maxX() != metadata.maxX()
                    || state.minY() != metadata.minY() || state.maxY() != metadata.maxY())
                throw new IOException("Native project metadata and edited bounds disagree");
            var document = EditorProjectDocument.restore(metadata,
                    new EditorProjectLevel(state, nativeLevel.getRingSpriteSheet()), source());
            document.restoreAssets(payload.assets());
            return document;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new IOException("Invalid native project edits: " + failure.getMessage(), failure);
        }
    }

    private static EditorProjectState captureBase(NativeProjectLevel nativeLevel, NativeProjectSource source) throws IOException {
        if (!source.equals(nativeLevel.nativeProjectSource()))
            throw new IOException("Native project source does not match the opened stage");
        return EditorProjectState.captureNativeView(nativeLevel);
    }

    private static boolean isInteger(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToInt();
    }

    private static LayoutSize layoutSize(EditorProjectState state) {
        return new LayoutSize(state.width(), state.height());
    }

    private static void reshapeBaseline(ObjectNode base, EditorProjectState source, LayoutSize dimensions) {
        byte[] map = EditorProjectLevel.resizeMap(source.map(), source.layers(), layoutSize(source), dimensions, (byte) 0);
        base.put("width", dimensions.width());
        base.put("height", dimensions.height());
        base.set("map", BinaryNode.valueOf(map));
    }

    private static ObjectNode indexed(EditorProjectState state) {
        // Ring backing comes from a map. Its order has no editor meaning, so sort
        // before hashing the decoded source to give it a stable baseline identity.
        var ordered = new EditorProjectState(state.zoneIndex(), state.blockGridSide(), state.patterns(), state.chunks(),
                state.blocks(), state.solids(), state.palettes(), state.layers(), state.width(), state.height(),
                state.map(), state.objects(), state.rings(), state.ringBacking().stream()
                        .sorted(Comparator.comparingInt(EditorProjectState.BackingState::objectPlacementId)).toList(),
                state.minX(), state.maxX(), state.minY(), state.maxY());
        ObjectNode level = MAPPER.valueToTree(ordered);
        EditorProjectEdits.indexPlacements(level, "objects", "placementId");
        EditorProjectEdits.indexPlacements(level, "rings", "placementId");
        EditorProjectEdits.indexPlacements(level, "ringBacking", "objectPlacementId");
        return level;
    }

    private static String checksum(JsonNode node) throws IOException {
        return EditorProjectStore.checksum(MAPPER.writeValueAsBytes(node));
    }

    private record Envelope(String format, int version, Payload payload, String checksum) {}
    private record Payload(NativeProjectSource source, EditorProjectStore.MetadataState metadata,
            EditorProjectAssets assets, String baseChecksum, EditorProjectEdits edits,
            @JsonInclude(JsonInclude.Include.NON_NULL) LayoutSize resizedLayout) {}
}
