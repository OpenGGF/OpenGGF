package slaytherobotnik.run;

/**
 * A treasure node: an item capsule to open. Its size decides the relic odds, as with Slay
 * the Spire's small, medium and large chests.
 */
public final class TreasureRoom implements Room {
    public static final String SMALL = "Small";
    public static final String MEDIUM = "Medium";
    public static final String LARGE = "Large";

    private final Run run;
    private final String size;
    private boolean opened;

    TreasureRoom(Run run, String size) {
        this.run = run;
        this.size = size;
    }

    public String size() { return size; }
    public boolean opened() { return opened; }

    /** Opens the capsule and shows its rewards. */
    public void open() {
        if (!opened) {
            opened = true;
            run.openTreasure(this);
        }
    }

    /** Leaves without opening. */
    public void skip() {
        run.leaveRoom();
    }
}
