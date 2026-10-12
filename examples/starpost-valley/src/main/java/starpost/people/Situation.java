package starpost.people;

import java.util.Map;
import java.util.Set;

/**
 * What a line, letter or event can depend on, gathered once: the date and weather, the farmer,
 * story flags, the speaker's hearts, and when each heart event was seen.
 *
 * @param met       whether the farmer has talked to the speaker before
 * @param birthday  whether today is the speaker's birthday
 * @param seen      heart event id to the day number it was seen
 */
public record Situation(int year, int season, int day, int weekday, int dayNumber, int minutes, boolean raining,
        String farmer, Set<String> flags, int hearts, boolean met, boolean birthday, Map<String, Integer> seen) {

    public boolean seenEvent(String event) {
        return seen.containsKey(event);
    }

    /** Days since an event was seen, or a large number when it was not. */
    public int daysSince(String event) {
        Integer day = seen.get(event);
        return day == null ? Integer.MAX_VALUE : dayNumber - day;
    }
}
