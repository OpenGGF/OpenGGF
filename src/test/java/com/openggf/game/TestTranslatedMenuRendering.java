package com.openggf.game;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.*;
import com.openggf.graphics.text.*;
import com.openggf.i18n.UiMessages;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Production screen draws at the minimum viewport, with real glyph ink and GL rendering. */
@Isolated
class TestTranslatedMenuRendering {
    @TempDir Path directory;
    private record Draw(String text, int x, int y, float scale, TextLanguage language) { }

    @Test
    void translatedMenusAndLanguageRecoveryKeepTheirTextInsideTheNativeViewport() throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            TexturedQuadRenderer renderer = new TexturedQuadRenderer();
            MenuPixelFont font = spy(new MenuPixelFont());
            List<Draw> draws = new ArrayList<>();
            doAnswer(invocation -> {
                draws.add(new Draw(invocation.getArgument(0), Math.round((Float) invocation.getArgument(1)),
                        Math.round((Float) invocation.getArgument(2)), invocation.getArgument(3), invocation.getArgument(8)));
                return invocation.callRealMethod();
            }).when(font).drawText(anyString(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyFloat(), any(TextLanguage.class));
            try {
                renderer.init();
                renderer.setProjectionMatrix(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                font.init(null, renderer);
                var config = SonicConfigurationService.createStandalone(directory);
                // Reuse the screen so this also exercises live wording and layout invalidation.
                var settings = new EngineSettingsScreen(config, font);
                var store = new com.openggf.game.launch.LaunchProfileStore(config);
                // A single selected card excludes the deliberately clipped carousel neighbours.
                var hub = new MasterTitleScreen(config, store,
                        List.of(new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2)));
                hub.setStateForTest(MasterTitleScreen.State.ACTIVE);
                hub.setSelectedIndexForTest(0);
                hub.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
                hub.setTitleEntries(List.of("Extra mode"), (games, index) -> { });
                hub.setEnabledModsByGame(java.util.Map.of(MasterTitleScreen.GameEntry.SONIC_2, List.of("One", "Two")));
                setField(hub, "renderer", mock(TexturedQuadRenderer.class));
                setField(hub, "font", font);
                List<String> failures = new ArrayList<>();
                for (var language : UiMessages.languages()) {
                    UiMessages.selectLanguage(language.tag());
                    clear(); draws.clear(); settings.render(320);
                    recordBounds(draws, language.tag() + " settings", failures);
                    export(language.tag() + "-settings");
                    var draftField = EngineSettingsScreen.class.getDeclaredField("draft");
                    draftField.setAccessible(true);
                    var draft = (com.openggf.configuration.EngineSettingsDraft) draftField.get(settings);
                    draft.set(com.openggf.configuration.SonicConfiguration.UI_LANGUAGE, "fr");
                    for (String state : List.of("dirty", "resetPrompt", "prompt")) {
                        java.lang.reflect.Field field = null;
                        if (!state.equals("dirty")) {
                            field = EngineSettingsScreen.class.getDeclaredField(state);
                            field.setAccessible(true); field.setBoolean(settings, true);
                        }
                        clear(); draws.clear(); settings.render(320);
                        recordBounds(draws, language.tag() + " " + state, failures);
                        export(language.tag() + "-" + state);
                        if (field != null) field.setBoolean(settings, false);
                    }
                    draft.set(com.openggf.configuration.SonicConfiguration.UI_LANGUAGE, "auto");
                    clear(); draws.clear();
                    new LanguagePickerScreen(language.tag(), ignored -> { }).render(font, 320);
                    recordBounds(draws, language.tag() + " language picker", failures);
                    export(language.tag() + "-languages");
                    clear(); draws.clear(); hub.drawForRecording();
                    recordBounds(draws, language.tag() + " hub", failures);
                    export(language.tag() + "-hub");
                    var launch = new LaunchConfigPanel(MasterTitleScreen.GameEntry.SONIC_2,
                            com.openggf.game.launch.LaunchProfile.stockFor(MasterTitleScreen.GameEntry.SONIC_2),
                            store, config, font, renderer);
                    for (boolean prompt : List.of(false, true)) {
                        setField(launch, "prompt", prompt);
                        clear(); draws.clear(); launch.render(320);
                        recordBounds(draws, language.tag() + " launch prompt=" + prompt, failures);
                        export(language.tag() + "-launch-" + prompt);
                    }
                    var editor = new MenuTextEditor(UiMessages.text("menu.text.type_normally_down_selects_the_keypad"),
                            UiMessages.text("menu.text.type_normally_down_selects_the_keypad").repeat(12),
                            4096);
                    for (boolean keypad : List.of(false, true)) {
                        setField(editor, "keypadFocused", keypad);
                        clear(); draws.clear(); editor.render(font, 320);
                        recordTextFieldBounds(draws, 60, language.tag() + " text field keypad=" + keypad, failures);
                        export(language.tag() + "-text-field-" + keypad);
                    }
                    var numeric = new MenuTextEditor("123", "123", 64, null, MenuTextEditor.Mode.INTEGER);
                    clear(); draws.clear(); numeric.render(font, 320);
                    recordTextFieldBounds(draws, 20, language.tag() + " numeric field", failures);
                }
                assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { font.cleanup(); renderer.cleanup(); }
        }
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(owner, value);
    }

    private static void recordBounds(List<Draw> draws, String surface, List<String> failures) {
        try { assertInkBounds(draws, surface); }
        catch (AssertionError failure) { failures.add(failure.getMessage()); }
    }

    private static void recordTextFieldBounds(List<Draw> draws, int cells, String surface, List<String> failures) {
        try {
            assertInkBounds(draws, surface);
            assertEquals(cells + 5, draws.size(), surface + " draw order");
            int hintBottom = bottom(draws.get(1));
            int gridTop = Integer.MAX_VALUE, gridBottom = 0;
            for (int i = 2; i < cells + 2; i++) {
                if (draws.get(i).text().isEmpty()) continue;
                gridTop = Math.min(gridTop, top(draws.get(i)));
                gridBottom = Math.max(gridBottom, bottom(draws.get(i)));
            }
            Draw detail = draws.get(cells + 2), count = draws.get(cells + 3), controls = draws.get(cells + 4);
            assertTrue(hintBottom <= gridTop, surface + " hint overlaps keys");
            assertTrue(gridBottom <= top(detail), surface + " keys overlap detail");
            assertTrue(bottom(detail) <= top(count), surface + " detail overlaps footer");
            assertTrue(bottom(count) <= top(controls), surface + " footer rows overlap");
            assertTrue(bottom(controls) <= 224, surface + " footer extends below screen");
        } catch (AssertionError failure) { failures.add(failure.getMessage()); }
    }

    private static LineMetrics metrics(Draw draw) {
        return HostText.shared().shape(draw.text(), MenuPixelFont.face(draw.scale()), draw.language()).metrics()
                .scaled(MenuPixelFont.pixelScale(draw.scale()));
    }

    private static int top(Draw draw) { return draw.y() - metrics(draw).above(); }
    private static int bottom(Draw draw) {
        LineMetrics metrics = metrics(draw);
        return draw.y() + metrics.cellHeight() + metrics.below();
    }

    private static void assertInkBounds(List<Draw> draws, String surface) {
        assertFalse(draws.isEmpty(), surface);
        for (Draw draw : draws) {
            int multiplier = MenuPixelFont.pixelScale(draw.scale());
            int cellTop = draw.y();
            for (String value : HostText.lines(draw.text())) {
                var line = HostText.shared().shape(value, MenuPixelFont.face(draw.scale()), draw.language());
                assertTrue(line.complete(), () -> surface + ": " + draw.text() + ": " + line.missing());
                for (PlacedGlyph glyph : line.glyphs()) {
                    int left = draw.x() + glyph.x() * multiplier;
                    int top, width, height;
                    if (glyph.kind() == PlacedGlyph.Kind.BITMAP) {
                        GlyphBitmap bitmap = glyph.bitmap();
                        if (bitmap.empty()) continue;
                        left += bitmap.left() * multiplier;
                        top = cellTop + (line.baseline() + glyph.y() + bitmap.top()) * multiplier;
                        width = bitmap.width() * multiplier; height = bitmap.height() * multiplier;
                    } else {
                        top = cellTop + glyph.y() * multiplier;
                        width = (draw.scale() < 1 ? 6 : 9) * multiplier;
                        height = (draw.scale() < 1 ? 8 : 10) * multiplier;
                    }
                    assertTrue(left >= 0 && left + width <= 320 && top >= 0 && top + height <= 224,
                            surface + ": clipped glyph at " + left + "," + top + ": " + draw.text());
                }
                cellTop += line.metrics().lineHeight() * multiplier;
            }
        }
    }

    private static void clear() {
        glViewport(0, 0, 320, 224); glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
    }

    private static void export(String name) throws Exception {
        String destination = System.getenv("OPENGGF_I18N_UI_SPECIMENS");
        if (destination == null || destination.isBlank()) return;
        Path root = Path.of(destination); Files.createDirectories(root);
        ScreenshotCapture.savePNG(ScreenshotCapture.captureFramebuffer(320, 224), root.resolve(name + ".png"));
    }
}
