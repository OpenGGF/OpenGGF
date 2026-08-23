package com.openggf.tools.audio.s3kparity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kAudioParityManifest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String HASH_C = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

    @Test
    void canonicalWriteManifestRoundTripsWithExactTypedOwnership() throws Exception {
        String json = validWriteManifest(false, false, 2, null);

        S3kAudioParityManifest manifest = S3kAudioParityManifest.read(new StringReader(json));

        assertEquals(S3kAudioParityManifest.ManifestKind.WRITE, manifest.kind());
        assertEquals("B", manifest.capturePair().replica());
        assertEquals(2, manifest.writes().size());
        assertEquals(0x1D90, manifest.writes().getFirst().owner().trackBase());
        assertEquals(1, manifest.writes().getLast().eventOrdinal());
        assertEquals(json, manifest.canonicalJson());
        manifest.validateAgainst(expectedProvenance());
        S3kAudioParityManifest.Provenance wrong = new S3kAudioParityManifest.Provenance(
                "LOCKED_ON_S3K_V4", expectedProvenance().sourceConditions(),
                expectedProvenance().romSha1(), HASH_B,
                expectedProvenance().gpgxSourceSha256(),
                expectedProvenance().nativePatchSha256(),
                expectedProvenance().artifactLockSha256(),
                expectedProvenance().openggfCommit(), expectedProvenance().openggfTree(),
                false, expectedProvenance().openggfArtifactSha256(),
                expectedProvenance().toolchainIdentity(),
                expectedProvenance().runtimeConfigSha256());
        assertThrows(IllegalArgumentException.class, () -> manifest.validateAgainst(wrong));
        assertThrows(UnsupportedOperationException.class,
                () -> manifest.writes().add(manifest.writes().getFirst()));
    }

    @Test
    void parserRejectsDirtySourcesOrdinalGapsOwnerMutationAndTerminalLies() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> S3kAudioParityManifest.read(new StringReader(
                        validWriteManifest(true, false, 2, null))));
        assertThrows(IllegalArgumentException.class,
                () -> S3kAudioParityManifest.read(new StringReader(
                        validWriteManifest(false, true, 2, null))));
        assertThrows(IllegalArgumentException.class,
                () -> S3kAudioParityManifest.read(new StringReader(
                        validWriteManifest(false, false, 3, null))));
        assertThrows(IllegalArgumentException.class,
                () -> S3kAudioParityManifest.read(new StringReader(
                        validWriteManifest(false, false, 2, HASH_B))));
    }

    @Test
    void parserRejectsMissingOwnerAndDuplicateJsonFields() throws Exception {
        String valid = validWriteManifest(false, false, 2, null);
        assertThrows(IllegalArgumentException.class,
                () -> S3kAudioParityManifest.read(new StringReader(
                        valid.replaceFirst("\\\"owner\\\":\\{[^}]+},", ""))));
        assertThrows(IllegalArgumentException.class,
                () -> S3kAudioParityManifest.read(new StringReader(
                        valid.replace("\"kind\":\"WRITE\"",
                                "\"kind\":\"WRITE\",\"kind\":\"WRITE\""))));
        JsonNode reordered = JSON.readTree(valid);
        ObjectNode body = (ObjectNode) reordered.path("body");
        JsonNode kind = body.remove("kind");
        body.set("kind", kind);
        ((ObjectNode) reordered).put("body_sha256", sha256(JSON.writeValueAsString(body)));
        assertThrows(IllegalArgumentException.class,
                () -> S3kAudioParityManifest.read(new StringReader(
                        JSON.writeValueAsString(reordered))));
    }

    @Test
    void parserRejectsAReopenedTransactionAfterAnotherOwnerPublishes() throws Exception {
        JsonNode document = JSON.readTree(validWriteManifest(false, false, 2, null));
        ArrayNode writes = (ArrayNode) document.path("body").path("writes");
        ObjectNode first = writes.get(0).deepCopy();
        ObjectNode middle = writes.get(1).deepCopy();
        middle.put("event_ordinal", 1);
        middle.put("master_cycle", 1015);
        ((ObjectNode) middle.path("owner")).put("transaction_id", 42);
        first.put("event_ordinal", 2);
        first.put("master_cycle", 1030);
        writes.removeAll();
        writes.add(JSON.readTree(writeRow(0, 1000, "7", 0x28, 0x05)));
        writes.add(middle);
        writes.add(first);
        String poisoned = rehash(document);

        assertThrows(IllegalArgumentException.class,
                () -> S3kAudioParityManifest.read(new StringReader(poisoned)));
    }

    @Test
    void pcmManifestAuthenticatesLayoutSamplesAndPresentationBoundary() throws Exception {
        String chip = validPcmManifest("CHIP_PCM", "YM2612_MIX_STEREO",
                new int[] {1, -2, 3, -4}, 2, 0, 1, 0, 1, null);
        S3kAudioParityManifest parsed = S3kAudioParityManifest.read(new StringReader(chip));
        assertEquals(S3kAudioParityManifest.ManifestKind.CHIP_PCM, parsed.kind());
        assertEquals(4, parsed.pcmWindows().getFirst().samples().size());

        assertThrows(IllegalArgumentException.class, () -> S3kAudioParityManifest.read(
                new StringReader(validPcmManifest("CHIP_PCM", "YM2612_MIX_STEREO",
                        new int[] {1, -2, 3, -4}, 3, 0, 1, 0, 1, null))));
        assertThrows(IllegalArgumentException.class, () -> S3kAudioParityManifest.read(
                new StringReader(validPcmManifest("CHIP_PCM", "YM2612_MIX_STEREO",
                        new int[] {1, -2, 3, -4}, 2, 0, 2, 0, 1, null))));
        assertThrows(IllegalArgumentException.class, () -> S3kAudioParityManifest.read(
                new StringReader(validPcmManifest("CHIP_PCM", "YM2612_MIX_STEREO",
                        new int[] {1, -2, 3, -4}, 2, 0, 1, 0, 1, HASH_A))));
        assertThrows(IllegalArgumentException.class, () -> S3kAudioParityManifest.read(
                new StringReader(validPcmManifest("FINAL_PCM", "YM2612_MIX_STEREO",
                        new int[] {1, -2, 3, -4}, 2, 0, 1, 0, 1, null))));

        S3kAudioParityManifest.read(new StringReader(validPcmManifest(
                "FINAL_PCM", "FINAL_PRESENTATION_STEREO",
                new int[] {1, -2, 3, -4}, 2, 0, 1, 0, 1, null)));
        S3kAudioParityManifest.read(new StringReader(validPcmManifest(
                "CHIP_PCM", "DAC_LATCH_MONO",
                new int[] {12, -12}, 2, 0, 1, 0, 1, null)));
    }

    @Test
    void parityManifestRemainsComparisonOnlyTooling() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            assertTrue(paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/tools/"))
                    .noneMatch(path -> uncheckedRead(path)
                            .contains("S3kAudioParityManifest")));
        }
    }

    @Test
    void diagnosticNativeArtifactIsLockedWithoutChangingOrdinaryAbi() throws Exception {
        Path root = Path.of("tools/bizhawk-headless/native/gpgx-audio-observer");
        JsonNode lock = JSON.readTree(root.resolve("s3k-parity-artifact-lock.json").toFile());
        assertEquals("DIAGNOSTIC_S3K_PARITY_ONLY", lock.path("publication").asText());
        assertEquals(false, lock.path("production_lock_eligible").asBoolean());
        assertEquals(sha256(Files.readString(root.resolve(
                        "0001-buffer-z80-audio-events.patch"))),
                lock.path("ordinary_patch_sha256").asText());
        assertEquals(sha256(Files.readString(root.resolve(
                        "0002-s3k-audio-parity-events.patch"))),
                lock.path("parity_patch_sha256").asText());
        assertEquals(sha256(Files.readString(root.resolve(
                        "selftest/s3k_parity_harness.c"))),
                lock.path("native_parity_selftest_sha256").asText());
        assertEquals(sha256(Files.readString(root.resolve(
                        "selftest/s3k-parity-run.sh"))),
                lock.path("native_selftest_runner_sha256").asText());
        assertTrue(Files.isExecutable(root.resolve("selftest/s3k-parity-run.sh")));
        assertEquals(4, lock.path("ordinary_observer").path("abi_version").asInt());
        assertEquals(32, lock.path("ordinary_observer").path("event_size").asInt());
        assertEquals(1, lock.path("s3k_parity").path("abi_version").asInt());
        assertEquals(38, lock.path("s3k_parity").path("event_size").asInt());
        assertTrue(Integer.parseInt(lock.path("invisible_state")
                .path("section_size_hex").asText(), 16)
                < lock.path("invisible_state").path("maximum_bytes").asInt());

        String patch = Files.readString(root.resolve("0002-s3k-audio-parity-events.patch"));
        assertTrue(patch.contains("gpgx_s3k_audio_parity_instruction("));
        assertTrue(patch.contains("expected_track_type"));
        assertTrue(patch.contains("source_pointer"));
        assertTrue(!patch.contains("SOUND_ID_FINGERPRINT"));
    }

    private static String uncheckedRead(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static S3kAudioParityManifest.Provenance expectedProvenance() {
        return new S3kAudioParityManifest.Provenance(
                "LOCKED_ON_S3K_V4",
                Map.of("FixBugs", "0", "FixMusicAndSFXDataBugs", "0",
                        "SonicDriverVer", "4", "fix_sndbugs", "0"),
                "cfbf98c36c776677290a872547ac47c53d2761d6",
                HASH_A, HASH_B, HASH_C, HASH_A,
                "79a6b9d86", HASH_B, false, HASH_C,
                "gcc-pinned", HASH_A);
    }

    private static String validWriteManifest(boolean dirty, boolean mutateOwner,
            int terminalCount, String terminalDigest) throws Exception {
        String secondGeneration = mutateOwner ? "8" : "7";
        String firstRow = writeRow(0, 1000, "7", 0x28, 0x05);
        String secondRow = writeRow(1, 1015, secondGeneration, 0xB4, 0xC0);
        String rowsDigest = terminalDigest == null
                ? sha256(firstRow + "\n" + secondRow + "\n") : terminalDigest;
        String body = "{"
                + "\"kind\":\"WRITE\","
                + "\"capture_pair\":{\"pair_id\":\"collapse-a-b\",\"replica\":\"B\","
                + "\"peer_body_sha256\":\"" + HASH_A + "\"},"
                + "\"provenance\":{"
                + "\"dialect\":\"LOCKED_ON_S3K_V4\","
                + "\"source_conditions\":{\"FixBugs\":\"0\","
                + "\"FixMusicAndSFXDataBugs\":\"0\",\"SonicDriverVer\":\"4\","
                + "\"fix_sndbugs\":\"0\"},"
                + "\"rom_sha1\":\"cfbf98c36c776677290a872547ac47c53d2761d6\","
                + "\"bk2_sha256\":\"" + HASH_A + "\","
                + "\"gpgx_source_sha256\":\"" + HASH_B + "\","
                + "\"native_patch_sha256\":\"" + HASH_C + "\","
                + "\"artifact_lock_sha256\":\"" + HASH_A + "\","
                + "\"openggf_commit\":\"79a6b9d86\","
                + "\"openggf_tree\":\"" + HASH_B + "\","
                + "\"openggf_dirty\":" + dirty + ","
                + "\"openggf_artifact_sha256\":\"" + HASH_C + "\","
                + "\"toolchain_identity\":\"gcc-pinned\","
                + "\"runtime_config_sha256\":\"" + HASH_A + "\"},"
                + "\"writes\":[" + firstRow + "," + secondRow + "],"
                + "\"pcm_windows\":[],"
                + "\"terminal\":{\"row_count\":" + terminalCount + ","
                + "\"ordered_rows_sha256\":\"" + rowsDigest + "\","
                + "\"overflow\":false,\"fault\":false}}";
        return "{\"schema\":\"openggf.s3k-audio-write-parity.v1\","
                + "\"body_sha256\":\"" + sha256(body) + "\",\"body\":" + body + "}\n";
    }

    private static String writeRow(int ordinal, long cycle, String generation,
            int register, int value) {
        return "{\"event_ordinal\":" + ordinal + ",\"master_cycle\":" + cycle
                + ",\"vint_ordinal\":12,\"service_entry_master_cycle\":900,"
                + "\"source_pc\":6961,\"owner\":{\"transaction_id\":41,"
                + "\"service_kind\":3,\"service_ordinal\":8,\"generation\":"
                + generation + ",\"track_base\":7568,\"track_type\":128,"
                + "\"channel_id\":4,\"bank\":15,\"source_pointer\":62000},"
                + "\"chip\":\"YM2612\",\"port\":1,\"register\":" + register
                + ",\"value\":" + value + "}";
    }

    private static String validPcmManifest(String kind, String tap, int[] samples,
            int frameCount, int leftOnset, int leftTail, int rightOnset, int rightTail,
            String digestOverride) throws Exception {
        StringBuilder sampleJson = new StringBuilder();
        for (int index = 0; index < samples.length; index++) {
            if (index > 0) sampleJson.append(',');
            sampleJson.append(samples[index]);
        }
        String digest = digestOverride == null ? pcmSha256(samples) : digestOverride;
        String window = "{\"tap\":\"" + tap + "\",\"first_master_cycle\":1000,"
                + "\"phase\":7,\"initial_state_sha256\":\"" + HASH_A + "\","
                + "\"write_group_sha256\":\"" + HASH_B + "\",\"frame_count\":"
                + frameCount + ",\"pcm_sha256\":\"" + digest + "\","
                + "\"left_onset\":" + leftOnset + ",\"right_onset\":" + rightOnset
                + ",\"left_tail\":" + leftTail + ",\"right_tail\":" + rightTail
                + ",\"samples\":[" + sampleJson + "]}";
        String body = "{\"kind\":\"" + kind + "\","
                + "\"capture_pair\":{\"pair_id\":\"pcm-a-b\",\"replica\":\"A\","
                + "\"peer_body_sha256\":\"" + HASH_A + "\"},"
                + "\"provenance\":" + provenanceJson(false) + ","
                + "\"writes\":[],\"pcm_windows\":[" + window + "],"
                + "\"terminal\":{\"row_count\":1,\"ordered_rows_sha256\":\""
                + sha256(window + "\n") + "\",\"overflow\":false,\"fault\":false}}";
        String schema = switch (kind) {
            case "CHIP_PCM" -> "openggf.s3k-chip-pcm-parity.v1";
            case "FINAL_PCM" -> "openggf.s3k-final-pcm-parity.v1";
            default -> throw new IllegalArgumentException(kind);
        };
        return "{\"schema\":\"" + schema + "\",\"body_sha256\":\""
                + sha256(body) + "\",\"body\":" + body + "}\n";
    }

    private static String provenanceJson(boolean dirty) {
        return "{\"dialect\":\"LOCKED_ON_S3K_V4\","
                + "\"source_conditions\":{\"FixBugs\":\"0\","
                + "\"FixMusicAndSFXDataBugs\":\"0\",\"SonicDriverVer\":\"4\","
                + "\"fix_sndbugs\":\"0\"},"
                + "\"rom_sha1\":\"cfbf98c36c776677290a872547ac47c53d2761d6\","
                + "\"bk2_sha256\":\"" + HASH_A + "\","
                + "\"gpgx_source_sha256\":\"" + HASH_B + "\","
                + "\"native_patch_sha256\":\"" + HASH_C + "\","
                + "\"artifact_lock_sha256\":\"" + HASH_A + "\","
                + "\"openggf_commit\":\"79a6b9d86\",\"openggf_tree\":\""
                + HASH_B + "\",\"openggf_dirty\":" + dirty + ","
                + "\"openggf_artifact_sha256\":\"" + HASH_C + "\","
                + "\"toolchain_identity\":\"gcc-pinned\","
                + "\"runtime_config_sha256\":\"" + HASH_A + "\"}";
    }

    private static String pcmSha256(int[] samples) throws Exception {
        ByteBuffer bytes = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int sample : samples) bytes.putShort((short) sample);
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(bytes.array()));
    }

    private static String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String rehash(JsonNode document) throws Exception {
        ObjectNode body = (ObjectNode) document.path("body");
        ArrayNode writes = (ArrayNode) body.path("writes");
        StringBuilder ordered = new StringBuilder();
        for (JsonNode write : writes) {
            ordered.append(JSON.writeValueAsString(write)).append('\n');
        }
        ObjectNode terminal = (ObjectNode) body.path("terminal");
        terminal.put("row_count", writes.size());
        terminal.put("ordered_rows_sha256", sha256(ordered.toString()));
        ((ObjectNode) document).put("body_sha256", sha256(JSON.writeValueAsString(body)));
        return JSON.writeValueAsString(document) + "\n";
    }
}
