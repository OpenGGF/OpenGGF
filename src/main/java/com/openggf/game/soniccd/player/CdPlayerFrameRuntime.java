package com.openggf.game.soniccd.player;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.objects.CdSolidObject;
import com.openggf.game.soniccd.objects.CdSpring;
import com.openggf.game.soniccd.objects.CdSpinTunnelTag;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Finite CD-private R11A development frame-state/service owner.
 * Original external handoff was uncompiled; parent behavioral checks are
 * recorded with the task evidence. scddisasm source pin:
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 *
 * The normal module PLC service calls serviceVBlank; the production controller
 * only prepares one sample and the native playable slot only calls runBody.
 * No private loop, host input polling, actor subtype, drawing, audio playback,
 * generic timing port, camera algorithm or runtime disassembly reader exists.
 * Body owns charge/animation exactly once; art observes the PREVIOUS publication.
 * This is not a native cold boot, full frame, arbitrary restore/world admission,
 * startup interrupt count, hardware timing model or playable-route certificate.
 */
public final class CdPlayerFrameRuntime implements PlcLifecycleService, AutoCloseable {

    // BEGIN direct object HurtPlayer publication ports.
    /**
     * Preflight the separate LONG-Y prefix before an actual object HurtPlayer call. Native Spikes
     * (Level/_Objects/Spikes/Main.asm:70-76, pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff)
     * subtracts sign-extended YVel shifted by8, modulo32 bits, including fraction carry/borrow.
     * Root owns object identity, phase and reached gates; these expected values confer none of them.
     */
    public void validateObjectPreHurtYRewind(CdPlayerTouchResponse.Actor expectedActor, int finalYLongBits) {
        requireOpenIdle(); requireClosedFrame(); Objects.requireNonNull(expectedActor, "pre-Hurt actor");
        var h = state.header();
        var current = new CdPlayerTouchResponse.Actor(state.motion(), h.tile(), h.priority(),
                state.timers().hurt(), state.rotationDistance());
        if (!expectedActor.equals(current)) {
            throw new IllegalArgumentException("Stale pre-Hurt actor cut");
        }
        var m = expectedActor.motion();
        if (finalYLongBits != m.centreY16_16() - (((short) m.yVelocity()) << 8)) {
            throw new IllegalArgumentException("Pre-Hurt Y must be the native full-long velocity rewind");
        }
    }

    /** Publish only the checked Y LONG; every other partition/reference and earlier Display survives. */
    public void applyObjectPreHurtYRewind(CdPlayerTouchResponse.Actor expectedActor, int finalYLongBits) {
        validateObjectPreHurtYRewind(expectedActor, finalYLongBits);
        var m = expectedActor.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), finalYLongBits, m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(),
                m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var nextState = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(),
                state.history(), state.rotationDistance(), state.objectLinks());
        state = nextState;
    }

    /**
     * Value-CAS for direct HurtPlayer's three native exits, not a second source execution. PP Player
     * Object Collision.asm:229-298 and Sonic/Main.asm:2295-2330 at the pin above own every store.
     * Shipped FixBugs=0: preserve charge/hurt on Kill, links and non-reset fields on both real exits,
     * and the entire already-prefixed actor on a full-word debug no-write return.
     * Frame owns exactly invincible/scoreChain and reads debugCheat/debugMode/miniSonic here.
     * Root preflights contact/world/slot images and publishes their native effects independently.
     */
    public void validateObjectHurtResult(CdPlayerTouchResponse.Actor expectedActor,
                                         CdPlayerTouchResponse.PlayerGlobals expectedPlayer,
                                         CdPlayerTouchResponse.Result result) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedActor, "HurtPlayer actor");
        Objects.requireNonNull(expectedPlayer, "HurtPlayer globals"); Objects.requireNonNull(result, "HurtPlayer result");
        var h = state.header(); var movement = environment.movement(); var bodyGlobals = environment.globals();
        var current = new CdPlayerTouchResponse.Actor(state.motion(), h.tile(), h.priority(),
                state.timers().hurt(), state.rotationDistance());
        if (!expectedActor.equals(current) || expectedPlayer.invincible() != bodyGlobals.invincible()
                || expectedPlayer.scoreChain() != movement.globals().scoreChain()
                || expectedPlayer.debugCheat() != bodyGlobals.debugCheat()
                || expectedPlayer.debugMode() != bodyGlobals.debugMode()
                || expectedPlayer.miniSonic() != movement.gates().miniSonic()) {
            throw new IllegalArgumentException("Stale HurtPlayer actor/Frame-global cut");
        }
        var exit = result.exit();
        if (exit != CdPlayerTouchResponse.Exit.HURT && exit != CdPlayerTouchResponse.Exit.KILL_PLAYER
                && exit != CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE) {
            throw new IllegalArgumentException("Only direct HurtPlayer native exits are admitted");
        }
        var next = result.actor(); var globals = result.player();
        if (globals.timeWarp() != expectedPlayer.timeWarp() || globals.debugCheat() != expectedPlayer.debugCheat()
                || globals.debugMode() != expectedPlayer.debugMode() || globals.miniSonic() != expectedPlayer.miniSonic()) {
            throw new IllegalArgumentException("HurtPlayer changes a readonly helper global");
        }
        if (exit == CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE) {
            if (expectedPlayer.debugMode() == 0 || !next.equals(expectedActor) || !globals.equals(expectedPlayer)) {
                throw new IllegalArgumentException("KillPlayer debug return must preserve the complete call cut");
            }
            return;
        }
        boolean kill = exit == CdPlayerTouchResponse.Exit.KILL_PLAYER;
        if (globals.scoreChain() != 0 || globals.invincible() != (kill ? 0 : expectedPlayer.invincible())) {
            throw new IllegalArgumentException("HurtPlayer result disagrees with its native Frame-global stores");
        }
        var m = expectedActor.motion(); var n = next.motion();
        if (n.centreX16_16() != m.centreX16_16()
                || (n.centreY16_16() & 0xFFFF) != (m.centreY16_16() & 0xFFFF)
                || n.angle() != m.angle() || n.spriteFlags() != m.spriteFlags()
                || n.previousAnimation() != m.previousAnimation() || n.moveLock() != m.moveLock()
                || n.playerControl() != m.playerControl() || n.previousPrimaryAngle() != m.previousPrimaryAngle()
                || n.previousSecondaryAngle() != m.previousSecondaryAngle()) {
            throw new IllegalArgumentException("HurtPlayer changes an unowned motion field or fraction");
        }
        // Validate Reset's exact publication bounds; do not rerun Reset or replay its call metadata.
        boolean rolling = (m.flags() & 4) != 0, miniature = expectedPlayer.miniSonic() != 0;
        int resetYWord = ((m.centreY16_16() >>> 16) - (rolling ? (miniature ? 2 : 5) : 0)) & 0xFFFF;
        if ((n.centreY16_16() >>> 16) != resetYWord
                || n.xRadius() != (rolling ? (miniature ? 5 : 9) : m.xRadius())
                || n.yRadius() != (rolling ? (miniature ? 0xA : 0x13) : m.yRadius())
                || n.flags() != ((m.flags() & ~0x36) | 2) || n.playerJump() != 0 || n.groundVelocity() != 0) {
            throw new IllegalArgumentException("HurtPlayer changes fields outside its reached native reset stores");
        }
        if (kill) {
            if (expectedPlayer.debugMode() != 0 || n.routine() != 6 || n.xVelocity() != 0 || n.yVelocity() != -0x700
                    || n.animation() != 0x18 || n.charge() != m.charge() || next.hurt() != expectedActor.hurt()
                    || n.stick() != (resetYWord >>> 8) || next.rotationDistance() != (resetYWord & 0xFF)
                    || next.tile() != (expectedActor.tile() | 0x8000) || next.priority() != 0
                    || globals.shield() != expectedPlayer.shield() || globals.combineRing() != expectedPlayer.combineRing()) {
                throw new IllegalArgumentException("KillPlayer changes an unowned field or omits a native terminal store");
            }
        } else {
            int speed = (m.flags() & 0x40) != 0 ? 0x100 : 0x200;
            if (n.routine() != 4 || n.yVelocity() != -2 * speed
                    || (n.xVelocity() != -speed && n.xVelocity() != speed)
                    || n.animation() != 0x1A || n.charge() != 0 || next.hurt() != 0x78 || n.stick() != m.stick()
                    || next.tile() != expectedActor.tile() || next.priority() != expectedActor.priority()
                    || next.rotationDistance() != expectedActor.rotationDistance()) {
                throw new IllegalArgumentException("HurtPlayer changes an unowned field or omits a native terminal store");
            }
        }
    }

    /**
     * Adopt only checked Actor/invincible/scoreChain values once at the closed object cut. Build all
     * immutable replacements before aggregate assignments. No pool/contact/world publication, call
     * replay, FM execution, Body/display/camera dispatch, callback, input, art or clock work occurs.
     * Root calls this synchronously after its complete cross-owner preflight, without a callback gap.
     */
    public void applyObjectHurtResult(CdPlayerTouchResponse.Actor expectedActor,
                                      CdPlayerTouchResponse.PlayerGlobals expectedPlayer,
                                      CdPlayerTouchResponse.Result result) {
        validateObjectHurtResult(expectedActor, expectedPlayer, result);
        if (result.exit() == CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE) return;
        boolean kill = result.exit() == CdPlayerTouchResponse.Exit.KILL_PLAYER;
        var actor = result.actor(); var globals = result.player(); var h = state.header(); var timers = state.timers();
        var nextHeader = kill ? new CdPlayerBody.Header(h.slotId(), h.mappingAddress(), actor.tile(), actor.priority(), h.width()) : h;
        var nextTimers = kill ? timers : new CdPlayerBody.Timers(actor.hurt(), timers.invincibility(), timers.speedShoes(), timers.reset());
        var nextState = new CdPlayerBody.State(actor.motion(), state.animation(), nextHeader, nextTimers,
                state.history(), actor.rotationDistance(), state.objectLinks());
        var m = environment.movement(); var g = m.globals(); var b = environment.globals();
        var movementGlobals = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(),
                g.boredTimer(), globals.scoreChain(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance());
        var nextBodyGlobals = kill ? new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(),
                b.debugCheat(), b.timeWarpTimer(), globals.invincible(), b.bossMusic()) : b;
        var nextEnvironment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(),
                movementGlobals), environment.controls(), nextBodyGlobals, environment.dynamicSlots());
        state = nextState;
        environment = nextEnvironment;
    }
    // END direct object HurtPlayer publication ports.
    private static final long ULONG_MASK = 0xFFFFFFFFL;

    /**
     * Root binds an already-claimed resident bank. This callback applies exactly
     * this DMA and must not advance source state. fullState is the complete
     * logical post-service $300 residency (including untouched tile 24).
     * Construction/rebind/restore of all 24 tiles is root-owned and does not
     * invoke this callback. A throwing callback can have partially changed its
     * bank; this owner therefore closes admission, never retries or pretends
     * that rolling back logical bytes alone repairs the bank.
     */
    @FunctionalInterface
    public interface ArtPublication {
        void publish(CdPlayerArtRuntime.DmaRequest request, CdPlayerArtRuntime.State fullState);
    }

    /** Prospective disposition only. Both phases represent native selector $8. */
    public record InputSelection(boolean nextPaused, PlcLifecyclePhase phase) {
        public InputSelection {
            if (phase != (nextPaused ? PlcLifecyclePhase.NORMAL_PAUSE : PlcLifecyclePhase.ORDINARY_LEVEL)) {
                throw new IllegalArgumentException("Pause selection must describe its selector-$8 service disposition");
            }
        }
    }

    /**
     * Unknown absolute native levelVIntCounter seed is distinct from delta0.
     * Both represented values narrow as native longwords. No consumer needing
     * an absolute clock may substitute delta when absoluteValue is empty.
     */
    public record VIntClock(OptionalLong absoluteSeed, long deltaSinceAdmittedCut) {
        public VIntClock {
            Objects.requireNonNull(absoluteSeed, "absoluteSeed");
            if ((absoluteSeed.isPresent() && (absoluteSeed.getAsLong() < 0 || absoluteSeed.getAsLong() > ULONG_MASK))
                    || deltaSinceAdmittedCut < 0 || deltaSinceAdmittedCut > ULONG_MASK) {
                throw new IllegalArgumentException("V-int values must be unsigned native longwords");
            }
        }
        public OptionalLong absoluteValue() {
            return absoluteSeed.isEmpty() ? OptionalLong.empty()
                    : OptionalLong.of((absoluteSeed.getAsLong() + deltaSinceAdmittedCut) & ULONG_MASK);
        }
        private VIntClock increment() { return new VIntClock(absoluteSeed, (deltaSinceAdmittedCut + 1) & ULONG_MASK); }
    }

    /** Opaque identity created only with this runtime/source binding, never shared across worlds. */
    public static final class CaptureOwner {
        private CaptureOwner() { }
    }

    /** Actor subclass owns this partition only; no shared globals/input/art are captured here. */
    public record ActorState(CaptureOwner owner, CdPlayerBody.State state,
                             Optional<CdPlayerBody.DisplayPublication> display) {
        public ActorState { Objects.requireNonNull(owner); Objects.requireNonNull(state); Objects.requireNonNull(display); }
    }

    /**
     * Module adapter owns this partition only. Environment contains all Body
     * globals, current angle buffers, camera centres/bounds/physics, raw and
     * retained logical latches. rawHeld IS the last actually ReadController-held
     * byte, so no second last-held shadow is stored. Native history/index are
     * in Body.State and captured only by the actor partition.
     */
    public record SharedState(CaptureOwner owner, CdPlayerBody.Environment environment,
                              CdPlayerArtRuntime.State artState, boolean paused, VIntClock vIntClock) {
        public SharedState {
            Objects.requireNonNull(owner); Objects.requireNonNull(environment);
            Objects.requireNonNull(artState); Objects.requireNonNull(vIntClock);
        }
    }

    /** Symbolic IDs retain their original typed owner and source ordering, with no playback. */
    public sealed interface Sound permits BodySound, ChargeSound, MovementSound, ContactFmSound, ContactSubCpuCommand,
            DriverRamWrite { }
    public record BodySound(CdPlayerBody.Sound sound) implements Sound {
        public BodySound { Objects.requireNonNull(sound); }
    }
    public record ChargeSound(CdPlayerCharge.Sound sound) implements Sound {
        public ChargeSound { Objects.requireNonNull(sound); }
    }
    public record MovementSound(CdPlayerMovement.Sound sound) implements Sound {
        public MovementSound { Objects.requireNonNull(sound); }
    }
    /**
     * A PlayFMSound request owned by the PP Player Object Collision response owner, at its call
     * position: a selected enemy/hazard response, or a Hurt KillPlayer call (Sonic/Main.asm:2376,1705).
     */
    public record ContactFmSound(CdPlayerTouchResponse.FmSound sound) implements Sound {
        public ContactFmSound { Objects.requireNonNull(sound); }
    }
    /** AddPoints' 1-up SubCPUCmd request (HUD and Points/Main.asm:176-177), at its call position. */
    public record ContactSubCpuCommand(CdPlayerTouchResponse.SubCpuCommand command) implements Sound {
        public ContactSubCpuCommand { Objects.requireNonNull(command); }
    }
    /**
     * Restart's MOVE.B #1,Z80RAM+$1C3E (Sonic/Main.asm:2462) at its position, as the RELATIVE Z80 RAM offset
     * (Z80RAM is $2000 bytes, Main CPU.inc:31-33). Typed request only: no Z80 bus, driver or playback exists,
     * and the Body's StopZ80/StartZ80 observations are not instructions to replay them.
     */
    public record DriverRamWrite(int address, int value) implements Sound {
        public DriverRamWrite {
            if (address < 0 || address >= 0x2000 || (value & ~0xFF) != 0) {
                throw new IllegalArgumentException("Driver write requires a Z80 RAM offset and an unsigned byte");
            }
        }
    }
    /**
     * Final values already committed once; ordered effects are observations, not write-replay instructions.
     * A DeadCheckGone/RestartCut summary is the Runtime's world/global commit input, never applied here.
     */
    public record BodyPublication(CdPlayerBody.Result result, List<Sound> sounds) {
        public BodyPublication { Objects.requireNonNull(result); sounds = List.copyOf(sounds); }
    }

    /**
     * Plain values an in-module death restart carries because LevelStart's .NormalLoad never writes them
     * (Level/Main.asm:19-329, lives != 0, no checkpoint, present), plus the DEV raw latch. Widths are native:
     * sonicRecordBuf rs.b $100 and sonicArtBuf rs.b $300 (Level/_Definitions.inc:125-126), the $300-byte $F000
     * resident window, debugAngle..debugFloorDist rs.b 1 each and debugCheat rs.w 1 (_Include/Main CPU
     * Variables.inc:130-133,139), and p1CtrlHold rs.b 1 (_Definitions.inc:185). Every value is checked here.
     */
    public record RestartIngress(CdAssetBytes history, CdAssetBytes artCpu, CdAssetBytes artResident,
                                 int debugAngle, int debugAngleShift, int debugQuadrant, int debugFloorDistance,
                                 int debugCheat, int rawHeld) {
        public RestartIngress {
            Objects.requireNonNull(history, "sonicRecordBuf");
            Objects.requireNonNull(artCpu, "sonicArtBuf");
            Objects.requireNonNull(artResident, "resident Sonic art window");
            if (history.length() != 0x100 || artCpu.length() != CdPlayerArtRuntime.CPU_BUFFER_BYTES
                    || artResident.length() != CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES) {
                throw new IllegalArgumentException("Restart carry requires $100 history and $300 CPU/resident art windows");
            }
            if (((debugAngle | debugAngleShift | debugQuadrant | debugFloorDistance | rawHeld) & ~0xFF) != 0
                    || (debugCheat & ~0xFFFF) != 0) {
                throw new IllegalArgumentException("Restart carry requires native debug/held bytes and a debugCheat word");
            }
        }
    }

    public enum UnsupportedBoundary { VBLANK_PHASE, SOURCE_FAMILY, PAUSED_RESET }
    public static final class UnsupportedFrame extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private UnsupportedFrame(UnsupportedBoundary boundary, String detail) {
            super(boundary + ": " + detail); this.boundary = boundary;
        }
        public UnsupportedBoundary boundary() { return boundary; }
    }

    private record PendingInput(int held, InputSelection selection) { }

    private final CaptureOwner captureOwner = new CaptureOwner();
    private final CdR11aAssets assets;
    private final CdReleaseVariant variant;
    private final CdPlayerBody body;
    private final CdPlayerArtRuntime art;
    private ArtPublication artPublication;
    private CdPlayerBody.State state;
    private CdPlayerBody.Environment environment;
    private Optional<CdPlayerBody.DisplayPublication> display = Optional.empty();
    private VIntClock vIntClock = new VIntClock(OptionalLong.empty(), 0);
    private boolean paused;
    private boolean closed;
    private boolean inOperation;
    private PendingInput pending;
    private boolean bodyEntitled;

    private CdPlayerFrameRuntime(CdR11aAssets assets, ArtPublication publication, CdPlayerBody.State state,
                                 CdPlayerBody.Environment environment, CdPlayerArtRuntime.State artIngress) {
        this.assets = assets;
        variant = Objects.requireNonNull(assets.profile(), "profile").variant();
        body = new CdPlayerBody(assets);
        art = new CdPlayerArtRuntime(Objects.requireNonNull(assets.sonic(), "sonic"), artIngress);
        artPublication = publication;
        this.state = state; this.environment = environment;
    }

    /**
     * Explicit new terrain-only development ingress, not a generic constructor
     * or restore/reset path. Production must supply the authenticated loader's
     * aggregate/load plan, and root must complete actor/renderer/world admission
     * before exposing it as a module service. No data reauthentication occurs here.
     *
     * Objects and header/animation/timers: Main.ClearObjects (186-192), then
     * LoadPlayer ID1 (438-447); positions from LevelSizeLoad_StartPos.DefaultStart
     * (PP Scroll:76-83) via load plan. Physics stays camera-clear zero until
     * Body Init (Sonic/Main:135-155); never eagerly call Body or prime a cue.
     * History uses independent RunMMD.ClearFileBuffer (IPX:1311-1319), not art
     * zeroing; its index/angles/look/warp/physics use Main.ClearCamera (210-216),
     * declarations Level/_Definitions:282-329. Cam Y comes from CamBounds and
     * cam X160 from LevelSizeLoad (PP Scroll:19-34). Main:278-300 clears logical
     * input, boredom/flags/levelFrames. Raw controller bytes are read again by
     * the following fade V-ints (Main:894-895,942-944), so their zero ingress
     * here is DEV policy: no button held at the last represented fade read. Paused0 is Main:35. Art is the separately
     * established completed cold-load projection: CPU300/resident300/last0/clean.
     *
     * Deliberate DEV policy: omit title/HUD/objects/rings, unlock ctrlLocked
     * instead of native title's1 (Main:271-275); admit normal non-mini R11A
     * zoneAct0/timeZone1, no time-attack/debug cheat/boss music and clear debug
     * probe bytes. MAINVARS can carry across native program entries; the factory
     * does not assert all those chosen globals are proved by the art/camera cut.
     * Absolute pre-cut V-int seed remains unknown; only represented delta is0.
     * PauseMusic/UnpauseMusic requests (FM_CHARGESTOP, SCMD_PAUSECDA and
     * SCMD_UNPAUSECDA, Main:620-675) and their music latch are deliberately
     * omitted DEV audio. BodyPublication.sounds is only the Body request stream,
     * not a complete frame/service audio ledger.
     */
    public static CdPlayerFrameRuntime terrainOnlyDevelopment(CdR11aAssets assets, ArtPublication publication) {
        Objects.requireNonNull(publication, "publication");
        return createDevelopment(assets, publication, CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96);
    }

    /**
     * Two-stage root wiring: construct the actor from assets(), claim/full24
     * restore its bank, then bindArtPublication before any service. Unbound
     * construction does not grant a source service even if its first cue is empty.
     */
    public static CdPlayerFrameRuntime terrainOnlyDevelopment(CdR11aAssets assets) {
        return createDevelopment(assets, null, CdPlayerBody.DynamicSlots.EMPTY_DEVELOPMENT_96);
    }

    /**
     * Two-stage player ingress for an external touch owner: reuse the documented
     * cold player initializer, but NEVER declare that dynamic object RAM is empty.
     * Root must bind art before service and supply a fresh, world/source-bound
     * TouchContext to each calling Body slot. Missing input rejects at the real
     * touch gate; Init/absent/native skip gates still need no touch input.
     * This factory retains the existing player-only DEV policies, not the
     * terrain factory's empty-object declaration. It runs no Body/world/load or
     * native work and grants no retail, opening-route or object admission.
     */
    public static CdPlayerFrameRuntime externalTouchRequiredDevelopment(CdR11aAssets assets) {
        return createDevelopment(assets, null, CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
    }

    /**
     * Explicit finite DEV clock policy at the admitted cut, required by owners such as LostRing.
     * This is not a measured retail-entry seed. The original factory retains an unknown seed;
     * no engine frame count, represented delta or comparison observation selects this value.
     * Only the existing Frame service increments and shared partition restores this clock.
     */
    public static CdPlayerFrameRuntime externalTouchRequiredDevelopment(CdR11aAssets assets, long declaredNativeVIntSeed) {
        var declaredClock = new VIntClock(OptionalLong.of(declaredNativeVIntSeed), 0);
        var frame = createDevelopment(assets, null, CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        frame.vIntClock = declaredClock;
        return frame;
    }

    /**
     * Restart sibling of the factory above, for Root's in-module LevelStart reconstruction. A new owner: the fresh
     * Body actor and every other field are exactly this class's cold DEV cut, except the carried values:
     * - sonicRecordBuf ($FFCB00) lies outside every LevelStart clear (Level/Main.asm:170-216) and keeps its bytes;
     *   its index (_Definitions.inc:315) is in the cleared camera RAM, so it is 0;
     * - sonicArtBuf ($FFC800) and VRAM $F000: ClearScreen clears only the planes, scroll and CPU SAT (Functions
     *   (General).asm:182-228); sonicLastFrame/updateSonicArt (_Definitions.inc:285-286) are cleared: 0, clean;
     * - debugAngle..debugFloorDist ($FF157C-$FF157F) and debugCheat ($FF1588) have no LevelStart writer;
     * - rawHeld: :279 clears p1CtrlData, then the fade V-ints reread the pad (:860-862,894-895,942-944). DEV: the
     *   last represented read stands in for them, so a Start held across the restart is no new tap.
     * rawTap and the logical pair stay 0 (:278-279); debugMode and levelFrames stay 0 (:298,:300). The seed is
     * declared by the caller, as for fresh entry. No pending sample, entitlement, pause, display or art callback
     * exists until root binds art. Plain values only: no source, art, input or clock service, no captured context.
     */
    public static CdPlayerFrameRuntime externalTouchRequiredDevelopment(CdR11aAssets assets, long declaredNativeVIntSeed,
                                                                         RestartIngress carried) {
        Objects.requireNonNull(carried, "carried restart ingress");
        var declaredClock = new VIntClock(OptionalLong.of(declaredNativeVIntSeed), 0);
        var cold = coldDevelopmentCut(assets, CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        var s = cold.state();
        var state = new CdPlayerBody.State(s.motion(), s.animation(), s.header(), s.timers(),
                new CdPlayerBody.History(carried.history(), 0), s.rotationDistance(), s.objectLinks());
        var e = cold.environment(); var m = e.movement(); var g = m.globals(); var c = e.controls(); var b = e.globals();
        var globals = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(),
                g.boredTimer(), g.scoreChain(), carried.debugAngle(), carried.debugAngleShift(), carried.debugQuadrant(),
                carried.debugFloorDistance());
        var controls = new CdPlayerBody.Controls(carried.rawHeld(), c.rawTap(), c.logicalHeld(), c.logicalTap(),
                c.controlLocked());
        var bodyGlobals = new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(),
                carried.debugCheat(), b.timeWarpTimer(), b.invincible(), b.bossMusic());
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(),
                m.gates(), globals), controls, bodyGlobals, e.dynamicSlots());
        var frame = new CdPlayerFrameRuntime(assets, null, state, environment,
                new CdPlayerArtRuntime.State(carried.artCpu(), carried.artResident(), 0, false));
        frame.vIntClock = declaredClock;
        return frame;
    }

    private static CdPlayerFrameRuntime createDevelopment(CdR11aAssets assets, ArtPublication publication,
                                                           CdPlayerBody.DynamicSlots dynamicSlots) {
        var cold = coldDevelopmentCut(assets, dynamicSlots);
        return new CdPlayerFrameRuntime(assets, publication, cold.state(), cold.environment(),
                new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false));
    }


    /** Narrow addressed stores during LoadCheckpointData, before this fresh visit can take input. */
    public enum CheckpointLoadField {
        PRIMARY_X(0xFFFF), PRIMARY_Y(0xFFFF), CURRENT_BOTTOM(0xFFFF),
        LEFT_BOUND(0xFFFF), MINI_SONIC(0xFF);
        private final int maximum;
        CheckpointLoadField(int maximum) { this.maximum = maximum; }
    }

    /** Expected/current plain values; the callback-scoped private port supplies load-phase authority. */
    public record CheckpointLoadStore(CheckpointLoadField field, int expectedValue, int value) {
        public CheckpointLoadStore {
            Objects.requireNonNull(field, "checkpoint load field");
            if (((expectedValue | value) & ~field.maximum) != 0) {
                throw new IllegalArgumentException("Checkpoint load store exceeds its native field width");
            }
        }
    }

    @FunctionalInterface public interface CheckpointLoadInitializer {
        void initialize(CheckpointLoadPort port);
    }

    /**
     * Privately minted for one construction callback. No whole-state restore, input, Body,
     * display, art, clock, saved bytes, camera or sound authority exists here. Retained ports
     * reject after either normal return or failure, including after later rewind restore.
     */
    public static final class CheckpointLoadPort {
        private final CdPlayerFrameRuntime owner;
        private boolean active = true;
        private CheckpointLoadPort(CdPlayerFrameRuntime owner) { this.owner = owner; }
        private void requireActive() {
            if (!active || owner.closed || !owner.inOperation || owner.pending != null || owner.bodyEntitled) {
                throw new IllegalStateException("Checkpoint load port is outside its fresh construction cut");
            }
        }
        public int read(CheckpointLoadField field) {
            requireActive(); Objects.requireNonNull(field, "checkpoint load field");
            var motion = owner.state.motion(); var movement = owner.environment.movement();
            return switch (field) {
                case PRIMARY_X -> motion.centreX16_16() >>> 16;
                case PRIMARY_Y -> motion.centreY16_16() >>> 16;
                case CURRENT_BOTTOM -> movement.bounds().bottom();
                case LEFT_BOUND -> movement.bounds().left();
                case MINI_SONIC -> movement.gates().miniSonic();
            };
        }
        public void store(CheckpointLoadStore call) {
            Objects.requireNonNull(call, "checkpoint load store"); requireActive();
            if (read(call.field()) != call.expectedValue()) {
                throw new IllegalArgumentException("Stale checkpoint load field");
            }
            var field = call.field(); int value = call.value();
            if (field == CheckpointLoadField.PRIMARY_X || field == CheckpointLoadField.PRIMARY_Y) {
                var motion = owner.state.motion();
                int x = field == CheckpointLoadField.PRIMARY_X
                        ? (value << 16) | (motion.centreX16_16() & 0xFFFF) : motion.centreX16_16();
                int y = field == CheckpointLoadField.PRIMARY_Y
                        ? (value << 16) | (motion.centreY16_16() & 0xFFFF) : motion.centreY16_16();
                owner.state = checkpointPosition(owner.state, x, y);
                return;
            }
            var e = owner.environment; var movement = e.movement(); var bounds = movement.bounds();
            var gates = movement.gates();
            var nextBounds = field == CheckpointLoadField.CURRENT_BOTTOM
                    ? new CdPlayerMovement.Bounds(bounds.left(), bounds.right(), value, bounds.bossFight())
                    : field == CheckpointLoadField.LEFT_BOUND
                    ? new CdPlayerMovement.Bounds(value, bounds.right(), bounds.bottom(), bounds.bossFight()) : bounds;
            var nextGates = field == CheckpointLoadField.MINI_SONIC
                    ? new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), value, gates.waterSlide(),
                            gates.windTunnel(), gates.speedShoes(), gates.timeWarpDirection()) : gates;
            var nextMovement = new CdPlayerMovement.Environment(movement.physics(), nextBounds, nextGates, movement.globals());
            var nextEnvironment = new CdPlayerBody.Environment(nextMovement, e.controls(), e.globals(), e.dynamicSlots());
            owner.environment = nextEnvironment;
        }
    }

    /**
     * Fresh checkpoint restart ingress. LoadCheckpointData (Load Saved Data.asm:49-84 at
     * scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff) replaces DefaultStart.
     * ClearObjects leaves both coordinate longs zero before the callback; LevelSizeLoad's
     * existing bounds/centres and the explicit restart residue retain their current owners.
     * Root executes the actual ordered saved reads and addressed stores in this callback,
     * then SetupCamera/InitLevelScroll/load-time LevelScroll outside it. No input, native
     * clear replay, SaveData, timer, Body, FM or camera initialization runs in this factory.
     */
    public static CdPlayerFrameRuntime checkpointRestartDevelopment(CdR11aAssets assets, long declaredNativeVIntSeed,
                                                                     RestartIngress carried,
                                                                     CheckpointLoadInitializer initializer) {
        Objects.requireNonNull(initializer, "checkpoint load initializer");
        var owner = externalTouchRequiredDevelopment(assets, declaredNativeVIntSeed, carried);
        owner.state = checkpointPosition(owner.state, 0, 0);
        owner.inOperation = true;
        var port = new CheckpointLoadPort(owner);
        try {
            initializer.initialize(port);
            return owner;
        } catch (RuntimeException | Error failure) {
            owner.closed = true;
            owner.artPublication = null;
            throw failure;
        } finally {
            port.active = false;
            owner.inOperation = false;
        }
    }

    private static CdPlayerBody.State checkpointPosition(CdPlayerBody.State before, int x, int y) {
        var m = before.motion();
        var next = new CdPlayerMotionState(x, y, m.xVelocity(), m.yVelocity(), m.groundVelocity(), m.routine(),
                m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        return new CdPlayerBody.State(next, before.animation(), before.header(), before.timers(), before.history(),
                before.rotationDistance(), before.objectLinks());
    }

    private record ColdCut(CdPlayerBody.State state, CdPlayerBody.Environment environment) { }

    private static ColdCut coldDevelopmentCut(CdR11aAssets assets, CdPlayerBody.DynamicSlots dynamicSlots) {
        Objects.requireNonNull(assets, "assets");
        var plan = Objects.requireNonNull(assets.loadPlan(), "R11A load plan");
        var centre = Objects.requireNonNull(plan.initialCentre(), "initial centre");
        var bounds = Objects.requireNonNull(plan.bounds(), "bounds");
        var motion = new CdPlayerMotionState(centre.centreX() << 16, centre.centreY() << 16,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var state = new CdPlayerBody.State(motion, new CdPlayerBody.AnimationCursor(0, 0, 0),
                new CdPlayerBody.Header(1, 0, 0, 0, 0), new CdPlayerBody.Timers(0, 0, 0, 0), // ClearObjects zeroes $3A too.
                new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[0x100]), 0), 0, new CdPlayerBody.ObjectLinks(0, 0)); // Cold ClearObjects owns byte39 and both object-link bytes.
        var movement = new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0, 0, 0),
                new CdPlayerMovement.Bounds(bounds.left(), bounds.right(), bounds.bottom(), 0),
                new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 0),
                new CdPlayerMovement.Globals(0, bounds.cameraCentreY(), 0, 0, 0, 0, 0, 0, 0, 0));
        var environment = new CdPlayerBody.Environment(movement, new CdPlayerBody.Controls(0, 0, 0, 0, 0),
                new CdPlayerBody.Globals(0, 160, 0, 0, 0, 0, 0, 0), dynamicSlots);
        return new ColdCut(state, environment);
    }

    /** Same immutable aggregate used by Body/art; actor/renderer must not bind a second source. */
    public CdR11aAssets assets() { return assets; }
    public CdPlayerBody.State state() { return state; }
    public CdPlayerBody.Environment environment() { return environment; }
    /** Source eligibility/current pose; root's explicit object-queue port supplies bit7 independently of GPU drawing. */
    public Optional<CdPlayerBody.DisplayPublication> display() { return display; }
    public CdPlayerArtRuntime.State artState() { return art.capture(); }
    /** Read-only represented native clock; an absent absolute seed never means the delta is absolute. */
    public VIntClock vIntClock() { return vIntClock; }
    public boolean paused() { return paused; }
    public boolean closed() { return closed; }

    /**
     * Only a prospective Start selection plus one transient saved held byte.
     * Does NOT read/commit latches, toggle pause, run clocks, art, charge or Body.
     * Last read rawHeld remains the edge baseline across lag/holds/restore.
     */
    public InputSelection prepareInput(int heldByte) {
        requireOpenIdle();
        if ((heldByte & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native held byte");
        requireClosedFrame();
        int tap = (heldByte ^ environment.controls().rawHeld()) & heldByte;
        boolean nextPaused = prospectivePause(tap);
        var selection = new InputSelection(nextPaused,
                nextPaused ? PlcLifecyclePhase.NORMAL_PAUSE : PlcLifecyclePhase.ORDINARY_LEVEL);
        pending = new PendingInput(heldByte, selection);
        return selection;
    }

    /**
     * Named production boundary only. ORDINARY_LEVEL/NORMAL_PAUSE are selector8:
     * ReadController -> LoadSonicDynPLC/fixed dirty DMA -> VInt_Finish(old paused)
     * -> longword count -> Level_MainLoop Start toggle. LAG is selector0 PP:
     * no controller/DPLC/DMA/toggle, but still Finish/count. Every admitted
     * service consumes exactly one pending sample, including lag's unused one.
     * Main:333-349,704-712,738-766,821-847,971-995; General:86-105.
     */
    @Override public void serviceVBlank(PlcLifecyclePhase phase) {
        // Existing terrain DEV owns no level-art globals; its declared omission remains explicit.
        serviceVBlank(phase, () -> { });
    }

    /**
     * Ephemeral source-position call after Sonic DPLC/DMA, before VInt_Finish/count and Start toggle.
     * Selector eight (ordinary or normal pause) calls it once even without a player DMA; PP lag never
     * calls it. The level owner supplies the actual powerup store/service and retains no callback here.
     * A failure preserves completed input/art writes, closes admission and prevents Finish or retry.
     * VInt_Level (Level/Main.asm:829-847), UpdateAnimTiles (Palmtree Panic/Animated Tiles Update.asm:7-8).
     */
    public void serviceVBlank(PlcLifecyclePhase phase, Runnable levelArtService) {
        Objects.requireNonNull(levelArtService, "level-art service at the selector-eight source cut");
        requireOpenIdle();
        boolean advanced = false;
        bodyEntitled = false;
        inOperation = true;
        try {
            requireSupportedPhase(phase); requireSourceFamily();
            if (artPublication == null) throw new IllegalStateException("Root must bind the resident-art publication before service");
            PendingInput sample = pending;
            if (sample == null) throw new IllegalStateException("V-int requires one prepared frame sample");
            if (phase != PlcLifecyclePhase.LAG && phase != sample.selection().phase()) {
                throw new IllegalStateException("Service phase differs from prepared prospective pause disposition");
            }
            if (phase != PlcLifecyclePhase.LAG) {
                var controls = environment.controls();
                int tap = (sample.held() ^ controls.rawHeld()) & sample.held();
                advanced = true; // Raw is now actually ReadController-committed, even if a bank later fails.
                environment = withControls(environment, new CdPlayerBody.Controls(sample.held(), tap,
                        controls.logicalHeld(), controls.logicalTap(), controls.controlLocked()));
                var request = art.serviceVInt(state.header().slotId() != 0, state.animation().mapFrame());
                if (request.isPresent()) artPublication.publish(request.orElseThrow(), art.capture());
                levelArtService.run();
            }
            advanced = true;
            environment = finishTimers(environment, paused, state.motion().charge());
            vIntClock = vIntClock.increment();
            if (phase != PlcLifecyclePhase.LAG) {
                paused = prospectivePause(environment.controls().rawTap()); // Only AFTER Finish/count.
                if (paused && pausedResetRequested(environment.controls().rawTap())) {
                    throw new UnsupportedFrame(UnsupportedBoundary.PAUSED_RESET,
                            "Level_MainLoop paused action reset requires unimplemented restart/life/music owners");
                }
                bodyEntitled = !paused;
            }
        } catch (RuntimeException | Error failure) {
            bodyEntitled = false;
            if (advanced) { closed = true; artPublication = null; }
            throw failure;
        } finally {
            pending = null;
            inOperation = false;
        }
    }

    /**
     * One unpaused native playable slot after its selector8 service. Body stages
     * all writes/effects. Extract sounds before committing any result; an
     * unsupported Body/animation/terrain path has no half-state or sound list.
     * A failed slot consumes its entitlement; another call needs a new service.
     * levelFrames is the engine's ALREADY incremented argument projected low16.
     */
    public BodyPublication runBody(int levelFrames) {
        return runBody(levelFrames, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Ephemeral declared pure input; never retained in shared/actor state or a capture. A
     * ResponseContext's response, contact and world values are equally ephemeral: the Runtime
     * commits the published response's slots, world and contact only after this call returns.
     */
    public BodyPublication runBody(int levelFrames, Optional<CdPlayerBody.TouchContext> touchContext) {
        return runBody(levelFrames, touchContext, Optional.empty(), Optional.empty());
    }

    /**
     * As above, plus the routine-6/8 LevelContext, equally ephemeral: never retained, captured or
     * published. Only ObjSonic_Dead/Restart sample it; the Runtime commits their DeadCheckGone/RestartCut
     * world and global values after this call returns. Without it those routines keep their typed stops.
     */
    public BodyPublication runBody(int levelFrames, Optional<CdPlayerBody.TouchContext> touchContext,
                                   Optional<CdPlayerBody.LevelContext> levelContext) {
        return runBody(levelFrames, touchContext, levelContext, Optional.empty());
    }

    /**
     * Forward only this invocation's borrowed support cut. Body owns its actual reads and local touch
     * overlay; no context or Snapshot enters Frame fields, ActorState, SharedState or the publication.
     * Runtime folds/commits immutable MODE/touch images after this entire call succeeds.
     */
    public BodyPublication runBody(int levelFrames, Optional<CdPlayerBody.TouchContext> touchContext,
                                   Optional<CdPlayerBody.LevelContext> levelContext,
                                   Optional<CdPlayerMovement.ObjectContext> objects) {
        requireBodyEntitlement();
        bodyEntitled = false;
        inOperation = true;
        try {
            var result = body.step(state, withLevelFrames(environment, levelFrames & 0xFFFF), touchContext, levelContext, objects);
            var publication = new BodyPublication(result, collectSounds(result.effects()));
            state = result.state(); environment = result.environment(); display = result.display();
            return publication;
        } finally {
            pending = null;
            inOperation = false;
        }
    }

    /** Engine adapter preflight: distinguish an unserviced call from a failed serviced Body. */
    void requireBodyEntitlement() {
        requireOpenIdle();
        if (pending != null || !bodyEntitled || paused) {
            throw new IllegalStateException("Body requires its unconsumed serviced unpaused slot");
        }
    }

    /** No PLC queue/preparation is owned here; unsupported semantic loops reject at this predicate. */
    @Override public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
        requireSupportedPhase(phase); return false;
    }
    @Override public void prepareAfterLoop(PlcLifecyclePhase phase) { requireSupportedPhase(phase); }

    /** Root may replace/rebind its renderer callback only at a closed frame cut; no implicit upload. */
    public void bindArtPublication(ArtPublication publication) {
        requireOpenIdle(); requireClosedFrame(); artPublication = Objects.requireNonNull(publication, "publication");
    }

    /**
     * Value-CAS for one timeWarpDir.b store at a closed post-Body object cut.
     * ObjTimePost_Main (Monitor and Time Post/Main.asm:163-168, pin
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff) writes FF, then 01 for Future.
     * Root owns the live object identity, source prefix and synchronous call order;
     * these byte values neither certify a call nor grant phase authority.
     */
    public void validateObjectTimePostDirection(int expectedRawDirection, int rawDirection) {
        requireOpenIdle(); requireClosedFrame();
        if ((expectedRawDirection & ~0xFF) != 0 || (rawDirection & ~0xFF) != 0) {
            throw new IllegalArgumentException("Time Post direction requires native unsigned bytes");
        }
        if (environment.movement().gates().timeWarpDirection() != expectedRawDirection) {
            throw new IllegalArgumentException("Stale Time Post direction call cut");
        }
    }

    /**
     * Adopt exactly one native byte through the existing Movement gate owner.
     * Same-value stores are legal. Actor, Display, timers, every other gate/global,
     * art, inputs, clocks, sounds and capture shape remain unchanged.
     */
    public void applyObjectTimePostDirection(int expectedRawDirection, int rawDirection) {
        validateObjectTimePostDirection(expectedRawDirection, rawDirection);
        var m = environment.movement(); var g = m.gates();
        var gates = new CdPlayerMovement.Gates(g.zoneAct(), g.timeZone(), g.miniSonic(), g.waterSlide(),
                g.windTunnel(), g.speedShoes(), rawDirection);
        var movement = new CdPlayerMovement.Environment(m.physics(), m.bounds(), gates, m.globals());
        var next = new CdPlayerBody.Environment(movement, environment.controls(), environment.globals(),
                environment.dynamicSlots());
        environment = next;
    }

    /**
     * ObjGetFloorDist's final shared scratch byte, published by the dynamic object owner after Body.
     * Collision Check.asm:166-175 writes primaryAngle even when its odd value returns zero in d3.
     * This does not write the player's oAngle/previous angles or the secondary scratch buffer.
     */
    public void applyObjectPrimaryAngle(int unsignedByte) {
        requireOpenIdle(); requireClosedFrame();
        if ((unsignedByte & ~0xFF) != 0) throw new IllegalArgumentException("Primary collision angle requires a byte");
        var m = environment.movement(); var g = m.globals();
        var globals = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), unsignedByte, g.secondaryAngle(),
                g.boredTimer(), g.scoreChain(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance());
        var movement = new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(), globals);
        environment = new CdPlayerBody.Environment(movement, environment.controls(), environment.globals(), environment.dynamicSlots());
    }

    /**
     * Obj3DRamp_Main's staged player write, MOVE.W #-$C00 / #$C00,oYVel(a6) (3D Ramp/Main.asm:329-337),
     * published by the dynamic object owner after that body returns and before the next physical dispatch.
     * Changes ONLY the actor's oYVel word. No other motion field, the display projection, timers, history,
     * art, input, clock or body step is touched, and nothing is pre-dispatched. Call at a closed post-Body
     * boundary; semantic phase ownership remains with root.
     */
    public void applyObjectYVelocity(int signedWord) {
        requireOpenIdle(); requireClosedFrame();
        if (signedWord < -0x8000 || signedWord > 0x7FFF) throw new IllegalArgumentException("Object Y velocity requires a signed native word");
        var m = state.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), signedWord, m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        state = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(), state.history(), state.rotationDistance(), state.objectLinks());
    }

    /**
     * Pure value-CAS preflight for one native Spring action after its shared Solid return.
     * Root owns the live object identity/phase and source prefix; values do not prove a call ran.
     * Spring writes integer centre WORDs, nine motion fields at most, and no globals.
     * Both fractions and every actor/slot field outside those exact stores must survive.
     */
    public void validateObjectSpringLaunch(CdSpring.LaunchCall call) {
        requireOpenIdle(); requireClosedFrame(); Objects.requireNonNull(call, "Spring launch call");
        var s = state; var h = s.header(); var g = environment.movement().globals();
        var currentPlayer = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(), h.tile(), h.priority(),
                s.timers().hurt(), s.rotationDistance()), h.slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
        var currentGlobals = new CdSolidObject.Globals(environment.globals().invincible(), g.scoreChain(),
                g.primaryAngle(), g.secondaryAngle());
        if (!call.expectedPlayer().equals(currentPlayer) || !call.expectedGlobals().equals(currentGlobals)) {
            throw new IllegalArgumentException("Stale Spring player/global call cut");
        }
        if (!call.globalWrites().isEmpty() || !call.finalGlobals().equals(call.expectedGlobals())) {
            throw new IllegalArgumentException("Spring action has no native global-write authority");
        }
        var before = call.expectedPlayer(); var next = call.finalPlayer();
        var a = before.actor(); var b = next.actor(); var m = a.motion(); var n = b.motion();
        if (before.slotId() != next.slotId() || before.pushObject() != next.pushObject() || before.standObject() != next.standObject()
                || a.tile() != b.tile() || a.priority() != b.priority() || a.hurt() != b.hurt() || a.rotationDistance() != b.rotationDistance()
                || (m.centreX16_16() & 0xFFFF) != (n.centreX16_16() & 0xFFFF)
                || (m.centreY16_16() & 0xFFFF) != (n.centreY16_16() & 0xFFFF)
                || m.routine() != n.routine() || m.spriteFlags() != n.spriteFlags()
                || m.xRadius() != n.xRadius() || m.yRadius() != n.yRadius()
                || m.previousAnimation() != n.previousAnimation() || m.playerJump() != n.playerJump()
                || m.charge() != n.charge() || m.stick() != n.stick() || m.playerControl() != n.playerControl()
                || m.previousPrimaryAngle() != n.previousPrimaryAngle() || m.previousSecondaryAngle() != n.previousSecondaryAngle()) {
            throw new IllegalArgumentException("Spring launch changes an unowned player field");
        }
        for (var field : CdSpring.LaunchPlayerField.values()) {
            if (!call.playerWrites().contains(field) && springLaunchValue(before, field) != springLaunchValue(next, field)) {
                throw new IllegalArgumentException("Undeclared Spring launch store: " + field);
            }
        }
    }

    /**
     * Adopt the checked motion once at a closed post-Body object cut. MOVE_LOCK is motion's WORD3E;
     * invincibility timer32, the earlier DrawObject projection, animation cursor and all service state stay owned.
     * No pool mutation, sound, source routine, input, art, clock or display publication occurs here.
     */
    public void applyObjectSpringLaunch(CdSpring.LaunchCall call) {
        validateObjectSpringLaunch(call);
        var next = new CdPlayerBody.State(call.finalPlayer().actor().motion(), state.animation(), state.header(), state.timers(),
                state.history(), state.rotationDistance(), state.objectLinks());
        state = next;
    }

    private static int springLaunchValue(CdSolidObject.Player player, CdSpring.LaunchPlayerField field) {
        var m = player.actor().motion();
        return switch (field) {
            case CENTRE_X -> m.centreX16_16() >>> 16;
            case CENTRE_Y -> m.centreY16_16() >>> 16;
            case X_VELOCITY -> m.xVelocity();
            case Y_VELOCITY -> m.yVelocity();
            case GROUND_VELOCITY -> m.groundVelocity();
            case ANIMATION -> m.animation();
            case FLAGS -> m.flags();
            case ANGLE -> m.angle();
            case MOVE_LOCK -> m.moveLock();
        };
    }

    /**
     * Pure expected-motion preflight for ObjSpinTunnel_Main's eight native stores (Spin Tunnel/Main
     * (Tag).asm:47-161, source pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff). CENTRE_Y_WORD is
     * only the integer word; both fractions, the entire X long and flag bits outside $06 are fixed.
     * Same-value executed stores are legal. Motion/PlayerResult constructors enforce native widths
     * and the immutable exact field set. No global, slot or service authority is carried by this value.
     * Root owns physical phase/identity, pool CAS and synchronous publication; values do not prove
     * execution. No Body, animation, collision, service, callback or capture/restore is invoked here.
     */
    public void validateObjectSpinTunnelResult(CdSpinTunnelTag.PlayerResult result) {
        requireOpenIdle(); requireClosedFrame(); Objects.requireNonNull(result, "Spin tunnel player result");
        var m = result.expectedMotion(); var n = result.finalMotion();
        if (!m.equals(state.motion())) {
            throw new IllegalArgumentException("Stale Spin tunnel player motion cut");
        }
        if (m.centreX16_16() != n.centreX16_16()
                || (m.centreY16_16() & 0xFFFF) != (n.centreY16_16() & 0xFFFF)
                || m.routine() != n.routine() || m.angle() != n.angle() || m.spriteFlags() != n.spriteFlags()
                || m.previousAnimation() != n.previousAnimation() || m.playerJump() != n.playerJump()
                || m.charge() != n.charge() || m.moveLock() != n.moveLock() || m.stick() != n.stick()
                || m.playerControl() != n.playerControl() || m.previousPrimaryAngle() != n.previousPrimaryAngle()
                || m.previousSecondaryAngle() != n.previousSecondaryAngle()
                || ((m.flags() ^ n.flags()) & ~0x06) != 0) {
            throw new IllegalArgumentException("Spin tunnel result changes an unowned player field or flag bit");
        }
        for (var field : CdSpinTunnelTag.PlayerField.values()) {
            if (!result.executedWrites().contains(field) && spinTunnelValue(m, field) != spinTunnelValue(n, field)) {
                throw new IllegalArgumentException("Undeclared Spin tunnel player store: " + field);
            }
        }
    }

    /**
     * Adopt only the checked motion once at the existing closed object cut. Reuse the cursor, header,
     * all four timers, history, rotation byte39 and links20/3D. The earlier display and the entire
     * Environment/art/input/clock/service state remain owned by their existing source cuts.
     */
    public void applyObjectSpinTunnelResult(CdSpinTunnelTag.PlayerResult result) {
        validateObjectSpinTunnelResult(result);
        state = new CdPlayerBody.State(result.finalMotion(), state.animation(), state.header(), state.timers(),
                state.history(), state.rotationDistance(), state.objectLinks());
    }

    private static int spinTunnelValue(CdPlayerMotionState motion, CdSpinTunnelTag.PlayerField field) {
        return switch (field) {
            case CENTRE_Y_WORD -> motion.centreY16_16() >>> 16;
            case X_VELOCITY -> motion.xVelocity();
            case Y_VELOCITY -> motion.yVelocity();
            case GROUND_VELOCITY -> motion.groundVelocity();
            case FLAGS -> motion.flags();
            case Y_RADIUS -> motion.yRadius();
            case X_RADIUS -> motion.xRadius();
            case ANIMATION -> motion.animation();
        };
    }

    // BEGIN shared native GiveSpeedShoes value port.
    /**
     * Pure value-CAS at the existing closed post-Body object cut. Native .GiveSpeedShoes
     * (Monitor and Time Post/Main.asm:570-589, pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff)
     * writes only speedShoes.b, oPlayerShoes.w and three shared physics words. Root owns
     * phase/identity and ordered command publication; these plain values prove neither.
     */
    public void validateObjectSpeedShoesReward(CdPlayerSpeedShoesReward.State expectedState,
                                               CdPlayerSpeedShoesReward.Result result) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "speed shoes call state");
        Objects.requireNonNull(result, "speed shoes result");
        var movement = environment.movement();
        var current = new CdPlayerSpeedShoesReward.State(movement.gates().speedShoes(),
                state.timers().speedShoes(), movement.physics());
        if (!expectedState.equals(current)) {
            throw new IllegalArgumentException("Stale speed shoes player/global call cut");
        }
    }

    /**
     * Build all replacement immutable values before assignment, then adopt exactly the five native stores.
     * Same-value stores are legal. Both position fractions, every other actor/global field, earlier Display,
     * history, object links, controls, art, clocks and service state survive. No callback, command playback,
     * ledger, pool, Body, capture/restore, retained input or additional owner token is involved.
     */
    public void applyObjectSpeedShoesReward(CdPlayerSpeedShoesReward.State expectedState,
                                            CdPlayerSpeedShoesReward.Result result) {
        validateObjectSpeedShoesReward(expectedState, result);
        var value = result.state(); var timers = state.timers();
        var nextState = new CdPlayerBody.State(state.motion(), state.animation(), state.header(),
                new CdPlayerBody.Timers(timers.hurt(), timers.invincibility(), value.speedShoesTimer(), timers.reset()),
                state.history(), state.rotationDistance(), state.objectLinks());
        var movement = environment.movement(); var gates = movement.gates();
        var nextGates = new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), gates.miniSonic(),
                gates.waterSlide(), gates.windTunnel(), value.speedShoes(), gates.timeWarpDirection());
        var nextMovement = new CdPlayerMovement.Environment(value.physics(), movement.bounds(), nextGates, movement.globals());
        var nextEnvironment = new CdPlayerBody.Environment(nextMovement, environment.controls(),
                environment.globals(), environment.dynamicSlots());
        state = nextState;
        environment = nextEnvironment;
    }
    // END shared native GiveSpeedShoes value port.

    /**
     * Pure value-CAS preflight for one completed native Object Solid call. Root owns the physical pass,
     * the same call-cut pool capture, synchronous commit order and sounds. Values are not an identity token
     * or proof that a call ran. No callback, pool, sound, display, art, clock or input work occurs here.
     */
    public void validateObjectSolidResult(CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
                                          CdSolidObject.Result result) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedPlayer, "solid call player");
        Objects.requireNonNull(expectedGlobals, "solid call globals");
        Objects.requireNonNull(result, "solid result");
        var s = state; var h = s.header(); var g = environment.movement().globals();
        var currentPlayer = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(), h.tile(), h.priority(),
                s.timers().hurt(), s.rotationDistance()), h.slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
        var currentGlobals = new CdSolidObject.Globals(environment.globals().invincible(), g.scoreChain(),
                g.primaryAngle(), g.secondaryAngle());
        if (!expectedPlayer.equals(currentPlayer) || !expectedGlobals.equals(currentGlobals)) {
            throw new IllegalArgumentException("Stale SolidObject player/global call cut");
        }
        var next = result.player(); var before = expectedPlayer.actor().motion(); var after = next.actor().motion();
        if (next.slotId() != expectedPlayer.slotId() || after.spriteFlags() != before.spriteFlags()
                || after.previousAnimation() != before.previousAnimation() || after.charge() != before.charge()
                || after.moveLock() != before.moveLock() || after.previousPrimaryAngle() != before.previousPrimaryAngle()
                || after.previousSecondaryAngle() != before.previousSecondaryAngle()) {
            throw new IllegalArgumentException("SolidObject result changes an unowned player field");
        }
        for (var field : CdSolidObject.PlayerField.values()) {
            if (!result.playerWrites().contains(field) && solidPlayerValue(expectedPlayer, field) != solidPlayerValue(next, field)) {
                throw new IllegalArgumentException("Undeclared SolidObject player write: " + field);
            }
        }
        for (var field : CdSolidObject.GlobalField.values()) {
            if (!result.globalWrites().contains(field)
                    && solidGlobalValue(expectedGlobals, field) != solidGlobalValue(result.globals(), field)) {
                throw new IllegalArgumentException("Undeclared SolidObject global write: " + field);
            }
        }
    }

    /**
     * Adopt the checked final values once, preserving every field outside the declared native write sets.
     * Root preflights this before pool CAS, then calls it synchronously after that CAS, with no callback between.
     * All new immutable values are built before the first assignment. No ordered call/write metadata is replayed.
     */
    public void applyObjectSolidResult(CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
                                       CdSolidObject.Result result) {
        validateObjectSolidResult(expectedPlayer, expectedGlobals, result);
        var player = result.player(); var actor = player.actor(); var globals = result.globals();
        var h = state.header(); var timers = state.timers();
        var nextState = new CdPlayerBody.State(actor.motion(), state.animation(),
                new CdPlayerBody.Header(h.slotId(), h.mappingAddress(), actor.tile(), actor.priority(), h.width()),
                new CdPlayerBody.Timers(actor.hurt(), timers.invincibility(), timers.speedShoes(), timers.reset()),
                state.history(), actor.rotationDistance(), new CdPlayerBody.ObjectLinks(player.pushObject(), player.standObject()));
        var m = environment.movement(); var g = m.globals(); var body = environment.globals();
        var movementGlobals = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), globals.primaryAngle(),
                globals.secondaryAngle(), g.boredTimer(), globals.scoreChain(), g.debugAngle(), g.debugAngleShift(),
                g.debugQuadrant(), g.debugFloorDistance());
        var nextEnvironment = new CdPlayerBody.Environment(
                new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(), movementGlobals), environment.controls(),
                new CdPlayerBody.Globals(body.levelFrames(), body.cameraXCentre(), body.timeAttackMode(), body.debugMode(),
                        body.debugCheat(), body.timeWarpTimer(), globals.invincible(), body.bossMusic()), environment.dynamicSlots());
        state = nextState;
        environment = nextEnvironment;
    }

    private static int solidPlayerValue(CdSolidObject.Player player, CdSolidObject.PlayerField field) {
        var a = player.actor(); var m = a.motion();
        return switch (field) {
            case TILE -> a.tile();
            case CENTRE_X -> m.centreX16_16() >>> 16;
            case CENTRE_X_FRACTION -> m.centreX16_16() & 0xFFFF;
            case CENTRE_Y -> m.centreY16_16() >>> 16;
            case CENTRE_Y_FRACTION -> m.centreY16_16() & 0xFFFF;
            case X_VELOCITY -> m.xVelocity();
            case Y_VELOCITY -> m.yVelocity();
            case GROUND_VELOCITY -> m.groundVelocity();
            case Y_RADIUS -> m.yRadius();
            case X_RADIUS -> m.xRadius();
            case PRIORITY -> a.priority();
            case ANIMATION -> m.animation();
            case PUSH_OBJECT -> player.pushObject();
            case FLAGS -> m.flags();
            case ROUTINE -> m.routine();
            case ANGLE -> m.angle();
            case PLAYER_CONTROL -> m.playerControl();
            case HURT -> a.hurt();
            case STICK -> m.stick();
            case ROTATION_DISTANCE -> a.rotationDistance();
            case PLAYER_JUMP -> m.playerJump();
            case STAND_OBJECT -> player.standObject();
        };
    }

    private static int solidGlobalValue(CdSolidObject.Globals globals, CdSolidObject.GlobalField field) {
        return switch (field) {
            case INVINCIBLE -> globals.invincible();
            case SCORE_CHAIN -> globals.scoreChain();
            case PRIMARY_ANGLE -> globals.primaryAngle();
            case SECONDARY_ANGLE -> globals.secondaryAngle();
        };
    }

    /**
     * Source scroll/event owner feeds existing shared fields after Body. It owns
     * the camera algorithm and bound changes. No duplicate bounds/centre shadow,
     * source work, input read, level counter or renderer update is performed here.
     */
    public void applyScrollFeedback(CdPlayerMovement.Bounds bounds, int cameraYCenter) {
        requireOpenIdle(); requireClosedFrame(); Objects.requireNonNull(bounds, "bounds");
        var m = environment.movement(); var g = m.globals();
        var globals = new CdPlayerMovement.Globals(g.lookMode(), cameraYCenter, g.primaryAngle(), g.secondaryAngle(),
                g.boredTimer(), g.scoreChain(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance());
        var movement = new CdPlayerMovement.Environment(m.physics(), bounds, m.gates(), globals);
        environment = new CdPlayerBody.Environment(movement, environment.controls(), environment.globals(), environment.dynamicSlots());
    }

    /**
     * Narrow production object-queue publication port, NOT a GPU/draw hook.
     * Object Functions.DrawObject:137-138 clears bit7 before camera/queue tests;
     * DrawObjects.DrawDone:345-346 sets it for a queued loaded actor, even for
     * an empty mapping. Root supplies false after an eligible Body Display call
     * and true after its source camera/queue pass. Root owns eligibility,
     * Display-time position, old camera cull, queue admission, loaded-slot and
     * SAT limits; it must not clear on a Display call skipped by hurt/warp/etc.
     *
     * Body R1's pure call is unchanged: no later admitted own-player bit7 read
     * observes the deferred clear before return. This equivalence is bounded
     * to that body and the admitted R11A touch scan. Only bit7 is
     * changed here in actor motion and its optional current display projection.
     * No cue/input/clock/Body/effect/callback is executed. Call at a closed
     * post-Body boundary; semantic phase ownership remains with root.
     */
    public void publishDrawObjectOnScreen(boolean onScreen) {
        requireOpenIdle(); requireClosedFrame();
        var m = state.motion();
        int bit = onScreen ? 0x80 : 0;
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), (m.spriteFlags() & 0x7F) | bit, m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var nextState = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(), state.history(), state.rotationDistance(), state.objectLinks());
        var nextDisplay = display.map(p -> new CdPlayerBody.DisplayPublication(p.queued(), p.mappingAddress(), p.tile(),
                p.priority(), p.width(), p.mapFrame(), (p.spriteFlags() & 0x7F) | bit, p.centreX16_16(), p.centreY16_16()));
        state = nextState; display = nextDisplay;
    }

    public ActorState captureActor() {
        requireOpenIdle(); requireClosedFrame(); return new ActorState(captureOwner, state, display);
    }
    public SharedState captureShared() {
        requireOpenIdle(); requireClosedFrame(); return new SharedState(captureOwner, environment, art.capture(), paused, vIntClock);
    }
    /** Validate both partition owners before root starts a composite restore; no mutation. */
    public void validateActorRestore(ActorState snapshot) {
        requireOpenIdle(); requireOwner(Objects.requireNonNull(snapshot, "snapshot").owner());
    }
    public void validateSharedRestore(SharedState snapshot) {
        requireOpenIdle(); requireOwner(Objects.requireNonNull(snapshot, "snapshot").owner());
    }
    /** Root owns roster/world guards and post-composite projection; no initialization/effects are replayed. */
    public void restoreActor(ActorState snapshot) {
        validateActorRestore(snapshot);
        state = snapshot.state(); display = snapshot.display(); pending = null; bodyEntitled = false;
    }
    /** Root rebinds the complete resident bank after composite restore; this never invokes publication/service. */
    public void restoreShared(SharedState snapshot) {
        validateSharedRestore(snapshot);
        environment = snapshot.environment(); art.restore(snapshot.artState()); paused = snapshot.paused(); vIntClock = snapshot.vIntClock();
        pending = null; bodyEntitled = false;
    }

    /** Cancel/transition/end-frame cleanup, preserving last actually read raw AND retained logical pairs. */
    public void clearPendingInput() {
        if (inOperation) throw new IllegalStateException("Cannot cancel inside an active source operation");
        pending = null; bodyEntitled = false;
    }
    /** Idempotent teardown; failed admission cannot be reopened by restore or callback replacement. */
    @Override public void close() {
        if (inOperation) throw new IllegalStateException("Cannot close inside an active source operation");
        pending = null; bodyEntitled = false; artPublication = null; closed = true;
    }

    private boolean prospectivePause(int rawTap) {
        // Level_MainLoop:337-345. USA skips Start on routine>=6; ctrlLocked always retains pause.
        boolean startAdmitted = environment.controls().controlLocked() == 0
                && (variant != CdReleaseVariant.USA || state.motion().routine() < 6);
        return startAdmitted && (rawTap & 0x80) != 0 ? !paused : paused;
    }
    private boolean pausedResetRequested(int rawTap) {
        // Level_MainLoop:357-395: retail USA any ABC, JP/EU all ABC; time attack any ABC.
        int action = rawTap & 0x70;
        return variant == CdReleaseVariant.USA || environment.globals().timeAttackMode() != 0
                ? action != 0 : action == 0x70;
    }
    private void requireSourceFamily() {
        var gates = environment.movement().gates();
        if (gates.zoneAct() != 0 || gates.timeZone() != 1) {
            throw new UnsupportedFrame(UnsupportedBoundary.SOURCE_FAMILY, "Only PPZ R11A present service is admitted");
        }
    }
    private static void requireSupportedPhase(PlcLifecyclePhase phase) {
        if (phase != PlcLifecyclePhase.ORDINARY_LEVEL && phase != PlcLifecyclePhase.NORMAL_PAUSE && phase != PlcLifecyclePhase.LAG) {
            throw new UnsupportedFrame(UnsupportedBoundary.VBLANK_PHASE, String.valueOf(phase));
        }
    }
    private void requireOpenIdle() {
        if (closed) throw new IllegalStateException("CD player frame admission is closed; a known rebuild is required");
        if (inOperation) throw new IllegalStateException("Reentrant CD frame mutation is not admitted");
    }
    private void requireClosedFrame() {
        if (pending != null || bodyEntitled) throw new IllegalStateException("A closed frame cut requires no pending input or body entitlement");
    }
    private void requireOwner(CaptureOwner owner) {
        if (owner != captureOwner) throw new IllegalArgumentException("Snapshot belongs to another CD runtime/source owner");
    }

    /** VInt_Finish/RunBoredTimer/RunTimeWarp: old paused gates active words; charge gates only warp. */
    private static CdPlayerBody.Environment finishTimers(CdPlayerBody.Environment e, boolean oldPaused, int charge) {
        if (oldPaused) return e;
        var m = e.movement(); var g = m.globals(); var b = e.globals();
        int bored = g.boredTimer() == 0 ? 0 : (g.boredTimer() + 1) & 0xFFFF;
        int warp = charge != 0 || b.timeWarpTimer() == 0 ? b.timeWarpTimer() : (b.timeWarpTimer() + 1) & 0xFFFF;
        var globals = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(),
                bored, g.scoreChain(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance());
        var movement = new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(), globals);
        var bodyGlobals = new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(),
                b.debugCheat(), warp, b.invincible(), b.bossMusic());
        return new CdPlayerBody.Environment(movement, e.controls(), bodyGlobals, e.dynamicSlots());
    }
    private static CdPlayerBody.Environment withControls(CdPlayerBody.Environment e, CdPlayerBody.Controls controls) {
        return new CdPlayerBody.Environment(e.movement(), controls, e.globals(), e.dynamicSlots());
    }
    private static CdPlayerBody.Environment withLevelFrames(CdPlayerBody.Environment e, int levelFrames) {
        var g = e.globals();
        var globals = new CdPlayerBody.Globals(levelFrames, g.cameraXCentre(), g.timeAttackMode(), g.debugMode(),
                g.debugCheat(), g.timeWarpTimer(), g.invincible(), g.bossMusic());
        return new CdPlayerBody.Environment(e.movement(), e.controls(), globals, e.dynamicSlots());
    }
    private static List<Sound> collectSounds(List<CdPlayerBody.Effect> effects) {
        var sounds = new ArrayList<Sound>();
        for (var effect : effects) {
            if (effect instanceof CdPlayerBody.SoundRequest sound) sounds.add(new BodySound(sound.sound()));
            else if (effect instanceof CdPlayerBody.ChargeEffect charge) collectChargeSound(charge.effect(), sounds);
            else if (effect instanceof CdPlayerBody.ModeEffect mode) collectMovementSound(mode.effect(), sounds);
            else if (effect instanceof CdPlayerBody.AirCollisionEffect air) collectMovementSound(air.effect(), sounds);
            else if (effect instanceof CdPlayerBody.ObjectTouchResponse touch) collectContactSounds(touch.result().calls(), sounds);
            else if (effect instanceof CdPlayerBody.KillPlayerCall kill) collectContactSounds(kill.result().calls(), sounds);
            else if (effect instanceof CdPlayerBody.Z80RamWrite driver) sounds.add(new DriverRamWrite(driver.address(), driver.value()));
        }
        return List.copyOf(sounds);
    }
    private static void collectChargeSound(CdPlayerCharge.Effect effect, List<Sound> sounds) {
        if (effect instanceof CdPlayerCharge.SoundRequest sound) sounds.add(new ChargeSound(sound.sound()));
    }
    /** MODE and Hurt's shared collision call publish the same Movement-owned request types. */
    private static void collectMovementSound(CdPlayerMovement.Effect effect, List<Sound> sounds) {
        if (effect instanceof CdPlayerMovement.SoundRequest sound) sounds.add(new MovementSound(sound.sound()));
        else if (effect instanceof CdPlayerMovement.ChargeEffect charge) collectChargeSound(charge.effect(), sounds);
    }
    /** Only PlayFMSound/SubCPUCmd requests, in call order; FindObjSlot, AddPoints and ResetOnFloor are metadata. */
    private static void collectContactSounds(List<CdPlayerTouchResponse.Call> calls, List<Sound> sounds) {
        for (var call : calls) {
            if (call instanceof CdPlayerTouchResponse.PlayFm fm) sounds.add(new ContactFmSound(fm.sound()));
            else if (call instanceof CdPlayerTouchResponse.SubCpu command) sounds.add(new ContactSubCpuCommand(command.command()));
        }
    }
}
