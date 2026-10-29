package com.openggf.graphics;

import com.openggf.game.MenuChrome;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.LineMetrics;
import com.openggf.graphics.text.ShapedLine;
import com.openggf.graphics.text.TextFace;
import com.openggf.graphics.text.TextLanguage;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Real GL pixels for every bundled host-text script at the native 320x224 grid: each line's
 * ink must reach the framebuffer and stay inside the box its measurement reserved. Set
 * {@code OPENGGF_HOST_TEXT_SPECIMENS} to a directory to export the frames for review.
 */
@Isolated
class TestHostTextRendering {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 224;
    private static final String[][] SAMPLES = {
            {"en", "Settings  Save  Cancel"}, {"es", "Configuración ¿Guardar?"}, {"fr", "Paramètres « vidéo »"},
            {"de", "Übernehmen Größe"}, {"pt-BR", "Configurações ação"}, {"it", "Proprietà città"},
            {"pl", "Zażółć gęślą jaźń"}, {"ru", "Настройки звука"}, {"id", "Pengaturan suara"},
            {"zh-Hans", "保存设置（需要重启）"}, {"zh-Hant", "儲存設定（需要重新啟動）"}, {"ja", "設定を保存しました"},
            {"ko", "설정 저장 완료"}, {"ar", "حفظ الإعدادات"}, {"ur", "ترتیبات محفوظ کریں"},
            {"hi", "सेटिंग्स सहेजें"}, {"bn", "সেটিংস সংরক্ষণ"}, {"th", "บันทึกการตั้งค่า"}};

    @Test
    void everyBundledScriptReachesTheFramebufferInsideItsMeasuredBox() throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(WIDTH, HEIGHT,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            TexturedQuadRenderer renderer = new TexturedQuadRenderer();
            MenuPixelFont font = new MenuPixelFont();
            try {
                renderer.init();
                renderer.setProjectionMatrix(new Matrix4f().ortho(0, WIDTH, 0, HEIGHT, -1, 1).get(new float[16]));
                font.init(null, renderer);
                for (float scale : new float[] {MenuStyleScale.COMPACT, 1f}) {
                    for (int page = 0; page * 6 < SAMPLES.length; page++) {
                        RgbaImage image = renderPage(font, scale, page);
                        export(image, (scale < 1 ? "compact" : "label") + "-" + page);
                    }
                }
                RgbaImage paged = renderParagraphs(font);
                export(paged, "paragraphs");
                assertEquals(GL_NO_ERROR, glGetError(), "Host text must not leave a GL error");
            } finally {
                font.cleanup();
                renderer.cleanup();
            }
        }
    }

    private static RgbaImage renderPage(MenuPixelFont font, float scale, int page) {
        clear();
        int y = 8;
        record Placed(String tag, int x, int y, ShapedLine line, LineMetrics metrics) { }
        java.util.List<Placed> placed = new java.util.ArrayList<>();
        TextFace role = MenuPixelFont.face(scale);
        for (int i = page * 6; i < Math.min(SAMPLES.length, page * 6 + 6); i++) {
            TextLanguage language = TextLanguage.of(SAMPLES[i][0]);
            LineMetrics metrics = font.lineMetrics(scale, language);
            int cellTop = y + metrics.above();
            font.beginMegaBatch();
            font.drawText(SAMPLES[i][1], 8, cellTop, scale, 1, 1, 1, 1, language);
            font.endMegaBatch();
            placed.add(new Placed(SAMPLES[i][0], 8, cellTop, font.shape(SAMPLES[i][1], scale, language), metrics));
            y += metrics.lineHeight() + 4;
        }
        RgbaImage image = ScreenshotCapture.captureFramebuffer(WIDTH, HEIGHT);
        int multiplier = MenuPixelFont.pixelScale(scale);
        boolean[] claimed = new boolean[WIDTH * HEIGHT];
        for (Placed p : placed) {
            assertTrue(p.line().complete(), p.tag() + " missing " + p.line().missing());
            int left = p.x() - 2 * multiplier;
            int right = p.x() + p.line().width() * multiplier + 2 * multiplier;
            int top = p.y() - p.metrics().above();
            int bottom = p.y() + p.metrics().cellHeight() + p.metrics().below();
            long ink = 0;
            for (int py = Math.max(0, top); py < Math.min(HEIGHT, bottom); py++) {
                for (int px = Math.max(0, left); px < Math.min(WIDTH, right); px++) {
                    if (lit(image, px, py)) {
                        ink++;
                        claimed[py * WIDTH + px] = true;
                    }
                }
            }
            assertTrue(ink > 20 * multiplier, p.tag() + " must reach the framebuffer: " + ink);
        }
        for (int py = 0; py < HEIGHT; py++) {
            for (int px = 0; px < WIDTH; px++) {
                assertFalse(lit(image, px, py) && !claimed[py * WIDTH + px],
                        "Ink at " + px + "," + py + " escaped every measured box on page " + page + " @" + scale);
            }
        }
        return image;
    }

    /** Wrapped help text at the 320x224 minimum, paged by the language's own line pitch. */
    private static RgbaImage renderParagraphs(MenuPixelFont font) {
        clear();
        String[][] paragraphs = {
                {"ar", "سيتم تطبيق إعدادات الفيديو عند إعادة تشغيل اللعبة."},
                {"hi", "वीडियो सेटिंग्स अगली बार खेल शुरू करने पर लागू होंगी।"},
                {"th", "การตั้งค่าวิดีโอจะมีผลเมื่อเริ่มเกมครั้งถัดไป"},
                {"ja", "ビデオ設定は次回の起動時に適用されます。"},
                {"de", "Videoeinstellungen werden beim nächsten Start übernommen."}};
        int leftY = 6;
        int rightY = 6;
        TextLanguage previous = HostTextContext.current();
        try {
            for (String[] paragraph : paragraphs) {
                TextLanguage language = TextLanguage.of(paragraph[0]);
                HostTextContext.activate(language);
                int pitch = MenuChrome.compactLineHeight();
                List<String> lines = font.wrap(paragraph[1], 150, MenuStyleScale.COMPACT, language);
                boolean rightColumn = paragraph[0].equals("ar") || paragraph[0].equals("th");
                int x = rightColumn ? 162 : 6;
                int y = rightColumn ? rightY : leftY;
                for (String line : lines) {
                    assertTrue(HostText.shared().width(line, TextFace.COMPACT, language) <= 150, line);
                    if (paragraph[0].equals("ar")) {
                        font.drawText(line, x + 150 - font.measureWidth(line, MenuStyleScale.COMPACT, language),
                                y + font.lineMetrics(MenuStyleScale.COMPACT, language).above(),
                                MenuStyleScale.COMPACT, .9f, .93f, 1, 1, language);
                    } else {
                        font.drawText(line, x, y + font.lineMetrics(MenuStyleScale.COMPACT, language).above(),
                                MenuStyleScale.COMPACT, .9f, .93f, 1, 1, language);
                    }
                    y += pitch;
                }
                assertTrue(lines.size() >= 2, paragraph[0] + " wraps at the minimum width");
                if (rightColumn) rightY = y + 4;
                else leftY = y + 4;
                assertTrue(Math.max(leftY, rightY) < HEIGHT, "Paged help text fits the native screen");
            }
        } finally {
            HostTextContext.activate(previous);
        }
        return ScreenshotCapture.captureFramebuffer(WIDTH, HEIGHT);
    }

    private static void clear() {
        glViewport(0, 0, WIDTH, HEIGHT);
        glDisable(GL_DITHER);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(.02f, .07f, .23f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private static boolean lit(RgbaImage image, int x, int y) {
        int pixel = image.pixels()[y * image.width() + x];
        int r = (pixel >> 16) & 255, g = (pixel >> 8) & 255;
        return r > 40 && g > 40;
    }

    private static void export(RgbaImage image, String name) throws Exception {
        String configured = System.getenv("OPENGGF_HOST_TEXT_SPECIMENS");
        if (configured == null || configured.isBlank()) return;
        Path directory = Path.of(configured);
        Files.createDirectories(directory);
        ScreenshotCapture.savePNG(image, directory.resolve(name + ".png"));
    }

    private static final class MenuStyleScale {
        static final float COMPACT = 2f / 3f;
    }
}
