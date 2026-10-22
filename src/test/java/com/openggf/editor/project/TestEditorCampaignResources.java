package com.openggf.editor.project;

import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestEditorCampaignResources {
    @TempDir Path directory;

    @Test void copiesOnlySceneRelativeImagesVoiceAndEveryAuthoredSetIncludingChanges() throws Exception {
        Path source = Files.createDirectory(directory.resolve("source"));
        Path scenes = Files.createDirectory(source.resolve("scenes"));
        Path staged = Files.createDirectory(directory.resolve("staged"));
        var document = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224)
                .withSet(new StageplayDocument.SetSource("s2", 0, 0, "", "terrain.ogfproject"))
                .withCamera(List.of(
                        new StageplayDocument.CameraKey(0, 0, 0, 1, StageplayDocument.Interpolation.HOLD),
                        new StageplayDocument.CameraKey(10, 0, 0, 1, StageplayDocument.Interpolation.HOLD)))
                .withSceneChanges(List.of(new StageplayDocument.SceneChange("next", 10,
                        new StageplayDocument.SetSource("s2", 0, 0, "", "other.ogfproject"),
                        StageplayDocument.Transition.CUT, 0)))
                .withAudio(List.of(new StageplayDocument.AudioCue("voice", 0,
                        StageplayDocument.AudioKind.VOICE, "s2", -1, "voice.wav", 1)))
                .withDialogueSkins(List.of(new StageplayDocument.DialogueSkin("panel", -1, -1,
                        0xff101010, -1, 6, "panel.png", 0, false)))
                .withDialogue(List.of(new StageplayDocument.DialogueCue("line", 0, 60, "", "Narrator",
                        "Hello", "panel", "", 0, 0, 200, 60, "portrait.png", List.of())));
        StageplayStore.write(scenes.resolve("intro.stageplay"), document);
        for (String name : List.of("terrain.ogfproject", "other.ogfproject", "voice.wav", "panel.png", "portrait.png"))
            Files.writeString(scenes.resolve(name), "owned bytes for " + name);
        Files.writeString(source.resolve("unrelated.txt"), "must stay out of the package");
        EditorCampaignResources.copy(source, staged, List.of("scenes/intro.stageplay"));
        for (String name : List.of("intro.stageplay", "terrain.ogfproject", "other.ogfproject", "voice.wav", "panel.png", "portrait.png"))
            assertArrayEquals(Files.readAllBytes(scenes.resolve(name)), Files.readAllBytes(staged.resolve("scenes").resolve(name)));
        assertFalse(Files.exists(staged.resolve("unrelated.txt")));
        assertDoesNotThrow(() -> EditorCampaignResources.copy(source, staged, List.of("scenes/intro.stageplay")),
                "Identical shared dependencies can be reused across acts");
    }

    @Test void conflictingAssetsAndMissingOrEscapedScenesFailWithoutOverwritingStagedBytes() throws Exception {
        Path first = Files.createDirectory(directory.resolve("first"));
        Path second = Files.createDirectory(directory.resolve("second"));
        Path staged = Files.createDirectory(directory.resolve("staged"));
        var scene = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224);
        StageplayStore.write(first.resolve("intro.stageplay"), scene.withName("First"));
        StageplayStore.write(second.resolve("intro.stageplay"), scene.withName("Second"));
        EditorCampaignResources.copy(first, staged, List.of("intro.stageplay"));
        byte[] original = Files.readAllBytes(staged.resolve("intro.stageplay"));
        assertThrows(IOException.class, () -> EditorCampaignResources.copy(second, staged, List.of("intro.stageplay")));
        assertArrayEquals(original, Files.readAllBytes(staged.resolve("intro.stageplay")));
        assertThrows(IOException.class, () -> EditorCampaignResources.copy(first, staged, List.of("missing.stageplay")));
        assertThrows(IllegalArgumentException.class, () -> EditorCampaignResources.copy(first, staged, List.of("../intro.stageplay")));
    }
}
