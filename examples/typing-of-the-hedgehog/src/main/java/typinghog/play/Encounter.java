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

    /** A scheduled pickup: a monitor, a decoy or a fleeing animal. */
    record PickupArrival(int row, Pickup.Kind kind, boolean fromLeft) { }

    static final int STRIKE_DISTANCE = 20;
    static final int RECOIL_ROWS = 36;
    static final int SPAWN_MARGIN = 40;

    private final boolean boss;
    private boolean endless;
    private int wave;
    private final List<Arrival> arrivals = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<PickupArrival> pickupArrivals = new ArrayList<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private int nextPickup;
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
        Pickup.Kind[] powers = {Pickup.Kind.SHIELD, Pickup.Kind.SPEED, Pickup.Kind.INVINCIBLE, Pickup.Kind.SUPER_RING,
                Pickup.Kind.SUPER_RING, Pickup.Kind.EXTRA_LIFE};
        int span = Math.max(120, at);
        encounter.pickupArrivals.add(new PickupArrival(span / 3 + random.nextInt(60),
                powers[random.nextInt(powers.length)], random.nextBoolean()));
        if (standNumber >= 2) encounter.pickupArrivals.add(new PickupArrival(span / 2 + random.nextInt(80),
                Pickup.Kind.DECOY, random.nextBoolean()));
        for (int i = 0; i < 1 + standNumber / 3; i++) {
            encounter.pickupArrivals.add(new PickupArrival(80 + random.nextInt(span), Pickup.Kind.RESCUE, random.nextBoolean()));
        }
        encounter.pickupArrivals.sort(java.util.Comparator.comparingInt(PickupArrival::row));
        return encounter;
    }

    /** Endless: one stand whose waves keep coming, faster and longer each time, until game over. */
    static Encounter endless(Course course) {
        Encounter encounter = stand(course, 1);
        encounter.endless = true;
        return encounter;
    }

    /** Endless: plans the next, harder wave once the current one has fully arrived. */
    private void extendEndless(Course course) {
        if (!endless || nextArrival < arrivals.size()) return;
        wave++;
        List<Species> cast = course.assets().cast(course.intent().game()).zone(course.intent().zone());
        if (cast.isEmpty()) return;
        var random = course.random();
        int gap = Math.max(18, 70 - wave * 4);
        int at = row + 90;
        for (int i = 0; i < 6 + wave * 2; i++) {
            Species species = cast.get(random.nextInt(cast.size()));
            arrivals.add(new Arrival(at, species, i % 5 == 4, random.nextBoolean(),
                    species.archetype() == Species.Archetype.DIVER));
            at += gap + random.nextInt(gap);
        }
        course.hud().banner("WAVE " + (wave + 1), 0xFFFFD000);
    }

    int wave() { return wave; }

    static Encounter boss(Course course) {
        return new Encounter(true, Boss.forAct(course));
    }

    boolean isBoss() { return boss; }

    boolean finished() {
        if (boss) return bossFight != null && bossFight.defeated() && effects.idle();
        return !endless && nextArrival >= arrivals.size() && enemies.isEmpty() && effects.idle();
    }

    /** Drops pickups still on screen when the stand ends. */
    void clearPickups(Course course) {
        for (Pickup pickup : pickups) {
            course.field().remove(pickup.targetId);
            course.stagehand().remove(pickup.visual);
        }
        pickups.clear();
    }

    List<Pickup> pickups() { return pickups; }

    List<Enemy> enemies() { return enemies; }
    Effects effects() { return effects; }
    Boss bossFight() { return bossFight; }

    // ---- per advanced row ------------------------------------------------------------------------

    void advance(Course course) {
        row++;
        extendEndless(course);
        int px = course.playerX();
        int py = course.playerY();
        while (nextArrival < arrivals.size() && arrivals.get(nextArrival).row() <= row) {
            spawn(course, arrivals.get(nextArrival++), px, py);
        }
        while (nextPickup < pickupArrivals.size() && pickupArrivals.get(nextPickup).row() <= row) {
            spawnPickup(course, pickupArrivals.get(nextPickup++), px, py);
        }
        for (Pickup pickup : List.copyOf(pickups)) movePickup(course, pickup);
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
        if (arrival.elite()) course.say(typinghog.audio.VoiceLine.ANNOUNCER_ELITE);
        Enemy.Part part = new Enemy.Part(prompt, body);
        part.targetId = course.field().add(prompt, enemy.rowsToReach(px));
        if (course.invincible()) course.field().assist(part.targetId, prompt.typed().length() / 2);
        enemy.parts.add(part);
        enemies.add(enemy);
    }

    private void spawnPickup(Course course, PickupArrival arrival, int px, int py) {
        var camera = GameServices.camera();
        var intent = course.intent();
        var art = course.assets().props(intent.game()).get(arrival.kind().prop);
        String word = arrival.kind().word;
        if (arrival.kind() == Pickup.Kind.RESCUE) {
            word = course.picker().pick(List.of("rescues"), 3, 10, lead -> !course.field().leadInUse(lead));
            if (word == null) word = "hop";
        }
        Prompt prompt = Prompt.of(word, intent.capitals(), intent.punctuation());
        int x, y, vx;
        if (arrival.kind().monitor()) {
            int offset = 56 + course.random().nextInt(48);
            x = px + (arrival.fromLeft() ? -offset : offset);
            y = py + 19 - 16;
            vx = 0;
        } else {
            x = arrival.fromLeft() ? px - 24 : px + 24;
            y = py + 19 - 12;
            vx = arrival.fromLeft() ? -0x180 : 0x180;
        }
        var visual = course.stagehand().add(art == null ? "animal" : art.artKey(), x, y);
        visual.frame = art == null ? 0 : art.frame();
        visual.visible = art != null || arrival.kind() == Pickup.Kind.RESCUE;
        int id = course.field().add(prompt, arrival.kind().monitor() ? Pickup.MONITOR_ROWS : 90);
        if (arrival.kind() == Pickup.Kind.DECOY) course.say(typinghog.audio.VoiceLine.ANNOUNCER_TRAP);
        pickups.add(new Pickup(arrival.kind(), prompt, visual, id, x, y, vx));
    }

    private void movePickup(Course course, Pickup pickup) {
        pickup.rows++;
        pickup.animTick++;
        if (pickup.kind == Pickup.Kind.RESCUE) {
            pickup.x += pickup.vx;
            pickup.visual.x = pickup.px();
            int hop = (pickup.animTick % 24);
            pickup.visual.y = pickup.y - (hop < 12 ? hop : 24 - hop);
            pickup.visual.flipX = pickup.vx > 0;
            pickup.visual.frame = hop < 12 ? 1 : 0;
            var camera = GameServices.camera();
            if (pickup.px() < camera.getX() - 16 || pickup.px() > camera.getX() + camera.getWidth() + 16) removePickup(course, pickup);
        } else if (pickup.rows > Pickup.MONITOR_ROWS) {
            removePickup(course, pickup);
        } else if (pickup.rows > Pickup.MONITOR_ROWS - 90) {
            pickup.visual.blink = pickup.rows;
        }
    }

    private void removePickup(Course course, Pickup pickup) {
        course.field().remove(pickup.targetId);
        course.stagehand().remove(pickup.visual);
        pickups.remove(pickup);
    }

    private boolean completePickup(Course course, int targetId) {
        for (Pickup pickup : List.copyOf(pickups)) {
            if (pickup.targetId != targetId) continue;
            pickups.remove(pickup);
            course.stagehand().remove(pickup.visual);
            switch (pickup.kind) {
                case DECOY -> {
                    course.hud().banner("IT'S A TRAP!", 0xFFFF4040);
                    course.say(typinghog.audio.VoiceLine.ROBOTNIK_TRAP_TYPED);
                    course.hitSonic();
                }
                case RESCUE -> {
                    int points = course.card().rescue(pickup.prompt);
                    course.play(Sounds.Cue.RING);
                    course.say(typinghog.audio.VoiceLine.ANNOUNCER_ANIMAL_RESCUED);
                    effects.score(course, pickup.px(), pickup.y, points);
                }
                default -> {
                    course.play(Sounds.Cue.MONITOR);
                    effects.kill(course, pickup.px(), pickup.y, 100);
                    course.grant(pickup.kind);
                }
            }
            return true;
        }
        return false;
    }

    /** Picks a word whose lead is not already on screen. {@code tier} 1 short .. 4 long/phrase. */
    static Prompt pickPrompt(Course course, int tier, boolean phrase) {
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
                if (completePickup(course, event.target())) return;
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
                course.chainMilestone(card.chain());
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
