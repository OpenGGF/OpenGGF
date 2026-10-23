package com.openggf.editor.render;

import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.EditorSpawnEditMode;
import com.openggf.editor.EditorStockObjectPalette;
import com.openggf.editor.LevelEditorController;
import com.openggf.level.objects.ObjectSpawn;

/** Screen-space placement composition, shared by desktop and gamepad chrome. */
public final class EditorPlacementScene {
    private EditorPlacementScene() { }
    private record Chip(String label, float x, float y, int color, float alpha) { }
    public interface Painter {
        void image(EditorObjectPreviewCache.Sprite sprite, float x, float y, float width, float height, float alpha);
        void rectangle(float x, float y, float width, float height, int color, float alpha, boolean fill);
        void chip(String label, float x, float y, int color, float alpha);
    }
    public static void draw(LevelEditorController controller, EditorObjectPreviewCache cache, Painter painter,
                            float left, float top, float width, float height, float unit, int cameraX, int cameraY) {
        if (controller.currentLevel() == null || controller.depth() != EditorHierarchyDepth.WORLD) return;
        var labels = new java.util.ArrayList<Chip>();
        for (var spawn : controller.currentLevel().getObjects())
            object(controller, cache, painter, labels, spawn, left, top, width, height, unit, cameraX, cameraY, false);
        for (var ring : controller.currentLevel().getRings()) {
            float x = left + (short) (ring.x() - cameraX) * unit, y = top + (short) (ring.y() - cameraY) * unit;
            if (x < left - 8 || y < top - 8 || x > left + width + 8 || y > top + height + 8) continue;
            var sprite = cache.ring();
            if (sprite != null) painter.image(sprite, x - sprite.originX() * unit, y - sprite.originY() * unit,
                    sprite.width() * unit, sprite.height() * unit, 1);
            if (controller.selectedRingSpawn() != null && controller.selectedRingSpawn().placementId() == ring.placementId())
                painter.rectangle(x - 9 * unit, y - 9 * unit, 18 * unit, 18 * unit, 0xffd36b, 1, false);
        }
        if (controller.spawnEditMode() == EditorSpawnEditMode.OBJECTS && controller.objectPaletteOrNull() != null
                && !controller.isPastePreviewActive()) {
            var cursor = controller.worldCursor();
            var brush = controller.objectPalette().brushPreview(cursor.x(), cursor.y());
            if (com.openggf.level.objects.authored.AuthoredGizmos.handlesKey(brush.objectKey())
                    && cursor.x() < controller.currentLevel().getMap().getWidth() * controller.currentLevel().getBlockPixelSize()
                    && cursor.y() < controller.currentLevel().getMap().getHeight() * controller.currentLevel().getBlockPixelSize())
                brush = com.openggf.editor.properties.EditorGizmoPlacements.initialize(brush, controller.currentLevel());
            object(controller, cache, painter, labels, brush, left, top, width, height, unit, cameraX, cameraY, true);
        }
        // Labels are chrome: rings and object pictures must never obscure their text.
        for (var chip : labels) painter.chip(chip.label(), chip.x(), chip.y(), chip.color(), chip.alpha());
        // Clipboard art is drawn by each front end alongside its terrain ghost, after destination validation.
    }
    private static void object(LevelEditorController controller, EditorObjectPreviewCache cache, Painter painter,
                               java.util.List<Chip> labels, ObjectSpawn spawn, float left, float top, float width, float height, float unit,
                               int cameraX, int cameraY, boolean ghost) {
        float x = left + (short) (spawn.x() - cameraX) * unit, y = top + (short) (spawn.y() - cameraY) * unit;
        var properties = spawn.authoredProperties();
        float margin = 256 * unit;
        if (properties == null && (x < left - margin || y < top - margin || x > left + width + margin || y > top + height + margin)) return;
        boolean selected = !ghost && controller.selectedObjectSpawn() != null
                && controller.selectedObjectSpawn().layoutIndex() == spawn.layoutIndex();
        boolean hovered = !ghost && controller.isSpawnEditing()
                && Math.abs((short) (controller.worldCursor().x() - spawn.x())) <= 12
                && Math.abs((short) (controller.worldCursor().y() - spawn.y())) <= 12;
        int accent = selected ? 0xffd36b : 0x70c8f0;
        if (properties != null) {
            float w = properties.width() * unit, h = properties.height() * unit;
            if (x + w < left || y + h < top || x > left + width || y > top + height) return;
            painter.rectangle(x, y, w, h, accent, ghost ? .08f : .13f, true);
            painter.rectangle(x, y, w, h, accent, ghost ? .6f : .9f, false);
            labels.add(new Chip(gizmoLabel(spawn.objectKey()), Math.max(left + 3, x + 4), Math.max(top + 3, y + 4), accent, 1));
            return;
        }
        var sprite = cache.sprite(spawn);
        if (sprite != null) {
            float sx = x - sprite.originX() * unit, sy = y - sprite.originY() * unit;
            float w = sprite.width() * unit, h = sprite.height() * unit;
            if (sx + w < left || sy + h < top || sx > left + width || sy > top + height) return;
            painter.image(sprite, sx, sy, w, h, ghost ? .5f : 1);
            if (selected || hovered || ghost) painter.rectangle(sx - 2, sy - 2, w + 4, h + 4, accent, ghost ? .65f : 1, false);
        } else if (controller.isSpawnEditing() && x >= left && y >= top && x < left + width && y < top + height) {
            String name = spawn.objectKey() == null ? controller.objectPalette().entries().get(spawn.objectId()).label()
                    : EditorStockObjectPalette.displayName(spawn.objectKey());
            labels.add(new Chip(name, x, y, selected ? 0xffd36b : 0xa8b3ce, ghost ? .5f : .9f));
        }
    }
    public static String gizmoLabel(String key) {
        if (key == null) return "Object";
        return switch (key) {
            case "openggf:camera-zone" -> "Camera";
            case "openggf:music-zone" -> "Music";
            case "openggf:warp" -> "Warp";
            case "openggf:stageplay-trigger" -> "Stageplay";
            default -> EditorStockObjectPalette.displayName(key);
        };
    }
}
