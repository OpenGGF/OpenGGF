package com.openggf.editor.hud;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.hud.EditorHudCanvas.Size;
import com.openggf.editor.hud.EditorHudLayout.Rect;
import com.openggf.editor.route.RouteTapeOverlay;
import com.openggf.editor.route.RouteTapeTimeline;
import com.openggf.editor.route.RouteTapeWorkspace;
import com.openggf.editor.route.RouteTapeWorkspace.Action;
import com.openggf.i18n.UiMessages;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.openggf.editor.hud.EditorHudPad.Button.*;
import static com.openggf.editor.hud.EditorHudTheme.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Controller-first route tape front end: the editor's level with the replayed path, a route panel
 * (qualification, launch, native observations) and a two-lane timeline of physical button rows.
 * Every edit goes through the shared {@link RouteTapeWorkspace}.
 */
final class RouteHud implements RouteTapeOverlay.Painter {
    /** Timeline dock: header, ruler and eight tracks per pad. */
    static final int DOCK = 166;
    private static final int TRACK = 8;
    private static final int LANE_GAP = 4;
    private static final int LABEL_W = 34;
    private static final float ROW_UNITS = 4;
    private static final int DIRECTION = ACCENT, ACTION = GOLD, START = 0xC9D3F2;

    private record Hit(Rect rect, Runnable action) { }

    private final RouteTapeWorkspace workspace;
    private final LevelEditorController level;
    private final java.util.function.Supplier<int[]> camera;
    private final EditorHudPad pad = new EditorHudPad();
    final EditorHudReader reader = new EditorHudReader();
    private EditorHudCanvas pathCanvas;
    private List<Hit> hits = List.of();
    private Rect grid;
    private double pointerScale = Double.NaN;
    private boolean controllerPrompts;
    private int ticks;

    RouteHud(RouteTapeWorkspace workspace, LevelEditorController level) {
        this(workspace, level, RouteHud::editorCamera);
    }

    /** @param camera the editor camera's top-left world position, or null when no level is shown */
    RouteHud(RouteTapeWorkspace workspace, LevelEditorController level, java.util.function.Supplier<int[]> camera) {
        this.workspace = workspace;
        this.level = level;
        this.camera = camera;
    }

    private static int[] editorCamera() {
        var editor = com.openggf.game.session.SessionManager.getCurrentEditorMode();
        if (editor == null || editor.getCamera() == null) return null;
        return new int[] {Short.toUnsignedInt(editor.getCamera().getXCopy()), editor.getCamera().getYCopy()};
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, int rgb, float width) {
        pathCanvas.line(x1, y1, x2, y2, rgb, width);
    }

    static EditorHudLayout layout(int width, int height, int logicalWidth, int logicalHeight) {
        return EditorHudLayout.of(width, height, logicalWidth, logicalHeight, true, DOCK);
    }

    void setPointerScale(double scale) {
        pointerScale = scale;
    }

    void reactivated() {
        pad.resync();
        hits = List.of();
    }

    // ------------------------------------------------------------------ input

    /** Always owns the tick, including the tick that returns to level editing. */
    boolean update(InputHandler input) {
        ticks++;
        pad.sample(input.capturePhysicalInput().gamepads());
        controllerPrompts = MenuInput.controller(input);
        if (reader.isOpen()) {
            reader.update(input, pad);
            return true;
        }
        if (input.isKeyPressed(GLFW_KEY_F2) || pad.pressed(R3)) {
            reader.open(workspace.detailsText());
            pad.consumeHeld();
            return true;
        }
        if (input.isKeyPressed(GLFW_KEY_ESCAPE) || pad.pressed(EAST)) {
            if (workspace.mark() >= 0) workspace.invoke(Action.MARK);
            else workspace.invoke(Action.EXIT);
            return true;
        }
        boolean control = input.isKeyDown(GLFW_KEY_LEFT_CONTROL) || input.isKeyDown(GLFW_KEY_RIGHT_CONTROL)
                || input.isKeyDown(GLFW_KEY_LEFT_SUPER) || input.isKeyDown(GLFW_KEY_RIGHT_SUPER);
        if (control) {
            if (input.isKeyPressed(GLFW_KEY_S)) workspace.invoke(Action.SAVE);
            else if (input.isKeyPressed(GLFW_KEY_Z)) workspace.invoke(Action.UNDO);
            else if (input.isKeyPressed(GLFW_KEY_Y)) workspace.invoke(Action.REDO);
            else if (input.isKeyPressed(GLFW_KEY_O)) workspace.invoke(Action.OPEN);
            return true;
        }
        if (input.isKeyPressed(GLFW_KEY_F1) || pad.pressed(MENU)) {
            workspace.invoke(Action.MENU);
            return true;
        }
        if (!workspace.hasRoute()) {
            if (input.isKeyPressed(GLFW_KEY_ENTER) || pad.pressed(SOUTH)) workspace.invoke(Action.OPEN);
            pointer(input);
            return true;
        }
        if ((input.isKeyPressed(GLFW_KEY_R) || pad.pressed(VIEW)) && workspace.sceneRows() == null) {
            workspace.invoke(workspace.jobRunning() ? Action.CANCEL_JOB : Action.REPLAY);
        }
        if (input.isKeyPressed(GLFW_KEY_SPACE) || input.isKeyPressed(GLFW_KEY_ENTER) || pad.pressed(SOUTH)) {
            workspace.invoke(Action.TOGGLE);
        }
        if (input.isKeyPressed(GLFW_KEY_M) || pad.pressed(WEST)) workspace.invoke(Action.MARK);
        if (input.isKeyPressed(GLFW_KEY_N)) workspace.invoke(Action.NEXT_FACT);
        if (input.isKeyPressed(GLFW_KEY_G)) workspace.invoke(Action.GO_TO_ROW);
        if (input.isKeyPressed(GLFW_KEY_INSERT)) workspace.invoke(Action.INSERT_ROWS);
        if (input.isKeyPressed(GLFW_KEY_DELETE)) workspace.invoke(Action.DELETE_ROWS);
        if (pad.pressed(NORTH)) workspace.invoke(Action.ROWS);
        boolean shift = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
        int horizontal = pad.navX();
        if (input.isKeyPressed(GLFW_KEY_LEFT)) horizontal--;
        if (input.isKeyPressed(GLFW_KEY_RIGHT)) horizontal++;
        if (horizontal != 0) workspace.moveCursor(horizontal * (shift ? 60 : 1));
        int page = 0;
        if (pad.repeated(LB) || input.isKeyPressed(GLFW_KEY_PAGE_UP)) page--;
        if (pad.repeated(RB) || input.isKeyPressed(GLFW_KEY_PAGE_DOWN)) page++;
        if (page != 0) workspace.moveCursor(page * workspace.visibleRows());
        if (input.isKeyPressed(GLFW_KEY_HOME)) workspace.setCursor(0);
        if (input.isKeyPressed(GLFW_KEY_END)) workspace.setCursor(workspace.tape().size() - 1);
        int vertical = pad.navY();
        if (input.isKeyPressed(GLFW_KEY_UP)) vertical--;
        if (input.isKeyPressed(GLFW_KEY_DOWN)) vertical++;
        if (vertical != 0) workspace.moveTrack(vertical);
        pointer(input);
        return true;
    }

    private void pointer(InputHandler input) {
        if (Double.isNaN(pointerScale) || !input.hasMouseInputSeen() || !input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT)) return;
        double x = input.getMouseX() * pointerScale, y = input.getMouseY() * pointerScale;
        for (Hit hit : hits) {
            if (hit.rect().contains(x, y)) {
                hit.action().run();
                return;
            }
        }
        if (grid != null && grid.contains(x, y) && workspace.hasRoute()) {
            var view = workspace.timeline();
            workspace.setCursor(view.rowAt((float) x, grid.x(), grid.w()));
            int track = trackAt((float) y);
            if (track >= 0) workspace.setTrack(track);
        }
    }

    private int trackAt(float y) {
        for (int t = 0; t < RouteTapeTimeline.TRACKS.size(); t++) {
            float top = trackY(t);
            if (y >= top && y < top + TRACK) return t;
        }
        return -1;
    }

    // ------------------------------------------------------------------ drawing

    void draw(EditorHudCanvas c, EditorHudLayout layout) {
        var nextHits = new ArrayList<Hit>();
        Rect game = layout.game();
        if (workspace.hasRoute() && workspace.sceneRows() == null && !workspace.levelMatches()) {
            c.rect(game.x(), game.y(), game.w(), game.h(), BACKDROP, 1);
            String empty = UiMessages.text("editor.route_tape.preview_none");
            c.text(empty, game.x() + (game.w() - EditorHudCanvas.textWidth(empty, Size.SMALL)) / 2,
                    game.y() + game.h() / 2, Size.SMALL, TEXT_DIM, 1);
        } else paintPath(c, layout, game);
        c.rect(0, 0, layout.width(), game.y(), BACKDROP, 1);
        c.rect(0, game.bottom(), layout.width(), layout.height() - game.bottom(), BACKDROP, 1);
        c.rect(0, game.y(), game.x(), game.h(), BACKDROP, 1);
        c.rect(game.right(), game.y(), layout.width() - game.right(), game.h(), BACKDROP, 1);
        c.frame(game.x() - 2, game.y() - 2, game.w() + 4, game.h() + 4, 1, ACCENT, 1);
        topBar(c, layout, nextHits);
        Rect panel = layout.workbench();
        if (panel != null && panel.w() > 20) drawPanel(c, panel, nextHits);
        drawTimeline(c, layout.dock());
        float readerRoom = EditorHudPainter.readerHintWidth(controllerPrompts, "F2");
        EditorHudPainter.promptBar(c, layout.promptBar(), prompts(), controllerPrompts, pad, readerRoom, ticks);
        EditorHudPainter.readerHint(c, layout.promptBar(), controllerPrompts, "F2");
        if (reader.isOpen()) reader.draw(c, layout);
        hits = List.copyOf(nextHits);
    }

    private void paintPath(EditorHudCanvas c, EditorHudLayout layout, Rect game) {
        int[] position = camera.get();
        // A shared route form draws inside the level view; the path stays off it.
        if (position == null || workspace.overlay() == null || workspace.menu().isOpen()) return;
        float unit = layout.gameViewport()[4] / (float) layout.uiScale() * level.canvasView().zoom();
        var view = new RouteTapeOverlay.Camera(game.x(), game.y(), position[0], position[1], unit);
        pathCanvas = c;
        c.clip(game);
        try {
            workspace.paintOverlay(this, view);
        } finally {
            c.unclip();
            pathCanvas = null;
        }
    }

    private void topBar(EditorHudCanvas c, EditorHudLayout layout, List<Hit> nextHits) {
        c.rect(0, 0, layout.width(), layout.topBar().h(), PANEL, 1);
        String title = UiMessages.text("editor.route_tape.hud_title");
        c.text(title, 8, 6, Size.BODY, GOLD, 1);
        String exit = Action.EXIT.label();
        float exitWidth = EditorHudCanvas.textWidth(exit, Size.SMALL) + 16 + EditorHudPainter.keycapWidth("Esc");
        String mode = mode();
        float chipWidth = EditorHudCanvas.textWidth(mode, Size.SMALL) + 20;
        float chipX = layout.width() - 6 - exitWidth - 6 - chipWidth;
        int colour = workspace.jobRunning() ? ACCENT : workspace.mark() >= 0 ? GOLD : TEXT_DIM;
        c.pill(chipX, 4.5f, chipWidth, 13, PANEL_RAISED, 1);
        c.rect(chipX + 6, 9, 4, 4, colour, 1);
        c.text(mode, chipX + 14, 7.5f, Size.SMALL, colour == TEXT_DIM ? TEXT : colour, 1);
        float titleX = 16 + EditorHudCanvas.textWidth(title, Size.BODY);
        String name = workspace.manifestPath() == null ? UiMessages.text("editor.route_tape.qualification_empty")
                : workspace.manifestPath().getFileName().toString();
        c.text(StageplayHud.documentTitle(name, workspace.dirty(), chipX - titleX - 10), titleX, 7, Size.SMALL, TEXT, 1);
        button(c, nextHits, new Rect(layout.width() - 6 - exitWidth, 3, exitWidth, 16), exit,
                controllerPrompts ? null : "Esc", controllerPrompts ? EditorHud.Glyph.EAST : null,
                () -> workspace.invoke(Action.EXIT), false);
    }

    String mode() {
        if (workspace.jobRunning()) return UiMessages.text("editor.route_tape.hud_mode_job");
        if (workspace.mark() >= 0) return UiMessages.text("editor.route_tape.hud_mode_selecting");
        return UiMessages.text("editor.route_tape.hud_mode_editing");
    }

    private int qualificationColour() {
        return switch (workspace.qualification()) {
            case QUALIFIED -> GOOD;
            case PASSES_UNSAVED -> ACCENT;
            case NOT_QUALIFIED, UNSUPPORTED -> WARN;
            case EDITED, DRAFT -> GOLD;
            case SCENE -> workspace.dirty() ? GOLD : ACCENT;
            case EMPTY, UNCHECKED -> TEXT_DIM;
        };
    }

    private void drawPanel(EditorHudCanvas c, Rect panel, List<Hit> nextHits) {
        c.panel(panel.x(), panel.y(), panel.w(), panel.h(), PANEL, 1);
        float x = panel.x() + 8, y = panel.y() + 8, w = panel.w() - 16;
        int pitch = EditorHudCanvas.lineHeight(Size.SMALL) + 3;
        int colour = qualificationColour();
        c.rect(x, y + 2, 4, 4, colour, 1);
        float badgeWidth = EditorHudCanvas.textWidth("ENGINE", Size.SMALL) + 8;
        c.pill(x + 8, y - 1, badgeWidth, 10, PANEL_RAISED, 1);
        c.text("ENGINE", x + 12, y, Size.SMALL, ACCENT, 1);
        c.fittedText(workspace.qualificationText(), x + 14 + badgeWidth, y, w - 14 - badgeWidth, Size.SMALL, colour, 1);
        y += pitch + 2;
        var job = workspace.job();
        if (job != null && job.running()) {
            c.fittedText(job.message(), x, y, w, Size.SMALL, TEXT_DIM, 1);
            c.rect(x, y + pitch - 2, w, 2, LINE, 1);
            c.rect(x, y + pitch - 2, w * job.fraction(), 2, ACCENT, 1);
            y += pitch + 2;
        }
        // Launch facts on the left; native observations and the path legend on the right.
        float half = (w - 8) / 2, bottom = panel.bottom() - 61;
        float gy = facts(c, x, y, half, workspace.launchFacts(), TEXT, pitch) + 3;
        for (var goal : workspace.goalRows()) {
            if (gy + pitch > bottom) break;
            float chip = EditorHudCanvas.textWidth(goal.player(), Size.SMALL) + 8;
            c.pill(x, gy - 1, chip, 10, PANEL_RAISED, 1);
            c.text(goal.player(), x + 4, gy, Size.SMALL, com.openggf.editor.route.RouteEditorStyle.playerColour(goal.player()), 1);
            c.fittedText(goal.predicate(), x + chip + 4, gy, half - chip - 4, Size.SMALL, TEXT, 1);
            gy += pitch;
        }
        float rx = x + half + 8, ry = y;
        c.text(workspace.evidenceSummary(), rx, ry, Size.SMALL, TEXT_DIM, 1);
        ry += pitch;
        if (!workspace.exportBlocker().isBlank()) {
            c.fittedText(workspace.exportSummary(), rx, ry, half, Size.SMALL, WARN, 1);
            ry += pitch;
        }
        var goals = workspace.goalFacts();
        String repair = workspace.repairText();
        if (!repair.isEmpty() && ry + pitch <= bottom) {
            c.fittedText(repair, rx, ry, half, Size.SMALL, workspace.repairCurrent() ? GOOD : GOLD, 1);
            ry += pitch;
        }
        if (ry + pitch <= bottom) {
            c.fittedText(goals.size() == 1 ? goals.getFirst()[0] + "  " + goals.getFirst()[1]
                    : UiMessages.text("editor.route_tape.hud_goal_legs", Map.of("legs", String.valueOf(goals.size()))),
                    rx, ry, half, Size.SMALL, TEXT_DIM, 1);
            ry += pitch;
        }
        ry += 2;
        if (workspace.overlay() != null && ry + pitch <= bottom) {
            c.rect(rx, ry + 2, 6, 3, RouteTapeOverlay.GROUND, 1);
            c.text(UiMessages.text("editor.route_tape.hud_ground"), rx + 9, ry, Size.SMALL, TEXT_DIM, 1);
            c.rect(rx + 56, ry + 2, 6, 3, RouteTapeOverlay.AIR, 1);
            c.text(UiMessages.text("editor.route_tape.hud_air"), rx + 65, ry, Size.SMALL, TEXT_DIM, 1);
            c.rect(rx + 104, ry + 2, 6, 3, RouteTapeOverlay.SIDEKICK, 1);
            c.text(UiMessages.text("editor.route_tape.hud_sidekick"), rx + 113, ry, Size.SMALL, TEXT_DIM, 1);
            ry += pitch;
            String note = workspace.pathStale() ? UiMessages.text("editor.route_tape.hud_path_stale")
                    : !workspace.levelMatches() ? UiMessages.text("editor.route_tape.hud_path_other_level") : null;
            if (note != null && ry + pitch <= bottom) {
                c.fittedText(note, rx, ry, half, Size.SMALL, workspace.pathStale() ? GOLD : TEXT_FAINT, 1);
            }
        }
        float buttonWidth = (w - 6) / 2;
        Action[] actions;
        String[] keys;
        EditorHud.Glyph[] glyphs;
        if (workspace.sceneRows() != null) {
            actions = new Action[] {Action.APPLY_ROWS, Action.EXIT};
            keys = new String[] {null, "Esc"};
            glyphs = new EditorHud.Glyph[] {null, EditorHud.Glyph.EAST};
        } else if (workspace.hasRoute()) {
            actions = new Action[] {workspace.jobRunning() ? Action.CANCEL_JOB : Action.REPLAY, Action.SAVE,
                    Action.EXPORT_BK2, Action.OPEN};
            keys = new String[] {"R", "Ctrl S", null, "Ctrl O"};
            glyphs = new EditorHud.Glyph[] {EditorHud.Glyph.VIEW, null, null, null};
        } else {
            actions = new Action[] {Action.OPEN};
            keys = new String[] {"Enter"};
            glyphs = new EditorHud.Glyph[] {EditorHud.Glyph.SOUTH};
        }
        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            String key = keys[i];
            EditorHud.Glyph glyph = glyphs[i];
            button(c, nextHits, new Rect(x + (i % 2) * (buttonWidth + 6), bottom + (i / 2) * 21, buttonWidth, 18),
                    action.label(), controllerPrompts ? null : key, controllerPrompts ? glyph : null,
                    () -> workspace.invoke(action), false);
        }
        c.fittedText(workspace.status(), x, panel.bottom() - 15, w, Size.SMALL, TEXT_DIM, 1);
    }

    /** Facts as a label column: values start after the widest label. */
    private static float facts(EditorHudCanvas c, float x, float y, float w, List<String[]> facts, int colour, int pitch) {
        float column = 0;
        for (String[] fact : facts) column = Math.max(column, EditorHudCanvas.textWidth(fact[0], Size.SMALL));
        column += 8;
        for (String[] fact : facts) {
            c.text(fact[0], x, y, Size.SMALL, TEXT_FAINT, 1);
            c.fittedText(fact[1], x + column, y, w - column, Size.SMALL, colour, 1);
            y += pitch;
        }
        return y;
    }

    private float trackY(int track) {
        return grid.y() + track * TRACK + (track >= RouteTapeTimeline.TRACKS_PER_LANE ? LANE_GAP : 0);
    }

    private void drawTimeline(EditorHudCanvas c, Rect dock) {
        c.rect(dock.x(), dock.y(), dock.w(), dock.h(), PANEL, 1);
        float left = dock.x() + 8 + LABEL_W;
        float width = dock.w() - 16 - LABEL_W;
        float header = dock.y() + 4;
        float ruler = dock.y() + 18;
        grid = new Rect(left, dock.y() + 30, width, 2 * RouteTapeTimeline.TRACKS_PER_LANE * TRACK + LANE_GAP);
        workspace.setVisibleRows(Math.max(8, (int) (width / ROW_UNITS)));
        if (!workspace.hasRoute()) {
            c.fittedText(UiMessages.text("editor.route_tape.status_open_a_route"), left, header + 30, width, Size.SMALL, TEXT_DIM, 1);
            return;
        }
        var view = workspace.timeline();
        var track = RouteTapeTimeline.TRACKS.get(view.track());
        boolean held = workspace.tape().size() > 0 && workspace.tape().row(view.cursor()).held(track.slot(), track.button());
        // Rows are numbered from 0 everywhere (replay, divergence, Go to row); "of" names the row count.
        String head = UiMessages.text("editor.route_tape.hud_row", Map.of("row", String.valueOf(view.cursor()),
                "rows", String.valueOf(view.tapeRows()), "pad", track.slot().name(), "button", buttonName(track.button()),
                "state", UiMessages.text(held ? "editor.route_tape.hud_held" : "editor.route_tape.hud_released")));
        c.fittedText(head, dock.x() + 8, header, width * .6f, Size.SMALL, TEXT, 1);
        if (workspace.mark() >= 0) {
            String selection = UiMessages.text("editor.route_tape.hud_selection", Map.of(
                    "rows", String.valueOf(workspace.selectionTo() - workspace.selectionFrom())));
            c.text(selection, dock.right() - 8 - EditorHudCanvas.textWidth(selection, Size.SMALL), header, Size.SMALL, GOLD, 1);
        }
        float rowWidth = view.rowWidth(width);
        c.rect(left, ruler + 8, width, 1, LINE, 1);
        for (int tick : view.ticks()) {
            float tx = view.x(tick, left, width);
            c.rect(tx, ruler + 5, 1, 4, TEXT_FAINT, 1);
            c.text(String.valueOf(tick), tx + 2, ruler - 2, Size.SMALL, TEXT_FAINT, 1);
        }
        for (var lane : PlayerSlot.values()) {
            int firstTrack = lane.ordinal() * RouteTapeTimeline.TRACKS_PER_LANE;
            c.rect(left, trackY(firstTrack), width, RouteTapeTimeline.TRACKS_PER_LANE * TRACK, TILE, 1);
            c.text(lane.name(), dock.x() + 8, trackY(firstTrack) + 1, Size.SMALL, com.openggf.editor.route.RouteEditorStyle.playerColour(lane.name()), 1);
        }
        for (int t = 0; t < RouteTapeTimeline.TRACKS.size(); t++) {
            var label = RouteTapeTimeline.TRACKS.get(t);
            boolean focused = t == view.track();
            c.text(label.label(), left - 9, trackY(t), Size.SMALL, focused ? GOLD : TEXT_FAINT, 1);
            if (focused) c.rect(left, trackY(t), width, TRACK, PANEL_HIGHLIGHT, 1);
        }
        if (view.selectionTo() > view.selectionFrom()) {
            float sx = view.x(Math.max(view.first(), view.selectionFrom()), left, width);
            float ex = view.x(Math.min(view.first() + view.rows(), view.selectionTo()), left, width);
            c.rect(sx, grid.y(), Math.max(1, ex - sx), grid.h(), ACCENT_DARK, .55f);
        }
        int locked = workspace.immutablePrefixRows();
        if (locked > view.first()) {
            float end = view.x(Math.min(locked, view.first() + view.rows()), left, width);
            c.rect(left, grid.y(), end - left, grid.h(), 0x030611, 1);
        }
        for (var run : view.runs()) {
            var lane = RouteTapeTimeline.TRACKS.get(run.track());
            int colour = lane.button() == PhysicalButton.START ? START
                    : lane.button().ordinal() >= PhysicalButton.A.ordinal() ? ACTION : DIRECTION;
            int split = Math.max(run.from(), Math.min(run.to(), locked));
            if (split > run.from()) c.rect(view.x(run.from(), left, width), trackY(run.track()) + 1,
                    Math.max(1, (split - run.from()) * rowWidth - .5f), TRACK - 2,
                    com.openggf.editor.route.RouteEditorStyle.lockedColour(colour), 1);
            if (run.to() > split) c.rect(view.x(split, left, width), trackY(run.track()) + 1,
                    Math.max(1, (run.to() - split) * rowWidth - .5f), TRACK - 2, colour, 1);
        }
        if (locked > 0) {
            c.menuIcon(com.openggf.game.MenuChrome.Icon.LOCK, dock.right() - 100, header, TEXT_DIM);
            c.text(UiMessages.text("editor.route_tape.locked_prefix"), dock.right() - 89, header, Size.SMALL, TEXT_DIM, 1);
            if (locked >= view.first() && locked < view.first() + view.rows()) {
                float edge = view.x(locked, left, width);
                c.rect(edge, ruler, 1, grid.bottom() - ruler, TEXT, 1);
                c.menuIcon(com.openggf.game.MenuChrome.Icon.LOCK, edge - 3, ruler - 5, TEXT);
            }
        }
        for (var marker : view.markers()) {
            float mx = view.x(marker.from(), left, width);
            switch (marker.kind()) {
                case DIVERGED -> c.rect(mx, ruler, Math.max(1, rowWidth), grid.bottom() - ruler, WARN, 1);
                case NATIVE_LAG, NATIVE_DIVERGED -> c.rect(mx, ruler + 2, Math.max(1, rowWidth), 6, GOLD, 1);
                case SEARCHED -> c.rect(mx, ruler + 9, (marker.to() - marker.from()) * rowWidth, 2, ACCENT, 1);
                case KEPT -> c.rect(mx, ruler + 9, (marker.to() - marker.from()) * rowWidth, 2, GOOD, 1);
                case REFERENCE -> c.rect(mx, ruler + 9, (marker.to() - marker.from()) * rowWidth, 2, TEXT_DIM, 1);
            }
        }
        if (view.tapeRows() < view.first() + view.rows()) {
            float end = view.x(view.tapeRows(), left, width);
            c.rect(end, grid.y(), left + width - end, grid.h(), BACKDROP, .7f);
            c.rect(end, ruler + 2, 1, grid.bottom() - ruler - 2, TEXT_FAINT, 1);
        }
        float cx = view.x(view.cursor(), left, width);
        c.rect(cx, ruler + 4, Math.max(1, rowWidth), grid.bottom() - ruler - 4, GOLD, .35f);
        c.frame(cx - 1, trackY(view.track()) - 1, Math.max(1, rowWidth) + 2, TRACK + 2, 1, GOLD, 1);
    }

    static String buttonName(PhysicalButton button) {
        return switch (button) {
            case UP -> UiMessages.text("editor.route_tape.button_up");
            case DOWN -> UiMessages.text("editor.route_tape.button_down");
            case LEFT -> UiMessages.text("editor.route_tape.button_left");
            case RIGHT -> UiMessages.text("editor.route_tape.button_right");
            case START -> UiMessages.text("editor.route_tape.button_start");
            case A -> "A";
            case B -> "B";
            case C -> "C";
        };
    }

    /** The prompts for the current state; a pad shows only what its buttons do. */
    List<EditorHud.Prompt> prompts() {
        var prompts = new ArrayList<EditorHud.Prompt>();
        if (!workspace.hasRoute()) {
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.SOUTH, "Enter", Action.OPEN.label()));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.MENU, "F1", Action.MENU.label()));
            return prompts;
        }
        if (controllerPrompts) {
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.DPAD, "", UiMessages.text("editor.route_tape.hud_row_button")));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.SOUTH, "", Action.TOGGLE.label()));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.WEST, "", Action.MARK.label()));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.NORTH, "", Action.ROWS.label()));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.MENU, "", Action.MENU.label()));
            return prompts;
        }
        prompts.add(new EditorHud.Prompt(null, "Left / Right", UiMessages.text("editor.route_tape.hud_row_word")));
        prompts.add(new EditorHud.Prompt(null, "Up / Down", UiMessages.text("editor.route_tape.hud_button_word")));
        prompts.add(new EditorHud.Prompt(null, "Space", Action.TOGGLE.label()));
        prompts.add(new EditorHud.Prompt(null, "M", Action.MARK.label()));
        prompts.add(new EditorHud.Prompt(null, "F1", Action.MENU.label()));
        return prompts;
    }

    private void button(EditorHudCanvas c, List<Hit> nextHits, Rect rect, String label, String key, EditorHud.Glyph glyph,
                        Runnable action, boolean selected) {
        boolean disabled = label.equals(Action.EXPORT_BK2.label()) && !workspace.exportBlocker().isBlank();
        c.panel(rect.x(), rect.y(), rect.w(), rect.h(), disabled ? TILE : selected ? ACCENT_DARK : PANEL_RAISED, 1);
        float centre = rect.y() + rect.h() / 2f;
        float shortcut = 0;
        if (key != null) {
            shortcut = EditorHudPainter.keycapWidth(key) + 4;
            EditorHudPainter.keycap(c, key, rect.right() - shortcut, centre);
        } else if (glyph != null) {
            shortcut = EditorHudPainter.glyphWidthOf(glyph) + 4;
            EditorHudPainter.glyph(c, glyph, rect.right() - shortcut, centre, pad);
        }
        c.text(com.openggf.game.MenuStyle.pageText(label, Math.max(1, (int) (rect.w() - 10 - shortcut)), Size.SMALL.scale, ticks),
                rect.x() + 5, rect.y() + Math.max(0, (rect.h() - EditorHudCanvas.lineHeight(Size.SMALL)) / 2), Size.SMALL,
                disabled ? TEXT_FAINT : selected ? GOLD : TEXT, 1);
        if (!disabled) nextHits.add(new Hit(rect, action));
    }
}
