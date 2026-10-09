package starpost.festivals;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.people.People;
import starpost.valley.Runner;

/**
 * The Great Valley Race's rules (Summer 11): twice round a Green Hill track with springs and
 * loops, on the ported Sonic controller. The heroes race on the same physics as the farmer with
 * scripted pads: Tails (or Sonic) charges a spin dash on the line, Knuckles just runs, and both
 * jump whatever stops them. Robotnik flies the Egg Mobile over everything at his own pace and,
 * once a lap, fires its booster when he falls behind. A farmer who only holds right finishes
 * with the pack; spin dashing and keeping the roll wins. Engine-free: the rivals step over any
 * {@link Runner.Ground}.
 */
public final class Race {
    public static final int LAPS = 2;
    /** The countdown before GO, in ticks (three, two, one). */
    public static final int COUNTDOWN = 180;
    /** Robotnik: cruising and boosting speed, how long a boost lasts, and how far behind triggers it. */
    public static final float EGG_CRUISE = 5.4f;
    public static final float EGG_BOOST = 8.0f;
    public static final int EGG_BOOST_TICKS = 150;
    public static final int EGG_BEHIND = 300;
    /** The Egg Mobile's height above the track. */
    public static final int EGG_HEIGHT = 58;
    /** Prizes by place, and the first win's Speed Shoes. */
    public static final int FIRST_PURSE = 1000;
    public static final int SECOND_PURSE = 400;
    public static final int THIRD_PURSE = 200;
    public static final int LAST_PURSE = 50;

    private Race() {
    }

    /** Who races the farmer: Tails or Sonic, Knuckles once he has arrived, and Robotnik. */
    public static List<String> rivals(Game game) {
        List<String> out = new ArrayList<>();
        People people = game.section(People.class);
        for (String id : new String[] {"tails", "sonic", "knuckles"}) {
            if (id.equals(game.farmer)) {
                continue;
            }
            if (id.equals("sonic") && !game.farmer.equals("tails")) {
                continue;   // Sonic races only when Tails farms (he'd lap the field otherwise)
            }
            if (people == null || people.cast.get(id) == null || people.present(people.cast.get(id), game)) {
                out.add(id);
            }
        }
        out.add("robotnik");
        return out;
    }

    /** A rival's held pace (fraction of top speed) and whether they spin dash off the line. */
    static float pace(String who) {
        return switch (who) {
            case "knuckles" -> 0.98f;
            default -> 1f;
        };
    }

    static boolean dashesOffTheLine(String who) {
        return !who.equals("knuckles");
    }

    /** One racer besides the farmer. */
    public static final class Rival {
        public final String who;
        public final Runner runner;
        /** Where the rival started (laps are counted from here). */
        public final float startX;
        /** The tick (from GO) the rival crossed the line, or -1. */
        public int finish = -1;
        /** Robotnik's Egg Mobile: no runner, it flies. */
        public float eggX;
        public float eggY;
        int boostLeft;
        int boostLap = -1;
        private boolean dashReleased;

        public Rival(String who, float x, float y) {
            this.who = who;
            this.runner = new Runner(x, y);
            this.startX = x;
            this.eggX = x;
            this.eggY = y - EGG_HEIGHT;
        }

        public boolean flies() {
            return who.equals("robotnik");
        }

        public float x() {
            return flies() ? eggX : runner.x;
        }

        public boolean boosting() {
            return boostLeft > 0;
        }

        /**
         * One tick. {@code tick} counts from GO (negative during the countdown), {@code leader}
         * is the furthest x of the field, {@code lapLength} the track's length.
         */
        public boolean step(Runner.Ground ground, int tick, float leader, int lapLength) {
            if (flies()) {
                if (tick < 0) {
                    return false;
                }
                int lap = (int) Math.floor((eggX - startX) / lapLength);
                if (boostLeft == 0 && boostLap != lap && leader - eggX > EGG_BEHIND) {
                    boostLeft = EGG_BOOST_TICKS;
                    boostLap = lap;
                }
                float speed = boostLeft > 0 ? EGG_BOOST : EGG_CRUISE;
                if (boostLeft > 0) {
                    boostLeft--;
                }
                eggX += speed;
                int floor = ground.floorBelow(Math.round(eggX), 0);
                float target = Math.min(floor, 260) - EGG_HEIGHT + (float) Math.sin(tick / 20.0) * 4;
                eggY += (target - eggY) * 0.08f;
                return false;
            }
            Runner r = runner;
            boolean down = false, right = false, jump = false;
            if (tick < 0) {
                // On the line: crouch and rev the spin dash on the last second (or just wait).
                if (dashesOffTheLine(who) && tick > -60) {
                    down = true;
                    jump = tick % 14 == 0;
                }
            } else {
                if (dashesOffTheLine(who) && !dashReleased) {
                    dashReleased = true;     // GO: let the dash go
                } else if (r.rolling && r.onGround) {
                    // Keep the roll while it is faster than running; hop out of it after.
                    jump = Math.abs(r.speed) < pace(who) * Runner.TOP;
                } else {
                    right = r.speed < pace(who) * Runner.TOP;
                    jump = r.pushing && r.onGround;
                }
            }
            return r.step(ground, false, right, down, jump, jump || !r.onGround, tick);
        }
    }

    /** The farmer's place: one plus everyone who finished first (a dead heat goes to the farmer). */
    public static int place(int farmerFinish, List<Rival> rivals) {
        int place = 1;
        for (Rival r : rivals) {
            if (r.finish >= 0 && (farmerFinish < 0 || r.finish < farmerFinish)) {
                place++;
            }
        }
        return place;
    }

    /**
     * The race's prizes: the first win brings the Speed Shoes (+{@value Prizes#SPEED_SHOES}
     * Momentum for good) and the trophy; every place pays a purse. Returns notices.
     */
    public static List<String> reward(Game game, Festivals festivals, int place, int ticks) {
        List<String> notices = new ArrayList<>();
        festivals.record(FestivalBook.RACE, game.calendar.year(), place, Math.max(0, 5 - place));
        if (place == 1) {
            festivals.recordTime(FestivalBook.RACE, ticks);
            if (festivals.takePrize("trophy." + FestivalBook.RACE)) {
                game.maxMomentum += Prizes.SPEED_SHOES;
                game.momentum = game.maxMomentum;
                game.flags.add("speed_shoes");
                notices.add("SPEED SHOES! +" + Prizes.SPEED_SHOES + " MOMENTUM FOR GOOD");
                notices.add("A STAR POST FOR THE TROPHY STAND");
            }
        }
        int purse = switch (place) {
            case 1 -> FIRST_PURSE;
            case 2 -> SECOND_PURSE;
            case 3 -> THIRD_PURSE;
            default -> LAST_PURSE;
        };
        game.rings += purse;
        notices.add(0, "+" + purse + " RINGS");
        Prizes.everyone(game, 20);
        return notices;
    }
}
