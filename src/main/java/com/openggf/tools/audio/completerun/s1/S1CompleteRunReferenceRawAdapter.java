package com.openggf.tools.audio.completerun.s1;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded, transactional reader of TraceChaser's S1 raw.v1 chip stream.
 * Source: S1CompleteRunAudioReferenceCapture at TraceChaser 322851cb.
 * This is observation-only: managed evidence is not canonical state or input.
 * The source omits the terminal native frontier and pre-epoch deferred reservation;
 * successful parsing therefore cannot attest either of those identities.
 */
public final class S1CompleteRunReferenceRawAdapter {
    public static final String SCHEMA = "openggf.s1-complete-run-audio-raw.v1";
    static final int STATE_START = 0xf000;
    static final int STATE_BYTES = 0x5c0;
    private static final int MAX_LINE = 65_536;
    private static final int MAX_AGGREGATE_LINE = 33_554_432;
    private static final int MAX_FRAME_CHARACTERS = 33_554_432;
    private static final int MAX_RECORDS = 65_536;
    private static final BigInteger MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                    .maxStringLength(MAX_AGGREGATE_LINE).maxNumberLength(32).build()).build());
    private static final Set<String> EVIDENCE_TYPES = Set.of("managed_hook_evidence", "request",
            "decision", "dispatch", "managed_service_snapshot", "managed_reset_service_snapshot",
            "input_reset", "override_resume", "native_pcm_packet");

    private S1CompleteRunReferenceRawAdapter() { }

    public interface Sink {
        /** All callbacks remain private until commit; abort discards the complete transaction. */
        void begin() throws IOException;
        void baseline(Baseline value) throws IOException;
        void frame(Frame value) throws IOException;
        void terminal(int exclusiveEnd) throws IOException;
        void commit() throws IOException;
        void abort() throws IOException;
    }

    public record Baseline(int row, byte[] driverState, int ymPort0Latch, int ymPort1Latch) {
        public Baseline { driverState = driverState.clone(); }
        @Override public byte[] driverState() { return driverState.clone(); }
    }

    public record Frame(int row, List<NativeEvent> events, int managedEvidenceCount) {
        public Frame { events = List.copyOf(events); }
    }

    public record NativeEvent(long ordinal, int serviceToken, int parentToken, long pc,
            int subject, int offset, int kind, int serviceKind, int depth, int sourceCpu,
            int payloadLength, int value, int flags, BigInteger payload) { }

    public static void scan(Path raw, Sink sink) throws IOException {
        scan(raw, sink, true);
    }

    static void scanPrefixForTesting(Path raw, Sink sink) throws IOException {
        scan(raw, sink, false);
    }

    private static void scan(Path raw, Sink sink, boolean full) throws IOException {
        Objects.requireNonNull(raw, "S1 raw stream");
        Objects.requireNonNull(sink, "S1 raw sink");
        boolean started = false;
        boolean committed = false;
        try (BufferedReader input = Files.newBufferedReader(raw, StandardCharsets.UTF_8)) {
            metadata(object(line(input, MAX_LINE)));
            JsonNode baselineJson = object(line(input, MAX_AGGREGATE_LINE));
            Ledger ledger = new Ledger();
            Baseline baseline = baseline(baselineJson, ledger);
            started = true;
            sink.begin();
            sink.baseline(baseline);
            int row = baseline.row();
            while (true) {
                String beginLine = line(input, MAX_LINE);
                JsonNode begin = object(beginLine);
                if ("terminal".equals(text(begin, "type"))) {
                    exact(begin, "type", "exclusive_end", "rows", "orphan_closes",
                            "opcode_mismatches", "overflows");
                    require(number(begin, "exclusive_end", 225_101) == row
                                    && number(begin, "rows", 225_101) == row - 860,
                            "terminal does not follow contiguous rows");
                    require(!full || row == 225_101, "full capture ended before the pinned terminal");
                    for (String counter : List.of("orphan_closes", "opcode_mismatches", "overflows"))
                        require(number(begin, counter, 0) == 0, "terminal reports native capture failures");
                    ledger.finishFrame();
                    require(optionalLine(input, MAX_LINE) == null, "records follow terminal");
                    sink.terminal(row);
                    sink.commit();
                    committed = true;
                    return;
                }
                exact(begin, "type", "row");
                require(text(begin, "type").equals("frame_begin") && row < 225_101
                                && number(begin, "row", 225_100) == row,
                        "frame_begin is not contiguous or is out of range");
                List<NativeEvent> events = new ArrayList<>();
                int evidence = 0;
                int characters = beginLine.length() + 1;
                int records = 1;
                while (true) {
                    String source = line(input, MAX_LINE);
                    characters = Math.addExact(characters, source.length() + 1);
                    require(characters <= MAX_FRAME_CHARACTERS && ++records <= MAX_RECORDS,
                            "frame exceeds source publication bounds");
                    JsonNode value = object(source);
                    String type = text(value, "type");
                    require(number(value, type.equals("override_resume") ? "frame" : "row", 225_100)
                                    == row, "record belongs to a different frame");
                    if (type.equals("frame_end")) {
                        exact(value, "type", "row");
                        ledger.finishFrame();
                        sink.frame(new Frame(row, events, evidence));
                        row++;
                        break;
                    }
                    if (type.equals("native_event")) {
                        NativeEvent event = event(value);
                        require(event.ordinal() == events.size(), "native ordinals are not contiguous");
                        ledger.accept(event);
                        events.add(event);
                    } else {
                        require(EVIDENCE_TYPES.contains(type), "unknown raw record type: " + type);
                        evidence(value, type);
                        evidence++;
                    }
                }
            }
        } catch (IOException | RuntimeException | Error failure) {
            if (started && !committed) {
                try { sink.abort(); }
                catch (IOException | RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    private static void metadata(JsonNode value) {
        exact(value, "type", "schema", "rom_sha1", "bk2_sha256", "first_row", "exclusive_end",
                "native_abi", "native_event_size", "native_capacity");
        var fixture = S1CompleteRunAudioProfile.profile().fixture();
        require(text(value, "type").equals("metadata") && text(value, "schema").equals(SCHEMA),
                "raw metadata schema changed");
        require(text(value, "rom_sha1").equalsIgnoreCase(fixture.romSha1())
                        && text(value, "bk2_sha256").equals(fixture.bk2Sha256()),
                "raw ROM or emerald BK2 identity changed");
        require(number(value, "first_row", 860) == 860
                        && number(value, "exclusive_end", 225_101) == 225_101,
                "raw comparison interval changed");
        require(number(value, "native_abi", 5) == 5 && number(value, "native_event_size", 32) == 32
                        && number(value, "native_capacity", MAX_RECORDS) == MAX_RECORDS,
                "raw native ABI shape changed");
    }

    private static Baseline baseline(JsonNode value, Ledger ledger) {
        exact(value, "type", "row", "state_start", "state_hex", "ym_port0_latch", "ym_port1_latch",
                "active_services", "pending_descendants", "native_arm_epoch", "native_armed");
        require(text(value, "type").equals("baseline") && number(value, "row", 860) == 860,
                "baseline does not precede the pinned comparison row");
        require(bool(value, "native_armed") && unsigned(value, "native_arm_epoch").signum() > 0,
                "baseline lacks native arm evidence");
        Set<Integer> tokens = new HashSet<>();
        for (String field : List.of("active_services", "pending_descendants")) {
            JsonNode services = array(value, field, field.equals("active_services") ? 8 : MAX_RECORDS);
            for (JsonNode service : services) {
                exact(service, "token", "parent_token", "kind", "depth", "current_parent_token",
                        "current_depth", "ancestry_transitions", "state", "begin_coordinate",
                        "begin_pc", "begin_hook_token", "begin_source_cpu", "state_start", "state_hex");
                int token = positive(service, "token", 65_535);
                int parent = number(service, "current_parent_token", 65_535);
                int depth = number(service, "current_depth", 7);
                int kind = positive(service, "kind", 6);
                number(service, "parent_token", 65_535);
                number(service, "depth", 7);
                unsigned(service, "begin_coordinate");
                number(service, "begin_pc", 0xff_ffff);
                positive(service, "begin_hook_token", 65_535);
                positive(service, "begin_source_cpu", 2);
                state(service);
                array(service, "ancestry_transitions", 7);
                boolean active = field.equals("active_services");
                require(text(service, "state").equals(active ? "CARRIED_IN_OPEN" : "COMPLETED"),
                        "baseline lifecycle partition changed");
                if (active) {
                    require(tokens.add(token), "baseline duplicates an active service token");
                    require(depth == ledger.active.size() && parent == ledger.topToken(),
                            "baseline effective ancestry is not a stack");
                    ledger.active.add(new Owner(token, parent, kind, depth));
                }
            }
        }
        return new Baseline(860, state(value), number(value, "ym_port0_latch", 255),
                number(value, "ym_port1_latch", 255));
    }

    private static NativeEvent event(JsonNode value) {
        exact(value, "type", "row", "ordinal", "service_token", "parent_token", "pc", "subject",
                "offset", "kind", "service_kind", "depth", "source_cpu", "payload_length", "value",
                "flags", "payload");
        int kind = positive(value, "kind", 11);
        int cpu = positive(value, "source_cpu", 3);
        int pc = number(value, "pc", cpu == 1 ? 65_535 : cpu == 2 ? 0xff_ffff : 0);
        int length = number(value, "payload_length", 8);
        BigInteger payload = unsigned(value, "payload");
        int flags = number(value, "flags", 2);
        require(kind == 2 ? flags == 0 || flags == 2
                        : kind == 8 || kind == 9 ? flags <= 1 : flags == 0,
                "event flags disagree with ABI kind");
        boolean stackObservation = kind == 10 && number(value, "value", 255) == 3;
        require(kind == 6 ? length > 0 : stackObservation ? length == 4
                        : length == 0 && payload.signum() == 0,
                "payload bytes occur outside a snapshot chunk");
        require(payload.shiftRight(length * 8).signum() == 0, "payload exceeds declared byte length");
        int byteValue = number(value, "value", 255);
        require(kind == 3 || kind == 4 || kind == 10 || byteValue == 0,
                "unexpected event value byte");
        int subject = number(value, "subject", 65_535);
        int offset = number(value, "offset", 65_535);
        if (kind == 3) require(subject <= 3 && offset == 0, "invalid YM bus write");
        if (kind == 4) require(subject == 0 && offset == 0, "invalid PSG bus write");
        if (kind == 10) require(byteValue <= 4 && offset == 0 && cpu == 2,
                "unknown S1 marker value or source");
        if (kind == 1 || kind == 2 || kind == 8 || kind == 9 || kind == 11)
            require(offset == 0, "lifecycle event has a nonzero offset");
        if (kind == 1 || kind == 11 || kind == 10 || kind == 2 && flags == 0)
            require(subject > 0 && cpu != 3, "hook event has no hook token or CPU");
        if (kind == 2 && flags == 2)
            require(subject == 0 && cpu == 3, "cancellation is not reset-owned");
        if (kind == 9) require(subject == 0, "reset end has a subject");
        return new NativeEvent(number(value, "ordinal", MAX_RECORDS - 1),
                number(value, "service_token", 65_535), number(value, "parent_token", 65_535), pc,
                subject, offset, kind, number(value, "service_kind", 6), number(value, "depth", 7),
                cpu, length, byteValue, flags, payload);
    }

    /** Validate shape only: these unobserved records never supply canonical values or gameplay. */
    private static void evidence(JsonNode value, String type) {
        if (type.equals("managed_service_snapshot") || type.equals("managed_reset_service_snapshot"))
            state(value);
        if (type.equals("managed_hook_evidence")) {
            text(value, "name"); text(value, "action");
            number(value, "pc", 0xff_ffff);
            JsonNode registers = value.get("registers");
            exact(registers, "D0", "D1", "D2", "D3", "D4", "D5", "D6", "D7",
                    "A0", "A1", "A2", "A3", "A4", "A5", "A6", "A7");
            registers.fieldNames().forEachRemaining(name -> unsigned(registers, name));
        }
        if (Set.of("request", "decision", "dispatch").contains(type)) {
            number(value, "sound_id", 255);
            if (!type.equals("dispatch")) number(value, "queue", 2);
            JsonNode request = value.get("request_id");
            require(request != null && (request.isNull()
                            || request.isIntegralNumber() && request.canConvertToLong() && request.longValue() > 0),
                    "managed request identity is malformed");
        }
        // A deferred occurrence can correlate to a future row. Do not reinterpret
        // native_ordinal as a current-row event index or claim causal verification.
        if (value.has("native_correlation_events")) array(value, "native_correlation_events", MAX_RECORDS);
    }

    private static final class Ledger {
        private final List<Owner> active = new ArrayList<>();
        private Owner reset;
        private NativeEvent snapshot;
        private int snapshotOffset;
        private NativeEvent previous;
        private boolean promotionPending;
        private int resetFlags;
        private final Map<Integer, NativeEvent> completedSnapshots = new HashMap<>();

        private int topToken() { return active.isEmpty() ? 0 : active.getLast().token; }

        private void accept(NativeEvent event) {
            require(!promotionPending || event.kind() == 11,
                    "direct-parent completion lacks immediate child promotion");
            if (snapshot != null) require(event.kind() == 6 || event.kind() == 7,
                    "snapshot transaction was interrupted");
            switch (event.kind()) {
                case 1 -> {
                    require(reset == null && event.serviceToken() != 0 && event.serviceKind() > 0
                                    && event.parentToken() == topToken() && event.depth() == active.size()
                                    && active.stream().noneMatch(owner -> owner.token == event.serviceToken()),
                            "service begin has invalid active ancestry or token");
                    active.add(Owner.of(event));
                }
                case 2 -> {
                    int index = -1;
                    for (int i = 0; i < active.size(); i++) if (active.get(i).matches(event)) index = i;
                    require(index >= 0 && (index == active.size() - 1 || index == active.size() - 2),
                            "service completion has no active owner");
                    require((reset != null) == (event.flags() == 2), "reset cancellation is unpaired");
                    require(reset == null || index == active.size() - 1,
                            "reset cancellation does not close the stack top");
                    requireCompletionSnapshot(event);
                    promotionPending = index == active.size() - 2;
                    active.remove(index);
                }
                case 3, 4 -> require(topOwner(event) && (reset == null || active.isEmpty())
                                && (event.sourceCpu() != 3 || reset != null),
                        "chip write has no active service or reset owner");
                case 5 -> {
                    require(owner(event) != null || rootObservation(event), "snapshot has no active owner");
                    require(event.offset() == 0 && rangeBytes(event.subject()) > 0,
                            "snapshot begin has an unknown S1 range");
                    require(!completedSnapshots.containsKey(event.serviceToken()),
                            "service has duplicate completion snapshots");
                    snapshot = event;
                    snapshotOffset = 0;
                }
                case 6, 7 -> {
                    require(snapshot != null && sameSnapshot(snapshot, event)
                                    && event.offset() == snapshotOffset,
                            "snapshot owner or contiguous offset changed");
                    if (event.kind() == 6) {
                        snapshotOffset += event.payloadLength();
                        require(snapshotOffset <= rangeBytes(event.subject()), "snapshot exceeds range");
                    } else {
                        require(snapshotOffset == rangeBytes(event.subject()), "snapshot is incomplete");
                        completedSnapshots.put(event.serviceToken(), event);
                        snapshot = null;
                    }
                }
                case 8 -> {
                    require(reset == null && event.serviceToken() != 0 && event.serviceKind() == 1
                                    && event.sourceCpu() == 3 && event.parentToken() == 0 && event.depth() == 0
                                    && event.subject() == active.size(), "invalid reset begin");
                    reset = Owner.of(event);
                    resetFlags = event.flags();
                }
                case 9 -> {
                    require(reset != null && reset.matches(event) && active.isEmpty()
                                    && event.sourceCpu() == 3 && event.flags() == resetFlags,
                            "invalid reset completion");
                    requireCompletionSnapshot(event);
                    reset = null;
                }
                case 10 -> require(owner(event) != null || rootObservation(event),
                        "native marker has no active or root observation owner");
                case 11 -> {
                    require(promotionPending && previous != null && previous.kind() == 2 && !active.isEmpty(),
                            "promotion lacks adjacent direct-parent completion");
                    Owner child = active.getLast();
                    require(child.token == event.serviceToken() && child.kind == event.serviceKind()
                                    && child.parent == previous.serviceToken() && event.parentToken() == previous.parentToken()
                                    && event.depth() + 1 == child.depth && event.subject() == previous.subject()
                                    && event.pc() == previous.pc() && event.sourceCpu() == previous.sourceCpu(),
                            "promotion changed surviving child identity");
                    active.set(active.size() - 1, Owner.of(event));
                    promotionPending = false;
                }
                default -> throw invalid("unsupported native event");
            }
            previous = event;
        }

        private Owner owner(NativeEvent event) {
            if (reset != null && reset.matches(event)) return reset;
            // Retry/internal M68K observations can be owned by a direct parent below a typed Z80 child.
            for (Owner candidate : active) if (candidate.matches(event)) return candidate;
            return null;
        }

        private boolean topOwner(NativeEvent event) {
            return reset != null ? reset.matches(event)
                    : !active.isEmpty() && active.getLast().matches(event);
        }

        private void requireCompletionSnapshot(NativeEvent event) {
            NativeEvent evidence = completedSnapshots.remove(event.serviceToken());
            int expectedRange = event.serviceKind() == 4 ? 10 : event.serviceKind() == 5 ? 30 : 2;
            require(evidence != null && evidence.subject() == expectedRange
                            && evidence.sourceCpu() == event.sourceCpu() && evidence.pc() == event.pc()
                            && previous == evidence, "completion lacks its adjacent source-owned snapshot");
        }

        private boolean rootObservation(NativeEvent event) {
            return active.isEmpty() && event.serviceToken() == 0 && event.parentToken() == 0
                    && event.serviceKind() == 0 && event.depth() == 0
                    && (event.kind() >= 5 && event.kind() <= 7 || event.kind() == 10);
        }

        private void finishFrame() {
            require(snapshot == null && reset == null && !promotionPending && completedSnapshots.isEmpty(),
                    "frame ends inside snapshot, reset or promotion transaction");
            previous = null;
        }
    }

    private record Owner(int token, int parent, int kind, int depth) {
        private static Owner of(NativeEvent event) {
            return new Owner(event.serviceToken(), event.parentToken(), event.serviceKind(), event.depth());
        }
        private boolean matches(NativeEvent event) {
            return token == event.serviceToken() && parent == event.parentToken()
                    && kind == event.serviceKind() && depth == event.depth();
        }
    }

    private static boolean sameSnapshot(NativeEvent first, NativeEvent next) {
        return Owner.of(first).equals(Owner.of(next)) && first.subject() == next.subject()
                && first.pc() == next.pc() && first.sourceCpu() == next.sourceCpu();
    }

    private static int rangeBytes(int id) {
        // Ranges20..26 are native return-PC predicates (flags1), not emitted snapshots.
        return switch (id) { case 2, 10 -> 1; case 30 -> 8192; default -> 0; };
    }

    private static byte[] state(JsonNode value) {
        require(number(value, "state_start", STATE_START) == STATE_START, "M68K state start changed");
        String hex = text(value, "state_hex");
        require(hex.length() == STATE_BYTES * 2 && hex.matches("[0-9a-f]+"),
                "state is not exact lowercase M68K $F000..$F5BF bytes");
        return java.util.HexFormat.of().parseHex(hex);
    }

    private static JsonNode object(String source) throws IOException {
        try (var parser = JSON.createParser(source)) {
            JsonNode result = JSON.readTree(parser);
            require(result != null && result.isObject() && parser.nextToken() == null,
                    "record is not one JSON object");
            return result;
        }
    }

    private static void exact(JsonNode value, String... fields) {
        require(value != null && value.isObject(), "expected object");
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(Set.of(fields)), "record fields changed: " + actual);
    }

    private static String text(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isTextual(), field + " is not text");
        return node.textValue();
    }

    private static int number(JsonNode value, String field, int maximum) {
        JsonNode node = value.get(field);
        require(node != null && node.isIntegralNumber() && node.canConvertToInt()
                        && node.intValue() >= 0 && node.intValue() <= maximum, field + " is outside bounds");
        return node.intValue();
    }

    private static int positive(JsonNode value, String field, int maximum) {
        int result = number(value, field, maximum);
        require(result > 0, field + " is zero");
        return result;
    }

    private static BigInteger unsigned(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isIntegralNumber(), field + " is not a JSON uint64 number");
        BigInteger result = node.bigIntegerValue();
        require(result.signum() >= 0 && result.compareTo(MAX_U64) <= 0, field + " is outside uint64");
        return result;
    }

    private static boolean bool(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isBoolean(), field + " is not boolean");
        return node.booleanValue();
    }

    private static JsonNode array(JsonNode value, String field, int maximum) {
        JsonNode result = value.get(field);
        require(result != null && result.isArray() && result.size() <= maximum, field + " is not bounded array");
        return result;
    }

    private static String line(BufferedReader input, int limit) throws IOException {
        String result = optionalLine(input, limit);
        require(result != null, "unexpected end of raw stream");
        return result;
    }

    private static String optionalLine(BufferedReader input, int limit) throws IOException {
        StringBuilder value = new StringBuilder();
        int character;
        while ((character = input.read()) != -1) {
            if (character == '\n') return value.toString();
            require(character != '\r' && value.length() < limit, "raw line exceeds bounds or contains CR");
            value.append((char) character);
        }
        require(value.isEmpty(), "unterminated raw line");
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("S1 raw: " + message);
    }
}
