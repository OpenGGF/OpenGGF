package starpost.festivals;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.people.People;
import java.util.function.IntUnaryOperator;

/** Great Valley Race rules and scripted competitors; the farmer uses the native S3K player.
 * Rivals follow the ROM-backed course surface; they do not implement a second player controller. */
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

    /** One scripted competitor. Distances are cumulative across the circuit's laps. */
    public static final class Rival {
        public final String who;
        public final float startX;
        public int finish = -1;
        public float eggX, eggY;
        private int boostLeft, boostLap = -1;
        public Rival(String who, float x, float y) {
            this.who=who; startX=x; eggX=x; eggY=y-(flies()?EGG_HEIGHT:0);
        }
        public boolean flies() { return who.equals("robotnik"); }
        public float x() { return eggX; }
        public boolean boosting() { return boostLeft>0; }
        public void step(IntUnaryOperator floor, int tick, float leader, int lapLength) {
            if(tick<0 || finish>=0) return;
            int lap=(int)((eggX-startX)/lapLength);
            if(flies() && boostLeft==0 && boostLap!=lap && leader-eggX>EGG_BEHIND) {
                boostLeft=EGG_BOOST_TICKS; boostLap=lap;
            }
            float speed=flies()?(boostLeft>0?EGG_BOOST:EGG_CRUISE):6*pace(who);
            // The dash off the line is the competitor's authored advantage, not player physics.
            if(!flies() && dashesOffTheLine(who) && tick<60) speed=8;
            if(boostLeft>0) boostLeft--;
            eggX+=speed;
            int surface=floor.applyAsInt(Math.round(eggX));
            eggY=surface-(flies()?EGG_HEIGHT:0);
        }
        public record Snapshot(String who,float startX,int finish,float x,float y,int boostLeft,int boostLap) {}
        public Snapshot capture() { return new Snapshot(who,startX,finish,eggX,eggY,boostLeft,boostLap); }
        public static Rival restore(Snapshot s) {
            var r=new Rival(s.who(),s.startX(),s.y()); r.finish=s.finish(); r.eggX=s.x(); r.eggY=s.y();
            r.boostLeft=s.boostLeft(); r.boostLap=s.boostLap(); return r;
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
