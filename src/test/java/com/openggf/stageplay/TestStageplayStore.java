package com.openggf.stageplay;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.TestStageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayStore {
    @TempDir Path directory;

    @Test void rejectsRomCueGainWhenLoadingCanonicalScenes() throws Exception {
        for (var kind : List.of(AudioKind.ROM_MUSIC, AudioKind.ROM_SFX)) {
            var valid = scene().withAudio(List.of(new AudioCue("cue", 0, kind, "s2", 1, "", 1)));
            var mapper = new JsonMapper();
            var tree = (ObjectNode) mapper.readTree(StageplayStore.encode(valid));
            ((ObjectNode) tree.path("audio").get(0)).put("gain", .58);
            var error = assertThrows(IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(tree)));
            assertTrue(error.getMessage().contains("ROM cue gain must be 1; gain is available for voice clips"), error.getMessage());
        }
    }

    @Test
    void roundTripsEveryTrackAndLinkedProjectWithoutEmbeddingRomBytes() throws IOException {
        var doc = scene().withName("Arrival 🎬")
                .withSet(new SetSource("s3k", 0, 0, "cfbf98c36c776677290a872547ac47c53d2761d6", "sets/arrival.ogfproject"))
                .withActors(List.of(actor(List.of(key(0, 1.5, 2, -1, Interpolation.SMOOTH)))))
                .withCaptions(List.of(new Caption("line", 1, 20, "Hello\nworld", 10, 180, 0xffffffff)))
                .withAudio(List.of(new AudioCue("voice", 1, AudioKind.VOICE, "s3k", -1, "voice/hello.ogg", .5)));
        var file = directory.resolve("arrival.stageplay");
        StageplayStore.write(file, doc);
        assertEquals(doc, StageplayStore.read(file));
        assertEquals(doc, StageplayStore.decode(StageplayStore.encode(doc)));
        StageplayStore.write(file, doc.withName("Revised"));
        assertEquals("Revised", StageplayStore.read(file).name());
        try (var entries = Files.list(directory)) {
            assertEquals(1, entries.count(), "save removes its temporary file");
        }
    }

    @Test
    void rejectsAmbiguousCoercedUnknownTruncatedAndOversizedInput() throws IOException {
        String valid = new String(StageplayStore.encode(scene()), StandardCharsets.UTF_8);
        for (String bad : List.of(valid + "{}", valid.substring(0, valid.length() - 1),
                valid.replaceFirst("\\{", "{\"unexpected\":true,"),
                valid.replaceFirst("\\{", "{\"version\":1,"),
                valid.replace("\"name\":\"Untitled\"", "\"name\":7"),
                valid.replace("\"romSha1\":\"\",", ""),
                valid.replace("\"interpolation\":\"HOLD\"", "\"interpolation\":0"),
                valid.replace("\"interpolation\":\"HOLD\"", "\"interpolation\":\"UNKNOWN\""),
                valid.replace("\"version\":" + VERSION, "\"version\":" + (VERSION + 1)),
                valid.replaceAll("\"durationFrames\"\\s*:\\s*600", "\"durationFrames\":\"600\""),
                valid.replaceAll("\"zoom\"\\s*:\\s*1\\.0", "\"zoom\":null"),
                valid.replaceAll("\"camera\"\\s*:", "\"camera\":null,\"ignored\":"))) {
            assertThrows(IOException.class, () -> StageplayStore.decode(bad.getBytes(StandardCharsets.UTF_8)), bad);
        }
        assertThrows(IOException.class, () -> StageplayStore.decode(new byte[8 * 1024 * 1024 + 1]));
        assertThrows(IOException.class, () -> StageplayStore.decode(new byte[0]));
        assertThrows(IOException.class, () -> StageplayStore.decode("null".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsEveryUnsupportedVersionAndRequiresTheCompleteCurrentShape() throws IOException {
        var mapper = new JsonMapper();
        for (int version : new int[]{-1, 0, 1, 2, 3, 4, 5, 6, 7, VERSION + 1}) {
            var tree = (ObjectNode) mapper.readTree(StageplayStore.encode(scene()));
            tree.put("version", version);
            assertThrows(IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(tree)));
        }
        for (String field : List.of("cameraEffects", "sceneChanges", "dialogueTypers", "dialogueSkins",
                "dialogue", "puppets", "objectActors", "worldCommands")) {
            var tree = (ObjectNode) mapper.readTree(StageplayStore.encode(scene()));
            tree.remove(field);
            assertThrows(IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(tree)), field);
        }
        for (String policy : List.of("LIVE", "TERRAIN_ONLY", "LEGACY_LIVE", "LEGACY_TERRAIN_ONLY")) {
            var tree = (ObjectNode) mapper.readTree(StageplayStore.encode(scene()));
            ((ObjectNode) tree.get("set")).put("worldPolicy", policy);
            assertThrows(IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(tree)), policy);
        }
    }

    @Test
    void dialogueProfilesAssetsAndExplicitGraphemeTimingRoundTripStrictly() throws IOException {
        var cue = new DialogueCue("speech", 2, 100, "hero", "Pilot", "Ae\u0301!", "radio", "radio",
                8, 140, 304, 76, "portraits/hero.png", List.of(0, 3, 10));
        var document = scene().withActors(List.of(actor(List.of(key(0, 0, 0, -1, Interpolation.HOLD)))))
                .withDialogueSkins(List.of(new DialogueSkin("radio", 0xffeeffee, 0xffffcc66,
                        0xff112233, 0xffaabbcc, 6, "skins/radio.png", 8, true)))
                .withDialogueTypers(List.of(new DialogueTyper("radio", 3, 9, 2)))
                .withDialogue(List.of(cue));
        var destination = directory.resolve("dialogue.stageplay");
        StageplayStore.write(destination, document);
        assertEquals(document, StageplayStore.read(destination));
        assertEquals("Ae\u0301", StageplayTimeline.sample(StageplayStore.read(destination), 5).dialogue().getFirst().visibleText());
        var mapper = new JsonMapper();
        ObjectNode valid = (ObjectNode) mapper.readTree(StageplayStore.encode(document));
        for (String field : List.of("dialogueTypers", "dialogueSkins", "dialogue")) {
            ObjectNode missing = valid.deepCopy();
            missing.remove(field);
            assertThrows(IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(missing)), field);
        }
        ObjectNode unknown = valid.deepCopy();
        ((ObjectNode) unknown.withArray("dialogue").get(0)).put("callback", "unsafe");
        assertThrows(IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(unknown)));
        ObjectNode decimal = valid.deepCopy();
        ((ObjectNode) decimal.withArray("dialogue").get(0)).putArray("revealFrames").add(0).add(3.5).add(10);
        assertThrows(IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(decimal)));
        ObjectNode coerced = valid.deepCopy();
        ((ObjectNode) coerced.withArray("dialogueTypers").get(0)).put("framesPerGrapheme", "3");
        assertThrows(IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(coerced)));
        ObjectNode escaped = valid.deepCopy();
        ((ObjectNode) escaped.withArray("dialogueSkins").get(0)).put("decorationAsset", "../outside.png");
        assertThrows(IOException.class, () -> StageplayStore.decode(mapper.writeValueAsBytes(escaped)));
    }

    @Test
    void failedSavePreservesOriginalAndDoesNotLeaveTemporaryFiles() throws IOException {
        var destination = directory.resolve("occupied.stageplay");
        Files.createDirectory(destination);
        var sentinel = destination.resolve("keep.txt");
        Files.writeString(sentinel, "keep");
        assertThrows(IOException.class, () -> StageplayStore.write(destination, scene()));
        assertEquals("keep", Files.readString(sentinel));
        try (var entries = Files.list(directory)) {
            assertEquals(1, entries.count());
        }
    }

    @Test
    void oversizedEncodingLeavesExistingSceneUntouched() throws IOException {
        var destination = directory.resolve("existing.stageplay");
        StageplayStore.write(destination, scene());
        byte[] original = Files.readAllBytes(destination);
        var keys = new ArrayList<ActorKey>();
        for (int frame = 0; frame < 30_000; frame++) {
            keys.add(key(frame, frame / 10.0, 0, -1, Interpolation.LINEAR));
        }
        var large = scene().withDurationFrames(36_000).withActors(List.of(
                new Actor("one", "One", "s3k", "sonic", keys),
                new Actor("two", "Two", "s3k", "tails", keys),
                new Actor("three", "Three", "s3k", "knuckles", keys)));
        assertThrows(IOException.class, () -> StageplayStore.write(destination, large));
        assertArrayEquals(original, Files.readAllBytes(destination));
        try (var entries = Files.list(directory)) {
            assertEquals(1, entries.count());
        }
    }
}
