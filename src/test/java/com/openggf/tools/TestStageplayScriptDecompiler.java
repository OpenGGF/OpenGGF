package com.openggf.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

class TestStageplayScriptDecompiler {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MINIMAL =
            """
            {"version":6,"name":"A small scene","cast":[],"shots":[
              {"id":"opening","durationFrames":60,"set":{"gameId":"s2","zone":0,"act":0},
               "camera":[{"frame":0,"x":0,"y":0}]}]}
            """;

    @Test
    void screenplayVersionSixAloneAdmitsTheLargerSourceBudget() throws Exception {
        byte[] versionSix = padded(MINIMAL, StageplayStore.MAX_BYTES + 1);
        assertEquals(
                StageplayScriptCompiler.compile(MINIMAL.getBytes(StandardCharsets.UTF_8)),
                StageplayScriptCompiler.compile(versionSix));
        for (int version = 1; version <= 5; version++) {
            byte[] legacy =
                    padded(
                            MINIMAL.replace("\"version\":6", "\"version\":" + version),
                            StageplayStore.MAX_BYTES + 1);
            assertThrows(IOException.class, () -> StageplayScriptCompiler.compile(legacy));
        }
        assertEquals(32 * 1024 * 1024, StageplayScriptCompiler.MAX_BYTES);
        assertThrows(
                IOException.class,
                () ->
                        StageplayScriptCompiler.compile(
                                new byte[StageplayScriptCompiler.MAX_BYTES + 1]));
    }

    @Test
    void emptyCastAndAdjacentOneFrameShotsKeepExactKeysAndIndependentTransitionIdentities()
            throws Exception {
        ObjectNode canonical =
                JSON.valueToTree(
                        StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withDurationFrames(3));
        var camera = canonical.putArray("camera");
        for (int frame = 0; frame < 3; frame++) {
            camera.addObject()
                    .put("frame", frame)
                    .put("x", frame + 0.25)
                    .put("y", frame - 0.5)
                    .put("zoom", 1 + frame * 0.125)
                    .put("interpolation", "SMOOTH")
                    .set("follow", JSON.valueToTree(StageplayDocument.CameraFollow.none()));
        }
        for (int frame = 1; frame < 3; frame++) {
            canonical
                    .withArray("sceneChanges")
                    .addObject()
                    .put("id", frame == 1 ? "unrelated.transition" : "shot1.change")
                    .put("frame", frame)
                    .put("transition", "CUT")
                    .put("durationFrames", 0)
                    .set("set", canonical.get("set"));
        }
        StageplayDocument document = StageplayStore.decode(JSON.writeValueAsBytes(canonical));
        byte[] source = StageplayScriptDecompiler.decompile(document);
        assertEquals(document, StageplayScriptCompiler.compile(source));
        var screenplay = JSON.readTree(source);
        assertEquals("shot1", screenplay.path("shots").get(1).path("id").textValue());
        assertEquals(
                "unrelated.transition",
                screenplay.path("shots").get(1).path("transition").path("id").textValue());
        assertEquals(
                List.of(0, 1, 2),
                StageplayScriptCompiler.compile(source).camera().stream()
                        .map(StageplayDocument.CameraKey::frame)
                        .toList());
        assertArrayEquals(
                source,
                StageplayScriptDecompiler.decompile(StageplayScriptCompiler.compile(source)));
    }

    @Test
    void defaultExpansionNeverCoercesV6ScalarsOrConsumesUnknownFields() throws Exception {
        for (Consumer<ObjectNode> edit :
                List.<Consumer<ObjectNode>>of(
                        root -> root.put("name", 7),
                        root -> root.put("width", "400"),
                        root -> camera(root).put("x", "1.25"),
                        root -> camera(root).put("zoom", true),
                        root -> camera(root).put("interpolation", 1),
                        root -> camera(root).putNull("follow"),
                        root ->
                                shot(root)
                                        .putObject("set")
                                        .put("gameId", true)
                                        .put("zone", 0)
                                        .put("act", 0),
                        root ->
                                shot(root)
                                        .putArray("captions")
                                        .addObject()
                                        .put("id", "cue")
                                        .put("startFrame", 0)
                                        .put("endFrame", 20)
                                        .put("text", 42),
                        root ->
                                root.putArray("dialogueSkins")
                                        .addObject()
                                        .put("id", "unused")
                                        .put("pointer", "true"),
                        root ->
                                root.putArray("dialogueTypers")
                                        .addObject()
                                        .put("id", "unused")
                                        .put("spacePauseFrames", 1.5),
                        root -> root.putObject("order").putArray("unknown"),
                        root -> shot(root).put("durationFrames", 60.0))) {
            var recipe = (ObjectNode) JSON.readTree(MINIMAL);
            edit.accept(recipe);
            assertThrows(
                    IOException.class,
                    () -> StageplayScriptCompiler.compile(JSON.writeValueAsBytes(recipe)),
                    recipe.toString());
        }
    }

    private static ObjectNode shot(ObjectNode root) {
        return (ObjectNode) root.path("shots").get(0);
    }

    private static ObjectNode camera(ObjectNode root) {
        return (ObjectNode) shot(root).path("camera").get(0);
    }

    private static byte[] padded(String json, int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) ' ');
        byte[] prefix = json.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        return bytes;
    }
}
