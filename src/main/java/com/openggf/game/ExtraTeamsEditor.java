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
 * Settings page for {@code characters.dataSelectExtraCombos}: the teams each save screen
 * offers after its built-in teams, stored as {@code main,sidekick...;main,sidekick...}
 * ({@code DataSelectHostProfile.parseExtraTeams}). Each team is a row; confirm opens Edit
 * team / Move earlier / Move later / Remove, and Edit and Add team open a
 * {@link SidekickTeamEditor} whose first row is the team's player. Done hands the joined
 * value to the caller's draft; Back leaves the draft untouched.
 */
final class ExtraTeamsEditor {
    enum Kind { TEAM, ADD, CLEAR, DONE }
    record Row(Kind kind, int team) { }
    enum Action { EDIT, MOVE_EARLIER, MOVE_LATER, REMOVE }

    private final Consumer<String> done;
    private final List<String> teams;
    private SidekickTeamEditor child;
    private int selected;
    private int scroll;
    private int ticks;
    private int width = 320;
    private boolean closed;
    private int actionTeam = -1;
    private int actionChoice;
    private InputHandler lastInput;

    ExtraTeamsEditor(String current, Consumer<String> done) {
        this.teams = new ArrayList<>(parse(current));
        this.done = Objects.requireNonNull(done, "done");
    }

    /** Each team in its normalised comma form; empty teams are dropped, as the save screen does. */
    static List<String> parse(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) return out;
        for (String team : value.split(";")) {
            List<String> codes = SidekickTeamEditor.parse(team);
            if (!codes.isEmpty()) out.add(String.join(",", codes));
        }
        return out;
    }

    /** "Sonic + Tails + Knuckles", or "Sonic alone". */
    static String teamLabel(String team) {
        List<String> codes = SidekickTeamEditor.parse(team);
        if (codes.size() == 1) return UiMessages.text("settings.teams.alone", Map.of("name", SidekickTeamEditor.name(codes.getFirst())));
        return String.join(" + ", codes.stream().map(SidekickTeamEditor::name).toList());
    }

    /** The Settings row's value: every team, in save-screen order. */
    static String summary(String value) {
        return String.join("; ", parse(value).stream().map(ExtraTeamsEditor::teamLabel).toList());
    }

    List<String> teams() { return List.copyOf(teams); }
    String value() { return String.join(";", teams); }
    boolean closed() { return closed; }
    SidekickTeamEditor child() { return child; }

    List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) rows.add(new Row(Kind.TEAM, i));
        rows.add(new Row(Kind.ADD, -1));
        rows.add(new Row(Kind.CLEAR, -1));
        rows.add(new Row(Kind.DONE, -1));
        return rows;
    }

    List<Action> actions(int team) {
        List<Action> actions = new ArrayList<>();
        actions.add(Action.EDIT);
        if (team > 0) actions.add(Action.MOVE_EARLIER);
        if (team < teams.size() - 1) actions.add(Action.MOVE_LATER);
        actions.add(Action.REMOVE);
        return actions;
    }

    /** Returns true once the page has closed, by Done or Back. */
    boolean update(InputHandler input, MenuInput.Pointer pointer) {
        if (closed || input == null) return closed;
        ticks++;
        lastInput = input;
        if (child != null) {
            if (child.update(input, pointer)) child = null;
            return false;
        }
        if (actionTeam >= 0) { updateActions(input, pointer); return closed; }
        if (MenuInput.back(input) || pointer.rightPressed()) {
            closed = true;
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            return true;
        }
        List<Row> rows = rows();
        int before = selected;
        if (MenuInput.up(input)) selected = Math.floorMod(selected - 1, rows.size());
        if (MenuInput.down(input)) selected = (selected + 1) % rows.size();
        if (before != selected) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        boolean clicked = false;
        int pitch = SidekickTeamEditor.rowPitch();
        for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
            if (!pointer.leftPressed() || !pointer.over(9, rowY(i), width - 18, pitch)) continue;
            clicked = scroll + i == selected;
            if (!clicked) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
            selected = scroll + i;
        }
        if (MenuInput.accept(input) || clicked) activate(rows.get(selected));
        keepVisible();
        return closed;
    }

    private void updateActions(InputHandler input, MenuInput.Pointer pointer) {
        List<Action> actions = actions(actionTeam);
        if (MenuInput.back(input) || pointer.rightPressed()) {
            actionTeam = -1;
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            return;
        }
        int before = actionChoice;
        if (MenuInput.up(input)) actionChoice = Math.floorMod(actionChoice - 1, actions.size());
        if (MenuInput.down(input)) actionChoice = (actionChoice + 1) % actions.size();
        if (before != actionChoice) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        boolean clicked = false;
        int pitch = SidekickTeamEditor.rowPitch();
        for (int i = 0; i < actions.size(); i++) {
            if (pointer.leftPressed() && pointer.over(9, SidekickTeamEditor.firstRowTop() + i * pitch, width - 18, pitch)) {
                actionChoice = i;
                clicked = true;
            }
        }
        if (MenuInput.accept(input) || clicked) perform(actions.get(actionChoice));
    }

    void activate(Row row) {
        switch (row.kind()) {
            case TEAM -> { actionTeam = row.team(); actionChoice = 0; }
            case ADD -> child = SidekickTeamEditor.saveScreenTeam(UiMessages.text("settings.teams.new_title"), "sonic,tails",
                    team -> { teams.add(team); selected = teams.size() - 1; keepVisible(); });
            case CLEAR -> { teams.clear(); selected = rows().size() - 1; }
            case DONE -> { done.accept(value()); closed = true; }
        }
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        keepVisible();
    }

    void perform(Action action) {
        int team = actionTeam;
        switch (action) {
            case EDIT -> {
                int edited = team;
                child = SidekickTeamEditor.saveScreenTeam(
                        UiMessages.text("settings.teams.team_title", Map.of("number", edited + 1)), teams.get(edited),
                        value -> teams.set(edited, value));
            }
            case MOVE_EARLIER -> { java.util.Collections.swap(teams, team, team - 1); team--; }
            case MOVE_LATER -> { java.util.Collections.swap(teams, team, team + 1); team++; }
            case REMOVE -> teams.remove(team);
        }
        selected = team;
        actionTeam = -1;
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        keepVisible();
    }

    private String rowText(Row row) {
        return switch (row.kind()) {
            case TEAM -> teamLabel(teams.get(row.team()));
            case ADD -> UiMessages.text("settings.teams.add");
            case CLEAR -> UiMessages.text("settings.screen.restore_default");
            case DONE -> UiMessages.text("settings.sidekicks.done");
        };
    }

    private String help(Row row) {
        return switch (row.kind()) {
            case TEAM -> UiMessages.text("settings.teams.team_help",
                    Map.of("number", row.team() + 1, "team", teamLabel(teams.get(row.team()))));
            case ADD -> UiMessages.text("settings.teams.add_help");
            case CLEAR -> UiMessages.text("settings.teams.default_help");
            case DONE -> UiMessages.text("settings.teams.done_help");
        };
    }

    private String actionText(Action action) {
        return switch (action) {
            case EDIT -> UiMessages.text("settings.teams.edit");
            case MOVE_EARLIER -> UiMessages.text("settings.sidekicks.move_earlier");
            case MOVE_LATER -> UiMessages.text("settings.sidekicks.move_later");
            case REMOVE -> UiMessages.text("settings.sidekicks.remove");
        };
    }

    void render(PixelFont font, int viewportWidth) {
        if (font == null) return;
        if (child != null) { child.render(font, viewportWidth); return; }
        width = Math.max(320, viewportWidth);
        keepVisible();
        font.beginMegaBatch();
        try {
            MenuChrome.background(font, width);
            if (actionTeam >= 0) { renderActions(font); return; }
            MenuChrome.header(font, width, page(UiMessages.text("settings.teams.title"), width - 90),
                    UiMessages.text("settings.teams.tag_caps"));
            MenuChrome.compact(font, page(UiMessages.text("settings.teams.caption"), width - 24),
                    12, SidekickTeamEditor.captionTop() + MenuChrome.compactMetrics().above(), MenuChrome.MUTED);
            List<Row> rows = rows();
            int pitch = SidekickTeamEditor.rowPitch();
            int textDy = (pitch - MenuChrome.compactLineHeight()) / 2 + MenuChrome.compactMetrics().above();
            if (teams.isEmpty()) {
                // No extra teams is the stock state: an information row above the way to add one.
                int y = SidekickTeamEditor.firstRowTop();
                MenuChrome.icon(font, MenuChrome.Icon.INFO, 16, y + (pitch - MenuChrome.ICON_SIZE) / 2, MenuChrome.CYAN);
                MenuChrome.compact(font, page(UiMessages.text("settings.teams.empty"), width - 44), 28, y + textDy, MenuChrome.TEXT);
            }
            for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
                int index = scroll + i;
                Row row = rows.get(index);
                int y = rowY(i);
                boolean focused = index == selected;
                if (focused) MenuChrome.focus(font, 9, y, width - 18, pitch - 1, ticks);
                float[] ink = focused ? MenuChrome.GOLD : MenuChrome.TEXT;
                if (row.kind() == Kind.TEAM) {
                    MenuChrome.compact(font, Integer.toString(row.team() + 1), 16, y + textDy, MenuChrome.DIM);
                    MenuChrome.compact(font, page(rowText(row), width - 56), 30, y + textDy, ink);
                } else {
                    MenuChrome.compact(font, page(rowText(row), width - 40), 16, y + textDy,
                            row.kind() == Kind.DONE && !focused ? MenuChrome.CYAN : ink);
                }
            }
            if (rows.size() > visibleRows()) {
                MenuChrome.scrollbar(font, width - 8, SidekickTeamEditor.firstRowTop(), visibleRows() * pitch,
                        scroll, visibleRows(), rows.size());
            }
            MenuChrome.descriptionBox(font, 9, SidekickTeamEditor.boxTop(), width - 18, SidekickTeamEditor.boxHeight(),
                    List.of(help(rows.get(selected))), List.of(MenuChrome.MUTED), ticks);
            boolean team = rows.get(selected).kind() == Kind.TEAM;
            MenuChrome.footer(font, width, null, List.of(
                    MenuChrome.hint(MenuChrome.confirmKey(lastInput),
                            UiMessages.text(team ? "settings.sidekicks.actions_caps" : "settings.screen.choose_caps")),
                    MenuChrome.hint(MenuChrome.backKey(lastInput), UiMessages.text("settings.screen.cancel_caps"))),
                    MenuChrome.controller(lastInput), ticks);
        } finally {
            font.endMegaBatch();
        }
    }

    private void renderActions(PixelFont font) {
        MenuChrome.header(font, width, page(UiMessages.text("settings.teams.team_title", Map.of("number", actionTeam + 1)), width - 90),
                UiMessages.text("settings.teams.tag_caps"));
        MenuChrome.compact(font, page(teamLabel(teams.get(actionTeam)), width - 24),
                12, SidekickTeamEditor.captionTop() + MenuChrome.compactMetrics().above(), MenuChrome.MUTED);
        List<Action> actions = actions(actionTeam);
        int pitch = SidekickTeamEditor.rowPitch();
        int textDy = (pitch - MenuChrome.compactLineHeight()) / 2 + MenuChrome.compactMetrics().above();
        for (int i = 0; i < actions.size(); i++) {
            int y = SidekickTeamEditor.firstRowTop() + i * pitch;
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

    private String page(String text, int pixels) {
        return MenuStyle.pageText(text, Math.max(1, pixels), MenuStyle.COMPACT, ticks);
    }

    private boolean emptyGap() { return teams.isEmpty() && actionTeam < 0; }
    private int rowY(int i) {
        return SidekickTeamEditor.firstRowTop() + (i + (emptyGap() ? 1 : 0)) * SidekickTeamEditor.rowPitch();
    }
    private int visibleRows() {
        return Math.max(1, (SidekickTeamEditor.boxTop() - 4 - SidekickTeamEditor.firstRowTop()) / SidekickTeamEditor.rowPitch()
                - (emptyGap() ? 1 : 0));
    }

    private void keepVisible() {
        int count = rows().size();
        selected = Math.clamp(selected, 0, count - 1);
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + visibleRows()) scroll = selected - visibleRows() + 1;
        scroll = Math.clamp(scroll, 0, Math.max(0, count - visibleRows()));
    }
}
