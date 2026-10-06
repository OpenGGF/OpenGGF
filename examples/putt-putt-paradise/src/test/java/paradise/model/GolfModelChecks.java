package paradise.model;

import java.util.ArrayList;
import java.util.List;

/** Engine-free regression scenarios invoked against separately compiled example source. */
public final class GolfModelChecks {
    private static final ShotMeter.Input NONE = new ShotMeter.Input(false, false, false, false, false, false);
    private static final ShotMeter.Input A = new ShotMeter.Input(false, false, false, false, true, false);
    private static final ShotMeter.Input B = new ShotMeter.Input(false, false, false, false, false, true);
    private static final GolfShot SHOT = new GolfShot(1, 0, 500, 0);
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

    private static ShotMeter chip() {
        var meter = new ShotMeter();
        meter.tick(new ShotMeter.Input(true, false, false, false, false, false));
        press(meter); return meter;
    }

    public static void puttTiming() {
        same(32, GolfRules.powerAtPhase(2), "light shots preserve the existing native power granularity");
        same(332, GolfRules.powerAtPhase(20), "single sweep preserves intermediate shot strength");
        var meter = new ShotMeter();
        same(1L, count(press(meter), ShotMeter.Kind.DUCK), "A opens the shot panel");
        same(ShotMeter.Stage.POWER, meter.snapshot().stage(), "putt starts its single power sweep");
        idle(meter, 60);
        var events = press(meter);
        same(1L, count(events, ShotMeter.Kind.COMMIT), "next A seals the shot");
        same(1000, meter.snapshot().shot().normalizedPower(), "peak power follows the full-power guide");
        same(0, meter.snapshot().shot().spin(), "putts have no vertical spin stage");
        require(new GolfShot(1, 0, 0, 0).speedFixed() < 0x200, "very light finishing putt");
    }

    public static void chipTiming() {
        for (int ticks : new int[]{30, 60, 90}) {
            var meter = chip();
            same(ShotMeter.Stage.SPIN, meter.snapshot().stage(), "chip opens spin marker");
            idle(meter, ticks);
            var spinEvents = press(meter);
            same(1L, count(spinEvents, ShotMeter.Kind.CHARGE), "stopping spin requests native charge");
            same(0L, count(spinEvents, ShotMeter.Kind.COMMIT), "spin alone cannot seal a stroke");
            int expected = ticks == 30 ? 100 : ticks == 60 ? 0 : -100;
            same(expected, meter.snapshot().spin(), "top, neutral and back contact points");
            same(ShotMeter.Stage.POWER, meter.snapshot().stage(), "spin leads to power");
            idle(meter, 45); press(meter);
            same(750, meter.snapshot().shot().normalizedPower(), "power is independent of spin");
            same(expected, meter.snapshot().shot().spin(), "power hit preserves spin");
            same(1, meter.snapshot().shot().elevationDegrees(), "spin never rewrites aim");
        }
        var meter = chip();
        for (int i=0;i<10;i++) meter.tick(new ShotMeter.Input(true,false,false,false,false,false));
        same(50, meter.snapshot().targetSpin(), "up adjusts intended contact point rather than locked elevation");
        idle(meter, 5); press(meter);
        same(50, meter.snapshot().spin(), "overlapping marker and target matches the planned spin");
        same(1, meter.snapshot().elevationDegrees(), "contact panel preserves loft");
    }

    public static void softExpiry() {
        for (boolean fly : new boolean[]{false,true}) {
            var meter = fly ? chip() : new ShotMeter();
            if (fly) { idle(meter,60); press(meter); } else press(meter);
            idle(meter,120);
            require(meter.snapshot().shot() == null, "end frame is still hittable");
            var events = new ArrayList<>(meter.tick(NONE));
            same(1L, count(events,ShotMeter.Kind.COMMIT), "miss seals one very light shot");
            same(0, meter.snapshot().power(), "expiry cannot recycle the power sweep");
            require(meter.snapshot().timedOut(), "expiry is visible to the HUD and rewind");
            for(int i=0;i<200;i++) events.addAll(meter.tick(A));
            same(1L, count(events,ShotMeter.Kind.RELEASE), "miss releases automatically once");
            same(1L, count(events,ShotMeter.Kind.COMMIT), "late A cannot retry a missed sweep");
        }
        var falling = new ShotMeter(); press(falling); idle(falling,90); press(falling);
        same(500,falling.snapshot().power(),"falling half remains usable");
    }

    public static void buttonEdges() {
        var meter = chip();
        for(int i=0;i<200;i++) require(meter.tick(A).isEmpty(), "held A cannot stop spin");
        same(ShotMeter.Stage.SPIN,meter.snapshot().stage(),"spin waits for a fresh A edge");
        meter.tick(NONE); press(meter);
        var events = new ArrayList<ShotMeter.Event>();
        for(int i=0;i<200;i++) events.addAll(meter.tick(A));
        same(1L,count(events,ShotMeter.Kind.COMMIT),"held A cannot hit power; it eventually expires once");
        same(1L,count(events,ShotMeter.Kind.RELEASE),"automatic release exactly once");
        same(ShotMeter.Stage.WATCH,meter.snapshot().stage(),"shot settles without another launch input");
        for(int i=0;i<30;i++) require(meter.tick(i%2==0?A:B).isEmpty(),"postcommit controls cannot duplicate/cancel");
    }

    public static void freeCancel() {
        var meter = chip(); idle(meter,30); press(meter); idle(meter,15);
        same(1L,count(meter.tick(B),ShotMeter.Kind.CANCEL),"optional B cancels an unsealed panel");
        same(ShotMeter.Stage.AIM,meter.snapshot().stage(),"back to aim");
        same(0,meter.snapshot().spin(),"previous contact point cleared");
        same(0,meter.snapshot().targetSpin(),"previous preview cleared");
        require(meter.snapshot().shot()==null,"cancel does not seal a shot");
        meter.tick(NONE); press(meter);
        same(ShotMeter.Stage.SPIN,meter.snapshot().stage(),"fresh chip starts with spin again");
    }

    public static void meterReplay() {
        for(boolean expired:new boolean[]{false,true}) {
            var meter=chip(); idle(meter,90); press(meter); idle(meter,35);
            var saved=meter.snapshot(); var events=new ArrayList<ShotMeter.Event>();
            if(!expired) events.addAll(press(meter));
            for(int i=0;i<200;i++) events.addAll(meter.tick(NONE));
            var expected=meter.snapshot(); meter.restore(saved); var replay=new ArrayList<ShotMeter.Event>();
            if(!expired) replay.addAll(press(meter));
            for(int i=0;i<200;i++) replay.addAll(meter.tick(NONE));
            same(events,replay,"power/expiry replay event order"); same(expected,meter.snapshot(),"complete meter state");
        }
        var held=chip(); var state=held.snapshot(); held.tick(NONE); press(held); held.restore(state);
        require(held.tick(A).isEmpty(),"restored held A cannot become a fresh edge");
        var target=chip(); target.tick(new ShotMeter.Input(false,true,false,false,false,false));
        var saved=target.snapshot(); var events=press(target); var expected=target.snapshot();
        target.restore(saved); same(events,press(target),"spin marker/target replay events");
        same(expected,target.snapshot(),"spin marker/target replay state");
    }

    public static void powerFeedback() {
        long previous=-1;
        for(int ticks:new int[]{1,15,30,45,60}) {
            var meter=new ShotMeter(); press(meter); idle(meter,ticks); press(meter);
            var saved=meter.snapshot(); var events=new ArrayList<ShotMeter.Event>();
            for(int i=0;i<GolfRules.CHARGE_FEEDBACK_TICKS+GolfRules.PRE_RELEASE_TICKS;i++) events.addAll(meter.tick(NONE));
            long charges=count(events,ShotMeter.Kind.CHARGE);
            require(charges>previous,"more power requests more native charges");previous=charges;
            same(1L,count(events,ShotMeter.Kind.RELEASE),"fixed release after native feedback/pause");
            meter.restore(saved);var replay=new ArrayList<ShotMeter.Event>();
            for(int i=0;i<GolfRules.CHARGE_FEEDBACK_TICKS+GolfRules.PRE_RELEASE_TICKS;i++) replay.addAll(meter.tick(NONE));
            same(events,replay,"feedback cadence restores");
        }
    }

    public static void aimLocks() {
        var meter=new ShotMeter();
        for(int i=0;i<1000;i++) meter.tick(new ShotMeter.Input(true,false,true,false,false,false));
        same(90,meter.snapshot().elevationDegrees(),"vertical aim clamp");same(-1,meter.snapshot().direction(),"left faces left");
        press(meter);
        for(int i=0;i<100;i++) meter.tick(new ShotMeter.Input(false,true,false,true,false,false));
        same(90,meter.snapshot().elevationDegrees(),"panel locks loft");same(-1,meter.snapshot().direction(),"panel locks facing");
        same(-100,meter.snapshot().targetSpin(),"down in contact panel chooses backspin");
        meter.tick(B);meter.tick(NONE);
        for(int i=0;i<1000;i++)meter.tick(new ShotMeter.Input(false,true,false,true,false,false));
        same(0,meter.snapshot().elevationDegrees(),"down returns to putt");same(1,meter.snapshot().direction(),"right faces right");
    }

    public static void spinPhysics() {
        for(int direction:new int[]{-1,1}) for(int angle:new int[]{0,16,248}) {
            var back=GolfRules.launchVelocity(direction,45,0x800,angle,-100);
            var neutral=GolfRules.launchVelocity(direction,45,0x800,angle,0);
            var top=GolfRules.launchVelocity(direction,45,0x800,angle,100);
            require(back.ground()*direction<neutral.ground()*direction && neutral.ground()*direction<top.ground()*direction,
                    "contact point changes the shared surface-relative departure");
            same(neutral,GolfRules.launchVelocity(direction,45,0x800,angle),"neutral keeps native departure");
            require(GolfRules.landingSpeed(direction*300,new GolfShot(direction,45,1000,-100))*direction<0,"backspin can reverse at landing");
            require(GolfRules.landingSpeed(direction*300,new GolfShot(direction,45,1000,100))*direction>300,"topspin carries forward");
        }
        for(int spin:new int[]{-101,101}) {
            try { new GolfShot(1,45,1000,spin); throw new AssertionError("invalid spin accepted"); }
            catch(IllegalArgumentException expected) { }
        }
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
        same(GolfMatch.Decision.REJECTED, match.commit(id, new GolfShot(-1, 0, 500, 0)), "conflicting payload rejected");
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

    public static void shotRewind() {
        var match = GolfMatch.competition(GolfMatch.Character.SONIC, GolfMatch.Character.TAILS);
        var before = match.snapshot(); var oldId = match.nextShotId();
        match.commit(oldId, SHOT);
        same(GolfMatch.Decision.ACCEPTED, match.rewind(oldId), "rewind pending shot");
        same(before.golfers(), match.snapshot().golfers(), "refund shot without changing score history");
        same(before.turnSequence(), match.snapshot().turnSequence(), "retry keeps turn");
        same(before.activePlayer(), match.snapshot().activePlayer(), "retry keeps golfer");
        require(match.nextShotId().shotSequence() > oldId.shotSequence(), "retry identity must advance");
        same(GolfMatch.Decision.REJECTED, match.rewind(oldId), "duplicate rewind cannot refund another stroke");
        same(GolfMatch.Decision.REJECTED, match.commit(oldId, SHOT), "retired shot cannot be resubmitted");
        play(match, SETTLE);
        same(1, match.snapshot().golfers().get(0).total(), "only kept shot counts");
        same(1, match.snapshot().activePlayer(), "kept shot passes turn normally");
    }

    public static void rewindAllowances() {
        var allowance = new RewindAllowance(RewindAllowance.Rules.defaults());
        var id = new GolfMatch.ShotId(0, 1, 0, 1);
        require(allowance.spend(id), "first rewind");
        require(!allowance.spend(id), "duplicate cannot consume quota");
        require(!allowance.spend(new GolfMatch.ShotId(0, 1, 0, 2)), "retry does not reset turn limit");
        same(2, allowance.holeRemaining(0, 0), "hole allowance consumed once");
        require(allowance.spend(new GolfMatch.ShotId(0, 2, 1, 3)), "other golfer independent");
        require(allowance.spend(new GolfMatch.ShotId(0, 3, 0, 4)), "next turn resets turn limit");
        require(allowance.spend(new GolfMatch.ShotId(0, 5, 0, 6)), "third hole rewind");
        require(!allowance.spend(new GolfMatch.ShotId(0, 7, 0, 8)), "fourth hole rewind rejected");
        require(allowance.spend(new GolfMatch.ShotId(1, 8, 0, 9)), "new hole has fresh allowance");
        var saved = allowance.snapshot();
        allowance.spend(new GolfMatch.ShotId(1, 10, 0, 11)); allowance.restore(saved);
        same(saved, allowance.snapshot(), "debug replay restores allowance exactly");
        for (int holeLimit : new int[]{0, 3, 5, -1}) for (int turnLimit : new int[]{1, 3, -1}) {
            var configured = new RewindAllowance(new RewindAllowance.Rules(holeLimit, turnLimit));
            for (int n = 1; n <= 8; n++) {
                boolean expected = holeLimit != 0 && (holeLimit < 0 || n <= holeLimit) && (turnLimit < 0 || n <= turnLimit);
                same(expected, configured.spend(new GolfMatch.ShotId(0, 1, 0, n)), "configured allowance");
            }
        }
    }

    public static void rewindSpeed() {
        int shortSpeed = RewindAllowance.replaySpeed(30), longSpeed = RewindAllowance.replaySpeed(900);
        require(longSpeed > shortSpeed, "longer replay rewinds faster");
        require((900 + longSpeed - 1) / longSpeed <= 90, "long rewind completes within 90 ticks");
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

    public static void turnReadiness() {
        var ready = new TurnReadiness();
        require(ready.open(0, true, true), "a competition's first golfer is introduced");
        require(ready.firstOfHole(), "first turn of the hole is marked");
        // An A held on the handoff row neither confirms nor arms the gate.
        require(!ready.press(true, true), "held A from the previous turn cannot confirm");
        require(!ready.press(true, false), "still held");
        require(!ready.press(false, false), "release arms the gate");
        require(ready.press(true, true), "fresh press confirms once");
        require(!ready.press(true, true), "a request cannot be confirmed twice");
        require(ready.requested(), "the press is a request until accepted");
        ready.accept(); require(!ready.waiting(), "accepted turn is ready");
        ready.accept(); require(!ready.waiting(), "acceptance is idempotent");
        require(!ready.open(0, true, false), "a rewind retry keeps its turn and readiness");
        require(ready.open(1, true, false), "the other golfer is held at the handoff");
        require(!ready.firstOfHole(), "mid-hole handoff is not a hole introduction");
        same(1, ready.owner(), "incoming owner");
        require(ready.press(true, true), "released at the handoff: the next fresh press confirms");
        ready.reopen(); require(ready.waiting() && !ready.requested(), "a refused request waits again");
        require(!ready.press(true, true), "reopened gate needs another release");
        ready.press(false, false); ready.disarm();
        require(!ready.press(true, true), "pause disarms an armed gate");
        var saved = ready.snapshot();
        ready.press(false, false); require(ready.press(true, true), "confirm after release");
        ready.restore(saved); require(ready.waiting() && !ready.requested(), "restore returns the exact gate");
        ready.accept();
        require(!ready.open(1, true, false), "a golfer whose opponent finished plays on without a handoff");
        ready.newHole(); require(ready.open(1, true, false), "a new hole always introduces its first golfer");
        var practice = new TurnReadiness();
        require(!practice.open(0, false, false) && !practice.open(0, false, false), "practice never waits");
        var guest = new TurnReadiness();
        guest.adopt(1, true, true, true); require(guest.waiting() && guest.firstOfHole(), "guest mirrors the host's hold");
        require(!guest.press(true, true), "guest A held across the handoff cannot request");
        guest.adopt(0, false, false, false); require(!guest.waiting(), "an unheld host turn needs no press");
        try { ready.open(2, true, false); throw new AssertionError("owner range"); } catch (IllegalArgumentException correct) { }
    }
}
