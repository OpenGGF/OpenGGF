package com.openggf.game.timeattack;

import com.openggf.game.ghost.GhostFileCodec;
import com.openggf.game.ghost.GhostHeader;
import com.openggf.game.ghost.GhostRecording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostStore {
    private static AttemptInputRecording inputs() {
        AttemptInputRecording rec = new AttemptInputRecording(
                new AttemptStartDescriptor("s3k", 0, 0, "sonic", "fp"));
        rec.appendFrame(0x08, false);
        return rec;
    }

    private static GhostRecording ghost(int firstInput, int finish) {
        byte[] frames = new byte[7];
        // Header hash must match the inputs the store persists alongside (binding enforced).
        return new GhostRecording(new GhostHeader(1, "s3k", 0, 0, "sonic", "p",
                firstInput, finish, new int[0], inputs().sha256()), frames);
    }

    @Test
    void savesFirstRunAsBestWithInputSidecar(@TempDir Path root) throws IOException {
        GhostStore store = new GhostStore(root);
        assertTrue(store.saveIfBest(ghost(0, 3600), inputs()));
        assertTrue(Files.exists(root.resolve("s3k").resolve("0-0-sonic.ggfghost")));
        assertTrue(Files.exists(root.resolve("s3k").resolve("0-0-sonic.ggfinputs")));
        assertEquals(3600, store.loadBest("s3k", 0, 0, "sonic").orElseThrow()
                .header().finalTimeFrames());
    }

    @Test
    void rejectsSlowerRunKeepsBest(@TempDir Path root) throws IOException {
        GhostStore store = new GhostStore(root);
        store.saveIfBest(ghost(0, 3600), inputs());
        assertFalse(store.saveIfBest(ghost(0, 4000), inputs()));
        assertEquals(3600, store.loadBest("s3k", 0, 0, "sonic").orElseThrow()
                .header().finalTimeFrames());
    }

    @Test
    void rotatesPreviousBestsKeepingThree(@TempDir Path root) throws IOException {
        GhostStore store = new GhostStore(root);
        store.saveIfBest(ghost(0, 4000), inputs());
        store.saveIfBest(ghost(0, 3800), inputs());
        store.saveIfBest(ghost(0, 3600), inputs());
        Path dir = root.resolve("s3k");
        assertEquals(3600, GhostFileCodec.read(dir.resolve("0-0-sonic.ggfghost")).header().finalTimeFrames());
        assertEquals(3800, GhostFileCodec.read(dir.resolve("0-0-sonic-prev1.ggfghost")).header().finalTimeFrames());
        assertEquals(4000, GhostFileCodec.read(dir.resolve("0-0-sonic-prev2.ggfghost")).header().finalTimeFrames());
    }

    @Test
    void rejectsGhostWhoseHashDoesNotMatchInputs(@TempDir Path root) {
        GhostStore store = new GhostStore(root);
        GhostRecording mismatched = new GhostRecording(new GhostHeader(1, "s3k", 0, 0, "sonic", "p",
                0, 3600, new int[0], new byte[32]), new byte[7]); // zero hash != inputs().sha256()
        assertThrows(IllegalArgumentException.class, () -> store.saveIfBest(mismatched, inputs()));
    }

    @Test
    void listsImportsSortedOrEmpty(@TempDir Path root) throws IOException {
        GhostStore store = new GhostStore(root);
        assertTrue(store.listImports("s3k").isEmpty());
        Path importDir = root.resolve("s3k").resolve("import");
        Files.createDirectories(importDir);
        GhostFileCodec.write(ghost(0, 100), importDir.resolve("b.ggfghost"));
        GhostFileCodec.write(ghost(0, 100), importDir.resolve("a.ggfghost"));
        Files.writeString(importDir.resolve("readme.txt"), "ignored");
        var imports = store.listImports("s3k");
        assertEquals(2, imports.size());
        assertTrue(imports.get(0).getFileName().toString().startsWith("a"));
    }
}
