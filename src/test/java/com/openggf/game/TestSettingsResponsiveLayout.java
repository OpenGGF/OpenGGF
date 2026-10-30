package com.openggf.game;

import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.i18n.UiMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

@Isolated
class TestSettingsResponsiveLayout {
    @TempDir Path directory;
    record Draw(String text, int x, int y, float scale) { }
    static final class RecordingFont extends PixelFont {
        final List<Draw> draws = new ArrayList<>();
        @Override public void drawText(String text, int x, int y, float scale,
                                      float r, float g, float b, float a) {
            draws.add(new Draw(text, x, y, scale));
        }
    }

    @AfterEach void restoreEnglish() { UiMessages.selectLanguage("en"); }

    @Test void ultrawideUsesTheSameContentLayoutAsSixteenNine() {
        UiMessages.selectLanguage("en");
        var font = new RecordingFont();
        var screen = new EngineSettingsScreen(SonicConfigurationService.createStandalone(directory), font);
        screen.render(400);
        var expected = List.copyOf(font.draws);
        for (int width : new int[] {528, 800}) {
            font.draws.clear();
            screen.render(width);
            assertEquals(expected, font.draws, "Content coordinates stay inside the centered 400px frame");
        }
    }

    @Test void resetQuestionPagesItsCompleteTranslatedText() throws Exception {
        UiMessages.selectLanguage("de");
        var font = new RecordingFont();
        var screen = new EngineSettingsScreen(SonicConfigurationService.createStandalone(directory), font);
        set(screen, "resetPrompt", true);
        String expected = UiMessages.text("settings.reset_category", java.util.Map.of(
                "category", UiMessages.text("settings.category.display")));
        var pages = new java.util.LinkedHashSet<String>();
        for (int tick = 0; tick < 1800; tick += 90) {
            set(screen, "ticks", tick); font.draws.clear(); screen.render(320);
            font.draws.stream().filter(draw -> draw.y() == 76).forEach(draw -> pages.add(draw.text()));
        }
        assertTrue(pages.stream().noneMatch(part -> part.endsWith("...") || part.endsWith("…")), pages.toString());
        assertEquals(expected.replaceAll("\\s+", " ").trim(), String.join(" ", pages).replaceAll("\\s+", " ").trim());
    }

    @Test void gutterClicksDoNotActivateRowsButCenteredClicksDo() {
        UiMessages.selectLanguage("en");
        var font = new RecordingFont();
        var screen = new EngineSettingsScreen(SonicConfigurationService.createStandalone(directory), font);
        screen.render(800);
        int rowY = font.draws.stream().filter(d -> d.text().equals("Window mode")).findFirst().orElseThrow().y();
        assertFalse(screen.updatePointer(new MenuInput.Pointer(20, rowY, true, true, false)));
        assertTrue(screen.updatePointer(new MenuInput.Pointer(220, rowY, true, true, false)));
    }

    @Test void childButtonsUseTheSameCenteredPointerFrame() {
        UiMessages.selectLanguage("en");
        var frame = MenuContentFrame.fit(800);
        var font = new RecordingFont();
        var selected = new ArrayList<String>();
        var input = new InputHandler();
        var picker = new LanguagePickerScreen("auto", selected::add);
        picker.render(font, frame.width());
        assertFalse(picker.update(input, frame.local(new MenuInput.Pointer(15, 44, true, true, false))));
        assertTrue(selected.isEmpty());
        assertTrue(picker.update(input, frame.local(new MenuInput.Pointer(215, 44, true, true, false))));
        assertEquals(List.of("auto"), selected);

        var details = new MenuDetailsScreen("Details", "Full text");
        font.draws.clear(); details.render(font, frame.width());
        Draw back = font.draws.stream().filter(d -> d.text().equals("Back")).findFirst().orElseThrow();
        assertFalse(details.update(input, frame.local(new MenuInput.Pointer(back.x(), back.y(), true, true, false))));
        assertTrue(details.update(input, frame.local(new MenuInput.Pointer(frame.left() + back.x(), back.y(), true, true, false))));
    }

    @Test void ordinaryRowsWrapWithoutEllipsesInEveryLanguageAndWidth() {
        var config = SonicConfigurationService.createStandalone(directory);
        List<String> failures = new ArrayList<>();
        for (var language : UiMessages.languages()) {
            UiMessages.selectLanguage(language.tag());
            for (int width : new int[] {320, 352, 400, 528, 800}) {
                var font = new RecordingFont();
                var screen = new EngineSettingsScreen(config, font);
                int count = new EngineSettingsDraft(config).keys(EngineSettingsDraft.Category.DISPLAY).size();
                for (int selected = 0; selected < count; selected++) {
                    try { set(screen, "row", selected); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                    font.draws.clear(); screen.render(width);
                    for (Draw draw : font.draws) {
                    if (draw.y() < 52 || draw.y() >= 150) continue;
                    if (draw.text().endsWith("...")) failures.add(language.tag() + "/" + width + " " + draw.text());
                    int measured = HostText.shared().width(draw.text(), MenuPixelFont.face(draw.scale()), HostTextContext.current());
                    assertTrue(draw.x() >= 0 && draw.x() + measured <= Math.min(width, 400), draw.toString());
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }

    @Test void veryLongValuesReachTheirLastPageWithoutEllipses() throws Exception {
        UiMessages.selectLanguage("en");
        var config = SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(SonicConfiguration.CAPTURE_OUTPUT_DIR, "segment/".repeat(60) + "TAIL_SENTINEL");
        var font = new RecordingFont();
        var screen = new EngineSettingsScreen(config, font);
        var keys = new EngineSettingsDraft(config).keys(EngineSettingsDraft.Category.RECORDING);
        set(screen, "category", 5);
        set(screen, "row", keys.indexOf(SonicConfiguration.CAPTURE_OUTPUT_DIR));
        set(screen, "scroll", keys.indexOf(SonicConfiguration.CAPTURE_OUTPUT_DIR));
        var input = new InputHandler();
        boolean tail = false;
        for (int tick = 0; tick < 2200; tick++) {
            screen.update(input);
            if (tick % 90 != 0) continue;
            font.draws.clear();
            screen.render(320);
            var rows = font.draws.stream().filter(d -> d.y() >= 52 && d.y() < 150).toList();
            assertTrue(rows.stream().noneMatch(d -> d.text().endsWith("...")));
            tail |= rows.stream().anyMatch(d -> d.text().contains("TAIL_SENTINEL"));
        }
        assertTrue(tail, "Focused paging must expose the end of a value longer than the row viewport");
        pulse(screen, input, GLFW_KEY_F1);
        List<String> details = new ArrayList<>();
        for (int step = 0; step < 40; step++) {
            font.draws.clear(); screen.render(800);
            font.draws.stream().filter(d -> d.y() >= 35 && d.y() < 185).forEach(d -> details.add(d.text()));
            pulse(screen, input, GLFW_KEY_DOWN);
        }
        assertTrue(String.join("", new java.util.LinkedHashSet<>(details)).contains("TAIL_SENTINEL"), details.toString());
        assertTrue(details.stream().anyMatch(line -> line.equals(UiMessages.text("settings.screen.default"))));
    }

    private static void pulse(EngineSettingsScreen screen, InputHandler input, int key) {
        input.handleKeyEvent(key, GLFW_PRESS); input.refreshLogicalSnapshot(); screen.update(input); input.update();
        input.handleKeyEvent(key, GLFW_RELEASE); input.refreshLogicalSnapshot(); screen.update(input); input.update();
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
