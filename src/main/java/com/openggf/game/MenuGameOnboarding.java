package com.openggf.game;

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
        if (browser != null) {
            browser.update(input);
            if (browser.cancelled()) { browser = null; return; }
            if (browser.selected() != null) {
                Path selected = Path.of(browser.selected());
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
        if (discovery != null) {
            if (!discovery.done()) return;
            try {
                DiscoveryResult result = discovery.result();
                found = result.games();
                scanNotice = result.notice();
                if (found.isEmpty()) message = scanning ? "No supported games found." : "Not a supported ROM image.";
                else {
                    EngineSettingsDraft draft = new EngineSettingsDraft(config);
                    for (var entry : found.entrySet()) draft.set(entry.getKey().configurationKey(), entry.getValue().toString());
                    Path first = found.values().iterator().next();
                    draft.set(SonicConfiguration.ROMS_DIRECTORY, first.getParent().toString());
                    try { draft.apply(); }
                    catch (IOException | IllegalArgumentException e) {
                        found = Map.of();
                        message = "Could not save. Check settings-file access.";
                        discovery = null;
                        return;
                    }
                    message = found.size() + (found.size() == 1 ? " game added" : " games added");
                    MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
                }
            } catch (Exception e) { found = Map.of(); scanNotice = "Check access or choose another location.";
                message = scanning ? "Could not read this folder." : "Could not read this file."; }
            discovery = null;
            return;
        }
        if (!found.isEmpty()) {
            if (MenuInput.textAccept(input) || mouse != null && mouse.click() && mouse.over(30, 159, width - 60, 25)) completed = true;
            return;
        }
        boolean click = false;
        if (mouse != null) for (int i = 0; i < 2; i++) {
            if (mouse.over(30, 105 + i * 34, width - 60, 26) && (mouse.moved() || mouse.click())) {
                choice = i; click = mouse.click();
            }
        }
        if (MenuInput.textUp(input) || MenuInput.textDown(input)) { choice = 1 - choice; MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE); }
        if (MenuInput.textAccept(input) || click) {
            scanning = choice == 1;
            String initial = config.getString(SonicConfiguration.ROMS_DIRECTORY);
            browser = scanning ? MenuPathBrowser.folder(initial, "SCAN FOLDER", "Choose a folder containing your games")
                    : new MenuPathBrowser(initial, true, "LOCATE GAME", "Choose a ROM file (any filename)");
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
        if (!Files.isDirectory(selection)) throw new IOException("Choose a folder");
        // No directory symlinks. Limits and denied subtrees preserve discoveries already made.
        boolean[] limited = {false};
        boolean[] unreadable = {false};
        Files.walkFileTree(selection, EnumSet.noneOf(java.nio.file.FileVisitOption.class), 8,
                new SimpleFileVisitor<>() {
                    private int visited;
                    private FileVisitResult boundary() throws IOException {
                        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Scan cancelled");
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
        if (!Files.isRegularFile(path)) throw new IOException("Choose a ROM file");
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
        cancelled = true;
    }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        this.width = Math.max(320, width);
        if (browser != null) { browser.render(font, this.width); return; }
        MenuChrome.background(font, this.width);
        MenuChrome.header(font, this.width, "ADD YOUR GAMES", null);
        if (discovery != null) {
            // What is being read, where, and that it is still working: an indeterminate meter.
            MenuChrome.icon(font, scanning ? MenuChrome.Icon.FOLDER : MenuChrome.Icon.FILE,
                    (this.width - MenuChrome.ICON_SIZE) / 2, 58, MenuChrome.CYAN);
            text(font, scanning ? "Looking for your games..." : "Checking game...", 75, MenuChrome.GOLD);
            if (reading != null) text(font, MenuPathBrowser.breadcrumb(reading, this.width - 40), 89, MenuChrome.TEXT);
            int track = 120, tx = (this.width - track) / 2, run = 30;
            MenuChrome.fill(font, tx, 105, track, 2, MenuChrome.EDGE);
            int head = Math.floorMod(frame * 2, track + run) - run;
            int from = Math.max(0, head), to = Math.min(track, head + run);
            if (to > from) MenuChrome.fill(font, tx + from, 105, to - from, 2, MenuChrome.CYAN);
            text(font, "Your files stay where they are.", 121, MenuChrome.MUTED);
        } else if (!found.isEmpty()) {
            text(font, message, 48, MenuChrome.GOLD);
            int y = 75;
            // Found games read as one list: every check, badge and name starts on the same column.
            int listWidth = 0;
            for (RomGame game : found.keySet()) listWidth = Math.max(listWidth, MenuChrome.ICON_SIZE + 5
                    + MenuChrome.tagWidth(badge(game)) + 5 + MenuChrome.compactWidth(gameLabel(game)));
            for (RomGame game : found.keySet()) {
                // A found game reads as a confirmed item: check, game badge and its name.
                String name = gameLabel(game);
                int x = (width - listWidth) / 2;
                MenuChrome.icon(font, MenuChrome.Icon.CHECK, x, y, MenuChrome.GREEN);
                x += MenuChrome.ICON_SIZE + 5;
                x += MenuChrome.gameBadge(font, x, y - 1, game.name().toLowerCase(java.util.Locale.ROOT)) + 5;
                MenuChrome.compact(font, name, x, y, MenuChrome.TEXT);
                y += 21;
            }
            if (scanNotice != null) text(font, scanNotice, 140, MenuChrome.AMBER);
            MenuChrome.focus(font, 30, 159, this.width - 60, 25, 0);
            text(font, "Continue", 167, MenuChrome.WHITE);
        } else {
            text(font, "Play Sonic 1, Sonic 2 and Sonic 3 & Knuckles.", 45, MenuChrome.TEXT);
            text(font, "Choose your own Mega Drive / Genesis ROMs.", 61, MenuChrome.MUTED);
            text(font, "We'll recognize them by their contents.", 77, MenuChrome.MUTED);
            String[] labels = {"Locate a game", "Scan a folder"};
            String[] notes = {"Choose one ROM file", "Find every game in one folder"};
            MenuChrome.Icon[] icons = {MenuChrome.Icon.FILE, MenuChrome.Icon.FOLDER};
            for (int i = 0; i < labels.length; i++) {
                int y = 105 + i * 34;
                if (choice == i) MenuChrome.focus(font, 30, y, this.width - 60, 26, 0);
                else MenuChrome.panel(font, 30, y, this.width - 60, 26);
                // Icon, action and what it does, left-aligned inside the button.
                MenuChrome.icon(font, icons[i], 42, y + 10, choice == i ? MenuChrome.GOLD : MenuChrome.CYAN);
                MenuChrome.compact(font, labels[i], 56, y + 5, choice == i ? MenuChrome.WHITE : MenuChrome.TEXT);
                MenuChrome.compact(font, MenuChrome.fitCompact(notes[i], this.width - 100), 56, y + 15,
                        choice == i ? MenuChrome.MUTED : MenuChrome.DIM);
            }
            if (message != null) text(font, message, 178, MenuChrome.AMBER);
            if (scanNotice != null) text(font, scanNotice, 190, MenuChrome.AMBER);
        }
        List<MenuChrome.Hint> hints = discovery != null
                ? List.of(MenuChrome.hint(MenuChrome.backKey(menuInput), "CANCEL"))
                : List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), found.isEmpty() ? "CHOOSE" : "CONTINUE"),
                        MenuChrome.hint(MenuChrome.backKey(menuInput), "BACK"));
        MenuChrome.footer(font, this.width, null, hints, MenuChrome.controller(menuInput));
    }
    private static String badge(RomGame game) {
        return switch (game) { case S1 -> "S1"; case S2 -> "S2"; case S3K -> "S3K"; };
    }
    private static String gameLabel(RomGame game) {
        return switch (game) { case S1 -> "Sonic the Hedgehog"; case S2 -> "Sonic the Hedgehog 2"; case S3K -> "Sonic 3 & Knuckles"; };
    }
    private void text(PixelFont font, String value, int y, float[] color) {
        String shown = MenuChrome.fitCompact(value, width - 20);
        MenuChrome.compact(font, shown, (width - MenuChrome.compactWidth(shown)) / 2, y, color);
    }
}
