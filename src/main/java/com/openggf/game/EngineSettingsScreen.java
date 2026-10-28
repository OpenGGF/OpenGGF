package com.openggf.game;

import com.openggf.configuration.ConfigCatalog;
import com.openggf.configuration.ConfigType;
import com.openggf.configuration.EngineSettingsDraft;
import com.openggf.configuration.GlfwKeyNameResolver;
import com.openggf.configuration.PendingRestart;
import com.openggf.configuration.SettingTiming;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.MenuFeedback.Cue.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * All-games Settings: category tabs across the top, full-width rows, a description box
 * for the focused row and a persisted draft. Back with unsaved changes asks
 * Save / Discard / Keep editing, matching Game Setup and the mod manager.
 */
public final class EngineSettingsScreen {
    /** Which tabs the screen shows: the hub's Settings, or Advanced from the Advanced menu. */
    public enum Scope {
        MAIN(EngineSettingsDraft.Category.DISPLAY, EngineSettingsDraft.Category.AUDIO,
                EngineSettingsDraft.Category.INPUT, EngineSettingsDraft.Category.GAMEPLAY,
                EngineSettingsDraft.Category.FILES, EngineSettingsDraft.Category.RECORDING),
        ADVANCED(EngineSettingsDraft.Category.ADVANCED);

        final EngineSettingsDraft.Category[] tabs;
        Scope(EngineSettingsDraft.Category... tabs) { this.tabs = tabs; }
    }

    private static final int VISIBLE_ROWS = 8;
    private static final int TAB_Y = 33;
    private static final int ROW_Y = 54;
    private static final int DESC_Y = 170;
    private static final int DESC_H = 34;
    private static final float[] WHITE = {1, 1, 1};
    private static final float[] AMBER = {1, .73f, .25f};
    private static final float[] CYAN = {.4f, .88f, 1};
    private static final float[] MUTED = {.65f, .75f, .9f};
    private static final String[] PROMPT_OPTIONS = {"Save", "Discard", "Keep editing"};

    private final EngineSettingsDraft draft;
    private final SonicConfigurationService config;
    private final PixelFont font;
    private final Scope scope;
    private final EngineSettingsDraft.Category[] tabs;
    private int category;
    private int row;
    private int scroll;
    private int ticks;
    private int focusTicks;
    private boolean tabsFocused;
    private boolean saveFocused;
    private boolean resetFocused;
    private boolean groupsFocused;
    private int controlGroup;
    private static final String[] CONTROL_GROUP_LABELS = {"PLAYER 1", "PLAYER 2", "CONTROLLERS", "SHORTCUTS"};
    private boolean resetPrompt;
    private int resetChoice = 1;
    private int promptScroll;
    private boolean closeRequested;
    private boolean applied;
    private boolean choosing;
    private int choice;
    private boolean keyMenu;
    private boolean capturing;
    private int captureModifier = GLFW_KEY_UNKNOWN;
    private boolean prompt;
    private int promptChoice;
    private MenuTextEditor textEditor;
    private MenuDetailsScreen details;
    private InputHandler lastInput;
    private int viewportWidth = 320;
    private long presentationRevision = -1;
    private final java.util.Map<SonicConfiguration, String> labels = new java.util.EnumMap<>(SonicConfiguration.class);
    private final java.util.Map<SonicConfiguration, String> valueLabels = new java.util.EnumMap<>(SonicConfiguration.class);
    private final java.util.Map<EngineSettingsDraft.Category, Boolean> categoryChanged = new java.util.EnumMap<>(EngineSettingsDraft.Category.class);
    private final java.util.Map<SonicConfiguration, List<String>> optionCache = new java.util.EnumMap<>(SonicConfiguration.class);
    private String descriptionText;
    private int descriptionWidth;
    private List<String> descriptionLines = List.of();
    private String message;
    private int messageTicks;
    private int lastPointerX = Integer.MIN_VALUE;
    private int lastPointerY = Integer.MIN_VALUE;

    public EngineSettingsScreen(SonicConfigurationService config, PixelFont font) {
        this(config, font, Scope.MAIN);
    }

    public EngineSettingsScreen(SonicConfigurationService config, PixelFont font, Scope scope) {
        this.config = config;
        this.draft = new EngineSettingsDraft(config);
        this.font = font;
        this.scope = java.util.Objects.requireNonNull(scope, "scope");
        this.tabs = scope.tabs;
    }

    /** Set while a game is suspended behind the master title: says which saved changes reach it. */
    private java.util.function.Predicate<SonicConfiguration> pausedGameTakes;

    /** A game is suspended: the description box says whether each setting reaches it on resume. */
    public void setPausedGame(java.util.function.Predicate<SonicConfiguration> appliesOnResume) {
        pausedGameTakes = appliesOnResume;
    }

    private boolean displayPrompt;
    private boolean keepDisplay;

    public void cancelDisplayTrial() {
        com.openggf.configuration.DisplaySettingsTrial.forConfig(config).cancel();
        displayPrompt = false;
    }

    public void update(InputHandler input) {
        if (input == null || closeRequested) return;
        ticks++;
        focusTicks++;
        if (messageTicks > 0 && --messageTicks == 0) message = null;
        lastInput = input;
        if (displayPrompt) { updateDisplayPrompt(input); return; }
        if (details != null) { if (details.update(input)) details = null; return; }
        MenuInput.Pointer pointer = MenuInput.pointer(input, viewportWidth, 224);
        if (resetPrompt) { updateResetPrompt(input, pointer); return; }
        if (prompt) { updatePrompt(input, pointer); return; }
        if (textEditor != null) {
            textEditor.update(input);
            switch (textEditor.consumeResult()) {
                case ACCEPTED -> {
                    try {
                        draft.set(selected(), textEditor.value());
                        MenuFeedback.emit(CONFIRM);
                        textEditor = null;
                    } catch (IllegalArgumentException e) { textEditor.reject(e.getMessage()); }
                }
                case CANCELLED -> textEditor = null;
                case NONE -> { }
            }
            return;
        }
        if (!MenuInput.back(input) && MenuInput.details(input) && !capturing && !tabsFocused && !saveFocused && !resetFocused && !groupsFocused) {
            details = new MenuDetailsScreen(detailsContent(selected()));
            MenuFeedback.emit(CONFIRM);
            return;
        }
        if (capturing) { updateCapture(input); return; }
        if (keyMenu) { updateKeyMenu(input); return; }
        if (choosing) { updateChoice(input); return; }
        if (MenuInput.back(input) || pointer.rightPressed()) { requestClose(); return; }
        if (updatePointer(pointer)) return;
        if (groupsFocused) {
            if (MenuInput.left(input) || MenuInput.right(input)) {
                selectControlGroup(Math.floorMod(controlGroup + (MenuInput.left(input) ? -1 : 1), 4));
                MenuFeedback.emit(NAVIGATE);
            }
            if (MenuInput.up(input)) { groupsFocused = false; tabsFocused = true; MenuFeedback.emit(NAVIGATE); }
            if (MenuInput.down(input) || MenuInput.accept(input)) { groupsFocused = false; focusRow(0); MenuFeedback.emit(NAVIGATE); }
            return;
        }
        if (resetFocused) {
            if (MenuInput.accept(input)) openResetPrompt();
            else if (MenuInput.up(input)) { resetFocused = false; focusRow(keys().size() - 1); MenuFeedback.emit(NAVIGATE); }
            else if (MenuInput.down(input)) { resetFocused = false; if (draft.dirty()) saveFocused = true; else { tabsFocused = tabs.length > 1; focusRow(0); } MenuFeedback.emit(NAVIGATE); }
            return;
        }
        if (saveFocused) {
            if (MenuInput.accept(input)) { if (apply()) closeRequested = true; }
            else if (MenuInput.down(input)) { saveFocused = false; tabsFocused = tabs.length > 1; focusRow(0); MenuFeedback.emit(NAVIGATE); }
            else if (MenuInput.up(input)) { saveFocused = false; tabsFocused = false; focusRow(keys().size() - 1); MenuFeedback.emit(NAVIGATE); }
            return;
        }
        if (tabsFocused) {
            if (MenuInput.up(input) && draft.dirty()) { saveFocused = true; tabsFocused = false; MenuFeedback.emit(NAVIGATE); return; }
            if (MenuInput.left(input) || MenuInput.right(input)) {
                selectCategory(Math.floorMod(category + (MenuInput.left(input) ? -1 : 1), tabs.length));
                MenuFeedback.emit(NAVIGATE);
            }
            if ((MenuInput.down(input) || MenuInput.accept(input)) && !keys().isEmpty()) {
                tabsFocused = false;
                focusRow(0);
                MenuFeedback.emit(NAVIGATE);
            }
            return;
        }
        List<SonicConfiguration> keys = keys();
        if (MenuInput.up(input)) {
            if (row == 0 && tabs[category] == EngineSettingsDraft.Category.INPUT) groupsFocused = true;
            else if (row == 0 && tabs.length > 1) tabsFocused = true;
            else if (row == 0 && draft.dirty()) saveFocused = true;
            else focusRow(Math.floorMod(row - 1, keys.size()));
            MenuFeedback.emit(NAVIGATE);
            return;
        }
        if (MenuInput.down(input)) {
            if (row == keys.size() - 1) resetFocused = true;
            else focusRow((row + 1) % keys.size());
            MenuFeedback.emit(NAVIGATE); return;
        }
        try {
            if (MenuInput.left(input) || MenuInput.right(input)) {
                String before = draft.text(selected());
                draft.step(selected(), MenuInput.left(input) ? -1 : 1);
                if (!before.equals(draft.text(selected()))) MenuFeedback.emit(NAVIGATE);
            } else if (MenuInput.accept(input)) {
                openEditor();
            }
        } catch (IllegalArgumentException e) { flash(e.getMessage()); MenuFeedback.emit(ERROR); }
    }

    private void openEditor() {
        MenuFeedback.emit(CONFIRM);
        ConfigType type = ConfigCatalog.meta(selected()).type();
        if (selected() == SonicConfiguration.AUDIO_HUB_MUSIC_VOLUME) return;
        if (type == ConfigType.KEY) { keyMenu = true; choice = 0; }
        else if (type == ConfigType.BOOL || type == ConfigType.ENUM) {
            choosing = true;
            choice = Math.max(0, options().indexOf(draft.text(selected())));
        } else openTextEditor();
    }

    boolean updatePointer(MenuInput.Pointer pointer) {
        if (!pointer.inside()) return false;

        boolean moved = pointer.x() != lastPointerX || pointer.y() != lastPointerY;
        lastPointerX = pointer.x();
        lastPointerY = pointer.y();
        if (draft.dirty() && pointer.over(viewportWidth - 112, 8, 104, 17)) {
            if (moved || pointer.leftPressed()) { saveFocused = true; tabsFocused = false; resetFocused = false; groupsFocused = false; }
            if (pointer.leftPressed() && apply()) closeRequested = true;
            return pointer.leftPressed();
        }
        if (pointer.over(viewportWidth - resetButtonWidth() - 8, 155, resetButtonWidth(), 12)) {
            if (moved || pointer.leftPressed()) { resetFocused = true; tabsFocused = false; saveFocused = false; groupsFocused = false; }
            if (pointer.leftPressed()) openResetPrompt();
            return pointer.leftPressed();
        }
        int[] xs = tabXs(viewportWidth);
        for (int i = 0; i < tabs.length && tabs.length > 1; i++) {
            if (!pointer.over(xs[i], TAB_Y, tabWidth(i), 14)) continue;
            if (pointer.leftPressed() && (i != category || !tabsFocused)) {
                selectCategory(i);
                tabsFocused = true;
                saveFocused = false;
                MenuFeedback.emit(NAVIGATE);
            }
            return pointer.leftPressed();
        }
        if (tabs[category] == EngineSettingsDraft.Category.INPUT) {
            for (int i = 0; i < 4; i++) {
                int x = 8 + i * (viewportWidth - 16) / 4;
                if (pointer.over(x, ROW_Y - 2, (viewportWidth - 16) / 4, 12) && pointer.leftPressed()) {
                    selectControlGroup(i); groupsFocused = true; tabsFocused = false; saveFocused = false; resetFocused = false;
                    MenuFeedback.emit(NAVIGATE); return true;
                }
            }
        }
        List<Line> lines = lines();
        for (int i = 0; i < visibleRows() && scroll + i < lines.size(); i++) {
            if (!pointer.over(8, rowTop() + i * rowHeight() - 1, viewportWidth - 22, rowHeight())) continue;
            Line line = lines.get(scroll + i);
            if (line.caption() != null) return false; // Captions name a group; they are not rows.
            int index = line.index();
            if ((moved || pointer.leftPressed()) && (tabsFocused || saveFocused || resetFocused || groupsFocused || row != index)) {
                tabsFocused = false;
                focusRow(index);
                MenuFeedback.emit(NAVIGATE);
            }
            if (selected() == SonicConfiguration.AUDIO_HUB_MUSIC_VOLUME && pointer.leftPressed() && pointer.x() >= viewportWidth - 126) {
                int sliderX = viewportWidth - 126;
                int percent = Math.clamp((pointer.x() - sliderX) * 100 / 85, 0, 100);
                draft.set(selected(), Integer.toString(percent)); MenuFeedback.emit(NAVIGATE); return true;
            }
            if (pointer.leftPressed()) { openEditor(); return true; }
            return false;
        }
        return false;
    }

    private void selectCategory(int index) {
        resetFocused = false;
        groupsFocused = false;
        category = index;
        row = 0;
        scroll = 0;
        focusTicks = 0;
    }

    private void focusRow(int index) {
        saveFocused = false;
        resetFocused = false;
        groupsFocused = false;
        row = index;
        // The list scrolls by drawn lines; a group's first setting brings its caption into view.
        List<Line> lines = lines();
        int line = lineOf(index);
        int top = line > 0 && lines.get(line - 1).caption() != null ? line - 1 : line;
        if (top < scroll) scroll = top;
        if (line >= scroll + visibleRows()) scroll = line - visibleRows() + 1;
        focusTicks = 0;
    }

    /**
     * One drawn list line: a group caption, never focusable, or the setting at {@code index} in
     * {@link #keys()}. Display and Advanced group their rows under captions; other tabs have none.
     */
    record Line(String caption, int index, SonicConfiguration key) { }

    private List<Line> cachedLines = List.of();
    private EngineSettingsDraft.Category cachedLinesTab;
    private int cachedLinesGroup = -1;

    List<Line> lines() {
        EngineSettingsDraft.Category tab = tabs[Math.min(category, tabs.length - 1)];
        if (tab == cachedLinesTab && controlGroup == cachedLinesGroup) return cachedLines;
        List<SonicConfiguration> keys = keys();
        List<Line> lines = new ArrayList<>(keys.size() + 8);
        String caption = null;
        for (int i = 0; i < keys.size(); i++) {
            String group = controlsTab() ? null : EngineSettingLabels.group(keys.get(i));
            if (group != null && !group.equals(caption)) lines.add(new Line(group, -1, null));
            caption = group;
            lines.add(new Line(null, i, keys.get(i)));
        }
        cachedLines = List.copyOf(lines);
        cachedLinesTab = tab;
        cachedLinesGroup = controlGroup;
        return cachedLines;
    }

    private int lineOf(int index) {
        List<Line> lines = lines();
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).index() == index) return i;
        throw new IllegalArgumentException("No row " + index);
    }

    /** The drawn top of a setting's row in the current scroll position, or -1 when scrolled out. */
    int rowYForTest(SonicConfiguration key) {
        List<Line> lines = lines();
        for (int i = scroll; i < Math.min(lines.size(), scroll + visibleRows()); i++)
            if (lines.get(i).key() == key) return rowTop() + (i - scroll) * rowHeight();
        return -1;
    }

    SonicConfiguration selectedForTest() { return selected(); }

    /** Whether a dependent setting has an effect with its parent's current draft value. */
    private boolean inEffect(SonicConfiguration key) {
        SonicConfiguration parent = EngineSettingLabels.parent(key);
        return parent == null || EngineSettingLabels.inEffect(key, draft.text(parent));
    }

    /** Footer pill while Settings saved since start-up wait for a restart. */
    static final String RESTART_STATUS = "RESTART NEEDED";
    private static final String RESTART_TAG = "RESTART";

    record Timing(String text, float[] color) { }

    /**
     * The status line for a row, or null to keep both description lines: a restart wins, then a
     * paused game's view of the change. When the rest apply is in Details ({@link #effect}).
     */
    Timing status(SonicConfiguration key) {
        if (restartPending(key)) {
            return new Timing("Needs a restart: close and reopen OpenGGF.", MenuChrome.AMBER);
        }
        if (key == SonicConfiguration.AUDIO_HUB_MUSIC_ENABLED || key == SonicConfiguration.AUDIO_HUB_MUSIC_VOLUME)
            return new Timing("Applies to the hub as soon as you save.", MenuChrome.CYAN);
        if (!inEffect(key)) return new Timing(EngineSettingLabels.notInEffect(key), MenuChrome.MUTED);
        if (pausedGameTakes == null) return null;
        return pausedGameTakes.test(key) ? new Timing("Your paused game takes this when it resumes.", MenuChrome.CYAN)
                : new Timing("Applies when you next start a game.", MenuChrome.MUTED);
    }

    boolean restartPending(SonicConfiguration key) {
        return PendingRestart.awaiting(key, draft.value(key), draft.changed(key));
    }

    private static int restartTagWidth() {
        return MenuChrome.compactWidth(RESTART_TAG) + 6;
    }

    /** A small amber tag on rows with changes awaiting a restart. */
    private void restartTag(int x, int y) {
        int w = restartTagWidth();
        MenuChrome.fill(font, x, y, w, 10, MenuChrome.NAVY, .95f);
        MenuChrome.outline(font, x, y, w, 10, MenuChrome.AMBER, 1f, 1);
        MenuChrome.compact(font, RESTART_TAG, x + 3, y + 1, MenuChrome.AMBER);
    }

    private void flash(String text) {
        message = text;
        messageTicks = 180;
    }

    private int visibleRows() { return VISIBLE_ROWS; }
    // Controls rows sit under the group tabs; 11 px keeps a two-pixel gap between the 9 px key labels
    // and leaves the last focus band clear of the reset button.
    private int rowTop() { return tabs[category] == EngineSettingsDraft.Category.INPUT ? ROW_Y + 13 : ROW_Y; }
    private int rowHeight() { return tabs[category] == EngineSettingsDraft.Category.INPUT ? 11 : 12; }
    private static int controlGroup(SonicConfiguration key) {
        String section = ConfigCatalog.meta(key).section();
        return section.equals("input.player1") ? 0 : section.equals("input.player2") ? 1
                : section.equals("input.controller") ? 2 : 3;
    }
    private void selectControlGroup(int group) { controlGroup = group; row = 0; scroll = 0; focusTicks = 0; }
    private boolean controlsTab() { return tabs[category] == EngineSettingsDraft.Category.INPUT; }
    private int resetButtonWidth() { return controlsTab() ? 100 : 74; }
    private String resetButtonText() { return controlsTab() ? "RESET BINDINGS" : "RESET TAB"; }
    private void openResetPrompt() { resetPrompt = true; resetChoice = 1; MenuFeedback.emit(CONFIRM); }
    private void updateResetPrompt(InputHandler input, MenuInput.Pointer pointer) {
        if (MenuInput.back(input) || pointer.rightPressed()) { resetPrompt = false; MenuFeedback.emit(CANCEL); return; }
        if (MenuInput.left(input) || MenuInput.right(input) || MenuInput.up(input) || MenuInput.down(input)) {
            resetChoice = 1 - resetChoice; MenuFeedback.emit(NAVIGATE);
        }
        boolean accept = MenuInput.accept(input);
        for (int i = 0; i < 2; i++) {
            if (pointer.over(viewportWidth / 2 - 92 + i * 96, 130, 88, 17) && pointer.leftPressed()) {
                resetChoice = i; accept = true;
            }
        }
        if (!accept) return;
        resetPrompt = false;
        if (resetChoice == 0) { draft.reset(tabs[category]); MenuFeedback.emit(CONFIRM); }
        else MenuFeedback.emit(CANCEL);
    }

    private List<SonicConfiguration> keys() {
        List<SonicConfiguration> keys = draft.keys(tabs[Math.min(category, tabs.length - 1)]);
        if (tabs[category] == EngineSettingsDraft.Category.INPUT)
            return keys.stream().filter(key -> controlGroup(key) == controlGroup).toList();
        // Captioned tabs keep each group together, in caption order; within a group, catalog order.
        return keys.stream().sorted(java.util.Comparator.comparingInt(key -> {
            String group = EngineSettingLabels.group(key);
            return group == null ? -1 : EngineSettingLabels.GROUPS.indexOf(group);
        })).toList();
    }

    private SonicConfiguration selected() {
        List<SonicConfiguration> keys = keys();
        return keys.get(Math.clamp(row, 0, keys.size() - 1));
    }

    private void requestClose() {
        if (draft.dirty()) {
            prompt = true;
            promptChoice = 0;
            promptScroll = 0;
            MenuFeedback.emit(CONFIRM);
        } else {
            MenuFeedback.emit(CANCEL);
            closeRequested = true;
        }
    }

    private void updatePrompt(InputHandler input, MenuInput.Pointer pointer) {
        int before = promptChoice;
        if (MenuInput.back(input) || pointer.rightPressed()) { prompt = false; MenuFeedback.emit(CANCEL); return; }
        if (MenuInput.left(input)) promptChoice = Math.floorMod(promptChoice - 1, 3);
        if (MenuInput.right(input)) promptChoice = (promptChoice + 1) % 3;
        if (MenuInput.up(input)) promptScroll = Math.max(0, promptScroll - 1);
        if (MenuInput.down(input)) promptScroll = Math.min(Math.max(0, changeSummaryLines().size() - 8), promptScroll + 1);
        boolean choose = MenuInput.accept(input);
        int[] xs = promptButtonXs(viewportWidth);
        boolean moved = pointer.x() != lastPointerX || pointer.y() != lastPointerY;
        lastPointerX = pointer.x();
        lastPointerY = pointer.y();
        for (int i = 0; i < 3; i++) {
            if (!pointer.over(xs[i], 176, promptButtonWidth(i), 16)) continue;
            if (pointer.leftPressed() || moved) promptChoice = i;
            choose |= pointer.leftPressed();
        }
        if (before != promptChoice) MenuFeedback.emit(NAVIGATE);
        if (!choose) return;
        prompt = false;
        switch (promptChoice) {
            case 0 -> {
                if (apply()) closeRequested = true;
            }
            case 1 -> { MenuFeedback.emit(CANCEL); closeRequested = true; }
            default -> MenuFeedback.emit(CANCEL);
        }
    }

    private boolean apply() {
        if (draft.changed(SonicConfiguration.DISPLAY_WINDOW_MODE)) {
            com.openggf.configuration.DisplaySettingsTrial.forConfig(config).begin(draft.text(SonicConfiguration.DISPLAY_WINDOW_MODE));
            displayPrompt = true;
            keepDisplay = false;
            MenuFeedback.emit(CONFIRM);
            return false;
        }
        return saveDraft();
    }

    private void updateDisplayPrompt(InputHandler input) {
        var trial = com.openggf.configuration.DisplaySettingsTrial.forConfig(config);
        var pointer = MenuInput.pointer(input, viewportWidth, 224);
        if (!trial.active() || MenuInput.back(input) || pointer.rightPressed()) {
            cancelDisplayTrial();
            flash("Display restored; changes not saved");
            MenuFeedback.emit(CANCEL);
            return;
        }
        if (MenuInput.left(input) || MenuInput.right(input) || MenuInput.up(input) || MenuInput.down(input))
            keepDisplay = !keepDisplay;
        boolean accept = MenuInput.accept(input);
        boolean moved = pointer.x() != lastPointerX || pointer.y() != lastPointerY;
        lastPointerX = pointer.x();
        lastPointerY = pointer.y();
        for (int i = 0; i < 2; i++) {
            if (pointer.over(30, 120 + i * 28, viewportWidth - 60, 22)) {
                if (moved || pointer.leftPressed()) keepDisplay = i == 0;
                accept |= pointer.leftPressed();
            }
        }
        if (!accept) return;
        if (keepDisplay) {
            // Persistence is atomic. A failed save restores the display and leaves the draft editable.
            boolean saved = saveDraft();
            cancelDisplayTrial();
            if (saved) closeRequested = true;
        } else {
            cancelDisplayTrial();
            flash("Display restored; changes not saved");
            MenuFeedback.emit(CANCEL);
        }
    }

    private void renderDisplayPrompt(int width) {
        MenuChrome.background(font, width);
        MenuChrome.header(font, width, "KEEP DISPLAY SETTINGS?", null);
        var trial = com.openggf.configuration.DisplaySettingsTrial.forConfig(config);
        // The countdown reads as a warning with a draining meter, the shared progress treatment.
        MenuChrome.descriptionBox(font, 20, 52, width - 40, 50, List.of(), List.of());
        MenuChrome.icon(font, MenuChrome.Icon.WARNING, 28, 60, MenuChrome.AMBER);
        MenuChrome.compact(font, "Reverting in " + trial.secondsRemaining() + " seconds", 40, 60, MenuChrome.AMBER);
        MenuChrome.fill(font, 28, 74, width - 56, 2, MenuChrome.EDGE);
        MenuChrome.fill(font, 28, 74, Math.round((width - 56) * trial.remainingFraction()), 2, MenuChrome.AMBER);
        MenuChrome.compact(font, "Keep saves all your pending changes.", 28, 84, MenuChrome.MUTED);
        String[] labels = {"Keep changes", "Revert display"};
        MenuChrome.Icon[] icons = {MenuChrome.Icon.CHECK, MenuChrome.Icon.WARNING};
        for (int i = 0; i < 2; i++) {
            int y = 120 + i * 28;
            boolean focused = keepDisplay == (i == 0);
            if (focused) MenuChrome.focus(font, 30, y, width - 60, 22, ticks);
            else MenuChrome.panel(font, 30, y, width - 60, 22);
            MenuChrome.icon(font, icons[i], 40, y + 8, i == 0 ? MenuChrome.GREEN : MenuChrome.CYAN);
            MenuChrome.compact(font, labels[i], 52, y + 7, focused ? MenuChrome.WHITE : MenuChrome.TEXT);
        }
        MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(lastInput), "CHOOSE"),
                MenuChrome.hint(MenuChrome.backKey(lastInput), "REVERT")), MenuChrome.controller(lastInput));
    }

    private boolean saveDraft() {
        try {
            boolean changed = draft.dirty();
            draft.apply();
            MenuFeedback.emit(CONFIRM);
            applied |= changed;
            return true;
        } catch (IOException e) {
            flash("Save failed - try again");
            MenuFeedback.emit(ERROR);
        } catch (IllegalArgumentException e) {
            flash(e.getMessage());
            MenuFeedback.emit(ERROR);
        }
        return false;
    }

    private void openTextEditor() {
        Object defaultValue = config.getDefaultValue(selected());
        textEditor = new MenuTextEditor(EngineSettingLabels.label(selected()), draft.text(selected()), 4096,
                defaultValue == null ? "" : defaultValue.toString(), fieldMode(selected()));
        textEditor.deferAcceptanceFeedback();
    }

    private List<String> options() {
        return optionCache.computeIfAbsent(selected(), key -> {
            if (ConfigCatalog.meta(key).type() == ConfigType.BOOL) return List.of("false", "true");
            return ConfigCatalog.meta(key).allowedValues().stream().sorted().toList();
        });
    }

    static MenuTextEditor.Mode fieldMode(SonicConfiguration key) {
        return switch (key) {
            case SONIC_1_ROM, SONIC_2_ROM, SONIC_3K_ROM, ROMS_DIRECTORY, PLAYBACK_MOVIE_PATH,
                    TRACE_CATALOG_DIR, CAPTURE_OUTPUT_DIR -> MenuTextEditor.Mode.PATH;
            default -> switch (ConfigCatalog.meta(key).type()) {
                case INT -> MenuTextEditor.Mode.INTEGER;
                case DOUBLE -> MenuTextEditor.Mode.DECIMAL;
                default -> MenuTextEditor.Mode.TEXT;
            };
        };
    }

    static String effect(SonicConfiguration key) {
        // InputBindingFactory's supplier is sampled by InputHandler each frame.
        return switch (key) {
            case UP, DOWN, LEFT, RIGHT, P1_A, P1_B, P1_C, START,
                    P2_UP, P2_DOWN, P2_LEFT, P2_RIGHT, P2_A, P2_B, P2_C, P2_START,
                    CONTROLLER_ENABLED, CONTROLLER_DEADZONE, CONTROLLER_PLAYER1, CONTROLLER_PLAYER2,
                    DEBUG_MODE_KEY, LIVE_REWIND_KEY, FRAME_STEP_KEY -> "Takes effect immediately after saving.";
            case DISPLAY_COLOR_EXPANSION -> "Takes effect immediately after saving, including cached palette colours.";
            // MasterTitleScreen refreshes ROM previews on consumeApplied; load resolves saved ROM paths.
            case SONIC_1_ROM, SONIC_2_ROM, SONIC_3K_ROM, ROMS_DIRECTORY, ROMS_PREFER_COMPOSITE ->
                    "After saving: the ROM catalogue rescans and previews refresh; used for the next game load.";
            case TRACE_CATALOG_DIR -> "After saving: used when the Playback browser next opens.";
            // LiveCaptureRecorderFactory reads these when creating a recording.
            case CAPTURE_CODEC, CAPTURE_AUDIO_CODEC, CAPTURE_ENCODER_THREADS, CAPTURE_ENCODER_PRESET,
                    CAPTURE_FFMPEG_PASS1_ARGS, CAPTURE_FFMPEG_PASS2_ARGS, CAPTURE_QUEUE_BUDGET_MB,
                    CAPTURE_OUTPUT_DIR, CAPTURE_CONTAINER -> "After saving: used for the next recording.";
            // LaunchProfileApplier only overrides these for games whose setup is not Default.
            // One authored sentence per timing class; a paused game's share follows LiveSettings.
            case LIVE_REWIND_ENABLED, DEBUG_VIEW_ENABLED ->
                    "For every game whose Game Setup uses the global value, when you next start or resume a game.";
            case DISPLAY_ASPECT ->
                    "For every game whose Game Setup uses the global value, when you next start a game or resume one paused in a level.";
            default -> switch (SettingTiming.of(key)) {
                case NOW -> "Takes effect as soon as you save.";
                case NEXT_GAME -> com.openggf.game.launch.LiveSettings.appliesOnResume(key, false)
                        ? "Takes effect when you next start a game or resume a paused one."
                        : "Takes effect when you next start a game.";
                case RESTART -> "Takes effect after you close and reopen OpenGGF.";
            };
        };
    }

    private void refreshPresentation() {
        if (presentationRevision == draft.revision()) return;
        for (EngineSettingsDraft.Category group : tabs) {
            boolean any = false;
            for (SonicConfiguration key : draft.keys(group)) {
                labels.computeIfAbsent(key, EngineSettingLabels::rowLabel);
                valueLabels.put(key, MenuDetailsScreen.readable(EngineSettingLabels.value(key, draft.text(key))));
                any |= draft.nonDefault(key);
            }
            categoryChanged.put(group, any);
        }
        presentationRevision = draft.revision();
    }

    private void updateChoice(InputHandler input) {
        if (MenuInput.back(input)) { choosing = false; MenuFeedback.emit(CANCEL); return; }
        int count = options().size() + 1;
        if (MenuInput.up(input)) { choice = Math.floorMod(choice - 1, count); MenuFeedback.emit(NAVIGATE); }
        if (MenuInput.down(input)) { choice = (choice + 1) % count; MenuFeedback.emit(NAVIGATE); }
        if (MenuInput.accept(input)) {
            MenuFeedback.emit(CONFIRM);
            if (choice == count - 1) draft.reset(selected());
            else draft.set(selected(), options().get(choice));
            choosing = false;
        }
    }

    private void updateKeyMenu(InputHandler input) {
        if (MenuInput.back(input)) { keyMenu = false; MenuFeedback.emit(CANCEL); return; }
        if (MenuInput.up(input)) { choice = Math.floorMod(choice - 1, 4); MenuFeedback.emit(NAVIGATE); }
        if (MenuInput.down(input)) { choice = (choice + 1) % 4; MenuFeedback.emit(NAVIGATE); }
        if (MenuInput.accept(input)) {
            MenuFeedback.emit(CONFIRM);
            switch (choice) {
                case 0 -> { captureModifier = GLFW_KEY_UNKNOWN; capturing = true; }
                case 1 -> openTextEditor();
                case 2 -> { draft.set(selected(), ""); keyMenu = false; }
                case 3 -> { draft.reset(selected()); keyMenu = false; }
                default -> throw new IllegalStateException("Unknown binding action");
            }
        }
    }

    private void updateCapture(InputHandler input) {
        if (MenuInput.textBack(input)) {
            capturing = false;
            captureModifier = GLFW_KEY_UNKNOWN;
            MenuFeedback.emit(CANCEL);
            return;
        }
        for (int key = GLFW_KEY_SPACE; key <= GLFW_KEY_LAST; key++) {
            if (!MenuInput.textKeyPressed(input, key)) continue;
            if (key >= GLFW_KEY_LEFT_SHIFT && key <= GLFW_KEY_RIGHT_SUPER) {
                // Wait for a following key to distinguish a chord from a modifier tap.
                captureModifier = key;
                continue;
            }
            String chord = (input.isPhysicalControlDown() ? "CTRL+" : "")
                    + (input.isPhysicalShiftDown() ? "SHIFT+" : "")
                    + (input.isPhysicalAltDown() ? "ALT+" : "")
                    + (input.isPhysicalSuperDown() ? "META+" : "")
                    + GlfwKeyNameResolver.nameOf(key);
            finishCapture(chord);
            return;
        }
        if (captureModifier != GLFW_KEY_UNKNOWN && !input.isPhysicalKeyDown(captureModifier)) {
            // Modifier keys are valid player buttons too (stock P2 A uses Right Shift).
            finishCapture(GlfwKeyNameResolver.nameOf(captureModifier));
        }
    }

    private void finishCapture(String chord) {
        draft.set(selected(), chord);
        MenuFeedback.emit(CONFIRM);
        capturing = false;
        captureModifier = GLFW_KEY_UNKNOWN;
        keyMenu = false;
    }

    public boolean consumeCloseRequested() {
        boolean result = closeRequested;
        closeRequested = false;
        return result;
    }

    public boolean consumeApplied() {
        boolean result = applied;
        applied = false;
        return result;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private int tabWidth(int i) {
        return MenuChrome.compactWidth(tabs[i].label().toUpperCase(java.util.Locale.ROOT)) + 8;
    }

    private int[] tabXs(int width) {
        int total = 0;
        for (int i = 0; i < tabs.length; i++) total += tabWidth(i) + (i == 0 ? 0 : 3);
        int x = (width - total) / 2;
        int[] xs = new int[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            xs[i] = x;
            x += tabWidth(i) + 3;
        }
        return xs;
    }

    public void render(int viewportWidth) {
        if (font == null) return;
        int width = Math.max(320, viewportWidth);
        this.viewportWidth = width;
        if (details != null) { details.render(font, width); return; }
        refreshPresentation();
        if (textEditor != null) { textEditor.render(font, width); return; }
        if (choosing) { renderChoice(width); return; }
        if (keyMenu || capturing) { renderKeyMenu(width); return; }
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, width);
            MenuChrome.header(font, width, scope == Scope.ADVANCED ? "ADVANCED SETTINGS" : "SETTINGS", draft.dirty() ? "" : "ALL GAMES");
            if (draft.dirty()) {
                if (saveFocused && !prompt) MenuChrome.focus(font, width - 112, 8, 104, 17, focusTicks);
                else MenuChrome.panel(font, width - 112, 8, 104, 17);
                MenuChrome.compact(font, "SAVE CHANGES", width - 103, 13, MenuChrome.GOLD);
            }
            int contentTop = renderTabs(width);
            renderRows(width, rowTop());
            if (tabs[category] == EngineSettingsDraft.Category.INPUT) renderControlGroups(width);
            int resetX = width - resetButtonWidth() - 8;
            if (resetFocused) MenuChrome.focus(font, resetX, 155, resetButtonWidth(), 12, focusTicks);
            else MenuChrome.panel(font, resetX, 155, resetButtonWidth(), 12);
            // The legend explains the amber marker only when this tab shows one.
            if (Boolean.TRUE.equals(categoryChanged.get(tabs[category]))) {
                MenuChrome.changedMarker(font, 11, 161);
                MenuChrome.compact(font, "CHANGED FROM DEFAULT", 18, 158, MenuChrome.DIM);
            }
            MenuChrome.compact(font, resetButtonText(), resetX + 7, 158, MenuChrome.AMBER);
            renderDescription(width);
            int unsaved = draft.changedCount();
            String status = message != null ? message : unsaved > 0 ? unsaved + " UNSAVED"
                    : PendingRestart.current(config).isEmpty() ? null : RESTART_STATUS;
            List<MenuChrome.Hint> hints = new ArrayList<>();
            if (saveFocused) hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), "SAVE"));
            else if (resetFocused) hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), "RESET"));
            else hints.add(MenuChrome.leftRight(lastInput, tabsFocused ? "TAB" : groupsFocused ? "GROUP" : "CHANGE"));
            if (tabsFocused) hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), "SETTINGS"));
            hints.add(MenuChrome.hint(MenuChrome.backKey(lastInput), draft.dirty() ? "SAVE OR DISCARD" : "BACK"));
            MenuChrome.footer(font, width, status, hints, MenuChrome.controller(lastInput));
            if (prompt) renderPrompt(width);
            if (resetPrompt) renderResetPrompt(width);
            if (displayPrompt) renderDisplayPrompt(width);
        } finally {
            font.endMegaBatch();
        }
    }

    private void renderControlGroups(int width) {
        for (int i = 0; i < 4; i++) {
            int x = 8 + i * (width - 16) / 4;
            int w = (width - 16) / 4 - 2;
            if (i == controlGroup && groupsFocused) MenuChrome.focus(font, x, ROW_Y - 2, w, 12, focusTicks);
            else MenuChrome.panel(font, x, ROW_Y - 2, w, 12);
            MenuChrome.compact(font, CONTROL_GROUP_LABELS[i], x + 3, ROW_Y + 1,
                    i == controlGroup ? MenuChrome.GOLD : MenuChrome.MUTED);
        }
    }

    /** Draws the tab strip; returns the y where rows may begin. */
    private int renderTabs(int width) {
        if (tabs.length <= 1) return ROW_Y;
        int[] xs = tabXs(width);
        for (int i = 0; i < tabs.length; i++) {
            int tw = tabWidth(i);
            boolean selected = i == category;
            if (selected && tabsFocused) MenuChrome.focus(font, xs[i], TAB_Y, tw, 14, focusTicks);
            if (selected) {
                MenuChrome.fill(font, xs[i] + (tabsFocused ? 2 : 0), TAB_Y + (tabsFocused ? 2 : 0),
                        tw - (tabsFocused ? 4 : 0), 14 - (tabsFocused ? 4 : 0), MenuChrome.GOLD);
            } else {
                MenuChrome.panel(font, xs[i], TAB_Y, tw, 14);
            }
            String label = tabs[i].label().toUpperCase(java.util.Locale.ROOT);
            MenuChrome.compact(font, label, xs[i] + 4, TAB_Y + 3, selected ? MenuChrome.GOLD_INK : MenuChrome.TEXT);
            if (Boolean.TRUE.equals(categoryChanged.get(tabs[i]))) {
                MenuChrome.fill(font, xs[i] + tw - 4, TAB_Y + 1, 3, 3, selected ? new float[] {.63f, .29f, 0f} : MenuChrome.AMBER);
            }
        }
        return ROW_Y;
    }

    private void renderRows(int width, int top) {
        List<Line> lines = lines();
        int visible = Math.min(visibleRows(), lines.size() - scroll);
        // Every tab uses the light row face; the bold face is reserved for headings.
        for (int i = 0; i < visible; i++) {
            Line line = lines.get(scroll + i);
            int y = top + i * rowHeight();
            if (line.caption() != null) {
                MenuChrome.section(font, line.caption(), 12, y + 2, width - 34);
                continue;
            }
            int index = line.index();
            SonicConfiguration key = line.key();
            // A dependent setting sits 8 px under the one it needs, dimmed while that is off.
            int indent = EngineSettingLabels.parent(key) == null ? 0 : 8;
            boolean live = inEffect(key);
            boolean focused = !tabsFocused && !saveFocused && !resetFocused && !groupsFocused && row == index && !prompt && !resetPrompt;
            if (focused) MenuChrome.focus(font, 8, y - 1, width - 22, rowHeight(), focusTicks);
            boolean nonDefault = draft.nonDefault(key);
            if (nonDefault) MenuChrome.changedMarker(font, 11 + indent, y + 3);
            String value = shownValue(key);
            if (key == SonicConfiguration.AUDIO_HUB_MUSIC_VOLUME) {
                int sliderX = width - 126;
                int percent = Integer.parseInt(draft.text(key));
                MenuChrome.fill(font, sliderX, y + 4, 85, 2, MenuChrome.EDGE);
                MenuChrome.fill(font, sliderX, y + 4, 85 * percent / 100, 2, MenuChrome.CYAN);
                MenuChrome.fill(font, sliderX + 85 * percent / 100 - 1, y + 1, 3, 8, MenuChrome.GOLD);
                MenuChrome.compact(font, percent + "%", width - 38, y + 1, MenuChrome.WHITE);
                MenuChrome.compact(font, rowLabel(key), 18, y + 1, MenuChrome.TEXT);
                continue;
            }
            int right = width - 22 - (focused ? 8 : 0);
            float[] valueColor = !live ? MenuChrome.DIM : nonDefault ? MenuChrome.AMBER : MenuChrome.WHITE;
            float[] labelColor = focused ? MenuChrome.WHITE : live ? MenuChrome.TEXT : MenuChrome.DIM;
            int valueX;
            boolean restart = restartPending(key);
            // Values keep up to half the row; labels take what is left and scroll when focused.
            int valueRoom = Math.max(30, (width - 40) / 2);
            if (ConfigCatalog.meta(key).type() == com.openggf.configuration.ConfigType.KEY && !value.equals("Unbound")
                    && MenuChrome.keyCapWidth(value) <= valueRoom) {
                // A binding reads as the key itself; dense rows use the outline-free key label.
                if (rowHeight() < 12) {
                    valueX = right - MenuChrome.keyLabelWidth(value);
                    MenuChrome.keyLabel(font, valueX, y, value);
                } else {
                    valueX = right - MenuChrome.keyCapWidth(value);
                    MenuChrome.keyCap(font, valueX, y, value, false);
                }
            } else {
                String v = window(value, valueRoom, focused);
                valueX = right - MenuChrome.compactWidth(v);
                MenuChrome.compact(font, v, valueX, y + 1, value.equals("Unbound") ? MenuChrome.DIM : valueColor);
                valueX = switchBeside(key, valueX, y + 1);
            }
            int labelX = 18 + indent;
            int labelRoom = valueX - 10 - labelX;
            if (restart) {
                restartTag(valueX - 10 - restartTagWidth(), y);
                labelRoom -= restartTagWidth() + 4;
            }
            String shownLabel = window(rowLabel(key), labelRoom, focused);
            MenuChrome.compact(font, shownLabel, labelX, y + 1, labelColor);
            if (!focused) leader(labelX + MenuChrome.compactWidth(shownLabel) + 4,
                    valueX - (restart ? restartTagWidth() + 14 : 4), y + 7);
            if (focused) {
                MenuChrome.triangle(font, valueX - 7, y + 2, 3, 'L', MenuChrome.GOLD, 1f);
                MenuChrome.triangle(font, width - 25, y + 2, 3, 'R', MenuChrome.GOLD, 1f);
            }
        }
        if (lines.size() > visibleRows()) {
            // A slim scrollbar along the right edge of the rows.
            int trackTop = top - 3;
            int trackH = visibleRows() * rowHeight();
            int thumbH = Math.max(8, trackH * visibleRows() / lines.size());
            int thumbY = trackTop + (trackH - thumbH) * scroll / Math.max(1, lines.size() - visibleRows());
            MenuChrome.fill(font, width - 11, trackTop, 2, trackH, MenuChrome.EDGE, .6f);
            MenuChrome.fill(font, width - 12, thumbY, 4, thumbH, MenuChrome.CYAN);
        }
    }

    /**
     * On/off rows show the shared switch beside their word, as Recordings and Mods do;
     * returns the new left edge of the value so arrows and leaders stay clear of it.
     */
    private int switchBeside(SonicConfiguration key, int valueX, int y) {
        if (ConfigCatalog.meta(key).type() != ConfigType.BOOL) return valueX;
        int x = valueX - 5 - MenuChrome.TOGGLE_W;
        MenuChrome.toggle(font, x, y, Boolean.parseBoolean(draft.text(key)), true);
        return x;
    }

    /** Quiet dotted leader from a label to its value, so long rows read across. */
    private void leader(int from, int to, int y) {
        for (int x = from + Math.floorMod(-from, 3); x < to; x += 3)
            MenuChrome.fill(font, x, y, 1, 1, MenuChrome.EDGE, .9f);
    }

    /** Under a Player 1 / Player 2 group the player is already named, so rows show the button alone. */
    private String rowLabel(SonicConfiguration key) {
        String label = labels.get(key);
        if (!controlsTab()) return label;
        if (controlsTab() && controlGroup < 2) {
            String prefix = "Player " + (controlGroup + 1) + " ";
            if (label.startsWith(prefix)) return label.substring(prefix.length());
        }
        return label;
    }

    /**
     * Details lead with the short player description, then a label column for the value, its default,
     * when it applies and the stored form; the catalog's technical explanation follows below.
     */
    private MenuDetailsScreen.Content detailsContent(SonicConfiguration key) {
        String stored = draft.text(key);
        String shown = EngineSettingLabels.value(key, stored);
        Object defaultValue = config.getDefaultValue(key);
        List<MenuDetailsScreen.Row> rows = new ArrayList<>();
        rows.add(new MenuDetailsScreen.Row("Current", shown));
        rows.add(new MenuDetailsScreen.Row("Default", EngineSettingLabels.value(key,
                defaultValue == null ? "" : String.valueOf(defaultValue))));
        rows.add(new MenuDetailsScreen.Row("Applies", effect(key)));
        if (!shown.equals(stored) && !stored.isEmpty()) rows.add(new MenuDetailsScreen.Row("Saved as", stored));
        var meta = ConfigCatalog.meta(key);
        rows.add(new MenuDetailsScreen.Row("Config key", meta.section() + "." + meta.leaf()));
        String player = EngineSettingLabels.description(key);
        String technical = meta.description();
        List<MenuDetailsScreen.Section> sections = new ArrayList<>();
        sections.add(new MenuDetailsScreen.Section("", rows, false));
        if (!technical.equals(player))
            sections.add(new MenuDetailsScreen.Section("ABOUT", List.of(new MenuDetailsScreen.Row("", technical)), true));
        return new MenuDetailsScreen.Content(EngineSettingLabels.label(key), "", player,
                MenuDetailsScreen.Tone.INFO, sections, "", List.of(), "");
    }

    private String shownValue(SonicConfiguration key) {
        String value = valueLabels.get(key);
        return value == null || value.isEmpty() ? "(none)" : value;
    }

    private void renderDescription(int width) {
        List<String> lines = new ArrayList<>();
        List<float[]> colors = new ArrayList<>();
        if (resetFocused) {
            lines.add(controlsTab() ? "Restore all key bindings and controller settings." : "Restore this tab's settings to their defaults.");
            lines.add("You can review the changes before saving.");
        } else if (saveFocused) {
            lines.add("Save your changes and return to the menu.");
            colors.add(null);
        } else if (groupsFocused) {
            lines.add("Choose Player 1, Player 2, controllers or shortcuts.");
            lines.add("Reset tab restores all four groups together.");
        } else if (tabsFocused) {
            lines.add(tabSummary(tabs[category]));
            List<SonicConfiguration> pending = PendingRestart.current(config);
            colors.add(null);
            if (!pending.isEmpty()) {
                String named = "Restart OpenGGF to apply: " + String.join(", ",
                        pending.stream().map(EngineSettingLabels::label).toList()) + ".";
                lines.add(MenuChrome.compactWidth(named) <= width - 28 ? named : "Restart OpenGGF to apply "
                        + pending.size() + (pending.size() == 1 ? " saved change." : " saved changes."));
                colors.add(MenuChrome.AMBER);
            } else {
                lines.add(pausedGameTakes == null ? "These settings apply to every game."
                        : "Every game; your paused game takes some on resume.");
                colors.add(MenuChrome.DIM);
            }
        } else {
            String description = EngineSettingLabels.description(selected());
            if (!description.equals(descriptionText) || width != descriptionWidth) {
                descriptionText = description;
                descriptionWidth = width;
                descriptionLines = MenuDetailsScreen.wrapText(MenuDetailsScreen.readable(description), width - 28);
            }
            // A setting that needs a restart, or a paused game's status, takes the second line;
            // Details has the full description.
            List<SonicConfiguration> conflicts = draft.bindingConflicts(selected());
            Timing status = conflicts.isEmpty() ? status(selected()) : new Timing(
                    MenuChrome.fitCompact("Also bound: " + String.join(", ", conflicts.stream()
                            .map(EngineSettingLabels::label).toList()), width - 28), MenuChrome.AMBER);
            int rows = status == null ? 2 : 1;
            for (int i = 0; i < rows && i < descriptionLines.size(); i++) lines.add(descriptionLines.get(i));
            if (descriptionLines.size() > rows) {
                String last = lines.get(rows - 1);
                lines.set(rows - 1, MenuChrome.fitCompact(last + "...", width - 28));
            }
            if (status != null) {
                while (colors.size() < lines.size()) colors.add(null);
                lines.add(status.text());
                colors.add(status.color());
            }
        }
        MenuChrome.descriptionBox(font, 8, DESC_Y, width - 16, DESC_H, lines, colors);
        if (!tabsFocused && !saveFocused && !resetFocused && !groupsFocused) {
            ConfigType type = ConfigCatalog.meta(selected()).type();
            String verb = type == ConfigType.KEY ? "REBIND" : type == ConfigType.BOOL || type == ConfigType.ENUM ? "CHOOSE" : "EDIT";
            List<MenuChrome.Hint> hints = List.of(selected() == SonicConfiguration.AUDIO_HUB_MUSIC_VOLUME
                            ? MenuChrome.leftRight(lastInput, "VOLUME") : MenuChrome.hint(MenuChrome.confirmKey(lastInput), verb),
                    MenuChrome.hint(MenuChrome.detailsKey(lastInput), "DETAILS"));
            MenuChrome.hints(font, 14, DESC_Y + 22, hints, MenuChrome.controller(lastInput), MenuChrome.MUTED);
            Object defaultValue = config.getDefaultValue(selected());
            String def = "DEFAULT: " + MenuDetailsScreen.readable(EngineSettingLabels.value(selected(),
                    defaultValue == null ? "" : String.valueOf(defaultValue)));
            def = MenuChrome.fitCompact(def, Math.max(0, width - 36 - MenuChrome.hintsWidth(hints)));
            MenuChrome.compact(font, def, width - 14 - MenuChrome.compactWidth(def), DESC_Y + 24, MenuChrome.DIM);
        }
    }

    private static String tabSummary(EngineSettingsDraft.Category category) {
        return switch (category) {
            case DISPLAY -> "Aspect ratio, window, shaders and colour.";
            case AUDIO -> "Music, sound effects and the sound chip.";
            case INPUT -> "Keyboard, controllers and every shortcut key.";
            case GAMEPLAY -> "Multiplayer chat, rewind, debug tools and startup.";
            case FILES -> "Where your ROM images live.";
            case RECORDING -> "Video capture of your play sessions.";
            case LAUNCH -> "Per-game launch profiles.";
            case ADVANCED -> "Debug, playback, Discord and direct-launch options.";
        };
    }

    private static int promptButtonWidth(int i) {
        return MenuChrome.compactWidth(PROMPT_OPTIONS[i]) + 24;
    }

    private static int[] promptButtonXs(int w) {
        int total = 16;
        for (int i = 0; i < 3; i++) total += promptButtonWidth(i);
        int x = (w - total) / 2;
        int[] xs = new int[3];
        for (int i = 0; i < 3; i++) {
            xs[i] = x;
            x += promptButtonWidth(i) + 8;
        }
        return xs;
    }

    List<String> changeSummaryLines() {
        List<String> lines = new ArrayList<>();
        for (EngineSettingsDraft.Change change : draft.changes()) {
            lines.add(EngineSettingLabels.label(change.key()));
            String value = EngineSettingLabels.value(change.key(), change.before()) + " -> "
                    + EngineSettingLabels.value(change.key(), change.after());
            lines.addAll(MenuDetailsScreen.wrapText(MenuDetailsScreen.readable(value), Math.min(viewportWidth - 24, 300) - 20));
        }
        return lines;
    }

    private void renderResetPrompt(int w) {
        MenuChrome.fill(font, 0, 0, w, 224, new float[] {0, 0, 0}, .65f);
        // Same shape as the quit and save prompts: bold centred question, centred lines, sentence-case buttons.
        MenuChrome.fill(font, 12, 66, w - 24, 91, MenuChrome.NAVY, .97f);
        MenuChrome.outline(font, 12, 66, w - 24, 91, MenuChrome.GOLD, 1f, 1);
        String title = controlsTab() ? "RESET ALL BINDINGS?" : "RESET " + tabs[category].label().toUpperCase(java.util.Locale.ROOT) + " DEFAULTS?";
        title = MenuChrome.fitLabel(title, w - 40);
        MenuChrome.label(font, title, (w - MenuChrome.labelWidth(title)) / 2, 76, MenuChrome.GOLD);
        if (controlsTab()) centred("Includes controller setup and deadzone.", w, 94, MenuChrome.TEXT);
        centred("Changes stay in the draft until saved.", w, controlsTab() ? 106 : 100, MenuChrome.MUTED);
        for (int i = 0; i < 2; i++) {
            int x = w / 2 - 92 + i * 96;
            if (resetChoice == i) MenuChrome.focus(font, x, 130, 88, 17, ticks);
            else MenuChrome.panel(font, x, 130, 88, 17);
            String label = i == 0 ? (controlsTab() ? "Reset all" : "Reset tab") : "Cancel";
            MenuChrome.compact(font, label, x + (88 - MenuChrome.compactWidth(label)) / 2, 135,
                    i == 0 ? MenuChrome.AMBER : resetChoice == i ? MenuChrome.WHITE : MenuChrome.TEXT);
        }
    }

    private void centred(String text, int w, int y, float[] color) {
        String shown = MenuChrome.fitCompact(text, w - 40);
        MenuChrome.compact(font, shown, (w - MenuChrome.compactWidth(shown)) / 2, y, color);
    }

    private void renderPrompt(int w) {
        int[] xs = promptButtonXs(w);
        int[] widths = new int[3];
        for (int i = 0; i < 3; i++) widths[i] = promptButtonWidth(i);
        List<String> summaries = changeSummaryLines();
        MenuChrome.changePrompt(font, w, "SAVE SETTINGS?", summaries, promptScroll,
                summaries.size() > 8 ? MenuChrome.verticalKey(lastInput) + ": REVIEW " + draft.changedCount() + " CHANGES" : null,
                PROMPT_OPTIONS, xs, widths, promptChoice, ticks);
    }

    private void renderChoice(int width) {
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, width);
            MenuChrome.header(font, width, MenuChrome.fitLabel(EngineSettingLabels.label(selected()).toUpperCase(java.util.Locale.ROOT), width - 90),
                    "CHOOSE");
            List<String> values = options();
            String defaultText = String.valueOf(config.getDefaultValue(selected()));
            for (int i = 0; i <= values.size(); i++) {
                int y = 40 + i * 18;
                if (choice == i) MenuChrome.focus(font, 9, y - 3, width - 18, 15, ticks);
                String label = i == values.size() ? "Restore default" : EngineSettingLabels.value(selected(), values.get(i));
                boolean nonDefault = i < values.size() && !values.get(i).equals(defaultText);
                boolean current = i < values.size() && values.get(i).equals(draft.text(selected()));
                if (current) MenuChrome.fill(font, 14, y + 3, 3, 3, MenuChrome.CYAN);
                label(MenuChrome.fitLabel(label, width - 40), 22, y, nonDefault ? AMBER : WHITE);
            }
            MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(lastInput), "SET"),
                    MenuChrome.hint(MenuChrome.backKey(lastInput), "CANCEL")), MenuChrome.controller(lastInput));
        } finally {
            font.endMegaBatch();
        }
    }

    private void renderKeyMenu(int width) {
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, width);
            MenuChrome.header(font, width, MenuChrome.fitLabel(EngineSettingLabels.label(selected()).toUpperCase(java.util.Locale.ROOT), width - 90),
                    "KEYBOARD");
            label(EngineSettingLabels.value(selected(), draft.text(selected())), 12, 40, width - 24,
                    draft.nonDefault(selected()) ? AMBER : WHITE);
            if (capturing) {
                float a = .55f + .45f * MenuChrome.pulse(ticks * 2);
                MenuChrome.label(font, "Press a key or chord", 12, 76, CYAN, a);
                text("Modifiers: Ctrl, Shift, Alt, Super", 12, 92, width - 24, MUTED);
                text("Use key-name editing to bind Escape", 12, 118, width - 24, MUTED);
                text("Controller buttons keep navigating menus", 12, 130, width - 24, MUTED);
                MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.backKey(lastInput), "CANCEL")), MenuChrome.controller(lastInput));
                return;
            }
            String[] options = { "Capture a new key", "Type a key name or chord", "Unbind this shortcut", "Restore default" };
            for (int i = 0; i < options.length; i++) {
                int y = 64 + i * 22;
                if (choice == i) MenuChrome.focus(font, 9, y - 4, width - 18, 17, ticks);
                label(options[i], 17, y, width - 34, WHITE);
            }
            MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(lastInput), "CHOOSE"),
                    MenuChrome.hint(MenuChrome.detailsKey(lastInput), "DETAILS"),
                    MenuChrome.hint(MenuChrome.backKey(lastInput), "BACK")), MenuChrome.controller(lastInput));
        } finally {
            font.endMegaBatch();
        }
    }

    /** Fit compact text to a pixel width; a focused row scrolls its overflow by whole characters. */
    private String window(String value, int room, boolean scroll) {
        return scroll ? MenuStyle.marquee(value, room, MenuStyle.COMPACT, focusTicks / 8)
                : MenuChrome.fitCompact(value, room);
    }

    private void label(String value, int x, int y, int space, float[] color) {
        MenuStyle.label(font, value, x, y, space, color[0], color[1], color[2]);
    }

    private void label(String value, int x, int y, float[] color) {
        MenuChrome.label(font, value, x, y, color);
    }

    private void text(String value, int x, int y, int space, float[] color) {
        MenuStyle.text(font, value, x, y, space, color[0], color[1], color[2]);
    }
}
