package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent losslessness checks: migrated stock examples and canonical editor refinements. */
class TestStageplayScriptRoundTrip {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path EXAMPLES = Path.of("examples/stageplay-demo");
    private static final String A = id("actorA"), B = id("actorB"), MANUAL = id("manual");

    @TestFactory Stream<DynamicTest> everyShippedCanonicalAndScreenplaySurvivesTwoStableRounds() throws IOException {
        List<Path> files;
        try (var paths = Files.walk(EXAMPLES)) {
            files = paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".stageplay")
                    || p.toString().endsWith(".screenplay.json") || p.toString().endsWith(".script.json")).sorted().toList();
        }
        assertEquals(8, files.stream().filter(p -> p.toString().endsWith(".stageplay")).count());
        assertEquals(7, files.stream().filter(p -> p.toString().endsWith(".json")).count());
        return files.stream().map(path -> DynamicTest.dynamicTest(EXAMPLES.relativize(path).toString(), () -> {
            byte[] bytes = Files.readAllBytes(path);
            StageplayDocument document = path.toString().endsWith(".stageplay")
                    ? StageplayStore.decode(bytes) : StageplayScriptCompiler.compile(bytes);
            roundTrip(document);
        }));
    }

    @Test void canonicalEditorRefinementsPreserveLiteralIdsTrackOrderAndIntervalsAcrossThreeShots() throws Exception {
        StageplayDocument document = refinedDocument();
        byte[] screenplay = roundTrip(document);
        var tree = (ObjectNode) JSON.readTree(screenplay);
        assertEquals(6, tree.path("version").intValue());
        assertEquals(List.of(B, A), strings(tree.path("order").path("puppets")));
        assertEquals(document.sceneChanges().getFirst().id(), tree.path("shots").get(1).path("transition").path("id").textValue());
        assertEquals(64, document.sceneChanges().getFirst().id().length());
        assertEquals(64, document.puppets().getLast().commands().getFirst().id().length());
        assertTrue(tree.path("shots").get(0).path("captions").get(0).path("endFrame").intValue() > 60,
                "The shot containing an interval's start owns the complete unsplit interval");
    }

    @TestFactory Stream<DynamicTest> strictVersionSixOrderRejectsAnythingExceptAnExactTypedPermutation() throws Exception {
        var cases = new ArrayList<DynamicTest>();
        for (String track : List.of("puppets", "captions", "audio", "cameraEffects", "dialogue")) {
            cases.add(reject("missing " + track, root -> order(root, track).remove(0)));
            cases.add(reject("duplicate " + track, root -> order(root, track).set(1, order(root, track).get(0))));
            cases.add(reject("unknown " + track, root -> order(root, track).set(0, JSON.getNodeFactory().textNode("missing"))));
            cases.add(reject("wrong type " + track, root -> order(root, track).set(0, JSON.getNodeFactory().numberNode(7))));
        }
        cases.add(reject("wrong category", root -> order(root, "captions").set(0, JSON.getNodeFactory().textNode(A))));
        cases.add(reject("unknown order category", root -> ((ObjectNode) root.path("order")).putArray("actors")));
        cases.add(reject("non-array order", root -> ((ObjectNode) root.path("order")).put("audio", "oops")));
        cases.add(reject("non-object order", root -> root.putArray("order")));
        return cases.stream();
    }

    @Test void versionSixRemainsStrictAboutUnknownDuplicateTrailingAndMalformedFields() throws Exception {
        for (Consumer<ObjectNode> edit : List.<Consumer<ObjectNode>>of(
                root -> root.put("unexpected", true),
                root -> shot(root, 0).put("canonical", "hidden payload"),
                root -> ((ObjectNode) shot(root, 0).path("camera").get(0)).put("unused", 4),
                root -> ((ObjectNode) shot(root, 1).path("transition")).put("unused", true),
                root -> ((ObjectNode) shot(root, 1).path("transition")).put("id", "bad id"),
                root -> root.put("version", 6.5), root -> root.put("version", 7))) {
            var root = recipe(); edit.accept(root);
            assertThrows(IOException.class, () -> compile(root));
        }
        String encoded = new String(StageplayScriptDecompiler.decompile(refinedDocument()), StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> StageplayScriptCompiler.compile((encoded + " {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> StageplayScriptCompiler.compile(
                encoded.replaceFirst("\\{", "{\"version\":6,").getBytes(StandardCharsets.UTF_8)), "Duplicate version must fail");
        assertThrows(IOException.class, () -> StageplayScriptCompiler.compile(
                encoded.replaceFirst("\\\"order\\\"\\s*:", "\"order\":{},\"order\":").getBytes(StandardCharsets.UTF_8)), "Duplicate nested order must fail");
    }

    @Test void rejectsUnsupportedScreenplayVersionsWithoutMigration() throws Exception {
        for (int version : new int[]{0, 1, 2, 3, 4, 5, 7}) {
            var script = minimalTwoShots(version);
            assertThrows(IOException.class, () -> compile(script));
        }
    }

    private static byte[] roundTrip(StageplayDocument document) throws Exception {
        byte[] screenplay = StageplayScriptDecompiler.decompile(document);
        StageplayDocument compiled = StageplayScriptCompiler.compile(screenplay);
        assertEquals(document, compiled);
        byte[] second = StageplayScriptDecompiler.decompile(compiled);
        assertArrayEquals(screenplay, second, "A second round must produce the same source bytes");
        assertEquals(document, StageplayScriptCompiler.compile(second));
        return screenplay;
    }
    private static DynamicTest reject(String name, Consumer<ObjectNode> edit) {
        return DynamicTest.dynamicTest(name, () -> {
            var root = recipe(); edit.accept(root);
            assertThrows(IOException.class, () -> compile(root), name);
        });
    }
    private static ObjectNode recipe() throws IOException { return (ObjectNode) JSON.readTree(StageplayScriptDecompiler.decompile(refinedDocument())); }
    private static StageplayDocument compile(ObjectNode root) throws IOException { return StageplayScriptCompiler.compile(JSON.writeValueAsBytes(root)); }
    private static ObjectNode shot(ObjectNode root, int index) { return (ObjectNode) root.path("shots").get(index); }
    private static ArrayNode order(ObjectNode root, String track) { return (ArrayNode) root.path("order").path(track); }
    private static List<String> strings(com.fasterxml.jackson.databind.JsonNode node) {
        var values = new ArrayList<String>(); node.forEach(value -> values.add(value.textValue())); return values;
    }
    private static String id(String prefix) { return prefix + "x".repeat(64 - prefix.length()); }

    private static ObjectNode minimalTwoShots(int version) throws IOException {
        return (ObjectNode) JSON.readTree("""
                {"version":%d,"name":"Strict intervals","cast":[],"shots":[
                {"id":"shot0","durationFrames":60,"set":{"gameId":"s2","zone":0,"act":0},
                 "camera":[{"frame":0,"x":0,"y":0}]},
                {"id":"shot1","durationFrames":60,"set":{"gameId":"s2","zone":0,"act":0},
                 "camera":[{"frame":0,"x":0,"y":0}]}]}
                """.formatted(version));
    }

    private static StageplayDocument refinedDocument() {
        var firstSet = new SetSource("s2", 0, 0, "", "");
        var secondSet = new SetSource("s2", 0, 0, "", "");
        var thirdSet = new SetSource("s2", 0, 0, "", "");
        var actors = List.of(actor(A, "Alpha", "sonic"), actor(B, "Beta", "tails"),
                new Actor(MANUAL, "Manual", "s3k", "knuckles", List.of(
                        new ActorKey(0, -17.25, 101.5, 18, 4, false, true, 2.25, Interpolation.SMOOTH),
                        new ActorKey(60, 233.75, -4.5, 2, 7, true, true, .5, Interpolation.LINEAR),
                        new ActorKey(120, 11, 12, 1, -1, false, false, 1, Interpolation.HOLD))));
        var part = new ObjectPartPose("root/child0/inline1", 65535, 65535);
        var keys = List.of(new ObjectKey(0, 80.25, 130.5, 8, 4, true, true, Interpolation.SMOOTH, ObjectMode.POSE, List.of(part)),
                new ObjectKey(60, 100.75, 131.5, 2, -1, false, true, Interpolation.LINEAR, ObjectMode.PUPPET, List.of(part)),
                new ObjectKey(120, 130, 132, 0, 0, false, false, Interpolation.HOLD, ObjectMode.POSE));
        var objects = List.of(new ObjectActor(id("animal"), "Released animal", "s3k", 0, 0, 0, 0, -1, keys,
                        List.of(new ObjectCommand(id("objectmove"), 62, ObjectCommandKind.MOVE_TO, -8.25, 97.5, .375),
                                new ObjectCommand(id("objectstop"), 90, ObjectCommandKind.STOP, 0, 0, 0)), ObjectSourceKind.RELEASED_ANIMAL),
                new ObjectActor(id("placed"), "Placed monitor", "s2", 0, 0, 0x26, 3, 43, keys, List.of()));
        var puppets = List.of(new PuppetTrack(B, false, 1, List.of(new PuppetCommand(id("stopB"), 0, PuppetCommandKind.STOP, 0, 0, 0, 0))),
                new PuppetTrack(A, true, 0, List.of(
                        new PuppetCommand(id("moveA"), 1, PuppetCommandKind.MOVE_TO, 125.75, 145.25, .75, 0,
                                new ObjectTarget(43, 0x26, 3, -5.5, 7.25), WaitCondition.NONE, 90),
                        new PuppetCommand(id("waitA"), 2, PuppetCommandKind.WAIT, 0, 0, 0, 0,
                                new ObjectTarget(43, 0x26, 3, 0, 0), WaitCondition.TARGET_AVAILABLE, 40),
                        new PuppetCommand(id("jumpA"), 61, PuppetCommandKind.JUMP, 0, 0, 0, 12),
                        new PuppetCommand(id("legacyMove"), 123, PuppetCommandKind.MOVE_TO, 301, 144, .5, 0))));
        return new StageplayDocument(StageplayDocument.VERSION, "Editor refinements: quotes \" and Unicode 👩‍🚀", 180, 400, 224, firstSet,
                actors, List.of(new CameraKey(0, 7.25, -3.5, .85, Interpolation.SMOOTH,
                                new CameraFollow(List.of(B, A), 3, 5, 4, .2, -9.25, 2.75)),
                        new CameraKey(60, 109.5, 3.25, 1.2, Interpolation.LINEAR,
                                new CameraFollow(List.of(A, B), 2, 1, 3, .3, 0, 0)),
                        new CameraKey(120, 43, 7, 1, Interpolation.HOLD)),
                List.of(new Caption(id("captionLate"), 130, 170, "Late", 8, 8, 0x80442211),
                        new Caption(id("captionCross"), 40, 140, "Cross-shot é 👩‍🚀\nsecond line", -2, 207, -1),
                        new Caption(id("captionEarly"), 0, 8, "Early", 9, 9, 0)),
                List.of(new AudioCue(id("voiceLate"), 150, AudioKind.VOICE, "s1", -1, "voice/take.ogg", .33),
                        new AudioCue(id("musicEarly"), 0, AudioKind.ROM_MUSIC, "s2", 3, "", 1),
                        new AudioCue(id("stopMiddle"), 75, AudioKind.MUSIC_STOP, "s3k", -1, "", 0)),
                List.of(new CameraEffect(id("effectLate"), 130, 170, 3, 4, 9, 7),
                        new CameraEffect(id("effectCross"), 50, 150, 8, 2, 6, Integer.MIN_VALUE)),
                List.of(new SceneChange(id("transitionA"), 60, secondSet, Transition.FADE_BLACK, 10),
                        new SceneChange(id("transitionB"), 120, thirdSet, Transition.CUT, 0)),
                List.of(new DialogueTyper("unusedTyper", 0, 0, 0), new DialogueTyper("usedTyper", 1, 2, 0)),
                List.of(new DialogueSkin("unusedSkin", -1, -1, 0, 0, 7, "decor/unused.png", 3, false),
                        new DialogueSkin("usedSkin", -1, 0xFFCC8822, 0x80102030, 0xFF778899, 8, "", 0, true)),
                List.of(dialogue(id("dialogueLate"), 145, 175), dialogue(id("dialogueCross"), 45, 135)),
                puppets, objects, List.of(new WorldCommand(id("clearBadniks"), 10, WorldCommandKind.CLEAR_BADNIKS, "", ObjectTarget.none()),
                        new WorldCommand(id("removePlacement"), 10, WorldCommandKind.REMOVE_OBJECT, "", new ObjectTarget(43, 0x26, 3, 0, 0)),
                        new WorldCommand(id("clearObjects"), 60, WorldCommandKind.CLEAR_OBJECTS, "", ObjectTarget.none())));
    }
    private static Actor actor(String id, String name, String character) {
        return new Actor(id, name, "s2", character, List.of(new ActorKey(0, 30, 144, 0, -1, false, true, 1, Interpolation.HOLD),
                new ActorKey(60, 35, 145, 0, -1, true, true, 1, Interpolation.HOLD),
                new ActorKey(120, 40, 146, 0, -1, false, true, 1, Interpolation.HOLD)));
    }
    private static DialogueCue dialogue(String id, int start, int end) {
        return new DialogueCue(id, start, end, A, "", "Hi!", "usedSkin", "usedTyper", 8, 170, 384, 48,
                "decor/speaker.png", List.of(0, 3, 8));
    }
}
