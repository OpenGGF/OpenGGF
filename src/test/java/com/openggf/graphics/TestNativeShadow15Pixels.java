package com.openggf.graphics;

import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.util.FboHelper;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.*;

/** Constrained native color-15 operators must participate in SAT admission and source ownership. */
@Isolated
class TestNativeShadow15Pixels {
    @Test
    void shadowAndNormalSourcesRespectNativeOrderHolesFlipsAndCommandReuse() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false, 0);
            for (boolean instanced : new boolean[]{false, true}) {
                fixture.batching(instanced ? 2 : 0);
                for (boolean hFlip : new boolean[]{false, true}) for (boolean vFlip : new boolean[]{false, true}) {
                    int opaqueX = hFlip ? 46 : 41, holeX = hFlip ? 41 : 46;
                    int opaqueY = vFlip ? 46 : 41, holeY = vFlip ? 41 : 46;
                    fixture.draw(() -> {
                        SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, hFlip, vFlip));
                        fixture.normal(true);
                    });
                    pixel(opaqueX, opaqueY, 18, 55, 91);
                    pixel(holeX, opaqueY, 0, 255, 0);
                    pixel(opaqueX, holeY, 0, 255, 0);
                    fixture.draw(() -> {
                        fixture.normal(true);
                        SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, hFlip, vFlip));
                    });
                    pixel(opaqueX, opaqueY, 0, 255, 0);
                    fixture.draw(() -> {
                        SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, hFlip, vFlip));
                        SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, hFlip, vFlip));
                    });
                    pixel(opaqueX, opaqueY, 18, 55, 91); // one winning source selects the same intensity
                    pixel(holeX, opaqueY, 204, 204, 204);
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void terrainHiddenShadowStillOwnsTheSourceAndHonorsTheFrozenPaletteMask() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            for (boolean instanced : new boolean[]{false, true}) {
                fixture.batching(instanced ? 2 : 0);
                fixture.terrain(true, 0);
                fixture.draw(() -> {
                    SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, false, false));
                    fixture.normal(true);
                });
                pixel(41, 41, 204, 204, 204);
                pixel(46, 41, 0, 255, 0);
                fixture.draw(() -> {
                    fixture.normal(false);
                    SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, true, false, false));
                });
                pixel(41, 41, 204, 204, 204); // hidden earlier ordinary sprite also excludes the operator
                fixture.draw(() -> {
                    SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, true, false, false));
                    fixture.normal(true);
                });
                pixel(41, 41, 18, 55, 91);
                fixture.terrain(true, 3);
                fixture.draw(() -> {
                    fixture.graphics.setCurrentSpriteTileOcclusionPaletteMask(0b0111);
                    SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, false, false));
                    fixture.graphics.setCurrentSpriteTileOcclusionPaletteMask(0xF);
                    fixture.normal(true);
                });
                pixel(41, 41, 18, 55, 91);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void groupedOperatorKeepsItsImmutableArtExactAdmittedRowsAndFlipsInEveryPresentationBatchMode() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false, 0);
            for (int mode = 0; mode < 3; mode++) {
                fixture.batching(mode);
                for (boolean hFlip : new boolean[]{false, true}) for (boolean vFlip : new boolean[]{false, true}) {
                    var nativeGeometry = SpriteSatEntry.of(40, 40, 1, 1, 0, 3, false, false, false, false)
                            .withVisibleScanlines(42, 46);
                    var frame = SpritePresentationRenderer.prepare(fixture.graphics, 0, 0, () -> {
                        fixture.graphics.beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.h32(0, 0, 224));
                        SpriteSatInternalAccess.submitShadowGroups(fixture.graphics, List.of(nativeGeometry),
                                () -> fixture.graphics.submitSpriteSatPiece(piece(3, 3, false, hFlip, vFlip)));
                        fixture.normal(true);
                        fixture.graphics.endSpriteSatCollectionAndReplay();
                    });
                    var operators = frame.tiles().stream()
                            .filter(tile -> tile.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15).toList();
                    assertEquals(1, operators.size());
                    assertEquals(2, operators.getFirst().rowStart());
                    assertEquals(6, operators.getFirst().rowEnd());
                    assertNotNull(frame.patternVersions().get(3), "operator source is frozen with its published frame");
                    // A later upload must not change the captured operator source or trigger source-14 rejection.
                    fixture.graphics.cachePatternTexture(solid(14), 3);
                    fixture.clear();
                    fixture.graphics.executeCapturedCommands(() -> fixture.withBackdrop(() ->
                            SpritePresentationRenderer.drawNative(fixture.graphics, frame, 0, 0, layer -> true)),
                            0, 0, 320, 224);
                    int opaqueX = hFlip ? 46 : 41, holeX = hFlip ? 41 : 46;
                    pixel(opaqueX, vFlip ? 44 : 43, 18, 55, 91);
                    pixel(holeX, vFlip ? 44 : 43, 0, 255, 0);
                    pixel(opaqueX, vFlip ? 43 : 44, 0, 255, 0);
                    pixel(opaqueX, 41, 0, 255, 0);
                    pixel(opaqueX, 46, 0, 255, 0);
                    fixture.graphics.cachePatternTexture(shadow(), 3);
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void liveHeadReplayAndAnatomyCopyRetainTheConstrainedOperator() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false, 0);
            var mask = new SpritePresentation.HeadMask(0, 0, java.util.Set.of(), List.of(), List.of());
            var head = new SpritePresentation.HeadTransform(mask, 0, 40, 40, false, false,
                    200, 0, java.util.Map.of(3, SpritePresentation.patternSample(fixture.graphics, 3)),
                    SpritePresentationRenderer::replayHeadSat);
            var subject = new SpritePresentation.Subject("shadow-copy", SpritePresentation.Part.BODY,
                    false, 200, head);
            fixture.draw(() -> {
                SpritePresentation.withSubject(fixture.graphics, subject, () ->
                        SpriteSatInternalAccess.submitShadowPiece(fixture.graphics,
                                piece(3, 3, false, false, false)));
                fixture.normal(true);
            });
            pixel(41, 41, 18, 55, 91);
            pixel(46, 41, 0, 255, 0);
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void indexedOperatorNeedsNoPaletteResidencyInEitherDirectOrDedicatedInstancedReplay() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false, 0);
            fixture.graphics.clearPaletteTextures();
            for (boolean instanced : new boolean[]{false, true}) {
                fixture.batching(instanced ? 2 : 0);
                fixture.draw(() -> {
                    SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, false, false));
                    fixture.normal(true); // ordinary art cannot render without a palette
                });
                pixel(41, 41, 18, 55, 91);
                pixel(46, 41, 204, 204, 204);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void logicalBackdropSamplesTheDrawViewportAtTwoScaledPixelOrigins() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false, 0);
            glBindFramebuffer(GL_FRAMEBUFFER, fixture.backdrop.fboId());
            glEnable(GL_SCISSOR_TEST);
            glScissor(41, 223 - 41, 1, 1);
            glClearColor(91 / 255f, 18 / 255f, 55 / 255f, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            glDisable(GL_SCISSOR_TEST);
            var scene = FboHelper.createColorOnly(700, 500, GL_CLAMP_TO_EDGE);
            assertNotNull(scene);
            int foreignSampler = glGenSamplers();
            glSamplerParameteri(foreignSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glSamplerParameteri(foreignSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glBindSampler(6, foreignSampler);
            try {
                for (int[] origin : new int[][]{{16, 12}, {32, 20}}) {
                    glBindFramebuffer(GL_FRAMEBUFFER, scene.fboId());
                    glClearColor(0.8f, 0.8f, 0.8f, 1);
                    glClear(GL_COLOR_BUFFER_BIT);
                    glViewport(origin[0], origin[1], 640, 448);
                    fixture.graphics.setViewport(origin[0], origin[1], 640, 448);
                    fixture.graphics.executeCapturedCommands(() -> SpriteSatInternalAccess.withShadow15Backdrop(
                            fixture.graphics, fixture.backdrop.textureId(), origin[0], origin[1], 640, 448, () -> {
                                fixture.graphics.beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.h32(0, 0, 224));
                                SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, false, false));
                                fixture.normal(true);
                                fixture.graphics.endSpriteSatCollectionAndReplay();
                            }), 0, 0, 320, 224);
                    pixelWindow(origin[0] + 83, origin[1] + 365, 91, 18, 55);
                    pixelWindow(origin[0] + 85, origin[1] + 363, 18, 55, 91);
                    pixelWindow(origin[0] + 93, origin[1] + 365, 0, 255, 0);
                    assertNull(fixture.graphics.currentShadow15Backdrop, "sampler scope ended before command execution");
                    assertEquals(foreignSampler, glGetIntegeri(GL_SAMPLER_BINDING, 6));
                }
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                fixture.graphics.setViewport(0, 0, 320, 224);
                glViewport(0, 0, 320, 224);
                glBindSampler(6, 0);
                glDeleteSamplers(foreignSampler);
                FboHelper.destroy(scene);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void missingAndFeedbackBackdropsFailExplicitlyAndUnwindNativeCoverage() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.terrain(false, 0);
            fixture.clear();
            Runnable table = () -> {
                fixture.graphics.beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.h32(0, 0, 224));
                SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, false, false));
                fixture.graphics.endSpriteSatCollectionAndReplay();
            };
            int foreignSampler = glGenSamplers();
            glSamplerParameteri(foreignSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glSamplerParameteri(foreignSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glBindSampler(6, foreignSampler);
            try {
                assertTrue(assertThrows(IllegalStateException.class, () ->
                        fixture.graphics.executeCapturedCommands(table, 0, 0, 320, 224))
                        .getMessage().contains("live native shadow backdrop"));
                assertFalse(fixture.graphics.nativeSpriteOwnershipActive());
                glBindFramebuffer(GL_FRAMEBUFFER, fixture.backdrop.fboId());
                assertTrue(assertThrows(IllegalStateException.class, () -> fixture.graphics.executeCapturedCommands(
                        () -> fixture.withBackdrop(table), 0, 0, 320, 224))
                        .getMessage().contains("distinct from the draw target"));
                assertEquals(fixture.backdrop.fboId(), glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER));
                assertFalse(fixture.graphics.nativeSpriteOwnershipActive());
                // Fail after the command has unbound the foreign sampler, so unwind
                // must restore it rather than merely leave an untouched binding alone.
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, fixture.backdrop.textureId());
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                assertTrue(assertThrows(IllegalStateException.class, () -> fixture.graphics.executeCapturedCommands(
                        () -> fixture.withBackdrop(table), 0, 0, 320, 224))
                        .getMessage().contains("nearest texture filtering"));
                assertEquals(foreignSampler, glGetIntegeri(GL_SAMPLER_BINDING, 6));
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, fixture.backdrop.textureId());
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                fixture.draw(() -> SpriteSatInternalAccess.submitShadowPiece(fixture.graphics, piece(3, 3, false, false, false)));
                pixel(41, 41, 18, 55, 91);
                assertEquals(foreignSampler, glGetIntegeri(GL_SAMPLER_BINDING, 6));
                assertEquals(GL_LINEAR_MIPMAP_LINEAR, glGetSamplerParameteri(foreignSampler, GL_TEXTURE_MIN_FILTER));
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { glBindSampler(6, 0); glDeleteSamplers(foreignSampler); }
        }
    }

    @Test
    void scopedNativeShadowPaletteTracksItsNativeSourceFadeBeforeIntensityAndExpansion() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            var owner = new Object();
            PaletteView[] recipes = new PaletteView[4];
            for (int line = 0; line < 4; line++) {
                var source = new Palette();
                source.getColor(1).fromSegaFormat(0xEEE);
                recipes[line] = NativeIntensityPalette.shadow(source, line);
            }
            fixture.graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.TO_BLACK, 1, 1 << 2);
            var bank = ScopedPaletteGraphicsAccess.acquire(fixture.graphics, owner, recipes);
            try {
                int base = ScopedPaletteGraphicsAccess.base(bank, fixture.graphics, owner);
                assertArrayEquals(new int[]{109, 128, 128, 255}, paletteColor(fixture.graphics, base + 2, 1));
                assertArrayEquals(new int[]{128, 128, 128, 255}, paletteColor(fixture.graphics, base + 1, 1));
                fixture.graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.TO_BLACK, 2, 1 << 2);
                assertArrayEquals(new int[]{91, 128, 128, 255}, paletteColor(fixture.graphics, base + 2, 1));
                fixture.graphics.setDisplayColorProfile(com.openggf.graphics.color.DisplayColorProfile.MD_ANALOG);
                assertArrayEquals(new int[]{57, 99, 99, 255}, paletteColor(fixture.graphics, base + 2, 1),
                        "an acquired intensity bank follows a changed profile without reacquisition");
                fixture.graphics.setDisplayColorProfile(com.openggf.graphics.color.DisplayColorProfile.RAW_RGB);
                fixture.graphics.setDisplayColorExpansion(DisplayColorExpansion.LIMITED);
                assertArrayEquals(new int[]{85, 119, 119, 255}, paletteColor(fixture.graphics, base + 2, 1));
                assertEquals(0, paletteColor(fixture.graphics, base + 2, 0)[3]);
            } finally { ScopedPaletteGraphicsAccess.release(bank, owner); }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void constrainedShadowRejectsColor14OtherColorsMissingArtAndOtherPaletteLines() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.initHeadless();
        try {
            Pattern art = solid(14);
            graphics.cachePatternTexture(art, 3);
            // The resident immutable sample, rather than this later-mutated object, owns validation.
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) art.setPixel(x, y, (byte) 15);
            graphics.cachePatternTexture(solid(1), 4);
            graphics.beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.h32(0, 0, 224));
            assertTrue(assertThrows(IllegalArgumentException.class, () ->
                    SpriteSatInternalAccess.submitShadowPiece(graphics, piece(3, 3, false, false, false)))
                    .getMessage().contains("color 14"));
            assertThrows(IllegalArgumentException.class, () ->
                    SpriteSatInternalAccess.submitShadowPiece(graphics, piece(4, 3, false, false, false)));
            assertThrows(IllegalArgumentException.class, () ->
                    SpriteSatInternalAccess.submitShadowPiece(graphics, piece(99, 3, false, false, false)));
            graphics.updatePatternTexture(art, 3);
            assertThrows(IllegalArgumentException.class, () ->
                    SpriteSatInternalAccess.submitShadowPiece(graphics, piece(3, 0, false, false, false)));
            SpriteSatInternalAccess.submitShadowPiece(graphics, piece(3, 3, false, false, false));
            assertEquals(SpriteSatEntry.PixelOperation.NORMAL, graphics.currentSpritePixelOperation,
                    "successful and rejected submissions restore the operation scope");
        } finally {
            graphics.cancelSpritePresentationCollection();
            graphics.cleanup();
        }
    }

    private static SpritePieceRenderer.PreparedPiece piece(int pattern, int palette, boolean high,
                                                           boolean hFlip, boolean vFlip) {
        return new SpritePieceRenderer.PreparedPiece(40, 40, 1, 1, pattern, pattern, palette, hFlip, vFlip,
                high, false, SpriteMaskReplayRole.NORMAL, 0, 1, 0, 1, null);
    }

    private static Pattern solid(int index) {
        Pattern pattern = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) index);
        return pattern;
    }

    private static Pattern shadow() {
        Pattern pattern = new Pattern();
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) pattern.setPixel(x, y, (byte) 15);
        return pattern;
    }

    private static void pixel(int x, int y, int red, int green, int blue) {
        pixelWindow(x, 223 - y, red, green, blue);
    }

    private static void pixelWindow(int x, int y, int red, int green, int blue) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(4);
        glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        assertEquals(red, bytes.get(0) & 255, "red at " + x + "," + y);
        assertEquals(green, bytes.get(1) & 255, "green at " + x + "," + y);
        assertEquals(blue, bytes.get(2) & 255, "blue at " + x + "," + y);
    }

    private static int[] paletteColor(GraphicsManager graphics, int line, int index) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(16 * RenderContext.getTotalPaletteLines() * 4);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, graphics.getCombinedPaletteTextureId());
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        int offset = (line * 16 + index) * 4;
        return new int[]{bytes.get(offset) & 255, bytes.get(offset + 1) & 255,
                bytes.get(offset + 2) & 255, bytes.get(offset + 3) & 255};
    }

    private static final class Fixture implements AutoCloseable {
        final PresentationOffscreenTestContext context = PresentationOffscreenTestContext.open(320, 224);
        final GraphicsManager graphics = new GraphicsManager();
        FboHelper.FboHandle backdrop;
        Fixture() throws Exception {
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, 320, 224);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
                Palette palette = new Palette();
                palette.setColor(2, new Palette.Color((byte) 0, (byte) 255, (byte) 0));
                for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(palette, line);
                graphics.cachePatternTexture(solid(2), 2);
                graphics.cachePatternTexture(shadow(), 3);
                backdrop = FboHelper.createColorOnly(320, 224, GL_CLAMP_TO_EDGE);
                assertNotNull(backdrop);
                glBindFramebuffer(GL_FRAMEBUFFER, backdrop.fboId());
                glViewport(0, 0, 320, 224);
                glClearColor(18 / 255f, 55 / 255f, 91 / 255f, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
            } catch (Exception | Error failure) { close(); throw failure; }
        }
        void batching(int mode) {
            graphics.setBatchingEnabled(mode != 0);
            graphics.setInstancedBatchingEnabled(mode == 2);
        }
        void terrain(boolean high, int paletteLine) {
            TilePriorityFBO priority = graphics.getTilePriorityFBO(320, 224);
            assertNotNull(priority);
            glDisable(GL_SCISSOR_TEST);
            priority.begin();
            glClearColor(high ? 1 : 0, paletteLine / 3f, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            priority.end();
        }
        void clear() {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, 320, 224);
            glDisable(GL_DITHER); glDisable(GL_SCISSOR_TEST); glDisable(GL_STENCIL_TEST); glDisable(GL_DEPTH_TEST);
            glClearColor(0.8f, 0.8f, 0.8f, 1); glClear(GL_COLOR_BUFFER_BIT);
        }
        void normal(boolean high) {
            graphics.submitSpriteSatPiece(piece(2, 0, high, false, false));
        }
        void draw(Runnable producer) {
            clear();
            graphics.executeCapturedCommands(() -> withBackdrop(() -> {
                graphics.beginSpriteSatCollection(SpriteSatMaskPostProcessor.Raster.h32(0, 0, 224));
                producer.run();
                graphics.endSpriteSatCollectionAndReplay();
            }), 0, 0, 320, 224);
        }
        void withBackdrop(Runnable table) {
            SpriteSatInternalAccess.withShadow15Backdrop(graphics, backdrop.textureId(), 0, 0, 320, 224, table);
        }
        @Override public void close() {
            try {
                graphics.cleanup(); RenderContext.reset();
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                FboHelper.destroy(backdrop);
            } finally { context.close(); }
        }
    }
}
