package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.internal.RetainedPlaneDescriptorSource;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Per-view plane identity through the production tile path, including ordinary draw recovery. */
@Isolated
class TestNativeCompetitionViewPlanes {
    @Test @RequiresRom(SonicGame.SONIC_2)
    void viewsSelectTheirOwnForegroundAndShareBackgroundWithoutLeavingAnOverride() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        GameServices.audio().setBackend(new com.openggf.audio.NullAudioBackend());
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        fixture.stepIdleFrames(2);
        try (var gl = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
             var renderer = new NativeCompetitionRenderer();
             AutoCloseable cleanup = () -> {
                 try { SessionManager.closeGameplaySession(); }
                 finally { GameServices.graphics().cleanup(); }
             }) {
            var context = SessionManager.getCurrentGameplayMode();
            var lm = context.getLevelManager();
            var camera = context.getCamera().capture();
            int frame = lm.getFrameCounter();
            var gameState = context.getGameStateManager().capture();
            var graphics = GameServices.graphics();
            graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.bindRuntimeManagedReferences(context.getCamera(), context.getFadeManager());
            graphics.setViewport(0, 0, 320, 224);
            glViewport(0, 0, 320, 224);
            LevelGraphicsPublisher.publish(lm.getCurrentLevel(), graphics);
            for (int id = 1; id <= 3; id++) {
                var pattern = new Pattern();
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                    pattern.setPixel(x, y, (byte) (id == 3 || x < 4 ? id : 0));
                graphics.cachePatternTexture(pattern, id);
            }
            var palette = new Palette();
            palette.setColor(1, new Palette.Color((byte) 255, (byte) 0, (byte) 0));
            palette.setColor(2, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
            palette.setColor(3, new Palette.Color((byte) 0, (byte) 0, (byte) 255));
            graphics.cachePaletteTexture(palette, 0);
            lm.tilemapManager.setPatternLookupDirty(true);
            var ordinary = new NativeCompetitionRenderer.View(camera, 0, 224, 0, 0, 0, 512,
                    new int[224], SpritePresentation.Frame.empty());
            var before = draw(renderer, List.of(ordinary));
            var explicitNull = new NativeCompetitionRenderer.View(camera, 0, 224, 0, 0, 0, 512,
                    new int[224], SpritePresentation.Frame.empty(), null);
            equalPixels(before, draw(renderer, List.of(explicitNull)));
            var background = plane(3);
            var first = source(plane(1), background);
            var second = source(plane(2), background);
            var top = new NativeCompetitionRenderer.View(camera, 0, 108, 0, 0, 0, 512,
                    new int[224], SpritePresentation.Frame.empty(), first);
            var bottom = new NativeCompetitionRenderer.View(camera, 108, 116, 0, 0, 0, 512,
                    new int[224], SpritePresentation.Frame.empty(), second);
            var image = draw(renderer, List.of(top, bottom));
            for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
                int expected = (x & 7) < 4 ? (y < 108 ? 0xFFFF0000 : 0xFF00FF00) : 0xFF0000FF;
                assertEquals(expected, image.argb(x, y), "view pixel " + x + "," + y);
            }
            assertNull(lm.tilemapManager.retainedPlaneOverride);
            equalPixels(before, draw(renderer, List.of(ordinary)));
            // Exception recovery also restores scoped plane ownership.
            assertThrows(IllegalStateException.class, () -> lm.spritePresentationRenderer()
                    .withNativeCompetitionView(top, () -> { throw new IllegalStateException("draw failed"); }));
            assertNull(lm.tilemapManager.retainedPlaneOverride);
            equalPixels(before, draw(renderer, List.of(ordinary)));
            assertEquals(camera, context.getCamera().capture());
            assertEquals(frame, lm.getFrameCounter());
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey("game-state", gameState,
                    context.getGameStateManager().capture()).isEmpty());
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            TestEnvironment.resetAll();
        }
    }

    private static RetainedPlaneDescriptorSource.Plane plane(int word) {
        return new RetainedPlaneDescriptorSource.Plane() {
            public int widthTiles() { return 64; }
            public int heightTiles() { return 32; }
            public long revision() { return 1; }
            public int descriptorAt(int x, int y) { return word; }
        };
    }
    private static RetainedPlaneDescriptorSource source(RetainedPlaneDescriptorSource.Plane foreground,
                                                        RetainedPlaneDescriptorSource.Plane background) {
        return new RetainedPlaneDescriptorSource() {
            public Plane foregroundPlane() { return foreground; }
            public Plane backgroundPlane() { return background; }
        };
    }
    private static RgbaImage draw(NativeCompetitionRenderer renderer, List<NativeCompetitionRenderer.View> views) {
        PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(),
                () -> renderer.draw(SessionManager.getCurrentGameplayMode(), views, 0x123456));
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
    private static void equalPixels(RgbaImage expected, RgbaImage actual) {
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++)
            assertEquals(expected.argb(x, y), actual.argb(x, y), "ordinary pixel " + x + "," + y);
    }
}
