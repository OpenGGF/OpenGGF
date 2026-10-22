package com.openggf.editor.project;

import com.openggf.Engine;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.editor.EditorCommandPalette;
import com.openggf.editor.EditorInputHandler;
import com.openggf.editor.EditorWindowChrome;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.hud.EditorHudRuntime;
import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.editor.stageplay.StageplayWorkspace;
import com.openggf.stageplay.runtime.StageplayRenderer;
import com.openggf.stageplay.runtime.StageplayResources;
import com.openggf.stageplay.editor.StageplayEditorSession;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ROM-backed Stageplay HUD review images, with no desktop or focus interaction.
 * Inputs: S2 ROM, surfaceless EGL, -Dopenggf.stageplay.hudPictures=true.
 * Outputs: target/stageplay-hud/*.png. Origin: 2026-10-21 Stageplay authoring task.
 */
@Isolated
class TestStageplayHudPictures {
    @Test
    void captureObjectCastArenaRestrictionAndModeForm() throws Exception {
        assumeTrue(Boolean.getBoolean("openggf.stageplay.hudPictures"), "Explicit Stageplay HUD picture refresh only");
        assumeTrue(PresentationOffscreenTestContext.requested(), "Stageplay HUD pictures require display-free EGL");
        TestEnvironment.resetAll();
        try (var fixture = TestEditorProjectLifecycle.Fixture.open(GameId.S2);
             var context = PresentationOffscreenTestContext.open(1280,720);
             var renderer = new EditorProjectMenuRenderer()) {
            var workspace = new StageplayWorkspace(() -> StageplayDocument.empty("s2",0,1,0,0,320,224));
            workspace.activate(); workspace.invoke(StageplayWorkspace.Action.OBJECTS);
            renderObjectMenu(renderer,workspace); capture("object-cast-commands",1280,720);
            objectMenuChoose(workspace,0);
            workspace.menu().setValue("name","EHZ boss"); workspace.menu().setValue("object","86");
            workspace.menu().submit(); assertNull(workspace.menu().error());
            renderObjectMenu(renderer,workspace); capture("object-arena-restriction",1280,720);
            objectMenuChoose(workspace,1); objectMenuChoose(workspace,0);
            renderObjectMenu(renderer,workspace); capture("object-mode-key",1280,720);
        } finally {
            SessionManager.clear(); RomManager.getInstance().setRom(null); Engine.clearGlobalInstance();
            TestEnvironment.resetAll(); SonicConfigurationService.getInstance().resetToDefaults(); GameModuleRegistry.reset();
        }
    }

    private static void renderObjectMenu(EditorProjectMenuRenderer renderer,StageplayWorkspace workspace) {
        glViewport(0,0,1280,720); glDisable(GL_SCISSOR_TEST); glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
        glViewport(160,24,960,672); renderer.render(workspace.menu(),SceneSurface.projection(320,224),320);
        assertEquals(GL_NO_ERROR,glGetError());
    }

    private static void objectMenuChoose(StageplayWorkspace workspace,int row) {
        var input = new InputHandler();
        for (int i=0;i<row;i++) objectMenuPress(workspace,input,GLFW_KEY_DOWN);
        objectMenuPress(workspace,input,GLFW_KEY_ENTER);
    }

    private static void objectMenuPress(StageplayWorkspace workspace,InputHandler input,int key) {
        input.handleKeyEvent(key,GLFW_PRESS); input.refreshLogicalSnapshot(); workspace.menu().update(input); input.update();
        input.handleKeyEvent(key,GLFW_RELEASE); input.update();
    }

    @Test
    void captureStageplayCastTimelineAndCommands() throws Exception {
        assumeTrue(Boolean.getBoolean("openggf.stageplay.hudPictures"), "Explicit Stageplay HUD picture refresh only");
        assumeTrue(PresentationOffscreenTestContext.requested(), "Stageplay HUD pictures require display-free EGL");
        TestEnvironment.resetAll();
        try (var fixture = TestEditorProjectLifecycle.Fixture.open(GameId.S2);
             var context = PresentationOffscreenTestContext.open(1600, 900);
             var library = new SceneRomLibrary(fixture.module, fixture.rom, RomManager.getInstance());
             var resources = new StageplayResources(library, false);
             var surface = new SceneSurface();
             var chrome = new EditorHudRuntime()) {
            int[] start = fixture.module.getZoneRegistry().getStartPosition(0, 0);
            double x = Math.max(0, start[0] - 80), y = Math.max(0, start[1] - 140);
            var scene = StageplayDocument.empty("s2", 0, 0, x, y, 384, 216).withName("The next adventure")
                    .withActors(List.of(
                            new StageplayDocument.Actor("hero", "Sonic", "s2", "sonic", List.of(
                                    key(0, x + 80, start[1], -1), key(120, x + 160, start[1], 1), key(240,x+80,start[1],-1))),
                            new StageplayDocument.Actor("friend", "Tails", "s2", "tails", List.of(key(0, x + 220, start[1], -1),key(240,x+220,start[1],-1)))))
                    .withCamera(List.of(new StageplayDocument.CameraKey(0,x,y,1,StageplayDocument.Interpolation.LINEAR),
                            new StageplayDocument.CameraKey(240,x,y,1,StageplayDocument.Interpolation.HOLD)))
                    .withCameraEffects(List.of(new StageplayDocument.CameraEffect("quake",30,90,2,4,8,7)))
                    .withSceneChanges(List.of(new StageplayDocument.SceneChange("next-shot",240,
                            new StageplayDocument.SetSource("s2",0,1,"",""),StageplayDocument.Transition.FADE_BLACK,30)))
                    .withCaptions(List.of(new StageplayDocument.Caption("greeting", 0, 180, "Ready for another adventure?", 12, 190, -1)))
                    .withAudio(List.of(new StageplayDocument.AudioCue("music", 0, StageplayDocument.AudioKind.ROM_MUSIC, "s2", 129, "", 1)))
                        .withDialogueTypers(List.of(new StageplayDocument.DialogueTyper("conversation",3,8,0)))
                        .withDialogueSkins(List.of(new StageplayDocument.DialogueSkin("warm",0xfff5f0df,0xffffcf66,
                                0xff172338,0xffa8b8cf,6,"",0,true)))
                        .withDialogue(List.of(new StageplayDocument.DialogueCue("spoken-greeting",0,180,"hero","",
                                "Tails, ready for another adventure?","warm","conversation",12,8,360,62,"",List.of())));
            var line = StageplayTimeline.sample(scene,60).dialogue().getFirst();
            assertEquals("Sonic",line.speakerName());
            assertFalse(line.visibleText().isEmpty());
            var workspace = new StageplayWorkspace(() -> scene);
            workspace.activate();
            workspace.selectActor("hero");
            workspace.seek(60);
            var levelController = new LevelEditorController();
            chrome.init(new EditorWindowChrome.Host() {
                public LevelEditorController controller() { return levelController; }
                public void dispatch(EditorInputHandler.Action action) { fail("Stageplay must not dispatch level edits"); }
                public List<EditorCommandPalette.HostCommand> commands() { return List.of(); }
                public StageplayWorkspace stageplay() { return workspace; }
            }, 1);
            var renderer = new StageplayRenderer(resources);
            for (int frame : new int[] {0, 60, 120}) for (var actor : StageplayTimeline.sample(scene, frame).actors()) {
                var sprite = resources.character(actor.gameId(), actor.character()).frame(renderer.mappingFrame(actor));
                assertTrue(java.util.Arrays.stream(sprite.image().pixels()).anyMatch(pixel -> (pixel >>> 24) != 0),
                        "The pictured cast must resolve visible ROM artwork: " + actor.actorId() + " at frame " + frame);
            }
            surface.record(scene.width(), scene.height(), canvas -> renderer.draw(scene, StageplayTimeline.sample(scene, 60), canvas));
            assertTrue(surface.recordedFrame().size() > 10, "The picture must contain the ROM set and cast");
            assertTrue(chrome.setActive(true));
            var input = new InputHandler();
            assertTrue(chrome.update(input));
            render(chrome, surface, scene, 1600, 900);
            capture("cast", 1600, 900);
            input.handleKeyEvent(GLFW_KEY_TAB, GLFW_PRESS);
            input.refreshLogicalSnapshot();
            assertTrue(chrome.update(input));
            input.update();
            input.handleKeyEvent(GLFW_KEY_TAB, GLFW_RELEASE);
            render(chrome, surface, scene, 1600, 900);
            capture("timeline", 1600, 900);
            input.handleKeyEvent(GLFW_KEY_F1, GLFW_PRESS);
            input.refreshLogicalSnapshot();
            assertTrue(chrome.update(input));
            render(chrome, surface, scene, 1280, 720);
            capture("commands-720p", 1280, 720);
            input.update(); input.handleKeyEvent(GLFW_KEY_F1,GLFW_RELEASE); input.update();
            input.handleKeyEvent(GLFW_KEY_F1,GLFW_PRESS); input.refreshLogicalSnapshot(); chrome.update(input); input.update();
            workspace.invoke(StageplayWorkspace.Action.CAMERA_MODE);
            workspace.moveCamera(8,0,2);
            surface.record(scene.width(),scene.height(),canvas -> renderer.draw(workspace.previewDocument(),workspace.previewFrame(),canvas));
            render(chrome,surface,scene,1280,720); capture("camera-720p",1280,720);
            render(chrome,surface,scene,1024,576); capture("camera-small",1024,576);
        } finally {
            SessionManager.clear();
            RomManager.getInstance().setRom(null);
            Engine.clearGlobalInstance();
            TestEnvironment.resetAll();
            SonicConfigurationService.getInstance().resetToDefaults();
            GameModuleRegistry.reset();
        }
    }

    private static StageplayDocument.ActorKey key(int frame, double x, double y, int pose) {
        // S2 mapping frame zero is empty. WAIT (5) resolves the shipped standing frames.
        return new StageplayDocument.ActorKey(frame, x, y, 5, pose, false, true, 1, StageplayDocument.Interpolation.LINEAR);
    }

    private static void render(EditorHudRuntime chrome, SceneSurface surface, StageplayDocument scene, int width, int height) {
        int[] viewport = chrome.gameViewport(width, height, width, height, 320, 224);
        glViewport(0, 0, width, height);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        int[] fitted = StageplayEditorSession.fitViewport(viewport,scene.width(),scene.height());
        glViewport(fitted[0], fitted[1], fitted[2], fitted[3]);
        surface.render(SceneSurface.projection(scene.width(), scene.height()), fitted);
        chrome.render(width, height, width, height);
        assertEquals(GL_NO_ERROR, glGetError());
    }

    private static void capture(String name, int width, int height) throws Exception {
        glFinish();
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int at = ((height - y - 1) * width + x) * 4;
                image.setRGB(x, y, (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255);
            }
            Path output = Path.of("target", "stageplay-hud", name + ".png");
            Files.createDirectories(output.getParent());
            assertTrue(ImageIO.write(image, "png", output.toFile()));
            assertEquals(GL_NO_ERROR, glGetError());
        } finally { MemoryUtil.memFree(pixels); }
    }
}
