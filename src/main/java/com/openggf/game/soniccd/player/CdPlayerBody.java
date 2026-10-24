package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure R11A ObjSonic dispatch/Init/Main/Hurt owner.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/_Objects/Sonic/Main.asm:56-106,135-456,2351-2391,2500-2603, plus
 * src/Level/Object Functions.asm:91-128 (ObjMove) and 137 (DrawObject) for Hurt.
 * Shipped FixBugs=0 behavior; no repaired arithmetic, route or frame gates.
 *
 * One invocation is one loaded player object call, NOT a level frame, V-int,
 * native boot, SAT build, render or parity result. Input has already been read
 * by its production service. Clocks, actor/engine projections and device work
 * remain outside. No setup dispatch, first-frame cue or default/carry/reset
 * factory exists. Every actor field, global, history byte and latch is ingress.
 *
 * Results are staged until the entire admitted call returns. Final actor and
 * Environment values are authoritative; effects describe ordered source work,
 * including historical helper writes. NEVER replay those writes over the final
 * values. Only supported deferred sound requests should reach a sound sink,
 * once after a completed result is accepted. Exceptions expose no partial
 * result, sound list or mutable cursor, so retry cannot emit half a call.
 */
public final class CdPlayerBody {
    /** oPrevAnim is stored ONCE, in motion.previousAnimation(). */
    public record AnimationCursor(int scriptIndex, int timer, int mapFrame) {
        public AnimationCursor { byteValue(scriptIndex); byteValue(timer); byteValue(mapFrame); }
    }

    /** Native object header. mappingAddress is the actual source-bound native long, not a Java renderer ID. */
    public record Header(int slotId, int mappingAddress, int tile, int priority, int width) {
        public Header { byteValue(slotId); wordValue(tile); byteValue(priority); byteValue(width); }
    }
    /**
     * Actor words oPlayerHurt/Invinc/Shoes/Reset ($30/$32/$34/$3A, Level/_Definitions.inc:83-86). reset is only
     * written by ObjSonic_DeadChkGone (Sonic/Main.asm:2439,2444) and counted by ObjSonic_Restart (:2454-2457);
     * Init, Main and Hurt carry it unchanged. No default overload: ingress or capture supplies all four words.
     */
    public record Timers(int hurt, int invincibility, int speedShoes, int reset) {
        public Timers { wordValue(hurt); wordValue(invincibility); wordValue(speedShoes); wordValue(reset); }
    }

    /**
     * Complete sonicRecordBuf, with its actual word index. No implicit clear.
     * Native RecordPos writes at the CURRENT index, then increments only its
     * low byte. Non-buffer/unaligned indices reject at that call, not at Init.
     * CdAssetBytes owns immutable storage; content equality supports replay.
     */
    public record History(CdAssetBytes bytes, int index) {
        public History {
            Objects.requireNonNull(bytes); wordValue(index);
            if (bytes.length() != 0x100) throw new IllegalArgumentException("Complete $100-byte sonicRecordBuf required");
        }
        @Override public boolean equals(Object value) {
            return value instanceof History other && index == other.index
                    && Arrays.equals(bytes.copy(), other.bytes.copy());
        }
        @Override public int hashCode() { return 31 * Arrays.hashCode(bytes.copy()) + index; }
    }

    /**
     * Body-owned capture, including native-global history whose only admitted writer is RecordPos.
     * Shared physics, look, angle buffers and controls are exclusively in Environment.
     * rotationDistance is native byte39 (oPlayerRotDist), adjacent to motion.stick at38.
     * Main/MODE do not write it; KillPlayer's word38 and later object owners do.
     * There is no implicit zero/default overload: the actor ingress/capture supplies it.
     */
    public record State(CdPlayerMotionState motion, AnimationCursor animation, Header header,
                        Timers timers, History history, int rotationDistance) {
        public State {
            Objects.requireNonNull(motion); Objects.requireNonNull(animation); Objects.requireNonNull(header);
            Objects.requireNonNull(timers); Objects.requireNonNull(history); byteValue(rotationDistance);
        }
    }

    /** Native already-serviced raw held/tap and retained logical held/tap pairs. No edge reconstruction. */
    public record Controls(int rawHeld, int rawTap, int logicalHeld, int logicalTap, int controlLocked) {
        public Controls {
            byteValue(rawHeld); byteValue(rawTap); byteValue(logicalHeld); byteValue(logicalTap); byteValue(controlLocked);
        }
    }

    /**
     * Actual Main/header/display globals not already held by Movement.Environment.
     * levelFrames is the already incremented level word; this owner never advances it.
     * debugMode/debugCheat are complete WORDS; ObjSonic tests debugMode's high
     * byte, while Main tests the whole debugCheat word. No absolute V-int seed is consumed.
     */
    public record Globals(int levelFrames, int cameraXCentre, int timeAttackMode, int debugMode,
                          int debugCheat, int timeWarpTimer, int invincible, int bossMusic) {
        public Globals {
            wordValue(levelFrames); wordValue(cameraXCentre); byteValue(timeAttackMode); wordValue(debugMode);
            wordValue(debugCheat); wordValue(timeWarpTimer); byteValue(invincible); byteValue(bossMusic);
        }
    }

    /**
     * Deliberate development admission of 96 inactive dynamic slots. This says
     * NOTHING about native placement data, rings, badniks or opening-route support.
     * This fallback applies only without an explicit TouchContext. UNIMPLEMENTED
     * rejects only when the source actually calls the touch owner without one.
     */
    public enum DynamicSlots { EMPTY_DEVELOPMENT_96, UNIMPLEMENTED }

    /**
     * Runtime-owned native bytes read or written by the selected enemy/hazard handler:
     * timeWarp ($FF1521), shield ($FF151E) and combineRing ($FF156F). Ephemeral call-cut
     * input only: never part of State, Environment or a Frame capture.
     */
    public record ContactGlobals(int timeWarp, int shield, int combineRing) {
        public ContactGlobals { byteValue(timeWarp); byteValue(shield); byteValue(combineRing); }
    }

    /**
     * The Runtime's single source-bound response owner plus its contact and world globals at this
     * call cut. Ephemeral like TouchContext; the Runtime alone commits contact/world/slot results.
     */
    public record ResponseContext(CdPlayerTouchResponse response, ContactGlobals contact,
                                  CdPlayerTouchResponse.WorldGlobals world) {
        public ResponseContext {
            Objects.requireNonNull(response); Objects.requireNonNull(contact); Objects.requireNonNull(world);
        }
    }

    /**
     * Ephemeral declared pure input, NOT a world/source ownership certificate.
     * The external world binding/preflight owns the actual scanner table and
     * pool identity. Body neither retains this input nor owns a pool capture;
     * Environment/State and frame/actor captures must never contain it.
     * An empty response means no ENEMY/HAZARD owner: those selections still stop
     * at UnsupportedTouchHandler. It is never a zero-filled global fallback.
     */
    public record TouchContext(CdPlayerObjectTouch scanner, CdObjectPool.Snapshot snapshot,
                               Optional<ResponseContext> response) {
        public TouchContext {
            Objects.requireNonNull(scanner); Objects.requireNonNull(snapshot); Objects.requireNonNull(response);
        }
        /** Scanner and capture only; no response owner. */
        public TouchContext(CdPlayerObjectTouch scanner, CdObjectPool.Snapshot snapshot) {
            this(scanner, snapshot, Optional.empty());
        }
        public TouchContext(CdPlayerObjectTouch scanner, CdObjectPool.Snapshot snapshot, ResponseContext response) {
            this(scanner, snapshot, Optional.of(response));
        }
    }
    public record Environment(CdPlayerMovement.Environment movement, Controls controls,
                              Globals globals, DynamicSlots dynamicSlots) {
        public Environment {
            Objects.requireNonNull(movement); Objects.requireNonNull(controls);
            Objects.requireNonNull(globals); Objects.requireNonNull(dynamicSlots);
        }
    }

    /** HURT_RETURN is a completed ObjSonic_Hurt call (Main.asm:2351-2366): drawn, but no MODE was called. */
    public enum Boundary { SLOT_ABSENT, INIT_RETURN, MAIN_RETURN, HURT_RETURN }
    public enum NativeCall {
        CHARGE_BEFORE_DISPATCH, INIT, EXTENDED_CAMERA, WATERFALL_SPLASH_CHECK, MODE,
        PP_SPECIAL_COLLISION, DISPLAY, RECORD_POSITION, PP_WATER, ANIMATE, SPECIAL_CHUNKS,
        OBJ_MOVE, HURT_CHECK_LAND, LEVEL_COLLISION_IN_AIR, KILL_PLAYER, LEVEL_BOUND, DRAW_OBJECT
    }
    /** CENTRE_*_LONG values are ObjMove's complete 16.16 longs; velocity values are signed words. */
    public enum ActorField {
        ROUTINE, X_RADIUS, Y_RADIUS, MAPPING_ADDRESS, TILE, PRIORITY, WIDTH, SPRITE_FLAGS,
        HURT_TIMER, INVINCIBILITY_TIMER, SHOES_TIMER, RESET_TIMER, PRIMARY_ANGLE_COPY, SECONDARY_ANGLE_COPY, ANIMATION,
        CENTRE_X_LONG, CENTRE_Y_LONG, X_VELOCITY, Y_VELOCITY, GROUND_VELOCITY
    }
    public enum GlobalField {
        CAMERA_X_CENTRE, LOGICAL_HELD, LOGICAL_TAP, INVINCIBLE, SPEED_SHOES,
        TOP_SPEED, ACCELERATION, DECELERATION
    }
    /** Present R11A PlayLevelMusic resolves to SCMD_R1AMUS (Level/Main.asm:555-583). */
    public enum Sound { SCMD_R1AMUS, FM_9C }
    public sealed interface Effect permits ActorWrite, GlobalWrite, ChargeEffect, ModeEffect,
            CallReturned, DisplayEligibility, PositionHistoryWrite, AnimationPublication,
            ObjectTouchResult, ObjectTouchResponse, TouchScanCompleted, SoundRequest,
            AirCollisionEffect, KillPlayerCall { }
    public record ActorWrite(ActorField field, int value) implements Effect {
        public ActorWrite { Objects.requireNonNull(field); }
    }
    public record GlobalWrite(GlobalField field, int value) implements Effect {
        public GlobalWrite { Objects.requireNonNull(field); }
    }
    public record ChargeEffect(CdPlayerCharge.Effect effect) implements Effect {
        public ChargeEffect { Objects.requireNonNull(effect); }
    }
    public record ModeEffect(CdPlayerMovement.Effect effect) implements Effect {
        public ModeEffect { Objects.requireNonNull(effect); }
    }
    /** Ordered call observation, not an instruction to invoke the owner a second time. */
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    /**
     * Exact DrawObject call cut: Main's Display call before history, Animate and SpecialChunks,
     * or Hurt's unconditional tail JMP after Animate (Main.asm:2366), there always eligible. The queue
     * owner uses this radius/position/raw width with the pre-scroll camera and
     * the raw priority byte for queue selection. It owns both
     * DrawObject's entry bit7 clear and DrawObjects' later bit7 set. Body leaves
     * that bit as ingress; no admitted later body owner reads its own bit7.
     * The final DisplayPublication remains the completed pose for the queued pointer.
     */
    public record DisplayEligibility(boolean eligible, int centreXWord, int centreYWord,
                                     int yRadius, int spriteFlags, int width, int priority) implements Effect {
        public DisplayEligibility {
            wordValue(centreXWord); wordValue(centreYWord); byteValue(yRadius); byteValue(spriteFlags);
            byteValue(width); byteValue(priority);
        }
    }
    public record PositionHistoryWrite(int byteOffset, int centreXWord, int centreYWord, int nextIndex) implements Effect { }
    /** Actual animator result/effects, already incorporated once into State. No art service is requested. */
    public record AnimationPublication(CdPlayerAnimation.State state, CdPlayerAnimation.Effects effects) implements Effect {
        public AnimationPublication { Objects.requireNonNull(state); Objects.requireNonNull(effects); }
    }
    /**
     * Existing immutable scanner result, including at most one detached item
     * image. The external pool owner may commit that image only after the WHOLE
     * Body call succeeds; this is no Ring reward, sound or instruction replay.
     */
    public record ObjectTouchResult(CdPlayerObjectTouch.Result result) implements Effect {
        public ObjectTouchResult { Objects.requireNonNull(result); }
    }
    /**
     * The complete pure selected ENEMY/HAZARD response. Its final actor, invincible and scoreChain
     * values are already adopted ONCE into this Body result. The Runtime commits only changedSlots
     * (compare-and-set against the same call-cut snapshot), world and contact, and only after the
     * WHOLE Body succeeds. Calls are ordered observations, never write-replay instructions.
     */
    public record ObjectTouchResponse(CdPlayerTouchResponse.Result result) implements Effect {
        public ObjectTouchResponse { Objects.requireNonNull(result); }
        /** Final Runtime-owned contact bytes after the handler. */
        public ContactGlobals contact() {
            var p = result.player();
            return new ContactGlobals(p.timeWarp(), p.shield(), p.combineRing());
        }
    }
    public record TouchScanCompleted(int slotsVisited) implements Effect { }
    public record SoundRequest(Sound sound) implements Effect {
        public SoundRequest { Objects.requireNonNull(sound); }
    }
    /**
     * One ordered helper effect of Hurt's Player_LevelColInAir call (Main.asm:2378). Not MODE:
     * the collision owner's final actor/scratch values are already adopted once into this Body result.
     */
    public record AirCollisionEffect(CdPlayerMovement.Effect effect) implements Effect {
        public AirCollisionEffect { Objects.requireNonNull(effect); }
    }
    /**
     * One executed KillPlayer call (PP Player Object Collision.asm:279-298) at its native position,
     * through the shared pure owner. Its actor, invincible and scoreChain values are already adopted
     * ONCE; calls are ordered observations, never write-replay instructions. No world, contact or pool
     * value exists here. A debug no-write exit is still a call that returned into Hurt.
     */
    public record KillPlayerCall(CdPlayerTouchResponse.KillResult result) implements Effect {
        public KillPlayerCall { Objects.requireNonNull(result); }
    }

    /**
     * Main's Display queues the actor before Animate and Hurt's DrawObject queues it after; the later
     * SAT owner observes the completed pose. This publication therefore combines that eligibility
     * with final body fields. Reading it never advances any owner. queued means
     * eligible for queueing, not evidence that native DrawObject/SAT ran.
     */
    public record DisplayPublication(boolean queued, int mappingAddress, int tile, int priority,
                                     int width, int mapFrame, int spriteFlags,
                                     int centreX16_16, int centreY16_16) { }
    public record Result(State state, Environment environment, List<Effect> effects,
                         Optional<DisplayPublication> display, Boundary boundary,
                         Optional<CdPlayerMovement.Boundary> modeBoundary) {
        public Result {
            Objects.requireNonNull(state); Objects.requireNonNull(environment); effects = List.copyOf(effects);
            Objects.requireNonNull(display); Objects.requireNonNull(boundary); Objects.requireNonNull(modeBoundary);
            boolean drawn = boundary == Boundary.MAIN_RETURN || boundary == Boundary.HURT_RETURN;
            if (drawn != display.isPresent()
                    || (boundary == Boundary.MAIN_RETURN) != modeBoundary.isPresent()
                    || modeBoundary.orElse(null) == CdPlayerMovement.Boundary.KILL_PLAYER
                    || (boundary == Boundary.HURT_RETURN && !display.orElseThrow().queued())) {
                throw new IllegalArgumentException(
                        "Only a completed Main or drawn Hurt has display; only Main has completed MODE publication");
            }
        }
    }

    public enum UnsupportedBoundary {
        SLOT_ID, SOURCE_FAMILY, DEBUG_UPDATE, ROUTINE_DISPATCH, DEAD_ROUTINE, RESTART_ROUTINE,
        MINIATURE_INIT, MAPPING_BINDING, WATERFALL_SPLASH_CREATION, DEBUG_ENTRY, OBJECT_CONTROL,
        KILL_PLAYER, HISTORY_ADDRESS, TOUCH_SCAN, ROLL_TUNNEL_OWNER, STANDING_OBJECT_MOVE
    }
    /** No staged state or effects escape. Kill names the unexecuted external call's exact MODE continuation. */
    public static final class UnsupportedBody extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final CdPlayerMovement.Continuation continuation;
        private UnsupportedBody(UnsupportedBoundary boundary, CdPlayerMovement.Continuation continuation) {
            super(boundary.name() + (continuation == CdPlayerMovement.Continuation.NONE ? "" : ": " + continuation));
            this.boundary = boundary; this.continuation = continuation;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public CdPlayerMovement.Continuation continuation() { return continuation; }
    }

    /**
     * Selected native handler without an owner here (MONITOR/SPECIAL, or ENEMY/HAZARD without a
     * ResponseContext); no staged Body result escapes.
     */
    public static final class UnsupportedTouchHandler extends UnsupportedOperationException {
        private final CdPlayerObjectTouch.Kind kind;
        private final CdObjectState selectedSlot;
        private UnsupportedTouchHandler(CdPlayerObjectTouch.HandlerBoundary selected) {
            super("Unsupported touch handler " + selected.kind() + " at whole slot "
                    + selected.selectedSlot().orElseThrow().identity().wholeIndex());
            kind = selected.kind(); selectedSlot = selected.selectedSlot().orElseThrow();
        }
        public CdPlayerObjectTouch.Kind kind() { return kind; }
        public CdObjectState selectedSlot() { return selectedSlot; }
    }

    private final CdR11aAssets assets;
    private final CdR11aTerrain terrain;
    private final CdSonicVisualAssets sonic;
    private final CdPlayerMovement movement;
    private final CdPlayerAnimation animator;
    private final int mappingAddress;

    /** Production binding retains the supplied aggregate. Authentication remains the asset loader's responsibility. */
    public CdPlayerBody(CdR11aAssets assets) {
        this.assets = Objects.requireNonNull(assets);
        terrain = Objects.requireNonNull(assets.terrain()); sonic = Objects.requireNonNull(assets.sonic());
        var player = Objects.requireNonNull(assets.player());
        if (player.sonic() != sonic) throw new IllegalArgumentException("Player and Sonic mapping owners differ");
        mappingAddress = Objects.requireNonNull(assets.profile()).range(CdR11aAsset.MAP_SPR_SONIC).nativeAddress();
        movement = new CdPlayerMovement(terrain, player); animator = new CdPlayerAnimation(player);
    }

    /** ObjSonic (56-105). No slot means no header, charge or routine work. */
    public Result step(State state, Environment environment) {
        return step(state, environment, Optional.empty());
    }

    /**
     * Explicit per-call touch input overrides only DynamicSlots fallback.
     * Neither scanner, snapshot nor response owner is sampled unless Main reaches its actual
     * post-Animate, pre-SpecialChunks call with both native touch gates open.
     */
    public Result step(State state, Environment environment, Optional<TouchContext> touchContext) {
        Objects.requireNonNull(state); Objects.requireNonNull(environment); Objects.requireNonNull(touchContext);
        if (state.header().slotId() == 0) {
            return new Result(state, environment, List.of(), Optional.empty(), Boundary.SLOT_ABSENT, Optional.empty());
        }
        if (state.header().slotId() != 1) unsupported(UnsupportedBoundary.SLOT_ID);
        var gates = environment.movement().gates();
        if (gates.zoneAct() != 0 || gates.timeZone() != 1) unsupported(UnsupportedBoundary.SOURCE_FAMILY);
        // Retail main-player branch (56-73): time attack bypasses this debug dispatch test.
        if (environment.globals().timeAttackMode() == 0 && (environment.globals().debugMode() >>> 8) != 0) {
            unsupported(UnsupportedBoundary.DEBUG_UPDATE);
        }
        Cursor c = new Cursor(state, environment);
        c.applyCharge(CdPlayerCharge.beforeRoutineDispatch(c.chargeState())); // EXACTLY ONCE, using prior rolling bit.
        c.returned(NativeCall.CHARGE_BEFORE_DISPATCH);
        switch (c.motion.routine()) {
            case 0 -> { initialize(c); return c.finish(Boundary.INIT_RETURN); }
            case 2 -> { main(c, touchContext); return c.finish(Boundary.MAIN_RETURN); }
            case 4 -> { hurt(c); return c.finish(Boundary.HURT_RETURN); } // Never samples touchContext.
            case 6 -> unsupported(UnsupportedBoundary.DEAD_ROUTINE);
            case 8 -> unsupported(UnsupportedBoundary.RESTART_ROUTINE);
            default -> unsupported(UnsupportedBoundary.ROUTINE_DISPATCH);
        }
        throw new AssertionError("Unsupported routine must throw");
    }

    /** ObjSonic_Init (135-155). No fallthrough to Main, animation, history or display. */
    private void initialize(Cursor c) {
        c.setRoutine(2); c.setRadii(9, 19);
        if (c.env.movement().gates().miniSonic() != 0) unsupported(UnsupportedBoundary.MINIATURE_INIT);
        c.header = new Header(c.header.slotId(), mappingAddress, 0x780, 2, 0x18);
        c.actor(ActorField.MAPPING_ADDRESS, mappingAddress); c.actor(ActorField.TILE, 0x780);
        c.actor(ActorField.PRIORITY, 2); c.actor(ActorField.WIDTH, 0x18); c.setSpriteFlags(4);
        c.normalPhysics(); c.returned(NativeCall.INIT);
    }

    /** ObjSonic_Main (285-351), literal call and branch order. */
    private void main(Cursor c, Optional<TouchContext> touchContext) {
        requireBoundMapping(c);
        extendedCamera(c); waterfall(c);
        if (c.debugCheat != 0 && (c.controls.rawTap() & 0x10) != 0) unsupported(UnsupportedBoundary.DEBUG_ENTRY);
        if (c.controls.controlLocked() == 0) {
            c.controls = new Controls(c.controls.rawHeld(), c.controls.rawTap(), c.controls.rawHeld(),
                    c.controls.rawTap(), c.controls.controlLocked());
            c.global(GlobalField.LOGICAL_HELD, c.controls.logicalHeld());
            c.global(GlobalField.LOGICAL_TAP, c.controls.logicalTap());
        }
        // This branch calls TimeWarp (or MMZ clears) before .SkipControl. Neither object-control owner is admitted.
        if ((c.motion.playerControl() & 1) != 0) unsupported(UnsupportedBoundary.OBJECT_CONTROL);
        var moved = movement.step(c.motion, new CdPlayerMovement.Input(c.controls.logicalHeld(), c.controls.logicalTap()), c.env.movement());
        if (moved.boundary() == CdPlayerMovement.Boundary.KILL_PLAYER) {
            throw new UnsupportedBody(UnsupportedBoundary.KILL_PLAYER, moved.continuation());
        }
        c.motion = moved.state(); c.setMovementEnvironment(moved.environment());
        for (var effect : moved.effects()) c.effects.add(new ModeEffect(effect));
        c.modeBoundary = Optional.of(moved.boundary()); c.returned(NativeCall.MODE);
        // PP's actual Player Special Collision.asm:7-8 is RTS, not a shared collision fallback.
        c.returned(NativeCall.PP_SPECIAL_COLLISION);
        display(c); recordPosition(c);
        // ObjSonic_Water (462-467): zone0 returns before any water write or service.
        c.returned(NativeCall.PP_WATER);
        var g = c.env.movement().globals();
        c.copyAngles(g.primaryAngle(), g.secondaryAngle()); // Main:330-331, AFTER MODE and water.
        if (c.env.movement().gates().windTunnel() != 0 && c.motion.animation() == 0) {
            c.setAnimation(c.motion.previousAnimation()); // Main:333-337, not an extra animation tick.
        }
        animate(c);
        if ((byte) c.motion.playerControl() >= 0 && c.motion.animation() != 0x2B) {
            touch(c, touchContext);
        }
        specialChunks(c);
    }

    /** Player_ObjCollide (PP Player Object Collision.asm:7-98), exactly Main:347. */
    private static void touch(Cursor c, Optional<TouchContext> touchContext) {
        if (touchContext.isEmpty()) {
            if (c.env.dynamicSlots() != DynamicSlots.EMPTY_DEVELOPMENT_96) unsupported(UnsupportedBoundary.TOUCH_SCAN);
            // Existing deliberate development fallback: 96 inactive slot flag visits.
            c.effects.add(new TouchScanCompleted(CdObjectPool.DYNAMIC_COUNT));
            return;
        }
        var input = touchContext.orElseThrow();
        var selected = input.scanner().scan(new CdPlayerObjectTouch.PlayerCut(c.x(), c.y(),
                c.motion.yRadius(), c.animation.mapFrame(), c.hurt), input.snapshot());
        if (selected instanceof CdPlayerObjectTouch.HandlerBoundary handler) {
            var kind = handler.kind();
            if (input.response().isEmpty()
                    || (kind != CdPlayerObjectTouch.Kind.ENEMY && kind != CdPlayerObjectTouch.Kind.HAZARD)) {
                throw new UnsupportedTouchHandler(handler);
            }
            respond(c, handler, input.response().orElseThrow(), input.snapshot());
        } else {
            c.effects.add(new ObjectTouchResult(selected));
        }
        // The scanner's first overlap returns in the immutable 32..127 physical
        // sequence. Its selected identity gives actual visits without a rescan.
        int visits = selected.selectedSlot().map(slot -> slot.identity().wholeIndex()
                - CdObjectPool.FIRST_WHOLE_INDEX + 1).orElse(CdObjectPool.DYNAMIC_COUNT);
        c.effects.add(new TouchScanCompleted(visits));
    }

    /**
     * Player_TouchEnemy/Player_TouchHazard (PP Player Object Collision.asm:130-298) through the pure
     * owner at this exact cut: post-Animate motion, post-Display hurt word and byte39, with the same
     * selection and snapshot. Final actor, invincible and scoreChain values are adopted once here,
     * before SpecialChunks reads them (Main:347-350). Slot/world/contact results stay in the effect.
     */
    private static void respond(Cursor c, CdPlayerObjectTouch.HandlerBoundary selected, ResponseContext owner,
                                CdObjectPool.Snapshot snapshot) {
        var movement = c.env.movement(); var contact = owner.contact();
        var player = new CdPlayerTouchResponse.PlayerGlobals(contact.timeWarp(), c.invincible, contact.shield(),
                contact.combineRing(), c.debugCheat, c.debugMode, movement.globals().scoreChain(), movement.gates().miniSonic());
        var result = owner.response().respond(selected, c.responseActor(), player, owner.world(), snapshot);
        c.adopt(result.actor());
        c.invincible = result.player().invincible();
        c.setScoreChain(result.player().scoreChain());
        c.effects.add(new ObjectTouchResponse(result));
    }

    /**
     * ObjSonic_Hurt (2351-2366), literal call order. There is no Display (no hurt, invincibility or
     * shoes servicing), ExtCamera, input copy, MODE, water, angle copy, touch or SpecialChunks call.
     * Each KillPlayer returns into this routine, so its later calls still run; DrawObject is unconditional.
     */
    private void hurt(Cursor c) {
        requireBoundMapping(c);
        objMove(c);
        // ADDI.W #$30 then underwater SUBI.W #$20 (2354-2357): two native word writes, no ObjMoveGrv fall cap.
        c.setYVelocity((short) (c.motion.yVelocity() + 0x30));
        if ((c.motion.flags() & 0x40) != 0) c.setYVelocity((short) (c.motion.yVelocity() - 0x20));
        // PP's actual Player Special Collision.asm:7-8 is RTS, not a shared collision fallback.
        c.returned(NativeCall.PP_SPECIAL_COLLISION);
        hurtCheckLand(c);
        levelBound(c);
        recordPosition(c);
        animate(c);
        drawObject(c);
    }

    /** ObjMove (Object Functions.asm:91-128): both longs add the OLD velocity words shifted by 8; no gravity. */
    private static void objMove(Cursor c) {
        // :97-114 reads oPlayerStandObj's slot oID for the CCZ flipper; that object belongs to the pool owner.
        if ((c.motion.flags() & 8) != 0) unsupported(UnsupportedBoundary.STANDING_OBJECT_MOVE);
        var m = c.motion;
        c.setCentre(m.centreX16_16() + (m.xVelocity() << 8), m.centreY16_16() + (m.yVelocity() << 8));
        c.returned(NativeCall.OBJ_MOVE);
    }

    /**
     * ObjSonic_HurtChkLand (2372-2391). Its UNSIGNED CMP.W/BCS.W branches into KillPlayer, whose RTS
     * returns from this subroutine into Hurt: LevelColInAir and the landing writes are then skipped.
     */
    private void hurtCheckLand(Cursor c) {
        if (uw(c.env.movement().bounds().bottom() + 224) < c.y()) {
            killPlayer(c);
        } else {
            var collided = movement.collideInAir(c.motion, c.env.movement()); // BSR.W Player_LevelColInAir (2378).
            c.motion = collided.state(); c.setMovementEnvironment(collided.environment());
            for (var effect : collided.effects()) c.effects.add(new AirCollisionEffect(effect));
            c.returned(NativeCall.LEVEL_COLLISION_IN_AIR);
            if ((c.motion.flags() & 2) == 0) {
                // 2382-2388: MOVEQ #0 to Y/X/ground words, oAnim 0, SUBQ.B #2,oRoutine, then oPlayerHurt 120.
                c.stopMotion(); c.setAnimation(0); c.setRoutine((c.motion.routine() - 2) & 0xFF);
                c.hurt = 120; c.actor(ActorField.HURT_TIMER, 120);
            }
        }
        c.returned(NativeCall.HURT_CHECK_LAND);
    }

    /**
     * ObjSonic_LevelBound (1673-1714) through the shared port: side clamp, then its SIGNED bottom test.
     * BNE.W KillPlayer returns into Hurt; the port itself stops at the unowned bored-death branch.
     */
    private static void levelBound(Cursor c) {
        var bounded = CdPlayerMovement.applyLevelBounds(c.motion, c.env.movement());
        c.motion = bounded.state(); c.setMovementEnvironment(bounded.environment());
        if (bounded.killRequested()) killPlayer(c);
        c.returned(NativeCall.LEVEL_BOUND);
    }

    /**
     * KillPlayer (PP Player Object Collision.asm:279-298) through the shared pure owner, never a copy.
     * debugMode is the complete word (:280). Adopted once at this call; the next call reads its result.
     */
    private static void killPlayer(Cursor c) {
        var e = c.env.movement();
        var result = CdPlayerTouchResponse.killPlayer(c.responseActor(), c.invincible, c.debugMode,
                e.globals().scoreChain(), e.gates().miniSonic());
        c.adopt(result.actor()); c.invincible = result.invincible(); c.setScoreChain(result.scoreChain());
        c.effects.add(new KillPlayerCall(result)); c.returned(NativeCall.KILL_PLAYER);
    }

    /** Hurt's tail JMP DrawObject (2366): unconditional, at the completed post-Animate pose. */
    private static void drawObject(Cursor c) {
        c.displayEligible = true;
        c.effects.add(new DisplayEligibility(true, c.x(), c.y(), c.motion.yRadius(), c.motion.spriteFlags(),
                c.header.width(), c.header.priority()));
        c.returned(NativeCall.DRAW_OBJECT);
    }

    /** Main and Hurt both draw through the actual source-bound mapping; Init alone writes it. */
    private void requireBoundMapping(Cursor c) {
        if (c.header.mappingAddress() != mappingAddress) unsupported(UnsupportedBoundary.MAPPING_BINDING);
    }

    /** ObjSonic_ExtCamera (231-279), incoming ground word and unsigned CMP/BCC including wrapped NEG.W. */
    private static void extendedCamera(Cursor c) {
        int speed = c.motion.groundVelocity(); int magnitude = speed < 0 ? uw(-speed) : speed;
        int centre = c.camX;
        boolean reset = (c.motion.playerControl() & 2) != 0 && c.x() < 0x1B00;
        if (reset || magnitude < 0x600) {
            if (centre != 160) centre = uw(centre + (centre < 160 ? 2 : -2));
        } else if (speed < 0) {
            centre = uw(centre + 2); if (centre >= 224) centre = 224;
        } else {
            centre = uw(centre - 2); if (centre < 96) centre = 96;
        }
        c.camX = centre; c.global(GlobalField.CAMERA_X_CENTRE, centre); c.returned(NativeCall.EXTENDED_CAMERA);
    }

    /** MakeWaterfallSplash/GetChunkAtPos (161-224). Only this helper masks the returned raw byte to $7F. */
    private void waterfall(Cursor c) {
        if ((c.levelFrames & 1) == 0) {
            int y = uw(c.y() + (byte) c.motion.yRadius());
            int chunk = rawChunk(c.x(), y) & 0x7F;
            if (chunk == 0x2F && c.x() < 0x15C0 && c.motion.playerControl() != 0) {
                // At the actual FindObjSlot/create call, not an invented coordinate admission fence.
                unsupported(UnsupportedBoundary.WATERFALL_SPLASH_CREATION);
            }
        }
        c.returned(NativeCall.WATERFALL_SPLASH_CHECK);
    }

    /** ObjSonic_Display (373-441). Eligibility uses old hurt bit2, then invincibility, then shoes. */
    private static void display(Cursor c) {
        boolean eligible = false;
        if (c.timeWarpTimer < 210) {
            int oldHurt = c.hurt;
            if (oldHurt != 0) { c.hurt = uw(oldHurt - 1); c.actor(ActorField.HURT_TIMER, c.hurt); }
            eligible = (oldHurt == 0 || (oldHurt & 4) != 0) && (c.motion.playerControl() & 0x40) == 0;
        }
        c.displayEligible = eligible;
        c.effects.add(new DisplayEligibility(eligible, c.x(), c.y(), c.motion.yRadius(), c.motion.spriteFlags(),
                c.header.width(), c.header.priority()));
        if (c.invincible != 0 && c.invincibilityTimer != 0) {
            c.invincibilityTimer = uw(c.invincibilityTimer - 1); c.actor(ActorField.INVINCIBILITY_TIMER, c.invincibilityTimer);
            if (c.invincibilityTimer == 0) {
                if (c.env.movement().gates().speedShoes() == 0 && c.bossMusic == 0) c.sound(Sound.SCMD_R1AMUS);
                c.invincible = 0; c.global(GlobalField.INVINCIBLE, 0);
            }
        }
        if (c.env.movement().gates().speedShoes() != 0 && c.shoesTimer != 0) {
            c.shoesTimer = uw(c.shoesTimer - 1); c.actor(ActorField.SHOES_TIMER, c.shoesTimer);
            if (c.shoesTimer == 0) {
                c.normalPhysics();
                if (c.invincible == 0 && c.bossMusic == 0) c.sound(Sound.SCMD_R1AMUS);
                c.setSpeedShoes(0); c.global(GlobalField.SPEED_SHOES, 0);
            }
        }
        c.returned(NativeCall.DISPLAY);
    }

    /** ObjSonic_RecordPos (447-456). Current word index addresses XY, ADDQ.B advances only its low byte. */
    private static void recordPosition(Cursor c) {
        int offset = c.history.index();
        if (offset > 0xFC || (offset & 1) != 0) unsupported(UnsupportedBoundary.HISTORY_ADDRESS);
        byte[] bytes = c.history.bytes().copy();
        writeWord(bytes, offset, c.x()); writeWord(bytes, offset + 2, c.y());
        int next = (offset & 0xFF00) | ((offset + 4) & 0xFF);
        c.history = new History(CdAssetBytes.copyOf(bytes), next);
        c.effects.add(new PositionHistoryWrite(offset, c.x(), c.y(), next)); c.returned(NativeCall.RECORD_POSITION);
    }

    private void animate(Cursor c) {
        var m = c.motion; var a = c.animation;
        var animated = animator.step(new CdPlayerAnimation.State(m.previousAnimation(), a.scriptIndex(), a.timer(), a.mapFrame()),
                new CdPlayerAnimation.Input(m.animation(), m.groundVelocity(), m.angle(), m.flags(), m.spriteFlags(),
                        m.playerControl(), c.env.movement().gates().miniSonic()));
        var result = animated.state(); var effects = animated.effects();
        int flags = m.flags(), xr = m.xRadius(), yr = m.yRadius();
        if (effects.miniRollHitboxWrite()) { flags |= 4; xr = 5; yr = 10; }
        c.motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), flags, effects.spriteFlagsWrite().orElse(m.spriteFlags()), xr, yr,
                effects.animationWrite().orElse(m.animation()), result.previousAnimation(), m.playerJump(), m.charge(),
                m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        c.animation = new AnimationCursor(result.scriptIndex(), result.timer(), result.mapFrame());
        c.effects.add(new AnimationPublication(result, effects)); c.returned(NativeCall.ANIMATE);
        // No cue observation/service here. mapFrame is simply the next eligible V-int's input.
    }

    /** ObjSonic_SpecialChunks (2500-2603), R11A PP branch; compare RAW bytes, X byte is big-endian oX high. */
    private void specialChunks(Cursor c) {
        int raw = rawChunk(c.x(), c.y()); var ids = assets.player().specialChunks();
        boolean tunnel = raw == ids.unsignedByte(2) ? (c.y() & 0xFF) >= 0x90 : raw == ids.unsignedByte(3);
        if (tunnel) {
            // Reuse StartRoll only where its admitted contract matches the source call.
            if ((c.motion.flags() & 2) != 0 || (c.motion.playerControl() & 3) != 0
                    || c.env.movement().gates().miniSonic() != 0) unsupported(UnsupportedBoundary.ROLL_TUNNEL_OWNER);
            if (assets.profile().variant() != CdReleaseVariant.USA && (c.motion.flags() & 4) == 0) c.sound(Sound.FM_9C);
            c.applyCharge(CdPlayerCharge.startRoll(c.chargeState(), new CdPlayerCharge.Body(c.motion.routine(),
                    c.motion.playerControl(), c.env.movement().gates().miniSonic())));
        } else if (raw == ids.unsignedByte(0)) {
            loopPath(c);
        } else if (raw == ids.unsignedByte(1)) {
            if ((c.motion.flags() & 2) != 0) c.setSpriteFlags(c.motion.spriteFlags() & ~0x40);
            else loopPath(c);
        } else {
            c.setSpriteFlags(c.motion.spriteFlags() & ~0x40); // Actual .CheckIfLoop nonmatch, even all-$7F R11A sentinels.
        }
        c.returned(NativeCall.SPECIAL_CHUNKS);
    }

    /** .CheckIfLeft/.CheckIfRight/.CheckAngle/.HighPath (2558-2597), native unsigned byte comparisons. */
    private static void loopPath(Cursor c) {
        int x = c.x() & 0xFF, spr = c.motion.spriteFlags(), angle = c.motion.angle();
        if (x < 0x2C) c.setSpriteFlags(spr & ~0x40);
        else if (x >= 0xE0) c.setSpriteFlags(spr | 0x40);
        else if ((spr & 0x40) == 0) {
            if (angle != 0 && angle <= 0x80) c.setSpriteFlags(spr | 0x40);
        } else if (angle > 0x80) c.setSpriteFlags(spr & ~0x40);
    }

    private int rawChunk(int x, int y) {
        return terrain.layout().ram().unsignedByte(((y >>> 1) & 0x380) + ((x >>> 8) & 0x7F));
    }

    /** Detached per-call staging. Never retained by this owner or exposed on failure. */
    private static final class Cursor {
        CdPlayerMotionState motion;
        AnimationCursor animation;
        Header header;
        History history;
        Environment env;
        Controls controls;
        final int levelFrames, timeAttack, debugMode, debugCheat, timeWarpTimer, bossMusic;
        int camX, invincible, hurt, invincibilityTimer, shoesTimer, resetTimer, rotationDistance;
        boolean displayEligible;
        Optional<CdPlayerMovement.Boundary> modeBoundary = Optional.empty();
        final List<Effect> effects = new ArrayList<>();
        Cursor(State state, Environment environment) {
            motion = state.motion(); animation = state.animation(); header = state.header(); history = state.history();
            rotationDistance = state.rotationDistance();
            env = environment; controls = environment.controls(); var g = environment.globals();
            levelFrames = g.levelFrames(); camX = g.cameraXCentre(); timeAttack = g.timeAttackMode();
            debugMode = g.debugMode(); debugCheat = g.debugCheat(); timeWarpTimer = g.timeWarpTimer();
            invincible = g.invincible(); bossMusic = g.bossMusic();
            hurt = state.timers().hurt(); invincibilityTimer = state.timers().invincibility(); shoesTimer = state.timers().speedShoes();
            resetTimer = state.timers().reset();
        }
        int x() { return motion.centreX16_16() >>> 16; }
        int y() { return motion.centreY16_16() >>> 16; }
        void actor(ActorField field, int value) { effects.add(new ActorWrite(field, value)); }
        void global(GlobalField field, int value) { effects.add(new GlobalWrite(field, value)); }
        void returned(NativeCall call) { effects.add(new CallReturned(call)); }
        void sound(Sound sound) { effects.add(new SoundRequest(sound)); }
        void setMovementEnvironment(CdPlayerMovement.Environment movement) {
            env = new Environment(movement, controls, env.globals(), env.dynamicSlots());
        }
        void normalPhysics() {
            var e = env.movement();
            setMovementEnvironment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600, 12, 0x80), e.bounds(), e.gates(), e.globals()));
            global(GlobalField.TOP_SPEED, 0x600); global(GlobalField.ACCELERATION, 12); global(GlobalField.DECELERATION, 0x80);
        }
        void setScoreChain(int value) {
            var e = env.movement(); var g = e.globals();
            setMovementEnvironment(new CdPlayerMovement.Environment(e.physics(), e.bounds(), e.gates(),
                    new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(),
                            g.boredTimer(), value, g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())));
        }
        void setSpeedShoes(int value) {
            var e = env.movement(); var g = e.gates();
            setMovementEnvironment(new CdPlayerMovement.Environment(e.physics(), e.bounds(), new CdPlayerMovement.Gates(g.zoneAct(),
                    g.timeZone(), g.miniSonic(), g.waterSlide(), g.windTunnel(), value, g.timeWarpDirection()), e.globals()));
        }
        CdPlayerCharge.State chargeState() {
            var m = motion; var g = env.movement().globals();
            return new CdPlayerCharge.State(m.charge(), m.groundVelocity(), m.xVelocity(), m.yVelocity(), m.flags(),
                    m.xRadius(), m.yRadius(), m.centreY16_16(), m.animation(), g.lookMode(), g.cameraYCentre());
        }
        void applyCharge(CdPlayerCharge.Result result) {
            var s = result.state(); var m = motion;
            motion = new CdPlayerMotionState(m.centreX16_16(), s.centreY16_16(), s.storedXVelocity(), s.storedYVelocity(), s.groundVelocity(),
                    m.routine(), m.angle(), s.flags(), m.spriteFlags(), s.xRadius(), s.yRadius(), s.animation(), m.previousAnimation(),
                    m.playerJump(), s.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            var e = env.movement(); var g = e.globals();
            setMovementEnvironment(new CdPlayerMovement.Environment(e.physics(), e.bounds(), e.gates(),
                    new CdPlayerMovement.Globals(s.lookMode(), s.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(),
                            g.boredTimer(), g.scoreChain(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance())));
            for (var effect : result.effects()) effects.add(new ChargeEffect(effect));
        }
        void setRoutine(int value) {
            var m = motion;
            motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                    value, m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                    m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            actor(ActorField.ROUTINE, value);
        }
        void setRadii(int x, int y) {
            var m = motion;
            motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                    m.routine(), m.angle(), m.flags(), m.spriteFlags(), x, y, m.animation(), m.previousAnimation(),
                    m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            actor(ActorField.Y_RADIUS, y); actor(ActorField.X_RADIUS, x); // Init writes Y before X.
        }
        void setSpriteFlags(int value) {
            var m = motion;
            motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                    m.routine(), m.angle(), m.flags(), value, m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                    m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            actor(ActorField.SPRITE_FLAGS, value);
        }
        void copyAngles(int primary, int secondary) {
            var m = motion;
            motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                    m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                    m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), primary, secondary);
            actor(ActorField.PRIMARY_ANGLE_COPY, primary); actor(ActorField.SECONDARY_ANGLE_COPY, secondary);
        }
        void setAnimation(int value) {
            var m = motion;
            motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                    m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), value, m.previousAnimation(),
                    m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            actor(ActorField.ANIMATION, value);
        }
        /** Replaces the two centre longs and three speed words; the record validates native widths. */
        void setMotion(int x, int y, int xVelocity, int yVelocity, int groundVelocity) {
            var m = motion;
            motion = new CdPlayerMotionState(x, y, xVelocity, yVelocity, groundVelocity,
                    m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                    m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                    m.previousPrimaryAngle(), m.previousSecondaryAngle());
        }
        /** ObjMove's MOVE.L d2,oX then MOVE.L d3,oY. */
        void setCentre(int x, int y) {
            var m = motion; setMotion(x, y, m.xVelocity(), m.yVelocity(), m.groundVelocity());
            actor(ActorField.CENTRE_X_LONG, x); actor(ActorField.CENTRE_Y_LONG, y);
        }
        void setYVelocity(int value) {
            var m = motion; setMotion(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), value, m.groundVelocity());
            actor(ActorField.Y_VELOCITY, value);
        }
        /** HurtChkLand's MOVE.W d0 to oYVel, oXVel, then oPlayerGVel. */
        void stopMotion() {
            var m = motion; setMotion(m.centreX16_16(), m.centreY16_16(), 0, 0, 0);
            actor(ActorField.Y_VELOCITY, 0); actor(ActorField.X_VELOCITY, 0); actor(ActorField.GROUND_VELOCITY, 0);
        }
        /** Contact/KillPlayer call-cut projection: oTile/oPriority, the post-Display hurt word and byte39. */
        CdPlayerTouchResponse.Actor responseActor() {
            return new CdPlayerTouchResponse.Actor(motion, header.tile(), header.priority(), hurt, rotationDistance);
        }
        /** Adopts one pure response actor once; slot ID, mapping and width are not response outputs. */
        void adopt(CdPlayerTouchResponse.Actor actor) {
            motion = actor.motion();
            header = new Header(header.slotId(), header.mappingAddress(), actor.tile(), actor.priority(), header.width());
            hurt = actor.hurt(); rotationDistance = actor.rotationDistance();
        }
        Result finish(Boundary boundary) {
            var state = new State(motion, animation, header, new Timers(hurt, invincibilityTimer, shoesTimer, resetTimer),
                    history, rotationDistance);
            var globals = new Globals(levelFrames, camX, timeAttack, debugMode, debugCheat, timeWarpTimer, invincible, bossMusic);
            var environment = new Environment(env.movement(), controls, globals, env.dynamicSlots());
            Optional<DisplayPublication> display = boundary == Boundary.MAIN_RETURN || boundary == Boundary.HURT_RETURN
                    ? Optional.of(new DisplayPublication(displayEligible, header.mappingAddress(), header.tile(), header.priority(),
                    header.width(), animation.mapFrame(), motion.spriteFlags(), motion.centreX16_16(), motion.centreY16_16())) : Optional.empty();
            return new Result(state, environment, effects, display, boundary, modeBoundary);
        }
    }

    private static void unsupported(UnsupportedBoundary boundary) {
        throw new UnsupportedBody(boundary, CdPlayerMovement.Continuation.NONE);
    }
    private static void writeWord(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8); bytes[offset + 1] = (byte) value;
    }
    private static int uw(int value) { return value & 0xFFFF; }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
