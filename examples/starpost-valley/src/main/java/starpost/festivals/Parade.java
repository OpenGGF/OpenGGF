package starpost.festivals;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.people.People;

/**
 * The Sunflower Parade's judging (Spring 24): the parade ends at the plaza, where Dandel judges
 * the valley's best flower. A sunflower beats every other flower; Pud's he painted gold; and
 * Robotnik's entry is a Robomart plastic sunflower, disqualified the moment Dandel sniffs it.
 * Engine-free.
 */
public final class Parade {
    /** Rings for second place, a first place after the first, and the seeds for every spectator. */
    public static final int SECOND_PURSE = 200;
    public static final int REPEAT_PURSE = 500;
    public static final int SPECTATOR_SEEDS = 3;

    /** Someone's entry and its score. */
    public record Entry(String who, String flower, int score) {
    }

    private Parade() {
    }

    /** The judge's score for an entry, by what it is (0: not a flower, so not an entry). */
    public static int score(String itemId) {
        return switch (itemId) {
            case "super_sunflower" -> 100;      // gold as a Super Ring
            case "sunflower" -> 80;
            case "spring_tulip" -> 55;
            case "hill_daffodil" -> 45;
            default -> 0;
        };
    }

    /** Whether an item can be entered (a flower). */
    public static boolean flower(String itemId) {
        return score(itemId) > 0;
    }

    /** The neighbours' entries, for those in the valley today. Robotnik's never counts. */
    public static List<Entry> rivals(Game game) {
        List<Entry> out = new ArrayList<>();
        People people = game.section(People.class);
        add(out, people, game, "clementine", "SUNFLOWER", 76);
        add(out, people, game, "pud", "GOLD-PAINTED SUNFLOWER", 62);
        add(out, people, game, "hazel", "HILL DAFFODIL", 41);
        add(out, people, game, "robotnik", "ROBO-SUNFLOWER (PLASTIC)", 0);
        return out;
    }

    private static void add(List<Entry> out, People people, Game game, String id, String flower, int score) {
        if (people == null || people.cast.get(id) == null || people.present(people.cast.get(id), game)) {
            out.add(new Entry(id, flower, score));
        }
    }

    /** The farmer's place among the entries (ties go to the farmer); 0 when there is no entry. */
    public static int place(int farmerScore, List<Entry> rivals) {
        if (farmerScore <= 0) {
            return 0;
        }
        int place = 1;
        for (Entry e : rivals) {
            if (e.score() > farmerScore) {
                place++;
            }
        }
        return place;
    }

    /**
     * The parade's prizes. First place: the first time, a Super Sunflower Seed (Pud's golden seed),
     * the rosette on the trophy shelf and Dandel's gratitude; after that, a purse. Second: a smaller
     * purse. Everyone else: Dandel hands out sunflower seeds. Returns the notices.
     */
    public static List<String> reward(Game game, Festivals festivals, String entered) {
        List<String> notices = new ArrayList<>();
        int score = entered == null ? 0 : score(entered);
        int place = place(score, rivals(game));
        festivals.record(FestivalBook.PARADE, game.calendar.year(), place, score);
        if (place == 1) {
            if (festivals.takePrize("trophy." + FestivalBook.PARADE)) {
                Prizes.give(game, festivals, "super_sunflower_seeds", 1, notices);
                notices.add("A SUNFLOWER FOR THE TROPHY STAND");
                Prizes.friendship(game, "dandel", 100);
            } else {
                game.rings += REPEAT_PURSE;
                notices.add("+" + REPEAT_PURSE + " RINGS");
            }
        } else if (place == 2) {
            game.rings += SECOND_PURSE;
            notices.add("+" + SECOND_PURSE + " RINGS");
        } else {
            Prizes.give(game, festivals, "sunflower_seeds", SPECTATOR_SEEDS, notices);
        }
        Prizes.everyone(game, 20);
        return notices;
    }
}
