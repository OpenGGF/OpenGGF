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

    /** A converted stock badnik joining at the fight's first row, where it stood. */
    record LoadedArrival(LoadedBadnik badnik, Species species) { }

    /** Fewest synthetic arrivals a fight keeps when loaded stock badniks join it. */
    static final int MIN_WAVE = 3;
    /** Rows a converted stock badnik waits before advancing, so its word can be read. */
    static final int LOADED_GRACE = 30;

    private final List<LoadedArrival> loaded = new ArrayList<>();
    private boolean loadedSpawned;

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
        return stand(course, standNumber, List.of());
    }

    /**
     * A fight: the stock badniks loaded when it began join first, where they stood, and the wave
     * that follows is shorter by as many (never below {@link #MIN_WAVE}).
     */
    static Encounter stand(Course course, int standNumber, List<LoadedBadnik> loaded) {
        Encounter encounter = new Encounter(false, null);
        var intent = course.intent();
        var castTable = course.assets().cast(intent.game());
        for (LoadedBadnik badnik : loaded) {
            Species species = castTable.nativeSpecies(badnik.className(), intent.zone(), intent.act());
            if (species != null) encounter.loaded.add(new LoadedArrival(badnik, species));
        }
        List<Species> cast = castTable.zone(intent.zone(), intent.act());
        if (cast.isEmpty()) return encounter;
        var random = course.random();
        // Four or five fights an act: waves grow by one a fight (Normal: 7 to 11 badniks).
        int count = Math.max(MIN_WAVE, 4 + standNumber + intent.difficulty().tier - encounter.loaded.size());
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
        List<Species> cast = course.assets().cast(course.intent().game()).zone(course.intent().zone(), course.intent().act());
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

    /** The target a demo typist should prefer (the boss quiz's right answer), or -1. */
    int preferredTarget() { return bossFight == null ? -1 : bossFight.correctTarget(); }

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

    /** True when {@code target} belongs to something inside the camera view (or to the boss). */
    boolean onScreen(int target) {
        var camera = com.openggf.game.GameServices.camera();
        int left = camera.getX() + 8, right = camera.getX() + camera.getWidth() - 8;
        if (bossFight != null && bossFight.owns(target)) return true;
        for (Enemy enemy : enemies) {
            for (Enemy.Part part : enemy.parts) {
                if (part.targetId == target) {
                    int x = enemy.x() + part.dx;
                    return x >= left && x <= right;
                }
            }
        }
        for (Pickup pickup : pickups) {
            if (pickup.targetId == target) return pickup.x >= left && pickup.x <= right;
        }
        return true;
    }

    // ---- per advanced row ------------------------------------------------------------------------

    void advance(Course course) {
        row++;
        extendEndless(course);
        int px = course.anchorX();
        int py = course.anchorY();
        if (!loadedSpawned) {
            loadedSpawned = true;
            for (LoadedArrival arrival : loaded) spawnLoaded(course, arrival, px);
        }
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

    /** A converted stock badnik: the same species art, standing where the stock one stood, facing Sonic. */
    private void spawnLoaded(Course course, LoadedArrival arrival, int px) {
        Species species = arrival.species();
        LoadedBadnik badnik = arrival.badnik();
        boolean airborne = species.archetype() == Species.Archetype.DIVER;
        int direction = badnik.x() < px ? 1 : -1;
        Enemy enemy = new Enemy(species, false, airborne, badnik.x(), badnik.y(), airborne ? 0x140 : 0x100, direction);
        enemy.grace = LOADED_GRACE;
        var body = course.stagehand().add(species.artKey(), badnik.x(), badnik.y());
        body.flipX = species.facesRight() != (direction > 0);
        Prompt prompt = pickPrompt(course, course.intent().difficulty().tier, false);
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
            int x = pickup.px(), y = pickup.y;
            switch (pickup.kind) {
                case DECOY -> course.homing().strike(() -> x, () -> y, () -> {
                    course.stagehand().remove(pickup.visual);
                    course.hud().banner("IT'S A TRAP!", 0xFFFF4040);
                    course.say(typinghog.audio.VoiceLine.ROBOTNIK_TRAP_TYPED);
                    course.hitSonic();
                });
                case RESCUE -> {
                    course.stagehand().remove(pickup.visual);
                    int points = course.card().rescue(pickup.prompt);
                    course.play(Sounds.Cue.RING);
                    course.say(typinghog.audio.VoiceLine.ANNOUNCER_ANIMAL_RESCUED);
                    effects.score(course, x, y, points);
                }
                // Sonic breaks a monitor by striking it, as in the games.
                default -> course.homing().strike(() -> x, () -> y, () -> {
                    course.stagehand().remove(pickup.visual);
                    course.play(Sounds.Cue.MONITOR);
                    effects.kill(course, x, y, 100);
                    course.grant(pickup.kind);
                });
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
                if (enemy.grace > 0) {
                    enemy.grace--;
                    break;
                }
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

    /**
     * A finished word sends Sonic on a homing strike: the last word of a badnik dooms it at once
     * (it stops and can no longer hurt him) and the kill lands when he strikes it; an earlier word
     * of a multi-word badnik knocks it back on contact.
     */
    private void complete(Course course, int targetId) {
        for (Enemy enemy : List.copyOf(enemies)) {
            for (Enemy.Part part : enemy.parts) {
                if (part.targetId != targetId) continue;
                part.typed = true;
                Scorecard card = course.card();
                if (!enemy.allTyped()) {
                    course.homing().strike(enemy::x, enemy::y, () -> {
                        course.play(Sounds.Cue.HIT);
                        if (enemy.state == Enemy.State.APPROACH) {
                            enemy.state = Enemy.State.RECOIL;
                            enemy.stateRows = 0;
                        }
                    });
                    return;
                }
                enemy.state = Enemy.State.DYING;
                int chainBefore = card.chain();
                card.kill(part.prompt);
                if (row - lastKillRow > 0) lastKillRow = row;
                course.chainMilestone(card.chain());
                course.homing().strike(enemy::x, enemy::y, () -> {
                    effects.kill(course, enemy.x(), enemy.y(), Scorecard.popupValue(chainBefore));
                    course.play(Sounds.Cue.KILL);
                    for (Enemy.Part each : enemy.parts) course.stagehand().remove(each.visual);
                    enemies.remove(enemy);
                });
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
