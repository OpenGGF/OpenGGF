package com.openggf.game.soniccd.player;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR12aTerrainAssets;
import com.openggf.game.soniccd.level.CdR12aPlayerAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bPlayerAssets;
import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdR31aAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.objects.CdBigRing;
import com.openggf.game.soniccd.objects.CdR13Boss;
import com.openggf.game.soniccd.objects.CdResults;
import com.openggf.game.soniccd.objects.CdSolidObject;
import com.openggf.game.soniccd.objects.CdSignpost;
import com.openggf.game.soniccd.objects.CdSpinningDisc;
import com.openggf.game.soniccd.objects.CdSpring;
import com.openggf.game.soniccd.objects.CdSpringboard;
import com.openggf.game.soniccd.objects.CdSpinTunnelTag;
import com.openggf.game.soniccd.objects.CdTunnelPath;
import com.openggf.game.soniccd.objects.CdTimeWarpLoadData;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Finite CD-private source-bound player frame-state/service owner.
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
        applyPreparedObjectSolidPublication(prepareObjectPreHurtYRewind(expectedActor, finalYLongBits));
    }

    /** Prepare the existing checked Y-only formula without adopting either Frame aggregate. */
    public ObjectSolidPublication prepareObjectPreHurtYRewind(CdPlayerTouchResponse.Actor expectedActor, int finalYLongBits) {
        validateObjectPreHurtYRewind(expectedActor, finalYLongBits);
        var expectedState = state; var expectedEnvironment = environment;
        var m = expectedActor.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), finalYLongBits, m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(),
                m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var nextState = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(),
                state.history(), state.rotationDistance(), state.objectLinks(), state.rotationAngle());
        return new ObjectSolidPublication(expectedState, expectedEnvironment, nextState, expectedEnvironment);
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
        applyPreparedObjectSolidPublication(prepareObjectHurtResult(expectedActor, expectedPlayer, result));
    }

    /**
     * Prepare direct HurtPlayer's already-validated owning formulas before any caller pool publication.
     * This four-value carrier is reusable observational State/Environment CAS data, never invocation,
     * Frame identity, contact/world/slot ownership, native-call or exactly-once authority.
     */
    public ObjectSolidPublication prepareObjectHurtResult(CdPlayerTouchResponse.Actor expectedActor,
                                                         CdPlayerTouchResponse.PlayerGlobals expectedPlayer,
                                                         CdPlayerTouchResponse.Result result) {
        validateObjectHurtResult(expectedActor, expectedPlayer, result);
        var expectedState = state; var expectedEnvironment = environment;
        if (result.exit() == CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE)
            return new ObjectSolidPublication(expectedState, expectedEnvironment, expectedState, expectedEnvironment);
        boolean kill = result.exit() == CdPlayerTouchResponse.Exit.KILL_PLAYER;
        var actor = result.actor(); var globals = result.player(); var h = state.header(); var timers = state.timers();
        var nextHeader = kill ? new CdPlayerBody.Header(h.slotId(), h.mappingAddress(), actor.tile(), actor.priority(), h.width()) : h;
        var nextTimers = kill ? timers : new CdPlayerBody.Timers(actor.hurt(), timers.invincibility(), timers.speedShoes(), timers.reset());
        var nextState = new CdPlayerBody.State(actor.motion(), state.animation(), nextHeader, nextTimers,
                state.history(), actor.rotationDistance(), state.objectLinks(), state.rotationAngle());
        var m = environment.movement(); var g = m.globals(); var b = environment.globals();
        var movementGlobals = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), g.primaryAngle(), g.secondaryAngle(),
                g.boredTimer(), globals.scoreChain(), g.debugAngle(), g.debugAngleShift(), g.debugQuadrant(), g.debugFloorDistance());
        var nextBodyGlobals = kill ? new CdPlayerBody.Globals(b.levelFrames(), b.cameraXCentre(), b.timeAttackMode(), b.debugMode(),
                b.debugCheat(), b.timeWarpTimer(), globals.invincible(), b.bossMusic()) : b;
        var nextEnvironment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(),
                movementGlobals), environment.controls(), nextBodyGlobals, environment.dynamicSlots());
        return new ObjectSolidPublication(expectedState, expectedEnvironment, nextState, nextEnvironment);
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
            DriverRamWrite, R31NativeSound { }
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

    // BEGIN scoped original R31 Body publication values 20261107.
    /** The three actual R31 request forms only, at their original Body effect positions.
     * Native IDs/selector inputs are DATA, not playback, SubCPU acknowledgement or World authority. */
    public record R31NativeSound(CdPlayerBody.R31Action request) implements Sound {
        public R31NativeSound {
            Objects.requireNonNull(request, "original R31 sound request");
            if (!(request instanceof CdPlayerBody.R31FmRequest)
                    && !(request instanceof CdPlayerBody.R31SubCpuRequest)
                    && !(request instanceof CdPlayerBody.R31MusicRequest)) {
                throw new IllegalArgumentException("Only original R31 FM, SubCPU or stage-music requests");
            }
        }
    }

    /** Complete immutable component output only. Equal values cannot construct an original call. */
    public record R31Publication(BodyPublication body, CdPlayerBody.R31Values nativeValues) {
        public R31Publication { Objects.requireNonNull(body); Objects.requireNonNull(nativeValues); }
    }

    /** Actual caller prebuilds all external owners and validates its private World/source cut BEFORE
     * original.applyNative(). Complete pool/chunk/plane/global/music adoption remains caller-owned. */
    public interface R31PublicationPort {
        void publish(R31BodyCall original);
    }

    /** Opt-in original PlaceBlock consumer. Prebuild ALL external state/cache/plane/sound changes,
     * revalidate the full external cut and this receipt, apply once, then adopt only built references.
     * Screen verification supplies no predicate/operands: Body alone evaluates its native branch. */
    public interface R31PlaceBlockPort {
        void store(R31PlaceBlockCall original);
        default void screen(R31PlaceBlockScreenCall original) { original.verifyNative(); }
    }
    private final class R31Invocation {
        final Object identity=new Object();
        final CdStagePlayerSource source;
        final Thread thread=Thread.currentThread();
        CdPlayerBody.R31Prefix last;
        int sequence;
        boolean published;
        R31Invocation(CdPlayerBody.R31Context context) {
            source=context.source();
            last=new CdPlayerBody.R31Prefix(state,environment,
                    new CdPlayerBody.R31Values(context.currentTerrain(),context.snapshot(),context.noBumperByte(),
                            context.response().contact(),context.response().world()),List.of());
        }
    }
    /** Private constructors and pending identity authenticate only this original synchronous Frame
     * invocation. Immutable prefixes/sounds are DATA. No captured/mid-call continuation exists. */
    public abstract sealed class R31PlaceCall permits R31PlaceBlockCall,R31PlaceBlockScreenCall {
        private final R31Invocation invocation;
        private final Object identity;
        private final int sequence;
        private final CdR31SpecialBlockCollision.PlaceBlock operation;
        private final CdR31SpecialBlockCollision.Source helperSource;
        private final CdPlayerBody.R31Prefix before;
        private final Optional<CdPlayerBody.DisplayPublication> expectedDisplay=display;
        private final CdPlayerArtRuntime.State expectedArt=art.capture();
        private final VIntClock expectedClock=vIntClock;
        private final boolean expectedPaused=paused;
        private boolean applied;
        private R31PlaceCall(CdR31SpecialBlockCollision.PlaceBlock operation) {
            invocation=Objects.requireNonNull(r31Invocation);identity=invocation.identity;
            sequence=++invocation.sequence;this.operation=operation;helperSource=operation.source();before=invocation.last;
        }
        public final int sequence(){return sequence;}
        public final int operationOrdinal(){return operation.ordinal();}
        public final int xWord(){return operation.xWord();}
        public final int yWord(){return operation.yWord();}
        public final CdR31SpecialBlockCollision.Site site(){return operation.site();}
        public final CdStagePlayerSource source(){return invocation.source;}
        public final CdPlayerBody.R31Prefix before(){return before;}
        public final Optional<CdPlayerBody.DisplayPublication> display(){return expectedDisplay;}
        public final CdPlayerArtRuntime.State artState(){return expectedArt;}
        public final VIntClock clock(){return expectedClock;}
        public final boolean paused(){return expectedPaused;}
        public final void requireOwner(CdPlayerFrameRuntime expected){
            if(expected!=CdPlayerFrameRuntime.this)throw new IllegalArgumentException("Foreign PlaceBlock Frame");
            verifyCut();
            if(applied)throw new IllegalStateException("Original PlaceBlock receipt already consumed");
        }
        private void verifyCut(){
            if(Thread.currentThread()!=invocation.thread||!inOperation||closed||bodyEntitled||pending!=null
                    ||r31Invocation!=invocation||identity!=invocation.identity||pendingR31Place!=this
                    ||pendingR31Body!=null||sequence!=invocation.sequence||invocation.source!=pastSource
                    ||display!=expectedDisplay||art.capture()!=expectedArt||vIntClock!=expectedClock||paused!=expectedPaused)
                throw new IllegalStateException("Expired/foreign original PlaceBlock invocation/phase");
            operation.current();
            if(operation.source()!=helperSource)throw new IllegalStateException("Changed original helper source");
            var cut=applied?invocation.last:before;
            if(state!=cut.state()||environment!=cut.environment())
                throw new IllegalStateException("Changed complete PlaceBlock Frame pair");
        }
        final void verifyReturned(boolean normal){
            verifyCut();
            if(normal&&!applied)throw new IllegalStateException("Original PlaceBlock receipt not consumed");
        }
        final void consume(){requireOwner(CdPlayerFrameRuntime.this);applied=true;}
    }
    public final class R31PlaceBlockCall extends R31PlaceCall {
        private final CdPlayerBody.R31PlaceStore store;
        private final CdPlayerBody.R31Prefix prefix;
        private final List<Sound> sounds;
        private R31PlaceBlockCall(CdR31SpecialBlockCollision.PlaceBlock operation,
                                 CdPlayerBody.R31PlaceStore store,CdPlayerBody.R31Prefix prefix){
            super(operation);this.store=store;this.prefix=prefix;sounds=collectR31Sounds(prefix.effects());
        }
        public CdPlayerBody.R31PlaceStore store(){return store;}
        public CdPlayerBody.R31Prefix prefix(){return prefix;}
        /** Complete reached sound prefix, never playback authority; consumer retains an exact ledger. */
        public List<Sound> sounds(){return sounds;}
        public void applyNative(){
            consume();state=prefix.state();environment=prefix.environment();
            r31Invocation.last=prefix;r31Invocation.published=true;
        }
    }
    /** Original camera lookup/verification cut after the chunk store; no write or caller predicate. */
    public final class R31PlaceBlockScreenCall extends R31PlaceCall {
        private R31PlaceBlockScreenCall(CdR31SpecialBlockCollision.PlaceBlock operation){super(operation);}
        public void verifyNative(){consume();}
    }
    private void invokeR31Place(R31PlaceCall call,Runnable action){
        if(pendingR31Place!=null)throw new IllegalStateException("Original PlaceBlock port reentry");
        pendingR31Place=call;
        try{
            action.run();call.verifyReturned(true);
        }catch(RuntimeException|Error original){
            try{call.verifyReturned(false);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}
            throw original;
        }finally{pendingR31Place=null;}
    }

    /** One original, synchronous pending Body call, never a captured/resumable capability.
     * publication() is immutable DATA; requireOwner/prepareDisplayFlag/applyNative have a live scope.
     * The real World separately authenticates its own retained source/epoch/phase/full native cut. */
    public final class R31BodyCall {
        private final R31Publication publication;
        private final CdStagePlayerSource source;
        private final CdPlayerBody.State expectedState, readyState;
        private final CdPlayerBody.Environment expectedEnvironment, readyEnvironment;
        private final Optional<CdPlayerBody.DisplayPublication> expectedDisplay, readyDisplay;
        private final CdPlayerArtRuntime.State expectedArt;
        private final VIntClock expectedClock;
        private final boolean expectedPaused;
        private final Thread invokingThread;
        private final R31Invocation invocation;
        private final Optional<CdPlayerBody.R31Prefix> lastPrefix;
        // BEGIN pending R31 primary display flag data 20261107.
        private CdPlayerBody.State readyDisplayState;
        private boolean displayPrepared;
        // END pending R31 primary display flag data 20261107.
        private boolean applied;

        private R31BodyCall(R31Publication publication, CdStagePlayerSource source) {
            this.publication = publication; this.source = source;
            invocation=r31Invocation;
            lastPrefix=invocation==null?Optional.empty():Optional.of(invocation.last);
            expectedState = state; expectedEnvironment = environment; expectedDisplay = display;
            expectedArt = art.capture(); expectedClock = vIntClock; expectedPaused = paused;
            invokingThread = Thread.currentThread();
            var result = publication.body().result();
            readyState = result.state(); readyEnvironment = result.environment(); readyDisplay = result.display();
        }

        /** Original Body output BEFORE caller-owned Draw, including its original state/display flags.
         * A prepared Draw state is separate DATA returned by prepareDisplayFlag; compare that returned
         * state against the final actual Frame after applyNative. This publication never replays Draw. */
        public R31Publication publication() { return publication; }
        /** Complete last published native prefix (or actual ingress before any store), for external
         * preflight. Final apply compares the current pair here, never the stale original ingress. */
        public Optional<CdPlayerBody.R31Prefix> lastPrefix(){return lastPrefix;}

        // BEGIN pending R31 primary display preparation 20261107.
        /**
         * Prebuild only the native primary oSprFlags bit7 value inside this original pending call.
         * Native DrawObject clears bit7 (Object Functions.asm:137-138); the sprite-build tail sets it
         * at .DrawDone (:342-346), N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Actual World/Pass must
         * prove its original Draw/queue branch, operation order, full external cut and final flags.
         * Presence of Body display, this operand, or a successful queue DTO grants no such authority.
         *
         * Returned immutable State8 is DATA for the caller's complete prebuild. Preparation mutates
         * no Frame, environment, display, art, clock, queue, Body result or effects. Only the original
         * applyNative can adopt this private prepared reference, once, while the existing scope lives.
         */
        public CdPlayerBody.State prepareDisplayFlag(int spriteFlags) {
            requireOwner(CdPlayerFrameRuntime.this);
            if (displayPrepared) throw new IllegalStateException("Original R31 display flag already prepared");
            if (readyDisplay.isEmpty()) throw new IllegalStateException("Original R31 Body has no display");
            if ((spriteFlags & ~0xFF) != 0) throw new IllegalArgumentException("R31 display flag requires an unsigned byte");
            var motion = readyState.motion();
            if (((spriteFlags ^ motion.spriteFlags()) & 0x7F) != 0)
                throw new IllegalArgumentException("R31 Draw may change only spriteFlags bit7");
            var nextMotion = new CdPlayerMotionState(motion.centreX16_16(), motion.centreY16_16(),
                    motion.xVelocity(), motion.yVelocity(), motion.groundVelocity(), motion.routine(),
                    motion.angle(), motion.flags(), spriteFlags, motion.xRadius(), motion.yRadius(),
                    motion.animation(), motion.previousAnimation(), motion.playerJump(), motion.charge(),
                    motion.moveLock(), motion.stick(), motion.playerControl(), motion.previousPrimaryAngle(),
                    motion.previousSecondaryAngle());
            var nextState = new CdPlayerBody.State(nextMotion, readyState.animation(), readyState.header(),
                    readyState.timers(), readyState.history(), readyState.rotationDistance(),
                    readyState.objectLinks(), readyState.rotationAngle());
            readyDisplayState = nextState;
            displayPrepared = true;
            return nextState;
        }
        // END pending R31 primary display preparation 20261107.

        /** Actual private pending identity plus complete Frame preimage, not value-CAS admission. */
        public void requireOwner(CdPlayerFrameRuntime expected) {
            Objects.requireNonNull(expected, "actual R31 Frame owner");
            if (expected != CdPlayerFrameRuntime.this) throw new IllegalArgumentException("Foreign R31 Frame call owner");
            verifyCut();
            if(applied)throw new IllegalStateException("Original R31 Body call already applied");
        }
        private void verifyCut(){
            var actualState=applied?(displayPrepared?readyDisplayState:readyState):expectedState;
            var actualEnvironment=applied?readyEnvironment:expectedEnvironment;
            var actualDisplay=applied?readyDisplay:expectedDisplay;
            if (Thread.currentThread() != invokingThread || !inOperation || pendingR31Body != this
                    || closed || bodyEntitled || pending != null || pendingR31Place!=null
                    || r31Invocation!=invocation || (invocation!=null&&invocation.last!=lastPrefix.orElseThrow())
                    || (invocation!=null&&!applied&&(state!=invocation.last.state()||environment!=invocation.last.environment()))
                    || source != pastSource || state != actualState || environment != actualEnvironment
                    || display != actualDisplay || art.capture() != expectedArt || vIntClock != expectedClock
                    || paused != expectedPaused) {
                throw new IllegalStateException("No current original R31 Body call/full Frame cut");
            }
        }
        private void verifyReturned(boolean normal){
            verifyCut();
            if(normal&&!applied)throw new IllegalStateException("Original R31 Body call was not applied");
        }

        /** Validate first, then assign only already-built Frame references. No effects/services replay. */
        public void applyNative() {
            requireOwner(CdPlayerFrameRuntime.this);
            var finalState = displayPrepared ? Objects.requireNonNull(readyDisplayState, "Original prepared R31 display state") : readyState;
            state = finalState; environment = readyEnvironment; display = readyDisplay;
            applied = true;
        }
    }
    // END scoped original R31 Body publication values.

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
    private final CdStagePlayerSource pastSource;
    private CdStagePlayerSource presentSource;
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
    // Meaningful only while bodyEntitled; no capture or independent service authority.
    private boolean mainFrameAdvanced;
    // Original R31 call exists only on its synchronous inOperation stack; never captured.
    private R31BodyCall pendingR31Body;
    private R31Invocation r31Invocation;
    private R31PlaceCall pendingR31Place;

    private CdPlayerFrameRuntime(CdR11aAssets assets, ArtPublication publication, CdPlayerBody.State state,
                                 CdPlayerBody.Environment environment, CdPlayerArtRuntime.State artIngress) {
        this.assets = assets;
        pastSource = null;
        variant = Objects.requireNonNull(assets.profile(), "profile").variant();
        body = new CdPlayerBody(assets);
        art = new CdPlayerArtRuntime(Objects.requireNonNull(assets.sonic(), "sonic"), artIngress);
        artPublication = publication;
        this.state = state; this.environment = environment;
    }


    private CdPlayerFrameRuntime(CdStagePlayerSource source, CdPlayerBody.State state,
                                 CdPlayerBody.Environment environment, CdPlayerArtRuntime.State artIngress,
                                 VIntClock clock) {
        assets = null;
        pastSource = source;
        variant = source.variant();
        body = CdPlayerBody.fromStageSource(source);
        art = new CdPlayerArtRuntime(source.sonic(), artIngress);
        this.state = state; this.environment = environment; vIntClock = clock;
    }

    /**
     * Fresh actual-Past source binding over complete existing component values. The caller obtains
     * assets from the authenticated original-USA loader and owns actual entry/load-phase authority.
     * This value ingress neither proves nor executes native clears, the six saved primary stores,
     * LoadPlayer, Init, art service or any loading wait. It supplies no DEV defaults or restart carry.
     * The source and CaptureOwner are fresh for this visit; restore cannot retarget either one.
     * Art publication remains unbound until the caller claims and restores its resident bank.
     */
    public static CdPlayerFrameRuntime fromPastEntry(CdR11bAssets assets, CdPlayerBody.State state,
                                                      CdPlayerBody.Environment environment,
                                                      CdPlayerArtRuntime.State artIngress, VIntClock clock) {
        Objects.requireNonNull(assets, "Past assets");
        Objects.requireNonNull(state, "Past player state");
        Objects.requireNonNull(environment, "Past player environment");
        Objects.requireNonNull(artIngress, "Past player art ingress");
        Objects.requireNonNull(clock, "Past player V-int clock");
        return new CdPlayerFrameRuntime(CdStagePlayerSource.fromPast(assets), state, environment, artIngress, clock);
    }

    /**
     * Fresh actual-R12A binding over complete explicit existing values. The detached terrain/player
     * pair supplies its actual native act, ordinary period and mapping through the existing source
     * relation. This factory performs no Init, native clear, actor/camera/art/clock default, service,
     * programme carry or World admission. Its source and CaptureOwner are fresh; art publication
     * remains unbound, and existing ordinary/old-programme service guards retain their own scope.
     */
    public static CdPlayerFrameRuntime fromAct2Entry(CdR12aTerrainAssets terrain, CdR12aPlayerAssets player,
                                                     CdPlayerBody.State state, CdPlayerBody.Environment environment,
                                                     CdPlayerArtRuntime.State artIngress, VIntClock clock) {
        Objects.requireNonNull(terrain, "Act2 terrain assets");
        Objects.requireNonNull(player, "Act2 player assets");
        Objects.requireNonNull(state, "Act2 player state");
        Objects.requireNonNull(environment, "Act2 player environment");
        Objects.requireNonNull(artIngress, "Act2 player art ingress");
        Objects.requireNonNull(clock, "Act2 player V-int clock");
        return new CdPlayerFrameRuntime(CdStagePlayerSource.fromAct2(terrain, player), state, environment, artIngress, clock);
    }

    /**
     * Fresh actual-R12B binding over complete explicit existing values. The authenticated
     * terrain/player pair supplies native act1, ordinary Past0 and its own mapping through
     * the existing source relation. This ingress performs no Init, native clear, saved load,
     * actor/camera/art/clock default, service, programme carry or World admission. The source
     * and CaptureOwner are fresh for this visit; art publication remains unbound. Actual
     * entry/load ordering and every native writer retain their existing private caller.
     */
    public static CdPlayerFrameRuntime fromAct2PastEntry(CdR12bTerrainAssets terrain, CdR12bPlayerAssets player,
                                                         CdPlayerBody.State state, CdPlayerBody.Environment environment,
                                                         CdPlayerArtRuntime.State artIngress, VIntClock clock) {
        Objects.requireNonNull(terrain, "Act2 Past terrain assets");
        Objects.requireNonNull(player, "Act2 Past player assets");
        Objects.requireNonNull(state, "Act2 Past player state");
        Objects.requireNonNull(environment, "Act2 Past player environment");
        Objects.requireNonNull(artIngress, "Act2 Past player art ingress");
        Objects.requireNonNull(clock, "Act2 Past player V-int clock");
        return new CdPlayerFrameRuntime(CdStagePlayerSource.fromAct2Past(terrain, player), state, environment, artIngress, clock);
    }

    // BEGIN explicit original R13 Future Frame component ingress.
    /**
     * Fresh original-USA R13C/D binding over complete explicit component values. The authenticated
     * aggregate supplies its exact terrain/player/167-frame visual relation and native mapping.
     * This ingress performs no Init, clear, saved load, actor/camera/art/clock default, preparation,
     * service, programme carry or World/FULL admission. The existing source and CaptureOwner are
     * fresh for this Frame; art publication stays unbound and all existing service guards apply.
     */
    public static CdPlayerFrameRuntime fromAct3FutureEntry(CdR13FutureAssets assets, CdPlayerBody.State state,
                                                          CdPlayerBody.Environment environment,
                                                          CdPlayerArtRuntime.State artIngress, VIntClock clock) {
        Objects.requireNonNull(assets, "Act3 Future assets");
        Objects.requireNonNull(state, "Act3 Future player state");
        Objects.requireNonNull(environment, "Act3 Future player environment");
        Objects.requireNonNull(artIngress, "Act3 Future player art ingress");
        Objects.requireNonNull(clock, "Act3 Future player V-int clock");
        return new CdPlayerFrameRuntime(CdStagePlayerSource.fromAct3Future(assets), state, environment, artIngress, clock);
    }
    // END explicit original R13 Future Frame component ingress.

    // BEGIN explicit original R31A Frame component ingress (Init only).
    /** Authenticated same-source R3 data plus complete explicit values; no defaults, Init, clear,
     * source service, art upload, clock tick or programme/World/FULL authority is performed here.
     * R31A has actual raw0100/1. Body admits only genuine PlayerInit (N objects/player.asm:139-157);
     * Main/Hurt/lifecycle continuations remain the explicit R3_CONTINUATION_OWNER boundary.
     * The existing private source/CaptureOwner and art service owners are fresh; callback unbound. */
    public static CdPlayerFrameRuntime fromR31aEntry(CdR31aAssets assets, CdPlayerBody.State state,
                                                     CdPlayerBody.Environment environment,
                                                     CdPlayerArtRuntime.State artIngress, VIntClock clock) throws IOException {
        Objects.requireNonNull(assets, "Original R31A assets");
        Objects.requireNonNull(state, "R31A complete player state");
        Objects.requireNonNull(environment, "R31A complete player environment");
        Objects.requireNonNull(artIngress, "R31A complete player art ingress");
        Objects.requireNonNull(clock, "R31A complete V-int clock");
        var source = CdStagePlayerSource.fromR31aAssets(assets);
        var gates = environment.movement().gates();
        if (!source.ownsR31aAssets(assets) || source.player().sourceGraph() != CdR11aPlayerAssets.SourceGraph.R3
                || gates.zoneAct() != source.ordinaryZoneActWord() || gates.timeZone() != source.ordinaryPeriod())
            throw new UnsupportedFrame(UnsupportedBoundary.SOURCE_FAMILY, "R31A component entry requires its actual raw0100/1 source");
        return new CdPlayerFrameRuntime(source, state, environment, artIngress, clock);
    }
    // END explicit original R31A Frame component ingress.

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
                new CdPlayerBody.History(carried.history(), 0), s.rotationDistance(), s.objectLinks(), s.rotationAngle());
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
                before.rotationDistance(), before.objectLinks(), before.rotationAngle());
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
                new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[0x100]), 0), 0, new CdPlayerBody.ObjectLinks(0, 0), 0); // LevelStart .ClearObjects (src/Level/Main.asm:186-192, source pin above) owns bytes $2B/$39 and both links.
        var movement = new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0, 0, 0),
                new CdPlayerMovement.Bounds(bounds.left(), bounds.right(), bounds.bottom(), 0),
                new CdPlayerMovement.Gates(0, 1, 0, 0, 0, 0, 0),
                new CdPlayerMovement.Globals(0, bounds.cameraCentreY(), 0, 0, 0, 0, 0, 0, 0, 0));
        var environment = new CdPlayerBody.Environment(movement, new CdPlayerBody.Controls(0, 0, 0, 0, 0),
                new CdPlayerBody.Globals(0, 160, 0, 0, 0, 0, 0, 0), dynamicSlots);
        return new ColdCut(state, environment);
    }

    /** Same immutable Present aggregate for every legacy A owner; an actual B visit has no A facade. */
    public CdR11aAssets assets() {
        if (pastSource != null) throw new IllegalStateException("The bound stage player has no legacy R11A assets");
        return assets;
    }

    /**
     * Read-only borrowed stage products, not invocation or capture authority. Legacy A construction
     * keeps its original validation sequence: only this new accessor derives its stricter data view.
     */
    public CdStagePlayerSource stageSource() {
        if (pastSource != null) return pastSource;
        if (presentSource == null) presentSource = CdStagePlayerSource.fromPresent(assets);
        return presentSource;
    }
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
        serviceVBlank(phase, () -> { }, levelArtService);
    }

    /**
     * VInt_Level:823-830 publishes the complete palette after ReadControllers and before Sonic art.
     * Both callbacks are synchronous service cuts, not retained owners. Lag calls neither. The
     * integrating Runtime owns the palette value and its capture; this Frame still owns input,
     * art, Finish timers and its single represented V-int count.
     */
    public void serviceVBlank(PlcLifecyclePhase phase, Runnable palettePublication, Runnable levelArtService) {
        serviceVBlank(phase, palettePublication, levelArtService, () -> { });
    }

    /**
     * Source caller's Main pause/music cut, AFTER Finish/count and pause toggle,
     * BEFORE paused-reset failure or a Body entitlement. Legacy DEV callers omit
     * these music owners explicitly; a real level caller supplies this callback.
     * It may publish only its own values/requests, never reenter this active Frame.
     */
    public void serviceVBlank(PlcLifecyclePhase phase, Runnable palettePublication, Runnable levelArtService,
                             Runnable mainMusic) {
        Objects.requireNonNull(palettePublication, "palette publication after ReadControllers");
        Objects.requireNonNull(levelArtService, "level-art service at the selector-eight source cut");
        Objects.requireNonNull(mainMusic, "Main pause/music publication after Finish");
        requireOpenIdle();
        boolean advanced = false;
        bodyEntitled = false;
        mainFrameAdvanced = false;
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
                palettePublication.run();
                var request = art.serviceVInt(state.header().slotId() != 0, state.animation().mapFrame());
                if (request.isPresent()) artPublication.publish(request.orElseThrow(), art.capture());
                levelArtService.run();
            }
            advanced = true;
            environment = finishTimers(environment, paused, state.motion().charge());
            vIntClock = vIntClock.increment();
            if (phase != PlcLifecyclePhase.LAG) {
                paused = prospectivePause(environment.controls().rawTap()); // Only AFTER Finish/count.
                mainMusic.run();
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
     * Player-owned projection of Main.ClearCamera (Level/Main.asm:210-216 at native
     * pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff), FFF700..FFF800. The declaration
     * range _Definitions.inc:237-329 includes these physics/bounds/scratch/controls,
     * sonicRecordIndex and art-comparison flags. It excludes the history and art byte
     * buffers, raw/logical controller latches and MAINVARS. In particular WORD
     * timeWarpTimer FFF786 is cleared here; BYTE timeWarp FF1521 is separately owned.
     *
     * This fixed writer performs no input, Body, DPLC, upload, camera/placement service
     * or clock/reset operation. The actual private loader owns phase and invocation;
     * it separately executes all other camera-region owners and later native stores.
     * Native title-lock1 is conditional and is not part of this clear. Prepare every
     * immutable reference before adopting the complete represented projection.
     */
    public void clearLevelStartCameraPlayerValues() {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        var before = state; var old = environment; var movement = old.movement();
        var gates = movement.gates(); var globals = movement.globals(); var bodyGlobals = old.globals();
        var controls = old.controls(); var oldArt = art.capture();
        var nextState = new CdPlayerBody.State(before.motion(), before.animation(), before.header(), before.timers(),
                new CdPlayerBody.History(before.history().bytes(), 0), before.rotationDistance(),
                before.objectLinks(), before.rotationAngle());
        var nextMovement = new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0, 0, 0),
                new CdPlayerMovement.Bounds(0, 0, 0, 0),
                new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), gates.miniSonic(),
                        0, 0, gates.speedShoes(), 0),
                new CdPlayerMovement.Globals(0, 0, 0, 0, 0, 0, globals.debugAngle(),
                        globals.debugAngleShift(), globals.debugQuadrant(), globals.debugFloorDistance()));
        var nextEnvironment = new CdPlayerBody.Environment(nextMovement,
                new CdPlayerBody.Controls(controls.rawHeld(), controls.rawTap(), controls.logicalHeld(), controls.logicalTap(), 0),
                new CdPlayerBody.Globals(bodyGlobals.levelFrames(), 0, bodyGlobals.timeAttackMode(), bodyGlobals.debugMode(),
                        bodyGlobals.debugCheat(), 0, bodyGlobals.invincible(), bodyGlobals.bossMusic()), old.dynamicSlots());
        var nextArt = new CdPlayerArtRuntime.State(oldArt.cpuBuffer(), oldArt.residentBank(), 0, false);
        state = nextState; environment = nextEnvironment; art.restore(nextArt);
    }

    // BEGIN fixed native LevelStart player value writers.
    /**
     * LoadPlayer (Level/Main.asm:438-447 at pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff),
     * selected nonzero live-spawn path only: ID BYTE1, then hurt WORD$0078. The caller's
     * private load scope authenticates the actual invocation, phase and nonzero spawn; none
     * is inferred from these Frame values. This completed value product does not execute
     * Init, read saved mini, initialize physics/header/art, or expose intermediate stores.
     */
    public void applyLevelStartWarpPlayer() {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        var before = state; var header = before.header(); var timers = before.timers();
        var nextHeader = new CdPlayerBody.Header(1, header.mappingAddress(), header.tile(), header.priority(), header.width());
        var nextTimers = new CdPlayerBody.Timers(0x0078, timers.invincibility(), timers.speedShoes(), timers.reset());
        state = new CdPlayerBody.State(before.motion(), before.animation(), nextHeader, nextTimers, before.history(),
                before.rotationDistance(), before.objectLinks(), before.rotationAngle());
    }

    /** Native LoadPlayer/Main:438-447 spawn0 branch: ID1 only; hurt timer is a nonwrite. */
    public void applyLevelStartNormalPlayer() {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        var before = state; var h = before.header();
        var next = new CdPlayerBody.Header(1, h.mappingAddress(), h.tile(), h.priority(), h.width());
        state = new CdPlayerBody.State(before.motion(), before.animation(), next, before.timers(), before.history(),
                before.rotationDistance(), before.objectLinks(), before.rotationAngle());
    }

    /** PPZ2 LevelSizeLoad_DefaultStart: actual source WORD centres, retaining both fractions. */
    public void initializeLevelSizeCentrePlayerValues(int centreXWord, int centreYWord) {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        if ((centreXWord & ~0xFFFF) != 0 || (centreYWord & ~0xFFFF) != 0)
            throw new IllegalArgumentException("LevelSize centres require unsigned native WORDs");
        var before = state; var m = before.motion();
        var next = new CdPlayerMotionState((centreXWord << 16) | (m.centreX16_16() & 0xFFFF),
                (centreYWord << 16) | (m.centreY16_16() & 0xFFFF), m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(),
                m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        state = new CdPlayerBody.State(next, before.animation(), before.header(), before.timers(), before.history(),
                before.rotationDistance(), before.objectLinks(), before.rotationAngle());
    }

    /**
     * Main.SkipTitleCard (Level/Main.asm:278-283 at the same native pin): represented
     * playerCtrl/p1CtrlData WORDs and primary boredTimer WORD only. P2 and unkLevelFlag
     * stores remain external. The caller owns reached load order; this port samples no
     * input and preserves controlLocked, pause, physics, Body state and the existing clock.
     */
    public void clearLevelStartControlsAndBoredTimer() {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        var before = environment; var movement = before.movement(); var globals = movement.globals();
        var nextGlobals = new CdPlayerMovement.Globals(globals.lookMode(), globals.cameraYCentre(),
                globals.primaryAngle(), globals.secondaryAngle(), 0, globals.scoreChain(), globals.debugAngle(),
                globals.debugAngleShift(), globals.debugQuadrant(), globals.debugFloorDistance());
        environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(),
                movement.gates(), nextGlobals), new CdPlayerBody.Controls(0, 0, 0, 0, before.controls().controlLocked()),
                before.globals(), before.dynamicSlots());
    }

    /**
     * Main.SkipClear (Level/Main.asm:293-300 at the same native pin): represented
     * invincible BYTE, speedShoes BYTE, debugMode WORD and levelFrames WORD become0.
     * The native timeWarp BYTE belongs to external ContactGlobals; timeWarpTimer WORD
     * is retained here. Shield/rings/lives/restart/spawn/HUD and ordering are external.
     * Native physics, actor timers, controls and all other component values are nonwrites.
     */
    public void clearLevelStartPlayerGlobals() {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        var before = environment; var movement = before.movement(); var gates = movement.gates(); var globals = before.globals();
        var nextGates = new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), gates.miniSonic(),
                gates.waterSlide(), gates.windTunnel(), 0, gates.timeWarpDirection());
        var nextGlobals = new CdPlayerBody.Globals(0, globals.cameraXCentre(), globals.timeAttackMode(), 0,
                globals.debugCheat(), globals.timeWarpTimer(), 0, globals.bossMusic());
        environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(),
                nextGates, movement.globals()), before.controls(), nextGlobals, before.dynamicSlots());
    }

    /**
     * Main:315-316 at the same native pin clears only timeZone bit7 and branches on its
     * previous value. Return that semantic was-marked predicate, not CPU flags. The caller
     * separately owns Main:314's display store and actual phase. Low bits and this final
     * source binding remain unchanged; a resulting incompatible bare period is rejected
     * by the next ordinary source guard. No destination normalization or source switch.
     */
    public boolean clearLevelStartTimeZoneMarker() {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        var before = environment; var movement = before.movement(); var gates = movement.gates();
        boolean wasMarked = (gates.timeZone() & 0x80) != 0;
        var nextGates = new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone() & 0x7F, gates.miniSonic(),
                gates.waterSlide(), gates.windTunnel(), gates.speedShoes(), gates.timeWarpDirection());
        environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(),
                nextGates, movement.globals()), before.controls(), before.globals(), before.dynamicSlots());
        return wasMarked;
    }
    // END fixed native LevelStart player value writers.

    // BEGIN addressed native time-warp load Frame writers.
    /**
     * Raw BYTE/WORD preimage for only the nine Frame-owned LoadTimeWarpData targets
     * (Load Saved Data.asm:7-40 at pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff).
     * This is an observation, not an additional native saved read or load-phase authority.
     * Cameras, destination bounds, rings, lives, water and time retain their separate owners.
     */
    public int readTimeWarpLoadField(CdTimeWarpLoadData.StoreTarget target) {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        Objects.requireNonNull(target, "time-warp load target");
        var motion = state.motion(); var movement = environment.movement();
        return switch (target) {
            case PRIMARY_X -> motion.centreX16_16() >>> 16;
            case PRIMARY_Y -> motion.centreY16_16() >>> 16;
            case PRIMARY_FLAGS -> motion.flags();
            case PRIMARY_GVEL -> motion.groundVelocity() & 0xFFFF;
            case PRIMARY_X_VELOCITY -> motion.xVelocity() & 0xFFFF;
            case PRIMARY_Y_VELOCITY -> motion.yVelocity() & 0xFFFF;
            case BOTTOM_BOUND -> movement.bounds().bottom();
            case MINI_SONIC -> movement.gates().miniSonic();
            case LEFT_BOUND -> movement.bounds().left();
            default -> throw new IllegalArgumentException("Time-warp load target is not owned by Frame: " + target);
        };
    }

    /**
     * One addressed native store with a known current preimage. WORD positions preserve
     * fractions; velocities adopt the raw WORD's signed representation. Only the selected
     * field changes. No whole-state restoration, saved read, input, Body, art or clock work
     * occurs. The actual private loader owns invocation, source, order and conditional gates;
     * public equal values cannot exclude a same-value no-op, replay or equal-owner swap.
     */
    public void applyTimeWarpLoadStore(CdTimeWarpLoadData.StoreCall call) {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        Objects.requireNonNull(call, "time-warp load store");
        int actual = readTimeWarpLoadField(call.target());
        if (call.expectedValue().isEmpty() || actual != call.expectedValue().getAsInt()) {
            throw new IllegalArgumentException("Unknown or stale time-warp load field");
        }
        var field = call.target(); int value = call.value();
        var before = state; var m = before.motion();
        switch (field) {
            case PRIMARY_X, PRIMARY_Y, PRIMARY_FLAGS, PRIMARY_GVEL, PRIMARY_X_VELOCITY, PRIMARY_Y_VELOCITY -> {
                var next = new CdPlayerMotionState(
                        field == CdTimeWarpLoadData.StoreTarget.PRIMARY_X
                                ? (value << 16) | (m.centreX16_16() & 0xFFFF) : m.centreX16_16(),
                        field == CdTimeWarpLoadData.StoreTarget.PRIMARY_Y
                                ? (value << 16) | (m.centreY16_16() & 0xFFFF) : m.centreY16_16(),
                        field == CdTimeWarpLoadData.StoreTarget.PRIMARY_X_VELOCITY ? (short) value : m.xVelocity(),
                        field == CdTimeWarpLoadData.StoreTarget.PRIMARY_Y_VELOCITY ? (short) value : m.yVelocity(),
                        field == CdTimeWarpLoadData.StoreTarget.PRIMARY_GVEL ? (short) value : m.groundVelocity(),
                        m.routine(), m.angle(), field == CdTimeWarpLoadData.StoreTarget.PRIMARY_FLAGS ? value : m.flags(),
                        m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(), m.playerJump(),
                        m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
                state = new CdPlayerBody.State(next, before.animation(), before.header(), before.timers(), before.history(),
                        before.rotationDistance(), before.objectLinks(), before.rotationAngle());
            }
            case BOTTOM_BOUND, MINI_SONIC, LEFT_BOUND -> {
                var e = environment; var movement = e.movement(); var bounds = movement.bounds(); var gates = movement.gates();
                var nextBounds = field == CdTimeWarpLoadData.StoreTarget.BOTTOM_BOUND
                        ? new CdPlayerMovement.Bounds(bounds.left(), bounds.right(), value, bounds.bossFight())
                        : field == CdTimeWarpLoadData.StoreTarget.LEFT_BOUND
                        ? new CdPlayerMovement.Bounds(value, bounds.right(), bounds.bottom(), bounds.bossFight()) : bounds;
                var nextGates = field == CdTimeWarpLoadData.StoreTarget.MINI_SONIC
                        ? new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), value, gates.waterSlide(),
                                gates.windTunnel(), gates.speedShoes(), gates.timeWarpDirection()) : gates;
                environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), nextBounds,
                        nextGates, movement.globals()), e.controls(), e.globals(), e.dynamicSlots());
            }
            default -> throw new IllegalArgumentException("Time-warp load target is not owned by Frame: " + field);
        }
    }

    /**
     * Main.ClearFlowers:178-184 clears FF1600..FF2000, including the native LONG
     * levelVIntCounter at FF190A. Earlier selector-C pulses precede this actual write.
     * Establish known zero in the existing counter without changing Frame/CaptureOwner,
     * input, ordinary levelFrames, art, display or any other flower-clear target. The
     * caller owns the reached clear and its other stores; this executes no V-int pulse.
     */
    public void clearLevelStartVIntCounter() {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        vIntClock = new VIntClock(OptionalLong.of(0), 0);
    }
    // BEGIN fixed flower-clear and LevelSizeLoad player projections.
    /**
     * Main.ClearFlowers:178-184 clears debugMode FF1906, levelVIntCounter FF190A,
     * bossMusic FF1958 and miniSonic FF195B. Native pin1ecc5f3a, Main CPU Variables.
     * The earlier selector-C pulse precedes these stores. No other player, input,
     * art, clock authority or source value is reset; the actual Module owns order.
     */
    public void clearLevelStartFlowersPlayerValues() {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        var old = environment; var movement = old.movement(); var gates = movement.gates(); var globals = old.globals();
        var nextGates = new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), 0, gates.waterSlide(),
                gates.windTunnel(), gates.speedShoes(), gates.timeWarpDirection());
        var nextGlobals = new CdPlayerBody.Globals(globals.levelFrames(), globals.cameraXCentre(), globals.timeAttackMode(),
                0, globals.debugCheat(), globals.timeWarpTimer(), globals.invincible(), 0);
        var next = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(),
                nextGates, movement.globals()), old.controls(), nextGlobals, old.dynamicSlots());
        var clock = new VIntClock(OptionalLong.of(0), 0);
        environment = next; vIntClock = clock;
    }

    /**
     * PP LevelSizeLoad:21-36 writes the source current bounds and camera centres.
     * Fixed 320/2 is the native camera-X centre; camera-Y comes from actual CamBounds.
     * bossFight is a nonwrite and the supplied complete bounds must retain its value.
     * No SetupCamera, saved read, actor, scroll, input, PLC or clock executes here.
     */
    public void initializeLevelSizePlayerValues(CdPlayerMovement.Bounds bounds, int cameraYCentreWord) {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        Objects.requireNonNull(bounds, "actual LevelSizeLoad bounds");
        if ((cameraYCentreWord & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected the native camera-Y WORD");
        var old = environment; var movement = old.movement(); var globals = movement.globals(); var body = old.globals();
        if (bounds.bossFight() != movement.bounds().bossFight()) {
            throw new IllegalArgumentException("LevelSizeLoad does not write the boss flag");
        }
        var nextGlobals = new CdPlayerMovement.Globals(globals.lookMode(), cameraYCentreWord, globals.primaryAngle(),
                globals.secondaryAngle(), globals.boredTimer(), globals.scoreChain(), globals.debugAngle(),
                globals.debugAngleShift(), globals.debugQuadrant(), globals.debugFloorDistance());
        var nextBody = new CdPlayerBody.Globals(body.levelFrames(), 160, body.timeAttackMode(), body.debugMode(),
                body.debugCheat(), body.timeWarpTimer(), body.invincible(), body.bossMusic());
        var next = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), bounds,
                movement.gates(), nextGlobals), old.controls(), nextBody, old.dynamicSlots());
        environment = next;
    }
    // END fixed flower-clear and LevelSizeLoad player projections.

    // END addressed native time-warp load Frame writers.

    /**
     * LevelStart:35 clears the existing gameplay-pause bit before its fade. This finite value port
     * changes only this Frame's existing pause authority; it neither represents the omitted native
     * music-pause latch/lower byte nor certifies a LevelStart invocation. Root owns the actual scope.
     */
    public void beginLevelStart() {
        requireOpenIdle(); requireClosedFrame();
        paused = false;
    }

    /**
     * One selector12 service at the old level's palette-fade VSync, before the caller's colour pass.
     * Pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Level/Main.asm:894-907,942-958,971-995:
     * ReadControllers -> current palette/SAT/HScroll publication -> DecompPLCFast -> VInt_Finish/count.
     * Root supplies the two synchronous source-position callbacks and authenticates LevelStart's
     * scope/phase; these values confer no invocation authority. No Sonic art, playable slot, Start
     * toggle/reset, level-frame tick, powerup, plane or slow CPU PLC service occurs here.
     */
    public void serviceLevelPaletteFade(int heldByte, Runnable palettePublication, Runnable fastPlcService) {
        if ((heldByte & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native held byte");
        Objects.requireNonNull(palettePublication, "palette publication after ReadControllers");
        Objects.requireNonNull(fastPlcService, "fast PLC service after palette publication");
        serviceLevelEntry(heldByte, palettePublication, null, fastPlcService);
    }

    /**
     * One fixed selectorC VInt_LevelLoad service (Level/Main.asm:860-876 at the source pin):
     * ReadControllers -> palette/HScroll/SAT publication -> camera/flag copies and DrawLevel
     * -> DecompPLCFast -> existing VInt_Finish/count. Callbacks are synchronous source cuts,
     * never retained. The caller authenticates the actual source/load phase; values alone
     * confer no invocation authority. No Sonic DPLC, Body entitlement, level-frame tick,
     * Start toggle, slow PLC, new clock or capture partition is introduced.
     */
    public void serviceLevelLoad(int heldByte, Runnable publication, Runnable copyAndDraw, Runnable fastPlcService) {
        if ((heldByte & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native held byte");
        Objects.requireNonNull(publication, "palette/HScroll/SAT publication after ReadControllers");
        Objects.requireNonNull(copyAndDraw, "camera/flag copies and DrawLevel after publication");
        Objects.requireNonNull(fastPlcService, "fast PLC service after copy and draw");
        serviceLevelEntry(heldByte, publication, copyAndDraw, fastPlcService);
    }

    /**
     * One old-programme selector12 after Results changed the actual raw Gates to1/1 or2/2.
     * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Level/Main.asm:85-115,894-907,971-995.
     * This component value service checks the full actual pair and retained old R11A/R11B or R12B data.
     * Root alone authenticates the completed physical walk, programme exit phase and call order.
     * Equal values confer no World authority. Ordinary source admission remains unchanged.
     */
    public void serviceOldProgrammeExitPaletteFade(int heldByte, CdPlayerBody.State expectedState,
                                                   CdPlayerBody.Environment expectedEnvironment,
                                                   Runnable palettePublication, Runnable fastPlcService) {
        if ((heldByte & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native held byte");
        Objects.requireNonNull(palettePublication, "palette publication after ReadControllers");
        Objects.requireNonNull(fastPlcService, "fast PLC service after palette publication");
        serviceOldProgrammeExitEntry(heldByte, expectedState, expectedEnvironment, palettePublication, null, fastPlcService);
    }

    /**
     * Old-programme selectorC publication/copy/DrawLevel/fast-PLC service before its native return.
     * N Level/Main.asm:105-115,860-876,971-995. Uses the same controller/Finish/count kernel as
     * existing load services; no Body, player art, level-frame tick, pause toggle, slow PLC,
     * warp-direction clear, destination Frame or new capture/clock owner is admitted here.
     */
    public void serviceOldProgrammeExitLoad(int heldByte, CdPlayerBody.State expectedState,
                                            CdPlayerBody.Environment expectedEnvironment,
                                            Runnable publication, Runnable copyAndDraw, Runnable fastPlcService) {
        if ((heldByte & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native held byte");
        Objects.requireNonNull(publication, "palette/HScroll/SAT publication after ReadControllers");
        Objects.requireNonNull(copyAndDraw, "camera/flag copies and DrawLevel after publication");
        Objects.requireNonNull(fastPlcService, "fast PLC service after copy and draw");
        serviceOldProgrammeExitEntry(heldByte, expectedState, expectedEnvironment, publication, copyAndDraw, fastPlcService);
    }

    private void serviceOldProgrammeExitEntry(int heldByte, CdPlayerBody.State expectedState,
                                               CdPlayerBody.Environment expectedEnvironment,
                                               Runnable publication, Runnable copyAndDraw, Runnable fastPlcService) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "old-programme actual State");
        Objects.requireNonNull(expectedEnvironment, "old-programme actual Environment");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment))
            throw new IllegalArgumentException("Stale old-programme exit State/Environment cut");
        requireOldProgrammeExitSourceFamily();
        serviceAdmittedLevelEntry(heldByte, publication, copyAndDraw, fastPlcService);
    }

    private void requireOldProgrammeExitSourceFamily() {
        boolean boundOldSource;
        if (pastSource == null) {
            // Existing A component ingress can deliberately have no retail identity. Its typed
            // profile/data binding is still R11A; Root's actual World separately authenticates media.
            var profile = assets.profile(); var identity = assets.identity();
            boundOldSource = profile != null && profile.stage().equals(CdStagePayload.R11A.stage())
                    && profile.file() == CdNativeFile.R11A && profile.domain() == CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    && assets.player().sonic() == assets.sonic()
                    && (identity == null || profile.stage().equals(identity.stage()) && identity.file() == profile.file()
                        && identity.domain() == profile.domain() && identity.variant() == profile.variant());
        } else {
            // Both retained pairs were authenticated by their exact existing entry factories.
            boundOldSource = assets == null && (pastSource.stage().equals(CdStagePayload.R11B.stage())
                    ||pastSource.stage().equals(CdStagePayload.R12B.stage())) && pastSource.ordinaryPeriod() == 0;
        }
        var gates = environment.movement().gates();
        boolean r12b=pastSource!=null&&pastSource.stage().equals(CdStagePayload.R12B.stage());
        int destination=r12b?2:1;
        if (!boundOldSource || gates.zoneAct() != destination || gates.timeZone() != destination)
            throw new UnsupportedFrame(UnsupportedBoundary.SOURCE_FAMILY,
                    "Old programme exit requires retained R11A/R11B with raw1/1 or retained R12B with actual Results2/2 Gates");
    }

    // BEGIN distinct retained R13 Results old-programme value services.
    /** Actual retained C/D source with Results' raw present1/zoneAct0100. Root authenticates
     * its private completed-walk/exit phase; this complete-pair component service grants no World.
     * Native Main85-115 / VInt894-907 / Finish704-715,971-995; no Body or destination-source admission. */
    public void serviceAct3FutureOldProgrammeExitPaletteFade(int heldByte, CdR13FutureAssets source,
                                                             CdPlayerBody.State expectedState,
                                                             CdPlayerBody.Environment expectedEnvironment,
                                                             Runnable publication, Runnable fastPlcService) {
        if ((heldByte & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native held byte");
        Objects.requireNonNull(publication, "old R13 palette publication");
        Objects.requireNonNull(fastPlcService, "old R13 fast PLC service");
        serviceAct3FutureOldProgrammeExitEntry(heldByte, source, expectedState, expectedEnvironment, publication, null, fastPlcService);
    }
    /** Original selectorC publication, camera copies/DrawLevel and fast service, then shared Finish/count. */
    public void serviceAct3FutureOldProgrammeExitLoad(int heldByte, CdR13FutureAssets source,
                                                      CdPlayerBody.State expectedState,
                                                      CdPlayerBody.Environment expectedEnvironment,
                                                      Runnable publication, Runnable copyAndDraw, Runnable fastPlcService) {
        if ((heldByte & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native held byte");
        Objects.requireNonNull(publication, "old R13 palette/HScroll/SAT publication");
        Objects.requireNonNull(copyAndDraw, "old R13 camera copies and DrawLevel");
        Objects.requireNonNull(fastPlcService, "old R13 fast PLC service");
        serviceAct3FutureOldProgrammeExitEntry(heldByte, source, expectedState, expectedEnvironment, publication, copyAndDraw, fastPlcService);
    }
    private void serviceAct3FutureOldProgrammeExitEntry(int heldByte, CdR13FutureAssets source,
                                                        CdPlayerBody.State expectedState, CdPlayerBody.Environment expectedEnvironment,
                                                        Runnable publication, Runnable copyAndDraw, Runnable fastPlcService) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(source, "retained original R13 source");
        Objects.requireNonNull(expectedState, "old R13 actual State");
        Objects.requireNonNull(expectedEnvironment, "old R13 actual Environment");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment))
            throw new IllegalArgumentException("Stale old R13 State/Environment cut");
        if (assets != null || pastSource == null || !pastSource.ownsAct3Future(source))
            throw new IllegalArgumentException("Exact retained original R13 source required");
        var gates = environment.movement().gates();
        if (gates.zoneAct() != 0x100 || gates.timeZone() != 1)
            throw new UnsupportedFrame(UnsupportedBoundary.SOURCE_FAMILY, "Retained R13 Results requires actual raw0100/1");
        serviceAdmittedLevelEntry(heldByte, publication, copyAndDraw, fastPlcService);
    }
    // END distinct retained R13 Results old-programme value services.

    /** Shared controller/timer/failure kernel; selector12 has no camera-copy/DrawLevel cut. */
    private void serviceLevelEntry(int heldByte, Runnable palettePublication, Runnable copyAndDraw,
                                   Runnable fastPlcService) {
        requireOpenIdle(); requireClosedFrame(); requireSourceFamily();
        serviceAdmittedLevelEntry(heldByte, palettePublication, copyAndDraw, fastPlcService);
    }

    /** Single admitted service algorithm; both admission paths above finish before its first write. */
    private void serviceAdmittedLevelEntry(int heldByte, Runnable palettePublication, Runnable copyAndDraw,
                                          Runnable fastPlcService) {
        boolean advanced = false;
        inOperation = true;
        try {
            var controls = environment.controls();
            int tap = (heldByte ^ controls.rawHeld()) & heldByte;
            advanced = true;
            environment = withControls(environment, new CdPlayerBody.Controls(heldByte, tap,
                    controls.logicalHeld(), controls.logicalTap(), controls.controlLocked()));
            palettePublication.run();
            if (copyAndDraw != null) copyAndDraw.run();
            fastPlcService.run();
            environment = finishTimers(environment, paused, state.motion().charge());
            vIntClock = vIntClock.increment();
        } catch (RuntimeException | Error failure) {
            bodyEntitled = false;
            if (advanced) { closed = true; artPublication = null; }
            throw failure;
        } finally {
            pending = null;
            inOperation = false;
        }
    }

    /** LevelStart:72's fixed CLR.B changes only the existing direction, retaining period and timer. */
    public void clearLevelStartWarpDirection() {
        requireOpenIdle(); requireClosedFrame();
        var movement = environment.movement(); var gates = movement.gates();
        var nextGates = new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), gates.miniSonic(),
                gates.waterSlide(), gates.windTunnel(), gates.speedShoes(), 0);
        environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(movement.physics(), movement.bounds(),
                nextGates, movement.globals()), environment.controls(), environment.globals(), environment.dynamicSlots());
    }

    /**
     * Native Main:400's ADDQ.W #1,levelFrames after the completed selector8 and
     * unpaused Main branch, before SpawnObjects and the physical primary dispatch
     * (pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Level/Main.asm:397-403).
     * Root owns that actual call order. This finite value store uses the existing
     * serviced Body entitlement once, preserves it for the later Body, and does
     * not execute input, Finish, clocks, art, placement or Body itself. Legacy
     * callers may still supply their already-incremented value directly to runBody.
     */
    public int advanceLevelMainFrame() {
        requireBodyEntitlement();
        if (mainFrameAdvanced) throw new IllegalStateException("Native Main level frame already advanced for this service");
        int next = (environment.globals().levelFrames() + 1) & 0xFFFF;
        var ready = withLevelFrames(environment, next);
        environment = ready;
        mainFrameAdvanced = true;
        return next;
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
        return runBody(levelFrames, touchContext, levelContext, objects, Optional.empty());
    }

    /**
     * Forward the actual borrowed TimeWarp cut without retaining it. Body stages timer/direction,
     * reserved patches and its same-call contact overlay; Runtime alone adopts the latter owners
     * after the whole Body succeeds. Entitlement, sound preparation and Frame adoption stay single.
     */
    public BodyPublication runBody(int levelFrames, Optional<CdPlayerBody.TouchContext> touchContext,
                                   Optional<CdPlayerBody.LevelContext> levelContext,
                                   Optional<CdPlayerMovement.ObjectContext> objects,
                                   Optional<CdPlayerBody.TimeWarpContext> timeWarpContext) {
        return runBody(levelFrames,touchContext,levelContext,objects,timeWarpContext,Optional.empty());
    }
    // BEGIN R13 OPTIONAL BOSS FRAME BODY ENTRY 20261106
    /** Borrow only this actual call's marker; existing entitlement/whole Body atomic adoption remains.
     * A stopped pure SPECIAL prefix is not a successful Frame publication. No owner is minted here. */
    public BodyPublication runBody(int levelFrames,Optional<CdPlayerBody.TouchContext> touchContext,
                                   Optional<CdPlayerBody.LevelContext> levelContext,
                                   Optional<CdPlayerMovement.ObjectContext> objects,
                                   Optional<CdPlayerBody.TimeWarpContext> timeWarpContext,
                                   Optional<CdPlayerBody.BossSpecialContext> bossSpecialContext) {
        requireBodyEntitlement();
        bodyEntitled = false;
        inOperation = true;
        try {
            var result = body.step(state, withLevelFrames(environment, levelFrames & 0xFFFF), touchContext, levelContext, objects, timeWarpContext,bossSpecialContext);
            var publication = new BodyPublication(result, collectSounds(result.effects()));
            state = result.state(); environment = result.environment(); display = result.display();
            return publication;
        } finally {
            pending = null;
            inOperation = false;
        }
    }

    // BEGIN genuine R31 ordinary Frame bridge 20261107.
    /**
     * Distinct normal R31 Main/Hurt component entry. The six legacy runBody overloads retain their
     * original R3 continuation fence. N objects/player.asm:272-335,353-415,2206-2239;
     * r3/player_block_check.asm and r3/player_object_collide.asm at pin1ecc5f3a.
     *
     * Body executes once over its exact retained source and the complete explicit current cut.
     * Already-incremented low16 levelFrames is projected into a LOCAL input; the live Frame pair
     * stays unchanged through all fallible Body/sound/display preparation and caller preflight.
     * The typed port must apply its exact original call once. It receives no arbitrary restore
     * or Result-adoption entry. Public values confer no actual World/Module invocation authority.
     *
     * A pre-apply throw consumes entitlement but publishes no Body values, retaining earlier real
     * V-int input/art/clock prefixes. A fault AFTER apply preserves that completed prefix and closes
     * this Frame admission; no rollback or fabricated successful publication is supplied. Actual
     * external native writes/planes/music/requests and whole-World capture remain caller-owned.
     */
    public R31Publication runR31Body(int levelFrames, CdPlayerBody.R31Context context,
                                    R31PublicationPort port) {
        return runR31BodyInternal(levelFrames,context,port,null);
    }
    /** Opt-in real original stores; legacy three-argument entry remains whole-call atomic. */
    public R31Publication runR31Body(int levelFrames,CdPlayerBody.R31Context context,
                                     R31PublicationPort port,R31PlaceBlockPort places){
        Objects.requireNonNull(places,"original PlaceBlock consumer");
        return runR31BodyInternal(levelFrames,context,port,places);
    }
    private R31Publication runR31BodyInternal(int levelFrames,CdPlayerBody.R31Context context,
                                            R31PublicationPort port,R31PlaceBlockPort places){
        requireBodyEntitlement();
        bodyEntitled = false;
        inOperation = true;
        try {
            Objects.requireNonNull(port, "actual R31 publication port");
            Objects.requireNonNull(context,"original R31 context");
            CdPlayerBody.R31LivePlacePort live=null;
            if(places!=null){
                r31Invocation=new R31Invocation(context);
                live=new CdPlayerBody.R31LivePlacePort(){
                    public void store(CdR31SpecialBlockCollision.PlaceBlock operation,CdPlayerBody.R31PlaceStore store,CdPlayerBody.R31Prefix prefix){
                        var call=new R31PlaceBlockCall(operation,store,prefix);invokeR31Place(call,()->places.store(call));
                    }
                    public void screen(CdR31SpecialBlockCollision.PlaceBlock operation,CdPlayerBody.R31Prefix prefix){
                        var call=new R31PlaceBlockScreenCall(operation);invokeR31Place(call,()->places.screen(call));
                    }
                };
            }
            var completed = body.stepR31(state, withLevelFrames(environment, levelFrames & 0xFFFF), context,live);
            var publication = new R31Publication(new BodyPublication(completed.body(),
                    collectR31Sounds(completed.body().effects())), completed.nativeValues());
            var original = new R31BodyCall(publication, context.source());
            pendingR31Body = original;
            try{port.publish(original);original.verifyReturned(true);}
            catch(RuntimeException|Error fault){
                try{original.verifyReturned(false);}catch(RuntimeException|Error verification){if(verification!=fault)fault.addSuppressed(verification);}
                throw fault;
            }
            return publication;
        } catch (RuntimeException | Error original) {
            if ((pendingR31Body != null && pendingR31Body.applied)||(r31Invocation!=null&&r31Invocation.published)) { closed = true; artPublication = null; }
            throw original;
        } finally {
            pendingR31Body = null;
            pendingR31Place = null;
            r31Invocation = null;
            pending = null;
            inOperation = false;
        }
    }

    /** Reuse legacy extraction verbatim, inserting only actual R31 requests at their positions.
     * R31BlockCall is a comparison log, never another source of sound or executable effects. */
    private static List<Sound> collectR31Sounds(List<CdPlayerBody.Effect> effects) {
        var sounds = new ArrayList<Sound>();
        for (var effect : effects) {
            if (effect instanceof CdPlayerBody.R31FmRequest request) sounds.add(new R31NativeSound(request));
            else if (effect instanceof CdPlayerBody.R31SubCpuRequest request) sounds.add(new R31NativeSound(request));
            else if (effect instanceof CdPlayerBody.R31MusicRequest request) sounds.add(new R31NativeSound(request));
            else sounds.addAll(collectSounds(List.of(effect)));
        }
        return List.copyOf(sounds);
    }
    // END genuine R31 ordinary Frame bridge.

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
        state = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(), state.history(), state.rotationDistance(), state.objectLinks(), state.rotationAngle());
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
                state.history(), state.rotationDistance(), state.objectLinks(), state.rotationAngle());
        state = next;
    }

    // BEGIN R13 BOSS SCALAR PREPARATION 20261106
    /**
     * Pure projection of ONE Frame-owned Boss store (N1ecc5f3a objects/r1/boss.asm30-64,
     * 232-330,474-514,619-696; ObjGetFloorDist Collision Check.asm157-179). BOSS_FIGHT is
     * movement.bounds().bossFight(), not the independent bossMusic flag. Destination bounds,
     * bossFlags/goodFuture and read-only player fields remain outside this Frame port.
     *
     * expectedRaw and operand are canonical unsigned native BYTE/WORD values, matching the
     * issued Boss StoreCall metadata. ASSIGN replaces that value; XOR uses expectedRaw XOR
     * operand. Signed velocity storage is a final WORD interpretation, never a position/physics
     * calculation. The native XOR site is only PLAYER_ANGLE/80; these public pure metadata
     * values cannot authenticate that site or any native call. Root must guard its original
     * cached Boss invocation, actual source/full96/order and SAME Frame/complete earlier pair
     * before preparing, then publish through the existing adoption method after all preflight.
     *
     * Preparation compares the current scalar and captures this complete actual State8/Env4
     * pair in the unchanged opaque carrier. It cannot detect an earlier different pair whose
     * scalar still matches. Adoption retains the existing complete VALUE CAS: equal Frames,
     * same-value repeats and restored equal pairs are indistinguishable, not invocation receipts.
     * No owner assignment, callback/body, art/display, clock, capture, score or other work runs.
     */
    public ObjectSolidPublication prepareObjectBossScalar(CdR13Boss.Field field,
                                                          CdR13Boss.StoreForm form,
                                                          int expectedRaw, int operand) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(field, "Boss scalar field");
        Objects.requireNonNull(form, "Boss scalar store form");
        var currentState = state; var currentEnvironment = environment;
        var motion = currentState.motion(); var movement = currentEnvironment.movement();
        var bounds = movement.bounds(); var globals = movement.globals();
        int actualRaw = switch (field) {
            case BOSS_FIGHT -> bounds.bossFight();
            case BOTTOM_BOUND -> bounds.bottom();
            case LEFT_BOUND -> bounds.left();
            case RIGHT_BOUND -> bounds.right();
            case CAMERA_Y_CENTRE -> globals.cameraYCentre();
            case PLAYER_ANGLE -> motion.angle();
            case PLAYER_GROUND_SPEED -> motion.groundVelocity() & 0xFFFF;
            case PLAYER_X_SPEED -> motion.xVelocity() & 0xFFFF;
            case PLAYER_Y_SPEED -> motion.yVelocity() & 0xFFFF;
            case PRIMARY_ANGLE -> globals.primaryAngle();
            default -> throw new IllegalArgumentException("Boss scalar is not Frame-owned: " + field);
        };
        int mask = field.bits() == 8 ? 0xFF : 0xFFFF;
        if (((expectedRaw | operand) & ~mask) != 0)
            throw new IllegalArgumentException("Boss scalar requires canonical unsigned native values: " + field);
        if (actualRaw != expectedRaw)
            throw new IllegalArgumentException("Stale Boss Frame scalar: " + field);
        int value = form == CdR13Boss.StoreForm.ASSIGN ? operand : expectedRaw ^ operand;
        var nextState = currentState; var nextEnvironment = currentEnvironment;
        if (value != actualRaw) {
            switch (field) {
                case BOSS_FIGHT, BOTTOM_BOUND, LEFT_BOUND, RIGHT_BOUND -> {
                    var nextBounds = new CdPlayerMovement.Bounds(
                            field == CdR13Boss.Field.LEFT_BOUND ? value : bounds.left(),
                            field == CdR13Boss.Field.RIGHT_BOUND ? value : bounds.right(),
                            field == CdR13Boss.Field.BOTTOM_BOUND ? value : bounds.bottom(),
                            field == CdR13Boss.Field.BOSS_FIGHT ? value : bounds.bossFight());
                    var nextMovement = new CdPlayerMovement.Environment(movement.physics(), nextBounds,
                            movement.gates(), globals);
                    nextEnvironment = new CdPlayerBody.Environment(nextMovement, currentEnvironment.controls(),
                            currentEnvironment.globals(), currentEnvironment.dynamicSlots());
                }
                case CAMERA_Y_CENTRE, PRIMARY_ANGLE -> {
                    var nextGlobals = new CdPlayerMovement.Globals(globals.lookMode(),
                            field == CdR13Boss.Field.CAMERA_Y_CENTRE ? value : globals.cameraYCentre(),
                            field == CdR13Boss.Field.PRIMARY_ANGLE ? value : globals.primaryAngle(),
                            globals.secondaryAngle(), globals.boredTimer(), globals.scoreChain(), globals.debugAngle(),
                            globals.debugAngleShift(), globals.debugQuadrant(), globals.debugFloorDistance());
                    var nextMovement = new CdPlayerMovement.Environment(movement.physics(), bounds,
                            movement.gates(), nextGlobals);
                    nextEnvironment = new CdPlayerBody.Environment(nextMovement, currentEnvironment.controls(),
                            currentEnvironment.globals(), currentEnvironment.dynamicSlots());
                }
                case PLAYER_ANGLE, PLAYER_GROUND_SPEED, PLAYER_X_SPEED, PLAYER_Y_SPEED -> {
                    var nextMotion = new CdPlayerMotionState(motion.centreX16_16(), motion.centreY16_16(),
                            field == CdR13Boss.Field.PLAYER_X_SPEED ? (short) value : motion.xVelocity(),
                            field == CdR13Boss.Field.PLAYER_Y_SPEED ? (short) value : motion.yVelocity(),
                            field == CdR13Boss.Field.PLAYER_GROUND_SPEED ? (short) value : motion.groundVelocity(),
                            motion.routine(), field == CdR13Boss.Field.PLAYER_ANGLE ? value : motion.angle(),
                            motion.flags(), motion.spriteFlags(), motion.xRadius(), motion.yRadius(), motion.animation(),
                            motion.previousAnimation(), motion.playerJump(), motion.charge(), motion.moveLock(),
                            motion.stick(), motion.playerControl(), motion.previousPrimaryAngle(), motion.previousSecondaryAngle());
                    nextState = new CdPlayerBody.State(nextMotion, currentState.animation(), currentState.header(),
                            currentState.timers(), currentState.history(), currentState.rotationDistance(),
                            currentState.objectLinks(), currentState.rotationAngle());
                }
                default -> throw new IllegalArgumentException("Boss scalar is not Frame-owned: " + field);
            }
        }
        return new ObjectSolidPublication(currentState, currentEnvironment, nextState, nextEnvironment);
    }
    // END R13 BOSS SCALAR PREPARATION 20261106

    // BEGIN R13 REUSABLE COMPLETE FRAME PREPARATIONS 20261106
    /** Pure floor scratch-angle projection, preserving the full actor/display/art/clock and every other environment field. */
    public ObjectSolidPublication prepareObjectPrimaryAngle(int expectedByte, int valueByte) {
        requireOpenIdle();requireClosedFrame();
        if(((expectedByte|valueByte)&~255)!=0)throw new IllegalArgumentException("Primary collision angle requires bytes");
        var currentState=state;var currentEnvironment=environment;var m=currentEnvironment.movement();var g=m.globals();
        if(g.primaryAngle()!=expectedByte)throw new IllegalArgumentException("Stale primary collision angle");
        var nextGlobals=new CdPlayerMovement.Globals(g.lookMode(),g.cameraYCentre(),valueByte,g.secondaryAngle(),g.boredTimer(),g.scoreChain(),g.debugAngle(),g.debugAngleShift(),g.debugQuadrant(),g.debugFloorDistance());
        var nextMovement=new CdPlayerMovement.Environment(m.physics(),m.bounds(),m.gates(),nextGlobals);
        var nextEnvironment=new CdPlayerBody.Environment(nextMovement,currentEnvironment.controls(),currentEnvironment.globals(),currentEnvironment.dynamicSlots());
        return new ObjectSolidPublication(currentState,currentEnvironment,currentState,nextEnvironment);
    }
    /** Existing strict launch validator and the same complete actor projection, prepared before any owner assignment. */
    public ObjectSolidPublication prepareObjectSpringLaunch(CdSpring.LaunchCall call) {
        validateObjectSpringLaunch(call);
        var currentState=state;var currentEnvironment=environment;
        var next=new CdPlayerBody.State(call.finalPlayer().actor().motion(),currentState.animation(),currentState.header(),currentState.timers(),
                currentState.history(),currentState.rotationDistance(),currentState.objectLinks(),currentState.rotationAngle());
        return new ObjectSolidPublication(currentState,currentEnvironment,next,currentEnvironment);
    }
    // END R13 REUSABLE COMPLETE FRAME PREPARATIONS 20261106

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

    // BEGIN R12B SpinTunnel prebuilt full-pair publication.
    /**
     * Prepare the existing SpinTunnel motion delta against the caller's complete closed value pair.
     * ObjSpinTunnel_Main, N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
     * Level/_Objects/Spin Tunnel/Main (Tag).asm:37-161. No object/native invocation authority is
     * carried by these values. The caller owns source/pool/order and preparation before publication.
     */
    public ObjectSolidPublication prepareObjectSpinTunnelResult(CdPlayerBody.State expectedState,
                                                                CdPlayerBody.Environment expectedEnvironment,
                                                                CdSpinTunnelTag.PlayerResult result) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "SpinTunnel expected State");
        Objects.requireNonNull(expectedEnvironment, "SpinTunnel expected Environment");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale SpinTunnel State/Environment cut");
        }
        validateObjectSpinTunnelResult(result);
        var nextState = new CdPlayerBody.State(result.finalMotion(), state.animation(), state.header(), state.timers(),
                state.history(), state.rotationDistance(), state.objectLinks(), state.rotationAngle());
        return new ObjectSolidPublication(state, environment, nextState, environment);
    }
    // END R12B SpinTunnel prebuilt full-pair publication.

    /**
     * Adopt only the checked motion once at the existing closed object cut. Reuse the cursor, header,
     * all four timers, history, rotation byte39 and links20/3D. The earlier display and the entire
     * Environment/art/input/clock/service state remain owned by their existing source cuts.
     */
    public void applyObjectSpinTunnelResult(CdSpinTunnelTag.PlayerResult result) {
        applyPreparedObjectSolidPublication(prepareObjectSpinTunnelResult(state, environment, result));
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
                state.history(), state.rotationDistance(), state.objectLinks(), state.rotationAngle());
        var movement = environment.movement(); var gates = movement.gates();
        var nextGates = new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), gates.miniSonic(),
                gates.waterSlide(), gates.windTunnel(), value.speedShoes(), gates.timeWarpDirection());
        var nextMovement = new CdPlayerMovement.Environment(value.physics(), movement.bounds(), nextGates, movement.globals());
        var nextEnvironment = new CdPlayerBody.Environment(nextMovement, environment.controls(),
                environment.globals(), environment.dynamicSlots());
        state = nextState;
        environment = nextEnvironment;
    }
    // BEGIN R13 PREBUILT SHOES PUBLICATION 20261106
    public ObjectSolidPublication prepareObjectSpeedShoesReward(CdPlayerSpeedShoesReward.State expectedState,
                                            CdPlayerSpeedShoesReward.Result result) {
        validateObjectSpeedShoesReward(expectedState, result);
        var value = result.state(); var timers = state.timers();
        var nextState = new CdPlayerBody.State(state.motion(), state.animation(), state.header(),
                new CdPlayerBody.Timers(timers.hurt(), timers.invincibility(), value.speedShoesTimer(), timers.reset()),
                state.history(), state.rotationDistance(), state.objectLinks(), state.rotationAngle());
        var movement = environment.movement(); var gates = movement.gates();
        var nextGates = new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), gates.miniSonic(),
                gates.waterSlide(), gates.windTunnel(), value.speedShoes(), gates.timeWarpDirection());
        var nextMovement = new CdPlayerMovement.Environment(value.physics(), movement.bounds(), nextGates, movement.globals());
        var nextEnvironment = new CdPlayerBody.Environment(nextMovement, environment.controls(),
                environment.globals(), environment.dynamicSlots());
        return new ObjectSolidPublication(state,environment,nextState,nextEnvironment);
    }
    // END R13 PREBUILT SHOES PUBLICATION 20261106

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
        validateSolidValues(expectedPlayer, expectedGlobals, result.player(), result.globals(),
                result.playerWrites(), result.globalWrites(), "result");
    }

    /**
     * Adopt the checked final values once, preserving every field outside the declared native write sets.
     * Root preflights this before pool CAS, then calls it synchronously after that CAS, with no callback between.
     * All new immutable values are built before the first assignment. No ordered call/write metadata is replayed.
     */
    public void applyObjectSolidResult(CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
                                       CdSolidObject.Result result) {
        applyPreparedObjectSolidPublication(prepareObjectSolidResult(expectedPlayer, expectedGlobals, result));
    }

    /** Intrinsic declared-store/nonwrite rules shared by genuine completed and stopped player projections. */
    private static void validateSolidValues(CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
            CdSolidObject.Player next, CdSolidObject.Globals nextGlobals,
            java.util.Set<CdSolidObject.PlayerField> playerWrites, java.util.Set<CdSolidObject.GlobalField> globalWrites,
            String kind) {
        var before = expectedPlayer.actor().motion(); var after = next.actor().motion();
        if (next.slotId() != expectedPlayer.slotId() || after.spriteFlags() != before.spriteFlags()
                || after.previousAnimation() != before.previousAnimation() || after.charge() != before.charge()
                || after.moveLock() != before.moveLock() || after.previousPrimaryAngle() != before.previousPrimaryAngle()
                || after.previousSecondaryAngle() != before.previousSecondaryAngle()) {
            throw new IllegalArgumentException("SolidObject " + kind + " changes an unowned player field");
        }
        for (var field : CdSolidObject.PlayerField.values()) {
            if (!playerWrites.contains(field) && solidPlayerValue(expectedPlayer, field) != solidPlayerValue(next, field)) {
                throw new IllegalArgumentException("Undeclared SolidObject player write: " + field);
            }
        }
        for (var field : CdSolidObject.GlobalField.values()) {
            if (!globalWrites.contains(field) && solidGlobalValue(expectedGlobals, field) != solidGlobalValue(nextGlobals, field)) {
                throw new IllegalArgumentException("Undeclared SolidObject global write: " + field);
            }
        }
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

    // BEGIN R13 LEVEL MAIN DEATH CAMERA VALUE JOIN 20261107
    /**
     * Level_MainLoop's MOVE.W cameraY,bottomBound (N1ecc Level/Main.asm:409-415).
     * Prepare only this native WORD writer over the complete actual pair. The original
     * World owns the post-RunObjects branch and the matching Host destBottomBound store.
     * This is immutable value data, not call/World authority. No scroll/events, camera
     * centre, actor, input, clocks, art, display or source family is reconstructed here.
     */
    public ObjectSolidPublication prepareAct3FutureLevelMainDeathCamera(CdR13FutureAssets assets,
                                                                       CdPlayerBody.State expectedState,
                                                                       CdPlayerBody.Environment expectedEnvironment,
                                                                       int cameraYWord) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(assets, "actual R13 assets");
        Objects.requireNonNull(expectedState, "Level Main State");
        Objects.requireNonNull(expectedEnvironment, "Level Main Environment");
        if (!stageSource().ownsAct3Future(assets)) throw new IllegalArgumentException("Another R13 source");
        requireSourceFamily();
        if ((cameraYWord & ~0xFFFF) != 0) throw new IllegalArgumentException("cameraY requires an unsigned native WORD");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment))
            throw new IllegalArgumentException("Stale Level Main State/Environment pair");
        if (paused || environment.globals().debugMode() != 0 || state.motion().routine() < 6)
            throw new IllegalStateException("Level Main death-camera branch was not selected");
        var currentState = state; var currentEnvironment = environment;
        var movement = currentEnvironment.movement(); var bounds = movement.bounds();
        var nextEnvironment = currentEnvironment;
        if (bounds.bottom() != cameraYWord) {
            var nextBounds = new CdPlayerMovement.Bounds(bounds.left(), bounds.right(), cameraYWord, bounds.bossFight());
            var nextMovement = new CdPlayerMovement.Environment(movement.physics(), nextBounds, movement.gates(), movement.globals());
            nextEnvironment = new CdPlayerBody.Environment(nextMovement, currentEnvironment.controls(),
                    currentEnvironment.globals(), currentEnvironment.dynamicSlots());
        }
        return new ObjectSolidPublication(currentState, currentEnvironment, currentState, nextEnvironment);
    }
    // END R13 LEVEL MAIN DEATH CAMERA VALUE JOIN 20261107

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
        var nextState = new CdPlayerBody.State(motion, state.animation(), state.header(), state.timers(), state.history(), state.rotationDistance(), state.objectLinks(), state.rotationAngle());
        var nextDisplay = display.map(p -> new CdPlayerBody.DisplayPublication(p.queued(), p.mappingAddress(), p.tile(),
                p.priority(), p.width(), p.mapFrame(), (p.spriteFlags() & 0x7F) | bit, p.centreX16_16(), p.centreY16_16()));
        state = nextState; display = nextDisplay;
    }

    /** Prepared resident-only InitVDP clear; observational data, not native invocation authority. */
    public static final class PlayerPatternClear {
        private final CaptureOwner owner;
        private final ActorState beforeActor;
        private final SharedState beforeShared;
        private final CdPlayerArtRuntime.State ready;
        private PlayerPatternClear(CaptureOwner owner, ActorState beforeActor, SharedState beforeShared,
                                   CdPlayerArtRuntime.State ready) {
            this.owner = owner; this.beforeActor = beforeActor; this.beforeShared = beforeShared; this.ready = ready;
        }
        public CdPlayerArtRuntime.State artState() { return ready; }
    }

    /** Native InitVDP clears the $F000 resident window, preserving CPU RAM, cue and dirty state. */
    public PlayerPatternClear preparePlayerPatternClear() {
        requireOpenIdle(); requireClosedFrame();
        var actor = captureActor(); var shared = captureShared(); var before = shared.artState();
        var ready = new CdPlayerArtRuntime.State(before.cpuBuffer(),
                CdAssetBytes.copyOf(new byte[CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES]), before.lastFrame(), before.dirty());
        return new PlayerPatternClear(captureOwner, actor, shared, ready);
    }

    /** Same-owner full closed-pair CAS. Equal values do not confer native execution authority. */
    public void validatePreparedPlayerPatternClear(PlayerPatternClear prepared) {
        requireOpenIdle(); requireClosedFrame(); Objects.requireNonNull(prepared, "prepared pattern clear");
        requireOwner(prepared.owner);
        if (!captureActor().equals(prepared.beforeActor) || !captureShared().equals(prepared.beforeShared)) {
            throw new IllegalStateException("Prepared pattern clear has a stale complete Frame pair");
        }
    }

    /** Adopt only the prebuilt art reference after all potentially failing preparation. */
    public void applyPreparedPlayerPatternClear(PlayerPatternClear prepared) {
        validatePreparedPlayerPatternClear(prepared);
        art.restore(prepared.ready);
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
        if (pastSource != null && pastSource.player().sourceGraph() == CdR11aPlayerAssets.SourceGraph.R3) {
            if (!pastSource.stage().equals(CdStagePayload.R31A.stage()) || variant != CdReleaseVariant.USA
                    || gates.zoneAct() != 0x100 || gates.timeZone() != 1)
                throw new UnsupportedFrame(UnsupportedBoundary.SOURCE_FAMILY, "Only retained original R31A raw0100/1 is admitted for component Init");
            return; // Shared service values only; Body separately fences every unowned R3 continuation.
        }
        // The bound program continues while native timeZone carries an active destination marker.
        boolean activePeriod = gates.timeZone() >= 0x80 && gates.timeZone() <= 0x82;
        int ordinaryPeriod = pastSource == null ? 1 : pastSource.ordinaryPeriod();
        int ordinaryZoneAct = pastSource == null ? 0 : pastSource.stage().actNumber() - 1;
        if (gates.zoneAct() != ordinaryZoneAct || (gates.timeZone() != ordinaryPeriod && !activePeriod)) {
            throw new UnsupportedFrame(UnsupportedBoundary.SOURCE_FAMILY, pastSource == null
                    ? "Only the bound R11A present/active-warp program is admitted"
                    : "Only the bound source's actual native act and ordinary/active-warp period are admitted");
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
            else if (effect instanceof CdPlayerBody.ObjectBossTouchResponse touch) collectContactSounds(touch.result().values().calls(), sounds);
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

    /**
     * Pure value-CAS for a stopped native Solid prefix at the same existing post-Body cut.
     * Identical actor/environment current-value and nonwrite rules as the completed port; Root alone owns
     * actual call-cut/source/full-pool/phase and FM publication authority. Public metadata supplies none.
     */
    public void validateObjectSolidPrefix(CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
                                          CdSolidObject.Prefix prefix) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedPlayer, "solid call player");
        Objects.requireNonNull(expectedGlobals, "solid call globals");
        Objects.requireNonNull(prefix, "solid prefix");
        var s = state; var h = s.header(); var g = environment.movement().globals();
        var currentPlayer = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(), h.tile(), h.priority(),
                s.timers().hurt(), s.rotationDistance()), h.slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
        var currentGlobals = new CdSolidObject.Globals(environment.globals().invincible(), g.scoreChain(),
                g.primaryAngle(), g.secondaryAngle());
        if (!expectedPlayer.equals(currentPlayer) || !expectedGlobals.equals(currentGlobals)) {
            throw new IllegalArgumentException("Stale SolidObject player/global call cut");
        }
        validateSolidValues(expectedPlayer, expectedGlobals, prefix.player(), prefix.globals(),
                prefix.playerWrites(), prefix.globalWrites(), "prefix");
    }

    /**
     * Adopt the checked final values once, preserving every field outside the declared native write sets.
     * Root preflights this before pool CAS, then calls it synchronously after that CAS, with no callback between.
     * All new immutable values are built before the first assignment. No ordered call/write metadata is replayed.
     */
    public void applyObjectSolidPrefix(CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
                                       CdSolidObject.Prefix prefix) {
        applyPreparedObjectSolidPublication(prepareObjectSolidPrefix(expectedPlayer, expectedGlobals, prefix));
    }

    // BEGIN prebuilt Solid and direct Disc player value publication ports.
    /**
     * Immutable values prepared by this Frame's checked Solid builders before any caller pool write.
     * This is public data, not native-call, pool, phase, owner or exactly-once authority. Complete value
     * equality deliberately cannot distinguish another equal-valued Frame or repeated same-value work.
     */
    public static final class ObjectSolidPublication {
        private final CdPlayerBody.State expectedState;
        private final CdPlayerBody.Environment expectedEnvironment;
        private final CdPlayerBody.State finalState;
        private final CdPlayerBody.Environment finalEnvironment;

        private ObjectSolidPublication(CdPlayerBody.State expectedState, CdPlayerBody.Environment expectedEnvironment,
                                       CdPlayerBody.State finalState, CdPlayerBody.Environment finalEnvironment) {
            this.expectedState = expectedState;
            this.expectedEnvironment = expectedEnvironment;
            this.finalState = finalState;
            this.finalEnvironment = finalEnvironment;
        }

        public CdPlayerBody.State expectedState() { return expectedState; }
        public CdPlayerBody.Environment expectedEnvironment() { return expectedEnvironment; }
        public CdPlayerBody.State finalState() { return finalState; }
        public CdPlayerBody.Environment finalEnvironment() { return finalEnvironment; }
    }

    /** Validate the completed call, then build every Frame aggregate without assigning any owner value. */
    public ObjectSolidPublication prepareObjectSolidResult(CdSolidObject.Player expectedPlayer,
                                                           CdSolidObject.Globals expectedGlobals,
                                                           CdSolidObject.Result result) {
        validateObjectSolidResult(expectedPlayer, expectedGlobals, result);
        return buildObjectSolidPublication(result.player(), result.globals());
    }

    /** Validate the reached prefix, then build every Frame aggregate without assigning any owner value. */
    public ObjectSolidPublication prepareObjectSolidPrefix(CdSolidObject.Player expectedPlayer,
                                                           CdSolidObject.Globals expectedGlobals,
                                                           CdSolidObject.Prefix prefix) {
        validateObjectSolidPrefix(expectedPlayer, expectedGlobals, prefix);
        return buildObjectSolidPublication(prefix.player(), prefix.globals());
    }

    /**
     * Prepare a genuine selected-player TopSolidObject outcome at the complete closed Frame value cut.
     * The selected secondary remains detached: only its actual counterpart BCLR may alter the primary.
     * Shared globals come from the original completed result or stopped prefix, never a primary-shaped
     * substitute. Public metadata grants no native-call, source, pool, phase, sound or publication authority.
     */
    public ObjectSolidPublication prepareObjectSelectedSolid(CdPlayerBody.State expectedState,
            CdPlayerBody.Environment expectedEnvironment, CdSolidObject.Player expectedSelected,
            CdSolidObject.PlayerSelection selection, CdSolidObject.SelectedOutcome actual) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "selected Solid player State");
        Objects.requireNonNull(expectedEnvironment, "selected Solid player Environment");
        Objects.requireNonNull(expectedSelected, "selected Solid call player");
        Objects.requireNonNull(selection, "Solid player selection");
        Objects.requireNonNull(actual, "selected Solid outcome");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale selected Solid State/Environment cut");
        }
        var s = state; var h = s.header(); var g = environment.movement().globals();
        var primary = new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(), h.tile(), h.priority(),
                s.timers().hurt(), s.rotationDistance()), h.slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
        var globals = new CdSolidObject.Globals(environment.globals().invincible(), g.scoreChain(),
                g.primaryAngle(), g.secondaryAngle());
        boolean primarySelected = selection.selected() == CdSolidObject.PlayerSlot.PRIMARY;
        if (primarySelected ? !expectedSelected.equals(primary) : selection.counterpartFlags() != s.motion().flags()) {
            throw new IllegalArgumentException("Stale selected Solid primary player cut");
        }
        var counterpart = actual.counterpart();
        var counterpartSlot = primarySelected ? CdSolidObject.PlayerSlot.SECONDARY : CdSolidObject.PlayerSlot.PRIMARY;
        if (counterpart.slot() != counterpartSlot || counterpart.before() != selection.counterpartFlags()
                || counterpart.value() != (counterpart.stored() ? counterpart.before() & ~8 : counterpart.before())) {
            throw new IllegalArgumentException("Invalid selected Solid counterpart flags");
        }
        CdSolidObject.Player selectedAfter; CdSolidObject.Globals globalsAfter;
        if (actual.outcome() instanceof CdSolidObject.Completed completed) {
            var result = completed.completedResult();
            if (result.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT) {
                throw new IllegalArgumentException("Selected Solid publication requires TopSolidObject");
            }
            selectedAfter = result.player(); globalsAfter = result.globals();
            validateSolidValues(expectedSelected, globals, selectedAfter, globalsAfter,
                    result.playerWrites(), result.globalWrites(), "result");
        } else {
            var prefix = ((CdSolidObject.NativeStopped) actual.outcome()).prefix();
            if (prefix.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT) {
                throw new IllegalArgumentException("Selected Solid publication requires TopSolidObject");
            }
            selectedAfter = prefix.player(); globalsAfter = prefix.globals();
            validateSolidValues(expectedSelected, globals, selectedAfter, globalsAfter,
                    prefix.playerWrites(), prefix.globalWrites(), "prefix");
        }
        var primaryAfter = primarySelected ? selectedAfter : primary;
        if (!primarySelected && counterpart.stored()) primaryAfter = solidPrimaryWithFlags(primary, counterpart.value());
        return buildObjectSolidPublication(primaryAfter, globalsAfter);
    }

    /**
     * Prepare only the post-Solid Swing integer-Y WORD. The caller owns the reached native formula and
     * invocation; this observational carrier grants neither. Both WORDs and the complete current pair
     * are checked before building the single-field State, preserving every fraction bit and Environment.
     */
    public ObjectSolidPublication prepareObjectSwingYWord(CdPlayerBody.State expectedState,
            CdPlayerBody.Environment expectedEnvironment, int expectedYWord, int value) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "Swing player State");
        Objects.requireNonNull(expectedEnvironment, "Swing player Environment");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale Swing player State/Environment cut");
        }
        if ((expectedYWord & ~0xFFFF) != 0 || (value & ~0xFFFF) != 0) {
            throw new IllegalArgumentException("Swing Y stores require unsigned native words");
        }
        var s = state; var m = s.motion();
        if (expectedYWord != m.centreY16_16() >>> 16) {
            throw new IllegalArgumentException("Stale Swing player Y word");
        }
        var nextMotion = new CdPlayerMotionState(m.centreX16_16(), (value << 16) | (m.centreY16_16() & 0xFFFF),
                m.xVelocity(), m.yVelocity(), m.groundVelocity(), m.routine(), m.angle(), m.flags(), m.spriteFlags(),
                m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(),
                m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var nextState = new CdPlayerBody.State(nextMotion, s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle());
        return new ObjectSolidPublication(s, environment, nextState, environment);
    }

    /**
     * Prepare one ObjSpringBoard primary store at the complete closed Frame value cut. Native pin
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Springboard/Main.asm:165-373, owns these nine targets.
     * WORD Y preserves the existing fraction; LONG Y replaces both words. Independently check the
     * old scalar, native widths and the entire prebuilt output against exactly that declared store.
     * Every other State value and the complete Environment survive; no Frame owner is assigned.
     *
     * The private caller authenticates its cached-body invocation, original pending call, full pool,
     * actual source, site, ordinal and prepare-before-publication order. Public call metadata and this
     * reusable four-value carrier supply no receipt, origin or exactly-once authority. No Body, input,
     * clock, callback, object, display, art, sound, capture or reconstruction service executes here.
     * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
     */
    public ObjectSolidPublication prepareObjectSpringboardPlayerStore(CdSpringboard.PrimaryCall call) {
        requireOpenIdle(); requireClosedFrame(); Objects.requireNonNull(call, "Springboard primary call");
        var expectedState = call.expectedState(); var expectedEnvironment = call.expectedEnvironment();
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale Springboard player State/Environment cut");
        }
        var field = call.field(); int old = call.expectedValue(), value = call.value();
        switch (field) {
            case CENTRE_Y_LONG -> { } // Every int is a native LONG bit pattern.
            case CENTRE_Y_WORD -> {
                if (((old | value) & ~0xFFFF) != 0)
                    throw new IllegalArgumentException("Springboard Y requires unsigned native words");
            }
            case Y_VELOCITY -> {
                if (old != (short) old || value != (short) value)
                    throw new IllegalArgumentException("Springboard Y velocity requires signed native words");
            }
            case FLAGS, PLAYER_JUMP, STICK, Y_RADIUS, X_RADIUS, ANIMATION -> {
                if (((old | value) & ~0xFF) != 0)
                    throw new IllegalArgumentException("Springboard byte store exceeds its native width");
            }
        }
        var m = expectedState.motion();
        int current = switch (field) {
            case CENTRE_Y_LONG -> m.centreY16_16();
            case CENTRE_Y_WORD -> m.centreY16_16() >>> 16;
            case Y_VELOCITY -> m.yVelocity();
            case FLAGS -> m.flags();
            case PLAYER_JUMP -> m.playerJump();
            case STICK -> m.stick();
            case Y_RADIUS -> m.yRadius();
            case X_RADIUS -> m.xRadius();
            case ANIMATION -> m.animation();
        };
        if (old != current) throw new IllegalArgumentException("Stale Springboard primary field value");
        int y = m.centreY16_16(), yVelocity = m.yVelocity(), flags = m.flags(), jump = m.playerJump();
        int stick = m.stick(), yRadius = m.yRadius(), xRadius = m.xRadius(), animation = m.animation();
        switch (field) {
            case CENTRE_Y_LONG -> y = value;
            case CENTRE_Y_WORD -> y = (value << 16) | (y & 0xFFFF);
            case Y_VELOCITY -> yVelocity = value;
            case FLAGS -> flags = value;
            case PLAYER_JUMP -> jump = value;
            case STICK -> stick = value;
            case Y_RADIUS -> yRadius = value;
            case X_RADIUS -> xRadius = value;
            case ANIMATION -> animation = value;
        }
        var projectedMotion = new CdPlayerMotionState(m.centreX16_16(), y, m.xVelocity(), yVelocity,
                m.groundVelocity(), m.routine(), m.angle(), flags, m.spriteFlags(), xRadius, yRadius,
                animation, m.previousAnimation(), jump, m.charge(), m.moveLock(), stick,
                m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var projectedState = new CdPlayerBody.State(projectedMotion, expectedState.animation(), expectedState.header(),
                expectedState.timers(), expectedState.history(), expectedState.rotationDistance(),
                expectedState.objectLinks(), expectedState.rotationAngle());
        if (!projectedState.equals(call.outputState())) {
            throw new IllegalArgumentException("Springboard output changes values outside its declared primary store");
        }
        return new ObjectSolidPublication(expectedState, expectedEnvironment, call.outputState(), expectedEnvironment);
    }

    /** A secondary StandOnObject can clear only bit3 in the actual primary projection. */
    private static CdSolidObject.Player solidPrimaryWithFlags(CdSolidObject.Player primary, int flags) {
        var a = primary.actor(); var m = a.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), m.routine(), m.angle(), flags, m.spriteFlags(), m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(),
                m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(motion, a.tile(), a.priority(), a.hurt(),
                a.rotationDistance()), primary.slotId(), primary.pushObject(), primary.standObject());
    }

    /** Recheck the existing closed-cut guards and the entire prepared actual State/Environment pair. */
    public void validatePreparedObjectSolidPublication(ObjectSolidPublication publication) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(publication, "prepared SolidObject publication");
        if (!state.equals(publication.expectedState()) || !environment.equals(publication.expectedEnvironment())) {
            throw new IllegalArgumentException("Stale prepared SolidObject State/Environment cut");
        }
    }

    /** After caller preflight/pool publication, adopt only the already-built references; replay no work. */
    public void applyPreparedObjectSolidPublication(ObjectSolidPublication publication) {
        validatePreparedObjectSolidPublication(publication);
        state = publication.finalState();
        environment = publication.finalEnvironment();
    }

    /** The single existing-owner reconstruction for completed and stopped Solid final values. */
    private ObjectSolidPublication buildObjectSolidPublication(CdSolidObject.Player player,
                                                               CdSolidObject.Globals globals) {
        var expectedState = state; var expectedEnvironment = environment;
        var actor = player.actor(); var h = expectedState.header(); var timers = expectedState.timers();
        var nextState = new CdPlayerBody.State(actor.motion(), expectedState.animation(),
                new CdPlayerBody.Header(h.slotId(), h.mappingAddress(), actor.tile(), actor.priority(), h.width()),
                new CdPlayerBody.Timers(actor.hurt(), timers.invincibility(), timers.speedShoes(), timers.reset()),
                expectedState.history(), actor.rotationDistance(),
                new CdPlayerBody.ObjectLinks(player.pushObject(), player.standObject()), expectedState.rotationAngle());
        var m = expectedEnvironment.movement(); var g = m.globals(); var body = expectedEnvironment.globals();
        var movementGlobals = new CdPlayerMovement.Globals(g.lookMode(), g.cameraYCentre(), globals.primaryAngle(),
                globals.secondaryAngle(), g.boredTimer(), globals.scoreChain(), g.debugAngle(), g.debugAngleShift(),
                g.debugQuadrant(), g.debugFloorDistance());
        var nextEnvironment = new CdPlayerBody.Environment(
                new CdPlayerMovement.Environment(m.physics(), m.bounds(), m.gates(), movementGlobals), expectedEnvironment.controls(),
                new CdPlayerBody.Globals(body.levelFrames(), body.cameraXCentre(), body.timeAttackMode(), body.debugMode(),
                        body.debugCheat(), body.timeWarpTimer(), globals.invincible(), body.bossMusic()), expectedEnvironment.dynamicSlots());
        return new ObjectSolidPublication(expectedState, expectedEnvironment, nextState, nextEnvironment);
    }

    /**
     * Validate a direct Disc delta at the existing closed object cut. The complete actual value pair
     * is compared before the Disc owner's shared pure store validator. No native invocation is implied.
     */
    public void validateObjectDiscPlayerResult(CdPlayerBody.State expectedState,
                                               CdPlayerBody.Environment expectedEnvironment,
                                               CdSpinningDisc.PlayerResult result) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "Disc player State");
        Objects.requireNonNull(expectedEnvironment, "Disc player Environment");
        Objects.requireNonNull(result, "Disc player result");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale Disc player State/Environment cut");
        }
        CdSpinningDisc.validatePlayerResult(new CdSpinningDisc.PlayerCut(expectedState, expectedEnvironment), result);
    }

    /** Adopt the validated prebuilt pair only; no Body, store replay, service, sound or callback runs. */
    public void applyObjectDiscPlayerResult(CdPlayerBody.State expectedState,
                                            CdPlayerBody.Environment expectedEnvironment,
                                            CdSpinningDisc.PlayerResult result) {
        validateObjectDiscPlayerResult(expectedState, expectedEnvironment, result);
        state = result.finalState();
        environment = result.finalEnvironment();
    }
    // END prebuilt Solid and direct Disc player value publication ports.


    /**
     * Prepare only ObjBreakableWall_Main.BreakUp's ASR.W player X-velocity store
     * (Breakable Wall/Main.asm:56-60, native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff).
     * Complete-value CAS and the existing closed-cut guards precede construction. No actor,
     * environment, display, art, clock or callback is published. The private caller owns the
     * actual break/FM/source sequence and must prepare every owner before adopting any output;
     * this reusable value carrier supplies no invocation or exactly-once authority.
     */
    public ObjectSolidPublication prepareObjectBreakableWallXVelocity(CdPlayerBody.State expectedState,
                                                                      CdPlayerBody.Environment expectedEnvironment) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "Breakable Wall player State");
        Objects.requireNonNull(expectedEnvironment, "Breakable Wall player Environment");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale Breakable Wall player State/Environment cut");
        }
        var actual = state; var m = actual.motion(); int velocity = m.xVelocity() >> 1;
        var next = actual;
        if (velocity != m.xVelocity()) {
            var motion = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), velocity, m.yVelocity(),
                    m.groundVelocity(), m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(),
                    m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(),
                    m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            next = new CdPlayerBody.State(motion, actual.animation(), actual.header(), actual.timers(),
                    actual.history(), actual.rotationDistance(), actual.objectLinks(), actual.rotationAngle());
        }
        return new ObjectSolidPublication(actual, environment, next, environment);
    }

    /**
     * Prepare an ID07 player value pair at the existing closed object cut without publishing it.
     * The complete current State/Environment pair and the tunnel owner's pure finite store predicate
     * are checked before allocating the existing carrier. These values supply no native invocation,
     * source/pool identity, phase, reentry or exactly-once authority; the private Runtime owns those.
     */
    public ObjectSolidPublication prepareObjectTunnelPathPlayerResult(CdPlayerBody.State expectedState,
                                                                      CdPlayerBody.Environment expectedEnvironment,
                                                                      CdTunnelPath.PlayerResult result) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "Tunnel Path player State");
        Objects.requireNonNull(expectedEnvironment, "Tunnel Path player Environment");
        Objects.requireNonNull(result, "Tunnel Path player result");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale Tunnel Path player State/Environment cut");
        }
        CdTunnelPath.validatePlayerResult(new CdTunnelPath.PlayerCut(expectedState, expectedEnvironment), result);
        return new ObjectSolidPublication(expectedState, expectedEnvironment, result.finalState(), result.finalEnvironment());
    }

    /**
     * Prepare only the Frame stores of reached ObjGoalPost_Main (Level/_Objects/Level End/Main.asm:626-650,
     * scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff): leftBound WORD receives cameraX,
     * timeWarpTimer WORD and timeWarpDir BYTE clear. The private caller owns the reached Y/X gates,
     * actual source/camera/phase, destLeftBound/contact timeWarp stores and LoadPLC $12 call.
     * This reusable complete-value CAS carries no invocation or exactly-once authority and executes
     * no Body, service, cue, input, clock, callback or publication. The untouched actor retains this
     * Frame's actual reference even when the expected pair is separately constructed but equal.
     */
    public ObjectSolidPublication prepareObjectGoalPostReached(CdPlayerBody.State expectedState,
                                                               CdPlayerBody.Environment expectedEnvironment,
                                                               int cameraXWord) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "Goal Post player State");
        Objects.requireNonNull(expectedEnvironment, "Goal Post player Environment");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale Goal Post player State/Environment cut");
        }
        if ((cameraXWord & ~0xFFFF) != 0) {
            throw new IllegalArgumentException("Goal Post camera X requires an unsigned native word");
        }
        var actualState = state; var actualEnvironment = environment;
        var movement = actualEnvironment.movement(); var bounds = movement.bounds(); var gates = movement.gates();
        var body = actualEnvironment.globals();
        var nextMovement = new CdPlayerMovement.Environment(movement.physics(),
                new CdPlayerMovement.Bounds(cameraXWord, bounds.right(), bounds.bottom(), bounds.bossFight()),
                new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), gates.miniSonic(), gates.waterSlide(),
                        gates.windTunnel(), gates.speedShoes(), 0), movement.globals());
        var nextEnvironment = new CdPlayerBody.Environment(nextMovement, actualEnvironment.controls(),
                new CdPlayerBody.Globals(body.levelFrames(), body.cameraXCentre(), body.timeAttackMode(), body.debugMode(),
                        body.debugCheat(), 0, body.invincible(), body.bossMusic()), actualEnvironment.dynamicSlots());
        return new ObjectSolidPublication(actualState, actualEnvironment, actualState, nextEnvironment);
    }

    /**
     * Pure TitleCard/Main.asm:184 literal ctrlLocked BYTE clear, and Main:274's
     * literal BYTE1 setup. The actual programme/walk producer admits native order.
     * ScrollLock belongs to the scroll owner and is deliberately rejected here.
     * This full-pair CAS prepares values only; it executes no service or callback.
     */
    public ObjectSolidPublication prepareObjectTitleCardGlobal(CdPlayerBody.State expectedState,
                                                                CdPlayerBody.Environment expectedEnvironment,
                                                                com.openggf.game.soniccd.objects.CdTitleCard.StoreField field,
                                                                int value) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "Title player State");
        Objects.requireNonNull(expectedEnvironment, "Title player Environment");
        Objects.requireNonNull(field, "Title store field");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment))
            throw new IllegalArgumentException("Stale Title player State/Environment cut");
        if (field != com.openggf.game.soniccd.objects.CdTitleCard.StoreField.CONTROL_LOCK_BYTE
                || (value != 0 && value != 1))
            throw new IllegalArgumentException("Frame owns only the Title control-lock BYTE0/1");
        var actualState = state; var actualEnvironment = environment;
        var controls = actualEnvironment.controls();
        var nextEnvironment = controls.controlLocked() == value ? actualEnvironment
                : withControls(actualEnvironment, new CdPlayerBody.Controls(controls.rawHeld(), controls.rawTap(),
                        controls.logicalHeld(), controls.logicalTap(), value));
        return new ObjectSolidPublication(actualState, actualEnvironment, actualState, nextEnvironment);
    }

    /**
     * Prepare one Frame-owned Signpost store without adopting it. Native pin
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level End/Main.asm:771,779-780,830-834.
     * _Include/Main CPU Variables.inc:74-75 defines the two powerup BYTE flags;
     * _Definitions.inc:83-85 defines separate player timer WORDs, which these stores do not clear.
     * _Definitions.inc:181-186 places logical playerCtrlHold/high and playerCtrlTap/low before
     * separate raw p1 held/tap bytes; Sonic/Main.asm:297-299 owns their ordinary copy gate.
     * BSET0 retains every other ctrlLocked bit (_Definitions.inc:327; Level End:830).
     *
     * Root authenticates the actual private source/phase, native order and reached store site,
     * including the exact zoneAct $0502 zero-control override. These reusable complete-value CAS
     * values confer no invocation or exactly-once authority. No other Signpost store belongs to
     * Frame. No Body, art, clock, callback, sound, GoalPost reset or adoption is executed here.
     */
    public ObjectSolidPublication prepareObjectSignpostStore(CdPlayerBody.State expectedState,
                                                             CdPlayerBody.Environment expectedEnvironment,
                                                             CdSignpost.StoreField field, int value) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "Signpost player State");
        Objects.requireNonNull(expectedEnvironment, "Signpost player Environment");
        Objects.requireNonNull(field, "Signpost store field");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale Signpost player State/Environment cut");
        }
        var actualState = state; var actualEnvironment = environment;
        var movement = actualEnvironment.movement(); var bounds = movement.bounds(); var gates = movement.gates();
        var controls = actualEnvironment.controls(); var body = actualEnvironment.globals();
        var nextMovement = movement; var nextControls = controls; var nextBody = body;
        switch (field) {
            case LEFT_BOUND_WORD -> {
                if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Signpost left bound requires an unsigned native word");
                if (bounds.left() != value) nextMovement = new CdPlayerMovement.Environment(movement.physics(),
                        new CdPlayerMovement.Bounds(value, bounds.right(), bounds.bottom(), bounds.bossFight()), gates, movement.globals());
            }
            case SPEED_SHOES_BYTE -> {
                if (value != 0) throw new IllegalArgumentException("Signpost clears only the speedShoes byte flag");
                if (gates.speedShoes() != 0) nextMovement = new CdPlayerMovement.Environment(movement.physics(), bounds,
                        new CdPlayerMovement.Gates(gates.zoneAct(), gates.timeZone(), gates.miniSonic(), gates.waterSlide(),
                                gates.windTunnel(), 0, gates.timeWarpDirection()), movement.globals());
            }
            case INVINCIBLE_BYTE -> {
                if (value != 0) throw new IllegalArgumentException("Signpost clears only the invincible byte flag");
                if (body.invincible() != 0) nextBody = new CdPlayerBody.Globals(body.levelFrames(), body.cameraXCentre(),
                        body.timeAttackMode(), body.debugMode(), body.debugCheat(), body.timeWarpTimer(), 0, body.bossMusic());
            }
            case CONTROL_LOCK_BIT0 -> {
                if (value != 1) throw new IllegalArgumentException("Signpost sets only control-lock bit zero");
                int locked = controls.controlLocked() | 1;
                if (locked != controls.controlLocked()) nextControls = new CdPlayerBody.Controls(controls.rawHeld(), controls.rawTap(),
                        controls.logicalHeld(), controls.logicalTap(), locked);
            }
            case PLAYER_CONTROL_HOLD_WORD -> {
                if (value != 0x0808 && value != 0) throw new IllegalArgumentException("Signpost logical control word is $0808 or zero");
                int held = value >>> 8, tap = value & 0xFF;
                if (held != controls.logicalHeld() || tap != controls.logicalTap())
                    nextControls = new CdPlayerBody.Controls(controls.rawHeld(), controls.rawTap(), held, tap, controls.controlLocked());
            }
            default -> throw new IllegalArgumentException("Signpost store is not Frame-owned: " + field);
        }
        var nextEnvironment = nextMovement == movement && nextControls == controls && nextBody == body
                ? actualEnvironment : new CdPlayerBody.Environment(nextMovement, nextControls, nextBody, actualEnvironment.dynamicSlots());
        return new ObjectSolidPublication(actualState, actualEnvironment, actualState, nextEnvironment);
    }

    /**
     * Prepare one Frame-owned ObjBigRing_Main store without adopting it. Native pin
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/_Objects/Level End/Main.asm:488-503.
     * MOVE.W oX preserves the fraction word; BSET0 preserves every other ctrlLocked bit.
     * _Definitions.inc:181-186 places logical playerCtrlHold/high and playerCtrlTap/low before
     * separate raw p1 held/tap bytes; Sonic/Main.asm:297-299 owns their ordinary copy gate.
     * XVel and playerGVel clear independently, preserving all Y motion and other actor fields.
     *
     * Root owns the actual private Big Ring source/camera+336, placement cut, reached gates and
     * native store order. The public operation and its descriptive placement/source values confer
     * no pool, Frame, invocation or exactly-once authority here. This complete-value CAS executes
     * no Body, input, clock, callback, capture, sound, art, scroll, object allocation or adoption work.
     */
    public ObjectSolidPublication prepareObjectBigRingPlayerStore(CdPlayerBody.State expectedState,
                                                                  CdPlayerBody.Environment expectedEnvironment,
                                                                  CdBigRing.PlayerStore operation) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "Big Ring player State");
        Objects.requireNonNull(expectedEnvironment, "Big Ring player Environment");
        Objects.requireNonNull(operation, "Big Ring player store");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment)) {
            throw new IllegalArgumentException("Stale Big Ring player State/Environment cut");
        }
        var actualState = state; var actualEnvironment = environment;
        var motion = actualState.motion(); var controls = actualEnvironment.controls();
        int x = motion.centreX16_16(), xVelocity = motion.xVelocity(), groundVelocity = motion.groundVelocity();
        var nextControls = controls;
        switch (operation) {
            case CdBigRing.PlayerXWord store -> x = (store.value() << 16) | (x & 0xFFFF);
            case CdBigRing.ControlLockBit0 ignored -> {
                int locked = controls.controlLocked() | 1;
                if (locked != controls.controlLocked()) nextControls = new CdPlayerBody.Controls(controls.rawHeld(), controls.rawTap(),
                        controls.logicalHeld(), controls.logicalTap(), locked);
            }
            case CdBigRing.ControlHold0808 ignored -> {
                if (controls.logicalHeld() != 8 || controls.logicalTap() != 8)
                    nextControls = new CdPlayerBody.Controls(controls.rawHeld(), controls.rawTap(), 8, 8, controls.controlLocked());
            }
            case CdBigRing.XVelocityZero ignored -> xVelocity = 0;
            case CdBigRing.GroundVelocityZero ignored -> groundVelocity = 0;
        }
        var nextState = actualState;
        if (x != motion.centreX16_16() || xVelocity != motion.xVelocity() || groundVelocity != motion.groundVelocity()) {
            var nextMotion = new CdPlayerMotionState(x, motion.centreY16_16(), xVelocity, motion.yVelocity(), groundVelocity,
                    motion.routine(), motion.angle(), motion.flags(), motion.spriteFlags(), motion.xRadius(), motion.yRadius(),
                    motion.animation(), motion.previousAnimation(), motion.playerJump(), motion.charge(), motion.moveLock(),
                    motion.stick(), motion.playerControl(), motion.previousPrimaryAngle(), motion.previousSecondaryAngle());
            nextState = new CdPlayerBody.State(nextMotion, actualState.animation(), actualState.header(), actualState.timers(),
                    actualState.history(), actualState.rotationDistance(), actualState.objectLinks(), actualState.rotationAngle());
        }
        var nextEnvironment = nextControls == controls ? actualEnvironment
                : new CdPlayerBody.Environment(actualEnvironment.movement(), nextControls, actualEnvironment.globals(), actualEnvironment.dynamicSlots());
        return new ObjectSolidPublication(actualState, actualEnvironment, nextState, nextEnvironment);
    }

    /**
     * Pure reached Results programme gate value, native pin1ecc5f3a Results/Main.asm:295,300,311.
     * The actual private World producer owns operation admission and native order, including the
     * period store before the zoneAct store. These complete-value CAS arguments grant no source
     * family, programme transition, clock, Body or invocation authority. Preserve every actor bit,
     * all other Gates/Environment values and actual references; adopt via the existing pair port.
     */
    public ObjectSolidPublication prepareObjectResultsProgrammeGates(CdPlayerBody.State expectedState,
                                                                     CdPlayerBody.Environment expectedEnvironment,
                                                                     CdResults.ProgrammeField field, int value) {
        requireOpenIdle(); requireClosedFrame();
        Objects.requireNonNull(expectedState, "Results player State");
        Objects.requireNonNull(expectedEnvironment, "Results player Environment");
        Objects.requireNonNull(field, "Results programme gate field");
        if (!state.equals(expectedState) || !environment.equals(expectedEnvironment))
            throw new IllegalArgumentException("Stale Results player State/Environment cut");
        var actualState = state; var actualEnvironment = environment;
        var movement = actualEnvironment.movement(); var gates = movement.gates();
        int period = gates.timeZone(), zoneAct = gates.zoneAct();
        switch (field) {
            case TIME_ZONE_BYTE -> {
                if (value != 1 && value != 2) throw new IllegalArgumentException("Results timeZone is native BYTE1 or2");
                period = value;
            }
            case ZONE_ACT_WORD -> {
                if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Results zoneAct requires an unsigned native WORD");
                zoneAct = value;
            }
            default -> throw new IllegalArgumentException("Results programme field is not a Frame gate: " + field);
        }
        var nextEnvironment = actualEnvironment;
        if (period != gates.timeZone() || zoneAct != gates.zoneAct()) {
            var nextGates = new CdPlayerMovement.Gates(zoneAct, period, gates.miniSonic(), gates.waterSlide(),
                    gates.windTunnel(), gates.speedShoes(), gates.timeWarpDirection());
            var nextMovement = new CdPlayerMovement.Environment(movement.physics(), movement.bounds(), nextGates, movement.globals());
            nextEnvironment = new CdPlayerBody.Environment(nextMovement, actualEnvironment.controls(),
                    actualEnvironment.globals(), actualEnvironment.dynamicSlots());
        }
        return new ObjectSolidPublication(actualState, actualEnvironment, actualState, nextEnvironment);
    }

}
