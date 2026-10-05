package slaytherobotnik.core;

import java.util.Set;
import java.util.function.Predicate;

/**
 * A "?" room event. {@code acts} lists the acts it can appear in; {@code condition} can
 * keep it out of the pool (for example until the player has enough rings). {@code art} names
 * the illustration shown beside the text.
 */
public record EventDef(
        String id,
        String title,
        String art,
        Set<Integer> acts,
        Predicate<RunState> condition,
        Script script) {

    @FunctionalInterface
    public interface Script {
        void start(EventContext ctx);
    }

    public boolean available(RunState run) {
        return acts.contains(run.act()) && !run.seenEvents().contains(id)
                && (condition == null || condition.test(run));
    }
}
