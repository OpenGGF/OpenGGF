package com.openggf.stageplay;

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
                valid.replace("\"version\" : 1", "\"version\" : 2").replace("\"version\":1", "\"version\":2"),
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
