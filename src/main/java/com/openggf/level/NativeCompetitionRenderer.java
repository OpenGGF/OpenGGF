package com.openggf.level;

import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.game.internal.RetainedPlaneDescriptorSource;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GraphicsProjectionAccess;
import com.openggf.graphics.RenderProjection;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpriteSatInternalAccess;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.util.FboHelper;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/** Pure presentation of native split registers and immutable, already admitted sprite pages. */
public final class NativeCompetitionRenderer implements AutoCloseable {
    public static final int WIDTH = 320, HEIGHT = 224;

    /**
     * When planes is non-null, this view's foreground and background are that source's complete
     * retained images, independent of the zone feature provider. A null source keeps the zone
     * provider's retained planes or the ordinary layout.
     */
    public record View(CameraSnapshot camera, int rasterStart, int rasterHeight,
                       int foregroundVScroll, int backgroundVScroll, int backgroundX,
                       int backgroundPeriod, int[] horizontalScroll, SpritePresentation.Frame sprites,
                       RetainedPlaneDescriptorSource planes) {
        public View {
            Objects.requireNonNull(camera, "camera");
            Objects.requireNonNull(sprites, "sprites");
            requireRaster(rasterStart, rasterHeight, horizontalScroll.length);
            if ((horizontalScroll.length != HEIGHT && horizontalScroll.length != 448) || backgroundPeriod <= 0) {
                throw new IllegalArgumentException("Native view needs a full packed scroll page and positive BG period");
            }
            horizontalScroll = horizontalScroll.clone();
        }
        /** Existing callers retain the zone provider's planes. */
        public View(CameraSnapshot camera, int rasterStart, int rasterHeight,
                    int foregroundVScroll, int backgroundVScroll, int backgroundX,
                    int backgroundPeriod, int[] horizontalScroll, SpritePresentation.Frame sprites) {
            this(camera, rasterStart, rasterHeight, foregroundVScroll, backgroundVScroll, backgroundX,
                    backgroundPeriod, horizontalScroll, sprites, null);
        }
        @Override public int[] horizontalScroll() { return horizontalScroll.clone(); }
    }

    private final int height;
    public NativeCompetitionRenderer() { this(HEIGHT); }
    public NativeCompetitionRenderer(int height) {
        if (height != HEIGHT && height != 448) throw new IllegalArgumentException("Unsupported view height");
        this.height = height;
    }
    private FboHelper.FboHandle scratch;
    private boolean drawing;
    private RenderProjection projection() { return new RenderProjection() {
        private final float[] matrix = new Matrix4f().ortho2D(0, WIDTH, 0, height).get(new float[16]);
        @Override public float[] getProjectionMatrixBuffer() { return matrix; }
        @Override public boolean isFBOProjectionActive() { return true; }
        @Override public int getCurrentDisplayHeight() { return height; }
    }; }

    /** Producers use native screen coordinates and whole mapping footprints in SAT order. */
    public static SpritePresentation.Frame prepareSprites(GraphicsManager graphics, int start, int height,
                                                           Runnable producer) {
        return prepareSprites(graphics, start, height, HEIGHT, producer);
    }
    public static SpritePresentation.Frame prepareSprites(GraphicsManager graphics, int start, int height,
                                                           int screenHeight, Runnable producer) {
        requireRaster(start, height, screenHeight);
        return SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            SpriteSatInternalAccess.beginCollection(graphics, SpriteSatMaskPostProcessor.Raster.h40(0, start, height));
            producer.run();
            graphics.endSpriteSatCollectionAndReplay();
        });
    }

    /** No logic, palette cycler, camera tracker, or animation owner is advanced here. */
    public void draw(GameplayModeContext context, List<View> views, int backdropRgb) {
        Objects.requireNonNull(context, "context");
        views = List.copyOf(views);
        for (var view : views) if (view.horizontalScroll.length != height)
            throw new IllegalArgumentException("View scroll page does not match output height");
        var level = Objects.requireNonNull(context.getLevelManager(), "levelManager");
        var graphics = level.graphicsManager;
        if (drawing) throw new IllegalStateException("Nested native competition renderer");
        if (!graphics.isGlInitialized()) throw new IllegalStateException("Native views require an owned GL context");
        // Commands and batches retain their projection owner through execution.
        graphics.flushPatternBatch();
        graphics.flush();
        int[] viewport = new int[4], scissor = new int[4];
        float[] clear = new float[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissor);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
        boolean scissorEnabled = glIsEnabled(GL_SCISSOR_TEST);
        int drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int gx = graphics.getViewportX(), gy = graphics.getViewportY();
        int gw = graphics.getViewportWidth(), gh = graphics.getViewportHeight();
        drawing = true;
        try {
            ensureScratch();
            glDisable(GL_SCISSOR_TEST);
            try (var ignored = GraphicsProjectionAccess.openProjection(graphics, projection())) {
            graphics.setViewport(0, 0, WIDTH, height);
            for (var view : views) {
                glBindFramebuffer(GL_FRAMEBUFFER, scratch.fboId());
                glViewport(0, 0, WIDTH, height);
                glClearColor(((backdropRgb >>> 16) & 255) / 255f,
                        ((backdropRgb >>> 8) & 255) / 255f, (backdropRgb & 255) / 255f, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                level.spritePresentationRenderer().withNativeCompetitionView(view, () ->
                        graphics.executeCapturedCommands(() -> {
                            level.drawWithRenderOptions(context.getSpriteManager(), LevelManager.LevelRenderOptions.tilesOnly());
                            SpritePresentationRenderer.drawNative(graphics, view.sprites(), view.camera().x(),
                                    view.foregroundVScroll(), layer -> true);
                        }, view.camera().x(), view.foregroundVScroll(), WIDTH, height));
                // Offscreen terrain passes own their whole FBO. Clip only the
                // completed view, so a split never truncates priority/BG scratch work.
                glBindFramebuffer(GL_READ_FRAMEBUFFER, scratch.fboId());
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
                int bottom = height - view.rasterStart() - view.rasterHeight();
                int top = height - view.rasterStart();
                glBlitFramebuffer(0, bottom, WIDTH, top,
                        viewport[0], viewport[1] + bottom * viewport[3] / height,
                        viewport[0] + viewport[2], viewport[1] + top * viewport[3] / height,
                        GL_COLOR_BUFFER_BIT, GL_NEAREST);
            }
            }
        } finally {
            graphics.setViewport(gx, gy, gw, gh);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            if (scissorEnabled) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
            glClearColor(clear[0], clear[1], clear[2], clear[3]);
            drawing = false;
        }
    }

    private void ensureScratch() {
        if (scratch != null) return;
        int active = glGetInteger(GL_ACTIVE_TEXTURE), texture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            scratch = FboHelper.createColorOnly(WIDTH, height, GL_CLAMP_TO_EDGE);
            if (scratch == null) throw new IllegalStateException("Cannot create native view framebuffer");
        } finally {
            glActiveTexture(active);
            glBindTexture(GL_TEXTURE_2D, texture);
        }
    }

    private static void requireRaster(int start, int height, int screenHeight) {
        if (start < 0 || height <= 0 || start + height > screenHeight) {
            throw new IllegalArgumentException("Native view raster exceeds the logical screen");
        }
    }

    @Override public void close() {
        if (drawing) throw new IllegalStateException("Cannot close an active native view");
        FboHelper.destroy(scratch);
        scratch = null;
    }
}
