package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.game.launch.LaunchToggle;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.TexturedQuadRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.openggf.game.MenuFeedback.Cue.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;

/**
 * Game Setup: the per-game launch profile editor hosted by {@link MasterTitleScreen}.
 *
 * <p>Rows are grouped as Players, Screen, Extras and Mods. Rewind, debug tools and
 * aspect ratio follow the all-games Settings value ("Use global (Off)") until overridden.
 * Edits form a draft: Back with unsaved changes asks Save / Discard / Keep editing,
 * matching Settings and the mod manager.
 */
public final class LaunchConfigPanel {

    private static final int SCREEN_H = 224;
    private static final int DESC_Y = 163;
    private static final int DESC_H = 41;

    public enum Result { NONE, CLOSED, CANCELLED, OPEN_MODS }

    /** One visible row of the panel. Team replaces Character/Sidekick under the S3K save screen. */
    enum Item {
        CHARACTER(LaunchProfile.Row.MAIN_CHARACTER, "Character", 44),
        SIDEKICK(LaunchProfile.Row.SIDEKICK, "Sidekick", 57),
        TEAM(null, "Team", 44),
        ASPECT(LaunchProfile.Row.WIDESCREEN, "Aspect ratio", 83),
        REWIND(LaunchProfile.Row.REWIND, "Rewind", 109),
        BORROW(LaunchProfile.Row.CROSS_GAME, "Borrow from", 122),
        DEBUG(LaunchProfile.Row.DEBUG_TOOLS, "Debug tools", 135),
        MODS(null, "Mods", 148),
        SAVE(null, "Save changes", 209);

        final LaunchProfile.Row row;
        final String label;
        final int y;

        Item(LaunchProfile.Row row, String label, int y) {
            this.row = row;
            this.label = label;
            this.y = y;
        }

        boolean focusable() { return this != TEAM; }
    }

    record RowView(String label, String value, boolean stock, boolean nonStandard, boolean experimental) {
    }

    private final MasterTitleScreen.GameEntry entry;
    private final LaunchProfileStore store;
    private final SonicConfigurationService configService;
    private final PixelFont font;

    private LaunchProfile profile;
    private final LaunchProfile originalProfile;
    private List<String> enabledMods = List.of();
    private InputHandler lastInput;
    private int focusIndex;
    private int ticks;
    private int focusTicks;
    private int changeTicks = 99;
    private int viewportWidth = 320;
    private Result pendingResult = Result.NONE;
    private boolean closed;
    private String saveError;
    private boolean prompt;
    private int promptChoice;

    public LaunchConfigPanel(MasterTitleScreen.GameEntry entry,
                             LaunchProfile currentProfile,
                             LaunchProfileStore store,
                             SonicConfigurationService configService,
                             PixelFont font,
                             TexturedQuadRenderer renderer) {
        this.entry = Objects.requireNonNull(entry, "entry");
        this.store = Objects.requireNonNull(store, "store");
        this.profile = this.store.sanitize(
                Objects.requireNonNull(currentProfile, "currentProfile"), entry);
        this.originalProfile = this.profile;
        this.configService = Objects.requireNonNull(configService, "configService");
        this.font = font;
    }

    /** Non-null while this game is suspended: true when it was paused in a level. */
    private Boolean pausedInLevel;

    /**
     * This game is suspended behind the master title. Rewind, debug tools and (in a level) aspect
     * reach it when it resumes; the rest of the setup waits for its next start.
     */
    public void setPausedGame(boolean inLevel) {
        pausedInLevel = inLevel;
    }

    /** Names of enabled mods that apply to this game, shown read-only in the Mods row. */
    public void setEnabledMods(List<String> names) {
        enabledMods = List.copyOf(Objects.requireNonNull(names, "names"));
    }

    /** Opens with the focus on a profile row, for the hub's setup chips. */
    public void focus(LaunchProfile.Row row) {
        List<Item> items = focusableItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).row == row) { focusIndex = i; return; }
        }
    }

    /** Opens with the focus on the Mods row. */
    public void focusMods() {
        focusIndex = focusableItems().indexOf(Item.MODS);
    }

    /** Re-arms the panel after the mod manager it opened has closed. */
    public void resumeAfterMods() {
        closed = false;
        pendingResult = Result.NONE;
    }

    public void update(InputHandler inputHandler) {
        if (closed || inputHandler == null) {
            return;
        }
        ticks++;
        focusTicks++;
        changeTicks++;
        lastInput = inputHandler;
        MenuInput.Pointer pointer = MenuInput.pointer(inputHandler, viewportWidth, SCREEN_H);
        if (prompt) {
            updatePrompt(inputHandler, pointer);
            return;
        }
        int beforeFocus = focusIndex;
        LaunchProfile beforeProfile = profile;
        boolean confirmCue = false;

        if (MenuInput.back(inputHandler) || pointer.rightPressed()) {
            requestLeave();
            return;
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_TAB) || inputHandler.isGamepadBackButtonPressed()) {
            // Quick save-and-close, kept from Launch Options for keyboard regulars.
            close(Result.CLOSED);
            MenuFeedback.emit(CONFIRM);
            return;
        }
        List<Item> items = focusableItems();
        focusIndex = Math.clamp(focusIndex, 0, items.size() - 1);
        if (MenuInput.up(inputHandler)) focusIndex = Math.floorMod(focusIndex - 1, items.size());
        if (MenuInput.down(inputHandler)) focusIndex = (focusIndex + 1) % items.size();
        Item item = items.get(focusIndex);

        // Mouse: hovering a row focuses it; clicking the left or right half steps the value.
        for (int i = 0; i < items.size(); i++) {
            Item candidate = items.get(i);
            int hitWidth = candidate == Item.SAVE ? saveButtonWidth() : viewportWidth - 16;
            if (!pointer.over(candidate == Item.SAVE ? 6 : 8, candidate == Item.SAVE ? 209 : candidate.y - 2,
                    hitWidth, candidate == Item.SAVE ? 11 : 13)) continue;
            if (focusIndex != i && (pointer.leftPressed() || movedOver(pointer))) focusIndex = i;
            if (pointer.leftPressed()) {
                item = candidate;
                if (item == Item.SAVE) { close(Result.CLOSED); MenuFeedback.emit(CONFIRM); return; }
                if (item == Item.MODS) { openMods(); return; }
                profile = step(item, pointer.x() >= viewportWidth / 2);
            }
        }

        if (item == Item.SAVE) {
            if (MenuInput.accept(inputHandler)) { close(Result.CLOSED); MenuFeedback.emit(CONFIRM); return; }
        } else if (item == Item.MODS) {
            if (MenuInput.accept(inputHandler)) { openMods(); return; }
        } else if (item.row != null) {
            if (MenuInput.left(inputHandler)) profile = step(item, false);
            if (MenuInput.right(inputHandler) || MenuInput.accept(inputHandler)) profile = step(item, true);
            if (inputHandler.isKeyPressed(GLFW_KEY_BACKSPACE) || MenuInput.details(inputHandler)) {
                profile = resetRow(item);
                confirmCue = true;
            }
        }
        // Borrow from can swap Character/Sidekick for Team, which shifts the rows above it;
        // keep the focus on the row that was changed rather than on its old index.
        int kept = focusableItems().indexOf(items.get(focusIndex));
        focusIndex = kept >= 0 ? kept : Math.clamp(focusIndex, 0, focusableItems().size() - 1);
        if (beforeFocus != focusIndex) focusTicks = 0;
        if (!beforeProfile.equals(profile)) changeTicks = 0;
        if (beforeFocus != focusIndex || !beforeProfile.equals(profile)) {
            saveError = null;
            MenuFeedback.emit(confirmCue ? CONFIRM : NAVIGATE);
        }
    }

    private int lastPointerX = Integer.MIN_VALUE;
    private int lastPointerY = Integer.MIN_VALUE;

    private boolean movedOver(MenuInput.Pointer pointer) {
        boolean moved = pointer.x() != lastPointerX || pointer.y() != lastPointerY;
        lastPointerX = pointer.x();
        lastPointerY = pointer.y();
        return moved;
    }

    private void openMods() {
        MenuFeedback.emit(CONFIRM);
        closed = true;
        pendingResult = Result.OPEN_MODS;
    }

    private LaunchProfile step(Item item, boolean forward) {
        return forward ? store.withNext(profile, item.row, entry) : store.withPrevious(profile, item.row, entry);
    }

    private LaunchProfile resetRow(Item item) {
        LaunchProfile stock = LaunchProfile.stockFor(entry);
        LaunchProfile p = profile;
        LaunchProfile reset = switch (item) {
            case REWIND -> new LaunchProfile(stock.rewind(), p.crossGameSource(), p.debugTools(), p.aspect(), p.mainCharacter(), p.sidekick());
            case DEBUG -> new LaunchProfile(p.rewind(), p.crossGameSource(), stock.debugTools(), p.aspect(), p.mainCharacter(), p.sidekick());
            case ASPECT -> new LaunchProfile(p.rewind(), p.crossGameSource(), p.debugTools(), stock.aspect(), p.mainCharacter(), p.sidekick());
            case BORROW -> new LaunchProfile(p.rewind(), stock.crossGameSource(), p.debugTools(), p.aspect(), p.mainCharacter(), p.sidekick());
            case CHARACTER, SIDEKICK -> new LaunchProfile(p.rewind(), p.crossGameSource(), p.debugTools(), p.aspect(),
                    stock.mainCharacter(), stock.sidekick());
            default -> p;
        };
        return store.sanitize(reset, entry);
    }

    private void requestLeave() {
        if (unsavedCount() == 0) {
            close(Result.CANCELLED);
            MenuFeedback.emit(CANCEL);
            return;
        }
        prompt = true;
        promptChoice = 0;
        MenuFeedback.emit(CONFIRM);
    }

    private static final String[] PROMPT_OPTIONS = {"Save", "Discard", "Keep editing"};

    private void updatePrompt(InputHandler input, MenuInput.Pointer pointer) {
        int before = promptChoice;
        if (MenuInput.back(input) || pointer.rightPressed()) {
            prompt = false;
            MenuFeedback.emit(CANCEL);
            return;
        }
        if (MenuInput.left(input) || MenuInput.up(input)) promptChoice = Math.floorMod(promptChoice - 1, 3);
        if (MenuInput.right(input) || MenuInput.down(input)) promptChoice = (promptChoice + 1) % 3;
        boolean choose = MenuInput.accept(input);
        int[] xs = promptButtonXs(viewportWidth);
        for (int i = 0; i < 3; i++) {
            if (!pointer.over(xs[i], 122, promptButtonWidth(i), 16)) continue;
            if (pointer.leftPressed() || movedOver(pointer)) promptChoice = i;
            choose |= pointer.leftPressed();
        }
        if (before != promptChoice) MenuFeedback.emit(NAVIGATE);
        if (!choose) return;
        prompt = false;
        switch (promptChoice) {
            case 0 -> { close(Result.CLOSED); MenuFeedback.emit(CONFIRM); }
            case 1 -> { profile = originalProfile; close(Result.CANCELLED); MenuFeedback.emit(CANCEL); }
            default -> MenuFeedback.emit(CANCEL);
        }
    }

    public Result consumeResult() {
        Result result = pendingResult;
        pendingResult = Result.NONE;
        return result;
    }

    void saveFailed() {
        closed = false;
        saveError = "Save failed - try again";
        int saveIndex = focusableItems().indexOf(Item.SAVE);
        if (saveIndex >= 0) focusIndex = saveIndex;
    }

    private void close(Result result) {
        if (closed) return;
        closed = true;
        pendingResult = result;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void render(int viewportWidth) {
        if (font == null) {
            return;
        }
        this.viewportWidth = Math.max(320, viewportWidth);
        int w = this.viewportWidth;
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, w);
            MenuChrome.header(font, w, entry.menuLabel.toUpperCase(Locale.ROOT) + " SETUP", "THIS GAME ONLY");
            MenuChrome.section(font, "PLAYERS", 12, 32, w - 24);
            MenuChrome.section(font, "SCREEN", 12, 71, w - 24);
            MenuChrome.section(font, "EXTRAS", 12, 97, w - 24);
            Item focused = focusedItem();
            for (Item item : items()) {
                if (item != Item.SAVE) drawRow(item, item == focused && !prompt, w);
            }
            drawDescription(focused, w);
            MenuChrome.footer(font, w, unsavedCount() == 0 ? saveError : "SAVE CHANGES",
                    footerHints(focused), MenuChrome.controller(lastInput));
            if (unsavedCount() != 0) {
                if (focused == Item.SAVE && !prompt) MenuChrome.focus(font, 6, 209, saveButtonWidth(), 11, focusTicks);
                else MenuChrome.panel(font, 6, 209, saveButtonWidth(), 11);
                MenuChrome.compact(font, "SAVE CHANGES", 10, 211, MenuChrome.GOLD);
            }
            if (prompt) drawPrompt(w);
        } finally {
            font.endMegaBatch();
        }
    }

    private static int saveButtonWidth() {
        return MenuChrome.compactWidth("SAVE CHANGES") + 8;
    }

    private List<MenuChrome.Hint> footerHints(Item focused) {
        List<MenuChrome.Hint> hints = new ArrayList<>();
        // The Mods row's own key (Enter) is shown in its description box.
        if (focused == Item.SAVE) hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), "SAVE"));
        else if (focused != Item.MODS) hints.add(MenuChrome.leftRight("CHANGE"));
        hints.add(MenuChrome.hint(MenuChrome.backKey(lastInput), unsavedCount() == 0 || focused == Item.SAVE ? "BACK" : "SAVE OR DISCARD"));
        return hints;
    }

    private void drawRow(Item item, boolean focused, int w) {
        int y = item.y;
        if (focused) MenuChrome.focus(font, 8, y - 1, w - 16, 12, focusTicks);
        if (item == Item.MODS) {
            MenuChrome.compact(font, item.label, 18, y + 1, focused ? MenuChrome.WHITE : MenuChrome.TEXT);
            String names = enabledMods.isEmpty() ? "NONE FOR THIS GAME" : String.join(", ", enabledMods);
            int labelEnd = 18 + MenuChrome.compactWidth(item.label) + 10;
            names = MenuChrome.fitCompact(names, w - 30 - labelEnd);
            MenuChrome.compact(font, names, w - 26 - MenuChrome.compactWidth(names), y + 1,
                    enabledMods.isEmpty() ? MenuChrome.DIM : MenuChrome.CYAN);
            MenuChrome.triangle(font, w - 19, y + 2, 3, 'R', focused ? MenuChrome.GOLD : MenuChrome.CYAN, 1f);
            return;
        }
        if (item == Item.TEAM) {
            MenuChrome.compact(font, item.label, 18, y + 1, MenuChrome.DIM);
            String v = "CHOSEN ON SAVE SCREEN";
            MenuChrome.compact(font, v, w - 16 - MenuChrome.compactWidth(v), y + 1, MenuChrome.DIM);
            return;
        }
        RowView view = view(item);
        boolean inherit = isInheriting(item);
        boolean changed = !view.stock() && !inherit;
        if (changed) MenuChrome.changedMarker(font, 11, y + 3);
        MenuChrome.compact(font, item.label, 18, y + 1, focused ? MenuChrome.WHITE : MenuChrome.TEXT);
        float[] color = view.experimental() ? MenuChrome.RED
                : inherit ? MenuChrome.DIM : changed ? MenuChrome.AMBER : MenuChrome.WHITE;
        String value = view.value();
        int labelEnd = 18 + MenuChrome.compactWidth(item.label) + 8;
        int right = w - 16 - (focused ? 8 : 0);
        value = MenuChrome.fitCompact(value, right - labelEnd - (focused ? 8 : 0));
        int valueWidth = MenuChrome.compactWidth(value);
        int valueX = right - valueWidth;
        MenuChrome.compact(font, value, valueX, y + 1, color);
        if (focused) {
            // The arrows hop outward for a few frames after each change.
            int nudge = changeTicks < 6 ? 1 : 0;
            MenuChrome.triangle(font, valueX - 7 - nudge, y + 2, 3, 'L', MenuChrome.GOLD, 1f);
            MenuChrome.triangle(font, w - 19 + nudge, y + 2, 3, 'R', MenuChrome.GOLD, 1f);
        }
    }

    private void drawDescription(Item item, int w) {
        List<String> lines = new ArrayList<>();
        List<float[]> colors = new ArrayList<>();
        if (item != null) describe(item, lines, colors);
        if (item != null && item.row != null && pausedInLevel != null) describePaused(item, lines, colors);
        if (saveError != null) {
            lines.add(saveError);
            while (colors.size() < lines.size() - 1) colors.add(null);
            colors.add(MenuChrome.RED);
        }
        MenuChrome.descriptionBox(font, 8, DESC_Y, w - 16, DESC_H, lines, colors);
        if (item == Item.MODS) {
            MenuChrome.hints(font, 14, keyLineY(lines.size()), List.of(MenuChrome.hint(MenuChrome.confirmKey(lastInput), "MANAGE MODS")),
                    MenuChrome.controller(lastInput), MenuChrome.MUTED);
        }
        if (item != null && item.row != null) {
            String key = MenuChrome.controller(lastInput) ? MenuChrome.detailsKey(lastInput) : "Bksp";
            MenuChrome.hints(font, 14, keyLineY(lines.size()), List.of(MenuChrome.hint(key,
                    isResettable(item) ? (item == Item.REWIND || item == Item.DEBUG || item == Item.ASPECT
                            ? "USE GLOBAL" : "USE STOCK") : (isInheriting(item) ? "USING GLOBAL" : "ALREADY STOCK"))),
                    MenuChrome.controller(lastInput), isResettable(item) ? MenuChrome.MUTED : MenuChrome.DIM);
        }
    }

    /** The key line sits just under the description text. */
    private static int keyLineY(int lineCount) {
        return Math.min(DESC_Y + DESC_H - 12, DESC_Y + 5 + lineCount * 8);
    }

    private boolean isResettable(Item item) {
        return !resetRow(item).equals(profile);
    }

    private void describe(Item item, List<String> lines, List<float[]> colors) {
        String game = entry.menuLabel;
        switch (item) {
            case CHARACTER -> {
                lines.add("Who you play as. Borrow from changes choices.");
                lines.add("Tails needs Sonic 2 or 3&K; Knuckles needs 3&K.");
            }
            case SIDEKICK -> {
                lines.add("The partner who follows you. None plays solo.");
                lines.add("Stock for " + game + ": " + titleCase(LaunchProfile.stockFor(entry).sidekick()) + ".");
            }
            case TEAM -> {
                lines.add("The Sonic 3&K save screen picks your team.");
            }
            case ASPECT -> {
                lines.add("Wider screens show more of each level.");
                if (view(item).experimental()) {
                    lines.add("21:9 and 32:9 are EXPERIMENTAL.");
                    colors.add(null);
                    colors.add(MenuChrome.RED);
                } else {
                    lines.add("Global follows Settings > Display (" + globalAspectLabel() + ").");
                }
            }
            case REWIND -> {
                lines.add("Hold the rewind key to step back in time.");
                lines.add("All games: " + onOff(configService.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED))
                        + ".  " + game + ": " + effective(profile.rewind(), SonicConfiguration.LIVE_REWIND_ENABLED) + ".");
                colors.add(null);
                colors.add(profile.rewind().isDefault() ? null : MenuChrome.AMBER);
            }
            case BORROW -> {
                lines.add("Use another game's sprites, spindash and sounds.");
                lines.add("Sonic 3&K also brings its save screen.");
            }
            case DEBUG -> {
                lines.add("Debug view and developer tools while playing.");
                lines.add("All games: " + onOff(configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED))
                        + ".  " + game + ": " + effective(profile.debugTools(), SonicConfiguration.DEBUG_VIEW_ENABLED) + ".");
                colors.add(null);
                colors.add(profile.debugTools().isDefault() ? null : MenuChrome.AMBER);
            }
            case SAVE -> {
                lines.add("Save " + unsavedCount() + (unsavedCount() == 1 ? " change" : " changes") + " and return to the menu.");
            }
            case MODS -> {
                lines.add(enabledMods.isEmpty() ? "No enabled mods change " + game + "."
                        : enabledMods.size() + (enabledMods.size() == 1 ? " enabled mod changes " : " enabled mods change ") + game + ".");
                lines.add("Mods are switched on and off for all games");
                lines.add("together, in the mod manager.");
            }
        }
    }

    /** Whether the suspended game takes this row on resume (see LiveSettings). */
    private void describePaused(Item item, List<String> lines, List<float[]> colors) {
        boolean live = item == Item.REWIND || item == Item.DEBUG || item == Item.ASPECT && pausedInLevel;
        while (colors.size() < lines.size()) colors.add(null);
        lines.add(live ? "Your paused game takes this when it resumes."
                : "Applies when you next start " + entry.menuLabel + ".");
        colors.add(live ? MenuChrome.CYAN : MenuChrome.DIM);
    }

    private String effective(LaunchToggle toggle, SonicConfiguration allGames) {
        return onOff(toggle.resolve(configService.getBoolean(allGames)));
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static String titleCase(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static int promptButtonWidth(int i) {
        return MenuChrome.labelWidth(PROMPT_OPTIONS[i]) + 14;
    }

    private static int[] promptButtonXs(int w) {
        int total = 0;
        for (int i = 0; i < 3; i++) total += promptButtonWidth(i);
        total += 2 * 8;
        int x = (w - total) / 2;
        int[] xs = new int[3];
        for (int i = 0; i < 3; i++) {
            xs[i] = x;
            x += promptButtonWidth(i) + 8;
        }
        return xs;
    }

    private void drawPrompt(int w) {
        MenuChrome.fill(font, 0, 0, w, SCREEN_H, new float[] {0, 0, 0}, .62f);
        int bw = Math.min(w - 24, 300);
        int bx = (w - bw) / 2;
        MenuChrome.fill(font, bx, 70, bw, 80, MenuChrome.NAVY, .98f);
        MenuChrome.outline(font, bx, 70, bw, 80, MenuChrome.GOLD, 1f, 1);
        String title = "SAVE " + entry.menuLabel.toUpperCase(Locale.ROOT) + " SETUP?";
        MenuChrome.label(font, title, (w - MenuChrome.labelWidth(title)) / 2, 80, MenuChrome.GOLD);
        String detail = unsavedCount() == 1 ? "1 change is not saved yet." : unsavedCount() + " changes are not saved yet.";
        MenuChrome.compact(font, detail, (w - MenuChrome.compactWidth(detail)) / 2, 100, MenuChrome.MUTED);
        int[] xs = promptButtonXs(w);
        for (int i = 0; i < 3; i++) {
            int bwidth = promptButtonWidth(i);
            if (i == promptChoice) MenuChrome.focus(font, xs[i], 122, bwidth, 16, ticks);
            else MenuChrome.panel(font, xs[i], 122, bwidth, 16);
            float[] c = i == 1 ? MenuChrome.AMBER : MenuChrome.WHITE;
            MenuChrome.label(font, PROMPT_OPTIONS[i], xs[i] + 7, 125, c);
        }
    }

    // -------------------------------------------------------------------------
    // Model views
    // -------------------------------------------------------------------------

    private List<Item> items() {
        List<Item> items = new ArrayList<>();
        if (profile.usesS3kDataSelectCharacters()) {
            items.add(Item.TEAM);
        } else {
            items.add(Item.CHARACTER);
            items.add(Item.SIDEKICK);
        }
        items.add(Item.ASPECT);
        items.add(Item.REWIND);
        items.add(Item.BORROW);
        items.add(Item.DEBUG);
        items.add(Item.MODS);
        if (unsavedCount() != 0) items.add(Item.SAVE);
        return items;
    }

    private List<Item> focusableItems() {
        return items().stream().filter(Item::focusable).toList();
    }

    private Item focusedItem() {
        List<Item> items = focusableItems();
        return items.isEmpty() ? null : items.get(Math.clamp(focusIndex, 0, items.size() - 1));
    }

    private boolean isInheriting(Item item) {
        return switch (item) {
            case REWIND -> profile.rewind().isDefault();
            case DEBUG -> profile.debugTools().isDefault();
            case ASPECT -> "global".equals(profile.aspect());
            default -> false;
        };
    }

    private RowView view(Item item) {
        LaunchProfile.Row row = item.row;
        String value = switch (item) {
            case REWIND -> toggleValue(profile.rewind(), SonicConfiguration.LIVE_REWIND_ENABLED);
            case DEBUG -> toggleValue(profile.debugTools(), SonicConfiguration.DEBUG_VIEW_ENABLED);
            case ASPECT -> "global".equals(profile.aspect()) ? "USE GLOBAL (" + globalAspectLabel() + ")"
                    : store.displayValue(profile, row, entry).toUpperCase(Locale.ROOT);
            case BORROW -> borrowLabel(profile.crossGameSource());
            default -> store.displayValue(profile, row, entry).toUpperCase(Locale.ROOT);
        };
        return new RowView(item.label, value, profile.isStock(row, entry),
                store.isNonStandard(profile, row, entry), profile.isExperimental(row));
    }

    private String toggleValue(LaunchToggle toggle, SonicConfiguration allGames) {
        return switch (toggle) {
            case DEFAULT -> "USE GLOBAL (" + onOff(configService.getBoolean(allGames)).toUpperCase(Locale.ROOT) + ")";
            case ON -> "ON";
            case OFF -> "OFF";
        };
    }

    static String borrowLabel(String source) {
        return switch (source) {
            case "s1" -> "SONIC 1";
            case "s2" -> "SONIC 2";
            case "s3k" -> "SONIC 3&K";
            default -> "OFF";
        };
    }

    private String globalAspectLabel() {
        return switch (configService.getString(SonicConfiguration.DISPLAY_ASPECT)) {
            case "WIDE_16_10" -> "16:10";
            case "WIDE_16_9" -> "16:9";
            case "ULTRA_21_9" -> "21:9";
            case "SUPER_32_9" -> "32:9";
            default -> "4:3";
        };
    }

    /** Rows whose draft value differs from the value the panel opened with. */
    int unsavedCount() {
        int count = 0;
        for (LaunchProfile.Row row : LaunchProfile.Row.values()) {
            boolean same = switch (row) {
                case REWIND -> profile.rewind() == originalProfile.rewind();
                case CROSS_GAME -> profile.crossGameSource().equals(originalProfile.crossGameSource());
                case DEBUG_TOOLS -> profile.debugTools() == originalProfile.debugTools();
                case WIDESCREEN -> profile.aspect().equals(originalProfile.aspect());
                case MAIN_CHARACTER -> profile.mainCharacter().equals(originalProfile.mainCharacter());
                case SIDEKICK -> profile.sidekick().equals(originalProfile.sidekick());
            };
            if (!same) count++;
        }
        return count;
    }

    List<RowView> rowViews() {
        List<RowView> views = new ArrayList<>();
        for (Item item : items()) {
            if (item.row != null) views.add(view(item));
        }
        return List.copyOf(views);
    }

    LaunchProfile.Row selectedRowForTest() {
        Item item = focusedItem();
        return item == null ? null : item.row;
    }

    boolean isModsRowFocusedForTest() {
        return focusedItem() == Item.MODS;
    }

    boolean isSaveFocusedForTest() {
        return focusedItem() == Item.SAVE;
    }

    boolean isPromptOpenForTest() {
        return prompt;
    }

    LaunchProfile currentProfileForTest() {
        return profile;
    }

    LaunchProfile currentProfile() {
        return profile;
    }
}
