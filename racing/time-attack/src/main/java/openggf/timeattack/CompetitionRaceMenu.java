package openggf.timeattack;

/** Non-pausing race-menu model; the runtime keeps executing steps while it is open. */
public final class CompetitionRaceMenu {
    public enum Action { LEAVE, CHAT }
    private boolean open;
    private Action selected = Action.LEAVE;
    public boolean open() { return open; }
    public Action selected() { return selected; }
    public void toggle() { open = !open; }
    public void close() { open = false; }
    public void move() { selected = selected == Action.LEAVE ? Action.CHAT : Action.LEAVE; }
}
