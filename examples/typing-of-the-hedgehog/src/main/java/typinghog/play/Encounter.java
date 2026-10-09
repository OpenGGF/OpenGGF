package typinghog.play;

import com.openggf.game.GameServices;
import java.util.ArrayList;
import java.util.List;
import typinghog.core.Prompt;
import typinghog.core.Scorecard;
import typinghog.core.TypingField;
import typinghog.save.LaunchIntent;

/**
 * One stand's (or the boss's) badniks: when they arrive, how they move, what they say, and what
 * typing does to them. Badniks walk or fly at Sonic from the screen edges; a badnik that reaches
 * him strikes, recoils and comes again. Typing a part lights it; typing the last part makes the
 * kill: a spark burst, the ROM explosion, a freed animal and the ROM score sprite.
 */
public final class Encounter {
    /** A scheduled arrival, in advanced rows since the stand began. */
    record Arrival(int row, Species species, boolean elite, boolean fromLeft, boolean airborne) { }

    static final int STRIKE_DISTANCE = 20;
    static final int RECOIL_ROWS = 36;
    static final int SPAWN_MARGIN = 40;

    private final boolean boss;
    private final List<Arrival> arrivals = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final Effects effects = new Effects();
    private final Boss bossFight;
    private int row;
    private int nextArrival;
    private int lastKillRow = -9999;

    private Encounter(boolean boss, Boss bossFight) {
        this.boss = boss;
        this.bossFight = bossFight;
    }

    // ---- planning ------------------------------------------------------------------------------

    /** A stand's waves: more and tougher badniks with each stand and with the difficulty. */
    static Encounter stand(Course course, int standNumber) {
        Encounter encounter = new Encounter(false, null);
        var intent = course.intent();
        List<Species> cast = course.assets().cast(intent.game()).zone(intent.zone());
        if (cast.isEmpty()) return encounter;
        var random = course.random();
        int count = 6 + standNumber * 2 + intent.difficulty().tier;
        int gap = Math.max(26, (int) (80 / intent.difficulty().speed) - standNumber * 4);
        int at = 40;
        for (int i = 0; i < count; i++) {
            Species species = cast.get(random.nextInt(cast.size()));
            boolean elite = i == count - 1 && standNumber >= 2;
            boolean airborne = species.archetype() == Species.Archetype.DIVER;
            encounter.arrivals.add(new Arrival(at, species, elite, random.nextBoolean(), airborne));
            at += gap + random.nextInt(gap);
            if (i % 4 == 3) at += 60;
        }
        return encounter;
    }

    static Encounter boss(Course course) {
        return new Encounter(true, Boss.forAct(course));
    }

    boolean isBoss() { return boss; }

    boolean finished() {
        if (boss) return bossFight != null && bossFight.defeated() && effects.idle();
        return nextArrival >= arrivals.size() && enemies.isEmpty() && effects.idle();
    }

    List<Enemy> enemies() { return enemies; }
    Effects effects() { return effects; }
    Boss bossFight() { return bossFight; }

    // ---- per advanced row ------------------------------------------------------------------------

    void advance(Course course) {
        row++;
        int px = course.playerX();
        int py = course.playerY();
        while (nextArrival < arrivals.size() && arrivals.get(nextArrival).row() <= row) {
            spawn(course, arrivals.get(nextArrival++), px, py);
        }
        double speedScale = course.intent().difficulty().speed * (course.intent().slowMotion() ? 0.6 : 1.0);
        for (Enemy enemy : List.copyOf(enemies)) move(course, enemy, px, py, speedScale);
        if (bossFight != null) bossFight.advance(course, this);
        effects.advance(course);
        for (Enemy enemy : enemies) {
            for (Enemy.Part part : enemy.parts) {
                if (part.targetId >= 0) course.field().setUrgency(part.targetId, enemy.rowsToReach(px));
            }
        }
    }

    private void spawn(Course course, Arrival arrival, int px, int py) {
        var camera = GameServices.camera();
        int width = camera.getWidth();
        int x = arrival.fromLeft() ? camera.getX() - SPAWN_MARGIN : camera.getX() + width + SPAWN_MARGIN;
        Species species = arrival.species();
        int feet = py + 19;
        int y = arrival.airborne() ? py - 56 - course.random().nextInt(24) : feet - species.halfHeight();
        int baseSpeed = arrival.airborne() ? 0x140 : 0x100;
        Enemy enemy = new Enemy(species, arrival.elite(), arrival.airborne(), x, y, baseSpeed,
                arrival.fromLeft() ? 1 : -1);
        var stagehand = course.stagehand();
        var body = stagehand.add(species.artKey(), x, y);
        body.flipX = species.facesRight() != (enemy.direction > 0);
        Prompt prompt = pickPrompt(course, arrival.elite() ? 4 : course.intent().difficulty().tier, arrival.elite());
        Enemy.Part part = new Enemy.Part(prompt, body);
        part.targetId = course.field().add(prompt, enemy.rowsToReach(px));
        enemy.parts.add(part);
        enemies.add(enemy);
    }

    /** Picks a word whose lead is not already on screen. {@code tier} 1 short .. 4 long/phrase. */
    Prompt pickPrompt(Course course, int tier, boolean phrase) {
        var intent = course.intent();
        var lexicon = course.assets().lexicon();
        var picker = course.picker();
        List<String> pools = new ArrayList<>(List.of("common"));
        if (intent.dictionaries().contains("lore")) pools.add("lore-" + intent.game());
        if (intent.dictionaries().contains("tricky") && tier >= 3) pools.add("tricky");
        if (intent.dictionaries().contains("user")) pools.add("user");
        if (phrase) pools = List.of("phrases");
        int min = switch (tier) { case 1 -> 3; case 2 -> 4; case 3 -> 6; default -> 8; };
        int max = switch (tier) { case 1 -> 5; case 2 -> 7; case 3 -> 9; default -> phrase ? 32 : 12; };
        String word = picker.pick(pools, min, max, lead -> !course.field().leadInUse(lead));
        if (word == null) word = picker.pick(List.of("common"), 3, 12, lead -> true);
        if (word == null) word = "ring";
        return Prompt.of(word, intent.capitals(), intent.punctuation());
    }

    private void move(Course course, Enemy enemy, int px, int py, double speedScale) {
        enemy.tick++;
        Enemy.Part body = enemy.parts.getFirst();
        Species species = enemy.species;
        switch (enemy.state) {
            case APPROACH -> {
                int step = (int) (enemy.speed * speedScale);
                enemy.x += enemy.direction * step;
                if (enemy.airborne) {
                    int targetY = Math.abs(enemy.x() - px) < 96 ? py : enemy.homeY;
                    enemy.y += Integer.signum((targetY << 8) - enemy.y) * Math.min(Math.abs((targetY << 8) - enemy.y), 0x120);
                }
                if (Math.abs(enemy.x() - px) <= STRIKE_DISTANCE && Math.abs(enemy.y() - py) <= 40) {
                    course.hitSonic();
                    enemy.state = Enemy.State.RECOIL;
                    enemy.stateRows = 0;
                    effects.impact(course, px, py);
                }
            }
            case RECOIL -> {
                enemy.x -= enemy.direction * 0x200;
                if (++enemy.stateRows >= RECOIL_ROWS) enemy.state = Enemy.State.APPROACH;
            }
            case DYING, GONE -> { }
        }
        body.visual.x = enemy.x();
        body.visual.y = enemy.y();
        body.visual.frame = species.frameAt(enemy.tick);
        body.visual.flipX = species.facesRight() != (enemy.direction > 0);
        if (body.visual.blink > 0) body.visual.blink--;
    }

    // ---- typing -----------------------------------------------------------------------------------

    void onEvent(Course course, TypingField.Event event, int codePoint) {
        var card = course.card();
        switch (event.kind()) {
            case LOCK, HIT -> {
                card.keystroke();
                course.play(event.kind() == TypingField.Kind.LOCK ? Sounds.Cue.LOCK : Sounds.Cue.KEY);
                course.keyStats().hit(codePoint);
                Enemy.Part part = partFor(event.target());
                if (part != null) effects.spark(course, part.visual.x, part.visual.y);
                if (bossFight != null) bossFight.onKey(course, event);
            }
            case COMPLETE -> {
                card.keystroke();
                course.keyStats().hit(codePoint);
                if (bossFight != null && bossFight.owns(event.target())) {
                    bossFight.onComplete(course, this, event.target());
                    return;
                }
                complete(course, event.target());
            }
            case ERROR -> {
                if (event.target() < 0 && effects.collectRing(course, codePoint)) return;
                card.error();
                course.play(Sounds.Cue.ERROR);
                var target = event.target() >= 0 ? course.field().target(event.target()) : null;
                if (target != null) {
                    course.keyStats().miss(target.prompt().typed().codePointAt(target.progress()));
                    Enemy.Part part = partFor(event.target());
                    if (part != null) part.visual.blink = 8;
                }
                course.hud().shake();
            }
            case RELEASE, IGNORED -> { }
        }
    }

    void onRelease(Course course, TypingField.Event event) {
        if (event.kind() == TypingField.Kind.RELEASE) course.card().error();
    }

    private void complete(Course course, int targetId) {
        for (Enemy enemy : List.copyOf(enemies)) {
            for (Enemy.Part part : enemy.parts) {
                if (part.targetId != targetId) continue;
                part.typed = true;
                if (!enemy.allTyped()) return;
                Scorecard card = course.card();
                int chainBefore = card.chain();
                card.kill(part.prompt);
                if (row - lastKillRow > 0) lastKillRow = row;
                effects.kill(course, enemy.x(), enemy.y(), Scorecard.popupValue(chainBefore));
                course.play(Sounds.Cue.KILL);
                for (Enemy.Part each : enemy.parts) course.stagehand().remove(each.visual);
                enemies.remove(enemy);
                return;
            }
        }
    }

    Enemy.Part partFor(int targetId) {
        for (Enemy enemy : enemies) for (Enemy.Part part : enemy.parts) if (part.targetId == targetId) return part;
        return null;
    }

    /** The plate style a part's target should draw with. */
    typinghog.art.Plates.Style styleFor(Course course, Enemy enemy, Enemy.Part part) {
        if (course.field().locked() == part.targetId) return typinghog.art.Plates.Style.LOCKED;
        return enemy.elite ? typinghog.art.Plates.Style.ELITE : typinghog.art.Plates.Style.NORMAL;
    }

    LaunchIntent intent(Course course) { return course.intent(); }
}
