package com.openggf.editor.hud;

import com.openggf.control.InputHandler;
import com.openggf.editor.LevelEditorController;
import com.openggf.editor.route.RouteTapeFixtures;
import com.openggf.editor.route.RouteTapeJob;
import com.openggf.editor.route.RouteTapeOverlay;
import com.openggf.editor.route.RouteTapeTimeline;
import com.openggf.editor.route.RouteTapeWorkspace;
import com.openggf.route.LevelId;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerSlot;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * The controller-first route tape HUD: keys reach the shared workspace, and an offscreen
 * surfaceless-EGL frame paints the replayed path over the level view and both pads' rows in the
 * timeline. Origin: 2026-11-02 route editor support. Pictures go to target/route-hud, or the
 * directory named by openggf.routes.hudPictureDir.
 */
@Isolated
class TestRouteHud {
    private static final int WIDTH = 1280, HEIGHT = 720;

    @TempDir
    Path dir;

    private static final class Host implements RouteTapeWorkspace.Host {
        RouteTapeJob.Update update;

        @Override
        public RouteTapeJob.Update pollJob() {
            return update;
        }

        @Override
        public Optional<LevelId> currentLevel() {
            return Optional.of(new LevelId("s2", 0, 0));
        }
    }

    private RouteTapeWorkspace workspace(Host host) throws Exception {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "40 R\n6 R+A\n30 R\n20 R / R\n4 - / C\n");
        var workspace = new RouteTapeWorkspace(host);
        workspace.open(manifest);
        var p1 = new ArrayList<RouteTapeOverlay.Point>();
        var p2 = new ArrayList<RouteTapeOverlay.Point>();
        for (int row = 0; row < 100; row++) {
            boolean air = row >= 40 && row < 70;
            int y = air ? 150 - (int) (40 * Math.sin((row - 40) * Math.PI / 30)) : 150;
            p1.add(new RouteTapeOverlay.Point(row, 0, 40 + 2 * row, y, air));
            p2.add(new RouteTapeOverlay.Point(row, 0, 20 + 2 * row, 152, false));
        }
        host.update = new RouteTapeJob.Update(1, RouteTapeJob.Phase.FAILED, RouteTapeJob.Kind.REPLAY, 2, 2, 100, 100,
                "fell", workspace.workingContent(), 80, new RouteTapeOverlay(p1, p2, 1, 80), null);
        workspace.tick(new InputHandler());
        assertEquals(RouteTapeWorkspace.Qualification.NOT_QUALIFIED, workspace.qualification());
        return workspace;
    }

    private static void key(RouteHud hud, InputHandler input, int key, int... held) {
        for (int modifier : held) input.handleKeyEvent(modifier, GLFW_PRESS);
        input.handleKeyEvent(key, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        assertTrue(hud.update(input));
        input.update();
        input.handleKeyEvent(key, GLFW_RELEASE);
        for (int modifier : held) input.handleKeyEvent(modifier, GLFW_RELEASE);
        input.refreshLogicalSnapshot();
        hud.update(input);
        input.update();
    }

    @Test
    void everyShownKeyboardPromptReachesTheSharedWorkspace() throws Exception {
        var host = new Host();
        var workspace = workspace(host);
        workspace.activate();
        var hud = new RouteHud(workspace, new LevelEditorController(), () -> new int[] {0, 0});
        var input = new InputHandler();
        assertEquals(List.of("Left / Right", "Up / Down", "Space", "M", "F1"),
                hud.prompts().stream().map(EditorHud.Prompt::key).toList());
        workspace.setCursor(10);
        key(hud, input, GLFW_KEY_RIGHT);
        assertEquals(11, workspace.cursor());
        key(hud, input, GLFW_KEY_LEFT, GLFW_KEY_LEFT_SHIFT);
        assertEquals(0, workspace.cursor(), "Shift steps a second of rows, clamped at the start");
        int track = workspace.track();
        key(hud, input, GLFW_KEY_DOWN);
        assertEquals(track + 1, workspace.track());
        var focused = RouteTapeTimeline.TRACKS.get(workspace.track());
        assertEquals(new RouteTapeTimeline.Track(PlayerSlot.P1, PhysicalButton.DOWN), focused);
        key(hud, input, GLFW_KEY_M);
        assertEquals(0, workspace.mark());
        key(hud, input, GLFW_KEY_RIGHT);
        key(hud, input, GLFW_KEY_RIGHT);
        key(hud, input, GLFW_KEY_SPACE);
        for (int row = 0; row < 4; row++) {
            assertEquals(row <= 2, workspace.tape().row(row).held(PlayerSlot.P1, PhysicalButton.DOWN), "row " + row);
        }
        key(hud, input, GLFW_KEY_ESCAPE);
        assertEquals(-1, workspace.mark(), "Back first clears a marked selection");
        key(hud, input, GLFW_KEY_Z, GLFW_KEY_LEFT_CONTROL);
        assertFalse(workspace.dirty());
        key(hud, input, GLFW_KEY_F1);
        assertTrue(workspace.menu().isOpen(), "F1 opens the route menu");
        workspace.menu().close();
        key(hud, input, GLFW_KEY_ESCAPE);
        assertFalse(workspace.active(), "Back leaves for the level editor");
    }

    @Test
    void offscreenFrameDrawsThePathOverTheLevelViewAndBothPadsRows() throws Exception {
        var host = new Host();
        var workspace = workspace(host);
        workspace.activate();
        workspace.setTrack(RouteTapeTimeline.TRACKS.indexOf(new RouteTapeTimeline.Track(PlayerSlot.P1, PhysicalButton.A)));
        workspace.setCursor(42);
        var hud = new RouteHud(workspace, new LevelEditorController(), () -> new int[] {0, 0});
        var canvas = new EditorHudCanvas();
        try (var context = PresentationOffscreenTestContext.openConfigured(WIDTH, HEIGHT,
                PresentationOffscreenTestContext.LegacyProfile.DEFAULT)) {
            canvas.init();
            try {
                var layout = RouteHud.layout(WIDTH, HEIGHT, 320, 224);
                glViewport(0, 0, WIDTH, HEIGHT);
                glClearColor(0, 0, 0, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                canvas.begin(WIDTH, HEIGHT, layout.uiScale());
                hud.draw(canvas, layout);
                canvas.end();
                BufferedImage image = read();
                Path output = Path.of(System.getProperty("openggf.routes.hudPictureDir", "target/route-hud"),
                        "route-hud.png").toAbsolutePath();
                Files.createDirectories(output.getParent());
                assertTrue(ImageIO.write(image, "png", output.toFile()));
                assertEquals(GL_NO_ERROR, glGetError());

                int scale = layout.uiScale();
                var game = layout.game();
                assertTrue(count(image, game, scale, RouteTapeOverlay.GROUND) > 20, "Ground path pixels in the level view");
                assertTrue(count(image, game, scale, RouteTapeOverlay.AIR) > 20, "Airborne path pixels in the level view");
                assertTrue(count(image, game, scale, RouteTapeOverlay.SIDEKICK) > 20, "P2 path pixels in the level view");
                var dock = layout.dock();
                assertTrue(count(image, dock, scale, EditorHudTheme.ACCENT) > 200, "Held direction runs in the timeline");
                assertTrue(count(image, dock, scale, EditorHudTheme.GOLD) > 20, "Held action runs and the cursor");
                assertTrue(count(image, dock, scale, EditorHudTheme.WARN) > 20, "The diverging row is marked");
                assertEquals(0, count(image, game, scale, EditorHudTheme.WARN), "No timeline marker leaks into the level view");
            } finally {
                canvas.close();
            }
        }
    }

    private static int count(BufferedImage image, EditorHudLayout.Rect rect, int scale, int rgb) {
        int hits = 0;
        int x0 = Math.max(0, (int) (rect.x() * scale)), y0 = Math.max(0, (int) (rect.y() * scale));
        int x1 = Math.min(image.getWidth(), (int) (rect.right() * scale)), y1 = Math.min(image.getHeight(), (int) (rect.bottom() * scale));
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int pixel = image.getRGB(x, y) & 0xFFFFFF;
            if (Math.abs((pixel >> 16) - (rgb >> 16)) <= 6 && Math.abs((pixel >> 8 & 255) - (rgb >> 8 & 255)) <= 6
                    && Math.abs((pixel & 255) - (rgb & 255)) <= 6) hits++;
        }
        return hits;
    }

    private static BufferedImage read() {
        glFinish();
        ByteBuffer pixels = MemoryUtil.memAlloc(WIDTH * HEIGHT * 4);
        try {
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                int offset = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
                image.setRGB(x, y, (pixels.get(offset) & 255) << 16 | (pixels.get(offset + 1) & 255) << 8
                        | pixels.get(offset + 2) & 255);
            }
            return image;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }
}
