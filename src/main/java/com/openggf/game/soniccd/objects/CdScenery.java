package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;

/**
 * ObjScenery ($32), Palmtree Panic/Objects/Scenery/Main.asm:7-67 at N
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Init falls through into Main's RTS;
 * its wrapper then calls DrawObject once and jumps to CheckObjDespawn.
 * There is no FixBugs conditional, player, collision, movement, clock or sound call in this closure.
 *
 * <p>Native Init's six stores precede the reached base WORD read. PRIORITY is untouched.
 * Future selection resets the working value to ONE before ADD.B goodFuture; LogShadow's
 * different directory/index formula is not used. Missing finite reads retain the live prefix.
 * Draw is synchronous and its complete live output is reloaded before the shared despawn tail.
 *
 * <p>Both constructors accept component values, not authenticated source or World authority.
 * The observed constructor supplies original-operation receipts for the real caller's validation.
 * Full placement cuts are borrowed only during the operation. Effects describe completed work;
 * they never replay it. No transient dispatch state is a captured gameplay partition.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdScenery {
    public static final int OBJECT_ID = 0x32;

    public enum NativeRoutine { INIT, MAIN }
    public enum NativeCall { DRAW_OBJECT }

    public sealed interface Effect permits RoutineEntered, CallReturned, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /** A completed native tail deletion, not an instruction or an inference from the final ID. */
    public record DespawnChecked(boolean deleted) implements Effect { }

    /** Native BYTEs and the pre-scroll camera X WORD; no period, flag or act normalization. */
    public record Globals(int timeZone, int goodFuture, int act, int timeWarpDirection, int cameraXWord) {
        public Globals {
            byteValue(timeZone, "timeZone"); byteValue(goodFuture, "goodFuture");
            byteValue(act, "act"); byteValue(timeWarpDirection, "timeWarpDirection");
            wordValue(cameraXWord, "cameraXWord");
        }
    }

    /**
     * Constructed finite format values only. The base view may be short or odd: validate only the
     * selected reached WORD after Init's stores. The mapping LONG retains all32 bits.
     * Three static frames use a maximum17-tile FORMAT hull (Past frames need17; Future frame2 needs4).
     * This is not a17-tile Future source, an art upload, a residency claim or frame admission.
     */
    public record Assets(CdAssetBytes baseTileWords, CdObjectVisualAssets visual, int mappingsAddressBits) {
        public Assets {
            Objects.requireNonNull(baseTileWords, "baseTileWords");
            Objects.requireNonNull(visual, "visual");
            if (visual.mappingFrames().size() != 3 || !visual.animations().isEmpty())
                throw new IllegalArgumentException("MapSpr_Scenery has three counted frames and no scripts");
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces()) {
                if (piece.tileOffset() > 17 - piece.tileCount())
                    throw new IllegalArgumentException("Scenery mapping exceeds its17-tile format hull");
            }
        }
    }

    /** Actual synchronous DrawObject over the supplied pool and physical identity. */
    @FunctionalInterface public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state, "state"); effects = List.copyOf(effects); }
        public boolean deleted() {
            return effects.stream().anyMatch(e -> e instanceof DespawnChecked checked && checked.deleted());
        }
    }

    public enum UnsupportedBoundary { ROUTINE_DISPATCH, BASE_TILE_ADDRESS, SAVED_FLAGS_ADDRESS }

    /** Only the reached unavailable operation stops; every earlier native store remains live. */
    public static final class UnsupportedScenery extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedScenery(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Scenery whole slot " + wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        /** Raw routine BYTE, selected directory BYTE offset or complete saved-bank BYTE index. */
        public int value() { return value; }
    }

    /** Public values are observations; only the original currently pending instance is a producer receipt. */
    public record Publication(long ordinal, CdObjectPlacementRuntime.Snapshot cut,
                              CdObjectState source, CdObjectState output, int savedIndex) { }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) { }
    public interface DispatchObserver {
        void beforePublication(Publication call);
        void afterPublication(Publication call);
        void beforeCall(Object call);
        void afterCall(Object call);
    }
    @FunctionalInterface public interface SourceDraw { void draw(DrawCall call); }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final DrawObjectCall drawObject;
    private final DispatchObserver observer;
    private final SourceDraw sourceDraw;
    private boolean running;
    private int runningWhole = -1;
    private long publicationOrdinal;
    private Publication pendingPublication;
    private DrawCall pendingDraw;

    public CdScenery(CdObjectPlacementRuntime placement, Assets assets, DrawObjectCall draw) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.assets = Objects.requireNonNull(assets, "assets");
        drawObject = Objects.requireNonNull(draw, "draw");
        observer = null; sourceDraw = null;
    }

    /** Observed COMPONENT ingress only; a future source factory must bind the actual matching source separately. */
    public CdScenery(CdObjectPlacementRuntime placement, Assets assets, DispatchObserver observer, SourceDraw draw) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.observer = Objects.requireNonNull(observer, "observer");
        sourceDraw = Objects.requireNonNull(draw, "sourceDraw");
        drawObject = null;
    }

    public boolean isCurrentCall(Object call) {
        if (!running || call == null) return false;
        if (call == pendingPublication)
            return pendingPublication.source().identity().wholeIndex() == runningWhole;
        if (call == pendingDraw)
            return pendingDraw.source().identity().wholeIndex() == runningWhole;
        return false;
    }

    /** One physical RunObjects dispatch. A caught reentry cannot clear or poison the outer scope. */
    public Result run(int wholeIndex, Globals globals) {
        if (running) throw new IllegalStateException("Reentrant ObjScenery dispatch");
        Objects.requireNonNull(globals, "globals");
        var pool = placement.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID)
            throw new IllegalArgumentException("RunObjects dispatch requires ID $32 at slot " + wholeIndex);
        int routine = state.routine();
        if (routine != 0 && routine != 2)
            throw new UnsupportedScenery(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, routine);
        running = true; runningWhole = wholeIndex; publicationOrdinal = 0;
        try {
            var effects = new ArrayList<Effect>();
            if (routine == 0) {
                effects.add(new RoutineEntered(NativeRoutine.INIT));
                // Scenery/Main:23-28; no PRIORITY store, and the base read follows all six stores.
                state = state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)
                        .moveLong(MAPPINGS, assets.mappingsAddressBits()).moveByte(MAP_FRAME, state.subtype())
                        .moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 0x18);
                observedWrite(state);
                state = state.moveWord(TILE, selectedBaseWord(wholeIndex, globals)); // :53 MOVE.W
                observedWrite(state);
                state = state.moveWord(TILE, state.word(TILE) | 0x4000); // :54 ORI.W
                observedWrite(state);
            }
            effects.add(new RoutineEntered(NativeRoutine.MAIN)); // Main:34-35 RTS.
            observedDraw(pool, state.identity()); // Wrapper:12 always calls once.
            state = pool.state(wholeIndex); // All callback writes, including ID, remain authoritative.
            effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
            boolean deleted = checkObjDespawn(state, globals);
            effects.add(new DespawnChecked(deleted));
            return new Result(pool.state(wholeIndex), effects);
        } finally {
            pendingPublication = null; pendingDraw = null; publicationOrdinal = 0;
            runningWhole = -1; running = false;
        }
    }

    /** Native :41-55. BYTE addition neither carries into nor clears the retained high WORD byte. */
    private int selectedBaseWord(int wholeIndex, Globals globals) {
        int index = globals.timeZone() & 0x7F;
        if (index == 2) index = (1 + globals.goodFuture()) & 0xFF; // MOVEQ #1, then ADD.B.
        index = (index + index) & 0xFFFF;
        index = (index & 0xFF00) | ((index + globals.act()) & 0xFF);
        int offset = (short) ((index + index) & 0xFFFF);
        // Six reviewed WORDs only. Extra constructed bytes do not authenticate adjacent native data.
        if (offset < 0 || (offset & 1) != 0 || offset > 10 || offset > assets.baseTileWords().length() - 2)
            throw new UnsupportedScenery(UnsupportedBoundary.BASE_TILE_ADDRESS, wholeIndex, offset);
        return assets.baseTileWords().unsignedWord(offset);
    }

    /** Object Spawner:385-399,442-446; raw warp-bit read follows X/sign/unsigned wrapped distance. */
    private boolean checkObjDespawn(CdObjectState state, Globals globals) {
        int x = state.centreX();
        if (state.signedByte(SPRITE_FLAGS) >= 0) {
            int distance = ((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF;
            if (distance > 0x280) return despawnObject(state, globals);
        }
        if ((globals.timeZone() & 0x80) != 0) return despawnObject(state, globals);
        return false;
    }

    /** Object Spawner:403-438. Saved ID0 bypasses period/direction/bank arithmetic entirely. */
    private boolean despawnObject(CdObjectState state, Globals globals) {
        int savedId = state.savedFlagsId();
        if (savedId != 0) {
            int offset = (savedId + savedId + savedId) & 0xFFFF;
            int rawPeriod = globals.timeZone();
            int period = rawPeriod & 0x7F;
            if ((rawPeriod & 0x80) != 0) {
                int adjusted = (period - (byte) globals.timeWarpDirection()) & 0xFFFF;
                period = (short) adjusted < 0 ? 0 : adjusted < 3 ? adjusted : 2;
            }
            int index = 2 + (short) ((offset + period) & 0xFFFF);
            if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES)
                throw new UnsupportedScenery(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, state.identity().wholeIndex(), index);
            int bodyOffset = index - 2;
            observedSaved(state, bodyOffset / 3, bodyOffset % 3, index);
        }
        observedDelete(state);
        return true;
    }

    private void observedWrite(CdObjectState output) {
        if (observer == null) { placement.pool().write(output); return; }
        var call = new Publication(++publicationOrdinal, placement.capture(),
                placement.pool().state(output.identity().wholeIndex()), output, -1);
        observePublication(call, () -> placement.pool().write(output));
    }
    private void observedSaved(CdObjectState source, int savedId, int period, int index) {
        if (observer == null) { placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED); return; }
        var call = new Publication(++publicationOrdinal, placement.capture(), source, source, index);
        observePublication(call, () -> placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED));
    }
    private void observedDelete(CdObjectState source) {
        if (observer == null) { placement.pool().deleteObject(source.identity().wholeIndex()); return; }
        var call = new Publication(++publicationOrdinal, placement.capture(), source, null, -1);
        observePublication(call, () -> placement.pool().deleteObject(source.identity().wholeIndex()));
    }
    private void observePublication(Publication call, Runnable nativeStore) {
        if (pendingPublication != null) throw new IllegalStateException("Nested Scenery publication");
        pendingPublication = call;
        try { observer.beforePublication(call); nativeStore.run(); observer.afterPublication(call); }
        finally { pendingPublication = null; }
    }
    private void observedDraw(CdObjectPool pool, CdObjectState.Identity identity) {
        if (observer == null) { drawObject.drawObject(pool, identity); return; }
        if (pendingDraw != null) throw new IllegalStateException("Nested Scenery DrawObject");
        var call = new DrawCall(placement.capture(), pool.state(identity.wholeIndex()));
        pendingDraw = call;
        try { observer.beforeCall(call); sourceDraw.draw(call); observer.afterCall(call); }
        finally { pendingDraw = null; }
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException(name + " must be an unsigned native BYTE");
    }
    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException(name + " must be an unsigned native WORD");
    }
}
