package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One R11A ordinary normal-sized MODE call (Main.asm:675-776).
 * Source pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. FixBugs=0 semantics;
 * no repaired sign asymmetry, byte/word overflow, skid-register or high-memory-byte Y read.
 *
 * Caller owns ObjSonic predispatch charge increment, routine admission, raw/logical
 * input latch pair, Main before/after MODE, animation execution and all services.
 * This owner selects oAnim only. MODE_RETURN/JUMP_MODE_RETURN are exact returns to
 * Main, never post-frame claims. KILL_PLAYER stops BEFORE that external owner and
 * names the exact instruction continuation after its eventual return; its tail
 * has not executed. Unsupported owners/probe failures throw without publication.
 * No services, source/JSON/trace reader, default table or secondary loop exists.
 */
public final class CdPlayerMovement {
    private static final int LEFT = 4, RIGHT = 8, DOWN = 2, ACTION = 0x70;
    private static final int AIR = 2, ROLL = 4;

    /** Already latched logical bytes. No reconstruction or once-per-dispatch charge increment. */
    public record Input(int logicalHeld, int logicalTap) {
        public Input { byteValue(logicalHeld); byteValue(logicalTap); }
    }
    public record Physics(int topSpeed, int acceleration, int deceleration) {
        public Physics { signedWord(topSpeed); signedWord(acceleration); signedWord(deceleration); }
    }
    /** Actual native bounds; right is camera boundary, not a guessed stage edge. */
    public record Bounds(int left, int right, int bottom, int bossFight) {
        public Bounds { wordValue(left); wordValue(right); wordValue(bottom); byteValue(bossFight); }
    }
    /** Native globals, not caller-manufactured safe-work booleans. R11A is zoneAct=0,timeZone=1. */
    public record Gates(int zoneAct, int timeZone, int miniSonic, int waterSlide,
                        int windTunnel, int speedShoes, int timeWarpDirection) {
        public Gates {
            wordValue(zoneAct); byteValue(timeZone); byteValue(miniSonic); byteValue(waterSlide);
            byteValue(windTunnel); byteValue(speedShoes); byteValue(timeWarpDirection);
        }
    }
    /** MODE-owned global values; boredom's V-int increment is external. Debug fields are actual bytes. */
    public record Globals(int lookMode, int cameraYCentre, int primaryAngle, int secondaryAngle,
                          int boredTimer, int scoreChain, int debugAngle, int debugAngleShift,
                          int debugQuadrant, int debugFloorDistance) {
        public Globals {
            byteValue(lookMode); wordValue(cameraYCentre); byteValue(primaryAngle); byteValue(secondaryAngle);
            wordValue(boredTimer); wordValue(scoreChain); byteValue(debugAngle); byteValue(debugAngleShift);
            byteValue(debugQuadrant); byteValue(debugFloorDistance);
        }
    }
    public record Environment(Physics physics, Bounds bounds, Gates gates, Globals globals) {
        public Environment {
            Objects.requireNonNull(physics); Objects.requireNonNull(bounds);
            Objects.requireNonNull(gates); Objects.requireNonNull(globals);
        }
    }
    public enum Boundary { MODE_RETURN, JUMP_MODE_RETURN, KILL_PLAYER }
    public enum Continuation {
        NONE, MD_GROUND_AFTER_LEVEL_BOUND, MD_ROLL_AFTER_LEVEL_BOUND,
        MD_AIR_AFTER_LEVEL_BOUND, MD_JUMP_AFTER_LEVEL_BOUND
    }
    public enum Sound { FM_JUMP, FM_SKID, FM_CHARGESTOP }
    public sealed interface Effect permits SoundRequest, ChargeEffect, KillRequest { }
    public record SoundRequest(Sound sound) implements Effect {
        public SoundRequest { Objects.requireNonNull(sound); }
    }
    /** Ordered exact helper effects retained verbatim; final values are applied ONCE from Result. */
    public record ChargeEffect(CdPlayerCharge.Effect effect) implements Effect {
        public ChargeEffect { Objects.requireNonNull(effect); }
    }
    public record KillRequest() implements Effect { }
    public record Result(CdPlayerMotionState state, Environment environment, List<Effect> effects,
                         Boundary boundary, Continuation continuation) {
        public Result {
            Objects.requireNonNull(state); Objects.requireNonNull(environment);
            effects = List.copyOf(effects); Objects.requireNonNull(boundary); Objects.requireNonNull(continuation);
            if ((boundary == Boundary.KILL_PLAYER) != (continuation != Continuation.NONE)) {
                throw new IllegalArgumentException("Only KillPlayer has an unfinished MODE continuation");
            }
        }
    }
    public enum UnsupportedBoundary {
        ROUTINE, SOURCE_FAMILY, MINIATURE, OBJECT_CONTROL, OBJECT_SUPPORT, CONTROL_OWNER,
        BORED_GIVE_UP, RAMP_ENTRY, TIME_WARP_ACTIVE
    }
    /** A bounded missing owner, not a partially completed MODE result. */
    public static final class UnsupportedMotion extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private UnsupportedMotion(UnsupportedBoundary boundary) { super(boundary.name()); this.boundary = boundary; }
        public UnsupportedBoundary boundary() { return boundary; }
    }
    /**
     * Player_ResetOnFloor final values (Main.asm:2295-2330). scoreChain is the routine's MOVE.W #0
     * write; sounds holds its single FM_CHARGESTOP request only when bit 2 was set.
     */
    public record ResetOnFloorResult(CdPlayerMotionState motion, int scoreChain, List<SoundRequest> sounds) {
        public ResetOnFloorResult { Objects.requireNonNull(motion); wordValue(scoreChain); sounds = List.copyOf(sounds); }
    }

    private final CdR11aTerrain terrainData;
    private final CdR11aPlayerAssets assets;
    private final CdPlayerTerrain terrain;
    private final CdPlayerMath math;
    private final CdPlayerGroundCollision ground;

    public CdPlayerMovement(CdR11aTerrain terrain, CdR11aPlayerAssets assets) {
        terrainData = Objects.requireNonNull(terrain);
        this.assets = Objects.requireNonNull(assets);
        this.terrain = new CdPlayerTerrain(terrain);
        math = new CdPlayerMath(assets);
        ground = new CdPlayerGroundCollision(this.terrain);
    }

    /** ObjSonic.NormalCtrl/ModeIndex (Main.asm:314-319,355-359), one admitted routine-2 MODE call. */
    public Result step(CdPlayerMotionState state, Input input, Environment environment) {
        Objects.requireNonNull(state); Objects.requireNonNull(input); Objects.requireNonNull(environment);
        if (state.routine() != 2) unsupported(UnsupportedBoundary.ROUTINE);
        if (state.animation() == 0x2B) unsupported(UnsupportedBoundary.BORED_GIVE_UP);
        if (environment.gates().zoneAct() != 0 || environment.gates().timeZone() != 1) {
            unsupported(UnsupportedBoundary.SOURCE_FAMILY);
        }
        if (environment.gates().miniSonic() != 0) unsupported(UnsupportedBoundary.MINIATURE);
        if ((state.playerControl() & 1) != 0) unsupported(UnsupportedBoundary.OBJECT_CONTROL);
        if ((state.flags() & 8) != 0) unsupported(UnsupportedBoundary.OBJECT_SUPPORT);
        // Bar/pole owners change ObjMoveGrv and object control; do not simulate their active states.
        if ((state.playerControl() & 0xC) != 0) unsupported(UnsupportedBoundary.CONTROL_OWNER);
        Cursor c = new Cursor(state, input, environment);
        int mode = c.flags & 6;
        if (mode == 0) {
            boredom(c);
        } else if (mode == AIR && c.env.gates().windTunnel() == 0 && c.anim != 0x15 && c.yv >= 0) {
            c.anim = 0; // MdAir only; MdJump omits this reset (713-720).
        }
        ramp(c); // 695/723/743/763; lookup uses actual $380 layout and asset-owned raw IDs.
        if (c.charge == 0 && c.env.gates().timeWarpDirection() != 0) {
            unsupported(UnsupportedBoundary.TIME_WARP_ACTIVE); // TimeWarp.NotPPZ1:585-589.
        }
        if ((mode & AIR) == 0) {
            if (checkJump(c)) return c.finish(Boundary.JUMP_MODE_RETURN, Continuation.NONE);
            slope(c, mode == ROLL);
            if (mode == ROLL) moveRoll(c); else moveGround(c);
            if (mode == 0) checkRoll(c);
            if (levelBound(c)) return kill(c, mode);
            if (mode != ROLL || c.charge == 0) integrate(c, false);
            ground(c);
            falloff(c);
        } else {
            jumpHeight(c);
            moveAir(c);
            if (levelBound(c)) return kill(c, mode);
            integrate(c, true);
            if ((c.flags & 0x40) != 0) c.yv = sw(c.yv - 0x28);
            jumpAngle(c);
            airCollision(c);
        }
        return c.finish(Boundary.MODE_RETURN, Continuation.NONE);
    }

    /** ChkBoredom (11-47): ordinary timer writes only; threshold/giving-up has its own actor/service owner. */
    private static void boredom(Cursor c) {
        if (c.anim == 0x2B) unsupported(UnsupportedBoundary.BORED_GIVE_UP);
        if (c.anim != 5) { c.bored = 0; return; }
        if (c.bored == 0) c.bored = 1;
        if (c.bored >= 10800) unsupported(UnsupportedBoundary.BORED_GIVE_UP);
    }

    /** Handle3DRamp (782-831). Stop on its actual matching raw data gate, before marking entry. */
    private void ramp(Cursor c) {
        int offset = ((c.y() >>> 1) & 0x380) + ((c.x() >>> 8) & 0x7F);
        int raw = terrainData.layout().ram().unsignedByte(offset);
        for (int i = 0; i < assets.rampChunks().length(); i++) {
            int id = assets.rampChunks().signedByte(i);
            if (id < 0) break;
            if (id == raw) unsupported(UnsupportedBoundary.RAMP_ENTRY);
        }
        boolean leaving = (c.ctrl & 2) != 0;
        c.ctrl &= ~2;
        // .NotFound uses unsigned CMP.W/BCC after testing negative oYVel (809-820).
        if (leaving && c.yv < 0 && uw(c.yv) < 0xF800) {
            c.xv = (c.flags & 1) == 0 ? 0x600 : -0x600;
        }
    }

    /** CheckJump (1779-1861). Successful ADDQ.L #4,sp exits the containing ground/roll MODE. */
    private boolean checkJump(Cursor c) {
        if (c.charge != 0 || ((c.held & 3) != 0 && c.gv == 0) || (c.tap & ACTION) == 0) return false;
        int headAngle = (c.angle + 0x80) & 0xFF;
        c.pri = c.sec = headAngle;
        if (pair(c, (headAngle + 0x20) & 0xC0).distance < 6) return false;
        int speed = (c.flags & 0x40) == 0 ? 0x680 : 0x380;
        var trig = math.calcSine(c.angle - 0x40);
        c.xv = sw(c.xv + ((trig.cosine() * speed) >> 8));
        c.yv = sw(c.yv + ((trig.sine() * speed) >> 8));
        c.flags = (c.flags | AIR) & ~0x20; c.jump = 1; c.stick = 0; c.look = 0;
        c.sound(Sound.FM_JUMP);
        if ((c.flags & ROLL) != 0) {
            c.flags |= 0x10;
        } else {
            c.yr = 14; c.xr = 7; c.ypos = addWord(c.ypos, 5);
            c.flags |= ROLL; c.anim = 2;
        }
        return true;
    }

    /** SlopeResist[/Roll] (1901-1966), preserving the signed word product and long ASR. */
    private void slope(Cursor c, boolean rolling) {
        if (c.charge != 0 || ((c.angle + 0x60) & 0xFF) >= 0xC0) return;
        int value = math.calcSine(c.angle).sine() * (rolling ? 0x50 : 0x20) >> 8;
        if (!rolling) { if (c.gv != 0) c.gv = sw(c.gv + value); return; }
        if ((c.gv >= 0 && value < 0) || (c.gv < 0 && value >= 0)) value >>= 2;
        c.gv = sw(c.gv + value);
    }

    /** MoveGround (837-1173), with real ObjGetFloorDist balance and exact helper continuation. */
    private void moveGround(Cursor c) {
        if (c.env.gates().waterSlide() != 0) { project(c, false); return; }
        if (c.lock != 0) { resetScreen(c, true); settleGround(c); project(c, false); return; }
        if ((c.held & LEFT) != 0) directionGround(c, true);
        if ((c.held & RIGHT) != 0) directionGround(c, false);
        if (((c.angle + 0x20) & 0xC0) != 0 || (c.gv != 0 && c.charge == 0)) {
            resetScreen(c, true); settleGround(c); project(c, false); return;
        }
        if (c.gv == 0) { c.flags &= ~0x20; c.anim = 5; }
        // Object support is rejected at admission; ordinary BalanceGround still does its centre probe.
        c.pri = 0;
        var balance = terrain.floor(c.x(), uw(c.y() + (byte) c.yr), 13, 0, 16, c.spr);
        c.pri = balance.angleWrite().orElse(c.pri);
        if (balance.distance() >= 12) {
            c.sound(Sound.FM_CHARGESTOP); c.charge = 0; c.gv = 0;
            boolean right = c.previousPri == 3;
            boolean left = !right && c.previousSec == 3;
            if (right || left) {
                boolean facingLeft = (c.flags & 1) != 0;
                c.anim = (right != facingLeft) ? 6 : 0x32;
                resetScreen(c, true); settleGround(c, 13); project(c, false); return;
            }
        }
        var r = CdPlayerCharge.moveGroundAtCheckCharge(c.chargeState(), c.chargeInput());
        c.apply(r);
        if (r.continuation() == CdPlayerCharge.Continuation.MOVE_GROUND_RETURN) return;
        if (r.continuation() != CdPlayerCharge.Continuation.GROUND_SETTLE) throw new IllegalStateException("Ground continuation");
        // ObjGetFloorDist MOVEQ #$D,d5 (Collision Check:170) survives its RTS.
        // The global sonicAcceleration is unchanged; only this caller path settles by 13.
        settleGround(c, 13); project(c, false);
    }

    /** MoveGndLeft/Right (1235-1338). The live d0 upper byte survives MOVE/ADD/AND.B at .Skid. */
    private static void directionGround(Cursor c, boolean left) {
        if (c.charge != 0) return;
        int velocity = c.gv;
        int accel = c.env.physics().acceleration(), decel = c.env.physics().deceleration();
        int top = c.env.physics().topSpeed();
        if (left ? velocity > 0 : velocity < 0) {
            int before = velocity;
            velocity = left ? sw(velocity - decel) : sw(velocity + decel);
            boolean carry = left ? uw(before) < uw(decel) : uw(before) + uw(decel) > 0xFFFF;
            if (carry) velocity = left ? -0x80 : 0x80;
            c.gv = velocity;
            int angleSector = (c.angle + 0x20) & 0xC0;
            int register = sw((velocity & 0xFF00) | angleSector);
            if (angleSector != 0 || (left ? register < 0x400 : register > -0x400)) return;
            c.anim = 0xD;
            c.flags = left ? c.flags & ~1 : c.flags | 1;
            c.sound(Sound.FM_SKID);
            return;
        }
        boolean oldFacingLeft = (c.flags & 1) != 0;
        c.flags = left ? c.flags | 1 : c.flags & ~1;
        if (oldFacingLeft != left) { c.flags &= ~0x20; c.prev = 1; }
        if (left) {
            int minimum = sw(-top);
            if (velocity > minimum) { velocity = sw(velocity - accel); if (velocity <= minimum) velocity = minimum; }
        } else if (velocity < top) {
            velocity = sw(velocity + accel); if (velocity >= top) velocity = top;
        }
        c.gv = velocity; c.anim = 0;
    }

    /** Settle uses native carry/borrow, including unusual signed acceleration words. */
    private static int settle(int velocity, int acceleration) {
        if (velocity == 0) return 0;
        if (velocity > 0) return uw(velocity) < uw(acceleration) ? 0 : sw(velocity - acceleration);
        return uw(velocity) + uw(acceleration) > 0xFFFF ? 0 : sw(velocity + acceleration);
    }
    private static void settleGround(Cursor c) {
        settleGround(c, c.env.physics().acceleration());
    }
    private static void settleGround(Cursor c, int acceleration) {
        if ((c.held & 12) == 0) c.gv = settle(c.gv, acceleration);
    }

    /** MoveRoll (1344-1509), including mature-release direction and early-cancel CalcXY entry. */
    private void moveRoll(Cursor c) {
        if (c.env.gates().waterSlide() != 0) { project(c, true); return; }
        if (c.lock == 0) {
            if ((c.held & LEFT) != 0) directionRoll(c, true);
            if ((c.held & RIGHT) != 0) directionRoll(c, false);
        }
        var r = CdPlayerCharge.moveRollAtNotRight(c.chargeState(), c.chargeInput());
        c.apply(r);
        switch (r.continuation()) {
            case MOVE_ROLL_RETURN -> { return; }
            case ROLL_CALC_XY_VELOCITIES -> { project(c, true); return; }
            case ROLL_MOVE_LEFT_THEN_SETTLE -> directionRoll(c, true);
            case ROLL_MOVE_RIGHT_THEN_SETTLE -> directionRoll(c, false);
            case ROLL_SETTLE -> { }
            default -> throw new IllegalStateException("Roll continuation");
        }
        c.gv = settle(c.gv, c.env.physics().acceleration() >> 1);
        if (c.gv == 0) {
            c.sound(Sound.FM_CHARGESTOP);
            c.flags &= ~ROLL; c.yr = 19; c.xr = 9;
            c.ypos = addWord(c.ypos, -5); c.anim = 5;
        }
        project(c, true);
    }

    /** MoveRollLeft/Right (1514-1553); no charge check in these routines. */
    private static void directionRoll(Cursor c, boolean left) {
        int decel = c.env.physics().deceleration() >> 2;
        if (left ? c.gv <= 0 : c.gv >= 0) {
            c.flags = left ? c.flags | 1 : c.flags & ~1; c.anim = 2; return;
        }
        int before = c.gv;
        c.gv = left ? sw(before - decel) : sw(before + decel);
        if (left ? uw(before) < uw(decel) : uw(before) + uw(decel) > 0xFFFF) c.gv = left ? -0x80 : 0x80;
    }

    /** CalcXYVels and CheckWallCol (1165-1229,1488-1508). MULS/ASR.L precedes low-word compare. */
    private void project(Cursor c, boolean rolling) {
        var trig = math.calcSine(c.angle);
        int x = sw(trig.cosine() * c.gv >> 8);
        c.yv = sw(trig.sine() * c.gv >> 8);
        if (rolling) x = Math.max(-0x1000, Math.min(0x1000, x));
        c.xv = x;
        if ((byte) (c.angle + 0x40) < 0 || c.gv == 0) return;
        int sensorAngle = (c.angle + (c.gv < 0 ? 0x40 : -0x40)) & 0xFF;
        int distance = roomInFront(c, sensorAngle);
        if (distance >= 0) return;
        int zip = sw(distance << 8);
        switch ((sensorAngle + 0x20) & 0xC0) {
            case 0 -> c.yv = sw(c.yv + zip);
            case 0x40 -> { c.xv = sw(c.xv - zip); c.flags |= 0x20; c.gv = 0; }
            case 0x80 -> c.yv = sw(c.yv - zip);
            default -> { c.xv = sw(c.xv + zip); c.flags |= 0x20; c.gv = 0; }
        }
    }

    /** CheckRoll/StartRoll (1720-1773). StartRoll is the existing source owner. */
    private static void checkRoll(Cursor c) {
        if (c.env.gates().waterSlide() != 0) return;
        int magnitude = c.gv < 0 ? uw(-c.gv) : c.gv;
        if (magnitude >= 0x80 && (c.held & 12) == 0 && (c.held & DOWN) != 0) {
            c.apply(CdPlayerCharge.startRoll(c.chargeState(), new CdPlayerCharge.Body(c.original.routine(), c.ctrl, c.env.gates().miniSonic())));
        }
    }

    /** LevelBound (1673-1714). KillPlayer is a typed pre-call boundary, never a guessed death result. */
    private static boolean levelBound(Cursor c) {
        int predictedX = (c.xpos + (c.xv << 8)) >>> 16;
        int edge = uw(c.env.bounds().left() + 16);
        boolean hit = edge > predictedX;
        if (!hit) {
            edge = uw(c.env.bounds().right() + 304);
            if (c.env.bounds().bossFight() == 0) edge = uw(edge + 56);
            hit = edge <= predictedX;
        }
        if (hit) { c.xpos = edge << 16; c.xv = 0; c.gv = 0; }
        return sw(c.env.bounds().bottom() + 224) < (short) c.y();
    }
    private static Result kill(Cursor c, int mode) {
        c.effects.add(new KillRequest());
        Continuation continuation = switch (mode) {
            case 0 -> Continuation.MD_GROUND_AFTER_LEVEL_BOUND;
            case ROLL -> Continuation.MD_ROLL_AFTER_LEVEL_BOUND;
            case AIR -> Continuation.MD_AIR_AFTER_LEVEL_BOUND;
            default -> Continuation.MD_JUMP_AFTER_LEVEL_BOUND;
        };
        return c.finish(Boundary.KILL_PLAYER, continuation);
    }

    /** ObjMove/ObjMoveGrv (Object Functions.asm:42-82,91-130), old signed Y integrates before gravity result. */
    private static void integrate(Cursor c, boolean gravity) {
        int oldY = c.yv;
        c.xpos += c.xv << 8;
        if (gravity) {
            c.yv = sw(c.yv + 0x38);
            if (c.yv >= 0x1000) c.yv = 0x1000;
        }
        c.ypos += oldY << 8;
    }
    private void ground(Cursor c) {
        var r = ground.ground(new CdPlayerGroundCollision.Input(
                new CdPlayerGroundCollision.State(c.xpos, c.ypos, c.angle, c.flags, c.prev),
                c.xr, c.yr, c.spr, c.stick));
        c.xpos = r.state().xPosition(); c.ypos = r.state().yPosition(); c.angle = r.state().angle();
        c.flags = r.state().flags(); c.prev = r.state().previousAnimation();
        c.pri = r.primaryAngle(); c.sec = r.secondaryAngle();
    }
    /** CheckFallOff (1974-2007), even after GroundCol detaches; stick skips lock decrement. */
    private static void falloff(Cursor c) {
        if (c.stick != 0) return;
        if (c.lock != 0) { c.lock = sw(c.lock - 1); return; }
        if (((c.angle + 0x20) & 0xC0) != 0 && (c.gv < 0 ? uw(-c.gv) : c.gv) < 0x280) {
            c.gv = 0; c.flags |= AIR; c.lock = 30;
        }
    }

    /** JumpHeight (1867-1893). */
    private static void jumpHeight(Cursor c) {
        if (c.jump != 0) {
            int cap = (c.flags & 0x40) == 0 ? -0x400 : -0x200;
            if (c.yv < cap && (c.held & ACTION) == 0) { c.charge = 0; c.yv = cap; }
        } else if (c.yv < -0xFC0) c.yv = -0xFC0;
    }
    /** MoveAir (1559-1650), including PP's literal launch-window early return and unsigned drag gate. */
    private static void moveAir(Cursor c) {
        if (c.x() >= 0x6C8 && c.x() < 0x840) return;
        int velocity = c.xv, acceleration = sw(c.env.physics().acceleration() << 1);
        int top = c.env.physics().topSpeed();
        if ((c.held & LEFT) != 0) {
            c.flags |= 1; velocity = sw(velocity - acceleration);
            int minimum = sw(-top); if (velocity <= minimum) velocity = minimum;
        }
        if ((c.held & RIGHT) != 0) {
            c.flags &= ~1; velocity = sw(velocity + acceleration); if (velocity >= top) velocity = top;
        }
        c.xv = velocity; resetScreen(c, false);
        if (uw(c.yv) < 0xFC00) return;
        int drag = c.xv >> 5;
        if (drag == 0) return;
        boolean borrow = uw(c.xv) < uw(drag);
        c.xv = sw(c.xv - drag);
        if (drag > 0 ? borrow : !borrow) c.xv = 0;
    }
    /** MoveGround.ResetScreen / MoveAir.ResetScreen (1121-1136,1607-1616). */
    private static void resetScreen(Cursor c, boolean clearLook) {
        if (c.cam == 0x60) { if (clearLook && (c.look & 15) == 0) c.look = 0; return; }
        if (c.cam < 0x60) c.cam = uw(c.cam + 4);
        c.cam = uw(c.cam - 2);
    }
    /** JumpAngle (2013-2036), byte carry/borrow saturates at zero. */
    private static void jumpAngle(Cursor c) {
        if ((c.ctrl & 2) != 0 || c.angle == 0) return;
        if ((byte) c.angle < 0) c.angle = c.angle >= 254 ? 0 : (c.angle + 2) & 0xFF;
        else c.angle = Math.max(0, c.angle - 2);
    }

    /** Air quadrant (2046-2068) reads post-gravity words and publishes actual debug byte writes. */
    private void airCollision(Cursor c) {
        c.debugAngle = math.calcAngleByte(c.xv, c.yv);
        c.debugShift = (c.debugAngle - 0x20) & 0xFF;
        c.debugQuadrant = c.debugShift & 0xC0;
        switch (c.debugQuadrant) {
            case 0x40 -> sideAir(c, true);
            case 0x80 -> upAir(c);
            case 0xC0 -> sideAir(c, false);
            default -> downAir(c);
        }
    }
    /** Down: left wall -> right wall -> floor; MOVE.B oYVel reads the HIGH memory byte of its word. */
    private void downAir(Cursor c) {
        wall(c, true); wall(c, false);
        Pair floor = pair(c, 0);
        c.debugFloor = floor.distance & 0xFF;
        if (floor.distance >= 0) return;
        // Main.asm:2093 reads big-endian MEMORY at oYVel, not a register byte or oYVel+1.
        int threshold = (byte) -(((c.yv >>> 8) & 0xFF) + 8);
        if ((byte) floor.distance < threshold && (byte) floor.otherDistance < threshold) return;
        c.ypos = addWord(c.ypos, floor.distance); c.angle = floor.angle;
        resetOnFloor(c); c.anim = 0;
        if (((floor.angle + 0x20) & 0x40) != 0) {
            c.xv = 0; if (c.yv > 0xFC0) c.yv = 0xFC0;
        } else if (((floor.angle + 0x10) & 0x20) != 0) {
            c.yv >>= 1;
        } else {
            c.yv = 0; c.gv = c.xv; return;
        }
        c.gv = (byte) floor.angle < 0 ? sw(-c.yv) : c.yv;
    }
    /** Left/right (2148-2185,2247-2286), wall contact returns before ceiling/floor. */
    private void sideAir(Cursor c, boolean left) {
        if (wall(c, left)) { c.gv = c.yv; return; }
        Pair ceiling = pair(c, 0x80);
        if (ceiling.distance < 0) {
            c.ypos = addWord(c.ypos, -ceiling.distance); if (c.yv < 0) c.yv = 0; return;
        }
        if (c.yv < 0) return;
        Pair floor = pair(c, 0);
        if (floor.distance >= 0) return;
        c.ypos = addWord(c.ypos, floor.distance); c.angle = floor.angle;
        resetOnFloor(c); c.anim = 0; c.yv = 0; c.gv = c.xv;
    }
    /** Up (2196-2238), flat ceiling keeps air/roll; steep attachment does not resize. */
    private void upAir(Cursor c) {
        wall(c, true); wall(c, false);
        Pair ceiling = pair(c, 0x80);
        if (ceiling.distance >= 0) return;
        c.ypos = addWord(c.ypos, -ceiling.distance);
        if (((ceiling.angle + 0x20) & 0x40) == 0) { c.yv = 0; return; }
        c.angle = ceiling.angle; resetOnSteepSlope(c);
        c.gv = (byte) ceiling.angle < 0 ? sw(-c.yv) : c.yv;
    }
    /**
     * Player_ResetOnFloor (2295-2330), the single implementation shared by MODE floor landings and
     * the touch response. miniSonic is the actual byte ($FF195B, TST.B). A miniature result is NOT
     * MODE admission: step() still rejects miniature Sonic before any MODE work.
     */
    public static ResetOnFloorResult resetOnFloor(CdPlayerMotionState motion, int miniSonic) {
        Objects.requireNonNull(motion); byteValue(miniSonic);
        // BTST #4 / BEQ / NOP (2296-2298) has no effect; BCLR 5, 1 and 4 follow.
        int flags = motion.flags() & ~(0x20 | AIR | 0x10), xr = motion.xRadius(), yr = motion.yRadius();
        int y = motion.centreY16_16(), anim = motion.animation();
        List<SoundRequest> sounds = List.of();
        if ((flags & ROLL) != 0) {
            flags &= ~ROLL; // SUBQ.W adjusts only the integer word; the fraction survives.
            if (miniSonic != 0) { yr = 0xA; xr = 5; y = addWord(y, -2); } else { yr = 0x13; xr = 9; y = addWord(y, -5); }
            anim = 0; sounds = List.of(new SoundRequest(Sound.FM_CHARGESTOP));
        }
        var m = motion;
        return new ResetOnFloorResult(new CdPlayerMotionState(m.centreX16_16(), y, m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), m.routine(), m.angle(), flags, m.spriteFlags(), xr, yr, anim, m.previousAnimation(),
                0, m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(),
                m.previousSecondaryAngle()), 0, sounds);
    }
    /** MODE floor landings (2104,2181,2280) delegate once; admission already rejected miniature Sonic. */
    private static void resetOnFloor(Cursor c) {
        var reset = resetOnFloor(c.motion(), c.env.gates().miniSonic());
        var m = reset.motion();
        c.flags = m.flags(); c.xr = m.xRadius(); c.yr = m.yRadius(); c.ypos = m.centreY16_16();
        c.anim = m.animation(); c.jump = m.playerJump(); c.score = reset.scoreChain();
        c.effects.addAll(reset.sounds());
    }
    /** Player_ResetOnSteepSlope (2339-2345, called at 2230): no bit-2 resize, Y, animation or sound. */
    private static void resetOnSteepSlope(Cursor c) {
        c.flags &= ~(0x20 | AIR | 0x10); c.jump = 0; c.score = 0;
    }
    private boolean wall(Cursor c, boolean left) {
        int distance = single(c, c.x(), c.y(), left ? 0x40 : 0xC0);
        if (distance >= 0) return false;
        c.xpos = addWord(c.xpos, left ? -distance : distance); c.xv = 0;
        return true;
    }

    /** Collision Check.asm:14-65: predict with full fractions, signed-round angle; wall Y+8 gate. */
    private int roomInFront(Cursor c, int angle) {
        int x = (c.xpos + (c.xv << 8)) >>> 16;
        int y = (c.ypos + (c.yv << 8)) >>> 16;
        c.pri = c.sec = angle;
        int rounded;
        if ((byte) (angle + 0x20) < 0) {
            rounded = (angle + ((byte) angle < 0 ? -1 : 0) + 0x20) & 0xC0;
        } else {
            rounded = (angle + ((byte) angle < 0 ? 1 : 0) + 0x1F) & 0xC0;
        }
        if (rounded != 0 && rounded != 0x80 && (angle & 0x38) == 0) y = uw(y + 8);
        return single(c, x, y, rounded);
    }
    /** Single Part2 probes (Collision Check.asm:137-153,227-235,305-314,388-397). */
    private int single(Cursor c, int x, int y, int direction) {
        CdPlayerTerrain.Result result = switch (direction) {
            case 0 -> terrain.floor(x, uw(y + 10), 14, 0, 16, c.spr);
            case 0x40 -> terrain.wall(uw(x - 10) ^ 15, y, 14, 0x800, -16, c.spr);
            case 0x80 -> terrain.floor(x, uw(y - 10) ^ 15, 14, 0x1000, -16, c.spr);
            default -> terrain.wall(uw(x + 10), y, 14, 0, 16, c.spr);
        };
        c.pri = result.angleWrite().orElse(c.pri);
        return result.distance();
    }
    private record Pair(int distance, int otherDistance, int angle) { }

    /** Paired CheckFloor/Ceiling/LCeil/RCeil, ChooseAngle (Collision Check.asm:79-125,184-216,259-293,343-377). */
    private Pair pair(Cursor c, int direction) {
        int x = c.x(), y = c.y(), rx = (byte) c.xr, ry = (byte) c.yr;
        CdPlayerTerrain.Result first, second;
        switch (direction) {
            case 0 -> {
                first = terrain.floor(uw(x + rx), uw(y + ry), 13, 0, 16, c.spr);
                c.pri = first.angleWrite().orElse(c.pri);
                second = terrain.floor(uw(x - rx), uw(y + ry), 13, 0, 16, c.spr);
            }
            case 0x40 -> {
                first = terrain.wall(uw(x - ry) ^ 15, uw(y - rx), 14, 0x800, -16, c.spr);
                c.pri = first.angleWrite().orElse(c.pri);
                second = terrain.wall(uw(x - ry) ^ 15, uw(y + rx), 14, 0x800, -16, c.spr);
            }
            case 0x80 -> {
                first = terrain.floor(uw(x + rx), uw(y - ry) ^ 15, 14, 0x1000, -16, c.spr);
                c.pri = first.angleWrite().orElse(c.pri);
                second = terrain.floor(uw(x - rx), uw(y - ry) ^ 15, 14, 0x1000, -16, c.spr);
            }
            default -> {
                first = terrain.wall(uw(x + ry), uw(y - rx), 14, 0, 16, c.spr);
                c.pri = first.angleWrite().orElse(c.pri);
                second = terrain.wall(uw(x + ry), uw(y + rx), 14, 0, 16, c.spr);
            }
        }
        c.sec = second.angleWrite().orElse(c.sec);
        boolean secondary = second.distance() <= first.distance();
        int angle = secondary ? c.sec : c.pri;
        return new Pair(secondary ? second.distance() : first.distance(),
                secondary ? first.distance() : second.distance(), (angle & 1) == 0 ? angle : direction);
    }

    /** Per-call detached semantic fields. Never retained, published, or indexed as RAM. */
    private static final class Cursor {
        final CdPlayerMotionState original;
        final Environment env;
        final int held, tap, previousPri, previousSec;
        final List<Effect> effects = new ArrayList<>();
        int xpos, ypos, xv, yv, gv, angle, flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl;
        int look, cam, pri, sec, bored, score, debugAngle, debugShift, debugQuadrant, debugFloor;
        Cursor(CdPlayerMotionState s, Input input, Environment env) {
            original = s; this.env = env; held = input.logicalHeld(); tap = input.logicalTap();
            xpos = s.centreX16_16(); ypos = s.centreY16_16(); xv = s.xVelocity(); yv = s.yVelocity(); gv = s.groundVelocity();
            angle = s.angle(); flags = s.flags(); spr = s.spriteFlags(); xr = s.xRadius(); yr = s.yRadius();
            anim = s.animation(); prev = s.previousAnimation(); jump = s.playerJump(); charge = s.charge();
            lock = s.moveLock(); stick = s.stick(); ctrl = s.playerControl();
            previousPri = s.previousPrimaryAngle(); previousSec = s.previousSecondaryAngle();
            var g = env.globals(); look = g.lookMode(); cam = g.cameraYCentre(); pri = g.primaryAngle(); sec = g.secondaryAngle();
            bored = g.boredTimer(); score = g.scoreChain(); debugAngle = g.debugAngle(); debugShift = g.debugAngleShift();
            debugQuadrant = g.debugQuadrant(); debugFloor = g.debugFloorDistance();
        }
        int x() { return xpos >>> 16; }
        int y() { return ypos >>> 16; }
        void sound(Sound value) { effects.add(new SoundRequest(value)); }
        CdPlayerCharge.State chargeState() {
            return new CdPlayerCharge.State(charge, gv, xv, yv, flags, xr, yr, ypos, anim, look, cam);
        }
        CdPlayerCharge.Input chargeInput() {
            return new CdPlayerCharge.Input(new CdPlayerCharge.Body(original.routine(), ctrl, env.gates().miniSonic()), held, tap,
                    env.physics().topSpeed(), env.gates().speedShoes(), angle, lock, env.gates().waterSlide());
        }
        void apply(CdPlayerCharge.Result result) {
            var s = result.state(); charge = s.charge(); gv = s.groundVelocity(); xv = s.storedXVelocity(); yv = s.storedYVelocity();
            flags = s.flags(); xr = s.xRadius(); yr = s.yRadius(); ypos = s.centreY16_16(); anim = s.animation();
            look = s.lookMode(); cam = s.cameraYCentre();
            for (var effect : result.effects()) effects.add(new ChargeEffect(effect));
        }
        CdPlayerMotionState motion() {
            return new CdPlayerMotionState(xpos, ypos, xv, yv, gv, original.routine(), angle, flags, spr, xr, yr,
                    anim, prev, jump, charge, lock, stick, ctrl, previousPri, previousSec);
        }
        Result finish(Boundary boundary, Continuation continuation) {
            var state = motion();
            var globals = new Globals(look, cam, pri, sec, bored, score, debugAngle, debugShift, debugQuadrant, debugFloor);
            return new Result(state, new Environment(env.physics(), env.bounds(), env.gates(), globals), effects, boundary, continuation);
        }
    }
    private static void unsupported(UnsupportedBoundary boundary) { throw new UnsupportedMotion(boundary); }
    private static int addWord(int position, int delta) { return (uw((position >>> 16) + delta) << 16) | (position & 0xFFFF); }
    private static int sw(int value) { return (short) value; }
    private static int uw(int value) { return value & 0xFFFF; }
    private static void byteValue(int value) { if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected a native byte"); }
    private static void wordValue(int value) { if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word"); }
    private static void signedWord(int value) { if (value != (short) value) throw new IllegalArgumentException("Expected a signed native word"); }
}
