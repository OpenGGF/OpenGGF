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
    private MenuLoadTask<Map<RomGame, Path>> discovery;
    private MasterTitlePointer pointer;
    private InputHandler menuInput;
    private int width = 320;
    private int choice;
    private boolean scanning;
    private boolean completed;
    private boolean cancelled;
    private String message;
    private Map<RomGame, Path> found = Map.of();

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
        if (browser != null) {
            browser.update(input);
            if (browser.cancelled()) { browser = null; return; }
            if (browser.selected() != null) {
                Path selected = Path.of(browser.selected());
                browser = null;
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
                found = discovery.result();
                if (found.isEmpty()) message = "No supported games found. Try another folder.";
                else {
                    EngineSettingsDraft draft = new EngineSettingsDraft(config);
                    for (var entry : found.entrySet()) draft.set(entry.getKey().configurationKey(), entry.getValue().toString());
                    Path first = found.values().iterator().next();
                    draft.set(SonicConfiguration.ROMS_DIRECTORY, first.getParent().toString());
                    draft.apply();
                    message = found.size() + (found.size() == 1 ? " game added" : " games added");
                    MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
                }
            } catch (Exception e) { found = Map.of(); message = "Could not add games. Try another folder."; }
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
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        }
    }

    /** Uses the runtime classifier, including logical games within lock-on images; extensions never gate identity. */
    static Map<RomGame, Path> discover(Path selection, boolean folder) throws IOException, InterruptedException {
        Map<RomGame, Path> result = new EnumMap<>(RomGame.class);
        if (!folder) { identify(selection, result); return result; }
        if (!Files.isDirectory(selection)) throw new IOException("Choose a folder");
        // Do not follow directory symlinks. A denied subtree must not discard games already found.
        Files.walkFileTree(selection, EnumSet.noneOf(java.nio.file.FileVisitOption.class), 8,
                new SimpleFileVisitor<>() {
                    private int visited;
                    private FileVisitResult boundary() throws IOException {
                        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Scan cancelled");
                        if (++visited > 20000) throw new IOException("Folder scan limit reached; choose a smaller folder");
                        return result.size() == RomGame.values().length ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return boundary();
                    }
                    @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        FileVisitResult next = boundary();
                        if (next == FileVisitResult.TERMINATE) return next;
                        if (attrs.isRegularFile()) {
                            try { identify(path, result); } catch (IOException | SecurityException ignored) { /* Ignore unreadable files. */ }
                        }
                        return result.size() == RomGame.values().length ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                        return boundary();
                    }
                });
        return result;
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
            text(font, scanning ? "Looking for your games..." : "Checking game...", 77, MenuChrome.GOLD);
            text(font, "Your files stay where they are.", 99, MenuChrome.MUTED);
        } else if (!found.isEmpty()) {
            text(font, message, 48, MenuChrome.GOLD);
            int y = 75;
            for (RomGame game : found.keySet()) { text(font, gameLabel(game), y, MenuChrome.TEXT); y += 21; }
            MenuChrome.focus(font, 30, 159, this.width - 60, 25, 0);
            text(font, "Continue", 167, MenuChrome.WHITE);
        } else {
            text(font, "Play Sonic 1, Sonic 2 and Sonic 3 & Knuckles.", 45, MenuChrome.TEXT);
            text(font, "Choose your own Mega Drive / Genesis ROMs.", 61, MenuChrome.MUTED);
            text(font, "We'll recognize them by their contents.", 77, MenuChrome.MUTED);
            String[] labels = {"Locate a game", "Scan a folder"};
            for (int i = 0; i < labels.length; i++) {
                int y = 105 + i * 34;
                if (choice == i) MenuChrome.focus(font, 30, y, this.width - 60, 26, 0);
                else MenuChrome.panel(font, 30, y, this.width - 60, 26);
                text(font, labels[i], y + 9, MenuChrome.TEXT);
            }
            if (message != null) text(font, message, 182, MenuChrome.AMBER);
        }
        List<MenuChrome.Hint> hints = discovery != null
                ? List.of(MenuChrome.hint(MenuChrome.backKey(menuInput), "CANCEL"))
                : List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), found.isEmpty() ? "CHOOSE" : "CONTINUE"),
                        MenuChrome.hint(MenuChrome.backKey(menuInput), "BACK"));
        MenuChrome.footer(font, this.width, null, hints, MenuChrome.controller(menuInput));
    }
    private static String gameLabel(RomGame game) {
        return switch (game) { case S1 -> "Sonic the Hedgehog"; case S2 -> "Sonic the Hedgehog 2"; case S3K -> "Sonic 3 & Knuckles"; };
    }
    private void text(PixelFont font, String value, int y, float[] color) {
        String shown = MenuChrome.fitCompact(value, width - 20);
        MenuChrome.compact(font, shown, (width - MenuChrome.compactWidth(shown)) / 2, y, color);
    }
}
