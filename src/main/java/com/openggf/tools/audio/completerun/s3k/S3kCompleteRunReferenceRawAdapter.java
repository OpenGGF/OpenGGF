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
    static final String SUBMISSION_SCHEMA = "openggf.s3k-complete-run-audio-raw.v2";
    private static final String ROM_SHA1 = "cfbf98c36c776677290a872547ac47c53d2761d6";
    private static final String BK2_SHA256 =
            "aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc";
    private static final String MANIFEST_SHA256 =
            "ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0";
    private static final String SUBMISSION_MANIFEST_SHA256 =
            "a1736a1ec5e279299f15177192eefc737efbbe4d046d3260a942f7cb3074a16c";
    private static final int MAX_LINE_CHARACTERS = 16 * 1024 * 1024;
    private static final int MAX_EVENTS = 65_536;
    private static final BigInteger MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                    .maxStringLength(MAX_LINE_CHARACTERS).maxNumberLength(64).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private S3kCompleteRunReferenceRawAdapter() { }

    public interface Sink {
        /** Opens a private transaction; callbacks below must remain invisible until commit. */
        void begin() throws IOException;
        void header(Header value) throws IOException;
        void baseline(RawBoundary value) throws IOException;
        void frame(RawFrame value) throws IOException;
        void cutoff(RawBoundary value) throws IOException;
        /** Atomically makes the staged callbacks visible. */
        void commit() throws IOException;
        /** Discards every staged callback and is safe after any pre-commit failure. */
        void abort() throws IOException;
    }

    public record Header(int firstRow, int exclusiveEnd, int stateStart, int stateExclusiveEnd) { }

    public record RawBoundary(int row, int exclusiveEnd, byte[] driverState,
            int ymPort0Latch, int ymPort1Latch, long nativeArmEpoch, boolean nativeArmed,
            List<RawService> activeServices, List<RawService> pendingDescendants) {
        public RawBoundary {
            driverState = driverState.clone();
            activeServices = List.copyOf(activeServices);
            pendingDescendants = List.copyOf(pendingDescendants);
        }
        @Override public byte[] driverState() { return driverState.clone(); }
    }

    public record RawFrame(int row, boolean lag, byte[] driverState, List<RawEvent> events,
            List<RawSubmission> submissions) {
        public RawFrame {
            driverState = driverState.clone();
            events = List.copyOf(events);
            submissions = List.copyOf(submissions);
        }
        @Override public byte[] driverState() { return driverState.clone(); }
    }

    public record RawSubmission(int serviceToken, int parentToken, long beginOrdinal,
            long endOrdinal, long beginPc, long endPc, int beginHookToken,
            int endHookToken, List<Integer> mailboxBytes, int request) {
        public RawSubmission { mailboxBytes = List.copyOf(mailboxBytes); }
    }

    public record RawEvent(long ordinal, int serviceToken, int parentToken, long pc,
            int subject, int offset, int kind, int serviceKind, int depth, int sourceCpu,
            int payloadLength, int value, int flags, int reserved, BigInteger payload) { }

    public record RawService(int token, int parentToken, int kind, int depth,
            int currentParentToken, int currentDepth, long beginCoordinate, long endCoordinate,
            long beginPc, long endPc, int beginHookToken, int endHookToken, int beginSourceCpu,
            boolean cancelled, boolean complete, List<RawChip> chips,
            List<RawSnapshot> snapshots, List<RawAncestryTransition> ancestryTransitions) {
        public RawService {
            chips = List.copyOf(chips);
            snapshots = List.copyOf(snapshots);
            ancestryTransitions = List.copyOf(ancestryTransitions);
        }
    }

    public record RawChip(long coordinate, long nativeOrdinal, int eventKind, int subject,
            int value, long pc, int sourceCpu, boolean data, int port, int register) { }

    public record RawSnapshot(int rangeId, int sourceCpu, long pc, byte[] bytes) {
        public RawSnapshot { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    public record RawAncestryTransition(long coordinate, long nativeOrdinal,
            int previousParentToken, int previousDepth, int currentParentToken,
            int currentDepth, int hookToken, int sourceCpu, long pc) { }

    public static void scan(Path raw, Sink sink) throws IOException {
        scan(raw, sink, true, false);
    }

    static void scanPrefixForTesting(Path raw, Sink sink) throws IOException {
        scan(raw, sink, false, false);
    }

    static void scanSubmissionV2PrefixForTesting(Path raw, Sink sink) throws IOException {
        scan(raw, sink, false, true);
    }

    private static void scan(Path raw, Sink sink, boolean requireFull,
            boolean submissionV2) throws IOException {
        Objects.requireNonNull(raw, "S3K raw staging path");
        Objects.requireNonNull(sink, "S3K raw staging sink");
        boolean transactionStarted = false;
        boolean committed = false;
        try (BufferedReader input = Files.newBufferedReader(raw, StandardCharsets.UTF_8)) {
            Header header = header(object(requiredLine(input), "metadata"), submissionV2);
            RawBoundary baseline = boundary(
                    object(requiredLine(input), "baseline"), true, submissionV2);
            if (baseline.row() != header.firstRow()) {
                throw invalid("baseline row does not match the pinned first row");
            }
            transactionStarted = true;
            sink.begin();
            sink.header(header);
            sink.baseline(baseline);
            int expected = header.firstRow();
            while (true) {
                String line = requiredLine(input);
                JsonNode value = object(line, "raw record");
                String type = text(value, "type");
                if ("cutoff".equals(type)) {
                    RawBoundary cutoff = boundary(value, false, submissionV2);
                    if (cutoff.exclusiveEnd() != expected) {
                        throw invalid("cutoff does not follow the contiguous raw rows");
                    }
                    if (requireFull && cutoff.exclusiveEnd() != header.exclusiveEnd()) {
                        throw invalid("full S3K raw capture ended before the pinned exclusive end");
                    }
                    if (requireFull && (cutoff.activeServices().size() != 1
                            || cutoff.pendingDescendants().size() != 4)) {
                        throw invalid("full S3K raw cutoff frontier is not the pinned 1/4 shape");
                    }
                    if (boundedLine(input) != null) throw invalid("raw records follow the cutoff");
                    sink.cutoff(cutoff);
                    sink.commit();
                    committed = true;
                    return;
                }
                RawFrame frame = frame(value, submissionV2);
                if (frame.row() != expected || expected >= header.exclusiveEnd()) {
                    throw invalid("S3K raw frame rows are not contiguous and in range");
                }
                sink.frame(frame);
                expected++;
            }
        } catch (IOException | RuntimeException | Error failure) {
            if (transactionStarted && !committed) {
                try { sink.abort(); }
                catch (IOException | RuntimeException | Error abortFailure) {
                    failure.addSuppressed(abortFailure);
                }
            }
            throw failure;
        }
    }

    private static Header header(JsonNode value, boolean submissionV2) {
        if (submissionV2) {
            exact(value, "type", "schema", "rom_sha1", "service_manifest_sha256",
                    "first_row", "exclusive_end", "state_start", "state_exclusive_end",
                    "authority");
            require(text(value, "type").equals("metadata"),
                    "first raw record is not metadata");
            require(text(value, "schema").equals(SUBMISSION_SCHEMA),
                    "S3K submission raw schema is not the unbound v2 contract");
            require(text(value, "authority").equals("UNBOUND_TEST_ONLY"),
                    "S3K submission raw authority is not explicitly unbound");
            require(text(value, "rom_sha1").equals(ROM_SHA1),
                    "S3K submission raw ROM identity changed");
            require(text(value, "service_manifest_sha256")
                            .equals(SUBMISSION_MANIFEST_SHA256),
                    "S3K submission raw service-manifest identity changed");
            int first = integer(value, "first_row");
            int end = integer(value, "exclusive_end");
            int stateStart = integer(value, "state_start");
            int stateEnd = integer(value, "state_exclusive_end");
            require(first == 0 && end == 1 && stateStart == 0x1c00 && stateEnd == 0x2000,
                    "S3K unbound submission interval or driver-state range changed");
            return new Header(first, end, stateStart, stateEnd);
        }
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

    private static RawBoundary boundary(JsonNode value, boolean baseline,
            boolean submissionV2) {
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
        RawBoundary result = new RawBoundary(row, end, state(value), unsignedByte(value, "ym_port0_latch"),
                unsignedByte(value, "ym_port1_latch"), nonNegativeLong(value, "native_arm_epoch"),
                bool(value, "native_armed"),
                serviceJson(value, "active_services", 8, submissionV2),
                serviceJson(value, "pending_descendants", MAX_EVENTS, submissionV2));
        if (baseline && submissionV2) {
            require(result.ymPort0Latch() == 0 && result.ymPort1Latch() == 0,
                    "S3K unbound submission baseline YM latches changed");
            require(result.nativeArmEpoch() == 0 && result.nativeArmed(),
                    "S3K unbound submission baseline native state changed");
            require(result.activeServices().isEmpty() && result.pendingDescendants().isEmpty(),
                    "S3K unbound submission baseline frontier is not empty");
        } else if (baseline) {
            require(result.ymPort0Latch() == 0x28 && result.ymPort1Latch() == 0xa1,
                    "S3K raw baseline YM latches changed");
            require(result.nativeArmEpoch() == 1 && result.nativeArmed(),
                    "S3K raw baseline native arm proof changed");
            require(result.activeServices().isEmpty() && result.pendingDescendants().isEmpty(),
                    "S3K raw baseline frontier is not empty");
        } else if (submissionV2) {
            require(result.nativeArmed() && result.nativeArmEpoch() == 0,
                    "S3K unbound submission cutoff native state changed");
            require(result.activeServices().stream().noneMatch(RawService::complete)
                            && result.pendingDescendants().stream().allMatch(RawService::complete),
                    "S3K unbound submission cutoff lifecycle partition changed");
        } else {
            require(result.nativeArmed() && result.nativeArmEpoch() >= 1,
                    "S3K raw cutoff lost its native arm proof");
            require(result.activeServices().stream().noneMatch(RawService::complete)
                            && result.pendingDescendants().stream().allMatch(RawService::complete),
                    "S3K raw cutoff active/pending lifecycle partition changed");
        }
        return result;
    }

    private static RawFrame frame(JsonNode value, boolean submissionV2) {
        if (submissionV2) exact(value, "type", "row", "lag", "state_hex", "events",
                "submissions");
        else exact(value, "type", "row", "lag", "state_hex", "events");
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
            String payloadText = text(event, "payload");
            require(payloadText.matches("0|[1-9][0-9]*"),
                    "S3K raw payload is not canonical unsigned decimal");
            try { payload = new BigInteger(payloadText); }
            catch (NumberFormatException failure) { throw invalid("S3K raw payload is not unsigned decimal", failure); }
            require(payload.signum() >= 0 && payload.compareTo(MAX_U64) <= 0,
                    "S3K raw payload is outside uint64");
            int kind = unsignedByte(event, "kind");
            int sourceCpu = sourceCpu(event, "source_cpu");
            int payloadLength = unsignedByte(event, "payload_length");
            int flags = unsignedByte(event, "flags");
            int reserved = unsignedByte(event, "reserved");
            require(kind >= 1 && kind <= 11, "S3K raw event kind is outside ABI v3");
            require(payloadLength <= 8, "S3K raw payload length is outside ABI v3");
            require(reserved == 0, "S3K raw reserved field is nonzero");
            require(validFlags(kind, flags), "S3K raw event flags are outside ABI v3");
            require(kind == 6 || payload.signum() == 0,
                    "S3K raw non-snapshot event carries payload bytes");
            int valueByte = unsignedByte(event, "value");
            require(kind == 3 || kind == 4 || kind == 10 || valueByte == 0,
                    "S3K raw event kind carries an unexpected value byte");
            require((kind == 6 && payloadLength >= 1)
                            || (kind != 6 && payloadLength == 0),
                    "S3K raw event payload length disagrees with its ABI kind");
            require(payloadLength == 8 || payload.shiftRight(payloadLength * 8).signum() == 0,
                    "S3K raw payload has nonzero bytes outside its declared length");
            long pc = unsignedInt(event, "pc");
            requirePc(sourceCpu, pc, "event");
            events.add(new RawEvent(ordinal, unsignedWord(event, "service_token"),
                    unsignedWord(event, "parent_token"), unsignedInt(event, "pc"),
                    unsignedWord(event, "subject"), unsignedWord(event, "offset"),
                    kind, serviceKind(event, "service_kind", submissionV2),
                    depth(event, "depth"), sourceCpu, payloadLength,
                    valueByte, flags, reserved, payload));
        }
        List<RawSubmission> submissions = submissionV2
                ? submissions(value.get("submissions"), events) : List.of();
        return new RawFrame(integer(value, "row"), bool(value, "lag"), state(value),
                events, submissions);
    }

    private static List<RawSubmission> submissions(JsonNode source, List<RawEvent> events) {
        require(source != null && source.isArray() && source.size() <= MAX_EVENTS,
                "S3K raw submission list is not bounded");
        List<RawSubmission> result = new ArrayList<>(source.size());
        Set<Integer> tokens = new HashSet<>();
        long previousBegin = -1;
        for (JsonNode value : source) {
            exact(value, "service_token", "parent_token", "begin_ordinal", "end_ordinal",
                    "begin_pc", "end_pc", "begin_hook_token", "end_hook_token",
                    "mailbox_hex", "request");
            int token = nonZeroWord(value, "service_token");
            int parent = nonZeroWord(value, "parent_token");
            long begin = nonNegativeLong(value, "begin_ordinal");
            long end = nonNegativeLong(value, "end_ordinal");
            require(tokens.add(token), "S3K raw submission service token is duplicated");
            require(begin > previousBegin, "S3K raw submissions are not in native begin order");
            previousBegin = begin;
            require(end > begin && end < events.size(),
                    "S3K raw submission native interval is outside its frame");
            long beginPc = unsignedInt(value, "begin_pc");
            long endPc = unsignedInt(value, "end_pc");
            int beginHook = unsignedWord(value, "begin_hook_token");
            int endHook = unsignedWord(value, "end_hook_token");
            require(beginPc == 0x1358 && endPc == 0x1374
                            && beginHook == 27 && endHook == 28,
                    "S3K raw submission Play_Music boundary changed");
            String mailbox = text(value, "mailbox_hex");
            require(mailbox.matches("[0-9a-f]{2}"),
                    "S3K raw submission mailbox is not exact lowercase byte hex");
            List<Integer> bytes = List.of(Integer.parseInt(mailbox, 0, 2, 16));
            int request = unsignedByte(value, "request");
            require(request == bytes.getFirst(),
                    "S3K raw submission request differs from its mailbox evidence");
            requireSubmissionEvents(events, token, parent, begin, end, bytes);
            result.add(new RawSubmission(token, parent, begin, end, beginPc, endPc,
                    beginHook, endHook, bytes, request));
        }
        long declaredBegins = events.stream()
                .filter(event -> event.kind() == 1 && event.serviceKind() == 13).count();
        long declaredEnds = events.stream()
                .filter(event -> event.kind() == 2 && event.serviceKind() == 13).count();
        require(declaredBegins == result.size() && declaredEnds == result.size(),
                "S3K raw submission inventory is not exact");
        return List.copyOf(result);
    }

    private static void requireSubmissionEvents(List<RawEvent> events, int token, int parent,
            long beginOrdinal, long endOrdinal, List<Integer> bytes) {
        require(endOrdinal == beginOrdinal + 4,
                "S3K raw submission snapshot interval is not the bounded native shape");
        RawEvent begin = events.get(Math.toIntExact(beginOrdinal));
        RawEvent start = events.get(Math.toIntExact(beginOrdinal + 1));
        RawEvent chunk = events.get(Math.toIntExact(beginOrdinal + 2));
        RawEvent finish = events.get(Math.toIntExact(beginOrdinal + 3));
        RawEvent end = events.get(Math.toIntExact(endOrdinal));
        require(begin.kind() == 1 && begin.serviceToken() == token
                        && begin.parentToken() == parent && begin.serviceKind() == 13
                        && begin.depth() == 1 && begin.sourceCpu() == 2
                        && begin.pc() == 0x1358 && begin.subject() == 27,
                "S3K raw submission begin evidence changed");
        require(start.kind() == 5 && chunk.kind() == 6 && finish.kind() == 7
                        && start.serviceToken() == token && chunk.serviceToken() == token
                        && finish.serviceToken() == token
                        && start.subject() == 2 && chunk.subject() == 2 && finish.subject() == 2
                        && start.offset() == 0 && start.payloadLength() == 0
                        && chunk.offset() == 0 && chunk.payloadLength() == 1
                        && finish.offset() == 1 && finish.payloadLength() == 0
                        && start.sourceCpu() == 2 && chunk.sourceCpu() == 2
                        && finish.sourceCpu() == 2
                        && start.pc() == 0x1374 && chunk.pc() == 0x1374
                        && finish.pc() == 0x1374,
                "S3K raw submission snapshot evidence changed");
        BigInteger expectedPayload = BigInteger.valueOf(bytes.getFirst());
        require(chunk.payload().equals(expectedPayload),
                "S3K raw submission mailbox bytes differ from native snapshot evidence");
        require(end.kind() == 2 && end.serviceToken() == token
                        && end.parentToken() == parent && end.serviceKind() == 13
                        && end.depth() == 1 && end.sourceCpu() == 2
                        && end.pc() == 0x1374 && end.subject() == 28,
                "S3K raw submission completion evidence changed");
        require(events.subList(Math.toIntExact(endOrdinal + 1), events.size()).stream()
                        .anyMatch(event -> event.kind() == 1 && event.serviceKind() == 12),
                "S3K raw submission is not ordered before following UpdateMusic consumption");
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

    private static List<RawService> serviceJson(JsonNode value, String field, int maximum,
            boolean submissionV2) {
        JsonNode array = value.get(field);
        require(array != null && array.isArray() && array.size() <= maximum,
                "S3K raw boundary service list is not bounded");
        List<RawService> result = new ArrayList<>(array.size());
        for (JsonNode service : array) {
            require(service.isObject(), "S3K raw boundary service is not an object");
            exact(service, "token", "parent_token", "kind", "depth", "current_parent_token",
                    "current_depth", "begin_coordinate", "end_coordinate", "begin_pc", "end_pc",
                    "begin_hook_token", "end_hook_token", "begin_source_cpu", "cancelled", "complete",
                    "chips", "snapshots", "ancestry_transitions");
            int kind = serviceKind(service, "kind", submissionV2);
            int beginHookToken = unsignedWord(service, "begin_hook_token");
            int sourceCpu = integer(service, "begin_source_cpu");
            long beginPc = unsignedInt(service, "begin_pc");
            long endPc = unsignedInt(service, "end_pc");
            boolean resetService = kind == 1;
            if (resetService) {
                require(sourceCpu == 0 && beginPc == 0 && endPc == 0 && beginHookToken == 0,
                        "S3K raw reset service begin-source shape changed");
            } else {
                require(sourceCpu >= 1 && sourceCpu <= 3,
                        "S3K raw begin_source_cpu is outside ABI v3");
                requirePc(sourceCpu, beginPc, "service begin");
                if (endPc != 0) requirePc(sourceCpu, endPc, "service end");
            }
            int parentToken = unsignedWord(service, "parent_token");
            int serviceDepth = depth(service, "depth");
            int currentParentToken = unsignedWord(service, "current_parent_token");
            int currentDepth = depth(service, "current_depth");
            require((serviceDepth == 0) == (parentToken == 0),
                    "S3K raw frontier immutable ancestry is inconsistent");
            require((currentDepth == 0) == (currentParentToken == 0),
                    "S3K raw frontier effective ancestry is inconsistent");
            boolean complete = bool(service, "complete");
            boolean cancelled = bool(service, "cancelled");
            long beginCoordinate = nonNegativeLong(service, "begin_coordinate");
            long endCoordinate = nonNegativeLong(service, "end_coordinate");
            int endHookToken = unsignedWord(service, "end_hook_token");
            require(!resetService || (parentToken == 0 && serviceDepth == 0
                    && currentParentToken == 0 && currentDepth == 0 && complete
                    && endHookToken == 0), "S3K raw reset service lifecycle shape changed");
            require(!complete || endCoordinate >= beginCoordinate,
                    "S3K raw frontier service ends before it begins");
            require(complete || (!cancelled && endCoordinate == 0 && endPc == 0
                    && endHookToken == 0),
                    "S3K raw open frontier service carries completion state");
            result.add(new RawService(nonZeroWord(service, "token"),
                    parentToken, kind, serviceDepth,
                    currentParentToken, currentDepth, beginCoordinate, endCoordinate,
                    beginPc, endPc, beginHookToken, endHookToken,
                    sourceCpu, cancelled, complete,
                    chips(service.get("chips")), snapshots(service.get("snapshots")),
                    ancestry(service.get("ancestry_transitions"))));
        }
        return List.copyOf(result);
    }

    private static List<RawChip> chips(JsonNode array) {
        require(array != null && array.isArray() && array.size() <= MAX_EVENTS,
                "S3K raw frontier chip list is not bounded");
        List<RawChip> result = new ArrayList<>(array.size());
        for (JsonNode chip : array) {
            exact(chip, "coordinate", "native_ordinal", "event_kind", "subject", "value", "pc",
                    "source_cpu", "data", "port", "register");
            int kind = unsignedByte(chip, "event_kind");
            int subject = unsignedByte(chip, "subject");
            int source = sourceCpu(chip, "source_cpu");
            long pc = unsignedInt(chip, "pc");
            requirePc(source, pc, "frontier chip");
            boolean data = bool(chip, "data");
            int port = unsignedByte(chip, "port");
            int register = unsignedByte(chip, "register");
            require(kind == 3 || kind == 4, "S3K raw frontier chip kind is not YM/PSG");
            require(kind != 3 || (subject <= 3 && port == (subject < 2 ? 0 : 1)
                    && data == (subject == 1 || subject == 3)), "S3K raw frontier YM shape changed");
            require(kind != 4 || (subject == 0 && data && port == 0 && register == 0),
                    "S3K raw frontier PSG shape changed");
            result.add(new RawChip(nonNegativeLong(chip, "coordinate"),
                    unsignedInt(chip, "native_ordinal"), kind, subject,
                    unsignedByte(chip, "value"), pc, source, data, port, register));
        }
        return List.copyOf(result);
    }

    private static List<RawSnapshot> snapshots(JsonNode array) {
        require(array != null && array.isArray() && array.size() <= MAX_EVENTS,
                "S3K raw frontier snapshot list is not bounded");
        List<RawSnapshot> result = new ArrayList<>(array.size());
        for (JsonNode snapshot : array) {
            exact(snapshot, "range_id", "source_cpu", "pc", "bytes_hex");
            int range = unsignedWord(snapshot, "range_id");
            require(range == 1 || range == 2, "S3K raw frontier snapshot range is unknown");
            int source = sourceCpu(snapshot, "source_cpu");
            long pc = unsignedInt(snapshot, "pc");
            requirePc(source, pc, "frontier snapshot");
            String hex = text(snapshot, "bytes_hex");
            int expected = range == 1 ? 8192 : 1;
            require(hex.length() == expected * 2 && hex.matches("[0-9a-f]+"),
                    "S3K raw frontier snapshot width changed");
            result.add(new RawSnapshot(range, source, pc, hex(hex)));
        }
        return List.copyOf(result);
    }

    private static List<RawAncestryTransition> ancestry(JsonNode array) {
        require(array != null && array.isArray() && array.size() <= 7,
                "S3K raw frontier ancestry list is not bounded");
        List<RawAncestryTransition> result = new ArrayList<>(array.size());
        for (JsonNode transition : array) {
            exact(transition, "coordinate", "native_ordinal", "previous_parent_token",
                    "previous_depth", "current_parent_token", "current_depth", "hook_token",
                    "source_cpu", "pc");
            int source = sourceCpu(transition, "source_cpu");
            long pc = unsignedInt(transition, "pc");
            requirePc(source, pc, "frontier ancestry");
            result.add(new RawAncestryTransition(nonNegativeLong(transition, "coordinate"),
                    unsignedInt(transition, "native_ordinal"),
                    unsignedWord(transition, "previous_parent_token"),
                    depth(transition, "previous_depth"),
                    unsignedWord(transition, "current_parent_token"),
                    depth(transition, "current_depth"), nonZeroWord(transition, "hook_token"),
                    source, pc));
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
        require(node != null && node.isIntegralNumber() && node.canConvertToInt(),
                "S3K raw " + field + " is not canonical int");
        return node.intValue();
    }

    private static boolean bool(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isBoolean(), "S3K raw " + field + " is not boolean");
        return node.booleanValue();
    }

    private static long nonNegativeLong(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isIntegralNumber() && node.canConvertToLong()
                        && node.longValue() >= 0,
                "S3K raw " + field + " is not a canonical non-negative long");
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

    private static int nonZeroWord(JsonNode value, String field) {
        int result = unsignedWord(value, field);
        require(result != 0, "S3K raw " + field + " is zero");
        return result;
    }

    private static int sourceCpu(JsonNode value, String field) {
        int result = unsignedByte(value, field);
        require(result >= 1 && result <= 3, "S3K raw " + field + " is outside ABI v3");
        return result;
    }

    private static int depth(JsonNode value, String field) {
        int result = unsignedByte(value, field);
        require(result <= 7, "S3K raw " + field + " exceeds the ABI stack bound");
        return result;
    }

    private static int serviceKind(JsonNode value, String field, boolean submissionV2) {
        int result = unsignedByte(value, field);
        require(result == 0 || result == 1 || result == 2 || result == 3
                        || (result >= 5 && result <= (submissionV2 ? 13 : 12)),
                "S3K raw " + field + " is outside the reviewed S3K manifest");
        return result;
    }

    private static boolean validFlags(int kind, int flags) {
        return switch (kind) {
            case 2 -> flags == 0 || flags == 2;
            case 8, 9 -> flags == 0 || flags == 1;
            default -> flags == 0;
        };
    }

    private static void requirePc(int sourceCpu, long pc, String label) {
        require((sourceCpu == 1 && pc <= 0xffffL)
                        || (sourceCpu == 2 && pc <= 0xff_ffffL)
                        || (sourceCpu == 3 && pc == 0),
                "S3K raw " + label + " PC is outside its ABI source CPU");
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++)
            result[index] = (byte) Integer.parseInt(value, index * 2, index * 2 + 2, 16);
        return result;
    }

    private static long unsignedInt(JsonNode value, String field) {
        JsonNode node = value.get(field);
        require(node != null && node.isIntegralNumber() && node.canConvertToLong(),
                "S3K raw " + field + " is not uint32");
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
