package com.openggf.game.sonic2.specialstage;

import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GLCommandable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GraphicsProjectionAccess;
import com.openggf.graphics.NativeIntensityPalette;
import com.openggf.graphics.PaletteView;
import com.openggf.graphics.PatternRenderCommand;
import com.openggf.graphics.RenderProjection;
import com.openggf.graphics.ShaderProgram;
import com.openggf.graphics.QuadRenderer;
import com.openggf.graphics.ScopedPaletteGraphicsAccess;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.WaterShaderProgram;
import com.openggf.level.Palette;
import org.lwjgl.opengl.GL;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.*;

/**
 * S2's projected, native-shadow terrain companion for palette-3/color-15 SAT operators.
 * Derived GL ownership only: no resource, palette bank or submitted frame enters rewind state.
 * Origin: 2026-10-22 s2_chain_pacing native special-stage presentation correction.
 */
final class Sonic2SpecialStageTerrainPass {
    private final GraphicsManager graphics;
    private final Sonic2SpecialStageRenderer terrain;
    private SpecialStageBackgroundRenderer background;
    private final Sonic2SpecialStageManager.BackgroundCommandPool backgroundCommands =
            new Sonic2SpecialStageManager.BackgroundCommandPool();
    private final Set<OwnedCommand> submitted = Collections.newSetFromMap(new IdentityHashMap<>());
    private ShaderProgram backdropShader;
    private final QuadRenderer backdropQuad = new QuadRenderer();
    private boolean backdropQuadOwned;
    private int backdropPaletteLocation, backdropRowLocation, backdropViewportLocation;
    private int backdropLogicalWidthLocation, backdropH32Location;
    private final Object paletteOwner = new Object();
    private final Palette[] sources = new Palette[4];
    private final PaletteView[] shadowSources = new PaletteView[4];
    private ScopedPaletteGraphicsAccess.Bank paletteBank;
    private boolean paletteAcquisitionPending;
    private SpecialStageOwnedFramebuffer projected;
    private SpecialStageViewport viewport = SpecialStageViewport.nativeViewport();
    private RenderProjection logicalProjection = projection(SpecialStageViewport.NATIVE_WIDTH);
    private Object atlasGeneration;
    private Object glContext;
    private GlState pendingRestoration;
    private boolean configured;
    private int backgroundPatternBase, trackPatternBase;
    private long stageGeneration, configurationGeneration;

    Sonic2SpecialStageTerrainPass(GraphicsManager graphics) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        terrain = new Sonic2SpecialStageRenderer(graphics);
        background = new SpecialStageBackgroundRenderer(graphics);
    }

    void configure(SpecialStageViewport viewport, int backgroundPatternBase,
                   int trackPatternBase, long stageGeneration) {
        Objects.requireNonNull(viewport, "viewport");
        boolean changed = !configured || !this.viewport.equals(viewport)
                || this.backgroundPatternBase != backgroundPatternBase || this.trackPatternBase != trackPatternBase
                || this.stageGeneration != stageGeneration;
        if (changed && !submitted.isEmpty())
            throw new IllegalStateException("Drain or discard the owning draw before changing S2 terrain configuration");
        if (changed) configurationGeneration++;
        if (!this.viewport.equals(viewport)) {
            cleanup();
            this.viewport = viewport;
        }
        terrain.setSpecialStageViewport(viewport);
        terrain.setPatternBases(backgroundPatternBase, trackPatternBase);
        terrain.beginStaticBackgroundStage(stageGeneration);
        background.setSpecialStageViewport(viewport);
        logicalProjection = projection(viewport.logicalWidth());
        this.backgroundPatternBase = backgroundPatternBase;
        this.trackPatternBase = trackPatternBase;
        this.stageGeneration = stageGeneration;
        configured = true;
    }

    /** The four sources stay unfaded; the GPU recipe applies source-line fade before native intensity. */
    int preparePaletteBank(Palette[] liveUnfadedSources) {
        if (liveUnfadedSources == null || liveUnfadedSources.length != 4)
            throw new IllegalArgumentException("S2 terrain requires exactly four live source palette lines");
        for (int line = 0; line < 4; line++)
            Objects.requireNonNull(liveUnfadedSources[line], "source palette line " + line);
        for (int line = 0; line < 4; line++) {
            Palette source = liveUnfadedSources[line];
            if (sources[line] != source) {
                sources[line] = source;
                shadowSources[line] = NativeIntensityPalette.shadow(source, line);
                terrain.invalidateStaticBackground();
            }
        }
        checkContext();
        if (pendingRestoration != null) throwFailure(restore(null));
        GlState prior = graphics.isHeadlessMode() ? null : GlState.capture(graphics);
        Throwable failure = null;
        int base = -1;
        try {
            paletteAcquisitionPending = true;
            paletteBank = ScopedPaletteGraphicsAccess.acquire(graphics, paletteOwner, shadowSources);
            paletteAcquisitionPending = false;
            base = ScopedPaletteGraphicsAccess.base(paletteBank, graphics, paletteOwner);
            terrain.useNativeIntensity(base, true);
            terrain.refreshNativePalettePresentation();
        } catch (RuntimeException | Error next) {
            failure = next;
        } finally {
            if (prior != null) {
                pendingRestoration = prior;
                failure = restore(failure);
            }
        }
        throwFailure(failure);
        return base;
    }

    void queueBackdrop(byte[] bgMappings, Palette[] palettes, int hScroll, int vScroll,
                       int trackFrame, int[] trackTiles, boolean planeB, boolean planeA) {
        if (!configured) throw new IllegalStateException("S2 terrain pass has not been configured");
        if (graphics.isHeadlessMode()) return;
        graphics.flushPatternBatch();
        preparePaletteBank(palettes);
        ensureResources();
        var command = new BackdropCommand(bgMappings == null ? null : bgMappings.clone(),
                palettes, hScroll, vScroll, trackFrame,
                trackTiles == null ? null : trackTiles.clone(), planeB, planeA);
        submitted.add(command);
        try { graphics.registerCommand(command); }
        catch (RuntimeException | Error failure) { command.discard(); throw failure; }
    }

    /** Fill the current MAIN target from the same known native-shadow color-0 recipe as the companion. */
    void queueNativeBackdropFill(int preparedShadowBankBase) {
        if (!configured) throw new IllegalStateException("S2 terrain pass has not been configured");
        if (graphics.isHeadlessMode()) return;
        checkContext();
        requirePaletteBank(preparedShadowBankBase);
        graphics.flushPatternBatch();
        ensureResources();
        var command = new NativeBackdropFillCommand(preparedShadowBankBase, paletteTexture());
        submitted.add(command);
        try { graphics.registerCommand(command); }
        catch (RuntimeException | Error failure) { command.discard(); throw failure; }
    }

    /** Captures the physical MAIN viewport now, before the queued companion selects its own target. */
    void withBackdrop(Runnable tableDraw) {
        Objects.requireNonNull(tableDraw, "tableDraw");
        if (graphics.isHeadlessMode()) { tableDraw.run(); return; }
        checkContext();
        if (projected == null || !projected.isPublished())
            throw new IllegalStateException("S2 terrain backdrop must be queued before the native table");
        int[] physical = new int[4];
        glGetIntegerv(GL_VIEWPORT, physical);
        SpriteSatInternalAccess.withShadow15Backdrop(graphics, projected.textureId(),
                physical[0], physical[1], physical[2], physical[3], tableDraw);
    }

    private void checkContext() {
        if (graphics.isHeadlessMode()) return;
        Object current = GL.getCapabilities();
        if (glContext != null && glContext != current && hasCleanupPendingOwnership())
            throw new IllegalStateException("S2 terrain resources must be cleaned in their creating GL context");
        glContext = current;
        Object atlas = graphics.getPatternAtlas();
        if (atlasGeneration != atlas) {
            if (!submitted.isEmpty())
                throw new IllegalStateException("Drain or discard the owning draw before retiring S2 terrain atlas generation");
            if (atlasGeneration != null) cleanup();
            atlasGeneration = atlas;
            terrain.onRenderContextGenerationChanged(atlas);
        }
    }

    private void ensureResources() {
        checkContext();
        if (pendingRestoration != null) {
            Throwable failure = restore(null);
            throwFailure(failure);
        }
        GlState prior = GlState.capture(graphics);
        Throwable failure = null;
        try {
            if (backdropShader == null) {
                backdropShader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER,
                        "shaders/shader_s2_native_backdrop.glsl");
                int program = backdropShader.getProgramId();
                backdropPaletteLocation = glGetUniformLocation(program, "Palette");
                backdropRowLocation = glGetUniformLocation(program, "PaletteRow");
                backdropViewportLocation = glGetUniformLocation(program, "DrawViewport");
                backdropLogicalWidthLocation = glGetUniformLocation(program, "LogicalWidth");
                backdropH32Location = glGetUniformLocation(program, "H32Range");
            }
            if (!backdropQuadOwned) { backdropQuad.init(); backdropQuadOwned = true; }
            if (!background.isInitialized() && background.hasCleanupPendingOwnership()) background.cleanup();
            if (!background.isInitialized()) background.init();
            if (projected != null && !projected.isPublished()) {
                projected.cleanup();
                if (projected.hasPendingOwnership())
                    throw new IllegalStateException("S2 projected framebuffer cleanup is still pending");
                projected = null;
            }
            if (projected == null) {
                projected = new SpecialStageOwnedFramebuffer(viewport.logicalWidth(),
                        SpecialStageViewport.NATIVE_HEIGHT, GL_CLAMP_TO_EDGE);
                projected.create();
            }
        } catch (IOException initializationFailure) {
            failure = new IllegalStateException("Cannot initialize S2 shadow terrain", initializationFailure);
        } catch (RuntimeException | Error initializationFailure) {
            failure = initializationFailure;
        } finally {
            pendingRestoration = prior;
            failure = restore(failure);
        }
        throwFailure(failure);
    }

    private void queueStaticBackground(byte[] mappings, Palette[] palettes) {
        terrain.refreshNativePalettePresentation();
        if (!terrain.needsStaticBackgroundRebuild(mappings, palettes)) return;
        background.beginFBOProjection();
        Throwable failure = null;
        try {
            graphics.registerCommand(backgroundCommands.obtainBegin(background));
            terrain.rebuildBackgroundToFBO(mappings, palettes);
        } catch (RuntimeException | Error next) {
            failure = next;
        } finally {
            try { background.endFBOProjection(); }
            catch (RuntimeException | Error next) { failure = combine(failure, next); }
            try {
                graphics.registerCommand(backgroundCommands.obtainEnd(background, terrain::invalidateStaticBackground));
            } catch (RuntimeException | Error next) { failure = combine(failure, next); }
        }
        if (failure != null) terrain.invalidateStaticBackground();
        throwFailure(failure);
    }

    private abstract class OwnedCommand implements GLCommandable {
        private final Object submittedAtlas = atlasGeneration;
        private final Object submittedContext = glContext;
        private final long submittedConfiguration = configurationGeneration;
        private boolean leased = true;

        @Override public final void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
            if (!leased) return;
            if (submittedContext != GL.getCapabilities() || submittedAtlas != graphics.getPatternAtlas())
                throw new IllegalStateException("S2 terrain command belongs to a retired rendering generation");
            if (submittedConfiguration != configurationGeneration)
                throw new IllegalStateException("S2 terrain command belongs to a retired stage configuration");
            checkContext();
            if (graphics.getShaderProgram() instanceof WaterShaderProgram)
                throw new IllegalStateException("S2 terrain requires the stage's published normal shader");
            if (pendingRestoration != null) throw new IllegalStateException("S2 terrain GL restoration is pending");
            pendingRestoration = GlState.capture(graphics);
            Throwable failure = null;
            try {
                // The companion can also run as an isolated owner with a level's
                // CPU water flags still live. Its producer and draws use S2's
                // Normal_palette; the captured caller flags restore transactionally.
                graphics.setWaterEnabled(false);
                graphics.setUseUnderwaterPaletteForBackground(false);
                PatternRenderCommand.resetFrameState();
                glDisable(GL_SCISSOR_TEST);
                glDisable(GL_DEPTH_TEST);
                glDisable(GL_STENCIL_TEST);
                glDisable(GL_DITHER);
                glDisable(GL_CULL_FACE);
                glDisable(GL_RASTERIZER_DISCARD);
                glDisable(GL_COLOR_LOGIC_OP);
                glDisable(GL_FRAMEBUFFER_SRGB);
                glColorMask(true, true, true, true);
                glDepthMask(true);
                glEnable(GL_BLEND);
                glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                draw(cameraX, cameraY, cameraWidth, cameraHeight);
            } catch (RuntimeException | Error drawFailure) {
                terrain.invalidateStaticBackground();
                failure = drawFailure;
            } finally {
                failure = restore(failure);
                release();
            }
            throwFailure(failure);
        }

        abstract void draw(int cameraX, int cameraY, int cameraWidth, int cameraHeight);

        @Override public void unwindAfterFailure(int x, int y, int width, int height) {
            Throwable failure = restore(null);
            discard();
            throwFailure(failure);
        }

        @Override public void discard() {
            if (!leased) return;
            terrain.invalidateStaticBackground();
            release();
        }

        private void release() {
            leased = false;
            submitted.remove(this);
        }
    }

    private final class BackdropCommand extends OwnedCommand {
        private final byte[] mappings;
        private final Palette[] palettes;
        private final int hScroll, vScroll, trackFrame;
        private final int[] trackTiles;
        private final boolean planeB, planeA;
        private final int preparedBankBase = ScopedPaletteGraphicsAccess.base(paletteBank, graphics, paletteOwner);
        private final int preparedPaletteTexture = paletteTexture();

        BackdropCommand(byte[] mappings, Palette[] palettes, int hScroll, int vScroll,
                        int trackFrame, int[] trackTiles, boolean planeB, boolean planeA) {
            this.mappings = mappings;
            this.palettes = palettes;
            this.hScroll = hScroll;
            this.vScroll = vScroll;
            this.trackFrame = trackFrame;
            this.trackTiles = trackTiles;
            this.planeB = planeB;
            this.planeA = planeA;
        }

        @Override void draw(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
            // The static 256-high source is rebuilt before selecting the projected 224-high target.
            try (var projection = GraphicsProjectionAccess.openProjection(graphics, logicalProjection)) {
                graphics.executeCapturedCommands(() -> {
                    try {
                        if (planeB) queueStaticBackground(mappings, palettes);
                        graphics.registerCommand((x, y, w, h) -> {
                            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, projected.fboId());
                            glViewport(0, 0, viewport.logicalWidth(), SpecialStageViewport.NATIVE_HEIGHT);
                            glDisable(GL_SCISSOR_TEST);
                            glClearColor(0, 0, 0, 1);
                            glClear(GL_COLOR_BUFFER_BIT);
                            drawNativeBackdrop(preparedBankBase, preparedPaletteTexture);
                        });
                        if (planeB) graphics.registerCommand(backgroundCommands.obtainShader(background, hScroll, vScroll));
                        if (planeA && trackTiles != null && trackTiles.length > 0)
                            terrain.renderTrack(trackFrame, trackTiles);
                    } catch (RuntimeException | Error producerFailure) {
                        try { graphics.flushPatternBatch(); }
                        catch (RuntimeException | Error flushFailure) { producerFailure.addSuppressed(flushFailure); }
                        throw producerFailure;
                    }
                }, cameraX, cameraY, cameraWidth, cameraHeight);
            }
        }
    }

    private final class NativeBackdropFillCommand extends OwnedCommand {
        private final int bankBase, texture;
        NativeBackdropFillCommand(int bankBase, int texture) { this.bankBase = bankBase; this.texture = texture; }
        @Override void draw(int x, int y, int width, int height) { drawNativeBackdrop(bankBase, texture); }
    }

    private void requirePaletteBank(int base) {
        if (!ScopedPaletteGraphicsAccess.valid(paletteBank, graphics, paletteOwner)
                || base != ScopedPaletteGraphicsAccess.base(paletteBank, graphics, paletteOwner))
            throw new IllegalArgumentException("S2 backdrop requires this pass's current prepared shadow bank");
    }

    private int paletteTexture() {
        Integer texture = graphics.getCombinedPaletteTextureId();
        if (texture == null || texture <= 0 || !glIsTexture(texture))
            throw new IllegalStateException("S2 backdrop has no allocated live palette texture");
        return texture;
    }

    private void drawNativeBackdrop(int preparedBankBase, int preparedTexture) {
        requirePaletteBank(preparedBankBase);
        if (preparedTexture != paletteTexture())
            throw new IllegalStateException("S2 backdrop palette texture belongs to a retired allocation");
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, preparedTexture);
        glBindSampler(0, 0);
        int columns = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int rows = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        if (columns != Palette.PALETTE_SIZE || preparedBankBase < 0 || preparedBankBase + 4 > rows)
            throw new IllegalStateException("S2 backdrop shadow bank is outside its allocated palette rows");
        int[] physical = new int[4];
        glGetIntegerv(GL_VIEWPORT, physical);
        if (physical[2] <= 0 || physical[3] <= 0)
            throw new IllegalStateException("S2 backdrop needs a nonempty draw viewport");
        backdropShader.use();
        glUniform1i(backdropPaletteLocation, 0);
        glUniform1i(backdropRowLocation, preparedBankBase);
        glUniform4f(backdropViewportLocation, physical[0], physical[1], physical[2], physical[3]);
        glUniform1f(backdropLogicalWidthLocation, viewport.logicalWidth());
        glUniform2f(backdropH32Location, viewport.innerH32OriginX(),
                viewport.innerH32OriginX() + SpecialStageViewport.H32_WIDTH);
        try { backdropQuad.draw(0, 0, viewport.logicalWidth(), SpecialStageViewport.NATIVE_HEIGHT); }
        finally { PatternRenderCommand.resetFrameState(); }
    }

    private Throwable restore(Throwable failure) {
        if (pendingRestoration != null) {
            if (glContext != GL.getCapabilities())
                return combine(failure, new IllegalStateException(
                        "Cannot restore S2 terrain bindings in a replacement GL context"));
            failure = pendingRestoration.restore(failure);
            if (!pendingRestoration.pending()) pendingRestoration = null;
        }
        PatternRenderCommand.resetFrameState();
        return failure;
    }

    void cleanup() {
        if (!submitted.isEmpty())
            throw new IllegalStateException("Drain or discard the owning draw before cleaning S2 terrain resources");
        if (!hasCleanupPendingOwnership()) {
            // Configuration may retire an empty, never-initialized viewport.
            // No creating GL context or restoration transaction exists yet.
            terrain.invalidateStaticBackground();
            return;
        }
        if (graphics.isHeadlessMode()) {
            // A logic-only host cannot retire GPU names or replay GL restoration.
            // Keep the whole pending transaction for a creating-context retry.
            if (hasCleanupPendingOwnership())
                throw new IllegalStateException("Cannot clean owned S2 terrain resources while graphics are headless");
            return;
        }
        if (glContext != null && glContext != GL.getCapabilities())
            throw new IllegalStateException("Cannot delete S2 terrain resources in a replacement GL context");
        Throwable failure = restore(null);
        if (pendingRestoration != null) throwFailure(failure);
        GlState prior = graphics.isHeadlessMode() ? null : GlState.capture(graphics);
        try {
            try { background.cleanup(); }
            catch (RuntimeException | Error next) { failure = combine(failure, next); }
            if (backdropShader != null) {
                try { backdropShader.cleanup(); backdropShader = null; }
                catch (RuntimeException | Error next) { failure = combine(failure, next); }
            }
            if (backdropQuadOwned) {
                try { backdropQuad.cleanup(); backdropQuadOwned = false; }
                catch (RuntimeException | Error next) { failure = combine(failure, next); }
            }
            if (projected != null) {
                failure = projected.cleanup(failure);
                if (!projected.hasPendingOwnership()) projected = null;
            }
            if (paletteAcquisitionPending) {
                try {
                    paletteBank = ScopedPaletteGraphicsAccess.acquire(graphics, paletteOwner, shadowSources);
                    paletteAcquisitionPending = false;
                } catch (RuntimeException | Error next) { failure = combine(failure, next); }
            }
            if (paletteBank != null) {
                try { ScopedPaletteGraphicsAccess.release(paletteBank, paletteOwner); paletteBank = null; }
                catch (RuntimeException | Error next) { failure = combine(failure, next); }
            }
        } finally {
            if (prior != null) {
                pendingRestoration = prior;
                failure = restore(failure);
            }
            terrain.invalidateStaticBackground();
        }
        throwFailure(failure);
    }

    boolean hasCleanupPendingOwnership() {
        return paletteBank != null || paletteAcquisitionPending || pendingRestoration != null
                || backdropShader != null || backdropQuadOwned
                || !submitted.isEmpty() || background.hasCleanupPendingOwnership()
                || projected != null && projected.hasPendingOwnership();
    }

    private static RenderProjection projection(int width) {
        float[] matrix = new Matrix4f().ortho2D(0, width, 0, SpecialStageViewport.NATIVE_HEIGHT)
                .get(new float[16]);
        return new RenderProjection() {
            @Override public float[] getProjectionMatrixBuffer() { return matrix; }
            @Override public boolean isFBOProjectionActive() { return true; }
            @Override public int getCurrentDisplayHeight() { return SpecialStageViewport.NATIVE_HEIGHT; }
        };
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException(failure);
    }

    /** Each successful restoration retires independently; failed writes remain owned for cleanup retry. */
    private static final class GlState {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

        static GlState capture(GraphicsManager graphics) {
            var state = new GlState();
            boolean water = graphics.isWaterEnabled();
            boolean underwaterBackground = graphics.isUseUnderwaterPaletteForBackground();
            state.pending.add(() -> graphics.setWaterEnabled(water));
            state.pending.add(() -> graphics.setUseUnderwaterPaletteForBackground(underwaterBackground));
            int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            int[] viewport = new int[4], scissor = new int[4];
            float[] clear = new float[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetIntegerv(GL_SCISSOR_BOX, scissor);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
            boolean[] mask = new boolean[4];
            var maskBytes = org.lwjgl.BufferUtils.createByteBuffer(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, maskBytes);
            for (int i = 0; i < 4; i++) mask[i] = maskBytes.get(i) != 0;
            boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
            int equationRgb = glGetInteger(GL_BLEND_EQUATION_RGB), equationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
            int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
            int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
            int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            int arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING), renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
            state.pending.add(() -> glBindFramebuffer(GL_READ_FRAMEBUFFER, read));
            state.pending.add(() -> glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw));
            state.pending.add(() -> glViewport(viewport[0], viewport[1], viewport[2], viewport[3]));
            state.pending.add(() -> glScissor(scissor[0], scissor[1], scissor[2], scissor[3]));
            state.pending.add(() -> glClearColor(clear[0], clear[1], clear[2], clear[3]));
            state.pending.add(() -> glColorMask(mask[0], mask[1], mask[2], mask[3]));
            state.pending.add(() -> glDepthMask(depthMask));
            for (int capability : new int[] {GL_SCISSOR_TEST, GL_DEPTH_TEST, GL_STENCIL_TEST, GL_BLEND,
                    GL_DITHER, GL_CULL_FACE, GL_RASTERIZER_DISCARD, GL_COLOR_LOGIC_OP, GL_FRAMEBUFFER_SRGB}) {
                boolean enabled = glIsEnabled(capability);
                state.pending.add(() -> { if (enabled) glEnable(capability); else glDisable(capability); });
            }
            state.pending.add(() -> glBlendEquationSeparate(equationRgb, equationAlpha));
            state.pending.add(() -> glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha));
            state.pending.add(() -> glUseProgram(program));
            state.pending.add(() -> glBindVertexArray(vao));
            state.pending.add(() -> glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer));
            state.pending.add(() -> glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer));
            int active = glGetInteger(GL_ACTIVE_TEXTURE);
            try {
                for (int unit = 0; unit <= 6; unit++) {
                    int textureUnit = unit;
                    glActiveTexture(GL_TEXTURE0 + unit);
                    int texture2d = glGetInteger(GL_TEXTURE_BINDING_2D), texture1d = glGetInteger(GL_TEXTURE_BINDING_1D);
                    int sampler = glGetIntegeri(GL_SAMPLER_BINDING, unit);
                    state.pending.add(() -> {
                        try {
                            glActiveTexture(GL_TEXTURE0 + textureUnit);
                            glBindTexture(GL_TEXTURE_2D, texture2d);
                            glBindTexture(GL_TEXTURE_1D, texture1d);
                            glBindSampler(textureUnit, sampler);
                        } finally { glActiveTexture(active); }
                    });
                }
            } finally { glActiveTexture(active); }
            state.pending.add(() -> glActiveTexture(active));
            return state;
        }

        boolean pending() { return !pending.isEmpty(); }

        Throwable restore(Throwable failure) {
            for (int attempt = pending.size(); attempt > 0; attempt--) {
                Runnable action = pending.removeFirst();
                try { action.run(); }
                catch (RuntimeException | Error next) { pending.addLast(action); failure = combine(failure, next); }
            }
            return failure;
        }
    }
}
