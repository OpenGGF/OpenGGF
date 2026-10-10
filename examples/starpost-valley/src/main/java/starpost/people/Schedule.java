package starpost.people;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Where a villager is through the day. A schedule is a list of {@link Plan}s, each a run of
 * timed stops ("06:00 inside the inn, 09:00 the plaza, 18:00 the inn porch") with the
 * conditions it applies under: seasons, weekdays, rain or dry, a story flag. The most specific
 * matching plan wins (flag, then weather, then weekday, then season; ties go to the first
 * written); within it, the last stop at or before the time.
 *
 * <p>Times are written {@code hhmm} as the clock shows them (600 is 06:00, 2400 midnight,
 * 2530 half past one at night).
 */
public final class Schedule {
    public static final int ANY = 0;
    public static final int DRY = 1;
    public static final int RAIN = 2;
    private static final int ALL_SEASONS = 15;
    private static final int ALL_DAYS = 127;

    /** One timed stop. */
    public record Stop(int minute, Spot spot) {
    }

    /** One way the day can go. */
    public static final class Plan {
        private int seasons = ALL_SEASONS;
        private int weekdays = ALL_DAYS;
        private int weather = ANY;
        private String flag;
        private final List<Stop> stops = new ArrayList<>();

        /** In the valley at an anchor, {@code dx} pixels east of it. */
        public Plan at(int hhmm, String anchor, int dx) {
            return stop(hhmm, Spot.valley(anchor, dx));
        }

        public Plan at(int hhmm, String anchor) {
            return at(hhmm, anchor, 0);
        }

        /** Indoors at a doorway (not drawn). */
        public Plan inside(int hhmm, String place) {
            return stop(hhmm, Spot.indoors(place));
        }

        /** Visiting the farm: a farm anchor, offset, and depth into the field. */
        public Plan farm(int hhmm, String anchor, int dx, int depth) {
            return stop(hhmm, Spot.onFarm(anchor, dx, depth));
        }

        private Plan stop(int hhmm, Spot spot) {
            int minute = minutes(hhmm);
            if (!stops.isEmpty() && minute <= stops.get(stops.size() - 1).minute()) {
                throw new IllegalArgumentException("Stops must run forward in time: " + hhmm);
            }
            stops.add(new Stop(minute, spot));
            return this;
        }

        /** Only in these seasons ({@code Calendar.SPRING}...). */
        public Plan seasons(int... list) {
            seasons = 0;
            for (int s : list) {
                seasons |= 1 << s;
            }
            return this;
        }

        /** Only on these weekdays (0 Monday ... 6 Sunday). */
        public Plan weekdays(int... list) {
            weekdays = 0;
            for (int d : list) {
                weekdays |= 1 << d;
            }
            return this;
        }

        public Plan rain() {
            weather = RAIN;
            return this;
        }

        public Plan dry() {
            weather = DRY;
            return this;
        }

        /** Only once a story flag is set. */
        public Plan when(String storyFlag) {
            flag = storyFlag;
            return this;
        }

        boolean matches(int season, int weekday, boolean raining, Set<String> flags) {
            return (seasons & 1 << season) != 0 && (weekdays & 1 << weekday) != 0
                    && (weather == ANY || weather == (raining ? RAIN : DRY))
                    && (flag == null || flags.contains(flag));
        }

        int specificity() {
            return (flag != null ? 8 : 0) + (weather != ANY ? 4 : 0) + (weekdays != ALL_DAYS ? 2 : 0)
                    + (seasons != ALL_SEASONS ? 1 : 0);
        }

        public List<Stop> stops() {
            return Collections.unmodifiableList(stops);
        }
    }

    private final List<Plan> plans = new ArrayList<>();

    /** Starts a new plan; give it conditions and stops. */
    public Plan plan() {
        Plan plan = new Plan();
        plans.add(plan);
        return plan;
    }

    public List<Plan> plans() {
        return Collections.unmodifiableList(plans);
    }

    /** The plan for a day, or null when none applies. */
    public Plan planFor(int season, int weekday, boolean raining, Set<String> flags) {
        Plan best = null;
        for (Plan plan : plans) {
            if (!plan.stops.isEmpty() && plan.matches(season, weekday, raining, flags)
                    && (best == null || plan.specificity() > best.specificity())) {
                best = plan;
            }
        }
        return best;
    }

    /** Where the villager is at a time of day ({@code minutes} since midnight), or null with no plan. */
    public Spot resolve(int season, int weekday, boolean raining, int minutes, Set<String> flags) {
        Plan plan = planFor(season, weekday, raining, flags);
        if (plan == null) {
            return null;
        }
        Spot spot = plan.stops.get(0).spot();
        for (Stop stop : plan.stops) {
            if (stop.minute() <= minutes) {
                spot = stop.spot();
            }
        }
        return spot;
    }

    /** {@code hhmm} as minutes since midnight (2530 is 25 * 60 + 30). */
    public static int minutes(int hhmm) {
        int h = hhmm / 100, m = hhmm % 100;
        if (m >= 60 || h < 0 || h > 26) {
            throw new IllegalArgumentException("Not a time: " + hhmm);
        }
        return h * 60 + m;
    }
}
