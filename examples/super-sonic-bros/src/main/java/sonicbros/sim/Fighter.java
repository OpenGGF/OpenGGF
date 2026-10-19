package sonicbros.sim;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import sonicbros.sim.FighterDef.Slot;

/**
 * One fighter in a match: position, damage, stocks and the state machine that turns a
 * {@link Pad} into running, jumping, attacking, shielding, dodging, hanging from ledges and being
 * launched. Positions are the feet; the body is a box {@code width} wide and {@code height} tall
 * above them.
 */
public final class Fighter {
    public enum State {
        IDLE, RUN, SKID, CROUCH, JUMPSQUAT, AIR, LAND, ATTACK, SHIELD, SHIELD_STUN, SHIELD_DROP, ROLL, SPOTDODGE,
        AIRDODGE, HITSTUN, LYING, GETUP, TECH, HELPLESS, LEDGE, LEDGE_CLIMB, LEDGE_ROLL, HOLDING, THROWING, HELD,
        DIZZY, RESPAWN, DEAD, OUT
    }

    /** Frames an input stays buffered while the fighter cannot act. */
    static final int BUFFER = 8;
    static final double SHIELD_MAX = 50;

    public final Match match;
    public final FighterDef def;
    public final FighterDef.Stats st;
    public final int port;
    public final int team;
    public final int costume;

    public double x;
    public double y;
    public double vx;
    public double vy;
    /** Launch velocity from knockback, decaying separately from movement. */
    public double lx;
    public double ly;
    public int facing = 1;
    /** -1 airborne, 0 the island, 1+ platform index + 1. */
    public int ground = -1;
    public State state = State.IDLE;
    public int timer;
    public Move move;
    public Slot moveSlot;
    public int moveFrame;
    public int charge;
    public int holdFrames;
    public int jumpsLeft;
    public boolean airDodged;
    public boolean fastFalling;
    public boolean upSpecialUsed;
    public boolean sideSpecialUsed;
    public boolean homingUsed;
    public int hitlag;
    public int hitstun;
    public boolean tumble;
    public int invincible;
    public double shieldHp = SHIELD_MAX;
    public double damage;
    public int stocks;
    public double stamina;
    public int score;
    public int dropThrough;
    public int ledgeSide;
    public int ledgeCooldown;
    public boolean ledgeInvulnUsed;
    public Fighter holding;
    public Fighter heldBy;
    public int holdTimer;
    public FighterDef.Throw activeThrow;
    public Fighter lastHitBy;
    /** The move that last hit this fighter, for KO statistics. */
    public String lastHitKey;
    public int lastHitAge = 9999;
    public int combo;
    public int comboTimer;
    public int starTimer;
    public int shoesTimer;
    public int superTimer;
    public int barrier;
    public int barrierTimer;
    public int flightTimer;
    public int glideTimer;
    public boolean rolling;
    public int rollTimer;
    public int techWindow;
    public int techLockout;
    public int respawnTimer;
    public int placement;
    public int frozenUntilLaunch;
    public int pendingLaunchDirection;
    public double pendingKb;
    public double pendingAngle;
    public boolean pendingGrounded;
    public double chargeAmount;
    public int ageOfPose;
    public int lastPose = -1;
    public int skidTimer;
    public int runFrames;
    public int dirFreshX;
    public int dirFreshY;
    private int bufferBits;
    private int bufferAge = 99;
    public Pad pad = Pad.none();

    // Statistics for the results screen.
    public int kos;
    public int falls;
    public int selfDestructs;
    public double damageDealt;
    public double damageTaken;
    public double peakDamage;
    public int framesAlive;
    /** Frames this fighter has actually run (not counting hitlag): times its multi-hit repeats. */
    public int clock;

    final Map<Long, Integer> struck = new HashMap<>();
    final ArrayDeque<String> stale = new ArrayDeque<>();

    Fighter(Match match, FighterDef def, int port, int team, int costume) {
        this.match = match;
        this.def = def;
        this.st = def.stats;
        this.port = port;
        this.team = team;
        this.costume = costume;
    }

    // ------------------------------------------------------------------ queries

    public double halfWidth() { return st.width() / 2; }

    public double height() { return st.height(); }

    public double centerY() { return y - st.height() / 2; }

    public boolean grounded() { return ground >= 0; }

    public boolean alive() { return state != State.DEAD && state != State.OUT; }

    public boolean onStage() { return alive() && state != State.RESPAWN; }

    public boolean intangible() {
        if (!alive() || state == State.RESPAWN) return true;
        if (invincible > 0 || starTimer > 0 || superTimer > 0) return true;
        switch (state) {
            case ROLL: return timer >= 3 && timer < 18;
            case SPOTDODGE: return timer >= 2 && timer < 18;
            case AIRDODGE: return timer >= 3 && timer < 28;
            case TECH: return timer < 18;
            case LEDGE_CLIMB: case LEDGE_ROLL: return true;
            case LEDGE: return !ledgeInvulnUsed && timer < 40;
            case GETUP: return timer < 18;
            default: break;
        }
        if (state == State.ATTACK && move != null) {
            if (moveFrame >= move.intangibleFrom && moveFrame <= move.intangibleTo) return true;
        }
        return false;
    }

    public boolean shielding() { return state == State.SHIELD || state == State.SHIELD_STUN; }

    public boolean reflecting() {
        return state == State.ATTACK && move != null && moveFrame >= move.reflectFrom && moveFrame <= move.reflectTo;
    }

    public boolean armored() {
        return state == State.ATTACK && move != null && moveFrame >= move.armorFrom && moveFrame <= move.armorTo;
    }

    /** Current shield bubble radius (it shrinks as it weakens). */
    public double shieldRadius() { return 9 + 15 * shieldHp / SHIELD_MAX; }

    public double speedMul() { return (shoesTimer > 0 ? 1.35 : 1.0) * (superTimer > 0 ? 1.25 : 1.0); }

    public double damageMul() { return (superTimer > 0 ? 1.3 : 1.0) * (barrier == Item.FIRE_SHIELD ? 1.15 : 1.0); }

    /** Charge multiplier of the current move: 1 uncharged, up to 1.4 for a full smash charge. */
    public double chargeMul() { return 1 + chargeAmount; }

    // ------------------------------------------------------------------ setup

    void spawn(double sx, int face) {
        x = sx;
        y = match.arena.surfaceAt(sx);
        vx = vy = lx = ly = 0;
        facing = face;
        ground = 0;
        state = State.IDLE;
        timer = 0;
        jumpsLeft = st.airJumps();
        damage = 0;
        invincible = 0;
        shieldHp = SHIELD_MAX;
    }

    void respawn() {
        x = 0;
        y = match.arena.floorLevel() - 120;
        vx = vy = lx = ly = 0;
        ground = -1;
        state = State.RESPAWN;
        timer = 0;
        damage = 0;
        hitstun = 0;
        tumble = false;
        shieldHp = SHIELD_MAX;
        jumpsLeft = st.airJumps();
        upSpecialUsed = sideSpecialUsed = homingUsed = false;
        barrier = 0;
        superTimer = 0;
        starTimer = 0;
        shoesTimer = 0;
        move = null;
        holding = null;
        heldBy = null;
        fastFalling = false;
        combo = 0;
        if (match.rules.mode == Rules.Mode.STAMINA) stamina = match.rules.stamina;
    }

    // ------------------------------------------------------------------ per frame

    /** Reads input and runs one frame of state and movement. */
    void step(Pad input) {
        if (!alive()) {
            pad = Pad.none();
            if (state == State.DEAD) {
                respawnTimer--;
                if (respawnTimer <= 0) match.afterKo(this);
            }
            return;
        }
        // Keep presses that arrive while the fighter cannot act.
        int newPresses = input.pressed() & ~(Pad.START);
        if (newPresses != 0) {
            bufferBits = newPresses;
            bufferAge = 0;
        } else if (bufferAge < 99) {
            bufferAge++;
        }
        pad = input;
        if ((input.pressed() & (Pad.LEFT | Pad.RIGHT)) != 0) dirFreshX = 0; else dirFreshX++;
        if ((input.pressed() & (Pad.UP | Pad.DOWN)) != 0) dirFreshY = 0; else dirFreshY++;
        if (input.hit(Pad.SHIELD)) {
            if (techLockout <= 0) techWindow = 20;
            techLockout = 40;
        }
        if (techWindow > 0) techWindow--;
        if (techLockout > 0) techLockout--;
        if (hitlag > 0) {
            hitlag--;
            if (hitlag == 0 && frozenUntilLaunch > 0) launchNow();
            return;
        }
        framesAlive++;
        clock++;
        lastHitAge++;
        if (comboTimer > 0 && --comboTimer == 0) combo = 0;
        if (invincible > 0) invincible--;
        if (starTimer > 0) starTimer--;
        if (shoesTimer > 0) shoesTimer--;
        if (superTimer > 0) superTimer--;
        if (barrierTimer > 0 && --barrierTimer == 0) barrier = 0;
        if (ledgeCooldown > 0) ledgeCooldown--;
        if (dropThrough > 0) dropThrough--;
        if (!shielding() && shieldHp < SHIELD_MAX) shieldHp = Math.min(SHIELD_MAX, shieldHp + 0.08);
        timer++;
        think();
        physics();
    }

    /** True when {@code bits} were pressed this frame or within the buffer window. */
    private boolean pressed(int bits) {
        return pad.hit(bits) || (bufferAge <= BUFFER && (bufferBits & bits) != 0);
    }

    private void consume() {
        bufferBits = 0;
        bufferAge = 99;
    }

    private boolean jumpPressed() {
        return pressed(Pad.JUMP) || (tapJump() && pad.hit(Pad.UP) && !pad.down(Pad.ATTACK | Pad.SPECIAL));
    }

    private boolean jumpHeld() { return pad.down(Pad.JUMP) || (tapJump() && pad.down(Pad.UP)); }

    /** Up jumps for people who turned it on; the CPU always uses the jump button. */
    private boolean tapJump() { return match.rules.tapJump && !match.isCpu(port); }

    private void setState(State s) {
        state = s;
        timer = 0;
    }

    private void think() {
        switch (state) {
            case IDLE, RUN, CROUCH, SKID -> groundActions();
            case JUMPSQUAT -> {
                if (pressed(Pad.ATTACK) && pad.down(Pad.SHIELD)) { /* no jump grab */ }
                if (timer >= st.jumpSquat()) {
                    boolean full = jumpHeld();
                    leaveGround();
                    vy = -(full ? st.jump() : st.shortHop());
                    vx = Math.max(-st.airSpeed(), Math.min(st.airSpeed(), vx * 0.85 + pad.x() * 0.6)) * speedMul();
                    setState(State.AIR);
                    match.sound(Sfx.JUMP);
                    match.event(Event.DUST, x, y, 0, 0, port);
                    if (pressed(Pad.ATTACK) || pressed(Pad.SPECIAL)) airActions();
                }
            }
            case AIR -> airActions();
            case LAND -> { if (timer >= landingLag) setState(State.IDLE); }
            case ATTACK -> attackFrame();
            case SHIELD -> shieldActions();
            case SHIELD_STUN -> { if (timer >= hitstun) setState(State.SHIELD); }
            case SHIELD_DROP -> { if (timer >= 7) setState(State.IDLE); }
            case ROLL -> {
                if (timer < 18) vx = rollDirection * 2.6;
                else vx *= 0.7;
                if (timer >= 26) { facing = -rollDirection; setState(State.IDLE); }
            }
            case SPOTDODGE -> { vx = 0; if (timer >= 22) setState(State.IDLE); }
            case AIRDODGE -> {
                if (timer < 18 && (airDodgeX != 0 || airDodgeY != 0)) {
                    double decay = 1 - timer / 18.0;
                    vx = airDodgeX * 3.6 * decay;
                    vy = airDodgeY * 3.6 * decay - effectiveGravity();
                }
                if (timer >= 32) setState(State.AIR);
            }
            case HITSTUN -> hitstunFrame();
            case LYING -> {
                vx *= 0.8;
                if (timer >= 50 || (timer > 8 && (pressed(Pad.ATTACK) || pad.x() != 0 || jumpPressed() || pad.hit(Pad.SHIELD)))) {
                    consume();
                    if (pressed(Pad.ATTACK)) startMove(Slot.GETUP_ATTACK);
                    else { setState(State.GETUP); getupDirection = pad.x(); }
                }
            }
            case GETUP, TECH -> {
                vx = getupDirection * (timer < 20 ? 2.4 : 0);
                if (timer >= 26) setState(State.IDLE);
            }
            case HELPLESS -> {
                drift(0.55);
                if (pressed(Pad.DOWN) && vy > 0) fastFalling = true;
            }
            case LEDGE -> ledgeActions();
            case LEDGE_CLIMB -> {
                if (timer >= 22) {
                    x = match.arena.ledgeX(ledgeSide) - ledgeSide * (halfWidth() + 4);
                    y = match.arena.ledgeY(ledgeSide);
                    land(0);
                    setState(State.IDLE);
                }
            }
            case LEDGE_ROLL -> {
                if (timer >= 30) {
                    x = match.arena.ledgeX(ledgeSide) - ledgeSide * (halfWidth() + 44);
                    y = match.arena.surfaceAt(x);
                    land(0);
                    setState(State.IDLE);
                }
            }
            case HOLDING -> holdingActions();
            case THROWING -> throwingFrame();
            case HELD -> {
                if (pad.pressed() != 0) holdTimer -= 4;
                if (heldBy == null || heldBy.state != State.HOLDING && heldBy.state != State.THROWING) {
                    heldBy = null;
                    setState(ground >= 0 ? State.IDLE : State.AIR);
                }
            }
            case DIZZY -> {
                vx *= 0.8;
                if (pad.pressed() != 0) timer += 3;
                if (timer >= dizzyFrames) { shieldHp = SHIELD_MAX * 0.4; setState(State.IDLE); }
            }
            case RESPAWN -> {
                invincible = 120;
                if (timer > 30 && (pad.pressed() != 0 || pad.x() != 0) || timer > 240) {
                    setState(State.AIR);
                    vy = 0.5;
                }
            }
            default -> { }
        }
    }

    private int landingLag = 4;
    private int rollDirection = 1;
    private int getupDirection;
    private double airDodgeX;
    private double airDodgeY;
    private int dizzyFrames = 200;

    private void groundActions() {
        int dx = pad.x();
        if (pressed(Pad.SPECIAL)) { consume(); special(); return; }
        if (pressed(Pad.ATTACK) && pad.down(Pad.SHIELD)) { consume(); startMove(Slot.GRAB); return; }
        if (pressed(Pad.ATTACK)) { consume(); groundAttack(); return; }
        if (jumpPressed()) { consume(); setState(State.JUMPSQUAT); return; }
        if (pad.down(Pad.SHIELD)) { setState(State.SHIELD); match.sound(Sfx.SHIELD); return; }
        if (pad.hit(Pad.DOWN) && ground > 0) {
            ground = -1;
            dropThrough = 12;
            y += 2;
            setState(State.AIR);
            return;
        }
        if (pad.down(Pad.DOWN) && dx == 0) {
            if (state != State.CROUCH) setState(State.CROUCH);
            vx *= 0.75;
            return;
        }
        if (state == State.SKID) {
            vx *= 0.82;
            if (timer >= 8) setState(dx != 0 ? State.RUN : State.IDLE);
            return;
        }
        if (dx != 0) {
            if (dx != facing) {
                facing = dx;
                if (Math.abs(vx) > st.run() * 0.6 && state == State.RUN) {
                    setState(State.SKID);
                    match.sound(Sfx.SKID);
                    match.event(Event.DUST, x, y, 0, 0, port);
                    return;
                }
            }
            if (state != State.RUN) {
                setState(State.RUN);
                runFrames = 0;
                if (dirFreshX < 3) {
                    vx = dx * st.run() * 0.75 * speedMul();
                    match.event(Event.DUST, x - dx * 8, y, 0, 0, port);
                }
            }
            runFrames++;
            double target = dx * st.run() * speedMul();
            vx += Math.signum(target - vx) * Math.min(Math.abs(target - vx), st.groundAccel());
        } else {
            if (state != State.IDLE) setState(State.IDLE);
            vx -= Math.signum(vx) * Math.min(Math.abs(vx), st.friction());
        }
    }

    private void groundAttack() {
        int dx = pad.x();
        int dy = pad.y();
        boolean fresh = (dirFreshX <= 4 && dx != 0) || (dirFreshY <= 4 && dy != 0);
        if (state == State.RUN && runFrames > 4 && fresh == false && dx != 0) { startMove(Slot.DASH); return; }
        if (state == State.RUN && runFrames > 4 && dx == facing && !fresh) { startMove(Slot.DASH); return; }
        if (fresh) {
            if (dy < 0 && dirFreshY <= 4) { startMove(Slot.USMASH); return; }
            if (dy > 0 && dirFreshY <= 4) { startMove(Slot.DSMASH); return; }
            if (dx != 0) { facing = dx; startMove(Slot.FSMASH); return; }
        }
        if (dy < 0) { startMove(Slot.UTILT); return; }
        if (dy > 0) { startMove(Slot.DTILT); return; }
        if (dx != 0) { facing = dx; startMove(Slot.FTILT); return; }
        startMove(Slot.JAB);
    }

    private void special() {
        int dx = pad.x();
        int dy = pad.y();
        if (dy < 0) {
            if (upSpecialUsed && !grounded()) return;
            if (dx != 0) facing = dx;
            startMove(Slot.USPEC);
            if (!grounded()) upSpecialUsed = true;
        } else if (dy > 0) {
            startMove(Slot.DSPEC);
        } else if (dx != 0) {
            facing = dx;
            if (!grounded() && sideSpecialUsed) return;
            startMove(Slot.SSPEC);
            if (!grounded()) sideSpecialUsed = true;
        } else {
            startMove(Slot.NSPEC);
        }
    }

    private void airActions() {
        if (pressed(Pad.SPECIAL)) { consume(); special(); if (state == State.ATTACK) return; }
        if (pressed(Pad.ATTACK)) {
            consume();
            int dx = pad.x();
            int dy = pad.y();
            if (dy < 0) startMove(Slot.UAIR);
            else if (dy > 0) startMove(Slot.DAIR);
            else if (dx == facing) startMove(Slot.FAIR);
            else if (dx == -facing) startMove(Slot.BAIR);
            else startMove(Slot.NAIR);
            return;
        }
        if (jumpPressed() && jumpsLeft > 0) {
            consume();
            jumpsLeft--;
            vy = -st.airJump();
            int dx = pad.x();
            if (dx != 0) facing = dx;
            vx = dx * st.airSpeed() * speedMul();
            fastFalling = false;
            timer = 0;
            airJumpTimer = 18;
            match.sound(Sfx.JUMP);
            return;
        }
        if (pressed(Pad.SHIELD) && !airDodged) {
            consume();
            airDodged = true;
            double dx = pad.x();
            double dy = pad.y();
            double len = Math.hypot(dx, dy);
            airDodgeX = len == 0 ? 0 : dx / len;
            airDodgeY = len == 0 ? 0 : dy / len;
            if (len == 0) { vx *= 0.5; vy *= 0.3; }
            setState(State.AIRDODGE);
            return;
        }
        if (pad.hit(Pad.DOWN) && vy > -1) fastFalling = true;
        drift(1.0);
    }

    public int airJumpTimer;

    private void drift(double control) {
        int dx = pad.x();
        double max = st.airSpeed() * speedMul();
        if (dx != 0) {
            double target = dx * max;
            vx += Math.signum(target - vx) * Math.min(Math.abs(target - vx), st.airAccel() * control);
        } else {
            vx -= Math.signum(vx) * Math.min(Math.abs(vx), st.airFriction());
        }
    }

    private void shieldActions() {
        shieldHp -= 0.13;
        if (shieldHp <= 0) { shieldBreak(); return; }
        vx *= 0.7;
        if (pressed(Pad.ATTACK)) { consume(); startMove(Slot.GRAB); return; }
        if (jumpPressed()) { consume(); setState(State.JUMPSQUAT); return; }
        if (pressed(Pad.SPECIAL) && pad.y() < 0) { consume(); special(); return; }
        if (dirFreshX <= 2 && pad.x() != 0) {
            // Roll toward the pressed direction, ending up facing back the way it came.
            rollDirection = pad.x();
            setState(State.ROLL);
            match.sound(Sfx.ROLL);
            return;
        }
        if (dirFreshY <= 2 && pad.y() > 0) { setState(State.SPOTDODGE); return; }
        if (!pad.down(Pad.SHIELD)) setState(State.SHIELD_DROP);
    }

    private void shieldBreak() {
        match.event(Event.SHIELD_BREAK, x, centerY(), 0, 0, port);
        match.sound(Sfx.BREAK);
        shieldHp = 0;
        dizzyFrames = (int) Math.max(110, 300 - damage);
        leaveGround();
        vy = -7;
        setState(State.DIZZY);
        dizzyAirborne = true;
    }

    private boolean dizzyAirborne;

    private void ledgeActions() {
        vx = vy = 0;
        x = match.arena.ledgeX(ledgeSide) + ledgeSide * (halfWidth() - 2);
        y = match.arena.ledgeY(ledgeSide) + st.height() * 0.7;
        facing = -ledgeSide;
        if (timer < 8) return;
        int toward = -ledgeSide;
        if (timer > 300 || pad.y() > 0 || pad.x() == ledgeSide) { releaseLedge(); vy = 1; setState(State.AIR); return; }
        if (jumpPressed()) {
            consume();
            releaseLedge();
            vy = -st.jump() * 1.05;
            vx = toward * 1.2;
            invincible = Math.max(invincible, 4);
            jumpsLeft = st.airJumps();
            setState(State.AIR);
            match.sound(Sfx.JUMP);
            return;
        }
        if (pressed(Pad.ATTACK) || pressed(Pad.SPECIAL)) { consume(); releaseLedgeToStage(); startMove(Slot.LEDGE_ATTACK); return; }
        if (pressed(Pad.SHIELD)) { consume(); setState(State.LEDGE_ROLL); match.sound(Sfx.ROLL); return; }
        if (pad.x() == toward || pad.y() < 0) { setState(State.LEDGE_CLIMB); return; }
    }

    private void releaseLedge() {
        match.ledgeOwner[ledgeSide < 0 ? 0 : 1] = null;
        ledgeCooldown = 30;
        ledgeInvulnUsed = true;
    }

    private void releaseLedgeToStage() {
        releaseLedge();
        x = match.arena.ledgeX(ledgeSide) - ledgeSide * (halfWidth() + 4);
        y = match.arena.ledgeY(ledgeSide);
        land(0);
    }

    private void holdingActions() {
        vx = 0;
        if (holding == null || holding.state != State.HELD) { holding = null; setState(State.IDLE); return; }
        holding.x = x + facing * 18;
        holding.y = y;
        holding.facing = -facing;
        holding.holdTimer--;
        if (holding.holdTimer <= 0) {
            Fighter h = holding;
            release(h);
            h.leaveGround();
            h.vx = -facing * 2.5;
            h.vy = -3;
            h.setState(State.AIR);
            vx = facing * -1.5;
            setState(State.SHIELD_DROP);
            timer = -10;
            return;
        }
        if (timer < 6) return;
        int dx = pad.x();
        int dy = pad.y();
        FighterDef.Throw t = null;
        if (dy < 0) t = def.upThrow;
        else if (dy > 0) t = def.downThrow;
        else if (dx == facing) t = def.forwardThrow;
        else if (dx == -facing) t = def.backThrow;
        if (t == null && pressed(Pad.ATTACK)) {
            consume();
            Move pummel = def.move(Slot.PUMMEL);
            if (pummel != null) {
                holding.applyDamage(pummel.hits.get(0).damage(), this);
                match.event(Event.HIT, holding.x, holding.centerY(), 10, Hit.FX_NORMAL, holding.port);
                match.sound(Sfx.THUMP);
                hitlag = holding.hitlag = 3;
                timer = 0;
            }
            return;
        }
        if (t != null) {
            activeThrow = t;
            if (dx == -facing) { facing = -facing; }
            setState(State.THROWING);
        }
    }

    private void throwingFrame() {
        if (holding != null && holding.state == State.HELD) {
            holding.x = x + facing * activeThrow.holdX() * (activeThrow == def.backThrow ? -1 : 1) * Math.min(1, timer / 6.0);
            holding.y = y + activeThrow.holdY() * Math.min(1, timer / 6.0);
            if (timer >= activeThrow.frame()) {
                Fighter h = holding;
                release(h);
                int dir = activeThrow == def.backThrow ? -facing : facing;
                Hit hit = Hit.of(0, 0, 0, 0, 0, activeThrow.damage(), activeThrow.angle(), activeThrow.baseKb(),
                        activeThrow.growth());
                match.applyHit(this, h, hit, dir, "throw:" + (activeThrow == def.forwardThrow ? "f" : activeThrow == def.backThrow ? "b" : activeThrow == def.upThrow ? "u" : "d"), false);
            }
        }
        if (timer >= activeThrow.duration()) { activeThrow = null; setState(grounded() ? State.IDLE : State.AIR); }
    }

    void release(Fighter held) {
        if (held == null) return;
        held.heldBy = null;
        if (holding == held) holding = null;
        if (held.state == State.HELD) held.setState(held.grounded() ? State.IDLE : State.AIR);
    }

    void grab(Fighter target) {
        holding = target;
        target.heldBy = this;
        target.move = null;
        target.setState(State.HELD);
        target.holdTimer = (int) (60 + target.damage * 0.9);
        target.vx = target.vy = target.lx = target.ly = 0;
        move = null;
        setState(State.HOLDING);
        match.sound(Sfx.GRAB);
    }

    // ------------------------------------------------------------------ moves

    void startMove(Slot slot) {
        Move m = def.move(slot);
        if (m == null) return;
        move = m;
        moveSlot = slot;
        moveFrame = 0;
        charge = 0;
        holdFrames = 0;
        chargeAmount = 0;
        rolling = false;
        flightTimer = 0;
        glideTimer = 0;
        struck.clear();
        setState(State.ATTACK);
        if (m.custom == Move.Custom.HOMING) homingUsed = true;
        playFrameSounds();
    }

    private void playFrameSounds() {
        for (Move.Sound s : move.sounds) if (s.frame() == moveFrame) match.sound(s.sfx());
    }

    private void attackFrame() {
        Move m = move;
        if (m == null) { setState(grounded() ? State.IDLE : State.AIR); return; }
        // Smash attacks charge while attack is held at their charge frame.
        if (m.chargeFrame >= 0 && moveFrame == m.chargeFrame && pad.down(Pad.ATTACK) && charge < 60) {
            charge++;
            chargeAmount = 0.4 * charge / 60.0;
            if (charge % 10 == 1) match.event(Event.SPARKLE, x, centerY(), 0, 0, port);
            vx *= 0.8;
            return;
        }
        customFrame(m);
        if (move == null || state != State.ATTACK) return;
        if (!grounded()) {
            double control = m.airControl;
            if (m.custom == Move.Custom.NONE || m.custom == Move.Custom.SPRING) drift(control);
            if (m.fastFallFrom >= 0 && moveFrame >= m.fastFallFrom && pad.hit(Pad.DOWN) && vy > -1) fastFalling = true;
            if (moveSlot.ordinal() >= Slot.NAIR.ordinal() && moveSlot.ordinal() <= Slot.DAIR.ordinal()
                    && pad.hit(Pad.DOWN) && vy > -1) fastFalling = true;
        } else if (m.groundFriction && !rolling) {
            vx -= Math.signum(vx) * Math.min(Math.abs(vx), st.friction() * 1.2);
        }
        for (Move.Impulse imp : m.impulses) {
            if (imp.frame() != moveFrame) continue;
            double scale = imp.scaleByCharge() ? 1 + holdFrames / (double) Math.max(1, m.maxHold) : 1;
            if (!Double.isNaN(imp.vx())) vx = (imp.add() ? vx : 0) + imp.vx() * facing * scale;
            if (!Double.isNaN(imp.vy())) {
                vy = (imp.add() ? vy : 0) + imp.vy() * scale;
                if (imp.vy() < 0 && grounded()) leaveGround();
                fastFalling = false;
            }
        }
        for (Move.Spawn s : m.spawns) {
            if (s.frame() == moveFrame) match.fire(this, s.def(), x + s.x() * facing, centerY() + s.y(), holdFrames / (double) Math.max(1, m.maxHold));
        }
        // Hold loops (spin dash revs, charged specials).
        if (m.loopFrom >= 0 && moveFrame == m.loopTo - 1 && pad.down(m.holdButton) && holdFrames < m.maxHold) {
            holdFrames += m.loopTo - m.loopFrom;
            moveFrame = m.loopFrom;
            playFrameSounds();
            return;
        }
        if (m.landLoopFrom >= 0 && moveFrame == m.landLoopTo - 1 && !grounded()) {
            moveFrame = m.landLoopFrom;
            return;
        }
        moveFrame++;
        playFrameSounds();
        if (moveFrame >= m.duration) endMove();
    }

    private void endMove() {
        Move m = move;
        move = null;
        rolling = false;
        if (grounded()) {
            setState(pad.down(Pad.DOWN) ? State.CROUCH : State.IDLE);
        } else if (m != null && m.helpless) {
            setState(State.HELPLESS);
        } else {
            setState(State.AIR);
        }
    }

    /** Called by the match when one of this fighter's hits connects. */
    void onConnect(Fighter target, boolean shielded) {
        if (move == null) return;
        switch (move.custom) {
            case HOMING -> {
                vx = -facing * 1.2;
                vy = -5.2;
                fastFalling = false;
                jumpsLeft = Math.max(jumpsLeft, 1);
                homingUsed = false;
                endMove();
            }
            case ROLLING, SPIN_DASH -> {
                if (rolling) {
                    vx = -vx * 0.35;
                    vy = grounded() ? vy : -3;
                    rolling = false;
                    endMove();
                    if (grounded()) { setState(State.LAND); landingLag = 10; }
                }
            }
            case GLIDE -> {
                vx *= 0.3;
                endMove();
                if (!grounded()) setState(State.AIR);
            }
            default -> { }
        }
    }

    private void customFrame(Move m) {
        switch (m.custom) {
            case HOMING -> {
                if (moveFrame < 10) {
                    vx *= 0.85;
                    if (vy > 0) vy *= 0.5;
                } else if (moveFrame == 10) {
                    Fighter target = match.nearestOpponent(this, 170);
                    double dx = target == null ? facing : target.x - x;
                    double dy = target == null ? 0 : target.centerY() - centerY();
                    double len = Math.max(1, Math.hypot(dx, dy));
                    if (target != null) facing = dx < 0 ? -1 : 1;
                    vx = dx / len * 7.2;
                    vy = dy / len * 7.2;
                    if (grounded() && vy < 0) leaveGround();
                    if (grounded() && vy >= 0) vy = 0;
                    match.sound(Sfx.DASH);
                } else if (moveFrame < 30) {
                    // Keep the dash's speed, ignoring gravity, until it ends or connects.
                    vy -= effectiveGravity();
                } else if (moveFrame == 30) {
                    vx *= 0.4;
                    vy = Math.min(vy, 1);
                }
            }
            case SPIN_DASH -> {
                if (!rolling && grounded()) vx *= 0.8;
                if (!rolling && moveFrame == m.loopTo && holdFrames >= 0) {
                    rolling = true;
                    rollTimer = 0;
                    double speed = (4.6 + 3.4 * Math.min(1, holdFrames / (double) Math.max(1, m.maxHold))) * speedMul();
                    vx = facing * speed;
                    match.sound(Sfx.DASH);
                    match.event(Event.DUST, x - facing * 10, y, 1, 0, port);
                }
                if (rolling) rollFrame(m);
                if (pad.hit(Pad.SPECIAL) && !rolling) match.sound(Sfx.SPINDASH);
            }
            case ROLLING -> {
                if (!rolling && grounded()) vx *= 0.8;
                if (moveFrame == m.loopTo && !rolling) {
                    rolling = true;
                    rollTimer = 0;
                    vx = facing * (5.2 + 2.6 * Math.min(1, holdFrames / (double) Math.max(1, m.maxHold))) * speedMul();
                    match.sound(Sfx.DASH);
                }
                if (rolling) rollFrame(m);
            }
            case SPRING -> {
                if (moveFrame == 2) {
                    match.fire(this, springDef(), x, y - 4, 0);
                    leaveGround();
                    vy = -10.4;
                    vx = pad.x() * 1.4;
                    match.sound(Sfx.SPRING);
                }
            }
            case FLIGHT -> flightFrame(m);
            case GLIDE -> glideFrame(m);
            case HOVER_ROLL -> {
                if (moveFrame >= 6 && moveFrame < m.duration - 4) {
                    int dx = pad.x();
                    vx += dx * 0.25;
                    vx = Math.max(-3, Math.min(3, vx));
                }
            }
            default -> { }
        }
    }

    private Projectile.Def springDef() {
        return new Projectile.Def(Projectile.K_SPRING, 0, 0, 0.35, 70, 10,
                Hit.of(0, 0, 0, 0, 10, 4, 270, 30, 40).fx(Hit.FX_NORMAL), null, 0, true, true, 1, false);
    }

    private int airRoll;

    private void rollFrame(Move m) {
        rollTimer++;
        // Hold the rolling frames until the roll runs out of speed or time.
        if (moveFrame >= m.loopTo + 2) moveFrame = m.loopTo + 1;
        double friction = grounded() ? 0.045 : 0.01;
        vx -= Math.signum(vx) * friction;
        if (grounded() && pad.x() == -Integer.signum((int) Math.signum(vx))) vx -= Math.signum(vx) * 0.12;
        if (rollTimer % 6 == 0) match.event(Event.DUST, x - Math.signum(vx) * 8, y, 0, 0, port);
        if (jumpPressed() && rollTimer > 4) {
            consume();
            rolling = false;
            move = null;
            if (grounded()) { leaveGround(); vy = -st.jump() * 0.9; }
            else if (jumpsLeft > 0) { jumpsLeft--; vy = -st.airJump(); }
            setState(State.AIR);
            match.sound(Sfx.JUMP);
            return;
        }
        if (!grounded()) airRoll++; else airRoll = 0;
        if (airRoll > 14) {
            // A roll that leaves the ground becomes a normal jump arc the fighter can act out of.
            rolling = false;
            move = null;
            setState(State.AIR);
            return;
        }
        if (Math.abs(vx) < 1.4 || rollTimer > 70) {
            rolling = false;
            moveFrame = m.duration - 1;
        }
    }

    private void flightFrame(Move m) {
        flightTimer++;
        if (moveFrame >= 8) {
            if (moveFrame >= m.duration - 2) moveFrame = 8;
            if (pressed(Pad.JUMP) || pressed(Pad.SPECIAL) || (tapJump() && pad.hit(Pad.UP))) {
                consume();
                vy = Math.max(-3.6, vy - 2.6);
                match.sound(Sfx.FLYING);
            }
            vy = Math.min(1.6, vy + 0.11);
            int dx = pad.x();
            if (dx != 0) facing = dx;
            vx += (dx * 2.1 - vx) * 0.12;
            if (pressed(Pad.ATTACK)) {
                consume();
                move = null;
                setState(State.AIR);
                upSpecialUsed = true;
                airActions();
                return;
            }
            if (flightTimer > 96) {
                match.sound(Sfx.FLY_TIRED);
                move = null;
                setState(State.HELPLESS);
            }
        }
    }


    private void glideFrame(Move m) {
        if (moveFrame < 6) {
            vx *= 0.9;
            vy = Math.min(vy, 0.5);
            return;
        }
        glideTimer++;
        if (moveFrame >= m.duration - 2) moveFrame = 8;
        int dx = pad.x();
        if (dx == -facing) {
            vx -= facing * 0.22;
            if (Math.signum(vx) != facing) facing = -facing;
        } else {
            vx += facing * 0.05;
        }
        double max = 3.6 * speedMul();
        vx = Math.max(-max, Math.min(max, vx));
        vy = pad.down(Pad.DOWN) ? 2.2 : 0.75;
        if (grounded()) {
            match.sound(Sfx.GLIDE_LAND);
            match.event(Event.DUST, x, y, 1, 0, port);
            move = null;
            setState(State.LAND);
            landingLag = 14;
            return;
        }
        if (glideTimer > 120 || pressed(Pad.ATTACK) || pressed(Pad.SPECIAL)) {
            consume();
            move = null;
            setState(State.HELPLESS);
        }
    }

    // ------------------------------------------------------------------ physics

    double effectiveGravity() {
        double g = st.gravity();
        if (state == State.ATTACK && move != null && moveFrame >= move.gravityFrom && moveFrame <= move.gravityTo) g *= move.gravityMul;
        return g;
    }

    private void physics() {
        if (state == State.LEDGE || state == State.LEDGE_CLIMB || state == State.LEDGE_ROLL || state == State.HELD
                || state == State.RESPAWN) {
            if (state == State.RESPAWN) { vx = vy = 0; }
            return;
        }
        Arena a = match.arena;
        double px = x;
        double py = y;
        // Launch velocity decays along its direction.
        double ls = Math.hypot(lx, ly);
        if (ls > 0) {
            double ns = Math.max(0, ls - Knockback.DECAY);
            lx *= ns / ls;
            ly *= ns / ls;
        }
        if (grounded()) {
            x += vx + lx;
            double sx = x;
            if (ground == 0) {
                if (!a.over(sx) && state == State.ATTACK && move != null && move.groundFriction && !rolling) {
                    // Most grounded attacks stop at the edge rather than carrying off it.
                    x = Math.max(a.left + 0.5, Math.min(a.right() - 0.5, x));
                    vx = 0;
                    y = a.surfaceAt(x);
                } else if (!a.over(sx)) {
                    leaveGround();
                    if (state == State.ATTACK && move != null && move.custom == Move.Custom.NONE && !rolling) {
                        // Running off the edge mid-move keeps the move going in the air.
                    } else if (state != State.ATTACK && state != State.HITSTUN && state != State.DIZZY) {
                        setState(State.AIR);
                    }
                    if (state == State.ROLL || state == State.SPOTDODGE || state == State.SHIELD) setState(State.AIR);
                } else {
                    y = a.surfaceAt(x);
                }
            } else {
                Arena.Platform p = a.platforms.get(ground - 1);
                long f = match.frame;
                x += p.dx(f) - p.dx(f - 1);
                if (x < p.left(f) || x > p.right(f)) {
                    leaveGround();
                    if (state != State.ATTACK && state != State.HITSTUN) setState(State.AIR);
                } else {
                    y = p.top(f);
                }
            }
            if (ly < -0.5) leaveGround();
        }
        if (!grounded()) {
            double g = effectiveGravity();
            double cap = fastFalling ? st.fastFall() : st.fall();
            if (state == State.HITSTUN) cap = Math.max(cap, st.fall());
            if (fastFalling && vy < cap) vy = cap;
            else vy = Math.min(cap, vy + g);
            if (state == State.ATTACK && move != null && move.custom == Move.Custom.HOMING && moveFrame >= 10 && moveFrame < 30) {
                // Homing dash keeps its own vertical speed.
            }
            x += vx + lx;
            y += vy + ly;
            collideAir(a, px, py);
        }
        if (grounded() && state == State.HITSTUN) {
            vx *= 0.9;
        }
    }

    private void collideAir(Arena a, double px, double py) {
        double hw = halfWidth();
        // Island: land on top, bump the underside, stop at the sides.
        if (x + hw > a.left && x - hw < a.right()) {
            double cx = Math.max(a.left, Math.min(a.right() - 0.01, x));
            double top = a.surfaceAt(cx);
            double bottom = a.bottomAt(cx);
            boolean overlaps = y > top && y - st.height() < bottom;
            if (overlaps) {
                boolean wasAbove = py <= a.surfaceAt(Math.max(a.left, Math.min(a.right() - 0.01, px))) + 2;
                boolean wasBelow = py - st.height() >= a.bottomAt(Math.max(a.left, Math.min(a.right() - 0.01, px))) - 2;
                boolean wasOutside = px + hw <= a.left || px - hw >= a.right();
                if (wasAbove && a.over(x) && vy + ly >= -0.01) {
                    y = top;
                    land(0);
                } else if (wasBelow && !wasOutside) {
                    y = bottom + st.height();
                    if (state == State.HITSTUN && ly < -2) { ly = -ly * 0.6; match.event(Event.SHAKE, x, y, 2, 0, port); }
                    else ly = Math.max(0, ly);
                    vy = Math.max(0, vy);
                } else if (wasOutside || !a.over(x)) {
                    x = x < (a.left + a.right()) / 2 ? a.left - hw : a.right() + hw;
                    if (state == State.HITSTUN && Math.abs(lx) > 2) lx = -lx * 0.5;
                    else lx = 0;
                    vx = 0;
                } else {
                    // Inside the island from above at speed: put it back on top.
                    y = top;
                    land(0);
                }
            }
        }
        if (grounded()) return;
        // Platforms: only from above while falling, unless dropping through.
        long f = match.frame;
        for (int i = 0; dropThrough == 0 && i < a.platforms.size(); i++) {
            Arena.Platform p = a.platforms.get(i);
            double top = p.top(f);
            double prevTop = p.top(f - 1);
            if (vy + ly >= 0 && py <= prevTop + 1 && y >= top && x >= p.left(f) && x <= p.right(f)) {
                if (state == State.HITSTUN && pad.down(Pad.DOWN)) continue;
                y = top;
                land(i + 1);
                return;
            }
        }
        // Ledge catch.
        tryLedge(a);
    }

    private void tryLedge(Arena a) {
        if (ledgeCooldown > 0 || vy + ly < -0.5 && !(state == State.ATTACK && move != null && move.ledgeGrab)) return;
        boolean can = state == State.AIR || state == State.HELPLESS || state == State.AIRDODGE
                || (state == State.ATTACK && move != null && move.ledgeGrab);
        if (!can || pad.down(Pad.DOWN)) return;
        for (int side = -1; side <= 1; side += 2) {
            double lxPos = a.ledgeX(side);
            double lyPos = a.ledgeY(side);
            double gx = lxPos + side * halfWidth();
            boolean outside = side < 0 ? x <= lxPos + 4 : x >= lxPos - 4;
            if (!outside) continue;
            if (Math.abs(x - gx) < 18 && lyPos >= y - st.height() - 10 && lyPos <= y + 10) {
                int idx = side < 0 ? 0 : 1;
                Fighter owner = match.ledgeOwner[idx];
                if (owner != null && owner != this) {
                    if (owner.state == State.LEDGE) {
                        // Ledge trump: the newcomer takes it and the hanger is popped off.
                        owner.releaseLedge();
                        owner.setState(State.AIR);
                        owner.vx = side * 1.5;
                        owner.vy = -3;
                    }
                }
                match.ledgeOwner[idx] = this;
                ledgeSide = side;
                move = null;
                rolling = false;
                vx = vy = lx = ly = 0;
                fastFalling = false;
                jumpsLeft = st.airJumps();
                upSpecialUsed = sideSpecialUsed = homingUsed = false;
                airDodged = false;
                setState(State.LEDGE);
                match.sound(Sfx.GRAB);
                return;
            }
        }
    }

    void leaveGround() {
        ground = -1;
    }

    private void land(int surface) {
        boolean wasAir = ground < 0;
        ground = surface;
        double impact = vy + ly;
        vy = 0;
        fastFalling = false;
        if (!wasAir) return;
        jumpsLeft = st.airJumps();
        airDodged = false;
        upSpecialUsed = sideSpecialUsed = homingUsed = false;
        ledgeInvulnUsed = false;
        switch (state) {
            case HITSTUN -> {
                if (tumble) {
                    if (techWindow > 0) {
                        techWindow = 0;
                        lx = ly = 0;
                        vx = 0;
                        getupDirection = pad.x();
                        setState(State.TECH);
                        match.event(Event.TECH, x, centerY(), 0, 0, port);
                        match.sound(Sfx.SHIELD);
                    } else if (impact > 3.2) {
                        // Bounce off the floor.
                        ground = -1;
                        ly = -Math.abs(ly) * 0.45;
                        vy = -Math.min(3.5, impact * 0.4);
                        match.event(Event.DUST, x, y, 1, 0, port);
                        match.sound(Sfx.FLOOR_THUMP);
                    } else {
                        lx *= 0.3;
                        ly = 0;
                        setState(State.LYING);
                        match.event(Event.DUST, x, y, 1, 0, port);
                    }
                } else {
                    ly = 0;
                }
            }
            case ATTACK -> {
                if (move != null && move.landLoopFrom >= 0 && moveFrame >= move.landLoopFrom && moveFrame < move.landFrame) {
                    moveFrame = move.landFrame;
                    match.event(Event.DUST, x, y, 1, 0, port);
                } else if (move != null && (move.custom == Move.Custom.GLIDE)) {
                    // handled in glideFrame
                } else if (move != null && (rolling || move.custom == Move.Custom.SPIN_DASH || move.custom == Move.Custom.ROLLING)) {
                    // Rolls keep going on the ground.
                } else if (move != null && moveSlot.ordinal() >= Slot.NAIR.ordinal() && moveSlot.ordinal() <= Slot.DAIR.ordinal()) {
                    landingLag = moveFrame < move.duration - 3 ? move.landingLag : 4;
                    move = null;
                    setState(State.LAND);
                    match.event(Event.DUST, x, y, 0, 0, port);
                } else if (move != null && move.helpless) {
                    landingLag = move.landingLag;
                    move = null;
                    setState(State.LAND);
                } else if (move != null && !move.helpless && moveSlot.ordinal() >= Slot.NSPEC.ordinal()) {
                    // Specials that end on landing.
                    if (move.landLoopFrom < 0) {
                        landingLag = Math.min(12, move.landingLag);
                        move = null;
                        setState(State.LAND);
                    }
                }
            }
            case HELPLESS -> {
                landingLag = 16;
                setState(State.LAND);
                match.event(Event.DUST, x, y, 1, 0, port);
            }
            case AIRDODGE -> {
                landingLag = timer < 30 ? 10 : 2;
                setState(State.LAND);
            }
            case DIZZY -> {
                vx = 0;
                dizzyAirborne = false;
            }
            case AIR -> {
                landingLag = 4;
                setState(State.LAND);
                if (impact > 3) match.event(Event.DUST, x, y, 0, 0, port);
            }
            default -> { }
        }
    }

    private void hitstunFrame() {
        if (timer >= hitstun) {
            // Hitstun over: tumbling fighters may act again.
            if (grounded()) { setState(State.IDLE); tumble = false; }
            else if (pad.pressed() != 0 || pad.x() != 0) {
                tumble = false;
                setState(State.AIR);
                airActions();
            } else {
                drift(0.4);
            }
        } else if (!grounded()) {
            // A little drift control late in hitstun (hit stun "DI" drift).
            if (timer > hitstun * 0.6) drift(0.2);
        }
    }

    // ------------------------------------------------------------------ being hit

    void applyDamage(double amount, Fighter by) {
        damage = Math.min(999, damage + amount);
        peakDamage = Math.max(peakDamage, damage);
        damageTaken += amount;
        if (by != null && by != this) {
            by.damageDealt += amount;
            lastHitBy = by;
            lastHitAge = 0;
        }
        if (match.rules.mode == Rules.Mode.STAMINA) stamina -= amount;
    }

    /** Puts this fighter into hitlag, then launches it when the hitlag ends. */
    void hurt(double kb, double angleDeg, int direction, int lag, boolean groundedHit) {
        if (heldBy != null) heldBy.release(this);
        if (holding != null) release(holding);
        if (state == State.LEDGE) releaseLedge();
        move = null;
        rolling = false;
        pendingKb = kb;
        pendingAngle = angleDeg;
        pendingLaunchDirection = direction;
        pendingGrounded = groundedHit;
        hitlag = lag;
        frozenUntilLaunch = 1;
        combo = state == State.HITSTUN ? combo + 1 : 1;
        comboTimer = 60;
        setState(State.HITSTUN);
        hitstun = Knockback.hitstun(kb);
        tumble = kb >= Knockback.TUMBLE;
        if (lag == 0) launchNow();
    }

    private void launchNow() {
        frozenUntilLaunch = 0;
        double kb = pendingKb;
        double angle = Math.toRadians(pendingAngle);
        double speed = kb * Knockback.SPEED_PER_KB;
        double dx = Math.cos(angle) * pendingLaunchDirection;
        double dy = -Math.sin(angle);
        // Directional influence: holding across the launch line bends it by up to 15 degrees.
        double ix = pad.x();
        double iy = pad.y();
        if ((ix != 0 || iy != 0) && kb > 30) {
            double il = Math.hypot(ix, iy);
            double cross = (dx * iy / il - dy * ix / il);
            double rot = Math.toRadians(15) * cross;
            double ndx = dx * Math.cos(rot) - dy * Math.sin(rot);
            double ndy = dx * Math.sin(rot) + dy * Math.cos(rot);
            dx = ndx;
            dy = ndy;
        }
        if (pendingGrounded && dy > 0 && grounded()) dy = -dy * 0.8; // spikes on the ground pop up
        lx = dx * speed;
        ly = dy * speed;
        vx = 0;
        vy = 0;
        if (ly < -0.3 || !grounded()) leaveGround();
        if (grounded() && kb < Knockback.TUMBLE) {
            lx *= 0.8;
            ly = 0;
        }
        fastFalling = false;
        timer = 0;
    }

    // ------------------------------------------------------------------ presentation

    /** The pose the view should draw this frame. */
    public int pose(long frame) {
        FighterDef.Poses p = def.poses;
        int period = Math.max(1, p.period());
        switch (state) {
            case IDLE: return cycle(p.idle(), period * 2);
            case RUN:
                if (Math.abs(vx) > st.run() * 0.75) return cycle(p.run(), Math.max(2, (int) (period * 0.7)));
                return cycle(p.walk(), period);
            case SKID: return cycle(p.skid(), period);
            case CROUCH: return cycle(p.crouch(), period);
            case JUMPSQUAT: case LAND: case SHIELD_DROP: return p.land()[0];
            case AIR:
                if (airJumpTimer > 0) return cycle(p.airJump(), 3);
                return vy < 0 ? cycle(p.jump(), period) : cycle(p.fall(), period);
            case ATTACK: return move != null ? move.pose[Math.min(moveFrame, move.duration - 1)] : p.idle()[0];
            case SHIELD: case SHIELD_STUN: return p.shield()[0];
            case ROLL: case SPOTDODGE: case AIRDODGE: case TECH: case GETUP: case LEDGE_ROLL: return cycle(p.dodge(), 3);
            case HITSTUN:
                if (tumble) return cycle(p.tumble(), 4);
                return cycle(p.hurt(), period);
            case LYING: return cycle(p.lying(), period);
            case HELPLESS: return cycle(p.helpless(), period);
            case LEDGE: case LEDGE_CLIMB: return cycle(p.ledge(), period * 2);
            case HOLDING: case THROWING: return cycle(p.hold(), period);
            case HELD: return cycle(p.hurt(), period);
            case DIZZY: return cycle(p.dizzy(), period * 2);
            case RESPAWN: return cycle(p.idle(), period * 2);
            default: return p.idle()[0];
        }
    }

    private int cycle(int[] poses, int each) {
        if (poses.length == 1) return poses[0];
        return poses[(int) ((timer / Math.max(1, each)) % poses.length)];
    }

    /** Frames left of the current hitlag (the view shakes the sprite). */
    public boolean shaking() { return hitlag > 0 && frozenUntilLaunch > 0; }

    /** Called once per frame by the match after hitlag bookkeeping, for visual timers. */
    void tickVisual() {
        if (airJumpTimer > 0) airJumpTimer--;
    }
}
