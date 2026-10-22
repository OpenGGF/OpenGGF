package com.openggf.stageplay;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Strict versioned JSON persistence with bounded reads and atomic staged publication. */
public final class StageplayStore {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final ObjectMapper JSON = createMapper();

    private StageplayStore() { }

    public static StageplayDocument read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) {
            throw new IOException("Stageplay file must be a regular file no larger than 8 MiB");
        }
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            // The second bound also covers a file growing after its size was checked.
            return decode(input.readNBytes(MAX_BYTES + 1));
        }
    }

    public static void write(Path path, StageplayDocument document) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes = encode(document);
        if (!document.equals(decode(bytes))) throw new IOException("Stageplay serialization did not round-trip");
        Path destination = path.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null || !Files.isDirectory(parent)) throw new IOException("Stageplay destination directory is missing");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Stageplay destination is not a regular file");
        }
        Path staged = Files.createTempFile(parent, ".stageplay-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (!document.equals(read(staged))) throw new IOException("Staged Stageplay scene did not validate");
            // Atomic rename is required: unsupported file systems fail with the original intact.
            Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    public static byte[] encode(StageplayDocument document) throws IOException {
        Objects.requireNonNull(document, "document");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        try (OutputStream bounded = new OutputStream() {
            private int count;

            @Override
            public void write(int value) throws IOException {
                reserve(1);
                bytes.write(value);
            }

            @Override
            public void write(byte[] source, int offset, int length) throws IOException {
                reserve(length);
                bytes.write(source, offset, length);
            }

            private void reserve(int length) throws IOException {
                if (length > MAX_BYTES - count) throw new IOException("Stageplay document exceeds 8 MiB");
                count += length;
            }
        }) {
            JSON.writeValue(bounded, document);
        }
        return bytes.toByteArray();
    }

    public static StageplayDocument decode(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Stageplay input must contain 1 byte to 8 MiB");
        try {
            var tree = JSON.readTree(bytes);
            if (!(tree instanceof ObjectNode object)) throw new IOException("Stageplay document must be an object");
            var version = object.get("version");
            if (version == null || !version.isIntegralNumber() || !version.canConvertToInt())
                throw new IOException("Stageplay version must be an integer");
            int sourceVersion = version.intValue();
            if (sourceVersion < 1 || sourceVersion > StageplayDocument.VERSION)
                throw new IOException("Unsupported Stageplay version: " + sourceVersion);
            if (sourceVersion <= 6) {
                if (object.has("objectActors") || object.has("worldCommands"))
                    throw new IOException("Version " + sourceVersion + " cannot contain object directing tracks");
                object.putArray("objectActors");
                object.putArray("worldCommands");
            }
            if (sourceVersion == 1) {
                // Exact legacy shapes remain authoritative: later fields cannot masquerade as old data.
                if (object.has("cameraEffects") || object.has("sceneChanges"))
                    throw new IOException("Version one cannot contain version two tracks");
                object.putArray("cameraEffects");
                object.putArray("sceneChanges");
            }
            if (sourceVersion <= 2) {
                if (object.has("dialogueTypers") || object.has("dialogueSkins") || object.has("dialogue"))
                    throw new IOException("Version " + sourceVersion + " cannot contain dialogue tracks");
                object.putArray("dialogueTypers");
                object.putArray("dialogueSkins");
                object.putArray("dialogue");
            }
            if (sourceVersion <= 3) {
                if (object.has("puppets")) throw new IOException("Legacy scenes cannot contain puppet tracks");
                object.putArray("puppets");
            }
            if (sourceVersion <= 4) {
                migrateSet((ObjectNode) object.get("set"));
                for (var change : object.withArray("sceneChanges")) migrateSet((ObjectNode) change.get("set"));
                for (var key : object.withArray("camera")) {
                    if (key.has("follow")) throw new IOException("Legacy scenes cannot contain camera following");
                    ((ObjectNode) key).set("follow", JSON.valueToTree(StageplayDocument.CameraFollow.none()));
                }
                for (var track : object.withArray("puppets")) for (var command : track.withArray("commands")) {
                    var cue = (ObjectNode) command;
                    if (cue.has("target") || cue.has("condition") || cue.has("timeoutFrames"))
                        throw new IOException("Legacy scenes cannot contain live intents");
                    cue.set("target", JSON.valueToTree(StageplayDocument.ObjectTarget.none()));
                    cue.put("condition", "NONE").put("timeoutFrames", 0);
                }
            }
            if (sourceVersion <= 5) {
                migrateWorldPolicy((ObjectNode) object.get("set"));
                for (var change : object.withArray("sceneChanges")) migrateWorldPolicy((ObjectNode) change.get("set"));
            }
            object.put("version", StageplayDocument.VERSION);
            return JSON.treeToValue(object, StageplayDocument.class);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid Stageplay document", error);
        }
    }

    private static void migrateSet(ObjectNode set) throws IOException {
        if (set.has("worldPolicy")) throw new IOException("Legacy scenes cannot contain worldPolicy");
        set.put("worldPolicy", "TERRAIN_ONLY");
    }

    private static void migrateWorldPolicy(ObjectNode set) throws IOException {
        var policy = set.get("worldPolicy");
        if (policy == null || !policy.isTextual()) throw new IOException("worldPolicy must be a string");
        switch (policy.textValue()) {
            case "TERRAIN_ONLY" -> set.put("worldPolicy", "LEGACY_TERRAIN_ONLY");
            case "LIVE" -> set.put("worldPolicy", "LEGACY_LIVE");
            default -> throw new IOException("Unknown legacy worldPolicy: " + policy.textValue());
        }
    }

    private static ObjectMapper createMapper() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(16).maxStringLength(4096).maxNameLength(64)
                        .maxNumberLength(32).maxDocumentLength(MAX_BYTES).maxTokenCount(3_000_000).build())
                .build();
        ObjectMapper mapper = JsonMapper.builder(factory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }
}
