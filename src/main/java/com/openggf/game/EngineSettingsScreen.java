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
    private static final int ROW_H = 14;
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
    private boolean closeRequested;
    private boolean applied;
    private boolean choosing;
    private int choice;
    private boolean keyMenu;
    private boolean capturing;
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

    public void update(InputHandler input) {
        if (input == null || closeRequested) return;
        ticks++;
        focusTicks++;
        if (messageTicks > 0 && --messageTicks == 0) message = null;
        lastInput = input;
        if (details != null) { if (details.update(input)) details = null; return; }
        MenuInput.Pointer pointer = MenuInput.pointer(input, viewportWidth, 224);
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
        if (!MenuInput.back(input) && MenuInput.details(input) && !capturing && !tabsFocused) {
            details = new MenuDetailsScreen(EngineSettingLabels.label(selected()),
                    ConfigCatalog.meta(selected()).description() + "\n\n" + effect(selected())
                            + "\n\nCurrent draft value:\n" + draft.text(selected()));
            MenuFeedback.emit(CONFIRM);
            return;
        }
        if (capturing) { updateCapture(input); return; }
        if (keyMenu) { updateKeyMenu(input); return; }
        if (choosing) { updateChoice(input); return; }
        if (MenuInput.back(input) || pointer.rightPressed()) { requestClose(); return; }
        if (updatePointer(pointer)) return;
        if (tabsFocused) {
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
            if (row == 0 && tabs.length > 1) tabsFocused = true;
            else focusRow(Math.floorMod(row - 1, keys.size()));
            MenuFeedback.emit(NAVIGATE);
            return;
        }
        if (MenuInput.down(input)) { focusRow((row + 1) % keys.size()); MenuFeedback.emit(NAVIGATE); return; }
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
        if (type == ConfigType.KEY) { keyMenu = true; choice = 0; }
        else if (type == ConfigType.BOOL || type == ConfigType.ENUM) {
            choosing = true;
            choice = Math.max(0, options().indexOf(draft.text(selected())));
        } else openTextEditor();
    }

    private boolean updatePointer(MenuInput.Pointer pointer) {
        if (!pointer.inside()) return false;

        boolean moved = pointer.x() != lastPointerX || pointer.y() != lastPointerY;
        lastPointerX = pointer.x();
        lastPointerY = pointer.y();
        int[] xs = tabXs(viewportWidth);
        for (int i = 0; i < tabs.length && tabs.length > 1; i++) {
            if (!pointer.over(xs[i], TAB_Y, tabWidth(i), 14)) continue;
            if (pointer.leftPressed() && i != category) {
                selectCategory(i);
                tabsFocused = true;
                MenuFeedback.emit(NAVIGATE);
            }
            return pointer.leftPressed();
        }
        List<SonicConfiguration> keys = keys();
        for (int i = 0; i < VISIBLE_ROWS && scroll + i < keys.size(); i++) {
            if (!pointer.over(8, ROW_Y + i * ROW_H - 3, viewportWidth - 22, ROW_H)) continue;
            int index = scroll + i;
            if ((moved || pointer.leftPressed()) && (tabsFocused || row != index)) {
                tabsFocused = false;
                focusRow(index);
                MenuFeedback.emit(NAVIGATE);
            }
            if (pointer.leftPressed()) { openEditor(); return true; }
            return false;
        }
        return false;
    }

    private void selectCategory(int index) {
        category = index;
        row = 0;
        scroll = 0;
        focusTicks = 0;
    }

    private void focusRow(int index) {
        row = index;
        if (row < scroll) scroll = row;
        if (row >= scroll + VISIBLE_ROWS) scroll = row - VISIBLE_ROWS + 1;
        focusTicks = 0;
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
        if (SettingTiming.of(key) == SettingTiming.RESTART) {
            return new Timing("Needs a restart: close and reopen OpenGGF.", MenuChrome.AMBER);
        }
        if (pausedGameTakes == null) return null;
        return pausedGameTakes.test(key) ? new Timing("Your paused game takes this when it resumes.", MenuChrome.CYAN)
                : new Timing("Applies when you next start a game.", MenuChrome.MUTED);
    }

    private static int restartTagWidth() {
        return MenuChrome.compactWidth(RESTART_TAG) + 6;
    }

    /** A small amber tag on rows that only take effect after a restart. */
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

    private List<SonicConfiguration> keys() {
        return draft.keys(tabs[Math.min(category, tabs.length - 1)]);
    }

    private SonicConfiguration selected() {
        List<SonicConfiguration> keys = keys();
        return keys.get(Math.clamp(row, 0, keys.size() - 1));
    }

    private void requestClose() {
        if (draft.dirty()) {
            prompt = true;
            promptChoice = 0;
            MenuFeedback.emit(CONFIRM);
        } else {
            MenuFeedback.emit(CANCEL);
            closeRequested = true;
        }
    }

    private void updatePrompt(InputHandler input, MenuInput.Pointer pointer) {
        int before = promptChoice;
        if (MenuInput.back(input) || pointer.rightPressed()) { prompt = false; MenuFeedback.emit(CANCEL); return; }
        if (MenuInput.left(input) || MenuInput.up(input)) promptChoice = Math.floorMod(promptChoice - 1, 3);
        if (MenuInput.right(input) || MenuInput.down(input)) promptChoice = (promptChoice + 1) % 3;
        boolean choose = MenuInput.accept(input);
        int[] xs = promptButtonXs(viewportWidth);
        boolean moved = pointer.x() != lastPointerX || pointer.y() != lastPointerY;
        lastPointerX = pointer.x();
        lastPointerY = pointer.y();
        for (int i = 0; i < 3; i++) {
            if (!pointer.over(xs[i], 122, promptButtonWidth(i), 16)) continue;
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
            // MasterTitleScreen refreshes ROM previews on consumeApplied; load resolves saved ROM paths.
            case SONIC_1_ROM, SONIC_2_ROM, SONIC_3K_ROM, ROMS_DIRECTORY, ROMS_PREFER_COMPOSITE ->
                    "After saving: the ROM catalogue rescans and previews refresh; used for the next game load.";
            case TRACE_CATALOG_DIR -> "After saving: used when the trace picker next opens.";
            // LiveCaptureRecorderFactory reads these when creating a recording.
            case CAPTURE_CODEC, CAPTURE_AUDIO_CODEC, CAPTURE_ENCODER_THREADS, CAPTURE_ENCODER_PRESET,
                    CAPTURE_FFMPEG_PASS1_ARGS, CAPTURE_FFMPEG_PASS2_ARGS, CAPTURE_QUEUE_BUDGET_MB,
                    CAPTURE_OUTPUT_DIR, CAPTURE_CONTAINER -> "After saving: used for the next recording.";
            // LaunchProfileApplier only overrides these for games whose setup is not Default.
            case LIVE_REWIND_ENABLED, DEBUG_VIEW_ENABLED, DISPLAY_ASPECT ->
                    "Used by every game whose Game Setup says Default. Takes effect at the next game start,"
                            + " or when a paused game resumes (aspect: a game paused in a level).";
            default -> switch (SettingTiming.of(key)) {
                case NOW -> "Takes effect as soon as you save.";
                case NEXT_GAME -> "Takes effect when you next start a game.";
                case RESTART -> "Restart OpenGGF (close and reopen it) for this to take effect.";
            };
        };
    }

    private void refreshPresentation() {
        if (presentationRevision == draft.revision()) return;
        for (EngineSettingsDraft.Category group : tabs) {
            boolean any = false;
            for (SonicConfiguration key : draft.keys(group)) {
                labels.computeIfAbsent(key, EngineSettingLabels::label);
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
                case 0 -> capturing = true;
                case 1 -> openTextEditor();
                case 2 -> { draft.set(selected(), ""); keyMenu = false; }
                case 3 -> { draft.reset(selected()); keyMenu = false; }
                default -> throw new IllegalStateException("Unknown binding action");
            }
        }
    }

    private void updateCapture(InputHandler input) {
        if (MenuInput.textBack(input)) { capturing = false; MenuFeedback.emit(CANCEL); return; }
        for (int key = GLFW_KEY_SPACE; key <= GLFW_KEY_LAST; key++) {
            if (key >= GLFW_KEY_LEFT_SHIFT && key <= GLFW_KEY_RIGHT_SUPER) continue;
            if (!MenuInput.textKeyPressed(input, key)) continue;
            String chord = (input.isPhysicalControlDown() ? "CTRL+" : "")
                    + (input.isPhysicalShiftDown() ? "SHIFT+" : "")
                    + (input.isPhysicalAltDown() ? "ALT+" : "")
                    + (input.isPhysicalSuperDown() ? "META+" : "")
                    + GlfwKeyNameResolver.nameOf(key);
            draft.set(selected(), chord);
            MenuFeedback.emit(CONFIRM);
            capturing = false;
            keyMenu = false;
            return;
        }
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
            MenuChrome.header(font, width, scope == Scope.ADVANCED ? "ADVANCED SETTINGS" : "SETTINGS", "ALL GAMES");
            int contentTop = renderTabs(width);
            renderRows(width, contentTop);
            renderDescription(width);
            int unsaved = draft.changedCount();
            String status = message != null ? message : unsaved > 0 ? unsaved + " UNSAVED"
                    : PendingRestart.current(config).isEmpty() ? null : RESTART_STATUS;
            List<MenuChrome.Hint> hints = new ArrayList<>();
            hints.add(MenuChrome.leftRight(tabsFocused ? "TAB" : "CHANGE"));
            if (tabsFocused) hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), "SETTINGS"));
            hints.add(MenuChrome.hint(MenuChrome.backKey(lastInput), draft.dirty() ? "SAVE OR DISCARD" : "BACK"));
            MenuChrome.footer(font, width, status, hints, MenuChrome.controller(lastInput));
            if (prompt) renderPrompt(width);
        } finally {
            font.endMegaBatch();
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
        List<SonicConfiguration> keys = keys();
        int visible = Math.min(VISIBLE_ROWS, keys.size() - scroll);
        // One type size per page: full-size lettering only when every visible row fits it.
        boolean fullSize = true;
        for (int i = 0; i < visible; i++) {
            SonicConfiguration key = keys.get(scroll + i);
            String value = shownValue(key);
            fullSize &= MenuChrome.labelWidth(labels.get(key)) + 12 + MenuChrome.labelWidth(value) <= width - 34 - 8;
        }
        for (int i = 0; i < visible; i++) {
            int index = scroll + i;
            SonicConfiguration key = keys.get(index);
            int y = top + i * ROW_H;
            boolean focused = !tabsFocused && row == index && !prompt;
            if (focused) MenuChrome.focus(font, 8, y - 1, width - 22, 12, focusTicks);
            boolean nonDefault = draft.nonDefault(key);
            if (nonDefault) MenuChrome.changedMarker(font, 11, y + 3);
            String value = shownValue(key);
            int right = width - 22 - (focused ? 8 : 0);
            float[] valueColor = nonDefault ? MenuChrome.AMBER : MenuChrome.WHITE;
            float[] labelColor = focused ? MenuChrome.WHITE : MenuChrome.TEXT;
            int valueX;
            boolean restart = SettingTiming.of(key) == SettingTiming.RESTART;
            if (fullSize) {
                valueX = right - MenuChrome.labelWidth(value);
                MenuChrome.label(font, value, valueX, y, valueColor);
                MenuChrome.label(font, labels.get(key), 18, y, labelColor);
                int tagX = valueX - 10 - restartTagWidth();
                if (restart && tagX >= 18 + MenuChrome.labelWidth(labels.get(key)) + 6) restartTag(tagX, y);
            } else {
                // Values keep up to half the row; labels take what is left and scroll when focused.
                int valueRoom = Math.max(30, (width - 40) / 2);
                String v = window(value, valueRoom / MenuChrome.COMPACT_ADVANCE, focused);
                valueX = right - MenuChrome.compactWidth(v);
                MenuChrome.compact(font, v, valueX, y + 1, valueColor);
                int labelRoom = valueX - 10 - 18;
                if (restart) {
                    restartTag(valueX - 10 - restartTagWidth(), y);
                    labelRoom -= restartTagWidth() + 4;
                }
                MenuChrome.compact(font, window(labels.get(key), labelRoom / MenuChrome.COMPACT_ADVANCE, focused),
                        18, y + 1, labelColor);
            }
            if (focused) {
                MenuChrome.triangle(font, valueX - 7, y + 2, 3, 'L', MenuChrome.GOLD, 1f);
                MenuChrome.triangle(font, width - 25, y + 2, 3, 'R', MenuChrome.GOLD, 1f);
            }
        }
        if (keys.size() > VISIBLE_ROWS) {
            // A slim scrollbar along the right edge of the rows.
            int trackTop = top - 3;
            int trackH = VISIBLE_ROWS * ROW_H;
            int thumbH = Math.max(8, trackH * VISIBLE_ROWS / keys.size());
            int thumbY = trackTop + (trackH - thumbH) * scroll / Math.max(1, keys.size() - VISIBLE_ROWS);
            MenuChrome.fill(font, width - 11, trackTop, 2, trackH, MenuChrome.EDGE, .6f);
            MenuChrome.fill(font, width - 12, thumbY, 4, thumbH, MenuChrome.CYAN);
        }
    }

    private String shownValue(SonicConfiguration key) {
        String value = valueLabels.get(key);
        return value == null || value.isEmpty() ? "(none)" : value;
    }

    private void renderDescription(int width) {
        List<String> lines = new ArrayList<>();
        List<float[]> colors = new ArrayList<>();
        if (tabsFocused) {
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
            String description = ConfigCatalog.meta(selected()).description();
            if (!description.equals(descriptionText) || width != descriptionWidth) {
                descriptionText = description;
                descriptionWidth = width;
                descriptionLines = MenuDetailsScreen.wrapText(MenuDetailsScreen.readable(description), (width - 28) / 6);
            }
            // A setting that needs a restart, or a paused game's status, takes the second line;
            // Details has the full description.
            Timing status = status(selected());
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
        if (!tabsFocused) {
            ConfigType type = ConfigCatalog.meta(selected()).type();
            String verb = type == ConfigType.KEY ? "REBIND" : type == ConfigType.BOOL || type == ConfigType.ENUM ? "CHOOSE" : "EDIT";
            MenuChrome.hints(font, 14, DESC_Y + 22, List.of(MenuChrome.hint(MenuChrome.confirmKey(lastInput), verb),
                    MenuChrome.hint(MenuChrome.detailsKey(lastInput), "DETAILS")), MenuChrome.controller(lastInput), MenuChrome.MUTED);
            Object defaultValue = config.getDefaultValue(selected());
            String def = "DEFAULT: " + MenuDetailsScreen.readable(EngineSettingLabels.value(selected(),
                    defaultValue == null ? "" : String.valueOf(defaultValue)));
            def = MenuChrome.fitCompact(def, width / 2 - 20);
            MenuChrome.compact(font, def, width - 14 - MenuChrome.compactWidth(def), DESC_Y + 24, MenuChrome.DIM);
        }
    }

    private static String tabSummary(EngineSettingsDraft.Category category) {
        return switch (category) {
            case DISPLAY -> "Aspect ratio, window, shaders and colour.";
            case AUDIO -> "Music, sound effects and the sound chip.";
            case INPUT -> "Keyboard, controllers and every shortcut key.";
            case GAMEPLAY -> "Rewind, debug tools and what happens at startup.";
            case FILES -> "Where your ROM images live.";
            case RECORDING -> "Video capture of your play sessions.";
            case LAUNCH -> "Per-game launch profiles.";
            case ADVANCED -> "Debug, playback, Discord and direct-launch options.";
        };
    }

    private static int promptButtonWidth(int i) {
        return MenuChrome.labelWidth(PROMPT_OPTIONS[i]) + 14;
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

    private void renderPrompt(int w) {
        MenuChrome.fill(font, 0, 0, w, 224, new float[] {0, 0, 0}, .62f);
        int bw = Math.min(w - 24, 300);
        int bx = (w - bw) / 2;
        MenuChrome.fill(font, bx, 70, bw, 80, MenuChrome.NAVY, .98f);
        MenuChrome.outline(font, bx, 70, bw, 80, MenuChrome.GOLD, 1f, 1);
        String title = "SAVE SETTINGS?";
        MenuChrome.label(font, title, (w - MenuChrome.labelWidth(title)) / 2, 80, MenuChrome.GOLD);
        int count = draft.changedCount();
        String detail = count == 1 ? "1 change is not saved yet." : count + " changes are not saved yet.";
        MenuChrome.compact(font, detail, (w - MenuChrome.compactWidth(detail)) / 2, 100, MenuChrome.MUTED);
        int[] xs = promptButtonXs(w);
        for (int i = 0; i < 3; i++) {
            int bwidth = promptButtonWidth(i);
            if (i == promptChoice) MenuChrome.focus(font, xs[i], 122, bwidth, 16, ticks);
            else MenuChrome.panel(font, xs[i], 122, bwidth, 16);
            MenuChrome.label(font, PROMPT_OPTIONS[i], xs[i] + 7, 125, i == 1 ? MenuChrome.AMBER : MenuChrome.WHITE);
        }
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
                MenuChrome.footer(font, width, null, List.of(MenuChrome.hint("Esc", "CANCEL")), false);
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

    private String window(String value, int chars, boolean scroll) {
        if (value.length() <= chars) return value;
        if (!scroll) return value.substring(0, Math.max(0, chars - 3)) + "...";
        int overflow = value.length() - chars;
        int offset = Math.min(overflow, Math.max(0, (focusTicks / 8) % (overflow + 30) - 15));
        return value.substring(offset, offset + chars);
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
