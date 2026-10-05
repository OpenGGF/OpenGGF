package paradise.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Host/offline match ledger, separate from physical course state. Player/act indexes are zero-based.
 * A COMMIT event calls commit; one completed physics observation calls resolve. The adapter saves
 * or restores its course checkpoint according to the accepted outcome, then opens the next turn.
 * Only the pending and most recent resolved shot are retained for bounded idempotent receipts.
 */
public final class GolfMatch {
    public enum Character { SONIC, TAILS }
    public enum Mode { PRACTICE, COMPETITION }
    public enum Status { PLAYING, COMPLETE, CONCEDED, ABANDONED }
    public enum Decision { ACCEPTED, DUPLICATE, REJECTED }

    public record ShotId(int actIndex, long turnSequence, int player, long shotSequence) {
        public ShotId {
            if (actIndex < 0 || actIndex > 1 || turnSequence < 1 || player < 0 || player > 1 || shotSequence < 1) {
                throw new IllegalArgumentException("Invalid shot identity");
            }
        }
    }

    public record HoleScore(int strokes, int penalties, boolean finished, boolean dnf) {
        public HoleScore {
            if (strokes < 0 || penalties < 0 || penalties > strokes || (finished && dnf)) {
                throw new IllegalArgumentException("Invalid hole score");
            }
        }
        public int total() { return Math.addExact(strokes, penalties); }
    }

    public record Golfer(Character character, List<HoleScore> holes, boolean dnf) {
        public Golfer(Character character, List<HoleScore> holes) { this(character, holes, false); }
        public Golfer {
            Objects.requireNonNull(character, "character");
            holes = List.copyOf(holes);
            if (holes.size() != 2) throw new IllegalArgumentException("Two act score slots required");
        }
        public int total() { return Math.addExact(holes.get(0).total(), holes.get(1).total()); }
    }

    public record PendingShot(ShotId id, GolfShot shot) {
        public PendingShot { Objects.requireNonNull(id, "id"); Objects.requireNonNull(shot, "shot"); }
    }

    public record ResolvedShot(ShotId id, GolfShot shot, GolfOutcome outcome) {
        public ResolvedShot {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(shot, "shot"); Objects.requireNonNull(outcome, "outcome");
            if (outcome == GolfOutcome.NONE) throw new IllegalArgumentException("Resolved shot requires terminal outcome");
        }
    }

    /** winner is -1 for a draw/no result, otherwise the winning player index. */
    public record State(Mode mode, List<Golfer> golfers, int initialActIndex, int actIndex, int activePlayer,
                        long turnSequence, long nextShotSequence, PendingShot pending, ResolvedShot lastResolved,
                        Status status, int winner) {
        public State {
            Objects.requireNonNull(mode, "mode"); Objects.requireNonNull(status, "status");
            golfers = List.copyOf(golfers);
            int count = mode == Mode.PRACTICE ? 1 : 2;
            if (golfers.size() != count || initialActIndex < 0 || initialActIndex > 1
                    || actIndex < initialActIndex || actIndex > 1 || activePlayer < 0 || activePlayer >= count
                    || turnSequence < 1 || nextShotSequence < 1 || winner < -1 || winner >= count) {
                throw new IllegalArgumentException("Invalid match state");
            }
            if (mode == Mode.COMPETITION && initialActIndex != 0) throw new IllegalArgumentException("Competition starts at EHZ1");
            if (mode == Mode.PRACTICE && actIndex != initialActIndex) throw new IllegalArgumentException("Practice has one hole");
            if ((status == Status.PLAYING || status == Status.ABANDONED) && winner != -1) {
                throw new IllegalArgumentException("Unfinished/abandoned match has no winner");
            }
            if (status != Status.PLAYING && pending != null) throw new IllegalArgumentException("Closed match has no pending shot");
            if (pending != null && (pending.id().actIndex() != actIndex || pending.id().player() != activePlayer
                    || pending.id().turnSequence() != turnSequence || pending.id().shotSequence() != nextShotSequence - 1)) {
                throw new IllegalArgumentException("Pending shot is not this turn's committed shot");
            }
            if (lastResolved != null && (lastResolved.id().shotSequence() >= nextShotSequence
                    || lastResolved.id().player() >= count)) throw new IllegalArgumentException("Invalid cached receipt");
        }
    }

    /** holeAdvanced is true only on the original accepted EHZ1-to-EHZ2 transition. */
    public record Resolution(Decision decision, GolfOutcome outcome, boolean holeAdvanced) { }

    private static final HoleScore EMPTY_SCORE = new HoleScore(0, 0, false, false);
    private State state;

    private GolfMatch(Mode mode, List<Character> characters, int actIndex) {
        var golfers = characters.stream().map(c -> new Golfer(c, List.of(EMPTY_SCORE, EMPTY_SCORE))).toList();
        state = new State(mode, golfers, actIndex, actIndex, 0, 1, 1, null, null, Status.PLAYING, -1);
    }

    public static GolfMatch practice(Character character, int actIndex) {
        return new GolfMatch(Mode.PRACTICE, List.of(character), actIndex);
    }

    public static GolfMatch competition(Character playerOne, Character playerTwo) {
        return new GolfMatch(Mode.COMPETITION, List.of(playerOne, playerTwo), 0);
    }

    public State snapshot() { return state; }
    public void restore(State saved) { state = Objects.requireNonNull(saved, "saved"); }
    public ShotId nextShotId() {
        return new ShotId(state.actIndex(), state.turnSequence(), state.activePlayer(), state.nextShotSequence());
    }

    /** Rejects stale/future ownership and conflicting retransmissions without changing the ledger. */
    public Decision commit(ShotId id, GolfShot shot) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(shot, "shot");
        if (state.pending() != null && state.pending().id().equals(id)) {
            return state.pending().shot().equals(shot) ? Decision.DUPLICATE : Decision.REJECTED;
        }
        if (state.lastResolved() != null && state.lastResolved().id().equals(id)) {
            return state.lastResolved().shot().equals(shot) ? Decision.DUPLICATE : Decision.REJECTED;
        }
        if (state.status() != Status.PLAYING || state.pending() != null || !nextShotId().equals(id)) return Decision.REJECTED;
        HoleScore before = activeScore();
        var golfers = withScore(state.activePlayer(), new HoleScore(Math.incrementExact(before.strokes()),
                before.penalties(), before.finished(), before.dnf()));
        state = new State(state.mode(), golfers, state.initialActIndex(), state.actIndex(), state.activePlayer(),
                state.turnSequence(), Math.incrementExact(state.nextShotSequence()), new PendingShot(id, shot),
                state.lastResolved(), state.status(), state.winner());
        return Decision.ACCEPTED;
    }

    /** NONE leaves a committed shot in flight; compound terminal candidates are arbitrated exactly once. */
    public Resolution resolve(ShotId id, GolfOutcome.Candidates candidates) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(candidates, "candidates");
        if (state.lastResolved() != null && state.lastResolved().id().equals(id)) {
            return new Resolution(Decision.DUPLICATE, state.lastResolved().outcome(), false);
        }
        if (state.status() != Status.PLAYING || state.pending() == null || !state.pending().id().equals(id)) {
            return new Resolution(Decision.REJECTED, GolfOutcome.NONE, false);
        }
        GolfOutcome outcome = GolfOutcome.choose(candidates);
        if (outcome == GolfOutcome.NONE) return new Resolution(Decision.REJECTED, GolfOutcome.NONE, false);
        HoleScore before = activeScore();
        var golfers = withScore(state.activePlayer(), new HoleScore(before.strokes(),
                outcome.isPenalty() ? Math.incrementExact(before.penalties()) : before.penalties(),
                outcome == GolfOutcome.FINISH, false));
        int act = state.actIndex();
        int active = state.activePlayer();
        Status status = Status.PLAYING;
        int winner = -1;
        boolean holeAdvanced = false;
        final int currentAct = act;
        boolean allFinished = golfers.stream().allMatch(g -> g.holes().get(currentAct).finished());
        if (allFinished) {
            if (state.mode() == Mode.COMPETITION && act == 0) {
                act = 1; active = 1; holeAdvanced = true;
            } else {
                status = Status.COMPLETE;
                if (state.mode() == Mode.PRACTICE) winner = 0;
                else winner = Integer.compare(golfers.get(0).total(), golfers.get(1).total()) < 0 ? 0
                        : Integer.compare(golfers.get(0).total(), golfers.get(1).total()) > 0 ? 1 : -1;
            }
        } else if (state.mode() == Mode.COMPETITION) {
            int other = 1 - active;
            if (!golfers.get(other).holes().get(act).finished()) active = other;
        }
        ResolvedShot receipt = new ResolvedShot(id, state.pending().shot(), outcome);
        state = new State(state.mode(), golfers, state.initialActIndex(), act, active,
                Math.incrementExact(state.turnSequence()), state.nextShotSequence(), null, receipt, status, winner);
        return new Resolution(Decision.ACCEPTED, outcome, holeAdvanced);
    }

    /** Conceding a competitive hole ends the match as DNF; it never fabricates a stroke total. */
    public Decision concede(int player) {
        if (state.status() != Status.PLAYING || player < 0 || player >= state.golfers().size()) return Decision.REJECTED;
        HoleScore before = state.golfers().get(player).holes().get(state.actIndex());
        var golfers = new ArrayList<>(withScore(player, new HoleScore(before.strokes(), before.penalties(),
                before.finished(), !before.finished())));
        Golfer conceding = golfers.get(player);
        golfers.set(player, new Golfer(conceding.character(), conceding.holes(), true));
        state = new State(state.mode(), golfers, state.initialActIndex(), state.actIndex(), state.activePlayer(),
                state.turnSequence(), state.nextShotSequence(), null, state.lastResolved(), Status.CONCEDED,
                state.mode() == Mode.COMPETITION ? 1 - player : -1);
        return Decision.ACCEPTED;
    }

    /** Both leaving without a result records no winner; it cannot overwrite an established result. */
    public void abandon() {
        if (state.status() != Status.PLAYING) return;
        state = new State(state.mode(), state.golfers(), state.initialActIndex(), state.actIndex(), state.activePlayer(),
                state.turnSequence(), state.nextShotSequence(), null, state.lastResolved(), Status.ABANDONED, -1);
    }

    private HoleScore activeScore() { return state.golfers().get(state.activePlayer()).holes().get(state.actIndex()); }

    private List<Golfer> withScore(int player, HoleScore score) {
        var golfers = new ArrayList<>(state.golfers());
        Golfer golfer = golfers.get(player);
        var holes = new ArrayList<>(golfer.holes());
        holes.set(state.actIndex(), score);
        golfers.set(player, new Golfer(golfer.character(), holes, golfer.dnf()));
        return List.copyOf(golfers);
    }
}
