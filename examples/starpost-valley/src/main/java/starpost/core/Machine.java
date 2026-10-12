package starpost.core;

/**
 * A placed machine's work (an artisan machine on a farm plot): what went in, what comes out and
 * the morning it is ready. Machines load one input at a time and process overnight; what they
 * accept is the owning system's rule ({@code starpost.barn.Artisan}).
 *
 * @param input    the item loaded
 * @param output   the item it becomes
 * @param count    how many come out
 * @param readyDay the {@link Calendar#dayNumber()} from which it can be collected
 */
public record Machine(String input, String output, int count, int readyDay) {
    public boolean ready(Calendar calendar) {
        return calendar.dayNumber() >= readyDay;
    }

    /** Days still to wait (0 when ready). */
    public int daysLeft(Calendar calendar) {
        return Math.max(0, readyDay - calendar.dayNumber());
    }
}
