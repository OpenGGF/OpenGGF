package com.openggf.tools.audio.parity.s2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Strict, comparison-only reader for the unbound request-aware S2 oracle
 * candidate. Production binding is intentionally absent from this type.
 */
final class S2RequestAwareOracleRawStream {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(S2RequestAwareOracleSchema.MAX_LINE_BYTES)
                    .maxNestingDepth(16).maxTokenCount(100_000)
                    .maxNumberLength(20).maxStringLength(S2RequestAwareOracleSchema.MAX_LINE_BYTES)
                    .maxNameLength(64).build())
            .build());

    private S2RequestAwareOracleRawStream() {
    }

    /** Immutable, observed-only active-owner tuple carried with a transfer. */
    record ActiveServiceOwner(int token, int kind, int depth) {
    }

    /** Immutable native record retained as comparison data, never a driver input. */
    record NativeEvent(int ordinal, int serviceToken, int parentToken, long pc,
            int subject, int offset, int kind, int serviceKind, int depth, int sourceCpu,
            int payloadLength, int value, int flags, int reserved, long payload) {
    }

    /** Immutable opaque event envelope whose exact JSON schema was validated on input. */
    record ObservedEnvelope(String compactJson) {
        public ObservedEnvelope {
            Objects.requireNonNull(compactJson, "compactJson");
        }
    }

    /** A pre-consumption M68K-to-Z80 transfer observation, not an admission. */
    record RequestTransfer(int sourceRow, int rowOrder, long sourceGlobalOrdinal,
            int requestByte, int physicalSlot, int pc, long a7, int nativeOrdinal,
            int sourceCpu, int serviceToken, int serviceKind, int depth,
            ActiveServiceOwner activeServiceOwner) {
        public RequestTransfer {
            Objects.requireNonNull(activeServiceOwner, "activeServiceOwner");
        }
    }

    record Baseline(int row, int sourcePrecedingRow, byte[] state,
            int ymPort0Latch, int ymPort1Latch) {
        public Baseline {
            state = state.clone();
        }

        @Override
        public byte[] state() {
            return state.clone();
        }
    }

    record Frame(int row, boolean lag, byte[] state, List<NativeEvent> eventRecords,
            List<ObservedEnvelope> overrideResumeRecords, List<ObservedEnvelope> pcmRecords,
            List<RequestTransfer> requestTransfers) {
        public Frame {
            state = state.clone();
            eventRecords = List.copyOf(eventRecords);
            overrideResumeRecords = List.copyOf(overrideResumeRecords);
            pcmRecords = List.copyOf(pcmRecords);
            requestTransfers = List.copyOf(requestTransfers);
        }

        @Override
        public byte[] state() {
            return state.clone();
        }
    }

    /** Publication occurs only after the terminal record and physical EOF validate. */
    record Result(Baseline baseline, List<Frame> frames, String rawPayloadSha256) {
        public Result {
            Objects.requireNonNull(baseline, "baseline");
            frames = List.copyOf(frames);
            requireSha256(rawPayloadSha256, "raw payload digest");
        }
    }

    /**
     * Package-private on purpose: the reviewed candidate has no bound identity,
     * hence no production path may open it. Tests can exercise only this closed seam.
     */
    static Result scanCandidateForTesting(Path candidate) throws IOException {
        Objects.requireNonNull(candidate, "candidate");
        try (InputStream input = Files.newInputStream(candidate)) {
            return scan(input);
        }
    }

    private static Result scan(InputStream input) throws IOException {
        MessageDigest digest = sha256();
        StrictLines lines = new StrictLines(input, digest);
        byte[] metadataBytes = lines.next("metadata");
        JsonNode metadata = parse(metadataBytes, "metadata");
        validateMetadata(metadata);
        BoundedEvidence evidence = new BoundedEvidence();
        evidence.payloadPrefix(metadataBytes);
        byte[] baselineBytes = lines.next("baseline");
        Baseline baseline = parseBaseline(parse(baselineBytes, "baseline"));
        evidence.body(baselineBytes);
        List<Frame> frames = new ArrayList<>(S2RequestAwareOracleSchema.EXCLUSIVE_END
                - S2RequestAwareOracleSchema.FIRST_ROW);
        long previousGlobalOrdinal = -1;
        int overrideRow = -1;
        int pcmRow = -1;
        for (int row = S2RequestAwareOracleSchema.FIRST_ROW;
                row < S2RequestAwareOracleSchema.EXCLUSIVE_END; row++) {
            byte[] frameBytes = lines.next("frame");
            JsonNode frameRecord = parse(frameBytes, "frame");
            Frame frame = parseFrame(frameRecord, row,
                    previousGlobalOrdinal);
            if (!frame.requestTransfers().isEmpty()) {
                previousGlobalOrdinal = frame.requestTransfers().getLast().sourceGlobalOrdinal();
            }
            boolean hasOverride = !frame.overrideResumeRecords().isEmpty();
            boolean hasPcm = !frame.pcmRecords().isEmpty();
            require(!hasOverride || overrideRow < 0, "override resume is duplicated");
            if (hasOverride) overrideRow = row;
            if (hasPcm) {
                require(pcmRow < 0, "PCM packet is duplicated");
                String selection = text(frameRecord.get("pcm"), "selection");
                require(("service_frame".equals(selection) && hasOverride)
                                || ("following_row".equals(selection) && !hasOverride
                                        && overrideRow == row - 1),
                        "PCM selection differs from override resume");
                pcmRow = row;
            }
            evidence.frame(frameBytes, frameRecord, frame);
            frames.add(frame);
        }
        require((overrideRow < 0) == (pcmRow < 0),
                "override resume and PCM inventory differ");
        validateCutoff(parse(lines.next("cutoff"), "cutoff"), evidence);
        require(lines.eof(), "records follow the cutoff");
        return new Result(baseline, frames, HexFormat.of().formatHex(digest.digest()));
    }

    private static void validateMetadata(JsonNode value) {
        exact(value, "metadata", "type", "schema", "rom_sha1", "bk2_sha256",
                "service_manifest_sha256", "first_row", "exclusive_end", "state_start",
                "state_exclusive_end", "source_schema", "source_first_row",
                "source_exclusive_end", "request_transfer_schema", "production_bound",
                "digest_domains");
        require("metadata".equals(text(value, "type")), "metadata type differs");
        require(S2RequestAwareOracleSchema.PAYLOAD_SCHEMA.equals(text(value, "schema")),
                "schema differs");
        require(S2RequestAwareOracleSchema.S2_REV01_SHA1.equals(text(value, "rom_sha1")),
                "ROM identity differs");
        require(S2RequestAwareOracleSchema.BK2_SHA256.equals(text(value, "bk2_sha256")),
                "BK2 identity differs");
        require(S2RequestAwareOracleSchema.SERVICE_MANIFEST_SHA256.equals(
                text(value, "service_manifest_sha256")), "service manifest identity differs");
        require(integer(value, "first_row") == S2RequestAwareOracleSchema.FIRST_ROW
                        && integer(value, "exclusive_end")
                                == S2RequestAwareOracleSchema.EXCLUSIVE_END
                        && integer(value, "state_start") == 0
                        && integer(value, "state_exclusive_end")
                                == S2RequestAwareOracleSchema.STATE_BYTES,
                "bounded metadata shape differs");
        require(S2RequestAwareOracleSchema.SOURCE_SCHEMA.equals(text(value, "source_schema"))
                        && integer(value, "source_first_row")
                                == S2RequestAwareOracleSchema.SOURCE_FIRST_ROW
                        && integer(value, "source_exclusive_end")
                                == S2RequestAwareOracleSchema.SOURCE_EXCLUSIVE_END,
                "source identity differs");
        require(S2RequestAwareOracleSchema.REQUEST_TRANSFER_SCHEMA.equals(
                text(value, "request_transfer_schema")), "request transfer schema differs");
        require(!bool(value, "production_bound"), "candidate must remain unbound");
        JsonNode domains = value.get("digest_domains");
        exact(domains, "digest domains", "inventories", "body", "terminal_state",
                "payload_before_cutoff");
        require(S2RequestAwareOracleSchema.INVENTORY_DIGEST_DOMAIN.equals(
                        text(domains, "inventories"))
                        && S2RequestAwareOracleSchema.BODY_DIGEST_DOMAIN.equals(
                                text(domains, "body"))
                        && S2RequestAwareOracleSchema.TERMINAL_STATE_DIGEST_DOMAIN.equals(
                                text(domains, "terminal_state"))
                        && S2RequestAwareOracleSchema.PAYLOAD_PREFIX_DIGEST_DOMAIN.equals(
                                text(domains, "payload_before_cutoff")),
                "bounded digest domains differ");
    }

    private static Baseline parseBaseline(JsonNode value) {
        exact(value, "baseline", "type", "row", "source_preceding_row", "state_hex",
                "ym_port0_latch", "ym_port1_latch");
        require("baseline".equals(text(value, "type"))
                        && integer(value, "row") == S2RequestAwareOracleSchema.FIRST_ROW
                        && integer(value, "source_preceding_row")
                                == S2RequestAwareOracleSchema.FIRST_ROW - 1,
                "baseline boundary differs");
        return new Baseline(integer(value, "row"), integer(value, "source_preceding_row"),
                state(value), unsignedByte(value, "ym_port0_latch"),
                unsignedByte(value, "ym_port1_latch"));
    }

    private static Frame parseFrame(JsonNode value, int row, long previousGlobalOrdinal) {
        exact(value, "frame", "type", "row", "lag", "state_hex", "events",
                "override_resume", "pcm", "request_transfers");
        require("frame".equals(text(value, "type")) && integer(value, "row") == row,
                "frame row differs");
        List<NativeEvent> events = parseEvents(array(value, "events"));
        List<ObservedEnvelope> override = parseOverride(value.get("override_resume"), row);
        List<ObservedEnvelope> pcm = parsePcm(value.get("pcm"), row);
        List<RequestTransfer> transfers = parseTransfers(array(value, "request_transfers"), row,
                events, previousGlobalOrdinal);
        return new Frame(row, bool(value, "lag"), state(value), events, override, pcm, transfers);
    }

    private static List<NativeEvent> parseEvents(JsonNode values) {
        List<NativeEvent> result = new ArrayList<>();
        int ordinal = 0;
        for (JsonNode value : values) {
            NativeEvent event = parseNativeEvent(value);
            require(event.ordinal() == ordinal++, "native ordinals differ");
            require(!looksLikeMarker(event) || isStructuralMarker(event),
                    "near request marker differs from the fixed native shape");
            result.add(event);
        }
        return List.copyOf(result);
    }

    private static NativeEvent parseNativeEvent(JsonNode value) {
        exact(value, "event", "ordinal", "service_token", "parent_token", "pc",
                "subject", "offset", "kind", "service_kind", "depth", "source_cpu",
                "payload_length", "value", "flags", "reserved", "payload");
        return new NativeEvent(integer(value, "ordinal"), unsignedShort(value, "service_token"),
                unsignedShort(value, "parent_token"), unsignedInt(value, "pc"),
                unsignedShort(value, "subject"), unsignedShort(value, "offset"),
                unsignedByte(value, "kind"), unsignedByte(value, "service_kind"),
                unsignedByte(value, "depth"), unsignedByte(value, "source_cpu"),
                unsignedByte(value, "payload_length"), unsignedByte(value, "value"),
                unsignedByte(value, "flags"), unsignedByte(value, "reserved"),
                canonicalUnsigned(text(value, "payload"), "event payload"));
    }

    private static List<RequestTransfer> parseTransfers(JsonNode values, int row,
            List<NativeEvent> events, long previousGlobalOrdinal) {
        require(values.size() <= 4, "request occupancy exceeds four physical slots");
        List<RequestTransfer> result = new ArrayList<>();
        Set<Integer> consumedMarkers = new HashSet<>();
        int previousNative = -1;
        long previousGlobal = previousGlobalOrdinal;
        for (int order = 0; order < values.size(); order++) {
            JsonNode value = values.get(order);
            exact(value, "request transfer", "row", "order", "global_transfer_ordinal",
                    "request", "slot", "pc", "a7", "native_ordinal", "source_cpu",
                    "service_token", "service_kind", "depth", "active_service_owner");
            long global = integer64(value, "global_transfer_ordinal");
            int nativeOrdinal = integer(value, "native_ordinal");
            require(integer(value, "row") == row && integer(value, "order") == order
                            && global >= 0
                            && (previousGlobal < 0 || global == previousGlobal + 1)
                            && nativeOrdinal > previousNative,
                    "request transfer order differs");
            require(unsignedByte(value, "request") != 0
                            && integer(value, "slot") >= 0 && integer(value, "slot") <= 3
                            && unsignedInt(value, "pc") == S2RequestAwareOracleSchema.REQUEST_PC
                            && unsignedByte(value, "source_cpu") == 2
                            && unsignedShort(value, "service_token") == 0
                            && unsignedByte(value, "service_kind") == 0
                            && unsignedByte(value, "depth") == 0,
                    "request transfer identity differs");
            ActiveServiceOwner owner = parseOwner(value.get("active_service_owner"));
            require(owner.token() == unsignedShort(value, "service_token")
                            && owner.kind() == unsignedByte(value, "service_kind")
                            && owner.depth() == unsignedByte(value, "depth"),
                    "request owner differs");
            long a7 = a7(value);
            require(nativeOrdinal >= 0 && nativeOrdinal < events.size()
                            && consumedMarkers.add(nativeOrdinal)
                            && isExactMarker(events.get(nativeOrdinal), a7),
                    "request transfer marker differs");
            previousGlobal = global;
            previousNative = nativeOrdinal;
            result.add(new RequestTransfer(row, order, global, unsignedByte(value, "request"),
                    integer(value, "slot"), (int) unsignedInt(value, "pc"), a7, nativeOrdinal,
                    unsignedByte(value, "source_cpu"), unsignedShort(value, "service_token"),
                    unsignedByte(value, "service_kind"), unsignedByte(value, "depth"), owner));
        }
        for (NativeEvent event : events) {
            if (looksLikeMarker(event)) {
                require(consumedMarkers.contains(event.ordinal()),
                        "request marker has no transfer");
            }
        }
        return List.copyOf(result);
    }

    private static ActiveServiceOwner parseOwner(JsonNode value) {
        exact(value, "active service owner", "token", "kind", "depth");
        return new ActiveServiceOwner(unsignedShort(value, "token"), unsignedByte(value, "kind"),
                unsignedByte(value, "depth"));
    }

    private static boolean isExactMarker(NativeEvent value, long a7) {
        return isStructuralMarker(value) && value.payload() == a7;
    }

    private static boolean isStructuralMarker(NativeEvent value) {
        return value.kind() == 10 && value.value() == 3
                && value.pc() == S2RequestAwareOracleSchema.REQUEST_PC
                && value.subject() == S2RequestAwareOracleSchema.REQUEST_MARKER_TOKEN
                && value.sourceCpu() == 2 && value.serviceToken() == 0
                && value.parentToken() == 0 && value.serviceKind() == 0 && value.depth() == 0
                && value.payloadLength() == 4 && value.offset() == 0
                && value.flags() == 0 && value.reserved() == 0;
    }

    private static boolean looksLikeMarker(NativeEvent value) {
        return value.subject() == S2RequestAwareOracleSchema.REQUEST_MARKER_TOKEN
                || value.kind() == 10 && value.value() == 3
                        && value.pc() == S2RequestAwareOracleSchema.REQUEST_PC;
    }

    private static long a7(JsonNode value) {
        long result = canonicalUnsigned(text(value, "a7"), "request A7");
        require(Long.compareUnsigned(result, 0xffff_ffffL) <= 0,
                "request A7 is outside the native unsigned-int range");
        return result;
    }

    private static List<ObservedEnvelope> parseOverride(JsonNode value, int row) {
        if (value == null) throw invalid("override envelope is absent");
        if (value.isNull()) return List.of();
        exact(value, "override resume", "request", "admission", "request_pc", "pc",
                "service_token", "service_begin_ordinal", "native_ordinal", "frame",
                "fix_driver_bugs", "restores_saved_priority", "restores_psg_noise", "writes");
        require("cfFadeInToPrevious".equals(text(value, "request"))
                        && "native_service_completion".equals(text(value, "admission"))
                        && unsignedInt(value, "request_pc") == 0x0d35
                        && unsignedInt(value, "pc") == 0x0db4
                        && integer(value, "fix_driver_bugs") == 0
                        && bool(value, "restores_saved_priority")
                        && !bool(value, "restores_psg_noise") && integer(value, "frame") == row,
                "override resume identity differs");
        for (JsonNode write : array(value, "writes")) {
            exact(write, "override write", "native_ordinal", "event_kind", "subject", "value",
                    "pc", "source_cpu", "data", "port", "register");
            unsignedInt(write, "native_ordinal"); unsignedByte(write, "event_kind");
            unsignedByte(write, "subject"); unsignedByte(write, "value");
            unsignedInt(write, "pc"); unsignedByte(write, "source_cpu"); bool(write, "data");
            unsignedByte(write, "port"); unsignedByte(write, "register");
        }
        require(!array(value, "writes").isEmpty(), "override resume has no writes");
        return List.of(new ObservedEnvelope(compact(value)));
    }

    private static List<ObservedEnvelope> parsePcm(JsonNode value, int row) {
        if (value == null) throw invalid("PCM envelope is absent");
        if (value.isNull()) return List.of();
        exact(value, "PCM", "selection", "row", "offset", "sample_rate", "channels", "format",
                "stereo_frames", "byte_count", "pcm_hex", "sha256");
        require(("service_frame".equals(text(value, "selection"))
                        || "following_row".equals(text(value, "selection")))
                        && integer(value, "row") == row && integer(value, "offset") >= 0
                        && integer(value, "sample_rate") == 44_100 && integer(value, "channels") == 2
                        && "s16le-interleaved-stereo".equals(text(value, "format")),
                "PCM identity differs");
        long frames = integer64(value, "stereo_frames");
        long byteCount = integer64(value, "byte_count");
        String hex = lowercaseHex(text(value, "pcm_hex"), "PCM bytes");
        long expectedByteCount;
        try {
            expectedByteCount = Math.multiplyExact(frames, 4L);
        } catch (ArithmeticException exception) {
            throw invalid("PCM frame count overflows", exception);
        }
        require(frames >= 0 && byteCount >= 0 && byteCount == expectedByteCount
                        && byteCount <= S2RequestAwareOracleSchema.MAX_LINE_BYTES / 2
                        && hex.length() == byteCount * 2, "PCM shape differs");
        String expected = text(value, "sha256");
        requireSha256(expected, "PCM digest");
        require(expected.equals(HexFormat.of().formatHex(sha256().digest(HexFormat.of().parseHex(hex)))),
                "PCM digest differs");
        return List.of(new ObservedEnvelope(compact(value)));
    }

    private static void validateCutoff(JsonNode value, BoundedEvidence evidence) {
        exact(value, "cutoff", "type", "exclusive_end", "frame_count", "base_event_count",
                "all_event_count", "marker_event_count", "request_transfer_count",
                "override_resume_count", "pcm_count", "max_request_occupancy",
                "base_event_sha256", "all_event_sha256", "marker_event_sha256",
                "request_transfer_sha256", "override_resume_sha256", "pcm_sha256",
                "body_byte_count", "body_sha256", "terminal_state_sha256",
                "payload_before_cutoff_sha256");
        require("cutoff".equals(text(value, "type"))
                        && integer(value, "exclusive_end") == S2RequestAwareOracleSchema.EXCLUSIVE_END,
                "cutoff boundary differs");
        evidence.verify(value);
    }

    /** Self-contained bounded output accounting; it never consults raw-v3 provenance. */
    private static final class BoundedEvidence {
        private final MessageDigest payloadPrefix = sha256();
        private final MessageDigest body = sha256();
        private final MessageDigest baseEvents = sha256();
        private final MessageDigest allEvents = sha256();
        private final MessageDigest markerEvents = sha256();
        private final MessageDigest transfers = sha256();
        private final MessageDigest overrideResumes = sha256();
        private final MessageDigest pcm = sha256();
        private long bodyByteCount;
        private long frameCount;
        private long baseEventCount;
        private long allEventCount;
        private long markerEventCount;
        private long transferCount;
        private long overrideCount;
        private long pcmCount;
        private int maxRequestOccupancy;
        private byte[] terminalState;

        void payloadPrefix(byte[] line) {
            updateLine(payloadPrefix, line);
        }

        void body(byte[] line) {
            updateLine(payloadPrefix, line);
            updateLine(body, line);
            bodyByteCount += line.length + 1L;
        }

        void frame(byte[] line, JsonNode value, Frame parsed) {
            body(line);
            frameCount++;
            terminalState = parsed.state();
            JsonNode events = array(value, "events");
            for (JsonNode event : events) {
                byte[] compact = compactBytes(event);
                updateLine(allEvents, compact);
                allEventCount++;
                NativeEvent nativeEvent = parseNativeEvent(event);
                if (isStructuralMarker(nativeEvent)) {
                    updateLine(markerEvents, compact);
                    markerEventCount++;
                } else {
                    updateLine(baseEvents, compact);
                    baseEventCount++;
                }
            }
            JsonNode requestTransfers = array(value, "request_transfers");
            maxRequestOccupancy = Math.max(maxRequestOccupancy, requestTransfers.size());
            for (JsonNode transfer : requestTransfers) {
                updateLine(transfers, compactBytes(transfer));
                transferCount++;
            }
            JsonNode override = value.get("override_resume");
            if (override != null && !override.isNull()) {
                updateLine(overrideResumes, compactBytes(override));
                overrideCount++;
            }
            JsonNode pcmValue = value.get("pcm");
            if (pcmValue != null && !pcmValue.isNull()) {
                updateLine(pcm, compactBytes(pcmValue));
                pcmCount++;
            }
        }

        void verify(JsonNode cutoff) {
            require(frameCount == S2RequestAwareOracleSchema.EXCLUSIVE_END
                            - S2RequestAwareOracleSchema.FIRST_ROW
                            && terminalState != null && overrideCount <= 1 && pcmCount <= 1
                            && overrideCount == pcmCount && markerEventCount == transferCount
                            && allEventCount == baseEventCount + markerEventCount
                            && maxRequestOccupancy <= 4,
                    "bounded inventory shape differs");
            require(integer64(cutoff, "frame_count") == frameCount
                            && integer64(cutoff, "base_event_count") == baseEventCount
                            && integer64(cutoff, "all_event_count") == allEventCount
                            && integer64(cutoff, "marker_event_count") == markerEventCount
                            && integer64(cutoff, "request_transfer_count") == transferCount
                            && integer64(cutoff, "override_resume_count") == overrideCount
                            && integer64(cutoff, "pcm_count") == pcmCount
                            && integer(cutoff, "max_request_occupancy") == maxRequestOccupancy
                            && integer64(cutoff, "body_byte_count") == bodyByteCount,
                    "bounded inventory count differs");
            require(digest(baseEvents).equals(text(cutoff, "base_event_sha256"))
                            && digest(allEvents).equals(text(cutoff, "all_event_sha256"))
                            && digest(markerEvents).equals(text(cutoff, "marker_event_sha256"))
                            && digest(transfers).equals(text(cutoff, "request_transfer_sha256"))
                            && digest(overrideResumes).equals(text(cutoff, "override_resume_sha256"))
                            && digest(pcm).equals(text(cutoff, "pcm_sha256"))
                            && digest(body).equals(text(cutoff, "body_sha256"))
                            && HexFormat.of().formatHex(sha256().digest(terminalState)).equals(
                                    text(cutoff, "terminal_state_sha256"))
                            && digest(payloadPrefix).equals(text(cutoff,
                                    "payload_before_cutoff_sha256")),
                    "bounded inventory digest differs");
        }

        private static void updateLine(MessageDigest digest, byte[] value) {
            digest.update(value);
            digest.update((byte) '\n');
        }

        private static String digest(MessageDigest value) {
            return HexFormat.of().formatHex(value.digest());
        }
    }

    private static JsonNode parse(byte[] bytes, String label) throws IOException {
        try {
            JsonParser parser = JSON.getFactory().createParser(bytes);
            JsonNode result = JSON.readTree(parser);
            require(result != null && result.isObject() && parser.nextToken() == null
                            && Arrays.equals(bytes, compactBytes(result)),
                    label + " must be one object");
            return result;
        } catch (RuntimeException | IOException exception) {
            throw invalid(label + " JSON differs", exception);
        }
    }

    private static byte[] state(JsonNode value) {
        String hex = lowercaseHex(text(value, "state_hex"), "state");
        require(hex.length() == S2RequestAwareOracleSchema.STATE_BYTES * 2,
                "state snapshot shape differs");
        return HexFormat.of().parseHex(hex);
    }

    private static JsonNode array(JsonNode value, String field) {
        JsonNode result = value.get(field);
        require(result != null && result.isArray(), "missing array: " + field);
        return result;
    }

    private static String text(JsonNode value, String field) {
        JsonNode result = value.get(field);
        require(result != null && result.isTextual(), "missing string: " + field);
        return result.textValue();
    }

    private static boolean bool(JsonNode value, String field) {
        JsonNode result = value.get(field);
        require(result != null && result.isBoolean(), "missing boolean: " + field);
        return result.booleanValue();
    }

    private static int integer(JsonNode value, String field) {
        JsonNode result = value.get(field);
        require(result != null && result.isIntegralNumber() && result.canConvertToInt(),
                "missing integer: " + field);
        return result.intValue();
    }

    private static long integer64(JsonNode value, String field) {
        JsonNode result = value.get(field);
        require(result != null && result.isIntegralNumber() && result.canConvertToLong(),
                "missing long integer: " + field);
        return result.longValue();
    }

    private static int unsignedByte(JsonNode value, String field) {
        int result = integer(value, field);
        require(result >= 0 && result <= 0xff, field + " is outside byte range");
        return result;
    }

    private static int unsignedShort(JsonNode value, String field) {
        int result = integer(value, field);
        require(result >= 0 && result <= 0xffff, field + " is outside short range");
        return result;
    }

    private static long unsignedInt(JsonNode value, String field) {
        long result = integer64(value, field);
        require(result >= 0 && result <= 0xffff_ffffL, field + " is outside unsigned int range");
        return result;
    }

    private static long canonicalUnsigned(String value, String label) {
        try {
            long result = Long.parseUnsignedLong(value);
            require(value.equals(Long.toUnsignedString(result)), label + " is not canonical");
            return result;
        } catch (NumberFormatException exception) {
            throw invalid(label + " differs", exception);
        }
    }

    private static String lowercaseHex(String value, String label) {
        require((value.length() & 1) == 0 && value.chars().allMatch(character ->
                character >= '0' && character <= '9' || character >= 'a' && character <= 'f'),
                label + " is not lowercase hexadecimal");
        return value;
    }

    private static void requireSha256(String value, String label) {
        require(value.length() == 64 && value.chars().allMatch(character ->
                character >= '0' && character <= '9' || character >= 'a' && character <= 'f'),
                label + " is not a SHA-256 identity");
    }

    private static void exact(JsonNode value, String label, String... fields) {
        require(value != null && value.isObject() && value.size() == fields.length,
                label + " has missing or unknown fields");
        Set<String> expected = Set.of(fields);
        require(expected.size() == fields.length, "duplicate reader schema declaration");
        value.fieldNames().forEachRemaining(field -> require(expected.contains(field),
                label + " has unknown field: " + field));
        for (String field : fields) require(value.has(field), label + " is missing field: " + field);
    }

    private static String compact(JsonNode value) {
        return new String(compactBytes(value), StandardCharsets.UTF_8);
    }

    private static byte[] compactBytes(JsonNode value) {
        try {
            return JSON.writeValueAsBytes(value);
        } catch (IOException exception) {
            throw new IllegalStateException("validated JSON cannot be rendered", exception);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("S2 request-aware oracle candidate invalid: " + message);
    }

    private static IllegalArgumentException invalid(String message, Exception cause) {
        return new IllegalArgumentException("S2 request-aware oracle candidate invalid: " + message, cause);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    /** Reads, validates and hashes the exact same descriptor without a reopen. */
    private static final class StrictLines {
        private final InputStream input;
        private final MessageDigest digest;

        StrictLines(InputStream input, MessageDigest digest) {
            this.input = input;
            this.digest = digest;
        }

        byte[] next(String label) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (true) {
                int value = input.read();
                if (value < 0) throw invalid("payload ended before " + label);
                digest.update((byte) value);
                if (value == '\r') throw invalid("CR is forbidden in JSONL");
                if (value == '\n') break;
                require(line.size() < S2RequestAwareOracleSchema.MAX_LINE_BYTES,
                        "JSONL line exceeds the bounded limit");
                line.write(value);
            }
            byte[] bytes = line.toByteArray();
            require(bytes.length > 0 && !(bytes.length >= 3 && bytes[0] == (byte) 0xef
                    && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf),
                    "empty line or UTF-8 BOM is forbidden");
            try {
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes));
            } catch (CharacterCodingException exception) {
                throw invalid("JSONL is not strict UTF-8", exception);
            }
            return bytes;
        }

        boolean eof() throws IOException {
            int value = input.read();
            if (value < 0) return true;
            digest.update((byte) value);
            return false;
        }
    }
}
