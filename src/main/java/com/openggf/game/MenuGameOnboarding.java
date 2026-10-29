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
        String notice() {
            if (limitReached && unreadable) return UiMessages.text("menu.onboarding.scan_limited_some_paths_unreadable");
            if (limitReached) return UiMessages.text("menu.onboarding.scan_limit_reached_try_a_smaller_folder");
            return unreadable ? UiMessages.text("menu.onboarding.some_paths_unreadable_check_access") : null;
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
        cancelled = true;
    }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        this.width = Math.max(320, width);
        if (browser != null) { browser.render(font, this.width); return; }
        MenuChrome.background(font, this.width);
        MenuChrome.header(font, this.width, UiMessages.text("menu.onboarding.add_your_games_caps"), null);
        if (discovery != null) {
            text(font, scanning ? UiMessages.text("menu.onboarding.looking_for_your_games") : UiMessages.text("menu.onboarding.checking_game"), 77, MenuChrome.GOLD);
            text(font, UiMessages.text("menu.onboarding.your_files_stay_where_they_are"), 99, MenuChrome.MUTED);
        } else if (!found.isEmpty()) {
            text(font, message, 48, MenuChrome.GOLD);
            int y = 75;
            for (RomGame game : found.keySet()) { text(font, gameLabel(game), y, MenuChrome.TEXT); y += 21; }
            if (scanNotice != null) text(font, scanNotice, 140, MenuChrome.AMBER);
            MenuChrome.focus(font, 30, 159, this.width - 60, 25, 0);
            text(font, UiMessages.text("menu.onboarding.continue"), 167, MenuChrome.WHITE);
        } else {
            text(font, UiMessages.text("menu.onboarding.play_sonic_1_sonic_2_and_sonic_3_knuckles"), 45, MenuChrome.TEXT);
            text(font, UiMessages.text("menu.onboarding.choose_your_own_mega_drive_genesis_roms"), 61, MenuChrome.MUTED);
            text(font, UiMessages.text("menu.onboarding.we_ll_recognize_them_by_their_contents"), 77, MenuChrome.MUTED);
            String[] labels = {UiMessages.text("menu.onboarding.locate_a_game"), UiMessages.text("menu.onboarding.scan_a_folder")};
            for (int i = 0; i < labels.length; i++) {
                int y = 105 + i * 34;
                if (choice == i) MenuChrome.focus(font, 30, y, this.width - 60, 26, 0);
                else MenuChrome.panel(font, 30, y, this.width - 60, 26);
                text(font, labels[i], y + 9, MenuChrome.TEXT);
            }
            if (message != null) text(font, message, 178, MenuChrome.AMBER);
            if (scanNotice != null) text(font, scanNotice, 190, MenuChrome.AMBER);
        }
        List<MenuChrome.Hint> hints = discovery != null
                ? List.of(MenuChrome.hint(MenuChrome.backKey(menuInput), UiMessages.text("menu.onboarding.cancel_caps")))
                : List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), found.isEmpty() ? UiMessages.text("menu.onboarding.choose_caps") : UiMessages.text("menu.onboarding.continue_caps")),
                        MenuChrome.hint(MenuChrome.backKey(menuInput), UiMessages.text("menu.onboarding.back_caps")));
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
