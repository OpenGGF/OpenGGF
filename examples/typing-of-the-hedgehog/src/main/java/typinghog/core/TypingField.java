package typinghog.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The typing rules for everything currently on screen, independent of drawing and the engine.
 *
 * <p>Targets are added with a prompt and an urgency (frames until they strike; smaller is more
 * urgent). A character that begins an unlocked target's prompt locks the most urgent such
 * target; later characters must continue the locked prompt. A wrong character is an error: it
 * does not advance and is reported so the caller can break the chain. Backspace releases the
 * lock and starts that prompt over. A completed prompt reports {@link Kind#COMPLETE} and leaves
 * the field.
 */
public final class TypingField {
    /** What one typed character did. */
    public enum Kind { LOCK, HIT, COMPLETE, ERROR, RELEASE, IGNORED }

    /** One outcome; {@code target} is -1 for errors with nothing locked and for ignored input. */
    public record Event(Kind kind, int target, int index) { }

    /** A target's live typing state. */
    public static final class Target {
        final int id;
        final Prompt prompt;
        int urgency;
        int progress;
        boolean typeable = true;
        Target(int id, Prompt prompt, int urgency) { this.id = id; this.prompt = prompt; this.urgency = urgency; }
        public int id() { return id; }
        public Prompt prompt() { return prompt; }
        public int progress() { return progress; }
        public int urgency() { return urgency; }
        public boolean typeable() { return typeable; }
    }

    private final Map<Integer, Target> targets = new LinkedHashMap<>();
    private final boolean capitals;
    private int locked = -1;
    private int nextId = 1;

    public TypingField(boolean capitals) {
        this.capitals = capitals;
    }

    /** Adds a target and returns its id. */
    public int add(Prompt prompt, int urgency) {
        Objects.requireNonNull(prompt, "prompt");
        int id = nextId++;
        targets.put(id, new Target(id, prompt, urgency));
        return id;
    }

    /** Updates how soon a target strikes; the most urgent unlocked match wins a lock. */
    public void setUrgency(int id, int urgency) {
        Target target = targets.get(id);
        if (target != null) target.urgency = urgency;
    }

    /** Hides or shows a target's prompt (a surfacing badnik's word exists only while visible). */
    public void setTypeable(int id, boolean typeable) {
        Target target = targets.get(id);
        if (target == null) return;
        target.typeable = typeable;
        if (!typeable && locked == id) {
            locked = -1;
            target.progress = 0;
        }
    }

    /** Removes a target that left without being typed (it struck, fled or was destroyed). */
    public void remove(int id) {
        targets.remove(id);
        if (locked == id) locked = -1;
    }

    public void clear() {
        targets.clear();
        locked = -1;
    }

    public int locked() { return locked; }
    public Target target(int id) { return targets.get(id); }
    public List<Target> targets() { return List.copyOf(targets.values()); }

    /** True when some visible target's prompt already starts with this character. */
    public boolean leadInUse(int lead) {
        for (Target target : targets.values()) {
            if (target.typeable && target.progress == 0 && target.prompt.lead() == lead) return true;
        }
        return false;
    }

    /** Releases the current lock (Backspace). */
    public Event release() {
        if (locked < 0) return new Event(Kind.IGNORED, -1, 0);
        int id = locked;
        locked = -1;
        Target target = targets.get(id);
        if (target != null) target.progress = 0;
        return new Event(Kind.RELEASE, id, 0);
    }

    /** Applies one typed character. */
    public Event type(int codePoint) {
        int typed = Prompt.normalise(codePoint, capitals);
        if (locked >= 0) {
            Target target = targets.get(locked);
            if (target == null || !target.typeable) {
                locked = -1;
            } else {
                int expected = target.prompt.typed().codePointAt(target.progress);
                if (expected != typed) return new Event(Kind.ERROR, target.id, target.progress);
                return advance(target);
            }
        }
        if (typed == ' ') return new Event(Kind.IGNORED, -1, 0);
        Target best = null;
        for (Target target : targets.values()) {
            if (!target.typeable || target.progress != 0 || target.prompt.lead() != typed) continue;
            if (best == null || target.urgency < best.urgency) best = target;
        }
        if (best == null) return new Event(Kind.ERROR, -1, 0);
        locked = best.id;
        Event step = advance(best);
        return step.kind() == Kind.COMPLETE ? step : new Event(Kind.LOCK, best.id, step.index());
    }

    /** Applies a run of typed characters, in order. */
    public List<Event> type(String text) {
        List<Event> events = new ArrayList<>();
        text.codePoints().forEach(cp -> events.add(type(cp)));
        return events;
    }

    private Event advance(Target target) {
        int index = target.progress;
        target.progress += Character.charCount(target.prompt.typed().codePointAt(index));
        if (target.progress >= target.prompt.typed().length()) {
            targets.remove(target.id);
            locked = -1;
            return new Event(Kind.COMPLETE, target.id, index);
        }
        return new Event(Kind.HIT, target.id, index);
    }
}
