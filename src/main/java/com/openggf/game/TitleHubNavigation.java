package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;

/**
 * Focus model for the cartridge-shelf hub. Four rows from top to bottom: engine-wide
 * header tabs, the game card, the setup chips and the per-game action bar. Up/Down
 * moves between rows; Left/Right moves within a row, and on the card changes game.
 * The setup chips only report what Start will launch, so Up/Down passes over them;
 * the pointer can still focus one ({@link #selectChip}) to read or open it.
 * Physical/replay input authority remains in InputHandler.
 */
final class TitleHubNavigation {
    enum Zone { TABS, CARD, CHIPS, ACTIONS }

    /** Engine-wide header tabs. Extras appears only when mods register title entries. */
    enum Tab {
        MODS("MODS"), SETTINGS("SETTINGS"), EXTRAS("EXTRAS"), ADVANCED("ADVANCED"), QUIT("QUIT");
        final String label;
        Tab(String label) { this.label = label; }
    }

    /** Per-game actions under the selected card. */
    enum Action {
        START("START"), SETUP("GAME SETUP"), RECORDINGS("RECORDINGS");
        final String label;
        Action(String label) { this.label = label; }
    }

    private final boolean[] previous = new boolean[6];
    private final boolean[] pressed = new boolean[6];
    private Zone zone = Zone.CARD;
    private int tab;
    private int chip;
    private int action;
    private boolean extrasVisible;
    private int chipCount;
    private int actionCount = Action.values().length;

    void capture(InputHandler input) {
        boolean[] current = { MenuInput.left(input), MenuInput.right(input), MenuInput.up(input),
                MenuInput.down(input), MenuInput.accept(input), MenuInput.back(input) };
        for (int i = 0; i < current.length; i++) {
            pressed[i] = current[i] && !previous[i];
            previous[i] = current[i];
        }
    }

    boolean left() { return pressed[0]; }
    boolean right() { return pressed[1]; }
    boolean up() { return pressed[2]; }
    boolean down() { return pressed[3]; }
    boolean accept() { return pressed[4]; }
    boolean back() { return pressed[5]; }

    Zone zone() { return zone; }

    void focus(Zone next) {
        zone = next;
        clampIndices();
    }

    /** Visible tabs in drawing order. */
    Tab[] tabs() {
        return extrasVisible ? Tab.values()
                : new Tab[] { Tab.MODS, Tab.SETTINGS, Tab.ADVANCED, Tab.QUIT };
    }

    int tabIndex() { return tab; }
    Tab tab() { return tabs()[Math.clamp(tab, 0, tabs().length - 1)]; }
    void selectTab(int index) { tab = Math.clamp(index, 0, tabs().length - 1); zone = Zone.TABS; }

    int chipIndex() { return chip; }
    void selectChip(int index) { chip = Math.clamp(index, 0, Math.max(0, chipCount - 1)); zone = Zone.CHIPS; }

    int actionIndex() { return action; }
    Action action() { return Action.values()[Math.clamp(action, 0, actionCount - 1)]; }
    void selectAction(int index) { action = Math.clamp(index, 0, actionCount - 1); zone = Zone.ACTIONS; }

    void setExtrasVisible(boolean visible) {
        Tab current = tab();
        extrasVisible = visible;
        Tab[] tabs = tabs();
        tab = 0;
        for (int i = 0; i < tabs.length; i++) if (tabs[i] == current) tab = i;
    }

    /** Chips and actions depend on the selected game; empty rows are skipped. */
    void setRowSizes(int chips, int actions) {
        chipCount = Math.max(0, chips);
        actionCount = Math.clamp(actions, 1, Action.values().length);
        clampIndices();
    }

    /** Moves focus one row; returns true when the focus changed. */
    boolean moveRow(int delta) {
        Zone[] order = Zone.values();
        int index = zone.ordinal();
        do {
            index += delta;
            if (index < 0 || index >= order.length) return false;
        } while (order[index] == Zone.CHIPS);
        zone = order[index];
        clampIndices();
        return true;
    }

    /** Moves within the current row; returns true when the focus changed. Not used on the card. */
    boolean moveWithin(int delta) {
        return switch (zone) {
            case TABS -> { int next = Math.clamp(tab + delta, 0, tabs().length - 1); boolean m = next != tab; tab = next; yield m; }
            case CHIPS -> { int next = Math.clamp(chip + delta, 0, Math.max(0, chipCount - 1)); boolean m = next != chip; chip = next; yield m; }
            case ACTIONS -> { int next = Math.clamp(action + delta, 0, actionCount - 1); boolean m = next != action; action = next; yield m; }
            case CARD -> false;
        };
    }

    private void clampIndices() {
        tab = Math.clamp(tab, 0, tabs().length - 1);
        chip = Math.clamp(chip, 0, Math.max(0, chipCount - 1));
        action = Math.clamp(action, 0, actionCount - 1);
        if (zone == Zone.CHIPS && chipCount == 0) zone = Zone.CARD;
    }
}
