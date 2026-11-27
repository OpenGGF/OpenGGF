package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextFace;
import com.openggf.i18n.UiMessages;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The first-run setup guide: add games (Sonic CD included), choose how to play, then play. It opens on its
 * own once per run until it is finished or skipped ({@link SetupGuideSession}) and again from Extras.
 *
 * <p>Games found are saved as they are added, without replacing a game that already works unless the player
 * chooses the new file. A play style is held until the guide finishes, then written with the guide's state in
 * one configuration write; on an upgrade or re-entry a style that changes existing values is applied only after
 * its before-and-after list is accepted. Multiplayer is a shortcut that writes no settings.
 */
public final class MenuSetupGuide {
    /** Steps; Play together replaces Ready as the third step on the multiplayer path. */
    public enum Step { GAMES, STYLE, TOGETHER, READY }

    /** How the guide ended and what the hub does next. */
    public enum Exit { PLAY, HUB, ONLINE, LOCAL_TWO_PLAYER, SKIPPED, CLOSED }

    /** The guide's result: the exit and, for {@link Exit#PLAY}, the game to start. */
    public record Outcome(Exit exit, String gameId) { }

    /** What the hosting hub supplies. */
    public interface Host {
        /** Whether Race online can open (the bundled Time Attack mode is enabled). */
        boolean onlineAvailable();
        /** Called after the guide wrote games, so the hub refreshes its shelf. */
        void libraryChanged();
    }

    private enum Prompt { NONE, LEAVE, CONFLICT, APPLY }

    private final SonicConfigurationService config;
    private final java.util.function.Function<SonicConfigurationService, SetupGuideLibrary.Current> libraryReader;
    private final SetupGuideSession session;
    private final Host host;
    private final boolean reentry;
    private final List<SetupGuideLibrary.Game> games = SetupGuideLibrary.games();
    private final Deque<SetupGuideLibrary.Conflict> conflicts = new ArrayDeque<>();
    private SetupGuideLibrary.Current library;
    private SetupGuideLibrary.Findings findings;

    private Step step = Step.GAMES;
    private int focus;
    private SetupGuidePreset style;
    private boolean styleAccepted;
    private Prompt prompt = Prompt.NONE;
    private int promptChoice;
    private String message;
    private String notice;

    private MenuPathBrowser browser;
    private MenuLoadTask<SetupGuideLibrary.Findings> discovery;
    private boolean scanning;
    private Path reading;
    private MenuDetailsScreen details;

    private Outcome outcome;
    private InputHandler menuInput;
    private MasterTitlePointer pointer;
    private final List<int[]> targets = new ArrayList<>();
    private int width = 320;
    private int frame;

    public MenuSetupGuide(SonicConfigurationService config, SetupGuideSession session, Host host, boolean reentry) {
        this(config, session, host, reentry, SetupGuideLibrary::current);
    }

    /** As above, reading the library through {@code libraryReader} (tests supply a fixed library). */
    MenuSetupGuide(SonicConfigurationService config, SetupGuideSession session, Host host, boolean reentry,
                   java.util.function.Function<SonicConfigurationService, SetupGuideLibrary.Current> libraryReader) {
        this.libraryReader = Objects.requireNonNull(libraryReader, "libraryReader");
        this.config = Objects.requireNonNull(config, "config");
        this.session = Objects.requireNonNull(session, "session");
        this.host = Objects.requireNonNull(host, "host");
        this.reentry = reentry;
        refreshLibrary();
        SetupGuideSession.Progress kept = session.progress();
        if (kept != null) {
            step = Step.values()[kept.step()];
            style = kept.style();
            focus = kept.focus();
        } else {
            focus = anyAvailable() ? continueIndex() : 0;
        }
    }

    // ------------------------------------------------------------------ state

    public Outcome outcome() { return outcome; }
    public Step step() { return step; }
    public SetupGuidePreset style() { return style; }
    public int focus() { return focus; }
    boolean promptOpen() { return prompt != Prompt.NONE; }
    String message() { return message; }

    /** Whether this visit offers Keep my settings: an upgrade or a re-entry from Extras. */
    boolean offersKeep() { return session.existingInstall() || reentry; }

    List<SetupGuidePreset> styles() {
        List<SetupGuidePreset> list = new ArrayList<>();
        if (offersKeep()) list.add(SetupGuidePreset.KEEP);
        list.addAll(List.of(SetupGuidePreset.STOCK, SetupGuidePreset.BALANCED, SetupGuidePreset.ENHANCED,
                SetupGuidePreset.MULTIPLAYER));
        return list;
    }

    private SetupGuidePreset recommended() { return offersKeep() ? SetupGuidePreset.KEEP : SetupGuidePreset.BALANCED; }

    private void refreshLibrary() { library = libraryReader.apply(config); }

    private boolean anyAvailable() { return games.stream().anyMatch(library::available); }

    SetupGuideLibrary.Status status(SetupGuideLibrary.Game game) {
        return SetupGuideLibrary.status(game, library, findings, session.existingInstall() || reentry);
    }

    private int readyCount() {
        int n = 0;
        for (SetupGuideLibrary.Game g : games) {
            var s = status(g);
            if (s == SetupGuideLibrary.Status.READY || s == SetupGuideLibrary.Status.KEPT) n++;
        }
        return n;
    }

    private SetupGuideLibrary.Finding hostedProblem() {
        if (findings == null) return null;
        for (SetupGuideLibrary.Game g : games) {
            if (!g.hosted() || library.available(g)) continue;
            var f = findings.best(g.id());
            if (f.isPresent() && !f.get().usable()) return f.get();
        }
        return null;
    }

    private int continueIndex() { return 2; }

    // ------------------------------------------------------------------ input

    public void pointer(int x, int y, boolean moved, boolean click, boolean backClick, int wheel) {
        pointer = new MasterTitlePointer(x, y, moved, click, backClick, wheel);
        if (browser != null) browser.pointer(x, y, moved, click, backClick, wheel);
    }

    public void update(InputHandler input) {
        MasterTitlePointer mouse = pointer;
        pointer = null;
        if (input == null || outcome != null) return;
        menuInput = input;
        frame++;
        if (details != null) { if (details.update(input) || mouse != null && mouse.backClick()) details = null; return; }
        if (browser != null) { updateBrowser(); return; }
        if (discovery != null) {
            if (MenuInput.textBack(input)) { discovery.cancel(); discovery = null; MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return; }
            if (discovery.done()) finishDiscovery();
            return;
        }
        if (MenuInput.textDetails(input)) { openDetails(); return; }
        int hovered = hovered(mouse);
        boolean click = mouse != null && mouse.click() && hovered >= 0;
        boolean back = MenuInput.textBack(input) || mouse != null && mouse.backClick();
        boolean accept = MenuInput.textAccept(input) || click;
        boolean previous = MenuInput.textUp(input) || MenuInput.textLeft(input);
        boolean next = MenuInput.textDown(input) || MenuInput.textRight(input);
        if (prompt != Prompt.NONE) { updatePrompt(back, accept, previous, next, hovered); return; }
        if (hovered >= 0 && mouse != null && (mouse.moved() || mouse.click())) focus = hovered;
        switch (step) {
            case GAMES -> updateGames(back, accept, previous, next);
            case STYLE -> updateStyle(back, accept, previous, next);
            case TOGETHER -> updateTogether(back, accept, previous, next);
            case READY -> updateReady(back, accept, previous, next);
        }
    }

    private int hovered(MasterTitlePointer mouse) {
        if (mouse == null) return -1;
        for (int[] t : targets) if (mouse.over(t[1], t[2], t[3], t[4])) return t[0];
        return -1;
    }

    private int focusables() {
        return switch (step) {
            case GAMES -> 3;
            case STYLE -> styles().size();
            case TOGETHER -> 2;
            case READY -> anyAvailable() ? 2 : 1;
        };
    }

    private void move(boolean previous, boolean next) {
        int count = focusables();
        int before = focus;
        if (previous) focus = Math.max(0, focus - 1);
        if (next) focus = Math.min(count - 1, focus + 1);
        if (focus != before) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
    }

    private void updateGames(boolean back, boolean accept, boolean previous, boolean next) {
        move(previous, next);
        if (back) { openLeave(); return; }
        if (!accept) return;
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        switch (focus) {
            case 0 -> openBrowser(true);
            case 1 -> openBrowser(false);
            default -> {
                if (anyAvailable() || hasUsableHosted()) go(Step.STYLE, styles().indexOf(style == null ? recommended() : style));
                else openLeave();
            }
        }
    }

    private boolean hasUsableHosted() {
        return games.stream().anyMatch(g -> status(g) == SetupGuideLibrary.Status.FOUND);
    }

    private void updateStyle(boolean back, boolean accept, boolean previous, boolean next) {
        move(previous, next);
        if (back) { go(Step.GAMES, continueIndex()); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return; }
        if (!accept) return;
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        style = styles().get(Math.min(focus, styles().size() - 1));
        styleAccepted = false;
        if (style == SetupGuidePreset.MULTIPLAYER) { go(Step.TOGETHER, 0); return; }
        if (offersKeep() && !style.changes(config).isEmpty()) { prompt = Prompt.APPLY; promptChoice = 0; return; }
        styleAccepted = true;
        go(Step.READY, 0);
    }

    private void updateTogether(boolean back, boolean accept, boolean previous, boolean next) {
        move(previous, next);
        if (back) { go(Step.STYLE, styles().indexOf(SetupGuidePreset.MULTIPLAYER)); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return; }
        if (!accept) return;
        if (focus == 0 && !host.onlineAvailable() || focus == 1 && !twoPlayerAvailable()) { MenuFeedback.emit(MenuFeedback.Cue.ERROR); return; }
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        finish(SetupGuidePreset.MULTIPLAYER, new Outcome(focus == 0 ? Exit.ONLINE : Exit.LOCAL_TWO_PLAYER, focus == 1 ? "s2" : null));
    }

    boolean twoPlayerAvailable() {
        return games.stream().anyMatch(g -> g.id().equals("s2") && library.available(g));
    }

    private void updateReady(boolean back, boolean accept, boolean previous, boolean next) {
        move(previous, next);
        if (back) { go(Step.STYLE, styles().indexOf(style)); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return; }
        if (!accept) return;
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        String play = playGame();
        if (focus == 0 && play != null) finish(style, new Outcome(Exit.PLAY, play));
        else finish(style, new Outcome(Exit.HUB, null));
    }

    /** The game Play starts: the hub's last game when it can start, else the first that can. */
    String playGame() {
        String last = config.getString(SonicConfiguration.HUB_LAST_GAME);
        for (SetupGuideLibrary.Game g : games) if (g.id().equals(last) && library.available(g)) return g.id();
        for (SetupGuideLibrary.Game g : games) if (library.available(g)) return g.id();
        return null;
    }

    private void go(Step next, int newFocus) {
        step = next;
        focus = Math.max(0, newFocus);
        message = null;
    }

    private void finish(SetupGuidePreset chosen, Outcome result) {
        try {
            (chosen == null ? SetupGuidePreset.KEEP : chosen).finish(config);
        } catch (IOException failure) {
            message = UiMessages.text("menu.onboarding.could_not_save_check_settings_file_access");
            MenuFeedback.emit(MenuFeedback.Cue.ERROR);
            return;
        }
        session.clearProgress();
        outcome = result;
    }

    /** Leaves for the hub without settling (re-entry only); progress is kept for the next visit. */
    public void close() {
        if (browser != null) { browser.cancel(); browser = null; }
        if (discovery != null) { discovery.cancel(); discovery = null; }
        if (outcome == null) {
            session.keep(new SetupGuideSession.Progress(step.ordinal(), style, focus));
            outcome = new Outcome(Exit.CLOSED, null);
        }
    }

    private void openLeave() {
        if (SetupGuidePreset.settled(config)) { close(); MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return; }
        prompt = Prompt.LEAVE;
        promptChoice = 0;
        MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
    }

    private void updatePrompt(boolean back, boolean accept, boolean previous, boolean next, int hovered) {
        int before = promptChoice;
        if (previous) promptChoice = 0;
        if (next) promptChoice = 1;
        if (hovered == 100 || hovered == 101) promptChoice = hovered - 100;
        if (promptChoice != before) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        if (back) { promptChoice = 0; accept = true; }
        if (!accept) return;
        boolean consequential = promptChoice == 1;
        Prompt current = prompt;
        prompt = Prompt.NONE;
        switch (current) {
            case LEAVE -> {
                if (!consequential) return;
                try { SetupGuidePreset.skip(config); }
                catch (IOException failure) {
                    message = UiMessages.text("menu.onboarding.could_not_save_check_settings_file_access");
                    return;
                }
                session.clearProgress();
                outcome = new Outcome(Exit.SKIPPED, null);
            }
            case CONFLICT -> {
                var conflict = conflicts.poll();
                if (conflict != null && consequential) {
                    try { SetupGuideLibrary.replace(config, conflict); refreshLibrary(); host.libraryChanged(); }
                    catch (IOException failure) {
                        message = UiMessages.text("menu.onboarding.could_not_save_check_settings_file_access");
                    }
                }
                if (!conflicts.isEmpty()) { prompt = Prompt.CONFLICT; promptChoice = 0; }
            }
            case APPLY -> {
                if (!consequential) return;
                styleAccepted = true;
                go(Step.READY, 0);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ files

    private void openBrowser(boolean folder) {
        scanning = folder;
        message = null;
        notice = null;
        String initial = config.getString(SonicConfiguration.ROMS_DIRECTORY);
        browser = folder
                ? MenuPathBrowser.folder(initial, UiMessages.text("menu.onboarding.scan_folder_caps"),
                        UiMessages.text("menu.onboarding.choose_a_folder_containing_your_games"))
                : new MenuPathBrowser(initial, true, UiMessages.text("menu.onboarding.locate_game_caps"),
                        UiMessages.text("menu.setup.games.locate_note"));
    }

    private void updateBrowser() {
        browser.update(menuInput);
        if (browser.cancelled()) { browser = null; return; }
        if (browser.selected() == null) return;
        Path selected = Path.of(browser.selected());
        browser = null;
        reading = selected;
        boolean folder = scanning;
        discovery = new MenuLoadTask<>(() -> folder ? SetupGuideLibrary.scan(selected, games)
                : SetupGuideLibrary.locate(selected, games));
    }

    void showForTest(Step shown, int focused, SetupGuidePreset chosen) {
        step = shown; focus = focused; style = chosen; prompt = Prompt.NONE;
    }
    void openPromptForTest(String kind) { prompt = Prompt.valueOf(kind); promptChoice = 0; }

    /** Runs the same plan the finished scan would, for tests. */
    void acceptFindings(SetupGuideLibrary.Findings found) {
        findings = found;
        notice = found.notice();
        var plan = SetupGuideLibrary.plan(found, games, library);
        try { SetupGuideLibrary.apply(config, plan); }
        catch (IOException failure) {
            message = UiMessages.text("menu.onboarding.could_not_save_check_settings_file_access");
            return;
        }
        refreshLibrary();
        if (!plan.added().isEmpty()) host.libraryChanged();
        conflicts.clear();
        conflicts.addAll(plan.conflicts());
        boolean anything = !found.findings().isEmpty();
        if (!anything) message = scanning ? UiMessages.text("menu.onboarding.no_supported_games_found")
                : UiMessages.text("menu.onboarding.not_a_supported_rom_image");
        else MenuFeedback.emit(plan.added().isEmpty() ? MenuFeedback.Cue.NAVIGATE : MenuFeedback.Cue.CONFIRM);
        if (!conflicts.isEmpty()) { prompt = Prompt.CONFLICT; promptChoice = 0; }
        focus = anyAvailable() || hasUsableHosted() ? continueIndex() : (scanning ? 0 : 1);
    }

    private void finishDiscovery() {
        MenuLoadTask<SetupGuideLibrary.Findings> finished = discovery;
        discovery = null;
        SetupGuideLibrary.Findings found;
        try {
            found = finished.result();
        } catch (java.util.concurrent.ExecutionException failure) {
            // An unreadable folder or file is the player's to fix; anything else is an engine fault.
            if (!(failure.getCause() instanceof IOException))
                throw new IllegalStateException("Setup guide discovery failed", failure.getCause());
            message = scanning ? UiMessages.text("menu.onboarding.could_not_read_this_folder")
                    : UiMessages.text("menu.onboarding.could_not_read_this_file");
            notice = UiMessages.text("menu.onboarding.check_access_or_choose_another_location");
            return;
        } catch (Exception unexpected) {
            throw new IllegalStateException("Setup guide discovery failed", unexpected);
        }
        acceptFindings(found);
    }

    private void openDetails() {
        List<String> text = new ArrayList<>();
        if (step == Step.STYLE) {
            SetupGuidePreset focused = styles().get(Math.min(focus, styles().size() - 1));
            text.add(styleName(focused));
            text.add(styleNote(focused));
            for (var row : presetRows(focused)) text.add(row[0] + ": " + row[1]);
            text.addAll(caveats(focused));
            details = new MenuDetailsScreen(UiMessages.text("menu.setup.style.title_caps"), String.join("\n", text));
        } else {
            for (SetupGuideLibrary.Game g : games) {
                text.add(gameName(g.id()) + ": " + statusLabel(status(g)));
                library.file(g).ifPresent(p -> text.add(p.toString()));
                if (findings != null) findings.findings().stream().filter(f -> f.gameId().equals(g.id()) && !f.usable())
                        .forEach(f -> { text.add(UiMessages.text("crash.field.error") + ": " + f.detail()); text.add(problemFix(f)); });
            }
            if (notice != null) text.add(notice);
            text.add(UiMessages.text("menu.setup.games.cd_hint"));
            text.add(UiMessages.text("menu.setup.games.locate_note"));
            text.add(UiMessages.text("menu.onboarding.your_files_stay_where_they_are"));
            details = new MenuDetailsScreen(UiMessages.text("menu.setup.games.scan_details_caps"), String.join("\n", text));
        }
    }

    // ------------------------------------------------------------------ text

    static String gameName(String id) {
        return switch (id) {
            case "s1" -> UiMessages.text("menu.menugameonboarding.sonic_the_hedgehog");
            case "s2" -> UiMessages.text("menu.menugameonboarding.sonic_the_hedgehog_2");
            case "s3k" -> UiMessages.text("editor.editor_project_workspace.sonic_3_knuckles_138eayj");
            default -> UiMessages.text("settings.value.scd");
        };
    }

    static String statusLabel(SetupGuideLibrary.Status status) {
        return switch (status) {
            case READY -> UiMessages.text("menu.setup.games.ready");
            case FOUND -> UiMessages.text("menu.setup.games.found");
            case KEPT -> UiMessages.text("menu.setup.games.kept");
            case MISSING -> UiMessages.text("menu.setup.games.missing");
            case INCOMPLETE -> UiMessages.text("menu.setup.games.incomplete");
            case WRONG_RELEASE -> UiMessages.text("menu.setup.games.wrong_release");
            case UNREADABLE -> UiMessages.text("menu.setup.games.unreadable");
        };
    }

    static String styleName(SetupGuidePreset preset) {
        return switch (preset) {
            case KEEP -> UiMessages.text("menu.setup.style.keep");
            case STOCK -> UiMessages.text("menu.setup.style.stock");
            case BALANCED -> UiMessages.text("menu.setup.style.balanced");
            case ENHANCED -> UiMessages.text("menu.setup.style.enhanced");
            case MULTIPLAYER -> UiMessages.text("menu.setup.style.multiplayer");
        };
    }

    static String styleNote(SetupGuidePreset preset) {
        return switch (preset) {
            case KEEP -> UiMessages.text("menu.setup.style.keep_note");
            case STOCK -> UiMessages.text("menu.setup.style.stock_note");
            case BALANCED -> UiMessages.text("menu.setup.style.balanced_note");
            case ENHANCED -> UiMessages.text("menu.setup.style.enhanced_note");
            case MULTIPLAYER -> UiMessages.text("menu.setup.style.multiplayer_note");
        };
    }

    static List<String> caveats(SetupGuidePreset preset) {
        return switch (preset) {
            case STOCK -> List.of(UiMessages.text("menu.setup.style.stock_caveat"));
            case BALANCED -> List.of(UiMessages.text("menu.setup.style.native_scope"));
            case ENHANCED -> List.of(UiMessages.text("menu.setup.style.native_scope"),
                    UiMessages.text("menu.setup.style.smooth_caveat"));
            case MULTIPLAYER -> List.of(UiMessages.text("menu.setup.style.multiplayer_sets"));
            case KEEP -> List.of(UiMessages.text("menu.setup.style.keep_sets"));
        };
    }

    static String settingLabel(SonicConfiguration key) {
        return switch (key) {
            case DISPLAY_ASPECT -> UiMessages.text("settings.key.display_aspect.label");
            case WIDESCREEN_DRAW_MODE -> UiMessages.text("settings.key.widescreen_draw_mode.label");
            case DISPLAY_INTERPOLATION -> UiMessages.text("settings.key.display_interpolation.label");
            case REWIND_BUDGET_SCOPE, LIVE_REWIND_ENABLED -> UiMessages.text("settings.key.live_rewind_enabled.label");
            case DISPLAY_SPRITE_SCANLINE_LIMIT -> UiMessages.text("settings.key.display_sprite_scanline_limit.label");
            case AUDIO_FM_CORE -> UiMessages.text("settings.key.audio_fm_core.label");
            case LOAD_TIME_SIMULATION -> UiMessages.text("settings.key.load_time_simulation.label");
            default -> key.name();
        };
    }

    static String valueLabel(SonicConfiguration key, String value) {
        if (key == SonicConfiguration.REWIND_BUDGET_SCOPE)
            return UiMessages.text("off".equalsIgnoreCase(value) ? "settings.value.off" : "settings.value.on");
        return switch (value.toUpperCase(java.util.Locale.ROOT)) {
            case "NATIVE_4_3" -> UiMessages.text("settings.value.NATIVE_4_3");
            case "WIDE_16_10" -> UiMessages.text("settings.value.WIDE_16_10");
            case "WIDE_16_9" -> UiMessages.text("settings.value.WIDE_16_9");
            case "ULTRA_21_9" -> UiMessages.text("settings.value.ULTRA_21_9");
            case "SUPER_32_9" -> UiMessages.text("settings.value.SUPER_32_9");
            case "NATIVE" -> UiMessages.text("settings.value.NATIVE");
            case "EDGE" -> UiMessages.text("settings.value.EDGE");
            case "TRUE" -> UiMessages.text("settings.value.on");
            case "FALSE" -> UiMessages.text("settings.value.off");
            case "ACCURATE" -> UiMessages.text("settings.value.accurate");
            case "FAST" -> UiMessages.text("settings.value.FAST");
            case "PROFILED" -> UiMessages.text("settings.value.PROFILED");
            case "NONE" -> UiMessages.text("settings.value.NONE");
            default -> value;
        };
    }

    /** What a style sets, as {label, value} rows in display order. */
    static List<String[]> presetRows(SetupGuidePreset preset) {
        List<String[]> rows = new ArrayList<>();
        for (var entry : preset.writes().entrySet()) {
            SonicConfiguration key = entry.getKey();
            rows.add(new String[] {settingLabel(key), valueLabel(key, String.valueOf(entry.getValue()))});
        }
        return rows;
    }

    static String releaseLine() {
        var matcher = java.util.regex.Pattern.compile("^(\\d+\\.\\d+)")
                .matcher(String.valueOf(com.openggf.version.AppVersion.identity().baseVersion()));
        return matcher.find() ? matcher.group(1) : "";
    }

    private String title() {
        return switch (step) {
            case GAMES -> session.existingInstall() && !reentry && !releaseLine().isEmpty()
                    ? UiMessages.text("menu.setup.upgrade.title_caps", Map.of("version", releaseLine()))
                    : UiMessages.text("menu.setup.games.title_caps");
            case STYLE -> UiMessages.text("menu.setup.style.title_caps");
            case TOGETHER -> UiMessages.text("menu.setup.mp.title_caps");
            case READY -> UiMessages.text("menu.setup.ready.title_caps");
        };
    }

    // ------------------------------------------------------------------ drawing

    private static int pitch() { return Math.max(MenuChrome.DESCRIPTION_PITCH, MenuChrome.compactLineHeight() + 1); }
    private static int line() { return Math.max(8, MenuChrome.compactLineHeight()); }
    private static int above() { return MenuChrome.compactMetrics().above(); }
    private static boolean tall() { return MenuChrome.compactLineHeight() > 10; }
    private static final int BOTTOM = MenuChrome.FOOTER_Y - 3;
    /** Scan a folder and Locate a game: side by side, 10 px from the edges and 6 px apart. */
    private static final int BUTTON_MARGIN = 10, BUTTON_GAP = 6;

    public void render(PixelFont font, int width) {
        if (font == null) return;
        this.width = Math.max(320, width);
        targets.clear();
        if (browser != null) { browser.render(font, this.width); return; }
        if (details != null) { details.render(font, this.width); return; }
        MenuChrome.background(font, this.width);
        MenuChrome.header(font, this.width, MenuStyle.pageText(MenuStyle.language(font), title(), this.width - 60, 1, frame), null);
        dots(font);
        if (discovery != null) { renderScanning(font); footer(font); return; }
        switch (step) {
            case GAMES -> renderGames(font);
            case STYLE -> renderStyle(font);
            case TOGETHER -> renderTogether(font);
            case READY -> renderReady(font);
        }
        switch (prompt) {
            case LEAVE -> renderChoice(font, UiMessages.text("menu.setup.leave.title_caps"),
                    List.of(UiMessages.text("menu.setup.leave.detail")), null,
                    UiMessages.text("menu.setup.leave.stay"), UiMessages.text("menu.setup.leave.skip"));
            case CONFLICT -> renderConflict(font);
            case APPLY -> renderApply(font);
            default -> { }
        }
        footer(font);
    }

    private void dots(PixelFont font) {
        int total = 3, current = step == Step.GAMES ? 0 : step == Step.STYLE ? 1 : 2;
        int w = 2 * 4 + 8 + (total - 1) * 5;
        int x = width - 9 - w;
        for (int i = 0; i < total; i++) {
            boolean selected = i == current;
            int dw = selected ? 8 : 4;
            MenuChrome.fill(font, x - 1, 11, dw + 2, 6, MenuChrome.INK, .85f);
            MenuChrome.fill(font, x, 12, dw, 4, selected ? MenuChrome.GOLD : MenuChrome.MUTED);
            x += dw + 5;
        }
    }

    private void footer(PixelFont font) {
        String confirm = MenuChrome.confirmKey(menuInput), back = MenuChrome.backKey(menuInput), more = MenuChrome.detailsKey(menuInput);
        List<MenuChrome.Hint> hints = new ArrayList<>();
        if (discovery != null) hints.add(MenuChrome.hint(back, UiMessages.text("menu.onboarding.cancel_caps")));
        else if (prompt != Prompt.NONE) {
            hints.add(MenuChrome.hint(confirm, UiMessages.text("hub.ui.select_caps")));
            hints.add(MenuChrome.hint(back, prompt == Prompt.APPLY ? UiMessages.text("hub.ui.stay_caps")
                    : UiMessages.text("hub.ui.back_caps")));
        } else {
            String verb = step == Step.GAMES && focus == continueIndex() && (anyAvailable() || hasUsableHosted())
                    ? UiMessages.text("menu.onboarding.continue_caps")
                    : step == Step.READY && focus == 0 && playGame() != null ? UiMessages.text("hub.ui.start_caps")
                    : step == Step.TOGETHER ? UiMessages.text("hub.ui.open_caps")
                    : UiMessages.text("menu.onboarding.choose_caps");
            hints.add(MenuChrome.hint(confirm, verb));
            hints.add(MenuChrome.hint(back, step == Step.GAMES ? UiMessages.text("menu.setup.skip_caps")
                    : UiMessages.text("menu.onboarding.back_caps")));
            if (step == Step.GAMES || step == Step.STYLE) hints.add(MenuChrome.hint(more, UiMessages.text("menu.path.details_caps")));
        }
        MenuChrome.footer(font, width, null, hints, MenuChrome.controller(menuInput), frame);
    }

    private void target(int index, int x, int y, int w, int h) { targets.add(new int[] {index, x, y, w, h}); }

    /** A paragraph wrapped by measured width; right-to-left lines align right, as the legal page's do. */
    private int paragraph(PixelFont font, String text, int x, int y, int w, float[] colour, boolean centre) {
        for (String row : MenuStyle.wrap(text, w, MenuStyle.COMPACT)) {
            int rw = MenuChrome.compactWidth(row);
            boolean rtl = HostText.shared().shape(row, TextFace.COMPACT, HostTextContext.current()).rightToLeft();
            int rx = centre ? x + (w - rw) / 2 : rtl ? x + w - rw : x;
            MenuChrome.compact(font, row, rx, y + above(), colour);
            y += pitch();
        }
        return y;
    }

    private int paragraphHeight(String text, int w) { return MenuStyle.wrap(text, w, MenuStyle.COMPACT).size() * pitch(); }

    private void renderScanning(PixelFont font) {
        int top = 60;
        MenuChrome.icon(font, scanning ? MenuChrome.Icon.FOLDER : MenuChrome.Icon.FILE, (width - MenuChrome.ICON_SIZE) / 2, top, MenuChrome.CYAN);
        int y = paragraph(font, UiMessages.text(scanning ? "menu.onboarding.looking_for_your_games" : "menu.onboarding.checking_game"),
                20, top + 13, width - 40, MenuChrome.GOLD, true);
        if (reading != null) y = paragraph(font, MenuPathBrowser.breadcrumb(reading, width - 40), 20, y + 4, width - 40, MenuChrome.TEXT, true);
        int track = 120, tx = (width - track) / 2, run = 30, meter = y + 6;
        MenuChrome.fill(font, tx, meter, track, 2, MenuChrome.EDGE);
        int head = Math.floorMod(frame * 2, track + run) - run;
        int from = Math.max(0, head), to = Math.min(track, head + run);
        if (to > from) MenuChrome.fill(font, tx + from, meter, to - from, 2, MenuChrome.CYAN);
        paragraph(font, UiMessages.text("menu.onboarding.your_files_stay_where_they_are"), 20, meter + 10, width - 40, MenuChrome.MUTED, true);
    }

    /** Where each block of the games page goes this frame; shared by drawing and the overlap test. */
    record GamesLayout(List<SetupGuideLibrary.Game> rows, int headingY, int rowsY, int rowPitch, String note, int noteY,
                       int problemY, int problemDiagLines, boolean problemFix, int buttonsY, boolean sideBySide,
                       int buttonH, int continueY, boolean reassurance, int bottom) { }

    private String problemFix(SetupGuideLibrary.Finding problem) {
        return problem.file().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".chd")
                ? UiMessages.text("menu.setup.games.cd_fix_chd") : UiMessages.text("menu.setup.games.cd_fix_tracks");
    }

    private String gamesHeading() {
        return session.existingInstall() && !reentry && readyCount() > 0
                ? UiMessages.text("menu.setup.upgrade.kept")
                : UiMessages.text("menu.setup.games.ready_count", Map.of("count", readyCount()));
    }

    /**
     * Fits the games page between header and footer without truncation. While it does not fit, in order: the
     * Sonic CD note goes, the reassurance under Continue goes, ready games fold into the heading's count, the
     * exact diagnostic moves to Details, and the fix sentence moves to Details. Details always has all of it.
     */
    GamesLayout gamesLayout() {
        int x0 = Math.max(22, (width - 276) / 2), w = width - 2 * x0;
        int rowPitch = Math.max(13, line() + 5);
        int bh = Math.max(18, line() + (tall() ? 6 : 10));
        int half = (width - 2 * BUTTON_MARGIN - BUTTON_GAP) / 2, iconRun = MenuChrome.ICON_SIZE + 4 + 8;
        boolean sideBySide = MenuChrome.compactWidth(UiMessages.text("menu.onboarding.scan_a_folder")) + iconRun <= half
                && MenuChrome.compactWidth(UiMessages.text("menu.onboarding.locate_a_game")) + iconRun <= half;
        int buttonsH = sideBySide ? bh : 2 * bh + 5;
        SetupGuideLibrary.Finding problem = hostedProblem();
        String note = null;
        if (problem == null) {
            SetupGuideLibrary.Game cd = games.stream().filter(SetupGuideLibrary.Game::hosted).findFirst().orElse(null);
            if (cd != null && status(cd) == SetupGuideLibrary.Status.FOUND) note = UiMessages.text("menu.setup.games.cd_check_note");
            else if (cd != null && status(cd) == SetupGuideLibrary.Status.MISSING) note = UiMessages.text("menu.setup.games.cd_hint");
        }
        int labelW = MenuChrome.compactWidth(UiMessages.text("crash.field.error")) + 6;
        int diagAll = problem == null ? 0 : tall() ? 1
                : MenuStyle.wrapPath(problem.detail(), w + 4 - labelW, MenuStyle.COMPACT).size();
        int fixH = problem == null ? 0 : MenuStyle.wrap(problemFix(problem), w + 4, MenuStyle.COMPACT).size() * pitch();
        int messageH = (message == null ? 0 : paragraphHeight(message, w) + 2) + (notice == null ? 0 : paragraphHeight(notice, w) + 1);
        int top = MenuChrome.HEADER_H + 6;
        int headingH = paragraphHeight(gamesHeading(), w) + 3;
        boolean keepNote = note != null, reassurance = true, fold = false, fix = true;
        int diag = diagAll;
        for (int pass = 0; ; pass++) {
            List<SetupGuideLibrary.Game> rows = new ArrayList<>(games);
            if (fold) rows.removeIf(g -> {
                var st = status(g);
                return st == SetupGuideLibrary.Status.READY || st == SetupGuideLibrary.Status.KEPT;
            });
            int problemH = problem == null ? 0 : 2 + 6 + line() + 3 + Math.max(1, diag) * pitch() + 2 + (fix ? fixH : 0) + 4;
            int noteH = keepNote ? paragraphHeight(note, w) + 2 : 0;
            int total = headingH + rows.size() * rowPitch + noteH + problemH + messageH + 6 + buttonsH + 8 + bh
                    + (reassurance ? 4 + line() : 0);
            if (total <= BOTTOM - top || pass > 6) {
                int y = top, headingY = y;
                y += headingH;
                int rowsY = y;
                y += rows.size() * rowPitch;
                int noteY = y + 2;
                y += noteH;
                int problemY = y + 2;
                y += problemH + messageH;
                int buttonsY = y + 6, continueY = buttonsY + buttonsH + 8;
                return new GamesLayout(rows, headingY, rowsY, rowPitch, keepNote ? note : null, noteY, problemY,
                        Math.max(1, diag), fix, buttonsY, sideBySide, bh, continueY, reassurance,
                        continueY + bh + (reassurance ? 4 + line() : 0));
            }
            if (keepNote) keepNote = false;
            else if (reassurance) reassurance = false;
            else if (!fold && (problem != null || tall())) fold = true;
            else if (diag > 1) diag = 1;
            else if (fix) fix = false;
            else { fold = true; }
        }
    }

    private void renderGames(PixelFont font) {
        if (!anyAvailable() && findings == null) { renderEmpty(font); return; }
        int x0 = Math.max(22, (width - 276) / 2), w = width - 2 * x0;
        GamesLayout layout = gamesLayout();
        paragraph(font, gamesHeading(), x0, layout.headingY(), w, MenuChrome.GOLD, true);
        int badge = 0;
        for (SetupGuideLibrary.Game g : games) badge = Math.max(badge, MenuChrome.tagWidth(MenuChrome.gameBadgeText(g.id())));
        int y = layout.rowsY();
        for (SetupGuideLibrary.Game g : layout.rows()) {
            var status = status(g);
            boolean good = status == SetupGuideLibrary.Status.READY || status == SetupGuideLibrary.Status.KEPT
                    || status == SetupGuideLibrary.Status.FOUND;
            boolean problemRow = status == SetupGuideLibrary.Status.INCOMPLETE || status == SetupGuideLibrary.Status.WRONG_RELEASE
                    || status == SetupGuideLibrary.Status.UNREADABLE;
            float[] colour = status == SetupGuideLibrary.Status.FOUND ? MenuChrome.CYAN : good ? MenuChrome.GREEN
                    : problemRow ? MenuChrome.AMBER : MenuChrome.DIM;
            MenuChrome.Icon icon = good ? MenuChrome.Icon.CHECK : problemRow ? MenuChrome.Icon.WARNING : MenuChrome.Icon.INFO;
            int textY = y + (layout.rowPitch() - line()) / 2 - 1;
            MenuChrome.icon(font, icon, x0, textY + 1, colour);
            MenuChrome.gameBadge(font, x0 + 11, textY - 1, g.id(), badge);
            String name = gameName(g.id()), label = statusLabel(status);
            int nameX = x0 + 11 + badge + 6;
            int room = x0 + w - MenuChrome.compactWidth(label) - 6 - nameX;
            MenuChrome.compact(font, MenuChrome.fitCompact(name, room), nameX, textY + above(), good ? MenuChrome.TEXT : MenuChrome.DIM);
            MenuChrome.compact(font, label, x0 + w - MenuChrome.compactWidth(label), textY + above(), colour);
            y += layout.rowPitch();
        }
        if (layout.note() != null) paragraph(font, layout.note(), x0, layout.noteY(), w, MenuChrome.DIM, false);
        SetupGuideLibrary.Finding problem = hostedProblem();
        y = layout.problemY();
        if (problem != null) y = renderProblem(font, problem, x0 - 10, y, w + 20, layout.problemDiagLines(), layout.problemFix());
        if (message != null) y = paragraph(font, message, x0, y + 2, w, MenuChrome.AMBER, true);
        if (notice != null) paragraph(font, notice, x0, y + 1, w, MenuChrome.AMBER, true);
        int bh = layout.buttonH(), by = layout.buttonsY();
        String scanLabel = UiMessages.text("menu.onboarding.scan_a_folder"), locateLabel = UiMessages.text("menu.onboarding.locate_a_game");
        int m = BUTTON_MARGIN;
        if (layout.sideBySide()) {
            int half = (width - 2 * m - BUTTON_GAP) / 2;
            iconButton(font, m, by, half, bh, MenuChrome.Icon.FOLDER, scanLabel, focus == 0, false);
            target(0, m, by, half, bh);
            iconButton(font, m + half + BUTTON_GAP, by, half, bh, MenuChrome.Icon.FILE, locateLabel, focus == 1, false);
            target(1, m + half + BUTTON_GAP, by, half, bh);
        } else {
            iconButton(font, m, by, width - 2 * m, bh, MenuChrome.Icon.FOLDER, scanLabel, focus == 0, false);
            target(0, m, by, width - 2 * m, bh);
            iconButton(font, m, by + bh + 5, width - 2 * m, bh, MenuChrome.Icon.FILE, locateLabel, focus == 1, false);
            target(1, m, by + bh + 5, width - 2 * m, bh);
        }
        int cy = layout.continueY(), cw = Math.min(width - 120, 220);
        boolean proceed = anyAvailable() || hasUsableHosted();
        String label = proceed ? UiMessages.text("menu.onboarding.continue") : UiMessages.text("menu.setup.later");
        MenuChrome.button(font, (width - cw) / 2, cy, cw, bh, label, focus == 2, proceed, frame);
        target(2, (width - cw) / 2, cy, cw, bh);
        if (layout.reassurance())
            paragraph(font, problem != null ? UiMessages.text("menu.setup.games.optional")
                    : UiMessages.text("menu.onboarding.your_files_stay_where_they_are"), x0, cy + bh + 4, w, MenuChrome.MUTED, true);
    }

    private int renderProblem(PixelFont font, SetupGuideLibrary.Finding problem, int x, int y, int w, int lines, boolean withFix) {
        String head = UiMessages.text("menu.setup.games.cd_unusable_title");
        String label = UiMessages.text("crash.field.error");
        int labelW = MenuChrome.compactWidth(label) + 6;
        List<String> diag = tall() ? List.of(UiMessages.text("menu.path.details_caps"))
                : MenuStyle.wrapPath(problem.detail(), w - 16 - labelW, MenuStyle.COMPACT);
        if (diag.size() > lines) {
            // The rest of the exact diagnostic is in Details (F1), complete.
            diag = new ArrayList<>(diag.subList(0, lines - 1));
            diag.add(UiMessages.text("menu.path.details_caps"));
        }
        List<String> fixLines = withFix ? MenuStyle.wrap(problemFix(problem), w - 16, MenuStyle.COMPACT) : List.of();
        int h = 6 + line() + 3 + diag.size() * pitch() + 2 + fixLines.size() * pitch() + 4;
        MenuChrome.fill(font, x, y, w, h, MenuChrome.NAVY, .96f);
        MenuChrome.outline(font, x, y, w, h, MenuChrome.mix(MenuChrome.AMBER, MenuChrome.INK, .45f), 1f, 1);
        MenuChrome.icon(font, MenuChrome.Icon.WARNING, x + 8, y + 6, MenuChrome.AMBER);
        MenuChrome.compact(font, MenuChrome.fitCompact(head, w - 27), x + 19, y + 6 + above() - (tall() ? 1 : 0), MenuChrome.AMBER);
        int ty = y + 6 + line() + 3;
        MenuChrome.compact(font, label, x + 8, ty + above(), MenuChrome.DIM);
        for (int i = 0; i < diag.size(); i++) {
            boolean more = i == diag.size() - 1 && diag.get(i).equals(UiMessages.text("menu.path.details_caps"));
            MenuChrome.compact(font, diag.get(i), x + 8 + labelW, ty + above(), more ? MenuChrome.CYAN : MenuChrome.TEXT);
            ty += pitch();
        }
        ty += 2;
        for (String row : fixLines) { MenuChrome.compact(font, row, x + 8, ty + above(), MenuChrome.MUTED); ty += pitch(); }
        return y + h;
    }

    private void renderEmpty(PixelFont font) {
        int x0 = 24, w = width - 48;
        String title = UiMessages.text("menu.setup.games.empty_title");
        int tw = MenuChrome.labelWidth(title) + 12;
        int y = MenuChrome.HEADER_H + 9;
        MenuChrome.icon(font, MenuChrome.Icon.INFO, (width - tw) / 2, y + 1, MenuChrome.CYAN);
        MenuChrome.label(font, title, (width - tw) / 2 + 12, y - 1, MenuChrome.TEXT);
        y += Math.max(12, MenuChrome.labelLineHeight() + 2);
        y = paragraph(font, UiMessages.text("menu.setup.games.empty_hint"), x0, y, w, MenuChrome.MUTED, true);
        if (!tall()) y = paragraph(font, UiMessages.text("menu.setup.games.cd_hint"), x0, y + 3, w, MenuChrome.DIM, true);
        if (message != null) y = paragraph(font, message, x0, y + 2, w, MenuChrome.AMBER, true);
        int rw = Math.min(width - 64, 280), rx = (width - rw) / 2, rh = MenuChrome.actionRowHeight();
        y += 7;
        actionRow(font, rx, y, rw, MenuChrome.Icon.FOLDER, UiMessages.text("menu.onboarding.scan_a_folder"),
                UiMessages.text("menu.menugameonboarding.find_every_game_in_one_folder"), focus == 0);
        target(0, rx, y, rw, rh);
        y += rh + 4;
        actionRow(font, rx, y, rw, MenuChrome.Icon.FILE, UiMessages.text("menu.onboarding.locate_a_game"),
                UiMessages.text("menu.setup.games.locate_note"), focus == 1);
        target(1, rx, y, rw, rh);
        y += rh + 7;
        String later = UiMessages.text("menu.setup.later");
        int bw = MenuChrome.compactWidth(later) + 28, bh = Math.max(16, line() + 8);
        MenuChrome.button(font, (width - bw) / 2, Math.min(y, BOTTOM - bh), bw, bh, later, focus == 2, false, frame);
        target(2, (width - bw) / 2, Math.min(y, BOTTOM - bh), bw, bh);
    }

    private void actionRow(PixelFont font, int x, int y, int w, MenuChrome.Icon icon, String label, String note, boolean focused) {
        if (focused) MenuChrome.focus(font, x, y, w, MenuChrome.actionRowHeight(), frame);
        else MenuChrome.panel(font, x, y, w, MenuChrome.actionRowHeight());
        int room = w - 12 - MenuChrome.ACTION_TEXT_DX - 6;
        MenuChrome.actionRowText(font, icon, x + 10, y,
                MenuStyle.pageText(label, room, MenuStyle.COMPACT, frame), MenuStyle.pageText(note, room, MenuStyle.COMPACT, frame),
                room, focused);
    }

    /** A button whose label leads with a glyph, centred as one run. */
    private void iconButton(PixelFont font, int x, int y, int w, int h, MenuChrome.Icon icon, String label, boolean focused,
                            boolean amber) {
        if (focused) MenuChrome.focus(font, x, y, w, h, frame);
        else MenuChrome.panel(font, x, y, w, h);
        String shown = MenuChrome.fitCompact(label, w - 8 - MenuChrome.ICON_SIZE - 4);
        int run = MenuChrome.ICON_SIZE + 4 + MenuChrome.compactWidth(shown);
        int sx = x + (w - run) / 2;
        float[] colour = amber ? MenuChrome.AMBER : focused ? MenuChrome.WHITE : MenuChrome.TEXT;
        MenuChrome.icon(font, icon, sx, y + (h - MenuChrome.ICON_SIZE) / 2, amber ? MenuChrome.AMBER : focused ? MenuChrome.GOLD : MenuChrome.CYAN);
        MenuChrome.compact(font, shown, sx + MenuChrome.ICON_SIZE + 4, y + (h - MenuChrome.compactLineHeight()) / 2 + 1 + above(), colour);
    }

    private static MenuChrome.Icon styleIcon(SetupGuidePreset preset) {
        return switch (preset) {
            case KEEP -> MenuChrome.Icon.CHECK;
            case STOCK -> MenuChrome.Icon.CARTRIDGE;
            case BALANCED -> MenuChrome.Icon.BALANCE;
            case ENHANCED -> MenuChrome.Icon.WIDE;
            case MULTIPLAYER -> MenuChrome.Icon.PLAYERS;
        };
    }

    private void renderStyle(PixelFont font) {
        List<SetupGuidePreset> list = styles();
        int rx = 14, rw = width - 28;
        SetupGuidePreset shown = list.get(Math.min(focus, list.size() - 1));
        int must = pitch() + 4;   // the Details line, when the settings move there
        for (String c : caveats(shown)) must += paragraphHeight(c, rw - 4) + 2;
        int top = MenuChrome.HEADER_H + 6;
        int roomPerRow = (BOTTOM - top - 2 - must) / list.size() - 3;
        int rh = Math.max(line() + 2, Math.min(Math.max(16, line() + (tall() ? 3 : 6)), roomPerRow)), stepY = rh + 3;
        int y = top;
        for (int i = 0; i < list.size(); i++) {
            SetupGuidePreset preset = list.get(i);
            boolean focused = i == focus;
            if (focused) MenuChrome.focus(font, rx, y, rw, rh, frame);
            MenuChrome.icon(font, styleIcon(preset), rx + 8, y + (rh - MenuChrome.ICON_SIZE) / 2, focused ? MenuChrome.GOLD : MenuChrome.CYAN);
            String tag = preset == recommended() ? UiMessages.text("menu.setup.style.recommended_caps") : null;
            int room = rw - 30 - (tag == null ? 0 : MenuChrome.tagWidth(tag) + 10);
            MenuChrome.compact(font, MenuChrome.fitCompact(styleName(preset), room), rx + 22,
                    y + (rh - MenuChrome.compactLineHeight()) / 2 + above(), focused ? MenuChrome.WHITE : MenuChrome.TEXT);
            if (tag != null) MenuChrome.tag(font, rx + rw - MenuChrome.tagWidth(tag) - 8, y + (rh - Math.max(10, line() + 2)) / 2, tag, MenuChrome.GOLD);
            target(i, rx, y, rw, rh);
            y += stepY;
        }
        y += 2;
        SetupGuidePreset preset = list.get(Math.min(focus, list.size() - 1));
        int tx = rx + 2, tw = rw - 4;
        List<String[]> rows = preset == SetupGuidePreset.KEEP || preset == SetupGuidePreset.MULTIPLAYER ? List.of() : presetRows(preset);
        int colw = (rw - 12) / 2;
        boolean grid = !rows.isEmpty() && rows.stream().allMatch(r -> MenuChrome.compactWidth(r[0]) + MenuChrome.compactWidth(r[1]) + 8 <= colw);
        List<String> caveats = caveats(preset);
        int noteH = paragraphHeight(styleNote(preset), tw) + 3;
        int setsH = rows.isEmpty() ? 0 : 11 + (grid ? (rows.size() + 1) / 2 : rows.size()) * pitch() + 3;
        boolean keptLine = preset == SetupGuidePreset.BALANCED || preset == SetupGuidePreset.ENHANCED;
        int keptH = keptLine ? paragraphHeight(UiMessages.text("menu.setup.style.others_kept"), tw) + 2 : 0;
        int cavH = 0;
        for (String c : caveats) cavH += paragraphHeight(c, tw) + 2;
        int room = BOTTOM - y + pitch() - line();
        boolean showNote = true, showSets = !rows.isEmpty(), showKept = keptLine, showDetails = false;
        // Drop the least important block until the page fits: never truncate, never hide a caveat.
        if (cavH + noteH + setsH + keptH > room) showKept = false;
        if (cavH + noteH + setsH > room) showNote = false;
        if (cavH + setsH > room && showSets) { showSets = false; showDetails = true; }
        if (showNote) y = paragraph(font, styleNote(preset), tx, y, tw, MenuChrome.TEXT, false) + 3;
        if (showSets) {
            MenuChrome.section(font, UiMessages.text("menu.setup.style.sets_caps"), tx, y + above(), tw);
            y += 11;
            for (int i = 0; i < rows.size(); i++) {
                int cx = grid ? tx + (i % 2) * (colw + 8) : tx;
                int cy = grid ? y + (i / 2) * pitch() : y + i * pitch();
                int cw = grid ? colw : tw;
                MenuChrome.compact(font, rows.get(i)[0], cx, cy + above(), MenuChrome.DIM);
                MenuChrome.compact(font, rows.get(i)[1], cx + cw - MenuChrome.compactWidth(rows.get(i)[1]), cy + above(), MenuChrome.CYAN);
            }
            y += (grid ? (rows.size() + 1) / 2 : rows.size()) * pitch() + 3;
        }
        if (showKept) y = paragraph(font, UiMessages.text("menu.setup.style.others_kept"), tx, y, tw, MenuChrome.DIM, false) + 2;
        if (showDetails) {
            MenuChrome.compact(font, UiMessages.text("menu.path.details_caps") + ": " + UiMessages.text("menu.setup.style.sets_caps"),
                    tx, y + above(), MenuChrome.CYAN);
            y += pitch() + 2;
        }
        for (String c : caveats) y = paragraph(font, c, tx, y, tw, MenuChrome.MUTED, false) + 2;
        if (message != null) paragraph(font, message, tx, y, tw, MenuChrome.AMBER, false);
    }

    private void renderTogether(PixelFont font) {
        int rw = Math.min(width - 48, 300), x = (width - rw) / 2, rh = MenuChrome.actionRowHeight();
        int y = MenuChrome.HEADER_H + 11;
        boolean online = host.onlineAvailable(), local = twoPlayerAvailable();
        actionRow(font, x, y, rw, MenuChrome.Icon.GLOBE, UiMessages.text("menu.setup.mp.online"),
                online ? UiMessages.text("menu.setup.mp.online_note") : UiMessages.text("menu.setup.mp.unavailable"), focus == 0);
        target(0, x, y, rw, rh);
        y += rh + 5;
        actionRow(font, x, y, rw, MenuChrome.Icon.PLAYERS, UiMessages.text("menu.setup.mp.local"),
                UiMessages.text("menu.setup.mp.local_note"), focus == 1);
        target(1, x, y, rw, rh);
        y += rh + 10;
        y = paragraph(font, UiMessages.text("menu.setup.mp.rom_note"), x, y, rw, MenuChrome.MUTED, true) + 5;
        List<SetupGuideLibrary.Game> cartridges = games.stream().filter(g -> !g.hosted()).toList();
        int badge = 0;
        for (SetupGuideLibrary.Game g : cartridges) badge = Math.max(badge, MenuChrome.tagWidth(MenuChrome.gameBadgeText(g.id())));
        int each = badge + 16, gx = (width - cartridges.size() * each + 6) / 2;
        for (SetupGuideLibrary.Game g : cartridges) {
            boolean ok = library.available(g);
            MenuChrome.icon(font, ok ? MenuChrome.Icon.CHECK : MenuChrome.Icon.INFO, gx, y + 2, ok ? MenuChrome.GREEN : MenuChrome.DIM);
            MenuChrome.gameBadge(font, gx + 10, y + 1, g.id(), badge);
            gx += each;
        }
        y += 15;
        y = paragraph(font, UiMessages.text("menu.setup.mp.games_note"), x, y, rw, MenuChrome.DIM, true);
        if (message != null) paragraph(font, message, x, y + 2, rw, MenuChrome.AMBER, true);
    }

    private void renderReady(PixelFont font) {
        int x0 = 24, w = width - 48;
        boolean games = playGame() != null;
        int y = MenuChrome.HEADER_H + 8;
        SetupGuidePreset effective = style == null ? SetupGuidePreset.KEEP : style;
        String aspect = effective.writes().containsKey(SonicConfiguration.DISPLAY_ASPECT)
                ? String.valueOf(effective.writes().get(SonicConfiguration.DISPLAY_ASPECT)) : config.getString(SonicConfiguration.DISPLAY_ASPECT);
        String draw = effective.writes().containsKey(SonicConfiguration.WIDESCREEN_DRAW_MODE)
                ? String.valueOf(effective.writes().get(SonicConfiguration.WIDESCREEN_DRAW_MODE)) : config.getString(SonicConfiguration.WIDESCREEN_DRAW_MODE);
        List<String> first = new ArrayList<>(), second = new ArrayList<>();
        if (games) first.add(upper(UiMessages.text("menu.setup.games.ready_count", Map.of("count", readyCount()))));
        first.add(upper(styleName(effective)));
        second.add(upper(valueLabel(SonicConfiguration.DISPLAY_ASPECT, aspect)));
        if (!"NATIVE_4_3".equalsIgnoreCase(aspect))
            second.add(upper(settingLabel(SonicConfiguration.WIDESCREEN_DRAW_MODE) + ": " + valueLabel(SonicConfiguration.WIDESCREEN_DRAW_MODE, draw)));
        y = chips(font, first, y);
        y = chips(font, second, y + 3) + 10;
        int bw = Math.min(width - 80, 200);
        int bh = Math.max(20, line() + 12);
        if (games) {
            String play = upper(UiMessages.text("menu.setup.ready.play", Map.of("game", MasterTitleScreen.GameEntry.fromGameId(playGame()).menuLabel)));
            goldButton(font, (width - bw) / 2, y, bw, bh, play, focus == 0);
            target(0, (width - bw) / 2, y, bw, bh);
            y += bh + 8;
            MenuChrome.button(font, (width - bw) / 2, y, bw, bh - 2, UiMessages.text("menu.setup.ready.hub"), focus == 1, false, frame);
            target(1, (width - bw) / 2, y, bw, bh - 2);
            y += bh + 8;
            boolean cdFound = this.games.stream().anyMatch(g -> g.hosted() && status(g) == SetupGuideLibrary.Status.FOUND);
            if (cdFound) y = paragraph(font, UiMessages.text("menu.setup.games.cd_check_note"), x0, y, w, MenuChrome.DIM, true) + 4;
        } else {
            String note = UiMessages.text("menu.setup.ready.no_games");
            int tw = MenuChrome.compactWidth(note) + 12;
            MenuChrome.icon(font, MenuChrome.Icon.INFO, Math.max(x0, (width - tw) / 2), y, MenuChrome.CYAN);
            y = paragraph(font, note, Math.max(x0, (width - tw) / 2) + 12, y, Math.min(w, tw), MenuChrome.TEXT, false) + 8;
            MenuChrome.button(font, (width - bw) / 2, y, bw, bh, UiMessages.text("menu.setup.ready.hub"), focus == 0, true, frame);
            target(0, (width - bw) / 2, y, bw, bh);
            y += bh + 8;
            y = paragraph(font, UiMessages.text("menu.setup.later_note"), x0, y, w, MenuChrome.MUTED, true) + 4;
        }
        y = paragraph(font, UiMessages.text("menu.setup.ready.rerun"), x0, y, w, MenuChrome.MUTED, true);
        if (message != null) paragraph(font, message, x0, y + 2, w, MenuChrome.AMBER, true);
    }

    private static String upper(String text) { return text.toUpperCase(HostTextContext.current().locale()); }

    private int chips(PixelFont font, List<String> chips, int y) {
        int total = 0;
        for (String c : chips) total += MenuChrome.chipWidth(c) + 4;
        int x = (width - total + 4) / 2;
        for (String c : chips) x += MenuChrome.chip(font, x, y, c, false) + 4;
        return y + Math.max(MenuChrome.CHIP_H, MenuChrome.compactLineHeight() + 3);
    }

    private void goldButton(PixelFont font, int x, int y, int w, int h, String label, boolean focused) {
        if (focused) MenuChrome.focus(font, x, y, w, h, frame);
        MenuChrome.fill(font, x, y, w, h, MenuChrome.GOLD);
        String shown = MenuChrome.fitLabel(label, w - 20 - MenuChrome.ICON_SIZE);
        int run = MenuChrome.ICON_SIZE + 5 + MenuChrome.labelWidth(shown);
        int sx = x + (w - run) / 2;
        MenuChrome.icon(font, MenuChrome.Icon.PLAY, sx, y + (h - MenuChrome.ICON_SIZE) / 2, MenuChrome.GOLD_INK);
        MenuChrome.label(font, shown, sx + MenuChrome.ICON_SIZE + 5, y + (h - MenuChrome.labelLineHeight()) / 2 + 1, MenuChrome.GOLD_INK);
    }

    /** A centred two-choice prompt: CHECK leads the safe default, WARNING and amber the consequential one. */
    private void renderChoice(PixelFont font, String title, List<String> body, List<String[]> diff, String safe, String risky) {
        MenuChrome.fill(font, 0, 0, width, MenuChrome.SCREEN_H, new float[] {0, 0, 0}, .62f);
        int bw = Math.min(width - 24, 296), bx = (width - bw) / 2, inner = bw - 24;
        List<String> lines = new ArrayList<>();
        for (String b : body) lines.addAll(MenuStyle.wrap(b, inner, MenuStyle.COMPACT));
        int naturalLabel = 0, valueRun = 0;
        if (diff != null) for (String[] d : diff) {
            naturalLabel = Math.max(naturalLabel, MenuChrome.compactWidth(d[0]) + 10);
            valueRun = Math.max(valueRun, MenuChrome.compactWidth(d[1]) + 13 + MenuChrome.compactWidth(d[2]));
        }
        // A label and its change share a row when both fit; otherwise the change takes the next row.
        boolean diffRows = naturalLabel + valueRun <= inner;
        int diffH = diff == null ? 0 : diff.size() * pitch() * (diffRows ? 1 : 2) + 4;
        int button = Math.max(16, line() + 8);
        int content = 10 + MenuChrome.labelLineHeight() + 6 + diffH + lines.size() * pitch() + 8;
        boolean stacked = content + 2 * (button + 6) + 6 <= MenuChrome.FOOTER_Y - MenuChrome.HEADER_H - 8;
        int bh = content + (stacked ? 2 * (button + 6) : button + 6) + 6;
        int top = Math.max(MenuChrome.HEADER_H + 4, (MenuChrome.FOOTER_Y - bh) / 2 + 6);
        MenuChrome.fill(font, bx, top, bw, bh, MenuChrome.NAVY, .98f);
        MenuChrome.outline(font, bx, top, bw, bh, MenuChrome.GOLD, 1f, 1);
        String shownTitle = MenuChrome.fitLabel(title, inner);
        MenuChrome.label(font, shownTitle, (width - MenuChrome.labelWidth(shownTitle)) / 2, top + 9, MenuChrome.GOLD);
        int y = top + 10 + MenuChrome.labelLineHeight() + 6;
        if (diff != null) {
            int labelW = diffRows ? naturalLabel : 8;
            for (String[] d : diff) {
                MenuChrome.compact(font, d[0], bx + 12, y + above(), MenuChrome.TEXT);
                if (!diffRows) y += pitch();
                int x = bx + 12 + labelW;
                MenuChrome.compact(font, d[1], x, y + above(), MenuChrome.DIM);
                x += MenuChrome.compactWidth(d[1]) + 5;
                MenuChrome.triangle(font, x, y + 1, 3, 'R', MenuChrome.GOLD, 1f);
                MenuChrome.compact(font, d[2], x + 8, y + above(), MenuChrome.CYAN);
                y += pitch();
            }
            y += 4;
        }
        for (String row : lines) {
            boolean rtl = HostText.shared().shape(row, TextFace.COMPACT, HostTextContext.current()).rightToLeft();
            int rw = MenuChrome.compactWidth(row);
            MenuChrome.compact(font, row, diff == null ? (width - rw) / 2 : rtl ? bx + 12 + inner - rw : bx + 12, y + above(), MenuChrome.MUTED);
            y += pitch();
        }
        y += 8;
        if (stacked) {
            iconButton(font, bx + 14, y, bw - 28, button, MenuChrome.Icon.CHECK, safe, promptChoice == 0, false);
            target(100, bx + 14, y, bw - 28, button);
            y += button + 6;
            iconButton(font, bx + 14, y, bw - 28, button, MenuChrome.Icon.WARNING, risky, promptChoice == 1, true);
            target(101, bx + 14, y, bw - 28, button);
        } else {
            int half = (bw - 34) / 2;
            iconButton(font, bx + 12, y, half, button, MenuChrome.Icon.CHECK, safe, promptChoice == 0, false);
            target(100, bx + 12, y, half, button);
            iconButton(font, bx + 22 + half, y, half, button, MenuChrome.Icon.WARNING, risky, promptChoice == 1, true);
            target(101, bx + 22 + half, y, half, button);
        }
    }

    private void renderConflict(PixelFont font) {
        var conflict = conflicts.peek();
        if (conflict == null) return;
        String gameLabel = MasterTitleScreen.GameEntry.fromGameId(conflict.game().id()).menuLabel;
        int inner = Math.min(width - 24, 296) - 24;
        List<String> body = new ArrayList<>();
        body.add(UiMessages.text("settings.screen.current") + ": " + String.join("\n", MenuStyle.wrapPath(MenuPathBrowser.friendlyPath(conflict.current()), inner - 50, MenuStyle.COMPACT)));
        body.add(UiMessages.text("menu.setup.games.new_label") + ": " + String.join("\n", MenuStyle.wrapPath(MenuPathBrowser.friendlyPath(conflict.found()), inner - 50, MenuStyle.COMPACT)));
        renderChoice(font, UiMessages.text("menu.setup.games.conflict", Map.of("game", gameLabel)), body, null,
                UiMessages.text("menu.setup.games.keep_current"), UiMessages.text("menu.setup.games.use_new"));
    }

    private void renderApply(PixelFont font) {
        List<String[]> diff = new ArrayList<>();
        for (var change : style.changes(config))
            diff.add(new String[] {settingLabel(change.key()), valueLabel(change.key(), change.before()), valueLabel(change.key(), change.after())});
        renderChoice(font, UiMessages.text("menu.setup.apply.title_caps"), List.of(
                UiMessages.text("menu.setup.style.others_kept"), UiMessages.text("menu.setup.apply.note")), diff,
                UiMessages.text("launch.ui.keep_editing"), UiMessages.text("menu.setup.apply.confirm"));
    }
}
