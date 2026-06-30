package com.openggf.level;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void highPriorityForegroundMaskUsesForegroundVScrollOrigin() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelRenderer.java"));
        String normalizedSource = source.replace("\r\n", "\n");

        assertTrue(normalizedSource.contains("float fgWorldOffsetY = lm.parallaxManager.getVscrollFactorFG();"),
                "The high-priority mask must use the same foreground VScroll origin as the visible Plane A pass.");
        assertFalse(normalizedSource.contains("float fgWorldOffsetY = camera.getYWithShake();"),
                "Using camera Y for the mask shifts low-priority sprite occlusion away from visible foreground tiles.");
    }

    @Test
    void logicFrameParallaxPublishesRuntimeStateBeforeAnimatedTilesReadIt() throws Exception {
        String levelManagerSource = Files.readString(Path.of("src/main/java/com/openggf/level/LevelManager.java"));
        assertTrue(levelManagerSource.contains("frameRuntimeUpdater.updateParallaxAndAnimatedContent();"),
                "LevelManager.update must advance parallax and animated content during logic frames.");

        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelFrameRuntimeUpdater.java"));
        String method = methodBody(source.replace("\r\n", "\n"), "void updateParallaxAndAnimatedContent(");

        int parallaxUpdate = method.indexOf("parallaxManager.update(");
        int animatedPatternUpdate = method.indexOf("animatedPatternManager.update();");

        assertTrue(parallaxUpdate >= 0, "Logic-frame runtime update must update parallax each frame.");
        assertTrue(animatedPatternUpdate >= 0, "Logic-frame runtime update must update animated patterns each frame.");
        assertTrue(parallaxUpdate < animatedPatternUpdate,
                "Parallax/deform runtime state must be published before S3K animated tiles read it.");
    }

    @Test
    void backgroundTilemapCacheTracksRuntimeFullWidthRequirement() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/openggf/level/LevelTilemapManager.java"));

        assertTrue(source.contains("lastRequiresFullWidthBgTilemap"),
                "BG tilemap cache must remember the runtime full-width requirement used for the last build.");
        assertTrue(source.contains("if (lastRequiresFullWidthBgTilemap != null")
                        && source.contains("lastRequiresFullWidthBgTilemap != requiresFullWidthBgTilemap"),
                "ensureBackgroundTilemapData must dirty the BG cache when runtime full-width mode changes.");
        assertTrue(source.contains("lastRequiresFullWidthBgTilemap = requiresFullWidthBgTilemap"),
                "The last built full-width mode must be recorded after a BG rebuild.");
    }

    private static String methodBody(String text, String signature) {
        int start = text.indexOf(signature);
        assertTrue(start >= 0, "Missing method signature: " + signature);
        int brace = text.indexOf('{', start);
        assertTrue(brace >= 0, "Missing method body: " + signature);
        int depth = 0;
        for (int i = brace; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(brace + 1, i);
                }
            }
        }
        throw new AssertionError("Unclosed method body: " + signature);
    }
}
