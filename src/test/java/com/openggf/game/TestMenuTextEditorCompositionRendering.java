package com.openggf.game;

import com.openggf.control.Composition;
import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.control.MenuInput;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.TexturedQuadRenderer;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.ShapedLine;
import com.openggf.graphics.text.TextLanguage;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Production editor pixels, including preedit clauses and commits, at the native screen size. */
@Isolated
class TestMenuTextEditorCompositionRendering {
    private record Sample(String name, String language, String prefix, String preedit, int[] blocks, int focused, int caret) { }
    private static final Sample[] SAMPLES = {
            new Sample("japanese", "ja", "冒険 ", "日本語入力", new int[] {3, 2}, 0, 3),
            new Sample("chinese-scrolling", "zh-Hans", "已有文字".repeat(12), "正在输入中文", new int[] {2, 4}, 1, 6),
            new Sample("korean", "ko", "이름 ", "한글입력", new int[] {2, 2}, 0, 4),
            new Sample("combining-supplementary", "en", "café ", "😀é𐐷", new int[] {1, 2, 1}, 1, 4),
            new Sample("mixed-arabic-latin", "ar", "Sonic ", "سلام abc العربية", new int[] {5, 4, 7}, 2, 16)
    };

    @Test void editorDrawsShapedInkClippedClausesAndCaretAndThenOnlyCommitsCharacters() throws Exception {
        TextLanguage previous = HostTextContext.current();
        try (var context = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            var renderer = new TexturedQuadRenderer();
            var font = new MenuPixelFont();
            try {
                renderer.init();
                renderer.setProjectionMatrix(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                font.init(null, renderer);
                for (Sample sample : SAMPLES) {
                    HostTextContext.activate(TextLanguage.of(sample.language()));
                    font.setLanguage(HostTextContext.current());
                    InputHandler input = new InputHandler();
                    var editor = new MenuTextEditor("Edit text", sample.prefix(), 512);
                    editor.update(input); input.update();
                    var session = InputHandlerInternalAccess.textInput(input);
                    int[] nativeCaret = {-1};
                    session.attach(new com.openggf.control.TextInputSession.NativeHost() {
                        public void reset() { }
                        public void caretRectangle(int x, int y, int width, int height) { nativeCaret[0] = x; }
                    });
                    Composition composition = Composition.fromCodepoints(sample.preedit().codePoints().toArray(),
                            sample.blocks(), sample.focused(), sample.caret());
                    session.preedit(composition);
                    editor.update(input);
                    RgbaImage before = render(editor, font);
                    ShapedLine expectedLine = com.openggf.graphics.text.HostText.shared().shape(sample.prefix() + sample.preedit(),
                            com.openggf.graphics.text.TextFace.LABEL, HostTextContext.current());
                    int offset = 15 + expectedLine.caretX(sample.prefix().length() + composition.caret()) - nativeCaret[0];
                    assertEquals(sample.prefix(), editor.value());
                    assertEquals(sample.prefix() + sample.preedit(), expectedLine.text());
                    assertTrue(expectedLine.width() > 0);
                    int anchor = sample.prefix().length();
                    int caret = expectedLine.caretX(anchor + composition.caret());
                    assertTrue(caret - offset >= 0 && caret - offset < 290);
                    assertCyan(before, 15 + caret - offset, 45 - expectedLine.above(), "caret " + sample.name());
                    int underline = 45 + expectedLine.metrics().cellHeight() + expectedLine.below() + 1;
                    int visible = 0;
                    for (int i = 0; i < composition.clauses().size(); i++) {
                        var clause = composition.clauses().get(i);
                        for (var span : expectedLine.visualRanges(anchor + clause.start(), anchor + clause.end())) {
                            int left = Math.max(15, 15 + span.left() - offset);
                            int right = Math.min(305, 15 + span.right() - offset);
                            if (right <= left) continue;
                            visible += right - left;
                            if (i == composition.focusedClause()) {
                                assertCyan(before, (left + right - 1) / 2, underline + 1, "focused clause " + sample.name());
                            }
                        }
                    }
                    assertTrue(visible > 8, "Visible underline geometry " + sample.name());
                    long ink = 0;
                    for (int y = 40; y < underline; y++) for (int x = 15; x < 305; x++) {
                        int pixel = before.argb(x, y);
                        if ((pixel & 0xFFFFFF) == 0xFFFFFF) ink++;
                    }
                    assertTrue(ink > 15, "Real shaped field ink " + sample.name());
                    if (sample.name().contains("scrolling")) assertTrue(offset > 0);
                    export(before, sample.name() + "-preedit");
                    if (sample.name().equals("japanese")) {
                        session.preedit(new Composition(composition.text(), composition.clauses(), 1, composition.caret()));
                        RgbaImage wrongFocus = render(editor, font);
                        assertFalse(Arrays.equals(before.pixels(), wrongFocus.pixels()), "Wrong focused-clause control must change real pixels");
                        var clause = composition.clauses().getFirst();
                        int start = expectedLine.caretX(anchor + clause.start());
                        int end = expectedLine.caretX(new ShapedLine.Caret(anchor + clause.end(), true));
                        int middle = 15 + (start + end) / 2 - offset;
                        assertFalse(cyan(wrongFocus.argb(middle, underline + 1)), "The wrong focus must lose the expected second underline row");
                        export(wrongFocus, "japanese-wrong-focus-control");
                    }
                    session.preedit(Composition.EMPTY);
                    sample.preedit().codePoints().forEach(cp -> MenuInput.handleCharEvent(input, cp));
                    editor.update(input);
                    assertEquals(sample.prefix() + sample.preedit(), editor.value());
                    assertEquals(MenuTextEditor.Result.NONE, editor.consumeResult());
                    RgbaImage committed = render(editor, font);
                    assertFalse(Arrays.equals(before.pixels(), committed.pixels()));
                    export(committed, sample.name() + "-committed");
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { font.cleanup(); renderer.cleanup(); }
        } finally { HostTextContext.activate(previous); }
    }
    @Test void legacySceneAndCourseCaptureHasHostPreeditWithoutPublicUncommittedText() throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
                var overlay = new TextInputOverlay()) {
            var input = new InputHandler();
            input.captureKeyboardText();
            var session = InputHandlerInternalAccess.textInput(input);
            session.preedit(Composition.fromCodepoints("日本語".codePoints().toArray(), new int[] {2, 1}, 0, 3));
            assertEquals("", input.consumeTypedText());
            glViewport(0, 0, 320, 224); glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
            float[] projection = new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]);
            overlay.render(input, projection, 320, 224);
            RgbaImage preedit = ScreenshotCapture.captureFramebuffer(320, 224);
            long ink = Arrays.stream(preedit.pixels()).filter(pixel -> (pixel & 0xFFFFFF) == 0xFFFFFF).count();
            assertTrue(ink > 20, "The host overlay must draw actual preedit glyphs");
            export(preedit, "legacy-host-preedit");
            session.preedit(Composition.EMPTY);
            "日本語".codePoints().forEach(cp -> MenuInput.handleCharEvent(input, cp));
            assertEquals("日本語", input.consumeTypedText());
            glClear(GL_COLOR_BUFFER_BIT); overlay.render(input, projection, 320, 224);
            RgbaImage committed = ScreenshotCapture.captureFramebuffer(320, 224);
            assertTrue(Arrays.stream(committed.pixels()).allMatch(pixel -> (pixel & 0xFFFFFF) == 0));
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    private static RgbaImage render(MenuTextEditor editor, MenuPixelFont font) {
        glViewport(0, 0, 320, 224); glDisable(GL_DITHER);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT);
        font.beginMegaBatch(); editor.render(font, 320); font.endMegaBatch();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
    private static boolean cyan(int pixel) {
        int r = pixel >> 16 & 255, g = pixel >> 8 & 255, b = pixel & 255;
        return r >= 75 && r <= 78 && g >= 228 && g <= 231 && b == 255;
    }
    private static void assertCyan(RgbaImage image, int x, int y, String message) { assertTrue(cyan(image.argb(x, y)), message + " at " + x + "," + y); }
    private static void export(RgbaImage image, String name) throws Exception {
        String configured = System.getenv("OPENGGF_IME_UI_SPECIMENS");
        if (configured == null || configured.isBlank()) return;
        Path directory = Path.of(configured); Files.createDirectories(directory);
        ScreenshotCapture.savePNG(image, directory.resolve(name + ".png"));
    }
}
