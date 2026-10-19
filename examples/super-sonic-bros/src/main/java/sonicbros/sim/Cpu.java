package sonicbros.sim;

import java.util.ArrayDeque;
import sonicbros.sim.FighterDef.Slot;

/**
 * A computer player. It reads the match like a person would (where everyone is, who is
 * attacking, how much damage they have) and presses the same buttons, so it fights with every
 * character's real moves. Level 1 is sleepy and clumsy; level 9 reacts in a few frames, shields,
 * techs, survives with directional influence and guards the ledge.
 *
 * <p>Moves are chosen from their frame data (reach, startup, damage, knockback), never from a
 * per-character script, so the balance probe that pits CPUs against each other measures the
 * characters rather than the AI.
 */
public final class Cpu {
    private final Match match;
    private final Fighter me;
    public final int level;
    private final Rng rng;
    private final ArrayDeque<Integer> plan = new ArrayDeque<>();
    private int held;
    private int wait;
    private int shieldFor;
    private int ledgeWait = -1;
    private int respawnWait = -1;
    private int flapTimer;
    private Fighter target;

    public Cpu(Match match, Fighter me, int level, long seed) {
        this.match = match;
        this.me = me;
        this.level = Math.max(1, Math.min(9, level));
        this.rng = new Rng(seed ^ 0x5DEECE66DL);
    }

    private int reaction() { return Math.max(1, 20 - level * 2); }

    private Pad emit(int bits) {
        Pad p = new Pad(bits, bits & ~held);
        held = bits;
        return p;
    }

    /** Releases everything for a frame, so the next press registers as new. */
    private Pad blank() { return emit(0); }

    Pad think() {
        if (!me.alive()) { plan.clear(); return blank(); }
        if (me.hitlag > 0 && me.state != Fighter.State.HITSTUN) return emit(held);
        switch (me.state) {
            case RESPAWN -> {
                if (respawnWait < 0) respawnWait = 20 + rng.nextInt(60);
                if (--respawnWait <= 0) { respawnWait = -1; return emit(Pad.DOWN); }
                return blank();
            }
            case LEDGE -> { return ledge(); }
            case HELD -> { return emit(rng.nextInt(2) == 0 ? (held == 0 ? Pad.ATTACK : 0) : held ^ Pad.JUMP); }
            case HOLDING -> { return throwChoice(); }
            case HITSTUN -> { return hitstun(); }
            case LYING -> { return wait-- > 0 ? blank() : emit(rng.nextInt(3) == 0 ? Pad.ATTACK : towardCenter()); }
            case DIZZY -> { return emit(held == 0 ? Pad.ATTACK | Pad.JUMP : 0); }
            default -> { }
        }
        if (offstage()) { plan.clear(); return recover(); }
        if (!plan.isEmpty()) return emit(plan.poll());
        if (me.state == Fighter.State.ATTACK && me.move != null) return attackFollowThrough();
        if (shieldFor > 0) {
            shieldFor--;
            if (shieldFor == 0 && level >= 6 && target != null && dist(target) < 34 && rng.chance(0.5)) {
                plan.add(Pad.SHIELD | Pad.ATTACK);
                return emit(Pad.SHIELD);
            }
            return emit(Pad.SHIELD);
        }
        if (threatened()) return emit(Pad.SHIELD);
        if (wait > 0) { wait--; return emit(held & Pad.DIRECTIONS); }
        decide();
        return plan.isEmpty() ? blank() : emit(plan.poll());
    }

    // ------------------------------------------------------------------ situations

    private boolean offstage() {
        Arena a = match.arena;
        if (!me.grounded() && !a.over(me.x)) return true;
        return !me.grounded() && me.y > a.surfaceAt(me.x) + 4;
    }

    private int towardCenter() { return me.x < 0 ? Pad.RIGHT : Pad.LEFT; }

    private Pad hitstun() {
        int bits = 0;
        // Survival DI: hold toward the stage and up when launched hard; toward the floor at low damage.
        if (level >= 4 && rng.chance(0.15 + level * 0.09)) {
            bits |= towardCenter();
            if (me.damage > 70) bits |= Pad.UP;
        }
        // Tech on landing.
        if (level >= 3 && me.tumble && me.vy + me.ly > 2) {
            double floor = match.arena.over(me.x) ? match.arena.surfaceAt(me.x) : Double.NaN;
            if (!Double.isNaN(floor) && floor - me.y < 30 && floor - me.y > 0 && rng.chance(level * 0.09)) {
                bits |= Pad.SHIELD;
                if (held != 0 && (held & Pad.SHIELD) != 0) bits &= ~Pad.SHIELD;
            }
        }
        if (me.timer >= me.hitstun && offstage()) return recover();
        return emit(bits);
    }

    private Pad ledge() {
        if (ledgeWait < 0) ledgeWait = Math.max(8, reaction() + rng.nextInt(30));
        if (--ledgeWait > 0) return blank();
        ledgeWait = -1;
        int toward = -me.ledgeSide < 0 ? Pad.LEFT : Pad.RIGHT;
        int r = rng.nextInt(10);
        Fighter near = match.nearestOpponent(me, 50);
        if (near != null && r < 4) return emit(Pad.ATTACK);
        if (r < 3) return emit(Pad.JUMP);
        if (r < 5) return emit(Pad.SHIELD);
        return emit(toward);
    }

    private Pad throwChoice() {
        if (me.timer < 8 + rng.nextInt(10)) return blank();
        Arena a = match.arena;
        int forward = me.facing > 0 ? Pad.RIGHT : Pad.LEFT;
        int back = me.facing > 0 ? Pad.LEFT : Pad.RIGHT;
        double edgeAhead = me.facing > 0 ? a.right() - me.x : me.x - a.left;
        double edgeBehind = me.facing > 0 ? me.x - a.left : a.right() - me.x;
        Fighter t = me.holding;
        if (t != null && t.damage > 90) return emit(edgeAhead < edgeBehind ? forward : back);
        if (t != null && t.damage < 40 && rng.chance(0.6)) return emit(Pad.DOWN);
        if (rng.chance(0.3) && me.timer < 30) return emit(held == 0 ? Pad.ATTACK : 0);
        return emit(rng.chance(0.5) ? Pad.UP : forward);
    }

    private Pad attackFollowThrough() {
        Move m = me.move;
        int bits = 0;
        // Keep holding for charge moves and spin dashes for a while.
        if (m.chargeFrame >= 0 && me.moveFrame == m.chargeFrame && me.charge < 10 + rng.nextInt(level * 4 + 1)
                && target != null && target.state == Fighter.State.HITSTUN) bits |= Pad.ATTACK;
        if (m.loopFrom >= 0 && me.holdFrames < m.maxHold * (0.4 + level * 0.06)) bits |= m.holdButton;
        if (m.custom == Move.Custom.FLIGHT || m.custom == Move.Custom.GLIDE) return recover();
        if (target != null && (m.custom == Move.Custom.SPIN_DASH || m.custom == Move.Custom.ROLLING) && me.rolling) {
            // Jump out of a roll that would carry off the stage.
            Arena a = match.arena;
            double ahead = me.vx > 0 ? a.right() - me.x : me.x - a.left;
            if (ahead < 40) bits |= Pad.JUMP;
        }
        if (target != null && !me.grounded()) bits |= (target.x > me.x ? Pad.RIGHT : Pad.LEFT) & (level >= 5 ? Pad.DIRECTIONS : 0);
        return emit(bits);
    }

    private boolean threatened() {
        for (Fighter o : match.fighters) {
            if (o == me || !match.enemies(o, me) || o.state != Fighter.State.ATTACK || o.move == null) continue;
            if (!me.grounded() || me.state == Fighter.State.ATTACK) continue;
            double d = dist(o);
            double[] r = o.move.reach();
            if (d > Math.max(Math.abs(r[0]), Math.abs(r[1])) + 26) continue;
            int start = o.move.startup();
            if (start < 0 || o.moveFrame > start + 2) continue;
            if (rng.chance(level * 0.025)) {
                shieldFor = Math.max(6, o.move.duration - o.moveFrame);
                if (level >= 5 && rng.chance(0.25)) {
                    shieldFor = 0;
                    plan.add(Pad.SHIELD | Pad.DOWN);
                    plan.add(Pad.SHIELD | Pad.DOWN);
                }
                return true;
            }
        }
        // Projectiles heading this way.
        for (Projectile p : match.projectiles) {
            if (p.attacker() == me || p.attacker() == null || !match.enemies(p.attacker(), me)) continue;
            double dx = me.x - p.x;
            if (Math.abs(dx) < 50 && Math.signum(p.vx) == Math.signum(dx) && Math.abs(me.centerY() - p.y) < 30
                    && me.grounded() && rng.chance(level * 0.03)) {
                shieldFor = 10;
                return true;
            }
        }
        return false;
    }

    private double dist(Fighter o) { return Math.hypot(o.x - me.x, o.centerY() - me.centerY()); }

    // ------------------------------------------------------------------ recovery

    private Pad recover() {
        Arena a = match.arena;
        int side = me.x < (a.left + a.right()) / 2 ? -1 : 1;
        double lx = a.ledgeX(side);
        double ly = a.ledgeY(side);
        // Below the ledge, aim for the air beside it (the catch point) rather than the stage above.
        double gx = me.y > ly + 4 ? lx + side * (me.halfWidth() + 2) : lx - side * 10;
        int toward = Math.abs(gx - me.x) < 3 ? 0 : gx > me.x ? Pad.RIGHT : Pad.LEFT;
        double below = me.y - ly;
        double away = Math.abs(lx - me.x);
        // Under the island: get out from beneath it first.
        if (a.over(me.x) && me.y > a.surfaceAt(me.x)) toward = side < 0 ? Pad.LEFT : Pad.RIGHT;
        Move m = me.move;
        if (me.state == Fighter.State.ATTACK && m != null) {
            if (m.custom == Move.Custom.FLIGHT) {
                flapTimer++;
                int bits = toward;
                if (me.y > ly - 30 && flapTimer % 9 == 0) bits |= Pad.JUMP;
                return emit(bits);
            }
            if (m.custom == Move.Custom.GLIDE) return emit(toward);
            return emit(toward);
        }
        if (me.state == Fighter.State.HELPLESS || me.state == Fighter.State.AIRDODGE) return emit(toward);
        boolean clumsy = level <= 2 && rng.chance(0.4);
        if (clumsy) return emit(rng.chance(0.5) ? toward : 0);
        // Jump when below the ledge or drifting far.
        if (me.jumpsLeft > 0 && (below > -10 || away > 110) && me.vy > -1) {
            return emit((held & Pad.JUMP) != 0 ? toward : toward | Pad.JUMP);
        }
        boolean needUp = below > 6 || away > 70 && below > -40;
        if (!me.upSpecialUsed && needUp && me.vy > -1) {
            Move up = me.def.move(Slot.USPEC);
            Move side2 = me.def.move(Slot.SSPEC);
            boolean glide = side2 != null && side2.custom == Move.Custom.GLIDE && !me.sideSpecialUsed && below < 20 && away > 60;
            if (glide) {
                int dir = lx > me.x ? Pad.RIGHT : Pad.LEFT;
                return emit((held & Pad.SPECIAL) != 0 ? dir : dir | Pad.SPECIAL);
            }
            if (up != null && (below > 20 || away > me.def.recoveryReach * 0.6 || me.vy > 2)) {
                flapTimer = 0;
                return emit((held & Pad.SPECIAL) != 0 ? Pad.UP | toward : Pad.UP | Pad.SPECIAL | toward);
            }
        }
        return emit(toward);
    }

    // ------------------------------------------------------------------ choosing an attack

    private void decide() {
        target = pickTarget();
        Item item = nearbyItem();
        if (item != null && (target == null || Math.abs(item.x - me.x) < dist(target) * 0.7)) {
            approachPoint(item.x, item.y, true);
            return;
        }
        if (target == null) {
            int bits = Math.abs(me.x) > 30 ? towardCenter() : 0;
            plan.add(bits);
            wait = 10;
            return;
        }
        double dx = target.x - me.x;
        double dy = target.centerY() - me.centerY();
        int face = dx < 0 ? -1 : 1;
        Arena a = match.arena;
        boolean targetOff = !a.over(target.x) || target.y > a.surfaceAt(target.x) + 10;
        if (targetOff && target.alive()) {
            edgeguard(face);
            return;
        }
        if (rng.chance(0.08 + level * 0.07)) {
            Slot best = chooseMove(dx, dy, face);
            if (best != null) {
                queueMove(best, face);
                wait = reaction() / 2;
                return;
            }
        }
        // Projectiles and lunges from range.
        if (Math.abs(dx) > 70 && Math.abs(dy) < 40 && rng.chance(0.03 + level * 0.012)) {
            Slot ranged = rangedSpecial(Math.abs(dx));
            if (ranged != null) {
                queueMove(ranged, face);
                wait = reaction();
                return;
            }
        }
        // Approach: run in, jump toward fighters above, sometimes short-hop.
        int dir = face > 0 ? Pad.RIGHT : Pad.LEFT;
        if (me.grounded()) {
            double edgeAhead = face > 0 ? a.right() - me.x : me.x - a.left;
            if (edgeAhead < 16 && Math.abs(dx) > 30) { plan.add(0); wait = reaction(); return; }
            if (dy < -46 && Math.abs(dx) < 70 && rng.chance(0.4)) {
                plan.add(dir | Pad.JUMP);
                for (int i = 0; i < 6; i++) plan.add(dir | Pad.JUMP);
                return;
            }
            if (Math.abs(dx) < 60 && Math.abs(dy) < 30 && rng.chance(0.06 + level * 0.02)) {
                // Short hop toward them, then an aerial.
                plan.add(dir | Pad.JUMP);
                plan.add(dir);
                for (int i = 0; i < 4; i++) plan.add(dir);
                plan.add(0);
                plan.add((rng.chance(0.5) ? dir : 0) | Pad.ATTACK);
                return;
            }
            if (Math.abs(dx) > 20) {
                for (int i = 0; i < Math.min(12, reaction()); i++) plan.add(dir);
                return;
            }
            if (me.facing != face && me.state == Fighter.State.IDLE) plan.add(dir);
            plan.add(0);
            wait = reaction();
        } else {
            plan.add(Math.abs(dx) > 10 ? dir : 0);
            if (dy > 30 && me.vy > 0 && rng.chance(0.3)) plan.add(Pad.DOWN);
        }
    }

    private Fighter pickTarget() {
        Fighter best = null;
        double bestScore = Double.MAX_VALUE;
        for (Fighter o : match.fighters) {
            if (!match.enemies(me, o) || !o.onStage()) continue;
            double s = dist(o) - o.damage * 0.3 + (o.intangible() ? 200 : 0);
            if (s < bestScore) { bestScore = s; best = o; }
        }
        return best;
    }

    private Item nearbyItem() {
        Item best = null;
        for (Item i : match.items) {
            if (i.kind == Item.EGGMAN || i.ground < 0) continue;
            if (Math.abs(i.x - me.x) < 110 && Math.abs(i.y - me.y) < 60) best = i;
        }
        return best;
    }

    private void approachPoint(double x, double y, boolean attack) {
        double dx = x - me.x;
        int dir = dx < 0 ? Pad.LEFT : Pad.RIGHT;
        if (Math.abs(dx) < 24 && attack) {
            plan.add(dir);
            plan.add(0);
            plan.add(Pad.ATTACK);
            return;
        }
        for (int i = 0; i < 6; i++) plan.add(dir);
    }

    private void edgeguard(int face) {
        Arena a = match.arena;
        int side = target.x < 0 ? -1 : 1;
        double edge = side < 0 ? a.left + 12 : a.right() - 12;
        double dx = edge - me.x;
        int dir = dx < 0 ? Pad.LEFT : Pad.RIGHT;
        if (Math.abs(dx) > 14) {
            for (int i = 0; i < 4; i++) plan.add(dir);
            return;
        }
        double tdx = target.x - me.x;
        double tdy = target.centerY() - me.centerY();
        // Strong levels hit fighters coming back to the ledge.
        if (level >= 5 && Math.abs(tdx) < 50 && Math.abs(tdy) < 50 && rng.chance(0.25)) {
            int tdir = tdx < 0 ? Pad.LEFT : Pad.RIGHT;
            if (tdy > 10) plan.add(Pad.DOWN | Pad.ATTACK);
            else plan.add(tdir | Pad.ATTACK);
            return;
        }
        int face2 = tdx < 0 ? Pad.LEFT : Pad.RIGHT;
        if (me.facing != (tdx < 0 ? -1 : 1)) { plan.add(face2); plan.add(0); }
        plan.add(0);
        wait = reaction();
    }

    private Slot rangedSpecial(double distance) {
        Arena a = match.arena;
        int face = target != null && target.x < me.x ? -1 : 1;
        double room = face > 0 ? a.right() - me.x : me.x - a.left;
        for (Slot s : new Slot[] {Slot.NSPEC, Slot.SSPEC}) {
            Move m = me.def.move(s);
            if (m == null) continue;
            if (!m.spawns.isEmpty() && distance < 260) return s;
            if ((m.custom == Move.Custom.SPIN_DASH || m.custom == Move.Custom.ROLLING) && distance < 200 && me.grounded()
                    && room > distance + 60) return s;
            if (m.custom == Move.Custom.HOMING && distance < 160) return s;
        }
        return null;
    }

    /** The best move that would connect from here, or null. */
    private Slot chooseMove(double dx, double dy, int face) {
        boolean ground = me.grounded();
        Slot[] options = ground
                ? new Slot[] {Slot.JAB, Slot.FTILT, Slot.UTILT, Slot.DTILT, Slot.FSMASH, Slot.USMASH, Slot.DSMASH, Slot.GRAB,
                    Slot.DASH, Slot.NSPEC, Slot.DSPEC}
                : new Slot[] {Slot.NAIR, Slot.FAIR, Slot.BAIR, Slot.UAIR, Slot.DAIR};
        Slot best = null;
        double bestScore = 0;
        for (Slot s : options) {
            Move m = me.def.move(s);
            if (m == null || m.hits.isEmpty()) continue;
            if (s == Slot.DASH && (me.state != Fighter.State.RUN || me.runFrames < 10)) continue;
            if (s == Slot.GRAB && !(target.shielding() || rng.chance(0.25)) || s == Slot.GRAB && !target.grounded()) continue;
            if ((s == Slot.NSPEC || s == Slot.DSPEC) && (!m.spawns.isEmpty() || m.custom != Move.Custom.NONE)) continue;
            int facingFor = ground ? face : me.facing;
            int startup = Math.max(1, m.startup());
            // Predict where the target will be when the hit comes out.
            double px = dx + (target.vx + target.lx) * startup * 0.6 - me.vx * startup * 0.5;
            double py = dy + (target.vy + target.ly) * startup * 0.4;
            double fx = px * facingFor;
            double[] r = m.reach();
            double minX = r[0];
            double maxX = r[1];
            boolean inX = fx >= minX - 8 && fx <= maxX + 6;
            boolean inY = py >= r[2] - 14 && py <= r[3] + 14;
            if (!inX || !inY) continue;
            double value = m.maxDamage() + (target.damage > 80 ? m.killPower() * 0.12 : 0);
            if (s == Slot.FSMASH || s == Slot.USMASH || s == Slot.DSMASH) {
                if (target.damage < 60 && target.state != Fighter.State.HITSTUN) value *= 0.5;
            }
            double score = value / (startup + m.duration * 0.25) * (0.75 + rng.nextDouble() * 0.5);
            if (score > bestScore) { bestScore = score; best = s; }
        }
        return best;
    }

    private void queueMove(Slot s, int face) {
        int dir = face > 0 ? Pad.RIGHT : Pad.LEFT;
        boolean ground = me.grounded();
        switch (s) {
            case JAB -> { if (me.facing != face) { plan.add(dir); plan.add(0); } plan.add(Pad.ATTACK); plan.add(0); plan.add(0); plan.add(0); plan.add(Pad.ATTACK); }
            case FTILT -> { for (int i = 0; i < 6; i++) plan.add(dir); plan.add(dir | Pad.ATTACK); }
            case UTILT -> { plan.add(0); for (int i = 0; i < 5; i++) plan.add(Pad.UP); plan.add(Pad.UP | Pad.ATTACK); }
            case DTILT -> { if (me.facing != face) { plan.add(dir); plan.add(0); } for (int i = 0; i < 5; i++) plan.add(Pad.DOWN); plan.add(Pad.DOWN | Pad.ATTACK); }
            case FSMASH -> { plan.add(0); plan.add(dir | Pad.ATTACK); }
            case USMASH -> { plan.add(0); plan.add(Pad.UP | Pad.ATTACK); }
            case DSMASH -> { plan.add(0); plan.add(Pad.DOWN | Pad.ATTACK); }
            case GRAB -> { if (me.facing != face) { plan.add(dir); plan.add(0); } plan.add(Pad.SHIELD | Pad.ATTACK); }
            case DASH -> { for (int i = 0; i < 12; i++) plan.add(dir); plan.add(dir | Pad.ATTACK); }
            case NAIR -> plan.add(Pad.ATTACK);
            case FAIR -> plan.add((me.facing > 0 ? Pad.RIGHT : Pad.LEFT) | Pad.ATTACK);
            case BAIR -> plan.add((me.facing > 0 ? Pad.LEFT : Pad.RIGHT) | Pad.ATTACK);
            case UAIR -> plan.add(Pad.UP | Pad.ATTACK);
            case DAIR -> plan.add(Pad.DOWN | Pad.ATTACK);
            case NSPEC -> { if (me.facing != face) { plan.add(dir); plan.add(0); } plan.add(Pad.SPECIAL); for (int i = 0; i < 4; i++) plan.add(Pad.SPECIAL); }
            case SSPEC -> { plan.add(0); plan.add(dir | Pad.SPECIAL); for (int i = 0; i < 6; i++) plan.add(Pad.SPECIAL); }
            case DSPEC -> plan.add(Pad.DOWN | Pad.SPECIAL);
            default -> plan.add(Pad.ATTACK);
        }
        if (!ground) return;
    }
}
