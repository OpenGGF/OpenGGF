package com.openggf.game.sonic2.specialstage;

import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.HScrollBuffer;
import com.openggf.graphics.ParallaxShaderProgram;
import com.openggf.graphics.QuadRenderer;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * GPU-based background renderer for Special Stage.
 *
 * Implements the two-pass rendering approach:
 * 1. Render background tiles to an FBO (256x256 texture)
 * 2. Draw fullscreen quad with shader applying per-scanline H-scroll and H32
 * clipping
 *
 * This emulates the Mega Drive VDP behavior where:
 * - H-scroll table provides per-scanline horizontal scroll offsets
 * - H32 mode displays only 256 pixels centered on the 320-pixel screen
 * - V-scroll provides vertical parallax during rise/drop animations
 */
public class SpecialStageBackgroundRenderer {

    private static final Logger LOGGER = Logger.getLogger(SpecialStageBackgroundRenderer.class.getName());

    // FBO dimensions - background is 32x32 tiles = 256x256 pixels
    private static final int FBO_WIDTH = 256;
    private static final int FBO_HEIGHT = 256;

    // Screen dimensions
    public static final int SCREEN_WIDTH = 320;
    public static final int SCREEN_HEIGHT = 224;
    public static final int H32_WIDTH = 256;
    public static final int H32_OFFSET = (SCREEN_WIDTH - H32_WIDTH) / 2; // 32 pixels

    // OpenGL resources
    private SpecialStageOwnedFramebuffer fboHandle;
    private int fboId = -1;
    private int fboTextureId = -1;
    private int fboDepthId = -1;

    // Shader and scroll buffer
    private ParallaxShaderProgram shader;
    private HScrollBuffer hScrollBuffer;
    private final QuadRenderer quadRenderer = new QuadRenderer();

    // Per-scanline scroll data (224 entries)
    private final int[] hScrollData = new int[SCREEN_HEIGHT];

    // State
    private boolean initialized = false;
    private final int[] shaderViewport = new int[4];
    private final GraphicsManager graphicsManager;
    private SpecialStageViewport viewport = SpecialStageViewport.nativeViewport();
    private TilePassState activeTilePassState;
    private float[] activeProjection;
    private boolean quadRendererOwned;

    public SpecialStageBackgroundRenderer(GraphicsManager graphicsManager) {
        this.graphicsManager = java.util.Objects.requireNonNull(graphicsManager, "graphicsManager");
    }

    public void setSpecialStageViewport(SpecialStageViewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
    }

    /**
     * Initialize the renderer with FBO and shader.
     *
     * @throws IOException if shader loading fails
     */
    public void init() throws IOException {
        if (initialized) {
            return;
        }
        if (hasCleanupPendingOwnership()) {
            throw new IllegalStateException("special-stage background cleanup is still pending");
        }
        // Headless mode has no GL context: skip all GL resource creation and
        // leave the renderer un-initialised. Every draw entry point below already
        // guards on `initialized`, so the shared special-stage runtime can boot
        // and run its game logic (physics, object state) without rendering.
        // Mirrors Sonic1SpecialStageManager's headless renderer skip.
        if (graphicsManager.isHeadlessMode()) {
            LOGGER.fine("Skipping SpecialStageBackgroundRenderer GL init in headless mode");
            return;
        }

        try {
            createFBO();
            hScrollBuffer = new HScrollBuffer();
            hScrollBuffer.init();
            shader = new ParallaxShaderProgram("shaders/shader_ss_background.glsl");
            shader.cacheUniformLocations();
            quadRenderer.init();
            quadRendererOwned = true;
            for (int i = 0; i < SCREEN_HEIGHT; i++) hScrollData[i] = 0;
            initialized = true;
            LOGGER.info("SpecialStageBackgroundRenderer initialized");
        } catch (IOException | RuntimeException | Error failure) {
            cleanupResources(failure);
            throw failure;
        }
    }

    /**
     * Create the framebuffer object for tile rendering.
     */
    private void createFBO() {
        fboHandle = new SpecialStageOwnedFramebuffer(FBO_WIDTH, FBO_HEIGHT, GL_REPEAT);
        fboHandle.create();
        fboId = fboHandle.fboId();
        fboTextureId = fboHandle.textureId();
        fboDepthId = fboHandle.depthId();
        LOGGER.fine("Created FBO " + FBO_WIDTH + "x" + FBO_HEIGHT + " for special stage background");
    }

    /**
     * Sets up FBO projection mode for coordinate calculations.
     * Call this BEFORE creating the pattern batch so that Y coordinates
     * are calculated correctly for the 256x256 FBO.
     *
     * This method only updates the projection state - it does not perform
     * any GL operations. Call beginTilePassGL() for the actual GL setup.
     */
    public void beginFBOProjection() {
        graphicsManager.setProjectionMatrixBuffer(fboProjectionBuffer());
    }

    /**
     * Restores normal screen projection after FBO pattern batch creation.
     * Call this AFTER flushing the pattern batch.
     */
    public void endFBOProjection() {
        graphicsManager.setProjectionMatrixBuffer(null);
    }

    /**
     * Begin the tile rendering pass - bind FBO and set up viewport.
     * After calling this, render background tiles using the normal tile renderer.
     *
     * Note: Call beginFBOProjection() BEFORE creating the pattern batch to ensure
     * correct coordinate calculations. This method handles GL state setup.
     *
     * @param displayHeight The display height used by pattern renderer for Y-flip
     */
    public void beginTilePass(int displayHeight) {
        if (!initialized) return;
        if (activeTilePassState != null) {
            throw new IllegalStateException("special-stage FBO tile pass is already active");
        }
        TilePassState prior = TilePassState.capture(graphicsManager.getProjectionMatrixBuffer());
        activeTilePassState = prior;
        try {
            graphicsManager.setProjectionMatrixBuffer(fboProjectionBuffer());
            glBindFramebuffer(GL_FRAMEBUFFER, fboId);
            glViewport(0, 0, FBO_WIDTH, FBO_HEIGHT);
            glDisable(GL_SCISSOR_TEST);
            glClearColor(0, 0, 0, 0);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        } catch (RuntimeException | Error failure) {
            Throwable cleanupFailure = restoreTilePass(prior, failure);
            throwFailure(cleanupFailure);
        }
    }

    /**
     * End the tile rendering pass - unbind FBO and restore viewport.
     */
    public void endTilePass() {
        if (!initialized || activeTilePassState == null) return;
        TilePassState prior = activeTilePassState;
        Throwable failure = restoreTilePass(prior, null);
        if (failure == null) activeTilePassState = null;
        throwFailure(failure);
    }

    private float[] fboProjectionBuffer() {
        float[] buffer = new float[16];
        new org.joml.Matrix4f().ortho2D(0, FBO_WIDTH, 0, FBO_HEIGHT).get(buffer);
        return buffer;
    }

    private Throwable restoreTilePass(TilePassState state, Throwable failure) {
        failure = attempt(failure, () -> glBindFramebuffer(GL_FRAMEBUFFER, state.framebuffer()));
        failure = attempt(failure, () -> glViewport(state.viewport()[0], state.viewport()[1],
                state.viewport()[2], state.viewport()[3]));
        failure = attempt(failure, () -> glScissor(state.scissor()[0], state.scissor()[1],
                state.scissor()[2], state.scissor()[3]));
        failure = attempt(failure, () -> {
            if (state.scissorEnabled()) glEnable(GL_SCISSOR_TEST);
            else glDisable(GL_SCISSOR_TEST);
        });
        failure = attempt(failure, () -> glBlendEquationSeparate(state.blendEquationRgb(),
                state.blendEquationAlpha()));
        failure = attempt(failure, () -> glBlendFuncSeparate(state.blendSourceRgb(),
                state.blendDestinationRgb(), state.blendSourceAlpha(), state.blendDestinationAlpha()));
        failure = attempt(failure, () -> {
            if (state.blendEnabled()) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
        });
        failure = attempt(failure, () -> glClearColor(state.clearColor()[0], state.clearColor()[1],
                state.clearColor()[2], state.clearColor()[3]));
        if (failure == null) graphicsManager.setProjectionMatrixBuffer(state.projectionBuffer());
        return failure;
    }

    private record TilePassState(int framebuffer, int[] viewport, int[] scissor, float[] clearColor,
            boolean scissorEnabled, boolean blendEnabled, int blendSourceRgb, int blendDestinationRgb,
            int blendSourceAlpha, int blendDestinationAlpha, int blendEquationRgb,
            int blendEquationAlpha, float[] projectionBuffer) {
        private static TilePassState capture(float[] projectionBuffer) {
            int[] viewport = new int[4];
            int[] scissor = new int[4];
            float[] clearColor = new float[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetIntegerv(GL_SCISSOR_BOX, scissor);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
            return new TilePassState(glGetInteger(GL_FRAMEBUFFER_BINDING), viewport, scissor, clearColor,
                    glIsEnabled(GL_SCISSOR_TEST), glIsEnabled(GL_BLEND),
                    glGetInteger(GL_BLEND_SRC_RGB), glGetInteger(GL_BLEND_DST_RGB),
                    glGetInteger(GL_BLEND_SRC_ALPHA), glGetInteger(GL_BLEND_DST_ALPHA),
                    glGetInteger(GL_BLEND_EQUATION_RGB), glGetInteger(GL_BLEND_EQUATION_ALPHA),
                    projectionBuffer == null ? null : projectionBuffer.clone());
        }
    }

    private static Throwable attempt(Throwable failure, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | Error next) {
            if (failure == null) failure = next;
            else failure.addSuppressed(next);
        }
        return failure;
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    /**
     * Render the background with per-scanline scrolling using the shader.
     *
     * @param vScrollBG Vertical scroll offset for parallax
     */
    public void renderWithShader(float vScrollBG) {
        if (!initialized) return;
        ShaderPassState prior = ShaderPassState.capture();
        Throwable failure = null;
        try {

        hScrollBuffer.upload(hScrollData);

        hScrollBuffer.bind(1);

        shader.use();
        shader.cacheUniformLocations();

        shader.setBackgroundTexture(0); // FBO texture
        shader.setHScrollTexture(1); // H-scroll table

        glGetIntegerv(GL_VIEWPORT, shaderViewport);
        float scaleX = shaderViewport[2] / (float) viewport.logicalWidth();
        float scaleY = shaderViewport[3] / (float) viewport.logicalHeight();

        shader.setScreenDimensions(SCREEN_WIDTH * scaleX, SCREEN_HEIGHT * scaleY);
        shader.setActiveDisplayWidth((float) H32_WIDTH);
        shader.setBGTextureDimensions(FBO_WIDTH, FBO_HEIGHT);
        shader.setVScrollBG(vScrollBG);
        shader.setViewportOffset(shaderViewport[0] + viewport.outerOriginX() * scaleX,
                (float) shaderViewport[1]);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, fboTextureId);

        drawFullscreenQuad();
        } catch (RuntimeException | Error renderFailure) {
            failure = renderFailure;
        } finally {
            failure = attempt(failure, shader::stop);
            failure = attempt(failure, () -> hScrollBuffer.unbind(1));
            failure = prior.restore(failure);
        }
        throwFailure(failure);
    }

    private record ShaderPassState(int[] viewport, int[] scissor, float[] clearColor,
            boolean scissorEnabled, boolean blendEnabled, int blendSourceRgb, int blendDestinationRgb,
            int blendSourceAlpha, int blendDestinationAlpha, int blendEquationRgb,
            int blendEquationAlpha, int activeTexture, int texture0, int texture1) {
        private static ShaderPassState capture() {
            int[] viewport = new int[4];
            int[] scissor = new int[4];
            float[] clearColor = new float[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetIntegerv(GL_SCISSOR_BOX, scissor);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
            int active = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            int texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
            glActiveTexture(GL_TEXTURE1);
            int texture1 = glGetInteger(GL_TEXTURE_BINDING_1D);
            glActiveTexture(active);
            return new ShaderPassState(viewport, scissor, clearColor, glIsEnabled(GL_SCISSOR_TEST),
                    glIsEnabled(GL_BLEND), glGetInteger(GL_BLEND_SRC_RGB), glGetInteger(GL_BLEND_DST_RGB),
                    glGetInteger(GL_BLEND_SRC_ALPHA), glGetInteger(GL_BLEND_DST_ALPHA),
                    glGetInteger(GL_BLEND_EQUATION_RGB), glGetInteger(GL_BLEND_EQUATION_ALPHA),
                    active, texture0, texture1);
        }

        private Throwable restore(Throwable failure) {
            failure = attempt(failure, () -> glViewport(viewport[0], viewport[1], viewport[2], viewport[3]));
            failure = attempt(failure, () -> glScissor(scissor[0], scissor[1], scissor[2], scissor[3]));
            failure = attempt(failure, () -> {
                if (scissorEnabled) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
            });
            failure = attempt(failure, () -> glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha));
            failure = attempt(failure, () -> glBlendFuncSeparate(blendSourceRgb, blendDestinationRgb,
                    blendSourceAlpha, blendDestinationAlpha));
            failure = attempt(failure, () -> {
                if (blendEnabled) glEnable(GL_BLEND); else glDisable(GL_BLEND);
            });
            failure = attempt(failure, () -> glClearColor(clearColor[0], clearColor[1],
                    clearColor[2], clearColor[3]));
            failure = attempt(failure, () -> {
                glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture0);
                glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_1D, texture1);
                glActiveTexture(activeTexture);
            });
            return failure;
        }
    }

    /**
     * Draw a fullscreen quad covering the entire screen.
     * The shader handles H32 clipping internally.
     * Note: The shader uses gl_FragCoord for positioning,
     * so no projection matrix is needed.
     */
    private void drawFullscreenQuad() {
        quadRenderer.draw(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    }

    /**
     * Set the horizontal scroll value for all scanlines uniformly.
     *
     * @param scroll The scroll value in pixels
     */
    public void setUniformHScroll(int scroll) {
        for (int i = 0; i < SCREEN_HEIGHT; i++) {
            hScrollData[i] = scroll;
        }
    }

    /**
     * Set the horizontal scroll value for a specific scanline.
     *
     * @param scanline The scanline index (0-223)
     * @param scroll   The scroll value in pixels
     */
    public void setHScroll(int scanline, int scroll) {
        if (scanline >= 0 && scanline < SCREEN_HEIGHT) {
            hScrollData[scanline] = scroll;
        }
    }

    /**
     * Apply a delta to all scanlines' H-scroll values.
     * Used for the per-frame parallax scroll update.
     *
     * @param delta The value to add to each scanline's scroll
     */
    public void addHScrollDelta(int delta) {
        for (int i = 0; i < SCREEN_HEIGHT; i++) {
            hScrollData[i] += delta;
        }
    }

    /**
     * Check if renderer is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the FBO texture ID (for debugging).
     */
    public int getFBOTextureId() {
        return fboTextureId;
    }

    /**
     * Clean up OpenGL resources.
     */
    public void cleanup() {
        // Nothing was allocated in headless mode (init() short-circuits), so
        // there are no GL resources to release and no GL context to call into.
        if (graphicsManager.isHeadlessMode()) {
            initialized = false;
            return;
        }
        cleanupResources(null);
    }

    private void cleanupResources(Throwable originalFailure) {
        Throwable failure = originalFailure;
        if (activeTilePassState != null) {
            TilePassState state = activeTilePassState;
            failure = restoreTilePass(state, failure);
            if (failure == null) activeTilePassState = null;
        }
        if (hScrollBuffer != null) {
            HScrollBuffer resource = hScrollBuffer;
            CleanupAttempt cleanup = attemptCleanup(failure, resource::cleanup);
            failure = cleanup.failure();
            if (cleanup.succeeded()) hScrollBuffer = null;
        }
        if (shader != null) {
            ParallaxShaderProgram resource = shader;
            CleanupAttempt cleanup = attemptCleanup(failure, resource::cleanup);
            failure = cleanup.failure();
            if (cleanup.succeeded()) shader = null;
        }
        if (quadRendererOwned) {
            CleanupAttempt cleanup = attemptCleanup(failure, quadRenderer::cleanup);
            failure = cleanup.failure();
            if (cleanup.succeeded()) quadRendererOwned = false;
        }
        if (fboHandle != null) {
            SpecialStageOwnedFramebuffer resource = fboHandle;
            Throwable before = failure;
            failure = resource.cleanup(failure);
            if (failure == before && !resource.hasPendingOwnership()) {
                fboHandle = null;
                fboId = -1;
                fboTextureId = -1;
                fboDepthId = -1;
            }
        }
        initialized = false;
        if (failure != null) throwFailure(failure);
    }

    public boolean hasCleanupPendingOwnership() {
        return activeTilePassState != null || hScrollBuffer != null || shader != null
                || quadRendererOwned || (fboHandle != null && fboHandle.hasPendingOwnership());
    }

    private record CleanupAttempt(Throwable failure, boolean succeeded) {
    }

    private static CleanupAttempt attemptCleanup(Throwable failure, Runnable action) {
        try {
            action.run();
            return new CleanupAttempt(failure, true);
        } catch (RuntimeException | Error nextFailure) {
            return new CleanupAttempt(attempt(failure, () -> { throw nextFailure; }), false);
        }
    }
}
