package com.openggf.editor.hud;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.editor.hud.EditorHudCanvas.Size;
import com.openggf.editor.hud.EditorHudLayout.Rect;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayDialogue;
import com.openggf.editor.stageplay.StageplayWorkspace;
import com.openggf.editor.stageplay.StageplayWorkspace.Action;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.openggf.editor.hud.EditorHudPad.Button.*;
import static com.openggf.editor.hud.EditorHudTheme.*;
import static org.lwjgl.glfw.GLFW.*;

/** Controller and pointer authoring front end; all document edits use the shared workspace. */
final class StageplayHud implements com.openggf.stageplay.editor.StageplayRouteOverlay.Painter {
    private EditorHudCanvas routeCanvas;

    @Override public void line(float x1, float y1, float x2, float y2, int rgb, float width) {
        routeCanvas.line(x1, y1, x2, y2, rgb, width);
    }
    enum Page { CAST, TIMELINE, COMMANDS }
    record Row(String label, String detail, int frame, Runnable action) { }
    private record Hit(Rect rect, Runnable action) { }

    private final StageplayWorkspace workspace;
    private final EditorHudPad pad = new EditorHudPad();
    private Page page = Page.CAST;
    private int selected;
    private int visibleRows = 6;
    private List<Hit> hits = List.of();
    private Rect scrubber;
    private Rect stopButton;
    private double pointerScale = Double.NaN;
    private boolean dragging;
    private boolean controllerPrompts;
    private StageplayDocument rowDocument;
    private Page rowPage;
    private List<Row> cachedRows = List.of();

    StageplayHud(StageplayWorkspace workspace) { this.workspace = workspace; }

    /** Large authored frames still leave controls visible; ordinary pixel scenes keep integer scaling. */
    static EditorHudLayout layout(int width, int height, int logicalWidth, int logicalHeight) {
        int uiScale = EditorHudLayout.uiScale(width, height);
        int availableWidth = Math.max(1, width - (EditorHudLayout.MIN_WORKBENCH + 3 * EditorHudLayout.GAP) * uiScale);
        int availableHeight = Math.max(1, height - (EditorHudLayout.TOP_BAR + EditorHudLayout.PROMPT_BAR
                + EditorHudLayout.DOCK + 3 * EditorHudLayout.GAP) * uiScale);
        double fit = Math.min(1, Math.min(availableWidth / (double) Math.max(1, logicalWidth),
                availableHeight / (double) Math.max(1, logicalHeight)));
        return EditorHudLayout.of(width, height, Math.max(1, (int) (logicalWidth * fit)),
                Math.max(1, (int) (logicalHeight * fit)), true);
    }

    void setPointerScale(double scale) { pointerScale = scale; }

    void reactivated() { pad.resync(); dragging = false; hits = List.of(); }

    void finishPendingEdits() { dragging = false; }

    /** Always owns the tick, including the tick that returns to level editing. */
    boolean update(InputHandler input) {
        workspace.setCameraInputEnabled(page != Page.COMMANDS);
        pad.sample(input.capturePhysicalInput().gamepads());
        controllerPrompts = MenuInput.controller(input);
        if (workspace.recording()) {
            if (input.isKeyPressed(GLFW_KEY_SPACE) || input.isKeyPressed(GLFW_KEY_ESCAPE)
                    || pad.pressed(EAST) || pad.pressed(SOUTH)) workspace.stop();
            else if (stopButton != null && !Double.isNaN(pointerScale) && input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT)
                    && stopButton.contains(input.getMouseX() * pointerScale, input.getMouseY() * pointerScale)) workspace.invoke(Action.STOP);
            return true;
        }
        if (input.isKeyPressed(GLFW_KEY_F4)) { workspace.deactivate(); return true; }
        if (input.isKeyPressed(GLFW_KEY_ESCAPE) || pad.pressed(EAST)) {
            if (workspace.cameraMode()) workspace.invoke(Action.CAMERA_MODE);
            else if (page == Page.COMMANDS) changePage(Page.CAST);
            else workspace.deactivate();
            return true;
        }
        boolean control = input.isKeyDown(GLFW_KEY_LEFT_CONTROL) || input.isKeyDown(GLFW_KEY_RIGHT_CONTROL)
                || input.isKeyDown(GLFW_KEY_LEFT_SUPER) || input.isKeyDown(GLFW_KEY_RIGHT_SUPER);
        if (control) {
            if (input.isKeyPressed(GLFW_KEY_S)) workspace.invoke(Action.SAVE);
            else if (input.isKeyPressed(GLFW_KEY_Z)) workspace.invoke(Action.UNDO);
            else if (input.isKeyPressed(GLFW_KEY_Y)) workspace.invoke(Action.REDO);
            return true;
        }
        if (input.isKeyPressed(GLFW_KEY_F1) || pad.pressed(MENU)) {
            changePage(page == Page.COMMANDS ? Page.CAST : Page.COMMANDS);
            return true;
        }
        if (input.isKeyPressed(GLFW_KEY_TAB) || pad.pressed(VIEW)) {
            changePage(page == Page.TIMELINE ? Page.CAST : Page.TIMELINE);
            return true;
        }
        if (input.isKeyPressed(GLFW_KEY_SPACE)) { workspace.invoke(Action.PLAY); return true; }
        if (input.isKeyPressed(GLFW_KEY_C)) { workspace.invoke(Action.CAMERA_MODE); return true; }
        if (input.isKeyPressed(GLFW_KEY_R)) { workspace.invoke(workspace.cameraMode() ? Action.RECORD_CAMERA : Action.RECORD); return true; }
        if (pad.pressed(WEST) || input.isKeyPressed(GLFW_KEY_K)) { workspace.invoke(workspace.cameraMode() ? Action.SET_CAMERA_KEY : Action.SET_KEY); return true; }
        if (pad.pressed(NORTH)) { workspace.invoke(Action.ADD_ACTOR); return true; }
        if (pad.pressed(LB)) cycleActor(-1);
        if (pad.pressed(RB)) cycleActor(1);
        int step = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT) ? 60 : 1;
        int horizontal = pad.navX();
        if (!workspace.cameraMode() && input.isKeyPressed(GLFW_KEY_LEFT)) horizontal--;
        if (!workspace.cameraMode() && input.isKeyPressed(GLFW_KEY_RIGHT)) horizontal++;
        if (horizontal != 0) workspace.seek(workspace.controller().frame() + horizontal * step);
        if (input.isKeyPressed(GLFW_KEY_HOME)) workspace.seek(0);
        if (input.isKeyPressed(GLFW_KEY_END)) workspace.seek(workspace.controller().document().durationFrames() - 1);
        List<Row> rows = rows();
        int vertical = pad.navY();
        if (!workspace.cameraMode() && input.isKeyPressed(GLFW_KEY_UP)) vertical--;
        if (!workspace.cameraMode() && input.isKeyPressed(GLFW_KEY_DOWN)) vertical++;
        if (!rows.isEmpty()) {
            selected = Math.clamp(selected + vertical, 0, rows.size() - 1);
            if (pad.pressed(SOUTH) || input.isKeyPressed(GLFW_KEY_ENTER)) {
                rows.get(selected).action().run();
                return true;
            }
        }
        if (!Double.isNaN(pointerScale) && input.hasMouseInputSeen()) {
            double x = input.getMouseX() * pointerScale, y = input.getMouseY() * pointerScale;
            boolean pressed = input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT);
            if (!input.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT)) dragging = false;
            if (pressed && scrubber != null && scrubber.contains(x, y)) dragging = true;
            if (dragging && scrubber != null) {
                workspace.seek(frameAt(x, scrubber, workspace.controller().document().durationFrames()));
            } else if (pressed) {
                for (Hit hit : hits) if (hit.rect().contains(x, y)) {
                    hit.action().run();
                    break;
                }
            }
        }
        return true;
    }

    static int frameAt(double x, Rect track, int duration) {
        return (int) Math.round(Math.clamp((x - track.x()) / Math.max(1, track.w()), 0, 1)
                * Math.max(0, duration - 1));
    }

    private void cycleActor(int direction) {
        var ids = new ArrayList<String>();
        workspace.controller().document().actors().forEach(actor -> ids.add(actor.id()));
        workspace.controller().document().objectActors().forEach(actor -> ids.add(actor.id()));
        if (ids.isEmpty()) return;
        int index = ids.indexOf(workspace.controller().selectedActorId());
        workspace.selectActor(ids.get(Math.floorMod(index + direction, ids.size())));
    }

    private void changePage(Page value) { page = value; selected = 0; workspace.setCameraInputEnabled(value != Page.COMMANDS); }

    List<Row> rows() {
        var document = workspace.controller().document();
        if (document == rowDocument && page == rowPage) return cachedRows;
        var rows = new ArrayList<Row>();
        switch (page) {
            case COMMANDS -> {
                for (Action action : Action.values()) rows.add(new Row(action.label(), "", -1, () -> {
                    changePage(Page.CAST);
                    workspace.invoke(action);
                }));
            }
            case CAST -> {
                for (var actor : document.actors()) {
                    boolean physical = document.puppets().stream().anyMatch(p -> p.actorId().equals(actor.id()));
                    rows.add(new Row(actor.name(), actor.gameId().toUpperCase(java.util.Locale.ROOT) + " / " + actor.character()
                            + (physical ? " / Puppet" : " / Poser"), -1, () -> workspace.selectActor(actor.id())));
                }
                for (var actor : document.objectActors()) {
                    rows.add(new Row(actor.name(), actor.gameId().toUpperCase(java.util.Locale.ROOT) + " / Object", -1,
                            () -> workspace.selectActor(actor.id())));
                }
            }
            case TIMELINE -> {
                for (var actor : document.actors()) for (var key : actor.keys()) {
                    boolean physical = document.puppets().stream().anyMatch(p -> p.actorId().equals(actor.id()));
                    rows.add(new Row(actor.name(), physical ? "Placement / visibility" : key.pose() >= 0 ? "Pose " + key.pose() : "Animation " + key.animation(), key.frame(),
                            () -> { workspace.selectActor(actor.id()); workspace.seek(key.frame()); }));
                }
                for (var track : document.puppets()) for (var command : track.commands()) {
                    var actor = document.actors().stream().filter(a -> a.id().equals(track.actorId())).findFirst().orElseThrow();
                    String detail = command.kind() == StageplayDocument.PuppetCommandKind.MOVE_TO
                            ? "Goal " + command.x() + ", " + command.y() + " / " + Math.round(command.urgency() * 100) + "%"
                            : command.kind() == StageplayDocument.PuppetCommandKind.JUMP ? "Hold " + command.holdFrames() + " frames" : "Native braking";
                    rows.add(new Row(actor.name() + " / " + command.kind(), detail, command.frame(),
                            () -> { workspace.selectActor(actor.id()); workspace.seek(command.frame()); }));
                }
                for (var key : document.camera()) rows.add(new Row("Camera", key.interpolation().name(), key.frame(), () -> workspace.seek(key.frame())));
                for (var caption : document.captions()) rows.add(new Row("Caption", caption.text(), caption.startFrame(), () -> workspace.seek(caption.startFrame())));
                for (var cue : document.dialogue()) rows.add(new Row("Dialogue / "+StageplayDialogue.speakerName(document,cue),
                        cue.text().replace('\n',' '),cue.startFrame(),() -> {
                    if (!cue.speakerActor().isEmpty()) workspace.selectActor(cue.speakerActor());
                    workspace.seek(cue.startFrame());
                }));
                for (var cue : document.audio()) rows.add(new Row("Audio", cue.kind().name(), cue.frame(), () -> workspace.seek(cue.frame())));
                for (var effect : document.cameraEffects()) rows.add(new Row("Camera shake", effect.amplitudeX()+" / "+effect.amplitudeY()+" px, "+effect.frequency()+" Hz",
                        effect.startFrame(), () -> workspace.seek(effect.startFrame())));
                for (var change : document.sceneChanges()) rows.add(new Row("Scene change", change.transition()+" / "+change.set().gameId(),
                        change.frame(), () -> workspace.seek(change.frame())));
                rows.sort(Comparator.comparingInt(Row::frame));
            }
        }
        rowDocument = document;
        rowPage = page;
        cachedRows = List.copyOf(rows);
        return cachedRows;
    }

    void draw(EditorHudCanvas c, EditorHudLayout layout) {
        var nextHits = new ArrayList<Hit>();
        var controller = workspace.controller();
        var document = controller.document();
        Rect game = layout.game();
        if (workspace.routeOverlay() != null) {
            routeCanvas = c;
            c.clip(game);
            try { workspace.paintRouteOverlay(this, game.x(), game.y(), game.w(), game.h()); }
            finally { c.unclip(); routeCanvas = null; }
        }
        // Leave the engine's completed preview intact, filling only its surrounding chrome.
        c.rect(0, 0, layout.width(), game.y(), BACKDROP, 1);
        c.rect(0, game.bottom(), layout.width(), layout.height() - game.bottom(), BACKDROP, 1);
        c.rect(0, game.y(), game.x(), game.h(), BACKDROP, 1);
        c.rect(game.right(), game.y(), layout.width() - game.right(), game.h(), BACKDROP, 1);
        c.frame(game.x() - 2, game.y() - 2, game.w() + 4, game.h() + 4, 1, ACCENT, 1);
        c.rect(0, 0, layout.width(), layout.topBar().h(), PANEL, 1);
        c.text("STAGEPLAY", 8, 6, Size.BODY, GOLD, 1);
        c.fittedText(document.name() + (controller.dirty() ? " *" : ""), 108, 7,
                layout.width() - 246, Size.SMALL, TEXT, 1);
        button(c, nextHits, new Rect(layout.width() - 126, 3, 120, 16), "Level editor  F4", workspace::deactivate, false);

        Rect panel = layout.workbench();
        if (panel != null && panel.w() > 20) drawPanel(c, panel, nextHits);
        drawTimeline(c, layout.dock(), document, nextHits);
        c.rect(0, layout.promptBar().y(), layout.width(), layout.promptBar().h(), PANEL, 1);
        String prompt = workspace.recordingCamera() ? "CAMERA TAKE  /  WASD or arrows pan  /  Q E integer zoom  /  Space stop"
                : workspace.recordingPuppet() ? "PUPPET TAKE  /  A D move  /  W jump  /  release or S brake  /  Space end"
                : workspace.recording() ? "POSER TAKE  /  WASD move  /  Space or South stop"
                : workspace.cameraMode() ? "CAMERA  WASD/arrows pan  Q E integer zoom  K key  R record  C exit  F1 commands"
                : controllerPrompts ? "D-pad seek/select   A choose   X key   Y actor   View timeline   Start commands"
                : "Arrows seek/select  Tab timeline  K direct actor  R record  C camera  Space play  F1 commands";
        if (workspace.cameraMode() && workspace.directorCamera().zoom()!=Math.rint(workspace.directorCamera().zoom()))
            prompt = "FRACTIONAL ZOOM: Q/E choose integer framing; K capture. Imported zoom may shimmer while panning.";
        c.fittedText(prompt, 8, layout.promptBar().y() + 5, layout.width() - 16, Size.SMALL,
                workspace.recording() ? WARN : TEXT_DIM, 1);
        hits = List.copyOf(nextHits);
    }

    private List<StageplayDocument.Actor> documentActors() { return workspace.controller().document().actors(); }

    private void drawPanel(EditorHudCanvas c, Rect panel, List<Hit> nextHits) {
        c.panel(panel.x(), panel.y(), panel.w(), panel.h(), PANEL, 1);
        float gap = 4, width = (panel.w() - 20) / 3;
        Page[] pages = Page.values();
        String[] names = {"Cast", "Keys / cues", "Commands"};
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            button(c, nextHits, new Rect(panel.x() + 6 + i * (width + gap), panel.y() + 7, width, 18),
                    names[i], () -> changePage(target), page == target);
        }
        List<Row> rows = rows();
        var route = workspace.routeOverlay();
        var routeLines = route == null ? List.<String>of() : wrapRouteText(route.reason(), panel.w() - 28);
        float routeHeight = route == null ? 0 : 68 + routeLines.size() * 11;
        visibleRows = Math.max(1, (int) ((panel.h() - 110 - routeHeight) / 27));
        selected = rows.isEmpty() ? 0 : Math.clamp(selected, 0, rows.size() - 1);
        int first = Math.max(0, Math.min(selected - visibleRows / 2, rows.size() - visibleRows));
        if (rows.isEmpty()) {
            c.fittedText("Add an actor to start directing.", panel.x() + 9, panel.y() + 39, panel.w() - 18, Size.SMALL, TEXT_DIM, 1);
        }
        for (int i = first; i < Math.min(rows.size(), first + visibleRows); i++) {
            Row row = rows.get(i);
            int index = i;
            Rect area = new Rect(panel.x() + 6, panel.y() + 31 + (i - first) * 27, panel.w() - 12, 25);
            boolean chosen = i == selected;
            c.rect(area.x(), area.y(), area.w(), area.h(), chosen ? PANEL_HIGHLIGHT : PANEL_RAISED, 1);
            if (chosen) c.rect(area.x(), area.y(), 2, area.h(), GOLD, 1);
            String label = (row.frame() >= 0 ? row.frame() + "  " : "") + row.label();
            boolean selectedActor = page == Page.CAST && (i < documentActors().size() ? documentActors().get(i).id()
                    : workspace.controller().document().objectActors().get(i - documentActors().size()).id()).equals(workspace.controller().selectedActorId());
            if (selectedActor) label = "* " + label;
            c.fittedText(label, area.x() + 6, area.y() + 4, area.w() - 12, Size.SMALL, TEXT, 1);
            c.fittedText(selectedActor ? workspace.selectedActorStatus() : row.detail(), area.x() + 6, area.y() + 15, area.w() - 12, Size.SMALL, TEXT_DIM, 1);
            nextHits.add(new Hit(area, () -> { selected = index; row.action().run(); }));
        }
        float bottom = panel.bottom() - 67;
        if (route != null) {
            float x = panel.x() + 10, y = bottom - routeHeight - 10;
            c.rect(x - 2, y - 5, panel.w() - 16, 1, LINE, 1);
            c.text(route.title(), x, y, Size.SMALL, route.result().success() ? GOOD : WARN, 1);
            c.text("Cyan: walk", x, y + 14, Size.SMALL, com.openggf.stageplay.editor.StageplayRouteOverlay.GROUND, 1);
            c.text("Gold: air / takeoff", x + 85, y + 14, Size.SMALL, com.openggf.stageplay.editor.StageplayRouteOverlay.AIR, 1);
            c.text("Square: start   Diamond: goal", x, y + 26, Size.SMALL, TEXT_DIM, 1);
            c.text(route.applied() ? "White cross: actual position" : "Red cross: stopped attempt", x, y + 37, Size.SMALL, TEXT_DIM, 1);
            for (int i = 0; i < routeLines.size(); i++)
                c.text(routeLines.get(i), x, y + 52 + i * 11, Size.SMALL, TEXT, 1);
        }
        c.fittedText(rows.isEmpty() ? "" : (selected + 1) + " / " + rows.size() + "   Up / Down to browse",
                panel.x() + 8, bottom - 10, panel.w() - 16, Size.SMALL, TEXT_FAINT, 1);
        float half = (panel.w() - 18) / 2;
        Action[] actions = workspace.cameraMode()
                ? new Action[] {Action.CAMERA_MODE, Action.SET_CAMERA_KEY, Action.RECORD_CAMERA, Action.CAMERA_EFFECTS}
                : new Action[] {Action.ADD_ACTOR, Action.EDIT_ACTOR, Action.CAMERA_MODE, Action.SCENE_CHANGES};
        String[] labels = workspace.cameraMode() ? new String[] {"Leave camera C", "Capture key K", "Record camera R", "Shake / quake"}
                : new String[] {"Add actor", "Edit actor", "Direct camera C", "Scene changes"};
        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            button(c, nextHits, new Rect(panel.x() + 6 + (i % 2) * (half + 6), bottom + (i / 2) * 21, half, 18),
                    labels[i], () -> workspace.invoke(action), false);
        }
        c.fittedText(route == null ? workspace.status() : route.statusText(), panel.x() + 8,
                panel.bottom() - 16, panel.w() - 16, Size.SMALL, TEXT_DIM, 1);
    }

    /** Route explanations wrap at spaces instead of passing through the HUD's ellipsis fitter. */
    static List<String> wrapRouteText(String text, float width) {
        var lines = new ArrayList<String>();
        String line = "";
        for (String word : text.split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && EditorHudCanvas.textWidth(next, Size.SMALL) > width) {
                lines.add(line);
                line = word;
            } else line = next;
        }
        if (!line.isEmpty()) lines.add(line);
        return List.copyOf(lines);
    }

    private void drawTimeline(EditorHudCanvas c, Rect dock, StageplayDocument document, List<Hit> nextHits) {
        c.rect(dock.x(), dock.y(), dock.w(), dock.h(), PANEL, 1);
        float x = dock.x() + 8, y = dock.y() + 5;
        button(c, nextHits, new Rect(x, y, 55, 18), workspace.controller().playing() ? "Pause" : "Play",
                () -> workspace.invoke(Action.PLAY), workspace.controller().playing());
        stopButton = new Rect(x + 60, y, 42, 18);
        button(c, nextHits, stopButton, "Stop", () -> workspace.invoke(Action.STOP), false);
        button(c, nextHits, new Rect(x + 107, y, 42, 18), "Save", () -> workspace.invoke(Action.SAVE), false);
        c.text("FRAME " + workspace.controller().frame() + " / " + (document.durationFrames() - 1), x + 159, y + 6, Size.SMALL, TEXT, 1);
        c.fittedText("60 fps", dock.right() - 45, y + 6, 40, Size.SMALL, TEXT_DIM, 1);
        scrubber = new Rect(x, y + 27, dock.w() - 16, 18);
        c.rect(scrubber.x(), scrubber.y() + 5, scrubber.w(), 6, LINE, 1);
        for (var key : document.camera()) marker(c, key.frame(), document.durationFrames(), TEXT_FAINT, 3);
        for (var actor : document.actors()) if (actor.id().equals(workspace.controller().selectedActorId())) {
            for (var key : actor.keys()) marker(c, key.frame(), document.durationFrames(), ACCENT, 5);
        }
        for (var track : document.puppets()) if (track.actorId().equals(workspace.controller().selectedActorId()))
            for (var command : track.commands()) marker(c, command.frame(), document.durationFrames(), GOOD, 7);
        for (var caption : document.captions()) marker(c, caption.startFrame(), document.durationFrames(), GOOD, 3);
        for (var cue : document.dialogue()) marker(c, cue.startFrame(), document.durationFrames(), GOLD, 4);
        for (var cue : document.audio()) marker(c, cue.frame(), document.durationFrames(), WARN, 3);
        for (var effect : document.cameraEffects()) marker(c, effect.startFrame(), document.durationFrames(), WARN, 6);
        for (var change : document.sceneChanges()) marker(c, change.frame(), document.durationFrames(), GOLD, 8);
        marker(c, workspace.controller().frame(), document.durationFrames(), GOLD, 12);
    }

    private void marker(EditorHudCanvas c, int frame, int duration, int color, int size) {
        float x = scrubber.x() + frame / (float) Math.max(1, duration - 1) * scrubber.w();
        c.rect(x - 1, scrubber.y() + 8 - size / 2f, 2, size, color, 1);
    }

    private static void button(EditorHudCanvas c, List<Hit> hits, Rect rect, String label, Runnable action, boolean selected) {
        c.panel(rect.x(), rect.y(), rect.w(), rect.h(), selected ? ACCENT_DARK : PANEL_RAISED, 1);
        c.fittedText(label, rect.x() + 5, rect.y() + 5, rect.w() - 10, Size.SMALL, selected ? GOLD : TEXT, 1);
        hits.add(new Hit(rect, action));
    }
}
