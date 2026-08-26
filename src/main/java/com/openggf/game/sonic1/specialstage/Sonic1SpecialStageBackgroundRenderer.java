package com.openggf.game.sonic1.specialstage;

import com.openggf.Engine;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.HScrollBuffer;
import com.openggf.graphics.ParallaxShaderProgram;
import com.openggf.graphics.QuadRenderer;
import com.openggf.level.PatternDesc;
import com.openggf.game.SpecialStageViewport;
import com.openggf.util.FboHelper;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;
import static com.openggf.game.sonic1.constants.Sonic1Constants.ARTTILE_SS_BG_FISH;

/**
 * GPU-based background renderer for Sonic 1 Special Stage.
 *
 * Renders the active SS namespace tilemap to an FBO (512x512, matching a 64x64 tile
 * VDP plane), then draws a fullscreen quad with the SS background shader
 * applying per-scanline H-scroll across the active game viewport.
 *
 * Modeled on the S2 {@code SpecialStageBackgroundRenderer} but with:
 * <ul>
 *   <li>512x512 FBO (64x64 tiles) instead of 256x256 (32x32 tiles)</li>
 *   <li>Split tile remapping for dual art sets (clouds + fish) in one atlas</li>
 *   <li>FBO caching - tiles only re-rendered when tilemap changes</li>
 * </ul>
 */
public class Sonic1SpecialStageBackgroundRenderer {

    private static final Logger LOGGER = Logger.getLogger(Sonic1SpecialStageBackgroundRenderer.class.getName());

    // FBO dimensions - 64x64 tiles = 512x512 pixels
    private static final int FBO_WIDTH = 512;
    private static final int FBO_HEIGHT = 512;

    // Tile map dimensions
    private static final int MAP_WIDTH = 64;
    private static final int MAP_HEIGHT = 64;
    private static final int TILE_SIZE = 8;

    // Screen dimensions
    public static final int SCREEN_WIDTH = 320;
    public static final int SCREEN_HEIGHT = 224;

    // OpenGL resources
    private FboHelper.FboHandle fboHandle;
    private int fboId = -1;
    private int fboTextureId = -1;
    private int fboDepthId = -1;

    // Shader and scroll buffer
    private ParallaxShaderProgram shader;
    private HScrollBuffer hScrollBuffer;
    private final QuadRenderer quadRenderer = new QuadRenderer();

    // Per-scanline scroll data (224 entries)
    private final int[] hScrollData = new int[SCREEN_HEIGHT];

    // Tile remapping bases
    private int bgCloudPatternBase;
    private int bgFishPatternBase;

    // Tilemap data and caching
    private byte[] tilemapData;
    private boolean fboNeedsRedraw = true;

    // Reusable PatternDesc to avoid allocation in render loop
    private final PatternDesc reusableDesc = new PatternDesc();

    // State
    private boolean initialized = false;
    private final int[] savedViewport = new int[4];
    private final int[] shaderViewport = new int[4];
    private float backdropR;
    private float backdropG;
    private float backdropB;
    private boolean fillTransparentWithBackdrop;
    private final GraphicsManager graphicsManager;
    private SpecialStageViewport specialStageViewport = SpecialStageViewport.nativeViewport();
    private final Deque<TilePassState> tilePassStates = new ArrayDeque<>();

    public Sonic1SpecialStageBackgroundRenderer(GraphicsManager graphicsManager) {
        this.graphicsManager = java.util.Objects.requireNonNull(graphicsManager, "graphicsManager");
    }

    public void setSpecialStageViewport(SpecialStageViewport viewport) {
        this.specialStageViewport = Objects.requireNonNull(viewport, "viewport");
    }

    /**
     * Initialize the renderer with FBO and shader.
     */
    public void init() throws IOException {
        if (initialized) {
            return;
        }

        createFBO();

        hScrollBuffer = new HScrollBuffer();
        hScrollBuffer.init();

        shader = new ParallaxShaderProgram("shaders/shader_ss_background.glsl");
        shader.cacheUniformLocations();
        quadRenderer.init();

        for (int i = 0; i < SCREEN_HEIGHT; i++) {
            hScrollData[i] = 0;
        }

        initialized = true;
        LOGGER.info("Sonic1SpecialStageBackgroundRenderer initialized");
    }

    private void createFBO() {
        fboHandle = FboHelper.createWithDepth(FBO_WIDTH, FBO_HEIGHT, GL_REPEAT);
        fboId = fboHandle.fboId();
        fboTextureId = fboHandle.textureId();
        fboDepthId = fboHandle.depthId();
        LOGGER.fine("Created FBO " + FBO_WIDTH + "x" + FBO_HEIGHT + " for S1 SS namespace renderer");
    }

    /**
     * Store atlas offsets for tile remapping.
     */
    public void setPatternBases(int cloudBase, int fishBase) {
        this.bgCloudPatternBase = cloudBase;
        this.bgFishPatternBase = fishBase;
    }

    /**
     * Set active Enigma-decoded tilemap and mark FBO for redraw.
     */
    public void setTilemap(byte[] data) {
        this.tilemapData = data;
        this.fboNeedsRedraw = true;
    }

    public void setBackdropColor(float r, float g, float b) {
        this.backdropR = r;
        this.backdropG = g;
        this.backdropB = b;
    }

    public void setFillTransparentWithBackdrop(boolean fill) {
        this.fillTransparentWithBackdrop = fill;
    }

    /**
     * Returns true if the FBO needs to be redrawn (tilemap changed).
     */
    public boolean needsRedraw() {
        return fboNeedsRedraw;
    }

    /**
     * Sets up FBO projection mode for coordinate calculations.
     * Call BEFORE creating the pattern batch.
     */
    public void beginFBOProjection() {
        graphicsManager.setProjectionMatrixBuffer(fboProjectionBuffer());
    }

    /**
     * Restores normal screen projection after FBO pattern batch creation.
     * Call AFTER flushing the pattern batch.
     */
    public void endFBOProjection() {
        graphicsManager.setProjectionMatrixBuffer(null);
    }

    /**
     * Begin the tile rendering pass - bind FBO and set up viewport.
     *
     * @param displayHeight The display height used by pattern renderer for Y-flip
     */
    public void beginTilePass(int displayHeight) {
        if (!initialized) return;

        int[] viewport = new int[4];
        int[] scissor = new int[4];
        float[] clearColor = new float[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissor);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
        TilePassState state = new TilePassState(
                glGetInteger(GL_FRAMEBUFFER_BINDING), viewport, scissor, clearColor,
                glIsEnabled(GL_SCISSOR_TEST), glIsEnabled(GL_BLEND),
                glGetInteger(GL_BLEND_SRC_RGB), glGetInteger(GL_BLEND_DST_RGB),
                glGetInteger(GL_BLEND_SRC_ALPHA), glGetInteger(GL_BLEND_DST_ALPHA),
                glGetInteger(GL_BLEND_EQUATION_RGB), glGetInteger(GL_BLEND_EQUATION_ALPHA),
                copyProjectionBuffer());
        tilePassStates.push(state);
        try {
            graphicsManager.setProjectionMatrixBuffer(fboProjectionBuffer());
            glBindFramebuffer(GL_FRAMEBUFFER, fboId);
            glViewport(0, 0, FBO_WIDTH, FBO_HEIGHT);
            glDisable(GL_SCISSOR_TEST);
            glClearColor(0, 0, 0, 0);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        } catch (RuntimeException | Error failure) {
            Throwable cleanupFailure = finishTilePassState(state, failure);
            throwFailure(cleanupFailure);
        }
    }

    /**
     * Render background tiles to the FBO.
     * Iterates up to 64x64 tiles, remaps VDP tile indices to atlas IDs, renders each.
     */
    public void renderTilesToFBO(GraphicsManager gm) {
        if (tilemapData == null || tilemapData.length < 2) {
            return;
        }

        int wordCount = tilemapData.length / 2;
        int mapHeight = Math.min(wordCount / MAP_WIDTH, MAP_HEIGHT);

        for (int ty = 0; ty < mapHeight; ty++) {
            for (int tx = 0; tx < MAP_WIDTH; tx++) {
                int wordIndex = ty * MAP_WIDTH + tx;
                int idx = wordIndex * 2;
                if (idx + 1 >= tilemapData.length) continue;

                int word = ((tilemapData[idx] & 0xFF) << 8) | (tilemapData[idx + 1] & 0xFF);
                if (word == 0) continue;

                reusableDesc.set(word);
                int vdpTile = reusableDesc.getPatternIndex();

                // Split remap: fish art starts at ARTTILE_SS_BG_FISH (0x051)
                int atlasId;
                if (vdpTile >= ARTTILE_SS_BG_FISH) {
                    atlasId = bgFishPatternBase + (vdpTile - ARTTILE_SS_BG_FISH);
                } else {
                    atlasId = bgCloudPatternBase + vdpTile;
                }

                int screenX = tx * TILE_SIZE;
                int screenY = ty * TILE_SIZE;

                gm.renderPatternWithId(atlasId, reusableDesc, screenX, screenY);
            }
        }

        fboNeedsRedraw = false;
    }

    /**
     * End the tile rendering pass - unbind FBO and restore viewport.
     */
    public void endTilePass() {
        if (tilePassStates.isEmpty()) return;
        TilePassState state = tilePassStates.peek();
        Throwable failure = restoreTilePassState(state, null);
        if (failure == null) {
            tilePassStates.pop();
        }
        throwFailure(failure);
    }

    private float[] fboProjectionBuffer() {
        float[] buffer = new float[16];
        new org.joml.Matrix4f().ortho2D(0, FBO_WIDTH, 0, FBO_HEIGHT).get(buffer);
        return buffer;
    }

    private float[] copyProjectionBuffer() {
        float[] current = graphicsManager.getProjectionMatrixBuffer();
        return current == null ? null : current.clone();
    }

    private Throwable finishTilePassState(TilePassState state, Throwable failure) {
        failure = restoreTilePassState(state, failure);
        if (failure == null && tilePassStates.peek() == state) {
            tilePassStates.pop();
        }
        return failure;
    }

    private Throwable restoreTilePassState(TilePassState state, Throwable failure) {
        failure = attempt(failure, () -> glBindFramebuffer(GL_FRAMEBUFFER, state.framebuffer()));
        failure = attempt(failure, () -> glViewport(state.viewport()[0], state.viewport()[1],
                state.viewport()[2], state.viewport()[3]));
        failure = attempt(failure, () -> glScissor(state.scissor()[0], state.scissor()[1],
                state.scissor()[2], state.scissor()[3]));
        failure = attempt(failure, () -> {
            if (state.scissorEnabled()) glEnable(GL_SCISSOR_TEST);
            else glDisable(GL_SCISSOR_TEST);
        });
        failure = attempt(failure, () -> glBlendEquationSeparate(
                state.blendEquationRgb(), state.blendEquationAlpha()));
        failure = attempt(failure, () -> glBlendFuncSeparate(state.blendSrcRgb(), state.blendDstRgb(),
                state.blendSrcAlpha(), state.blendDstAlpha()));
        failure = attempt(failure, () -> {
            if (state.blendEnabled()) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
        });
        failure = attempt(failure, () -> glClearColor(state.clearColor()[0], state.clearColor()[1],
                state.clearColor()[2], state.clearColor()[3]));
        if (failure == null) {
            graphicsManager.setProjectionMatrixBuffer(state.projectionBuffer());
        }
        return failure;
    }

    private static Throwable attempt(Throwable failure, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error nextFailure) {
            if (failure == null) {
                failure = nextFailure;
            } else {
                failure.addSuppressed(nextFailure);
            }
        }
        return failure;
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        if (failure instanceof Error errorFailure) throw errorFailure;
    }

    private record TilePassState(int framebuffer, int[] viewport, int[] scissor, float[] clearColor,
            boolean scissorEnabled, boolean blendEnabled,
            int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha,
            int blendEquationRgb, int blendEquationAlpha, float[] projectionBuffer) {
    }

    /**
     * Render the background with per-scanline scrolling using the shader.
     *
     * @param vScroll Vertical scroll offset for parallax
     */
    public void renderWithShader(float vScroll) {
        if (!initialized) return;

        ShaderState prior = ShaderState.capture();
        boolean hScrollBound = false;
        boolean shaderInUse = false;
        Throwable primaryFailure = null;
        try {
            hScrollBuffer.upload(hScrollData);

            // Bind 1D sampler texture before shader use; macOS may validate samplers
            // at program-use time.
            hScrollBuffer.bind(1);
            hScrollBound = true;

            shader.use();
            shaderInUse = true;
            shader.cacheUniformLocations();

            shader.setBackgroundTexture(0);
            shader.setHScrollTexture(1);

            int fullViewportX = prior.viewport()[0];
            int fullViewportY = prior.viewport()[1];
            int fullViewportWidth = prior.viewport()[2];
            int fullViewportHeight = prior.viewport()[3];
            int logicalWidth = Math.max(SpecialStageViewport.NATIVE_WIDTH,
                    specialStageViewport.logicalWidth());
            int outerPhysicalX = Math.round(
                    specialStageViewport.outerOriginX() * fullViewportWidth / (float) logicalWidth);
            int outerPhysicalWidth = Math.round(
                    SpecialStageViewport.NATIVE_WIDTH * fullViewportWidth / (float) logicalWidth);

            glEnable(GL_SCISSOR_TEST);
            glScissor(fullViewportX + outerPhysicalX, fullViewportY,
                    outerPhysicalWidth, fullViewportHeight);
            shader.setScreenDimensions((float) outerPhysicalWidth, (float) fullViewportHeight);
            shader.setActiveDisplayWidth((float) SCREEN_WIDTH);
            shader.setBGTextureDimensions(FBO_WIDTH, FBO_HEIGHT);
            shader.setVScrollBG(vScroll);
            shader.setViewportOffset((float) (fullViewportX + outerPhysicalX), (float) fullViewportY);
            shader.setBackdropColor(backdropR, backdropG, backdropB);
            shader.setFillTransparentWithBackdrop(fillTransparentWithBackdrop);

            glDisable(GL_BLEND);
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, fboTextureId);

            quadRenderer.draw(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            Throwable cleanupFailure = null;
            if (shaderInUse) {
                cleanupFailure = attempt(cleanupFailure, shader::stop);
            }
            if (hScrollBound) {
                cleanupFailure = attempt(cleanupFailure, () -> hScrollBuffer.unbind(1));
            }
            cleanupFailure = appendFailure(cleanupFailure, prior.restore());
            if (cleanupFailure != null) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throwFailure(cleanupFailure);
                }
            }
        }
    }

    private static Throwable appendFailure(Throwable failure, Throwable nextFailure) {
        if (nextFailure == null) return failure;
        if (failure == null) return nextFailure;
        failure.addSuppressed(nextFailure);
        return failure;
    }

    private record ShaderState(int[] viewport, int[] scissor, float[] clearColor,
            boolean scissorEnabled, boolean blendEnabled,
            int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha,
            int blendEquationRgb, int blendEquationAlpha, int activeTexture,
            int texture0, int texture1) {
        private static ShaderState capture() {
            int[] viewport = new int[4];
            int[] scissor = new int[4];
            float[] clearColor = new float[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetIntegerv(GL_SCISSOR_BOX, scissor);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
            boolean scissorEnabled = glIsEnabled(GL_SCISSOR_TEST);
            boolean blendEnabled = glIsEnabled(GL_BLEND);
            int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            int texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
            glActiveTexture(GL_TEXTURE1);
            int texture1 = glGetInteger(GL_TEXTURE_BINDING_1D);
            glActiveTexture(activeTexture);
            return new ShaderState(viewport, scissor, clearColor, scissorEnabled, blendEnabled,
                    glGetInteger(GL_BLEND_SRC_RGB), glGetInteger(GL_BLEND_DST_RGB),
                    glGetInteger(GL_BLEND_SRC_ALPHA), glGetInteger(GL_BLEND_DST_ALPHA),
                    glGetInteger(GL_BLEND_EQUATION_RGB), glGetInteger(GL_BLEND_EQUATION_ALPHA),
                    activeTexture, texture0, texture1);
        }

        private Throwable restore() {
            Throwable failure = null;
            failure = attempt(failure, () -> glViewport(viewport[0], viewport[1], viewport[2], viewport[3]));
            failure = attempt(failure, () -> glScissor(scissor[0], scissor[1], scissor[2], scissor[3]));
            failure = attempt(failure, () -> {
                if (scissorEnabled) glEnable(GL_SCISSOR_TEST);
                else glDisable(GL_SCISSOR_TEST);
            });
            failure = attempt(failure, () -> glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha));
            failure = attempt(failure, () -> glBlendFuncSeparate(blendSrcRgb, blendDstRgb,
                    blendSrcAlpha, blendDstAlpha));
            failure = attempt(failure, () -> {
                if (blendEnabled) glEnable(GL_BLEND);
                else glDisable(GL_BLEND);
            });
            failure = attempt(failure, () -> glClearColor(clearColor[0], clearColor[1],
                    clearColor[2], clearColor[3]));
            failure = attempt(failure, () -> {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, texture0);
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_1D, texture1);
                glActiveTexture(activeTexture);
            });
            return failure;
        }
    }

    /**
     * Set all 224 scanlines to the same horizontal scroll value.
     */
    public void setUniformHScroll(int scroll) {
        for (int i = 0; i < SCREEN_HEIGHT; i++) {
            hScrollData[i] = scroll;
        }
    }

    /**
     * Set per-scanline H-scroll data from an external array.
     * The source array should have at least SCREEN_HEIGHT (224) entries.
     */
    public void setHScrollData(int[] scrollData) {
        System.arraycopy(scrollData, 0, hScrollData, 0, Math.min(scrollData.length, SCREEN_HEIGHT));
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Marks the cached FBO as stale. Useful when palette colors change.
     */
    public void markDirty() {
        fboNeedsRedraw = true;
    }

    /**
     * Release GL resources.
     */
    public void cleanup() {
        Throwable failure = null;
        while (!tilePassStates.isEmpty()) {
            TilePassState state = tilePassStates.peek();
            try {
                endTilePass();
            } catch (RuntimeException | Error tilePassFailure) {
                failure = attempt(failure, () -> { throw tilePassFailure; });
                if (tilePassStates.peek() == state) break;
            }
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
        CleanupAttempt quadCleanup = attemptCleanup(failure, quadRenderer::cleanup);
        failure = quadCleanup.failure();
        if (fboHandle != null) {
            FboHelper.FboHandle resource = fboHandle;
            CleanupAttempt cleanup = attemptCleanup(failure, () -> FboHelper.destroy(resource));
            failure = cleanup.failure();
            if (cleanup.succeeded()) {
                fboHandle = null;
                fboId = -1;
                fboTextureId = -1;
                fboDepthId = -1;
            }
        }
        initialized = false;
        throwFailure(failure);
    }

    public boolean hasCleanupPendingOwnership() {
        return !tilePassStates.isEmpty() || hScrollBuffer != null || shader != null || fboHandle != null;
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
