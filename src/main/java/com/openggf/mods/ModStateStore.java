package com.openggf.mods;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.openggf.io.ModInputLimits;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Strict, bounded persistence for repository-local pending mod state. */
public final class ModStateStore {
    private static final Set<String> ROOT_FIELDS = Set.of("formatVersion", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of(
            "id", "enabled", "order", "trusted", "trustedJarSha256");

    private final Path root;
    private final Path statePath;
    private final ModInputLimits limits;
    private final FileOperations files;
    private final JsonFactory jsonFactory;

    public ModStateStore(Path normalizedModRoot) {
        this(normalizedModRoot, ModInputLimits.production(), FileOperations.SYSTEM);
    }

    public ModStateStore(Path normalizedModRoot, ModInputLimits limits) {
        this(normalizedModRoot, limits, FileOperations.SYSTEM);
    }

    ModStateStore(Path normalizedModRoot, ModInputLimits limits, FileOperations files) {
        Objects.requireNonNull(normalizedModRoot, "normalizedModRoot");
        Path normalized = normalizedModRoot.toAbsolutePath().normalize();
        if (!normalizedModRoot.equals(normalized)) {
            throw new IllegalArgumentException("Mod root must be absolute and normalized: " + normalizedModRoot);
        }
        this.root = normalizedModRoot;
        this.statePath = root.resolve("modstate.json");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.files = Objects.requireNonNull(files, "files");
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(limits.maxYamlDepth())
                .maxDocumentLength(limits.maxDocumentCodePoints())
                .maxTokenCount(Math.max(32L, limits.maxCollectionEntries() * 12L + 16L))
                .maxStringLength(limits.maxStringChars())
                .maxNameLength(limits.maxStringChars())
                .maxNumberLength(limits.maxNumericDigits())
                .build();
        this.jsonFactory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(constraints)
                .build();
    }

    public Path statePath() {
        return statePath;
    }

    public LoadResult load() {
        RootIdentity identity = null;
        try {
            if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
                return LoadResult.empty();
            }
            identity = RootIdentity.capture(root);
            files.checkpoint(Boundary.AFTER_ROOT_CAPTURE, root);
            identity.verify();
            if (Files.notExists(statePath, LinkOption.NOFOLLOW_LINKS)) {
                identity.verify();
                return LoadResult.empty();
            }
            byte[] bytes = readMetadataBounded(identity);
            identity.verify();
            return new LoadResult(parse(bytes), Optional.empty(), Optional.empty());
        } catch (UnsafeRoot error) {
            return unsafeLoad(error);
        } catch (IOException | IllegalArgumentException error) {
            return identity == null ? unsafeLoad(new UnsafeRoot(
                    "Mod root identity was not stabilized before load failure", error))
                    : quarantine(error, identity);
        }
    }

    public ModStateSaveResult save(ModState state) {
        Objects.requireNonNull(state, "state");
        RootIdentity identity = null;
        StagingDirectory staging = null;
        try {
            byte[] encoded = encode(state);
            if (encoded.length > limits.maxMetadataBytes()) {
                throw new IOException("Serialized mod state exceeds metadata byte limit");
            }
            parse(encoded);
            ensureWritableRoot();
            identity = RootIdentity.capture(root);
            files.checkpoint(Boundary.AFTER_ROOT_CAPTURE, root);
            identity.verify();
            staging = createStaging(identity);
            OwnedTemporary temporary = openTemporary(staging, identity);
            try (FileChannel channel = temporary.channel()) {
                files.checkpoint(Boundary.AFTER_TEMP_OPEN, temporary.path());
                files.write(channel, encoded);
                channel.force(true);
            }
            files.checkpoint(Boundary.AFTER_TEMP_WRITE, temporary.path());
            identity.verify();
            validateOwnedStateFile(temporary.path(), temporary.fileKey(), encoded, staging);
            files.move(temporary.path(), statePath,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            identity.verify();
            validateOwnedStateFile(statePath, temporary.fileKey(), encoded, staging);
            return new ModStateSaveResult.Saved();
        } catch (IOException | SecurityException error) {
            return new ModStateSaveResult.Failed(safeMessage(error));
        } finally {
            if (identity != null && staging != null) {
                try {
                    cleanupStaging(identity, staging);
                } catch (IOException | SecurityException ignored) {
                    // Refuse cleanup unless the private staging marker and root remain verified.
                }
            }
        }
    }

    private byte[] readMetadataBounded(RootIdentity identity) throws IOException {
        identity.verify();
        BasicFileAttributes attributes = Files.readAttributes(
                statePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        identity.verify();
        if (!attributes.isRegularFile() || attributes.size() > limits.maxMetadataBytes()) {
            throw new IOException("modstate.json is not a bounded regular file");
        }
        identity.verify();
        FileChannel opened = FileChannel.open(statePath,
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try {
            identity.verify();
        } catch (IOException error) {
            opened.close();
            throw error;
        }
        try (FileChannel input = opened;
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) attributes.size())) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long total = 0;
            while (input.read(buffer) != -1) {
                buffer.flip();
                total = Math.addExact(total, buffer.remaining());
                if (total > limits.maxMetadataBytes()) {
                    throw new IOException("modstate.json exceeds metadata byte limit");
                }
                output.write(buffer.array(), 0, buffer.remaining());
                buffer.clear();
            }
            byte[] bytes = output.toByteArray();
            identity.verify();
            return bytes;
        }
    }

    private ModState parse(byte[] bytes) throws IOException {
        try (JsonParser parser = jsonFactory.createParser(bytes)) {
            requireToken(parser.nextToken(), JsonToken.START_OBJECT, "Mod state root must be an object");
            Integer formatVersion = null;
            List<ModState.Entry> entries = null;
            int fields = 0;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                requireToken(parser.currentToken(), JsonToken.FIELD_NAME, "Expected mod-state field");
                if (++fields > limits.maxCollectionEntries()) {
                    throw new IOException("Mod-state root exceeds collection-entry limit");
                }
                String name = parser.currentName();
                if (!ROOT_FIELDS.contains(name)) {
                    throw new IOException("Unknown mod-state field: " + name);
                }
                JsonToken value = parser.nextToken();
                if (name.equals("formatVersion")) {
                    formatVersion = requiredInt(parser, value, "formatVersion");
                } else {
                    entries = parseEntries(parser, value);
                }
            }
            if (parser.nextToken() != null) {
                throw new IOException("Trailing JSON content is not allowed");
            }
            if (formatVersion == null || entries == null) {
                throw new IOException("formatVersion and entries are required");
            }
            return new ModState(formatVersion, entries);
        }
    }

    private List<ModState.Entry> parseEntries(JsonParser parser, JsonToken token) throws IOException {
        requireToken(token, JsonToken.START_ARRAY, "entries must be an array");
        List<ModState.Entry> entries = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (entries.size() >= limits.maxCollectionEntries()) {
                throw new IOException("entries exceeds collection-entry limit");
            }
            requireToken(parser.currentToken(), JsonToken.START_OBJECT, "Each state entry must be an object");
            entries.add(parseEntry(parser));
        }
        return List.copyOf(entries);
    }

    private ModState.Entry parseEntry(JsonParser parser) throws IOException {
        String id = null;
        Boolean enabled = null;
        Integer order = null;
        boolean trusted = false;
        String trustedJarSha256 = null;
        int fields = 0;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            requireToken(parser.currentToken(), JsonToken.FIELD_NAME, "Expected state-entry field");
            if (++fields > limits.maxCollectionEntries()) {
                throw new IOException("State entry exceeds collection-entry limit");
            }
            String name = parser.currentName();
            if (!ENTRY_FIELDS.contains(name)) {
                throw new IOException("Unknown state-entry field: " + name);
            }
            JsonToken value = parser.nextToken();
            switch (name) {
                case "id" -> {
                    requireToken(value, JsonToken.VALUE_STRING, "entry id must be a string");
                    id = parser.getText();
                }
                case "enabled" -> {
                    if (value != JsonToken.VALUE_TRUE && value != JsonToken.VALUE_FALSE) {
                        throw new IOException("entry enabled must be a boolean");
                    }
                    enabled = value == JsonToken.VALUE_TRUE;
                }
                case "order" -> order = requiredInt(parser, value, "entry order");
                case "trusted" -> {
                    if (value != JsonToken.VALUE_TRUE && value != JsonToken.VALUE_FALSE) {
                        throw new IOException("entry trusted must be a boolean");
                    }
                    trusted = value == JsonToken.VALUE_TRUE;
                }
                case "trustedJarSha256" -> {
                    if (value == JsonToken.VALUE_NULL) {
                        trustedJarSha256 = null;
                    } else {
                        requireToken(value, JsonToken.VALUE_STRING,
                                "entry trustedJarSha256 must be a string or null");
                        trustedJarSha256 = parser.getText();
                    }
                }
                default -> throw new IOException("Unknown state-entry field: " + name);
            }
        }
        if (id == null || enabled == null || order == null) {
            throw new IOException("State entries require id, enabled, and order");
        }
        return new ModState.Entry(id, enabled, order, trusted, trustedJarSha256);
    }

    private static int requiredInt(JsonParser parser, JsonToken token, String field) throws IOException {
        requireToken(token, JsonToken.VALUE_NUMBER_INT, field + " must be an integer");
        long value = parser.getLongValue();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException(field + " exceeds integer range");
        }
        return (int) value;
    }

    private byte[] encode(ModState state) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeNumberField("formatVersion", state.formatVersion());
            generator.writeArrayFieldStart("entries");
            for (ModState.Entry entry : state.entries()) {
                generator.writeStartObject();
                generator.writeStringField("id", entry.id());
                generator.writeBooleanField("enabled", entry.enabled());
                generator.writeNumberField("order", entry.order());
                generator.writeBooleanField("trusted", entry.trusted());
                if (entry.trustedJarSha256() == null) {
                    generator.writeNullField("trustedJarSha256");
                } else {
                    generator.writeStringField("trustedJarSha256", entry.trustedJarSha256());
                }
                generator.writeEndObject();
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
        return output.toByteArray();
    }

    private LoadResult quarantine(Throwable parseFailure, RootIdentity identity) {
        Path quarantine = root.resolve("modstate.json." + UUID.randomUUID() + ".corrupt");
        try {
            identity.verify();
            files.move(statePath, quarantine);
            identity.verify();
            return new LoadResult(ModState.EMPTY, Optional.of(quarantine),
                    Optional.of(safeMessage(parseFailure)));
        } catch (UnsafeRoot unsafe) {
            return unsafeLoad(unsafe);
        } catch (IOException | SecurityException quarantineFailure) {
            return new LoadResult(ModState.EMPTY, Optional.empty(), Optional.of(
                    safeMessage(parseFailure) + "; quarantine failed: " + safeMessage(quarantineFailure)));
        }
    }

    private static LoadResult unsafeLoad(UnsafeRoot error) {
        return new LoadResult(ModState.EMPTY, Optional.empty(), Optional.of(safeMessage(error)));
    }

    private void ensureWritableRoot() throws IOException {
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(root);
        }
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Mod root is not a non-symlink directory");
        }
    }

    private StagingDirectory createStaging(RootIdentity identity) throws IOException {
        for (int attempt = 0; attempt < 8; attempt++) {
            String token = UUID.randomUUID().toString();
            Path path = root.resolve(".modstate-stage-" + token);
            try {
                identity.verify();
                Files.createDirectory(path);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Retry with a fresh bounded random name.
                continue;
            }
            Path marker = path.resolve(".owner");
            try {
                files.checkpoint(Boundary.AFTER_STAGE_CREATE, path);
                identity.verify();
                files.writeMarker(marker, token);
                identity.verify();
                return new StagingDirectory(path, marker, token);
            } catch (IOException | SecurityException error) {
                IOException failure = error instanceof IOException io
                        ? io : new IOException("Unable to initialize mod-state staging", error);
                try {
                    cleanupPartialStaging(path, marker, token);
                } catch (IOException | SecurityException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }
        throw new IOException("Unable to allocate private mod-state staging directory");
    }

    private OwnedTemporary openTemporary(StagingDirectory staging, RootIdentity identity)
            throws IOException {
        for (int attempt = 0; attempt < 8; attempt++) {
            Path candidate = staging.path().resolve("state-" + UUID.randomUUID() + ".tmp");
            FileChannel channel;
            try {
                identity.verify();
                channel = FileChannel.open(candidate, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                continue;
            }
            try {
                files.checkpoint(Boundary.AFTER_TEMP_CHANNEL_CREATE, candidate);
                BasicFileAttributes attributes = Files.readAttributes(
                        candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (Files.isSymbolicLink(candidate) || !attributes.isRegularFile()) {
                    throw new IOException("Mod-state staging entry is not a regular file");
                }
                return new OwnedTemporary(candidate, attributes.fileKey(), channel);
            } catch (IOException | SecurityException error) {
                IOException failure = error instanceof IOException io
                        ? io : new IOException("Unable to initialize mod-state temporary file", error);
                try {
                    channel.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException | SecurityException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }
        throw new IOException("Unable to allocate private mod-state temporary file");
    }

    private static void cleanupPartialStaging(Path path, Path marker, String token)
            throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || !path.getFileName().toString().equals(".modstate-stage-" + token)) {
            throw new IOException("Refusing to clean unverified partial mod-state staging");
        }
        List<Path> children;
        try (var listed = Files.list(path)) {
            children = listed.toList();
        }
        if (children.isEmpty()) {
            Files.delete(path);
            return;
        }
        if (children.size() == 1 && children.getFirst().equals(marker)
                && !Files.isSymbolicLink(marker)
                && Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(marker);
            Files.delete(path);
            return;
        }
        throw new IOException("Refusing to clean unverified partial mod-state staging contents");
    }

    private void validateOwnedStateFile(Path path, Object ownedFileKey, byte[] expected,
                                        StagingDirectory staging) throws IOException {
        validateStaging(staging);
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Mod-state publication path is not a regular non-symlink file");
        }
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (ownedFileKey != null && attributes.fileKey() != null
                && !ownedFileKey.equals(attributes.fileKey())) {
            throw new IOException("Mod-state temporary identity changed before publication");
        }
        byte[] actual;
        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             ByteArrayOutputStream output = new ByteArrayOutputStream(expected.length)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                output.write(buffer.array(), 0, buffer.remaining());
                buffer.clear();
                if (output.size() > limits.maxMetadataBytes()) {
                    throw new IOException("Published mod state exceeds metadata limit");
                }
            }
            actual = output.toByteArray();
        }
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new IOException("Published mod-state content changed before verification");
        }
    }

    private static void validateStaging(StagingDirectory staging) throws IOException {
        if (Files.isSymbolicLink(staging.path())
                || !Files.isDirectory(staging.path(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(staging.marker())
                || !Files.isRegularFile(staging.marker(), LinkOption.NOFOLLOW_LINKS)
                || !staging.token().equals(Files.readString(staging.marker()))) {
            throw new IOException("Unverified mod-state staging directory");
        }
    }

    private static void cleanupStaging(RootIdentity identity, StagingDirectory staging)
            throws IOException {
        identity.verify();
        validateStaging(staging);
        try (var paths = Files.walk(staging.path())) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        identity.verify();
    }

    private static boolean sameIdentity(Object expectedFileKey, FileTime expectedCreationTime,
                                        BasicFileAttributes current) {
        return expectedFileKey != null && current.fileKey() != null
                ? expectedFileKey.equals(current.fileKey())
                : expectedCreationTime != null && expectedCreationTime.equals(current.creationTime());
    }

    private static void requireToken(JsonToken actual, JsonToken expected, String message) throws IOException {
        if (actual != expected) {
            throw new IOException(message);
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    public record LoadResult(ModState state, Optional<Path> quarantinedPath, Optional<String> message) {
        public LoadResult {
            Objects.requireNonNull(state, "state");
            quarantinedPath = Objects.requireNonNull(quarantinedPath, "quarantinedPath");
            message = Objects.requireNonNull(message, "message");
        }

        private static LoadResult empty() {
            return new LoadResult(ModState.EMPTY, Optional.empty(), Optional.empty());
        }
    }

    interface FileOperations {
        FileOperations SYSTEM = new FileOperations() {
            @Override
            public void write(FileChannel channel, byte[] bytes) throws IOException {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }

            @Override
            public void move(Path source, Path target, CopyOption... options) throws IOException {
                Files.move(source, target, options);
            }
        };

        void write(FileChannel channel, byte[] bytes) throws IOException;

        void move(Path source, Path target, CopyOption... options) throws IOException;

        default void writeMarker(Path marker, String token) throws IOException {
            Files.writeString(marker, token, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        }

        default void checkpoint(Boundary boundary, Path path) throws IOException {
        }
    }

    enum Boundary {
        AFTER_ROOT_CAPTURE,
        AFTER_STAGE_CREATE,
        AFTER_TEMP_CHANNEL_CREATE,
        AFTER_TEMP_OPEN,
        AFTER_TEMP_WRITE
    }

    private record StagingDirectory(Path path, Path marker, String token) {
    }

    private record OwnedTemporary(Path path, Object fileKey, FileChannel channel) {
    }

    private record RootIdentity(Path root, Path realPath, Object fileKey, FileTime creationTime,
                                Path parent, Path parentRealPath, Object parentFileKey,
                                FileTime parentCreationTime) {
        private static RootIdentity capture(Path root) throws UnsafeRoot {
            try {
                Path parent = root.getParent();
                if (parent == null || Files.isSymbolicLink(parent)
                        || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                    throw new UnsafeRoot("Mod root parent identity is not a non-symlink directory");
                }
                Path parentReal = parent.toRealPath();
                BasicFileAttributes parentAttributes = Files.readAttributes(
                        parent, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (Files.isSymbolicLink(root)
                        || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                    throw new UnsafeRoot("Mod root identity is not a non-symlink directory");
                }
                Path real = root.toRealPath();
                BasicFileAttributes attributes = Files.readAttributes(
                        root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!real.getParent().equals(parentReal)) {
                    throw new UnsafeRoot("Mod root escaped its stabilized parent identity");
                }
                RootIdentity identity = new RootIdentity(root, real, attributes.fileKey(),
                        attributes.creationTime(), parent, parentReal, parentAttributes.fileKey(),
                        parentAttributes.creationTime());
                identity.verify();
                return identity;
            } catch (UnsafeRoot error) {
                throw error;
            } catch (IOException | SecurityException error) {
                throw new UnsafeRoot("Unable to capture mod root identity", error);
            }
        }

        private void verify() throws UnsafeRoot {
            try {
                verifyParent();
                if (Files.isSymbolicLink(root)
                        || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                        || !root.toRealPath().equals(realPath)) {
                    throw new UnsafeRoot("Mod root identity changed during state operation");
                }
                BasicFileAttributes current = Files.readAttributes(
                        root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!sameIdentity(fileKey, creationTime, current)) {
                    throw new UnsafeRoot("Mod root identity changed during state operation");
                }
            } catch (UnsafeRoot error) {
                throw error;
            } catch (IOException | SecurityException error) {
                throw new UnsafeRoot("Unable to verify mod root identity", error);
            }
        }

        private void verifyParent() throws UnsafeRoot {
            try {
                if (Files.isSymbolicLink(parent)
                        || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                        || !parent.toRealPath().equals(parentRealPath)) {
                    throw new UnsafeRoot("Mod root parent identity changed during state operation");
                }
                BasicFileAttributes current = Files.readAttributes(
                        parent, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!sameIdentity(parentFileKey, parentCreationTime, current)) {
                    throw new UnsafeRoot("Mod root parent identity changed during state operation");
                }
            } catch (UnsafeRoot error) {
                throw error;
            } catch (IOException | SecurityException error) {
                throw new UnsafeRoot("Unable to verify mod root parent identity", error);
            }
        }
    }

    private static final class UnsafeRoot extends IOException {
        private UnsafeRoot(String message) {
            super(message);
        }

        private UnsafeRoot(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
