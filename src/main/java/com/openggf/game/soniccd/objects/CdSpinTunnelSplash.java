package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;

/**
 * Finite $0E ObjSpinSplash body, scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/_Objects/Spin Tunnel/Main (Waterfall Splash).asm:7-51. No FixBugs alternative exists here.
 * Sonic/Main.asm:161-195 owns allocation and its ID/X/Y/flip stores; this body never repeats them.
 * Init's OR preserves existing flags, writes the default tile BEFORE the one reached timeZone read,
 * optionally replaces the tile for zero/Past, then stores priority. Every primitive has its own cut.
 * Routine0 falls into shared AnimateObject then actual DrawObject; FC still draws, and the NEXT
 * routine4 dispatch calls existing DeleteObject. No movement, allocation, sound or saved-bank store.
 *
 * <p>This component borrows one live placement/pool and a bounded media-supplied animation view.
 * It owns no World/Frame, runtime storage, decoder, art, renderer or producer outside these stores.
 * Public values/operations are observations, never commands or entitlement to replay/adopt effects.
 * The integrating owner's guard must prove source/data and complete external lifetime/state at every
 * original cut. Construction supplies no production authority or World return.</p>
 */
public final class CdSpinTunnelSplash {
    public static final int OBJECT_ID = 0x0E;
    public static final int MAPPING_ADDRESS = 0x23F05C, ANIMATION_ADDRESS = 0x23F054;
    public static final int MAPPING_BYTES = 24, ANIMATION_BYTES = 8, MAPPING_FRAMES = 3;

    /**
     * Format values only, never a media certificate. Root must borrow animation from the actual active
     * R11 asset owner and prove that exact relation in Guard, before any operation can run. This keeps
     * missing asset/mapper registrations declarative: no new loader, raw image access or fallback bytes.
     */
    public record Data(int mappingsAddress, CdObjectAnimation.AnimationTable animation) {
        public Data {
            Objects.requireNonNull(animation, "borrowed animation view");
            if (mappingsAddress != MAPPING_ADDRESS || animation.tableOffset() != 0
                    || animation.scriptCount() != 1 || animation.bytes().length() != ANIMATION_BYTES) {
                throw new IllegalArgumentException("Original R11 waterfall needs map $23F05C and exact one-script/8-byte view");
            }
        }
    }

    public enum Kind {
        DISPATCH, INIT_ROUTINE, INIT_FLAGS, INIT_MAPPING, INIT_TILE, TIME_ZONE, PAST_TILE,
        INIT_PRIORITY, ANIMATION, DRAW_OBJECT, DELETE_OBJECT
    }

    /**
     * Privately issued synchronous occurrence. before/cut hold the original physical source, output
     * is the exact proposed image for a store (null for reads/Draw/delete). animation is the actual
     * shared Prefix occurrence, not a reconstructed command. completed is set only after the action.
     * Neither the metadata nor a retained original occurrence can publish, retry, roll back or resume.
     */
    public static final class Operation {
        private final long ordinal;
        private final Kind kind;
        private final CdObjectPlacementRuntime.Snapshot cut;
        private final CdObjectState before, output;
        private final CdObjectAnimation.Operation animation;
        private boolean completed;
        private Operation(long ordinal, Kind kind, CdObjectPlacementRuntime.Snapshot cut,
                          CdObjectState before, CdObjectState output, CdObjectAnimation.Operation animation) {
            this.ordinal = ordinal; this.kind = kind; this.cut = cut;
            this.before = before; this.output = output; this.animation = animation;
        }
        public long ordinal() { return ordinal; }
        public Kind kind() { return kind; }
        public int wholeIndex() { return before.identity().wholeIndex(); }
        public CdObjectPlacementRuntime.Snapshot cut() { return cut; }
        public CdObjectState before() { return before; }
        public CdObjectState output() { return output; }
        public CdObjectAnimation.Operation animation() { return animation; }
        public boolean completed() { return completed; }
    }

    /**
     * Mandatory original invocation preflight and completion/unwind. Callbacks cannot change any
     * placement bytes. before proves original source, borrowed Data, service thread, Frame generation,
     * phase and complete external state; after validates/adopts only the actual completed prefix.
     * after runs even when before/action fails. Distinct faults are suppressed onto the first fault.
     * The owner must not classify its guard/source/lifetime exceptions as native finite-read outcomes.
     */
    public interface Guard {
        void before(Operation operation);
        void after(Operation operation);
    }
    @FunctionalInterface public interface TimeZoneRead { int readTimeZone(); }
    /** Actual existing DrawObject owner on the same slot; only that source's sprite bit7 may clear. */
    @FunctionalInterface public interface DrawObjectCall { void drawObject(Operation operation); }

    public static final class UnsupportedSpinTunnelSplash extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final int wholeIndex, routine;
        private UnsupportedSpinTunnelSplash(int wholeIndex, int routine) {
            super("Unsupported ObjSpinSplash routine $" + Integer.toHexString(routine) + " at whole " + wholeIndex);
            this.wholeIndex = wholeIndex; this.routine = routine;
        }
        public int wholeIndex() { return wholeIndex; }
        public int routine() { return routine; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final CdR11aAssets source;
    private final Data data;
    private final Guard guard;
    private final TimeZoneRead timeZone;
    private final DrawObjectCall draw;
    private final Thread thread = Thread.currentThread();
    private boolean running;
    private int wholeIndex;
    private long ordinal;
    private Operation current;
    private CdObjectPlacementRuntime.Snapshot entry;
    private List<CdObjectState> expected;

    /** Constructed format behavior only. No media, live World invocation or allocation is asserted. */
    public CdSpinTunnelSplash(CdObjectPlacementRuntime runtime, Data data, Guard guard,
                             TimeZoneRead timeZone, DrawObjectCall draw) {
        this(runtime, null, data, guard, timeZone, draw);
    }

    /**
     * Checks the existing actual-USA-reader/placement relation, also at every cut. Data authenticity and
     * all external partitions remain mandatory Root guard obligations; a public Data value cannot
     * certify that its bytes came from this reader. Until Root installs that relation/asset join the
     * World $0E fence must remain. This factory neither opens new sources nor decodes/exports assets.
     */
    public static CdSpinTunnelSplash fromOriginalPresentAssets(CdObjectPlacementRuntime runtime,
            CdR11aAssets source, Data borrowed, Guard guard, TimeZoneRead timeZone, DrawObjectCall draw) {
        CdOriginalPresentObjectSource.require(runtime, source);
        if (source.identity().variant() != CdReleaseVariant.USA || source.identity().file() != CdNativeFile.R11A
                || source.identity().domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM) {
            throw new IllegalArgumentException("Waterfall body operands require the actual original USA R11A source");
        }
        return new CdSpinTunnelSplash(runtime, source, borrowed, guard, timeZone, draw);
    }

    private CdSpinTunnelSplash(CdObjectPlacementRuntime runtime, CdR11aAssets source, Data data,
                              Guard guard, TimeZoneRead timeZone, DrawObjectCall draw) {
        this.runtime = Objects.requireNonNull(runtime, "existing placement"); this.source = source;
        this.data = Objects.requireNonNull(data, "borrowed format data");
        this.guard = Objects.requireNonNull(guard, "original invocation guard");
        this.timeZone = Objects.requireNonNull(timeZone, "actual timeZone read");
        this.draw = Objects.requireNonNull(draw, "actual DrawObject");
    }

    public boolean isCurrentCall(Object operation) {
        return Thread.currentThread() == thread && running && operation != null && current == operation;
    }
    public boolean isCurrentCallCompleted(Object operation) {
        return isCurrentCall(operation) && current.completed;
    }

    /** One actual physical body dispatch. No birth, placement scan or result/effect replay occurs. */
    public CdObjectState run(int wholeIndex) {
        if (Thread.currentThread() != thread || running) {
            throw new IllegalStateException("ObjSpinSplash requires its original non-reentrant thread/scope");
        }
        var initial = runtime.pool().state(wholeIndex);
        if (initial.id() != OBJECT_ID) throw new IllegalArgumentException("Physical slot is not ObjSpinSplash $0E");
        running = true; this.wholeIndex = wholeIndex; ordinal = 0;
        try {
            requireSource(); entry = runtime.capture(); expected = new ArrayList<>(entry.pool().slots());
            call(Kind.DISPATCH, null, () -> {
                int routine = state().routine();
                if (routine != 0 && routine != 2 && routine != 4) {
                    throw new UnsupportedSpinTunnelSplash(wholeIndex, routine);
                }
                return null;
            });
            if (initial.routine() == 4) {
                call(Kind.DELETE_OBJECT, null, () -> {
                    runtime.pool().deleteObject(wholeIndex);
                    // Existing pool primitive cannot call out; read back only its own cleared image.
                    expected.set(index(), runtime.pool().state(wholeIndex)); return null;
                });
                return state();
            }
            if (initial.routine() == 0) {
                store(Kind.INIT_ROUTINE, state().addByte(ROUTINE, 2));
                store(Kind.INIT_FLAGS, state().moveByte(SPRITE_FLAGS, state().spriteFlags() | 4));
                store(Kind.INIT_MAPPING, state().moveLong(MAPPINGS, data.mappingsAddress()));
                store(Kind.INIT_TILE, state().moveWord(TILE, 0x3E4));
                int period = call(Kind.TIME_ZONE, null, () -> {
                    int value = timeZone.readTimeZone();
                    if ((value & ~255) != 0) throw new IllegalArgumentException("timeZone port did not return an unsigned BYTE");
                    return value;
                });
                if (period == 0) store(Kind.PAST_TILE, state().moveWord(TILE, 0x39E));
                store(Kind.INIT_PRIORITY, state().moveByte(PRIORITY, 1));
            }
            animate();
            call(Kind.DRAW_OBJECT, null, () -> { drawChecked(current); return null; });
            return state();
        } finally {
            current = null; expected = null; entry = null; running = false;
        }
    }

    private int index() { return wholeIndex - CdObjectPool.FIRST_WHOLE_INDEX; }
    private CdObjectState state() { return runtime.pool().state(wholeIndex); }
    private void requireSource() { if (source != null) CdOriginalPresentObjectSource.require(runtime, source); }
    private void store(Kind kind, CdObjectState output) {
        call(kind, output, () -> {
            runtime.pool().write(output); expected.set(index(), output); return null;
        });
    }

    /** Delegate once; every reset/timer/selected-read/frame/command store is owned by shared Prefix. */
    private void animate() {
        var prefix = new CdObjectAnimation.Prefix(runtime.pool(), wholeIndex, new CdObjectAnimation.Guard() {
            @Override public void before(CdObjectAnimation.Operation operation) {
                if (current != null) throw new IllegalStateException("Nested ObjSpinSplash animation occurrence");
                var before = expected.get(index());
                var output = operation.kind() == CdObjectAnimation.Kind.STORE
                        ? before.moveByte(operation.field(), operation.value()) : null;
                current = new Operation(++ordinal, Kind.ANIMATION, runtime.capture(), before, output, operation);
                verify(); readOnly(() -> guard.before(current)); verify();
            }
            @Override public void after(CdObjectAnimation.Operation operation) {
                var call = current;
                if (call == null || call.animation != operation) {
                    throw new IllegalStateException("Foreign or retired ObjSpinSplash animation occurrence");
                }
                try {
                    call.completed = operation.completed();
                    if (call.completed && call.output != null) expected.set(index(), call.output);
                    // Prefix preserves the original failure; this independent unwind checks its whole
                    // enclosing placement even when native read/before/publication was refused.
                    finish(call, null);
                } finally { current = null; }
            }
        });
        prefix.step(data.animation()); // SelectedReadFailure stays issued by the actual shared call.
    }

    private <T> T call(Kind kind, CdObjectState output, Supplier<T> action) {
        if (current != null) throw new IllegalStateException("Nested ObjSpinSplash occurrence");
        var operation = new Operation(++ordinal, kind, runtime.capture(), expected.get(index()), output, null);
        current = operation;
        T result = null; Throwable failure = null;
        try {
            try {
                verify(); readOnly(() -> guard.before(operation)); verify();
                result = action.get(); operation.completed = true;
            } catch (RuntimeException | Error original) { failure = original; }
            finish(operation, failure);
            return result;
        } finally { current = null; }
    }

    /** Independent mandatory checks: a first failure cannot skip after or replace the original. */
    private void finish(Operation operation, Throwable failure) {
        try { verify(); } catch (RuntimeException | Error check) { failure = retain(failure, check); }
        try { readOnly(() -> guard.after(operation)); }
        catch (RuntimeException | Error check) { failure = retain(failure, check); }
        try { verify(); } catch (RuntimeException | Error check) { failure = retain(failure, check); }
        rethrow(failure);
    }
    private void readOnly(Runnable callback) {
        Throwable failure = null;
        try { callback.run(); } catch (RuntimeException | Error original) { failure = original; }
        try { verify(); } catch (RuntimeException | Error check) { failure = retain(failure, check); }
        rethrow(failure);
    }

    private void drawChecked(Operation operation) {
        var before = expected.get(index());
        var cleared = before.moveByte(SPRITE_FLAGS, before.spriteFlags() & 0x7F);
        expected.set(index(), cleared);
        Throwable failure = null;
        try { draw.drawObject(operation); }
        catch (RuntimeException | Error original) {
            failure = original;
            // Failed Draw may refuse or land only the source's real bit7 clear. This adjusts the
            // expectation only; it never writes, rolls back, retries or accepts arbitrary live bytes.
            if (state().equals(before)) expected.set(index(), before);
        }
        try { verify(); } catch (RuntimeException | Error check) { failure = retain(failure, check); }
        rethrow(failure);
    }

    private void verify() {
        requireSource();
        var actual = runtime.capture();
        if (!entry.status().equals(actual.status())
                || !Arrays.equals(entry.savedFlags().copy(), actual.savedFlags().copy())) {
            throw new IllegalStateException("ObjSpinSplash callback changed placement/saved state");
        }
        for (int n = 0; n < CdObjectPool.DYNAMIC_COUNT; n++) {
            if (!expected.get(n).equals(actual.pool().slots().get(n))) {
                throw new IllegalStateException("ObjSpinSplash callback changed forbidden bytes at whole "
                        + (n + CdObjectPool.FIRST_WHOLE_INDEX));
            }
        }
    }
    private static Throwable retain(Throwable original, Throwable check) {
        if (original == null) return check;
        if (original != check) original.addSuppressed(check);
        return original;
    }
    private static void rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure != null) throw (RuntimeException) failure;
    }
}
