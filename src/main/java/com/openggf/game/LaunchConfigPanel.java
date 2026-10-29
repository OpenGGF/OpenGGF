package com.openggf.game;

import com.openggf.i18n.UiMessages;
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
        CHARACTER(LaunchProfile.Row.MAIN_CHARACTER, "launch.item.character", 44),
        SIDEKICK(LaunchProfile.Row.SIDEKICK, "launch.item.sidekick", 57),
        TEAM(null, "launch.item.team", 44),
        ASPECT(LaunchProfile.Row.WIDESCREEN, "launch.item.aspect", 83),
        REWIND(LaunchProfile.Row.REWIND, "launch.item.rewind", 109),
        BORROW(LaunchProfile.Row.CROSS_GAME, "launch.item.borrow", 122),
        DEBUG(LaunchProfile.Row.DEBUG_TOOLS, "launch.item.debug", 135),
        MODS(null, "launch.item.mods", 148),
        SAVE(null, "launch.item.save", 209);

        final LaunchProfile.Row row;
        final String messageId;
        final int y;

        Item(LaunchProfile.Row row, String label, int y) {
            this.row = row;
            this.messageId = label;
            this.y = y;
        }

        String label() { return UiMessages.text(messageId); }

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
    private int promptScroll;
    private int rowScroll;

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
            if (candidate != Item.SAVE && !rowVisible(candidate)) continue;
            int hitWidth = candidate == Item.SAVE ? saveButtonWidth() : viewportWidth - 16;
            if (!pointer.over(candidate == Item.SAVE ? 6 : 8, candidate == Item.SAVE ? 209 : rowY(candidate) - 2,
                    hitWidth, candidate == Item.SAVE ? 11 : rowPitch())) continue;
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
        promptScroll = 0;
        MenuFeedback.emit(CONFIRM);
    }

    private static String[] promptOptions() { return new String[] {UiMessages.text("launch.ui.save"), UiMessages.text("launch.ui.discard"), UiMessages.text("launch.ui.keep_editing")}; }

    private void updatePrompt(InputHandler input, MenuInput.Pointer pointer) {
        int before = promptChoice;
        if (MenuInput.back(input) || pointer.rightPressed()) {
            prompt = false;
            MenuFeedback.emit(CANCEL);
            return;
        }
        if (MenuInput.left(input)) promptChoice = Math.floorMod(promptChoice - 1, 3);
        if (MenuInput.right(input)) promptChoice = (promptChoice + 1) % 3;
        boolean choose = MenuInput.accept(input);
        if (MenuInput.up(input)) promptScroll = Math.max(0, promptScroll - 1);
        if (MenuInput.down(input)) promptScroll = Math.min(Math.max(0, changeSummaryLines().size() - summaryCapacity()), promptScroll + 1);
        List<PromptButton> buttons = promptButtons(viewportWidth);
        for (int i = 0; i < buttons.size(); i++) {
            PromptButton button = buttons.get(i);
            if (!pointer.over(button.x(), button.y(), button.width(), button.height())) continue;
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
        saveError = UiMessages.text("launch.ui.save_failed_try_again");
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
            String scope = MenuChrome.fitCompact(UiMessages.text("launch.ui.this_game_only_caps"), (w - 40) / 3);
            String heading = MenuChrome.fitLabel(UiMessages.text("launch.heading", java.util.Map.of("game", entry.menuLabel)),
                    w - MenuChrome.compactWidth(scope) - 40);
            MenuChrome.header(font, w, heading, scope);
            if (!tallRows()) {
                MenuChrome.section(font, UiMessages.text("launch.ui.players_caps"), 12, 32, w - 24);
                MenuChrome.section(font, UiMessages.text("launch.ui.screen_caps"), 12, 71, w - 24);
                MenuChrome.section(font, UiMessages.text("launch.ui.extras_caps"), 12, 97, w - 24);
            }
            Item focused = focusedItem();
            for (Item item : items()) {
                if (item != Item.SAVE && rowVisible(item)) drawRow(item, item == focused && !prompt, w);
            }
            drawDescription(focused, w);
            MenuChrome.footer(font, w, unsavedCount() == 0 ? saveError : UiMessages.text("launch.ui.save_changes_caps"),
                    footerHints(focused), MenuChrome.controller(lastInput));
            if (unsavedCount() != 0) {
                if (focused == Item.SAVE && !prompt) MenuChrome.focus(font, 6, 209, saveButtonWidth(), 11, focusTicks);
                else MenuChrome.panel(font, 6, 209, saveButtonWidth(), 11);
                MenuChrome.compact(font, UiMessages.text("launch.ui.save_changes_caps"), 10, 211, MenuChrome.GOLD);
            }
            if (prompt) drawPrompt(w);
        } finally {
            font.endMegaBatch();
        }
    }

    private static int saveButtonWidth() {
        return MenuChrome.compactWidth(UiMessages.text("launch.ui.save_changes_caps")) + 8;
    }

    private List<MenuChrome.Hint> footerHints(Item focused) {
        List<MenuChrome.Hint> hints = new ArrayList<>();
        // The Mods row's own key (Enter) is shown in its description box.
        if (focused == Item.SAVE) hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("launch.ui.save_caps")));
        else if (focused != Item.MODS) hints.add(MenuChrome.leftRight(lastInput, UiMessages.text("launch.ui.change_caps")));
        hints.add(MenuChrome.hint(MenuChrome.backKey(lastInput), unsavedCount() == 0 || focused == Item.SAVE ? UiMessages.text("launch.ui.back_caps") : UiMessages.text("launch.ui.save_or_discard_caps")));
        return hints;
    }

    private boolean tallRows() { return MenuChrome.compactLineHeight() > 11; }
    private int rowPitch() { return Math.max(13, MenuChrome.compactLineHeight() + 4); }
    private int rowCapacity() { return Math.max(1, (DESC_Y - 47) / rowPitch()); }
    private void refreshRowScroll() {
        if (!tallRows()) { rowScroll = 0; return; }
        int focused = items().indexOf(focusedItem());
        if (focused < rowScroll) rowScroll = Math.max(0, focused);
        if (focused >= rowScroll + rowCapacity()) rowScroll = focused - rowCapacity() + 1;
    }
    private boolean rowVisible(Item item) {
        refreshRowScroll();
        int index = items().indexOf(item);
        return !tallRows() || index >= rowScroll && index < rowScroll + rowCapacity();
    }
    private int rowY(Item item) {
        return tallRows() ? 44 + (items().indexOf(item) - rowScroll) * rowPitch() : item.y;
    }

    private String setupControlText(String value, int width, boolean focused) {
        if (!focused) return MenuChrome.fitCompact(value, Math.max(0, width));
        var pages = com.openggf.graphics.text.HostText.shared().wrap(value, Math.max(1, width),
                com.openggf.graphics.text.TextFace.COMPACT, com.openggf.graphics.text.HostTextContext.current());
        return pages.get((focusTicks / 120) % pages.size());
    }

    private void drawRow(Item item, boolean focused, int w) {
        int y = rowY(item);
        String caption = setupControlText(item.label(), (w - 56) / 2, focused);
        if (focused) MenuChrome.focus(font, 8, y - 1, w - 16, rowPitch(), focusTicks);
        if (item == Item.MODS) {
            MenuChrome.compact(font, caption, 18, y + 1, focused ? MenuChrome.WHITE : MenuChrome.TEXT);
            String names = enabledMods.isEmpty() ? UiMessages.text("launch.ui.none_for_this_game_caps") : String.join(", ", enabledMods);
            int labelEnd = 18 + MenuChrome.compactWidth(caption) + 10;
            names = MenuChrome.fitCompact(names, w - 30 - labelEnd);
            MenuChrome.compact(font, names, w - 26 - MenuChrome.compactWidth(names), y + 1,
                    enabledMods.isEmpty() ? MenuChrome.DIM : MenuChrome.CYAN);
            MenuChrome.triangle(font, w - 19, y + 2, 3, 'R', focused ? MenuChrome.GOLD : MenuChrome.CYAN, 1f);
            return;
        }
        if (item == Item.TEAM) {
            MenuChrome.compact(font, caption, 18, y + 1, MenuChrome.DIM);
            String v = MenuChrome.fitCompact(UiMessages.text("launch.ui.chosen_on_save_screen_caps"), w - 42 - MenuChrome.compactWidth(caption));
            MenuChrome.compact(font, v, w - 16 - MenuChrome.compactWidth(v), y + 1, MenuChrome.DIM);
            return;
        }
        RowView view = view(item);
        boolean inherit = isInheriting(item);
        boolean changed = !view.stock() && !inherit;
        if (changed) MenuChrome.changedMarker(font, 11, y + 3);
        MenuChrome.compact(font, caption, 18, y + 1, focused ? MenuChrome.WHITE : MenuChrome.TEXT);
        float[] color = view.experimental() ? MenuChrome.RED
                : inherit ? MenuChrome.DIM : changed ? MenuChrome.AMBER : MenuChrome.WHITE;
        String value = view.value();
        int labelEnd = 18 + MenuChrome.compactWidth(caption) + 8;
        int right = w - 16 - (focused ? 8 : 0);
        value = setupControlText(value, right - labelEnd - (focused ? 8 : 0), focused);
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
            MenuChrome.hints(font, 14, keyLineY(lines.size()), List.of(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("launch.ui.manage_mods_caps"))),
                    MenuChrome.controller(lastInput), MenuChrome.MUTED);
        }
        if (item != null && item.row != null) {
            String key = MenuChrome.controller(lastInput) ? MenuChrome.detailsKey(lastInput) : "Bksp";
            MenuChrome.hints(font, 14, keyLineY(lines.size()), List.of(MenuChrome.hint(key,
                    isResettable(item) ? (item == Item.REWIND || item == Item.DEBUG || item == Item.ASPECT
                            ? UiMessages.text("launch.ui.use_global_caps") : UiMessages.text("launch.ui.use_stock_caps")) : (isInheriting(item) ? UiMessages.text("launch.ui.using_global_caps") : UiMessages.text("launch.ui.already_stock_caps")))),
                    MenuChrome.controller(lastInput), isResettable(item) ? MenuChrome.MUTED : MenuChrome.DIM);
        }
    }

    /** The key line sits just under the description text. */
    private static int keyLineY(int lineCount) {
        return Math.min(DESC_Y + DESC_H - 12, DESC_Y + 5 + lineCount * MenuChrome.compactLineHeight());
    }

    private boolean isResettable(Item item) {
        return !resetRow(item).equals(profile);
    }

    private void describe(Item item, List<String> lines, List<float[]> colors) {
        String game = entry.menuLabel;
        switch (item) {
            case CHARACTER -> {
                lines.add(UiMessages.text("launch.ui.who_you_play_as_borrow_from_changes_choices"));
                lines.add(UiMessages.text("launch.ui.tails_needs_sonic_2_or_3_k_knuckles_needs_3_k"));
            }
            case SIDEKICK -> {
                lines.add(UiMessages.text("launch.ui.the_partner_who_follows_you_none_plays_solo"));
                lines.add(UiMessages.text("launch.stock_sidekick", java.util.Map.of("game", game, "sidekick", characterLabel(LaunchProfile.stockFor(entry).sidekick()))));
            }
            case TEAM -> {
                lines.add(UiMessages.text("launch.ui.the_sonic_3_k_save_screen_picks_your_team"));
            }
            case ASPECT -> {
                lines.add(UiMessages.text("launch.ui.wider_screens_show_more_of_each_level"));
                if (view(item).experimental()) {
                    lines.add(UiMessages.text("launch.ui.21_9_and_32_9_are_experimental"));
                    colors.add(null);
                    colors.add(MenuChrome.RED);
                } else {
                    lines.add(UiMessages.text("launch.global_aspect_help", java.util.Map.of("aspect", globalAspectLabel())));
                }
            }
            case REWIND -> {
                lines.add(UiMessages.text("launch.ui.hold_the_rewind_key_to_step_back_in_time"));
                lines.add(UiMessages.text("launch.rewind_effective", java.util.Map.of("global", onOff(configService.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED)), "game", game, "effective", effective(profile.rewind(), SonicConfiguration.LIVE_REWIND_ENABLED))));
                colors.add(null);
                colors.add(profile.rewind().isDefault() ? null : MenuChrome.AMBER);
            }
            case BORROW -> {
                lines.add(UiMessages.text("launch.ui.use_another_game_s_sprites_spindash_and_sounds"));
                lines.add(UiMessages.text("launch.ui.sonic_3_k_also_brings_its_save_screen"));
            }
            case DEBUG -> {
                lines.add(UiMessages.text("launch.ui.debug_view_and_developer_tools_while_playing"));
                lines.add(UiMessages.text("launch.debug_effective", java.util.Map.of("global", onOff(configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED)), "game", game, "effective", effective(profile.debugTools(), SonicConfiguration.DEBUG_VIEW_ENABLED))));
                colors.add(null);
                colors.add(profile.debugTools().isDefault() ? null : MenuChrome.AMBER);
            }
            case SAVE -> {
                lines.add(UiMessages.text("launch.save_count", java.util.Map.of("count", unsavedCount())));
            }
            case MODS -> {
                lines.add(enabledMods.isEmpty() ? UiMessages.text("launch.no_mods_change_game", java.util.Map.of("game", game))
                        : UiMessages.text("launch.mods_change_game", java.util.Map.of("count", enabledMods.size(), "game", game)));
                lines.add(UiMessages.text("launch.mods_global"));
            }
        }
    }

    /** Whether the suspended game takes this row on resume (see LiveSettings). */
    private void describePaused(Item item, List<String> lines, List<float[]> colors) {
        boolean live = item == Item.REWIND || item == Item.DEBUG || item == Item.ASPECT && pausedInLevel;
        while (colors.size() < lines.size()) colors.add(null);
        lines.add(live ? UiMessages.text("launch.ui.your_paused_game_takes_this_when_it_resumes")
                : UiMessages.text("launch.next_game", java.util.Map.of("game", entry.menuLabel)));
        colors.add(live ? MenuChrome.CYAN : MenuChrome.DIM);
    }

    private String effective(LaunchToggle toggle, SonicConfiguration allGames) {
        return onOff(toggle.resolve(configService.getBoolean(allGames)));
    }

    private static String onOff(boolean value) {
        return value ? UiMessages.text("launch.ui.on") : UiMessages.text("launch.ui.off");
    }

    private static String characterLabel(String value) {
        if (value.equals("none")) return UiMessages.text("launch.character.none");
        return switch (value) { case "sonic" -> "Sonic"; case "tails" -> "Tails"; case "knuckles" -> "Knuckles"; default -> value; };
    }

    record PromptButton(int x, int y, int width, int height) { }

    static List<PromptButton> promptButtons(int width) {
        String[] labels = promptOptions();
        int[] widths = java.util.Arrays.stream(labels).mapToInt(label -> MenuChrome.labelWidth(label) + 14).toArray();
        int height = Math.max(16, MenuChrome.labelLineHeight() + 6);
        int total = java.util.Arrays.stream(widths).sum() + 16;
        List<PromptButton> result = new ArrayList<>();
        if (total <= width - 24) {
            int x = (width - total) / 2;
            for (int buttonWidth : widths) {
                result.add(new PromptButton(x, 192 - height, buttonWidth, height));
                x += buttonWidth + 8;
            }
        } else {
            int buttonWidth = Math.min(width - 32, java.util.Arrays.stream(widths).max().orElse(width - 32));
            int top = 192 - labels.length * height - (labels.length - 1) * 4;
            for (int i = 0; i < labels.length; i++)
                result.add(new PromptButton((width - buttonWidth) / 2, top + i * (height + 4), buttonWidth, height));
        }
        return List.copyOf(result);
    }

    private int summaryPitch() { return Math.max(10, MenuChrome.compactLineHeight() + 2); }
    private boolean summaryHintFits() {
        return promptButtons(viewportWidth).getFirst().y() - 77 - 8 >= 2 * summaryPitch();
    }
    private int summaryCapacity() {
        int room = promptButtons(viewportWidth).getFirst().y() - 77 - 8;
        if (summaryHintFits()) room -= summaryPitch();
        return Math.max(1, Math.min(8, room / summaryPitch()));
    }

    private void drawPrompt(int w) {
        MenuChrome.fill(font, 0, 0, w, SCREEN_H, new float[] {0, 0, 0}, .62f);
        int bw = Math.min(w - 24, 300);
        int bx = (w - bw) / 2;
        MenuChrome.fill(font, bx, 45, bw, 153, MenuChrome.NAVY, .98f);
        MenuChrome.outline(font, bx, 45, bw, 153, MenuChrome.GOLD, 1f, 1);
        String title = MenuChrome.fitLabel(UiMessages.text("launch.save_question", java.util.Map.of("game", entry.menuLabel)), bw - 20);
        MenuChrome.label(font, title, (w - MenuChrome.labelWidth(title)) / 2, 55, MenuChrome.GOLD);
        List<String> changes = changeSummaryLines();
        for (int i = 0; i < summaryCapacity() && promptScroll + i < changes.size(); i++)
            MenuChrome.compact(font, MenuChrome.fitCompact(changes.get(promptScroll + i), bw - 20), bx + 10, 77 + i * summaryPitch(),
                    i % 2 == 0 ? MenuChrome.TEXT : MenuChrome.CYAN);
        List<PromptButton> buttons = promptButtons(w);
        if (changes.size() > summaryCapacity() && summaryHintFits()) {
            String hint = UiMessages.text("launch.review_changes", java.util.Map.of("key", MenuChrome.verticalKey(lastInput), "count", unsavedCount()));
            MenuChrome.compact(font, MenuChrome.fitCompact(hint, bw - 20), bx + 10,
                    buttons.getFirst().y() - MenuChrome.compactLineHeight() - 5, MenuChrome.DIM);
        }
        String[] labels = promptOptions();
        for (int i = 0; i < buttons.size(); i++) {
            PromptButton button = buttons.get(i);
            if (i == promptChoice) MenuChrome.focus(font, button.x(), button.y(), button.width(), button.height(), ticks);
            else MenuChrome.panel(font, button.x(), button.y(), button.width(), button.height());
            float[] color = i == 1 ? MenuChrome.AMBER : MenuChrome.WHITE;
            String label = MenuChrome.fitLabel(labels[i], button.width() - 14);
            MenuChrome.label(font, label, button.x() + 7, button.y() + 3, color);
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
            case ASPECT -> "global".equals(profile.aspect()) ? UiMessages.text("launch.use_global_aspect", java.util.Map.of("aspect", globalAspectLabel()))
                    : store.displayValue(profile, row, entry);
            case BORROW -> borrowLabel(profile.crossGameSource());
            case CHARACTER -> characterLabel(profile.mainCharacter());
            case SIDEKICK -> characterLabel(profile.sidekick());
            default -> store.displayValue(profile, row, entry);
        };
        return new RowView(item.label(), value, profile.isStock(row, entry),
                store.isNonStandard(profile, row, entry), profile.isExperimental(row));
    }

    private String toggleValue(LaunchToggle toggle, SonicConfiguration allGames) {
        return switch (toggle) {
            case DEFAULT -> UiMessages.text("launch.use_global_toggle", java.util.Map.of("value", onOff(configService.getBoolean(allGames))));
            case ON -> UiMessages.text("launch.ui.on_caps");
            case OFF -> UiMessages.text("launch.ui.off_caps");
        };
    }

    static String borrowLabel(String source) {
        return switch (source) {
            case "s1" -> UiMessages.text("launch.ui.sonic_1_caps");
            case "s2" -> UiMessages.text("launch.ui.sonic_2_caps");
            case "s3k" -> UiMessages.text("launch.ui.sonic_3_k_caps");
            default -> UiMessages.text("launch.ui.off_caps");
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

    List<String> changeSummaryLines() {
        List<String> changes = new ArrayList<>();
        for (Item item : Item.values()) {
            if (item.row == null) continue;
            String before = store.displayValue(originalProfile, item.row, entry);
            String after = store.displayValue(profile, item.row, entry);
            if (before.equals(after)) continue;
            changes.add(item.label());
            changes.addAll(com.openggf.graphics.text.HostText.shared().wrap(before + " -> " + after,
                    Math.min(viewportWidth - 24, 300) - 20, com.openggf.graphics.text.TextFace.COMPACT,
                    com.openggf.graphics.text.HostTextContext.current()));
        }
        return changes;
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
