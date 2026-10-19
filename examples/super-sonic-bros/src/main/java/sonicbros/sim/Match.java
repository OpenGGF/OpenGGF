package sonicbros.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A whole battle: fighters, projectiles and monitors on one {@link Arena}, stepped one frame at
 * a time from each fighter's {@link Pad}. Deterministic for a given seed and inputs, and free of
 * any engine or drawing code, so tests and the balance probe can run thousands of matches.
 */
public final class Match {
    /** Who fights: a roster entry, costume, team, and CPU level (0 for a human). */
    public record Entrant(FighterDef def, int costume, int team, int cpuLevel) { }

    public static final int COUNTDOWN = 170;
    public static final int GAME_END = 150;

    public final Rules rules;
    public final Arena arena;
    public final List<Fighter> fighters = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Item> items = new ArrayList<>();
    public final List<Event> events = new ArrayList<>();
    public final Fighter[] ledgeOwner = new Fighter[2];
    public final Rng rng;
    public long frame;
    public int countdown = COUNTDOWN;
    public int framesLeft;
    public boolean suddenDeath;
    public boolean over;
    public int endTimer;
    public int nextItem;
    /** Fighters in the order they were eliminated (stock and stamina modes). */
    public final List<Fighter> eliminated = new ArrayList<>();
    private final List<Cpu> cpus = new ArrayList<>();
    /** Per move key (fighter:slot): {hits, damage, KOs}. Statistics for the balance probe and results. */
    public final java.util.Map<String, double[]> moveStats = new java.util.HashMap<>();

    public Match(Rules rules, Arena arena, List<Entrant> entrants, long seed) {
        this.rules = rules.copy();
        this.arena = arena;
        this.rng = new Rng(seed);
        for (int i = 0; i < entrants.size(); i++) {
            Entrant e = entrants.get(i);
            Fighter f = new Fighter(this, e.def(), i, rules.teams ? e.team() : i, e.costume());
            fighters.add(f);
            cpus.add(e.cpuLevel() > 0 ? new Cpu(this, f, e.cpuLevel(), seed * 31 + i) : null);
            double sx = arena.spawnX[i % arena.spawnX.length];
            f.spawn(sx, sx < 0 ? 1 : -1);
            f.stocks = rules.mode == Rules.Mode.TIME ? 0 : rules.mode == Rules.Mode.STOCK ? rules.stocks : 1;
            f.stamina = rules.stamina;
        }
        framesLeft = rules.mode == Rules.Mode.TIME ? rules.minutes * 3600 : -1;
        nextItem = 600 + rng.nextInt(300);
    }

    public boolean isCpu(int port) { return cpus.get(port) != null; }

    public Cpu cpu(int port) { return cpus.get(port); }

    /** Runs one frame. {@code pads} has one entry per fighter; CPU entries are ignored. */
    public void step(Pad[] pads) {
        events.clear();
        if (over) {
            endTimer++;
            for (Fighter f : fighters) f.tickVisual();
            return;
        }
        if (countdown > 0) {
            countdown--;
            if (countdown == 160 || countdown == 110 || countdown == 60) sound(Sfx.STARPOST);
            if (countdown == 10) sound(Sfx.BIG_RING);
            return;
        }
        frame++;
        if (framesLeft > 0) {
            framesLeft--;
            if (framesLeft == 0) timeUp();
        }
        for (int i = 0; i < fighters.size(); i++) {
            Fighter f = fighters.get(i);
            Pad pad = cpus.get(i) != null ? cpus.get(i).think() : pads[i];
            f.step(pad == null ? Pad.none() : pad);
            f.tickVisual();
        }
        stepProjectiles();
        stepItems();
        resolveHits();
        checkBlastLines();
        checkEnd();
    }

    // ------------------------------------------------------------------ helpers for fighters

    void sound(int id) { events.add(new Event(Event.SOUND, 0, 0, id, 0, -1)); }

    void event(int type, double x, double y, int a, int b, int port) { events.add(new Event(type, x, y, a, b, port)); }

    boolean enemies(Fighter a, Fighter b) {
        if (a == b) return false;
        return a.team != b.team || rules.friendlyFire;
    }

    Fighter nearestOpponent(Fighter from, double range) {
        Fighter best = null;
        double bestD = range;
        for (Fighter f : fighters) {
            if (!enemies(from, f) || !f.onStage()) continue;
            double d = Math.hypot(f.x - from.x, f.centerY() - from.centerY());
            if (d < bestD) { bestD = d; best = f; }
        }
        return best;
    }

    void fire(Fighter owner, Projectile.Def def, double x, double y, double charge) {
        int count = 0;
        for (Projectile p : projectiles) if (p.owner == owner && p.def.kind() == def.kind() && !p.dead) count++;
        if (count >= def.maxOwned()) return;
        Projectile p = new Projectile(def, owner, x, y, owner.facing);
        p.vx *= 1 + charge * 0.5;
        projectiles.add(p);
        event(Event.SHOT, x, y, def.kind(), owner.facing, owner.port);
    }

    // ------------------------------------------------------------------ projectiles

    private void stepProjectiles() {
        for (Projectile p : projectiles) {
            if (p.dead) continue;
            p.age++;
            if (!p.resting) {
                p.vy += p.def.gravity();
                p.x += p.vx;
                p.y += p.vy;
                if (p.def.ground()) {
                    if (!arena.over(p.x)) { p.dead = true; continue; }
                    p.y = arena.surfaceAt(p.x) - p.def.radius();
                    p.vy = 0;
                } else {
                    double floor = floorUnder(p.x, p.y - p.vy, p.y + p.def.radius());
                    if (!Double.isNaN(floor) && p.vy >= 0) {
                        p.y = floor - p.def.radius();
                        if (p.def.mine()) { p.resting = true; p.vx = 0; p.vy = 0; }
                        else if (p.bouncesLeft > 0) { p.bouncesLeft--; p.vy = -Math.abs(p.vy) * 0.6; p.vx *= 0.8; sound(Sfx.FLOOR_THUMP); }
                        else if (p.def.blast() != null) { burst(p); continue; }
                        else { p.dead = true; event(Event.DUST, p.x, floor, 0, 0, -1); continue; }
                    }
                    // Projectiles stop at the island's sides and underside.
                    if (arena.over(p.x) && p.y > arena.surfaceAt(p.x) + 2 && p.y < arena.bottomAt(p.x)) {
                        if (p.def.blast() != null) burst(p); else p.dead = true;
                        continue;
                    }
                }
            }
            if (p.age >= p.def.life()) {
                if (p.def.blast() != null) burst(p); else p.dead = true;
                continue;
            }
            if (p.x < arena.blastLeft - 40 || p.x > arena.blastRight + 40 || p.y > arena.blastBottom + 40 || p.y < arena.blastTop - 80) {
                p.dead = true;
                continue;
            }
            for (Fighter f : fighters) {
                if (p.dead) break;
                if (!f.onStage() || p.struck.contains(f)) continue;
                Fighter attacker = p.attacker();
                if (attacker == f || attacker != null && !enemies(attacker, f)) continue;
                if (f.reflecting() && overlaps(p.x, p.y, p.def.radius() + 10, f) && p.def.kind() != Projectile.K_SPRING) {
                    p.reflectedBy = f;
                    p.vx = -p.vx * 1.15;
                    p.vy = -Math.abs(p.vy) * 0.5;
                    p.age = 0;
                    p.facing = -p.facing;
                    p.resting = false;
                    p.struck.clear();
                    event(Event.REFLECT, p.x, p.y, 0, 0, f.port);
                    sound(Sfx.INSTA);
                    continue;
                }
                if (!overlaps(p.x, p.y, p.def.radius(), f)) continue;
                if (f.intangible()) continue;
                if (p.def.blast() != null && !p.def.pierce()) { burst(p); break; }
                int dir = p.def.mine() || p.def.kind() == Projectile.K_SPRING ? (f.x < p.x ? -1 : 1) : (p.vx < 0 ? -1 : 1);
                applyHit(attacker, f, p.def.hit(), dir, "shot:" + p.def.kind() + ":" + (attacker == null ? -1 : attacker.port), true);
                p.struck.add(f);
                if (!p.def.pierce()) p.dead = true;
            }
            // Projectiles break monitors too.
            for (Item item : items) {
                if (!p.dead && !item.dead && p.def.kind() != Projectile.K_SPRING
                        && Math.hypot(item.x - p.x, item.y - 8 - p.y) < p.def.radius() + 10) {
                    open(item, p.attacker());
                    if (!p.def.pierce()) p.dead = true;
                }
            }
        }
        projectiles.removeIf(p -> p.dead);
    }

    /** The floor y under a point falling from {@code fromY} to {@code toY}, or NaN. */
    private double floorUnder(double x, double fromY, double toY) {
        if (arena.over(x)) {
            double s = arena.surfaceAt(x);
            if (fromY <= s + 2 && toY >= s) return s;
        }
        for (Arena.Platform pl : arena.platforms) {
            double top = pl.top(frame);
            if (x >= pl.left(frame) && x <= pl.right(frame) && fromY <= top + 2 && toY >= top) return top;
        }
        return Double.NaN;
    }

    private void burst(Projectile p) {
        p.dead = true;
        Hit blast = p.def.blast();
        explode(p.x, p.y, blast, p.attacker());
    }

    /** An explosion hitting every fighter (but its owner) within the hit's radius. */
    void explode(double x, double y, Hit blast, Fighter owner) {
        event(Event.EXPLOSION, x, y, (int) blast.r(), 0, owner == null ? -1 : owner.port);
        sound(Sfx.EXPLODE);
        event(Event.SHAKE, x, y, 3, 0, -1);
        for (Fighter f : fighters) {
            if (!f.onStage() || f == owner || owner != null && !enemies(owner, f)) continue;
            if (!overlaps(x, y, blast.r(), f) || f.intangible()) continue;
            applyHit(owner, f, blast, f.x < x ? -1 : 1, "blast", true);
        }
        for (Item item : items) if (!item.dead && Math.hypot(item.x - x, item.y - y) < blast.r() + 8) open(item, owner);
    }

    // ------------------------------------------------------------------ items

    private void stepItems() {
        if (rules.itemFrequency > 0 && rules.itemMask != 0 && !suddenDeath) {
            if (--nextItem <= 0) {
                int[] base = {0, 1100, 640, 340};
                nextItem = base[rules.itemFrequency] + rng.nextInt(base[rules.itemFrequency] / 2);
                if (items.size() < 3) spawnItem();
            }
        }
        for (Item item : items) {
            if (item.dead) continue;
            item.age++;
            if (item.age > Item.LIFE) { item.dead = true; continue; }
            if (item.ground < 0) {
                item.vy = Math.min(4, item.vy + 0.2);
                double ny = item.y + item.vy;
                double floor = floorUnder(item.x, item.y, ny);
                if (!Double.isNaN(floor)) { item.y = floor; item.vy = 0; item.ground = 1; sound(Sfx.FLOOR_THUMP); }
                else item.y = ny;
                if (item.y > arena.blastBottom) item.dead = true;
            } else if (arena.platforms.size() > 0) {
                // Ride moving platforms.
                for (Arena.Platform pl : arena.platforms) {
                    if (Math.abs(item.y - pl.top(frame - 1)) < 1 && item.x >= pl.left(frame - 1) && item.x <= pl.right(frame - 1)) {
                        item.x += pl.dx(frame) - pl.dx(frame - 1);
                        item.y = pl.top(frame);
                    }
                }
            }
        }
        items.removeIf(i -> i.dead);
    }

    private void spawnItem() {
        int total = 0;
        for (int k = 0; k < Item.KINDS; k++) if ((rules.itemMask & (1 << k)) != 0) total += Item.weight(k);
        if (total == 0) return;
        int roll = rng.nextInt(total);
        int kind = 0;
        for (int k = 0; k < Item.KINDS; k++) {
            if ((rules.itemMask & (1 << k)) == 0) continue;
            roll -= Item.weight(k);
            if (roll < 0) { kind = k; break; }
        }
        double x = arena.left + 24 + rng.nextDouble() * (arena.width() - 48);
        items.add(new Item(kind, x, arena.floorLevel() - 230));
    }

    void open(Item item, Fighter by) {
        if (item.dead) return;
        item.dead = true;
        event(Event.ITEM, item.x, item.y - 8, item.kind, 0, by == null ? -1 : by.port);
        sound(Sfx.BREAK);
        event(Event.EXPLOSION, item.x, item.y - 10, 10, 1, -1);
        if (by == null) return;
        switch (item.kind) {
            case Item.SUPER_RING -> {
                by.damage = Math.max(0, by.damage - 15);
                if (rules.mode == Rules.Mode.STAMINA) by.stamina = Math.min(rules.stamina, by.stamina + 15);
                sound(Sfx.RING);
                event(Event.SPARKLE, by.x, by.centerY(), 1, 0, by.port);
            }
            case Item.FIRE_SHIELD, Item.BUBBLE_SHIELD, Item.LIGHTNING_SHIELD -> {
                by.barrier = item.kind;
                by.barrierTimer = 20 * 60;
                sound(item.kind == Item.FIRE_SHIELD ? Sfx.FIRE_SHIELD : item.kind == Item.BUBBLE_SHIELD ? Sfx.BUBBLE_SHIELD : Sfx.LIGHTNING_SHIELD);
                if (item.kind == Item.LIGHTNING_SHIELD) by.jumpsLeft++;
            }
            case Item.INVINCIBILITY -> by.starTimer = 8 * 60;
            case Item.SPEED_SHOES -> by.shoesTimer = 10 * 60;
            case Item.EGGMAN -> explode(item.x, item.y - 10,
                    Hit.of(0, 0, 0, 0, 34, 14, 80, 45, 75).fx(Hit.FX_EXPLOSION), null);
            case Item.SUPER -> {
                by.superTimer = 12 * 60;
                sound(Sfx.SUPER);
                event(Event.SPARKLE, by.x, by.centerY(), 2, 0, by.port);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ combat

    private record Contact(Fighter attacker, Fighter target, Hit hit, double damage) { }

    static boolean overlaps(double cx, double cy, double r, Fighter f) {
        double hw = f.halfWidth();
        double nx = Math.max(f.x - hw, Math.min(cx, f.x + hw));
        double ny = Math.max(f.y - f.height(), Math.min(cy, f.y));
        double dx = cx - nx;
        double dy = cy - ny;
        return dx * dx + dy * dy <= r * r;
    }

    private void resolveHits() {
        List<Contact> contacts = new ArrayList<>();
        for (Fighter a : fighters) {
            if (a.state != Fighter.State.ATTACK || a.move == null || a.hitlag > 0) continue;
            Move m = a.move;
            if (m.custom == Move.Custom.SPIN_DASH || m.custom == Move.Custom.ROLLING) {
                if (!a.rolling) continue;
            }
            for (Hit h : m.hits) {
                if (!h.live(a.moveFrame) && !(a.rolling && h.start() >= m.loopTo - 1)) continue;
                double hx = a.x + h.x() * a.facing;
                double hy = a.centerY() + h.y();
                // Monitors break on any attack.
                for (Item item : items) {
                    if (!item.dead && a.moveSlot != FighterDef.Slot.GRAB && Math.hypot(item.x - hx, item.y - 8 - hy) < h.r() + 10) open(item, a);
                }
                // Reflectable projectiles meet reflecting hitboxes.
                for (Fighter t : fighters) {
                    if (!enemies(a, t) || !t.onStage()) continue;
                    if (h.grounded() && !t.grounded()) continue;
                    long key = (long) t.port * 1000 + h.group();
                    Integer last = a.struck.get(key);
                    if (last != null && (h.rehit() == 0 || a.clock - last < h.rehit())) continue;
                    if (!overlaps(hx, hy, h.r(), t)) continue;
                    if (a.moveSlot == FighterDef.Slot.GRAB) {
                        if (t.intangible() || !t.grounded() || t.state == Fighter.State.HELD || t.heldBy != null) continue;
                        a.grab(t);
                        break;
                    }
                    contacts.add(new Contact(a, t, h, h.damage()));
                }
                if (a.state == Fighter.State.HOLDING) break;
            }
        }
        // One hit per attacker and target per frame: the strongest.
        contacts.sort(Comparator.comparingDouble(c -> -c.damage()));
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (Contact c : contacts) {
            long pair = (long) c.attacker().port * 100 + c.target().port;
            if (!done.add(pair)) continue;
            Hit h = c.hit();
            for (Hit other : c.attacker().move == null ? List.<Hit>of() : c.attacker().move.hits) {
                if (other.group() == h.group()) c.attacker().struck.put((long) c.target().port * 1000 + other.group(), c.attacker().clock);
            }
            c.attacker().struck.put((long) c.target().port * 1000 + h.group(), c.attacker().clock);
            if (c.target().intangible()) continue;
            String id = c.attacker().def.id + ":" + c.attacker().moveSlot;
            int dir = c.attacker().facing;
            if (h.x() < -2) dir = -dir;
            if (Math.abs(h.x()) <= 6 && h.angle() != Hit.SAKURAI && (h.angle() > 60 && h.angle() < 120) == false) {
                dir = c.target().x < c.attacker().x ? -1 : 1;
            }
            boolean blocked = applyHit(c.attacker(), c.target(), h, dir, id, false);
            c.attacker().onConnect(c.target(), blocked);
        }
    }

    /**
     * Applies a hit. Returns true when a shield (or item barrier) took it instead.
     *
     * @param direction  1 to launch right, -1 left
     * @param staleKey   identifies the move for stale-move damage reduction
     */
    boolean applyHit(Fighter attacker, Fighter target, Hit h, int direction, String staleKey, boolean projectile) {
        boolean isThrow = staleKey.startsWith("throw");
        double dmg = h.damage();
        if (attacker != null) {
            dmg *= attacker.damageMul();
            if (!projectile && attacker.state == Fighter.State.ATTACK) dmg *= attacker.chargeMul();
            int seen = 0;
            for (String s : attacker.stale) if (s.equals(staleKey)) seen++;
            dmg *= seen == 0 ? 1.05 : Math.max(0.55, 1 - 0.08 * seen);
        }
        dmg *= rules.damageRatio;
        dmg = Math.round(dmg * 10) / 10.0;
        if (!isThrow && target.barrier != 0) {
            target.barrier = 0;
            target.barrierTimer = 0;
            target.invincible = Math.max(target.invincible, 40);
            event(Event.BLOCK, target.x, target.centerY(), (int) dmg, 1, target.port);
            sound(Sfx.BREAK);
            if (attacker != null && !projectile) attacker.hitlag = 6;
            return true;
        }
        if (!isThrow && target.shielding()) {
            target.shieldHp -= dmg * h.shieldMul() * 1.15;
            target.hitstun = (int) (dmg * 0.7 + 3);
            target.state = Fighter.State.SHIELD_STUN;
            target.timer = 0;
            int lag = Math.max(3, Knockback.hitlag(dmg, h.effect()) - 2);
            target.hitlag = lag;
            target.vx = direction * Math.min(3.5, 0.6 + dmg * 0.16);
            if (attacker != null && !projectile) {
                attacker.hitlag = lag;
                if (attacker.grounded() && Math.abs(attacker.x - target.x) < 40) attacker.vx = -direction * Math.min(2, dmg * 0.08);
            }
            event(Event.BLOCK, target.x + (attacker == null ? 0 : (attacker.x < target.x ? -10 : 10)), target.centerY(), (int) dmg, 0, target.port);
            sound(Sfx.CLANK);
            if (target.shieldHp <= 0) {
                target.shieldHp = 0.01;
                target.state = Fighter.State.SHIELD;
                forceShieldBreak(target);
            }
            if (attacker != null) pushStale(attacker, staleKey);
            return true;
        }
        target.applyDamage(dmg, attacker);
        double[] stat = moveStats.computeIfAbsent(staleKey.startsWith("shot") || staleKey.equals("blast")
                ? (attacker == null ? "item" : attacker.def.id) + ":" + staleKey : staleKey, k -> new double[3]);
        stat[0]++;
        stat[1] += dmg;
        target.lastHitKey = staleKey.startsWith("shot") || staleKey.equals("blast")
                ? (attacker == null ? "item" : attacker.def.id) + ":" + staleKey : staleKey;
        double percent = target.damage;
        if (rules.mode == Rules.Mode.STAMINA) percent = Math.min(percent, 50);
        double weight = target.st.weight() * (target.barrier == Item.BUBBLE_SHIELD ? 1.2 : 1);
        double kb = Knockback.of(dmg, percent, weight, h.baseKb(), h.growth());
        if (target.state == Fighter.State.CROUCH) kb *= 0.85;
        if (suddenDeath) kb *= 1.0;
        if (rules.mode == Rules.Mode.STAMINA && target.stamina <= 0) {
            kb = Math.max(kb, 160);
        }
        boolean armorHold = target.armored() && kb < 120 && rules.mode != Rules.Mode.STAMINA;
        int lag = Knockback.hitlag(dmg, h.effect());
        if (isThrow) lag = 2;
        if (h.rehit() > 0) lag = Math.min(lag, 3);
        if (attacker != null && !projectile) attacker.hitlag = lag;
        if (armorHold) {
            target.hitlag = Math.max(2, lag / 2);
            event(Event.HIT, target.x, target.centerY(), (int) (kb / 3), h.effect(), target.port);
            sound(Sfx.CLANK);
        } else {
            double angle = Knockback.angle(h.angle(), kb, target.grounded());
            target.hurt(kb, angle, direction, lag, target.grounded());
            double ex = attacker != null && !projectile ? (target.x + attacker.x + attacker.facing * 12) / 2 : target.x;
            event(Event.HIT, ex, target.centerY(), (int) kb, h.effect(), target.port);
            if (kb >= 125) event(Event.SHAKE, target.x, target.y, kb >= 170 ? 6 : 4, 0, target.port);
            int s = switch (h.effect()) {
                case Hit.FX_FIRE -> Sfx.FIRE_ATTACK;
                case Hit.FX_ELECTRIC -> Sfx.ELECTRIC;
                case Hit.FX_EXPLOSION -> Sfx.EXPLODE;
                default -> kb >= 110 ? Sfx.BOSS_HIT : kb >= 55 ? Sfx.SPIKE_HIT : Sfx.THUMP;
            };
            sound(s);
        }
        if (attacker != null) pushStale(attacker, staleKey);
        return false;
    }

    private void forceShieldBreak(Fighter target) {
        // Let the fighter's own shield logic run the break on its next frame.
        target.shieldHp = -1;
        target.hitlag = 0;
        target.state = Fighter.State.SHIELD;
    }

    private void pushStale(Fighter attacker, String key) {
        attacker.stale.addFirst(key);
        while (attacker.stale.size() > 9) attacker.stale.removeLast();
    }

    // ------------------------------------------------------------------ KOs and the end

    private void checkBlastLines() {
        for (Fighter f : fighters) {
            if (!f.onStage()) continue;
            int side = -1;
            if (f.x < arena.blastLeft) side = 0;
            else if (f.x > arena.blastRight) side = 1;
            else if (f.y > arena.blastBottom) side = 3;
            else if (f.y - f.height() < arena.blastTop && (f.state == Fighter.State.HITSTUN || f.y < arena.blastTop - 120)) side = 2;
            if (rules.mode == Rules.Mode.STAMINA && f.stamina <= 0 && f.state != Fighter.State.HITSTUN) side = 4;
            if (side >= 0) ko(f, side);
        }
    }

    private void ko(Fighter f, int side) {
        double ex = Math.max(arena.blastLeft, Math.min(arena.blastRight, f.x));
        double ey = Math.max(arena.blastTop, Math.min(arena.blastBottom, f.y));
        if (side == 2 && rng.nextInt(3) == 0) event(Event.STAR_KO, ex, ey, 0, 0, f.port);
        else event(Event.KO, ex, ey, side, 0, f.port);
        sound(side == 4 ? Sfx.EXPLODE : Sfx.BLAST);
        event(Event.SHAKE, ex, ey, 7, 0, f.port);
        Fighter credit = f.lastHitBy != null && f.lastHitAge < 480 ? f.lastHitBy : null;
        if (credit != null && credit != f) {
            credit.kos++;
            if (f.lastHitKey != null) moveStats.computeIfAbsent(f.lastHitKey, k -> new double[3])[2]++;
            if (rules.mode == Rules.Mode.TIME) credit.score++;
        } else {
            f.selfDestructs++;
        }
        f.falls++;
        if (rules.mode == Rules.Mode.TIME) f.score--;
        if (f.heldBy != null) f.heldBy.release(f);
        if (f.holding != null) f.release(f.holding);
        for (int i = 0; i < 2; i++) if (ledgeOwner[i] == f) ledgeOwner[i] = null;
        if (rules.mode != Rules.Mode.TIME) f.stocks--;
        f.state = Fighter.State.DEAD;
        f.respawnTimer = 80;
        f.lastHitBy = null;
        f.vx = f.vy = f.lx = f.ly = 0;
        f.move = null;
        if (rules.mode != Rules.Mode.TIME && f.stocks <= 0) {
            f.state = Fighter.State.OUT;
            eliminated.add(f);
        }
    }

    void afterKo(Fighter f) {
        f.respawn();
    }

    private void timeUp() {
        int best = Integer.MIN_VALUE;
        for (Fighter f : fighters) best = Math.max(best, teamScore(f.team));
        List<Fighter> leaders = new ArrayList<>();
        for (Fighter f : fighters) if (teamScore(f.team) == best) leaders.add(f);
        java.util.Set<Integer> teams = new java.util.HashSet<>();
        for (Fighter f : leaders) teams.add(f.team);
        if (teams.size() <= 1) {
            finish();
            return;
        }
        // Sudden death: the tied fighters, one stock each, starting at 300%.
        suddenDeath = true;
        framesLeft = -1;
        projectiles.clear();
        items.clear();
        countdown = 120;
        rules.mode = Rules.Mode.STOCK;
        for (Fighter f : fighters) {
            if (leaders.contains(f)) {
                f.respawn();
                f.state = Fighter.State.IDLE;
                double sx = arena.spawnX[f.port % arena.spawnX.length];
                f.spawn(sx, sx < 0 ? 1 : -1);
                f.damage = 300;
                f.stocks = 1;
            } else {
                f.state = Fighter.State.OUT;
                f.stocks = 0;
            }
        }
    }

    private int teamScore(int team) {
        int s = 0;
        for (Fighter f : fighters) if (f.team == team) s += f.score;
        return s;
    }

    private void checkEnd() {
        if (rules.mode == Rules.Mode.TIME && !suddenDeath) return;
        java.util.Set<Integer> standing = new java.util.HashSet<>();
        for (Fighter f : fighters) if (f.state != Fighter.State.OUT) standing.add(f.team);
        if (standing.size() <= 1) finish();
    }

    private void finish() {
        over = true;
        endTimer = 0;
        sound(Sfx.BOSS_HIT);
        // Placements: stock/stamina by survival, time by score then damage.
        List<Fighter> order = new ArrayList<>(fighters);
        if (rules.mode == Rules.Mode.TIME && !suddenDeath) {
            order.sort(Comparator.comparingInt((Fighter f) -> -teamScore(f.team)).thenComparingDouble(f -> f.damage));
        } else {
            order.sort(Comparator.comparingInt((Fighter f) -> f.state == Fighter.State.OUT ? 1 : 0)
                    .thenComparingInt(f -> -eliminated.indexOf(f)));
        }
        int place = 1;
        for (int i = 0; i < order.size(); i++) {
            Fighter f = order.get(i);
            if (i > 0) {
                Fighter prev = order.get(i - 1);
                boolean tie = rules.mode == Rules.Mode.TIME && !suddenDeath
                        ? teamScore(prev.team) == teamScore(f.team) : false;
                boolean sameTeam = rules.teams && prev.team == f.team && prev.placement == 1 && place == 1;
                if (!tie && !sameTeam) place = i + 1;
            }
            f.placement = place;
        }
    }

    /** The winning fighters (placement 1). */
    public List<Fighter> winners() {
        List<Fighter> w = new ArrayList<>();
        for (Fighter f : fighters) if (f.placement == 1) w.add(f);
        return w;
    }
}
