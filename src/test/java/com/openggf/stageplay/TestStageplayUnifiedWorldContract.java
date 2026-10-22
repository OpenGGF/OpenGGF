package com.openggf.stageplay;

import static com.openggf.stageplay.StageplayWorldPolicies.usesNativeWorld;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayUnifiedWorldContract {
    @Test void bothCurrentPoliciesAcceptTheSameMixedDonorCameraAndIntentContract() {
        for (var policy : List.of(WorldPolicy.TERRAIN_ONLY, WorldPolicy.LIVE)) {
            var scene = scene(policy, "sets/scene.ogfproject");
            assertEquals(VERSION, scene.version());
            assertTrue(usesNativeWorld(scene.set().worldPolicy()));
            assertEquals(448, scene.height());
            assertEquals(.75, scene.camera().getFirst().zoom());
            assertEquals(PuppetCommandKind.FLY_TO, scene.puppets().getFirst().commands().getFirst().kind());
        }
    }

    @Test void versionFiveSourcesRetainTheirExecutionPoliciesAndLimits() throws Exception {
        var legacy = new StageplayDocument(5, "Old", 120, 320, 224,
                new SetSource("s2", 0, 0, "", "", WorldPolicy.TERRAIN_ONLY), List.of(),
                List.of(new CameraKey(0, 0, 500, 1, Interpolation.HOLD)), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertEquals(WorldPolicy.LEGACY_TERRAIN_ONLY, legacy.set().worldPolicy());
        var json = new ObjectMapper().readTree(StageplayStore.encode(legacy));
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).put("version", 5);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove(List.of("objectActors", "worldCommands"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.get("set")).put("worldPolicy", "TERRAIN_ONLY");
        assertEquals(legacy, StageplayStore.decode(new ObjectMapper().writeValueAsBytes(json)));
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.get("set")).put("worldPolicy", "LEGACY_TERRAIN_ONLY");
        assertThrows(java.io.IOException.class, () -> StageplayStore.decode(new ObjectMapper().writeValueAsBytes(json)));

        var live = new SetSource("s2", 0, 0, "", "", WorldPolicy.LIVE);
        assertThrows(IllegalArgumentException.class, () -> new StageplayDocument(5, "Old", 120, 640, 448,
                live, List.of(), List.of(new CameraKey(0, 0, 500, 1, Interpolation.HOLD)), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new StageplayDocument(5, "Old", 120, 320, 224,
                live, List.of(), List.of(new CameraKey(0, 0, 500, 2, Interpolation.HOLD)), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    @Test void currentDocumentRoundTripPreservesLegacyAndNativePoliciesAndRejectsUnknownFields() throws Exception {
        var scene = scene(WorldPolicy.LIVE, "");
        assertEquals(scene, StageplayStore.decode(StageplayStore.encode(scene)));
        var mapper = new ObjectMapper();
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(StageplayStore.encode(scene));
        tree.put("unknown", true);
        assertThrows(java.io.IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(tree)));
        assertEquals(WorldPolicy.LEGACY_TERRAIN_ONLY, new SetSource("s2", 0, 0, "", "").worldPolicy(),
                "Existing compiled five-argument callers retain detached semantics");
        assertEquals(WorldPolicy.LIVE, StageplayDocument.empty("s2", 0, 0, 0, 500, 640, 448).set().worldPolicy());
    }

    private static StageplayDocument scene(WorldPolicy policy, String project) {
        return new StageplayDocument(VERSION, "Unified", 120, 640, 448,
                new SetSource("s2", 0, 0, "", project, policy),
                List.of(new Actor("donor", "Donor", "s3k", "tails", List.of(
                        new ActorKey(0, 96, 655, 0, -1, false, true, 1, Interpolation.HOLD)))),
                List.of(new CameraKey(0, 0, 500, .75, Interpolation.LINEAR,
                        new CameraFollow(List.of("donor"), 0, 0, 0, 1, 0, 0))), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(new PuppetTrack("donor", true, 0, List.of(
                        new PuppetCommand("fly", 0, PuppetCommandKind.FLY_TO, 200, 600, .5, 0)))));
    }
}
