package com.openggf.mods;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.openggf.game.ModKeySyntax;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The build-pinned list of first-party mods shipped with one engine build, and the exact jar
 * bytes trusted for each. The build writes it into the engine's own classpath at
 * {@link #RESOURCE}; it is the only source of default trust for bundled mods. Jar bytes are
 * located separately (see {@link BundledModSource}) and must match {@link Entry#sha256()}.
 */
public record BundledModManifest(List<Entry> mods) {
    public static final int FORMAT_VERSION = 1;
    /** Classpath location of the manifest inside the engine artifact. */
    public static final String RESOURCE = "META-INF/openggf/bundled-mods.json";
    /** Classpath prefix of jars embedded in the universal JVM jar. */
    public static final String EMBEDDED_PREFIX = "openggf-bundled/";
    public static final int MAX_ENTRIES = 16;
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;
    public static final BundledModManifest EMPTY = new BundledModManifest(List.of());

    private static final Pattern FILE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,123}\\.jar");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ROOT_FIELDS = Set.of("formatVersion", "mods");
    private static final Set<String> ENTRY_FIELDS = Set.of("id", "version", "file", "sha256", "size");

    public BundledModManifest {
        mods = List.copyOf(Objects.requireNonNull(mods, "mods"));
        if (mods.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Bundled mod manifest exceeds " + MAX_ENTRIES + " entries");
        }
        Set<String> ids = new HashSet<>();
        Set<String> files = new HashSet<>();
        for (Entry entry : mods) {
            Objects.requireNonNull(entry, "entry");
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate bundled mod id: " + entry.id());
            }
            if (!files.add(entry.file().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate bundled mod file: " + entry.file());
            }
        }
    }

    public boolean isEmpty() { return mods.isEmpty(); }

    /** One shipped jar: its manifest identity, file name, exact SHA-256 and byte size. */
    public record Entry(String id, SemanticVersion version, String file, String sha256, long size) {
        public Entry {
            id = ModKeySyntax.requireManifestId(id);
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(file, "file");
            if (!FILE.matcher(file).matches()) {
                throw new IllegalArgumentException("Bundled mod file must be a plain lowercase .jar name: " + file);
            }
            Objects.requireNonNull(sha256, "sha256");
            if (!SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("Bundled mod sha256 must be 64 lowercase hexadecimal characters");
            }
            if (size <= 0) {
                throw new IllegalArgumentException("Bundled mod size must be positive: " + size);
            }
        }
    }

    /** Strict parse: unknown or duplicate fields, a wrong format version and bad values are rejected. */
    public static BundledModManifest parse(byte[] json) {
        Objects.requireNonNull(json, "json");
        if (json.length > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("Bundled mod manifest exceeds " + MAX_MANIFEST_BYTES + " bytes");
        }
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(8).maxStringLength(1024).maxNumberLength(20).build())
                .build();
        try (JsonParser parser = factory.createParser(json)) {
            expect(parser.nextToken(), JsonToken.START_OBJECT);
            Integer formatVersion = null;
            List<Entry> entries = null;
            Set<String> seen = new HashSet<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                if (!ROOT_FIELDS.contains(field) || !seen.add(field)) {
                    throw new IllegalArgumentException("Unexpected bundled manifest field: " + field);
                }
                JsonToken value = parser.nextToken();
                if (field.equals("formatVersion")) {
                    expect(value, JsonToken.VALUE_NUMBER_INT);
                    formatVersion = parser.getIntValue();
                } else {
                    expect(value, JsonToken.START_ARRAY);
                    entries = new ArrayList<>();
                    while (parser.nextToken() == JsonToken.START_OBJECT) {
                        if (entries.size() == MAX_ENTRIES) {
                            throw new IllegalArgumentException("Bundled mod manifest exceeds " + MAX_ENTRIES + " entries");
                        }
                        entries.add(parseEntry(parser));
                    }
                    expect(parser.currentToken(), JsonToken.END_ARRAY);
                }
            }
            expect(parser.currentToken(), JsonToken.END_OBJECT);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("Trailing content after bundled mod manifest");
            }
            if (formatVersion == null || formatVersion != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported bundled manifest formatVersion: " + formatVersion);
            }
            if (entries == null) throw new IllegalArgumentException("Bundled mod manifest has no mods array");
            return new BundledModManifest(entries);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Bundled mod manifest is not valid JSON: " + failure.getMessage(), failure);
        }
    }

    private static Entry parseEntry(JsonParser parser) throws IOException {
        String id = null, version = null, file = null, sha256 = null;
        Long size = null;
        Set<String> seen = new HashSet<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            if (!ENTRY_FIELDS.contains(field) || !seen.add(field)) {
                throw new IllegalArgumentException("Unexpected bundled mod field: " + field);
            }
            JsonToken value = parser.nextToken();
            if (field.equals("size")) {
                expect(value, JsonToken.VALUE_NUMBER_INT);
                size = parser.getLongValue();
            } else {
                expect(value, JsonToken.VALUE_STRING);
                switch (field) {
                    case "id" -> id = parser.getText();
                    case "version" -> version = parser.getText();
                    case "file" -> file = parser.getText();
                    default -> sha256 = parser.getText();
                }
            }
        }
        expect(parser.currentToken(), JsonToken.END_OBJECT);
        if (seen.size() != ENTRY_FIELDS.size()) {
            throw new IllegalArgumentException("Bundled mod entry requires " + ENTRY_FIELDS);
        }
        return new Entry(id, SemanticVersion.parse(version), file, sha256, size);
    }

    private static void expect(JsonToken actual, JsonToken expected) {
        if (actual != expected) {
            throw new IllegalArgumentException("Bundled mod manifest expected " + expected + " but found " + actual);
        }
    }

    /** Canonical UTF-8 JSON with a trailing newline; {@link #parse} round-trips it exactly. */
    public byte[] toJson() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator out = new JsonFactory().createGenerator(bytes)) {
            out.useDefaultPrettyPrinter();
            out.writeStartObject();
            out.writeNumberField("formatVersion", FORMAT_VERSION);
            out.writeArrayFieldStart("mods");
            for (Entry entry : mods) {
                out.writeStartObject();
                out.writeStringField("id", entry.id());
                out.writeStringField("version", entry.version().toString());
                out.writeStringField("file", entry.file());
                out.writeStringField("sha256", entry.sha256());
                out.writeNumberField("size", entry.size());
                out.writeEndObject();
            }
            out.writeEndArray();
            out.writeEndObject();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        bytes.write('\n');
        return bytes.toByteArray();
    }
}
