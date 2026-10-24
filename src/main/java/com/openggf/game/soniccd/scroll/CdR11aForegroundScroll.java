package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdR11aLoadPlan;
import com.openggf.game.soniccd.player.CdPlayerMovement;

import java.util.Objects;

/**
 * Pure R11A foreground LevelScroll cut and its PP1-present/shared bottom events.
 * Source pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, FixBugs=0.
 * Citations to "PP Scroll" mean Level/Palmtree Panic/Scroll (Act 1 Present).asm;
 * "Events" means Level/_Events.asm, under docs/scddisasm/src/.
 *
 * The context-free step retains its explicitly STATIC BACKGROUND development contract.
 * The named stepForBackground cut admits ramp control for a caller that executes the real BG owner
 * immediately afterward. Neither entry executes background/deformation/section-art/draw owners,
 * publish an engine camera, service a frame, or claim complete native LevelScroll.
 * Caller runs this after objects through its normal camera-driven seam, then
 * commits Result.state and shared bounds/Y-centre feedback once after success.
 * Draw, output rebuild and restore must only read/replace values, never call step.
 */
public final class CdR11aForegroundScroll {
    private CdR11aForegroundScroll() { }

    /** Native centre words, flag/control/animation bytes, signed ground-velocity word. */
    public record Actor(int centreX, int centreY, int flags, int groundVelocity,
                        int animation, int playerControl) {
        public Actor {
            wordValue(centreX); wordValue(centreY); byteValue(flags); signedWord(groundVelocity);
            byteValue(animation); byteValue(playerControl);
        }
    }

    /**
     * Only additional camera/event RAM is captured here. cameraY16_16 retains
     * every longword bit. All other word fields are RAW UNSIGNED word values,
     * including the two 8:8 diff words and flags/shift/lock words. Crossed fields
     * are bytes; LevelSizeLoad's single #$1010 write initializes both to $10.
     *
     * Big-endian memory: TST.B/WRITE.B/BSET at a word label accesses its HIGH byte.
     * Thus bottomBoundShift=$0100 is active; $0001 alone is not. Scroll flags
     * up/down/left/right are $0100/$0200/$0400/$0800 in this raw word encoding.
     * Level/_Definitions.inc:237-278 declares the exact native widths/adjacency.
     * Bounds and camera centres deliberately have no second owner in this record.
     */
    public record State(int cameraX, int cameraY16_16, int horizontalCrossed, int verticalCrossed,
                        int scrollXDiff, int scrollYDiff, int scrollFlags,
                        int destBottomBound, int destLeftBound, int bottomBoundShift, int scrollLock) {
        public State {
            wordValue(cameraX); byteValue(horizontalCrossed); byteValue(verticalCrossed);
            wordValue(scrollXDiff); wordValue(scrollYDiff); wordValue(scrollFlags);
            wordValue(destBottomBound); wordValue(destLeftBound);
            wordValue(bottomBoundShift); wordValue(scrollLock);
        }
        public int cameraYWord() { return cameraY16_16 >>> 16; }
    }

    /**
     * Bounds and both centres come from the actual shared player environment,
     * after Body/MODE has completed. CdPlayerMovement.Bounds currently has no top
     * member, so the authoritative current top word is supplied separately.
     * This source slice writes neither top nor X centre. Preserve bossFight.
     *
     * sourceHudTimeActive is the actual updateHUDTime BYTE. If the development
     * admission omits the HUD timer it must explicitly supply zero and declare
     * that policy; no timer, elapsed-time inference or default is owned here.
     * Dimensions are admitted in step (including when locked), exactly 320x224.
     */
    public record Input(Actor actor, CdPlayerMovement.Bounds bounds, int topBound,
                        int cameraXCentre, int cameraYCentre, CdReleaseVariant region,
                        int sourceHudTimeActive, int viewportWidth, int viewportHeight) {
        public Input {
            Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(region, "region"); wordValue(topBound);
            wordValue(cameraXCentre); wordValue(cameraYCentre); byteValue(sourceHudTimeActive);
        }
    }

    public enum Boundary {
        SCROLL_LOCKED, FOREGROUND_COMPLETE_STATIC_BACKGROUND_DEVELOPMENT,
        FOREGROUND_COMPLETE_FOR_BACKGROUND, DEAD_CAMERA_FREEZE
    }

    /** Feed bounds/Y centre back to their existing shared owner; do not retain another shadow. */
    public record Result(State state, CdPlayerMovement.Bounds bounds, int cameraYCentre, Boundary boundary) {
        public Result {
            Objects.requireNonNull(state, "state"); Objects.requireNonNull(bounds, "bounds");
            wordValue(cameraYCentre); Objects.requireNonNull(boundary, "boundary");
        }
    }

    public enum UnsupportedBoundary {
        ACTIVE_3D_RAMP_BACKGROUND, TOP_WRAP_ACTOR_AND_BACKGROUND, BOTTOM_WRAP_ACTOR_AND_BACKGROUND
    }

    /** No Result or partial actor/background writes are exposed for these unowned source effects. */
    public static final class UnsupportedScroll extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private UnsupportedScroll(UnsupportedBoundary boundary) { super(boundary.name()); this.boundary = boundary; }
        public UnsupportedBoundary boundary() { return boundary; }
    }

    /**
     * Named cold-development projection of completed ClearCamera/LevelSizeLoad,
     * accepting the real source load plan, not guessed stage dimensions.
     * Level/Main.asm:210-216 clears camera RAM; PP Scroll LevelSizeLoad:19-34,
     * DefaultStart/SetupCamera:76-109 installs targets/cross bytes/centre words.
     * This factory executes no actor, event, BG, PLC, render or engine work.
     * Shared bounds and centres remain the caller's plan-derived environment.
     */
    public static State coldFromLoadPlan(CdR11aLoadPlan loadPlan, int viewportWidth, int viewportHeight) {
        Objects.requireNonNull(loadPlan, "loadPlan");
        requireViewport(viewportWidth, viewportHeight);
        var start = loadPlan.initialCentre();
        var bounds = loadPlan.bounds();
        int x = start.centreX() < 160 ? 0 : uw(start.centreX() - 160);
        if (x >= bounds.right()) x = bounds.right(); // SetupCamera's unsigned BCS.
        int y = start.centreY() < 0x60 ? 0 : uw(start.centreY() - 0x60);
        if (sw(y) >= sw(bounds.bottom())) y = bounds.bottom(); // SetupCamera's signed BLT.
        return new State(x, y << 16, 0x10, 0x10, 0, 0, 0,
                bounds.bottom(), bounds.left(), 0, 0);
    }

    /**
     * Level_MainLoop's dead-camera branch (Level/Main.asm:405-418), after ALL object slots and
     * before LevelScroll. The caller owns levelRestart/debug/routine admission. Native skips
     * LevelScroll entirely: only bottomBound and destBottomBound take cameraY's high word.
     * Keep camera fractions, flags, event/shift state and centres; sprite build and loop tail
     * still belong to their existing owners. This method executes no scroll/event service.
     */
    public static Result deadCameraFreeze(State state, CdPlayerMovement.Bounds bounds, int cameraYCentre) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(bounds, "bounds");
        wordValue(cameraYCentre);
        int bottom = state.cameraYWord();
        var frozen = new State(state.cameraX(), state.cameraY16_16(), state.horizontalCrossed(),
                state.verticalCrossed(), state.scrollXDiff(), state.scrollYDiff(), state.scrollFlags(),
                bottom, state.destLeftBound(), state.bottomBoundShift(), state.scrollLock());
        return new Result(frozen, new CdPlayerMovement.Bounds(bounds.left(), bounds.right(), bottom,
                bounds.bossFight()), cameraYCentre, Boundary.DEAD_CAMERA_FREEZE);
    }

    /** One admitted foreground step. All writes remain detached until the complete Result exists. */
    public static Result step(State state, Input input) {
        return step(state, input, false);
    }

    /**
     * Pure foreground-only cut for the World composition's immediate real BG continuation.
     * Admits the native ramp bit without changing old consumers, clocks, wraps or boundaries.
     * The caller must execute CdR11aBackgroundScroll with this completed Result and actual oXVel;
     * this method alone is not a complete LevelScroll and publishes nothing.
     */
    public static Result stepForBackground(State state, Input input) {
        return step(state, input, true);
    }

    private static Result step(State state, Input input, boolean forBackground) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(input, "input");
        requireViewport(input.viewportWidth(), input.viewportHeight());
        // LevelScroll:177-186 returns before flag clears, events, X/Y or BG when the HIGH byte is set.
        if (highByte(state.scrollLock()) != 0) {
            return new Result(state, input.bounds(), input.cameraYCentre(), Boundary.SCROLL_LOCKED);
        }
        // PP Scroll:203-238 consumes the actual ramp-control bit for unowned BG velocity.
        // Reject the matching native control state, never an actor/camera coordinate range.
        if (!forBackground && (input.actor().playerControl() & 2) != 0) reject(UnsupportedBoundary.ACTIVE_3D_RAMP_BACKGROUND);
        var c = new Cursor(state, input);
        // LevelScroll:188-196: events see old XY in USA, completed new XY in JP/EU.
        if (input.region() == CdReleaseVariant.USA) events(c);
        horizontal(c);
        vertical(c);
        if (input.region() != CdReleaseVariant.USA) events(c);
        return c.finish(forBackground ? Boundary.FOREGROUND_COMPLETE_FOR_BACKGROUND
                : Boundary.FOREGROUND_COMPLETE_STATIC_BACKGROUND_DEVELOPMENT);
    }

    /** LevEvents_PPZ1:86-117 then RunLevelEvents:14-52, in that order. */
    private static void events(Cursor c) {
        int actorX = c.in.actor().centreX();
        if (actorX >= 0x1C16 && actorX < 0x21C6) c.yCentre = 0x88;
        c.destBottom = 0x710;
        if (c.x >= 0x840) {
            if (c.in.sourceHudTimeActive() != 0 && c.left < 0x820) {
                c.left = c.destLeft = 0x820;
            }
            c.destBottom = c.x < 0xE00 ? 0x410 : 0x310;
        }
        if (c.in.actor().animation() == 0x2B) c.bottom = c.destBottom = c.yWord();
        // SUB.W/BCC is unsigned here, not a signed destination-difference test.
        if (c.destBottom == c.bottom) return; // Does NOT clear an existing shift byte.
        if (c.destBottom < c.bottom) {
            if (c.yWord() > c.destBottom) c.bottom = c.yWord() & 0xFFFE;
            c.bottom = uw(c.bottom - 4);
        } else {
            int speed = uw(c.yWord() + 8) >= c.bottom && (c.in.actor().flags() & 2) != 0 ? 16 : 4;
            c.bottom = uw(c.bottom + speed); // No target clamp, including a constructed overshoot.
        }
        c.shift = 0x0100 | (c.shift & 0xFF); // MOVE.B #1 at a WORD label writes only the high byte.
    }

    /** ScrollCamX/MoveScreenHoriz, PP Scroll:540-605. */
    private static void horizontal(Cursor c) {
        int oldX = c.x;
        int relative = uw(c.in.actor().centreX() - oldX);
        boolean borrow = relative < c.in.cameraXCentre(); // CARRY from the SECOND subtraction only.
        int distance = uw(relative - c.in.cameraXCentre());
        if (distance == 0) {
            c.dx = 0;
        } else {
            int signedDistance = sw(distance);
            if (borrow) {
                if (signedDistance < -16) signedDistance = -16;
                c.x = uw(oldX + signedDistance);
                if (sw(c.x) <= sw(c.left)) c.x = c.left;
            } else {
                if (signedDistance >= 16) signedDistance = 16;
                c.x = uw(oldX + signedDistance);
                if (sw(c.x) >= sw(c.in.bounds().right())) c.x = c.in.bounds().right();
            }
            c.dx = uw(uw(c.x - oldX) << 8); // ASL.W #8, retaining native word overflow.
        }
        // The crossing check runs even when MoveScreenHoriz took AtDest.
        if (((c.x & 0x10) ^ c.hCross) == 0) {
            c.hCross ^= 0x10;
            c.flags |= sw(c.x - oldX) < 0 ? 0x0400 : 0x0800;
        }
    }

    /** ScrollCamY/NoRoll/OnGround/CamMoving, PP Scroll:628-693. */
    private static void vertical(Cursor c) {
        int distance = uw(c.in.actor().centreY() - c.yWord());
        if ((c.in.actor().flags() & 4) != 0) distance = uw(distance - 5);
        int cap;
        if ((c.in.actor().flags() & 2) != 0) {
            distance = uw(distance + 0x20);
            boolean above = distance < c.yCentre;
            distance = uw(distance - c.yCentre);
            if (!above) {
                boolean inside = distance < 0x40;
                distance = uw(distance - 0x40);
                if (inside) { idleOrStop(c); return; }
            }
            cap = 16;
        } else {
            distance = uw(distance - c.yCentre);
            if (distance == 0) { idleOrStop(c); return; }
            if (c.yCentre != 0x60) {
                cap = 2;
            } else {
                int groundVelocity = c.in.actor().groundVelocity();
                // NEG.W of -$8000 remains $8000; unsigned BCC #$800 selects fast.
                int magnitude = groundVelocity < 0 ? uw(-groundVelocity) : groundVelocity;
                cap = magnitude >= 0x800 ? 16 : 6;
            }
        }
        moveVertical(c, sw(distance), cap);
    }

    /** DoNotScroll/StopCam, PP Scroll:646-658,697-704. */
    private static void idleOrStop(Cursor c) {
        if (highByte(c.shift) == 0) { c.dy = 0; return; }
        c.shift &= 0xFF; // MOVE.B #0 to high byte; preserve the lower native byte.
        moveVertical(c, 0, 0);
    }

    /** GotCamSpeed/MovingUp/MovingDown and actual bound/wrap predicates, PP Scroll:701-778. */
    private static void moveVertical(Cursor c, int distance, int cap) {
        int oldY = c.y;
        int candidate;
        if (distance > cap) {
            candidate = oldY + (cap << 16); // EXT.L/ASL.L/ADD.L preserves the old fraction.
        } else if (distance < -cap) {
            candidate = oldY - (cap << 16);
        } else {
            // MOVEQ #0 followed by WORD move/add and SWAP creates a ZERO fractional word.
            candidate = uw(c.yWord() + distance) << 16;
        }
        int candidateWord = candidate >>> 16;
        if (distance < 0) {
            if (sw(candidateWord) <= sw(c.in.topBound())) {
                if (sw(candidateWord) <= -0x100) reject(UnsupportedBoundary.TOP_WRAP_ACTOR_AND_BACKGROUND);
                candidate = (c.in.topBound() << 16) | (candidate & 0xFFFF);
            }
        } else if (sw(candidateWord) >= sw(c.bottom)) {
            // ChkBottom SUBI.W #$800/B(CS): reject only when this actual path would wrap.
            if (candidateWord >= 0x800) reject(UnsupportedBoundary.BOTTOM_WRAP_ACTOR_AND_BACKGROUND);
            candidate = (c.bottom << 16) | (candidate & 0xFFFF);
        }
        c.dy = Integer.rotateRight(candidate - oldY, 8) & 0xFFFF; // ROR.L #8 then MOVE.W.
        c.y = candidate;
        if (((c.yWord() & 0x10) ^ c.vCross) == 0) {
            c.vCross ^= 0x10;
            c.flags |= sw(c.yWord() - (oldY >>> 16)) < 0 ? 0x0100 : 0x0200;
        }
    }

    /** Detached scratch values only; no retained owner, external write or callback exists. */
    private static final class Cursor {
        final Input in;
        final int lock;
        int x, y, hCross, vCross, dx, dy, flags, destBottom, destLeft, shift;
        int left, bottom, yCentre;

        Cursor(State s, Input input) {
            in = input; lock = s.scrollLock(); x = s.cameraX(); y = s.cameraY16_16();
            hCross = s.horizontalCrossed(); vCross = s.verticalCrossed();
            dx = s.scrollXDiff(); dy = s.scrollYDiff(); flags = 0; // LevelScroll CLR.W.
            destBottom = s.destBottomBound(); destLeft = s.destLeftBound(); shift = s.bottomBoundShift();
            left = input.bounds().left(); bottom = input.bounds().bottom(); yCentre = input.cameraYCentre();
        }
        int yWord() { return y >>> 16; }
        Result finish(Boundary boundary) {
            var oldBounds = in.bounds();
            var bounds = left == oldBounds.left() && bottom == oldBounds.bottom() ? oldBounds
                    : new CdPlayerMovement.Bounds(left, oldBounds.right(), bottom, oldBounds.bossFight());
            return new Result(new State(x, y, hCross, vCross, dx, dy, flags,
                    destBottom, destLeft, shift, lock), bounds, yCentre,
                    boundary);
        }
    }

    private static void requireViewport(int width, int height) {
        if (width != 320 || height != 224) {
            throw new IllegalArgumentException("R11A development foreground currently admits exactly 320x224");
        }
    }
    private static void reject(UnsupportedBoundary boundary) { throw new UnsupportedScroll(boundary); }
    private static int uw(int value) { return value & 0xFFFF; }
    private static int sw(int value) { return (short) value; }
    private static int highByte(int word) { return word >>> 8; }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Native byte required");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Native word required");
    }
    private static void signedWord(int value) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) throw new IllegalArgumentException("Signed native word required");
    }
}
