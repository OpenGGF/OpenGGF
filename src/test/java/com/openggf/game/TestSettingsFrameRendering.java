package com.openggf.game;

import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.*;
import com.openggf.i18n.UiMessages;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

@Isolated
class TestSettingsFrameRendering {
    @TempDir Path directory;

    @Test void fiveWidthsKeepSettingsAndChildrenInsideTheSameSixteenNineFrame() throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(800, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            var renderer = new TexturedQuadRenderer();
            var font = new MenuPixelFont();
            try {
                renderer.init(); font.init(null, renderer);
                var config = SonicConfigurationService.createStandalone(directory);
                for (var language : UiMessages.languages()) {
                    UiMessages.selectLanguage(language.tag());
                    for (String state : List.of("rows", "resetPrompt", "prompt", "choosing", "keyMenu",
                            "capturing", "details", "languagePicker", "textEditor")) {
                        var screen = new EngineSettingsScreen(config, font);
                        configure(screen, state);
                        int[] expected = null;
                        for (int width : new int[] {320, 352, 400, 528, 800}) {
                            if (state.equals("details")) {
                                var field = EngineSettingsScreen.class.getDeclaredField("details"); field.setAccessible(true);
                                set(field.get(screen), "ticks", 0);
                            }
                            glViewport(0, 0, width, 224);
                            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                            glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
                            renderer.setProjectionMatrix(new Matrix4f().ortho(0, width, 0, 224, -1, 1).get(new float[16]));
                            RgbaImage background = null;
                            if (width > 400) {
                                MenuChrome.background(font, width);
                                background = ScreenshotCapture.captureFramebuffer(width, 224);
                            }
                            screen.render(width);
                            var capture = ScreenshotCapture.captureFramebuffer(width, 224);
                            assertTrue(java.util.Arrays.stream(capture.pixels()).distinct().limit(2).count() > 1);
                            if (width == 400) expected = capture.pixels();
                            if (width > 400) {
                                int[] center = ScreenshotCapture.captureFramebufferRegion((width - 400) / 2, 0, 400, 224).pixels();
                                assertArrayEquals(expected, center, language.tag() + " " + state + " " + width);
                                int left = (width - 400) / 2;
                                for (int y = 0; y < 224; y++) {
                                    int offset = y * width;
                                    assertEquals(-1, java.util.Arrays.mismatch(background.pixels(), offset, offset + left,
                                            capture.pixels(), offset, offset + left), "Left gutter " + state);
                                    assertEquals(-1, java.util.Arrays.mismatch(background.pixels(), offset + left + 400, offset + width,
                                            capture.pixels(), offset + left + 400, offset + width), "Right gutter " + state);
                                }
                            }
                            export(language.tag() + "-" + width + "-" + state, capture);
                            assertEquals(GL_NO_ERROR, glGetError());
                        }
                    }
                }
                glDisable(GL_BLEND);
                assertThrows(IllegalStateException.class, () -> font.withHorizontalOffset(200, () -> {
                    font.fillRect(4, 4, 3, 3, 1, 0, 0, 1);
                    throw new IllegalStateException("probe projection restoration");
                }));
                font.fillRect(4, 4, 3, 3, 0, 1, 0, 1);
                var restored = ScreenshotCapture.captureFramebuffer(800, 224);
                assertEquals(0x00ff00, restored.argb(5, 5) & 0xffffff);
                assertEquals(0xff0000, restored.argb(205, 5) & 0xffffff);
            } finally { font.cleanup(); renderer.cleanup(); UiMessages.selectLanguage("en"); }
        }
    }

    private static void configure(EngineSettingsScreen screen, String state) throws Exception {
        switch (state) {
            case "rows" -> { }
            case "details" -> set(screen, state, new MenuDetailsScreen(UiMessages.text("settings.screen.settings_caps"),
                    UiMessages.text("settings.key.display_hud_anchor.help").repeat(12)));
            case "languagePicker" -> set(screen, state, new LanguagePickerScreen("auto", ignored -> { }));
            case "textEditor" -> set(screen, state, new MenuTextEditor(UiMessages.text("settings.screen.settings_caps"),
                    UiMessages.text("settings.key.display_hud_anchor.label").repeat(10), 4096));
            case "prompt" -> {
                var field = EngineSettingsScreen.class.getDeclaredField("draft");
                field.setAccessible(true);
                ((EngineSettingsDraft) field.get(screen)).set(SonicConfiguration.UI_LANGUAGE, "fr");
                set(screen, state, true);
            }
            default -> set(screen, state, true);
        }
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(owner, value);
    }

    private static void export(String name, RgbaImage image) throws Exception {
        String destination = System.getenv("OPENGGF_SETTINGS_FRAME_SPECIMENS");
        if (destination == null || destination.isBlank()) return;
        Path root = Path.of(destination); Files.createDirectories(root);
        ScreenshotCapture.savePNG(image, root.resolve(name + ".png"));
    }
}
