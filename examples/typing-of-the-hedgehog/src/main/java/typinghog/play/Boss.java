package typinghog.play;

import com.openggf.game.GameServices;
import typinghog.audio.VoiceLine;
import typinghog.core.Prompt;
import typinghog.core.TypingField;

/**
 * An act's typing boss: Robotnik's craft (the zone's own boss art) circles the arena and opens a
 * weak point carrying a phrase; typing it before the window closes lands a hit, letting it close
 * makes him swoop at Sonic. Eight hits win, as most stock bosses take. Variants change what the
 * window asks for: a phrase, his own taunt typed back, or the right answer to his question.
 */
public final class Boss {
    public enum Style { WEAK_POINT, TALK_BACK, QUIZ }

    static final int HITS = 8;
    static final int ENTRY_ROWS = 90;
    static final int RECOVER_ROWS = 70;

    private final Style style;
    private final String artKey;
    private final int[] frames;
    private Stagehand.Visual visual;
    private int hits;
    private int row;
    private int stateRow;
    private int windowRows;
    private int targetId = -1;
    private int[] answerIds = new int[0];
    private int correctId = -1;
    private String question = "";
    private int x;
    private int y;
    private int flash;
    private boolean defeated;
    private int fleeRows;

    private Boss(Style style, String artKey, int[] frames) {
        this.style = style;
        this.artKey = artKey;
        this.frames = frames;
    }

    /** The act's boss: style by zone position in the chapter, art from the cast's boss line. */
    static Boss forAct(Course course) {
        var intent = course.intent();
        Style style = switch (Math.floorMod(intent.zone() * 7 + intent.act(), 3)) {
            case 0 -> Style.WEAK_POINT;
            case 1 -> Style.TALK_BACK;
            default -> Style.QUIZ;
        };
        return new Boss(style, "eggman", new int[] {0});
    }

    boolean defeated() { return defeated && fleeRows > 90; }
    boolean owns(int target) {
        if (target == targetId) return true;
        for (int id : answerIds) if (id == target) return true;
        return false;
    }
    Style style() { return style; }
    int hits() { return hits; }
    String question() { return question; }
    int windowRows() { return windowRows; }
    int stateRow() { return stateRow; }
    int x() { return x; }
    int y() { return y; }

    void advance(Course course, Encounter encounter) {
        row++;
        stateRow++;
        int px = course.playerX(), py = course.playerY();
        var camera = GameServices.camera();
        if (visual == null) {
            x = camera.getX() + camera.getWidth() + 48;
            y = py - 72;
            visual = course.stagehand().add(artKey, x, y);
            visual.bucket = 5;
            stateRow = 0;
            course.say(switch (style) {
                case QUIZ -> VoiceLine.ROBOTNIK_INTRO_QUIZ;
                case TALK_BACK -> VoiceLine.ROBOTNIK_TALK_BACK;
                case WEAK_POINT -> new VoiceLine[] {VoiceLine.ROBOTNIK_INTRO_A, VoiceLine.ROBOTNIK_INTRO_B,
                        VoiceLine.ROBOTNIK_INTRO_C}[course.random().nextInt(3)];
            });
        }
        if (flash > 0) flash--;
        visual.blink = flash;
        if (defeated) {
            fleeRows++;
            x += 3;
            y -= 1;
            if (fleeRows % 8 == 0) encounter.effects().kill(course, x + course.random().nextInt(32) - 16,
                    y + course.random().nextInt(24) - 12, 1000);
            place();
            return;
        }
        int homeX = camera.getX() + camera.getWidth() * 3 / 4;
        int bob = (int) Math.round(Math.sin(row / 20.0) * 6);
        if (targetId < 0 && answerIds.length == 0) {
            x += Integer.signum(homeX - x) * Math.min(3, Math.abs(homeX - x));
            y = py - 72 + bob;
            if (stateRow > (hits == 0 ? ENTRY_ROWS : RECOVER_ROWS)) openWindow(course);
        } else {
            y = py - 72 + bob;
            windowRows--;
            if (windowRows <= 0) swoop(course);
        }
        place();
    }

    private void place() {
        visual.x = x;
        visual.y = y;
        visual.flipX = true;
        visual.frame = frames[(row / 8) % frames.length];
    }

    private void openWindow(Course course) {
        var intent = course.intent();
        var field = course.field();
        var lexicon = course.assets().lexicon();
        int baseRows = (int) (480 / intent.difficulty().speed);
        switch (style) {
            case WEAK_POINT -> {
                String phrase = course.picker().pick(java.util.List.of("phrases"), 8, 26, lead -> true);
                targetId = field.add(Prompt.of(phrase == null ? "spin attack" : phrase, intent.capitals(), intent.punctuation()), baseRows);
                windowRows = baseRows;
            }
            case TALK_BACK -> {
                var taunts = lexicon.taunts().stream()
                        .filter(t -> t.tag().equals("any") || t.tag().equals(intent.game())).toList();
                // Robotnik says the line you must type back when it is one of his voiced taunts.
                var voiced = taunts.stream().filter(t -> VoiceLine.taunt(t.text()) != null).toList();
                var pool = !voiced.isEmpty() && course.random().nextInt(4) != 0 ? voiced : taunts;
                String text = pool.isEmpty() ? "You will never win, hedgehog!"
                        : pool.get(course.random().nextInt(pool.size())).text();
                course.say(VoiceLine.taunt(text));
                targetId = field.add(Prompt.of(text, intent.capitals(), intent.punctuation()), baseRows * 2);
                windowRows = baseRows * 2;
            }
            case QUIZ -> {
                var questions = lexicon.trivia().stream()
                        .filter(t -> t.game().equals("any") || t.game().equals(intent.game())).toList();
                if (questions.isEmpty()) {
                    targetId = field.add(Prompt.of("chaos emerald", intent.capitals(), intent.punctuation()), baseRows);
                    windowRows = baseRows;
                    return;
                }
                var q = questions.get(course.random().nextInt(questions.size()));
                question = q.question();
                String[] answers = {q.correct(), q.wrong1(), q.wrong2()};
                int shift = course.random().nextInt(3);
                answerIds = new int[3];
                for (int i = 0; i < 3; i++) {
                    String answer = answers[(i + shift) % 3];
                    answerIds[i] = field.add(Prompt.of(answer, intent.capitals(), intent.punctuation()), baseRows * 2);
                    if (answer.equals(q.correct())) correctId = answerIds[i];
                }
                windowRows = baseRows * 2;
            }
        }
        stateRow = 0;
    }

    void onKey(Course course, TypingField.Event event) { }

    void onComplete(Course course, Encounter encounter, int target) {
        boolean right = target == targetId || target == correctId;
        closeWindow(course);
        if (!right) {
            course.hud().banner("WRONG!", 0xFFFF4040);
            if (style == Style.QUIZ) course.say(VoiceLine.ROBOTNIK_QUIZ_WRONG);
            swoop(course);
            return;
        }
        hits++;
        flash = 24;
        course.play(Sounds.Cue.BOSS_HIT);
        if (style == Style.QUIZ) course.say(VoiceLine.ROBOTNIK_QUIZ_RIGHT);
        else course.say(new VoiceLine[] {VoiceLine.ROBOTNIK_HURT_A, VoiceLine.ROBOTNIK_HURT_B,
                VoiceLine.ROBOTNIK_HURT_C, VoiceLine.ROBOTNIK_HURT_D}[course.random().nextInt(4)]);
        course.card().add(1000);
        encounter.effects().kill(course, x, y, 1000);
        if (hits >= HITS) {
            defeated = true;
            course.play(Sounds.Cue.BOSS_DEFEATED);
            course.say(VoiceLine.ANNOUNCER_BOSS_DEFEATED);
            course.say(new VoiceLine[] {VoiceLine.ROBOTNIK_DEFEAT_A, VoiceLine.ROBOTNIK_DEFEAT_B,
                    VoiceLine.ROBOTNIK_DEFEAT_C}[course.random().nextInt(3)]);
            course.hud().banner("BOSS DEFEATED!", 0xFFFFD000);
        }
        stateRow = 0;
    }

    private void closeWindow(Course course) {
        var field = course.field();
        if (targetId >= 0) field.remove(targetId);
        for (int id : answerIds) field.remove(id);
        targetId = -1;
        answerIds = new int[0];
        correctId = -1;
        question = "";
    }

    private void swoop(Course course) {
        closeWindow(course);
        course.hitSonic();
        stateRow = 0;
    }

    /** Prompts the boss currently shows, for the HUD (empty while closed). */
    int[] openTargets() {
        if (targetId >= 0) return new int[] {targetId};
        return answerIds.clone();
    }
}
