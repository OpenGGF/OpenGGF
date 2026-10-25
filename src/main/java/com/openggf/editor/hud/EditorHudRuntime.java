package com.openggf.editor.hud;

import com.openggf.control.InputHandler;
import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.EditorWindowChrome;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The engine's built-in {@link EditorWindowChrome}: the controller-first editor HUD. Each
 * displayed frame lays out the HUD around the integer-scaled game and draws it after the final
 * display pass; each editor tick runs the HUD before the world editing input handler. It reads the
 * pads itself, so the engine's pad mappings go neutral while it is active. A separate desktop
 * editor on the class path replaces it.
 */
public final class EditorHudRuntime implements EditorWindowChrome {
    private final EditorHudCanvas canvas = new EditorHudCanvas();
    private final EditorHudPainter painter = new EditorHudPainter();
    private EditorHud hud;
    private StageplayHud stageplay;
    private Host host;
    private boolean active;
    private boolean viewportOverridden;
    private EditorHudLayout layout;
    private double pointerScale = Double.NaN;
    private boolean stageplayShown;

    @Override
    public void init(Host host, float framebufferScale) throws java.io.IOException {
        if (canvas.isInitialized()) return;
        this.host = host;
        canvas.init();
        hud = new EditorHud(host.controller());
        if (host.stageplay() != null) stageplay = new StageplayHud(host.stageplay());
    }

    public boolean isAvailable() { return canvas.isInitialized() && hud != null; }

    @Override
    public boolean isActive() { return active; }

    @Override
    public boolean setActive(boolean editorFrame) {
        boolean next = editorFrame && isAvailable();
        if (active && !next) finishPendingEdits();
        // The pads were read by menus or gameplay meanwhile; buttons still held belong to them.
        if (!active && next) {
            hud.reactivated();
            if (stageplay != null) stageplay.reactivated();
        }
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
        boolean showingStageplay = stageplayActive();
        boolean workbench = showingStageplay || hud.controller().depth() != EditorHierarchyDepth.WORLD;
        layout = showingStageplay ? host.stageplay().pickingPosition()
                ? EditorHudLayout.positionPicker(framebufferWidth,framebufferHeight,logicalWidth,logicalHeight)
                : StageplayHud.layout(framebufferWidth, framebufferHeight, logicalWidth, logicalHeight)
                : EditorHudLayout.of(framebufferWidth, framebufferHeight, logicalWidth, logicalHeight, workbench);
        pointerScale = windowWidth > 0 ? framebufferWidth / (double) windowWidth / layout.uiScale() : Double.NaN;
        hud.setPointerScale(pointerScale);
        if (stageplay != null) stageplay.setPointerScale(pointerScale);
        viewportOverridden = true;
        int[] game = layout.gameViewport();
        return new int[] {game[0], framebufferHeight - game[1] - game[3], game[2], game[3]};
    }

    @Override
    public void render(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight) {
        if (!active || layout == null) return;
        canvas.begin(framebufferWidth, framebufferHeight, layout.uiScale());
        try {
            if (stageplayActive()) stageplay.draw(canvas, layout);
            else {
                painter.draw(canvas, layout, hud);
                if (stageplay != null) {
                    var button = stageplayButton();
                    canvas.panel(button.x(), button.y(), button.w(), button.h(), EditorHudTheme.ACCENT_DARK, 1);
                    canvas.text("Stageplay  F4", button.x() + 6, button.y() + 5,
                            EditorHudCanvas.Size.SMALL, EditorHudTheme.TEXT, 1);
                }
            }
        } finally {
            canvas.end();
        }
    }

    @Override
    public boolean update(InputHandler input) {
        if (!active) return false;
        boolean showingStageplay = stageplayActive();
        if (showingStageplay != stageplayShown) {
            finishPendingEdits();
            hud.reactivated();
            if (stageplay != null) stageplay.reactivated();
            stageplayShown = showingStageplay;
        }
        if (showingStageplay) return stageplay.update(input);
        if (stageplay != null && (input.isKeyPressed(GLFW_KEY_F4)
                || layout != null && !Double.isNaN(pointerScale)
                && input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT)
                && stageplayButton().contains(input.getMouseX() * pointerScale, input.getMouseY() * pointerScale))) {
            finishPendingEdits();
            host.stageplay().activate();
            return true;
        }
        return hud.update(input, host::dispatch);
    }

    private boolean stageplayActive() { return stageplay != null && host.stageplay().active(); }

    private EditorHudLayout.Rect stageplayButton() {
        return new EditorHudLayout.Rect(layout.width() - 98, 3, 92, 16);
    }

    @Override
    public void finishPendingEdits() {
        if (hud != null) hud.finishPendingEdits();
        if (stageplay != null) stageplay.finishPendingEdits();
    }

    @Override
    public boolean ownsGamepads() { return true; }

    @Override
    public void close() {
        painter.close();
        canvas.close();
    }
}
