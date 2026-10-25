package com.openggf.game.sonic2.competition;

/** REV01 Setup2PResults_Act and sub_8652: each strict category win awards one point. */
public final class Sonic2CompetitionResults {
    private Sonic2CompetitionResults() { }

    public record Metrics(int score, long timerFrames, int rings, int totalRings, int monitors) { }
    public record Wins(int player1, int player2) {
        public int winner() { return Integer.compare(player1, player2); }
    }

    public static Wins compare(Metrics p1, Metrics p2) {
        int[] comparisons = {Integer.compare(p1.score(), p2.score()),
                Long.compare(p2.timerFrames(), p1.timerFrames()),
                Integer.compare(p1.rings(), p2.rings()),
                Integer.compare(p1.totalRings(), p2.totalRings()),
                Integer.compare(p1.monitors(), p2.monitors())};
        int one = 0, two = 0;
        for (int comparison : comparisons) {
            if (comparison > 0) one++;
            else if (comparison < 0) two++;
        }
        return new Wins(one, two);
    }
}
