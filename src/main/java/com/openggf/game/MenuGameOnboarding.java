package com.openggf.game;

import com.openggf.i18n.UiMessages;
import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.data.PhysicalImage;
import com.openggf.data.RomGame;
import com.openggf.data.RomImageClassifier;
import com.openggf.graphics.PixelFont;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** First-run game discovery. All reads are cancellable; settings publish only on the UI thread. */
public final class MenuGameOnboarding {
    private final SonicConfigurationService config;
    private MenuPathBrowser browser;
    private MenuLoadTask<DiscoveryResult> discovery;
    private MasterTitlePointer pointer;
    private InputHandler menuInput;
    private int width = 320;
    private int choice;
    private boolean scanning;
    private boolean completed;
    private boolean cancelled;
    private String message;
    private String scanNotice;
    private String pendingPath;
    private MenuDetailsScreen details;
    private Map<RomGame, Path> found = Map.of();
    private Path reading;
    private int frame;

    public MenuGameOnboarding(SonicConfigurationService config) { this.config = config; }

    public void pointer(int x, int y, boolean moved, boolean click, boolean backClick, int wheel) {
        pointer = new MasterTitlePointer(x, y, moved, click, backClick, wheel);
        if (browser != null) browser.pointer(x, y, moved, click, backClick, wheel);
    }

    public void update(InputHandler input) {
        MasterTitlePointer mouse = pointer;
        pointer = null;
        if (input == null || completed || cancelled) return;
        menuInput = input;
        frame++;
        if (details != null) { if (details.update(input) || mouse != null && mouse.backClick()) details = null; return; }
        if (browser != null) {
            browser.update(input);
            if (browser.cancelled()) { browser = null; return; }
            if (browser.selected() != null) {
                Path selected = Path.of(browser.selected());
                pendingPath = selected.toString();
                browser = null;
                reading = selected;
                discovery = new MenuLoadTask<>(() -> discover(selected, scanning));
            }
            return;
        }
        if (MenuInput.textBack(input) || mouse != null && (mouse.backClick()
                || mouse.click() && mouse.over(0, MenuChrome.FOOTER_Y, width, MenuChrome.FOOTER_H))) {
            cancel(); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return;
        }
        if (MenuInput.textDetails(input)) {
            details = new MenuDetailsScreen(UiMessages.text("menu.onboarding.add_your_games_caps"), detailsText());
            return;
        }
        if (discovery != null) {
            if (!discovery.done()) return;
            try {
                DiscoveryResult result = discovery.result();
                found = result.games();
                scanNotice = result.notice();
                if (found.isEmpty()) message = scanning ? UiMessages.text("menu.onboarding.no_supported_games_found") : UiMessages.text("menu.onboarding.not_a_supported_rom_image");
                else {
                    EngineSettingsDraft draft = new EngineSettingsDraft(config);
                    for (var entry : found.entrySet()) draft.set(entry.getKey().configurationKey(), entry.getValue().toString());
                    Path first = found.values().iterator().next();
                    draft.set(SonicConfiguration.ROMS_DIRECTORY, first.getParent().toString());
                    try { draft.apply(); }
                    catch (IOException | IllegalArgumentException e) {
                        found = Map.of();
                        message = UiMessages.text("menu.onboarding.could_not_save_check_settings_file_access");
                        discovery = null;
                        return;
                    }
                    message = UiMessages.text("menu.onboarding.games_added", java.util.Map.of("count", found.size()));
                    MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
                }
            } catch (Exception e) { found = Map.of(); scanNotice = UiMessages.text("menu.onboarding.check_access_or_choose_another_location");
                message = scanning ? UiMessages.text("menu.onboarding.could_not_read_this_folder") : UiMessages.text("menu.onboarding.could_not_read_this_file"); }
            discovery = null;
            return;
        }
        Layout layout = layout();
        if (!found.isEmpty()) {
            if (MenuInput.textAccept(input) || mouse != null && mouse.click() && over(mouse, layout.button())) completed = true;
            return;
        }
        boolean click = false;
        if (mouse != null) for (int i = 0; i < 2; i++) {
            if (over(mouse, layout.choices().get(i)) && (mouse.moved() || mouse.click())) {
                choice = i; click = mouse.click();
            }
        }
        if (MenuInput.textUp(input) || MenuInput.textDown(input)) { choice = 1 - choice; MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE); }
        if (MenuInput.textAccept(input) || click) {
            scanning = choice == 1;
            String initial = config.getString(SonicConfiguration.ROMS_DIRECTORY);
            browser = scanning ? MenuPathBrowser.folder(initial, UiMessages.text("menu.onboarding.scan_folder_caps"), UiMessages.text("menu.onboarding.choose_a_folder_containing_your_games"))
                    : new MenuPathBrowser(initial, true, UiMessages.text("menu.onboarding.locate_game_caps"), UiMessages.text("menu.onboarding.choose_a_rom_file_any_filename"));
            message = null;
            scanNotice = null;
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        }
    }

    /** Uses the runtime classifier, including logical games within lock-on images; extensions never gate identity. */
    record DiscoveryResult(Map<RomGame, Path> games, boolean limitReached, boolean unreadable) {
        DiscoveryResult { games = java.util.Collections.unmodifiableMap(new EnumMap<>(games)); }
        /**
         * What the scan could not cover, said only when it matters: once every supported game is
         * found, nothing the scan skipped can add another, so there is no notice. The scan stops
         * after 20,000 entries or eight folders deep; unreadable files and folders are skipped.
         */
        String notice() {
            if (games.size() == RomGame.values().length || !limitReached && !unreadable) return null;
            if (limitReached && unreadable) return "Stopped early, skipped unreadable files; "
                    + (games.isEmpty() ? "scan their folder" : "scan the rest");
            if (limitReached) return "Stopped early; " + (games.isEmpty() ? "scan the folder that holds them" : "scan the folder with the rest");
            return games.isEmpty() ? "Some files could not be read; check access to them"
                    : "Skipped files that could not be read; check access to them";
        }
    }

    static DiscoveryResult discover(Path selection, boolean folder) throws IOException, InterruptedException {
        return discover(selection, folder, 20000);
    }

    static DiscoveryResult discover(Path selection, boolean folder, int maxEntries) throws IOException {
        Map<RomGame, Path> result = new EnumMap<>(RomGame.class);
        if (!folder) { identify(selection, result); return new DiscoveryResult(result, false, false); }
        if (!Files.isDirectory(selection)) throw new IOException(UiMessages.text("menu.onboarding.choose_a_folder"));
        // No directory symlinks. Limits and denied subtrees preserve discoveries already made.
        boolean[] limited = {false};
        boolean[] unreadable = {false};
        Files.walkFileTree(selection, EnumSet.noneOf(java.nio.file.FileVisitOption.class), 8,
                new SimpleFileVisitor<>() {
                    private int visited;
                    private FileVisitResult boundary() throws IOException {
                        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException(UiMessages.text("menu.menugameonboarding.scan_cancelled"));
                        if (result.size() == RomGame.values().length) return FileVisitResult.TERMINATE;
                        if (++visited > maxEntries) {
                            limited[0] = true;
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return boundary();
                    }
                    @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        FileVisitResult next = boundary();
                        if (next == FileVisitResult.TERMINATE) return next;
                        if (attrs.isDirectory()) limited[0] = true; // Maximum walk depth reached.
                        if (attrs.isRegularFile()) {
                            try { identify(path, result); }
                            catch (IOException | SecurityException failure) { unreadable[0] = true; }
                        }
                        return result.size() == RomGame.values().length ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                        if (file.equals(selection)) throw failure;
                        unreadable[0] = true;
                        return boundary();
                    }
                    @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                        if (failure != null) {
                            if (dir.equals(selection) && result.isEmpty()) throw failure;
                            unreadable[0] = true;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        return new DiscoveryResult(result, limited[0], unreadable[0]);
    }

    private static void identify(Path path, Map<RomGame, Path> result) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException(UiMessages.text("menu.onboarding.choose_a_rom_file"));
        // Probe just sizes and headers, exactly as MenuRomLocator and the runtime catalogue do.
        PhysicalImage image = PhysicalImage.probe(path);
        for (RomGame game : RomGame.values()) if (RomImageClassifier.windowOf(image, game.identity()).isPresent())
            result.putIfAbsent(game, path.toAbsolutePath().normalize());
    }

    public boolean completed() { return completed; }
    public boolean cancelled() { return cancelled; }
    public void cancel() {
        if (browser != null) { browser.cancel(); browser = null; }
        if (discovery != null) { discovery.cancel(); discovery = null; }
        pointer = null;
        details = null;
        cancelled = true;
    }

    /** A rectangle in menu pixels; the pointer hit-tests what {@link #render} draws. */
    record Box(int x, int y, int w, int h) { }

    /**
     * Each state's content as one group centred between the header and the footer. Offsets are
     * worked out from the group's first row, so the height that centres it is the one drawn:
     * light lines are 7 ink rows plus a descender row, badges 10 rows, the action rows and the
     * Continue button carry a 2-row focus frame, and every gap below names its clear rows.
     */
    record Layout(int top, List<Box> choices, Box button, int listY, int noticeY, int messageY) { }

    private static final int BODY_TOP = MenuChrome.HEADER_H, BODY_BOTTOM = MenuChrome.FOOTER_Y;
    /** A light line's ink and descender rows, a paragraph's line pitch and the found list's row pitch. */
    private static final int LINE = 8, PARAGRAPH_PITCH = 10, FOUND_PITCH = 15, BUTTON_H = 20, FRAME = 2;
    private static final int CHOICE_PITCH = MenuChrome.ACTION_ROW_H + 8;

    private static int lineHeight() { return Math.max(LINE, MenuChrome.compactLineHeight()); }
    private static int paragraphPitch() { return Math.max(PARAGRAPH_PITCH, lineHeight() + 2); }
    private static int foundPitch() { return Math.max(FOUND_PITCH, lineHeight() + 7); }
    private static int buttonHeight() { return Math.max(BUTTON_H, lineHeight() + 12); }
    private static int choicePitch() { return MenuChrome.actionRowHeight() + 8; }

    private static int scanStatus() { return MenuChrome.ICON_SIZE + 6; }
    private static int scanPath() { return scanStatus() + lineHeight() + 4; }
    private static int scanMeter() { return scanPath() + lineHeight() + 6; }
    private static int scanNote() { return scanMeter() + 10; }
    private static int introParagraph() { return lineHeight() + 6; }

    /** The current state's layout, shared by drawing and the pointer. */
    Layout layout() {
        int x = 30, w = width - 60;
        if (discovery != null) {
            // Icon, 6 clear, what is being read, 4 clear, where, 6 clear, the 2-row meter, 8 clear, reassurance.
            return new Layout(centredTop(scanNote() + lineHeight()), List.of(), null, 0, 0, 0);
        }
        if (!found.isEmpty()) {
            // Heading, 7 clear, the badges' rows, (6 clear, notice), 9 clear to Continue's frame.
            int listY = lineHeight() + 7 + 1;
            int next = listY - 1 + (found.size() - 1) * foundPitch() + 10;
            int noticeY = next + 6;
            if (scanNotice != null) next = noticeY + lineHeight();
            int buttonY = next + 9 + FRAME;
            int top = centredTop(buttonY + buttonHeight() + FRAME);
            return new Layout(top, List.of(), new Box(x, top + buttonY, w, buttonHeight()), top + listY, top + noticeY, 0);
        }
        // Lead sentence, 6 clear, a two-line paragraph, 8 clear to the first choice's frame, the two
        // choices, then (6 clear) any message and scan notice.
        int choiceY = introParagraph() + paragraphPitch() + lineHeight() + 8 + FRAME;
        int end = choiceY + choicePitch() + MenuChrome.actionRowHeight() + FRAME;
        int messageY = end + 6;
        int noticeY = message != null ? messageY + paragraphPitch() : messageY;
        if (message != null || scanNotice != null) end = (scanNotice != null ? noticeY : messageY) + lineHeight();
        int top = centredTop(end);
        List<Box> choices = List.of(new Box(x, top + choiceY, w, MenuChrome.actionRowHeight()),
                new Box(x, top + choiceY + choicePitch(), w, MenuChrome.actionRowHeight()));
        return new Layout(top, choices, null, 0, top + noticeY, top + messageY);
    }

    /** Scanning offsets from the icon's top row: the status line, the path, the meter and the note. */
    private static final int SCAN_STATUS = MenuChrome.ICON_SIZE + 6, SCAN_PATH = SCAN_STATUS + LINE + 4,
            SCAN_METER = SCAN_PATH + LINE + 6, SCAN_NOTE = SCAN_METER + 2 + 8;
    /** The intro paragraph's first line, six clear rows under the lead sentence. */
    private static final int INTRO_PARAGRAPH = LINE + 6;

    private static int centredTop(int height) {
        return BODY_TOP + (BODY_BOTTOM - BODY_TOP - height + 1) / 2;
    }

    private static boolean over(MasterTitlePointer mouse, Box box) {
        return box != null && mouse.over(box.x(), box.y(), box.w(), box.h());
    }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        this.width = Math.max(320, width);
        if (browser != null) { browser.render(font, this.width); return; }
        if (details != null) { details.render(font, this.width); return; }
        MenuChrome.background(font, this.width);
        MenuChrome.header(font, this.width, MenuStyle.pageText(MenuStyle.language(font), UiMessages.text("menu.onboarding.add_your_games_caps"), this.width - 18, 1, frame), null);
        Layout layout = layout();
        if (discovery != null) {
            // What is being read, where, and that it is still working: an indeterminate meter.
            int top = layout.top();
            MenuChrome.icon(font, scanning ? MenuChrome.Icon.FOLDER : MenuChrome.Icon.FILE,
                    (this.width - MenuChrome.ICON_SIZE) / 2, top, MenuChrome.CYAN);
            text(font, scanning ? UiMessages.text("menu.onboarding.looking_for_your_games") : UiMessages.text("menu.onboarding.checking_game"), top + scanStatus(), MenuChrome.GOLD);
            if (reading != null) text(font, MenuPathBrowser.breadcrumb(reading, this.width - 40), top + scanPath(), MenuChrome.TEXT);
            int track = 120, tx = (this.width - track) / 2, run = 30, meter = top + scanMeter();
            MenuChrome.fill(font, tx, meter, track, 2, MenuChrome.EDGE);
            int head = Math.floorMod(frame * 2, track + run) - run;
            int from = Math.max(0, head), to = Math.min(track, head + run);
            if (to > from) MenuChrome.fill(font, tx + from, meter, to - from, 2, MenuChrome.CYAN);
            text(font, UiMessages.text("menu.onboarding.your_files_stay_where_they_are"), top + scanNote(), MenuChrome.MUTED);
        } else if (!found.isEmpty()) {
            text(font, message, layout.top(), MenuChrome.GOLD);
            int y = layout.listY();
            // Found games read as one list: the checks, the badges and the names each share a
            // column. Every badge is as wide as the widest (S3K), its label centred, so the names
            // start on one line and the badges read as a set.
            int badgeColumn = 0, nameWidth = 0;
            for (RomGame game : found.keySet()) {
                badgeColumn = Math.max(badgeColumn, MenuChrome.tagWidth(MenuChrome.gameBadgeText(badgeId(game))));
                nameWidth = Math.max(nameWidth, MenuChrome.compactWidth(gameLabel(game)));
            }
            int listWidth = MenuChrome.ICON_SIZE + 5 + badgeColumn + 5 + nameWidth;
            int x = (this.width - listWidth) / 2;
            for (RomGame game : found.keySet()) {
                // A found game reads as a confirmed item: check, game badge and its name.
                MenuChrome.icon(font, MenuChrome.Icon.CHECK, x, y, MenuChrome.GREEN);
                MenuChrome.gameBadge(font, x + MenuChrome.ICON_SIZE + 5, y - 1, badgeId(game), badgeColumn);
                MenuChrome.compact(font, gameLabel(game), x + MenuChrome.ICON_SIZE + 5 + badgeColumn + 5, y, MenuChrome.TEXT);
                y += foundPitch();
            }
            if (scanNotice != null) text(font, scanNotice, layout.noticeY(), MenuChrome.AMBER);
            Box button = layout.button();
            MenuChrome.focus(font, button.x(), button.y(), button.w(), button.h(), 0);
            text(font, UiMessages.text("menu.onboarding.continue"), button.y() + 7, MenuChrome.WHITE);
        } else {
            int y = layout.top();
            text(font, UiMessages.text("menu.onboarding.play_sonic_1_sonic_2_and_sonic_3_knuckles"), y, MenuChrome.TEXT);
            text(font, UiMessages.text("menu.onboarding.choose_your_own_mega_drive_genesis_roms"), y + introParagraph(), MenuChrome.MUTED);
            text(font, UiMessages.text("menu.onboarding.we_ll_recognize_them_by_their_contents"), y + introParagraph() + paragraphPitch(), MenuChrome.MUTED);
            String[] labels = {UiMessages.text("menu.onboarding.locate_a_game"), UiMessages.text("menu.onboarding.scan_a_folder")};
            String[] notes = {UiMessages.text("menu.menugameonboarding.choose_one_rom_file"), UiMessages.text("menu.menugameonboarding.find_every_game_in_one_folder")};
            MenuChrome.Icon[] icons = {MenuChrome.Icon.FILE, MenuChrome.Icon.FOLDER};
            for (int i = 0; i < labels.length; i++) {
                Box box = layout.choices().get(i);
                if (choice == i) MenuChrome.focus(font, box.x(), box.y(), box.w(), box.h(), 0);
                else MenuChrome.panel(font, box.x(), box.y(), box.w(), box.h());
                // Icon, action and what it does, left-aligned inside the button.
                MenuChrome.actionRowText(font, icons[i], box.x() + 12, box.y(),
                        MenuStyle.pageText(MenuStyle.language(font), labels[i], box.w() - 40, MenuStyle.COMPACT, frame),
                        MenuStyle.pageText(MenuStyle.language(font), notes[i], box.w() - 40, MenuStyle.COMPACT, frame), box.w() - 40, choice == i);
            }
            if (message != null) text(font, message, layout.messageY(), MenuChrome.AMBER);
            if (scanNotice != null) text(font, scanNotice, layout.noticeY(), MenuChrome.AMBER);
        }
        List<MenuChrome.Hint> hints = discovery != null
                ? List.of(MenuChrome.hint(MenuChrome.backKey(menuInput), UiMessages.text("menu.onboarding.cancel_caps")))
                : List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), found.isEmpty() ? UiMessages.text("menu.onboarding.choose_caps") : UiMessages.text("menu.onboarding.continue_caps")),
                        MenuChrome.hint(MenuChrome.backKey(menuInput), UiMessages.text("menu.onboarding.back_caps")));
        var completeHints = new java.util.ArrayList<>(hints);
        completeHints.add(MenuChrome.hint(MenuChrome.detailsKey(menuInput), UiMessages.text("menu.path.details_caps")));
        MenuChrome.footer(font, this.width, null, completeHints, MenuChrome.controller(menuInput), frame);
    }
    private String detailsText() {
        var text = new java.util.ArrayList<String>();
        for (String key : List.of("play_sonic_1_sonic_2_and_sonic_3_knuckles", "choose_your_own_mega_drive_genesis_roms",
                "we_ll_recognize_them_by_their_contents", "locate_a_game", "scan_a_folder"))
            text.add(UiMessages.text("menu.onboarding." + key));
        text.add(UiMessages.text("menu.menugameonboarding.choose_one_rom_file"));
        text.add(UiMessages.text("menu.menugameonboarding.find_every_game_in_one_folder"));
        if (discovery != null) text.add(UiMessages.text(scanning ? "menu.onboarding.looking_for_your_games" : "menu.onboarding.checking_game"));
        text.add(UiMessages.text("menu.onboarding.your_files_stay_where_they_are"));
        if (pendingPath != null) text.add(pendingPath);
        if (message != null) text.add(message);
        for (var entry : found.entrySet()) { text.add(gameLabel(entry.getKey())); text.add(entry.getValue().toString()); }
        if (scanNotice != null) text.add(scanNotice);
        if (!found.isEmpty()) text.add(UiMessages.text("menu.onboarding.continue"));
        return String.join("\n", text);
    }

    private static String badgeId(RomGame game) { return game.name().toLowerCase(java.util.Locale.ROOT); }
    private static String gameLabel(RomGame game) {
        return switch (game) { case S1 -> UiMessages.text("menu.menugameonboarding.sonic_the_hedgehog"); case S2 -> UiMessages.text("menu.menugameonboarding.sonic_the_hedgehog_2"); case S3K -> UiMessages.text("editor.editor_project_workspace.sonic_3_knuckles_138eayj"); };
    }
    private void text(PixelFont font, String value, int y, float[] color) {
        text(font, value, y, width - 20, color);
    }

    private void text(PixelFont font, String value, int y, int pixels, float[] color) {
        String shown = MenuStyle.pageText(MenuStyle.language(font), value, pixels, MenuStyle.COMPACT, frame);
        MenuChrome.compact(font, shown, (width - MenuChrome.compactWidth(shown)) / 2, y, color);
    }
}
