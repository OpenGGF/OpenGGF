package com.openggf.game;

import com.openggf.i18n.UiMessages;
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
    private static String[] promptOptions() { return new String[] {UiMessages.text("settings.screen.save"), UiMessages.text("settings.screen.discard"), UiMessages.text("settings.screen.keep_editing")}; }

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
    private static String[] controlGroupLabels() { return new String[] {UiMessages.text("settings.screen.player_1_caps"), UiMessages.text("settings.screen.player_2_caps"), UiMessages.text("settings.screen.controllers_caps"), UiMessages.text("settings.screen.shortcuts_caps")}; }
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
    private long languageRevision = -1;
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
    private LanguagePickerScreen languagePicker;

    public void cancelDisplayTrial() {
        com.openggf.configuration.DisplaySettingsTrial.forConfig(config).cancel();
        displayPrompt = false;
    }

    public void update(InputHandler input) {
        if (input == null || closeRequested) return;
        refreshPresentation();
        ticks++;
        focusTicks++;
        if (messageTicks > 0 && --messageTicks == 0) message = null;
        lastInput = input;
        if (languagePicker != null) {
            if (languagePicker.update(input)) languagePicker = null;
            return;
        }
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
        if (selected() == SonicConfiguration.UI_LANGUAGE) {
            languagePicker = new LanguagePickerScreen(draft.text(selected()),
                    tag -> draft.set(SonicConfiguration.UI_LANGUAGE, tag));
            return;
        }
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
        if (draft.dirty() && pointer.over(viewportWidth - saveButtonWidth() - 8, 8, saveButtonWidth(), saveButtonHeight())) {
            if (moved || pointer.leftPressed()) { saveFocused = true; tabsFocused = false; resetFocused = false; groupsFocused = false; }
            if (pointer.leftPressed() && apply()) closeRequested = true;
            return pointer.leftPressed();
        }
        if (pointer.over(viewportWidth - resetButtonWidth() - 8, resetButtonY(), resetButtonWidth(), resetButtonHeight())) {
            if (moved || pointer.leftPressed()) { resetFocused = true; tabsFocused = false; saveFocused = false; groupsFocused = false; }
            if (pointer.leftPressed()) openResetPrompt();
            return pointer.leftPressed();
        }
        int[] xs = tabXs(viewportWidth);
        for (int i = 0; i < tabs.length && tabs.length > 1; i++) {
            if (!pointer.over(xs[i], TAB_Y, tabWidth(i), tabHeight())) continue;
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
                if (pointer.over(x, baseRowTop() - 2, (viewportWidth - 16) / 4, groupHeight()) && pointer.leftPressed()) {
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
    static String restartStatus() { return UiMessages.text("settings.screen.restart_needed_caps"); }
    private static String restartTag() { return UiMessages.text("settings.screen.restart_caps"); }

    record Timing(String text, float[] color) { }

    /**
     * The status line for a row, or null to keep both description lines: a restart wins, then a
     * paused game's view of the change. When the rest apply is in Details ({@link #effect}).
     */
    Timing status(SonicConfiguration key) {
        if (restartPending(key)) {
            return new Timing(UiMessages.text("settings.screen.needs_a_restart_close_and_reopen_openggf"), MenuChrome.AMBER);
        }
        if (key == SonicConfiguration.AUDIO_HUB_MUSIC_ENABLED || key == SonicConfiguration.AUDIO_HUB_MUSIC_VOLUME)
            return new Timing(UiMessages.text("settings.screen.applies_to_the_hub_as_soon_as_you_save"), MenuChrome.CYAN);
        if (!inEffect(key)) return new Timing(EngineSettingLabels.notInEffect(key), MenuChrome.MUTED);
        if (pausedGameTakes == null) return null;
        return pausedGameTakes.test(key) ? new Timing(UiMessages.text("settings.screen.your_paused_game_takes_this_when_it_resumes"), MenuChrome.CYAN)
                : new Timing(UiMessages.text("settings.screen.applies_when_you_next_start_a_game"), MenuChrome.MUTED);
    }

    boolean restartPending(SonicConfiguration key) {
        return PendingRestart.awaiting(key, draft.value(key), draft.changed(key));
    }

    private static int restartTagWidth() {
        return MenuChrome.compactWidth(restartTag()) + 6;
    }

    /** A small amber tag on rows with changes awaiting a restart. */
    private void restartTag(int x, int y) {
        int w = restartTagWidth();
        MenuChrome.fill(font, x, y, w, 10, MenuChrome.NAVY, .95f);
        MenuChrome.outline(font, x, y, w, 10, MenuChrome.AMBER, 1f, 1);
        MenuChrome.compact(font, restartTag(), x + 3, y + 1, MenuChrome.AMBER);
    }

    private void flash(String text) {
        message = text;
        messageTicks = 180;
    }

    private int visibleRows() { return Math.max(1, Math.min(VISIBLE_ROWS, (resetButtonY() - (rowTop() - 1)) / rowHeight())); }
    private int tabHeight() { return Math.max(14, MenuChrome.compactLineHeight() + 5); }
    private int baseRowTop() { return Math.max(ROW_Y, TAB_Y + tabHeight() + 7); }
    private int groupHeight() { return Math.max(12, MenuChrome.compactLineHeight() + 3); }
    private int rowTop() { return controlsTab() ? baseRowTop() + groupHeight() + 1 : baseRowTop(); }
    private int rowHeight() {
        int authored = controlsTab() ? 11 : 12;
        int text = MenuChrome.compactLineHeight();
        return Math.max(authored, text + 2);
    }
    private static int controlGroup(SonicConfiguration key) {
        String section = ConfigCatalog.meta(key).section();
        return section.equals("input.player1") ? 0 : section.equals("input.player2") ? 1
                : section.equals("input.controller") ? 2 : 3;
    }
    private void selectControlGroup(int group) { controlGroup = group; row = 0; scroll = 0; focusTicks = 0; }
    private boolean controlsTab() { return tabs[category] == EngineSettingsDraft.Category.INPUT; }
    private int saveButtonWidth() {
        return Math.min(viewportWidth - 32, Math.max(104, MenuChrome.compactWidth(UiMessages.text("settings.screen.save_changes_caps")) + 14));
    }
    private int saveButtonHeight() { return Math.max(17, MenuChrome.compactLineHeight() + 4); }

    private int resetButtonWidth() {
        return Math.min(viewportWidth - 32, Math.max(controlsTab() ? 100 : 74, MenuChrome.compactWidth(resetButtonText()) + 14));
    }
    private int resetButtonHeight() { return Math.max(12, MenuChrome.compactLineHeight() + 3); }
    private int resetButtonY() { return DESC_Y - resetButtonHeight() - 3; }
    private String resetButtonText() { return controlsTab() ? UiMessages.text("settings.screen.reset_bindings_caps") : UiMessages.text("settings.screen.reset_tab_caps"); }
    private void openResetPrompt() { resetPrompt = true; resetChoice = 1; MenuFeedback.emit(CONFIRM); }
    private void updateResetPrompt(InputHandler input, MenuInput.Pointer pointer) {
        if (MenuInput.back(input) || pointer.rightPressed()) { resetPrompt = false; MenuFeedback.emit(CANCEL); return; }
        if (MenuInput.left(input) || MenuInput.right(input) || MenuInput.up(input) || MenuInput.down(input)) {
            resetChoice = 1 - resetChoice; MenuFeedback.emit(NAVIGATE);
        }
        boolean accept = MenuInput.accept(input);
        for (int i = 0; i < 2; i++) {
            if (pointer.over(resetPromptX(i, viewportWidth), resetPromptY(i, viewportWidth), resetPromptWidth(i, viewportWidth), resetPromptHeight()) && pointer.leftPressed()) {
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
        if (MenuInput.down(input)) promptScroll = Math.min(Math.max(0, changeSummaryLines().size() - summaryCapacity()), promptScroll + 1);
        boolean choose = MenuInput.accept(input);
        int[] xs = promptButtonXs(viewportWidth);
        boolean moved = pointer.x() != lastPointerX || pointer.y() != lastPointerY;
        lastPointerX = pointer.x();
        lastPointerY = pointer.y();
        for (int i = 0; i < 3; i++) {
            if (!pointer.over(xs[i], promptButtonY(i, viewportWidth), promptButtonWidth(i, viewportWidth), promptButtonHeight(viewportWidth))) continue;
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
            flash(UiMessages.text("settings.screen.display_restored_changes_not_saved"));
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
            flash(UiMessages.text("settings.screen.display_restored_changes_not_saved"));
            MenuFeedback.emit(CANCEL);
        }
    }

    private void renderDisplayPrompt(int width) {
        MenuChrome.background(font, width);
        MenuChrome.header(font, width, UiMessages.text("settings.screen.keep_display_settings_caps"), null);
        var trial = com.openggf.configuration.DisplaySettingsTrial.forConfig(config);
        // The countdown reads as a warning with a draining meter, the shared progress treatment.
        MenuChrome.descriptionBox(font, 20, 52, width - 40, 50, List.of(), List.of());
        MenuChrome.icon(font, MenuChrome.Icon.WARNING, 28, 60, MenuChrome.AMBER);
        MenuChrome.compact(font, UiMessages.text("settings.display.revert_countdown", java.util.Map.of("seconds", trial.secondsRemaining())), 40, 60, MenuChrome.AMBER);
        MenuChrome.fill(font, 28, 74, width - 56, 2, MenuChrome.EDGE);
        MenuChrome.fill(font, 28, 74, Math.round((width - 56) * trial.remainingFraction()), 2, MenuChrome.AMBER);
        MenuChrome.compact(font, UiMessages.text("settings.screen.keep_saves_all_your_pending_changes"), 28, 84, MenuChrome.MUTED);
        String[] labels = {UiMessages.text("settings.screen.keep_changes"), UiMessages.text("settings.screen.revert_display")};
        MenuChrome.Icon[] icons = {MenuChrome.Icon.CHECK, MenuChrome.Icon.WARNING};
        for (int i = 0; i < 2; i++) {
            int y = 120 + i * 28;
            boolean focused = keepDisplay == (i == 0);
            if (focused) MenuChrome.focus(font, 30, y, width - 60, 22, ticks);
            else MenuChrome.panel(font, 30, y, width - 60, 22);
            MenuChrome.icon(font, icons[i], 40, y + 8, i == 0 ? MenuChrome.GREEN : MenuChrome.CYAN);
            MenuChrome.compact(font, labels[i], 52, y + 7, focused ? MenuChrome.WHITE : MenuChrome.TEXT);
        }
        MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("settings.screen.choose_caps")),
                MenuChrome.hint(MenuChrome.backKey(lastInput), UiMessages.text("settings.screen.revert_caps"))), MenuChrome.controller(lastInput));
    }

    private boolean saveDraft() {
        try {
            boolean changed = draft.dirty();
            UiLanguageSettings.apply(draft);
            MenuFeedback.emit(CONFIRM);
            applied |= changed;
            return true;
        } catch (IOException e) {
            flash(UiMessages.text("settings.screen.save_failed_try_again"));
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
                    DEBUG_MODE_KEY, LIVE_REWIND_KEY, FRAME_STEP_KEY -> UiMessages.text("settings.screen.takes_effect_immediately_after_saving");
            case DISPLAY_COLOR_EXPANSION -> UiMessages.text("settings.screen.takes_effect_immediately_after_saving_including_cached_palette_colours");
            // MasterTitleScreen refreshes ROM previews on consumeApplied; load resolves saved ROM paths.
            case SONIC_1_ROM, SONIC_2_ROM, SONIC_3K_ROM, ROMS_DIRECTORY, ROMS_PREFER_COMPOSITE ->
                    UiMessages.text("settings.screen.after_saving_the_rom_catalogue_rescans_and_previews_refresh_used_for_the_next_game_lo");
            case TRACE_CATALOG_DIR -> UiMessages.text("settings.screen.after_saving_used_when_the_playback_browser_next_opens");
            // LiveCaptureRecorderFactory reads these when creating a recording.
            case CAPTURE_CODEC, CAPTURE_AUDIO_CODEC, CAPTURE_ENCODER_THREADS, CAPTURE_ENCODER_PRESET,
                    CAPTURE_FFMPEG_PASS1_ARGS, CAPTURE_FFMPEG_PASS2_ARGS, CAPTURE_QUEUE_BUDGET_MB,
                    CAPTURE_OUTPUT_DIR, CAPTURE_CONTAINER -> UiMessages.text("settings.screen.after_saving_used_for_the_next_recording");
            // LaunchProfileApplier only overrides these for games whose setup is not Default.
            // One authored sentence per timing class; a paused game's share follows LiveSettings.
            case LIVE_REWIND_ENABLED, DEBUG_VIEW_ENABLED ->
                    UiMessages.text("settings.screen.for_every_game_whose_game_setup_uses_the_global_value_when_you_next_start_or_resume_a_game");
            case DISPLAY_ASPECT ->
                    UiMessages.text("settings.screen.for_every_game_whose_game_setup_uses_the_global_value_when_you_next_start_a_game_or_resume_one_paused_in_a_level");
            default -> switch (SettingTiming.of(key)) {
                case NOW -> UiMessages.text("settings.screen.takes_effect_as_soon_as_you_save");
                case NEXT_GAME -> com.openggf.game.launch.LiveSettings.appliesOnResume(key, false)
                        ? UiMessages.text("settings.screen.takes_effect_when_you_next_start_a_game_or_resume_a_paused_one")
                        : UiMessages.text("settings.screen.takes_effect_when_you_next_start_a_game");
                case RESTART -> UiMessages.text("settings.screen.takes_effect_after_you_close_and_reopen_openggf");
            };
        };
    }

    private void refreshPresentation() {
        long currentLanguage = UiMessages.revision();
        if (languageRevision != currentLanguage) {
            labels.clear(); valueLabels.clear(); optionCache.clear();
            descriptionText = null; descriptionLines = List.of();
            languageRevision = currentLanguage;
            presentationRevision = -1;
            cachedLinesTab = null;
            scroll = Math.clamp(scroll, 0, Math.max(0, lines().size() - visibleRows()));
        }
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
        int available = viewportWidth - 16 - 3 * (tabs.length - 1);
        int natural = 0;
        for (var tab : tabs) natural += MenuChrome.compactWidth(categoryLabel(tab)) + 8;
        int requested = MenuChrome.compactWidth(categoryLabel(tabs[i])) + 8;
        if (natural <= available) return requested;
        // Preserve the common baseline width, then share the remaining space by actual text width.
        int minimum = Math.min(20, available / tabs.length);
        int flexible = available - minimum * tabs.length;
        return minimum + flexible * Math.max(0, requested - minimum) / Math.max(1, natural - minimum * tabs.length);
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
        if (languagePicker != null) { languagePicker.render(font, width); return; }
        if (details != null) { details.render(font, width); return; }
        refreshPresentation();
        if (textEditor != null) { textEditor.render(font, width); return; }
        if (choosing) { renderChoice(width); return; }
        if (keyMenu || capturing) { renderKeyMenu(width); return; }
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, width);
            String heading = scope == Scope.ADVANCED ? UiMessages.text("settings.screen.advanced_settings_caps") : UiMessages.text("settings.screen.settings_caps");
            MenuChrome.header(font, width, draft.dirty() ? MenuChrome.fitLabel(heading, width - saveButtonWidth() - 26) : heading,
                    draft.dirty() ? "" : UiMessages.text("settings.screen.all_games_caps"));
            if (draft.dirty()) {
                int saveX = width - saveButtonWidth() - 8;
                if (saveFocused && !prompt) MenuChrome.focus(font, saveX, 8, saveButtonWidth(), saveButtonHeight(), focusTicks);
                else MenuChrome.panel(font, saveX, 8, saveButtonWidth(), saveButtonHeight());
                MenuChrome.compact(font, MenuChrome.fitCompact(UiMessages.text("settings.screen.save_changes_caps"), saveButtonWidth() - 14),
                        saveX + 7, 10 + MenuChrome.compactMetrics().above(), MenuChrome.GOLD);
            }
            int contentTop = renderTabs(width);
            renderRows(width, rowTop());
            if (tabs[category] == EngineSettingsDraft.Category.INPUT) renderControlGroups(width);
            int resetX = width - resetButtonWidth() - 8;
            int resetY = resetButtonY();
            if (resetFocused) MenuChrome.focus(font, resetX, resetY, resetButtonWidth(), resetButtonHeight(), focusTicks);
            else MenuChrome.panel(font, resetX, resetY, resetButtonWidth(), resetButtonHeight());
            int resetTextY = resetY + 2 + MenuChrome.compactMetrics().above();
            // The legend explains the amber marker only when this tab shows one.
            if (Boolean.TRUE.equals(categoryChanged.get(tabs[category]))) {
                MenuChrome.changedMarker(font, 11, resetY + resetButtonHeight() / 2);
                MenuChrome.compact(font, UiMessages.text("settings.screen.changed_from_default_caps"), 18, resetTextY, MenuChrome.DIM);
            }
            MenuChrome.compact(font, MenuChrome.fitCompact(resetButtonText(), resetButtonWidth() - 14), resetX + 7, resetTextY, MenuChrome.AMBER);
            renderDescription(width);
            int unsaved = draft.changedCount();
            String status = message != null ? message : unsaved > 0 ? UiMessages.text("settings.unsaved_count", java.util.Map.of("count", unsaved))
                    : PendingRestart.current(config).isEmpty() ? null : restartStatus();
            List<MenuChrome.Hint> hints = new ArrayList<>();
            if (saveFocused) hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("settings.screen.save_caps")));
            else if (resetFocused) hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("settings.screen.reset_caps")));
            else hints.add(MenuChrome.leftRight(lastInput, tabsFocused ? UiMessages.text("settings.screen.tab_caps") : groupsFocused ? UiMessages.text("settings.screen.group_caps") : UiMessages.text("settings.screen.change_caps")));
            if (tabsFocused) hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("settings.screen.settings_caps")));
            hints.add(MenuChrome.hint(MenuChrome.backKey(lastInput), draft.dirty() ? UiMessages.text("settings.screen.save_or_discard_caps") : UiMessages.text("settings.screen.back_caps")));
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
            if (i == controlGroup && groupsFocused) MenuChrome.focus(font, x, baseRowTop() - 2, w, groupHeight(), focusTicks);
            else MenuChrome.panel(font, x, baseRowTop() - 2, w, groupHeight());
            MenuChrome.compact(font, MenuChrome.fitCompact(controlGroupLabels()[i], w - 6), x + 3, baseRowTop() + 1,
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
            if (selected && tabsFocused) MenuChrome.focus(font, xs[i], TAB_Y, tw, tabHeight(), focusTicks);
            if (selected) {
                MenuChrome.fill(font, xs[i] + (tabsFocused ? 2 : 0), TAB_Y + (tabsFocused ? 2 : 0),
                        tw - (tabsFocused ? 4 : 0), tabHeight() - (tabsFocused ? 4 : 0), MenuChrome.GOLD);
            } else {
                MenuChrome.panel(font, xs[i], TAB_Y, tw, tabHeight());
            }
            String label = MenuChrome.fitCompact(categoryLabel(tabs[i]), tw - 8);
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
                MenuChrome.section(font, EngineSettingLabels.groupCaption(line.caption()), 12, y + 1, width - 34);
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
            if (ConfigCatalog.meta(key).type() == com.openggf.configuration.ConfigType.KEY && !draft.text(key).isEmpty()
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
                MenuChrome.compact(font, v, valueX, y + 1, draft.text(key).isEmpty() ? MenuChrome.DIM : valueColor);
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
        if (controlsTab() && controlGroup < 2) {
            String button = switch (key) {
                case UP, P2_UP -> "up";
                case DOWN, P2_DOWN -> "down";
                case LEFT, P2_LEFT -> "left";
                case RIGHT, P2_RIGHT -> "right";
                case P1_A, P2_A -> "a";
                case P1_B, P2_B -> "b";
                case P1_C, P2_C -> "c";
                case START, P2_START -> "start";
                default -> null;
            };
            if (button != null) return UiMessages.text("settings.button." + button);
        }
        return labels.get(key);
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
        rows.add(new MenuDetailsScreen.Row(UiMessages.text("settings.screen.current"), shown));
        rows.add(new MenuDetailsScreen.Row(UiMessages.text("settings.screen.default"), EngineSettingLabels.value(key,
                defaultValue == null ? "" : String.valueOf(defaultValue))));
        rows.add(new MenuDetailsScreen.Row(UiMessages.text("settings.screen.applies"), effect(key)));
        if (!shown.equals(stored) && !stored.isEmpty()) rows.add(new MenuDetailsScreen.Row(UiMessages.text("settings.screen.saved_as"), stored));
        var meta = ConfigCatalog.meta(key);
        rows.add(new MenuDetailsScreen.Row(UiMessages.text("settings.screen.config_key"), meta.section() + "." + meta.leaf()));
        String player = EngineSettingLabels.description(key);
        String technical = meta.description();
        List<MenuDetailsScreen.Section> sections = new ArrayList<>();
        sections.add(new MenuDetailsScreen.Section("", rows, false));
        if (!technical.equals(player))
            sections.add(new MenuDetailsScreen.Section(UiMessages.text("settings.screen.about"), List.of(new MenuDetailsScreen.Row("", technical)), true));
        return new MenuDetailsScreen.Content(EngineSettingLabels.label(key), "", player,
                MenuDetailsScreen.Tone.INFO, sections, "", List.of(), "");
    }

    private String shownValue(SonicConfiguration key) {
        String value = valueLabels.get(key);
        return value == null || value.isEmpty() ? UiMessages.text("settings.screen.none") : value;
    }

    private void renderDescription(int width) {
        List<String> lines = new ArrayList<>();
        List<float[]> colors = new ArrayList<>();
        if (resetFocused) {
            lines.add(controlsTab() ? UiMessages.text("settings.screen.restore_all_key_bindings_and_controller_settings") : UiMessages.text("settings.screen.restore_this_tab_s_settings_to_their_defaults"));
            lines.add(UiMessages.text("settings.screen.you_can_review_the_changes_before_saving"));
        } else if (saveFocused) {
            lines.add(UiMessages.text("settings.screen.save_your_changes_and_return_to_the_menu"));
            colors.add(null);
        } else if (groupsFocused) {
            lines.add(UiMessages.text("settings.screen.choose_player_1_player_2_controllers_or_shortcuts"));
            lines.add(UiMessages.text("settings.screen.reset_tab_restores_all_four_groups_together"));
        } else if (tabsFocused) {
            lines.add(tabSummary(tabs[category]));
            List<SonicConfiguration> pending = PendingRestart.current(config);
            colors.add(null);
            if (!pending.isEmpty()) {
                String named = UiMessages.text("settings.restart_named", java.util.Map.of("settings", String.join(", ", pending.stream().map(EngineSettingLabels::label).toList())));
                lines.add(MenuChrome.compactWidth(named) <= width - 28 ? named : UiMessages.text("settings.restart_count", java.util.Map.of("count", pending.size())));
                colors.add(MenuChrome.AMBER);
            } else {
                lines.add(pausedGameTakes == null ? UiMessages.text("settings.screen.these_settings_apply_to_every_game")
                        : UiMessages.text("settings.screen.every_game_your_paused_game_takes_some_on_resume"));
                colors.add(MenuChrome.DIM);
            }
        } else {
            String description = EngineSettingLabels.rowDescription(selected());
            if (!description.equals(descriptionText) || width != descriptionWidth) {
                descriptionText = description;
                descriptionWidth = width;
                descriptionLines = MenuDetailsScreen.wrapText(MenuDetailsScreen.readable(description), width - 28);
            }
            // A setting that needs a restart, or a paused game's status, takes the second line;
            // Details has the full description.
            List<SonicConfiguration> conflicts = draft.bindingConflicts(selected());
            Timing status = conflicts.isEmpty() ? status(selected()) : new Timing(
                    MenuChrome.fitCompact(UiMessages.text("settings.also_bound", java.util.Map.of("settings", String.join(", ", conflicts.stream().map(EngineSettingLabels::label).toList()))), width - 28), MenuChrome.AMBER);
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
            String verb = type == ConfigType.KEY ? UiMessages.text("settings.screen.rebind_caps") : type == ConfigType.BOOL || type == ConfigType.ENUM ? UiMessages.text("settings.screen.choose_caps") : UiMessages.text("settings.screen.edit_caps");
            List<MenuChrome.Hint> hints = List.of(selected() == SonicConfiguration.AUDIO_HUB_MUSIC_VOLUME
                            ? MenuChrome.leftRight(lastInput, UiMessages.text("settings.screen.volume_caps")) : MenuChrome.hint(MenuChrome.confirmKey(lastInput), verb),
                    MenuChrome.hint(MenuChrome.detailsKey(lastInput), UiMessages.text("settings.screen.details_caps")));
            MenuChrome.hints(font, 14, DESC_Y + 22, hints, MenuChrome.controller(lastInput), MenuChrome.MUTED);
            Object defaultValue = config.getDefaultValue(selected());
            String def = UiMessages.text("settings.default_value", java.util.Map.of("value", MenuDetailsScreen.readable(EngineSettingLabels.value(selected(), defaultValue == null ? "" : String.valueOf(defaultValue)))));
            def = MenuChrome.fitCompact(def, Math.max(0, width - 36 - MenuChrome.hintsWidth(hints)));
            MenuChrome.compact(font, def, width - 14 - MenuChrome.compactWidth(def), DESC_Y + 24, MenuChrome.DIM);
        }
    }

    private static String categoryLabel(EngineSettingsDraft.Category category) {
        return UiMessages.text("settings.category." + category.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String tabSummary(EngineSettingsDraft.Category category) {
        return switch (category) {
            case DISPLAY -> UiMessages.text("settings.screen.aspect_ratio_window_shaders_and_colour");
            case AUDIO -> UiMessages.text("settings.screen.music_sound_effects_and_the_sound_chip");
            case INPUT -> UiMessages.text("settings.screen.keyboard_controllers_and_every_shortcut_key");
            case GAMEPLAY -> UiMessages.text("settings.screen.multiplayer_chat_rewind_debug_tools_and_startup");
            case FILES -> UiMessages.text("settings.screen.where_your_rom_images_live");
            case RECORDING -> UiMessages.text("settings.screen.video_capture_of_your_play_sessions");
            case LAUNCH -> UiMessages.text("settings.screen.per_game_launch_profiles");
            case ADVANCED -> UiMessages.text("settings.screen.debug_playback_discord_and_direct_launch_options");
        };
    }

    private static boolean stackedPrompt(int width) {
        int total = 16;
        for (String option : promptOptions()) total += MenuChrome.compactWidth(option) + 24;
        return total > width - 40;
    }

    private static int promptButtonWidth(int i, int width) {
        return stackedPrompt(width) ? width - 48 : MenuChrome.compactWidth(promptOptions()[i]) + 24;
    }

    private static int promptButtonHeight(int width) {
        return Math.max(16, MenuChrome.compactLineHeight() + 6);
    }

    private static int promptButtonY(int i, int width) {
        int height = promptButtonHeight(width);
        return stackedPrompt(width) ? 196 - 3 * height - 8 + i * (height + 4) : 176;
    }

    private static int[] promptButtonXs(int width) {
        if (stackedPrompt(width)) return new int[] {24, 24, 24};
        int total = 16;
        for (int i = 0; i < 3; i++) total += promptButtonWidth(i, width);
        int x = (width - total) / 2;
        int[] xs = new int[3];
        for (int i = 0; i < 3; i++) {
            xs[i] = x;
            x += promptButtonWidth(i, width) + 8;
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

    private String resetPromptLabel(int i) {
        return i == 0 ? (controlsTab() ? UiMessages.text("settings.screen.reset_all") : UiMessages.text("settings.screen.reset_tab")) : UiMessages.text("settings.screen.cancel");
    }

    private boolean stackedResetPrompt(int width) {
        return resetPromptNaturalWidth(0) + resetPromptNaturalWidth(1) + 8 > width - 48;
    }

    private int resetPromptNaturalWidth(int i) { return Math.max(88, MenuChrome.compactWidth(resetPromptLabel(i)) + 14); }
    private int resetPromptHeight() { return Math.max(17, MenuChrome.compactLineHeight() + 4); }
    private int resetPromptWidth(int i, int width) { return stackedResetPrompt(width) ? width - 48 : resetPromptNaturalWidth(i); }
    private int resetPromptX(int i, int width) {
        if (stackedResetPrompt(width)) return 24;
        return (width - resetPromptNaturalWidth(0) - resetPromptNaturalWidth(1) - 8) / 2
                + (i == 0 ? 0 : resetPromptNaturalWidth(0) + 8);
    }
    private int resetPromptY(int i, int width) {
        return 130 + (stackedResetPrompt(width) ? i * (resetPromptHeight() + 4) : 0);
    }

    private void renderResetPrompt(int w) {
        MenuChrome.fill(font, 0, 0, w, 224, new float[] {0, 0, 0}, .65f);
        // Same shape as the quit and save prompts: bold centred question, centred lines, sentence-case buttons.
        MenuChrome.fill(font, 12, 66, w - 24, resetPromptY(1, w) + resetPromptHeight() + 10 - 66, MenuChrome.NAVY, .97f);
        MenuChrome.outline(font, 12, 66, w - 24, resetPromptY(1, w) + resetPromptHeight() + 10 - 66, MenuChrome.GOLD, 1f, 1);
        String title = controlsTab() ? UiMessages.text("settings.screen.reset_all_bindings_caps") : UiMessages.text("settings.reset_category", java.util.Map.of("category", categoryLabel(tabs[category])));
        title = MenuChrome.fitLabel(title, w - 40);
        MenuChrome.label(font, title, (w - MenuChrome.labelWidth(title)) / 2, 76, MenuChrome.GOLD);
        if (controlsTab()) centred(UiMessages.text("settings.screen.includes_controller_setup_and_deadzone"), w, 94, MenuChrome.TEXT);
        centred(UiMessages.text("settings.screen.changes_stay_in_the_draft_until_saved"), w, controlsTab() ? 106 : 100, MenuChrome.MUTED);
        for (int i = 0; i < 2; i++) {
            int x = resetPromptX(i, w), y = resetPromptY(i, w), buttonWidth = resetPromptWidth(i, w);
            if (resetChoice == i) MenuChrome.focus(font, x, y, buttonWidth, resetPromptHeight(), ticks);
            else MenuChrome.panel(font, x, y, buttonWidth, resetPromptHeight());
            String label = MenuChrome.fitCompact(resetPromptLabel(i), buttonWidth - 14);
            MenuChrome.compact(font, label, x + (buttonWidth - MenuChrome.compactWidth(label)) / 2, y + 2 + MenuChrome.compactMetrics().above(),
                    i == 0 ? MenuChrome.AMBER : resetChoice == i ? MenuChrome.WHITE : MenuChrome.TEXT);
        }
    }

    private void centred(String text, int w, int y, float[] color) {
        String shown = MenuChrome.fitCompact(text, w - 40);
        MenuChrome.compact(font, shown, (w - MenuChrome.compactWidth(shown)) / 2, y, color);
    }

    private static int summaryPitch() { return Math.max(10, MenuChrome.compactLineHeight() + 2); }
    private int summaryCapacity() { return nativePrompt(viewportWidth) ? 8 : Math.max(1, (promptButtonY(0, viewportWidth) - 20 - 77) / summaryPitch()); }
    private static boolean nativePrompt(int width) { return MenuChrome.compactLineHeight() <= 9 && !stackedPrompt(width); }

    private void renderPrompt(int w) {
        if (nativePrompt(w)) {
            int[] widths = new int[3];
            for (int i = 0; i < 3; i++) widths[i] = promptButtonWidth(i, w);
            List<String> summaries = changeSummaryLines();
            MenuChrome.changePrompt(font, w, UiMessages.text("settings.screen.save_settings_caps"), summaries, promptScroll,
                    summaries.size() > 8 ? UiMessages.text("settings.review_changes", java.util.Map.of(
                            "key", MenuChrome.verticalKey(lastInput), "count", draft.changedCount())) : null,
                    promptOptions(), promptButtonXs(w), widths, promptChoice, ticks);
            return;
        }
        MenuChrome.fill(font, 0, 0, w, 224, new float[] {0, 0, 0}, .62f);
        int bw = Math.min(w - 24, 300);
        int bx = (w - bw) / 2;
        MenuChrome.fill(font, bx, 45, bw, 153, MenuChrome.NAVY, .98f);
        MenuChrome.outline(font, bx, 45, bw, 153, MenuChrome.GOLD, 1f, 1);
        String title = UiMessages.text("settings.screen.save_settings_caps");
        title = MenuChrome.fitLabel(title, bw - 20);
        MenuChrome.label(font, title, (w - MenuChrome.labelWidth(title)) / 2, 55, MenuChrome.GOLD);
        List<String> summaries = changeSummaryLines();
        for (int i = 0; i < summaryCapacity() && promptScroll + i < summaries.size(); i++)
            MenuChrome.compact(font, MenuChrome.fitCompact(summaries.get(promptScroll + i), bw - 20), bx + 10, 77 + i * summaryPitch(),
                    i % 2 == 0 ? MenuChrome.TEXT : MenuChrome.CYAN);
        if (summaries.size() > summaryCapacity()) MenuChrome.compact(font, MenuChrome.fitCompact(UiMessages.text("settings.review_changes", java.util.Map.of("key", MenuChrome.verticalKey(lastInput), "count", draft.changedCount())), bw - 20), bx + 10, promptButtonY(0, w) - 12, MenuChrome.DIM);
        int[] xs = promptButtonXs(w);
        for (int i = 0; i < 3; i++) {
            int bwidth = promptButtonWidth(i, w), y = promptButtonY(i, w), height = promptButtonHeight(w);
            if (i == promptChoice) MenuChrome.focus(font, xs[i], y, bwidth, height, ticks);
            else MenuChrome.panel(font, xs[i], y, bwidth, height);
            String action = MenuChrome.fitCompact(promptOptions()[i], bwidth - 14);
            MenuChrome.compact(font, action, xs[i] + (bwidth - MenuChrome.compactWidth(action)) / 2,
                    y + (height - MenuChrome.compactLineHeight()) / 2 + MenuChrome.compactMetrics().above(),
                    i == 1 ? MenuChrome.AMBER : MenuChrome.WHITE);
        }
    }

    private void renderChoice(int width) {
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, width);
            MenuChrome.header(font, width, MenuChrome.fitLabel(EngineSettingLabels.label(selected()), width - 90),
                    UiMessages.text("settings.screen.choose_caps"));
            List<String> values = options();
            String defaultText = String.valueOf(config.getDefaultValue(selected()));
            int pitch = Math.max(18, MenuChrome.labelLineHeight() + 4);
            int visible = Math.max(1, (202 - 40) / pitch);
            int first = Math.clamp(choice - visible / 2, 0, Math.max(0, values.size() + 1 - visible));
            for (int i = first; i <= values.size() && i < first + visible; i++) {
                int y = 40 + (i - first) * pitch;
                if (choice == i) MenuChrome.focus(font, 9, y - 3, width - 18, pitch - 2, ticks);
                String label = i == values.size() ? UiMessages.text("settings.screen.restore_default") : EngineSettingLabels.value(selected(), values.get(i));
                boolean nonDefault = i < values.size() && !values.get(i).equals(defaultText);
                boolean current = i < values.size() && values.get(i).equals(draft.text(selected()));
                if (current) MenuChrome.fill(font, 14, y + 3, 3, 3, MenuChrome.CYAN);
                label(MenuChrome.fitLabel(label, width - 40), 22, y, nonDefault ? AMBER : WHITE);
            }
            MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("settings.screen.set_caps")),
                    MenuChrome.hint(MenuChrome.backKey(lastInput), UiMessages.text("settings.screen.cancel_caps"))), MenuChrome.controller(lastInput));
        } finally {
            font.endMegaBatch();
        }
    }

    private void renderKeyMenu(int width) {
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, width);
            MenuChrome.header(font, width, MenuChrome.fitLabel(EngineSettingLabels.label(selected()), width - 90),
                    UiMessages.text("settings.screen.keyboard_caps"));
            label(EngineSettingLabels.value(selected(), draft.text(selected())), 12, 40, width - 24,
                    draft.nonDefault(selected()) ? AMBER : WHITE);
            if (capturing) {
                float a = .55f + .45f * MenuChrome.pulse(ticks * 2);
                MenuChrome.label(font, UiMessages.text("settings.screen.press_a_key_or_chord"), 12, 76, CYAN, a);
                text(UiMessages.text("settings.screen.modifiers_ctrl_shift_alt_super"), 12, 92, width - 24, MUTED);
                text(UiMessages.text("settings.screen.use_key_name_editing_to_bind_escape"), 12, 118, width - 24, MUTED);
                text(UiMessages.text("settings.screen.controller_buttons_keep_navigating_menus"), 12, 130, width - 24, MUTED);
                MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.backKey(lastInput), UiMessages.text("settings.screen.cancel_caps"))), MenuChrome.controller(lastInput));
                return;
            }
            String[] options = { UiMessages.text("settings.screen.capture_a_new_key"), UiMessages.text("settings.screen.type_a_key_name_or_chord"), UiMessages.text("settings.screen.unbind_this_shortcut"), UiMessages.text("settings.screen.restore_default") };
            for (int i = 0; i < options.length; i++) {
                int y = 64 + i * 22;
                if (choice == i) MenuChrome.focus(font, 9, y - 4, width - 18, 17, ticks);
                label(options[i], 17, y, width - 34, WHITE);
            }
            MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("settings.screen.choose_caps")),
                    MenuChrome.hint(MenuChrome.detailsKey(lastInput), UiMessages.text("settings.screen.details_caps")),
                    MenuChrome.hint(MenuChrome.backKey(lastInput), UiMessages.text("settings.screen.back_caps"))), MenuChrome.controller(lastInput));
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
