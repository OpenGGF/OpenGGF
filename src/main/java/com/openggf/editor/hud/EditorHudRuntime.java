package com.openggf.editor.hud;

import com.openggf.control.InputHandler;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.EditorWindowChrome;

/**
 * The engine's built-in {@link EditorWindowChrome}: the controller-first editor HUD. Each
 * displayed frame lays out the HUD around the integer-scaled game and draws it after the final
 * display pass; each editor tick runs the HUD before the classic keyboard handler. It reads the
 * pads itself, so the engine's pad mappings go neutral while it is active. A separate desktop
 * editor on the class path replaces it.
 */
public final class EditorHudRuntime implements EditorWindowChrome {
    private final EditorHudCanvas canvas = new EditorHudCanvas();
    private final EditorHudPainter painter = new EditorHudPainter();
    private EditorHud hud;
    private Host host;
    private boolean active;
    private boolean viewportOverridden;
    private EditorHudLayout layout;

    @Override
    public void init(Host host, float framebufferScale) throws java.io.IOException {
        if (canvas.isInitialized()) return;
        this.host = host;
        canvas.init();
        hud = new EditorHud(host.controller());
    }

    public boolean isAvailable() { return canvas.isInitialized() && hud != null; }

    @Override
    public boolean isActive() { return active; }

    @Override
    public boolean setActive(boolean editorFrame) {
        boolean next = editorFrame && isAvailable();
        if (active && !next) hud.finishPendingEdits();
        // The pads were read by menus or gameplay meanwhile; buttons still held belong to them.
        if (!active && next) hud.reactivated();
        active = next;
        return active;
    }

    @Override
    public int[] gameViewport(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight,
                              int logicalWidth, int logicalHeight) {
        if (!active) {
            if (!viewportOverridden) return null;
            viewportOverridden = false;
            return new int[0];
        }
        boolean workbench = hud.controller().depth() != EditorHierarchyDepth.WORLD;
        layout = EditorHudLayout.of(framebufferWidth, framebufferHeight, logicalWidth, logicalHeight, workbench);
        hud.setPointerScale(windowWidth > 0 ? framebufferWidth / (double) windowWidth / layout.uiScale() : Double.NaN);
        viewportOverridden = true;
        int[] game = layout.gameViewport();
        return new int[] {game[0], framebufferHeight - game[1] - game[3], game[2], game[3]};
    }

    @Override
    public void render(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight) {
        if (!active || layout == null) return;
        canvas.begin(framebufferWidth, framebufferHeight, layout.uiScale());
        try {
            painter.draw(canvas, layout, hud);
        } finally {
            canvas.end();
        }
    }

    @Override
    public boolean update(InputHandler input) {
        return active && hud.update(input, host::dispatch);
    }

    @Override
    public void finishPendingEdits() {
        if (hud != null) hud.finishPendingEdits();
    }

    @Override
    public boolean ownsGamepads() { return true; }

    @Override
    public void close() {
        painter.close();
        canvas.close();
    }
}
