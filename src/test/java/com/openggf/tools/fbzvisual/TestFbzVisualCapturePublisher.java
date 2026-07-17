package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzVisualCapturePublisher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void acceptedCapturePublishesImagesAndCompleteReceiptAtomically() throws Exception {
        FbzVisualCapturePaths paths = compatibilityPaths();
        byte[] fullPng = new byte[]{1, 2, 3};
        byte[] cropPng = new byte[]{4, 5, 6};
        FbzVisualCaptureReceipt receipt = acceptedReceipt();

        new FbzVisualCapturePublisher(MAPPER).publishAccepted(
                paths, fullPng, cropPng, receipt);

        assertArrayEquals(fullPng, Files.readAllBytes(paths.fullPng()));
        assertArrayEquals(cropPng, Files.readAllBytes(paths.nativeCropPng()));
        JsonNode json = MAPPER.readTree(paths.receipt().toFile());
        assertEquals("accepted", json.path("status").asText());
        assertEquals("widescreen-400", json.path("mode_key").asText());
        assertEquals("artifact-sha", json.path("provenance").path("built_artifact_sha256").asText());
        assertEquals("rng-state", json.path("provenance").path("rng_state").asText());
        assertFalse(Files.exists(paths.fullPng().resolveSibling(paths.fullPng().getFileName() + ".tmp")));
    }

    @Test
    void rejectionDeletesAllImagesAndEmitsOnlyRejectionReceipt() throws Exception {
        FbzVisualCapturePaths paths = compatibilityPaths();
        Files.createDirectories(paths.fullPng().getParent());
        Files.write(paths.fullPng(), new byte[]{9});
        Files.write(paths.nativeCropPng(), new byte[]{9});

        FbzVisualCaptureReceipt rejected = FbzVisualCaptureReceipt.rejected(
                "fbz1-start-outdoor", "widescreen-400", "camera bounds mismatch",
                requiredProvenance());
        new FbzVisualCapturePublisher(MAPPER).publishRejected(paths, rejected);

        assertFalse(Files.exists(paths.fullPng()));
        assertFalse(Files.exists(paths.nativeCropPng()));
        JsonNode json = MAPPER.readTree(paths.receipt().toFile());
        assertEquals("rejected", json.path("status").asText());
        assertEquals("camera bounds mismatch", json.path("rejection_reason").asText());
    }

    @Test
    void receiptRejectsMissingPrebootProvenanceBeforeAnyFileIsWritten() {
        Map<String, Object> incomplete = Map.of("manifest_sha256", "manifest-sha");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> FbzVisualCaptureReceipt.rejected(
                        "fbz1-start-outdoor", "native-320", "failure", incomplete));
        assertTrue(failure.getMessage().contains("provenance"));
    }

    private FbzVisualCapturePaths compatibilityPaths() {
        return FbzVisualCaptureMode.resolve("widescreen-400", 400, 224, 40)
                .paths(tempDir, "fbz1-start-outdoor", tempDir.resolve("native.png"));
    }

    private static FbzVisualCaptureReceipt acceptedReceipt() {
        return FbzVisualCaptureReceipt.accepted(
                "fbz1-start-outdoor",
                "widescreen-400",
                requiredProvenance(),
                Map.of("player_x", 96, "player_y", 1900),
                Map.of("player_x", 96, "player_y", 1900),
                "full-sha",
                "crop-sha");
    }

    private static Map<String, Object> requiredProvenance() {
        return Map.ofEntries(
                Map.entry("commit", "abc123"),
                Map.entry("dirty_worktree_sha256", "dirty-sha"),
                Map.entry("built_artifact_sha256", "artifact-sha"),
                Map.entry("effective_config_sha256", "config-sha"),
                Map.entry("rom_sha1", "rom-sha"),
                Map.entry("manifest_sha256", "manifest-sha"),
                Map.entry("input_schedule_sha256", "input-sha"),
                Map.entry("input_schedule_source", "movie.bk2"),
                Map.entry("savestate_sha256", "state-sha"),
                Map.entry("savestate_source", "state.State"),
                Map.entry("rng_seed", 0x1234),
                Map.entry("rng_state", "rng-state"),
                Map.entry("preboot_verified", true));
    }
}
