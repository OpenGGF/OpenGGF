package com.openggf.editor.project;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.GameId;
import com.openggf.game.modzone.ModObjectZoneSet;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Bounded complete .ogfproject files; failure never replaces the preceding project. */
public final class EditorProjectStore {
    private static final String FORMAT = "OpenGGFEditorProject";
    private static final int VERSION = 1;
    static final int MAX_BYTES = 64 * 1024 * 1024;
    private final ObjectMapper mapper;
    private final AtomicPublisher publisher;

    public EditorProjectStore() {
        this((temporary, target) -> Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    EditorProjectStore(AtomicPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        StreamReadConstraints limits = StreamReadConstraints.builder().maxDocumentLength(MAX_BYTES)
                .maxNestingDepth(24).maxTokenCount(4_000_000).maxStringLength(MAX_BYTES)
                .maxNameLength(64).maxNumberLength(10).build();
        JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(limits).build();
        mapper = new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public void save(Path file, EditorProjectDocument document) throws IOException {
        Path target = projectPath(file);
        Objects.requireNonNull(document, "document");
        EditorProjectMetadata metadata = document.metadata();
        EditorProjectLevel level = document.snapshot();
        Payload payload = new Payload(MetadataState.capture(metadata), EditorProjectState.capture(level));
        byte[] payloadBytes = mapper.writeValueAsBytes(payload);
        byte[] bytes = mapper.writeValueAsBytes(new Envelope(FORMAT, VERSION, payload, checksum(payloadBytes)));
        if (bytes.length > MAX_BYTES) throw new IOException("Project exceeds the 64 MiB storage limit");
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getFileName() + "-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Read back the actual staged bytes, not only the in-memory snapshot.
            decode(readBounded(temporary));
            publisher.publish(temporary, target);
            document.markSaved(target, metadata, level);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public EditorProjectDocument open(Path file) throws IOException {
        Path source = projectPath(file);
        EditorProjectDocument document = decode(readBounded(source));
        document.markSaved(source, document.metadata(), document.snapshot());
        return document;
    }

    /** Decodes a bounded owned asset without retaining a filesystem path. */
    public EditorProjectDocument decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Project exceeds the 64 MiB storage limit");
        try (JsonParser parser = mapper.createParser(bytes)) {
            JsonNode node = mapper.readTree(parser);
            if (node == null || !node.isObject() || parser.nextToken() != null) throw new IOException("Project must contain exactly one JSON object");
            Set<String> allowed = Set.of("format", "version", "payload", "checksum");
            var fields = node.fieldNames();
            while (fields.hasNext()) if (!allowed.contains(fields.next())) throw new IOException("Unknown project envelope field");
            if (!node.path("format").isTextual() || !FORMAT.equals(node.path("format").textValue())
                    || !node.path("version").isIntegralNumber() || !node.path("version").canConvertToInt()
                    || node.path("version").intValue() != VERSION) throw new IOException("Unsupported project format or version");
            JsonNode rawPayload = node.get("payload");
            if (rawPayload == null || !rawPayload.isObject() || !node.path("checksum").isTextual()
                    || !checksum(mapper.writeValueAsBytes(rawPayload)).equals(node.path("checksum").textValue())) {
                throw new IOException("Project checksum does not match its complete payload");
            }
            Payload payload = mapper.treeToValue(rawPayload, Payload.class);
            payload.level().validate();
            EditorProjectMetadata metadata = payload.metadata().metadata();
            EditorProjectLevel level = new EditorProjectLevel(payload.level(), null);
            if (metadata.minX() != level.getMinX() || metadata.maxX() != level.getMaxX()
                    || metadata.minY() != level.getMinY() || metadata.maxY() != level.getMaxY()) {
                throw new IOException("Project metadata and level bounds disagree");
            }
            return EditorProjectDocument.create(metadata, level);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("Invalid project data: " + error.getMessage(), error);
        }
    }

    private static Path projectPath(Path file) {
        Path path = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (path.getFileName() == null || !path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogfproject")) {
            throw new IllegalArgumentException("Project files must use the .ogfproject extension");
        }
        return path;
    }

    private static byte[] readBounded(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES) throw new IOException("Project exceeds the 64 MiB storage limit");
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Project exceeds the 64 MiB storage limit");
            return bytes;
        }
    }

    private static String checksum(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    record Envelope(String format, int version, Payload payload, String checksum) {}
    record Payload(MetadataState metadata, EditorProjectState level) {}
    record MetadataState(String gameCode, String name, int sourceZone, int sourceAct, String sourceFingerprint,
            int startX, int startY, int minX, int maxX, int minY, int maxY, MusicState music, String objectZoneSet) {
        static MetadataState capture(EditorProjectMetadata value) {
            return new MetadataState(value.gameId().code(), value.name(), value.sourceZone(), value.sourceAct(), value.sourceFingerprint(),
                    value.startX(), value.startY(), value.minX(), value.maxX(), value.minY(), value.maxY(), MusicState.capture(value.music()),
                    value.objectZoneSet() == null ? null : value.objectZoneSet().name());
        }
        EditorProjectMetadata metadata() {
            return new EditorProjectMetadata(GameId.fromCode(gameCode), name, sourceZone, sourceAct, sourceFingerprint,
                    startX, startY, minX, maxX, minY, maxY, music.music(), objectZoneSet == null ? null : ModObjectZoneSet.valueOf(objectZoneSet));
        }
    }
    record MusicState(Integer stockId, String owner, String localName) {
        static MusicState capture(FullLevelExporter.ExportMusic value) {
            return value instanceof FullLevelExporter.ExportMusic.Stock stock ? new MusicState(stock.id(), null, null)
                    : new MusicState(null, ((FullLevelExporter.ExportMusic.Track) value).owner(), ((FullLevelExporter.ExportMusic.Track) value).localName());
        }
        FullLevelExporter.ExportMusic music() {
            if (stockId != null && owner == null && localName == null) return new FullLevelExporter.ExportMusic.Stock(stockId);
            if (stockId == null && owner != null && localName != null) return new FullLevelExporter.ExportMusic.Track(owner, localName);
            throw new IllegalArgumentException("Project music requires exactly one stock id or owned track key");
        }
    }
    @FunctionalInterface interface AtomicPublisher { void publish(Path temporary, Path target) throws IOException; }
}
