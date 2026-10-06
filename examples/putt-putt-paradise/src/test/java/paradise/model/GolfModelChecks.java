package paradise.model;

import java.util.ArrayList;
import java.util.List;

/** Engine-free regression scenarios invoked against separately compiled example source. */
public final class GolfModelChecks {
    private static final ShotMeter.Input NONE = new ShotMeter.Input(false, false, false, false, false, false);
    private static final ShotMeter.Input A = new ShotMeter.Input(false, false, false, false, true, false);
    private static final ShotMeter.Input B = new ShotMeter.Input(false, false, false, false, false, true);
    private static final GolfShot SHOT = new GolfShot(1, 0, 250, 250);
    private static final GolfOutcome.Candidates FINISH = new GolfOutcome.Candidates(false, false, true, false, false, false);
    private static final GolfOutcome.Candidates SETTLE = new GolfOutcome.Candidates(false, false, false, true, false, false);

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void same(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
    private static void idle(ShotMeter meter, int ticks) { for (int i = 0; i < ticks; i++) meter.tick(NONE); }
    private static List<ShotMeter.Event> press(ShotMeter meter) { return meter.tick(A); }
    private static long count(List<ShotMeter.Event> events, ShotMeter.Kind kind) {
        return events.stream().filter(e -> e.kind() == kind).count();
    }

    public static void bothCharges() {
        var meter = new ShotMeter();
        same(1L, count(press(meter), ShotMeter.Kind.DUCK), "first A must duck, not charge");
        idle(meter, 60);
        same(1L, count(press(meter), ShotMeter.Kind.CHARGE), "first timed A charges");
        idle(meter, 30);
        var events = press(meter);
        same(1L, count(events, ShotMeter.Kind.CHARGE), "second timed A charges");
        same(1L, count(events, ShotMeter.Kind.COMMIT), "second timed A commits");
        GolfShot shot = meter.snapshot().shot();
        same(500, shot.firstCharge(), "peak first contribution");
        same(250, shot.secondCharge(), "half-height second contribution");
        same(750, shot.normalizedPower(), "both power-building hits contribute");
        require(new GolfShot(1, 0, 0, 0).speedFixed() < 0x200, "precision putt must be slower than native spindash minimum");
        require(new GolfShot(1, 0, 500, 500).speedFixed() >= 0xA00, "full power must have useful loop speed");
        require(new GolfShot(1, 0, 400, 500).speedFixed() < new GolfShot(1, 0, 500, 500).speedFixed(), "power must be monotonic");
    }

    public static void buttonEdges() {
        var meter = new ShotMeter();
        press(meter);
        for (int i = 0; i < 200; i++) require(meter.tick(A).isEmpty(), "held A cannot charge");
        same(ShotMeter.Stage.FIRST_CHARGE, meter.snapshot().stage(), "held A stays at first charge");
        meter.tick(NONE); press(meter); meter.tick(NONE);
        var events = new ArrayList<>(press(meter));
        require(events.stream().noneMatch(e -> e.kind() == ShotMeter.Kind.RELEASE), "commit cannot launch immediately");
        for (int i = 0; i < 120; i++) events.addAll(meter.tick(i % 2 == 0 ? B : A));
        same(1L, count(events, ShotMeter.Kind.COMMIT), "extra A cannot double commit");
        same(1L, count(events, ShotMeter.Kind.RELEASE), "automatic release exactly once");
        same(0L, count(events, ShotMeter.Kind.CANCEL), "postcommit B is ignored");
        same(ShotMeter.Stage.WATCH, meter.snapshot().stage(), "automatically watching without launch input");
    }

    public static void freeCancel() {
        var meter = new ShotMeter();
        press(meter); idle(meter, 60); press(meter); meter.tick(NONE);
        same(1L, count(meter.tick(B), ShotMeter.Kind.CANCEL), "cancel second meter before commitment");
        same(ShotMeter.Stage.AIM, meter.snapshot().stage(), "back to aim");
        same(0, meter.snapshot().firstCharge(), "previous power cleared");
        require(meter.snapshot().shot() == null, "cancel does not seal a shot");
        meter.tick(NONE); press(meter); meter.tick(NONE);
        same(1L, count(press(meter), ShotMeter.Kind.CHARGE), "fresh first charge");
        same(ShotMeter.Stage.SECOND_CHARGE, meter.snapshot().stage(), "fresh shot still needs second charge");
    }

    public static void meterReplay() {
        var meter = new ShotMeter();
        press(meter); idle(meter, 20); press(meter); idle(meter, 15);
        ShotMeter.State saved = meter.snapshot();
        var suffix = new ArrayList<ShotMeter.Input>();
        suffix.add(A);
        for (int i = 0; i < 100; i++) suffix.add(NONE);
        var expected = new ArrayList<ShotMeter.Event>();
        for (var input : suffix) expected.addAll(meter.tick(input));
        var expectedState = meter.snapshot();
        meter.restore(saved);
        var actual = new ArrayList<ShotMeter.Event>();
        for (var input : suffix) actual.addAll(meter.tick(input));
        same(expected, actual, "midcharge replay event ordering");
        same(expectedState, meter.snapshot(), "midcharge replay complete state");
        require(saved.shot() == null && saved.stage() == ShotMeter.Stage.SECOND_CHARGE, "snapshot cannot alias live state");
        var held = new ShotMeter(); press(held);
        var heldState = held.snapshot();
        held.tick(NONE); press(held); held.restore(heldState);
        require(held.tick(A).isEmpty(), "restored held A cannot become a new edge");
    }

    public static void powerFeedback() {
        long previous = -1;
        for (int ticks : new int[]{1,15,30,45,60}) {
            var meter = new ShotMeter();
            press(meter); idle(meter,ticks); press(meter); idle(meter,ticks); press(meter);
            var saved = meter.snapshot();
            var events = new ArrayList<ShotMeter.Event>();
            for (int i=0;i<GolfRules.CHARGE_FEEDBACK_TICKS + GolfRules.PRE_RELEASE_TICKS;i++)
                events.addAll(meter.tick(NONE));
            long charges = count(events, ShotMeter.Kind.CHARGE);
            require(charges > previous, "more power produces more native charge requests"); previous = charges;
            same(1L, count(events, ShotMeter.Kind.RELEASE), "fixed automatic release after feedback and pause");
            meter.restore(saved);
            var replay = new ArrayList<ShotMeter.Event>();
            for (int i=0;i<GolfRules.CHARGE_FEEDBACK_TICKS + GolfRules.PRE_RELEASE_TICKS;i++)
                replay.addAll(meter.tick(NONE));
            same(events, replay, "feedback restore retains charge cadence");
        }
    }

    public static void aimLocks() {
        var meter = new ShotMeter();
        var upLeft = new ShotMeter.Input(true, false, true, false, false, false);
        for (int i = 0; i < 1000; i++) meter.tick(upLeft);
        same(90, meter.snapshot().elevationDegrees(), "elevation reaches vertical without wrapping");
        same(-1, meter.snapshot().direction(), "left changes facing");
        press(meter);
        for (int i = 0; i < 100; i++) meter.tick(new ShotMeter.Input(false, true, false, true, false, false));
        same(90, meter.snapshot().elevationDegrees(), "first A locks elevation");
        same(-1, meter.snapshot().direction(), "first A locks facing");
        meter.tick(B); meter.tick(NONE);
        for (int i = 0; i < 1000; i++) meter.tick(new ShotMeter.Input(false, true, false, true, false, false));
        same(0, meter.snapshot().elevationDegrees(), "down returns to putt without wrapping");
        same(1, meter.snapshot().direction(), "right faces right");
    }

    private static GolfMatch.Resolution play(GolfMatch match, GolfOutcome.Candidates candidates) {
        var id = match.nextShotId();
        same(GolfMatch.Decision.ACCEPTED, match.commit(id, SHOT), "valid shot commit");
        var result = match.resolve(id, candidates);
        same(GolfMatch.Decision.ACCEPTED, result.decision(), "valid result");
        return result;
    }

    public static void pairings() {
        for (var first : GolfMatch.Character.values()) for (var second : GolfMatch.Character.values()) {
            var match = GolfMatch.competition(first, second);
            same(0, match.snapshot().activePlayer(), "P1 starts act 1");
            same(first, match.snapshot().golfers().get(0).character(), "P1 character");
            same(second, match.snapshot().golfers().get(1).character(), "P2 character");
            play(match, SETTLE); same(1, match.snapshot().activePlayer(), "alternate to P2");
            play(match, SETTLE); same(0, match.snapshot().activePlayer(), "alternate to P1");
            play(match, FINISH); same(1, match.snapshot().activePlayer(), "unfinished P2 continues");
            var result = play(match, FINISH);
            require(result.holeAdvanced(), "both finished advance the act");
            same(1, match.snapshot().actIndex(), "full EHZ2 selected");
            same(1, match.snapshot().activePlayer(), "P2 starts act 2");
            play(match, FINISH); same(0, match.snapshot().activePlayer(), "P1 finishes act 2");
            play(match, FINISH);
            same(GolfMatch.Status.COMPLETE, match.snapshot().status(), "two holes complete");
            same(-1, match.snapshot().winner(), "equal summed scores draw");
            same(3, match.snapshot().golfers().get(0).total(), "both acts summed");
            same(3, match.snapshot().golfers().get(1).total(), "both acts summed for P2");
        }
    }

    public static void penaltiesAndDuplicates() {
        var match = GolfMatch.competition(GolfMatch.Character.SONIC, GolfMatch.Character.TAILS);
        var id = match.nextShotId();
        same(GolfMatch.Decision.ACCEPTED, match.commit(id, SHOT), "first commit");
        same(GolfMatch.Decision.DUPLICATE, match.commit(id, SHOT), "repeat commit does not increment strokes");
        same(GolfMatch.Decision.REJECTED, match.commit(id, new GolfShot(-1, 0, 250, 250)), "conflicting payload rejected");
        var all = new GolfOutcome.Candidates(true, true, true, true, true, true);
        same(GolfOutcome.DAMAGE, match.resolve(id, all).outcome(), "damage takes priority over finish");
        var score = match.snapshot().golfers().get(0).holes().get(0);
        same(1, score.strokes(), "one committed shot"); same(1, score.penalties(), "one penalty");
        require(!score.finished(), "damage cannot finish hole");
        same(1, match.snapshot().activePlayer(), "penalty ends turn");
        same(GolfMatch.Decision.DUPLICATE, match.resolve(id, FINISH).decision(), "result retry returns receipt");
        same(GolfOutcome.DAMAGE, match.resolve(id, FINISH).outcome(), "result retry cannot rewrite outcome");
        same(GolfMatch.Decision.DUPLICATE, match.commit(id, SHOT), "late commit returns prior receipt");
        same(score, match.snapshot().golfers().get(0).holes().get(0), "duplicate cannot alter score");
        same(GolfOutcome.FINISH, GolfOutcome.choose(new GolfOutcome.Candidates(false, false, true, true, true, true)), "finish beats settlement");
        same(GolfOutcome.SETTLED, GolfOutcome.choose(new GolfOutcome.Candidates(false, false, false, true, true, true)), "settlement beats lost ball");
        same(GolfOutcome.LOST_BALL, GolfOutcome.choose(new GolfOutcome.Candidates(false, false, false, false, true, true)), "explicit lost ball beats watchdog");
        same(GolfOutcome.NONE, GolfOutcome.choose(new GolfOutcome.Candidates(false, false, false, false, false, false)), "attack/pickup alone is nonterminal");
        same(GolfMatch.Decision.REJECTED, match.resolve(match.nextShotId(), SETTLE).decision(), "uncommitted result rejected");
    }

    public static void scoring() {
        var match = GolfMatch.competition(GolfMatch.Character.SONIC, GolfMatch.Character.SONIC);
        play(match, FINISH); play(match, SETTLE);
        same(1, match.snapshot().activePlayer(), "finished P1 skipped");
        play(match, FINISH); play(match, FINISH); play(match, FINISH);
        same(0, match.snapshot().winner(), "fewest summed strokes wins");
        same(2, match.snapshot().golfers().get(0).total(), "P1 scores both acts");
        same(3, match.snapshot().golfers().get(1).total(), "P2 extra stroke counts");
        require(match.commit(match.nextShotId(), SHOT) == GolfMatch.Decision.REJECTED, "cannot play after results");
    }

    public static void concession() {
        var match = GolfMatch.competition(GolfMatch.Character.TAILS, GolfMatch.Character.SONIC);
        same(GolfMatch.Decision.ACCEPTED, match.concede(0), "P1 concedes");
        same(GolfMatch.Status.CONCEDED, match.snapshot().status(), "DNF status");
        same(1, match.snapshot().winner(), "opponent wins even without score");
        require(match.snapshot().golfers().get(0).holes().get(0).dnf(), "DNF recorded");
        same(0, match.snapshot().golfers().get(0).total(), "no invented score");
        same(GolfMatch.Decision.REJECTED, match.concede(1), "cannot replace established winner");
        var abandoned = GolfMatch.competition(GolfMatch.Character.SONIC, GolfMatch.Character.TAILS);
        abandoned.abandon(); same(GolfMatch.Status.ABANDONED, abandoned.snapshot().status(), "room abandoned");
        same(-1, abandoned.snapshot().winner(), "both leaving does not award winner");
    }

    public static void finishedConcession() {
        var match = GolfMatch.competition(GolfMatch.Character.SONIC, GolfMatch.Character.TAILS);
        play(match, FINISH);
        same(GolfMatch.Decision.ACCEPTED, match.concede(0), "finished golfer may leave/concede match");
        same(1, match.snapshot().winner(), "remaining golfer wins by concession");
        var score = match.snapshot().golfers().get(0).holes().get(0);
        require(score.finished() && score.strokes() == 1, "concession must preserve completed hole score");
        require(match.snapshot().golfers().get(0).dnf(), "match-level DNF survives completed hole");
    }

    public static void otherPenalties() {
        for (var candidate : List.of(new GolfOutcome.Candidates(false, false, false, false, true, false),
                new GolfOutcome.Candidates(false, false, false, false, false, true),
                new GolfOutcome.Candidates(false, true, false, false, false, false))) {
            var match = GolfMatch.competition(GolfMatch.Character.SONIC, GolfMatch.Character.TAILS);
            play(match, candidate);
            same(2, match.snapshot().golfers().get(0).total(), "one shot plus one penalty");
            same(1, match.snapshot().activePlayer(), "penalty ends turn");
        }
    }

    public static void invalidTurns() {
        var match = GolfMatch.competition(GolfMatch.Character.SONIC, GolfMatch.Character.TAILS);
        var saved = match.snapshot();
        var correct = match.nextShotId();
        same(GolfMatch.Decision.REJECTED, match.commit(new GolfMatch.ShotId(0, 1, 1, 1), SHOT), "wrong player rejected");
        same(GolfMatch.Decision.REJECTED, match.commit(new GolfMatch.ShotId(0, 2, 0, 1), SHOT), "future turn rejected");
        same(GolfMatch.Decision.REJECTED, match.commit(new GolfMatch.ShotId(1, 1, 0, 1), SHOT), "wrong act rejected");
        same(saved, match.snapshot(), "invalid commands cannot mutate ledger");
        match.commit(correct, SHOT);
        var pending = match.snapshot();
        same(GolfMatch.Decision.REJECTED, match.resolve(correct,
                new GolfOutcome.Candidates(false, false, false, false, false, false)).decision(), "nonterminal shot stays in flight");
        same(pending, match.snapshot(), "attack/pickup cannot advance turn");
        match.resolve(correct, SETTLE); play(match, SETTLE);
        same(GolfMatch.Decision.REJECTED, match.commit(correct, SHOT), "expired receipt does not accept stale shot");
    }

    public static void practice() {
        var match = GolfMatch.practice(GolfMatch.Character.TAILS, 1);
        same(1, match.snapshot().actIndex(), "practice selects act 2 directly");
        play(match, SETTLE); same(0, match.snapshot().activePlayer(), "practice retains sole player");
        play(match, FINISH); same(GolfMatch.Status.COMPLETE, match.snapshot().status(), "practice finishes selected act");
        same(1, match.snapshot().actIndex(), "practice does not start another act");
        require(match.snapshot().golfers().size() == 1, "no sidekick or second golfer");
    }

    public static void ledgerReplay() {
        var match = GolfMatch.competition(GolfMatch.Character.SONIC, GolfMatch.Character.TAILS);
        var id = match.nextShotId(); match.commit(id, SHOT);
        var saved = match.snapshot();
        var expected = match.resolve(id, FINISH); var expectedState = match.snapshot();
        play(match, SETTLE);
        same(1, saved.golfers().get(0).holes().get(0).strokes(), "saved ledger retains shot");
        require(!saved.golfers().get(0).holes().get(0).finished(), "live resolution cannot mutate snapshot");
        try { saved.golfers().clear(); throw new AssertionError("mutable golfer list"); } catch (UnsupportedOperationException correct) { }
        try { saved.golfers().get(0).holes().clear(); throw new AssertionError("mutable hole list"); } catch (UnsupportedOperationException correct) { }
        match.restore(saved);
        same(expected, match.resolve(id, FINISH), "pending shot replay decision");
        same(expectedState, match.snapshot(), "pending shot replay entire ledger");
    }
}
