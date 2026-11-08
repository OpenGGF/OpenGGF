package com.openggf.widescreen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * White-box guard for S2 title-card widescreen wiring.
 *
 * <p>A previous merge dropped the actual TitleCardManager calls while tests
 * of copied arithmetic stayed green. Those arithmetic-only tests were retired.
 * This guard pins the manager to the viewport-aware helpers and edge-margin
 * plumbing that keep the 320-space composition centered and fully clearable.
 */
class TestS2TitleCardManagerWidescreenGuard {

    private static String source() throws IOException {
        return Files.readString(Path.of(
                "src/main/java/com/openggf/game/sonic2/titlecard/TitleCardManager.java"));
    }

    @Test
    void managerKeepsViewportAwareCenteringHelpers() throws IOException {
        String src = source();

        assertTrue(src.contains("private int viewportWidth()"),
                "S2 title cards must read GraphicsManager projection width");
        assertTrue(src.contains("private int xOffset()"),
                "S2 title cards must center the native 320-wide composition");
        assertTrue(src.contains("GameServices.graphics().getProjectionWidth()"),
                "viewportWidth() must use the active projection width");
        assertTrue(src.contains("part.x()-128+xOffset()"),
                "the projection centers ROM sprite coordinates without changing Obj34 state");
    }

    @Test
    void canonicalPositionsRemainIndependentOfViewportWidth() throws IOException {
        String src = source();
        assertTrue(src.contains("obj34.consumeFills()"));
        assertTrue(src.contains("obj34.tick()"));
        assertTrue(src.contains("viewportWidth(), SCREEN_HEIGHT"));
        org.junit.jupiter.api.Assertions.assertFalse(src.contains("setEdgeMargin("),
                "viewport extensions must not change ROM coordinates or phase timing");
    }
}
