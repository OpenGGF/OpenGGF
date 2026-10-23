package com.openggf.editor.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.ScenePresentationPass;

/**
 * Magnifies the complete editor world, including terrain and authoring markers,
 * before the window/HUD pass. At native zoom this allocates no graphics resources.
 */
public final class EditorCanvasRenderPass implements AutoCloseable {
    private final ScenePresentationPass presentation = new ScenePresentationPass();

    public void render(GraphicsManager graphics, int zoom, int logicalWidth, int logicalHeight, Runnable world) {
        if (zoom == 1 || !graphics.isGlInitialized()) {
            world.run();
            return;
        }
        var frame = new ScenePresentationPass.Frame(false, false, false, zoom, zoom, 0, 0, 0, 0, -1, -1);
        var scope = presentation.open(graphics, frame, null, logicalWidth, logicalHeight);
        // The scope captures/restores actual FBO and viewport state. Native terrain
        // uses gl_FragCoord; applying a projection only would leave it unscaled.
        scope.beginCommand().execute(0, 0, logicalWidth, logicalHeight);
        try {
            world.run();
        } catch (RuntimeException | Error failure) {
            graphics.discardQueuedCommands();
            try { scope.endCommand().execute(0, 0, logicalWidth, logicalHeight); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        scope.endCommand().execute(0, 0, logicalWidth, logicalHeight);
    }

    @Override public void close() { presentation.cleanup(); }
}
