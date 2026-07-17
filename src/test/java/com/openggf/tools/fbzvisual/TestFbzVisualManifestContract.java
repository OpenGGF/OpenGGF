package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzVisualManifestContract {

    private static final Path MANIFEST = Path.of("docs/s3k-zones/fbz-visual-checkpoints.json");
    private static final String MANIFEST_SHA256 =
            "D13D037BAF52BBD65D28096A71A54ACACB4229B8C4C560C76DCB921E90DC40DD";

    @Test
    void reviewedManifestHashAndBranchCoordinatesRemainFrozen() throws Exception {
        byte[] bytes = Files.readAllBytes(MANIFEST);
        assertEquals(MANIFEST_SHA256, java.util.HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));

        JsonNode root = new ObjectMapper().readTree(bytes);
        JsonNode recipes = root.path("setup_recipes");
        assertEquals(2815, recipes.path("fbz1-boundary-1-outdoor").path("centre").path("x").asInt());
        assertEquals(3199, recipes.path("fbz1-boundary-2-outdoor").path("centre").path("x").asInt());
        assertEquals(6271, recipes.path("fbz1-boundary-3-outdoor").path("centre").path("x").asInt());
        assertEquals(255, recipes.path("fbz1-boundary-4-horizontal").path("centre").path("y").asInt());

        JsonNode exitState = recipes.path("fbz2-exit").path("state");
        assertEquals(12, exitState.path("Events_routine_fg").asInt());
        assertEquals(16, exitState.path("Events_routine_bg").asInt());
        assertEquals("FG=B,BG=A", exitState.path("plane_assignment").asText());
    }

    @Test
    void everyReviewedNativeCheckpointHasAnExplicitFailClosedExecutor() throws Exception {
        FbzVisualManifest manifest = FbzVisualManifest.load(MANIFEST, MANIFEST_SHA256);
        FbzVisualScenarioDriver driver = new FbzVisualScenarioDriver(manifest);

        assertEquals(21, driver.plans().size());
        assertTrue(driver.plans().values().stream()
                .allMatch(FbzVisualScenarioDriver.ScenarioPlan::captureSupported));
        assertEquals("act1-miniboss-active", driver.plan("fbz1-miniboss").strategy());
        assertEquals("act2-subboss-active", driver.plan("fbz2-subboss").strategy());
        assertEquals("act2-end-boss-active", driver.plan("fbz2-end-boss").strategy());
        assertEquals("act2-exit-ready", driver.plan("fbz2-exit").strategy());
        assertEquals("act2-final-capsule", driver.plan("fbz2-capsule").strategy());
        assertEquals(0x2E20, driver.plan("fbz1-miniboss")
                .fixtureMutation().writes().get("camera_min_x"));
        assertEquals(0x2EA0, driver.plan("fbz1-miniboss")
                .fixtureMutation().writes().get("camera_max_x"));
        assertEquals(true, driver.plan("fbz2-end-boss").fixtureMutation().writes()
                .get("boss_load_position_adjustment_pending"));
        assertEquals(true, driver.plan("fbz2-exit").fixtureMutation().writes()
                .get("boss_defeated"));
        assertEquals(0x720, driver.plan("fbz2-capsule")
                .fixtureMutation().writes().get("camera_y"));
        var cadenceWrites = driver.plan("fbz1-aniplc-210").fixtureMutation().writes();
        assertFalse(cadenceWrites.containsKey("aniplc_timer_0"));
        assertFalse(cadenceWrites.containsKey("aniplc_frame_0"));
        assertFalse(cadenceWrites.containsKey("level_frame_counter"));
    }
}
