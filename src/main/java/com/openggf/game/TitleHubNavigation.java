package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;

/** Two explicit focus panes; physical/replay input authority remains in InputHandler. */
final class TitleHubNavigation {
    enum Action {
        START("START GAME"), LAUNCH("LAUNCH OPTIONS"),
        RECORDINGS("RECORDINGS"), MODS("MODS"), SETTINGS("SETTINGS"), TOOLS("ADVANCED"), QUIT("QUIT"),
        /** Mod-contributed title entries; shown only when at least one is registered. */
        EXTRAS("EXTRAS");
        final String label;
        Action(String label) { this.label = label; }
    }

    private final boolean[] previous = new boolean[6];
    private final boolean[] pressed = new boolean[6];
    private boolean actions;
    private int selected;
    private int count = Action.values().length - 1;

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
    boolean actions() { return actions; }
    int selected() { return selected; }
    Action action() { return Action.values()[selected]; }
    void enter() { actions = true; selected = 0; }
    /** Number of visible actions; {@link Action#EXTRAS} is last and hidden without entries. */
    int count() { return count; }
    void setExtrasVisible(boolean visible) {
        count = Action.values().length - (visible ? 0 : 1);
        selected = Math.min(selected, count - 1);
    }
    void leave() { actions = false; }
    boolean move(int delta) {
        int next = Math.clamp(selected + delta, 0, count - 1);
        boolean moved = next != selected;
        selected = next;
        return moved;
    }
}
