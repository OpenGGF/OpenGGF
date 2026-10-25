package typinghog.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One speaker queue shared by the announcer, Robotnik and Tails: a bounded priority queue with
 * per-line cooldowns that never lets two clips overlap. Stale requests (older than five seconds)
 * are dropped, so a callout never plays long after its moment. Engine-free: the caller supplies
 * the clock and the play function.
 */
public final class Announcer {
    /** A clip: its asset id (the clean render), PCM length in 60 Hz ticks, priority and cooldown. */
    public record Clip(String asset, int durationTicks, int priority, int cooldownTicks) { }

    /** How voices are heard: the Mega Drive DAC rendering, the clean recording, or not at all. */
    public enum Mode {
        MEGADRIVE, CLEAN, OFF;

        /** The asset id this mode plays for a clean asset id. */
        public String asset(String clean) {
            return this == MEGADRIVE ? clean + "-md" : clean;
        }

        public static Mode parse(String text) {
            for (Mode mode : values()) if (mode.name().equalsIgnoreCase(text)) return mode;
            return MEGADRIVE;
        }
    }

    static final int QUEUE_LIMIT = 6;
    static final int STALE_TICKS = 300;
    static final int GAP_TICKS = 8;

    private record Pending(Clip clip, long requested) { }

    private final List<Pending> queue = new ArrayList<>();
    private final Map<String, Long> lastPlayed = new HashMap<>();
    private Mode mode = Mode.MEGADRIVE;
    private long tick;
    private long busyUntil;

    public void mode(Mode value) {
        mode = value;
        if (value == Mode.OFF) queue.clear();
    }

    public Mode mode() { return mode; }

    /** Asks for a clip; dropped while on cooldown, already queued, or outranked by a full queue. */
    public void say(Clip clip) {
        if (clip == null || mode == Mode.OFF) return;
        if (tick - lastPlayed.getOrDefault(clip.asset(), Long.MIN_VALUE / 2) < clip.cooldownTicks()) return;
        for (Pending pending : queue) if (pending.clip.asset().equals(clip.asset())) return;
        if (queue.size() >= QUEUE_LIMIT) {
            Pending lowest = queue.stream().min(java.util.Comparator.comparingInt(p -> p.clip.priority())).orElseThrow();
            if (lowest.clip.priority() >= clip.priority()) return;
            queue.remove(lowest);
        }
        queue.add(new Pending(clip, tick));
    }

    /** Drops everything queued (a scene change); a clip already playing finishes. */
    public void clear() { queue.clear(); }

    /** Call every tick: starts the most urgent queued clip once the speaker is free. */
    public void update(long now, Predicate<String> play) {
        tick = now;
        queue.removeIf(p -> now - p.requested > STALE_TICKS);
        if (mode == Mode.OFF || now < busyUntil || queue.isEmpty()) return;
        Pending next = queue.stream().max(java.util.Comparator.<Pending>comparingInt(p -> p.clip.priority())
                .thenComparingLong(p -> -p.requested)).orElseThrow();
        queue.remove(next);
        lastPlayed.put(next.clip.asset(), now);
        if (play.test(mode.asset(next.clip.asset()))) busyUntil = now + next.clip.durationTicks() + GAP_TICKS;
    }
}
