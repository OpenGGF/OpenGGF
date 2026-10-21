package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneCanvas;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Engine-owned reusable scene canvas, shared by authoring, playback and offscreen capture. */
public final class SceneSurface implements AutoCloseable {
    private final SceneRenderer renderer = new SceneRenderer();
    private final SceneFontAtlas font = SceneFontAtlas.load();
    private List<SceneDrawOp> frame = List.of();
    private int width, height;
    private boolean closed;

    /** Records without requiring GL. A failed draw leaves the previous complete recording intact. */
    public void record(int width, int height, Consumer<SceneCanvas> draw) {
        requireOpen();
        if (width < 1 || height < 1 || width > 1920 || height > 1080)
            throw new IllegalArgumentException("Scene surface dimensions exceed limits");
        var canvas = new RecordingCanvas(width, height, font);
        Objects.requireNonNull(draw, "draw").accept(canvas);
        frame = List.copyOf(canvas.ops());
        this.width = width; this.height = height;
    }

    /** Render on the owning GL thread. The viewport is {x,y,width,height} in framebuffer pixels. */
    public void render(float[] projection, int[] viewport) {
        requireOpen();
        if (width == 0) return;
        if (projection == null || projection.length != 16 || viewport == null || viewport.length != 4)
            throw new IllegalArgumentException("A projection and viewport are required");
        try {
            if (!renderer.initialized()) renderer.init();
            renderer.render(frame, projection, width, height, viewport);
        } catch (IOException failure) {
            throw new IllegalStateException("Scene renderer could not be initialized", failure);
        }
    }

    public List<SceneDrawOp> recordedFrame() { requireOpen(); return frame; }
    public void releaseTextures() { requireOpen(); if (renderer.initialized()) renderer.releaseTextures(); }

    /** The renderer flips scene y coordinates into the existing bottom-up projection convention. */
    public static float[] projection(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Positive dimensions required");
        return new float[] {2f / width, 0, 0, 0, 0, 2f / height, 0, 0, 0, 0, -1, 0, -1, -1, 0, 1};
    }

    private void requireOpen() { if (closed) throw new IllegalStateException("Scene surface is closed"); }
    @Override public void close() {
        if (closed) return;
        closed = true; frame = List.of(); renderer.cleanup();
    }
}
