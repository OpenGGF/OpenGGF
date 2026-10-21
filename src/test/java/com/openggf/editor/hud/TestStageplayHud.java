package com.openggf.editor.hud;

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

    @Test
    void controllerCreatesActorAndEditsAKeyThroughTheSharedForms() {
        press(BUTTON_Y);
        assertEquals("Add actor", workspace.menu().title());
        workspace.menu().setValue("name", "Hero");
        workspace.menu().submit();
        assertEquals("Hero", workspace.controller().document().actors().getFirst().name());

        workspace.seek(20);
        hud.reactivated();
        tick();
        press(BUTTON_X);
        assertEquals("Actor key at frame 20", workspace.menu().title());
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
                new StageplayDocument.ActorKey(0, 50, 60, 0, -1, false, true, 1, StageplayDocument.Interpolation.LINEAR),
                new StageplayDocument.ActorKey(40, 90, 60, 0, 3, false, true, 1, StageplayDocument.Interpolation.HOLD)));
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
