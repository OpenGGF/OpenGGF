package com.openggf.editor.render;

import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.LevelEditorController;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;

import java.util.Objects;

/** World-space editing guides drawn underneath the window-resolution editor UI. */
public final class EditorOverlayRenderer {
    private final LevelEditorController controller;
    private final EditorWorldOverlayRenderer worldOverlay;

    public EditorOverlayRenderer(LevelEditorController controller) {
        this(controller, GameServices.graphics());
    }

    public EditorOverlayRenderer(LevelEditorController controller, GraphicsManager graphicsManager) {
        this.controller = Objects.requireNonNull(controller, "controller");
        worldOverlay = new EditorWorldOverlayRenderer(controller, graphicsManager);
    }

    public void renderWorldSpaceOverlay() {
        if (controller.depth() == EditorHierarchyDepth.WORLD) worldOverlay.render();
    }
}
