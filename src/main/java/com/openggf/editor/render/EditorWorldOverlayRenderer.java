package com.openggf.editor.render;

import com.openggf.camera.Camera;
import com.openggf.editor.EditorSpawnEditMode;
import com.openggf.editor.LevelEditorController;
import com.openggf.game.GameServices;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.EditorModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandGroup;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_LINES;

public class EditorWorldOverlayRenderer {
    public record SpawnLabel(String text, int worldX, int worldY) {}

    public record SpawnOverlay(List<GLCommand> markerCommands, List<SpawnLabel> labels) {
        public SpawnOverlay {
            markerCommands = List.copyOf(markerCommands);
            labels = List.copyOf(labels);
        }
    }

    private static final float CURSOR_R = 1.0f;
    private static final float CURSOR_G = 0.92f;
    private static final float CURSOR_B = 0.30f;
    private static final float GRID_R = 0.36f;
    private static final float GRID_G = 0.78f;
    private static final float GRID_B = 0.95f;
    private static final float OBJECT_R = 1.0f;
    private static final float OBJECT_G = 0.45f;
    private static final float OBJECT_B = 0.25f;
    private static final float RING_R = 1.0f;
    private static final float RING_G = 0.86f;
    private static final float RING_B = 0.15f;
    private static final int OBJECT_MARKER_RADIUS = 6;
    private static final int RING_MARKER_RADIUS = 4;

    private final GraphicsManager graphicsManager;
    private final LevelEditorController controller;
    private final EditorTextRenderer textRenderer;
    private final EditorCollisionOverlayBuilder collisionOverlayBuilder;

    public EditorWorldOverlayRenderer() {
        this(null, GameServices.graphics());
    }

    public EditorWorldOverlayRenderer(GraphicsManager graphicsManager) {
        this(null, graphicsManager);
    }

    public EditorWorldOverlayRenderer(LevelEditorController controller, GraphicsManager graphicsManager) {
        this(controller, graphicsManager, new EditorTextRenderer(graphicsManager));
    }

    public EditorWorldOverlayRenderer(LevelEditorController controller,
                                      GraphicsManager graphicsManager,
                                      EditorTextRenderer textRenderer) {
        this(controller, graphicsManager, textRenderer, new EditorCollisionOverlayBuilder());
    }

    public EditorWorldOverlayRenderer(LevelEditorController controller,
                                      GraphicsManager graphicsManager,
                                      EditorTextRenderer textRenderer,
                                      EditorCollisionOverlayBuilder collisionOverlayBuilder) {
        this.graphicsManager = Objects.requireNonNull(graphicsManager, "graphicsManager");
        this.controller = controller;
        this.textRenderer = Objects.requireNonNull(textRenderer, "textRenderer");
        this.collisionOverlayBuilder = Objects.requireNonNull(collisionOverlayBuilder, "collisionOverlayBuilder");
    }

    public void render() {
        EditorModeContext editorMode = SessionManager.getCurrentEditorMode();
        if (editorMode == null) {
            return;
        }
        List<GLCommand> commands = new ArrayList<>();
        appendWorldCommands(commands, editorMode.getCursor());
        SpawnOverlay spawnOverlay = currentSpawnOverlay(editorMode);
        commands.addAll(spawnOverlay.markerCommands());
        appendCollisionOverlayCommands(commands, currentCollisionOverlay(editorMode));
        if (!commands.isEmpty()) {
            graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, commands));
        }
        renderSpawnLabels(spawnOverlay.labels(), editorMode.getCamera());
    }

    protected void appendWorldCommands(List<GLCommand> commands, EditorCursorState cursor) {
        appendGridCommands(commands, cursor);
        appendCursorCommands(commands, cursor);
    }

    protected void appendGridCommands(List<GLCommand> commands, EditorCursorState cursor) {
        int baseX = cursor.x() & ~15;
        int baseY = cursor.y() & ~15;
        int span = 64;

        for (int x = baseX - span; x <= baseX + span; x += 16) {
            appendLine(commands, x, baseY - span, x, baseY + span, GRID_R, GRID_G, GRID_B);
        }
        for (int y = baseY - span; y <= baseY + span; y += 16) {
            appendLine(commands, baseX - span, y, baseX + span, y, GRID_R, GRID_G, GRID_B);
        }
    }

    protected void appendCursorCommands(List<GLCommand> commands, EditorCursorState cursor) {
        int x = cursor.x();
        int y = cursor.y();
        int outer = 16;
        int inner = 6;

        appendRectOutline(commands, x - outer, y - outer, x + outer, y + outer,
                CURSOR_R, CURSOR_G, CURSOR_B);
        appendLine(commands, x - outer, y, x - inner, y, CURSOR_R, CURSOR_G, CURSOR_B);
        appendLine(commands, x + inner, y, x + outer, y, CURSOR_R, CURSOR_G, CURSOR_B);
        appendLine(commands, x, y - outer, x, y - inner, CURSOR_R, CURSOR_G, CURSOR_B);
        appendLine(commands, x, y + inner, x, y + outer, CURSOR_R, CURSOR_G, CURSOR_B);
    }

    protected SpawnOverlay buildSpawnOverlay(Level level,
                                             EditorSpawnEditMode mode,
                                             int cameraX,
                                             int cameraY,
                                             int cameraWidth,
                                             int cameraHeight) {
        if (level == null || mode == null || mode == EditorSpawnEditMode.TILES) {
            return new SpawnOverlay(List.of(), List.of());
        }

        List<GLCommand> markers = new ArrayList<>();
        List<SpawnLabel> labels = new ArrayList<>();
        for (ObjectSpawn spawn : level.getObjects()) {
            if (!isVisible(spawn.x(), spawn.y(), cameraX, cameraY, cameraWidth, cameraHeight)) {
                continue;
            }
            appendCross(markers, cameraDomainCoordinate(spawn.x(), cameraX),
                    cameraDomainCoordinate(spawn.y(), cameraY),
                    OBJECT_MARKER_RADIUS,
                    OBJECT_R, OBJECT_G, OBJECT_B);
            labels.add(new SpawnLabel(String.format("%02X:%02X", spawn.objectId(), spawn.subtype()),
                    spawn.x(), spawn.y()));
        }
        for (RingSpawn spawn : level.getRings()) {
            if (!isVisible(spawn.x(), spawn.y(), cameraX, cameraY, cameraWidth, cameraHeight)) {
                continue;
            }
            appendDiamond(markers, cameraDomainCoordinate(spawn.x(), cameraX),
                    cameraDomainCoordinate(spawn.y(), cameraY),
                    RING_MARKER_RADIUS,
                    RING_R, RING_G, RING_B);
        }
        return new SpawnOverlay(markers, labels);
    }

    private SpawnOverlay currentSpawnOverlay(EditorModeContext editorMode) {
        Camera camera = editorMode.getCamera();
        Level level = editorMode.getWorldSession().getCurrentLevel();
        if (controller == null || camera == null || level == null) {
            return new SpawnOverlay(List.of(), List.of());
        }
        return buildSpawnOverlay(level, controller.spawnEditMode(), camera.getXWithShake(),
                camera.getYWithShake(), camera.getWidth(), camera.getHeight());
    }

    private List<EditorCollisionOverlayBuilder.Cell> currentCollisionOverlay(EditorModeContext editorMode) {
        Camera camera = editorMode.getCamera();
        Level level = editorMode.getWorldSession().getCurrentLevel();
        if (controller == null || camera == null || level == null) {
            return List.of();
        }
        return collisionOverlayBuilder.build(level, controller.collisionPath(),
                camera.getXWithShake(), camera.getYWithShake(), camera.getWidth(), camera.getHeight(),
                controller.isCollisionOverlayEnabled());
    }

    protected void appendCollisionOverlayCommands(List<GLCommand> commands,
                                                  List<EditorCollisionOverlayBuilder.Cell> cells) {
        for (EditorCollisionOverlayBuilder.Cell cell : cells) {
            float r;
            float g;
            float b;
            switch (cell.mode()) {
                case NO_COLLISION -> { r = 0.35f; g = 0.35f; b = 0.35f; }
                case TOP_SOLID -> { r = 0.20f; g = 0.95f; b = 0.35f; }
                case LEFT_RIGHT_BOTTOM_SOLID -> { r = 0.25f; g = 0.55f; b = 1.0f; }
                case ALL_SOLID -> { r = 1.0f; g = 0.25f; b = 0.25f; }
                default -> throw new IllegalStateException("Unexpected collision mode: " + cell.mode());
            }
            appendRectOutline(commands, cell.worldX(), cell.worldY(),
                    cell.worldX() + 16, cell.worldY() + 16, r, g, b);
        }
    }

    private void renderSpawnLabels(List<SpawnLabel> labels, Camera camera) {
        if (camera == null) {
            return;
        }
        int cameraX = camera.getXWithShake();
        int cameraY = camera.getYWithShake();
        List<EditorTextRenderer.PositionedLine> positionedLines = new ArrayList<>(labels.size());
        for (SpawnLabel label : labels) {
            positionedLines.add(new EditorTextRenderer.PositionedLine(label.text(),
                    signedDelta(label.worldX(), cameraX) + 8,
                    signedDelta(label.worldY(), cameraY) - 5));
        }
        textRenderer.renderPositionedLines(positionedLines);
    }

    private static boolean isVisible(int x, int y, int cameraX, int cameraY,
                                     int cameraWidth, int cameraHeight) {
        int screenX = signedDelta(x, cameraX);
        int screenY = signedDelta(y, cameraY);
        return screenX >= 0 && screenX < cameraWidth && screenY >= 0 && screenY < cameraHeight;
    }

    private static int signedDelta(int worldCoordinate, int cameraCoordinate) {
        return (short) (worldCoordinate - cameraCoordinate);
    }

    private static int cameraDomainCoordinate(int worldCoordinate, int cameraCoordinate) {
        return cameraCoordinate + signedDelta(worldCoordinate, cameraCoordinate);
    }

    private static void appendCross(List<GLCommand> commands, int x, int y, int radius,
                                    float r, float g, float b) {
        appendLine(commands, x - radius, y, x + radius, y, r, g, b);
        appendLine(commands, x, y - radius, x, y + radius, r, g, b);
    }

    private static void appendDiamond(List<GLCommand> commands, int x, int y, int radius,
                                      float r, float g, float b) {
        appendLine(commands, x, y - radius, x + radius, y, r, g, b);
        appendLine(commands, x + radius, y, x, y + radius, r, g, b);
        appendLine(commands, x, y + radius, x - radius, y, r, g, b);
        appendLine(commands, x - radius, y, x, y - radius, r, g, b);
    }

    private static void appendRectOutline(List<GLCommand> commands,
                                          int left,
                                          int top,
                                          int right,
                                          int bottom,
                                          float r,
                                          float g,
                                          float b) {
        appendLine(commands, left, top, right, top, r, g, b);
        appendLine(commands, right, top, right, bottom, r, g, b);
        appendLine(commands, right, bottom, left, bottom, r, g, b);
        appendLine(commands, left, bottom, left, top, r, g, b);
    }

    private static void appendLine(List<GLCommand> commands,
                                   int x1,
                                   int y1,
                                   int x2,
                                   int y2,
                                   float r,
                                   float g,
                                   float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I,
                -1, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA, r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I,
                -1, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA, r, g, b, x2, y2, 0, 0));
    }
}
