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
    /** A list row; {@code key} is the keyboard shortcut drawn as a cap beside a command, or empty. */
    record Row(String label, String detail, int frame, Runnable action, String key) {
        Row(String label, String detail, int frame, Runnable action) { this(label, detail, frame, action, ""); }
    }
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
        if(workspace.pickingPosition())return true;
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
                }, shortcut(action)));
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
        if(workspace.pickingPosition()){
            c.clip(game);
            try {workspace.paintPositionPicker(new com.openggf.editor.stageplay.StageplayPositionPicker.Painter(){
                public void line(float x1,float y1,float x2,float y2,int rgb,float width){c.line(x1,y1,x2,y2,rgb,width);}
                public void rect(float x,float y,float w,float h,int rgb){c.rect(x,y,w,h,rgb,1);}
                public void text(String text,float x,float y,int rgb){c.text(text,x,y,Size.SMALL,rgb,1);}
                public float textWidth(String text){return EditorHudCanvas.textWidth(text,Size.SMALL);}
            },game.x(),game.y(),game.w(),game.h());}
            finally {c.unclip();}
            c.text("PICK POSITION",8,6,Size.BODY,GOLD,1);
            c.fittedText(com.openggf.editor.stageplay.StageplayPositionPicker.HINT,8,layout.height()-16,layout.width()-16,Size.SMALL,TEXT_DIM,1);
            return;
        }
        c.rect(0, 0, layout.width(), layout.topBar().h(), PANEL, 1);
        c.text("STAGEPLAY", 8, 6, Size.BODY, GOLD, 1);
        float exitWidth = EditorHudCanvas.textWidth("Level editor", Size.SMALL) + 16 + EditorHudPainter.keycapWidth("F4");
        float titleX = 16 + EditorHudCanvas.textWidth("STAGEPLAY", Size.BODY);
        // The mode chip sits in the top bar beside the exit, as the level editor's mode chips do, so
        // every mode is named at every width and the prompt bar keeps its whole length for keys.
        ModeChip chip = modeChip();
        float chipX = layout.width() - 6 - exitWidth - 6 - chip.width();
        c.pill(chipX, 4.5f, chip.width(), 13, PANEL_RAISED, 1);
        c.rect(chipX + 6, 9, 4, 4, chip.colour(), 1);
        c.text(chip.mode(), chipX + 14, 7.5f, Size.SMALL, chip.colour() == TEXT_DIM ? TEXT : chip.colour(), 1);
        c.fittedText(document.name() + (controller.dirty() ? " *" : ""), titleX, 7,
                chipX - titleX - 10, Size.SMALL, TEXT, 1);
        button(c, nextHits, new Rect(layout.width() - 6 - exitWidth, 3, exitWidth, 16), "Level editor",
                controllerPrompts ? null : "F4", controllerPrompts ? EditorHud.Glyph.EAST : null, workspace::deactivate, false);

        Rect panel = layout.workbench();
        if (panel != null && panel.w() > 20) drawPanel(c, panel, nextHits);
        drawTimeline(c, layout.dock(), document, nextHits);
        drawPromptBar(c, layout);
        hits = List.copyOf(nextHits);
    }

    /** The prompt bar: the keys that act in the current mode (pad glyphs after a pad press). */
    private void drawPromptBar(EditorHudCanvas c, EditorHudLayout layout) {
        EditorHudPainter.promptBar(c, layout.promptBar(), prompts(modeChip().fractional()), controllerPrompts, pad, 0);
    }

    /** The top bar's mode chip: what the authoring view is doing now, in its state colour. */
    record ModeChip(String mode, int colour, boolean fractional, float width) { }

    ModeChip modeChip() {
        boolean fractional = workspace.cameraMode() && !workspace.recording()
                && workspace.directorCamera().zoom() != Math.rint(workspace.directorCamera().zoom());
        String mode = workspace.recordingCamera() ? "REC CAMERA TAKE" : workspace.recordingPuppet() ? "REC PUPPET TAKE"
                : workspace.recording() ? "REC POSER TAKE" : fractional ? "FRACTIONAL ZOOM"
                : workspace.cameraMode() ? "CAMERA" : workspace.controller().playing() ? "PLAYING"
                : page == Page.COMMANDS ? "COMMANDS" : "DIRECTING";
        int colour = workspace.recording() ? WARN : fractional ? GOLD : workspace.cameraMode() ? ACCENT
                : workspace.controller().playing() ? GOOD : TEXT_DIM;
        return new ModeChip(mode, colour, fractional, EditorHudCanvas.textWidth(mode, Size.SMALL) + 20);
    }

    /** The prompts for the current mode; a pad shows only what its buttons do. */
    List<EditorHud.Prompt> prompts(boolean fractional) {
        var prompts = new ArrayList<EditorHud.Prompt>();
        boolean padPrompts = controllerPrompts;
        if (workspace.recording()) {
            if (!padPrompts) {
                if (workspace.recordingCamera()) {
                    prompts.add(new EditorHud.Prompt(null, "WASD", "Pan"));
                    prompts.add(new EditorHud.Prompt(null, "Q / E", "Zoom"));
                } else if (workspace.recordingPuppet()) {
                    prompts.add(new EditorHud.Prompt(null, "A / D", "Move"));
                    prompts.add(new EditorHud.Prompt(null, "W", "Jump"));
                    prompts.add(new EditorHud.Prompt(null, "S", "Brake"));
                } else {
                    prompts.add(new EditorHud.Prompt(null, "WASD", "Move"));
                }
            }
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.SOUTH, "Space", workspace.recordingPuppet() ? "End take" : "Stop"));
            return prompts;
        }
        if (workspace.cameraMode()) {
            if (padPrompts) {
                prompts.add(new EditorHud.Prompt(EditorHud.Glyph.WEST, "K", "Camera key"));
                prompts.add(new EditorHud.Prompt(EditorHud.Glyph.EAST, "C", "Leave camera"));
                prompts.add(new EditorHud.Prompt(EditorHud.Glyph.MENU, "F1", "Commands"));
                return prompts;
            }
            prompts.add(new EditorHud.Prompt(null, "WASD", "Pan"));
            prompts.add(new EditorHud.Prompt(null, "Q / E", fractional ? "Whole zoom" : "Zoom"));
            prompts.add(new EditorHud.Prompt(null, "K", fractional ? "Capture" : "Camera key"));
            if (!fractional) prompts.add(new EditorHud.Prompt(null, "R", "Record"));
            prompts.add(new EditorHud.Prompt(null, "C", "Leave"));
            prompts.add(new EditorHud.Prompt(null, "F1", "Commands"));
            return prompts;
        }
        if (page == Page.COMMANDS) {
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.DPAD, "Up / Down", "Choose"));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.SOUTH, "Enter", "Run"));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.EAST, "Esc", "Back"));
            return prompts;
        }
        if (padPrompts) {
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.DPAD, "", "Seek / select"));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.SOUTH, "", "Choose"));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.WEST, "", "Set key"));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.NORTH, "", "Add actor"));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.BUMPERS, "", "Actor"));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.VIEW, "", page == Page.TIMELINE ? "Cast" : "Timeline"));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.MENU, "", "Commands"));
            return prompts;
        }
        prompts.add(new EditorHud.Prompt(null, "Arrows", "Seek / select"));
        prompts.add(new EditorHud.Prompt(null, "Enter", "Choose"));
        prompts.add(new EditorHud.Prompt(null, "Tab", page == Page.TIMELINE ? "Cast" : "Timeline"));
        prompts.add(new EditorHud.Prompt(null, "K", "Set key"));
        prompts.add(new EditorHud.Prompt(null, "R", "Record"));
        prompts.add(new EditorHud.Prompt(null, "C", "Camera"));
        prompts.add(new EditorHud.Prompt(null, "Space", "Play"));
        prompts.add(new EditorHud.Prompt(null, "F1", "Commands"));
        return prompts;
    }

    /** The keyboard shortcut that runs {@code action} from the authoring view, or empty. */
    static String shortcut(Action action) {
        return switch (action) {
            case CAMERA_MODE -> "C";
            case SET_KEY, SET_CAMERA_KEY -> "K";
            case RECORD, RECORD_CAMERA -> "R";
            case PLAY -> "Space";
            case SAVE -> "Ctrl S";
            case UNDO -> "Ctrl Z";
            case REDO -> "Ctrl Y";
            case EXIT -> "F4";
            default -> "";
        };
    }

    private static String friendly(String text) {
        String readable=java.util.regex.Pattern.compile("\\b[A-Z][A-Z_]{2,}\\b").matcher(text).replaceAll(m -> java.util.Arrays.stream(m.group().split("_"))
                .map(w -> w.charAt(0)+w.substring(1).toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.joining(" ")));
        return readable.replace("S1 /", "Sonic 1 /").replace("S2 /", "Sonic 2 /").replace("S3K /", "Sonic 3 & K /")
                .replace("/ sonic /", "/ Sonic /").replace("/ tails /", "/ Tails /").replace("/ knuckles /", "/ Knuckles /")
                .replace("Rom Music", "Music").replace("Rom Sfx", "Sound effect");
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
        float routeHeight = route == null ? 0 : 62;
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
            float capWidth = row.key().isEmpty() ? 0 : EditorHudPainter.keycapWidth(row.key()) + 6;
            if (capWidth > 0) EditorHudPainter.keycap(c, row.key(), area.right() - capWidth, area.y() + area.h() / 2f);
            float rowText = area.w() - 12 - capWidth;
            if (row.detail().isEmpty()) {
                c.fittedText(label, area.x() + 6, area.y() + 9, rowText, Size.SMALL, TEXT, 1);
            } else {
                c.fittedText(label, area.x() + 6, area.y() + 4, rowText, Size.SMALL, TEXT, 1);
                c.fittedText(friendly(row.detail()), area.x() + 6, area.y() + 15, rowText, Size.SMALL, TEXT_DIM, 1);
            }
            nextHits.add(new Hit(area, () -> { selected = index; row.action().run(); }));
        }
        float bottom = panel.bottom() - 67;
        if (route != null) {
            float x = panel.x() + 10, y = bottom - routeHeight - 10;
            c.rect(x - 2, y - 5, panel.w() - 16, 1, LINE, 1);
            c.rect(x,y+2,3,3,route.successful()?GOOD:WARN,1);
            c.fittedText(route.summary(), x+8, y, panel.w()-36, Size.SMALL, route.successful()?GOOD:WARN,1);
            c.rect(x,y+14,6,6,com.openggf.stageplay.editor.StageplayRouteOverlay.GROUND,1);c.text("Walk",x+10,y+14,Size.SMALL,TEXT_DIM,1);
            c.rect(x+60,y+14,6,6,com.openggf.stageplay.editor.StageplayRouteOverlay.AIR,1);c.text("Air",x+70,y+14,Size.SMALL,TEXT_DIM,1);
            c.rect(x,y+26,6,6,com.openggf.stageplay.editor.StageplayRouteOverlay.GROUND,1);c.text("Start",x+10,y+26,Size.SMALL,TEXT_DIM,1);
            c.rect(x+60,y+26,6,6,route.goalColor(),1);c.text("Goal",x+70,y+26,Size.SMALL,TEXT_DIM,1);
            c.rect(x,y+37,6,6,route.applied()?0xFFFFFF:com.openggf.stageplay.editor.StageplayRouteOverlay.FAILURE,1);
            c.text(route.applied() ? "Actual" : "Stopped", x+10, y + 37, Size.SMALL, TEXT_DIM, 1);
            float cap = EditorHudPainter.keycap(c, "F1", x, y + 53);
            c.text("Commands: Direction details", x + cap + 4, y + 50, Size.SMALL, TEXT_FAINT, 1);
        }
        c.fittedText(rows.isEmpty() ? "" : (selected + 1) + " / " + rows.size(),
                panel.x() + 8, bottom - 10, panel.w() - 16, Size.SMALL, TEXT_FAINT, 1);
        float half = (panel.w() - 18) / 2;
        Action[] actions = workspace.cameraMode()
                ? new Action[] {Action.CAMERA_MODE, Action.SET_CAMERA_KEY, Action.RECORD_CAMERA, Action.CAMERA_EFFECTS}
                : new Action[] {Action.ADD_ACTOR, Action.EDIT_ACTOR, Action.CAMERA_MODE, Action.SCENE_CHANGES};
        String[] labels = workspace.cameraMode() ? new String[] {"Leave camera", "Capture key", "Record take", "Shake / quake"}
                : new String[] {"Add actor", "Edit actor", "Direct camera", "Scene changes"};
        // Each button carries its own key: a cap for the keyboard, the pad button after a pad press.
        String[] keys = workspace.cameraMode() ? new String[] {"C", "K", "R", null} : new String[] {null, null, "C", null};
        EditorHud.Glyph[] glyphs = workspace.cameraMode()
                ? new EditorHud.Glyph[] {EditorHud.Glyph.EAST, EditorHud.Glyph.WEST, null, null}
                : new EditorHud.Glyph[] {EditorHud.Glyph.NORTH, null, null, null};
        for (int i = 0; i < actions.length; i++) {
            Action action = actions[i];
            button(c, nextHits, new Rect(panel.x() + 6 + (i % 2) * (half + 6), bottom + (i / 2) * 21, half, 18),
                    labels[i], controllerPrompts ? null : keys[i], controllerPrompts ? glyphs[i] : null,
                    () -> workspace.invoke(action), false);
        }
        c.fittedText(route == null ? workspace.statusSummary() : "", panel.x() + 8,
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

    private void button(EditorHudCanvas c, List<Hit> hits, Rect rect, String label, Runnable action, boolean selected) {
        button(c, hits, rect, label, null, null, action, selected);
    }

    /** A button whose shortcut sits at its right edge: a key cap, or a pad glyph. */
    private void button(EditorHudCanvas c, List<Hit> hits, Rect rect, String label, String key, EditorHud.Glyph glyph,
                        Runnable action, boolean selected) {
        c.panel(rect.x(), rect.y(), rect.w(), rect.h(), selected ? ACCENT_DARK : PANEL_RAISED, 1);
        float centre = rect.y() + rect.h() / 2f;
        float shortcut = 0;
        if (key != null) {
            shortcut = EditorHudPainter.keycapWidth(key) + 4;
            EditorHudPainter.keycap(c, key, rect.right() - shortcut, centre);
        } else if (glyph != null) {
            shortcut = EditorHudPainter.glyphWidthOf(glyph) + 4;
            EditorHudPainter.glyph(c, glyph, rect.right() - shortcut, centre, pad);
        }
        c.fittedText(label, rect.x() + 5, rect.y() + 5, rect.w() - 10 - shortcut, Size.SMALL, selected ? GOLD : TEXT, 1);
        hits.add(new Hit(rect, action));
    }
}
