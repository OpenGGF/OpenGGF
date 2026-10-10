package starpost.people;

/**
 * One thing a villager can say, with the conditions it needs. Written fluently by the cast:
 * {@code v.line("RAIN AGAIN. GOOD FOR THE RADISHES.").spring().rain()}.
 *
 * <p>Text is upper case (the engine font). {@code {FARMER}} becomes the farmer's name and
 * {@code {FARM}} the farm's; {@code |} starts a new page. Animals speak in pictures until the
 * Chirp Translator: {@link #pic} gives the picture version, otherwise one is read from the
 * words ({@link Pictures#fromText}).
 *
 * <p>A <em>special</em> line (first meeting, birthday, after an event, a date) pre-empts the
 * daily pool; otherwise a line is drawn from every matching line, weighted toward the more
 * specific ones (rain, season, hearts), avoiding the speaker's last few.
 */
public final class Line {
    private final String text;
    private String[] pictures;
    private int seasons = 15;
    private int weather = Schedule.ANY;
    private int minHearts;
    private int maxHearts = 10;
    private int weekdays = 127;
    private int fromMinute = 0;
    private int toMinute = 27 * 60;
    private String farmer;
    private String needFlag;
    private String withoutFlag;
    private String afterEvent;
    private int afterDays;
    private boolean first;
    private boolean birthday;
    private int dateSeason = -1;
    private int dateDay;
    private int yearOne;          // 0 any year, 1 only year 1, 2 only after year 1
    private int weight = 1;

    public Line(String text) {
        if (!text.equals(text.toUpperCase())) {
            throw new IllegalArgumentException("Lines are upper case: " + text);
        }
        this.text = text;
    }

    public String text() {
        return text;
    }

    /** The authored picture version (tokens for {@link Pictures}), or null to read it from the words. */
    public String[] pictures() {
        return pictures == null ? null : pictures.clone();
    }

    // ------------------------------------------------------------------ conditions

    public Line pic(String... tokens) {
        pictures = tokens.clone();
        return this;
    }

    public Line spring() {
        return seasons(0);
    }

    public Line summer() {
        return seasons(1);
    }

    public Line fall() {
        return seasons(2);
    }

    public Line winter() {
        return seasons(3);
    }

    public Line seasons(int... list) {
        seasons = 0;
        for (int s : list) {
            seasons |= 1 << s;
        }
        return this;
    }

    public Line rain() {
        weather = Schedule.RAIN;
        return this;
    }

    public Line dry() {
        weather = Schedule.DRY;
        return this;
    }

    /** At least this many hearts. */
    public Line hearts(int min) {
        minHearts = min;
        return this;
    }

    /** Fewer than this many hearts. */
    public Line below(int hearts) {
        maxHearts = hearts - 1;
        return this;
    }

    public Line weekdays(int... list) {
        weekdays = 0;
        for (int d : list) {
            weekdays |= 1 << d;
        }
        return this;
    }

    /** Only between two clock times ({@code hhmm}). */
    public Line between(int fromHhmm, int toHhmm) {
        fromMinute = Schedule.minutes(fromHhmm);
        toMinute = Schedule.minutes(toHhmm);
        return this;
    }

    /** Only when this hero farms ("sonic", "tails", "knuckles"). */
    public Line farmer(String code) {
        farmer = code;
        return this;
    }

    public Line flag(String storyFlag) {
        needFlag = storyFlag;
        return this;
    }

    public Line without(String storyFlag) {
        withoutFlag = storyFlag;
        return this;
    }

    /** Special: within {@code days} days after a heart event was seen. */
    public Line after(String event, int days) {
        afterEvent = event;
        afterDays = days;
        return this;
    }

    /** Special: the first time the farmer talks to them. */
    public Line first() {
        first = true;
        return this;
    }

    /** Special: on their birthday. */
    public Line birthday() {
        birthday = true;
        return this;
    }

    /** Special: on a date (a festival's eve, say). */
    public Line on(int season, int day) {
        dateSeason = season;
        dateDay = day;
        return this;
    }

    public Line yearOne() {
        yearOne = 1;
        return this;
    }

    public Line laterYears() {
        yearOne = 2;
        return this;
    }

    /** Makes the line more (or less) likely in the daily pool. */
    public Line weight(int value) {
        weight = Math.max(1, value);
        return this;
    }

    // ------------------------------------------------------------------ queries

    public boolean special() {
        return first || birthday || afterEvent != null || dateSeason >= 0;
    }

    public boolean isFirst() {
        return first;
    }

    public String afterEvent() {
        return afterEvent;
    }

    /** Whether the line can be said now. First-meeting lines match only before the first talk. */
    public boolean matches(Situation s) {
        if (first && s.met()) {
            return false;
        }
        if ((seasons & 1 << s.season()) == 0 || (weekdays & 1 << s.weekday()) == 0) {
            return false;
        }
        if (weather != Schedule.ANY && weather != (s.raining() ? Schedule.RAIN : Schedule.DRY)) {
            return false;
        }
        if (s.hearts() < minHearts || s.hearts() > maxHearts) {
            return false;
        }
        if (s.minutes() < fromMinute || s.minutes() >= toMinute) {
            return false;
        }
        if (farmer != null && !farmer.equals(s.farmer())) {
            return false;
        }
        if (needFlag != null && !s.flags().contains(needFlag) || withoutFlag != null && s.flags().contains(withoutFlag)) {
            return false;
        }
        if (yearOne == 1 && s.year() != 1 || yearOne == 2 && s.year() == 1) {
            return false;
        }
        if (birthday && !s.birthday()) {
            return false;
        }
        if (dateSeason >= 0 && (s.season() != dateSeason || s.day() != dateDay)) {
            return false;
        }
        return afterEvent == null || s.seenEvent(afterEvent) && s.daysSince(afterEvent) <= afterDays;
    }

    /** How strongly the line competes: specific lines win more often than general ones. */
    public int weight() {
        int w = weight;
        if (weather == Schedule.RAIN) {
            w *= 4;
        }
        if (seasons != 15) {
            w *= 2;
        }
        if (minHearts >= 2) {
            w *= 2;
        }
        if (farmer != null || needFlag != null) {
            w *= 2;
        }
        return w;
    }

    /** Among special lines, the more specific wins: first meeting, birthday, a date, then an event. */
    public int rank() {
        return (first ? 1000 : 0) + (birthday ? 500 : 0) + (dateSeason >= 0 ? 200 : 0) + (afterEvent != null ? 100 : 0)
                + minHearts + (farmer != null ? 10 : 0);
    }
}
