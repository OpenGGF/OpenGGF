package starpost.festivals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import starpost.core.Game;

/**
 * The Ring Hunt's rules (Spring 13): sixty seconds to grab more rings than the champion. Rings lie
 * all over town in Sonic's lines and arcs, some up on the totem ledge's box and some high over
 * the spring. The champion (Tails, or Sonic when Tails farms) hunts them too, flying to the
 * nearest ring and stopping to count each one; ties go to the champion. Engine-free: the layout
 * takes the valley's floor as a function, and the champion is stepped like any actor.
 */
public final class RingHunt {
    public static final int SECONDS = 60;
    public static final int TICKS = SECONDS * 60;
    /** The town's stretch of valley the rings are scattered over (west of the loop). */
    public static final int LEFT = 300;
    public static final int RIGHT = 2280;
    /** The champion's pace: pixels a tick, and ticks spent counting each ring. */
    public static final float CHAMPION_SPEED = 1.3f;
    public static final int CHAMPION_PAUSE = 70;
    /** Rings the farmer's hunt pays, each (and a win's purse). */
    public static final int RINGS_EACH = 10;
    public static final int WIN_PURSE = 500;

    /** One ring: where it hangs (world x, and the y of its centre). */
    public record Spot(float x, float y) {
    }

    private RingHunt() {
    }

    /**
     * The year's rings: lines on the ground, arcs to jump for, a row on the totem ledge and a
     * column over the spring. The same for a given year, different from year to year.
     *
     * @param floor     the valley floor's y under an x
     * @param springX   the spring's x (rings above it need its launch)
     * @param ledgeLeft the totem ledge's raised box, left and right x, and its top y
     */
    public static List<Spot> layout(int year, IntUnaryOperator floor, int springX, int ledgeLeft, int ledgeRight,
            int ledgeTop) {
        List<Spot> out = new ArrayList<>();
        int seed = year * 7919 + 13;
        // Ground lines and jump arcs along the town, avoiding the ledge.
        for (int x = LEFT; x < RIGHT - 60; x += 150) {
            if (x + 80 > ledgeLeft - 40 && x < ledgeRight + 40) {
                continue;
            }
            int kind = Board.mix(seed, x) % 3;
            int fy = floor.applyAsInt(x + 32);
            for (int i = 0; i < 5; i++) {
                float rx = x + i * 16;
                float ry = switch (kind) {
                    case 0 -> fy - 16;                                              // a line on the ground
                    case 1 -> fy - 24 - (float) Math.sin(Math.PI * i / 4) * 56;     // an arc to jump through
                    default -> fy - 70;                                             // a high line: jump for it
                };
                out.add(new Spot(rx, ry));
            }
        }
        // A row on the ledge's top, and a column over the spring for the launch.
        for (int i = 0; i < 6; i++) {
            out.add(new Spot(ledgeLeft + 24 + i * (ledgeRight - ledgeLeft - 48) / 5f, ledgeTop - 16));
        }
        for (int i = 0; i < 4; i++) {
            out.add(new Spot(springX + 6, floor.applyAsInt(springX) - 110 - i * 24));
        }
        return out;
    }

    /** Whether the farmer beat the champion (the champion keeps the title on a tie). */
    public static boolean farmerWins(int farmer, int champion) {
        return farmer > champion;
    }

    /** Who defends the title: Tails, or Sonic when Tails is the one farming. */
    public static String champion(Game game) {
        return game.farmer.equals("tails") ? "sonic" : "tails";
    }

    /**
     * The hunt's prizes: every ring found pays {@value #RINGS_EACH}; the first win takes the
     * Special Stage Record and the trophy, later wins a purse. Returns the notices.
     */
    public static List<String> reward(Game game, Festivals festivals, int farmer, int champion) {
        List<String> notices = new ArrayList<>();
        boolean won = farmerWins(farmer, champion);
        int year = game.calendar.year();
        festivals.record(FestivalBook.RING_HUNT, year, won ? 1 : 2, farmer);
        int pay = farmer * RINGS_EACH;
        if (won && !festivals.prizeTaken("trophy." + FestivalBook.RING_HUNT)) {
            festivals.takePrize("trophy." + FestivalBook.RING_HUNT);
            Prizes.give(game, festivals, "record_special_stage", 1, notices);
            notices.add("A GOLD RING FOR THE TROPHY STAND");
        } else if (won) {
            pay += WIN_PURSE;
        }
        game.rings += pay;
        notices.add(0, "+" + pay + " RINGS");
        return notices;
    }

    /**
     * The champion: flies straight for the nearest ring left, takes it on arrival and stops to
     * count it. Stepped once a tick by the hunt.
     */
    public static final class Champion {
        public float x;
        public float y;
        public int score;
        public boolean facingLeft;
        int target = -1;
        int pause;

        public Champion(float x, float y) {
            this.x = x;
            this.y = y;
        }

        /** Whether the champion is counting a ring (standing still). */
        public boolean counting() {
            return pause > 0;
        }

        /** One tick. {@code taken} is shared with the farmer; returns the ring taken this tick, or -1. */
        public int step(List<Spot> rings, boolean[] taken) {
            if (pause > 0) {
                pause--;
                return -1;
            }
            if (target < 0 || taken[target]) {
                target = nearest(rings, taken);
                if (target < 0) {
                    return -1;
                }
            }
            Spot s = rings.get(target);
            float dx = s.x() - x, dy = s.y() - y;
            float d = (float) Math.hypot(dx, dy);
            if (d <= CHAMPION_SPEED) {
                x = s.x();
                y = s.y();
                taken[target] = true;
                score++;
                pause = CHAMPION_PAUSE;
                int got = target;
                target = -1;
                return got;
            }
            x += dx / d * CHAMPION_SPEED;
            y += dy / d * CHAMPION_SPEED;
            if (Math.abs(dx) > 0.5f) {
                facingLeft = dx < 0;
            }
            return -1;
        }

        private int nearest(List<Spot> rings, boolean[] taken) {
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < rings.size(); i++) {
                if (taken[i]) {
                    continue;
                }
                float d = Math.abs(rings.get(i).x() - x) + Math.abs(rings.get(i).y() - y) * 1.5f;
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            return best;
        }
    }
}
