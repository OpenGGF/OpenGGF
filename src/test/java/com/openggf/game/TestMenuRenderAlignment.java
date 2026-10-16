package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.graphics.MenuPixelFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.mock;

/** Focus frames centre the glyph cells they hold, in Settings and Game Setup, at native and wide widths. */
class TestMenuRenderAlignment {
    @TempDir Path directory;

    @Test
    void settingsRowsCentreTheirGlyphCellsInTheFocusFrame() {
        for (int width : new int[] {320, 426}) {
            Capture capture = new Capture();
            SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
            EngineSettingsScreen screen = new EngineSettingsScreen(config, capture.font);
            InputHandler input = new InputHandler(InputBindingFactory.supplier(config), List::of);
            for (int row = 0; row < 3; row++) {
                capture.clear();
                screen.render(width);
                assertFocusHoldsCentredRow(capture, width);
                press(input, screen::update, GLFW_KEY_DOWN);
            }
        }
    }

    @Test
    void gameSetupRowsCentreTheirGlyphCellsInTheFocusFrame() {
        for (int width : new int[] {320, 426}) {
            Capture capture = new Capture();
            SonicConfigurationService config = SonicConfigurationService.createStandalone(directory);
            LaunchConfigPanel panel = new LaunchConfigPanel(MasterTitleScreen.GameEntry.SONIC_2,
                    LaunchProfile.stockFor(MasterTitleScreen.GameEntry.SONIC_2), new LaunchProfileStore(config),
                    config, capture.font, null);
            InputHandler input = new InputHandler(InputBindingFactory.supplier(config), List::of);
            for (int row = 0; row < 7; row++) {
                capture.clear();
                panel.render(width);
                assertFocusHoldsCentredRow(capture, width);
                press(input, panel::update, GLFW_KEY_DOWN);
            }
        }
    }

    private static void assertFocusHoldsCentredRow(Capture capture, int width) {
        assertFalse(capture.focus.isEmpty(), "a focused row draws its frame");
        Rect frame = capture.focus.getFirst();
        List<Line> inside = capture.lines.stream().filter(line -> line.x == 18
                && line.y >= frame.y && line.y + height(line) <= frame.y + frame.height).toList();
        assertFalse(inside.isEmpty(), "the focused row's label sits inside its frame");
        Line label = inside.getFirst();
        assertCentered(frame, label.y, height(label));
        assertTrue(frame.x + frame.width <= width - 8, "the frame clears the right edge");
    }

    private static int height(Line line) {
        return line.scale < 1 ? 8 : 10;
    }

    private static void press(InputHandler input, Consumer<InputHandler> update, int key) {
        input.handleKeyEvent(key, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        update.accept(input);
        input.update();
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.refreshLogicalSnapshot();
        update.accept(input);
        input.update();
    }

    private static void assertCentered(Rect rect, int textY, int textHeight) {
        int above = textY - rect.y;
        int below = rect.y + rect.height - textY - textHeight;
        assertTrue(above >= 1 && below >= 0, "Glyph cells must clear the focus border: " + above + "/" + below);
        assertTrue(Math.abs(above - below) <= 1, "Native cell padding must be balanced: " + above + "/" + below);
    }

    private record Line(String text, int x, int y, float scale) { }
    private record Rect(int x, int y, int width, int height) { }

    private static final class Capture {
        final List<Line> lines = new ArrayList<>();
        final List<Rect> focus = new ArrayList<>();
        final MenuPixelFont font = mock(MenuPixelFont.class, invocation -> {
            Object[] args = invocation.getArguments();
            if (invocation.getMethod().getName().equals("drawText") && args.length == 8) {
                lines.add(new Line((String) args[0], ((Number) args[1]).intValue(),
                        ((Number) args[2]).intValue(), ((Number) args[3]).floatValue()));
            } else if (invocation.getMethod().getName().equals("fillRect")
                    && (float) args[4] == MenuChrome.FOCUS_FILL[0] && (float) args[5] == MenuChrome.FOCUS_FILL[1]) {
                focus.add(new Rect((int) args[0], (int) args[1], (int) args[2], (int) args[3]));
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        void clear() { lines.clear(); focus.clear(); }
    }
}
