package com.openggf.recording;

import com.openggf.version.BuildIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestUserRecordingManifestJson {
    @Test
    void manifestJsonRoundtripsMetadataAndToleratesReservedSidecarInterval() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T14:30:22Z");
        UserRecordingManifest manifest = new UserRecordingManifest(
                UserRecordingManifest.CURRENT_SCHEMA_VERSION,
                "s3k-aiz1-test",
                new BuildIdentity("0.6.prerelease", "abcdef123", false),
                new RecordingLaunchContext(
                        "s3k",
                        0,
                        1,
                        "sonic",
                        List.of("tails", "knuckles"),
                        true,
                        "current-act-fresh-start"),
                UserRecordingSidecarMetadata.everyFrame(),
                new RecordingDeterminismMetadata(42, 123456789L),
                "A",
                600,
                UserRecordingStopReason.USER_STOPPED,
                createdAt);

        String json = UserRecordingJson.writeManifest(manifest);
        UserRecordingManifest roundtripped = UserRecordingJson.readManifest(json);

        assertEquals("A", roundtripped.jumpActionButton());
        assertEquals(List.of("tails", "knuckles"), roundtripped.launchContext().sidekickCharacters());
        assertEquals(UserRecordingSidecarMetadata.CURRENT_DESYNC_LITE_SCHEMA_VERSION,
                roundtripped.sidecar().desyncLiteSchemaVersion());
        assertEquals("every-frame", roundtripped.sidecar().sampleMode());
        assertNull(roundtripped.sidecar().sampleInterval());
        assertEquals(createdAt, roundtripped.createdAt());

        String fixtureWithReservedInterval = json.replace(
                "\"sampleInterval\" : null",
                "\"sampleInterval\" : 30");
        UserRecordingManifest withReservedInterval = UserRecordingJson.readManifest(fixtureWithReservedInterval);

        assertEquals(30, withReservedInterval.sidecar().sampleInterval());
        assertEquals(createdAt, withReservedInterval.createdAt());
    }
}
