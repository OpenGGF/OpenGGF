package starpost.festivals;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;

/**
 * The Night of the Flickies (Summer 28), after Sonic 3 &amp; Knuckles' ending: the valley's Flickies
 * fly south over the lake in waves. How many fly is how many animals the farmer has freed (the
 * valley's population): restoration you can see. Waving (the action button) as a wave passes
 * overhead brings one Flicky down to circle the farmer before it rejoins the flock. Nothing is
 * won; the night pays in friendship, and Pip's first migration leaves a Record. Engine-free.
 */
public final class FlickyNight {
    /** How long the migration lasts, and the gap between waves. */
    public static final int TICKS = 60 * 50;
    public static final int WAVE_GAP = 300;
    /** Each Flicky's flight across the sky, in ticks. */
    public static final int CROSSING = 520;
    /** A wave counts as overhead for this long after it enters. */
    public static final int OVERHEAD = 360;

    /** One Flicky: which wave, its place in the V, and its own wobble. */
    public record Bird(int wave, int rank, int side, int phase) {
    }

    private FlickyNight() {
    }

    /** How many Flickies fly: the valley's population, never fewer than the six who were always here. */
    public static int count(Game game) {
        return Math.max(6, Math.min(Game.MAX_POPULATION, game.population));
    }

    /** The flock in waves of V formations of up to nine; each wave's leader first. */
    public static List<Bird> flock(int count) {
        List<Bird> out = new ArrayList<>();
        int wave = 0, inWave = 0;
        for (int i = 0; i < count; i++) {
            int rank = (inWave + 1) / 2;
            int side = inWave == 0 ? 0 : inWave % 2 == 0 ? 1 : -1;
            out.add(new Bird(wave, rank, side, Board.mix(i, 77) % 64));
            if (++inWave == 9) {
                inWave = 0;
                wave++;
            }
        }
        return out;
    }

    public static int waves(int count) {
        return (count + 8) / 9;
    }

    /** The tick a wave enters from the east. */
    public static int waveStart(int wave) {
        return 120 + wave * WAVE_GAP;
    }

    /** Whether a wave is overhead now (a wave of the hand is answered). */
    public static boolean overhead(int wave, int tick) {
        int t = tick - waveStart(wave);
        return t >= 60 && t < OVERHEAD;
    }

    /** The night's rewards: friendship with everyone, more with Pip; the first year, the Record. */
    public static List<String> reward(Game game, Festivals festivals, int waved) {
        List<String> notices = new ArrayList<>();
        festivals.record(FestivalBook.FLICKIES, game.calendar.year(), 0, waved);
        Prizes.everyone(game, 30);
        Prizes.friendship(game, "pip", 70 + 10 * Math.min(5, waved));
        if (festivals.takePrize("record." + FestivalBook.FLICKIES)) {
            Prizes.give(game, festivals, "record_migration", 1, notices);
        }
        notices.add(0, count(game) + " FLICKIES FLEW SOUTH");
        return notices;
    }
}
