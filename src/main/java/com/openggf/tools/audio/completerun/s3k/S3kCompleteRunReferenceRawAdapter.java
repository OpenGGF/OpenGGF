package com.openggf.tools.audio.completerun.s3k;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict streaming reader for the game-owned headless S3K raw staging contract. */
public final class S3kCompleteRunReferenceRawAdapter {
    public static final String SCHEMA = "openggf.s3k-complete-run-audio-raw.v1";
    private static final String ROM_SHA1 = "cfbf98c36c776677290a872547ac47c53d2761d6";
    private static final String BK2_SHA256 =
            "aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc";
    private static final String MANIFEST_SHA256 =
            "ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0";
    private static final int MAX_LINE_CHARACTERS = 16 * 1024 * 1024;
    private static final int MAX_EVENTS = 65_536;
    private static final BigInteger MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                    .maxStringLength(MAX_LINE_CHARACTERS).maxNumberLength(64).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private S3kCompleteRunReferenceRawAdapter() { }

    public interface Sink {
        void header(Header value) throws IOException;
        void baseline(RawBoundary value) throws IOException;
        void frame(RawFrame value) throws IOException;
        void cutoff(RawBoundary value) throws IOException;
    }

    public record Header(int firstRow, int exclusiveEnd, int stateStart, int stateExclusiveEnd) { }

    public record RawBoundary(int row, int exclusiveEnd, byte[] driverState,
            int ymPort0Latch, int ymPort1Latch, long nativeArmEpoch, boolean nativeArmed,
            List<String> activeServices, List<String> pendingDescendants) {
        public RawBoundary {
            driverState = driverState.clone();
            activeServices = List.copyOf(activeServices);
            pendingDescendants = List.copyOf(pendingDescendants);
        }
        @Override public byte[] driverState() { return driverState.clone(); }
    }

    public record RawFrame(int row, boolean lag, byte[] driverState, List<RawEvent> events) {
        public RawFrame {
            driverState = driverState.clone();
            events = List.copyOf(events);
        }
        @Override public byte[] driverState() { return driverState.clone(); }
    }

    public record RawEvent(long ordinal, int serviceToken, int parentToken, long pc,
            int subject, int offset, int kind, int serviceKind, int depth, int sourceCpu,
            int payloadLength, int value, int flags, int reserved, BigInteger payload) { }

    public static void scan(Path raw, Sink sink) throws IOException {
        scan(raw, sink, true);
    }

    static void scanPrefixForTesting(Path raw, Sink sink) throws IOException {
        scan(raw, sink, false);
    }

    private static void scan(Path raw, Sink sink, boolean requireFull) throws IOException {
        Objects.requireNonNull(raw, "S3K raw staging path");
        Objects.requireNonNull(sink, "S3K raw staging sink");
        try (BufferedReader input = Files.newBufferedReader(raw, StandardCharsets.UTF_8)) {
            Header header = header(object(requiredLine(input), "metadata"));
            sink.header(header);
            RawBoundary baseline = boundary(object(requiredLine(input), "baseline"), true);
            if (baseline.row() != header.firstRow()) {
                throw invalid("baseline row does not match the pinned first row");
            }
            sink.baseline(baseline);
            int expected = header.firstRow();
            while (true) {
                String line = requiredLine(input);
                JsonNode value = object(line, "raw record");
                String type = text(value, "type");
                if ("cutoff".equals(type)) {
                    RawBoundary cutoff = boundary(value, false);
                    if (cutoff.exclusiveEnd() != expected) {
                        throw invalid("cutoff does not follow the contiguous raw rows");
                    }
                    if (requireFull && cutoff.exclusiveEnd() != header.exclusiveEnd()) {
                        throw invalid("full S3K raw capture ended before the pinned exclusive end");
                    }
                    if (boundedLine(input) != null) throw invalid("raw records follow the cutoff");
                    sink.cutoff(cutoff);
                    return;
                }
                RawFrame frame = frame(value);
                if (frame.row() != expected || expected >= header.exclusiveEnd()) {
                    throw invalid("S3K raw frame rows are not contiguous and in range");
                }
                sink.frame(frame);
                expected++;
            }
        }
    }

    private static Header header(JsonNode value) {
        exact(value, "type", "schema", "rom_sha1", "bk2_sha256",
                "service_manifest_sha256", "first_row", "exclusive_end",
                "state_start", "state_exclusive_end");
        require(text(value, "type").equals("metadata"), "first raw record is not metadata");
        require(text(value, "schema").equals(SCHEMA), "S3K raw schema is not pinned");
        require(text(value, "rom_sha1").equals(ROM_SHA1), "S3K raw ROM identity changed");
        require(text(value, "bk2_sha256").equals(BK2_SHA256), "S3K raw BK2 identity changed");
        require(text(value, "service_manifest_sha256").equals(MANIFEST_SHA256),
                "S3K raw service-manifest identity changed");
        int first = integer(value, "first_row");
        int end = integer(value, "exclusive_end");
        int stateStart = integer(value, "state_start");
        int stateEnd = integer(value, "state_exclusive_end");
        require(first == 810 && end == 434_417 && stateStart == 0x1c00 && stateEnd == 0x2000,
                "S3K raw interval or driver-state range changed");
        return new Header(first, end, stateStart, stateEnd);
    }

    private static RawBoundary boundary(JsonNode value, boolean baseline) {
        if (baseline) {
            exact(value, "type", "row", "state_hex", "ym_port0_latch", "ym_port1_latch",
                    "native_arm_epoch", "native_armed", "active_services", "pending_descendants");
            require(text(value, "type").equals("baseline"), "second raw record is not baseline");
        } else {
            exact(value, "type", "exclusive_end", "state_hex", "ym_port0_latch", "ym_port1_latch",
                    "native_arm_epoch", "native_armed", "active_services", "pending_descendants");
            require(text(value, "type").equals("cutoff"), "raw terminal record is not cutoff");
        }
        int row = baseline ? integer(value, "row") : -1;
        int end = baseline ? -1 : integer(value, "exclusive_end");
        return new RawBoundary(row, end, state(value), unsignedByte(value, "ym_port0_latch"),
                unsignedByte(value, "ym_port1_latch"), nonNegativeLong(value, "native_arm_epoch"),
                bool(value, "native_armed"), serviceJson(value, "active_services", 8),
                serviceJson(value, "pending_descendants", MAX_EVENTS));
    }

    private static RawFrame frame(JsonNode value) {
        exact(value, "type", "row", "lag", "state_hex", "events");
        require(text(value, "type").equals("frame"), "unknown S3K raw record type");
        JsonNode source = value.get("events");
        require(source != null && source.isArray() && source.size() <= MAX_EVENTS,
                "S3K raw event list is not bounded");
        List<RawEvent> events = new ArrayList<>(source.size());
        long expectedOrdinal = 0;
        for (JsonNode event : source) {
            exact(event, "ordinal", "service_token", "parent_token", "pc", "subject", "offset",
                    "kind", "service_kind", "depth", "source_cpu", "payload_length", "value",
                    "flags", "reserved", "payload");
            long ordinal = nonNegativeLong(event, "ordinal");
            require(ordinal == expectedOrdinal++, "S3K raw event ordinals are not contiguous");
            BigInteger payload;
            try { payload = new BigInteger(text(event, "payload")); }
            catch (NumberFormatException failure) { throw invalid("S3K raw payload is not unsigned decimal", failure); }
            require(payload.signum() >= 0 && payload.compareTo(MAX_U64) <= 0,
                    "S3K raw payload is outside uint64");
            events.add(new RawEvent(ordinal, unsignedWord(event, "service_token"),
                    unsignedWord(event, "parent_token"), unsignedInt(event, "pc"),
                    unsignedWord(event, "subject"), unsignedWord(event, "offset"),
                    unsignedByte(event, "kind"), unsignedByte(event, "service_kind"),
                    unsignedByte(event, "depth"), unsignedByte(event, "source_cpu"),
                    unsignedByte(event, "payload_length"), unsignedByte(event, "value"),
                    unsignedByte(event, "flags"), unsignedByte(event, "reserved"), payload));
        }
        return new RawFrame(integer(value, "row"), bool(value, "lag"), state(value), events);
    }

    private static byte[] state(JsonNode value) {
        String hex = text(value, "state_hex");
        require(hex.length() == 2048 && hex.matches("[0-9a-f]+"),
                "S3K raw state is not exact lowercase $1C00..$1FFF hex");
        byte[] bytes = new byte[1024];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte) Integer.parseInt(hex, i * 2, i * 2 + 2, 16);
        return bytes;
    }

    private static List<String> serviceJson(JsonNode value, String field, int maximum) {
        JsonNode array = value.get(field);
        require(array != null && array.isArray() && array.size() <= maximum,
                "S3K raw boundary service list is not bounded");
        List<String> result = new ArrayList<>(array.size());
        for (JsonNode service : array) {
            require(service.isObject(), "S3K raw boundary service is not an object");
            result.add(service.toString());
        }
        return List.copyOf(result);
    }

    private static JsonNode object(String line, String label) {
        try {
            JsonNode value = JSON.readTree(line);
            require(value != null && value.isObject(), label + " is not a JSON object");
            return value;
        } catch (IOException failure) {
            throw invalid(label + " is not strict JSON", failure);
        }
    }

    private static void exact(JsonNode value, String... fields) {
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(fields)), "S3K raw record fields are not exact");
    }

    private static String text(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isTextual(), "S3K raw " + field + " is not text");
        return node.textValue();
    }

    private static int integer(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.canConvertToInt(), "S3K raw " + field + " is not int");
        return node.intValue();
    }

    private static boolean bool(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isBoolean(), "S3K raw " + field + " is not boolean");
        return node.booleanValue();
    }

    private static long nonNegativeLong(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.canConvertToLong() && node.longValue() >= 0,
                "S3K raw " + field + " is not a non-negative long");
        return node.longValue();
    }

    private static int unsignedByte(JsonNode value, String field) {
        int result = integer(value, field);
        require(result >= 0 && result <= 0xff, "S3K raw " + field + " is not uint8");
        return result;
    }

    private static int unsignedWord(JsonNode value, String field) {
        int result = integer(value, field);
        require(result >= 0 && result <= 0xffff, "S3K raw " + field + " is not uint16");
        return result;
    }

    private static long unsignedInt(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isIntegralNumber(), "S3K raw " + field + " is not uint32");
        long result = node.longValue();
        require(result >= 0 && result <= 0xffff_ffffL, "S3K raw " + field + " is not uint32");
        return result;
    }

    private static String requiredLine(BufferedReader input) throws IOException {
        String line = boundedLine(input);
        if (line == null) throw invalid("S3K raw staging stream ended early");
        return line;
    }

    private static String boundedLine(BufferedReader input) throws IOException {
        StringBuilder line = new StringBuilder(8192);
        int value;
        while ((value = input.read()) >= 0) {
            if (value == '\n') return line.toString();
            if (value == '\r') throw invalid("S3K raw staging requires LF line endings");
            if (line.length() == MAX_LINE_CHARACTERS) throw invalid("S3K raw record exceeds its bound");
            line.append((char) value);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }
}
