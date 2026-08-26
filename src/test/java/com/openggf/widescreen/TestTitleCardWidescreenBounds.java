package com.openggf.widescreen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTitleCardWidescreenBounds {

    @Test
    void s2ExitTailsUseElementCompletionAtEveryViewportWidth() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/openggf/game/sonic2/titlecard/TitleCardManager.java"));

        assertTrue(source.contains("if (leftSwooshElement.hasExited())"));
        assertTrue(source.contains("if (bottomBarElement.hasExited())"));
    }

    @Test
    void s3kElementAndTeardownBoundsUseTheLiveCenteredViewport() throws IOException {
        String manager = Files.readString(Path.of(
                "src/main/java/com/openggf/game/sonic3k/titlecard/Sonic3kTitleCardManager.java"));
        String teardown = Files.readString(Path.of(
                "src/main/java/com/openggf/game/sonic3k/titlecard/Sonic3kTitleCardTeardownModel.java"));

        assertTrue(manager.contains("elemX[idx] + xOffset() - renderWidth >= viewportWidth()"));
        assertTrue(teardown.contains("int logicalWidth = viewportWidth();"));
        assertTrue(teardown.contains("x - SCREEN_ORIGIN + xOffset"));
        assertTrue(teardown.contains("dx - width >= logicalWidth"));
    }
}
