package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdR13ReusedObjectAssets;

import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.player.CdPlayerMovement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * ObjMonitorItem $1A, routines0/2/4 in native source order. Source pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Level/_Objects/Monitor and Time Post/Main.asm:
 * wrapper (:419-432), Init (:438-459), Main/reward prefix (:465-487), rewards (:486-618),
 * Delete (:624-638). Shipped FixBugs=0; CC_VARIANT branch encodings preserve the same delete sign test.
 *
 * <p>One dispatch is not a pool walk. Init falls into Main, old-negative Y velocity moves through the
 * shared raw ObjMove then adds $18, and the 33rd clean call reaches reward after32 moves. Animation
 * indexing doubles only the low byte (128 aliases0), sign-extends the actual retained MM word, then
 * adds1 to the full pointer. The possibly odd/static target is not dereferenced here; Root's mapper
 * owns that later finite boundary. No map/script bytes or assumed animation roots are embedded.
 *
 * <p>The legacy constructor admits only animation4. Named opt-in construction also admits Shield2
 * through a separate synchronous host port (Main.asm:525-529): contact.shield BYTE1, reserved whole6
 * ID BYTE3 only, then FM_SHIELD ($97). The host owns all three external effects and their failure
 * prefixes; there is no Frame shield field or reserved body here. Before either award, routine4/
 * deletion-word29 prefix is live. For shoes, Root projects actual Frame/world values, calls its pure
 * shoes owner, validates/commits exactly once and performs ordered sound calls. This body contains no reward values,
 * Frame patch, timer tick or no-op award. Other rewards stop explicitly after the legitimate item prefix.
 * All effect metadata describes completed work and must never be replayed. Every returning routine,
 * even DeleteObject, returns to the wrapper DrawObject. Root owns scheduling/phase/nonreentry and assets.
 * OpenAI Codex GPT-6.1 Sol <noreply@openai.com>.
 */
public final class CdMonitorItem {
    public static final int OBJECT_ID = 0x1A;
    public enum NativeRoutine { INIT, MAIN, DELETE }
    public enum NativeCall { OBJ_MOVE, DRAW_OBJECT }
    public enum AwardSound { SCMD_FADEPCM, SCMD_SHOESMUS }

    public sealed interface Effect permits RoutineEntered, CallReturned, Awarded, Deleted, ShieldAwarded, RingsAwarded { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    public record Awarded(AwardCall call, AwardResult result) implements Effect {
        public Awarded { Objects.requireNonNull(call); Objects.requireNonNull(result); }
    }
    public record Deleted() implements Effect { }

    /** Borrow only the ACTUAL current primary header ID/$22 if a standing item's link0 reaches it. */
    public record PrimarySupport(int id, int flags22) {
        public PrimarySupport { byteValue(id); byteValue(flags22); }
    }
    /** No Frame/pool/context/current-state aggregate is stored in the call or its completed result. */
    public record AwardCall(CdObjectState.Identity identity, int rawAnimation) {
        public AwardCall { Objects.requireNonNull(identity); byteValue(rawAnimation); }
    }
    /**
     * Commands already performed by Root, in native order. Shoes always returns SHOESMUS, preceded
     * by FADEPCM only for raw timeZone byte0. This validates shape, never invents current world values
     * or performs I/O. Root must derive the selected list from its actual source-cut world inputs.
     */
    public record AwardResult(List<AwardSound> completedSounds) {
        public AwardResult {
            completedSounds = List.copyOf(completedSounds);
            if (!completedSounds.equals(List.of(AwardSound.SCMD_SHOESMUS))
                    && !completedSounds.equals(List.of(AwardSound.SCMD_FADEPCM, AwardSound.SCMD_SHOESMUS))) {
                throw new IllegalArgumentException("A completed shoes award needs its actual ordered native commands");
            }
        }
    }
    /**
     * Synchronous Root-owned source cut. Verify open post-Body object phase/identity/live animation,
     * project actual values, run the pure reward transform, validate/apply the closed Frame patch once,
     * execute its SubCPU calls in order and return completed observations. No pool writes/reentry,
     * deferred commit, retained context, fake success or replay. Exceptions retain the item prefix.
     */
    @FunctionalInterface public interface AwardPort { AwardResult award(AwardCall call); }

    /** Separate finite Shield metadata; AwardSound/AwardResult retain their shoes-only grammar. */
    public enum ShieldSound { FM_SHIELD }
    public record ShieldAwardCall(CdObjectState.Identity identity, int rawAnimation) {
        public ShieldAwardCall {
            Objects.requireNonNull(identity);
            if (rawAnimation != 2) throw new IllegalArgumentException("Shield requires raw animation BYTE2");
        }
    }
    public record ShieldAwardResult(ShieldSound completedSound) {
        public ShieldAwardResult { Objects.requireNonNull(completedSound); }
    }
    public record ShieldAwarded(ShieldAwardCall call, ShieldAwardResult result) implements Effect {
        public ShieldAwarded { Objects.requireNonNull(call); Objects.requireNonNull(result); }
    }
    /**
     * Root synchronously stores ContactGlobals.shield BYTE1, then only reserved whole6 ID BYTE3,
     * then calls FM_SHIELD97, in that order. Return only after actual completion, never as commands.
     * Root validates each external prefix/once-only store and retains it on failure. This component
     * guards its dynamic/source banks, but cannot attest external stores, FM, Frame or World state.
     */
    @FunctionalInterface public interface ShieldAwardPort { ShieldAwardResult award(ShieldAwardCall call); }

    /**
     * Finite DEV source-call observations for ObjMonitorItem_Main .CheckRing/.GiveRings/.RingSound
     * (src/Level/_Objects/Monitor and Time Post/Main.asm:498-517), .Give1UP (:490-494), native pin
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. These typed names attest neither numeric driver
     * commands nor audible playback. Root owns actual FM/SubCPU dispatch and sound ordering.
     */
    public enum RingAwardSound { FM_RING, SCMD_YESSFX }
    public record RingAwardCall(CdObjectState.Identity identity, int rawAnimation) {
        public RingAwardCall {
            Objects.requireNonNull(identity);
            if (rawAnimation != 1) throw new IllegalArgumentException("Rings require raw animation BYTE1");
        }
    }
    /** Scalar completion only: no command, mutable bank, source cut or callback authority. */
    public record RingAwardResult(RingAwardSound sound) {
        public RingAwardResult { Objects.requireNonNull(sound); }
    }
    public record RingsAwarded(RingAwardCall call, RingAwardResult result) implements Effect {
        public RingsAwarded { Objects.requireNonNull(call); Objects.requireNonNull(result); }
    }
    /**
     * Root projects its actual eight CdRingGlobals fields, calls monitorItemTen, validates/applies
     * the native write set once, then completes exactly FM_RING or SubCPU SCMD_YESSFX. The latter
     * returns directly from .Give1UP without FM_RING. Return only after actual ordered completion.
     * No fabricated source, repeated single-ring collection, retained cut, pool writes or reentry.
     * This component cannot attest external global stores, sound, Frame/World phase or generation.
     */
    @FunctionalInterface public interface RingAwardPort { RingAwardResult award(RingAwardCall call); }

    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }
    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, REWARD_ONE_UP, REWARD_RINGS, REWARD_SHIELD, REWARD_INVINCIBILITY,
        REWARD_TIME_STOP, REWARD_COMBINE_RING, REWARD_S
    }
    public static final class UnsupportedMonitorItem extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedMonitorItem(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Monitor item whole slot " + wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final CdMonitor.Assets assets;
    private final AwardPort award;
    private final CdMonitor.DrawObjectCall drawObject;

    public CdMonitorItem(CdObjectPlacementRuntime runtime, CdMonitor.Assets assets,
                         AwardPort award, CdMonitor.DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime); this.assets = Objects.requireNonNull(assets);
        this.award = Objects.requireNonNull(award); this.drawObject = Objects.requireNonNull(drawObject);
    }

    private ShieldAwardPort shieldAward;
    private RingAwardPort ringAward;
    private DispatchObserver observer;
    private ReceiptGuard receiptGuard;
    private boolean running, primitiveActive, resultReady;
    private Object pendingOperation, operationResult;
    private long operationOrdinal;

    /** Public immutable descriptions are comparison data, never producer/adoption authority. */
    public enum StoreKind { INIT_PREFIX, MAPPING_POINTER, MOVE_READY, MOVE_RESULT, REWARD_PREFIX, DELETE_TIMER, WRAPPER_READY }
    public record Publication(StoreKind kind, CdObjectPlacementRuntime.Snapshot cut,
                              CdObjectState source, CdObjectState output) { }
    public record MappingReadCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source, int byteOffset) { }
    public record MotionCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source,
                             Optional<PrimarySupport> primarySupport) { }
    public record ShoesCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source, AwardCall request) { }
    public record ShieldCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source, ShieldAwardCall request) { }
    public record RingCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source, RingAwardCall request) { }
    public record DeleteCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) { }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) { }
    /** The exact Result exposed by currentResult at this cut must be the object returned to Root. */
    public record CompletionCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) { }
    public interface DispatchObserver {
        void beforeOperation(Object call);
        void afterOperation(Object call);
        void failedOperation(Object call, Throwable original);
    }
    public interface ReceiptGuard { void prepared(Object call); void completed(Object call); }

    // BEGIN R13 SOURCE FACTORY 20261106
    public static CdMonitorItem fromAct3FutureAssets(CdObjectPlacementRuntime runtime,CdR13FutureAssets assets,CdR13ReusedObjectAssets owner,
            AwardPort shoes,ShieldAwardPort shield,RingAwardPort rings,CdMonitor.DrawObjectCall draw,DispatchObserver observer,ReceiptGuard guard) {
        Objects.requireNonNull(runtime,"actual placement");Objects.requireNonNull(assets,"actual original future source");Objects.requireNonNull(owner,"actual reuse metadata");
        if(!runtime.ownsAct3FutureAssets(assets)||!owner.ownsAssets(assets,owner.startup()))throw new IllegalArgumentException("R13 reused body requires exact retained source/placement/metadata");
        var data=new CdMonitor.Assets(owner.startup().monitorTime(),com.openggf.game.soniccd.level.CdR13ObjectAssets.View.MAP_SPR_MONITOR_TIME.range(assets.identity().file()).nativeAddress());
        return withWorldOwner(runtime,data,shoes,shield,rings,draw,observer,guard);
    }
    // END R13 SOURCE FACTORY 20261106

    /** Actual source-paired R12B ingress over the unchanged finite reward/receipt owner. */
    public static CdMonitorItem fromAct2PastAssets(CdObjectPlacementRuntime runtime, CdR12bTerrainAssets owner,
                                                  AwardPort shoes, ShieldAwardPort shield, RingAwardPort rings,
                                                  CdMonitor.DrawObjectCall draw, DispatchObserver observer,
                                                  ReceiptGuard guard) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(owner, "owner");
        if (!runtime.sameAct2PastSource(owner)) throw new IllegalArgumentException("Monitor item placement/assets source differs");
        return withWorldOwner(runtime, CdMonitor.Assets.fromAct2PastAssets(owner.objects()), shoes, shield, rings, draw, observer, guard);
    }

    public CdMonitor.Assets assets() { return assets; }
    public boolean isCurrentCall(Object call) { return running && call != null && call == pendingOperation; }
    public boolean isCurrentPrimitive(Object call) { return isCurrentCall(call) && primitiveActive; }
    public long currentOrdinal(Object call) {
        if (!isCurrentCall(call)) throw new IllegalStateException("No original pending Monitor item operation");
        return operationOrdinal;
    }
    public Object currentResult(Object call) {
        if (!isCurrentCall(call) || !resultReady || primitiveActive)
            throw new IllegalStateException("No completed original Monitor item operation");
        return operationResult;
    }

    /** Explicit Shield opt-in without a World receipt binding; the old constructor stays unbound. */
    public static CdMonitorItem withShieldAward(CdObjectPlacementRuntime runtime, CdMonitor.Assets assets,
                                               AwardPort shoes, ShieldAwardPort shield, CdMonitor.DrawObjectCall draw) {
        var body = new CdMonitorItem(runtime, assets, shoes, draw);
        body.shieldAward = Objects.requireNonNull(shield); return body;
    }
    /**
     * Root caches this exact body and binds a private invocation scope/guard. Supply the retained
     * CdMonitor.Assets.fromAct2Assets view and its matching runtime; this factory alone grants no
     * media, phase, generation or Frame authority. No callback may reenter this bound body.
     */
    public static CdMonitorItem withWorldOwner(CdObjectPlacementRuntime runtime, CdMonitor.Assets assets,
                                              AwardPort shoes, ShieldAwardPort shield, CdMonitor.DrawObjectCall draw,
                                              DispatchObserver observer, ReceiptGuard guard) {
        var body = withShieldAward(runtime, assets, shoes, shield, draw);
        body.observer = Objects.requireNonNull(observer); body.receiptGuard = Objects.requireNonNull(guard);
        return body;
    }

    /** Explicit Ring/Shield opt-in; uses the inherited constructor and nonreentry/storage protocol. */
    public static CdMonitorItem withRingAndShieldAward(CdObjectPlacementRuntime runtime, CdMonitor.Assets assets,
                                                      AwardPort shoes, ShieldAwardPort shield, RingAwardPort rings,
                                                      CdMonitor.DrawObjectCall draw) {
        var body = withShieldAward(runtime, assets, shoes, shield, draw);
        body.ringAward = Objects.requireNonNull(rings); return body;
    }
    /** Same private World receipt binding as the inherited overload; no additional owner protocol. */
    public static CdMonitorItem withWorldOwner(CdObjectPlacementRuntime runtime, CdMonitor.Assets assets,
                                              AwardPort shoes, ShieldAwardPort shield, RingAwardPort rings,
                                              CdMonitor.DrawObjectCall draw, DispatchObserver observer, ReceiptGuard guard) {
        var body = withWorldOwner(runtime, assets, shoes, shield, draw, observer, guard);
        body.ringAward = Objects.requireNonNull(rings); return body;
    }

    @FunctionalInterface private interface Operation<T> { T run(PrimitiveReceipt receipt); }
    private static final class PrimitiveReceipt {
        CdObjectState expected;
        PrimitiveReceipt(CdObjectState source) { expected = source; }
    }
    /** No replay/rollback: a completed primitive prefix remains live even if verification fails. */
    private <T> T observed(Object call, CdObjectPlacementRuntime.Snapshot cut,
                           CdObjectState source, Operation<T> operation) {
        if (pendingOperation != null) throw new IllegalStateException("Nested Monitor item operation");
        pendingOperation = call; operationOrdinal++; resultReady = false;
        var receipt = new PrimitiveReceipt(source);
        try {
            verifyStorage(cut, receipt);
            if (observer != null) {
                observer.beforeOperation(call); verifyStorage(cut, receipt);
                receiptGuard.prepared(call); verifyStorage(cut, receipt);
            }
            T result;
            primitiveActive = true;
            try { result = operation.run(receipt); } finally { primitiveActive = false; }
            verifyStorage(cut, receipt);
            operationResult = result; resultReady = true;
            if (observer != null) {
                observer.afterOperation(call); verifyStorage(cut, receipt);
                receiptGuard.completed(call); verifyStorage(cut, receipt);
            }
            return result;
        } catch (RuntimeException | Error original) {
            verifySuppressed(original, () -> verifyStorage(cut, receipt));
            if (observer != null) {
                verifySuppressed(original, () -> observer.failedOperation(call, original));
                verifySuppressed(original, () -> verifyStorage(cut, receipt));
            }
            throw original;
        } finally {
            primitiveActive = false; pendingOperation = null; operationResult = null; resultReady = false;
        }
    }
    private void verifyStorage(CdObjectPlacementRuntime.Snapshot cut, PrimitiveReceipt receipt) {
        runtime.validateRestore(cut);
        var actual = runtime.capture();
        if (!cut.status().equals(actual.status())
                || !Arrays.equals(cut.savedFlags().copy(), actual.savedFlags().copy()))
            throw new IllegalStateException("Monitor item callback changed placement/saved storage");
        int own = receipt.expected.identity().wholeIndex();
        for (var before : cut.pool().slots()) {
            int whole = before.identity().wholeIndex();
            var expected = whole == own ? receipt.expected : before;
            var live = runtime.pool().state(whole);
            if (!expected.equals(live))
                throw new IllegalStateException("Monitor item callback changed unowned dynamic bytes at " + whole);
        }
    }
    private static void verifySuppressed(Throwable original, Runnable verification) {
        try { verification.run(); }
        catch (RuntimeException | Error fault) { if (fault != original) original.addSuppressed(fault); }
    }
    private CdObjectPlacementRuntime.Snapshot callbackCut(Cursor c) {
        var cut = runtime.capture();
        if (!c.state.equals(runtime.pool().state(c.wholeIndex)))
            throw new IllegalStateException("Monitor item source changed before its synchronous operation");
        return cut;
    }
    private void publish(Cursor c, StoreKind kind) {
        if (observer == null) { c.pool.write(c.state); return; }
        var cut = runtime.capture(); var source = c.pool.state(c.wholeIndex); var output = c.state;
        observed(new Publication(kind, cut, source, output), cut, source, receipt -> {
            c.pool.write(output); receipt.expected = output; return null;
        });
    }
    private void draw(Cursor c) {
        if (observer == null) { drawObject.drawObject(c.pool, c.state.identity()); return; }
        var cut = callbackCut(c); var source = c.state;
        observed(new DrawCall(cut, source), cut, source, receipt -> {
            Throwable original = null;
            try { drawObject.drawObject(c.pool, source.identity()); }
            catch (RuntimeException | Error failure) { original = failure; throw failure; }
            finally {
                // Draw's permission ends at primitive exit, even when another storage check will fail.
                // Retain every expected non-bit7 byte instead of adopting a possibly corrupted image.
                try {
                    int flags = c.pool.state(c.wholeIndex).spriteFlags();
                    receipt.expected = receipt.expected.moveByte(SPRITE_FLAGS,
                            (receipt.expected.spriteFlags() & 127) | (flags & 128));
                } catch (RuntimeException | Error verification) {
                    if (original == null) throw verification;
                    if (verification != original) original.addSuppressed(verification);
                }
            }
            return null;
        });
    }

    /** ObjMonitorItem (:419-425). Optional.empty is an explicit missing primary cut, never ID0/flags0. */
    public Result run(int wholeIndex, Optional<PrimarySupport> primarySupport) {
        // Legacy unbound path retains its original callback/write behavior, including lack of receipts.
        if (shieldAward == null) return runSelected(wholeIndex, primarySupport);
        if (running) throw new IllegalStateException("Reentrant Monitor item invocation");
        running = true; operationOrdinal = 0;
        try { return runSelected(wholeIndex, primarySupport); }
        finally { running = false; }
    }
    private Result runSelected(int wholeIndex, Optional<PrimarySupport> primarySupport) {
        Objects.requireNonNull(primarySupport);
        var pool = runtime.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Monitor item requires live ID $1A at whole " + wholeIndex);
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 4) throw new UnsupportedMonitorItem(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, routine);
        var c = new Cursor(pool, state);
        switch (routine) {
            case 0 -> { init(c); main(c, primarySupport); }
            case 2 -> main(c, primarySupport);
            case 4 -> delete(c);
        }
        publish(c, StoreKind.WRAPPER_READY);
        draw(c); // Native wrapper runs even after DeleteObject.
        c.state = pool.state(wholeIndex);
        c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        if (observer == null) return new Result(c.state, c.effects);
        var cut = callbackCut(c);
        return observed(new CompletionCall(cut, c.state), cut, c.state, receipt -> new Result(c.state, c.effects));
    }

    /** ObjMonitorItem_Init (:438-459), including the byte-width ADD.B and full signed ADDA.W. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveWord(TILE, 0x85A8);
        if (c.state.unsignedByte(LAYER) != 0) c.state = c.state.moveWord(TILE, c.state.word(TILE) & 0x7FFF);
        c.state = c.state.moveByte(SPRITE_FLAGS, 0x24).moveByte(PRIORITY, 3).moveByte(WIDTH, 8).moveWord(Y_VELOCITY, -0x300);
        int animation = c.state.unsignedByte(ANIMATION);
        c.state = c.state.moveByte(MAP_FRAME, animation);
        publish(c, StoreKind.INIT_PREFIX); // These stores precede the actual retained-table word read.
        int index = (animation + animation) & 0xFF;
        int relative;
        if (observer == null) relative = (short) assets.visual().mappings().unsignedWord(index);
        else {
            var cut = callbackCut(c);
            relative = observed(new MappingReadCall(cut, c.state, index), cut, c.state,
                    receipt -> (int) (short) assets.visual().mappings().unsignedWord(index));
        }
        int pointer = assets.mappingsAddress() + relative + 1;
        // Do not read/validate a target before DrawObjects does so. Static mapping pointers may be odd.
        c.state = c.state.moveLong(MAPPINGS, pointer);
        publish(c, StoreKind.MAPPING_POINTER);
    }

    /** ObjMonitorItem_Main (:465-482), followed only by the actually reached reward entry. */
    private void main(Cursor c, Optional<PrimarySupport> primarySupport) {
        c.enter(NativeRoutine.MAIN);
        if (c.state.signedWord(Y_VELOCITY) < 0) {
            publish(c, StoreKind.MOVE_READY);
            CdPlayerMovement.RawObjectMoveResult moved;
            if (observer == null) moved = CdPlayerMovement.moveObject(CdMonitor.rawMotion(c.state), support(c, primarySupport));
            else {
                var cut = callbackCut(c);
                moved = observed(new MotionCall(cut, c.state, primarySupport), cut, c.state,
                        receipt -> CdPlayerMovement.moveObject(CdMonitor.rawMotion(c.state), support(c, primarySupport)));
            }
            // ObjMove writes position longs only. Its local flipper resistance never stores X velocity.
            c.state = c.state.moveLong(X_POSITION, moved.centreX16_16()).moveLong(Y_POSITION, moved.centreY16_16())
                    .addWord(Y_VELOCITY, 0x18);
            publish(c, StoreKind.MOVE_RESULT); c.effects.add(new CallReturned(NativeCall.OBJ_MOVE));
            return;
        }
        c.state = c.state.addByte(ROUTINE, 2).moveWord(MONITOR_ITEM_DELETE, 29);
        publish(c, StoreKind.REWARD_PREFIX);
        int kind = c.state.unsignedByte(ANIMATION);
        if (kind == 1 && ringAward != null) {
            var call = new RingAwardCall(c.state.identity(), kind); var cut = callbackCut(c);
            var completed = observed(new RingCall(cut, c.state, call), cut, c.state,
                    receipt -> Objects.requireNonNull(ringAward.award(call), "Root must complete the actual Rings award"));
            c.effects.add(new RingsAwarded(call, completed)); return;
        }
        if (kind == 2 && shieldAward != null) {
            var call = new ShieldAwardCall(c.state.identity(), kind); var cut = callbackCut(c);
            var completed = observed(new ShieldCall(cut, c.state, call), cut, c.state,
                    receipt -> Objects.requireNonNull(shieldAward.award(call), "Root must complete the actual Shield award"));
            c.effects.add(new ShieldAwarded(call, completed)); return;
        }
        if (kind != 4) {
            var boundary = switch (kind) {
                case 0 -> UnsupportedBoundary.REWARD_ONE_UP;
                case 1 -> UnsupportedBoundary.REWARD_RINGS;
                case 2 -> UnsupportedBoundary.REWARD_SHIELD;
                case 3 -> UnsupportedBoundary.REWARD_INVINCIBILITY;
                case 5 -> UnsupportedBoundary.REWARD_TIME_STOP;
                case 6 -> UnsupportedBoundary.REWARD_COMBINE_RING;
                default -> UnsupportedBoundary.REWARD_S; // Source .CheckS handles every non0..6 byte.
            };
            throw new UnsupportedMonitorItem(boundary, c.wholeIndex, kind);
        }
        var call = new AwardCall(c.state.identity(), kind);
        AwardResult completed;
        if (observer == null) completed = Objects.requireNonNull(award.award(call), "Root must return a completed award result");
        else {
            var cut = callbackCut(c);
            completed = observed(new ShoesCall(cut, c.state, call), cut, c.state,
                    receipt -> Objects.requireNonNull(award.award(call), "Root must return a completed award result"));
        }
        if (!c.pool.state(c.wholeIndex).equals(c.state)) {
            throw new IllegalStateException("Monitor award port changed the item slot");
        }
        c.effects.add(new Awarded(call, completed));
    }

    /** Actual ID/flags reads only when ObjMove reaches standing bit3. No support normalization. */
    private static Optional<CdPlayerMovement.RawObjectSupport> support(Cursor c, Optional<PrimarySupport> primary) {
        if ((c.state.flags() & 8) == 0) return Optional.empty();
        int link = c.state.unsignedByte(STAND_LINK_3D);
        if (link == 0) {
            return primary.map(p -> new CdPlayerMovement.RawObjectSupport(0, p.id(), p.flags22()));
        }
        if (link >= CdObjectPool.FIRST_WHOLE_INDEX && link < CdObjectPool.END_WHOLE_INDEX) {
            var actual = c.pool.state(link);
            return Optional.of(new CdPlayerMovement.RawObjectSupport(link, actual.id(), actual.flags()));
        }
        // The shared raw owner throws its existing RESERVED_OBJECT_SLOT/OBJECT_ADDRESS at that read.
        return Optional.empty();
    }

    /** ObjMonitorItem_Delete (:624-638): decrement WORD, then B(M)I tests the resulting sign. */
    private void delete(Cursor c) {
        c.enter(NativeRoutine.DELETE);
        c.state = c.state.addWord(MONITOR_ITEM_DELETE, -1); publish(c, StoreKind.DELETE_TIMER);
        if (c.state.signedWord(MONITOR_ITEM_DELETE) < 0) {
            if (observer == null) c.pool.deleteObject(c.wholeIndex);
            else {
                var cut = callbackCut(c);
                observed(new DeleteCall(cut, c.state), cut, c.state, receipt -> {
                    c.pool.deleteObject(c.wholeIndex); receipt.expected = c.pool.state(c.wholeIndex); return null;
                });
            }
            c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted());
        }
    }

    private static final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        Cursor(CdObjectPool pool, CdObjectState state) {
            this.pool = pool; this.state = state; wholeIndex = state.identity().wholeIndex();
        }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
    }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
}
