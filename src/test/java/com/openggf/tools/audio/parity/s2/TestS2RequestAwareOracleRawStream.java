package com.openggf.tools.audio.parity.s2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Contract tests for the strictly unbound request-aware raw-v2 reader. */
class TestS2RequestAwareOracleRawStream {

    @TempDir
    Path temporaryDirectory;

    @Test
    void requestAwareReaderExistsAsItsOwnV2Authority() {
        assertDoesNotThrow(() -> Class.forName(
                "com.openggf.tools.audio.parity.s2.S2RequestAwareOracleRawStream"));
    }

    @Test
    void readsTheExactBoundedCandidateAndPreservesObservedTransfers() throws Exception {
        S2RequestAwareOracleRawStream.Result result = read(validPayload());

        assertEquals(750, result.frames().size());
        assertEquals(10_150, result.baseline().row());
        assertEquals(10_149, result.baseline().sourcePrecedingRow());
        S2RequestAwareOracleRawStream.RequestTransfer transfer = result.frames().getFirst()
                .requestTransfers().getFirst();
        assertEquals(10_150, transfer.sourceRow());
        assertEquals(17, transfer.sourceGlobalOrdinal());
        assertEquals(3, transfer.physicalSlot(), "slot three remains a literal shipped slot");
        assertEquals(0x10d6, transfer.pc());
        assertEquals(0x1234, transfer.a7());
        assertEquals(2, result.frames().getFirst().eventRecords().size());
        assertEquals(1, result.frames().getFirst().overrideResumeRecords().size());
        assertEquals(1, result.frames().getFirst().pcmRecords().size());
        assertThrows(UnsupportedOperationException.class,
                () -> result.frames().add(result.frames().getFirst()));
    }

    @Test
    void validatesProducerShapedBoundedClosureWithIndependentlyComputedClaims() throws Exception {
        String payload = boundedProducerPayload();

        S2RequestAwareOracleRawStream.Result result = read(payload);

        assertEquals(750, result.frames().size());
        assertEquals(17, result.frames().getFirst().requestTransfers().getFirst()
                .sourceGlobalOrdinal());
        assertEquals((byte) 1, result.frames().getLast().state()[0]);
        assertEquals(sha256(payload.getBytes(StandardCharsets.UTF_8)), result.rawPayloadSha256());
    }

    @Test
    void rejectsAReusedOrMismatchedNativeMarker() throws Exception {
        String payload = validPayload().replace("\"a7\":\"4660\",\"native_ordinal\":1,\"source_cpu\":2",
                "\"a7\":\"4660\",\"native_ordinal\":2,\"source_cpu\":2");

        assertThrows(IllegalArgumentException.class, () -> read(payload));
    }

    @Test
    void allowsAnArbitraryFirstGlobalOrdinalButRequiresEveryLaterOneToBeContiguous()
            throws Exception {
        String twoTransfers = boundedProducerPayload(true);

        assertDoesNotThrow(() -> read(twoTransfers));
        assertThrows(IllegalArgumentException.class, () -> read(twoTransfers.replace(
                "\"global_transfer_ordinal\":18", "\"global_transfer_ordinal\":20")));
    }

    @Test
    void rejectsEveryExactMarkerShapeMutation() throws Exception {
        for (String mutated : List.of(
                validPayload().replace("\"payload_length\":4,\"value\":3",
                        "\"payload_length\":3,\"value\":3"),
                validPayload().replace("\"offset\":0,\"kind\":10",
                        "\"offset\":1,\"kind\":10"),
                validPayload().replace("\"flags\":0,\"reserved\":0,\"payload\":\"4660\"",
                        "\"flags\":1,\"reserved\":0,\"payload\":\"4660\""),
                validPayload().replace("\"flags\":0,\"reserved\":0,\"payload\":\"4660\"",
                        "\"flags\":0,\"reserved\":1,\"payload\":\"4660\""))) {
            assertThrows(IllegalArgumentException.class, () -> read(mutated));
        }
    }

    @Test
    void rejectsWrongIdentityAndNonJsonlByteForms() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> read(validPayload().replace("\"production_bound\":false",
                        "\"production_bound\":true")));
        assertThrows(IllegalArgumentException.class, () -> read("\ufeff" + validPayload()));
        assertThrows(IllegalArgumentException.class, () -> read(validPayload().replaceFirst("\n", "\r\n")));
        assertThrows(IllegalArgumentException.class, () -> read(validPayload() + "{}\\n"));
        assertThrows(IllegalArgumentException.class,
                () -> read(validPayload().substring(0, validPayload().length() - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> read(validPayload().replaceFirst("\\n",
                        " ".repeat(S2RequestAwareOracleSchema.MAX_LINE_BYTES + 1) + "\n")));
    }

    @Test
    void rejectsUnknownSourceClaimsAndNonCanonicalJsonRecords() throws Exception {
        for (String field : List.of("source_raw_sha256", "source_raw_byte_count",
                "source_capability_sha256")) {
            String payload = validPayload().replace(",\"digest_domains\"",
                    ",\"" + field + "\":\"0\",\"digest_domains\"");
            assertThrows(IllegalArgumentException.class, () -> read(payload));
        }
        assertThrows(IllegalArgumentException.class, () -> read(validPayload().replace(
                "\"type\":\"cutoff\"", "\"type\":\"cutoff\","
                        + "\"source_cutoff_frontier_sha256\":\"0\"")));
        // terminal_state_sha256 is deliberately retained, but is proven against the bounded
        // terminal frame's decoded state by rejectsBoundedClaimsAndOverridePcmMutations.
        assertThrows(IllegalArgumentException.class, () -> read(validPayload().replaceFirst(
                "\"type\":\"metadata\"", "\"type\":\"metadata\",\"type\":\"metadata\"")));
        assertThrows(IllegalArgumentException.class, () -> read(validPayload().replaceFirst(
                "\"production_bound\":false", "\"production_bound\":false,")));
    }

    @Test
    void rejectsV1AtTheV2BoundaryAndV2AtTheExistingV1Boundary() throws Exception {
        Path v2 = temporaryDirectory.resolve("candidate-v2.raw.jsonl");
        Files.writeString(v2, validPayload());
        assertThrows(Exception.class, () -> S2OracleRawStream.scan(v2,
                new S2OracleRawStream.Sink() {
                    @Override public void header(S2OracleRawStream.Header header) { }
                    @Override public void baseline(S2OracleRawStream.Baseline baseline) { }
                    @Override public void frame(S2OracleRawStream.Frame frame) { }
                    @Override public void cutoff(int exclusiveEnd) { }
                }));
        assertThrows(IllegalArgumentException.class, () -> read("{\"type\":\"metadata\",\"schema\":\"openggf.s2-oracle-audio-raw.v1\"}\n"));
    }

    @Test
    void rejectsFixedIdentityAndTransferRangeMutations() throws Exception {
        for (String mutated : List.of(
                validPayload().replace(S2RequestAwareOracleSchema.S2_REV01_SHA1, "0".repeat(40)),
                validPayload().replace("\"inventories\":\"compact-json-lf-v1\"",
                        "\"inventories\":\"wrong-domain\""),
                validPayload().replace("\"source_preceding_row\":10149",
                        "\"source_preceding_row\":10148"),
                validPayload().replace("\"request\":181,\"slot\":3",
                        "\"request\":0,\"slot\":3"),
                validPayload().replace("\"request\":181,\"slot\":3",
                        "\"request\":181,\"slot\":4"),
                validPayload().replace("\"global_transfer_ordinal\":17",
                        "\"global_transfer_ordinal\":-1"),
                validPayload().replace("\"payload\":\"4660\"", "\"payload\":\"4661\""))) {
            assertThrows(IllegalArgumentException.class, () -> read(mutated));
        }
    }

    @Test
    void rejectsBoundedClaimsAndOverridePcmMutations() throws Exception {
        String payload = validPayload();
        for (String field : List.of("frame_count", "base_event_count", "all_event_count",
                "marker_event_count", "request_transfer_count", "override_resume_count",
                "pcm_count", "max_request_occupancy", "body_byte_count")) {
            assertThrows(IllegalArgumentException.class, () -> read(payload.replaceFirst(
                    "\"" + field + "\":[0-9]+", "\"" + field + "\":0")));
        }
        for (String field : List.of("base_event_sha256", "all_event_sha256", "marker_event_sha256",
                "request_transfer_sha256", "override_resume_sha256", "pcm_sha256", "body_sha256",
                "terminal_state_sha256", "payload_before_cutoff_sha256")) {
            assertThrows(IllegalArgumentException.class, () -> read(payload.replaceFirst(
                    "\"" + field + "\":\"[0-9a-f]{64}",
                    "\"" + field + "\":\"" + "0".repeat(64))));
        }
        assertThrows(IllegalArgumentException.class, () -> read(payload.replace(
                "\"fix_driver_bugs\":0", "\"fix_driver_bugs\":1")));
        assertThrows(IllegalArgumentException.class, () -> read(payload.replace(
                "\"selection\":\"service_frame\"", "\"selection\":\"following_row\"")));
        assertThrows(IllegalArgumentException.class, () -> read(payload.replace(
                "\"pcm_hex\":\"00000000\"", "\"pcm_hex\":\"00000001\"")));
    }

    private S2RequestAwareOracleRawStream.Result read(String payload) throws IOException {
        Path input = temporaryDirectory.resolve("candidate.raw.jsonl");
        Files.writeString(input, payload);
        return S2RequestAwareOracleRawStream.scanCandidateForTesting(input);
    }

    private static String validPayload() {
        return boundedProducerPayload(false);
    }

    /**
     * A corrected extractor-shaped bounded-v2 document. Every terminal claim is
     * recomputed here from the emitted compact JSON rather than copied from a
     * producer or reader constant.
     */
    private static String boundedProducerPayload() {
        return boundedProducerPayload(false);
    }

    private static String boundedProducerPayload(boolean twoTransfers) {
        String zeroState = "00".repeat(8_192);
        String terminalState = "01" + "00".repeat(8_191);
        String metadata = "{\"type\":\"metadata\",\"schema\":\"openggf.s2-oracle-audio-raw.v2\","
                + "\"rom_sha1\":\"8bca5dcef1af3e00098666fd892dc1c2a76333f9\","
                + "\"bk2_sha256\":\"e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5\","
                + "\"service_manifest_sha256\":\"ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0\","
                + "\"first_row\":10150,\"exclusive_end\":10900,\"state_start\":0,"
                + "\"state_exclusive_end\":8192,\"source_schema\":\"openggf.s2-complete-run-audio-raw.v3\","
                + "\"source_first_row\":769,\"source_exclusive_end\":259590,"
                + "\"request_transfer_schema\":\"openggf.s2-preconsumption-request-transfer.v1\","
                + "\"production_bound\":false,\"digest_domains\":{\"inventories\":\"compact-json-lf-v1\","
                + "\"body\":\"bounded-jsonl-body-bytes-v1\",\"terminal_state\":\"decoded-z80-state-bytes-v1\","
                + "\"payload_before_cutoff\":\"bounded-jsonl-before-cutoff-bytes-v1\"}}";
        String baseline = "{\"type\":\"baseline\",\"row\":10150,\"source_preceding_row\":10149,"
                + "\"state_hex\":\"" + zeroState + "\",\"ym_port0_latch\":0,\"ym_port1_latch\":0}";
        String baseEvent = "{\"ordinal\":0,\"service_token\":0,\"parent_token\":0,\"pc\":0,"
                + "\"subject\":0,\"offset\":0,\"kind\":1,\"service_kind\":0,\"depth\":0,"
                + "\"source_cpu\":2,\"payload_length\":0,\"value\":0,\"flags\":0,\"reserved\":0,\"payload\":\"0\"}";
        String markerEvent = "{\"ordinal\":1,\"service_token\":0,\"parent_token\":0,\"pc\":4310,"
                + "\"subject\":24,\"offset\":0,\"kind\":10,\"service_kind\":0,\"depth\":0,"
                + "\"source_cpu\":2,\"payload_length\":4,\"value\":3,\"flags\":0,\"reserved\":0,\"payload\":\"4660\"}";
        String override = "{\"request\":\"cfFadeInToPrevious\",\"admission\":\"native_service_completion\","
                + "\"request_pc\":3381,\"pc\":3508,\"service_token\":0,\"service_begin_ordinal\":0,"
                + "\"native_ordinal\":1,\"frame\":10150,\"fix_driver_bugs\":0,"
                + "\"restores_saved_priority\":true,\"restores_psg_noise\":false,\"writes\":[{\"native_ordinal\":1,"
                + "\"event_kind\":3,\"subject\":1,\"value\":0,\"pc\":3508,\"source_cpu\":2,"
                + "\"data\":true,\"port\":0,\"register\":0}]}";
        String pcm = "{\"selection\":\"service_frame\",\"row\":10150,\"offset\":0,\"sample_rate\":44100,"
                + "\"channels\":2,\"format\":\"s16le-interleaved-stereo\",\"stereo_frames\":1,"
                + "\"byte_count\":4,\"pcm_hex\":\"00000000\",\"sha256\":\"df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119\"}";
        String transfer = "{\"row\":10150,\"order\":0,\"global_transfer_ordinal\":17,\"request\":181,\"slot\":3,"
                + "\"pc\":4310,\"a7\":\"4660\",\"native_ordinal\":1,\"source_cpu\":2,\"service_token\":0,"
                + "\"service_kind\":0,\"depth\":0,\"active_service_owner\":{\"token\":0,\"kind\":0,\"depth\":0}}";
        String secondMarker = "{\"ordinal\":0,\"service_token\":0,\"parent_token\":0,\"pc\":4310,"
                + "\"subject\":24,\"offset\":0,\"kind\":10,\"service_kind\":0,\"depth\":0,"
                + "\"source_cpu\":2,\"payload_length\":4,\"value\":3,\"flags\":0,\"reserved\":0,\"payload\":\"4661\"}";
        String secondTransfer = "{\"row\":10151,\"order\":0,\"global_transfer_ordinal\":18,\"request\":181,\"slot\":2,"
                + "\"pc\":4310,\"a7\":\"4661\",\"native_ordinal\":0,\"source_cpu\":2,\"service_token\":0,"
                + "\"service_kind\":0,\"depth\":0,\"active_service_owner\":{\"token\":0,\"kind\":0,\"depth\":0}}";
        List<String> frames = new ArrayList<>();
        for (int row = 10_150; row < 10_900; row++) {
            String state = row == 10_899 ? terminalState : zeroState;
            if (row == 10_150) {
                frames.add("{\"type\":\"frame\",\"row\":10150,\"lag\":false,\"state_hex\":\""
                        + state + "\",\"events\":[" + baseEvent + ',' + markerEvent + "],\"override_resume\":"
                        + override + ",\"pcm\":" + pcm + ",\"request_transfers\":[" + transfer + "]}");
            } else if (twoTransfers && row == 10_151) {
                frames.add("{\"type\":\"frame\",\"row\":10151,\"lag\":false,\"state_hex\":\""
                        + state + "\",\"events\":[" + secondMarker + "],\"override_resume\":null,\"pcm\":null,"
                        + "\"request_transfers\":[" + secondTransfer + "]}");
            } else {
                frames.add("{\"type\":\"frame\",\"row\":" + row + ",\"lag\":false,\"state_hex\":\""
                        + state + "\",\"events\":[],\"override_resume\":null,\"pcm\":null,\"request_transfers\":[]}");
            }
        }
        byte[] body = bytes(baseline + '\n' + String.join("\n", frames) + '\n');
        String allEvents = baseEvent + '\n' + markerEvent + '\n'
                + (twoTransfers ? secondMarker + '\n' : "");
        String markers = markerEvent + '\n' + (twoTransfers ? secondMarker + '\n' : "");
        String transfers = transfer + '\n' + (twoTransfers ? secondTransfer + '\n' : "");
        int markerCount = twoTransfers ? 2 : 1;
        String cutoff = "{\"type\":\"cutoff\",\"exclusive_end\":10900,\"frame_count\":750,"
                + "\"base_event_count\":1,\"all_event_count\":" + (1 + markerCount)
                + ",\"marker_event_count\":" + markerCount + ","
                + "\"request_transfer_count\":" + markerCount
                + ",\"override_resume_count\":1,\"pcm_count\":1,"
                + "\"max_request_occupancy\":1,\"base_event_sha256\":\"" + sha256(bytes(baseEvent + '\n'))
                + "\",\"all_event_sha256\":\"" + sha256(bytes(allEvents))
                + "\",\"marker_event_sha256\":\"" + sha256(bytes(markers))
                + "\",\"request_transfer_sha256\":\"" + sha256(bytes(transfers))
                + "\",\"override_resume_sha256\":\"" + sha256(bytes(override + '\n'))
                + "\",\"pcm_sha256\":\"" + sha256(bytes(pcm + '\n'))
                + "\",\"body_byte_count\":" + body.length + ",\"body_sha256\":\"" + sha256(body)
                + "\",\"terminal_state_sha256\":\"" + sha256(hexBytes(terminalState))
                + "\",\"payload_before_cutoff_sha256\":\"" + sha256(bytes(metadata + '\n' + new String(body, StandardCharsets.UTF_8)))
                + "\"}";
        return metadata + '\n' + new String(body, StandardCharsets.UTF_8) + cutoff + '\n';
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hexBytes(String value) {
        return HexFormat.of().parseHex(value);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
