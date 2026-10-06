package paradise.model;

import java.util.Objects;

/**
 * Incoming-golfer readiness at a competitive turn handoff. The outgoing shot settles and
 * passes the turn automatically; only the next golfer's first input waits here.
 * <p>
 * A confirmation needs a fresh A press with every button released since the handoff, so an
 * A held across the handoff can neither confirm nor start the next shot. The host owns the
 * transition: a guest press only asks ({@link Phase#REQUESTED}) until the host accepts.
 * A rewind retry keeps its turn and therefore its confirmed readiness.
 */
public final class TurnReadiness {
    public enum Phase { NONE, WAITING, REQUESTED }
    public record State(Phase phase, boolean armed, int lastOwner, int owner, boolean firstOfHole) {
        public State {
            Objects.requireNonNull(phase);
            if (lastOwner < -1 || lastOwner > 1 || owner < -1 || owner > 1) throw new IllegalArgumentException("readiness owner");
            if (phase != Phase.NONE && owner < 0) throw new IllegalArgumentException("waiting readiness needs an owner");
        }
    }

    private State state = new State(Phase.NONE, false, -1, -1, false);

    public State snapshot() { return state; }
    public void restore(State saved) { state = Objects.requireNonNull(saved); }
    public boolean waiting() { return state.phase() != Phase.NONE; }
    public boolean requested() { return state.phase() == Phase.REQUESTED; }
    public int owner() { return state.owner(); }
    public boolean firstOfHole() { return state.firstOfHole(); }

    /** A new hole has no previous golfer, so its first turn always introduces one. */
    public void newHole() { state = new State(Phase.NONE, false, -1, -1, false); }

    /**
     * Opens a turn. Competition waits when the golfer differs from the previous turn's;
     * a same-golfer turn (retry, or the only golfer left) never waits. actionHeld reports
     * whether a button was held on the row that opened the turn: such a press must be
     * released before it can confirm. Returns true when waiting.
     */
    public boolean open(int owner, boolean handoffs, boolean actionHeld) {
        if (owner < 0 || owner > 1) throw new IllegalArgumentException("owner");
        boolean wait = handoffs && owner != state.lastOwner();
        state = new State(wait ? Phase.WAITING : Phase.NONE, wait && !actionHeld, owner, owner, wait && state.lastOwner() < 0);
        return wait;
    }

    /** Pause, rejected requests and reconnects require another release before confirming. */
    public void disarm() { state = new State(state.phase(), false, state.lastOwner(), state.owner(), state.firstOfHole()); }

    /**
     * Advances one input row for the incoming golfer. Returns true exactly once, on the fresh
     * press that confirms; the caller then accepts it (host) or requests acceptance (guest).
     */
    public boolean press(boolean anyActionHeld, boolean aPressed) {
        if (state.phase() != Phase.WAITING) return false;
        if (!state.armed()) {
            if (!anyActionHeld) state = new State(Phase.WAITING, true, state.lastOwner(), state.owner(), state.firstOfHole());
            return false;
        }
        if (!aPressed) return false;
        state = new State(Phase.REQUESTED, false, state.lastOwner(), state.owner(), state.firstOfHole());
        return true;
    }

    /** Authoritative acceptance. Idempotent: an already-ready turn stays ready. */
    public void accept() { state = new State(Phase.NONE, false, state.lastOwner(), state.owner(), state.firstOfHole()); }

    /** The host answered a request with a still-waiting status; ask for a new fresh press. */
    public void reopen() {
        if (state.phase() == Phase.REQUESTED)
            state = new State(Phase.WAITING, false, state.lastOwner(), state.owner(), state.firstOfHole());
    }

    /** Guest: adopts the host's newly opened turn and whether the host is holding it for readiness. */
    public void adopt(int owner, boolean waiting, boolean firstOfHole, boolean actionHeld) {
        if (owner < 0 || owner > 1) throw new IllegalArgumentException("owner");
        state = new State(waiting ? Phase.WAITING : Phase.NONE, waiting && !actionHeld, owner, owner, waiting && firstOfHole);
    }
}
