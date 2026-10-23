package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.*;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

@Isolated
class TestNativeCompetitionViews {
    private static final int BASE = 0x4F100;

    @Test @RequiresRom(SonicGame.SONIC_2)
    void independentNativePagesCompositeTheirActualTerrainRowsWithoutAdvancingTheWorld() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings);
             var renderer = new NativeCompetitionRenderer()) {
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0, settings);
            session.step(null); session.step(null);
            var context = SessionManager.getCurrentGameplayMode();
            var graphics = GameServices.graphics();
            var camera = context.getCamera();
            var initialCamera = camera.capture();
            int initialFrame = context.getLevelManager().getFrameCounter();
            var gameState = context.getGameStateManager().capture();
            var tile = new Pattern();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) tile.setPixel(x, y, (byte) 15);
            graphics.cachePatternTexture(tile, BASE);
            var topSprites = NativeCompetitionRenderer.prepareSprites(graphics, 0, 108,
                    () -> pieces(graphics, 24));
            var bottomSprites = NativeCompetitionRenderer.prepareSprites(graphics, 108, 116,
                    () -> pieces(graphics, 132));
            assertEquals(20, topSprites.tiles().size());
            assertEquals(20, bottomSprites.tiles().size(), "each native table has its own scanline budget");
            var second = new Camera();
            second.restore(initialCamera, camera.getFocusedSprite());
            second.setX((short) (camera.getX() + 128));
            int[] topScroll = scroll(camera.getX()), bottomScroll = scroll(second.getX());
            var top = new NativeCompetitionRenderer.View(initialCamera, 0, 108, camera.getY(), 0, 0, 512,
                    topScroll, topSprites);
            var bottom = new NativeCompetitionRenderer.View(second.capture(), 108, 116, camera.getY() - 112, 0,
                    0, 512, bottomScroll, bottomSprites);
            var fullTop = new NativeCompetitionRenderer.View(initialCamera, 0, 224, top.foregroundVScroll(), 0,
                    0, 512, topScroll, topSprites);
            var fullBottom = new NativeCompetitionRenderer.View(second.capture(), 0, 224, bottom.foregroundVScroll(), 0,
                    0, 512, bottomScroll, bottomSprites);
            var expectedTop = draw(renderer, List.of(fullTop));
            var expectedBottom = draw(renderer, List.of(fullBottom));
            glEnable(GL_SCISSOR_TEST);
            glScissor(7, 9, 11, 13);
            RgbaImage actual;
            try (var ignored = GraphicsProjectionAccess.openProjection(graphics, graphics.getProjectionSource())) {
                float[] override = new org.joml.Matrix4f().identity().get(new float[16]);
                graphics.setProjectionMatrixBuffer(override);
                actual = draw(renderer, List.of(top, bottom));
                assertSame(override, graphics.getProjectionMatrixBuffer(),
                        "native drawing restores the exact local matrix override after using its own projection");
            }
            assertTrue(glIsEnabled(GL_SCISSOR_TEST));
            int[] scissor = new int[4]; glGetIntegerv(GL_SCISSOR_BOX, scissor);
            assertArrayEquals(new int[]{7, 9, 11, 13}, scissor);
            glDisable(GL_SCISSOR_TEST);
            for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
                assertEquals((y < 108 ? expectedTop : expectedBottom).argb(x, y), actual.argb(x, y),
                        "native raster " + x + "," + y);
            }
            assertEquals(initialCamera, camera.capture());
            assertEquals(initialFrame, context.getLevelManager().getFrameCounter());
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey("game-state", gameState,
                    context.getGameStateManager().capture()).isEmpty());
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test void scrollPagesAreImmutableAndInvalidNativeRastersFailBeforeDrawing() {
        var camera = new Camera().capture();
        var words = new int[224];
        var view = new NativeCompetitionRenderer.View(camera, 108, 116, 0, 0, 0, 512, words,
                SpritePresentation.Frame.empty());
        words[108] = 3;
        assertEquals(0, view.horizontalScroll()[108]);
        view.horizontalScroll()[108] = 7;
        assertEquals(0, view.horizontalScroll()[108]);
        assertThrows(IllegalArgumentException.class, () -> new NativeCompetitionRenderer.View(camera, 108, 117,
                0, 0, 0, 512, words, SpritePresentation.Frame.empty()));
    }

    private static int[] scroll(int x) {
        var words = new int[224]; Arrays.fill(words, ((-x & 0xFFFF) << 16)); return words;
    }
    private static void pieces(GraphicsManager graphics, int y) {
        graphics.setUseSpritePriorityShader(true);
        for (int i = 0; i < 21; i++) graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                i * 8, y, 1, 1, BASE, 0, 0, false, false, true, true,
                SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, null));
    }
    private static RgbaImage draw(NativeCompetitionRenderer renderer, List<NativeCompetitionRenderer.View> views) {
        PaletteUploadPresentation.withoutDisplayedFade(GameServices.graphics(),
                () -> renderer.draw(SessionManager.getCurrentGameplayMode(), views, 0x123456));
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
}
