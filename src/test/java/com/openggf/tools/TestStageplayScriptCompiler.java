package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayScriptCompiler {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCREENPLAY = """
            {"version":1,"name":"Three locations","width":320,"height":224,
             "cast":[{"id":"hero","name":"Hero","gameId":"s2","character":"sonic"},
                     {"id":"friend","name":"Friend","gameId":"s2","character":"tails"}],
             "shots":[
              {"id":"opening","durationFrames":30,"set":{"gameId":"s2","zone":0,"act":0},
               "camera":[{"frame":0,"x":0,"y":0},{"frame":20,"x":100,"y":0}],
               "actors":{"hero":[{"frame":0,"x":10,"y":200,"animation":1},
                                   {"frame":20,"x":30,"y":200,"animation":1}]}},
              {"id":"middle","durationFrames":40,"set":{"gameId":"s3k","zone":0,"act":1},
               "transition":{"kind":"FADE_BLACK","durationFrames":10},
               "camera":[{"frame":0,"x":1000,"y":200},{"frame":20,"x":1200,"y":200,"zoom":2}],
               "actors":{"friend":[{"frame":0,"x":900,"y":300,"animation":5},
                                     {"frame":20,"x":940,"y":300,"animation":5}]},
               "captions":[{"id":"line","startFrame":2,"endFrame":8,"text":"Next location"}],
               "audio":[{"frame":3,"kind":"ROM_SFX","gameId":"s3k","soundId":45}],
               "cameraEffects":[{"startFrame":5,"endFrame":10,"amplitudeX":6,"amplitudeY":2,"frequency":9,"seed":13}]},
              {"id":"outro","durationFrames":20,"set":{"gameId":"s1","zone":0,"act":0},
               "camera":[{"frame":0,"x":3000,"y":0}],
               "actors":{"hero":[{"frame":0,"x":3000,"y":200,"animation":1}]}}
             ]}
            """;

    private static ObjectNode recipe() throws IOException { return (ObjectNode) JSON.readTree(SCREENPLAY); }
    private static ObjectNode shot(ObjectNode script, int index) { return (ObjectNode) script.path("shots").get(index); }
    private static StageplayDocument compile(ObjectNode script) throws IOException {
        return StageplayScriptCompiler.compile(JSON.writeValueAsBytes(script));
    }
    private static void rejects(Consumer<ObjectNode> edit) throws IOException {
        var script = recipe(); edit.accept(script);
        assertThrows(IOException.class, () -> compile(script), script.toString());
    }

    @Test void multiShotCompilationOffsetsEveryTrackAndSeedsHiddenCastAtEveryBoundary() throws Exception {
        var document = compile(recipe());
        assertEquals(2, document.version());
        assertEquals(90, document.durationFrames());
        assertEquals(List.of(0, 20, 30, 50, 70), document.camera().stream().map(StageplayDocument.CameraKey::frame).toList());
        assertEquals(List.of(30, 70), document.sceneChanges().stream().map(StageplayDocument.SceneChange::frame).toList());
        assertEquals("middle.change", document.sceneChanges().getFirst().id());
        assertEquals(StageplayDocument.Transition.FADE_BLACK, document.sceneChanges().getFirst().transition());
        assertEquals(StageplayDocument.Transition.CUT, document.sceneChanges().getLast().transition());
        var hero = document.actors().getFirst();
        var friend = document.actors().getLast();
        assertEquals(List.of(0, 20, 30, 70), hero.keys().stream().map(StageplayDocument.ActorKey::frame).toList());
        assertEquals(List.of(0, 30, 50, 70), friend.keys().stream().map(StageplayDocument.ActorKey::frame).toList());
        assertFalse(friend.keys().getFirst().visible(), "an initially absent role has a hidden frame-zero anchor");
        assertFalse(hero.keys().get(2).visible());
        assertEquals(30, hero.keys().get(2).x(), "hidden roles retain the previous authored pose");
        assertFalse(friend.keys().getLast().visible());
        assertEquals(940, friend.keys().getLast().x());
        assertTrue(hero.keys().getLast().visible(), "a returning track is visible by default");
        assertEquals("middle.line", document.captions().getFirst().id());
        assertEquals(32, document.captions().getFirst().startFrame());
        assertEquals(38, document.captions().getFirst().endFrame());
        assertEquals("middle.audio0", document.audio().getFirst().id());
        assertEquals(33, document.audio().getFirst().frame());
        assertEquals("middle.effect0", document.cameraEffects().getFirst().id());
        assertEquals(35, document.cameraEffects().getFirst().startFrame());
        assertEquals(40, document.cameraEffects().getFirst().endFrame());
        assertEquals(document, StageplayStore.decode(StageplayStore.encode(document)));
    }

    @Test void compiledShotBoundariesHoldOutgoingCoordinatesAndRestartIncomingAnimation() throws Exception {
        var document = compile(recipe());
        assertEquals(20, StageplayTimeline.sample(document, 10).actors().getFirst().x());
        var outgoing = StageplayTimeline.sample(document, 29);
        assertEquals(100, outgoing.baseCamera().x());
        assertEquals(30, outgoing.actors().getFirst().x());
        assertEquals("s2", outgoing.set().gameId());
        var incoming = StageplayTimeline.sample(document, 30);
        assertEquals("s3k", incoming.set().gameId());
        assertEquals(1000, incoming.baseCamera().x());
        assertEquals(1, incoming.transition().coverage());
        assertFalse(incoming.actors().getFirst().visible());
        assertEquals(900, incoming.actors().getLast().x());
        assertEquals(0, incoming.actors().getLast().animationTick());
        assertEquals(940, StageplayTimeline.sample(document, 69).actors().getLast().x());
        var last = StageplayTimeline.sample(document, 70);
        assertEquals("s1", last.set().gameId());
        assertEquals(3000, last.actors().getFirst().x());
        assertEquals(0, last.actors().getFirst().animationTick());
        assertEquals(outgoing, StageplayTimeline.sample(document, 29));
    }

    @Test void omittedAudioGameUsesItsShotAndExplicitCrossGameAudioRemainsPossible() throws Exception {
        var script = recipe();
        ((ObjectNode) shot(script, 1).path("audio").get(0)).remove("gameId");
        shot(script, 2).putArray("audio").addObject().put("frame", 0).put("kind", "ROM_MUSIC").put("soundId", 129);
        var audio = compile(script).audio();
        assertEquals("s3k", audio.getFirst().gameId());
        assertEquals("s1", audio.getLast().gameId());
        ((ObjectNode) shot(script, 2).path("audio").get(0)).put("gameId", "s2");
        assertEquals("s2", compile(script).audio().getLast().gameId());
    }

    @Test void unknownFieldsAreRejectedAtEveryAuthoredLevel() throws Exception {
        List<Consumer<ObjectNode>> edits = List.of(
                root -> root.put("typo", 1),
                root -> ((ObjectNode) root.path("cast").get(0)).put("typo", 1),
                root -> shot(root, 0).put("typo", 1),
                root -> ((ObjectNode) shot(root, 0).get("set")).put("typo", 1),
                root -> ((ObjectNode) shot(root, 1).get("transition")).put("typo", 1),
                root -> ((ObjectNode) shot(root, 0).path("camera").get(0)).put("typo", 1),
                root -> ((ObjectNode) shot(root, 0).path("actors").path("hero").get(0)).put("typo", 1),
                root -> ((ObjectNode) shot(root, 1).path("captions").get(0)).put("typo", 1),
                root -> ((ObjectNode) shot(root, 1).path("audio").get(0)).put("typo", 1),
                root -> ((ObjectNode) shot(root, 1).path("cameraEffects").get(0)).put("typo", 1),
                root -> ((ObjectNode) shot(root, 0).get("actors")).putArray("unknown-role"));
        for (var edit : edits) rejects(edit);
    }

    @Test void duplicateKeysTrailingContentAndScalarCoercionsNeverBecomeValidScenes() throws Exception {
        for (String invalid : List.of("null", "[]", SCREENPLAY + "{}", SCREENPLAY.substring(0, SCREENPLAY.lastIndexOf('}')),
                SCREENPLAY.replaceFirst("\\{", "{\"version\":1,"),
                SCREENPLAY.replace("\"id\":\"opening\"", "\"id\":\"opening\",\"id\":\"duplicate\""))) {
            assertThrows(IOException.class, () -> StageplayScriptCompiler.compile(invalid.getBytes(StandardCharsets.UTF_8)), invalid);
        }
        List<Consumer<ObjectNode>> edits = List.of(
                root -> root.put("version", "1"), root -> root.put("version", 1.0), root -> root.put("name", 17),
                root -> root.put("width", "320"), root -> shot(root, 0).put("durationFrames", 30.0),
                root -> ((ObjectNode) shot(root, 0).get("set")).put("zone", "0"),
                root -> ((ObjectNode) shot(root, 0).path("camera").get(0)).put("frame", "0"),
                root -> ((ObjectNode) shot(root, 0).path("camera").get(0)).put("x", "12"),
                root -> ((ObjectNode) shot(root, 0).path("actors").path("hero").get(0)).put("visible", "true"),
                root -> ((ObjectNode) shot(root, 1).path("cameraEffects").get(0)).put("seed", 1.5),
                root -> shot(root, 0).putNull("actors"));
        for (var edit : edits) rejects(edit);
    }

    @Test void shotLocalBoundsCannotLeakKeysOrEventsIntoAdjacentShots() throws Exception {
        List<Consumer<ObjectNode>> edits = List.of(
                root -> ((ObjectNode) shot(root, 0).path("camera").get(0)).put("frame", 1),
                root -> ((ObjectNode) shot(root, 0).path("camera").get(1)).put("frame", 30),
                root -> ((ObjectNode) shot(root, 0).path("actors").path("hero").get(0)).put("frame", 1),
                root -> ((ObjectNode) shot(root, 0).path("actors").path("hero").get(1)).put("frame", 0),
                root -> ((ObjectNode) shot(root, 1).path("captions").get(0)).put("endFrame", 41),
                root -> ((ObjectNode) shot(root, 1).path("audio").get(0)).put("frame", 40),
                root -> ((ObjectNode) shot(root, 1).path("cameraEffects").get(0)).put("startFrame", -1),
                root -> shot(root, 0).putObject("transition").put("kind", "CUT"),
                root -> ((ObjectNode) shot(root, 1).get("transition")).put("durationFrames", 62),
                root -> ((ObjectNode) shot(root, 0).get("set")).put("levelProject", "../escape.ogfproject"));
        for (var edit : edits) rejects(edit);
        rejects(root -> {
            ((ObjectNode) shot(root, 1).get("transition")).put("durationFrames", 60);
            shot(root, 2).putObject("transition").put("kind", "FADE_WHITE").put("durationFrames", 30);
        });
    }

    @Test void aggregateLimitsRejectOversizedScriptsAndAcceptTheMaximumShotCount() throws Exception {
        assertThrows(IOException.class, () -> StageplayScriptCompiler.compile(new byte[0]));
        assertThrows(IOException.class, () -> StageplayScriptCompiler.compile(new byte[StageplayStore.MAX_BYTES + 1]));
        rejects(root -> root.putArray("shots"));
        rejects(root -> shot(root, 0).put("durationFrames", StageplayDocument.MAX_DURATION_FRAMES));
        rejects(root -> root.put("width", 159));
        rejects(root -> {
            ArrayNode cast = (ArrayNode) root.get("cast");
            for (int i = 0; i < 31; i++) cast.addObject().put("id", "extra" + i).put("name", "Extra")
                    .put("gameId", "s2").put("character", "sonic");
        });
        var script = recipe(); script.putArray("cast");
        ArrayNode shots = script.putArray("shots");
        for (int i = 0; i < 129; i++) {
            var shot = shots.addObject().put("id", "shot" + i).put("durationFrames", 1);
            shot.putObject("set").put("gameId", "s2").put("zone", 0).put("act", 0);
            shot.putArray("camera").addObject().put("frame", 0).put("x", i).put("y", 0);
        }
        assertEquals(128, compile(script).sceneChanges().size());
        shots.add(shots.get(0).deepCopy());
        assertThrows(IOException.class, () -> compile(script));
    }
}
