package com.openggf.graphics;

import com.openggf.Engine;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

class TestTestReelPatternTransfer {
    @Test void alreadyCachedPixelsAndAliasesSurviveNewGlAtlasWithoutReloadingOwners() throws Exception {
        TestEnvironment.resetAll();
        var graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        int[] ids = {4, PatternAtlasRange.OBJECTS.base(), PatternAtlasRange.HUD.base(),
                PatternAtlasRange.WATER_SURFACE.base(), PatternAtlasRange.SIDEKICK_BANKS.base()};
        var cached = new Pattern[ids.length];
        for (int i = 0; i < ids.length; i++) {
            cached[i] = solid(i + 1);
            graphics.cachePatternTexture(cached[i], ids[i]);
        }
        graphics.getPatternAtlas().registerRange(PatternAtlasRange.OBJECTS);
        assertTrue(graphics.getPatternAtlas().aliasEntry(0x2001, ids[1]));
        var art = TestReelPatternTransfer.capture(graphics);
        // Reloading mutable source sheets would now yield different pixels.
        cached[1].setPixel(0, 0, (byte) 14);
        try (var context = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            graphics.setHeadlessMode(false);
            try {
                graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
                assertNull(graphics.getPatternAtlasEntry(ids[1]), "new GL atlas reproduces the recorder gap");
                art.upload(graphics);
                for (int i = 0; i < ids.length; i++) assertGpuTile(graphics, ids[i], i + 1);
                assertGpuTile(graphics, 0x2001, 2);
                assertEquals(graphics.getPatternAtlasEntry(ids[1]).slot(),
                        graphics.getPatternAtlasEntry(0x2001).slot(), "alias retains shared residency");
                assertEquals(1, graphics.getPatternAtlas().registeredRangesForTesting().size());
            } finally { graphics.cleanup(); }
        }
    }

    @Test void contradictoryAliasVersionsFailRatherThanInventAnUploadOrder() {
        TestEnvironment.resetAll();
        var graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        graphics.cachePatternTexture(solid(1), 4);
        assertTrue(graphics.getPatternAtlas().aliasEntry(5, 4));
        graphics.cachePatternTexture(solid(2), 5);
        assertThrows(IllegalStateException.class, () -> TestReelPatternTransfer.capture(graphics));
    }

    private static Pattern solid(int value) {
        var pattern = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) value);
        return pattern;
    }
    private static void assertGpuTile(GraphicsManager graphics, int id, int expected) {
        var entry = graphics.getPatternAtlasEntry(id);
        assertNotNull(entry);
        int width = graphics.getPatternAtlasWidth(), height = graphics.getPatternAtlasHeight();
        var pixels = BufferUtils.createByteBuffer(width * height);
        glBindTexture(GL_TEXTURE_2D, graphics.getPatternAtlasTextureId(entry.atlasIndex()));
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_BYTE, pixels);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            assertEquals(expected, pixels.get((entry.tileY() * 8 + y) * width + entry.tileX() * 8 + x) & 255,
                    "GPU pixels for existing tile " + id);
    }
}
