package com.openggf.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayTool {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;

    private record Result(int status, JsonNode json) { }

    private static Result execute(String... args) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int status;
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            status = StageplayTool.execute(args, output);
        }
        try (var parser = JSON.createParser(bytes.toByteArray())) {
            JsonNode json = JSON.readTree(parser);
            assertNotNull(json, "every invocation publishes a JSON result");
            assertTrue(json.isObject());
            assertNull(parser.nextToken(), "stdout contains exactly one JSON result");
            assertEquals(status == 0, json.path("ok").booleanValue());
            if (status != 0) {
                assertEquals(2, status);
                assertTrue(json.path("error").isTextual());
                assertFalse(json.path("error").textValue().isBlank());
                assertFalse(json.has("result"));
            }
            return new Result(status, json);
        }
    }

    private static ObjectNode recipe() throws IOException { return (ObjectNode) JSON.readTree(StageplayTool.scaffold()); }
    private static ObjectNode shot(ObjectNode script) { return (ObjectNode) script.path("shots").get(0); }
    private Path script(String name, ObjectNode recipe) throws IOException {
        Path file = directory.resolve(name); Files.write(file, JSON.writeValueAsBytes(recipe)); return file;
    }
    private static Set<String> entries(Path folder) throws IOException {
        try (var files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private static StageplayDocument timelineScene() {
        var linear = StageplayDocument.Interpolation.LINEAR;
        return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withDurationFrames(7)
                .withCamera(List.of(new StageplayDocument.CameraKey(0, 0, 0, 1, linear),
                        new StageplayDocument.CameraKey(2, 10, 0, 1, linear),
                        new StageplayDocument.CameraKey(3, 500, 0, 1, linear),
                        new StageplayDocument.CameraKey(6, 530, 0, 2, linear)))
                .withActors(List.of(new StageplayDocument.Actor("hero", "Hero", "s2", "sonic", List.of(
                        new StageplayDocument.ActorKey(0, 0, 100, 1, -1, false, true, 1, linear),
                        new StageplayDocument.ActorKey(3, 900, 100, 1, -1, false, true, 1, linear),
                        new StageplayDocument.ActorKey(6, 930, 100, 1, -1, false, true, 1, linear)))))
                .withCaptions(List.of(new StageplayDocument.Caption("line", 2, 4, "At the boundary", 0, 0, -1)))
                .withAudio(List.of(
                        new StageplayDocument.AudioCue("first", 0, StageplayDocument.AudioKind.ROM_SFX, "s2", 10, "", 1),
                        new StageplayDocument.AudioCue("between", 1, StageplayDocument.AudioKind.ROM_SFX, "s2", 11, "", 1),
                        new StageplayDocument.AudioCue("switch", 3, StageplayDocument.AudioKind.MUSIC_STOP, "s1", -1, "", 1),
                        new StageplayDocument.AudioCue("last", 6, StageplayDocument.AudioKind.ROM_SFX, "s1", 12, "", 1)))
                .withSceneChanges(List.of(new StageplayDocument.SceneChange("location", 3,
                        new StageplayDocument.SetSource("s1", 0, 0, "", ""), StageplayDocument.Transition.FADE_BLACK, 2)));
    }

    private static List<JsonNode> timelineRows(Path file) throws IOException {
        var rows = new java.util.ArrayList<JsonNode>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            try (var parser = JSON.createParser(line)) {
                JsonNode row = JSON.readTree(parser);
                assertNotNull(row); assertTrue(row.isObject()); assertNull(parser.nextToken());
                rows.add(row);
            }
        }
        return List.copyOf(rows);
    }

    @Test void timelineExportsExactSelectedProductionSamplesAndOnlyCuesAtThoseFrames() throws Exception {
        var document = timelineScene();
        Path scene = directory.resolve("scene.stageplay"), sampled = directory.resolve("sampled.jsonl");
        StageplayStore.write(scene, document);
        var result = execute("timeline", "--scene", scene.toString(), "--out", sampled.toString(), "--every", "3");
        assertEquals(0, result.status());
        assertEquals(3, result.json().path("result").path("every").intValue());
        assertEquals(3, result.json().path("result").path("samples").intValue());
        var rows = timelineRows(sampled);
        assertEquals(List.of(0, 3, 6), rows.stream().map(row -> row.path("frame").intValue()).toList());
        for (int index = 0; index < rows.size(); index++) {
            int frame = index * 3;
            assertEquals(frame / 60.0, rows.get(index).path("seconds").doubleValue());
            assertEquals(JSON.valueToTree(StageplayTimeline.sample(document, frame)), rows.get(index).path("sample"));
            assertEquals(1, rows.get(index).path("cues").size());
            assertEquals(List.of("first", "switch", "last").get(index), rows.get(index).path("cues").get(0).path("id").textValue());
        }
        assertEquals("s1", rows.get(1).path("sample").path("set").path("gameId").textValue());
        assertEquals(1, rows.get(1).path("sample").path("transition").path("coverage").doubleValue());
        assertEquals(900, rows.get(1).path("sample").path("actors").get(0).path("x").doubleValue());
        Path complete = directory.resolve("complete.jsonl");
        assertEquals(0, execute("timeline", "--scene", scene.toString(), "--out", complete.toString()).status());
        var all = timelineRows(complete);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), all.stream().map(row -> row.path("frame").intValue()).toList());
        assertEquals("between", all.get(1).path("cues").get(0).path("id").textValue());
        assertTrue(all.get(2).path("cues").isEmpty());
        assertTrue(all.get(4).path("cues").isEmpty());
        assertEquals(1, all.get(3).path("sample").path("captions").size());
        assertTrue(all.get(4).path("sample").path("captions").isEmpty());
        assertEquals(Set.of("scene.stageplay", "sampled.jsonl", "complete.jsonl"), entries(directory));
    }

    @Test void timelineRejectsInvalidIntervalsAndPreservesExistingOutputsAndSceneSource() throws Exception {
        Path scene = directory.resolve("scene.stageplay"), out = directory.resolve("state.jsonl");
        StageplayStore.write(scene, timelineScene());
        for (String every : List.of("0", "-1", "8", "1.5", "invalid", "2147483648")) {
            assertEquals(2, execute("timeline", "--scene", scene.toString(), "--out", out.toString(), "--every", every).status());
            assertFalse(Files.exists(out));
        }
        Files.writeString(out, "preserve existing provenance\n");
        byte[] original = Files.readAllBytes(out), source = Files.readAllBytes(scene);
        assertEquals(2, execute("timeline", "--scene", scene.toString(), "--out", out.toString()).status());
        assertArrayEquals(original, Files.readAllBytes(out));
        assertEquals(2, execute("timeline", "--scene", scene.toString(), "--out", scene.toString()).status());
        assertArrayEquals(source, Files.readAllBytes(scene));
        assertEquals(Set.of("scene.stageplay", "state.jsonl"), entries(directory));
    }

    @Test void initCompileInspectAndValidateWorkWithoutRomOrInteractiveEngine() throws Exception {
        Path script = directory.resolve("screenplay.json"), scene = directory.resolve("film.stageplay");
        assertEquals(0, execute("init", "--out", script.toString()).status());
        var compiled = execute("compile", "--script", script.toString(), "--out", scene.toString());
        assertEquals(0, compiled.status());
        assertEquals(StageplayDocument.VERSION, compiled.json().path("result").path("version").intValue());
        assertEquals(300, compiled.json().path("result").path("frames").intValue());
        assertEquals(5, compiled.json().path("result").path("seconds").doubleValue());
        assertEquals("First take", StageplayStore.read(scene).name());
        var inspected = execute("inspect", "--scene", scene.toString(), "--frame", "299");
        assertEquals(0, inspected.status());
        var sample = inspected.json().path("result").path("sample");
        assertEquals(299, sample.path("frame").intValue());
        assertEquals("tails", sample.path("actors").get(0).path("character").textValue());
        assertEquals(1, sample.path("baseCamera").path("zoom").doubleValue());
        assertEquals("s2", sample.path("set").path("gameId").textValue());
        var dialogue = execute("inspect", "--scene", scene.toString(), "--frame", "150")
                .json().path("result").path("sample").path("dialogue").get(0);
        assertEquals("Tails", dialogue.path("speakerName").textValue());
        assertEquals("Ready for our first take!", dialogue.path("visibleText").textValue());
        var validated = execute("validate", "--scene", scene.toString());
        assertEquals(0, validated.status());
        assertFalse(validated.json().path("result").has("sample"));
        assertTrue(validated.json().path("result").path("validation").textValue().startsWith("document structure"));
        assertEquals(Set.of("screenplay.json", "film.stageplay"), entries(directory));
    }

    @Test void initialLaterAndVoiceRelativeAssetsCannotSilentlyChangeTheirRootOnCompile() throws Exception {
        Path elsewhere = Files.createDirectory(directory.resolve("elsewhere"));
        List<Consumer<ObjectNode>> relativeAssets = List.of(
                script -> ((ObjectNode) shot(script).get("set")).put("levelProject", "sets/first.ogfproject"),
                script -> {
                    ObjectNode second = shot(script).deepCopy(); second.put("id", "second");
                    ((ObjectNode) second.get("set")).put("levelProject", "sets/second.ogfproject");
                    ((com.fasterxml.jackson.databind.node.ArrayNode) script.get("shots")).add(second);
                },
                script -> shot(script).putArray("audio").addObject().put("frame", 0)
                        .put("kind", "VOICE").put("asset", "voice/hello.wav"),
                script -> script.putArray("dialogueSkins").addObject().put("id", "blue")
                        .put("decorationAsset", "skins/blue.png").put("decorationBorder", 4),
                script -> ((ObjectNode) shot(script).path("dialogue").get(0))
                        .put("portraitAsset", "portraits/tails.png"));
        for (int i = 0; i < relativeAssets.size(); i++) {
            var recipe = recipe(); relativeAssets.get(i).accept(recipe);
            Path input = script("relative" + i + ".json", recipe);
            Path invalid = elsewhere.resolve("relative" + i + ".stageplay");
            var rejected = execute("compile", "--script", input.toString(), "--out", invalid.toString());
            assertEquals(2, rejected.status());
            assertTrue(rejected.json().path("error").textValue().contains("beside their screenplay"));
            assertFalse(Files.exists(invalid));
            Path sibling = directory.resolve("relative" + i + ".stageplay");
            assertEquals(0, execute("compile", "--script", input.toString(), "--out", sibling.toString()).status());
            assertEquals(StageplayScriptCompiler.compile(Files.readAllBytes(input)), StageplayStore.read(sibling));
        }
        Path assetFree = script("asset-free.json", recipe());
        assertEquals(0, execute("compile", "--script", assetFree.toString(), "--out",
                elsewhere.resolve("asset-free.stageplay").toString()).status());
        assertEquals(Set.of("asset-free.stageplay"), entries(elsewhere));
    }

    @Test void publicationFailsIfOutputExistsAndNeverTruncatesOrLeavesStagingFiles() throws Exception {
        Path output = directory.resolve("existing.stageplay");
        byte[] original = "preserve this exact content".getBytes(StandardCharsets.UTF_8);
        StageplayTool.publish(output, original);
        assertThrows(IOException.class, () -> StageplayTool.publish(output, new byte[]{1, 2, 3}));
        assertArrayEquals(original, Files.readAllBytes(output));
        assertEquals(2, execute("init", "--out", output.toString()).status());
        assertArrayEquals(original, Files.readAllBytes(output));
        Path input = script("screenplay.json", recipe());
        assertEquals(2, execute("compile", "--script", input.toString(), "--out", output.toString()).status());
        assertArrayEquals(original, Files.readAllBytes(output));
        byte[] recipeBytes = Files.readAllBytes(input);
        assertEquals(2, execute("compile", "--script", input.toString(), "--out", input.toString()).status());
        assertArrayEquals(recipeBytes, Files.readAllBytes(input), "compile cannot replace its source screenplay");
        Path occupied = Files.createDirectory(directory.resolve("occupied"));
        Files.writeString(occupied.resolve("keep.txt"), "keep");
        assertThrows(IOException.class, () -> StageplayTool.publish(occupied, new byte[]{4}));
        assertEquals("keep", Files.readString(occupied.resolve("keep.txt")));
        assertEquals(Set.of("existing.stageplay", "screenplay.json", "occupied"), entries(directory));
        assertEquals(Set.of("keep.txt"), entries(occupied));
    }

    @Test void concurrentPublicationHasExactlyOneWinnerAndPreservesItsCompleteBytes() throws Exception {
        Path destination = directory.resolve("one-winner.stageplay");
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        byte[] first = "first complete scene".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second complete scene".getBytes(StandardCharsets.UTF_8);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (byte[] bytes : List.of(first, second)) results.add(workers.submit(() -> {
                ready.countDown(); start.await();
                try { StageplayTool.publish(destination, bytes); return true; }
                catch (java.nio.file.FileAlreadyExistsException expected) { return false; }
            }));
            try { assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            boolean firstWon = results.get(0).get(5, java.util.concurrent.TimeUnit.SECONDS);
            boolean secondWon = results.get(1).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertNotEquals(firstWon, secondWon);
            assertArrayEquals(firstWon ? first : second, Files.readAllBytes(destination));
        }
        assertEquals(Set.of("one-winner.stageplay"), entries(directory));
    }

    @Test void malformedCommandsAndDocumentsReturnSingleJsonErrorsWithoutPublishingOutputs() throws Exception {
        Path malformed = directory.resolve("malformed.json"); Files.writeString(malformed, "{broken");
        Path scene = directory.resolve("film.stageplay");
        StageplayStore.write(scene, StageplayScriptCompiler.compile(StageplayTool.scaffold().getBytes(StandardCharsets.UTF_8)));
        Path output = directory.resolve("should-not-exist.stageplay");
        List<String[]> invalid = List.of(
                new String[]{"unknown"},
                new String[]{"help", "--unknown", "value"},
                new String[]{"init", "--out"},
                new String[]{"init", "--out", output.toString(), "--out", output.toString()},
                new String[]{"compile", "--script", malformed.toString(), "--out", output.toString()},
                new String[]{"compile", "--script", malformed.toString()},
                new String[]{"inspect", "--scene", scene.toString(), "--frame", "-1"},
                new String[]{"inspect", "--scene", scene.toString(), "--frame", "300"},
                new String[]{"inspect", "--scene", scene.toString(), "--frame", "not-a-frame"},
                new String[]{"validate", "--scene", directory.resolve("missing-\"quoted\"\nfile").toString()},
                new String[]{"catalog", "--game", "unknown", "--rom", "missing.gen"},
                new String[]{"init", "--out", directory.resolve("missing-parent/file.json").toString()});
        for (String[] args : invalid) assertEquals(2, execute(args).status(), String.join(" ", args));
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(directory.resolve("missing-parent")));
        assertEquals(Set.of("malformed.json", "film.stageplay"), entries(directory));
    }

    @Test void boundedInputRejectsDirectoriesAndOversizedFilesBeforeCompilation() throws Exception {
        assertThrows(IOException.class, () -> StageplayTool.read(directory));
        Path oversized = directory.resolve("oversized.json");
        try (var file = new RandomAccessFile(oversized.toFile(), "rw")) { file.setLength(StageplayStore.MAX_BYTES + 1L); }
        assertThrows(IOException.class, () -> StageplayTool.read(oversized));
        assertEquals(2, execute("compile", "--script", oversized.toString(), "--out",
                directory.resolve("oversized.stageplay").toString()).status());
        assertFalse(Files.exists(directory.resolve("oversized.stageplay")));
        Path empty = directory.resolve("empty.json"); Files.write(empty, new byte[0]);
        assertEquals(2, execute("compile", "--script", empty.toString(), "--out",
                directory.resolve("empty.stageplay").toString()).status());
        assertFalse(Files.exists(directory.resolve("empty.stageplay")));
        assertEquals(Set.of("oversized.json", "empty.json"), entries(directory));
    }

    @Test void helpDocumentsMachineReadableExitCodesAndCurrentSchemaWithoutRomAccess() throws Exception {
        var result = execute();
        assertEquals(0, result.status());
        assertEquals(1, result.json().path("result").path("screenplayVersion").intValue());
        assertEquals(StageplayDocument.VERSION, result.json().path("result").path("stageplayVersion").intValue());
        assertEquals(60, result.json().path("result").path("fps").intValue());
        assertTrue(result.json().path("result").path("outputs").textValue().contains("never overwritten"));
        assertTrue(entries(directory).isEmpty());
    }
}
