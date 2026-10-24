package com.openggf.game.soniccd.player;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssets;
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
    public sealed interface Sound permits BodySound, ChargeSound, MovementSound { }
    public record BodySound(CdPlayerBody.Sound sound) implements Sound {
        public BodySound { Objects.requireNonNull(sound); }
    }
    public record ChargeSound(CdPlayerCharge.Sound sound) implements Sound {
        public ChargeSound { Objects.requireNonNull(sound); }
    }
    public record MovementSound(CdPlayerMovement.Sound sound) implements Sound {
        public MovementSound { Objects.requireNonNull(sound); }
    }
    /** Final values already committed once; ordered effects are observations, not write-replay instructions. */
    public record BodyPublication(CdPlayerBody.Result result, List<Sound> sounds) {
        public BodyPublication { Objects.requireNonNull(result); sounds = List.copyOf(sounds); }
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

    private CdPlayerFrameRuntime(CdR11aAssets assets, ArtPublication publication,
                                 CdPlayerBody.State state, CdPlayerBody.Environment environment) {
        this.assets = assets;
        variant = Objects.requireNonNull(assets.profile(), "profile").variant();
        body = new CdPlayerBody(assets);
        art = new CdPlayerArtRuntime(Objects.requireNonNull(assets.sonic(), "sonic"),
                new CdPlayerArtRuntime.State(new byte[0x300], new byte[0x300], 0, false));
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

    private static CdPlayerFrameRuntime createDevelopment(CdR11aAssets assets, ArtPublication publication,
                                                           CdPlayerBody.DynamicSlots dynamicSlots) {
        Objects.requireNonNull(assets, "assets");
        var plan = Objects.requireNonNull(assets.loadPlan(), "R11A load plan");
        var centre = Objects.requireNonNull(plan.initialCentre(), "initial centre");
        var bounds = Objects.requireNonNull(plan.bounds(), "bounds");
        var motion = new CdPlayerMotionState(centre.centreX() << 16, centre.centreY() << 16,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var state = new CdPlayerBody.State(motion, new CdPlayerBody.AnimationCursor(0, 0, 0),
                new CdPlayerBody.Header(1, 0, 0, 0, 0), new CdPlayerBody.Timers(0, 0, 0),
                new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[0x100]), 0));
        var movement = new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0, 0, 0),
                new CdPlayerMovement.Bounds(bounds.left(), bounds.right(), bounds.bottom(), 0),
                new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 0),
                new CdPlayerMovement.Globals(0, bounds.cameraCentreY(), 0, 0, 0, 0, 0, 0, 0, 0));
        var environment = new CdPlayerBody.Environment(movement, new CdPlayerBody.Controls(0, 0, 0, 0, 0),
                new CdPlayerBody.Globals(0, 160, 0, 0, 0, 0, 0, 0), dynamicSlots);
        return new CdPlayerFrameRuntime(assets, publication, state, environment);
    }

    /** Same immutable aggregate used by Body/art; actor/renderer must not bind a second source. */
    public CdR11aAssets assets() { return assets; }
    public CdPlayerBody.State state() { return state; }
    public CdPlayerBody.Environment environment() { return environment; }
    /** Source eligibility/current pose; root's explicit object-queue port supplies bit7 independently of GPU drawing. */
    public Optional<CdPlayerBody.DisplayPublication> display() { return display; }
    public CdPlayerArtRuntime.State artState() { return art.capture(); }
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
        return runBody(levelFrames, Optional.empty());
    }

    /** Ephemeral declared pure input; never retained in shared/actor state or a capture. */
    public BodyPublication runBody(int levelFrames, Optional<CdPlayerBody.TouchContext> touchContext) {
        requireBodyEntitlement();
        bodyEntitled = false;
        inOperation = true;
        try {
            var result = body.step(state, withLevelFrames(environment, levelFrames & 0xFFFF), touchContext);
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
        state = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(), state.history());
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
        var nextState = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(), state.history());
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
            else if (effect instanceof CdPlayerBody.ModeEffect mode) {
                if (mode.effect() instanceof CdPlayerMovement.SoundRequest sound) sounds.add(new MovementSound(sound.sound()));
                else if (mode.effect() instanceof CdPlayerMovement.ChargeEffect charge) collectChargeSound(charge.effect(), sounds);
            }
        }
        return List.copyOf(sounds);
    }
    private static void collectChargeSound(CdPlayerCharge.Effect effect, List<Sound> sounds) {
        if (effect instanceof CdPlayerCharge.SoundRequest sound) sounds.add(new ChargeSound(sound.sound()));
    }
}
