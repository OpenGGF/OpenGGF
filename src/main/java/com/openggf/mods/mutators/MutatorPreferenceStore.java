package com.openggf.mods.mutators;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.openggf.game.ModKeySyntax;
import com.openggf.mods.ModStateSaveResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Strict schema-1 requested preference storage. Secure directory-relative I/O pins
 * root identity and never follows a settings/temp symlink. Platforms lacking that
 * facility report a visible save/load failure rather than weakening publication.
 * The caller supplies an existing owned directory; this store never creates
 * ancestors through a potentially substituted path. Effective/historical policy
 * state is never written here.
 */
public final class MutatorPreferenceStore {
    private static final int MAX_BYTES = 262_144;
    private final Path root;
    private final String profile;
    private final Path relativeFile;
    private final FileOperations operations;
    private final JsonFactory json = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8)
                    .maxStringLength(192).maxNameLength(192).maxNumberLength(10)
                    .maxDocumentLength(MAX_BYTES).maxTokenCount(40_000).build()).build();

    public MutatorPreferenceStore(Path absoluteNormalizedRoot, String profile) {
        this(absoluteNormalizedRoot, profile, FileOperations.SYSTEM);
    }

    MutatorPreferenceStore(Path root, String profile, FileOperations operations) {
        if (!root.equals(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Preferences root must be absolute and normalized");
        }
        this.root = root;
        this.profile = ModKeySyntax.requireManifestId(profile);
        this.relativeFile = Path.of("mutator-preferences-" + profile + ".json");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public Path statePath() { return root.resolve(relativeFile); }

    public LoadResult load() {
        try {
            if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) {
                return new LoadResult(new MutatorPreferences(profile, Map.of()), null);
            }
            RootIdentity identity = RootIdentity.capture(root);
            try (DirectoryStream<Path> opened = Files.newDirectoryStream(root)) {
                SecureDirectoryStream<Path> directory = secure(opened);
                identity.verify();
                operations.checkpoint(Boundary.AFTER_ROOT_CAPTURE, root);
                identity.verify();
                if (Files.notExists(statePath(), LinkOption.NOFOLLOW_LINKS)) {
                    identity.verify();
                    return new LoadResult(new MutatorPreferences(profile, Map.of()), null);
                }
                byte[] bytes = read(directory, relativeFile);
                identity.verify();
                return new LoadResult(parse(bytes), null);
            }
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            return new LoadResult(new MutatorPreferences(profile, Map.of()), message(failure));
        }
    }

    public ModStateSaveResult save(MutatorPreferences preferences) {
        Objects.requireNonNull(preferences, "preferences");
        if (!profile.equals(preferences.profile())) throw new IllegalArgumentException("Wrong preference profile");
        try {
            byte[] bytes = encode(preferences);
            parse(bytes);
            RootIdentity identity = RootIdentity.capture(root);
            try (DirectoryStream<Path> opened = Files.newDirectoryStream(root)) {
                SecureDirectoryStream<Path> directory = secure(opened);
                identity.verify();
                operations.checkpoint(Boundary.AFTER_ROOT_CAPTURE, root);
                identity.verify();
                Path temporary = Path.of(".mutator-preferences-" + UUID.randomUUID() + ".tmp");
                Object temporaryIdentity = null;
                try {
                    try (SeekableByteChannel channel = directory.newByteChannel(temporary,
                            Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
                        temporaryIdentity = attributes(directory, temporary).fileKey();
                        if (temporaryIdentity == null) throw new IOException("Staged preference identity unavailable");
                        operations.write(channel, bytes);
                        if (!(channel instanceof java.nio.channels.FileChannel file)) {
                            throw new IOException("Durable preference file I/O unavailable on this platform");
                        }
                        file.force(true);
                    }
                    operations.checkpoint(Boundary.AFTER_TEMP_WRITE, root.resolve(temporary));
                    identity.verify();
                    if (!Objects.equals(temporaryIdentity, attributes(directory, temporary).fileKey())
                            || !java.util.Arrays.equals(bytes, read(directory, temporary))) {
                        throw new IOException("Staged preference identity/content changed");
                    }
                    if (Files.exists(statePath(), LinkOption.NOFOLLOW_LINKS)
                            && !attributes(directory, relativeFile).isRegularFile()) {
                        throw new IOException("Preference destination is not a regular file");
                    }
                    operations.checkpoint(Boundary.BEFORE_MOVE, statePath());
                    identity.verify();
                    operations.move(directory, temporary, relativeFile);
                    identity.verify();
                    if (!Objects.equals(temporaryIdentity, attributes(directory, relativeFile).fileKey())
                            || !java.util.Arrays.equals(bytes, read(directory, relativeFile))) {
                        throw new IOException("Published preference identity/content changed");
                    }
                    return new ModStateSaveResult.Saved();
                } finally {
                    // Delete only our still-owned random temporary through the pinned directory.
                    try {
                        if (temporaryIdentity != null
                                && Objects.equals(temporaryIdentity, attributes(directory, temporary).fileKey())) {
                            directory.deleteFile(temporary);
                        }
                    } catch (IOException ignored) {
                        // A successful move removed it; replaced/unknown files are preserved.
                    }
                }
            }
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            return new ModStateSaveResult.Failed(message(failure));
        }
    }

    private byte[] read(SecureDirectoryStream<Path> directory, Path file) throws IOException {
        BasicFileAttributes before = attributes(directory, file);
        if (!before.isRegularFile() || before.size() > MAX_BYTES) {
            throw new IOException("Preferences are not a bounded regular file");
        }
        try (SeekableByteChannel input = directory.newByteChannel(file,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (input.read(buffer) != -1) {
                buffer.flip();
                if (bytes.size() + buffer.remaining() > MAX_BYTES) throw new IOException("Preferences exceed byte limit");
                bytes.write(buffer.array(), 0, buffer.remaining());
                buffer.clear();
            }
            BasicFileAttributes after = attributes(directory, file);
            if (!Objects.equals(before.fileKey(), after.fileKey()) || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
                throw new IOException("Preference file changed during read");
            }
            return bytes.toByteArray();
        }
    }

    private byte[] encode(MutatorPreferences preferences) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator generator = json.createGenerator(bytes)) {
            generator.writeStartObject();
            generator.writeNumberField("formatVersion", 1);
            generator.writeStringField("profile", profile);
            generator.writeObjectFieldStart("entries");
            for (Map.Entry<String, MutatorPreferences.Entry> item : preferences.entries().entrySet()) {
                generator.writeObjectFieldStart(item.getKey());
                generator.writeNumberField("schemaVersion", item.getValue().schemaVersion());
                generator.writeBooleanField("enabled", item.getValue().enabled());
                generator.writeObjectFieldStart("options");
                for (Map.Entry<String, Object> option : item.getValue().options().entrySet()) {
                    generator.writeFieldName(option.getKey());
                    if (option.getValue() instanceof Integer integer) generator.writeNumber(integer);
                    else if (option.getValue() instanceof Boolean bool) generator.writeBoolean(bool);
                    else generator.writeString((String) option.getValue());
                }
                generator.writeEndObject();
                generator.writeEndObject();
            }
            generator.writeEndObject();
            generator.writeEndObject();
        }
        if (bytes.size() > MAX_BYTES) throw new IOException("Serialized preferences exceed byte limit");
        return bytes.toByteArray();
    }

    private MutatorPreferences parse(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Preferences exceed byte limit");
        try (JsonParser parser = json.createParser(bytes)) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "Expected preference object");
            Integer version = null;
            String storedProfile = null;
            Map<String, MutatorPreferences.Entry> entries = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(parser.currentToken() == JsonToken.FIELD_NAME, "Expected preference field");
                String field = parser.currentName();
                JsonToken token = parser.nextToken();
                switch (field) {
                    case "formatVersion" -> { require(token == JsonToken.VALUE_NUMBER_INT, "Expected format version"); version = parser.getIntValue(); }
                    case "profile" -> { require(token == JsonToken.VALUE_STRING, "Expected profile"); storedProfile = parser.getText(); }
                    case "entries" -> entries = readEntries(parser);
                    default -> throw new IOException("Unknown preference field: " + field);
                }
            }
            require(Integer.valueOf(1).equals(version) && profile.equals(storedProfile)
                    && entries != null && parser.nextToken() == null, "Invalid/incomplete preference format");
            return new MutatorPreferences(profile, entries);
        }
    }

    private Map<String, MutatorPreferences.Entry> readEntries(JsonParser parser) throws IOException {
        require(parser.currentToken() == JsonToken.START_OBJECT, "Expected entries object");
        Map<String, MutatorPreferences.Entry> entries = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(parser.currentToken() == JsonToken.FIELD_NAME && entries.size() < 128, "Invalid/oversized entries");
            String key = parser.currentName();
            require(parser.nextToken() == JsonToken.START_OBJECT, "Expected entry object");
            Integer schema = null;
            Boolean enabled = null;
            Map<String, Object> options = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(parser.currentToken() == JsonToken.FIELD_NAME, "Expected entry field");
                String field = parser.currentName();
                JsonToken token = parser.nextToken();
                switch (field) {
                    case "schemaVersion" -> { require(token == JsonToken.VALUE_NUMBER_INT, "Expected schema version"); schema = parser.getIntValue(); }
                    case "enabled" -> { require(token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE, "Expected enabled boolean"); enabled = parser.getBooleanValue(); }
                    case "options" -> options = readOptions(parser);
                    default -> throw new IOException("Unknown entry field: " + field);
                }
            }
            require(schema != null && enabled != null && options != null, "Incomplete preference entry");
            entries.put(key, new MutatorPreferences.Entry(schema, enabled, options));
        }
        return entries;
    }

    private Map<String, Object> readOptions(JsonParser parser) throws IOException {
        require(parser.currentToken() == JsonToken.START_OBJECT, "Expected options object");
        Map<String, Object> options = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(parser.currentToken() == JsonToken.FIELD_NAME && options.size() < 32, "Invalid/oversized options");
            String key = parser.currentName();
            JsonToken token = parser.nextToken();
            require(token != null, "Missing option value");
            Object value = switch (token) {
                case VALUE_NUMBER_INT -> parser.getIntValue();
                case VALUE_TRUE, VALUE_FALSE -> parser.getBooleanValue();
                case VALUE_STRING -> parser.getText();
                default -> throw new IOException("Expected integer, boolean or stable enum token");
            };
            options.put(key, value);
        }
        return options;
    }

    private static BasicFileAttributes attributes(SecureDirectoryStream<Path> directory, Path file) throws IOException {
        return directory.getFileAttributeView(file, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
    }

    @SuppressWarnings("unchecked")
    private static SecureDirectoryStream<Path> secure(DirectoryStream<Path> opened) throws IOException {
        if (!(opened instanceof SecureDirectoryStream<?>)) throw new IOException("Secure preference directory I/O unavailable on this platform");
        return (SecureDirectoryStream<Path>) opened;
    }

    private record RootIdentity(Path root, Object fileKey) {
        static RootIdentity capture(Path root) throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.fileKey() == null || !root.toRealPath().equals(root)) {
                throw new IOException("Unsafe preference root identity/symlink");
            }
            return new RootIdentity(root, attributes.fileKey());
        }
        void verify() throws IOException {
            if (!equals(capture(root))) throw new IOException("Preference root identity changed");
        }
    }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException(reason);
    }
    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    public record LoadResult(MutatorPreferences preferences, String error) {
        public boolean successful() { return error == null; }
    }

    enum Boundary { AFTER_ROOT_CAPTURE, AFTER_TEMP_WRITE, BEFORE_MOVE }
    interface FileOperations {
        FileOperations SYSTEM = new FileOperations() { };
        default void checkpoint(Boundary boundary, Path path) throws IOException { }
        default void write(SeekableByteChannel channel, byte[] bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
        }
        default void move(SecureDirectoryStream<Path> directory, Path from, Path to) throws IOException {
            directory.move(from, directory, to);
        }
    }
}
