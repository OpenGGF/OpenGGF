package com.openggf.game.sonic3k.titlescreen;

/** Module-lived Title_screen_option; a warm title entry does not clear this native byte. */
public final class Sonic3kTitleState {
    private int option;

    public int option() { return option; }
    public void setOption(int value) { option = value & 0xFF; }
    /** Obj_SKTitle_Icon normalizes the retained byte when the icon initializes. */
    void enterStandaloneMenu() { option &= 1; }
    public Snapshot capture() { return new Snapshot(option); }
    public void apply(Snapshot state) { option = java.util.Objects.requireNonNull(state).option() & 0xFF; }
    public record Snapshot(int option) { }
}
