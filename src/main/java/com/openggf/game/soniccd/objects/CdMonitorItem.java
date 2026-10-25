package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.CdPlayerMovement;

import java.util.ArrayList;
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
 * <p>Only reached animation4 has an award port. Before that synchronous callback the native routine4/
 * deletion-word29 prefix is live. Root projects actual Frame/world values, calls its pure shoes owner,
 * validates/commits exactly once and performs ordered sound calls. This body contains no reward values,
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

    public sealed interface Effect permits RoutineEntered, CallReturned, Awarded, Deleted { }
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

    /** ObjMonitorItem (:419-425). Optional.empty is an explicit missing primary cut, never ID0/flags0. */
    public Result run(int wholeIndex, Optional<PrimarySupport> primarySupport) {
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
        c.publish();
        drawObject.drawObject(pool, c.state.identity()); // Native wrapper runs even after DeleteObject.
        c.state = pool.state(wholeIndex);
        c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        return new Result(c.state, c.effects);
    }

    /** ObjMonitorItem_Init (:438-459), including the byte-width ADD.B and full signed ADDA.W. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveWord(TILE, 0x85A8);
        if (c.state.unsignedByte(LAYER) != 0) c.state = c.state.moveWord(TILE, c.state.word(TILE) & 0x7FFF);
        c.state = c.state.moveByte(SPRITE_FLAGS, 0x24).moveByte(PRIORITY, 3).moveByte(WIDTH, 8).moveWord(Y_VELOCITY, -0x300);
        int animation = c.state.unsignedByte(ANIMATION);
        c.state = c.state.moveByte(MAP_FRAME, animation);
        c.publish(); // These stores precede the actual retained-table word read.
        int index = (animation + animation) & 0xFF;
        int relative = (short) assets.visual().mappings().unsignedWord(index);
        int pointer = assets.mappingsAddress() + relative + 1;
        // Do not read/validate a target before DrawObjects does so. Static mapping pointers may be odd.
        c.state = c.state.moveLong(MAPPINGS, pointer);
        c.publish();
    }

    /** ObjMonitorItem_Main (:465-482), followed only by the actually reached reward entry. */
    private void main(Cursor c, Optional<PrimarySupport> primarySupport) {
        c.enter(NativeRoutine.MAIN);
        if (c.state.signedWord(Y_VELOCITY) < 0) {
            c.publish();
            var moved = CdPlayerMovement.moveObject(CdMonitor.rawMotion(c.state), support(c, primarySupport));
            // ObjMove writes position longs only. Its local flipper resistance never stores X velocity.
            c.state = c.state.moveLong(X_POSITION, moved.centreX16_16()).moveLong(Y_POSITION, moved.centreY16_16())
                    .addWord(Y_VELOCITY, 0x18);
            c.publish(); c.effects.add(new CallReturned(NativeCall.OBJ_MOVE));
            return;
        }
        c.state = c.state.addByte(ROUTINE, 2).moveWord(MONITOR_ITEM_DELETE, 29);
        c.publish();
        int kind = c.state.unsignedByte(ANIMATION);
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
        var completed = Objects.requireNonNull(award.award(call), "Root must return a completed award result");
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
    private static void delete(Cursor c) {
        c.enter(NativeRoutine.DELETE);
        c.state = c.state.addWord(MONITOR_ITEM_DELETE, -1); c.publish();
        if (c.state.signedWord(MONITOR_ITEM_DELETE) < 0) {
            c.pool.deleteObject(c.wholeIndex); c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted());
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
        void publish() { pool.write(state); }
    }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
}
