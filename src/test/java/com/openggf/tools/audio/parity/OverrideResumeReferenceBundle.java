package com.openggf.tools.audio.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Fail-closed reader for the atomic override-resume fixture commit object.
 * Individual leaves and legacy game directories never confer authority.
 */
public final class OverrideResumeReferenceBundle {
    public static final String BUNDLE_NAME = "override-resume-first-divergence-v1";
    static final String LIMITATION_CODE =
            "FRESH_AUTHENTICATED_NATIVE_GPGX_AUTHORITY_UNAVAILABLE";
    static final List<String> EXACT_INVENTORY = List.of(
            "s1/s1-override-resume-reference.v1.jsonl.gz",
            "s1/s1-override-resume-metadata.v1.json",
            "s2/s2-override-resume-reference.v1.jsonl.gz",
            "s2/s2-override-resume-metadata.v1.json");
    private static final Set<String> METADATA_FIELDS = Set.of(
            "schema", "game", "raw_sha256", "raw_byte_count",
            "attestation_sha256", "record_count", "logical_byte_count",
            "logical_sha256", "stored_byte_count", "stored_sha256",
            "bundle_relative_root", "bundle_member_inventory",
            "publication_protocol", "namespace_lock_precondition");
    private static final Set<String> REFERENCE_FIELDS = Set.of(
            "schema", "game", "boundary", "pcm");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_MEMBER_BYTES = 1024 * 1024;

    record GameReference(byte[] logicalBytes, JsonNode reference, JsonNode metadata) {
        GameReference {
            logicalBytes = logicalBytes.clone();
        }

        @Override
        public byte[] logicalBytes() {
            return logicalBytes.clone();
        }
    }

    public static final class ReferenceUnavailableException extends IOException {
        private final String code;

        ReferenceUnavailableException(String message) {
            super(message);
            code = LIMITATION_CODE;
        }

        public String code() {
            return code;
        }
    }

    static final class InvalidBundleException extends IOException {
        InvalidBundleException(String message) {
            super(message);
        }

        InvalidBundleException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final GameReference s1;
    private final GameReference s2;

    private OverrideResumeReferenceBundle(GameReference s1, GameReference s2) {
        this.s1 = s1;
        this.s2 = s2;
    }

    public static OverrideResumeReferenceBundle open(Path parityRoot) throws IOException {
        if (parityRoot == null || !parityRoot.isAbsolute()) {
            throw new InvalidBundleException("parity root must be absolute");
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parityRoot)) {
            if (!(stream instanceof SecureDirectoryStream<Path> root)) {
                throw new InvalidBundleException(
                        "platform does not provide secure directory traversal");
            }
            BasicFileAttributes attributes;
            try {
                attributes = attributes(root, Path.of(BUNDLE_NAME));
            } catch (NoSuchFileException missing) {
                throw new ReferenceUnavailableException(
                        LIMITATION_CODE + ": atomic reference bundle is absent");
            }
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                throw new InvalidBundleException("bundle commit object is not a real directory");
            }
            try (SecureDirectoryStream<Path> bundle = root.newDirectoryStream(
                    Path.of(BUNDLE_NAME), LinkOption.NOFOLLOW_LINKS)) {
                requireInventory(bundle, Set.of("s1", "s2"), "bundle");
                GameReference s1 = readGame(bundle, "s1");
                GameReference s2 = readGame(bundle, "s2");
                return new OverrideResumeReferenceBundle(s1, s2);
            }
        } catch (ReferenceUnavailableException | InvalidBundleException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new InvalidBundleException("bundle commit validation failed", failure);
        }
    }

    GameReference s1() {
        return s1;
    }

    GameReference s2() {
        return s2;
    }

    List<String> memberInventory() {
        return EXACT_INVENTORY;
    }

    private static GameReference readGame(SecureDirectoryStream<Path> bundle,
            String game) throws IOException {
        BasicFileAttributes attributes = attributes(bundle, Path.of(game));
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new InvalidBundleException(game + " member is not a real directory");
        }
        try (SecureDirectoryStream<Path> directory = bundle.newDirectoryStream(
                Path.of(game), LinkOption.NOFOLLOW_LINKS)) {
            String referenceName = game + "-override-resume-reference.v1.jsonl.gz";
            String metadataName = game + "-override-resume-metadata.v1.json";
            requireInventory(directory, Set.of(referenceName, metadataName), game);
            byte[] stored = readRegular(directory, Path.of(referenceName));
            byte[] metadataBytes = readRegular(directory, Path.of(metadataName));
            byte[] logical = gunzip(stored);
            JsonNode reference = parseSingleLfRecord(logical, game + " reference");
            JsonNode metadata = parseSingleLfRecord(metadataBytes, game + " metadata");
            validate(game, stored, logical, reference, metadata);
            return new GameReference(logical, reference, metadata);
        }
    }

    private static void validate(String game, byte[] stored, byte[] logical,
            JsonNode reference, JsonNode metadata) throws IOException {
        requireExactFields(reference, REFERENCE_FIELDS, game + " reference");
        requireText(reference, "schema",
                "openggf.override-resume-first-divergence-reference.v1");
        requireText(reference, "game", game);
        if (!reference.path("boundary").isObject() || !reference.path("pcm").isObject()) {
            throw new InvalidBundleException(game + " reference payload is incomplete");
        }

        requireExactFields(metadata, METADATA_FIELDS, game + " metadata");
        requireText(metadata, "schema",
                "openggf.override-resume-first-divergence-metadata.v1");
        requireText(metadata, "game", game);
        requireText(metadata, "bundle_relative_root",
                "src/test/resources/audio/parity/" + BUNDLE_NAME);
        requireText(metadata, "publication_protocol",
                "linux-atomic-bundle-rename-noreplace-v1");
        if (metadata.path("namespace_lock_precondition").asText().isBlank()) {
            throw new InvalidBundleException("namespace/lock precondition is absent");
        }
        List<String> inventory = new ArrayList<>();
        metadata.path("bundle_member_inventory").forEach(
                member -> inventory.add(member.asText()));
        if (!inventory.equals(EXACT_INVENTORY)) {
            throw new InvalidBundleException("metadata bundle inventory is not exact");
        }
        if (metadata.path("record_count").asInt(-1) != 1
                || metadata.path("logical_byte_count").asLong(-1) != logical.length
                || metadata.path("stored_byte_count").asLong(-1) != stored.length
                || !metadata.path("logical_sha256").asText().equals(digest(logical))
                || !metadata.path("stored_sha256").asText().equals(digest(stored))) {
            throw new InvalidBundleException(game + " count or digest metadata disagrees");
        }
        requireDigestPair(metadata.path("raw_sha256"), "raw_sha256");
        requireDigestPair(metadata.path("attestation_sha256"), "attestation_sha256");
    }

    private static void requireDigestPair(JsonNode value, String label)
            throws InvalidBundleException {
        if (!value.isArray() || value.size() != 2) {
            throw new InvalidBundleException(label + " is not an exact pair");
        }
        for (JsonNode digest : value) {
            if (!digest.isTextual() || !digest.asText().matches("[0-9a-f]{64}")) {
                throw new InvalidBundleException(label + " contains an invalid digest");
            }
        }
    }

    private static void requireText(JsonNode object, String field, String expected)
            throws InvalidBundleException {
        if (!object.path(field).isTextual() || !object.path(field).asText().equals(expected)) {
            throw new InvalidBundleException(field + " is invalid");
        }
    }

    private static void requireExactFields(JsonNode object, Set<String> expected,
            String label) throws InvalidBundleException {
        if (!object.isObject()) {
            throw new InvalidBundleException(label + " is not an object");
        }
        Set<String> actual = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new InvalidBundleException(label + " schema inventory is not exact");
        }
    }

    private static void requireInventory(SecureDirectoryStream<Path> directory,
            Set<String> expected, String label) throws IOException {
        Set<String> actual = new LinkedHashSet<>();
        for (Path entry : directory) {
            actual.add(entry.getFileName().toString());
        }
        if (!actual.equals(expected)) {
            throw new InvalidBundleException(label + " inventory is not exact: " + actual);
        }
    }

    private static BasicFileAttributes attributes(SecureDirectoryStream<Path> directory,
            Path name) throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(name,
                BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        return view.readAttributes();
    }

    private static byte[] readRegular(SecureDirectoryStream<Path> directory,
            Path name) throws IOException {
        BasicFileAttributes attributes = attributes(directory, name);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()
                || attributes.size() <= 0 || attributes.size() > MAX_MEMBER_BYTES) {
            throw new InvalidBundleException(name + " is not a bounded regular member");
        }
        Set<OpenOption> options = Set.of(StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = directory.newByteChannel(name, options)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (channel.read(buffer) >= 0) {
                buffer.flip();
                output.write(buffer.array(), 0, buffer.remaining());
                buffer.clear();
                if (output.size() > MAX_MEMBER_BYTES) {
                    throw new InvalidBundleException(name + " exceeds the size bound");
                }
            }
            return output.toByteArray();
        }
    }

    private static byte[] gunzip(byte[] stored) throws IOException {
        if (stored.length < 18 || stored[0] != 0x1f || stored[1] != (byte) 0x8b) {
            throw new InvalidBundleException("reference is not gzip");
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(stored));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            if (output.size() > MAX_MEMBER_BYTES) {
                throw new InvalidBundleException("logical reference exceeds size bound");
            }
            return output.toByteArray();
        }
    }

    private static JsonNode parseSingleLfRecord(byte[] bytes, String label)
            throws IOException {
        if (bytes.length == 0 || bytes[bytes.length - 1] != '\n') {
            throw new InvalidBundleException(label + " is not LF-terminated");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception failure) {
            throw new InvalidBundleException(label + " is not strict UTF-8", failure);
        }
        if (text.indexOf('\r') >= 0 || text.indexOf('\n') != text.length() - 1) {
            throw new InvalidBundleException(label + " is not one LF record");
        }
        try {
            return JSON.readTree(text.substring(0, text.length() - 1));
        } catch (IOException failure) {
            throw new InvalidBundleException(label + " is not valid JSON", failure);
        }
    }

    private static String digest(byte[] value) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception failure) {
            throw new IOException("SHA-256 unavailable", failure);
        }
    }
}
