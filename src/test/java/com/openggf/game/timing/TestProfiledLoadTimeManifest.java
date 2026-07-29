package com.openggf.game.timing;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestProfiledLoadTimeManifest {

    @Test
    void returnsMeasuredDecisionForExactKindFingerprintAndModel() throws Exception {
        List<String> warnings = new ArrayList<>();
        ProfiledLoadTimeManifest manifest = load("""
                {
                  "formatVersion": 1,
                  "profile": "s3k",
                  "serviceModel": "s3k-kos-v1",
                  "fixtures": ["aiz/run-1"],
                  "entries": [{
                    "kind": "kos_decompression_queue",
                    "submissionFingerprint": "sha256:abc",
                    "serviceFrames": 7,
                    "eligibleBoundaries": ["pre_main_loop"],
                    "sampleCount": 2,
                    "minFrames": 6,
                    "maxFrames": 7,
                    "fixtureIndexes": [0]
                  }]
                }
                """, warnings);

        LoadTimeDecision decision = manifest.assign(
                null,
                new HardwareWorkHandle(
                        HardwareWorkKind.KOS_DECOMPRESSION_QUEUE,
                        4,
                        "sha256:abc"));

        assertEquals(7, decision.serviceFrames());
        assertEquals(LoadTimeDecisionSource.MEASURED, decision.source());
        assertEquals("s3k-kos-v1", decision.serviceModel());
        assertEquals(
                java.util.Set.of(HardwareServiceBoundary.PRE_MAIN_LOOP),
                decision.eligibleBoundaries());
        assertEquals(List.of(), warnings);
    }

    @Test
    void missingFingerprintWarnsOnceAndFallsBackToImmediate() throws Exception {
        List<String> warnings = new ArrayList<>();
        ProfiledLoadTimeManifest manifest = load("""
                {
                  "formatVersion": 1,
                  "profile": "s3k",
                  "serviceModel": "s3k-kos-v1",
                  "fixtures": [],
                  "entries": []
                }
                """, warnings);
        HardwareWorkHandle handle = new HardwareWorkHandle(
                HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, 0, "sha256:missing");

        assertEquals(LoadTimeDecisionSource.IMMEDIATE,
                manifest.assign(null, handle).source());
        assertEquals(LoadTimeDecisionSource.IMMEDIATE,
                manifest.assign(null, handle).source());
        assertEquals(1, warnings.size());
    }

    @Test
    void rejectsDuplicateKeysAndInvalidStatistics() {
        String duplicate = """
                {
                  "formatVersion": 1,
                  "profile": "s3k",
                  "serviceModel": "s3k-kos-v1",
                  "fixtures": [],
                  "entries": [
                    {"kind":"kos_module_queue","submissionFingerprint":"same",
                     "serviceFrames":0,"eligibleBoundaries":[],
                     "sampleCount":1,"minFrames":0,"maxFrames":0,"fixtureIndexes":[]},
                    {"kind":"kos_module_queue","submissionFingerprint":"same",
                     "serviceFrames":0,"eligibleBoundaries":[],
                     "sampleCount":1,"minFrames":0,"maxFrames":0,"fixtureIndexes":[]}
                  ]
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> load(duplicate, new ArrayList<>()));
    }

    private static ProfiledLoadTimeManifest load(
            String json, List<String> warnings) throws Exception {
        return ProfiledLoadTimeManifest.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                warnings::add);
    }
}
