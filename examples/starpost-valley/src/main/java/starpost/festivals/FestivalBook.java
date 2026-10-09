package starpost.festivals;

import static starpost.core.Calendar.FALL;
import static starpost.core.Calendar.SPRING;
import static starpost.core.Calendar.SUMMER;
import static starpost.core.Calendar.WINTER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import starpost.core.Calendar;

/**
 * The valley's calendar of festivals (design doc §8), two a season. One year's scope: the same
 * dates come round each year.
 */
public final class FestivalBook {
    public static final String RING_HUNT = "ring_hunt";
    public static final String PARADE = "sunflower_parade";
    public static final String RACE = "valley_race";
    public static final String FLICKIES = "flickies";
    public static final String FAIR = "valley_fair";
    public static final String SCRAP_BRAIN = "scrap_brain_night";
    public static final String ICE_CAP = "ice_cap";
    public static final String FEAST = "star_light_feast";

    private final List<Festival> all = new ArrayList<>();

    public FestivalBook() {
        add(RING_HUNT, "RING HUNT", SPRING, 13, 900, 1400, "plaza", 120, "THE TOWN PLAZA",
                "60 SECONDS TO GRAB THE MOST RINGS IN TOWN. TAILS DEFENDS HIS TITLE.");
        add(PARADE, "SUNFLOWER PARADE", SPRING, 24, 1000, 1500, "plaza", 180, "THE TOWN PLAZA",
                "THE SUNFLOWERS BLOOM AND THE VALLEY MARCHES. BRING YOUR BEST FLOWER.");
        add(RACE, "GREAT VALLEY RACE", SUMMER, 11, 900, 1300, "plaza", 150, "THE TOWN PLAZA",
                "ONCE ROUND THE GREEN HILL TRACK. ROBOTNIK HAS ENTERED THE EGG MOBILE.");
        add(FLICKIES, "NIGHT OF THE FLICKIES", SUMMER, 28, 2000, 2400, "meadow", 150, "THE MEADOW BY THE LAKE",
                "THE FLICKIES FLY SOUTH OVER THE LAKE. COME AND WAVE.");
        add(FAIR, "VALLEY FAIR", FALL, 16, 900, 1600, "plaza", 240, "THE TOWN PLAZA",
                "GRANGE DISPLAYS, SLOTS AND THE SPRING TEST. JUDGE: DR. ROBOTNIK.");
        add(SCRAP_BRAIN, "SCRAP BRAIN NIGHT", FALL, 27, 1900, 2400, "plaza", 120, "THE TOWN PLAZA",
                "A HAUNTED MAZE OF SCRAP BRAIN STEEL. SOMETHING WAITS AT THE END.");
        add(ICE_CAP, "ICE CAP FESTIVAL", WINTER, 8, 900, 1500, "meadow", 180, "THE MEADOW BY THE LAKE",
                "FROST'S FESTIVAL: THE CONTEST ON THE ICE AND THE SNOWBOARD RUN.");
        add(FEAST, "STAR LIGHT FEAST", WINTER, 25, 1700, 2300, "plaza", 240, "THE TOWN PLAZA",
                "A FEAST UNDER THE STAR LIGHT, AND A SECRET GIFT FOR A FRIEND.");
    }

    private void add(String id, String name, int season, int day, int open, int close, String anchor, int length,
            String where, String blurb) {
        all.add(new Festival(id, name, season, day, open, close, anchor, length, where, blurb));
    }

    public List<Festival> all() {
        return Collections.unmodifiableList(all);
    }

    public Festival get(String id) {
        for (Festival f : all) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }

    /** The festival on a date, or null. */
    public Festival on(int season, int day) {
        for (Festival f : all) {
            if (f.on(season, day)) {
                return f;
            }
        }
        return null;
    }

    /** Today's festival, or null. */
    public Festival today(Calendar calendar) {
        return on(calendar.season(), calendar.day());
    }

    /** Tomorrow's festival, or null (the eve's notices). */
    public Festival tomorrow(Calendar calendar) {
        return calendar.day() < Calendar.DAYS_PER_SEASON ? on(calendar.season(), calendar.day() + 1)
                : on((calendar.season() + 1) % 4, 1);
    }
}
