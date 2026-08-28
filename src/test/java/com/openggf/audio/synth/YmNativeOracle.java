package com.openggf.audio.synth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

final class YmNativeOracle {
    private static final String SCHEMA =
            "openggf.s3k-ym-write-timing-oracle.v1";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String eventPhase;
    private final Provenance provenance;
    private final List<Group> groups;
    private final String terminalSha256;

    private YmNativeOracle(String eventPhase, Provenance provenance,
                           List<Group> groups, String terminalSha256) {
        this.eventPhase = eventPhase;
        this.provenance = provenance;
        this.groups = List.copyOf(groups);
        this.terminalSha256 = terminalSha256;
    }

    static YmNativeOracle load(Path path) throws IOException {
        JsonNode root = JSON.readTree(path.toFile());
        requireText(root, "schema", SCHEMA);
        String terminalSha256 = requireSha256(root, "terminal_sha256");
        ObjectNode payload = ((ObjectNode) root).deepCopy();
        payload.remove("terminal_sha256");
        String calculated = sha256(JSON.writeValueAsBytes(payload));
        if (!terminalSha256.equals(calculated)) {
            throw new IOException("YM native oracle terminal SHA-256 differs: "
                    + calculated);
        }

        JsonNode provenanceNode = requireObject(root, "provenance");
        Provenance provenance = new Provenance(
                requireText(provenanceNode, "bizhawk_version"),
                requireText(provenanceNode, "bizhawk_commit"),
                requireText(provenanceNode, "gpgx_commit"),
                requireText(provenanceNode, "rom_sha1"),
                requireSha256(provenanceNode, "bk2_sha256"),
                requireSha256(provenanceNode, "diagnostic_patch_sha256"),
                requireSha256(provenanceNode, "diagnostic_core_sha256"),
                requireSha256(provenanceNode, "native_writes_sha256"),
                requireSha256(provenanceNode,
                        "native_writes_projection_sha256"),
                requireSha256(provenanceNode, "native_fm5_sha256"));

        JsonNode groupsNode = requireArray(root, "groups");
        List<Group> groups = new ArrayList<>(groupsNode.size());
        for (int groupIndex = 0; groupIndex < groupsNode.size(); groupIndex++) {
            JsonNode groupNode = groupsNode.get(groupIndex);
            int groupOrdinal = requireInt(groupNode, "group_ordinal");
            if (groupOrdinal != groupIndex) {
                throw new IOException("Non-dense YM group ordinal: "
                        + groupOrdinal);
            }
            JsonNode writesNode = requireArray(groupNode, "writes");
            List<Write> writes = new ArrayList<>(writesNode.size());
            for (int writeIndex = 0; writeIndex < writesNode.size();
                 writeIndex++) {
                JsonNode writeNode = writesNode.get(writeIndex);
                int sourceOrdinal = requireInt(writeNode, "source_ordinal");
                if (sourceOrdinal != writeIndex) {
                    throw new IOException("Non-dense YM source ordinal: "
                            + sourceOrdinal);
                }
                writes.add(new Write(
                        sourceOrdinal,
                        requireLong(writeNode, "master_cycle"),
                        requireLong(writeNode, "relative_master_cycle"),
                        requireLong(writeNode, "internal_ordinal"),
                        requireInt(writeNode, "port"),
                        requireInt(writeNode, "register"),
                        requireInt(writeNode, "value"),
                        requireInt(writeNode, "dma_stall_count")));
            }
            if (writes.isEmpty()
                    || writes.getFirst().relativeMasterCycle() != 0) {
                throw new IOException("YM group has no zero-cycle first write.");
            }
            long relativeLastMasterCycle = requireLong(
                    groupNode, "relative_last_master_cycle");
            if (writes.getLast().relativeMasterCycle()
                    != relativeLastMasterCycle) {
                throw new IOException(
                        "YM group terminal relative cycle differs.");
            }
            groups.add(new Group(
                    groupOrdinal,
                    requireInt(groupNode, "frame"),
                    requireLong(groupNode, "first_internal_ordinal"),
                    requireLong(groupNode, "key_on_internal_ordinal"),
                    relativeLastMasterCycle,
                    intList(requireArray(groupNode,
                            "key_on_attenuation")),
                    requireDouble(groupNode, "onset_rms"),
                    writes));
        }
        if (groups.isEmpty()) {
            throw new IOException("YM native oracle contains no groups.");
        }
        return new YmNativeOracle(requireText(root, "event_phase"),
                provenance, groups, terminalSha256);
    }

    String eventPhase() {
        return eventPhase;
    }

    Provenance provenance() {
        return provenance;
    }

    List<Group> groups() {
        return groups;
    }

    String terminalSha256() {
        return terminalSha256;
    }

    record Provenance(String bizhawkVersion, String bizhawkCommit,
                      String gpgxCommit, String romSha1, String bk2Sha256,
                      String diagnosticPatchSha256,
                      String diagnosticCoreSha256,
                      String nativeWritesSha256,
                      String nativeWritesProjectionSha256,
                      String nativeFm5Sha256) {
    }

    record Group(int groupOrdinal, int frame, long firstInternalOrdinal,
                 long keyOnInternalOrdinal,
                 long relativeLastMasterCycle,
                 List<Integer> keyOnAttenuation, double onsetRms,
                 List<Write> writes) {
        Group {
            keyOnAttenuation = List.copyOf(keyOnAttenuation);
            writes = List.copyOf(writes);
        }
    }

    record Write(int sourceOrdinal, long masterCycle,
                 long relativeMasterCycle, long internalOrdinal,
                 int port, int register, int value, int dmaStallCount) {
    }

    private static JsonNode requireObject(JsonNode node, String field)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject()) {
            throw new IOException("Missing YM oracle object: " + field);
        }
        return value;
    }

    private static JsonNode requireArray(JsonNode node, String field)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IOException("Missing YM oracle array: " + field);
        }
        return value;
    }

    private static String requireText(JsonNode node, String field)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IOException("Missing YM oracle text: " + field);
        }
        return value.textValue();
    }

    private static void requireText(JsonNode node, String field,
                                    String expected) throws IOException {
        String actual = requireText(node, field);
        if (!expected.equals(actual)) {
            throw new IOException("Unsupported YM oracle " + field
                    + ": " + actual);
        }
    }

    private static String requireSha256(JsonNode node, String field)
            throws IOException {
        String value = requireText(node, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid YM oracle SHA-256: " + field);
        }
        return value;
    }

    private static int requireInt(JsonNode node, String field)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new IOException("Missing YM oracle integer: " + field);
        }
        return value.intValue();
    }

    private static long requireLong(JsonNode node, String field)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IOException("Missing YM oracle long: " + field);
        }
        return value.longValue();
    }

    private static double requireDouble(JsonNode node, String field)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IOException("Missing YM oracle number: " + field);
        }
        return value.doubleValue();
    }

    private static List<Integer> intList(JsonNode node) throws IOException {
        List<Integer> values = new ArrayList<>(node.size());
        for (JsonNode value : node) {
            if (!value.canConvertToInt()) {
                throw new IOException(
                        "YM oracle integer array contains a non-integer.");
            }
            values.add(value.intValue());
        }
        return List.copyOf(values);
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }
}
