package com.openggf.editor.render;

import com.openggf.game.GameServices;
import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Real ROM terrain across the editor's expanded view, with native camera/state restoration. */
class TestEditorCanvasWorldRendering {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1ExpandedCanvas() throws Exception { expandedCanvas("sonic1.rom.path"); }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2ExpandedCanvas() throws Exception { expandedCanvas("sonic2.rom.path"); }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kExpandedCanvas() throws Exception { expandedCanvas("s3k.rom.path"); }

    private void expandedCanvas(String romProperty) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
            session.renderFrame();
            var graphics = GameServices.graphics();
            var fade = GameServices.fade();
            var editor = SessionManager.enterEditorMode(new EditorCursorState(0, 0));
            var level = editor.getLevelManager();
            var camera = editor.getCamera();
            graphics.bindRuntimeManagedReferences(camera, fade);
            camera.setX((short) 0); camera.setY((short) 0); camera.captureRenderCopy();
            var before = camera.capture();
            int frameBefore = level.getFrameCounter();
            try (var pass = new EditorCanvasRenderPass()) {
                for (float zoom : new float[]{.5f, .25f}) {
                    var origins = new HashSet<String>();
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    pass.renderWorld(graphics, level, camera, zoom, 320, 224, () -> {
                        origins.add(camera.getX() + ":" + camera.getY());
                        level.drawCinematicWorld(editor.getSpriteManager());
                        graphics.flush();
                        // A far-world marker must remain visible beyond the original native view.
                        if (camera.getX() == (int) (320 / zoom) - 320
                                && camera.getY() == (int) (224 / zoom) - 224) {
                            glEnable(GL_SCISSOR_TEST); glScissor(100, 80, 64, 64);
                            glClearColor(0, 1, 1, 1); glClear(GL_COLOR_BUFFER_BIT);
                            glDisable(GL_SCISSOR_TEST);
                        }
                    });
                    assertEquals((int) (1 / (zoom * zoom)), origins.size());
                    assertTrue(origins.contains("320:224"), "view extends in both world axes");
                    assertEquals(before, camera.capture(), "drawing must restore the authoring camera");
                    assertNull(camera.getFocusedSprite(), "the editor camera must remain playerless");
                    assertEquals(frameBefore, level.getFrameCounter(), "zooming must not tick gameplay");
                    var pixels = BufferUtils.createByteBuffer(320 * 224 * 4);
                    glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                    var colors = new HashSet<Integer>();
                    for (int y = 0; y < 224; y++) for (int x = 0; x < 160; x++)
                        colors.add(pixels.getInt((y * 320 + x) * 4));
                    assertTrue(colors.size() > 1, "the ROM world must contribute a nonuniform picture");
                    int x = (int) (320 - 192 * zoom), y = (int) (112 * zoom);
                    int index = (y * 320 + x) * 4;
                    assertEquals(0, pixels.get(index) & 255);
                    assertEquals(255, pixels.get(index + 1) & 255);
                    assertEquals(255, pixels.get(index + 2) & 255);
                    assertEquals(GL_NO_ERROR, glGetError());
                }
                assertThrows(IllegalStateException.class, () -> pass.renderWorld(graphics, level, camera,
                        .25f, 320, 224, () -> { throw new IllegalStateException("producer fault"); }));
                assertEquals(before, camera.capture(), "failed expanded drawing also restores the camera");
            }
        }
    }
}
