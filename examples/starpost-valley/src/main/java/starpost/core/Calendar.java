package starpost.core;

/**
 * The valley's clock: 28-day seasons, days from 06:00 to 02:00. Ten game minutes pass every
 * seven seconds while the player is free to act (420 ticks), so a whole day is 14 minutes;
 * menus, dialogue and cutscenes stop the clock.
 */
public final class Calendar {
    public static final int SPRING = 0;
    public static final int SUMMER = 1;
    public static final int FALL = 2;
    public static final int WINTER = 3;
    public static final int DAYS_PER_SEASON = 28;
    public static final int DAY_START = 6 * 60;
    public static final int DAY_END = 26 * 60;          // 02:00 the next morning
    public static final int TICKS_PER_TEN_MINUTES = 420;

    private int year = 1;
    private int season = SPRING;
    private int day = 1;
    private int minutes = DAY_START;
    private int tickInTen;
    /** Ticks per ten game minutes: 420 (a 14-minute day), 600 (20) or 840 (28); a player setting. */
    private int ticksPerTen = TICKS_PER_TEN_MINUTES;

    public void setDayMinutes(int realMinutes) {
        ticksPerTen = Math.max(1, realMinutes * 60 * 60 / 120);
    }

    public int year() {
        return year;
    }

    public int season() {
        return season;
    }

    public int day() {
        return day;
    }

    public int minutes() {
        return minutes;
    }

    /** Days since the first morning, from 0. */
    public int dayNumber() {
        return ((year - 1) * 4 + season) * DAYS_PER_SEASON + day - 1;
    }

    /** 0 Monday ... 6 Sunday. */
    public int weekday() {
        return (day - 1) % 7;
    }

    /** One tick of free play. Returns true when the clock moved on ten minutes. */
    public boolean tick() {
        if (++tickInTen < ticksPerTen) {
            return false;
        }
        tickInTen = 0;
        minutes = Math.min(DAY_END, minutes + 10);
        return true;
    }

    public boolean overtime() {
        return minutes >= DAY_END;
    }

    /** The light: 0 day, 1 dusk (18:00-20:00), 2 night (20:00 on). */
    public int light() {
        return minutes >= 20 * 60 ? 2 : minutes >= 18 * 60 ? 1 : 0;
    }

    /** The next morning. */
    public void nextDay() {
        minutes = DAY_START;
        tickInTen = 0;
        if (++day > DAYS_PER_SEASON) {
            day = 1;
            if (++season > WINTER) {
                season = SPRING;
                year++;
            }
        }
    }

    public void set(int year, int season, int day, int minutes) {
        this.year = Math.max(1, year);
        this.season = Math.floorMod(season, 4);
        this.day = Math.max(1, Math.min(DAYS_PER_SEASON, day));
        this.minutes = Math.max(DAY_START, Math.min(DAY_END, minutes));
        tickInTen = 0;
    }

    /** Exact in-memory clock state, including the partial ten-minute tick and day setting. */
    public record Snapshot(int year, int season, int day, int minutes, int tickInTen, int ticksPerTen) {}

    public Snapshot capture() {
        return new Snapshot(year, season, day, minutes, tickInTen, ticksPerTen);
    }

    public void restore(Snapshot state) {
        year = state.year(); season = state.season(); day = state.day(); minutes = state.minutes();
        tickInTen = state.tickInTen(); ticksPerTen = state.ticksPerTen();
    }

    /** "6:40AM", the S1 HUD's TIME. */
    public String clock() {
        int h = minutes / 60 % 24, m = minutes % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + (m < 10 ? "0" : "") + m + (h < 12 ? "AM" : "PM");
    }

    public static String seasonName(int season) {
        return switch (season) {
            case SPRING -> "SPRING";
            case SUMMER -> "SUMMER";
            case FALL -> "FALL";
            default -> "WINTER";
        };
    }

    public static String weekdayName(int weekday) {
        return switch (weekday) {
            case 0 -> "MON";
            case 1 -> "TUE";
            case 2 -> "WED";
            case 3 -> "THU";
            case 4 -> "FRI";
            case 5 -> "SAT";
            default -> "SUN";
        };
    }
}
