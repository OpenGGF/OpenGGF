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
        int[] downs = {2, 37, 35, 46, 47, 40, 41};
        int[] flats = {45, 12, 21, 26, 60};
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
        return score > FROST_RECORD;
    }

    /** The run's prizes (or the fishing contest's, with its own record). Returns notices. */
    public static List<String> reward(Game game, Festivals festivals, int score, int rings, boolean won) {
        List<String> notices = new ArrayList<>();
        festivals.record(FestivalBook.ICE_CAP, game.calendar.year(), won ? 1 : 2, score);
        int pay = rings * RINGS_EACH;
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
