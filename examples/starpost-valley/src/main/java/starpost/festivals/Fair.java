package starpost.festivals;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Kind;
import starpost.valley.Runner;

/**
 * The Valley Fair's rules (Fall 16): the grange display Robotnik judges, the slot booth and the
 * spring test. Engine-free.
 *
 * <p><b>The grange display.</b> Up to nine goods, scored for variety (kinds and different
 * items), value and a full table. Robotnik judges, and he is biased: every Robomart good on a
 * table impresses him, and his own Robomart Deluxe Hamper always scores {@value #ROBOTNIK}. He
 * awards himself first unless the farmer's display beats his by {@value #UNDENIABLE}: undeniable.
 *
 * <p><b>The slot booth</b> pays like Sonic 2's Casino Night slot machine (SlotMachine_ChooseReward
 * and SlotRingRewards in s2.asm): three of a face pays its reward, a Jackpot beside a pair
 * doubles or quadruples it, bars pay two each, and three Robotniks take rings instead.
 *
 * <p><b>The spring test</b>: stop the power meter, and the spring launches the farmer up the
 * column with Sonic's own gravity; the bell is at the top.
 */
public final class Fair {
    public static final int DISPLAY_SLOTS = 9;
    public static final int ROBOTNIK = 78;
    public static final int UNDENIABLE = 15;
    public static final int ROBOMART_BIAS = 12;
    public static final int CLEMENTINE = 52;
    public static final int FIRST_PURSE = 1500;
    public static final int REPEAT_PURSE = 800;
    public static final int SECOND_PURSE = 300;
    public static final int THIRD_PURSE = 100;

    /** Slot faces in Sonic 2's order (the slot pictures' order in its ROM). */
    public static final int SONIC = 0;
    public static final int TAILS = 1;
    public static final int ROBOTNIK_FACE = 2;
    public static final int JACKPOT = 3;
    public static final int RING = 4;
    public static final int BAR = 5;
    /** A spin's price and how many rings a slot reward ring is worth at the booth. */
    public static final int SPIN_COST = 25;
    public static final int RING_VALUE = 5;
    /** Three Robotniks: his spike balls take this many rings. */
    public static final int ROBOTNIK_TAKES = 100;

    /** The spring test: the column's bell, the meter's period, the launch range (pixels a tick). */
    public static final int BELL = 400;
    public static final int METER_PERIOD = 64;
    public static final float LAUNCH_MIN = 6f;
    public static final float LAUNCH_MAX = 14.5f;

    private Fair() {
    }

    // ------------------------------------------------------------------ the grange display

    /** Goods that can go on a display: anything sellable that isn't a tool, seed or placeable. */
    public static boolean displayable(Item item) {
        return item.price() > 0 && item.kind() != Kind.TOOL && item.kind() != Kind.SEED
                && item.kind() != Kind.PLACEABLE;
    }

    /** The farmer's display score as Robotnik sees it. */
    public static int score(Game game, List<String> display) {
        Set<Kind> kinds = new LinkedHashSet<>();
        Set<String> different = new LinkedHashSet<>();
        int value = 0, robomart = 0, count = 0;
        for (String id : display) {
            if (id == null || !game.catalog.hasItem(id)) {
                continue;
            }
            Item item = game.item(id);
            if (!displayable(item)) {
                continue;
            }
            count++;
            kinds.add(item.kind());
            different.add(id);
            value += item.price();
            if (robomart(id)) {
                robomart++;
            }
        }
        if (count == 0) {
            return 0;
        }
        int score = kinds.size() * 6 + different.size() * 3 + Math.min(35, value / 25)
                + (count >= DISPLAY_SLOTS ? 5 : 0) + robomart * ROBOMART_BIAS;
        return score;
    }

    /** Robomart's own goods, which Robotnik cannot help admiring. */
    public static boolean robomart(String id) {
        return id.equals("robo_cola");
    }

    /** The farmer's place: first only when undeniable; then Robotnik; Clementine's table after him. */
    public static int place(int score) {
        if (score <= 0) {
            return 0;
        }
        if (score >= ROBOTNIK + UNDENIABLE) {
            return 1;
        }
        return score >= CLEMENTINE ? 2 : 3;
    }

    /**
     * The fair's prizes for the display (the goods go back to the farmer). First: the blue
     * ribbon on the trophy shelf and a purse (smaller in later years), and Robotnik's grudging
     * respect. Second: an honourable mention. Returns notices.
     */
    public static List<String> reward(Game game, Festivals festivals, int score) {
        List<String> notices = new ArrayList<>();
        int place = place(score);
        festivals.record(FestivalBook.FAIR, game.calendar.year(), place, score);
        if (place == 1) {
            int purse = festivals.takePrize("trophy." + FestivalBook.FAIR) ? FIRST_PURSE : REPEAT_PURSE;
            if (purse == FIRST_PURSE) {
                notices.add("A BUMPER FOR THE TROPHY STAND");
            }
            game.rings += purse;
            notices.add(0, "+" + purse + " RINGS");
            Prizes.friendship(game, "robotnik", 120);
        } else if (place == 2) {
            game.rings += SECOND_PURSE;
            notices.add("+" + SECOND_PURSE + " RINGS. SO CLOSE.");
        } else if (place == 3) {
            game.rings += THIRD_PURSE;
            notices.add("+" + THIRD_PURSE + " RINGS. AN HONOURABLE MENTION.");
        }
        Prizes.everyone(game, 20);
        return notices;
    }

    // ------------------------------------------------------------------ the slot booth

    /** Sonic 2's reel strips (SlotSequence1-3 in s2.asm). */
    public static int[] strip(int reel) {
        return switch (reel) {
            case 1 -> new int[] {3, 0, 1, 4, 2, 5, 0, 2};
            default -> new int[] {3, 0, 1, 4, 2, 5, 4, 1};
        };
    }

    /** SlotRingRewards: rings for three of a face (Robotnik's -1 means he takes rings). */
    static int faceReward(int face) {
        return switch (face) {
            case SONIC -> 30;
            case TAILS -> 25;
            case ROBOTNIK_FACE -> -1;
            case JACKPOT -> 150;
            case RING -> 10;
            default -> 20;
        };
    }

    /**
     * SlotMachine_ChooseReward, face for face: the slot-ring reward for three reels (negative
     * when Robotnik's spike balls come instead). Slot 1 is {@code a}.
     */
    public static int slotReward(int a, int b, int c) {
        if (a == b && a == c) {
            return faceReward(b);                          // triple match: slot 2's reward
        }
        if (a == c) {                                      // SlotMachine_Match13
            if (c == JACKPOT) {
                return times(faceReward(b), 4);
            }
            if (b == JACKPOT) {
                return times(faceReward(c), 2);
            }
            return bars(a, b, c);
        }
        if (a == b) {                                      // SlotMachine_Match12
            if (b == JACKPOT) {
                return times(faceReward(c), 4);
            }
            if (c == JACKPOT) {
                return times(faceReward(b), 2);
            }
            return bars(a, b, c);
        }
        if (b == c) {                                      // SlotMachine_Unmatched1
            if (a == JACKPOT) {
                return times(faceReward(b), 2);
            }
            if (b == JACKPOT) {
                return times(faceReward(a), 4);
            }
        }
        return bars(a, b, c);
    }

    /** SlotMachine_QuadrupleUp / DoubleUp, keeping Robotnik's -1 a loss. */
    private static int times(int reward, int factor) {
        return reward < 0 ? reward : reward * factor;
    }

    /** SlotMachine_CheckBars: two rings per bar. */
    private static int bars(int a, int b, int c) {
        return (a == BAR ? 2 : 0) + (b == BAR ? 2 : 0) + (c == BAR ? 2 : 0);
    }

    /** What a spin pays at the booth in valley rings (negative: rings taken). */
    public static int payout(int a, int b, int c) {
        int reward = slotReward(a, b, c);
        return reward < 0 ? -ROBOTNIK_TAKES : reward * RING_VALUE;
    }

    // ------------------------------------------------------------------ the spring test

    /** The power meter at a tick: a triangle wave from 0 to 100 and back. */
    public static int meter(int tick) {
        int t = Math.floorMod(tick, METER_PERIOD);
        int half = METER_PERIOD / 2;
        return (t < half ? t : METER_PERIOD - t) * 100 / half;
    }

    /** The spring's launch speed for a meter reading. */
    public static float launch(int power) {
        return LAUNCH_MIN + (LAUNCH_MAX - LAUNCH_MIN) * Math.max(0, Math.min(100, power)) / 100f;
    }

    /** How high a launch carries the farmer, under Sonic's gravity ($38 a frame). */
    public static int height(int power) {
        float v = launch(power);
        return Math.round(v * v / (2 * Runner.GRAVITY));
    }

    /** Rings for a height: the bell pays most. */
    public static int strengthRings(int height) {
        if (height >= BELL) {
            return 120;
        }
        return height >= BELL * 3 / 4 ? 40 : height >= BELL / 2 ? 15 : 0;
    }
}
