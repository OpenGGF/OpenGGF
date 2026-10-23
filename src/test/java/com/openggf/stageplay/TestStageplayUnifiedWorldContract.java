package com.openggf.stageplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayUnifiedWorldContract {
    @Test void nativeWorldAcceptsMixedDonorCameraAndIntentContract() {
        var scene = scene("sets/scene.ogfproject");
        assertEquals(VERSION, scene.version());
        assertEquals(448, scene.height());
        assertEquals(.75, scene.camera().getFirst().zoom());
        assertEquals(PuppetCommandKind.FLY_TO, scene.puppets().getFirst().commands().getFirst().kind());
    }

    @Test void clearingObjectsIsAnOrdinaryWorldCommand() throws Exception {
        var scene = scene("");
        var cleared = scene.withWorldCommands(List.of(new WorldCommand("clear", 0,
                WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none())));
        assertEquals(scene.set(), cleared.set());
        assertEquals(scene.camera(), cleared.camera());
        assertEquals(scene.puppets(), cleared.puppets());
        assertEquals(cleared, StageplayStore.decode(StageplayStore.encode(cleared)));
    }

    @Test void currentDocumentRoundTripRejectsUnknownFields() throws Exception {
        var scene = scene("");
        assertEquals(scene, StageplayStore.decode(StageplayStore.encode(scene)));
        var mapper = new ObjectMapper();
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(StageplayStore.encode(scene));
        assertFalse(tree.get("set").has("worldPolicy"));
        tree.put("unknown", true);
        assertThrows(java.io.IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(tree)));
    }

    private static StageplayDocument scene(String project) {
        return new StageplayDocument(VERSION, "Unified", 120, 640, 448,
                new SetSource("s2", 0, 0, "", project),
                List.of(new Actor("donor", "Donor", "s3k", "tails", List.of(
                        new ActorKey(0, 96, 655, 0, -1, false, true, 1, Interpolation.HOLD)))),
                List.of(new CameraKey(0, 0, 500, .75, Interpolation.LINEAR,
                        new CameraFollow(List.of("donor"), 0, 0, 0, 1, 0, 0))), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(new PuppetTrack("donor", true, 0, List.of(
                        new PuppetCommand("fly", 0, PuppetCommandKind.FLY_TO, 200, 600, .5, 0)))));
    }
}
