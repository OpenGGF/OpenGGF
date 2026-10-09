package typinghog.hub.model;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import typinghog.core.Prompt;
import typinghog.core.Scorecard;
import typinghog.core.TypingField;

/**
 * One run of a Typing School drill, at 60 rows a second. It types through the course's own
 * {@link TypingField} and scores with its {@link Scorecard}, so speed and accuracy mean the
 * same here as in an act. Engine-free; the scene feeds it typed text and ticks, then draws it.
 *
 * <p>Rules per drill:
 * <ul>
 *   <li>lessons, Long Words, Phrases, Weak Keys and the Final Exam: type each prompt in turn;</li>
 *   <li>Ring Rush: as many words as fit in {@link Drill#seconds};</li>
 *   <li>Sudden Death: the first error ends it;</li>
 *   <li>Reflex: one letter at a time after a random pause; it must be typed within
 *       {@link #REFLEX_WINDOW} rows, and the reaction time is kept;</li>
 *   <li>Boss Training: a practice phrase that any error starts over (within
 *       {@link #PRACTICE_ROWS}), a taunt typed back (within {@link #TALK_BACK_ROWS}) and a
 *       quiz whose right answer must be typed among three (within {@link #QUIZ_ROWS}).</li>
 * </ul>
 */
public final class DrillSession {
    public static final int READY_ROWS = 90;
    public static final int REFLEX_WINDOW = 90;
    public static final int REFLEX_GAP_MIN = 18;
    public static final int REFLEX_GAP_MAX = 54;
    public static final int PRACTICE_ROWS = 30 * 60;
    public static final int TALK_BACK_ROWS = 25 * 60;
    public static final int QUIZ_ROWS = 15 * 60;
    /** Ends any drill that runs this long (five minutes), so a session always finishes. */
    public static final int MAX_ROWS = 5 * 60 * 60;

    /** How a prompt is played. */
    public enum Kind { WORD, PHRASE, LETTER, PRACTICE, TALK_BACK, QUIZ }

    /** One prompt: the text (for a quiz, the question), its typing rules and quiz answers (right one first). */
    public record Item(String display, boolean capitals, boolean punctuation, Kind kind, List<String> answers) {
        public Item {
            answers = List.copyOf(answers);
        }
        public static Item word(String text, boolean capitals, boolean punctuation) {
            return new Item(text, capitals, punctuation, Kind.WORD, List.of());
        }
        public static Item phrase(String text, boolean capitals, boolean punctuation) {
            return new Item(text, capitals, punctuation, Kind.PHRASE, List.of());
        }
        public static Item letter(String text) { return new Item(text, false, false, Kind.LETTER, List.of()); }
        public static Item practice(String text) { return new Item(text, false, false, Kind.PRACTICE, List.of()); }
        public static Item talkBack(String text) { return new Item(text, false, false, Kind.TALK_BACK, List.of()); }
        public static Item quiz(String question, List<String> answers) {
            return new Item(question, false, false, Kind.QUIZ, answers);
        }
    }

    public enum Phase { READY, PLAYING, FINISHED }

    /** What one keystroke or tick did, for sounds and effects. */
    public enum Signal { KEY, ERROR, COMPLETE, ROUND_WON, ROUND_LOST, MISSED, RESTART, FINISHED }

    public record Event(Signal signal, int codePoint) { }

    /** A finished drill. */
    public record Result(Drill drill, String grade, double wpm, double accuracy, int completed, int total,
                         int successes, double reactionMs, boolean failed, long score, long chars, long errors,
                         double activeMinutes, int bestChain) {
        public double netWpm() { return wpm * accuracy; }
    }

    private final Drill drill;
    private final List<Item> items;
    private final SplittableRandom random;
    private final Scorecard card = new Scorecard();
    private final KeyTally hits = new KeyTally();
    private final KeyTally misses = new KeyTally();
    private Phase phase = Phase.READY;
    private int rows;
    private int playRows;
    private int index = -1;
    private TypingField field;
    private final List<Integer> answerIds = new ArrayList<>();
    private int itemRows;
    private int correctAnswerId = -1;
    private int gap;
    private int completed;
    private int successes;
    private long reactionRows;
    private int reactions;
    private boolean failed;
    private Result result;

    public DrillSession(Drill drill, List<Item> items, long seed) {
        if (items.isEmpty()) throw new IllegalArgumentException(drill + " has nothing to type");
        this.drill = drill;
        this.items = List.copyOf(items);
        this.random = new SplittableRandom(seed);
    }

    public Drill drill() { return drill; }
    public Phase phase() { return phase; }
    public int rows() { return rows; }
    public int playRows() { return playRows; }
    public Scorecard card() { return card; }
    public KeyTally hits() { return hits; }
    public KeyTally misses() { return misses; }
    public int completed() { return completed; }
    public int successes() { return successes; }
    public int total() { return drill.seconds > 0 ? 0 : items.size(); }
    public Result result() { return result; }

    /** The prompt being typed, or null between Reflex letters and outside play. */
    public Item current() {
        return phase == Phase.PLAYING && index >= 0 && index < items.size() && field != null
                && !field.targets().isEmpty() ? items.get(index) : null;
    }

    /** Field targets for drawing: one per prompt, three for a quiz (in plate order). */
    public List<TypingField.Target> targets() {
        if (field == null) return List.of();
        List<TypingField.Target> out = new ArrayList<>();
        for (int id : answerIds) {
            TypingField.Target t = field.target(id);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** True for the quiz target holding the right answer. */
    public boolean correctAnswer(TypingField.Target target) {
        return target.id() == correctAnswerId;
    }

    public int lockedId() { return field == null ? -1 : field.locked(); }

    /** Rows the current prompt has been showing. */
    public int itemRows() { return itemRows; }

    /** Rows left on a timed prompt or drill, or -1 when untimed. */
    public int rowsLeft() {
        if (phase != Phase.PLAYING) return -1;
        if (drill.seconds > 0) return Math.max(0, drill.seconds * 60 - playRows);
        Item item = index >= 0 && index < items.size() ? items.get(index) : null;
        if (item == null || field == null || field.targets().isEmpty()) return -1;
        int limit = limit(item.kind());
        return limit < 0 ? -1 : Math.max(0, limit - itemRows);
    }

    /** The next few prompts' texts, for the queue beside the plate. */
    public List<String> upcoming(int n) {
        List<String> out = new ArrayList<>();
        for (int i = index + 1; i < items.size() && out.size() < n; i++) {
            Item item = items.get(i);
            out.add(item.kind() == Kind.QUIZ ? "?" : item.display());
        }
        return out;
    }

    /** The character the player should press next, or -1 (nothing live or a quiz not yet chosen). */
    public int expected() {
        if (field == null) return -1;
        int locked = field.locked();
        TypingField.Target target = locked >= 0 ? field.target(locked) : null;
        if (target == null) {
            List<TypingField.Target> live = field.targets();
            if (live.size() != 1) return -1;
            target = live.getFirst();
        }
        String typed = target.prompt().typed();
        return target.progress() < typed.length() ? typed.codePointAt(target.progress()) : -1;
    }

    // ---- driving ----------------------------------------------------------------------------

    /** Advances one 60 Hz row. */
    public List<Event> tick() {
        List<Event> events = new ArrayList<>();
        if (phase == Phase.FINISHED) return events;
        rows++;
        if (phase == Phase.READY) {
            if (rows >= READY_ROWS) {
                phase = Phase.PLAYING;
                next(events);
            }
            return events;
        }
        playRows++;
        if (field != null && !field.targets().isEmpty()) {
            card.activeRow();
            itemRows++;
        }
        if (drill.seconds > 0 && playRows >= drill.seconds * 60) {
            finish(events);
            return events;
        }
        if (playRows >= MAX_ROWS) {
            finish(events);
            return events;
        }
        Item item = items.get(index);
        if (field.targets().isEmpty()) {
            if (--gap <= 0) present(item);
            return events;
        }
        int limit = limit(item.kind());
        if (limit >= 0 && itemRows >= limit) {
            events.add(new Event(item.kind() == Kind.LETTER ? Signal.MISSED : Signal.ROUND_LOST, -1));
            if (item.kind() == Kind.LETTER) misses.add(item.display().toLowerCase().charAt(0), 1);
            next(events);
        }
        return events;
    }

    private static int limit(Kind kind) {
        return switch (kind) {
            case LETTER -> REFLEX_WINDOW;
            case PRACTICE -> PRACTICE_ROWS;
            case TALK_BACK -> TALK_BACK_ROWS;
            case QUIZ -> QUIZ_ROWS;
            default -> -1;
        };
    }

    /** Applies the tick's typed characters. */
    public List<Event> type(String text) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < text.length() && phase == Phase.PLAYING; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            typeOne(cp, events);
        }
        return events;
    }

    /** Backspace: lets go of the prompt being typed and starts it over. */
    public void backspace() {
        if (field != null && phase == Phase.PLAYING) field.release();
    }

    /** Stops early (the player left); the drill is not graded. */
    public void abandon() {
        phase = Phase.FINISHED;
        result = null;
    }

    private void typeOne(int cp, List<Event> events) {
        if (field == null || field.targets().isEmpty()) return;
        Item item = items.get(index);
        int expected = expected();
        TypingField.Event e = field.type(cp);
        switch (e.kind()) {
            case LOCK, HIT -> {
                card.keystroke();
                hits.add((char) Character.toLowerCase(cp), 1);
                events.add(new Event(Signal.KEY, cp));
            }
            case COMPLETE -> {
                card.keystroke();
                hits.add((char) Character.toLowerCase(cp), 1);
                complete(item, e.target(), events);
            }
            case ERROR -> {
                if (item.kind() == Kind.QUIZ && field.locked() < 0) {
                    // A key that starts no answer: an error, but no key is to blame.
                    card.error();
                    events.add(new Event(Signal.ERROR, cp));
                    return;
                }
                card.error();
                if (expected >= 0) misses.add((char) Character.toLowerCase(expected), 1);
                events.add(new Event(Signal.ERROR, cp));
                if (drill == Drill.SUDDEN_DEATH) {
                    failed = true;
                    finish(events);
                } else if (item.kind() == Kind.PRACTICE) {
                    field.release();
                    events.add(new Event(Signal.RESTART, cp));
                }
            }
            default -> { }
        }
    }

    private void complete(Item item, int target, List<Event> events) {
        switch (item.kind()) {
            case QUIZ -> {
                boolean right = target == correctAnswerId;
                if (right) {
                    successes++;
                    card.kill(Prompt.of(item.answers().getFirst(), false, false));
                }
                events.add(new Event(right ? Signal.ROUND_WON : Signal.ROUND_LOST, -1));
            }
            case PRACTICE, TALK_BACK -> {
                successes++;
                card.kill(Prompt.of(item.display(), false, false));
                events.add(new Event(Signal.ROUND_WON, -1));
            }
            case LETTER -> {
                successes++;
                reactionRows += itemRows;
                reactions++;
                card.kill(Prompt.of(item.display(), false, false));
                events.add(new Event(Signal.COMPLETE, -1));
            }
            default -> {
                card.kill(Prompt.of(item.display(), item.capitals(), item.punctuation()));
                events.add(new Event(Signal.COMPLETE, -1));
            }
        }
        completed++;
        next(events);
    }

    private void next(List<Event> events) {
        index++;
        if (index >= items.size()) {
            finish(events);
            return;
        }
        Item item = items.get(index);
        field = new TypingField(item.capitals());
        answerIds.clear();
        correctAnswerId = -1;
        itemRows = 0;
        if (item.kind() == Kind.LETTER) {
            gap = REFLEX_GAP_MIN + random.nextInt(REFLEX_GAP_MAX - REFLEX_GAP_MIN + 1);
        } else {
            present(item);
        }
    }

    private void present(Item item) {
        itemRows = 0;
        if (item.kind() == Kind.QUIZ) {
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2));
            java.util.Collections.shuffle(order, new java.util.Random(random.nextLong()));
            int[] ids = new int[3];
            for (int slot : order) {
                String answer = item.answers().get(slot);
                ids[slot] = field.add(Prompt.of(answer, false, false), 100 + slot);
            }
            correctAnswerId = ids[0];
            for (int slot : order) answerIds.add(ids[slot]);
        } else {
            answerIds.add(field.add(Prompt.of(item.display(), item.capitals(), item.punctuation()), 100));
        }
    }

    private void finish(List<Event> events) {
        if (phase == Phase.FINISHED) return;
        phase = Phase.FINISHED;
        if (field != null) field.clear();
        double reaction = reactions == 0 ? 0 : reactionRows * 1000.0 / 60.0 / reactions;
        int total = drill.seconds > 0 ? completed : items.size();
        double minutes = card.activeRows() / 3600.0;
        Result r = new Result(drill, "E", card.wpm(), card.accuracy(), completed, total, successes, reaction, failed,
                card.score(), card.correct(), card.errors(), minutes, card.bestChain());
        result = new Result(r.drill(), School.grade(r), r.wpm(), r.accuracy(), r.completed(), r.total(),
                r.successes(), r.reactionMs(), r.failed(), r.score(), r.chars(), r.errors(), r.activeMinutes(),
                r.bestChain());
        events.add(new Event(Signal.FINISHED, -1));
    }
}
