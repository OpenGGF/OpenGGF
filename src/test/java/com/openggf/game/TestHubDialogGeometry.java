package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.TexturedQuadRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.mock;

/**
 * Spacing and icon geometry of the hub and its dialogs at 320 and 400, read from what the real
 * owners draw (text cells and filled rectangles), not from their constants: the hub's rhythm from
 * the card to the footer, the page dots, the paused badge's icon, the quit and end prompts, the
 * missing-ROM page and the Advanced rows. Each pointer target is the drawn frame.
 */
class TestHubDialogGeometry {
    @TempDir Path directory;
    private final Recorder rec = new Recorder();

    // ------------------------------------------------------------------ hub

    @ParameterizedTest @ValueSource(ints = {320, 400})
    void everyHubStateKeepsOneRhythmFromTheCardToTheFooter(int width) throws Exception {
        record State(String name, MasterTitleScreen screen, boolean legend) { }
        var plain = screen(width);
        var overflow = screen(width);
        crowd(overflow, new LaunchProfile(false, "s1", true, "WIDE_16_10", "sonic", "tails"), 12);
        press(overflow, GLFW_KEY_DOWN);
        var donor = screen(width);
        crowd(donor, new LaunchProfile(true, "s3k", true, "global", "sonic", "tails"), 2);
        press(donor, GLFW_KEY_DOWN);
        var paused = pausedScreen(width);
        var pausedSettings = pausedScreen(width);
        press(pausedSettings, GLFW_KEY_UP);
        press(pausedSettings, GLFW_KEY_RIGHT);
        var missing = screen(width);
        missing.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, false);
        for (State state : List.of(new State("card", plain, false), new State("overflow on Start", overflow, true),
                new State("3&K donor on Start", donor, true), new State("paused card", paused, false),
                new State("paused, Settings tab", pausedSettings, true), new State("missing ROM", missing, false))) {
            String at = state.name() + " at " + width;
            draw(state.screen());
            var layout = state.screen().hubLayout();
            var card = layout.cards().stream().filter(slot -> slot.offset() == 0).findFirst().orElseThrow().rect();
            int frameBottom = card.y() + card.h() + 1; // the focused card's ring, reserved in every state
            Text name = rec.texts(t -> !t.light() && t.y() > card.y() + card.h() && t.y() < card.y() + card.h() + 12)
                    .getFirst();
            assertEquals(4, name.inkTop() - frameBottom - 1, "card frame to title, " + at);
            int slot = name.inkBottom() + 5;
            if (layout.chips().isEmpty()) {
                Text line = rec.texts(t -> t.light() && t.y() > name.y() && t.y() < name.y() + 20).getFirst();
                assertEquals(slot + 2, line.y(), "the line in the chips' row sits where a chip label would, " + at);
            }
            for (var chip : layout.chips()) {
                assertEquals(slot, chip.y(), "title to chips, " + at);
                assertEquals(MenuChrome.CHIP_H, chip.h(), at);
                assertTrue(rec.fills.stream().anyMatch(f -> f.x() == chip.x() && f.y() == chip.y() && f.h() == chip.h()),
                        "chips are drawn where they are laid out, " + at);
                Text label = rec.texts(t -> t.x() == chip.x() + 5 && t.y() >= chip.y() && t.y() < chip.y() + chip.h()).getFirst();
                assertEquals(2, label.y() - chip.y(), "two rows above the chip label, " + at);
                assertEquals(2, chip.y() + chip.h() - 1 - label.inkBottom(), "two rows below it, " + at);
            }
            var dots = layout.dots();
            assertEquals(3, dots.y() - (slot + MenuChrome.CHIP_H), "chips to page dots, " + at);
            var actions = layout.actions();
            for (var action : actions) assertEquals(3, action.y() - 2 - (dots.y() + dots.h()), "dots to the buttons' frame, " + at);
            int ringBottom = actions.getFirst().y() + actions.getFirst().h() + 1;
            List<Text> lines = rec.texts(t -> t.light() && t.y() > ringBottom && t.y() < MenuChrome.FOOTER_Y);
            assertFalse(lines.isEmpty(), at);
            int y = lines.getFirst().y();
            assertEquals(4, y - ringBottom - 1, "buttons' frame to the line under them, " + at);
            var rows = lines.stream().map(Text::y).distinct().sorted().toList();
            assertEquals(state.legend() ? 2 : 1, rows.size(), "lines under the buttons " + lines + ", " + at);
            // The legend is a statement of its own: a 12-row pitch keeps four clear rows under the
            // line's descenders, and its capitals end four clear rows above the footer.
            if (state.legend()) {
                assertEquals(12, rows.get(1) - rows.get(0), "line to legend, " + at);
                assertEquals(4, MenuChrome.FOOTER_Y - (rows.get(1) + 6) - 1, "legend to the footer, " + at);
            }
        }
    }

    @ParameterizedTest @ValueSource(ints = {320, 400})
    void pageDotsAreRingedSoTheyReadOverSkyAndCloud(int width) throws Exception {
        for (boolean paused : new boolean[] {false, true}) {
            var screen = paused ? pausedScreen(width) : screen(width);
            draw(screen);
            int n = screen.entriesForTest().size();
            var dots = screen.hubLayout().dots();
            List<Fill> rings = rec.fills(f -> same(f, MenuChrome.INK) && f.a() > .8f && inside(f, dots));
            List<Fill> gold = rec.fills(f -> same(f, MenuChrome.GOLD) && inside(f, dots));
            List<Fill> dim = rec.fills(f -> same(f, MenuChrome.DIM) && inside(f, dots));
            assertEquals(3 * n, rings.size(), "each dot has a one-pixel ring (top/bottom band and two sides)");
            assertEquals(2, gold.size(), "one selected dot");
            assertEquals(8, gold.stream().mapToInt(Fill::w).max().orElseThrow(), "the selected dot is a wider pill");
            assertEquals(2 * (n - 1), dim.size());
            assertEquals(4, dim.stream().mapToInt(Fill::w).max().orElseThrow());
            int left = dots.x(), right = width - (dots.x() + dots.w());
            assertTrue(Math.abs(left - right) <= 1, "dots centred: " + left + "/" + right);
            // The ring is what separates a dot from a light cloud; the fill from dark sky.
            assertTrue(contrast(MenuChrome.INK, MenuChrome.DIM) >= 4.5, "inactive dot edge");
            assertTrue(contrast(MenuChrome.INK, MenuChrome.GOLD) >= 4.5, "selected dot edge");
        }
    }

    @ParameterizedTest @ValueSource(ints = {320, 400})
    void thePausedBadgeUsesTheSharedPauseIconBesideItsLabel(int width) throws Exception {
        var screen = pausedScreen(width);
        draw(screen);
        Text label = rec.texts(t -> t.text().equals("PAUSED")).getFirst();
        List<Fill> icon = rec.fills(f -> same(f, MenuChrome.GOLD) && f.h() == 1 && f.y() >= label.y() && f.y() <= label.y() + 6
                && f.x() < label.x() && f.x() > label.x() - 12);
        int x = icon.stream().mapToInt(Fill::x).min().orElseThrow() - 1; // the icon cell's first column is clear
        assertEquals(mask(MenuChrome.Icon.PAUSE), maskOf(icon, x, label.y()), "the shared PAUSE icon, on the label's rows");
        assertEquals(label.x(), x + MenuChrome.ICON_SIZE + 2, "two columns from the icon cell to the label");
        Fill badge = rec.fills(f -> same(f, MenuChrome.GOLD) && f.h() == 1 && f.y() == label.y() - 2 && f.w() > 30).getFirst();
        int inkLeft = x + 1, inkRight = label.x() + MenuChrome.compactWidth("PAUSED") - 2;
        assertEquals(inkLeft - badge.x(), badge.x() + badge.w() - 1 - inkRight, "the badge's padding is even");
    }

    // ------------------------------------------------------------------ prompts

    @ParameterizedTest @ValueSource(ints = {320, 400})
    void choicePromptsLeadEachOptionWithTheIconOfItsConsequence(int width) throws Exception {
        var quit = screen(width);
        press(quit, GLFW_KEY_ESCAPE);
        assertTrue(quit.isQuitPromptOpenForTest());
        var pausedQuit = pausedScreen(width);
        press(pausedQuit, GLFW_KEY_ESCAPE);
        var end = pausedScreen(width);
        press(end, GLFW_KEY_RIGHT);
        press(end, GLFW_KEY_ENTER);
        assertTrue(end.isEndPromptOpenForTest());
        for (var screen : List.of(quit, pausedQuit, end)) {
            draw(screen);
            // Only what the prompt draws over the dimmed hub.
            Text title = rec.texts(t -> !t.light() && t.text().endsWith("?")).getFirst();
            Predicate<Text> prompt = t -> t.order() > title.order();
            Text detail = rec.texts(prompt.and(t -> t.light() && t.y() > title.y() && t.y() < title.y() + 16)).getFirst();
            assertEquals(4, detail.y() - title.inkBottom() - 1, "the detail is the title's subtitle");
            for (int option = 0; option < 2; option++) {
                var bounds = screen.choiceOptionBounds(option);
                Text label = rec.texts(prompt.and(t -> t.light() && t.y() > bounds.y() && t.y() < bounds.y() + bounds.h())).getFirst();
                float[] colour = option == 0 ? MenuChrome.GREEN : MenuChrome.AMBER;
                MenuChrome.Icon expected = option == 0 ? MenuChrome.Icon.CHECK : MenuChrome.Icon.WARNING;
                List<Fill> icon = rec.fills(f -> f.order() > title.order() && same(f, colour) && f.h() == 1
                        && f.y() >= label.y() && f.y() <= label.y() + 6 && f.x() >= label.x() - 11 && f.x() < label.x());
                int x = label.x() - 4 - MenuChrome.ICON_SIZE;
                assertEquals(mask(expected), maskOf(icon, x, label.y()), label.text() + ": icon four columns left, on the label's rows");
                int run = MenuChrome.ICON_SIZE + 4 + MenuChrome.compactWidth(label.text());
                assertTrue(Math.abs((x - bounds.x()) - (bounds.x() + bounds.w() - (x + run))) <= 1, "icon and label centred as one run");
                if (option == 0) assertEquals(5, bounds.y() - 2 - (detail.y() + 7) - 1, "the detail is clear of the first option's frame");
            }
        }
        // The pointer uses the drawn options: hovering the consequential one selects it, a click on Stay closes.
        var mouse = screen(width);
        mouse.setPointerMapperForTest((x, y) -> new int[] {x.intValue(), y.intValue(), 1});
        press(mouse, GLFW_KEY_ESCAPE);
        var input = new InputHandler();
        int[] quitCentre = centre(mouse.choiceOptionBounds(1));
        input.handleMouseMove(quitCentre[0], quitCentre[1]); mouse.update(input); input.update();
        draw(mouse);
        assertTrue(rec.fills(f -> same(f, MenuChrome.FOCUS_FILL) && f.y() == mouse.choiceOptionBounds(1).y()).size() == 1,
                "hovering Quit to desktop focuses the drawn option");
        click(mouse, centre(mouse.choiceOptionBounds(0)));
        assertFalse(mouse.isQuitPromptOpenForTest(), "Stay in OpenGGF closes the prompt");
    }

    // ------------------------------------------------------------------ missing ROM page

    @ParameterizedTest @ValueSource(ints = {320, 400})
    void theMissingRomPageFitsItsContentAndThePointerUsesTheDrawnButton(int width) throws Exception {
        String path = "/media/library/Sega Mega Drive and Genesis collection/Sonic The Hedgehog 2 (World) (Rev A).gen";
        var screen = screen(width);
        configOf(screen).setConfigValue(SonicConfiguration.SONIC_2_ROM, path);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, false);
        screen.setPointerMapperForTest((x, y) -> new int[] {x.intValue(), y.intValue(), 1});
        screen.showRomLoadError("s2");
        draw(screen);
        var page = screen.errorPageLayout();
        assertEquals(path, String.join("", page.detail()), "the path is shown whole");
        assertTrue(page.detail().size() >= 2, "a long path wraps at " + width);
        for (int i = 0; i < page.detail().size() - 1; i++) {
            String line = page.detail().get(i);
            assertTrue(line.endsWith("/"), "a line breaks after a folder separator: " + page.detail());
        }
        assertEquals("Sonic The Hedgehog 2 (World) (Rev A).gen", page.detail().getLast(), "the file name moves whole");
        var panel = page.panel();
        Text headline = rec.texts(t -> t.text().equals("ROM NOT FOUND")).getFirst();
        Text note = rec.texts(t -> t.text().startsWith("Or set its path")).getFirst();
        assertEquals(9, headline.inkTop() - panel.y(), "nine clear rows above the headline");
        assertEquals(9, panel.y() + panel.h() - 1 - (note.y() + 7), "nine below the last line");
        assertEquals(5, page.detailY() - headline.inkBottom() - 1);
        var locate = page.locate();
        assertNotNull(locate);
        int lastDetail = page.detailY() + (page.detail().size() - 1) * 10 + 7;
        assertEquals(5, locate.y() - 2 - lastDetail - 1, "detail to the Locate ROM frame");
        assertEquals(4, note.y() - (locate.y() + locate.h() + 1) - 1, "Locate ROM frame to the note");
        assertTrue(rec.fills.stream().anyMatch(f -> same(f, MenuChrome.FOCUS_FILL) && f.x() == locate.x()
                && f.y() == locate.y() && f.w() == locate.w() && f.h() == locate.h()), "Locate ROM is drawn where it is hit");
        int above = panel.y() - 45, below = MenuChrome.FOOTER_Y - (panel.y() + panel.h());
        assertTrue(Math.abs(above - below) <= 1, "the panel is centred under the game's name: " + above + "/" + below);
        // The path's own rows do nothing; the drawn button opens the locator.
        click(screen, new int[] {width / 2, page.detailY() + 3});
        assertTrue(screen.errorPageOpenForTest());
        click(screen, centre(locate));
        assertTrue(screen.isRomLocatorOpenForTest(), "the drawn Locate ROM button opens the locator");
    }

    @Test void anErrorWithoutALocatorEndsNineRowsUnderItsDetail() throws Exception {
        var screen = screen(320);
        screen.setRomAvailableForTest(MasterTitleScreen.GameEntry.SONIC_2, false);
        screen.showModLaunchError("s2");
        draw(screen);
        var page = screen.errorPageLayout();
        assertEquals(null, page.locate());
        Text headline = rec.texts(t -> t.text().equals("MOD LAUNCH FAILED")).getFirst();
        int last = page.detailY() + (page.detail().size() - 1) * 10 + 7;
        assertEquals(9, headline.inkTop() - page.panel().y());
        assertEquals(9, page.panel().y() + page.panel().h() - 1 - last, "the panel ends nine rows under its detail");
    }

    // ------------------------------------------------------------------ Advanced rows

    @ParameterizedTest @ValueSource(ints = {320, 400})
    void advancedRowsKeepTheNoteClearOfTheNameAndTheRules(int width) throws Exception {
        var screen = screen(width);
        screen.setEditorProjectsOpenHandler(() -> { });
        screen.contentImportOpenHandler = () -> { };
        screen.setPointerMapperForTest((x, y) -> new int[] {x.intValue(), y.intValue(), 1});
        // The production route: up to the tabs, across to Advanced, Enter.
        press(screen, GLFW_KEY_UP);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_RIGHT);
        press(screen, GLFW_KEY_ENTER);
        draw(screen);
        List<Text> names = rec.texts(t -> t.light() && t.x() == 31 && rec.colour(t, MenuChrome.WHITE, MenuChrome.TEXT));
        assertEquals(5, names.size(), "five tools: " + names);
        List<Fill> rules = rec.fills(f -> same(f, MenuChrome.EDGE) && f.h() == 1 && f.x() == 10 && f.w() == width - 20);
        assertEquals(4, rules.size(), "a rule under every row but the focused one");
        int checked = 0;
        for (int i = 0; i < names.size(); i++) {
            Text label = names.get(i);
            Text note = rec.texts(t -> t.x() == 31 && t.y() == label.y() + 10).getFirst();
            assertEquals(2, note.inkTop() - (label.y() + 7) - 1, "one pitch of 10: the name's descender row and two clear rows");
            int row = label.y() - MenuChrome.ACTION_LABEL_DY;
            List<Fill> icon = rec.fills(f -> f.h() == 1 && f.x() >= 17 && f.x() < 24 && f.y() >= row + 10 && f.y() < row + 17
                    && (same(f, MenuChrome.GOLD) || same(f, MenuChrome.CYAN)));
            assertFalse(icon.isEmpty(), "row " + i + " has its icon");
            int iconTop = icon.stream().mapToInt(Fill::y).min().orElseThrow();
            int iconBottom = icon.stream().mapToInt(Fill::y).max().orElseThrow();
            int pairTop = label.y(), pairBottom = note.y() + 6;
            assertTrue(Math.abs((iconTop - pairTop) - (pairBottom - iconBottom)) <= 1, "icon centred on name and note, row " + i);
            for (Fill rule : rules) {
                if (rule.y() < label.y() && rule.y() > label.y() - 10) {
                    assertEquals(5, label.y() - rule.y() - 1, "rule to the name");
                    checked++;
                }
                if (rule.y() > note.y() && rule.y() < note.y() + 16) {
                    assertEquals(5, rule.y() - (note.y() + 6) - 1, "note to the rule");
                    checked++;
                }
            }
            int[] centre = {width / 2, row + MenuChrome.ACTION_ROW_H / 2};
            var input = new InputHandler();
            input.handleMouseMove(centre[0], centre[1]); screen.update(input); input.update();
            draw(screen);
            int focusedRow = row;
            assertEquals(1, rec.fills(f -> same(f, MenuChrome.FOCUS_FILL) && f.y() == focusedRow && f.h() == MenuChrome.ACTION_ROW_H).size(),
                    "hovering row " + i + " focuses the drawn row");
        }
        // Four rules (the focused first row has its frame instead): each under a note, three above a name.
        assertEquals(7, checked, "every rule was measured from the note above it and the name below it");
    }

    // ------------------------------------------------------------------ helpers

    private MasterTitleScreen screen(int width) throws Exception {
        var config = SonicConfigurationService.createStandalone(directory.resolve("w" + width + "-" + System.nanoTime()));
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
        var screen = new MasterTitleScreen(config);
        screen.setStateForTest(MasterTitleScreen.State.ACTIVE);
        for (var game : MasterTitleScreen.GameEntry.values()) screen.setRomAvailableForTest(game, true);
        screen.setViewportWidth(width);
        install(screen);
        return screen;
    }

    private MasterTitleScreen pausedScreen(int width) throws Exception {
        var screen = screen(width);
        screen.setSuspendedGame(new MasterTitleEntry.Suspended(new MasterTitleEntry.Stock(MasterTitleScreen.GameEntry.SONIC_2),
                "Emerald Hill, Act 1", true), 0, 0, null);
        return screen;
    }

    private void crowd(MasterTitleScreen screen, LaunchProfile profile, int mods) throws Exception {
        new LaunchProfileStore(configOf(screen)).save(MasterTitleScreen.GameEntry.SONIC_2, profile);
        screen.setEnabledModsByGame(Map.of(MasterTitleScreen.GameEntry.SONIC_2,
                IntStream.rangeClosed(1, mods).mapToObj(i -> "Mod " + i).toList()));
    }

    private void install(MasterTitleScreen screen) throws Exception {
        var renderer = MasterTitleScreen.class.getDeclaredField("renderer");
        renderer.setAccessible(true);
        renderer.set(screen, mock(TexturedQuadRenderer.class));
        var font = MasterTitleScreen.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(screen, rec.font);
    }

    private static SonicConfigurationService configOf(MasterTitleScreen screen) throws Exception {
        var field = MasterTitleScreen.class.getDeclaredField("configService");
        field.setAccessible(true);
        return (SonicConfigurationService) field.get(screen);
    }

    private void draw(MasterTitleScreen screen) {
        rec.clear();
        screen.drawForRecording();
    }

    private static void press(MasterTitleScreen screen, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS);
        screen.update(input);
        input.handleKeyEvent(key, GLFW_RELEASE);
        input.update();
        screen.update(input);
    }

    private static void click(MasterTitleScreen screen, int[] at) {
        var input = new InputHandler();
        input.handleMouseMove(at[0], at[1]); screen.update(input); input.update();
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS); screen.update(input); input.update();
        input.handleMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE); screen.update(input); input.update();
    }

    private static int[] centre(MasterTitleScreen.Rect rect) {
        return new int[] {rect.x() + rect.w() / 2, rect.y() + rect.h() / 2};
    }

    private static boolean inside(Fill f, MasterTitleScreen.Rect r) {
        return f.x() >= r.x() && f.y() >= r.y() && f.x() + f.w() <= r.x() + r.w() && f.y() + f.h() <= r.y() + r.h();
    }

    static boolean same(Fill f, float[] c) {
        return Math.abs(f.r() - c[0]) < 1e-4 && Math.abs(f.g() - c[1]) < 1e-4 && Math.abs(f.b() - c[2]) < 1e-4;
    }

    /** An icon's rows as drawn by {@link MenuChrome#icon}: one bit per column, most significant at the left. */
    static List<Integer> mask(MenuChrome.Icon icon) {
        var probe = new Recorder();
        MenuChrome.icon(probe.font, icon, 0, 0, MenuChrome.WHITE);
        return maskOf(probe.fills, 0, 0);
    }

    static List<Integer> maskOf(List<Fill> fills, int x, int y) {
        Integer[] rows = new Integer[MenuChrome.ICON_SIZE];
        Arrays.fill(rows, 0);
        for (Fill f : fills) {
            int row = f.y() - y;
            if (row < 0 || row >= rows.length) continue;
            for (int col = f.x() - x; col < f.x() - x + f.w(); col++) {
                if (col >= 0 && col < MenuChrome.ICON_SIZE) rows[row] |= 1 << (MenuChrome.ICON_SIZE - 1 - col);
            }
        }
        return List.of(rows);
    }

    private static double contrast(float[] a, float[] b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + .05) / (Math.min(la, lb) + .05);
    }

    private static double luminance(float[] c) {
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) v[i] = c[i] <= .03928 ? c[i] / 12.92 : Math.pow((c[i] + .055) / 1.055, 2.4);
        return .2126 * v[0] + .7152 * v[1] + .0722 * v[2];
    }

    /** A text cell as drawn: light cells carry capitals on rows 0-6, bold cells on rows 1-7. */
    record Text(String text, int x, int y, float scale, float r, float g, float b, int order) {
        boolean light() { return scale < 1f; }
        int inkTop() { return light() ? y : y + 1; }
        int inkBottom() { return light() ? y + 6 : y + 7; }
    }

    record Fill(int x, int y, int w, int h, float r, float g, float b, float a, int order) { }

    static final class Recorder {
        final List<Text> texts = new ArrayList<>();
        final List<Fill> fills = new ArrayList<>();
        /** Draw order across text and fills, so a dialog's primitives can be told from the page under it. */
        private int order;
        final MenuPixelFont font = mock(MenuPixelFont.class, invocation -> {
            Object[] args = invocation.getArguments();
            String method = invocation.getMethod().getName();
            if (method.equals("drawText") && args.length == 8) {
                texts.add(new Text((String) args[0], Math.round(((Number) args[1]).floatValue()),
                        Math.round(((Number) args[2]).floatValue()), ((Number) args[3]).floatValue(),
                        (float) args[4], (float) args[5], (float) args[6], order++));
            } else if (method.equals("fillRect")) {
                fills.add(new Fill(((Number) args[0]).intValue(), ((Number) args[1]).intValue(), ((Number) args[2]).intValue(),
                        ((Number) args[3]).intValue(), (float) args[4], (float) args[5], (float) args[6], (float) args[7], order++));
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });

        void clear() { texts.clear(); fills.clear(); order = 0; }
        List<Text> texts(Predicate<Text> filter) { return texts.stream().filter(filter).toList(); }
        List<Fill> fills(Predicate<Fill> filter) { return fills.stream().filter(filter).toList(); }
        boolean colour(Text t, float[]... colours) {
            for (float[] c : colours) if (Math.abs(t.r() - c[0]) < 1e-4 && Math.abs(t.g() - c[1]) < 1e-4) return true;
            return false;
        }
    }
}
