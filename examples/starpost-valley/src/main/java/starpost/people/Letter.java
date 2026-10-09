package starpost.people;

/**
 * A letter the Flicky post brings in the morning. Each is sent once, the first morning its
 * conditions hold. A letter from an animal is drawn in pictures until the Chirp Translator.
 *
 * <pre>
 * cast.letter("robotnik_offer_1", "robotnik").on(SPRING, 3).yearOne()
 *         .text("DEAR NEIGHBOUR. I WILL BUY YOUR FARM...");
 * </pre>
 */
public final class Letter {
    public final String id;
    /** A villager id, or {@code post} for the Flicky post itself. */
    public final String from;
    private String text = "";
    private String[] pictures;
    private String item;
    private int count;
    private int season = -1;
    private int day;
    private int yearOne;
    private boolean firstMorning;
    private String afterEvent;
    private String flag;
    private String heartsOf;
    private int hearts;
    private String farmer;
    private String notFarmer;
    private int fromDay;

    Letter(String id, String from) {
        this.id = id;
        this.from = from;
    }

    public Letter text(String words) {
        if (!words.equals(words.toUpperCase())) {
            throw new IllegalArgumentException("Letters are upper case: " + words);
        }
        text = words;
        return this;
    }

    public Letter pic(String... tokens) {
        pictures = tokens.clone();
        return this;
    }

    /** Something enclosed, given when the letter is read (skipped if the item does not exist yet). */
    public Letter enclose(String itemId, int amount) {
        item = itemId;
        count = amount;
        return this;
    }

    public Letter on(int seasonOf, int dayOf) {
        season = seasonOf;
        day = dayOf;
        return this;
    }

    public Letter yearOne() {
        yearOne = 1;
        return this;
    }

    /** The very first morning in the valley. */
    public Letter firstMorning() {
        firstMorning = true;
        return this;
    }

    /** The morning after a heart event. */
    public Letter afterEvent(String event) {
        afterEvent = event;
        return this;
    }

    /** Once a story flag is set. */
    public Letter whenFlag(String storyFlag) {
        flag = storyFlag;
        return this;
    }

    /** Once a villager has this many hearts. */
    public Letter whenHearts(String villager, int count) {
        heartsOf = villager;
        hearts = count;
        return this;
    }

    public Letter farmer(String code) {
        farmer = code;
        return this;
    }

    public Letter notFarmer(String code) {
        notFarmer = code;
        return this;
    }

    /** Not before this day number. */
    public Letter fromDay(int dayNumber) {
        fromDay = dayNumber;
        return this;
    }

    public String text() {
        return text;
    }

    /** A dated letter without {@link #yearOne}: sent again every year. */
    public boolean yearly() {
        return season >= 0 && yearOne == 0;
    }

    public String[] pictures() {
        return pictures == null ? null : pictures.clone();
    }

    public String item() {
        return item;
    }

    public int count() {
        return count;
    }

    /** Whether the letter is due this morning. {@code heartsOf} reads a villager's hearts. */
    public boolean due(Situation s, java.util.function.ToIntFunction<String> heartsOfVillager) {
        if (firstMorning && s.dayNumber() != 0) {
            return false;
        }
        if (s.dayNumber() < fromDay) {
            return false;
        }
        if (season >= 0 && (s.season() != season || s.day() < day)) {
            return false;
        }
        if (season >= 0 && s.day() > day + 6) {
            return false;   // a dated letter missed by more than a week is not sent late
        }
        if (yearOne == 1 && s.year() != 1) {
            return false;
        }
        if (afterEvent != null && (!s.seenEvent(afterEvent) || s.daysSince(afterEvent) < 1)) {
            return false;
        }
        if (flag != null && !s.flags().contains(flag)) {
            return false;
        }
        if (heartsOf != null && heartsOfVillager.applyAsInt(heartsOf) < hearts) {
            return false;
        }
        if (farmer != null && !farmer.equals(s.farmer()) || notFarmer != null && notFarmer.equals(s.farmer())) {
            return false;
        }
        return true;
    }
}
