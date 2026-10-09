package typinghog.core;

/**
 * Score, chain, accuracy and speed for one act, fed by {@link TypingField} events. Pure
 * arithmetic on 60 Hz rows; the act tally reads its bonuses here.
 *
 * <p>Kill points are the prompt's tier points times a chain multiplier of 1 + chain/8 (capped at
 * 4). The chain counts error-free kills; an error or a hit taken resets it. Speed is words per
 * minute over <em>active</em> rows only (rows with a live target), so stand-to-stand travel does
 * not dilute it; a word is five typed characters.
 */
public final class Scorecard {
    public static final int ROWS_PER_SECOND = 60;

    private long score;
    private int chain;
    private int bestChain;
    private int correct;
    private int errors;
    private int kills;
    private int hitsTaken;
    private int rescues;
    private long activeRows;

    /** Word tier points by typed length and keyboard reach. */
    public static int tierPoints(Prompt prompt) {
        String typed = prompt.typed();
        if (typed.indexOf(' ') >= 0) return 150;
        int length = typed.length();
        int awkward = 0;
        for (int i = 0; i < length; i++) if ("qzxjkvbpy0123456789".indexOf(typed.charAt(i)) >= 0) awkward++;
        int tier = length <= 3 ? 10 : length <= 5 ? 20 : length <= 7 ? 30 : length <= 9 ? 50 : 80;
        return tier + awkward * 5;
    }

    /** The ROM score sprite a kill at this chain shows: 100, 200, 500, then 1000. */
    public static int popupValue(int chainBeforeKill) {
        return switch (Math.min(chainBeforeKill, 3)) {
            case 0 -> 100;
            case 1 -> 200;
            case 2 -> 500;
            default -> 1000;
        };
    }

    public static double multiplier(int chain) {
        return Math.min(4.0, 1.0 + chain / 8.0);
    }

    /** Records one correct keystroke. */
    public void keystroke() { correct++; }

    /** Records a wrong character; the chain breaks. */
    public void error() {
        errors++;
        chain = 0;
    }

    /** Records a completed prompt and returns the points it scored. */
    public int kill(Prompt prompt) {
        int points = (int) Math.round(tierPoints(prompt) * multiplier(chain));
        score += points;
        chain++;
        bestChain = Math.max(bestChain, chain);
        kills++;
        return points;
    }

    /** Records a rescued animal (a bonus word). */
    public int rescue(Prompt prompt) {
        rescues++;
        int points = tierPoints(prompt) * 2;
        score += points;
        return points;
    }

    /** Records a hit taken; the chain breaks. */
    public void hitTaken() {
        hitsTaken++;
        chain = 0;
    }

    /** Adds flat points (boss hits, monitor words, tallies). */
    public void add(long points) { score += points; }

    /** Counts one row in which a target was live. */
    public void activeRow() { activeRows++; }

    public long score() { return score; }
    public int chain() { return chain; }
    public int bestChain() { return bestChain; }
    public int correct() { return correct; }
    public int errors() { return errors; }
    public int kills() { return kills; }
    public int hitsTaken() { return hitsTaken; }
    public int rescues() { return rescues; }
    public long activeRows() { return activeRows; }

    /** Correct keystrokes over all keystrokes, 1.0 before any typing. */
    public double accuracy() {
        int total = correct + errors;
        return total == 0 ? 1.0 : (double) correct / total;
    }

    /** Words per minute over active rows; 0 before a full second of activity. */
    public double wpm() {
        if (activeRows < ROWS_PER_SECOND) return 0;
        double minutes = activeRows / (double) (ROWS_PER_SECOND * 60);
        return correct / 5.0 / minutes;
    }

    /** The act tally's lines. */
    public record Tally(long speedBonus, long accuracyBonus, long ringBonus, long rescueBonus, long perfectBonus,
                        String grade) {
        public long total() { return speedBonus + accuracyBonus + ringBonus + rescueBonus + perfectBonus; }
    }

    /** Computes the act tally; {@code rings} is the health left at the end. */
    public Tally tally(int rings) {
        double wpm = wpm();
        double accuracy = accuracy();
        long speed = Math.round(wpm) * 100;
        long precise = accuracy >= 0.999 ? 10_000 : accuracy >= 0.98 ? 5_000 : accuracy >= 0.95 ? 2_000
                : accuracy >= 0.90 ? 1_000 : 0;
        long ring = rings * 100L;
        long rescue = rescues * 500L;
        long perfect = hitsTaken == 0 && kills > 0 ? 5_000 : 0;
        return new Tally(speed, precise, ring, rescue, perfect, grade(wpm, accuracy, hitsTaken));
    }

    /** S/A/B/C/D/E from speed, accuracy and hits taken. */
    public static String grade(double wpm, double accuracy, int hitsTaken) {
        double value = wpm * accuracy * accuracy - hitsTaken * 4;
        if (value >= 70 && hitsTaken == 0) return "S";
        if (value >= 55) return "A";
        if (value >= 40) return "B";
        if (value >= 28) return "C";
        if (value >= 16) return "D";
        return "E";
    }
}
