package com.openggf.tools.audio.completerun.s3k;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS3kCompleteRunReferencePreflight {
    @TempDir Path temporary;

    @Test
    void provesCanonicalStateConstructionWithoutClaimingEventOrPublicationAuthority() throws Exception {
        Path raw = temporary.resolve("raw.jsonl");
        String state = "00".repeat(1024);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":810,", state)
                + "{\"type\":\"frame\",\"row\":810,\"lag\":true,\"state_hex\":\"" + state
                + "\",\"events\":[{" + event() + "}]}\n"
                + boundary("cutoff", "\"exclusive_end\":811,", state));

        var result = S3kCompleteRunReferencePreflight.preflightPrefixForTesting(
                raw, TestS3kCompleteRunReferencePreflight::state);

        assertEquals(810, result.baseline().absoluteFrame());
        assertEquals(1, result.frameRows());
        assertEquals(1, result.lagRows());
        assertEquals(1, result.rawEvents());
        assertEquals(1, result.rawEventKinds().get(6));
        assertEquals(1, result.segmentRows().get("aiz"));
        assertEquals(0, result.gapRows());
        assertEquals(0, result.cutoffActiveServices());
        assertEquals(0, result.cutoffPendingDescendants());
        assertFalse(result.canonicalRecordsReady());
        assertEquals(List.of(
                S3kCompleteRunReferencePreflight.Dependency.RAW_EVENT_SEMANTICS,
                S3kCompleteRunReferencePreflight.Dependency.CUTOFF_SERVICE_COORDINATES,
                S3kCompleteRunReferencePreflight.Dependency.REFERENCE_RUNTIME_AUTHORITY,
                S3kCompleteRunReferencePreflight.Dependency.RUN_LOCAL_BK2), result.dependencies());
    }

    @Test
    void abortsTheWholePreflightWhenAnyLateStateCannotNormalize() throws Exception {
        Path raw = temporary.resolve("late-state.jsonl");
        String state = "00".repeat(1024);
        Files.writeString(raw, metadata()
                + boundary("baseline", "\"row\":810,", state)
                + "{\"type\":\"frame\",\"row\":810,\"lag\":false,\"state_hex\":\"" + state
                + "\",\"events\":[]}\n"
                + boundary("cutoff", "\"exclusive_end\":811,", state));
        int[] calls = {0};

        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunReferencePreflight.preflightPrefixForTesting(raw, bytes -> {
                    if (++calls[0] == 3) throw new IllegalArgumentException("late decoder failure");
                    return state(bytes);
                }));
    }

    @Test
    void mapsThePinnedManifestSegmentsAndRetainedGapsExactly() {
        assertEquals("aiz", S3kCompleteRunReferencePreflight.segmentAtForTesting(810));
        assertEquals("aiz", S3kCompleteRunReferencePreflight.segmentAtForTesting(2462));
        assertEquals(null, S3kCompleteRunReferencePreflight.segmentAtForTesting(2463));
        assertEquals("ss", S3kCompleteRunReferencePreflight.segmentAtForTesting(2464));
        assertEquals(null, S3kCompleteRunReferencePreflight.segmentAtForTesting(433942));
    }

    private static CompleteRunAudioTrace.NormalizedState state(byte[] ignored) {
        List<CompleteRunAudioTrace.StateField> fields = new ArrayList<>();
        for (String name : S3kCompleteRunStateNormalizer.GLOBAL_FIELDS) {
            fields.add(new CompleteRunAudioTrace.StateField(name, 0));
        }
        List<CompleteRunAudioTrace.RoleState> roles = S3kCompleteRunAudioProfile.profile()
                .hardwareRoles().stream()
                .map(role -> new CompleteRunAudioTrace.RoleState(role, false, List.of()))
                .toList();
        return new CompleteRunAudioTrace.NormalizedState(fields, roles);
    }

    private static String metadata() {
        return "{\"type\":\"metadata\",\"schema\":\"openggf.s3k-complete-run-audio-raw.v1\","
                + "\"rom_sha1\":\"cfbf98c36c776677290a872547ac47c53d2761d6\","
                + "\"bk2_sha256\":\"aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc\","
                + "\"service_manifest_sha256\":\"ef8f8103c38d70e41cb09cb29751f56815a0401709dc509071aa514d614813a0\","
                + "\"first_row\":810,\"exclusive_end\":434417,\"state_start\":7168,"
                + "\"state_exclusive_end\":8192}\n";
    }

    private static String boundary(String type, String coordinate, String state) {
        return "{\"type\":\"" + type + "\"," + coordinate + "\"state_hex\":\"" + state
                + "\",\"ym_port0_latch\":40,\"ym_port1_latch\":161,"
                + "\"native_arm_epoch\":1,\"native_armed\":true,"
                + "\"active_services\":[],\"pending_descendants\":[]}\n";
    }

    private static String event() {
        return "\"ordinal\":0,\"service_token\":2,\"parent_token\":0,\"pc\":4660,"
                + "\"subject\":1,\"offset\":0,\"kind\":6,\"service_kind\":2,"
                + "\"depth\":1,\"source_cpu\":1,\"payload_length\":8,\"value\":0,"
                + "\"flags\":0,\"reserved\":0,\"payload\":\"18446744073709551615\"";
    }
}
