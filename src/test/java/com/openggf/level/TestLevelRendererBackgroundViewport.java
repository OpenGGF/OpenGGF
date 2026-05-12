package com.openggf.level;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLevelRendererBackgroundViewport {

    @Test
    void backgroundTilePassUsesFboViewportDimensions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelRenderer.java"));
        int commandStart = source.indexOf("private final GLCommand bgTilePassCommand");
        int commandEnd = source.indexOf("private final GLCommand highPriorityFboCommand", commandStart);
        if (commandEnd < 0) {
            commandEnd = source.indexOf("LevelRenderer(LevelManager levelManager)", commandStart);
        }
        String commandBody = source.substring(commandStart, commandEnd).replace("\r\n", "\n");

        assertTrue(commandBody.matches("(?s).*TilemapGpuRenderer\\.Layer\\.BACKGROUND,\\s+"
                        + "pendingBgTilePassRenderWidth,\\s+"
                        + "pendingBgTilePassRenderHeight,\\s+"
                        + "0,\\s+"
                        + "0,\\s+"
                        + "pendingBgTilePassRenderWidth,\\s+"
                        + "pendingBgTilePassRenderHeight,\\s+"
                        + "pendingBgTilePassBgTilemapWorldOffsetX,.*"),
                "The background tile pass renders into the BG FBO; its tilemap shader viewport must be the FBO viewport, not the cached screen viewport.");
    }
}
