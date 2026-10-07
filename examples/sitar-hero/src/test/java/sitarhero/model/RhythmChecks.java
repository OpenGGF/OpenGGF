package sitarhero.model;

import java.util.List;

/** Behaviour checks compiled with the external model, without engine dependencies. */
public final class RhythmChecks {
    public static void main(String[] args) {
        windowAndAnchoring(); chordsAndOverstrum(); hopoChainAndRecovery(); directPadsAndKick();
        sustainReleaseAndCadence(); phrasesPowerAndWhammy(); powerExpiryCadence(); failuresAndFiniteResults(); monotonicAndImmutableChart();
        finalJudgmentWindow();
        System.out.println("10 external rhythm checks passed");
    }
    private static ChartNote note(long at, int lanes, boolean hopo) {
        return new ChartNote(at, at, lanes, hopo, -1, 0);
    }
    private static RhythmSession session(boolean drums, ChartNote... notes) {
        return new RhythmSession(new Chart(List.of(notes), 10_000, 500), drums, 1_000);
    }
    private static void eq(long expected, long actual) {
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    private static void yes(boolean value) { if (!value) throw new AssertionError(); }

    public static void windowAndAnchoring() {
        var first = session(false, note(0, 1, false));
        first.input(-100, 1, 0, true, false, false); eq(1, first.hits());
        var s = session(false, note(1_000, 4, false), note(2_000, 1, false));
        s.input(900, 7, 0, true, false, false); // lower frets may be anchored
        eq(1, s.hits());
        s.input(2_100, 3, 0, true, false, false); // higher fret blocks green
        eq(1, s.hits());
        s.advance(2_101); eq(1, s.misses());
        var early = session(false, note(1_000, 1, false));
        early.input(899, 1, 0, true, false, false); eq(0, early.hits());
        early.input(1_100, 1, 0, true, false, false); eq(1, early.hits());
    }

    public static void chordsAndOverstrum() {
        var s = session(false, note(1_000, 5, false));
        s.input(1_000, 7, 0, true, false, false); eq(0, s.hits());
        s.input(1_010, 5, 0, true, false, false); eq(1, s.hits()); eq(100, s.score());
        s.input(1_020, 5, 0, true, false, false); eq(0, s.streak());
    }

    public static void hopoChainAndRecovery() {
        var s = session(false, note(1_000, 1, false), note(1_200, 2, true), note(1_400, 4, true));
        s.input(1_000, 1, 0, true, false, false);
        s.input(1_010, 2, 0, false, false, false); // GH3 allows an early held HOPO
        s.advance(1_100); eq(2, s.hits());
        s.input(1_110, 2, 0, true, false, false); // strum already-hit HOPO once
        eq(2, s.streak());
        s.input(1_250, 0, 0, true, false, false); // overstrum breaks chain
        s.input(1_400, 4, 0, false, false, false); eq(2, s.hits());
        s.input(1_410, 4, 0, true, false, false); eq(3, s.hits());
    }

    public static void directPadsAndKick() {
        var s = session(true, note(1_000, 18, false), note(1_500, 2, false));
        s.input(990, 2, 2, false, false, false); eq(0, s.hits());
        s.input(1_010, 18, 16, false, false, false); eq(1, s.hits());
        s.advance(1_500); eq(1, s.hits()); // held pad does not retrigger
        s.input(1_500, 2, 2, false, false, false); eq(2, s.hits());
    }

    public static void sustainReleaseAndCadence() {
        ChartNote sustain = new ChartNote(1_000, 2_000, 5, false, -1, 50);
        var a = session(false, sustain); var b = session(false, sustain);
        a.input(1_000, 5, 0, true, false, false); b.input(1_000, 5, 0, true, false, false);
        for (int i = 1_010; i <= 2_000; i += 10) a.advance(i);
        b.advance(2_000); eq(a.score(), b.score()); eq(150, b.score());
        var cut = session(false, sustain);
        cut.input(1_000, 5, 0, true, false, false);
        cut.input(1_500, 1, 0, false, false, false);
        cut.advance(2_000); eq(125, cut.score()); eq(1, cut.streak());
    }

    public static void phrasesPowerAndWhammy() {
        var s = session(false, new ChartNote(1_000, 1_500, 1, false, 0, 25),
                new ChartNote(2_000, 2_000, 2, false, 0, 0),
                new ChartNote(3_000, 3_000, 4, false, 1, 0));
        s.input(1_000, 1, 0, true, false, true); s.advance(1_500);
        s.input(2_000, 2, 0, true, false, false); yes(s.starCharge() > .25);
        s.input(3_000, 4, 0, true, false, false);
        s.input(3_010, 4, 0, false, true, false); yes(s.starActive());
        var failed = session(false, new ChartNote(1_000, 1_000, 1, false, 0, 0),
                new ChartNote(2_000, 2_000, 2, false, 0, 0));
        failed.advance(1_101); failed.input(2_000, 2, 0, true, false, false);
        eq(0, Math.round(failed.starCharge() * 100));
        for (boolean drums : List.of(false, true)) {
            var extra = session(drums, new ChartNote(1_000, 1_000, 1, false, 0, 0),
                    new ChartNote(2_000, 2_000, 2, false, 0, 0));
            extra.input(1_000, 1, drums ? 1 : 0, !drums, false, false);
            extra.input(1_500, 4, drums ? 4 : 0, !drums, false, false);
            extra.input(2_000, 2, drums ? 2 : 0, !drums, false, false);
            eq(2, extra.hits());
            eq(0, Math.round(extra.starCharge() * 100)); // extra strike breaks an unfinished phrase
        }
    }

    public static void failuresAndFiniteResults() {
        var notes = new ChartNote[40];
        for (int i = 0; i < notes.length; i++) notes[i] = note(100 + i * 150, 1, false);
        var s = session(false, notes); s.advance(9_000); yes(s.failed());
        var empty = session(false); empty.advance(10_000); yes(empty.finished());
        var retry = session(false, note(1_000, 1, false)); eq(0, retry.score());
        yes(retry.partAudible());
    }

    public static void powerExpiryCadence() {
        Chart chart = new Chart(List.of(new ChartNote(0, 0, 1, false, 0, 0),
                new ChartNote(1_000, 1_000, 1, false, 1, 0),
                new ChartNote(2_000, 12_000, 1, false, -1, 500)), 13_000, 500);
        RhythmSession a = new RhythmSession(chart, false, 1_000), b = new RhythmSession(chart, false, 1_000);
        for (RhythmSession s : List.of(a, b)) {
            s.input(0, 1, 0, true, false, false); s.input(1_000, 1, 0, true, false, false);
            s.input(1_001, 1, 0, false, true, false); s.input(2_000, 1, 0, true, false, false);
        }
        for (int sample = 2_010; sample <= 12_000; sample += 10) a.advance(sample);
        b.advance(12_000); eq(a.score(), b.score());
    }

    public static void monotonicAndImmutableChart() {
        var s = session(false, note(1_000, 1, false));
        s.input(1_000, 1, 0, true, false, false);
        s.advance(0); eq(1, s.hits()); eq(1_000, s.position());
        try { s.chart().notes().clear(); throw new AssertionError(); }
        catch (UnsupportedOperationException expected) { }
    }

    public static void finalJudgmentWindow() {
        Chart chart = new Chart(List.of(note(9_950, 1, false)), 10_000, 500);
        var missed = new RhythmSession(chart, false, 1_000);
        missed.advance(10_000); yes(!missed.finished()); eq(0, missed.misses());
        missed.advance(10_101); yes(missed.finished()); eq(1, missed.misses());
        var late = new RhythmSession(chart, false, 1_000);
        late.advance(10_000);
        late.input(10_050, 1, 0, true, false, false); eq(1, late.hits()); yes(late.finished());
    }
}
