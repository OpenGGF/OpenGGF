package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.i18n.UiMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Settings page for one ordered team, stored as comma-separated character codes. Each
 * sidekick is a row in follow order: the first follows the player, each later one follows
 * the sidekick before it ({@code GameplayTeamBootstrap}). Left/Right change a row's
 * character; confirm opens Move earlier / Move later / Remove. Done hands the joined value
 * to the caller; Back leaves it untouched.
 *
 * <p>The direct-launch line ({@code characters.sidekick}) holds sidekicks only, behind the
 * separately configured player. A save-screen team (one entry of
 * {@code characters.dataSelectExtraCombos}, edited from {@link ExtraTeamsEditor}) leads with
 * its player, which is then the first, editable row.
 */
final class SidekickTeamEditor {
    /** Built-in playable codes; an unlisted stored code (a mod character) is kept until changed. */
    static final List<String> CHARACTERS = List.of("sonic", "tails", "knuckles");
    /** Matches the authored-team bound in {@code ModLevelSettings}. */
    static final int MAX_SIDEKICKS = 16;
    private static final int TOP = 33;

    enum Kind { MAIN, SLOT, ADD, DEFAULT, DONE }
    record Row(Kind kind, int slot) { }
    enum Action { MOVE_EARLIER, MOVE_LATER, REMOVE }

    private final String title;
    private final boolean mainEditable;
    /** Null when the page has no stock value to restore. */
    private final List<String> defaults;
    private final Consumer<String> done;
    private final List<String> team;
    private String main;
    private int selected;
    private int scroll;
    private int ticks;
    private int width = 320;
    private boolean closed;
    private int actionSlot = -1;
    private int actionChoice;
    private InputHandler lastInput;

    private SidekickTeamEditor(String title, String main, boolean mainEditable, List<String> sidekicks,
                               List<String> defaults, Consumer<String> done) {
        this.title = title;
        this.main = main;
        this.mainEditable = mainEditable;
        this.team = new ArrayList<>(sidekicks);
        this.defaults = defaults;
        this.done = Objects.requireNonNull(done, "done");
    }

    /** The direct-launch sidekick line behind the configured player; Done yields sidekicks only. */
    static SidekickTeamEditor directLaunch(String leaderCode, String current, String defaultValue, Consumer<String> done) {
        List<String> leader = parse(leaderCode);
        return new SidekickTeamEditor(UiMessages.text("settings.sidekicks.title"),
                leader.isEmpty() ? "sonic" : leader.getFirst(), false, parse(current), parse(defaultValue), done);
    }

    /** One save-screen team; Done yields the player followed by its sidekicks. */
    static SidekickTeamEditor saveScreenTeam(String title, String current, Consumer<String> done) {
        List<String> codes = parse(current);
        return new SidekickTeamEditor(title, codes.isEmpty() ? "sonic" : codes.getFirst(), true,
                codes.isEmpty() ? List.of() : codes.subList(1, codes.size()), null, done);
    }

    static List<String> parse(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String code : value.split(",")) {
            String trimmed = code.trim().toLowerCase(java.util.Locale.ROOT);
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return List.copyOf(out);
    }

    static String name(String code) {
        if (code.isEmpty()) return code;
        return Character.toUpperCase(code.charAt(0)) + code.substring(1);
    }

    List<String> team() { return List.copyOf(team); }
    String main() { return main; }
    String value() {
        if (!mainEditable) return String.join(",", team);
        List<String> all = new ArrayList<>(team);
        all.addFirst(main);
        return String.join(",", all);
    }
    boolean closed() { return closed; }
    boolean actionsOpen() { return actionSlot >= 0; }
    Row selectedRow() { return rows().get(selected); }

    List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        if (mainEditable) rows.add(new Row(Kind.MAIN, -1));
        for (int i = 0; i < team.size(); i++) rows.add(new Row(Kind.SLOT, i));
        if (team.size() < MAX_SIDEKICKS) rows.add(new Row(Kind.ADD, -1));
        if (defaults != null) rows.add(new Row(Kind.DEFAULT, -1));
        rows.add(new Row(Kind.DONE, -1));
        return rows;
    }

    List<Action> actions(int slot) {
        List<Action> actions = new ArrayList<>();
        if (slot > 0) actions.add(Action.MOVE_EARLIER);
        if (slot < team.size() - 1) actions.add(Action.MOVE_LATER);
        actions.add(Action.REMOVE);
        return actions;
    }

    /** Returns true once the page has closed, by Done or Back. */
    boolean update(InputHandler input, MenuInput.Pointer pointer) {
        if (closed || input == null) return closed;
        ticks++;
        lastInput = input;
        if (actionSlot >= 0) { updateActions(input, pointer); return closed; }
        if (MenuInput.back(input) || pointer.rightPressed()) {
            closed = true;
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            return true;
        }
        List<Row> rows = rows();
        int before = selected;
        if (MenuInput.up(input)) selected = Math.floorMod(selected - 1, rows.size());
        if (MenuInput.down(input)) selected = (selected + 1) % rows.size();
        if (pointer.inside()) selected = Math.clamp(selected - pointer.wheel(), 0, rows.size() - 1);
        if (before != selected) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        Row row = rows.get(selected);
        if (editable(row) && (MenuInput.left(input) || MenuInput.right(input))) {
            cycle(row.slot(), MenuInput.left(input) ? -1 : 1);
        }
        boolean clicked = false;
        int pitch = rowPitch();
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            int y = rowY(i);
            if (!pointer.leftPressed() || !pointer.over(9, y, width - 18, pitch)) continue;
            int index = scroll + i;
            Row target = rows.get(index);
            if (editable(target) && index == selected && pointer.x() < arrowRight(target.slot())) {
                // The focused row's arrows are click targets; the rest of the row opens its actions.
                cycle(target.slot(), pointer.x() < 32 ? -1 : 1);
            } else {
                clicked = index == selected;
                if (index != selected) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
                selected = index;
            }
        }
        if (MenuInput.accept(input) || clicked) activate(rows.get(selected));
        keepVisible();
        return closed;
    }

    private void updateActions(InputHandler input, MenuInput.Pointer pointer) {
        List<Action> actions = actions(actionSlot);
        if (MenuInput.back(input) || pointer.rightPressed()) {
            actionSlot = -1;
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            return;
        }
        int before = actionChoice;
        if (MenuInput.up(input)) actionChoice = Math.floorMod(actionChoice - 1, actions.size());
        if (MenuInput.down(input)) actionChoice = (actionChoice + 1) % actions.size();
        if (pointer.inside()) actionChoice = Math.clamp(actionChoice - pointer.wheel(), 0, actions.size() - 1);
        if (before != actionChoice) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        boolean clicked = false;
        int pitch = rowPitch();
        for (int i = 0; i < actions.size(); i++) {
            if (pointer.leftPressed() && pointer.over(9, firstRowTop() + i * pitch, width - 18, pitch)) {
                actionChoice = i;
                clicked = true;
            }
        }
        if (MenuInput.accept(input) || clicked) perform(actions.get(actionChoice));
    }

    void activate(Row row) {
        switch (row.kind()) {
            case MAIN -> cycle(-1, 1);
            case SLOT -> { actionSlot = row.slot(); actionChoice = 0; }
            case ADD -> {
                team.add(team.isEmpty() ? "tails" : team.getLast());
                selected = team.size() - 1 + (mainEditable ? 1 : 0);
            }
            case DEFAULT -> {
                team.clear();
                team.addAll(defaults);
                selected = rows().size() - 1;
            }
            case DONE -> {
                done.accept(value());
                closed = true;
            }
        }
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        keepVisible();
    }

    void perform(Action action) {
        int slot = actionSlot;
        switch (action) {
            case MOVE_EARLIER -> { java.util.Collections.swap(team, slot, slot - 1); slot--; }
            case MOVE_LATER -> { java.util.Collections.swap(team, slot, slot + 1); slot++; }
            case REMOVE -> team.remove(slot);
        }
        selected = slot + (mainEditable ? 1 : 0);
        actionSlot = -1;
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        keepVisible();
    }

    /** Steps sidekick {@code slot}, or the player when {@code slot} is -1, through the built-in characters. */
    void cycle(int slot, int direction) {
        String code = slot < 0 ? main : team.get(slot);
        int index = CHARACTERS.indexOf(code);
        // An unlisted code steps onto the first or last built-in character.
        int next = index < 0 ? (direction > 0 ? 0 : CHARACTERS.size() - 1)
                : Math.floorMod(index + direction, CHARACTERS.size());
        if (slot < 0) main = CHARACTERS.get(next);
        else team.set(slot, CHARACTERS.get(next));
        MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
    }

    private static boolean editable(Row row) { return row.kind() == Kind.SLOT || row.kind() == Kind.MAIN; }

    private String follows(int slot) {
        return slot == 0 ? name(main) : name(team.get(slot - 1));
    }

    private String code(Row row) { return row.kind() == Kind.MAIN ? main : team.get(row.slot()); }

    private String rowText(Row row) {
        return switch (row.kind()) {
            case MAIN, SLOT -> name(code(row));
            case ADD -> UiMessages.text("settings.sidekicks.add");
            case DEFAULT -> UiMessages.text("settings.screen.restore_default");
            case DONE -> UiMessages.text("settings.sidekicks.done");
        };
    }

    private String help(Row row) {
        return switch (row.kind()) {
            case MAIN -> UiMessages.text("settings.teams.main_help");
            case SLOT -> UiMessages.text("settings.sidekicks.slot_help",
                    Map.of("number", row.slot() + 1, "leader", follows(row.slot())));
            case ADD -> UiMessages.text("settings.sidekicks.add_help",
                    Map.of("max", MAX_SIDEKICKS));
            case DEFAULT -> UiMessages.text("settings.sidekicks.default_help", Map.of("team", teamText(defaults)));
            case DONE -> UiMessages.text(mainEditable ? "settings.teams.keep_help" : "settings.sidekicks.done_help");
        };
    }

    private static String teamText(List<String> codes) {
        if (codes.isEmpty()) return UiMessages.text("settings.value.NONE");
        return String.join(", ", codes.stream().map(SidekickTeamEditor::name).toList());
    }

    private String actionText(Action action) {
        return switch (action) {
            case MOVE_EARLIER -> UiMessages.text("settings.sidekicks.move_earlier");
            case MOVE_LATER -> UiMessages.text("settings.sidekicks.move_later");
            case REMOVE -> UiMessages.text("settings.sidekicks.remove");
        };
    }

    void render(PixelFont font, int viewportWidth) {
        if (font == null) return;
        width = Math.max(320, viewportWidth);
        keepVisible();
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, width);
            if (actionSlot >= 0) { renderActions(font); return; }
            MenuChrome.header(font, width, page(title, width - 90), tag());
            String caption = mainEditable ? UiMessages.text("settings.teams.team_caption")
                    : UiMessages.text("settings.sidekicks.player", Map.of("name", name(main)));
            MenuChrome.compact(font, page(caption, width - 24), 12, TOP + MenuChrome.compactMetrics().above(), MenuChrome.MUTED);
            List<Row> rows = rows();
            int pitch = rowPitch();
            int textDy = (pitch - MenuChrome.compactLineHeight()) / 2 + MenuChrome.compactMetrics().above();
            if (team.isEmpty()) {
                // An empty line is a choice, not a fault: an information row above the way to add one.
                int y = firstRowTop() + (emptyAfter() + 1) * pitch;
                MenuChrome.icon(font, MenuChrome.Icon.INFO, 16, y + (pitch - MenuChrome.ICON_SIZE) / 2, MenuChrome.CYAN);
                MenuChrome.compact(font, page(UiMessages.text("settings.sidekicks.empty"), width - 44), 28, y + textDy, MenuChrome.TEXT);
            }
            for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
                int index = scroll + i;
                Row row = rows.get(index);
                int y = rowY(i);
                boolean focused = index == selected;
                if (focused) MenuChrome.focus(font, 9, y, width - 18, pitch - 1, ticks);
                float[] ink = focused ? MenuChrome.GOLD : MenuChrome.TEXT;
                if (editable(row)) {
                    if (row.kind() == Kind.SLOT)
                        MenuChrome.compact(font, Integer.toString(row.slot() + 1), 16, y + textDy, MenuChrome.DIM);
                    String note = row.kind() == Kind.MAIN ? UiMessages.text("settings.teams.main_note")
                            : UiMessages.text("settings.sidekicks.follows", Map.of("name", follows(row.slot())));
                    int noteWidth = Math.min(MenuChrome.compactWidth(note), (width - 60) / 2);
                    MenuChrome.compact(font, page(note, noteWidth), width - 16 - noteWidth, y + textDy,
                            focused ? MenuChrome.MUTED : MenuChrome.DIM);
                    String name = page(rowText(row), width - 76 - noteWidth);
                    MenuChrome.compact(font, name, 36, y + textDy, ink);
                    if (focused) {
                        int arrowY = y + (pitch - 5) / 2;
                        MenuChrome.triangle(font, 29, arrowY, 3, 'L', MenuChrome.GOLD, 1f);
                        MenuChrome.triangle(font, 38 + MenuChrome.compactWidth(name), arrowY, 3, 'R', MenuChrome.GOLD, 1f);
                    }
                } else {
                    MenuChrome.compact(font, page(rowText(row), width / 2 - 24), 16, y + textDy,
                            row.kind() == Kind.DONE && !focused ? MenuChrome.CYAN : ink);
                }
            }
            if (rows.size() > visibleRows()) {
                MenuChrome.scrollbar(font, width - 8, firstRowTop(), visibleRows() * pitch, scroll, visibleRows(), rows.size());
            }
            MenuChrome.descriptionBox(font, 9, boxTop(), width - 18, boxHeight(),
                    List.of(help(rows.get(selected))), List.of(MenuChrome.MUTED), ticks);
            List<MenuChrome.Hint> hints = new ArrayList<>();
            if (rows.get(selected).kind() == Kind.MAIN) {
                hints.add(MenuChrome.leftRight(lastInput, UiMessages.text("settings.sidekicks.character_caps")));
            } else if (rows.get(selected).kind() == Kind.SLOT) {
                hints.add(MenuChrome.leftRight(lastInput, UiMessages.text("settings.sidekicks.character_caps")));
                hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("settings.sidekicks.actions_caps")));
            } else {
                hints.add(MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("settings.screen.choose_caps")));
            }
            hints.add(MenuChrome.hint(MenuChrome.backKey(lastInput), UiMessages.text("settings.screen.cancel_caps")));
            MenuChrome.footer(font, width, null, hints, MenuChrome.controller(lastInput), ticks);
        } finally {
            font.endMegaBatch();
        }
    }

    private void renderActions(PixelFont font) {
        MenuChrome.header(font, width, page(UiMessages.text("settings.sidekicks.slot_title",
                Map.of("number", actionSlot + 1, "name", name(team.get(actionSlot)))), width - 90), tag());
        MenuChrome.compact(font, page(UiMessages.text("settings.sidekicks.slot_help",
                Map.of("number", actionSlot + 1, "leader", follows(actionSlot))), width - 24),
                12, TOP + MenuChrome.compactMetrics().above(), MenuChrome.MUTED);
        List<Action> actions = actions(actionSlot);
        int pitch = rowPitch();
        int textDy = (pitch - MenuChrome.compactLineHeight()) / 2 + MenuChrome.compactMetrics().above();
        for (int i = 0; i < actions.size(); i++) {
            int y = firstRowTop() + i * pitch;
            boolean focused = i == actionChoice;
            if (focused) MenuChrome.focus(font, 9, y, width - 18, pitch - 1, ticks);
            boolean remove = actions.get(i) == Action.REMOVE;
            float[] ink = remove ? MenuChrome.AMBER : focused ? MenuChrome.GOLD : MenuChrome.TEXT;
            if (remove) MenuChrome.icon(font, MenuChrome.Icon.WARNING, 16, y + (pitch - MenuChrome.ICON_SIZE) / 2, MenuChrome.AMBER);
            MenuChrome.compact(font, page(actionText(actions.get(i)), width - 50), 28, y + textDy, ink);
        }
        MenuChrome.footer(font, width, null, List.of(
                MenuChrome.hint(MenuChrome.confirmKey(lastInput), UiMessages.text("settings.screen.choose_caps")),
                MenuChrome.hint(MenuChrome.backKey(lastInput), UiMessages.text("settings.screen.back_caps"))),
                MenuChrome.controller(lastInput), ticks);
    }

    private String tag() {
        return UiMessages.text(mainEditable ? "settings.teams.tag_caps" : "settings.sidekicks.tag_caps");
    }

    private String page(String text, int pixels) {
        return MenuStyle.pageText(text, Math.max(1, pixels), MenuStyle.COMPACT, ticks);
    }

    private int arrowRight(int slot) {
        return 42 + MenuChrome.compactWidth(name(slot < 0 ? main : team.get(slot)));
    }

    static int rowPitch() { return Math.max(14, MenuChrome.compactLineHeight() + 6); }
    static int captionTop() { return TOP; }
    static int firstRowTop() { return TOP + MenuChrome.compactLineHeight() + 5; }
    /** The row index the empty-line information row follows: the player row, or none (-1). */
    private int emptyAfter() { return mainEditable ? 0 : -1; }
    private boolean emptyGap() { return team.isEmpty() && actionSlot < 0; }
    /** Top of visible row {@code i}; an empty line gives a row's space to the information row. */
    private int rowY(int i) {
        return firstRowTop() + i * rowPitch() + (emptyGap() && scroll + i > emptyAfter() ? rowPitch() : 0);
    }
    static int boxHeight() { return 2 * Math.max(MenuChrome.DESCRIPTION_PITCH, MenuChrome.compactLineHeight()) + 6; }
    static int boxTop() { return MenuChrome.FOOTER_Y - 4 - boxHeight(); }
    private int visibleRows() { return Math.max(1, (boxTop() - 4 - firstRowTop()) / rowPitch() - (emptyGap() ? 1 : 0)); }

    private void keepVisible() {
        int count = rows().size();
        selected = Math.clamp(selected, 0, count - 1);
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + visibleRows()) scroll = selected - visibleRows() + 1;
        scroll = Math.clamp(scroll, 0, Math.max(0, count - visibleRows()));
    }
}
