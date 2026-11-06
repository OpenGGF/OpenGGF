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

/** Select and identify a user-supplied image before atomically saving its location. */
public final class MenuRomLocator {
    private final MasterTitleScreen.GameEntry game;
    private final EngineSettingsDraft draft;
    private MenuPathBrowser browser;
    private MenuLoadTask<Path> validation;
    private Path validated;
    private String lastPath;
    private String error;
    private boolean incompatible;
    private boolean completed;
    private boolean cancelled;
    private MasterTitlePointer pointer;
    private int width = 320;
    private InputHandler menuInput;
    private int frame;
    private MenuDetailsScreen details;

    public MenuRomLocator(MasterTitleScreen.GameEntry game, SonicConfigurationService config) {
        this.game = game;
        draft = new EngineSettingsDraft(config);
        lastPath = config.getString(game.romConfigKey);
        if (lastPath == null || lastPath.isBlank()) lastPath = config.getString(SonicConfiguration.ROMS_DIRECTORY);
        if (lastPath == null) lastPath = "";
        browse();
    }

    private void browse() {
        browser = new MenuPathBrowser(lastPath, true, UiMessages.text("menu.rom.locate_game", java.util.Map.of("game", game.menuLabel.toUpperCase(UiMessages.locale()))),
                UiMessages.text("menu.rom.choose_a_rom_file_any_filename"));
        error = null;
    }

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
            if (browser.cancelled()) { cancelled = true; return; }
            if (browser.selected() == null) return;
            lastPath = browser.selected();
            browser = null;
            String selection = lastPath;
            validation = new MenuLoadTask<>(() -> validate(game, Path.of(selection)));
            return;
        }
        if (MenuInput.textBack(input) || mouse != null && (mouse.backClick()
                || mouse.click() && mouse.over(0, MenuChrome.FOOTER_Y, width, MenuChrome.FOOTER_H))) {
            cancel();
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            return;
        }
        if (MenuInput.textDetails(input)) {
            details = new MenuDetailsScreen(UiMessages.text("menu.rom.locate_rom_caps"),
                    game.menuLabel + "\n" + lastPath + "\n" + (error == null ? UiMessages.text("menu.rom.checking_rom")
                    : error + "\n" + recoveryText() + "\n" + retryText()));
            return;
        }
        if (validation != null) {
            if (!validation.done()) return;
            try {
                validated = validation.result();
                draft.set(game.romConfigKey, validated.toString());
                save();
            } catch (Exception e) {
                Throwable failure = e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e;
                incompatible = failure instanceof IncompatibleImageException;
                error = incompatible ? UiMessages.text("menu.rom.wrong_game", java.util.Map.of("game", game.menuLabel.toUpperCase(UiMessages.locale())))
                        : UiMessages.text("menu.rom.could_not_read_this_rom_file");
                MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            }
            validation = null;
            return;
        }
        if (error != null && (MenuInput.textAccept(input)
                || mouse != null && mouse.click() && mouse.over(10, 130, width - 20, 24))) {
            if (validated == null) browse();
            else save();
        }
    }

    private void save() {
        try {
            if (validated != null) draft.set(SonicConfiguration.ROMS_DIRECTORY, validated.getParent().toString());
            draft.apply();
            completed = true;
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        } catch (IOException | IllegalArgumentException e) {
            error = UiMessages.text("menu.rom.could_not_save_the_rom_location");
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
        }
    }

    /** Uses the same image layout classifier as the runtime catalogue, including lock-on windows. */
    static Path validate(MasterTitleScreen.GameEntry game, Path selection) throws IOException {
        Path path = selection.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IOException(UiMessages.text("menu.rom.choose_a_rom_file"));
        var identity = RomGame.valueOf(game.gameId.toUpperCase(java.util.Locale.ROOT)).identity();
        if (RomImageClassifier.windowOf(PhysicalImage.probe(path), identity).isEmpty())
            throw new IncompatibleImageException(game.menuLabel);
        return path;
    }

    static final class IncompatibleImageException extends IOException {
        IncompatibleImageException(String game) { super("Image does not contain " + game); }
    }

    /** Stop all reads when the owning menu closes; unsaved draft changes are discarded. */
    public void cancel() {
        if (browser != null) { browser.cancel(); browser = null; }
        if (validation != null) { validation.cancel(); validation = null; }
        pointer = null;
        details = null;
        cancelled = true;
    }

    public boolean completed() { return completed; }
    public boolean cancelled() { return cancelled; }

    /** The chosen file's name for display; roots and unparsable text fall back to the raw path. */
    private static String fileName(String path) {
        try {
            Path name = Path.of(path).getFileName();
            return name == null ? path : name.toString();
        } catch (java.nio.file.InvalidPathException invalid) {
            return path;
        }
    }

    private String recoveryText() {
        return validated == null ? (incompatible ? UiMessages.text("menu.rom.choose_a_compatible_rom_image")
                : UiMessages.text("menu.rom.check_access_or_choose_another_file"))
                : UiMessages.text("menu.rom.check_settings_file_access_then_retry");
    }

    private String retryText() {
        return UiMessages.text(validated == null ? "menu.rom.choose_another_file" : "menu.rom.retry_saving");
    }

    private String page(PixelFont font, String text, int pixels, float scale) {
        return MenuStyle.pageText(MenuStyle.language(font), text, pixels, scale, frame);
    }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        this.width = Math.max(320, width);
        if (browser != null) { browser.render(font, this.width); return; }
        if (details != null) { details.render(font, this.width); return; }
        font.beginMegaBatch();
        try {
            MenuChrome.page(font, this.width, UiMessages.text("menu.rom.locate_game", java.util.Map.of("game", game.menuLabel.toUpperCase(UiMessages.locale()))), null,
                    error == null ? UiMessages.text("menu.rom.identifying_your_rom_image") : UiMessages.text("menu.rom.rom_location_needs_attention"));
            MenuChrome.panel(font, 10, 62, this.width - 20, 56);
            MenuChrome.icon(font, error == null ? MenuChrome.Icon.FILE : MenuChrome.Icon.WARNING,
                    18, 73, error == null ? MenuChrome.CYAN : MenuChrome.AMBER);
            MenuChrome.compact(font, page(font, error == null ? UiMessages.text("menu.rom.checking_rom") : error, this.width - 48, MenuStyle.COMPACT),
                    31, 73, error == null ? MenuChrome.TEXT : MenuChrome.AMBER);
            String name = fileName(lastPath);
            MenuChrome.compact(font, page(font, MenuDetailsScreen.readable(name), this.width - 36, MenuStyle.COMPACT),
                    18, 90, MenuChrome.MUTED);
            if (error != null) {
                MenuChrome.compact(font, page(font, recoveryText(), this.width - 20, MenuStyle.COMPACT),
                        10, 167, MenuChrome.MUTED);
                MenuChrome.button(font, 10, 130, this.width - 20, 24,
                        page(font, retryText(), this.width - 28, MenuStyle.COMPACT), true, true, frame);
            }
            var hints = error == null
                    ? java.util.List.of(MenuChrome.hint(MenuChrome.backKey(menuInput), UiMessages.text("menu.rom.cancel_caps")))
                    : java.util.List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), UiMessages.text("menu.rom.retry_caps")), MenuChrome.hint(MenuChrome.backKey(menuInput), UiMessages.text("menu.rom.back_caps")));
            var completeHints = new java.util.ArrayList<>(hints);
            completeHints.add(MenuChrome.hint(MenuChrome.detailsKey(menuInput), UiMessages.text("menu.path.details_caps")));
            MenuChrome.footer(font, this.width, null, completeHints, MenuChrome.controller(menuInput), frame);
        } finally { font.endMegaBatch(); }
    }
}
