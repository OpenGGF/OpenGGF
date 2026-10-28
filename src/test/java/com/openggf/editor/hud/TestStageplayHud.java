package com.openggf.editor.hud;

import com.openggf.stageplay.StageplayDocument.Facing;

import com.openggf.InputBindingFactory;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.editor.stageplay.StageplayWorkspace;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.control.PhysicalGamepad.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real shared forms and synthetic controller/keyboard input; no GL or ROM dependencies. */
class TestStageplayHud {
    private final boolean[] buttons = new boolean[15];
    private final float[] axes = {0, 0, 0, 0, -1, -1};
    private StageplayWorkspace workspace;
    private StageplayHud hud;
    private InputHandler input;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        workspace = new StageplayWorkspace(() -> StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224));
        workspace.activate();
        hud = new StageplayHud(workspace);
        input = new InputHandler(InputBindingFactory.standaloneSupplier(), () -> List.of(
                GamepadStateSource.DeviceState.connected(0, "Xbox Controller", buttons, axes)));
        InputHandlerInternalAccess.suppressGamepad(input, true);
        tick();
    }

    @Test void pickerUsesTheFormerPanelAndDockSpaceAndFitsLargeFrames() {
        var ordinary=StageplayHud.layout(1280,720,480,270);
        var picker=EditorHudLayout.positionPicker(1280,720,480,270);
        assertNull(picker.workbench());assertTrue(picker.game().w()>ordinary.game().w());
        assertTrue(picker.game().y()>=picker.topBar().bottom());assertTrue(picker.game().bottom()<=picker.promptBar().y());
        var large=EditorHudLayout.positionPicker(1280,720,1920,1080);
        assertTrue(large.game().right()<=large.width());assertTrue(large.game().bottom()<=large.promptBar().y());
    }

    @Test void routeResultSentenceFitsTheActualSidePanelWithoutEllipsis() {
        var request = new com.openggf.stageplay.route.StageplayRouteRequest("hero", 0, 400, 300, 300);
        var result = com.openggf.stageplay.route.StageplayRouteResult.failure(
                com.openggf.stageplay.route.StageplayRouteResult.Status.BUDGET_EXCEEDED,
                "Try Run. Reachability is still unknown.", "0".repeat(64), request, 1024, 26686);
        var route = new com.openggf.stageplay.editor.StageplayRouteOverlay(result, false);
        for (int[] size : new int[][] {{1280, 720}, {1920, 1080}, {1024, 576}}) {
            var panel = StageplayHud.layout(size[0], size[1], 480, 270).workbench();
            assertEquals(route.summary(), EditorHudCanvas.fit(route.summary(), panel.w() - 36,
                    EditorHudCanvas.Size.SMALL), "Route result must be a complete sentence at " + size[0]);
        }
        assertTrue(route.reason().contains("Reachability is still unknown"));
        assertTrue(route.reason().contains("Try Run"));
    }

    @Test void routeExplanationWrapsAtWordsWithoutLosingText() {
        String explanation = "Search limit reached. Try a closer goal or increase the search budget. Reachability is still unknown.";
        for (int width : new int[] {260, 280, 420}) {
            var lines = StageplayHud.wrapRouteText(explanation, width);
            assertEquals(explanation, String.join(" ", lines));
            assertTrue(lines.stream().allMatch(line -> EditorHudCanvas.textWidth(line, EditorHudCanvas.Size.SMALL) <= width));
            assertFalse(lines.stream().anyMatch(line -> line.contains("...")));
        }
    }

    @Test
    void controllerCreatesActorAndEditsAKeyThroughTheSharedForms() {
        press(BUTTON_Y);
        assertEquals("Add actor", workspace.menu().title());
        workspace.menu().setValue("name", "Hero");
        workspace.menu().setValue("mode", "POSER");
        workspace.menu().submit();
        assertEquals("Hero", workspace.controller().document().actors().getFirst().name());

        workspace.seek(20);
        hud.reactivated();
        tick();
        press(BUTTON_X);
        assertEquals("Actor key", workspace.menu().title());
        workspace.menu().setValue("x", "240");
        workspace.menu().setValue("pose", "5");
        workspace.menu().submit();
        var actor = workspace.controller().document().actors().getFirst();
        assertEquals(2, actor.keys().size());
        assertEquals(240, actor.keys().getLast().x());
        assertEquals(5, actor.keys().getLast().pose());
        workspace.invoke(StageplayWorkspace.Action.UNDO);
        assertEquals(1, workspace.controller().document().actors().getFirst().keys().size());
    }

    @Test
    void commandsRemainReachableWithControllerAndCarriedButtonsAreSuppressed() {
        buttons[BUTTON_Y] = true;
        hud.reactivated();
        tick();
        assertFalse(workspace.menu().isOpen(), "A button held through a form must not reopen it");
        buttons[BUTTON_Y] = false;
        tick();
        press(BUTTON_START);
        assertEquals(StageplayWorkspace.Action.values().length, hud.rows().size());
        assertTrue(hud.rows().stream().anyMatch(row -> row.label().equals("Audio cues")));
        assertTrue(hud.rows().stream().anyMatch(row -> row.label().equals("Dialogue lines")));
        assertTrue(hud.rows().stream().anyMatch(row -> row.label().equals("Dialogue typers")));
        assertTrue(hud.rows().stream().anyMatch(row -> row.label().equals("Dialogue decorations")));
        assertTrue(hud.rows().stream().anyMatch(row -> row.label().equals("Export film")));
    }

    @Test
    void seekingAndLeavingConsumeTheWholeTickWithoutEditingTheScene() {
        var original = workspace.controller().document();
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_PRESS);
        tick();
        assertEquals(1, workspace.controller().frame());
        assertEquals(original, workspace.controller().document());
        input.handleKeyEvent(GLFW_KEY_RIGHT, GLFW_RELEASE);
        tick();
        input.handleKeyEvent(GLFW_KEY_F4, GLFW_PRESS);
        tick();
        assertFalse(workspace.active());
        assertEquals(original, workspace.controller().document());
    }

    @Test
    void timelineContainsActorKeysCameraCaptionsAndAudioInFrameOrder() {
        var base = workspace.controller().document();
        var actor = new StageplayDocument.Actor("hero", "Hero", "s2", "sonic", List.of(
                new StageplayDocument.ActorKey(0, 50, 60, 0, -1, Facing.RIGHT, true, 1, StageplayDocument.Interpolation.LINEAR),
                new StageplayDocument.ActorKey(40, 90, 60, 0, 3, Facing.RIGHT, true, 1, StageplayDocument.Interpolation.HOLD)));
        workspace.controller().replace(base.withActors(List.of(actor))
                .withCaptions(List.of(new StageplayDocument.Caption("line", 15, 25, "Hello", 0, 0, -1)))
                .withAudio(List.of(new StageplayDocument.AudioCue("sound", 10, StageplayDocument.AudioKind.ROM_SFX, "s2", 1, "", 1))));
        input.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
        tick();
        assertEquals(List.of(0, 0, 10, 15, 40), hud.rows().stream().map(StageplayHud.Row::frame).toList());
        hud.rows().getLast().action().run();
        assertEquals(40, workspace.controller().frame());
        assertEquals("hero", workspace.controller().selectedActorId());
    }

    @Test
    void dialogueTimelineIdentifiesSpeakerAndSelectsThemAtTheLineStart() {
        workspace.invoke(StageplayWorkspace.Action.ADD_ACTOR); workspace.menu().setValue("name","Sonic"); workspace.menu().submit();
        String actor=workspace.controller().selectedActorId();
        workspace.controller().replace(workspace.controller().document().withDialogue(List.of(new StageplayDocument.DialogueCue(
                "speech",20,100,actor,"","Hello!","","",8,140,304,72,"",List.of()))));
        input.handleKeyEvent(GLFW_KEY_TAB,GLFW_PRESS); tick();
        var line=hud.rows().stream().filter(r -> r.label().equals("Dialogue / Sonic")).findFirst().orElseThrow();
        assertEquals("Hello!",line.detail()); line.action().run();
        assertEquals(20,workspace.controller().frame()); assertEquals(actor,workspace.controller().selectedActorId());
    }

    @Test
    void physicalActorUsesActionMenuAndTimelineSelectsItsInstructions() {
        press(BUTTON_Y); workspace.menu().submit();
        String actor=workspace.controller().selectedActorId();
        assertTrue(workspace.selectedActorIsPuppet());
        hud.reactivated(); tick(); press(BUTTON_X);
        assertEquals("Direct Sonic",workspace.menu().title()); workspace.menu().close();
        var doc=workspace.controller().document();
        workspace.controller().replace(doc.withPuppets(List.of(new StageplayDocument.PuppetTrack(actor,true,0,List.of(
                new StageplayDocument.PuppetCommand("move",10,StageplayDocument.PuppetCommandKind.MOVE_TO,300,112,.5,0),
                new StageplayDocument.PuppetCommand("jump",20,StageplayDocument.PuppetCommandKind.JUMP,0,0,0,12))))));
        input.handleKeyEvent(GLFW_KEY_TAB,GLFW_PRESS); tick();
        assertEquals(List.of(0,0,10,20),hud.rows().stream().map(StageplayHud.Row::frame).toList());
        var move=hud.rows().stream().filter(r -> r.label().endsWith("MOVE_TO")).findFirst().orElseThrow();
        assertTrue(move.detail().contains("50%")); move.action().run();
        assertEquals(10,workspace.controller().frame()); assertEquals(actor,workspace.controller().selectedActorId());
    }

    @Test
    void scrubMappingIncludesBothEndpointsAndClampsOutsideTheTrack() {
        var track = new EditorHudLayout.Rect(20, 10, 100, 18);
        assertEquals(0, StageplayHud.frameAt(-20, track, 101));
        assertEquals(50, StageplayHud.frameAt(70, track, 101));
        assertEquals(100, StageplayHud.frameAt(120, track, 101));
        assertEquals(100, StageplayHud.frameAt(300, track, 101));
        assertEquals(0, StageplayHud.frameAt(70, track, 1));
    }

    @Test
    void largeSceneFramesLeaveThePreviewAndAuthoringControlsWithinTheWindow() {
        var layout = StageplayHud.layout(1280, 720, 1920, 1080);
        assertTrue(layout.workbench().w() >= EditorHudLayout.MIN_WORKBENCH);
        assertTrue(layout.game().x() >= 0 && layout.game().right() <= layout.width());
        assertTrue(layout.game().y() >= layout.topBar().bottom());
        assertTrue(layout.game().bottom() <= layout.dock().y());
        assertTrue(layout.workbench().right() <= layout.width());
    }

    @Test
    void cameraModeOwnsArrowsCapturesKeysAndKeepsActorCommandsSeparate() {
        input.handleKeyEvent(GLFW_KEY_C,GLFW_PRESS); tick();
        assertTrue(workspace.cameraMode());
        input.handleKeyEvent(GLFW_KEY_C,GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);
        input.refreshLogicalSnapshot(); assertTrue(hud.update(input)); workspace.tick(input); input.update();
        assertEquals(0,workspace.controller().frame()); assertEquals(2,workspace.directorCamera().x());
        assertEquals(0,workspace.controller().document().camera().getFirst().x());
        input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_K,GLFW_PRESS); tick();
        assertFalse(workspace.menu().isOpen()); assertEquals(2,workspace.controller().document().camera().getFirst().x());
        input.handleKeyEvent(GLFW_KEY_K,GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_ESCAPE,GLFW_PRESS); tick();
        assertFalse(workspace.cameraMode()); assertTrue(workspace.active());
    }

    @Test
    void effectsAndSceneChangesAppearInTimelineAndCommands() {
        var doc = workspace.controller().document();
        doc = doc.withCamera(List.of(doc.camera().getFirst(),new StageplayDocument.CameraKey(100,20,30,1,StageplayDocument.Interpolation.HOLD)))
                .withCameraEffects(List.of(new StageplayDocument.CameraEffect("quake",20,60,2,4,8,1)))
                .withSceneChanges(List.of(new StageplayDocument.SceneChange("shot",100,doc.set(),StageplayDocument.Transition.FADE_BLACK,30)));
        workspace.controller().replace(doc);
        input.handleKeyEvent(GLFW_KEY_TAB,GLFW_PRESS); tick();
        assertEquals(List.of(0,20,100,100),hud.rows().stream().map(StageplayHud.Row::frame).toList());
        assertTrue(hud.rows().stream().anyMatch(row -> row.label().equals("Camera shake")));
        assertTrue(hud.rows().stream().anyMatch(row -> row.label().equals("Scene change")));
        input.handleKeyEvent(GLFW_KEY_TAB,GLFW_RELEASE);
        input.handleKeyEvent(GLFW_KEY_F1,GLFW_PRESS); tick();
        assertTrue(hud.rows().stream().anyMatch(row -> row.label().equals("Camera shake / quake")));
        assertTrue(hud.rows().stream().anyMatch(row -> row.label().equals("Scene changes / transitions")));
    }

    @Test
    void everyModesPromptsFitTheWholeBarAndTheTopBarNamesTheMode() {
        workspace.controller().replace(workspace.controller().document().withActors(List.of(new StageplayDocument.Actor(
                "hero", "Sonic", "s2", "sonic", List.of(new StageplayDocument.ActorKey(0, 100, 100, 5, -1, Facing.RIGHT,
                        true, 1, StageplayDocument.Interpolation.HOLD))))));
        workspace.selectActor("hero");
        assertPromptsFit("directing");
        assertEquals("DIRECTING", hud.modeChip().mode());
        input.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS); tick(); input.handleKeyEvent(GLFW_KEY_TAB, GLFW_RELEASE); tick();
        assertPromptsFit("timeline");
        input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS); tick(); input.handleKeyEvent(GLFW_KEY_F1, GLFW_RELEASE); tick();
        assertPromptsFit("commands");
        input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_PRESS); tick(); input.handleKeyEvent(GLFW_KEY_ESCAPE, GLFW_RELEASE); tick();
        workspace.invoke(StageplayWorkspace.Action.CAMERA_MODE);
        assertPromptsFit("camera");
        workspace.invoke(StageplayWorkspace.Action.RECORD_CAMERA);
        assertPromptsFit("camera take");
        workspace.stop();
        workspace.invoke(StageplayWorkspace.Action.CAMERA_MODE);
        workspace.invoke(StageplayWorkspace.Action.RECORD);
        assertTrue(workspace.recording());
        assertPromptsFit("poser take");
        workspace.stop();
        press(BUTTON_DPAD_UP); // a pad press switches the prompts to pad glyphs
        List<EditorHud.Prompt> pad = hud.prompts(false);
        assertTrue(pad.stream().allMatch(prompt -> prompt.glyph() != null), "pad prompts name pad buttons");
        assertPromptsFit("pad directing");
    }

    private void assertPromptsFit(String mode) {
        for (boolean fractional : new boolean[] {false, true}) {
            List<EditorHud.Prompt> prompts = hud.prompts(fractional);
            boolean pad = prompts.stream().allMatch(prompt -> prompt.glyph() != null) && prompts.stream()
                    .anyMatch(prompt -> prompt.key().isEmpty());
            for (float width : new float[] {640, 800}) {
                // Every prompt fits the whole bar; the mode chip lives in the top bar beside the exit
                // button, so every mode is named at every width and still leaves the scene title room.
                assertTrue(EditorHudPainter.promptFit(prompts, pad, width).total() <= width - 12,
                        mode + " prompts fit at " + width);
                float exit = EditorHudCanvas.textWidth("Level editor", EditorHudCanvas.Size.SMALL) + 16
                        + EditorHudPainter.keycapWidth("F4");
                float title = 16 + EditorHudCanvas.textWidth("STAGEPLAY", EditorHudCanvas.Size.BODY);
                float widestChip = EditorHudCanvas.textWidth("REC CAMERA TAKE", EditorHudCanvas.Size.SMALL) + 20;
                assertTrue(hud.modeChip().width() <= widestChip);
                assertTrue(width - 6 - exit - 6 - widestChip - title - 10 >= 200,
                        mode + " leaves the scene name 200 units beside the widest chip at " + width);
            }
        }
    }

    @Test
    void commandRowsShowTheirRealKeyboardShortcutsAndLabelsCarryNone() {
        input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS); tick();
        java.util.Map<String, String> keys = new java.util.HashMap<>();
        for (StageplayHud.Row row : hud.rows()) {
            assertFalse(row.label().matches(".*\\([A-Z]\\)$"), "no shortcut inside the label: " + row.label());
            keys.put(row.label(), row.key());
        }
        assertEquals("C", keys.get("Direct camera"));
        assertEquals("K", keys.get("Capture camera key"));
        assertEquals("K", keys.get("Direct actor / set key"));
        assertEquals("R", keys.get("Record camera take"));
        assertEquals("R", keys.get("Record actor take"));
        assertEquals("Ctrl S", keys.get("Save"));
        assertEquals("", keys.get("Audio cues"));
    }

    private void tick() {
        input.refreshLogicalSnapshot();
        assertTrue(hud.update(input), "Stageplay must prevent a second level-editor input pass");
        input.update();
    }

    private void press(int button) {
        buttons[button] = true;
        tick();
        buttons[button] = false;
        tick();
    }
}
