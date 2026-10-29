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
    void settingsAndLanguageRecoveryKeepEveryLanguageInsideTheNativeViewport() throws Exception {
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
                for (var language : UiMessages.languages()) {
                    UiMessages.selectLanguage(language.tag());
                    clear(); draws.clear(); settings.render(320);
                    assertInkBounds(draws, language.tag() + " settings");
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
                        assertInkBounds(draws, language.tag() + " " + state);
                        export(language.tag() + "-" + state);
                        if (field != null) field.setBoolean(settings, false);
                    }
                    draft.set(com.openggf.configuration.SonicConfiguration.UI_LANGUAGE, "auto");
                    clear(); draws.clear();
                    new LanguagePickerScreen(language.tag(), ignored -> { }).render(font, 320);
                    assertInkBounds(draws, language.tag() + " language picker");
                    export(language.tag() + "-languages");
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { font.cleanup(); renderer.cleanup(); }
        }
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
