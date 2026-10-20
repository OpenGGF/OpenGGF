package sonicbros.ui;

/** One of the four player slots on the character select screen. */
public final class Slot {
    public enum Kind { OFF, HUMAN, CPU }

    public Kind kind = Kind.OFF;
    /** The controller a human uses (see {@link sonicbros.input.Controls}). */
    public String device;
    /** Roster index, {@link #RANDOM}, or {@link #NONE} while a human is still choosing. */
    public int fighter = NONE;
    public int costume;
    public int cpuLevel = 5;
    public int team;
    /** Human cursor position on the select screen. */
    public double cursorX;
    public double cursorY;
    /** Stocks for this fighter when not 0 (Classic's handicap rounds); otherwise the rules decide. */
    public int stocks;
    /** The fighter actually used after resolving random, for this match. */
    public int resolved;

    public static final int NONE = -1;
    public static final int RANDOM = -2;

    public boolean active() { return kind != Kind.OFF; }

    public boolean chosen() { return kind == Kind.CPU || kind == Kind.HUMAN && fighter != NONE; }
}
