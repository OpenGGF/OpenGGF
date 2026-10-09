package typinghog.hub.model;

/**
 * Tails' Typing School: six lessons, seven graded drills and the Final Exam (The Typing of the
 * Dead's Drill mode, reworked). {@code seconds} is a time limit (0 for none) and {@code count}
 * how many prompts the drill sets (0 for as many as the time allows). Engine-free.
 */
public enum Drill {
    HOME_ROW("Home Row", "home row", 0, 0, 15),
    TOP_ROW("Top Row", "top row", 0, 0, 15),
    BOTTOM_ROW("Bottom Row", "bottom row", 0, 0, 15),
    NUMBERS("Numbers", "numbers", 0, 0, 12),
    CAPITALS("Capitals", "capitals", 0, 0, 12),
    PUNCTUATION("Punctuation", "punctuation", 0, 0, 8),
    RING_RUSH("Ring Rush", "ring rush", 1, 60, 0),
    SUDDEN_DEATH("Sudden Death", "sudden death", 1, 0, 50),
    REFLEX("Reflex", "reflex", 1, 0, 30),
    LONG_WORDS("Long Words", "long words", 1, 0, 12),
    PHRASES("Phrases", "phrases", 1, 0, 8),
    BOSS_TRAINING("Boss Training", "boss training", 1, 0, 6),
    WEAK_KEYS("Weak Keys", "weak keys", 1, 0, 15),
    FINAL_EXAM("Final Exam", "final exam", 2, 0, 20);

    public static final int LESSON = 0;
    public static final int DRILL = 1;
    public static final int EXAM = 2;

    /** What the menu shows. */
    public final String label;
    /** What the player types to choose it. */
    public final String word;
    /** {@link #LESSON}, {@link #DRILL} or {@link #EXAM}. */
    public final int kind;
    public final int seconds;
    public final int count;

    Drill(String label, String word, int kind, int seconds, int count) {
        this.label = label;
        this.word = word;
        this.kind = kind;
        this.seconds = seconds;
        this.count = count;
    }

    public boolean lesson() { return kind == LESSON; }
    public boolean exam() { return kind == EXAM; }
}
