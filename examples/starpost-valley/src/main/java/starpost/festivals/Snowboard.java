package starpost.festivals;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;

/**
 * The Ice Cap Festival's snowboard run (Winter 8): down Green Hill's slopes under winter snow,
 * on the board Sonic rode into Ice Cap Zone. The course is a chain of the valley's own blocks
 * (down slopes, ledges to fly off, bumps and dips), each set where the last one ended so the run
 * keeps falling. Rings hang along it; rocks wait to trip the board; a jump with a spin in the air
 * is a trick. Score: rings, tricks and the time left; Frost's record is the one to beat. When the
 * fishing system is installed the festival's contest is fishing instead (see {@link FishingContest}).
 * Engine-free: the course is block ids, the scoring arithmetic.
 */
public final class Snowboard {
    /** The par time: seconds under it are worth points. */
    public static final int PAR_TICKS = 60 * 40;
    public static final int RING_POINTS = 10;
    public static final int TRICK_POINTS = 60;
    public static final int TIME_POINTS = 20;
    public static final int CRASH_TICKS = 70;
    /** Frost's standing record. */
    public static final int FROST_RECORD = 900;
    public static final int REPEAT_PURSE = 600;
    public static final int RINGS_EACH = 5;

    private Snowboard() {
    }

    /**
     * The year's course as Green Hill block ids: a flat start, then a run of down slopes, ledges
     * and bumps, ending on the flat where the signpost stands. Slopes up are rare (a kicker).
     */
    public static int[] course(int year) {
        // Open-air blocks only: 2 the long slope down, 37 the dip, 40 and 41 a ledge to fly off,
        // then flats with palms (45, 42), a bump (46), the short flat (60) and the plain (1).
        // Blocks with cave walls or cliffs above their path (12, 21, 26, 35, 47) drew them across
        // the sky, with the rider passing in front.
        int[] downs = {2, 37, 2, 40, 41, 37};
        int[] flats = {45, 42, 46, 60, 1};
        int length = 34;
        int[] out = new int[length];
        out[0] = 60;
        out[1] = 60;
        for (int i = 2; i < length - 2; i++) {
            int roll = Board.mix(year * 31 + 5, i) % 10;
            if (roll < 6) {
                out[i] = downs[Board.mix(year, i * 3) % downs.length];
            } else if (roll < 9) {
                out[i] = flats[Board.mix(year, i * 5) % flats.length];
            } else {
                out[i] = 7;          // the quarter-pipe kicker
            }
        }
        out[length - 2] = 60;
        out[length - 1] = 60;
        return out;
    }

    public static int score(int ticks, int rings, int tricks) {
        int timeBonus = Math.max(0, (PAR_TICKS - ticks) / 60) * TIME_POINTS;
        return rings * RING_POINTS + tricks * TRICK_POINTS + timeBonus;
    }

    public static boolean beatsFrost(int score) {
        return beats(score, FROST_RECORD);
    }

    /** Whether a score takes Frost's record (a tie leaves it with Frost). */
    public static boolean beats(int score, int record) {
        return score > record;
    }

    /** The run's prizes. Returns notices. */
    public static List<String> reward(Game game, Festivals festivals, int score, int rings, boolean won) {
        festivals.record(FestivalBook.ICE_CAP, game.calendar.year(), won ? 1 : 2, score);
        return prizes(game, festivals, rings * RINGS_EACH, won);
    }

    /**
     * The fishing contest's prizes: each point pays like a ring on the run, beating Frost's catch
     * record wins as beating the run's does. Its best is kept apart from the run's
     * ({@link #FISHING_BEST}): the two count different things. Returns notices.
     */
    public static List<String> fishingReward(Game game, Festivals festivals, int points, int record) {
        boolean won = beats(points, record);
        festivals.record(FestivalBook.ICE_CAP, game.calendar.year(), won ? 1 : 2, 0);
        festivals.recordBest(FISHING_BEST, points);
        return prizes(game, festivals, Math.max(0, points) * RINGS_EACH, won);
    }

    /** The best-score key of the fishing contest (the run's is the festival's own). */
    public static final String FISHING_BEST = FestivalBook.ICE_CAP + ":fishing";

    private static List<String> prizes(Game game, Festivals festivals, int pay, boolean won) {
        List<String> notices = new ArrayList<>();
        if (won) {
            if (festivals.takePrize("trophy." + FestivalBook.ICE_CAP)) {
                Prizes.give(game, festivals, "record_icecap_s3", 1, notices);
                notices.add("A SNOWBOARD FOR THE TROPHY STAND");
                Prizes.friendship(game, "frost", 150);
            } else {
                pay += REPEAT_PURSE;
            }
        }
        game.rings += pay;
        notices.add(0, "+" + pay + " RINGS");
        Prizes.everyone(game, 20);
        return notices;
    }
}
