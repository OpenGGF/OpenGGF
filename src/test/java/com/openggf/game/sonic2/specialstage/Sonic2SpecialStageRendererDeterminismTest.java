package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Sonic2SpecialStageRendererDeterminismTest {

    private static final Path RENDERER_PATH = Path.of(
            "src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageRenderer.java");
    private static final Path MANAGER_PATH = Path.of(
            "src/main/java/com/openggf/game/sonic2/specialstage/Sonic2SpecialStageManager.java");

    @Test
    public void playerFlashingUsesSpecialStageFrameCounterInsteadOfWallClock() throws Exception {
        String renderer = Files.readString(RENDERER_PATH);
        String manager = Files.readString(MANAGER_PATH);

        assertFalse(renderer.contains("System.currentTimeMillis()"),
                "Special-stage rendering must be deterministic and must not sample wall-clock time");
        assertTrue(renderer.contains("setFrameCounter(int frameCounter)"),
                "Renderer should accept the manager-owned frame counter for flash timing");
        assertTrue(manager.contains("renderer.setFrameCounter(frameCounter);"),
                "Manager should pass the special-stage frame counter into the renderer before drawing");
    }
}
