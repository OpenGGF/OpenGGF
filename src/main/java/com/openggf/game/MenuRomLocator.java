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

/** Select and identify a user-supplied image before atomically saving its location. */
public final class MenuRomLocator {
    private final MasterTitleScreen.GameEntry game;
    private final EngineSettingsDraft draft;
    private MenuPathBrowser browser;
    private MenuLoadTask<Path> validation;
    private Path validated;
    private String lastPath;
    private String error;
    private boolean completed;
    private boolean cancelled;
    private MasterTitlePointer pointer;
    private int width = 320;
    private InputHandler menuInput;

    public MenuRomLocator(MasterTitleScreen.GameEntry game, SonicConfigurationService config) {
        this.game = game;
        draft = new EngineSettingsDraft(config);
        lastPath = config.getString(game.romConfigKey);
        if (lastPath == null || lastPath.isBlank()) lastPath = config.getString(SonicConfiguration.ROMS_DIRECTORY);
        if (lastPath == null) lastPath = "";
        browse();
    }

    private void browse() {
        browser = new MenuPathBrowser(lastPath, true, "LOCATE " + game.menuLabel.toUpperCase(java.util.Locale.ROOT),
                "Choose a ROM file (any filename)");
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
                || mouse.click() && mouse.over(0, 207, width, 17))) {
            cancel();
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            return;
        }
        if (validation != null) {
            if (!validation.done()) return;
            try {
                validated = validation.result();
                draft.set(game.romConfigKey, validated.toString());
                save();
            } catch (Exception e) {
                error = "This file does not contain " + game.menuLabel + ".";
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
            error = "Could not save the ROM location.";
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
        }
    }

    /** Uses the same image layout classifier as the runtime catalogue, including lock-on windows. */
    static Path validate(MasterTitleScreen.GameEntry game, Path selection) throws IOException {
        Path path = selection.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IOException("Choose a ROM file");
        var identity = RomGame.valueOf(game.gameId.toUpperCase(java.util.Locale.ROOT)).identity();
        if (RomImageClassifier.windowOf(PhysicalImage.probe(path), identity).isEmpty())
            throw new IOException("Image does not contain " + game.menuLabel);
        return path;
    }

    /** Stop all reads when the owning menu closes; unsaved draft changes are discarded. */
    public void cancel() {
        if (browser != null) { browser.cancel(); browser = null; }
        if (validation != null) { validation.cancel(); validation = null; }
        pointer = null;
        cancelled = true;
    }

    public boolean completed() { return completed; }
    public boolean cancelled() { return cancelled; }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        this.width = Math.max(320, width);
        if (browser != null) { browser.render(font, this.width); return; }
        MenuStyle.page(font, this.width, "LOCATE ROM", game.menuLabel);
        MenuStyle.text(font, error == null ? "Checking ROM..." : error, 10, 76,
                this.width - 20, 1, .85f, .55f);
        if (error != null) {
            MenuStyle.text(font, validated == null ? "Choose a compatible ROM image." : "Check settings-file access, then retry.",
                    10, 98, this.width - 20, .8f, .85f, 1);
            MenuStyle.focusLabel(font, 10, 130, this.width - 20, 24);
            MenuStyle.label(font, validated == null ? "Choose another file" : "Retry saving", 16, 134,
                    this.width - 32, 1, 1, 1);
        }
        var hints = error == null
                ? java.util.List.of(MenuChrome.hint(MenuChrome.backKey(menuInput), "CANCEL"))
                : java.util.List.of(MenuChrome.hint(MenuChrome.confirmKey(menuInput), "RETRY"), MenuChrome.hint(MenuChrome.backKey(menuInput), "BACK"));
        MenuChrome.footer(font, this.width, null, hints, MenuChrome.controller(menuInput));
    }
}
