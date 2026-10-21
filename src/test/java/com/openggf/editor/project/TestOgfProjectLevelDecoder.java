package com.openggf.editor.project;

import com.openggf.game.GameId;
import com.openggf.level.resources.AuthoredLevelResources;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.runtime.StageplayResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestOgfProjectLevelDecoder {
    @TempDir Path directory;

    private Path project(Path path) throws IOException {
        var level = new TestEditorProjects.Fixture(GameId.S2);
        new EditorProjectStore().save(path,
                EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S2, level), level));
        return path;
    }

    @Test void registeredProviderReturnsIsolatedTerrainThroughTheLevelResourcePort() throws Exception {
        Path path = project(directory.resolve("set.ogfproject"));
        byte[] bytes = Files.readAllBytes(path);
        var decoded = AuthoredLevelResources.decode("ogfproject", bytes);
        var second = AuthoredLevelResources.read("ogfproject", path);
        assertEquals("s2", decoded.gameId());
        assertEquals(128, decoded.level().getBlockPixelSize());
        assertEquals(13, decoded.level().getPattern(1).getPixel(3, 3));
        decoded.level().getPattern(1).setPixel(3, 3, (byte) 2);
        assertEquals(13, second.level().getPattern(1).getPixel(3, 3));
        assertThrows(IOException.class, () -> AuthoredLevelResources.decode("ogfproject", Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IOException.class, () -> AuthoredLevelResources.decode("ogfproject",
                "{\"format\":\"bad\",\"format\":\"bad\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test void stageplayUsesTheProviderWithoutLosingGameOrPathValidation() throws Exception {
        Path scene = Files.createDirectory(directory.resolve("scene"));
        project(scene.resolve("set.ogfproject"));
        var document = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224)
                .withSet(new StageplayDocument.SetSource("s2", 0, 0, "", "set.ogfproject"));
        try (var resources = new StageplayResources(game -> mock(SceneRomArt.class), List.of("s2", "s1"))) {
            resources.setProjectRoot(scene);
            assertEquals(128, resources.set(document).blockSize());
            var wrongGame = document.withSet(new StageplayDocument.SetSource("s1", 0, 0, "", "set.ogfproject"));
            assertThrows(UncheckedIOException.class, () -> resources.set(wrongGame));
            Path outside = project(directory.resolve("outside.ogfproject"));
            Files.createSymbolicLink(scene.resolve("escape.ogfproject"), outside);
            var escaped = document.withSet(new StageplayDocument.SetSource("s2", 0, 0, "", "escape.ogfproject"));
            assertThrows(UncheckedIOException.class, () -> resources.set(escaped));
        }
    }
}
