package com.openggf.tools.audio.s3kparity;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Strict, comparison-only S3K write and PCM parity artifact. */
public final class S3kAudioParityManifest {

    private static final int MAX_WRITE_ROWS = 1_000_000;
    private static final int MAX_PCM_WINDOWS = 4_096;
    private static final int MAX_SAMPLES_PER_WINDOW = 1_000_000;
    private static final int MAX_TOTAL_SAMPLES = 2_000_000;
    private static final Map<String, String> LOCKED_ON_CONDITIONS = Map.of(
            "SonicDriverVer", "4",
            "fix_sndbugs", "0",
            "FixMusicAndSFXDataBugs", "0",
            "FixBugs", "0");

    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public enum ManifestKind {
        WRITE("openggf.s3k-audio-write-parity.v1"),
        CHIP_PCM("openggf.s3k-chip-pcm-parity.v1"),
        FINAL_PCM("openggf.s3k-final-pcm-parity.v1");

        private final String schema;

        ManifestKind(String schema) {
            this.schema = schema;
        }
    }

    public enum ChipTapKind {
        YM2612_MIX_STEREO,
        PSG_STEREO_NATIVE,
        DAC_LATCH_MONO,
        FINAL_PRESENTATION_STEREO
    }

    public record Provenance(
            String dialect,
            Map<String, String> sourceConditions,
            String romSha1,
            String bk2Sha256,
            String gpgxSourceSha256,
            String nativePatchSha256,
            String artifactLockSha256,
            String openggfCommit,
            String openggfTree,
            boolean openggfDirty,
            String openggfArtifactSha256,
            String toolchainIdentity,
            String runtimeConfigSha256) {

        public Provenance {
            requireText(dialect, "dialect");
            sourceConditions = Map.copyOf(sourceConditions);
            requireSha1(romSha1, "ROM SHA-1");
            requireSha256(bk2Sha256, "BK2 SHA-256");
            requireSha256(gpgxSourceSha256, "GPGX source SHA-256");
            requireSha256(nativePatchSha256, "native patch SHA-256");
            requireSha256(artifactLockSha256, "artifact lock SHA-256");
            requireText(openggfCommit, "OpenGGF commit");
            requireSha256(openggfTree, "OpenGGF tree");
            requireSha256(openggfArtifactSha256, "OpenGGF artifact SHA-256");
            requireText(toolchainIdentity, "toolchain identity");
            requireSha256(runtimeConfigSha256, "runtime config SHA-256");
            if (openggfDirty) {
                throw new IllegalArgumentException("parity provenance must be clean");
            }
            if (!"LOCKED_ON_S3K_V4".equals(dialect)
                    || !sourceConditions.equals(LOCKED_ON_CONDITIONS)) {
                throw new IllegalArgumentException("parity provenance is not locked-on V4");
            }
        }
    }

    public record Owner(
            long transactionId,
            int serviceKind,
            long serviceOrdinal,
            long generation,
            int trackBase,
            int trackType,
            int channelId,
            int bank,
            int sourcePointer) {

        public Owner {
            if (transactionId < 0 || serviceKind < 0 || serviceOrdinal < 0
                    || generation < 0 || trackBase < 0 || trackBase > 0xFFFF
                    || trackType < 0 || trackType > 0xFF
                    || channelId < 0 || channelId > 0xFF
                    || bank < 0 || bank > 0xFF
                    || sourcePointer < 0 || sourcePointer > 0xFFFF) {
                throw new IllegalArgumentException("write owner field is outside its fixed range");
            }
        }
    }

    public record WriteRow(
            long eventOrdinal,
            long masterCycle,
            long vintOrdinal,
            long serviceEntryMasterCycle,
            int sourcePc,
            Owner owner,
            String chip,
            int port,
            int register,
            int value) {

        public WriteRow {
            if (eventOrdinal < 0 || masterCycle < 0 || vintOrdinal < 0
                    || serviceEntryMasterCycle < 0
                    || serviceEntryMasterCycle > masterCycle
                    || sourcePc < 0 || sourcePc > 0xFFFF
                    || port < 0 || port > 1 || register < 0 || register > 0xFF
                    || value < 0 || value > 0xFF) {
                throw new IllegalArgumentException("write row field is outside its fixed range");
            }
            Objects.requireNonNull(owner, "owner");
            requireText(chip, "chip");
            if (!chip.equals("YM2612") && !chip.equals("PSG") && !chip.equals("DAC")) {
                throw new IllegalArgumentException("unknown parity chip: " + chip);
            }
            if (!chip.equals("YM2612") && (port != 0 || register != 0)) {
                throw new IllegalArgumentException("non-YM write carries a YM address");
            }
        }
    }

    public record PcmWindow(
            ChipTapKind tap,
            long firstMasterCycle,
            int phase,
            String initialStateSha256,
            String writeGroupSha256,
            int frameCount,
            String pcmSha256,
            int leftOnset,
            int rightOnset,
            int leftTail,
            int rightTail,
            List<Integer> samples) {

        public PcmWindow {
            Objects.requireNonNull(tap, "tap");
            if (firstMasterCycle < 0 || phase < 0 || frameCount < 0
                    || leftOnset < -1 || rightOnset < -1
                    || leftTail < -1 || rightTail < -1) {
                throw new IllegalArgumentException("PCM window coordinate is outside its range");
            }
            requireSha256(initialStateSha256, "initial state SHA-256");
            requireSha256(writeGroupSha256, "write group SHA-256");
            requireSha256(pcmSha256, "PCM SHA-256");
            samples = List.copyOf(samples);
            if (samples.size() > MAX_SAMPLES_PER_WINDOW
                    || samples.stream().anyMatch(sample -> sample < Short.MIN_VALUE
                    || sample > Short.MAX_VALUE)) {
                throw new IllegalArgumentException("PCM window exceeds its sample bounds");
            }
            int channels = tap == ChipTapKind.DAC_LATCH_MONO ? 1 : 2;
            if (samples.size() != Math.multiplyExact(frameCount, channels)) {
                throw new IllegalArgumentException("PCM sample count differs from frame layout");
            }
            if ((leftOnset >= frameCount || rightOnset >= frameCount
                    || leftTail >= frameCount || rightTail >= frameCount)
                    || (leftOnset < 0) != (leftTail < 0)
                    || (rightOnset < 0) != (rightTail < 0)
                    || (leftOnset >= 0 && leftTail < leftOnset)
                    || (rightOnset >= 0 && rightTail < rightOnset)) {
                throw new IllegalArgumentException("PCM onset/tail coordinates differ from frames");
            }
            if (tap == ChipTapKind.DAC_LATCH_MONO
                    && (leftOnset != rightOnset || leftTail != rightTail)) {
                throw new IllegalArgumentException("mono PCM has unequal channel coordinates");
            }
            if (!pcmSha256.equals(S3kAudioParityManifest.pcmSha256(samples))) {
                throw new IllegalArgumentException("PCM SHA-256 differs from samples");
            }
        }
    }

    public record CapturePair(String pairId, String replica, String peerBodySha256) {
        public CapturePair {
            requireText(pairId, "capture pair id");
            if (!"A".equals(replica) && !"B".equals(replica)) {
                throw new IllegalArgumentException("capture replica must be A or B");
            }
            requireSha256(peerBodySha256, "peer body SHA-256");
        }
    }

    public record Terminal(
            int rowCount,
            String orderedRowsSha256,
            boolean overflow,
            boolean fault) {

        public Terminal {
            if (rowCount < 0 || rowCount > MAX_WRITE_ROWS + MAX_PCM_WINDOWS) {
                throw new IllegalArgumentException("terminal row count exceeds its cap");
            }
            requireSha256(orderedRowsSha256, "ordered rows SHA-256");
            if (overflow || fault) {
                throw new IllegalArgumentException("parity capture ended with overflow or fault");
            }
        }
    }

    private record Body(
            ManifestKind kind,
            CapturePair capturePair,
            Provenance provenance,
            List<WriteRow> writes,
            List<PcmWindow> pcmWindows,
            Terminal terminal) {
    }

    private final String schema;
    private final String bodySha256;
    private final ManifestKind kind;
    private final CapturePair capturePair;
    private final Provenance provenance;
    private final List<WriteRow> writes;
    private final List<PcmWindow> pcmWindows;
    private final Terminal terminal;
    private final String canonicalJson;

    private S3kAudioParityManifest(String schema, String bodySha256, Body body,
            String canonicalJson) {
        this.schema = schema;
        this.bodySha256 = bodySha256;
        this.kind = body.kind();
        this.capturePair = body.capturePair();
        this.provenance = body.provenance();
        this.writes = List.copyOf(body.writes());
        this.pcmWindows = List.copyOf(body.pcmWindows());
        this.terminal = body.terminal();
        this.canonicalJson = canonicalJson;
    }

    public static S3kAudioParityManifest read(Path path) {
        Objects.requireNonNull(path, "path");
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot read S3K parity manifest", failure);
        }
    }

    public static S3kAudioParityManifest read(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        try {
            JsonNode document = JSON.readTree(reader);
            requireExactFields(document, "manifest", "schema", "body_sha256", "body");
            String schema = requiredText(document, "schema");
            String bodySha256 = requiredText(document, "body_sha256");
            requireSha256(bodySha256, "body SHA-256");
            JsonNode bodyNode = document.get("body");
            if (bodyNode == null || !bodyNode.isObject()) {
                throw new IllegalArgumentException("manifest body must be an object");
            }
            String canonicalBody = JSON.writeValueAsString(bodyNode);
            if (!sha256(canonicalBody).equals(bodySha256)) {
                throw new IllegalArgumentException("manifest body SHA-256 differs");
            }
            Body body = JSON.treeToValue(bodyNode, Body.class);
            validate(body, bodyNode);
            String typedCanonicalBody = JSON.writeValueAsString(JSON.valueToTree(body));
            if (!typedCanonicalBody.equals(canonicalBody)) {
                throw new IllegalArgumentException("manifest body is not canonical");
            }
            if (!body.kind().schema.equals(schema)) {
                throw new IllegalArgumentException("manifest schema differs from kind");
            }

            ObjectNode canonicalDocument = JsonNodeFactory.instance.objectNode();
            canonicalDocument.put("schema", schema);
            canonicalDocument.put("body_sha256", bodySha256);
            canonicalDocument.set("body", JSON.valueToTree(body));
            String canonicalJson = JSON.writeValueAsString(canonicalDocument) + "\n";
            return new S3kAudioParityManifest(schema, bodySha256, body, canonicalJson);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("invalid S3K parity manifest", failure);
        }
    }

    public void validateAgainst(Provenance expected) {
        if (!provenance.equals(Objects.requireNonNull(expected, "expected"))) {
            throw new IllegalArgumentException("parity provenance differs from expected authority");
        }
    }

    public ManifestKind kind() {
        return kind;
    }

    public CapturePair capturePair() {
        return capturePair;
    }

    public Provenance provenance() {
        return provenance;
    }

    public List<WriteRow> writes() {
        return writes;
    }

    public List<PcmWindow> pcmWindows() {
        return pcmWindows;
    }

    public Terminal terminal() {
        return terminal;
    }

    public String canonicalJson() {
        return canonicalJson;
    }

    private static void validate(Body body, JsonNode bodyNode) throws IOException {
        Objects.requireNonNull(body.kind(), "kind");
        Objects.requireNonNull(body.capturePair(), "capturePair");
        Objects.requireNonNull(body.provenance(), "provenance");
        List<WriteRow> writes = List.copyOf(body.writes());
        List<PcmWindow> windows = List.copyOf(body.pcmWindows());
        Objects.requireNonNull(body.terminal(), "terminal");
        if (writes.size() > MAX_WRITE_ROWS || windows.size() > MAX_PCM_WINDOWS) {
            throw new IllegalArgumentException("parity manifest exceeds its row cap");
        }
        int samples = 0;
        for (PcmWindow window : windows) {
            samples = Math.addExact(samples, window.samples().size());
            if (samples > MAX_TOTAL_SAMPLES) {
                throw new IllegalArgumentException("parity manifest exceeds aggregate sample cap");
            }
        }
        if (body.kind() == ManifestKind.WRITE && !windows.isEmpty()) {
            throw new IllegalArgumentException("write manifest contains PCM windows");
        }
        if (body.kind() != ManifestKind.WRITE && !writes.isEmpty()) {
            throw new IllegalArgumentException("PCM manifest contains write rows");
        }
        for (PcmWindow window : windows) {
            boolean presentation = window.tap() == ChipTapKind.FINAL_PRESENTATION_STEREO;
            if ((body.kind() == ManifestKind.CHIP_PCM && presentation)
                    || (body.kind() == ManifestKind.FINAL_PCM && !presentation)) {
                throw new IllegalArgumentException("PCM tap is outside the manifest boundary");
            }
        }
        int expectedRows = body.kind() == ManifestKind.WRITE ? writes.size() : windows.size();
        if (body.terminal().rowCount() != expectedRows) {
            throw new IllegalArgumentException("terminal row count differs");
        }

        long previousCycle = -1;
        Map<Long, Owner> owners = new HashMap<>();
        java.util.Set<Long> closedTransactions = new java.util.HashSet<>();
        long activeTransaction = -1;
        for (int index = 0; index < writes.size(); index++) {
            WriteRow row = writes.get(index);
            if (row.eventOrdinal() != index || row.masterCycle() < previousCycle) {
                throw new IllegalArgumentException("write ordinals/cycles are not dense and ordered");
            }
            previousCycle = row.masterCycle();
            Owner incumbent = owners.putIfAbsent(row.owner().transactionId(), row.owner());
            if (incumbent != null && !incumbent.equals(row.owner())) {
                throw new IllegalArgumentException("write transaction owner mutated");
            }
            long transaction = row.owner().transactionId();
            if (activeTransaction != transaction) {
                if (activeTransaction >= 0) {
                    closedTransactions.add(activeTransaction);
                }
                if (closedTransactions.contains(transaction)) {
                    throw new IllegalArgumentException("write transaction reopened after interruption");
                }
                activeTransaction = transaction;
            }
        }

        JsonNode rowsNode = body.kind() == ManifestKind.WRITE
                ? bodyNode.get("writes") : bodyNode.get("pcm_windows");
        StringBuilder orderedRows = new StringBuilder();
        for (JsonNode row : rowsNode) {
            orderedRows.append(JSON.writeValueAsString(row)).append('\n');
        }
        if (!sha256(orderedRows.toString()).equals(body.terminal().orderedRowsSha256())) {
            throw new IllegalArgumentException("terminal ordered-row SHA-256 differs");
        }
    }

    private static void requireExactFields(JsonNode object, String label, String... fields) {
        if (object == null || !object.isObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        java.util.Set<String> expected = java.util.Set.of(fields);
        java.util.Set<String> actual = new java.util.HashSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(label + " fields differ: " + actual);
        }
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.textValue();
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(label + " is missing or exceeds its bound");
        }
    }

    private static void requireSha1(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException(label + " must be canonical lowercase SHA-1");
        }
    }

    private static void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be canonical lowercase SHA-256");
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String pcmSha256(List<Integer> samples) {
        ByteBuffer bytes = ByteBuffer.allocate(Math.multiplyExact(samples.size(), 2))
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int sample : samples) {
            bytes.putShort((short) sample);
        }
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes.array()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
