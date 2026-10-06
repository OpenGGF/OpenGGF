package paradise.model;

import java.util.List;
import java.util.Objects;

/** Mulligan budgets belong to the match, never the physical course checkpoint. */
public final class RewindAllowance {
    public record Rules(int perHole, int perTurn) {
        public static Rules defaults() { return new Rules(3, 1); }
        public static Rules off() { return new Rules(0, 1); }
        public Rules {
            if (perHole != 0 && perHole != 3 && perHole != 5 && perHole != -1
                    || perTurn != 1 && perTurn != 3 && perTurn != -1)
                throw new IllegalArgumentException("Rewinds: hole off/3/5/*, turn 1/3/*");
        }
        public String holeLabel() { return perHole == 0 ? "OFF" : label(perHole); }
        public String turnLabel() { return label(perTurn); }
        public static String label(int value) { return value < 0 ? "*" : Integer.toString(value); }
    }
    public record State(Rules rules, List<Integer> usedPerHole, GolfMatch.ShotId last, int usedPerTurn) {
        public State {
            Objects.requireNonNull(rules); usedPerHole = List.copyOf(usedPerHole);
            if (usedPerHole.size() != 4 || usedPerHole.stream().anyMatch(n -> n < 0) || usedPerTurn < 0)
                throw new IllegalArgumentException("Invalid rewind allowance");
        }
    }
    private State state;
    public RewindAllowance(Rules rules) { state = new State(rules, List.of(0, 0, 0, 0), null, 0); }
    public State snapshot() { return state; }
    public void restore(State saved) { state = Objects.requireNonNull(saved); }
    public int holeRemaining(int player, int act) {
        if (player < 0 || player > 1 || act < 0 || act > 1) throw new IllegalArgumentException("Golfer/hole");
        return remaining(state.rules().perHole(), state.usedPerHole().get(act * 2 + player));
    }
    public int turnRemaining(GolfMatch.ShotId id) {
        return remaining(state.rules().perTurn(), sameTurn(state.last(), id) ? state.usedPerTurn() : 0);
    }
    public boolean available(GolfMatch.ShotId id) {
        return holeRemaining(id.player(), id.actIndex()) != 0 && turnRemaining(id) != 0;
    }
    /** The caller validates current shot ownership. Stale/duplicate IDs cannot spend twice. */
    public boolean spend(GolfMatch.ShotId id) {
        Objects.requireNonNull(id);
        if (state.last() != null && id.shotSequence() <= state.last().shotSequence() || !available(id)) return false;
        var used = new java.util.ArrayList<>(state.usedPerHole());
        int index = id.actIndex() * 2 + id.player(); used.set(index, Math.incrementExact(used.get(index)));
        state = new State(state.rules(), used, id, sameTurn(state.last(), id) ? Math.incrementExact(state.usedPerTurn()) : 1);
        return true;
    }
    private static boolean sameTurn(GolfMatch.ShotId a, GolfMatch.ShotId b) {
        return a != null && a.actIndex() == b.actIndex() && a.turnSequence() == b.turnSequence() && a.player() == b.player();
    }
    private static int remaining(int limit, int used) { return limit < 0 ? -1 : Math.max(0, limit - used); }
    /** Whole shot in at most 90 presentation ticks; longer recordings traverse more source ticks. */
    public static int replaySpeed(int shotTicks) { return Math.max(1, (Math.max(0, shotTicks) + 89) / 90); }
}
