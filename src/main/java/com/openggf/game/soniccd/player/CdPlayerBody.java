package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdRingGlobals;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Source-bound PPZ1 ObjSonic dispatch/Init/Main/Hurt/Dead/Restart owner.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/_Objects/Sonic/Main.asm:56-106,135-456,2351-2495,2500-2603, plus
 * src/Level/Object Functions.asm:42-82 (ObjMoveGrv), 91-128 (ObjMove) and 137 (DrawObject), and
 * src/Level/Object Spawner.asm:329-340 (FindObjSlot over the call-cut capture).
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
     * Raw player object bytes oPlayerPushObj ($20) and oPlayerStandObj ($3D),
     * Level/_Definitions.inc:92-93. These are whole-slot indices, not object IDs.
     * All native byte values are ingress; SolidObject owns address admission at an actual dereference.
     * LevelStart .ClearObjects (Level/Main.asm:186-192) initializes them; ObjSonic never resets them.
     */
    public record ObjectLinks(int pushObject, int standObject) {
        public ObjectLinks { byteValue(pushObject); byteValue(standObject); }
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
     * rotationAngle is the independent native byte2B (oPlayerRotAngle, Level/_Definitions.inc:88).
     * ObjSonic does not write it; Disc owns its stores, and a new LevelStart .ClearObjects owns its clear.
     * There is no implicit zero/default overload: the actor ingress/capture supplies both bytes.
     */
    public record State(CdPlayerMotionState motion, AnimationCursor animation, Header header,
                        Timers timers, History history, int rotationDistance, ObjectLinks objectLinks, int rotationAngle) {
        public State {
            Objects.requireNonNull(motion); Objects.requireNonNull(animation); Objects.requireNonNull(header);
            Objects.requireNonNull(timers); Objects.requireNonNull(history); byteValue(rotationDistance);
            Objects.requireNonNull(objectLinks, "player object links"); byteValue(rotationAngle);
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
     * Runtime-owned native bytes at the selected enemy/hazard/monitor or owned SPECIAL flag handler:
     * timeWarp ($FF1521), shield ($FF151E) and combineRing ($FF156F). Ephemeral call-cut
     * input only: never part of State, Environment or a Frame capture.
     */
    public record ContactGlobals(int timeWarp, int shield, int combineRing) {
        public ContactGlobals { byteValue(timeWarp); byteValue(shield); byteValue(combineRing); }
    }

    /**
     * Borrowed inputs for this Main invocation's reached TimeWarp subcall. Neither the reserved
     * snapshot nor contact values become an actor/shared capture or another live owner.
     */
    public record TimeWarpContext(CdPlayerTimeWarp.Context context, ContactGlobals contact) {
        public TimeWarpContext { Objects.requireNonNull(context); Objects.requireNonNull(contact); }
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
     * An empty response means no ENEMY/HAZARD/MONITOR or SPECIAL flag owner: those selections still stop
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

    /**
     * Ephemeral declared pure input for routines 6 and 8 only, NOT a world/source ownership certificate: the
     * Runtime's call-cut dynamic pool capture (the same one it gives TouchContext), its ring globals (lives,
     * updateHUDLives) and its level-flow globals. Sampled only by ObjSonic_Dead/Restart; never retained,
     * captured or published. The Runtime alone commits DeadCheckGone/RestartCut after the whole call.
     */
    public record LevelContext(CdObjectPool.Snapshot snapshot, CdRingGlobals ring, CdLevelGlobals level) {
        public LevelContext {
            Objects.requireNonNull(snapshot); Objects.requireNonNull(ring); Objects.requireNonNull(level);
        }
    }
    public record Environment(CdPlayerMovement.Environment movement, Controls controls,
                              Globals globals, DynamicSlots dynamicSlots) {
        public Environment {
            Objects.requireNonNull(movement); Objects.requireNonNull(controls);
            Objects.requireNonNull(globals); Objects.requireNonNull(dynamicSlots);
        }
    }

    /**
     * HURT_RETURN is a completed ObjSonic_Hurt call (Main.asm:2351-2366): drawn, but no MODE was called.
     * DEAD_RETURN is a completed ObjSonic_Dead call (2397-2404): its tail JMP DrawObject always draws.
     * RESTART_RETURN is a completed ObjSonic_Restart call (2453-2494): no DrawObject and no MODE.
     * CONTROLLED_MAIN_RETURN calls TimeWarp then the common Main tail, with no MODE or PP special collision.
     */
    public enum Boundary { SLOT_ABSENT, INIT_RETURN, MAIN_RETURN, HURT_RETURN, DEAD_RETURN, RESTART_RETURN, CONTROLLED_MAIN_RETURN }
    /**
     * STOP_Z80/START_Z80 are ordered source-position observations of the Z80 bus calls around Restart's driver
     * byte write (2461-2463). No Z80 bus or driver owner exists; they are never instructions to replay the calls.
     */
    public enum NativeCall {
        CHARGE_BEFORE_DISPATCH, INIT, EXTENDED_CAMERA, WATERFALL_SPLASH_CHECK, MODE,
        PP_SPECIAL_COLLISION, DISPLAY, RECORD_POSITION, PP_WATER, ANIMATE, SPECIAL_CHUNKS,
        OBJ_MOVE, HURT_CHECK_LAND, LEVEL_COLLISION_IN_AIR, KILL_PLAYER, LEVEL_BOUND, DRAW_OBJECT,
        DEAD_CHECK_GONE, FIND_OBJ_SLOT, OBJ_MOVE_GRAVITY, STOP_Z80, START_Z80, TIME_WARP
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
    /**
     * Native ordered writes to Runtime-owned level-flow and ring globals (Main CPU Variables.inc):
     * updateHUDTime $FF1510, levelRestart word $FF1502, plcLoadFlags $FF151C and spawnMode $FF1522 belong to
     * CdLevelGlobals; lives and updateHUDLives to CdRingGlobals. FLOWER_COUNT_LONG is CLR.L flowerCount
     * ($FF1900), spanning the three count bytes AND fadeEnableDisplay $FF1903 (Sonic/Main.asm:2466).
     */
    public enum LevelField {
        UPDATE_HUD_TIME(0xFF), UPDATE_HUD_LIVES(0xFF), LIVES(0xFF), LEVEL_RESTART(0xFFFF),
        FLOWER_COUNT_LONG(-1), PLC_LOAD_FLAGS(0xFF), SPAWN_MODE(0xFF);
        private final int mask;
        LevelField(int mask) { this.mask = mask; }
    }
    /**
     * Source-ordered PP music requests (Level/Main.asm:555-578; Sonic/Main.asm:401-407,429-435).
     * Past expiry fades first; PlayLevelMusic selects the masked period. An unavailable goodFuture
     * read keeps its typed boundary. These are requests only, not Sub CPU execution or playback.
     */
    public enum Sound { SCMD_R1AMUS, FM_9C, SCMD_FADECDA, SCMD_PASTMUS, SCMD_R1DMUS }
    public sealed interface Effect permits ActorWrite, GlobalWrite, ChargeEffect, ModeEffect,
            CallReturned, DisplayEligibility, PositionHistoryWrite, AnimationPublication,
            ObjectTouchResult, ObjectTouchResponse, TouchScanCompleted, SoundRequest,
            AirCollisionEffect, KillPlayerCall, LevelWrite, DeadCheckGone, UncheckedAliasWrite,
            Z80RamWrite, ResetSavedObjectFlagsRequest, RestartCut, TimeWarpCall { }
    public record ActorWrite(ActorField field, int value) implements Effect {
        public ActorWrite { Objects.requireNonNull(field); }
    }
    public record GlobalWrite(GlobalField field, int value) implements Effect {
        public GlobalWrite { Objects.requireNonNull(field); }
    }
    public record ChargeEffect(CdPlayerCharge.Effect effect) implements Effect {
        public ChargeEffect { Objects.requireNonNull(effect); }
    }
    /**
     * One ordered Movement helper effect. A stopped MODE's KillRequest marker is retained here, immediately
     * before the one KillPlayerCall it caused; resumed tail effects follow that call. Nothing is replayed.
     */
    public record ModeEffect(CdPlayerMovement.Effect effect) implements Effect {
        public ModeEffect { Objects.requireNonNull(effect); }
    }
    /** Completed pure subcall metadata; Runtime alone validates/publishes its reserved/contact outputs. */
    public record TimeWarpCall(CdPlayerTimeWarp.Result result) implements Effect {
        public TimeWarpCall { Objects.requireNonNull(result); }
    }
    /** Ordered call observation, not an instruction to invoke the owner a second time. */
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    /**
     * Exact DrawObject call cut: Main's Display call before history, Animate and SpecialChunks,
     * or Hurt's/Dead's unconditional tail JMP after Animate (Main.asm:2366,2404), there always eligible. The queue
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
     * The complete pure selected ENEMY/HAZARD/MONITOR or owned SPECIAL flag response. Its final actor, invincible and scoreChain
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
     * value exists here. A debug no-write exit is still a call that returned into Hurt or MODE.
     */
    public record KillPlayerCall(CdPlayerTouchResponse.KillResult result) implements Effect {
        public KillPlayerCall { Objects.requireNonNull(result); }
    }

    /**
     * One ordered native write to a Runtime-owned global, at its source position. An observation only: the
     * Runtime commits the DeadCheckGone/RestartCut summary once and never replays these writes.
     */
    public record LevelWrite(LevelField field, int value) implements Effect {
        public LevelWrite {
            Objects.requireNonNull(field);
            if ((value & ~field.mask) != 0) throw new IllegalArgumentException("Level write exceeds its native width");
        }
    }

    /**
     * ObjSonic_DeadChkGone's gone branch (Sonic/Main.asm:2416-2444), published only when those writes ran.
     * Final Runtime-owned values: lives and updateHudLives (CdRingGlobals), updateHudTime (CdLevelGlobals).
     * allocation is empty on the time-attack .ResetDelay path (no call), FindObjSlot(empty) for a full pool
     * and the first oID-zero whole slot otherwise. gameOverImage is present exactly for a found slot: the
     * call-cut image with only MOVE.B #$3B,oID applied, every other dirty byte retained. The Runtime
     * validates and commits it against the same call cut, before the dynamic pass; Body writes no pool.
     */
    public record DeadCheckGone(int lives, int updateHudLives, int updateHudTime,
                                Optional<CdPlayerTouchResponse.FindObjSlot> allocation,
                                Optional<CdObjectState> gameOverImage) implements Effect {
        public DeadCheckGone {
            byteValue(lives); byteValue(updateHudLives); byteValue(updateHudTime);
            Objects.requireNonNull(allocation); Objects.requireNonNull(gameOverImage);
            OptionalInt found = allocation.map(CdPlayerTouchResponse.FindObjSlot::wholeIndex).orElse(OptionalInt.empty());
            if (found.isPresent() != gameOverImage.isPresent() || (gameOverImage.isPresent()
                    && (gameOverImage.orElseThrow().identity().wholeIndex() != found.getAsInt()
                    || gameOverImage.orElseThrow().id() != GAME_OVER_ID))) {
                throw new IllegalArgumentException("Only a found FindObjSlot answer carries its oID $3B image");
            }
        }
    }

    /**
     * FindObjSlot's exhausted DBF leaves a1 = dynObjectsEnd ($FFF000, Level/_Definitions.inc:165-174), and
     * MOVE.B #$3B,oID(a1) (Sonic/Main.asm:2437) has no branch. An ordered observation of that unchecked
     * 24-bit write only: no pool slot, owned buffer or global exists for it here.
     */
    public record UncheckedAliasWrite(int address, int value) implements Effect {
        public UncheckedAliasWrite {
            if ((address & ~0xFFFFFF) != 0) throw new IllegalArgumentException("Expected a 24-bit native address");
            byteValue(value);
        }
    }

    /**
     * MOVE.B #1,Z80RAM+$1C3E between StopZ80 and StartZ80 (Sonic/Main.asm:2461-2463), as the RELATIVE Z80
     * RAM offset (Z80RAM is $2000 bytes, Main CPU.inc:31-33). Typed observation: no Z80 driver runs here.
     */
    public record Z80RamWrite(int address, int value) implements Effect {
        public Z80RamWrite {
            if (address < 0 || address >= Z80_RAM_SIZE) throw new IllegalArgumentException("Expected a Z80 RAM offset");
            byteValue(value);
        }
    }

    /**
     * BSR.W ResetSavedObjFlags (Sonic/Main.asm:2465; Object Spawner.asm:11-20) at its source position. NOT
     * executed here: the saved bank belongs to the placement owner, which performs it once from RestartCut.
     */
    public record ResetSavedObjectFlagsRequest() implements Effect { }

    /**
     * ObjSonic_Restart's zero-word cut (Sonic/Main.asm:2459-2491). next is the complete post-routine level-flow
     * value: levelRestart 1, flowerCount and fadeEnableDisplay 0, and the native plcLoadFlags/spawnMode
     * branches. resetSavedObjectFlags is the unconditional BSR above. The Runtime commits both before later
     * objects run; the ordered LevelWrite effects are observations of the same writes.
     */
    public record RestartCut(CdLevelGlobals next, boolean resetSavedObjectFlags) implements Effect {
        public RestartCut { Objects.requireNonNull(next); }
    }

    /**
     * Main's Display queues the actor before Animate and Hurt's/Dead's DrawObject queues it after; the later
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
            boolean unconditional = boundary == Boundary.HURT_RETURN || boundary == Boundary.DEAD_RETURN;
            boolean controlled = boundary == Boundary.CONTROLLED_MAIN_RETURN;
            boolean drawn = boundary == Boundary.MAIN_RETURN || controlled || unconditional;
            if (drawn != display.isPresent()
                    || (boundary == Boundary.MAIN_RETURN) != modeBoundary.isPresent()
                    || modeBoundary.orElse(null) == CdPlayerMovement.Boundary.KILL_PLAYER
                    || (unconditional && !display.orElseThrow().queued())
                    || (controlled && effects.stream().anyMatch(effect -> effect instanceof ModeEffect
                        || effect instanceof CallReturned returned && (returned.call() == NativeCall.MODE
                            || returned.call() == NativeCall.PP_SPECIAL_COLLISION)))) {
                throw new IllegalArgumentException(
                        "Completed Main/controlled Main or drawn Hurt/Dead has display; only ordinary Main has MODE publication");
            }
        }
    }

    /** DEAD_ROUTINE/RESTART_ROUTINE now mean routine 6/8 without an explicit LevelContext. */
    public enum UnsupportedBoundary {
        SLOT_ID, SOURCE_FAMILY, DEBUG_UPDATE, ROUTINE_DISPATCH, DEAD_ROUTINE, RESTART_ROUTINE,
        MINIATURE_INIT, MAPPING_BINDING, WATERFALL_SPLASH_CREATION, DEBUG_ENTRY, OBJECT_CONTROL,
        HISTORY_ADDRESS, TOUCH_SCAN, ROLL_TUNNEL_OWNER, STANDING_OBJECT_MOVE,
        TIME_WARP_TIMER_START, TIME_WARP_RESTART_WRITE, TIME_WARP_SPAWN_MODE, TIME_WARP_STAR_SLOT,
        TIME_WARP_SLOW_RESET, TIME_WARP_STOP_RESET, LEVEL_MUSIC_GOOD_FUTURE
    }
    /** No staged state or effects escape. */
    public static final class UnsupportedBody extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private UnsupportedBody(UnsupportedBoundary boundary) {
            super(boundary.name());
            this.boundary = boundary;
        }
        public UnsupportedBoundary boundary() { return boundary; }
    }

    /**
     * Selected native handler without an owner here (unowned SPECIAL selector, or any response
     * handler without a ResponseContext); no staged Body result escapes.
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

    /** ObjGameOver's ID ($3B, Sonic/Main.asm:2437), dynObjectsEnd and the Z80 RAM size. */
    private static final int GAME_OVER_ID = 0x3B;
    private static final int DYN_OBJECTS_END = 0xFFD000 + CdObjectPool.END_WHOLE_INDEX * CdObjectState.BYTE_SIZE;
    private static final int Z80_RAM_SIZE = 0x2000;

    private final CdR11aPlayerAssets player;
    private final CdAssetBytes layout;
    private final CdReleaseVariant variant;
    private final int ordinaryPeriod;
    private final CdSonicVisualAssets sonic;
    private final CdPlayerMovement movement;
    private final CdPlayerAnimation animator;
    private final int mappingAddress;

    /** Production binding borrows the supplied products. Authentication remains the asset loader's responsibility. */
    public CdPlayerBody(CdR11aAssets assets) {
        Objects.requireNonNull(assets);
        var terrain = Objects.requireNonNull(assets.terrain()); sonic = Objects.requireNonNull(assets.sonic());
        player = Objects.requireNonNull(assets.player());
        if (player.sonic() != sonic) throw new IllegalArgumentException("Player and Sonic mapping owners differ");
        mappingAddress = Objects.requireNonNull(assets.profile()).range(CdR11aAsset.MAP_SPR_SONIC).nativeAddress();
        movement = new CdPlayerMovement(terrain, player); animator = new CdPlayerAnimation(player);
        layout = terrain.layout().ram(); variant = assets.profile().variant(); ordinaryPeriod = 1;
    }

    private CdPlayerBody(CdStagePlayerSource source, CdPlayerMovement movement) {
        player = source.player(); sonic = source.sonic(); layout = source.terrain().layout().ram();
        mappingAddress = source.mappingAddress(); variant = source.variant(); ordinaryPeriod = source.ordinaryPeriod();
        this.movement = movement; animator = new CdPlayerAnimation(player);
    }

    /** Immutable stage-data binding only; grants no Frame, invocation, load or touch-owner admission. */
    public static CdPlayerBody fromStageSource(CdStagePlayerSource source) {
        Objects.requireNonNull(source, "stage player source");
        return new CdPlayerBody(source, CdPlayerMovement.fromStageSource(source));
    }

    /** ObjSonic (56-105). No slot means no header, charge or routine work. */
    public Result step(State state, Environment environment) {
        return step(state, environment, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Explicit per-call touch input overrides only DynamicSlots fallback.
     * Neither scanner, snapshot nor response owner is sampled unless Main reaches its actual
     * post-Animate, pre-SpecialChunks call with both native touch gates open.
     */
    public Result step(State state, Environment environment, Optional<TouchContext> touchContext) {
        return step(state, environment, touchContext, Optional.empty(), Optional.empty());
    }

    /**
     * As above, plus the explicit routine-6/8 LevelContext. Only ObjSonic_Dead and ObjSonic_Restart sample
     * it; without it they stop at DEAD_ROUTINE/RESTART_ROUTINE after the unchanged header and predispatch.
     * Init, Main, Hurt and an absent slot never read it, and no State/Environment/Result retains it.
     */
    public Result step(State state, Environment environment, Optional<TouchContext> touchContext,
                       Optional<LevelContext> levelContext) {
        return step(state, environment, touchContext, levelContext, Optional.empty());
    }

    /**
     * Borrowed stood-slot cut for Movement and Hurt's actual ObjMove read only. No capture/partition or
     * owner field retains it. A stopped MODE receives the SAME input on its one KillPlayer continuation.
     * ChkFlipper's immutable ObjectPatch remains metadata; only a later admitted touch derives a local
     * same-owner view, shared by its scanner AND response. The Runtime alone folds/commits final images.
     */
    public Result step(State state, Environment environment, Optional<TouchContext> touchContext,
                       Optional<LevelContext> levelContext, Optional<CdPlayerMovement.ObjectContext> objects) {
        return step(state, environment, touchContext, levelContext, objects, Optional.empty());
    }

    /**
     * Adds only this invocation's borrowed TimeWarp cut. Ordinary context-free MODE retains its legacy
     * active-warp stop; controlled Main admits only source-proven read-only returns without that cut.
     * Init/Hurt/Dead/Restart never sample it. A thrown helper/tail still exposes no staged
     * Body Result: the helper's exception prefix is descriptive, not an adopted live publication.
     */
    public Result step(State state, Environment environment, Optional<TouchContext> touchContext,
                       Optional<LevelContext> levelContext, Optional<CdPlayerMovement.ObjectContext> objects,
                       Optional<TimeWarpContext> timeWarpContext) {
        Objects.requireNonNull(state); Objects.requireNonNull(environment); Objects.requireNonNull(touchContext);
        Objects.requireNonNull(levelContext); Objects.requireNonNull(objects); Objects.requireNonNull(timeWarpContext);
        if (state.header().slotId() == 0) {
            return new Result(state, environment, List.of(), Optional.empty(), Boundary.SLOT_ABSENT, Optional.empty());
        }
        if (state.header().slotId() != 1) unsupported(UnsupportedBoundary.SLOT_ID);
        var gates = environment.movement().gates();
        boolean activePeriod = gates.timeZone() >= 0x80 && gates.timeZone() <= 0x82;
        if (gates.zoneAct() != 0 || (gates.timeZone() != ordinaryPeriod && !(activePeriod && timeWarpContext.isPresent()))) {
            unsupported(UnsupportedBoundary.SOURCE_FAMILY);
        }
        // Retail main-player branch (56-73): time attack bypasses this debug dispatch test.
        if (environment.globals().timeAttackMode() == 0 && (environment.globals().debugMode() >>> 8) != 0) {
            unsupported(UnsupportedBoundary.DEBUG_UPDATE);
        }
        Cursor c = new Cursor(state, environment);
        c.applyCharge(CdPlayerCharge.beforeRoutineDispatch(c.chargeState())); // EXACTLY ONCE, using prior rolling bit.
        c.returned(NativeCall.CHARGE_BEFORE_DISPATCH);
        switch (c.motion.routine()) {
            case 0 -> { initialize(c); return c.finish(Boundary.INIT_RETURN); }
            case 2 -> { return c.finish(main(c, touchContext, objects, timeWarpContext)); }
            case 4 -> { hurt(c, objects); return c.finish(Boundary.HURT_RETURN); } // Never samples touchContext.
            case 6 -> { dead(c, level(levelContext, UnsupportedBoundary.DEAD_ROUTINE)); return c.finish(Boundary.DEAD_RETURN); }
            case 8 -> {
                restart(c, level(levelContext, UnsupportedBoundary.RESTART_ROUTINE));
                return c.finish(Boundary.RESTART_RETURN); // No DrawObject, so no display and no MODE.
            }
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
    private Boundary main(Cursor c, Optional<TouchContext> touchContext,
                      Optional<CdPlayerMovement.ObjectContext> objects, Optional<TimeWarpContext> timeWarpContext) {
        requireBoundMapping(c);
        extendedCamera(c); waterfall(c);
        if (c.debugCheat != 0 && (c.controls.rawTap() & 0x10) != 0) unsupported(UnsupportedBoundary.DEBUG_ENTRY);
        if (c.controls.controlLocked() == 0) {
            c.controls = new Controls(c.controls.rawHeld(), c.controls.rawTap(), c.controls.rawHeld(),
                    c.controls.rawTap(), c.controls.controlLocked());
            c.global(GlobalField.LOGICAL_HELD, c.controls.logicalHeld());
            c.global(GlobalField.LOGICAL_TAP, c.controls.logicalTap());
        }
        // Capture the entry selector once; later touch/animation cannot change the semantic return.
        boolean controlled = (c.motion.playerControl() & 1) != 0;
        if (controlled) {
            touchContext = controlledTimeWarp(c, touchContext, timeWarpContext);
            c.returned(NativeCall.TIME_WARP);
        } else {
            var input = new CdPlayerMovement.Input(c.controls.logicalHeld(), c.controls.logicalTap());
            var moved = timeWarpContext.isPresent()
                    ? movement.stepToTimeWarp(c.motion, input, c.env.movement(), objects)
                    : movement.step(c.motion, input, c.env.movement(), objects);
            c.motion = moved.state(); c.setMovementEnvironment(moved.environment());
            if (moved.boundary() == CdPlayerMovement.Boundary.TIME_WARP) {
                for (var effect : moved.effects()) c.effects.add(new ModeEffect(effect));
                touchContext = timeWarp(c, touchContext, timeWarpContext.orElseThrow());
                // Return to the originally selected MODE after its one ramp/TimeWarp prefix. The same
                // latched logical Input/support cut reaches JumpHeight/CheckJump and later KillPlayer.
                moved = movement.resumeAfterTimeWarp(moved, c.env.movement(), input, objects);
                c.motion = moved.state(); c.setMovementEnvironment(moved.environment());
            }
            boolean stopped = moved.boundary() == CdPlayerMovement.Boundary.KILL_PLAYER;
            for (var effect : moved.effects()) {
                c.effects.add(new ModeEffect(effect));
                // LevelBound .Bottom BNE.W KillPlayer (1703-1705): the ONE shared call, at the stop's final marker.
                if (stopped && effect instanceof CdPlayerMovement.KillRequest) killPlayer(c);
            }
            if (stopped) {
                // KillPlayer's RTS returns into the DISPATCHED MODE routine (701/727/748/767): only its unfinished
                // tail runs, from the adopted actor; no dispatch, charge, ramp, slope, LevelBound or input repeats.
                moved = movement.resumeAfterLevelBound(moved, c.motion, c.env.movement(), objects);
                c.motion = moved.state(); c.setMovementEnvironment(moved.environment());
                for (var effect : moved.effects()) c.effects.add(new ModeEffect(effect));
            }
            c.modeBoundary = Optional.of(moved.boundary()); c.returned(NativeCall.MODE);
            // PP's actual Player Special Collision.asm:7-8 is RTS, not a shared collision fallback.
            c.returned(NativeCall.PP_SPECIAL_COLLISION);
        }
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
            touch(c, touchContext, objects);
        }
        specialChunks(c);
        return controlled ? Boundary.CONTROLLED_MAIN_RETURN : Boundary.MAIN_RETURN;
    }

    /**
     * ObjSonic_TimeWarp (573-669) from Main's controlled branch. With no borrowed
     * contact/reserved owner only the exact no-write returns are knowable; do not
     * invent timeWarp=0 or a successful pure-helper result for the opaque byte.
     * The bound source-family guard has already run before the header dispatch.
     */
    private static Optional<TouchContext> controlledTimeWarp(Cursor c, Optional<TouchContext> touchContext,
                                                            Optional<TimeWarpContext> timeWarpContext) {
        if (timeWarpContext.isPresent()) return timeWarp(c, touchContext, timeWarpContext.orElseThrow());
        var gates = c.env.movement().gates();
        // Main.asm:574-583 precedes charge/direction. Only the borrowed full helper owns its three clears.
        if (gates.zoneAct() == 0 && (gates.timeZone() == 0 || gates.timeZone() == 2) && c.x() < 0x0900) {
            unsupported(UnsupportedBoundary.TIME_WARP_STOP_RESET);
        }
        if (c.motion.charge() != 0) return touchContext;
        if (c.env.movement().gates().timeWarpDirection() == 0) return touchContext;
        // MOVE.W/BPL/NEG.W reads GVel before the timer. NEG.W($8000) remains $8000.
        int raw = c.motion.groundVelocity() & 0xFFFF;
        int magnitude = (raw & 0x8000) == 0 ? raw : uw(-raw);
        int timer = c.timeWarpTimer;
        if (timer == 0) unsupported(UnsupportedBoundary.TIME_WARP_TIMER_START);
        if (timer >= 230) unsupported(UnsupportedBoundary.TIME_WARP_RESTART_WRITE);
        if (timer >= 210) unsupported(UnsupportedBoundary.TIME_WARP_SPAWN_MODE);
        if (timer < 90) {
            if (magnitude >= 0x600) unsupported(UnsupportedBoundary.TIME_WARP_STAR_SLOT);
            unsupported(UnsupportedBoundary.TIME_WARP_SLOW_RESET);
        }
        if (magnitude < 0x600) unsupported(UnsupportedBoundary.TIME_WARP_STOP_RESET);
        return touchContext;
    }

    /** One shared pure TimeWarp invocation and scalar/contact adoption for both Main paths. */
    private static Optional<TouchContext> timeWarp(Cursor c, Optional<TouchContext> touchContext,
                                                 TimeWarpContext borrowed) {
        var before = c.env.movement(); var gates = before.gates();
        var context = borrowed.context();
        if (context.saveContext().isPresent()) {
            var supplied = context.saveContext().orElseThrow();
            var reads = new CdPlayerTimeWarp.SaveReads() {
                @Override public int readScrollLockWord() { return supplied.reads().readScrollLockWord(); }
                @Override public int read(CdTimeWarpSaveData.Source source) {
                    // Actual local post-predispatch/post-ramp cut, before the original MODE tail or common closure.
                    return switch (Objects.requireNonNull(source)) {
                        case PRIMARY_X -> c.x();
                        case PRIMARY_Y -> c.y();
                        case PRIMARY_GVEL -> c.motion.groundVelocity() & 0xFFFF;
                        case PRIMARY_X_VELOCITY -> c.motion.xVelocity() & 0xFFFF;
                        case PRIMARY_Y_VELOCITY -> c.motion.yVelocity() & 0xFFFF;
                        case PRIMARY_FLAGS -> c.motion.flags();
                        case MINI_SONIC -> c.env.movement().gates().miniSonic();
                        case BOTTOM_BOUND -> c.env.movement().bounds().bottom();
                        default -> supplied.reads().read(source);
                    };
                }
            };
            context = new CdPlayerTimeWarp.Context(context.spawnMode(), context.reserved(),
                    Optional.of(new CdPlayerTimeWarp.SaveContext(supplied.initial(), reads)), context.levelRestartWord());
        }
        var warp = CdPlayerTimeWarp.step(new CdPlayerTimeWarp.Input(gates.zoneAct(), gates.timeZone(),
                c.x(), c.motion.charge(), c.motion.groundVelocity() & 0xFFFF, gates.timeWarpDirection(),
                c.timeWarpTimer, borrowed.contact().timeWarp()), context);
        c.timeWarpTimer = warp.state().timer();
        int period = warp.saveResult().map(CdPlayerTimeWarp.SaveResult::finalPeriodByte).orElse(gates.timeZone());
        c.setMovementEnvironment(new CdPlayerMovement.Environment(before.physics(), before.bounds(),
                new CdPlayerMovement.Gates(gates.zoneAct(), period, gates.miniSonic(), gates.waterSlide(),
                        gates.windTunnel(), gates.speedShoes(), warp.state().direction()), before.globals()));
        if (warp.restartResult().isPresent()) {
            // Main.asm:608 then FadeOutMusic (Sub CPU.asm:11-12); this is staged metadata only.
            c.level(LevelField.LEVEL_RESTART, warp.restartResult().orElseThrow().finalRestartWord());
            c.sound(Sound.SCMD_FADECDA);
        }
        c.effects.add(new TimeWarpCall(warp));
        // Preserve the actual scanner, snapshot/response/world and unrelated contact bytes.
        if (touchContext.isPresent() && touchContext.orElseThrow().response().isPresent()) {
            var oldTouch = touchContext.orElseThrow(); var response = oldTouch.response().orElseThrow();
            var contact = new ContactGlobals(warp.state().timeWarp(), response.contact().shield(), response.contact().combineRing());
            touchContext = Optional.of(new TouchContext(oldTouch.scanner(), oldTouch.snapshot(),
                    new ResponseContext(response.response(), contact, response.world())));
        }
        return touchContext;
    }

    /** Player_ObjCollide (PP Player Object Collision.asm:7-98), exactly Main:347. */
    private static void touch(Cursor c, Optional<TouchContext> touchContext,
                              Optional<CdPlayerMovement.ObjectContext> objects) {
        boolean patched = c.effects.stream().anyMatch(effect -> effect instanceof ModeEffect mode
                && mode.effect() instanceof CdPlayerMovement.ObjectPatch);
        // An omitted later touch owner cannot be invented from support input. Keep the ordered patch
        // metadata for the external caller; ordinary context-free/no-patch behavior stays unchanged.
        if (patched && touchContext.isEmpty()) return;
        if (touchContext.isEmpty()) {
            if (c.env.dynamicSlots() != DynamicSlots.EMPTY_DEVELOPMENT_96) unsupported(UnsupportedBoundary.TOUCH_SCAN);
            // Existing deliberate development fallback: 96 inactive slot flag visits.
            c.effects.add(new TouchScanCompleted(CdObjectPool.DYNAMIC_COUNT));
            return;
        }
        var input = touchContext.orElseThrow();
        if (patched) {
            var original = objects.orElseThrow().slots();
            // Identical selected images in a different capture do NOT establish the shared call cut.
            if (input.snapshot() != original) {
                throw new IllegalArgumentException("Flipper touch must use the identical borrowed object snapshot");
            }
            var derived = original;
            for (var effect : c.effects) {
                if (effect instanceof ModeEffect mode && mode.effect() instanceof CdPlayerMovement.ObjectPatch patch) {
                    var image = patch.patch();
                    int index = image.before().identity().dynamicIndex();
                    // equals includes actual pool owner, whole identity and EVERY byte at this source cut.
                    if (!image.before().equals(derived.slots().get(index))) {
                        throw new IllegalArgumentException("Flipper patch before-image is not the current overlay image");
                    }
                    derived = CdObjectPool.overlay(derived, List.of(image.after()));
                }
            }
            // One EXACT derived Snapshot instance goes to both scan and respond below.
            input = new TouchContext(input.scanner(), derived, input.response());
        }
        var selected = input.scanner().scan(new CdPlayerObjectTouch.PlayerCut(c.x(), c.y(),
                c.motion.yRadius(), c.animation.mapFrame(), c.hurt), input.snapshot());
        if (selected instanceof CdPlayerObjectTouch.HandlerBoundary handler) {
            var kind = handler.kind();
            boolean ownedHandler = switch (kind) {
                case ENEMY, HAZARD, MONITOR -> true;
                case SPECIAL -> CdPlayerTouchResponse.ownsSpecialFlagCollision(handler.selectedSlot().orElseThrow()
                        .unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));
                default -> false;
            };
            if (input.response().isEmpty() || !ownedHandler) {
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
     * Player_TouchMonitor/Player_TouchEnemy/Player_TouchHazard and owned Player_TouchSpecial flag branches
     * (PP Player Object Collision.asm:102-395) through the pure
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
    private void hurt(Cursor c, Optional<CdPlayerMovement.ObjectContext> objects) {
        requireBoundMapping(c);
        objMove(c, objects);
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

    /**
     * ObjSonic_Dead (2397-2404), literal call order: DeadChkGone, ObjMoveGrv, RecordPos, Animate, then the
     * unconditional tail JMP DrawObject. No Display, ExtCamera, input copy, MODE, water, angle copy, touch or
     * SpecialChunks call. The gone writes are adopted before ObjMoveGrv reads the new velocity word.
     */
    private void dead(Cursor c, LevelContext level) {
        requireBoundMapping(c);
        deadCheckGone(c, level);
        objMoveGravity(c);
        recordPosition(c);
        animate(c);
        drawObject(c);
    }

    /**
     * ObjSonic_DeadChkGone (2410-2447). MOVE.W bottomBound / ADDI.W #256 wraps, then CMP.W oY / BCC.W is an
     * UNSIGNED word test. FindObjSlot runs over the call-cut capture; the Runtime commits the summary.
     */
    private static void deadCheckGone(Cursor c, LevelContext level) {
        if (c.y() > uw(c.env.movement().bounds().bottom() + 0x100)) {
            c.setYVelocity(-0x38); // MOVE.W #-$38,oYVel (2416).
            c.setRoutine((c.motion.routine() + 2) & 0xFF); // ADDQ.B #2,oRoutine (2417).
            c.level(LevelField.UPDATE_HUD_TIME, 0); // CLR.B updateHUDTime (2419).
            int hudLives = (level.ring().updateHudLives() + 1) & 0xFF; // ADDQ.B wraps (2420).
            c.level(LevelField.UPDATE_HUD_LIVES, hudLives);
            int lives = (level.ring().lives() - 1) & 0xFF; // SUBQ.B #1,lives (2421).
            c.level(LevelField.LIVES, lives);
            if ((lives & 0x80) != 0) { lives = 0; c.level(LevelField.LIVES, 0); } // BPL.S fails: CLR.B (2422-2423).
            Optional<CdPlayerTouchResponse.FindObjSlot> allocation = Optional.empty();
            Optional<CdObjectState> image = Optional.empty();
            if (c.motion.animation() != 0x2B && c.timeAttack != 0) {
                lives = 0; c.level(LevelField.LIVES, 0); // MOVE.B #0,lives, then BRA.S .ResetDelay (2432-2433).
                c.setResetTimer(60);
            } else {
                // .LoadGameOver (2435-2444): the unchecked FindObjSlot answer gets oID $3B with no branch.
                var found = findObjSlot(level.snapshot());
                c.returned(NativeCall.FIND_OBJ_SLOT);
                allocation = Optional.of(new CdPlayerTouchResponse.FindObjSlot(found));
                if (found.isPresent()) {
                    image = Optional.of(level.snapshot().slots().get(found.getAsInt() - CdObjectPool.FIRST_WHOLE_INDEX)
                            .moveByte(CdObjectState.ByteField.ID, GAME_OVER_ID));
                } else {
                    c.effects.add(new UncheckedAliasWrite(DYN_OBJECTS_END, GAME_OVER_ID));
                }
                c.setResetTimer(8 * 60);
                if (lives != 0) c.setResetTimer(60); // TST.B lives / BEQ.S .End (2440-2444).
            }
            c.effects.add(new DeadCheckGone(lives, hudLives, 0, allocation, image));
        }
        c.returned(NativeCall.DEAD_CHECK_GONE);
    }

    /** FindObjSlot (Object Spawner.asm:329-340): the first oID-zero whole slot 32..127 of the capture; no write. */
    private static OptionalInt findObjSlot(CdObjectPool.Snapshot snapshot) {
        var slots = snapshot.slots();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).id() == 0) return OptionalInt.of(CdObjectPool.FIRST_WHOLE_INDEX + i);
        }
        return OptionalInt.empty();
    }

    /**
     * ObjSonic_Restart (2453-2494). A zero word is never counted. At the zero cut, the ordered writes and
     * requests are published for the Runtime's one RestartCut commit; the Z80/Sub CPU calls are observations.
     * timeZone is the full byte (CMPI.B #1); the source-bound admission does not normalize it.
     * Not-present and marked decisions retain the native raw-byte branches.
     */
    private static void restart(Cursor c, LevelContext context) {
        if (c.resetTimer == 0) return; // TST.W / BEQ.W .End (2454-2455).
        c.setResetTimer(uw(c.resetTimer - 1)); // SUBQ.W #1 (2456).
        if (c.resetTimer != 0) return; // BNE.W .End (2457).
        var level = context.level();
        c.level(LevelField.LEVEL_RESTART, 1); // MOVE.W #1,levelRestart (2459).
        c.returned(NativeCall.STOP_Z80); // JSR StopZ80 (2461).
        c.effects.add(new Z80RamWrite(0x1C3E, 1)); // MOVE.B #1,Z80RAM+$1C3E (2462).
        c.returned(NativeCall.START_Z80); // JSR StartZ80 (2463).
        c.effects.add(new ResetSavedObjectFlagsRequest()); // BSR.W ResetSavedObjFlags (2465).
        c.level(LevelField.FLOWER_COUNT_LONG, 0); // CLR.L flowerCount, including fadeEnableDisplay (2466).
        int timeZone = c.env.movement().gates().timeZone(), plc = level.plcLoadFlags(), spawn = level.spawnMode();
        if (level.checkpoint() == 0 && timeZone == 1) { // TST.B checkpoint, CMPI.B #1,timeZone (2468-2471).
            plc &= ~2; c.level(LevelField.PLC_LOAD_FLAGS, plc); // BCLR #1 always stores the byte (2472).
        }
        if (context.ring().lives() != 0) { // TST.B lives / BEQ.S .SendCmd (2477-2478).
            if (timeZone != 1) { spawn = 0; c.level(LevelField.SPAWN_MODE, 0); } // .SpawnAtStart (2479-2480,2487-2488).
            else if (level.checkpoint() != 0) { spawn = 1; c.level(LevelField.SPAWN_MODE, 1); } // (2481-2484).
        }
        c.sound(Sound.SCMD_FADECDA); // MOVE.W #SCMD_FADECDA,d0 then BRA.W SubCPUCmd (2475,2490-2491).
        c.effects.add(new RestartCut(new CdLevelGlobals(1, level.updateHudTime(), level.checkpoint(), spawn, plc, 0, 0,
                level.powerup(), level.timeOver()), true));
    }

    /** Routines 6/8 sample their explicit context; without one they keep their typed stop and publish nothing. */
    private static LevelContext level(Optional<LevelContext> context, UnsupportedBoundary absent) {
        if (context.isEmpty()) unsupported(absent);
        return context.orElseThrow();
    }

    /** ObjMove (Object Functions.asm:91-128): shared pure arithmetic; retain Body's native write observations. */
    private static void objMove(Cursor c, Optional<CdPlayerMovement.ObjectContext> objects) {
        // Preserve Hurt's existing typed missing-owner boundary at the actual stood-slot read.
        // With a supplied cut, reserved/outside-address errors come from the shared native owner.
        if ((c.motion.flags() & 8) != 0 && objects.isEmpty()) unsupported(UnsupportedBoundary.STANDING_OBJECT_MOVE);
        var moved = CdPlayerMovement.moveWithoutGravity(c.motion, objects);
        c.setCentre(moved.centreX16_16(), moved.centreY16_16());
        c.returned(NativeCall.OBJ_MOVE);
    }

    /**
     * ObjMoveGrv (Object Functions.asm:42-82). Movement alone supplies final motion/full-long integration.
     * Body retains the existing ordered ActorWrite observations, including the intermediate ADDI.W
     * before a cap (and an executed same-value cap). These are metadata, never a second physics result.
     * Shipped FixBugs=0: BTST preserves old Y's N, and the cap also runs on .NoGravity. No stood-slot read.
     */
    private static void objMoveGravity(Cursor c) {
        var before = c.motion;
        var moved = CdPlayerMovement.moveWithGravity(before);
        int old = before.yVelocity(), control = before.playerControl();
        boolean gravity = (control & 8) == 0 && (old >= 0 || (control & 2) == 0 || uw(old) >= 0xF800) && (control & 4) == 0;
        int writtenWord = gravity ? (short) (old + 0x38) : old;
        if (gravity) c.actor(ActorField.Y_VELOCITY, writtenWord); // Historical ADDI.W (:66), including before cap.
        if (writtenWord >= 0x1000) c.actor(ActorField.Y_VELOCITY, moved.yVelocity()); // Historical MOVE.W (:73).
        c.motion = moved;
        c.setCentre(moved.centreX16_16(), moved.centreY16_16());
        c.returned(NativeCall.OBJ_MOVE_GRAVITY);
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

    /** Hurt's and Dead's tail JMP DrawObject (2366, 2404): unconditional, at the completed post-Animate pose. */
    private static void drawObject(Cursor c) {
        c.displayEligible = true;
        c.effects.add(new DisplayEligibility(true, c.x(), c.y(), c.motion.yRadius(), c.motion.spriteFlags(),
                c.header.width(), c.header.priority()));
        c.returned(NativeCall.DRAW_OBJECT);
    }

    /** Main, Hurt and Dead draw through the actual source-bound mapping; Init alone writes it. */
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
                if (c.env.movement().gates().speedShoes() == 0 && c.bossMusic == 0) restoreLevelMusic(c);
                c.invincible = 0; c.global(GlobalField.INVINCIBLE, 0);
            }
        }
        if (c.env.movement().gates().speedShoes() != 0 && c.shoesTimer != 0) {
            c.shoesTimer = uw(c.shoesTimer - 1); c.actor(ActorField.SHOES_TIMER, c.shoesTimer);
            if (c.shoesTimer == 0) {
                c.normalPhysics();
                if (c.invincible == 0 && c.bossMusic == 0) restoreLevelMusic(c);
                c.setSpeedShoes(0); c.global(GlobalField.SPEED_SHOES, 0);
            }
        }
        c.returned(NativeCall.DISPLAY);
    }

    /** Display expiry: raw Past fade, then the PP row of PlayLevelMusic's masked-period table. */
    private static void restoreLevelMusic(Cursor c) {
        int raw = c.env.movement().gates().timeZone();
        if (raw == 0) c.sound(Sound.SCMD_FADECDA);
        switch (raw & 0x7F) {
            case 0 -> c.sound(Sound.SCMD_PASTMUS);
            case 1 -> c.sound(Sound.SCMD_R1AMUS);
            case 2 -> {
                // Main:562-565 reads goodFuture only outside time attack. No owner supplies it here.
                if (c.timeAttack == 0) unsupported(UnsupportedBoundary.LEVEL_MUSIC_GOOD_FUTURE);
                c.sound(Sound.SCMD_R1DMUS);
            }
            default -> unsupported(UnsupportedBoundary.SOURCE_FAMILY);
        }
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

    /** ObjSonic_SpecialChunks (2500-2603), PP branch; compare RAW bytes, X byte is big-endian oX high. */
    private void specialChunks(Cursor c) {
        int raw = rawChunk(c.x(), c.y()); var ids = player.specialChunks();
        boolean tunnel = raw == ids.unsignedByte(2) ? (c.y() & 0xFF) >= 0x90 : raw == ids.unsignedByte(3);
        if (tunnel) {
            // Reuse StartRoll only where its admitted contract matches the source call.
            if ((c.motion.flags() & 2) != 0 || (c.motion.playerControl() & 3) != 0
                    || c.env.movement().gates().miniSonic() != 0) unsupported(UnsupportedBoundary.ROLL_TUNNEL_OWNER);
            if (variant != CdReleaseVariant.USA && (c.motion.flags() & 4) == 0) c.sound(Sound.FM_9C);
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
        return layout.unsignedByte(((y >>> 1) & 0x380) + ((x >>> 8) & 0x7F));
    }

    /** Detached per-call staging. Never retained by this owner or exposed on failure. */
    private static final class Cursor {
        CdPlayerMotionState motion;
        AnimationCursor animation;
        Header header;
        History history;
        Environment env;
        Controls controls;
        final int levelFrames, timeAttack, debugMode, debugCheat, bossMusic;
        int camX, invincible, hurt, invincibilityTimer, shoesTimer, resetTimer, rotationDistance, timeWarpTimer;
        final ObjectLinks objectLinks;
        private final int rotationAngle;
        boolean displayEligible;
        Optional<CdPlayerMovement.Boundary> modeBoundary = Optional.empty();
        final List<Effect> effects = new ArrayList<>();
        Cursor(State state, Environment environment) {
            motion = state.motion(); animation = state.animation(); header = state.header(); history = state.history();
            rotationDistance = state.rotationDistance(); objectLinks = state.objectLinks(); rotationAngle = state.rotationAngle();
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
        void level(LevelField field, int value) { effects.add(new LevelWrite(field, value)); }
        /** oPlayerReset ($3A word): staged once in the cursor, each native write logged. */
        void setResetTimer(int value) { resetTimer = value; actor(ActorField.RESET_TIMER, value); }
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
                    history, rotationDistance, objectLinks, rotationAngle);
            var globals = new Globals(levelFrames, camX, timeAttack, debugMode, debugCheat, timeWarpTimer, invincible, bossMusic);
            var environment = new Environment(env.movement(), controls, globals, env.dynamicSlots());
            Optional<DisplayPublication> display = boundary == Boundary.MAIN_RETURN
                    || boundary == Boundary.CONTROLLED_MAIN_RETURN || boundary == Boundary.HURT_RETURN
                    || boundary == Boundary.DEAD_RETURN ? Optional.of(new DisplayPublication(displayEligible, header.mappingAddress(), header.tile(), header.priority(),
                    header.width(), animation.mapFrame(), motion.spriteFlags(), motion.centreX16_16(), motion.centreY16_16())) : Optional.empty();
            return new Result(state, environment, effects, display, boundary, modeBoundary);
        }
    }

    private static void unsupported(UnsupportedBoundary boundary) {
        throw new UnsupportedBody(boundary);
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
