package com.openggf.stageplay.runtime;

import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;
import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayDialogueResources {
    @TempDir Path temp;

    @Test void missingAndEscapingDecorationPathsFailBeforePresentation() throws Exception {
        Path sceneRoot = Files.createDirectory(temp.resolve("scene"));
        Path outside = temp.resolve("outside.png"); PngCodec.write(outside, PixelImage.blank(12, 12));
        Files.createSymbolicLink(sceneRoot.resolve("panel.png"), outside);
        try (var resources = resources()) {
            resources.setProjectRoot(sceneRoot);
            assertThrows(UncheckedIOException.class, () -> resources.prepareDialogue(scene("panel.png", 2)));
            assertThrows(UncheckedIOException.class, () -> resources.prepareDialogue(scene("missing.png", 2)));
        }
        assertThrows(IllegalArgumentException.class, () -> StageplayAssets.read(sceneRoot, "../outside.png"));
        try (var file = java.nio.channels.FileChannel.open(sceneRoot.resolve("oversized.png"),
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
            file.position(StageplayAssets.MAX_ENCODED_BYTES); file.write(ByteBuffer.wrap(new byte[]{0}));
        }
        assertThrows(java.io.IOException.class, () -> StageplayAssets.read(sceneRoot, "oversized.png"));
    }

    @Test void pngHeaderAndAggregatePixelsAreBoundedBeforeDecode() throws Exception {
        byte[] tiny = png(12, 12); byte[] oversized = tiny.clone();
        ByteBuffer.wrap(oversized).putInt(16, 1025);
        try (var resources = resources()) {
            resources.setAssetReader(path -> oversized);
            var error = assertThrows(UncheckedIOException.class, () -> resources.prepareDialogue(scene("huge.png", 2)));
            assertTrue(error.getCause().getMessage().contains("1024"));
        }
        byte[] large = png(1024, 1024);
        var skins = new ArrayList<DialogueSkin>();
        for (int index = 0; index < 5; index++) skins.add(skin("skin" + index, "panel" + index + ".png", 2));
        var document = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withDialogueSkins(skins);
        try (var resources = resources()) {
            resources.setAssetReader(path -> large);
            var error = assertThrows(UncheckedIOException.class, () -> resources.prepareDialogue(document));
            assertTrue(error.getCause().getMessage().contains("pixel budget"));
        }
    }

    @Test void preparedImagesAreSharedAndBadSlicingDoesNotReplaceTheLastCompleteSnapshot() throws Exception {
        byte[] encoded = png(12, 12); var reads = new AtomicInteger();
        var first = scene("panel.png", 2);
        var cue = first.dialogue().getFirst();
        first = first.withDialogue(List.of(new DialogueCue(cue.id(), cue.startFrame(), cue.endFrame(), "", "NARRATOR",
                "Hello.", "skin", "", 12, 130, 296, 82, "panel.png", List.of())));
        try (var resources = resources()) {
            resources.setAssetReader(path -> { reads.incrementAndGet(); return encoded; });
            resources.prepareDialogue(first); var image = resources.dialogueImage("panel.png");
            resources.prepareDialogue(first); assertEquals(1, reads.get(), "one path used as portrait and decoration decodes once");
            var invalid = first.withDialogueSkins(List.of(skin("skin", "panel.png", 6)));
            assertThrows(IllegalArgumentException.class, () -> resources.prepareDialogue(invalid));
            assertSame(image, resources.dialogueImage("panel.png"));
            assertEquals(2, resources.dialogueSkin(cue.id()).decorationBorder());
            resources.setProjectRoot(temp);
            assertThrows(IllegalArgumentException.class, () -> resources.dialogueImage("panel.png"));
        }
    }

    @Test void decorationInsetsMustAlsoFitTheDestinationPanel() throws Exception {
        byte[] encoded = png(128, 128);
        try (var resources = resources()) {
            resources.setAssetReader(path -> encoded);
            assertThrows(IllegalArgumentException.class, () -> resources.prepareDialogue(scene("panel.png", 42)));
        }
    }

    private StageplayResources resources() { return new StageplayResources(game -> null, List.of()); }
    private byte[] png(int width, int height) throws Exception {
        Path file = temp.resolve("image-" + width + "-" + height + ".png");
        PngCodec.write(file, PixelImage.blank(width, height)); return Files.readAllBytes(file);
    }
    static DialogueSkin skin(String id, String asset, int border) {
        return new DialogueSkin(id, 0xfffff0cf, 0xffffd36a, 0xff101522, 0xffc7cbd1, 6, asset, border, true);
    }
    static StageplayDocument scene(String asset, int border) {
        return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224)
                .withDialogueSkins(List.of(skin("skin", asset, border)))
                .withDialogue(List.of(new DialogueCue("line", 0, 180, "", "NARRATOR", "Hello.",
                        "skin", "", 12, 130, 296, 82, "", List.of())));
    }
}
