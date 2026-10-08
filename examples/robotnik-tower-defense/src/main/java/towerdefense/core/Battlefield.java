package towerdefense.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic siege rules. No engine, drawing, random or wall-clock dependencies. */
public final class Battlefield {
    public static final String PREP = "PREP", WAVE = "WAVE", WON = "WON", LOST = "LOST";
    public static final int SITES = 10, DOOR_MAX = 200, DOOR_X = 347, BOMB_RELOAD = 1200;
    private final Tower[] towers = new Tower[SITES];
    final List<Flicky> birds = new ArrayList<>();
    private final List<Effect> effects = new ArrayList<>();
    private String phase = PREP;
    private int scrap = 150, wave, ticks, waveTicks, spawnIndex, kills, bombCooldown, shots;
    int doorHp = DOOR_MAX, nextBirdId;
    private Catalog.Wave activeWave;

    public static int siteX(int site) { return 66 + (site % 5) * 54; }
    public static int siteY(int site) { return site < 5 ? 174 : 113; }
    public Tower tower(int site) { return validSite(site) ? towers[site] : null; }
    public List<Flicky> birds() { return List.copyOf(birds); }
    public List<Effect> effects() { return List.copyOf(effects); }
    public String phase() { return phase; }
    public int scrap() { return scrap; }
    public int doorHp() { return doorHp; }
    public int wave() { return wave; }
    public int ticks() { return ticks; }
    public int kills() { return kills; }
    public int shots() { return shots; }
    public int bombCooldown() { return bombCooldown; }
    public int score() { return kills * 10 + wave * 100 + (phase.equals(WON) ? doorHp * 5 : 0); }
    public boolean finished() { return phase.equals(WON) || phase.equals(LOST); }
    public Catalog.Wave preview() { return Catalog.wave(Math.min(Catalog.WAVES, wave + (phase.equals(PREP) ? 1 : 0))); }
    public int remainingSpawns() { return activeWave == null ? 0 : activeWave.spawns().size() - spawnIndex; }
    private boolean validSite(int site) { return site >= 0 && site < SITES; }

    public boolean build(int site, int kind) {
        if (finished() || !validSite(site) || towers[site] != null || kind < 0 || kind > 5) return false;
        int cost = Catalog.defense(kind).cost();
        if (scrap < cost) return false;
        scrap -= cost;
        towers[site] = new Tower(site, kind);
        return true;
    }

    public int upgradeCost(int site) {
        Tower t = tower(site);
        return t == null || t.level >= 3 ? 0 : Catalog.defense(t.kind).cost() * (t.level + 1) / 2;
    }

    public boolean upgrade(int site) {
        int cost = upgradeCost(site);
        if (finished() || cost == 0 || scrap < cost) return false;
        Tower t = tower(site);
        scrap -= cost;
        t.invested += cost;
        t.level++;
        return true;
    }

    public int sellValue(int site) { return tower(site) == null ? 0 : tower(site).invested * 3 / 4; }

    public boolean sell(int site) {
        if (finished() || tower(site) == null) return false;
        scrap += sellValue(site);
        towers[site] = null;
        return true;
    }

    public boolean repair() {
        if (!phase.equals(PREP) || doorHp >= DOOR_MAX || scrap < 25) return false;
        scrap -= 25;
        doorHp = Math.min(DOOR_MAX, doorHp + 40);
        return true;
    }

    public boolean startWave() {
        if (!phase.equals(PREP)) return false;
        wave++;
        activeWave = Catalog.wave(wave);
        waveTicks = spawnIndex = 0;
        phase = WAVE;
        return true;
    }

    /** Repels a breach; cooldown is measured in unpaused wave ticks. */
    public boolean bomb() {
        if (!phase.equals(WAVE) || bombCooldown > 0) return false;
        bombCooldown = BOMB_RELOAD;
        for (Flicky bird : birds) {
            bird.hp -= 28 + wave;
            bird.x = Math.max(-12, bird.x - 65);
            bird.slowTicks = 120;
            bird.hitTicks = 8;
        }
        effect("bomb", DOOR_X, 122, 0, 0, 0xFFFFDB84, 40);
        cleanDefeated();
        return true;
    }

    public void update() {
        if (finished()) return;
        effects.removeIf(e -> ++e.age >= e.lifetime);
        if (!phase.equals(WAVE)) return;
        ticks++;
        if (bombCooldown > 0) bombCooldown--;
        while (spawnIndex < activeWave.spawns().size() && activeWave.spawns().get(spawnIndex).tick() <= waveTicks) {
            Catalog.Spawn spawn = activeWave.spawns().get(spawnIndex++);
            birds.add(new Flicky(spawn.kind(), wave, nextBirdId++));
        }
        waveTicks++;
        // Compute support from frame-start positions, so list order cannot change the aura.
        List<Flicky> organisers = birds.stream().filter(f -> f.kind == Catalog.ORGANISER && f.hp > 0).toList();
        boolean[] supported = new boolean[birds.size()];
        for (int i = 0; i < birds.size(); i++) {
            Flicky candidate = birds.get(i);
            supported[i] = organisers.stream().anyMatch(o -> o != candidate && Math.abs(o.x - candidate.x) < 56);
        }
        for (int i = 0; i < birds.size(); i++) {
            Flicky bird = birds.get(i);
            if (bird.hp <= 0) continue;
            double movement = bird.speed * (supported[i] ? 1.35 : 1) * (bird.slowTicks > 0 ? 0.48 : 1);
            bird.x = Math.min(DOOR_X, bird.x + movement);
            if (bird.hitTicks > 0) bird.hitTicks--;
            if (bird.slowTicks > 0) bird.slowTicks--;
            if (bird.sabotageTicks > 0) bird.sabotageTicks--;
            if (bird.kind == Catalog.SABOTEUR && bird.sabotageTicks == 0) {
                Tower closest = null;
                double distance = 38 * 38;
                for (Tower tower : towers) {
                    if (tower == null) continue;
                    double d = distanceSquared(bird.x, bird.y, tower.x(), tower.y());
                    if (d < distance) { distance = d; closest = tower; }
                }
                if (closest != null) {
                    closest.jamTicks = 90;
                    bird.sabotageTicks = 240;
                    effect("jam", bird.x, bird.y, closest.x(), closest.y(), 0xFFB5F084, 20);
                }
            }
        }
        for (Tower tower : towers) {
            if (tower == null) continue;
            if (tower.cooldown > 0) tower.cooldown--;
            if (tower.jamTicks > 0) { tower.jamTicks--; continue; }
            if (tower.cooldown > 0) continue;
            Flicky target = birds.stream().filter(f -> eligible(tower, f))
                    .max(Comparator.comparingDouble(f -> f.x)).orElse(null);
            if (target != null) fire(tower, target);
        }
        cleanDefeated();
        for (Flicky bird : birds) {
            if (bird.x >= DOOR_X && ++bird.peckTicks >= 60) {
                bird.peckTicks = 0;
                doorHp = Math.max(0, doorHp - (bird.kind == Catalog.SHIELD ? 4 : 2));
                effect("peck", DOOR_X, bird.y, 0, 0, 0xFFFF806C, 14);
            }
        }
        if (doorHp == 0) { phase = LOST; return; }
        if (spawnIndex == activeWave.spawns().size() && birds.isEmpty()) {
            scrap += 35 + wave * 5;
            phase = wave == Catalog.WAVES ? WON : PREP;
        }
    }

    private boolean eligible(Tower t, Flicky f) {
        if (f.hp <= 0 || t.kind == Catalog.BUGGERNAUT && !f.flying() || t.kind == Catalog.MORTAR && f.flying()) return false;
        return distanceSquared(t.x(), t.y(), f.x, f.y) <= t.range() * t.range();
    }

    private void fire(Tower t, Flicky target) {
        t.cooldown = t.reload();
        shots++;
        int color = Catalog.defense(t.kind).color();
        effect("shot", t.x(), t.y(), target.x, target.y, color, 9);
        if (t.kind == Catalog.MORTAR || t.kind == Catalog.ORBINAUT) {
            int radius = t.kind == Catalog.MORTAR ? 25 + t.level * 3 : t.range();
            double cx = t.kind == Catalog.MORTAR ? target.x : t.x();
            double cy = t.kind == Catalog.MORTAR ? target.y : t.y();
            for (Flicky bird : birds) {
                if (bird.hp <= 0 || t.kind == Catalog.MORTAR && bird.flying()) continue;
                if (distanceSquared(cx, cy, bird.x, bird.y) <= radius * radius) {
                    // Lobbed explosions land behind the carried shield. Direct fire cannot.
                    hit(bird, t.damage(), t.kind == Catalog.MORTAR);
                    if (t.kind == Catalog.ORBINAUT) bird.slowTicks = 90 + t.level * 15;
                }
            }
            effect("burst", cx, cy, radius, 0, color, 18);
        } else if (t.kind == Catalog.EGG_ROBO) {
            List<Flicky> nearby = birds.stream().filter(f -> f != target && f.hp > 0
                    && distanceSquared(target.x, target.y, f.x, f.y) < 45 * 45)
                    .sorted(Comparator.comparingDouble(f -> distanceSquared(target.x, target.y, f.x, f.y)))
                    .limit(2).toList();
            hit(target, t.damage(), false);
            for (Flicky f : nearby) {
                hit(f, t.damage() * 0.75, false);
                effect("shot", target.x, target.y, f.x, f.y, color, 12);
            }
        } else {
            hit(target, t.damage(), t.kind == Catalog.SPIKER);
        }
    }

    private void hit(Flicky bird, double damage, boolean piercing) {
        int armor = bird.kind == Catalog.SHIELD && !piercing ? 3 + wave / 6 : 0;
        double reduced = damage - armor;
        // A carried industrial shield blocks 90% of direct fire; piercing and lobbed
        // explosives bypass it. The one-damage floor still permits a desperate defense.
        if (bird.kind == Catalog.SHIELD && !piercing) reduced *= 0.1;
        bird.hp -= Math.max(1, reduced);
        bird.hitTicks = 5;
    }

    private void cleanDefeated() {
        for (Flicky bird : birds) {
            if (bird.hp > 0) continue;
            kills++;
            scrap += bird.kind == Catalog.SHIELD || bird.kind == Catalog.ORGANISER || bird.kind == Catalog.SABOTEUR ? 5 : 3;
            effect("retreat", bird.x, bird.y, bird.kind, 0, Catalog.birdColor(bird.kind), 36);
        }
        birds.removeIf(f -> f.hp <= 0);
    }

    private void effect(String type, double x, double y, double tx, double ty, int color, int life) {
        // Bounded even when a whole crowd is hit by multiple area defenses on one tick.
        if (effects.size() < 240) effects.add(new Effect(type, x, y, tx, ty, color, life));
    }

    private static double distanceSquared(double x, double y, double tx, double ty) {
        return (x - tx) * (x - tx) + (y - ty) * (y - ty);
    }
}
