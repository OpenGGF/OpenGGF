package com.openggf.game;

import com.openggf.InputBindingFactory;
import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.GamepadStateSource;
import com.openggf.control.InputHandler;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.SyntheticProgramDisc;
import com.openggf.graphics.text.GlyphBitmap;
import com.openggf.graphics.MenuGlyphs;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.PlacedGlyph;
import com.openggf.graphics.text.TextLanguage;
import com.openggf.i18n.UiMessages;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * The setup guide drawn by the production font and GL renderer at the native 320 x 224 frame and at 16:9 (400),
 * in every shipped language, through every state: partial and empty libraries, a Sonic CD problem, each play
 * style (and Keep for upgrades), the prompts, Play together and Ready, with keyboard and controller footers.
 * Every glyph must be complete (no missing-glyph fallback) and inside the frame, and nothing may be ellipsized.
 * Set {@code OPENGGF_SETUP_GUIDE_PICTURES} to a folder to keep the pictures for review.
 */
@Isolated
class TestSetupGuideRendering {
    @TempDir Path dir;
    private GamepadStateSource padSource;
    private InputHandler padInput;
    private record Draw(String text, int x, int y, float scale, TextLanguage language) { }

    @AfterEach void english() { UiMessages.selectLanguage("en"); }

    @Test void firstRunWarningsAndDisclaimerSettingFitTheNativeFrame() throws Exception {
        UiMessages.selectLanguage("en");
        Path install = Files.createDirectory(dir.resolve("install"));
        Path games = Files.createDirectory(dir.resolve("downloads"));
        TestMenuGameOnboarding.image(games, "unverified.gen", 0x100000, "SONIC THE HEDGEHOG 2");
        Files.write(games.resolve("game.zip"), new byte[] {0x50, 0x4b, 3, 4});
        Files.write(games.resolve("game.smd"), new byte[0x80200]);
        var config = SonicConfigurationService.createStandalone(install);
        var guide = new MenuSetupGuide(config, SetupGuideSession.forConfiguration(config), new MenuSetupGuide.Host() {
            @Override public boolean onlineAvailable() { return false; }
            @Override public void libraryChanged() { }
        }, false);
        guide.acceptFindings(SetupGuideLibrary.scan(games, SetupGuideLibrary.games()));

        int width = 320;
        try (var context = PresentationOffscreenTestContext.openConfigured(width, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            var renderer = new TexturedQuadRenderer();
            var font = new MenuPixelFont();
            try {
                renderer.init();
                renderer.setProjectionMatrix(new Matrix4f().ortho(0, width, 0, 224, -1, 1).get(new float[16]));
                font.init(null, renderer);

                clear(width);
                guide.render(font, width);
                assertTrue(guide.gamesLayout().bottom() < MenuChrome.FOOTER_Y);
                export("first-run-your-games", width);

                var settings = new EngineSettingsScreen(config, font);
                var input = new InputHandler(InputBindingFactory.supplier(config), List::of);
                for (int key : new int[] {GLFW_KEY_UP, GLFW_KEY_RIGHT, GLFW_KEY_RIGHT, GLFW_KEY_RIGHT,
                        GLFW_KEY_DOWN, GLFW_KEY_DOWN, GLFW_KEY_DOWN}) {
                    input.handleKeyEvent(key, GLFW_PRESS);
                    input.refreshLogicalSnapshot(); settings.update(input); input.update();
                    input.handleKeyEvent(key, GLFW_RELEASE);
                    input.refreshLogicalSnapshot(); settings.update(input); input.update();
                }
                assertEquals(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP, settings.selectedForTest());
                clear(width);
                settings.render(width);
                export("first-run-startup-disclaimer", width);
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { font.cleanup(); renderer.cleanup(); }
        }
    }

    @Test void everyStateFitsTheFrameInEveryLanguageAtBothWidths() throws Exception {
        Path cd = SyntheticProgramDisc.write(dir.resolve("cd"), CdReleaseVariant.USA);
        Path broken = SyntheticProgramDisc.write(dir.resolve("broken"), CdReleaseVariant.USA);
        Files.delete(broken.resolveSibling("audio12.bin"));
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Logger textLog = Logger.getLogger(HostText.class.getName());
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) { if (record.getMessage().contains("truncated")) warnings.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        textLog.addHandler(capture);
        try {
            for (int width : new int[] {320, 400}) render(width, cd, broken, failures);
        } finally { textLog.removeHandler(capture); }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
        assertTrue(warnings.isEmpty(), () -> "ellipsized text:\n" + String.join("\n", warnings));
    }

    private void render(int width, Path cd, Path broken, List<String> failures) throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(width, 224,
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
                renderer.setProjectionMatrix(new Matrix4f().ortho(0, width, 0, 224, -1, 1).get(new float[16]));
                font.init(null, renderer);
                for (var language : UiMessages.languages()) {
                    UiMessages.selectLanguage(language.tag());
                    for (var state : states(cd, broken)) {
                        var guide = state.build();
                        if (state.input() == padInput) ((Pad) padSource).tap(state.input());
                        guide.update(state.input());   // footers read the device used last
                        clear(width); draws.clear();
                        guide.render(font, width);
                        String surface = language.tag() + " " + width + " " + state.name();
                        try { assertInk(draws, surface, width); } catch (AssertionError e) { failures.add(e.getMessage()); }
                        if (state.name().startsWith("games-") && !state.name().equals("games-empty")) {
                            var layout = guide.gamesLayout();
                            if (layout.bottom() > MenuChrome.FOOTER_Y - 2 || layout.buttonsY() < layout.rowsY() + layout.rows().size() * layout.rowPitch())
                                failures.add(surface + ": games blocks overlap or leave the body: " + layout);
                        }
                        export(language.tag() + "-" + width + "-" + state.name(), width);
                    }
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { font.cleanup(); renderer.cleanup(); }
        }
    }

    private record State(String name, java.util.concurrent.Callable<MenuSetupGuide> factory, InputHandler input) {
        MenuSetupGuide build() throws Exception { return factory.call(); }
    }

    private List<State> states(Path cd, Path broken) throws Exception {
        List<State> states = new ArrayList<>();
        Path settings = Files.createDirectories(dir.resolve("settings-" + System.nanoTime()));
        var config = SonicConfigurationService.createStandalone(settings);
        ConfigurationPersistence.apply(config, Map.of(SonicConfiguration.DISPLAY_INTERPOLATION, true));
        InputHandler keyboard = new InputHandler(InputBindingFactory.supplier(config), List::of);
        var padConfig = SonicConfigurationService.createStandalone(Files.createDirectories(settings.resolve("pad")));
        padConfig.setConfigValue(SonicConfiguration.CONTROLLER_ENABLED, true);
        padConfig.setConfigValue(SonicConfiguration.CONTROLLER_PLAYER1, "auto");
        padSource = new Pad();
        InputHandler pad = new InputHandler(InputBindingFactory.supplier(padConfig), padSource);
        padInput = pad;
        Map<String, Path> partial = Map.of("s2", Path.of("s2.gen"), "s3k", Path.of("s3k.gen"), "scd", cd);
        states.add(new State("games-partial", () -> guide(config, partial, false), keyboard));
        states.add(new State("games-partial-pad", () -> guide(config, partial, false), pad));
        states.add(new State("games-empty", () -> guide(config, Map.of(), false), keyboard));
        states.add(new State("games-cd-problem", () -> {
            var g = guide(config, Map.of("s1", Path.of("s1.gen"), "s2", Path.of("s2.gen"), "s3k", Path.of("s3k.gen")), false);
            g.acceptFindings(SetupGuideLibrary.locate(broken, SetupGuideLibrary.games()));
            return g;
        }, keyboard));
        states.add(new State("games-upgrade", () -> guide(config, partial, true), keyboard));
        for (SetupGuidePreset preset : List.of(SetupGuidePreset.STOCK, SetupGuidePreset.BALANCED, SetupGuidePreset.ENHANCED,
                SetupGuidePreset.MULTIPLAYER)) {
            states.add(new State("style-" + preset.name().toLowerCase(), () -> {
                var g = guide(config, partial, false);
                g.showForTest(MenuSetupGuide.Step.STYLE, g.styles().indexOf(preset), null);
                return g;
            }, keyboard));
        }
        states.add(new State("style-keep-pad", () -> {
            var g = guide(config, partial, true);
            g.showForTest(MenuSetupGuide.Step.STYLE, 0, null);
            return g;
        }, pad));
        states.add(new State("apply-diff", () -> {
            var g = guide(config, partial, true);
            g.showForTest(MenuSetupGuide.Step.STYLE, 3, SetupGuidePreset.ENHANCED);
            g.openPromptForTest("APPLY");
            return g;
        }, keyboard));
        states.add(new State("leave", () -> {
            var g = guide(config, Map.of(), false);
            g.openPromptForTest("LEAVE");
            return g;
        }, keyboard));
        states.add(new State("together", () -> {
            var g = guide(config, partial, false);
            g.showForTest(MenuSetupGuide.Step.TOGETHER, 0, SetupGuidePreset.MULTIPLAYER);
            return g;
        }, keyboard));
        states.add(new State("ready", () -> {
            var g = guide(config, partial, false);
            g.showForTest(MenuSetupGuide.Step.READY, 0, SetupGuidePreset.BALANCED);
            return g;
        }, keyboard));
        states.add(new State("ready-no-games", () -> {
            var g = guide(config, Map.of(), false);
            g.showForTest(MenuSetupGuide.Step.READY, 0, SetupGuidePreset.STOCK);
            return g;
        }, pad));
        return states;
    }

    private MenuSetupGuide guide(SonicConfigurationService config, Map<String, Path> available, boolean upgrade) {
        Map<String, Path> games = new HashMap<>(available);
        return new MenuSetupGuide(config, new SetupGuideSession(upgrade), new MenuSetupGuide.Host() {
            @Override public boolean onlineAvailable() { return true; }
            @Override public void libraryChanged() { }
        }, false, ignored -> new SetupGuideLibrary.Current() {
            @Override public boolean available(SetupGuideLibrary.Game g) { return games.containsKey(g.id()); }
            @Override public Optional<Path> file(SetupGuideLibrary.Game g) { return Optional.ofNullable(games.get(g.id())); }
        });
    }

    private static void assertInk(List<Draw> draws, String surface, int width) {
        assertTrue(!draws.isEmpty(), surface);
        for (Draw draw : draws) {
            int multiplier = MenuPixelFont.pixelScale(draw.scale());
            int cellTop = draw.y();
            for (String value : HostText.lines(draw.text())) {
                var line = HostText.shared().shape(value, MenuPixelFont.face(draw.scale()), draw.language());
                assertTrue(line.complete(), () -> surface + ": missing glyphs in " + draw.text() + ": " + line.missing());
                for (PlacedGlyph glyph : line.glyphs()) {
                    int left = draw.x() + glyph.x() * multiplier;
                    int top, w, h;
                    if (glyph.kind() == PlacedGlyph.Kind.BITMAP) {
                        GlyphBitmap bitmap = glyph.bitmap();
                        if (bitmap.empty()) continue;
                        left += bitmap.left() * multiplier;
                        top = cellTop + (line.baseline() + glyph.y() + bitmap.top()) * multiplier;
                        w = bitmap.width() * multiplier; h = bitmap.height() * multiplier;
                    } else {
                        top = cellTop + glyph.y() * multiplier;
                        w = (glyph.kind() == PlacedGlyph.Kind.NATIVE
                                ? MenuGlyphs.inkWidthIndex(draw.scale() < 1 ? MenuGlyphs.Face.LIGHT : MenuGlyphs.Face.BOLD, glyph.glyph())
                                : (draw.scale() < 1 ? 6 : 9)) * multiplier;
                        h = (draw.scale() < 1 ? 8 : 10) * multiplier;
                    }
                    int l = left, t = top, ww = w, hh = h;
                    assertTrue(l >= 0 && l + ww <= width && t >= 0 && t + hh <= 224,
                            () -> surface + ": glyph outside the frame at " + l + "," + t + " (" + ww + "x" + hh + "): " + draw.text());
                }
                cellTop += line.metrics().lineHeight() * multiplier;
            }
        }
    }

    private static void clear(int width) {
        glViewport(0, 0, width, 224); glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
    }

    private static void export(String name, int width) throws Exception {
        String destination = System.getenv("OPENGGF_SETUP_GUIDE_PICTURES");
        if (destination == null || destination.isBlank()) return;
        Path root = Path.of(destination); Files.createDirectories(root);
        ScreenshotCapture.savePNG(ScreenshotCapture.captureFramebuffer(width, 224), root.resolve(name + ".png"));
    }

    /** A pad whose last use was a press of its Back/Select button, so menus present controller caps. */
    private static final class Pad implements GamepadStateSource {
        private int pressed = -1;
        void tap(InputHandler input) {
            pressed = org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_BACK; input.refreshLogicalSnapshot(); input.update();
            pressed = -1; input.refreshLogicalSnapshot(); input.update();
        }
        @Override public List<DeviceState> pollDevices() {
            boolean[] buttons = new boolean[org.lwjgl.glfw.GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1];
            if (pressed >= 0) buttons[pressed] = true;
            return List.of(DeviceState.connected(0, "pad", buttons, 0f, 0f));
        }
    }
}
