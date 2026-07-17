package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzVisualEvidenceToolingContract {

    @Test
    void amendmentPreservesSupersededEvidenceAndVersionedReplacementPaths() throws Exception {
        JsonNode amendment = new ObjectMapper().readTree(Path.of(
                "docs/s3k-zones/fbz-visual-evidence-amendment-proposal.json").toFile());
        assertEquals("rejected-superseded",
                amendment.path("policy").path("current_evidence_status").asText());
        assertEquals("refs/fbz/fbz1-start-outdoor-gameplay-v2-320x224.png",
                amendment.path("proposed_exact_start_replacement")
                        .path("proposed_reference_path").asText());
        assertTrue(amendment.path("superseded_exact_start").path("files").toString()
                .contains("9572942942CC4A385397A942802B2EECCCB6B55BEC067C194D4D7C0909DC0569"));
    }

    @Test
    void emulatorAndValidatorSourcesContainFailClosedVisibilityAndSupersessionGates()
            throws Exception {
        String lua = Files.readString(Path.of("tools/bizhawk/capture_fbz_visual_references.lua"));
        for (String token : new String[]{
                "camera_x_copy", "camera_target_min_x", "anim_counters",
                "title_card_active", "palette_fade_timer", "game_mode",
                "OVERLAYS_DISABLED", "zero-step", "one-step", "vram_sha256"}) {
            assertTrue(lua.contains(token), "missing BizHawk evidence token: " + token);
        }

        String validator = Files.readString(Path.of(
                "tools/validation/Validate-FbzVisualCheckpoints.ps1"));
        for (String token : new String[]{
                "SUPERSEDED", "superseded_exact_start", "superseded_aniplc_series",
                "gameplay-v2", "-v2", "Get-FileHash", "natural_expiry_observed",
                "timer_before", "frame_before", "frame_after", "bk2_frame",
                "not consecutive"}) {
            assertTrue(validator.contains(token), "missing validator policy token: " + token);
        }

        String hostFinalizer = Files.readString(Path.of(
                "tools/validation/Capture-FbzBizHawkReferences.ps1"));
        for (String token : new String[]{
                "export-summary-v2.json", "fbz1-start-outdoor-gameplay-v2",
                "aniplc-cadence-200-v2", "crop_sha256", "Get-FileHash"}) {
            assertTrue(hostFinalizer.contains(token),
                    "missing v2 host-finalizer token: " + token);
        }
    }

    @Test
    void validatorExcludesHistoricalSupersededSeriesFromActiveFailureAccounting()
            throws Exception {
        String validator = Files.readString(Path.of(
                "tools/validation/Validate-FbzVisualCheckpoints.ps1"));
        String supersededBlock = between(validator,
                "$supersededAniPlc =",
                "foreach ($name in $activeAniPlcSeries)");

        assertFalse(supersededBlock.contains("$failCount++"),
                "historical SUPERSEDED rows are inventory evidence, not active failures");
        assertTrue(validator.contains("$expectedActiveGroupCount = 32"),
                "validator must pin the reviewed active-group count");
        assertTrue(validator.contains("Active FBZ visual evidence group count drifted"),
                "validator must fail closed if the active-group inventory changes");
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0, "missing start marker: " + start);
        assertTrue(endIndex > startIndex, "missing end marker: " + end);
        return source.substring(startIndex, endIndex);
    }
}
