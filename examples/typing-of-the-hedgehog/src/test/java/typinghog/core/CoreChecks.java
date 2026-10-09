package typinghog.core;

import java.util.List;

/** Behaviour checks for the engine-free typing core; run by the engine's TestTypingHedgehogCore. */
public final class CoreChecks {
    public static void main(String[] args) {
        promptForms(); lockingPrefersTheMostUrgentMatch(); errorsDoNotAdvanceOrSteal(); releaseAndHiddenTargets();
        phrasesNeedSpaces(); scoringChainsAndPopups(); speedCountsActiveRowsOnly(); tallyAndGrades();
        System.out.println("8 typing core checks passed");
    }

    private static void eq(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void yes(boolean value, String what) { if (!value) throw new AssertionError(what); }

    public static void promptForms() {
        eq("dont panic", Prompt.of("Don't Panic!", false, false).typed());
        eq("Don't Panic!", Prompt.of("Don't Panic!", true, true).typed());
        eq("dont panic", Prompt.of("  Don't   Panic! ", false, false).typed());
        eq((int) 'd', Prompt.of("Don't", false, false).lead());
        try { Prompt.of("!!!", false, false); throw new AssertionError("empty typed form accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    public static void lockingPrefersTheMostUrgentMatch() {
        var field = new TypingField(false);
        int far = field.add(Prompt.of("motobug", false, false), 300);
        int near = field.add(Prompt.of("moto", false, false), 40);
        var lock = field.type('m');
        eq(TypingField.Kind.LOCK, lock.kind());
        eq(near, lock.target());
        eq(List.of(TypingField.Kind.HIT, TypingField.Kind.HIT, TypingField.Kind.COMPLETE),
                field.type("oto").stream().map(TypingField.Event::kind).toList());
        eq(-1, field.locked());
        eq(TypingField.Kind.LOCK, field.type('M').kind());
        eq(far, field.locked());
    }

    public static void errorsDoNotAdvanceOrSteal() {
        var field = new TypingField(false);
        int ring = field.add(Prompt.of("ring", false, false), 100);
        field.add(Prompt.of("xeno", false, false), 10);
        field.type('r');
        var wrong = field.type('x');
        eq(TypingField.Kind.ERROR, wrong.kind());
        eq(ring, wrong.target());
        eq(1, field.target(ring).progress());
        eq(ring, field.locked());
        eq(TypingField.Kind.ERROR, new TypingField(false).type('q').kind());
    }

    public static void releaseAndHiddenTargets() {
        var field = new TypingField(false);
        int chopper = field.add(Prompt.of("chop", false, false), 50);
        field.type('c');
        eq(TypingField.Kind.RELEASE, field.release().kind());
        eq(-1, field.locked());
        eq(0, field.target(chopper).progress());
        field.setTypeable(chopper, false);
        eq(TypingField.Kind.ERROR, field.type('h').kind());
        var other = field.add(Prompt.of("crab", false, false), 70);
        yes(field.leadInUse('c'), "crab's lead is visible");
        field.setTypeable(chopper, true);
        field.remove(other);
        eq(TypingField.Kind.LOCK, field.type('c').kind());
        eq(chopper, field.locked());
    }

    public static void phrasesNeedSpaces() {
        var field = new TypingField(false);
        int taunt = field.add(Prompt.of("Gotta go!", false, false), 10);
        eq(TypingField.Kind.IGNORED, field.type(' ').kind());
        field.type("gotta");
        eq(TypingField.Kind.ERROR, field.type('g').kind());
        eq(TypingField.Kind.HIT, field.type(' ').kind());
        eq(TypingField.Kind.COMPLETE, field.type("go").getLast().kind());
        yes(field.target(taunt) == null, "completed phrase leaves the field");
    }

    public static void scoringChainsAndPopups() {
        var card = new Scorecard();
        Prompt word = Prompt.of("ring", false, false);
        eq(20, Scorecard.tierPoints(word));
        eq(150, Scorecard.tierPoints(Prompt.of("spin dash", false, false)));
        eq(100, Scorecard.popupValue(0));
        eq(200, Scorecard.popupValue(1));
        eq(500, Scorecard.popupValue(2));
        eq(1000, Scorecard.popupValue(9));
        int first = card.kill(word);
        for (int i = 0; i < 7; i++) card.kill(word);
        int ninth = card.kill(word);
        eq(20, first);
        eq(40, ninth);
        eq(9, card.bestChain());
        card.error();
        eq(0, card.chain());
        card.kill(word);
        card.hitTaken();
        eq(0, card.chain());
        eq(9, card.bestChain());
        eq(4.0, Scorecard.multiplier(1000));
    }

    public static void speedCountsActiveRowsOnly() {
        var card = new Scorecard();
        for (int i = 0; i < 300; i++) card.keystroke();
        eq(0.0, card.wpm());
        for (int i = 0; i < 3600; i++) card.activeRow();
        eq(60.0, card.wpm());
        card.error();
        yes(Math.abs(card.accuracy() - 300 / 301.0) < 1e-9, "accuracy");
    }

    public static void tallyAndGrades() {
        eq("S", Scorecard.grade(80, 1.0, 0));
        eq("A", Scorecard.grade(80, 1.0, 1));
        eq("E", Scorecard.grade(10, 0.8, 3));
        var card = new Scorecard();
        Prompt word = Prompt.of("ring", false, false);
        for (int i = 0; i < 250; i++) card.keystroke();
        for (int i = 0; i < 3600; i++) card.activeRow();
        card.kill(word);
        var tally = card.tally(12);
        eq(5_000L, tally.speedBonus());
        eq(10_000L, tally.accuracyBonus());
        eq(1_200L, tally.ringBonus());
        eq(5_000L, tally.perfectBonus());
        eq("B", tally.grade());
    }
}
