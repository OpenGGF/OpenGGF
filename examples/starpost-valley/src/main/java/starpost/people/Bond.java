package starpost.people;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/** The farmer's friendship with one villager. Rules live in {@link People}. */
public final class Bond {
    /** How many recent daily lines are remembered (and not repeated while others are left). */
    static final int MEMORY = 6;

    public int points;
    public boolean met;
    public boolean talkedToday;
    public boolean giftedToday;
    public int giftsThisWeek;
    public boolean partner;
    /** Loved gifts the farmer has discovered (shown on the social page). */
    public final Set<String> knownLoves = new LinkedHashSet<>();
    /** Indices of the last few daily lines said, newest first. */
    final Deque<Integer> said = new ArrayDeque<>();
    /** The last gift: item id, how it went, and the day. */
    public String lastGift;
    public Taste lastTaste;
    public int lastGiftDay = -100;

    public int hearts() {
        return points / People.POINTS_PER_HEART;
    }

    void remember(int lineIndex) {
        said.remove(lineIndex);
        said.addFirst(lineIndex);
        while (said.size() > MEMORY) {
            said.removeLast();
        }
    }
}
