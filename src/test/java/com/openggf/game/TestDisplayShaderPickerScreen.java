package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputHandler;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.shaderlib.DisplayShaderPickerController;
import com.openggf.graphics.shaderlib.DisplayShaderPresetRef;
import com.openggf.graphics.shaderlib.DisplayShaderSelectionModel;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextFace;
import com.openggf.graphics.text.TextLanguage;
import com.openggf.i18n.UiMessages;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestDisplayShaderPickerScreen {
    private static final String ROOT = "/long-library-root/" + "same-prefix/".repeat(12) + "ROOTTAIL";
    private static final String STATUS = "Download failed: " + "transport detail ".repeat(40) + "ERRTAIL";
    private static final int PICKER_KEY = GLFW_KEY_BACKSLASH;

    @Test
    void readerExposesEveryWrappedFragmentOfDeepPathsQueryAndError() throws Exception {
        String path = "crt/" + "same-prefix-".repeat(14) + "ITEMTAIL.glslp";
        var current = preset(path);
        var picker = picker(current);
        picker.update(input(PICKER_KEY), current);
        String query = "a".repeat(160) + "QUERYTAIL e\u0301 😀";
        seedQuery(picker, query);
        String full = DisplayShaderPickerScreen.fullText(picker, ROOT, STATUS);
        assertTrue(full.contains(ROOT + "/crt"));
        assertTrue(full.contains(ROOT + "/" + path));
        assertTrue(full.contains(query));
        assertTrue(full.contains(STATUS));
        assertTrue(full.contains(DisplayShaderPickerController.downloadHintText()));
        var screen = new DisplayShaderPickerScreen();
        assertNone(screen.update(input(GLFW_KEY_F1), picker, current, ROOT, STATUS, 320));
        var font = new RecordingFont();
        var seen = new HashSet<String>();
        for (int i = 0; i < 200; i++) {
            font.draws.clear();
            screen.render(font, 320, picker, ROOT, STATUS, false);
            font.draws.forEach(draw -> seen.add(draw.text()));
            assertNone(screen.update(input(GLFW_KEY_DOWN), picker, current, ROOT, STATUS, 320));
        }
        var expected = HostText.shared().wrap(MenuDetailsScreen.readable(full), 290,
                TextFace.COMPACT, HostTextContext.current()).stream().filter(s -> !s.isBlank()).toList();
        assertFalse(expected.isEmpty());
        for (String fragment : expected) assertTrue(seen.contains(fragment), "Unreadable reader fragment: " + fragment);
        assertEquals(current, picker.selectedItem().ref());
        assertTrue(picker.isOpen());
    }

    @Test
    void readerConsumesNavigationTypingDownloadPickerToggleAndCloseWithoutActivating() {
        var current = preset("crt/first.glslp");
        var picker = picker(current, preset("crt/second.glslp"));
        picker.update(input(PICKER_KEY), current);
        var selected = picker.selectedItem();
        var screen = new DisplayShaderPickerScreen();
        assertNone(screen.update(input(GLFW_KEY_F1, GLFW_KEY_ENTER, GLFW_KEY_F5), picker, current, ROOT, STATUS, 560));
        for (int key : new int[]{GLFW_KEY_DOWN, GLFW_KEY_UP, GLFW_KEY_LEFT, GLFW_KEY_RIGHT,
                GLFW_KEY_F5, PICKER_KEY, GLFW_KEY_A, GLFW_KEY_BACKSPACE}) {
            assertNone(screen.update(input(key), picker, current, ROOT, STATUS, 560));
            assertSame(selected, picker.selectedItem());
            assertEquals("", picker.query());
            assertTrue(picker.isOpen());
        }
        assertNone(screen.update(input(GLFW_KEY_ENTER, GLFW_KEY_F5), picker, current, ROOT, STATUS, 560));
        assertSame(selected, picker.selectedItem());
        assertTrue(picker.isOpen(), "Reader confirm closes only the reader");
        screen.update(input(GLFW_KEY_F1), picker, current, ROOT, STATUS, 560);
        assertNone(screen.update(input(GLFW_KEY_ESCAPE, GLFW_KEY_ENTER), picker, current, ROOT, STATUS, 560));
        assertTrue(picker.isOpen(), "Reader Back closes only the reader");
        assertSame(selected, picker.selectedItem());
        assertEquals(DisplayShaderPickerController.ActionType.DOWNLOAD_LIBRETRO_GLSL,
                screen.update(input(GLFW_KEY_F5), picker, current, ROOT, STATUS, 560).type(),
                "Normal picker actions resume on a later frame");
    }

    @Test
    void controllerDetailsIsAdvertisedAndConfirmCannotActivateThePickerBehindIt() {
        var config = SonicConfigurationService.createStandalone();
        config.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        config.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER2, "none");
        var devices = new java.util.concurrent.atomic.AtomicReference<List<GamepadStateSource.DeviceState>>(List.of());
        var input = new InputHandler(InputBindingFactory.supplier(config), devices::get);
        devices.set(List.of(device()));
        input.refreshLogicalSnapshot(); input.update();
        var current = preset("crt/first.glslp");
        var picker = picker(current);
        picker.update(input(PICKER_KEY), current);
        var screen = new DisplayShaderPickerScreen();
        devices.set(List.of(device(GLFW_GAMEPAD_BUTTON_Y, GLFW_GAMEPAD_BUTTON_A)));
        input.refreshLogicalSnapshot();
        assertNone(screen.update(input, picker, current, ROOT, STATUS, 320));
        assertTrue(picker.isOpen());
        input.update(); devices.set(List.of(device())); input.refreshLogicalSnapshot();
        screen.update(input, picker, current, ROOT, STATUS, 320);
        input.update(); devices.set(List.of(device(GLFW_GAMEPAD_BUTTON_A))); input.refreshLogicalSnapshot();
        assertNone(screen.update(input, picker, current, ROOT, STATUS, 320));
        assertTrue(picker.isOpen(), "Controller confirm dismisses only the reader");
        var font = new RecordingFont();
        screen.render(font, 320, picker, ROOT, STATUS, false);
        assertTrue(font.draws.stream().anyMatch(draw -> draw.text().equals(
                "Y " + UiMessages.text("menu.path.details_caps"))));
        assertEquals(current, picker.selectedItem().ref());
    }

    @Test
    void boundedPreviewsPageCompletelyAndKeepTheDetailsHintInsideTallLanguageRows() throws Exception {
        var previous = HostTextContext.current();
        try {
            for (String tag : List.of("en", "ja", "th", "ar")) {
                HostTextContext.activate(TextLanguage.of(tag));
                var language = HostTextContext.current();
                var current = preset("crt/" + "very-long-name-".repeat(9) + "ITEMTAIL.glslp");
                var picker = picker(current);
                picker.update(input(PICKER_KEY), current);
                String query = "a".repeat(150) + "QUERYTAIL e\u0301";
                seedQuery(picker, query);
                var screen = new DisplayShaderPickerScreen();
                var font = new RecordingFont();
                var seen = new HashSet<String>();
                for (int tick = 0; tick < 90 * 20; tick++) {
                    screen.update(null, picker, current, ROOT, STATUS, 320);
                    if (tick % 90 != 0) continue;
                    font.draws.clear();
                    screen.render(font, 320, picker, ROOT, STATUS, true);
                    var metrics = HostText.shared().lineMetrics(TextFace.COMPACT, language);
                    int previousY = -100;
                    for (var draw : font.draws) {
                        assertTrue(draw.x() >= 0);
                        assertTrue(draw.x() + HostText.shared().width(draw.text(), TextFace.COMPACT, language) <= 320,
                                "Preview crosses the content frame in " + tag + ": " + draw.text());
                        assertTrue(draw.y() - metrics.above() >= 0);
                        assertTrue(draw.y() + metrics.cellHeight() + metrics.below() <= 224);
                        if (draw.x() != 18 && !draw.text().equals(">")) {
                            assertTrue(draw.y() - previousY >= metrics.lineHeight(), "Stacked text overlaps in " + tag);
                            previousY = draw.y();
                        }
                        seen.add(draw.text());
                    }
                }
                for (String source : List.of(DisplayShaderPickerScreen.folderText(picker, ROOT), query,
                        current.relativePath().substring(4), STATUS, DisplayShaderPickerController.downloadHintText(),
                        "F1 " + UiMessages.text("menu.path.details_caps"))) {
                    int width = source.equals(current.relativePath().substring(4)) ? 296 : 308;
                    for (String fragment : HostText.shared().wrap(source, width, TextFace.COMPACT, language))
                        assertTrue(seen.contains(fragment), "Missing paged fragment in " + tag + ": " + fragment);
                }
            }
        } finally { HostTextContext.activate(previous); }
    }

    @Test
    void rootSeparatorAndParentDirectoryDetailsPreserveTheirCompletePaths() {
        var current = preset("crt/nested/preset.glslp");
        var picker = picker(current);
        picker.update(input(PICKER_KEY), current);
        assertEquals("/crt/nested", DisplayShaderPickerScreen.folderText(picker, "/"));
        picker.update(input(GLFW_KEY_UP), current);
        assertTrue(picker.selectedItem().parentDirectory());
        assertTrue(DisplayShaderPickerScreen.fullText(picker, ROOT, "").contains(ROOT + "/crt\n"));
    }

    @Test
    void unmatchedFilterKeepsParentNavigationAndTheReaderWithoutApplyingAPreset() throws Exception {
        var current = preset("crt/preset.glslp");
        var picker = picker(current);
        picker.update(input(PICKER_KEY), current);
        seedQuery(picker, "no-matching-filter");
        picker.update(input(GLFW_KEY_Z), current);
        var screen = new DisplayShaderPickerScreen();
        var font = new RecordingFont();
        assertTrue(picker.selectedItem().parentDirectory(), "An unmatched filter retains the parent navigation row");
        assertNone(screen.update(input(GLFW_KEY_ENTER), picker, current, ROOT, STATUS, 800));
        assertDoesNotThrow(() -> screen.render(font, 800, picker, ROOT, STATUS, false));
        assertNone(screen.update(input(GLFW_KEY_F1), picker, current, ROOT, STATUS, 800));
        assertDoesNotThrow(() -> screen.render(font, 800, picker, ROOT, STATUS, false));
        assertTrue(screen.reading());
        assertTrue(picker.isOpen());
    }

    private static void seedQuery(DisplayShaderPickerController picker, String query) throws Exception {
        // Legacy picker has no IME text-session owner; preserve its keyboard contract here.
        var field = DisplayShaderPickerController.class.getDeclaredField("query");
        field.setAccessible(true);
        field.set(picker, query);
    }

    private static GamepadStateSource.DeviceState device(int... pressed) {
        boolean[] buttons = new boolean[GLFW_GAMEPAD_BUTTON_LAST + 1];
        for (int button : pressed) buttons[button] = true;
        return GamepadStateSource.DeviceState.connected(0, "Xbox shader picker test", buttons, 0, 0);
    }

    private static InputHandler input(int... keys) {
        var input = new InputHandler();
        for (int key : keys) input.handleKeyEvent(key, GLFW_PRESS);
        input.refreshLogicalSnapshot();
        return input;
    }

    private static DisplayShaderPresetRef preset(String path) {
        return new DisplayShaderPresetRef(DisplayShaderPresetRef.Kind.GLSLP, path, Path.of("shaders").resolve(path));
    }

    private static DisplayShaderPickerController picker(DisplayShaderPresetRef... refs) {
        var entries = new ArrayList<DisplayShaderPresetRef>();
        entries.add(DisplayShaderPresetRef.OFF);
        entries.addAll(List.of(refs));
        return new DisplayShaderPickerController(new DisplayShaderSelectionModel(entries), PICKER_KEY);
    }

    private static void assertNone(DisplayShaderPickerController.Action action) {
        assertEquals(DisplayShaderPickerController.ActionType.NONE, action.type());
        assertNull(action.ref());
    }

    private record Draw(String text, int x, int y) { }
    private static final class RecordingFont extends PixelFont {
        private final List<Draw> draws = new ArrayList<>();
        @Override public void drawText(String text, int x, int y, float scale, float r, float g, float b, float a) {
            draws.add(new Draw(text, x, y));
        }
    }
}
