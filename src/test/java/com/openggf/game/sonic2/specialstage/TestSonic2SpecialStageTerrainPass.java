package com.openggf.game.sonic2.specialstage;

import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GraphicsProjectionAccess;
import com.openggf.graphics.PaletteFadePresentation;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.PatternRenderCommand;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.RenderProjection;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.graphics.WaterShaderProgram;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.util.FboHelper;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.*;

/** Real-GL component evidence; full production boot/SAT integration is checked by its owning tests. */
@Isolated
class TestSonic2SpecialStageTerrainPass {
    @Test
    void headlessCleanupClosesEveryCpuConstructionScopeAndRestoresTheExactProjectionTuple() throws Exception {
        var graphics = new GraphicsManager();
        graphics.initHeadless();
        float[] priorMatrix = new Matrix4f().ortho2D(0, 400, 0, 333).get(new float[16]);
        RenderProjection priorSource = new RenderProjection() {
            @Override public float[] getProjectionMatrixBuffer() { return priorMatrix; }
            @Override public boolean isFBOProjectionActive() { return true; }
            @Override public int getCurrentDisplayHeight() { return 333; }
        };
        GraphicsProjectionAccess.install(graphics, priorSource);
        graphics.setProjectionMatrixBuffer(priorMatrix);
        var background = new SpecialStageBackgroundRenderer(graphics);
        try {
            background.beginFBOProjection();
            assertEquals(256, graphics.getProjectionSource().getCurrentDisplayHeight());
            assertNull(field(graphics, "projectionMatrixBuffer"));
            background.beginFBOProjection();
            background.cleanup();
            assertSame(priorSource, graphics.getProjectionSource());
            assertSame(priorMatrix, field(graphics, "projectionMatrixBuffer"));
            assertFalse(background.hasCleanupPendingOwnership());
        } finally { background.cleanup(); graphics.cleanup(); RenderContext.reset(); }
    }

    @Test
    void abandonedOrFailedStaticBuildInvalidatesItsCacheButSuccessfulEndCommitsIt() {
        var pool = new Sonic2SpecialStageManager.BackgroundCommandPool();
        var background = mock(SpecialStageBackgroundRenderer.class);
        int[] invalidations = {0};
        var discarded = pool.obtainEnd(background, () -> invalidations[0]++);
        discarded.discard();
        discarded.discard();
        assertEquals(1, invalidations[0]);
        pool.obtainBegin(background).execute(0, 0, 320, 224);
        pool.obtainEnd(background, () -> invalidations[0]++).unwindAfterFailure(0, 0, 320, 224);
        assertEquals(2, invalidations[0], "restoring the target does not certify partially drawn pixels");
        pool.obtainBegin(background).execute(0, 0, 320, 224);
        pool.obtainEnd(background, () -> invalidations[0]++).execute(0, 0, 320, 224);
        assertEquals(2, invalidations[0], "a completed build commits the static image");
    }

    @Test
    void projectedBackgroundScrollAndTwoScanlineTrackStripsShareTheNativeH32Origin() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            for (int mode = 0; mode < 3; mode++) {
                fixture.graphics.setBatchingEnabled(mode != 0);
                fixture.graphics.setInstancedBatchingEnabled(mode == 2);
                fixture.pass.configure(fixture.viewport, 100, 200, mode + 1);
                fixture.draw(true, true);
                int left = fixture.viewport.innerH32OriginX();
                fixture.pixel(left + 17, 1, 109, 55, 36);
                fixture.pixel(left + 25, 1, 36, 109, 73);
                fixture.pixel(left + 10, 0, 18, 91, 128);
                fixture.pixel(left + 10, 1, 18, 91, 128);
                fixture.pixel(left + 10, 2, 109, 55, 36);
                fixture.pixel(left + 26, 2, 18, 91, 128);
                fixture.pixel(left + 26, 3, 18, 91, 128);
                fixture.pixel(left + 26, 4, 36, 109, 73);
                fixture.pixel(left - 1, 4, 0, 0, 0);
                fixture.pixel(left + 256, 4, 0, 0, 0);
            }
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    @Test
    void staticRgbaRebuildsForLiveSourceProfileExpansionAndDisplayedSourceLineFade() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            int x = fixture.viewport.innerH32OriginX() + 25;
            fixture.draw(true, false);
            fixture.pixel(x, 1, 36, 109, 73);
            fixture.palettes[0].getColor(2).fromSegaFormat((1 << 9) | (3 << 5) | (7 << 1));
            fixture.draw(true, false);
            fixture.pixel(x, 1, 128, 55, 18);
            fixture.graphics.setDisplayColorProfile(DisplayColorProfile.NTSC_SOFT);
            fixture.draw(true, false);
            fixture.pixel(x, 1, 86, 35, 19);
            fixture.graphics.setDisplayColorExpansion(DisplayColorExpansion.LIMITED);
            fixture.draw(true, false);
            fixture.pixel(x, 1, 80, 33, 18);
            fixture.graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
            fixture.graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
            PaletteUploadPresentation.publishAndLatch(fixture.graphics);
            fixture.graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.TO_BLACK, 1, 1);
            fixture.draw(true, false);
            fixture.pixel(x, 1, 128, 55, 18, "a requested fade waits for its actual CRAM publication");
            PaletteUploadPresentation.publishAndLatch(fixture.graphics);
            fixture.draw(true, false);
            fixture.pixel(x, 1, 109, 55, 18, "native red-channel fade precedes shadow DAC expansion");
            assertEquals(255, fixture.palettes[0].getColor(2).r & 255, "source palette remains unfaded");
        }
    }

    @Test
    void inheritedWaterCannotTintTheCompanionOrNativeTableAndCallerFlagsRestore() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            var graphics = fixture.graphics;
            for (int line = 0; line < 4; line++) graphics.cachePaletteTexture(fixture.palettes[line], line);
            Palette[] underwater = {new Palette(), new Palette(), new Palette(), new Palette()};
            for (Palette line : underwater)
                for (int color = 0; color < 16; color++) line.getColor(color).fromSegaFormat(0x0E0E);
            assertNotNull(graphics.cacheUnderwaterPaletteTexture(underwater, fixture.palettes[0]));
            int left = fixture.viewport.innerH32OriginX();
            for (int mode = 0; mode < 3; mode++) {
                graphics.setBatchingEnabled(mode != 0);
                graphics.setInstancedBatchingEnabled(mode == 2);
                fixture.pass.configure(fixture.viewport, 100, 200, mode + 20);
                graphics.setWaterEnabled(true);
                graphics.setUseUnderwaterPaletteForBackground(true);
                graphics.setWaterlineScreenY(-1);
                fixture.draw(true, true);
                assertTrue(graphics.isWaterEnabled(), "isolated companion restores its caller's water flag");
                assertTrue(graphics.isUseUnderwaterPaletteForBackground());
                fixture.pixel(left + 17, 1, 109, 55, 36, "BG stays native shadow under inherited water");
                fixture.pixel(left + 10, 0, 18, 91, 128, "track stays native shadow under inherited water");
                fixture.pixel(left + 34, 0, 0, 0, 0, "native backdrop color0 remains its normal source");

                // The negative control proves this underwater CRAM and above-screen
                // waterline actually tint native SAT replay before applying the stage boundary.
                for (boolean stageBoundary : new boolean[] {false, true}) {
                    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fixture.draw.fboId());
                    glViewport(0, 0, 400, 224);
                    glDisable(GL_SCISSOR_TEST); glDisable(GL_DEPTH_TEST); glDisable(GL_STENCIL_TEST);
                    glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
                    publishForeignWater(graphics);
                    graphics.executeCapturedCommands(() -> {
                        if (stageBoundary) {
                            graphics.registerCommand((x, y, width, height) -> publishForeignWater(graphics));
                            queueNativeNoWaterPresentation(graphics);
                            assertFalse(graphics.isWaterEnabled(), "CPU stage construction uses normal CRAM");
                            assertFalse(graphics.isUseUnderwaterPaletteForBackground());
                            assertFalse(graphics.isUseSpritePriorityShader());
                            assertFalse(graphics.getShaderProgram() instanceof WaterShaderProgram);
                        }
                        SpriteSatInternalAccess.beginCollection(graphics,
                                SpriteSatMaskPostProcessor.Raster.h32(left, 0, 224));
                        graphics.setCurrentSpriteTileOcclusionPaletteMask(0);
                        graphics.submitSpriteSatPiece(new SpritePieceRenderer.PreparedPiece(
                                left + 64, 40, 1, 1, 101, 101, 0, false, false, true, true, 0, 1));
                        graphics.endSpriteSatCollectionAndReplay();
                    }, 0, 0, 400, 224);
                    if (stageBoundary) {
                        fixture.pixelFrom(fixture.draw.fboId(), left + 65, 41, 219, 109, 73,
                                "normal native body after queued old WATER publication, mode " + mode);
                        assertFalse(graphics.isWaterEnabled());
                        assertFalse(graphics.isUseUnderwaterPaletteForBackground());
                        assertFalse(graphics.isUseSpritePriorityShader());
                        assertFalse(graphics.getShaderProgram() instanceof WaterShaderProgram);
                    } else {
                        fixture.pixelFrom(fixture.draw.fboId(), left + 65, 41, 255, 0, 255,
                                "discriminating inherited-water negative control, mode " + mode);
                    }
                }
            }
            publishForeignWater(graphics);
            graphics.setUseSpritePriorityShader(false); // Expose the invalid water shader precondition.
            fixture.pass.queueBackdrop(fixture.mappings, fixture.palettes, 8, 8, 0, fixture.track, true, true);
            var invalidShader = fixture.takeCommand();
            try {
                var failure = assertThrows(IllegalStateException.class,
                        () -> invalidShader.execute(0, 0, 400, 224));
                assertTrue(failure.getMessage().contains("published normal shader"));
                assertTrue(graphics.isWaterEnabled());
                assertTrue(graphics.isUseUnderwaterPaletteForBackground());
            } finally { invalidShader.discard(); graphics.setUseWaterShader(false); }
        }
    }

    @Test
    void nativeColorZeroFillsTransparentTerrainThroughWhiteFadeAndTheSameRecipeFillsMain() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            int left = fixture.viewport.innerH32OriginX();
            fixture.draw(true, true);
            fixture.pixel(left + 34, 0, 0, 0, 0);
            fixture.graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.TO_WHITE, 1, 1);
            fixture.draw(true, true);
            fixture.pixel(left + 34, 0, 18, 0, 0,
                    "transparent BG and track texels retain native shadow color0, ignoring palette alpha0");
            fixture.pixel(left - 1, 0, 0, 0, 0, "outer H32 border stays black during the native white fade");
            assertEquals(0, fixture.palettes[0].getColor(0).r, "live owner source remains unfaded black");

            int bank = fixture.pass.preparePaletteBank(fixture.palettes);
            assertThrows(IllegalArgumentException.class, () -> fixture.pass.queueNativeBackdropFill(bank + 4));
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fixture.draw.fboId());
            glViewport(0, 0, 400, 224);
            fixture.pass.queueNativeBackdropFill(bank);
            var invalidRows = fixture.takeCommand();
            try (MockedStatic<GL11> gl11 = mockStatic(GL11.class, CALLS_REAL_METHODS)) {
                gl11.when(() -> GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT))
                        .thenReturn(bank + 3);
                assertThrows(IllegalStateException.class, () -> invalidRows.execute(0, 0, 400, 224));
                gl11.verify(() -> GL11.glDrawArrays(GL_TRIANGLE_STRIP, 0, 4), never());
            }
            fixture.pass.queueNativeBackdropFill(bank);
            var normal = fixture.takeCommand();
            normal.execute(0, 0, 400, 224);
            fixture.pixelFrom(fixture.draw.fboId(), left + 34, 0, 18, 0, 0, "MAIN native backdrop");
            fixture.pixelFrom(fixture.draw.fboId(), left - 1, 0, 0, 0, 0, "MAIN outer H32 border");
        }
    }

    @Test
    void submissionAndRenderingPreserveDistinctReadDrawTargetsAndForeignRasterState() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            float[] foreignMatrix = new Matrix4f().ortho2D(0, 400, 0, 333).get(new float[16]);
            RenderProjection foreignProjection = new RenderProjection() {
                @Override public float[] getProjectionMatrixBuffer() { return foreignMatrix; }
                @Override public boolean isFBOProjectionActive() { return true; }
                @Override public int getCurrentDisplayHeight() { return 333; }
            };
            fixture.graphics.setProjectionMatrixBuffer(null);
            GraphicsProjectionAccess.install(fixture.graphics, foreignProjection);
            fixture.foreignState();
            var before = rasterState();
            fixture.draw(true, true);
            assertEquals(before, rasterState());
            assertSame(foreignProjection, fixture.graphics.getProjectionSource());
            assertNull(field(fixture.graphics, "projectionMatrixBuffer"), "restore retains the absent local override");
            assertSame(foreignMatrix, fixture.graphics.getProjectionMatrixBuffer());
            fixture.pixel(fixture.viewport.innerH32OriginX() + 10, 0, 18, 91, 128);
            try (MockedStatic<SpriteSatInternalAccess> bridge = mockStatic(SpriteSatInternalAccess.class)) {
                bridge.when(() -> SpriteSatInternalAccess.withShadow15Backdrop(eq(fixture.graphics), anyInt(),
                        anyInt(), anyInt(), anyInt(), anyInt(), any(Runnable.class)))
                        .thenAnswer(call -> { ((Runnable) call.getArgument(6)).run(); return null; });
                boolean[] called = {false};
                fixture.pass.withBackdrop(() -> called[0] = true);
                assertTrue(called[0]);
                bridge.verify(() -> SpriteSatInternalAccess.withShadow15Backdrop(eq(fixture.graphics), anyInt(),
                        eq(13), eq(17), eq(320), eq(224), any(Runnable.class)));
            }
            assertEquals(before, rasterState());
            fixture.pass.cleanup();
            assertFalse(fixture.pass.hasCleanupPendingOwnership());
            assertEquals(before, rasterState());
        }
    }

    @Test
    void drawFailureUnwindsTheCompanionAndRetainsFailedRestorationForCleanupRetry() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.draw(false, false); // Allocate before installing the deliberate foreign-state failure.
            var renderer = mock(Sonic2SpecialStageRenderer.class);
            set(fixture.pass, "terrain", renderer);
            doAnswer(call -> {
                assertFalse(fixture.graphics.isWaterEnabled(), "companion producer owns no-water state");
                assertFalse(fixture.graphics.isUseUnderwaterPaletteForBackground());
                fixture.graphics.registerCommand((x, y, width, height) -> {
                    assertFalse(fixture.graphics.isWaterEnabled(), "companion execution owns no-water state");
                    assertFalse(fixture.graphics.isUseUnderwaterPaletteForBackground());
                    throw new IllegalArgumentException("track draw");
                });
                return null;
            }).when(renderer).renderTrack(anyInt(), any(int[].class));
            fixture.foreignState();
            fixture.graphics.setWaterEnabled(true);
            fixture.graphics.setUseUnderwaterPaletteForBackground(true);
            var before = rasterState();
            fixture.pass.queueBackdrop(fixture.mappings, fixture.palettes, 8, 8, 0, fixture.track, false, true);
            var command = fixture.takeCommand();
            try (MockedStatic<GL30> gl30 = mockStatic(GL30.class, CALLS_REAL_METHODS);
                 MockedStatic<GL33> gl33 = mockStatic(GL33.class, CALLS_REAL_METHODS)) {
                gl30.when(() -> GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, fixture.read.fboId()))
                        .thenThrow(new IllegalStateException("restore once"))
                        .thenCallRealMethod();
                gl33.when(() -> GL33.glBindSampler(1, 0))
                        .thenThrow(new IllegalStateException("restore unit once"))
                        .thenCallRealMethod();
                var failure = assertThrows(IllegalArgumentException.class, () -> command.execute(0, 0, 400, 224));
                assertEquals("track draw", failure.getMessage());
                assertTrue(List.of(failure.getSuppressed()).stream()
                        .anyMatch(suppressed -> suppressed.getMessage().equals("restore once")));
                assertTrue(List.of(failure.getSuppressed()).stream()
                        .anyMatch(suppressed -> suppressed.getMessage().equals("restore unit once")));
                try (MockedStatic<GL> gl = mockStatic(GL.class, CALLS_REAL_METHODS)) {
                    gl.when(GL::getCapabilities).thenReturn(mock(GLCapabilities.class));
                    assertThrows(IllegalStateException.class, () -> command.unwindAfterFailure(0, 0, 400, 224));
                    gl30.verify(() -> GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, fixture.read.fboId()), times(1));
                }
                fixture.pass.cleanup();
            }
            assertEquals(before, rasterState());
            assertTrue(fixture.graphics.isWaterEnabled(), "failed draw and cleanup restore caller CPU flags");
            assertTrue(fixture.graphics.isUseUnderwaterPaletteForBackground());
            assertFalse(fixture.pass.hasCleanupPendingOwnership());
        }
    }

    @Test
    void unpublishedFramebufferIsCleanedAndRecreatedBeforeTheNextSubmission() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.foreignState();
            var before = rasterState();
            SpecialStageOwnedFramebuffer partial;
            try (MockedStatic<GL30> gl30 = mockStatic(GL30.class, CALLS_REAL_METHODS)) {
                gl30.when(() -> GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER))
                        .thenCallRealMethod() // Static background's 256x256 owner succeeds.
                        .thenReturn(GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT) // Projected owner fails.
                        .thenCallRealMethod();
                assertThrows(IllegalStateException.class, () -> fixture.draw(true, true));
                partial = (SpecialStageOwnedFramebuffer) field(fixture.pass, "projected");
                assertNotNull(partial);
                assertFalse(partial.isPublished());
                assertTrue(partial.hasPendingOwnership());
                assertEquals(before, rasterState());
                fixture.draw(true, true);
            }
            assertFalse(partial.hasPendingOwnership());
            var current = (SpecialStageOwnedFramebuffer) field(fixture.pass, "projected");
            assertNotSame(partial, current);
            assertTrue(current.isPublished());
            assertEquals(before, rasterState());
            fixture.pixel(fixture.viewport.innerH32OriginX() + 10, 0, 18, 91, 128);
        }
    }

    @Test
    void discardedSubmissionDoesNotRunAndReplacementContextCannotDeleteOwnedNames() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.foreignState();
            fixture.pass.queueBackdrop(fixture.mappings, fixture.palettes, 8, 8, 0, fixture.track, true, true);
            fixture.graphics.discardQueuedCommands();
            var before = rasterState();
            try (MockedStatic<GL> gl = mockStatic(GL.class, CALLS_REAL_METHODS)) {
                gl.when(GL::getCapabilities).thenReturn(mock(GLCapabilities.class));
                assertThrows(IllegalStateException.class, fixture.pass::cleanup);
                assertTrue(fixture.pass.hasCleanupPendingOwnership());
                var background = (SpecialStageBackgroundRenderer) field(fixture.pass, "background");
                var backgroundFailure = assertThrows(IllegalStateException.class, background::cleanup);
                assertTrue(backgroundFailure.getMessage().contains("creating GL context"));
                assertTrue(background.hasCleanupPendingOwnership(),
                        "manager's separate static background must retain its old-context resource names too");
            }
            assertEquals(before, rasterState());
            fixture.pass.cleanup();
            assertFalse(fixture.pass.hasCleanupPendingOwnership());
            fixture.draw(true, true);
            fixture.pixel(fixture.viewport.innerH32OriginX() + 10, 0, 18, 91, 128);
        }
    }

    @Test
    void allocatedHeadlessCleanupRetainsNamesAndPendingRestorationUntilCreatingContextRetry() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.draw(false, false);
            var projected = (SpecialStageOwnedFramebuffer) field(fixture.pass, "projected");
            int texture = projected.textureId();
            var emptyGraphics = new GraphicsManager();
            emptyGraphics.initHeadless();
            var empty = new Sonic2SpecialStageTerrainPass(emptyGraphics);
            for (boolean retainRestoration : new boolean[] {false, true}) {
                if (retainRestoration) {
                    fixture.foreignState();
                    fixture.pass.queueBackdrop(fixture.mappings, fixture.palettes, 8, 8, 0,
                            fixture.track, false, false);
                    var command = fixture.takeCommand();
                    try (MockedStatic<GL30> gl30 = mockStatic(GL30.class, CALLS_REAL_METHODS)) {
                        gl30.when(() -> GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, fixture.read.fboId()))
                                .thenThrow(new IllegalStateException("retain restoration"));
                        var failure = assertThrows(IllegalStateException.class,
                                () -> command.execute(0, 0, 400, 224));
                        assertEquals("retain restoration", failure.getMessage());
                    } finally { command.discard(); }
                    assertNotNull(field(fixture.pass, "pendingRestoration"));
                }
                Object restoration = field(fixture.pass, "pendingRestoration");
                fixture.graphics.setHeadlessMode(true);
                try (MockedStatic<GL> gl = mockStatic(GL.class, CALLS_REAL_METHODS);
                     MockedStatic<GL11> gl11 = mockStatic(GL11.class, CALLS_REAL_METHODS);
                     MockedStatic<GL20> gl20 = mockStatic(GL20.class, CALLS_REAL_METHODS);
                     MockedStatic<GL30> gl30 = mockStatic(GL30.class, CALLS_REAL_METHODS)) {
                    gl.when(GL::getCapabilities).thenReturn(mock(GLCapabilities.class));
                    // Keep the negative control safe if the ownership guard regresses:
                    // observe attempted foreign deletion without invoking native pointers.
                    gl11.when(() -> GL11.glDeleteTextures(anyInt())).thenAnswer(call -> null);
                    gl20.when(() -> GL20.glDeleteProgram(anyInt())).thenAnswer(call -> null);
                    gl30.when(() -> GL30.glDeleteFramebuffers(anyInt())).thenAnswer(call -> null);
                    gl30.when(() -> GL30.glDeleteRenderbuffers(anyInt())).thenAnswer(call -> null);
                    gl30.when(() -> GL30.glDeleteVertexArrays(anyInt())).thenAnswer(call -> null);
                    // Static CALLS_REAL_METHODS setup can itself observe the
                    // capabilities. The no-GL assertion starts at cleanup.
                    gl.clearInvocations();
                    assertDoesNotThrow(empty::cleanup, "truly unallocated headless cleanup is a CPU-only no-op");
                    var failure = assertThrows(IllegalStateException.class, fixture.pass::cleanup);
                    assertTrue(failure.getMessage().contains("headless"));
                    assertTrue(fixture.pass.hasCleanupPendingOwnership());
                    assertSame(projected, field(fixture.pass, "projected"));
                    assertSame(restoration, field(fixture.pass, "pendingRestoration"));
                    assertEquals(texture, projected.textureId());
                    gl.verify(GL::getCapabilities, never());
                    gl11.verify(() -> GL11.glDeleteTextures(anyInt()), never());
                    gl20.verify(() -> GL20.glDeleteProgram(anyInt()), never());
                    gl30.verify(() -> GL30.glDeleteFramebuffers(anyInt()), never());
                    gl30.verify(() -> GL30.glDeleteRenderbuffers(anyInt()), never());
                    gl30.verify(() -> GL30.glDeleteVertexArrays(anyInt()), never());
                } finally { fixture.graphics.setHeadlessMode(false); }
                assertTrue(glIsTexture(texture), "headless attempt preserves the original context's image");
            }
            emptyGraphics.cleanup();
            fixture.pass.cleanup();
            assertFalse(fixture.pass.hasCleanupPendingOwnership());
            assertNull(field(fixture.pass, "pendingRestoration"));
            assertFalse(glIsTexture(texture), "valid creating-context cleanup retires the owned image");
        }
    }

    @Test
    void pendingDrawMustDrainBeforeConfigurationAtlasRetirementOrCleanup() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.pass.queueBackdrop(fixture.mappings, fixture.palettes, 8, 8, 0, fixture.track, true, true);
            var target = field(fixture.pass, "projected");
            assertThrows(IllegalStateException.class,
                    () -> fixture.pass.configure(SpecialStageViewport.fromLogicalWidth(512), 100, 200, 1));
            assertThrows(IllegalStateException.class,
                    () -> fixture.pass.configure(fixture.viewport, 101, 200, 1));
            assertThrows(IllegalStateException.class,
                    () -> fixture.pass.configure(fixture.viewport, 100, 200, 2));
            assertThrows(IllegalStateException.class, fixture.pass::cleanup);
            var atlas = fixture.graphics.getPatternAtlas();
            try {
                set(fixture.graphics, "patternAtlas", mock(PatternAtlas.class));
                assertThrows(IllegalStateException.class, () -> fixture.pass.preparePaletteBank(fixture.palettes));
            } finally { set(fixture.graphics, "patternAtlas", atlas); }
            assertSame(target, field(fixture.pass, "projected"));
            fixture.graphics.discardQueuedCommands();
            fixture.pass.configure(SpecialStageViewport.fromLogicalWidth(512), 100, 200, 2);
            fixture.draw(true, true);
            var replacement = (SpecialStageOwnedFramebuffer) field(fixture.pass, "projected");
            assertNotSame(target, replacement);
            assertTrue(replacement.isPublished());
            fixture.pixelFrom(replacement.fboId(), 128 + 10, 0, 18, 91, 128, "new 512-wide H32 origin");
        }
    }

    @Test
    void failedRetainedBeginRestoresItsExactProjectionAndAllowsANewBuild() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.draw(true, false);
            var background = (SpecialStageBackgroundRenderer) field(fixture.pass, "background");
            fixture.foreignState();
            var before = rasterState();
            var priorSource = fixture.graphics.getProjectionSource();
            var priorMatrix = field(fixture.graphics, "projectionMatrixBuffer");
            try (MockedStatic<GL11> gl11 = mockStatic(GL11.class, CALLS_REAL_METHODS)) {
                gl11.when(() -> GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT))
                        .thenThrow(new IllegalStateException("begin clear"))
                        .thenCallRealMethod();
                var failure = assertThrows(IllegalStateException.class, () -> background.beginTilePass(224));
                assertEquals("begin clear", failure.getMessage());
                assertNull(field(background, "activeTilePassState"), "successful unwind retires the failed pass");
                assertSame(priorSource, fixture.graphics.getProjectionSource());
                assertSame(priorMatrix, field(fixture.graphics, "projectionMatrixBuffer"));
                assertEquals(before, rasterState());
                background.beginTilePass(224);
                background.endTilePass();
            }
            assertEquals(before, rasterState());
            assertSame(priorSource, fixture.graphics.getProjectionSource());
            assertSame(priorMatrix, field(fixture.graphics, "projectionMatrixBuffer"));
        }
    }

    @Test
    void retainedTileBeginAndEndDoNotStealALaterPendingCpuBatch() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (Fixture fixture = new Fixture()) {
            fixture.draw(true, false);
            var background = (SpecialStageBackgroundRenderer) field(fixture.pass, "background");
            int bank = fixture.pass.preparePaletteBank(fixture.palettes);
            for (boolean instanced : new boolean[] {false, true}) {
                fixture.graphics.setBatchingEnabled(true);
                fixture.graphics.setInstancedBatchingEnabled(instanced);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fixture.draw.fboId());
                glViewport(0, 0, 400, 224);
                glDisable(GL_SCISSOR_TEST);
                glClearColor(0, 0, 0, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                fixture.graphics.beginPatternBatch();
                var desc = new PatternDesc(1);
                desc.setPaletteIndex(bank);
                fixture.graphics.renderPatternWithId(101, desc, 10, 10);

                background.beginTilePass(224);
                background.endTilePass();

                @SuppressWarnings("unchecked")
                var queued = (List<GLCommandable>) field(fixture.graphics, "commands");
                assertTrue(queued.isEmpty(), "retained execution must not queue the later CPU batch");
                fixture.graphics.flushPatternBatch();
                var pending = fixture.takeCommand();
                try { pending.execute(0, 0, 400, 224); }
                finally { pending.discard(); }
                fixture.pixelFrom(fixture.draw.fboId(), 11, 11, 109, 55, 36,
                        "pending " + (instanced ? "instanced" : "ordinary") + " batch survives the retained scope");
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final PresentationOffscreenTestContext context = PresentationOffscreenTestContext.open(400, 300);
        final GraphicsManager graphics = new GraphicsManager();
        final Sonic2SpecialStageTerrainPass pass = new Sonic2SpecialStageTerrainPass(graphics);
        final SpecialStageViewport viewport = SpecialStageViewport.fromLogicalWidth(400);
        final Palette[] palettes = {new Palette(), new Palette(), new Palette(), new Palette()};
        final byte[] mappings = new byte[32 * 32 * 2];
        final int[] track = new int[28 * 128];
        FboHelper.FboHandle read, draw;
        int foreignTexture, foreignSampler, foreignVao, foreignBuffer;

        Fixture() throws Exception {
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, 400, 224);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho2D(0, 400, 0, 224).get(new float[16]));
                graphics.setBatchingEnabled(false);
                palettes[0].getColor(1).fromSegaFormat((2 << 9) | (3 << 5) | (6 << 1));
                palettes[0].getColor(2).fromSegaFormat((4 << 9) | (6 << 5) | (2 << 1));
                palettes[0].getColor(3).fromSegaFormat((7 << 9) | (5 << 5) | (1 << 1));
                graphics.cachePatternTexture(solid(1), 101);
                graphics.cachePatternTexture(solid(2), 102);
                graphics.cachePatternTexture(solid(0), 103);
                graphics.cachePatternTexture(solid(3), 201);
                graphics.cachePatternTexture(solid(0), 202);
                for (int cell = 0; cell < 32 * 32; cell++) mappings[cell * 2 + 1] = 1;
                mappings[(32 + 2) * 2 + 1] = 2;
                mappings[(32 + 3) * 2 + 1] = 3; // An actual mapped tile made entirely of transparent color0.
                track[1] = 0x8001; // Even a high-priority track tile is shadow in the companion.
                track[32 + 3] = 1;
                track[4] = 2; // Transparent two-scanline strip over that BG hole after scroll.
                pass.configure(viewport, 100, 200, 1);
                read = FboHelper.createColorOnly(400, 300, GL_CLAMP_TO_EDGE);
                draw = FboHelper.createColorOnly(400, 300, GL_CLAMP_TO_EDGE);
                assertNotNull(read); assertNotNull(draw);
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glViewport(0, 0, 400, 224);
            } catch (Exception | Error failure) { close(); throw failure; }
        }

        void draw(boolean planeB, boolean planeA) throws Exception {
            pass.queueBackdrop(mappings, palettes, 8, 8, 0, track, planeB, planeA);
            var command = takeCommand();
            try { command.execute(0, 0, 400, 224); }
            finally { command.discard(); }
        }

        GLCommandable takeCommand() throws Exception {
            // Execute this component's sole command directly: any outer manager flush owns
            // its own palette-residency preamble, which is outside this component's scope.
            @SuppressWarnings("unchecked")
            var commands = (List<GLCommandable>) field(graphics, "commands");
            assertEquals(1, commands.size());
            return commands.removeFirst();
        }

        void foreignState() {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read.fboId());
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw.fboId());
            glViewport(13, 17, 320, 224);
            glEnable(GL_SCISSOR_TEST); glScissor(7, 11, 19, 23);
            glEnable(GL_DEPTH_TEST); glDepthMask(false); glClearDepth(0.25);
            glEnable(GL_STENCIL_TEST); glClearStencil(19);
            glStencilMaskSeparate(GL_FRONT, 0x55); glStencilMaskSeparate(GL_BACK, 0xAA);
            glColorMask(false, true, false, true);
            glClearColor(0.2f, 0.4f, 0.6f, 0.8f);
            glEnable(GL_BLEND); glBlendEquationSeparate(GL_FUNC_SUBTRACT, GL_FUNC_REVERSE_SUBTRACT);
            glBlendFuncSeparate(GL_DST_ALPHA, GL_ONE, GL_ZERO, GL_ONE_MINUS_DST_ALPHA);
            if (foreignTexture == 0) foreignTexture = glGenTextures();
            if (foreignSampler == 0) foreignSampler = glGenSamplers();
            if (foreignVao == 0) foreignVao = glGenVertexArrays();
            if (foreignBuffer == 0) foreignBuffer = glGenBuffers();
            glActiveTexture(GL_TEXTURE6); glBindTexture(GL_TEXTURE_2D, foreignTexture);
            glBindSampler(6, foreignSampler);
            glBindVertexArray(foreignVao); glBindBuffer(GL_ARRAY_BUFFER, foreignBuffer);
        }

        void pixel(int x, int topY, int r, int g, int b) throws Exception { pixel(x, topY, r, g, b, "projected pixel"); }
        void pixel(int x, int topY, int r, int g, int b, String message) throws Exception {
            var framebuffer = (SpecialStageOwnedFramebuffer) field(pass, "projected");
            pixelFrom(framebuffer.fboId(), x, topY, r, g, b, message);
        }
        void pixelFrom(int framebuffer, int x, int topY, int r, int g, int b, String message) {
            int prior = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            var pixel = BufferUtils.createByteBuffer(4);
            try {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
                glReadPixels(x, 223 - topY, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            } finally { glBindFramebuffer(GL_READ_FRAMEBUFFER, prior); }
            assertEquals(List.of(r, g, b, 255), List.of(pixel.get(0) & 255, pixel.get(1) & 255,
                    pixel.get(2) & 255, pixel.get(3) & 255), message + " at " + x + "," + topY);
        }

        @Override public void close() {
            try {
                graphics.discardQueuedCommands();
                pass.cleanup();
                graphics.cleanup();
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                FboHelper.destroy(read); FboHelper.destroy(draw);
                glDeleteTextures(foreignTexture); glDeleteSamplers(foreignSampler);
                glDeleteVertexArrays(foreignVao); glDeleteBuffers(foreignBuffer);
                RenderContext.reset();
            } finally { context.close(); }
        }
    }

    private static void publishForeignWater(GraphicsManager graphics) {
        graphics.setUseWaterShader(true);
        graphics.setUseSpritePriorityShader(true);
        graphics.setCurrentSpriteHighPriority(true);
        graphics.setWaterEnabled(true);
        graphics.setUseUnderwaterPaletteForBackground(true);
        PatternRenderCommand.resetFrameState();
    }

    private static void queueNativeNoWaterPresentation(GraphicsManager graphics) {
        try {
            var method = Sonic2SpecialStageManager.class.getDeclaredMethod(
                    "queueNativeNoWaterPresentation", GraphicsManager.class);
            method.setAccessible(true);
            method.invoke(null, graphics);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Pattern solid(int index) {
        var pattern = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) index);
        return pattern;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static List<Object> rasterState() {
        var result = new ArrayList<>();
        for (int query : new int[] {GL_READ_FRAMEBUFFER_BINDING, GL_DRAW_FRAMEBUFFER_BINDING,
                GL_CURRENT_PROGRAM, GL_VERTEX_ARRAY_BINDING, GL_ARRAY_BUFFER_BINDING,
                GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA, GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB,
                GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA, GL_STENCIL_CLEAR_VALUE,
                GL_STENCIL_WRITEMASK, GL_STENCIL_BACK_WRITEMASK}) result.add(glGetInteger(query));
        for (int query : new int[] {GL_VIEWPORT, GL_SCISSOR_BOX}) {
            int[] values = new int[4]; glGetIntegerv(query, values);
            for (int value : values) result.add(value);
        }
        float[] clear = new float[4]; glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
        for (float value : clear) result.add(value);
        result.add(glGetFloat(GL_DEPTH_CLEAR_VALUE)); result.add(glGetBoolean(GL_DEPTH_WRITEMASK));
        ByteBuffer colorMask = BufferUtils.createByteBuffer(4); glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
        for (int i = 0; i < 4; i++) result.add(colorMask.get(i));
        for (int cap : new int[] {GL_SCISSOR_TEST, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_BLEND,
                GL_DITHER, GL_CULL_FACE, GL_RASTERIZER_DISCARD, GL_COLOR_LOGIC_OP, GL_FRAMEBUFFER_SRGB})
            result.add(glIsEnabled(cap));
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        result.add(active);
        try {
            for (int unit = 0; unit <= 6; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit);
                result.add(glGetInteger(GL_TEXTURE_BINDING_2D));
                result.add(glGetInteger(GL_TEXTURE_BINDING_1D));
                result.add(glGetIntegeri(GL_SAMPLER_BINDING, unit));
            }
        } finally { glActiveTexture(active); }
        return result;
    }
}
