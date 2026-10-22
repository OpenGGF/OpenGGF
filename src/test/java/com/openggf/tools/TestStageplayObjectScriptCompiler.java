package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayObjectScriptCompiler {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCRIPT = """
            {"version":6,"name":"Complete cast","cast":[],
             "objectCast":[
              {"id":"crab","name":"Crab","gameId":"s2","objectId":146},
              {"id":"loaded","name":"Loaded","gameId":"s2","objectId":146,"subtype":3,"layoutIndex":5}],
             "shots":[
              {"id":"opening","durationFrames":60,"set":{"gameId":"s2","zone":0,"act":0},
               "camera":[{"frame":0,"x":0,"y":500}],
               "objects":{"crab":[{"frame":0,"x":100,"y":655},
                  {"frame":10,"x":100,"y":655,"mode":"PUPPET"},
                  {"frame":30,"x":130,"y":655,"mode":"POSE","animation":2,"pose":3,
                   "parts":[{"partId":"root/0","animation":1,"pose":4}]},
                  {"frame":50,"x":100,"y":655,"mode":"FREE"}],
                 "loaded":[{"frame":0,"x":160,"y":655,"mode":"POSE","pose":2}]},
               "objectActions":{"crab":[{"frame":10,"kind":"MOVE_TO","x":130,"y":655},
                  {"id":"halt","frame":20,"kind":"STOP"}]},
               "worldCommands":[{"frame":1,"kind":"CLEAR_BADNIKS"},
                  {"frame":40,"kind":"REMOVE_OBJECT","target":{"layoutIndex":7,"objectId":146,"subtype":3}}]},
              {"id":"outro","durationFrames":40,"set":{"gameId":"s1","zone":0,"act":0},
               "camera":[{"frame":0,"x":0,"y":500}],
               "objects":{"crab":[{"frame":0,"x":150,"y":600,"mode":"PUPPET"}]},
               "objectActions":{"crab":[{"frame":0,"kind":"MOVE_TO","x":180,"y":600,"speed":2.5}]},
               "worldCommands":[{"frame":20,"kind":"REMOVE_OBJECT","actorId":"crab"},
                  {"frame":30,"kind":"CLEAR_OBJECTS"}]}
             ]}
            """;

    private static ObjectNode script() throws IOException { return (ObjectNode) JSON.readTree(SCRIPT); }
    private static ObjectNode shot(ObjectNode script, int index) { return (ObjectNode) script.path("shots").get(index); }
    private static ObjectNode objectKey(ObjectNode script, int index) {
        return (ObjectNode) shot(script, 0).path("objects").path("crab").get(index);
    }
    private static StageplayDocument compile(ObjectNode script) throws IOException {
        return StageplayScriptCompiler.compile(JSON.writeValueAsBytes(script));
    }

    @Test void compilesAllModesPartOverridesPortableTargetsAndShotLocalCommands() throws Exception {
        var document = compile(script());
        assertEquals(VERSION, document.version()); assertEquals(100, document.durationFrames());
        var crab = document.objectActors().getFirst();
        assertEquals(-1, crab.layoutIndex()); assertEquals(0, crab.zone()); assertEquals(0, crab.act());
        assertEquals(List.of(0, 10, 30, 50, 60), crab.keys().stream().map(ObjectKey::frame).toList());
        assertEquals(List.of(ObjectMode.FREE, ObjectMode.PUPPET, ObjectMode.POSE, ObjectMode.FREE, ObjectMode.PUPPET),
                crab.keys().stream().map(ObjectKey::mode).toList());
        assertEquals(Interpolation.HOLD, crab.keys().getFirst().interpolation());
        assertEquals(Interpolation.LINEAR, crab.keys().get(1).interpolation());
        assertEquals(List.of(new ObjectPartPose("root/0", 1, 4)), crab.keys().get(2).parts());
        assertEquals(List.of(10, 20, 60), crab.commands().stream().map(ObjectCommand::frame).toList());
        assertEquals("opening.crab.objectAction0", crab.commands().getFirst().id());
        assertEquals("halt", crab.commands().get(1).id());
        assertEquals(1, crab.commands().getFirst().speed()); assertEquals(2.5, crab.commands().getLast().speed());
        var loaded = document.objectActors().getLast();
        assertEquals(5, loaded.layoutIndex()); assertEquals(146, loaded.objectId()); assertEquals(3, loaded.subtype());
        assertFalse(loaded.keys().getLast().visible(), "A missing per-shot role is explicitly hidden");
        assertEquals(60, loaded.keys().getLast().frame()); assertEquals(160, loaded.keys().getLast().x());
        assertEquals(List.of(1, 40, 80, 90), document.worldCommands().stream().map(WorldCommand::frame).toList());
        assertEquals(new ObjectTarget(7, 146, 3, 0, 0), document.worldCommands().get(1).target());
        assertEquals("crab", document.worldCommands().get(2).actorId());
        assertEquals(document, StageplayStore.decode(StageplayStore.encode(document)));
    }

    @Test void rejectsUnknownCoercedModeAndPlacementFieldsAndActionsOutsidePuppetMode() throws Exception {
        List<Consumer<ObjectNode>> edits = List.of(
                root -> ((ObjectNode) root.path("objectCast").get(0)).put("runtimeSlot", 3),
                root -> ((ObjectNode) root.path("objectCast").get(0)).put("objectId", "146"),
                root -> ((ObjectNode) root.path("objectCast").get(0)).remove("objectId"),
                root -> objectKey(root, 0).put("mode", "DIRECTED"),
                root -> objectKey(root, 0).put("pose", 3),
                root -> objectKey(root, 0).put("visible", "true"),
                root -> objectKey(root, 1).put("frame", 0),
                root -> objectKey(root, 1).put("frame", 60),
                root -> objectKey(root, 2).put("slot", 5),
                root -> ((ObjectNode) objectKey(root, 2).path("parts").get(0)).put("routine", 2),
                root -> objectKey(root, 3).put("x", 101),
                root -> ((ObjectNode) shot(root, 0).path("objectActions").path("crab").get(0)).put("frame", 1),
                root -> ((ObjectNode) shot(root, 0).path("objectActions").path("crab").get(0)).put("urgency", 0.5),
                root -> ((ObjectNode) shot(root, 0).path("objectActions").path("crab").get(0)).put("speed", 0),
                root -> ((ObjectNode) shot(root, 0).path("objectActions").path("crab").get(0)).remove("y"),
                root -> ((ObjectNode) shot(root, 0).path("objectActions")).putArray("unknown"),
                root -> ((ObjectNode) shot(root, 0).path("objects")).putArray("unknown"),
                root -> ((ObjectNode) shot(root, 1).path("worldCommands").get(0)).put("actorId", "unknown"),
                root -> ((ObjectNode) shot(root, 0).path("worldCommands").get(1).path("target")).put("offsetX", 1),
                root -> ((ObjectNode) shot(root, 0).path("worldCommands").get(1).path("target")).put("runtimeSlot", 1),
                root -> ((ObjectNode) shot(root, 0).path("worldCommands").get(0)).put("frame", -1),
                root -> ((ObjectNode) shot(root, 0).path("worldCommands").get(0)).put("kind", "CLEAR_RINGS"));
        for (var edit : edits) {
            var source = script(); edit.accept(source);
            assertThrows(IOException.class, () -> compile(source), source.toString());
        }
    }

    @Test void unsupportedScreenplayVersionsRejectAllShapesAndCurrentDefaultsToNative() throws Exception {
        var nativeRecipe = (ObjectNode) JSON.readTree("""
                {"version":6,"name":"Existing native","cast":[],"shots":[
                 {"id":"old","durationFrames":60,"set":{"gameId":"s2","zone":0,"act":0},
                  "camera":[{"frame":0,"x":0,"y":500}]}]}
                """);
        var compiled = compile(nativeRecipe);
        assertEquals(WorldPolicy.LIVE, compiled.set().worldPolicy());
        assertTrue(compiled.objectActors().isEmpty()); assertTrue(compiled.worldCommands().isEmpty());
        for (int version = 1; version <= 4; version++) {
            var old = nativeRecipe.deepCopy(); old.put("version", version);
            var rootField = old.deepCopy(); rootField.putArray("objectCast");
            assertThrows(IOException.class, () -> compile(rootField));
            for (String field : List.of("objects", "objectActions", "worldCommands")) {
                var shotField = old.deepCopy(); shot(shotField, 0).putArray(field);
                assertThrows(IOException.class, () -> compile(shotField));
            }
        }
        assertTrue(StageplayScriptCompiler.compile(StageplayTool.scaffold().getBytes(StandardCharsets.UTF_8)).objectActors().isEmpty());
    }

    @Test void releasedAnimalProviderSourceCompilesWithoutInventingALayoutId() throws Exception {
        var source = script();
        var role = (ObjectNode) source.path("objectCast").get(0);
        role.put("sourceKind", "RELEASED_ANIMAL").put("objectId", 0);
        assertEquals(ObjectSourceKind.RELEASED_ANIMAL, compile(source).objectActors().getFirst().sourceKind());
        role.put("objectId", 0x28);
        assertThrows(IOException.class, () -> compile(source));
    }
}
