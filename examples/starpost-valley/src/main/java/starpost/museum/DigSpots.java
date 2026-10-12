package starpost.museum;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Farm;
import starpost.core.Farmers;
import starpost.core.Game;
import starpost.core.Plot;

/**
 * Where the ground glints today: spots any farmer can dig with the action button for what the
 * season buried ({@link Finds#spot}). One on the farm's open grass and one along the valley path
 * each morning; Knuckles, who reads the ground, sees a second on the farm. Hazel's buried Star
 * Post Cap glints under the palms east of town while it is missing ({@link Museum#capBuried}).
 * The places follow from the day alone (as the valley's rings do), so they stay put all day.
 */
public final class DigSpots {
    /** The palms east of town (people.Anchors "palms_east", block 6 at x 120), a little east of them. */
    public static final int CAP_X = 6 * 256 + 140;
    /** Momentum a dig costs (as tilling a plot by hand). */
    public static final int COST = 2;

    private DigSpots() {
    }

    /** Today's farm spots as {row, column}: open, untilled grass with nothing on it. */
    public static List<int[]> farm(Game game) {
        List<int[]> free = new ArrayList<>();
        for (int r = 0; r < Farm.ROWS; r++) {
            for (int c = Farm.STARTER_PATCH; c < game.farm.open(); c++) {
                Plot p = game.farm.plot(r, c);
                if (p != null && !p.tilled && p.cover == Plot.GRASS && p.crop == null && p.object == null) {
                    free.add(new int[] {r, c});
                }
            }
        }
        List<int[]> out = new ArrayList<>();
        int wanted = game.farmer.equals(Farmers.KNUCKLES) ? 2 : 1;
        long seed = seed(game, 0x51);
        while (out.size() < wanted && !free.isEmpty()) {
            seed = next(seed);
            out.add(free.remove((int) ((seed >>> 33) % free.size())));
        }
        return out;
    }

    /** Today's valley spot's x, away from both ends of the path. */
    public static int valleyX(Game game, int valleyWidth) {
        long seed = next(seed(game, 0x77));
        return 300 + (int) ((seed >>> 33) % Math.max(1, valleyWidth - 700));
    }

    private static long seed(Game game, int salt) {
        return game.calendar.dayNumber() * 2654435761L + salt * 40503L + game.farmer.hashCode();
    }

    private static long next(long seed) {
        return seed * 6364136223846793005L + 1442695040888963407L;
    }
}
