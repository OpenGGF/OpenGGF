package com.openggf.game;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.InputHandlerInternalAccess;
import com.openggf.control.TextInputSession;
import com.openggf.graphics.*;
import com.openggf.graphics.text.*;
import com.openggf.i18n.UiMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

@Isolated
class TestMenuFullTextAccess {
    @TempDir Path directory;
    record Draw(String text, int x, int y, float scale) { }
    static final class Font extends PixelFont {
        final List<Draw> draws = new ArrayList<>();
        @Override public void drawText(String value, int x, int y, float scale, float r, float g, float b, float a) {
            draws.add(new Draw(value, x, y, scale));
        }
    }
    @AfterEach void restoreEnglish() { UiMessages.selectLanguage("en"); }

    @Test void structuredDetailsRetainEveryFieldAndReadingDoesNotInvokeActions() {
        UiMessages.selectLanguage("en");
        var calls = new AtomicInteger();
        var details = new MenuDetailsScreen(new MenuDetailsScreen.Content(longText("TITLEEND"),
                longText("RESULTEND"), "Meaning", MenuDetailsScreen.Tone.ERROR,
                List.of(new MenuDetailsScreen.Section(longText("HEADINGEND"),
                        List.of(new MenuDetailsScreen.Row("Field", "Value")), false)), "",
                List.of(new MenuDetailsScreen.Action(longText("ACTIONEND"), calls::incrementAndGet, true)), longText("HINTEND")));
        var font = new Font();
        Set<String> seen = new HashSet<>();
        for (int step = 0; step < 60; step++) {
            font.draws.clear(); details.render(font, 320);
            font.draws.forEach(d -> seen.add(d.text()));
            details.update(pressed(GLFW_KEY_RIGHT));
        }
        for (String end : List.of("TITLEEND", "RESULTEND", "HEADINGEND", "ACTIONEND", "HINTEND"))
            assertTrue(seen.stream().anyMatch(line -> line.contains(end)), end);
        assertEquals(0, calls.get());
        assertTrue(details.update(pressed(GLFW_KEY_ESCAPE)));
        assertEquals(0, calls.get());
    }

    @Test void footerNeverDropsAnOverlongReadHintAndEveryFragmentCanBeRead() {
        UiMessages.selectLanguage("en");
        var font = new Font();
        Set<String> seen = new HashSet<>();
        var hints = List.of(MenuChrome.hint("Enter", longText("FIRSTEND")),
                MenuChrome.hint("F1", longText("READEND")));
        for (int tick = 0; tick < 6000; tick += 120) {
            font.draws.clear(); MenuChrome.footer(font, 320, longText("STATUSEND"), hints, false, tick);
            font.draws.forEach(d -> seen.add(d.text()));
            assertTrue(font.draws.stream().anyMatch(d -> d.text().equals("F1")), "Reader key stays visible");
            assertBounds(font.draws, 320);
        }
        for (String end : List.of("FIRSTEND", "READEND", "STATUSEND"))
            assertTrue(seen.stream().anyMatch(line -> line.contains(end)), end);
    }

    @Test void tallScriptFooterKeepsBothControlRowsWithinItsReservedArea() {
        UiMessages.selectLanguage("bn");
        var font = new Font();
        MenuStyle.footer(font, 320, "এটি একটি দীর্ঘ নির্দেশনা ".repeat(10), "পূর্ণ বিবরণ পড়ুন ".repeat(10), 180);
        assertEquals(2, font.draws.size());
        assertTrue(MenuStyle.footerTop() < 198);
        assertBounds(font.draws, 320);
        for (Draw draw : font.draws) assertTrue(draw.y() - HostText.shared().shape(draw.text(),
                MenuPixelFont.face(draw.scale()), HostTextContext.current()).metrics().above() >= MenuStyle.footerTop());
    }

    @Test void centeredTextEditorOffsetsTheNativeCompositionCaretToo() {
        UiMessages.selectLanguage("en");
        var input = new InputHandler();
        var session = InputHandlerInternalAccess.textInput(input);
        int[] rectangle = new int[4];
        session.attach(new TextInputSession.NativeHost() {
            public void reset() { }
            public void caretRectangle(int x, int y, int width, int height) {
                rectangle[0] = x; rectangle[1] = y; rectangle[2] = width; rectangle[3] = height;
            }
        });
        try {
            var editor = new MenuTextEditor("Edit", "abc", 100);
            editor.update(input);
            var font = new Font(); editor.render(font, 400);
            int x = rectangle[0];
            editor.render(font, 400, MenuContentFrame.fit(800));
            assertEquals(x + 200, rectangle[0]);
            assertEquals(1, rectangle[2]);
            assertTrue(rectangle[3] > 0);
        } finally { session.detach(); }
    }

    @Test void readerBodyCannotOverlapItsHintButtonsOrTallScriptFooter() {
        for (String tag : List.of("en", "bn", "ar")) {
            UiMessages.selectLanguage(tag);
            String hint = UiMessages.text("menu.path.details_caps") + " HINTMARKER";
            var details = new MenuDetailsScreen(new MenuDetailsScreen.Content("Reader", "",
                    UiMessages.text("settings.key.display_hud_anchor.help").repeat(20), MenuDetailsScreen.Tone.INFO,
                    List.of(), "", List.of(), hint));
            var font = new Font(); details.render(font, 320);
            Draw hintDraw = font.draws.stream().filter(d -> d.text().equals(hint)).findFirst().orElseThrow();
            int hintTop = hintDraw.y() - HostText.shared().shape(hint, TextFace.COMPACT, HostTextContext.current()).above();
            for (Draw draw : font.draws) if (draw.x() == 15 && draw.y() >= 35 && draw.y() < hintDraw.y()) {
                var metrics = HostText.shared().shape(draw.text(), TextFace.COMPACT, HostTextContext.current()).metrics();
                assertTrue(draw.y() + metrics.cellHeight() + metrics.below() <= hintTop - 2, tag + " " + draw);
            }
            assertBounds(font.draws, 320);
        }
    }

    @Test void centeredFileBrowserDetailsReceiveTheSamePhysicalPointerOffset() throws Exception {
        UiMessages.selectLanguage("en");
        var input = new InputHandler();
        var editor = new MenuTextEditor("Path", directory.toString(), 1000, null, MenuTextEditor.Mode.PATH);
        var browser = new MenuPathBrowser(directory.toString());
        var details = new MenuDetailsScreen("Path", "Full path");
        set(browser, "details", details); set(editor, "browser", browser);
        var font = new Font(); editor.render(font, 400, MenuContentFrame.fit(800));
        Draw back = font.draws.stream().filter(d -> d.text().equals("Back")).findFirst().orElseThrow();
        try (var pointers = mockStatic(com.openggf.control.MenuInput.class, CALLS_REAL_METHODS)) {
            pointers.when(() -> com.openggf.control.MenuInput.pointer(input, 800, 224))
                    .thenReturn(new com.openggf.control.MenuInput.Pointer(back.x(), back.y(), true, true, false));
            editor.update(input);
            assertSame(details, get(browser, "details"));
            pointers.when(() -> com.openggf.control.MenuInput.pointer(input, 800, 224))
                    .thenReturn(new com.openggf.control.MenuInput.Pointer(back.x() + 200, back.y(), true, true, false));
            editor.update(input);
            assertNull(get(browser, "details"));
            assertSame(browser, get(editor, "browser"));
            assertEquals(MenuTextEditor.Result.NONE, editor.consumeResult());
        }
    }

    @Test void longExtrasAndLargeListsRemainVisibleWithoutActivatingAnEntry() throws Exception {
        UiMessages.selectLanguage("en");
        var config = SonicConfigurationService.createStandalone(directory);
        var title = new MasterTitleScreen(config, new com.openggf.game.launch.LaunchProfileStore(config),
                List.of(new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2)));
        title.setStateForTest(MasterTitleScreen.State.ACTIVE);
        title.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, true);
        var calls = new AtomicInteger();
        title.setTitleEntries(java.util.stream.IntStream.range(0, 8).mapToObj(i -> i == 7 ? longText("EXTRAEND") : "Extra " + i).toList(),
                (games, index) -> calls.incrementAndGet());
        var font = new Font(); set(title, "font", font); set(title, "renderer", mock(TexturedQuadRenderer.class));
        set(title, "extrasOpen", true); set(title, "extrasIndex", 7);
        boolean tail = false;
        for (int tick = 0; tick < 3000; tick += 90) {
            set(title, "frameCounter", tick); font.draws.clear(); title.drawForRecording();
            tail |= font.draws.stream().anyMatch(d -> d.text().contains("EXTRAEND"));
            assertBounds(font.draws, 320);
        }
        assertTrue(tail); assertEquals(0, calls.get());
    }

    @Test void centeredLaunchErrorPagesReachTheTailWithoutMovingTheLocateButton() throws Exception {
        var config = SonicConfigurationService.createStandalone(directory);
        var title = new MasterTitleScreen(config, new com.openggf.game.launch.LaunchProfileStore(config),
                List.of(new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2)));
        set(title, "launchErrorDetail", "long-folder/".repeat(100) + "ERROR_TAIL");
        for (String tag : List.of("en", "bn", "ar")) {
            UiMessages.selectLanguage(tag);
            var first = title.errorPageLayout();
            boolean tail = false;
            for (int tick = 0; tick < 5000; tick += 120) {
                set(title, "frameCounter", tick);
                var page = title.errorPageLayout();
                assertEquals(first.locate(), page.locate(), "Reading must not move the live action");
                assertTrue(page.panel().y() >= 45);
                assertTrue(page.panel().y()+page.panel().h() <= MenuChrome.FOOTER_Y);
                tail |= String.join("", page.detail()).contains("ERROR_TAIL");
            }
            assertTrue(tail, tag);
        }
    }

    private static String longText(String end) { return "Readable complete words ".repeat(15) + end; }
    private static InputHandler pressed(int key) {
        var input = new InputHandler(); input.handleKeyEvent(key, GLFW_PRESS); input.refreshLogicalSnapshot(); return input;
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static Object get(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void assertBounds(List<Draw> draws, int width) {
        for (Draw draw : draws) {
            var line = HostText.shared().shape(draw.text(), MenuPixelFont.face(draw.scale()), HostTextContext.current());
            assertTrue(draw.x() >= 0 && draw.x() + line.width() <= width, draw.toString());
            assertTrue(draw.y() - line.above() >= 0 && draw.y() + line.metrics().cellHeight() + line.metrics().below() <= 224, draw.toString());
        }
    }
}
