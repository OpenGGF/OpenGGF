package com.openggf.game.playback;

import com.openggf.control.InputHandler;
import com.openggf.game.MenuChrome;
import com.openggf.graphics.*;
import com.openggf.graphics.text.*;
import com.openggf.i18n.UiMessages;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

@Isolated
class TestPlaybackFullTextLayout {
    record Draw(String text, int x, int y) { }
    @AfterEach void restoreLanguage() { UiMessages.selectLanguage("en"); }

    private static PlaybackBrowser browser(PixelFont font, String name, String error) {
        return new PlaybackBrowser(new PlaybackLibrary(List.of(new PlaybackEntry.Movie(Path.of(name), 120, error)), List.of()),
                "", PlaybackBrowser.Filter.ALL, false, font,
                new PlaybackLauncher((entry, options) -> fail("Reader cannot launch"), entry -> { fail("Reader cannot launch"); return false; }, () -> false, () -> false));
    }

    @Test void tallPreviewsUseSeparateRowsAndEverySummaryFragmentGetsATurn() throws Exception {
        for (String tag : List.of("bn", "ar")) {
            UiMessages.selectLanguage(tag);
            List<Draw> draws = new ArrayList<>();
            var font = new PixelFont() {
                @Override public void drawText(String text, int x, int y, float scale, float r, float g, float b, float a) {
                    draws.add(new Draw(text, x, y));
                }
            };
            var browser = browser(font, "বাংলা পরীক্ষার নাম ".repeat(3)+"NAME_TAIL.bk2", "পূর্ণ বিবরণ ".repeat(8)+"STATUS_TAIL");
            var clock = PlaybackBrowser.class.getDeclaredField("ticks"); clock.setAccessible(true);
            Set<String> seen = new HashSet<>();
            for (int tick = 0; tick < 4000; tick += 120) {
                clock.setInt(browser, tick); draws.clear(); browser.render();
                var summary = draws.stream().filter(d -> d.y() >= 157 && d.y() < 200).toList();
                int last = -100;
                for (Draw draw : summary) {
                    var shaped = HostText.shared().shape(draw.text(), TextFace.COMPACT, HostTextContext.current());
                    assertTrue(draw.y()-shaped.above() >= 160, draw.toString());
                    assertTrue(draw.y()+shaped.metrics().cellHeight()+shaped.metrics().below() <= 200, draw.toString());
                    assertTrue(draw.y()-last >= MenuChrome.compactLineHeight(), draw.toString());
                    last = draw.y(); seen.add(draw.text());
                }
                var filters = draws.stream().filter(d -> d.x()==80 && d.y() < 83).toList();
                assertEquals(2, filters.size());
                assertTrue(filters.get(1).y()-filters.get(0).y() >= MenuChrome.compactLineHeight());
            }
            assertTrue(seen.stream().anyMatch(s -> s.contains("NAME_TAIL")), seen.toString());
            assertTrue(seen.stream().anyMatch(s -> s.contains("STATUS_TAIL")), seen.toString());
        }
    }

    @Test void unicodePreviewPagesHaveRealNativeAndWidePixels() throws Exception {
        for (String tag : List.of("bn", "ar")) {
            UiMessages.selectLanguage(tag);
            for (int width : new int[]{320, 400}) {
                try (var context = PresentationOffscreenTestContext.openConfigured(width, 224, PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
                    var renderer = new TexturedQuadRenderer(); var font = new MenuPixelFont();
                    try {
                        renderer.init(); font.init(null, renderer);
                        var browser = browser(font, "বাংলা পরীক্ষার নাম NAME_TAIL.bk2", "পূর্ণ বিবরণ ".repeat(10)+"STATUS_TAIL");
                        for (int phase : new int[]{0, 120}) {
                            var clock = PlaybackBrowser.class.getDeclaredField("ticks"); clock.setAccessible(true); clock.setInt(browser, phase);
                            glViewport(0,0,width,224); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
                            glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
                            renderer.setProjectionMatrix(new Matrix4f().ortho(0,width,0,224,-1,1).get(new float[16]));
                            browser.render();
                            var pixels = ScreenshotCapture.captureFramebuffer(width,224);
                            assertTrue(Arrays.stream(pixels.pixels()).distinct().count()>10);
                            String root = System.getenv("OPENGGF_PLAYBACK_LAYOUT_SCREENS");
                            if(root!=null&&!root.isBlank()) { Files.createDirectories(Path.of(root)); ScreenshotCapture.savePNG(pixels,Path.of(root).resolve(tag+"-"+width+"-summary-"+phase+".png")); }
                            assertEquals(GL_NO_ERROR,glGetError());
                        }
                    } finally { font.cleanup(); renderer.cleanup(); }
                }
            }
        }
    }
}
