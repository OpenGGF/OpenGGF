package com.openggf.tools.audio.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable capture identity and cycle boundary data from the first JSONL line. */
public record AudioParityMetadata(
        String schema,
        String capture,
        int cycleStart,
        int period,
        int terminalRecordCount,
        String romSha1,
        String romCrc32,
        JsonNode details) {
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern CRC32 = Pattern.compile("[0-9a-f]{8}");
    private static final Pattern ABSOLUTE_PATH = Pattern.compile("^(?:/|\\\\|[A-Za-z]:[\\\\/]|file:).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_TIMESTAMP = Pattern.compile(
            ".*\\d{4}-\\d{2}-\\d{2}[Tt ]\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?(?:[Zz]|[+-]\\d{2}:?\\d{2})?.*");

    public AudioParityMetadata {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(romSha1, "romSha1");
        Objects.requireNonNull(romCrc32, "romCrc32");
        if (!AudioParitySchema.VERSION.equals(schema)) {
            throw new IllegalArgumentException("unknown audio parity schema: " + schema);
        }
        if (capture.isBlank()) {
            throw new IllegalArgumentException("capture identity must not be blank");
        }
        if (cycleStart < 0 || period <= 0 || terminalRecordCount <= 0) {
            throw new IllegalArgumentException("cycle_start, period, and terminal_record_count are out of range");
        }
        romSha1 = romSha1.toLowerCase();
        romCrc32 = romCrc32.toLowerCase();
        if (!SHA1.matcher(romSha1).matches() || !CRC32.matcher(romCrc32).matches()) {
            throw new IllegalArgumentException("ROM SHA-1/CRC32 identity is malformed");
        }
        rejectHostIdentity(capture);
        details = details == null ? JsonNodeFactory.instance.objectNode() : details.deepCopy();
        validatePortable(details);
    }

    public static AudioParityMetadata reference(String capture, int cycleStart, int period,
            int terminalRecordCount, String romSha1, String romCrc32) {
        return new AudioParityMetadata(AudioParitySchema.VERSION, capture, cycleStart, period,
                terminalRecordCount, romSha1, romCrc32, JsonNodeFactory.instance.objectNode());
    }

    @Override
    public JsonNode details() {
        return details.deepCopy();
    }

    private static void validatePortable(JsonNode node) {
        if (node.isTextual()) {
            rejectHostIdentity(node.textValue());
        } else if (node.isContainerNode()) {
            node.forEach(AudioParityMetadata::validatePortable);
        }
    }

    private static void rejectHostIdentity(String value) {
        if (ABSOLUTE_PATH.matcher(value).matches()) {
            throw new IllegalArgumentException("normalized metadata contains an absolute path");
        }
        if (ISO_TIMESTAMP.matcher(value).matches()) {
            throw new IllegalArgumentException("normalized metadata contains a timestamp");
        }
    }
}
