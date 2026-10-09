package typinghog.play;

import com.openggf.game.GameServices;
import java.util.ArrayList;
import java.util.List;
import typinghog.audio.VoiceLine;
import typinghog.core.Prompt;
import typinghog.core.TypingField;

/**
 * An act's typing boss: Robotnik's craft, built from the zone's own boss sprites by its
 * {@link BossRig}, hovers at the arena's right and opens a weak point carrying a phrase; typing
 * it before the window closes lands a hit, letting it close makes him lunge at Sonic. Eight hits
 * win, as most stock bosses take. Variants change what the window asks for: a phrase, his own
 * taunt typed back, or the right answer to his question. Beaten, he shudders through a chain of
 * explosions and then flees, as the stock bosses do.
 */
public final class Boss {
    public enum Style { WEAK_POINT, TALK_BACK, QUIZ }

    static final int HITS = 8;
    static final int ENTRY_ROWS = 90;
    static final int RECOVER_ROWS = 70;
    /** Rows he explodes in place once beaten before fleeing. */
    static final int EXPLODE_ROWS = 120;
    static final int FLEE_ROWS = 80;
    static final int WIND_UP_ROWS = 16;
    static final int DASH_ROWS = 10;
    static final int RETURN_ROWS = 24;
    static final int LAUGH_ROWS = 64;

    private final Style style;
    private final BossRig rig;
    private final List<List<Stagehand.Visual>> visuals = new ArrayList<>();
    private boolean spawned;
    private boolean facingRight;
    private BossRig.Mood mood = BossRig.Mood.IDLE;
    private int moodRow;
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
    private int laughRows;
    private int recoil;
    private int lungeRow = -1;
    private int lungeFromX;
    private int lungeFromY;
    private boolean lungeLanded;
    private boolean defeated;
    private int defeatRows;

    private Boss(Style style, BossRig rig) {
        this.style = style;
        this.rig = rig;
    }

    /** The act's boss: style by zone position in the chapter, look from the zone's rig. */
    static Boss forAct(Course course) {
        var intent = course.intent();
        Style style = switch (Math.floorMod(intent.zone() * 7 + intent.act(), 3)) {
            case 0 -> Style.WEAK_POINT;
            case 1 -> Style.TALK_BACK;
            default -> Style.QUIZ;
        };
        BossRig rig = course.assets().cast(intent.game()).boss(intent.zone());
        if (rig == null) rig = BossRig.parse("* ship eggman 0 0 *=8:0").get(-1);
        return new Boss(style, rig);
    }

    boolean defeated() { return defeated && defeatRows > EXPLODE_ROWS + FLEE_ROWS; }
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
    BossRig.Mood mood() { return mood; }
    int correctTarget() { return correctId; }

    void advance(Course course, Encounter encounter) {
        row++;
        stateRow++;
        int px = course.playerX(), py = course.playerY();
        var camera = GameServices.camera();
        if (!spawned) spawn(course, camera.getX() + camera.getWidth() + 64, py - rig.height());
        if (flash > 0) flash--;
        if (laughRows > 0) laughRows--;
        if (recoil > 0) recoil--;
        int homeX = camera.getX() + camera.getWidth() * 3 / 4;
        int bob = (int) Math.round(Math.sin(row / 20.0) * rig.bob());
        if (defeated) {
            defeatRows++;
            if (defeatRows <= EXPLODE_ROWS) {
                // Explosions scattered over the craft every few rows, each with its blast.
                if (defeatRows % 6 == 1) {
                    encounter.effects().blast(course, x + course.random().nextInt(48) - 24,
                            y + course.random().nextInt(40) - 20);
                    if (defeatRows % 12 == 1) course.play(Sounds.Cue.BOSS_DEFEATED);
                }
            } else {
                facingRight = true;
                x += 4;
                if (!rig.grounded()) y -= defeatRows % 2;
            }
        } else if (lungeRow >= 0) {
            lunge(course, px, py);
        } else {
            x += Integer.signum(homeX - x) * Math.min(3, Math.abs(homeX - x));
            y = py - rig.height() + bob;
            if (targetId < 0 && answerIds.length == 0) {
                if (stateRow > (hits == 0 ? ENTRY_ROWS : RECOVER_ROWS)) openWindow(course);
            } else {
                windowRows--;
                if (windowRows <= 0) swoop(course);
            }
        }
        setMood(currentMood());
        place();
    }

    private void spawn(Course course, int startX, int startY) {
        spawned = true;
        x = startX;
        y = startY;
        stateRow = 0;
        for (BossRig.Part part : rig.parts()) {
            List<Stagehand.Visual> sprites = new ArrayList<>();
            for (int i = 0; i < part.sprites(); i++) {
                Stagehand.Visual visual = course.stagehand().add(i < spritesOfLinks(part) ? part.links().artKey()
                        : part.artKey(), x, y);
                visual.bucket = part.back() ? 4 : 5;
                sprites.add(visual);
            }
            visuals.add(sprites);
        }
        course.say(switch (style) {
            case QUIZ -> VoiceLine.ROBOTNIK_INTRO_QUIZ;
            case TALK_BACK -> VoiceLine.ROBOTNIK_TALK_BACK;
            case WEAK_POINT -> new VoiceLine[] {VoiceLine.ROBOTNIK_INTRO_A, VoiceLine.ROBOTNIK_INTRO_B,
                    VoiceLine.ROBOTNIK_INTRO_C}[course.random().nextInt(3)];
        });
    }

    private static int spritesOfLinks(BossRig.Part part) {
        return part.links() == null ? 0 : part.links().distances().length;
    }

    /** Wind up (back and up), dash at Sonic, hit him on arrival, then drift home laughing. */
    private void lunge(Course course, int px, int py) {
        lungeRow++;
        int targetX = px + 28, targetY = rig.grounded() ? py - rig.height() : py - 20;
        if (lungeRow <= WIND_UP_ROWS) {
            double t = ease(lungeRow / (double) WIND_UP_ROWS);
            x = lungeFromX + (int) Math.round(18 * t);
            y = lungeFromY - (int) Math.round(10 * t);
        } else if (lungeRow <= WIND_UP_ROWS + DASH_ROWS) {
            double t = (lungeRow - WIND_UP_ROWS) / (double) DASH_ROWS;
            int fromX = lungeFromX + 18, fromY = lungeFromY - 10;
            x = fromX + (int) Math.round((targetX - fromX) * t * t);
            y = fromY + (int) Math.round((targetY - fromY) * t * t);
            if (lungeRow == WIND_UP_ROWS + DASH_ROWS && !lungeLanded) {
                lungeLanded = true;
                course.hitSonic();
                laughRows = LAUGH_ROWS;
            }
        } else if (lungeRow <= WIND_UP_ROWS + DASH_ROWS + RETURN_ROWS) {
            var camera = GameServices.camera();
            int homeX = camera.getX() + camera.getWidth() * 3 / 4;
            double t = ease((lungeRow - WIND_UP_ROWS - DASH_ROWS) / (double) RETURN_ROWS);
            x = targetX + (int) Math.round((homeX - targetX) * t);
            y = targetY + (int) Math.round((py - rig.height() - targetY) * t);
        } else {
            lungeRow = -1;
            stateRow = 0;
        }
    }

    private static double ease(double t) { return t * t * (3 - 2 * t); }

    private BossRig.Mood currentMood() {
        if (defeated) return defeatRows <= EXPLODE_ROWS ? BossRig.Mood.DEFEAT : BossRig.Mood.FLEE;
        if (flash > 0) return BossRig.Mood.HIT;
        if (lungeRow >= 0 && !lungeLanded) return BossRig.Mood.LUNGE;
        if (laughRows > 0) return BossRig.Mood.LAUGH;
        if (targetId >= 0 || answerIds.length > 0) return BossRig.Mood.OPEN;
        return BossRig.Mood.IDLE;
    }

    private void setMood(BossRig.Mood next) {
        if (next != mood) {
            mood = next;
            moodRow = 0;
        } else {
            moodRow++;
        }
    }

    private void place() {
        int mirror = facingRight ? -1 : 1;
        // A struck craft shudders back; a beaten one shakes while it burns.
        int ox = recoil * mirror / 2 + (defeated && defeatRows <= EXPLODE_ROWS ? (defeatRows & 2) - 1 : 0);
        int blink = flash > 0 ? flash : 0;
        for (int p = 0; p < visuals.size(); p++) {
            BossRig.Part part = rig.parts().get(p);
            List<Stagehand.Visual> sprites = visuals.get(p);
            BossRig.Anim anim = part.anim(mood);
            int ax = x + ox + part.dx() * mirror, ay = y + part.dy();
            int links = spritesOfLinks(part);
            double angle = 0;
            if (part.swing() != null) {
                angle = Math.toRadians(part.swing().degrees()
                        * Math.sin(2 * Math.PI * row / Math.max(1, part.swing().periodRows())));
            }
            for (int i = 0; i < sprites.size(); i++) {
                Stagehand.Visual visual = sprites.get(i);
                visual.visible = anim != null;
                visual.blink = blink;
                visual.flipX = facingRight ^ part.flip();
                if (i < links) {
                    int d = part.links().distances()[i];
                    visual.x = ax + (int) Math.round(Math.sin(angle) * d) * mirror;
                    visual.y = ay + (int) Math.round(Math.cos(angle) * d);
                    visual.frame = part.links().frame();
                    continue;
                }
                if (anim != null) visual.frame = anim.frame(moodRow);
                if (part.swing() != null) {
                    visual.x = ax + (int) Math.round(Math.sin(angle) * part.swing().length()) * mirror;
                    visual.y = ay + (int) Math.round(Math.cos(angle) * part.swing().length());
                } else if (part.orbit() != null) {
                    var orbit = part.orbit();
                    double a = 2 * Math.PI * (row / (double) Math.max(1, orbit.periodRows())
                            + (i - links) / (double) orbit.count());
                    visual.x = ax + (int) Math.round(Math.cos(a) * orbit.radius());
                    visual.y = ay + (int) Math.round(Math.sin(a) * orbit.radius());
                } else {
                    visual.x = ax;
                    visual.y = ay;
                }
            }
        }
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
        recoil = 12;
        laughRows = 0;
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
        windowRows = 0;
    }

    /** The window closed on him unanswered (or wrongly): he lunges at Sonic. */
    private void swoop(Course course) {
        closeWindow(course);
        lungeRow = 0;
        lungeFromX = x;
        lungeFromY = y;
        lungeLanded = false;
        stateRow = 0;
    }

    /** Prompts the boss currently shows, for the HUD (empty while closed). */
    int[] openTargets() {
        if (targetId >= 0) return new int[] {targetId};
        return answerIds.clone();
    }
}
