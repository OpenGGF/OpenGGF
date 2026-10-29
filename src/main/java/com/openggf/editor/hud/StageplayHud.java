package com.openggf.editor.hud;

import com.openggf.i18n.UiMessages;
import com.openggf.editor.EditorLabels;
import java.util.Map;



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
    final EditorHudReader reader = new EditorHudReader();
    private int ticks;
    private final List<String> buttonLabels = new ArrayList<>();
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
    private long rowLanguageRevision = -1;
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
        ticks++;
        workspace.setCameraInputEnabled(!reader.isOpen() && page != Page.COMMANDS);
        pad.sample(input.capturePhysicalInput().gamepads());
        controllerPrompts = MenuInput.controller(input);
        if (reader.isOpen()) { reader.update(input, pad); return true; }
        if(workspace.pickingPosition())return true;
        if (!workspace.recording() && (input.isKeyPressed(GLFW_KEY_F2) || pad.pressed(R3))) {
            reader.open(detailsText()); pad.consumeHeld(); workspace.setCameraInputEnabled(false); return true;
        }
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
        if (document == rowDocument && page == rowPage && rowLanguageRevision == UiMessages.revision()) return cachedRows;
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
                    rows.add(new Row(actor.name(), (physical ? String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_hud.puppet_5urosi", Map.of("value0", String.valueOf(actor.gameId().toUpperCase(java.util.Locale.ROOT) + " / " + actor.character()))))))) : String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_hud.poser_rojl83", Map.of("value0", String.valueOf(actor.gameId().toUpperCase(java.util.Locale.ROOT) + " / " + actor.character())))))))), -1, () -> workspace.selectActor(actor.id())));
                }
                for (var actor : document.objectActors()) {
                    rows.add(new Row(actor.name(), UiMessages.text("editor.stageplay_hud.object_535wzx", Map.of("value0", String.valueOf(actor.gameId().toUpperCase(java.util.Locale.ROOT)))), -1,
                            () -> workspace.selectActor(actor.id())));
                }
            }
            case TIMELINE -> {
                for (var actor : document.actors()) for (var key : actor.keys()) {
                    boolean physical = document.puppets().stream().anyMatch(p -> p.actorId().equals(actor.id()));
                    rows.add(new Row(actor.name(), (physical ? ((UiMessages.text("editor.stageplay_hud.placement_visibility_1kc0yzi"))) : (key.pose() >= 0 ? String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_hud.pose_190h8he", Map.of("value0", String.valueOf(key.pose()))))))) : String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_hud.animation_d9nkdb", Map.of("value0", String.valueOf(key.animation()))))))))), key.frame(),
                            () -> { workspace.selectActor(actor.id()); workspace.seek(key.frame()); }));
                }
                for (var track : document.puppets()) for (var command : track.commands()) {
                    var actor = document.actors().stream().filter(a -> a.id().equals(track.actorId())).findFirst().orElseThrow();
                    String detail = (command.kind() == StageplayDocument.PuppetCommandKind.MOVE_TO ? UiMessages.text("editor.stageplay_hud.goal_n4nveh", Map.of("value0", String.valueOf(command.x()), "value1", String.valueOf(command.y()), "value2", String.valueOf(Math.round(command.urgency() * 100)))) : (command.kind() == StageplayDocument.PuppetCommandKind.JUMP ? UiMessages.text("editor.stageplay_hud.hold_frames_8yzerm", Map.of("value0", String.valueOf(command.holdFrames()))) : UiMessages.text("editor.stageplay_hud.native_braking_1p8qi4f")));
                    rows.add(new Row(actor.name() + " / " + EditorLabels.value(command.kind().name()), detail, command.frame(),
                            () -> { workspace.selectActor(actor.id()); workspace.seek(command.frame()); }));
                }
                for (var key : document.camera()) rows.add(new Row(UiMessages.text("editor.stageplay_hud.camera_x9ch8l"), EditorLabels.value(key.interpolation().name()), key.frame(), () -> workspace.seek(key.frame())));
                for (var caption : document.captions()) rows.add(new Row(UiMessages.text("editor.stageplay_hud.caption_10o2v2e"), caption.text(), caption.startFrame(), () -> workspace.seek(caption.startFrame())));
                for (var cue : document.dialogue()) rows.add(new Row(UiMessages.text("editor.stageplay_hud.dialogue_1m96f18", Map.of("value0", String.valueOf(StageplayDialogue.speakerName(document, cue)))),
                        cue.text().replace('\n',' '),cue.startFrame(),() -> {
                    if (!cue.speakerActor().isEmpty()) workspace.selectActor(cue.speakerActor());
                    workspace.seek(cue.startFrame());
                }));
                for (var cue : document.audio()) rows.add(new Row(UiMessages.text("editor.stageplay_hud.audio_11vgs6"), EditorLabels.value(cue.kind().name()), cue.frame(), () -> workspace.seek(cue.frame())));
                for (var effect : document.cameraEffects()) rows.add(new Row(UiMessages.text("editor.stageplay_hud.camera_shake_y55ffv"), UiMessages.text("editor.stageplay_hud.px_hz_b764t3", Map.of("value0", String.valueOf(effect.amplitudeX() + " / " + effect.amplitudeY()), "value1", String.valueOf(effect.frequency()))),
                        effect.startFrame(), () -> workspace.seek(effect.startFrame())));
                for (var change : document.sceneChanges()) rows.add(new Row(UiMessages.text("editor.stageplay_hud.scene_change_vbhj38"), change.transition()+" / "+change.set().gameId(),
                        change.frame(), () -> workspace.seek(change.frame())));
                rows.sort(Comparator.comparingInt(Row::frame));
            }
        }
        rowLanguageRevision = UiMessages.revision();
        rowDocument = document;
        rowPage = page;
        cachedRows = List.copyOf(rows);
        return cachedRows;
    }

    void draw(EditorHudCanvas c, EditorHudLayout layout) {
        var nextHits = new ArrayList<Hit>();
        buttonLabels.clear();
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
            c.text(UiMessages.text("editor.menu.stageplay_hud.pick_position"),8,6,Size.BODY,GOLD,1);
            c.fittedText(com.openggf.editor.stageplay.StageplayPositionPicker.HINT,8,layout.height()-16,layout.width()-16,Size.SMALL,TEXT_DIM,1);
            return;
        }
        c.rect(0, 0, layout.width(), layout.topBar().h(), PANEL, 1);
        c.text(UiMessages.text("editor.stageplay_hud.stageplay_5g3eaa"), 8, 6, Size.BODY, GOLD, 1);
        float exitWidth = EditorHudCanvas.textWidth(UiMessages.text("editor.stageplay_workspace.level_editor_i7o04p"), Size.SMALL) + 16 + EditorHudPainter.keycapWidth("F4");
        float titleX = 16 + EditorHudCanvas.textWidth(UiMessages.text("editor.stageplay_hud.stageplay_5g3eaa"), Size.BODY);
        // The mode chip sits in the top bar beside the exit, as the level editor's mode chips do, so
        // every mode is named at every width and the prompt bar keeps its whole length for keys.
        ModeChip chip = modeChip();
        float chipX = layout.width() - 6 - exitWidth - 6 - chip.width();
        c.pill(chipX, 4.5f, chip.width(), 13, PANEL_RAISED, 1);
        c.rect(chipX + 6, 9, 4, 4, chip.colour(), 1);
        c.text(chip.mode(), chipX + 14, 7.5f, Size.SMALL, chip.colour() == TEXT_DIM ? TEXT : chip.colour(), 1);
        c.text(documentTitle(document.name(), controller.dirty(), chipX - titleX - 10),
                titleX, 7, Size.SMALL, TEXT, 1);
        button(c, nextHits, new Rect(layout.width() - 6 - exitWidth, 3, exitWidth, 16), UiMessages.text("editor.stageplay_workspace.level_editor_i7o04p"),
                controllerPrompts ? null : "F4", controllerPrompts ? EditorHud.Glyph.EAST : null, workspace::deactivate, false);

        Rect panel = layout.workbench();
        if (panel != null && panel.w() > 20) drawPanel(c, panel, nextHits);
        drawTimeline(c, layout.dock(), document, nextHits);
        drawPromptBar(c, layout);
        if (reader.isOpen()) reader.draw(c, layout);
        hits = List.copyOf(nextHits);
    }

    String detailsText() {
        StringBuilder text = new StringBuilder(workspace.controller().document().name()).append("\n\n");
        List<Row> rows = rows();
        if (!rows.isEmpty()) {
            Row row = rows.get(Math.clamp(selected, 0, rows.size() - 1));
            text.append(row.label()).append("\n").append(row.detail()).append("\n\n");
        }
        for (Row row : rows) text.append(row.frame() >= 0 ? row.frame() + "  " : "").append(row.label()).append("\n").append(row.detail()).append("\n");
        for (Action action : Action.values()) text.append(action.label()).append("\n");
        for (String label : buttonLabels) text.append(label).append("\n");
        text.append("\n").append(workspace.status()).append("\n").append(modeChip().mode());
        for (var prompt : prompts(modeChip().fractional())) text.append("\n").append(prompt.key()).append(" / ")
                .append(prompt.glyph()).append(" ").append(prompt.label());
        var route = workspace.routeOverlay();
        if (route != null) text.append("\n").append(route.reason()).append("\n").append(route.motionSummary());
        return text.toString();
    }


    /** Keep the unsaved cue visible even when the scene name needs elision. */
    static String documentTitle(String name, boolean dirty, float width) {
        String marker = dirty ? " *" : "";
        return EditorHudCanvas.fit(name, width - EditorHudCanvas.textWidth(marker, Size.SMALL), Size.SMALL)
                + marker;
    }

    /** The prompt bar: the keys that act in the current mode (pad glyphs after a pad press). */
    private void drawPromptBar(EditorHudCanvas c, EditorHudLayout layout) {
        float readerRoom = workspace.recording() ? 0 : EditorHudPainter.readerHintWidth(controllerPrompts, "F2");
        EditorHudPainter.promptBar(c, layout.promptBar(), prompts(modeChip().fractional()), controllerPrompts, pad, readerRoom, ticks);
        if (readerRoom > 0) EditorHudPainter.readerHint(c, layout.promptBar(), controllerPrompts, "F2");
    }

    /** The top bar's mode chip: what the authoring view is doing now, in its state colour. */
    record ModeChip(String mode, int colour, boolean fractional, float width) { }

    ModeChip modeChip() {
        boolean fractional = workspace.cameraMode() && !workspace.recording()
                && workspace.directorCamera().zoom() != Math.rint(workspace.directorCamera().zoom());
        String mode = workspace.recordingCamera() ? UiMessages.text("editor.menu.stageplay_hud.rec_camera_take") : workspace.recordingPuppet() ? UiMessages.text("editor.menu.stageplay_hud.rec_puppet_take")
                : workspace.recording() ? UiMessages.text("editor.menu.stageplay_hud.rec_poser_take") : fractional ? UiMessages.text("editor.menu.stageplay_hud.fractional_zoom")
                : workspace.cameraMode() ? UiMessages.text("editor.menu.stageplay_hud.camera") : workspace.controller().playing() ? UiMessages.text("editor.menu.stageplay_hud.playing")
                : page == Page.COMMANDS ? UiMessages.text("editor.menu.stageplay_hud.commands") : UiMessages.text("editor.menu.stageplay_hud.directing");
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
                    prompts.add(new EditorHud.Prompt(null, "WASD", UiMessages.text("editor.menu.stageplay_hud.pan")));
                    prompts.add(new EditorHud.Prompt(null, "Q / E", UiMessages.text("editor.stageplay_menu_presentation.zoom_1ntv7")));
                } else if (workspace.recordingPuppet()) {
                    prompts.add(new EditorHud.Prompt(null, "A / D", UiMessages.text("editor.editor_hud.move_1fj75")));
                    prompts.add(new EditorHud.Prompt(null, "W", UiMessages.text("editor.stageplay_workspace.jump_1dqha")));
                    prompts.add(new EditorHud.Prompt(null, "S", UiMessages.text("editor.menu.stageplay_hud.brake")));
                } else {
                    prompts.add(new EditorHud.Prompt(null, "WASD", UiMessages.text("editor.editor_hud.move_1fj75")));
                }
            }
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.SOUTH, "Space", workspace.recordingPuppet() ? UiMessages.text("editor.menu.stageplay_hud.end_take") : UiMessages.text("editor.stageplay_hud.stop_1jgo2")));
            return prompts;
        }
        if (workspace.cameraMode()) {
            if (padPrompts) {
                prompts.add(new EditorHud.Prompt(EditorHud.Glyph.WEST, "K", UiMessages.text("editor.stageplay_menu_presentation.camera_key_1yp2z2c")));
                prompts.add(new EditorHud.Prompt(EditorHud.Glyph.EAST, "C", UiMessages.text("editor.menu.stageplay_hud.leave_camera")));
                prompts.add(new EditorHud.Prompt(EditorHud.Glyph.MENU, "F1", UiMessages.text("editor.stageplay_hud.commands_1q4v6i0")));
                return prompts;
            }
            prompts.add(new EditorHud.Prompt(null, "WASD", UiMessages.text("editor.menu.stageplay_hud.pan")));
            prompts.add(new EditorHud.Prompt(null, "Q / E", fractional ? UiMessages.text("editor.menu.stageplay_hud.whole_zoom") : UiMessages.text("editor.stageplay_menu_presentation.zoom_1ntv7")));
            prompts.add(new EditorHud.Prompt(null, "K", fractional ? UiMessages.text("editor.menu.stageplay_hud.capture") : UiMessages.text("editor.stageplay_menu_presentation.camera_key_1yp2z2c")));
            if (!fractional) prompts.add(new EditorHud.Prompt(null, "R", UiMessages.text("editor.menu.stageplay_hud.record")));
            prompts.add(new EditorHud.Prompt(null, "C", UiMessages.text("editor.menu.stageplay_hud.leave")));
            prompts.add(new EditorHud.Prompt(null, "F1", UiMessages.text("editor.stageplay_hud.commands_1q4v6i0")));
            return prompts;
        }
        if (page == Page.COMMANDS) {
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.DPAD, "Up / Down", UiMessages.text("editor.editor_hud.choose_xd8it3")));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.SOUTH, "Enter", UiMessages.text("editor.editor_hud.run_1ror")));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.EAST, "Esc", UiMessages.text("editor.editor_hud.back_187if")));
            return prompts;
        }
        // At most five prompts, for the focused pane, with Commands last. Every key stays bound on
        // both panes; a key already on a visible button (C on Direct camera, the pad's Add actor)
        // is not repeated here. Tab names the pane it opens, as its tab button does.
        String other = page == Page.TIMELINE ? UiMessages.text("editor.stageplay_hud.cast_18uvz") : UiMessages.text("editor.stageplay_hud.keys_cues_1c41qpp");
        if (padPrompts) {
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.DPAD, "", page == Page.TIMELINE ? UiMessages.text("editor.menu.stageplay_hud.seek_key") : UiMessages.text("editor.menu.stageplay_hud.actor_seek")));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.SOUTH, "", UiMessages.text("editor.editor_hud.choose_xd8it3")));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.WEST, "", UiMessages.text("editor.stageplay_workspace.set_key_1obzb0h")));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.VIEW, "", other));
            prompts.add(new EditorHud.Prompt(EditorHud.Glyph.MENU, "", UiMessages.text("editor.stageplay_hud.commands_1q4v6i0")));
            return prompts;
        }
        if (page == Page.TIMELINE) {
            prompts.add(new EditorHud.Prompt(null, "Left / Right", UiMessages.text("editor.menu.stageplay_hud.seek")));
            prompts.add(new EditorHud.Prompt(null, "Space", UiMessages.text("editor.stageplay_hud.play_1hdg4")));
            prompts.add(new EditorHud.Prompt(null, "R", UiMessages.text("editor.menu.stageplay_hud.record")));
        } else {
            prompts.add(new EditorHud.Prompt(null, "Up / Down", UiMessages.text("editor.stageplay_menu_presentation.actor_11kb11")));
            prompts.add(new EditorHud.Prompt(null, "Enter", UiMessages.text("editor.editor_hud.choose_xd8it3")));
            prompts.add(new EditorHud.Prompt(null, "K", UiMessages.text("editor.stageplay_workspace.set_key_1obzb0h")));
        }
        prompts.add(new EditorHud.Prompt(null, "Tab", other));
        prompts.add(new EditorHud.Prompt(null, "F1", UiMessages.text("editor.stageplay_hud.commands_1q4v6i0")));
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

    private static String friendly(String text) { return text; }

    private List<StageplayDocument.Actor> documentActors() { return workspace.controller().document().actors(); }

    private void drawPanel(EditorHudCanvas c, Rect panel, List<Hit> nextHits) {
        c.panel(panel.x(), panel.y(), panel.w(), panel.h(), PANEL, 1);
        float gap = 4, width = (panel.w() - 20) / 3;
        Page[] pages = Page.values();
        String[] names = {UiMessages.text("editor.stageplay_hud.cast_18uvz"), UiMessages.text("editor.stageplay_hud.keys_cues_1c41qpp"), UiMessages.text("editor.stageplay_hud.commands_1q4v6i0")};
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            button(c, nextHits, new Rect(panel.x() + 6 + i * (width + gap), panel.y() + 7, width, 18),
                    names[i], () -> changePage(target), page == target);
        }
        List<Row> rows = rows();
        var route = workspace.routeOverlay();
        float routeHeight = route == null ? 0 : Math.max(62, EditorHudCanvas.lineHeight(Size.SMALL) * 5 + 14);
        int rowPitch = Math.max(27, EditorHudCanvas.lineHeight(Size.SMALL) * 2 + 9);
        visibleRows = Math.max(1, (int) ((panel.h() - 110 - routeHeight) / rowPitch));
        selected = rows.isEmpty() ? 0 : Math.clamp(selected, 0, rows.size() - 1);
        int first = Math.max(0, Math.min(selected - visibleRows / 2, rows.size() - visibleRows));
        if (rows.isEmpty()) {
            c.fittedText(UiMessages.text("editor.stageplay_hud.add_an_actor_to_start_directing_1n3pb6h"), panel.x() + 9, panel.y() + 39, panel.w() - 18, Size.SMALL, TEXT_DIM, 1);
        }
        for (int i = first; i < Math.min(rows.size(), first + visibleRows); i++) {
            Row row = rows.get(i);
            int index = i;
            Rect area = new Rect(panel.x() + 6, panel.y() + 31 + (i - first) * rowPitch, panel.w() - 12, rowPitch - 2);
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
                c.fittedText(label, area.x() + 6, area.y() + Math.max(0, (area.h() - EditorHudCanvas.lineHeight(Size.SMALL)) / 2), rowText, Size.SMALL, TEXT, 1);
            } else {
                c.fittedText(label, area.x() + 6, area.y() + 4, rowText, Size.SMALL, TEXT, 1);
                c.fittedText(friendly(row.detail()), area.x() + 6, area.y() + 7 + EditorHudCanvas.lineHeight(Size.SMALL), rowText, Size.SMALL, TEXT_DIM, 1);
            }
            nextHits.add(new Hit(area, () -> { selected = index; row.action().run(); }));
        }
        float bottom = panel.bottom() - 67;
        if (route != null) {
            float x = panel.x() + 10, y = bottom - routeHeight - 10;
            int legendPitch = Math.max(12, EditorHudCanvas.lineHeight(Size.SMALL) + 2);
            c.rect(x - 2, y - 5, panel.w() - 16, 1, LINE, 1);
            c.rect(x,y+2,3,3,route.successful()?GOOD:WARN,1);
            c.fittedText(route.summary(), x+8, y, panel.w()-36, Size.SMALL, route.successful()?GOOD:WARN,1);
            c.rect(x,y+legendPitch,6,6,com.openggf.stageplay.editor.StageplayRouteOverlay.GROUND,1);c.text(UiMessages.text("editor.stageplay_hud.walk_1lmg9"),x+10,y+legendPitch,Size.SMALL,TEXT_DIM,1);
            c.rect(x+60,y+legendPitch,6,6,com.openggf.stageplay.editor.StageplayRouteOverlay.AIR,1);c.text(UiMessages.text("editor.stageplay_hud.air_1esq"),x+70,y+legendPitch,Size.SMALL,TEXT_DIM,1);
            c.rect(x,y+2*legendPitch,6,6,com.openggf.stageplay.editor.StageplayRouteOverlay.GROUND,1);c.text(UiMessages.text("editor.stageplay_hud.start_1br2gy"),x+10,y+2*legendPitch,Size.SMALL,TEXT_DIM,1);
            c.rect(x+60,y+2*legendPitch,6,6,route.goalColor(),1);c.text(UiMessages.text("editor.stageplay_hud.goal_1bos3"),x+70,y+2*legendPitch,Size.SMALL,TEXT_DIM,1);
            c.rect(x,y+3*legendPitch,6,6,route.applied()?0xFFFFFF:com.openggf.stageplay.editor.StageplayRouteOverlay.FAILURE,1);
            c.text(route.applied() ? UiMessages.text("editor.stageplay_hud.actual_wchm0e") : UiMessages.text("editor.stageplay_hud.stopped_1vebulp"), x+10, y + 3*legendPitch, Size.SMALL, TEXT_DIM, 1);
            float cap = EditorHudPainter.keycap(c, "F1", x, y + 4*legendPitch + 3);
            c.text(UiMessages.text("editor.menu.stageplay_hud.commands_direction_details"), x + cap + 4, y + 4*legendPitch, Size.SMALL, TEXT_FAINT, 1);
        }
        c.fittedText(rows.isEmpty() ? "" : (selected + 1) + " / " + rows.size(),
                panel.x() + 8, bottom - 10, panel.w() - 16, Size.SMALL, TEXT_FAINT, 1);
        float half = (panel.w() - 18) / 2;
        Action[] actions = workspace.cameraMode()
                ? new Action[] {Action.CAMERA_MODE, Action.SET_CAMERA_KEY, Action.RECORD_CAMERA, Action.CAMERA_EFFECTS}
                : new Action[] {Action.ADD_ACTOR, Action.EDIT_ACTOR, Action.CAMERA_MODE, Action.SCENE_CHANGES};
        String[] labels = workspace.cameraMode() ? new String[] {UiMessages.text("editor.menu.stageplay_hud.leave_camera"), UiMessages.text("editor.menu.stageplay_hud.capture_key"), UiMessages.text("editor.menu.stageplay_hud.record_take"), UiMessages.text("editor.stageplay_hud.shake_quake_szdox8")}
                : new String[] {UiMessages.text("editor.stageplay_hud.add_actor_8pm4k6"), UiMessages.text("editor.stageplay_hud.edit_actor_14lpawv"), UiMessages.text("editor.menu.stageplay_hud.direct_camera"), UiMessages.text("editor.stageplay_hud.scene_changes_1bhmq6n")};
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
        return com.openggf.graphics.text.HostText.shared().wrap(text, Math.max(1, (int) width),
                com.openggf.graphics.text.TextFace.COMPACT, com.openggf.graphics.text.HostTextContext.current());
    }

    private void drawTimeline(EditorHudCanvas c, Rect dock, StageplayDocument document, List<Hit> nextHits) {
        c.rect(dock.x(), dock.y(), dock.w(), dock.h(), PANEL, 1);
        float x = dock.x() + 8, y = dock.y() + 5;
        button(c, nextHits, new Rect(x, y, 55, 18), (workspace.controller().playing() ? UiMessages.text("editor.stageplay_hud.pause_19rys6") : UiMessages.text("editor.stageplay_hud.play_1hdg4")),
                () -> workspace.invoke(Action.PLAY), workspace.controller().playing());
        stopButton = new Rect(x + 60, y, 42, 18);
        button(c, nextHits, stopButton, UiMessages.text("editor.stageplay_hud.stop_1jgo2"), () -> workspace.invoke(Action.STOP), false);
        button(c, nextHits, new Rect(x + 107, y, 42, 18), UiMessages.text("editor.stageplay_hud.save_1j2ql"), () -> workspace.invoke(Action.SAVE), false);
        c.text(UiMessages.text("editor.stageplay_hud.frame_jw5pst", Map.of("value0", String.valueOf(workspace.controller().frame()), "value1", String.valueOf((document.durationFrames() - 1)))), x + 159, y + 6, Size.SMALL, TEXT, 1);
        c.fittedText(UiMessages.text("editor.stageplay_hud.60_fps_qbgbr7"), dock.right() - 45, y + 6, 40, Size.SMALL, TEXT_DIM, 1);
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
        buttonLabels.add(label);
        c.text(com.openggf.game.MenuStyle.pageText(label, Math.max(1, (int) (rect.w() - 10 - shortcut)), Size.SMALL.scale, ticks),
                rect.x() + 5, rect.y() + Math.max(0, (rect.h() - EditorHudCanvas.lineHeight(Size.SMALL)) / 2), Size.SMALL, selected ? GOLD : TEXT, 1);
        hits.add(new Hit(rect, action));
    }
}
