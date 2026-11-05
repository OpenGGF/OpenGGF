package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjFlower ($1F): Level/_Objects/Flower/Main.asm:7-162 at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * executed over the live dynamic pool of one CdObjectPlacementRuntime. One {@link #run} is one RunObjects dispatch
 * of the slot: JSR of the routine selected by oRoutine through the five-entry .Index (:11-26), then JMP DrawObject
 * through the supplied port on every call. There is no despawn or delete path: a flower occupies its slot until the
 * level's object RAM is cleared.
 *
 * <p>Admitted path: the respawn-free badnik flower (oSubtype nonzero, as KillEnemy leaves it). Init (:32-57) falls
 * through .NoFlags into the Seed (:63-89), which probes the floor with ObjGetFloorDist and reports the resulting
 * primaryAngle byte for the caller's shared angle owner to apply once; this body never commits that global. The
 * oSubtype-zero saved-flag/flower-count/position-buffer calls (:44-52, :69-80) are typed boundaries: the native
 * writes before each call are committed, nothing after it is invented. Every unwritten byte keeps slot contents; no
 * global, camera, score or player state is read; nothing is keyed on a fixture, frame or route coordinate.</p>
 */
public final class CdFlower {
    public static final int OBJECT_ID = 0x1F;
    /** ObjFlower_Init: MOVE.W #$A6D7,oTile(a0) - priority, palette 1, tile $6D7 (Art_Flower at VRAM $DAE0). */
    public static final int INIT_TILE_WORD = 0xA6D7;
    /** ObjFlower_Init .NoFlags: MOVE.W #$6D7,oTile(a0) - palette 0, no priority, while the seed falls and plants. */
    public static final int SEED_TILE_WORD = 0x06D7;
    /** ObjFlower_Growing: MOVE.W #$26D7,oTile(a0) - palette 1 without priority. */
    public static final int GROWING_TILE_WORD = 0x26D7;

    /** INIT precedes SEED in the same dispatch because Init falls through .NoFlags into ObjFlower_Seed (:54-63). */
    public enum NativeCall { INIT, SEED, ANIMATE, GROWING, DONE, DRAW_OBJECT }
    /** ObjGetFloorDist zeroes and then FindLevelFloor may write the primaryAngle byte. */
    public enum GlobalField { PRIMARY_ANGLE }

    /**
     * Ordered return metadata. Pool effects describe completed writes and must not be replayed. PRIMARY_ANGLE supplies
     * the final scratch byte for the caller's separate shared angle owner; this body has not committed that global.
     */
    public sealed interface Effect permits CallReturned, GlobalWrite { }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record GlobalWrite(GlobalField field, int value) implements Effect {
        public GlobalWrite {
            Objects.requireNonNull(field, "field");
            if ((value & ~0xFF) != 0) { throw new IllegalArgumentException("global byte must be an unsigned native byte"); }
        }
    }

    /**
     * Pure declared flower data: the eight-frame MapSpr_Flower / four-script Ani_Flower visual and the native long
     * Init stores in oMap. Production obtains it from the authenticated owner through {@link #fromObjectAssets}; a
     * constructed record never authenticates media and supplies no fallback bytes.
     */
    public record Assets(CdObjectVisualAssets flower, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(flower, "flower");
            if (flower.mappingFrames().size() != 8 || flower.animations().size() != 4) {
                throw new IllegalArgumentException("MapSpr_Flower/Ani_Flower declare eight frames and four scripts");
            }
        }

        /** Actual original USA R12A data only; no component dispatch, prefix or World authority. */
        public static Assets fromAct2Assets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            var identity = Objects.requireNonNull(owner.identity(), "identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12A
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12A.stage()))
                throw new IllegalArgumentException("Flower Act2 assets require the authenticated original USA R12A source");
            return new Assets(owner.flower(), owner.profile().range(CdR12aProfile.Asset.MAP_SPR_FLOWER).nativeAddress());
        }

        /** Original Word RAM address of the authenticated mapping view; this is the stored native long. */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(assets.flower(),
                    assets.view(CdR11aObjectAssets.Table.FLOWER_MAPPINGS).range().nativeAddress());
        }
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196) at the ObjFlower cut (Flower/Main.asm:17): after the routine
     * returns. The shared CPU queue implements it over the live pool: clear bit 7 of the live slot first, then
     * cull/capacity, then append only the physical slot reference; it must not test oID. Any bit-7 write it makes is
     * reloaded into the Result. The caller must keep this synchronous port non-reentrant: it must not dispatch another body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    /** Final live image of the dispatched slot after the whole dispatch. */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
    }

    public enum UnsupportedBoundary {
        /** oRoutine outside {0,2,4,6,8} misaligns into or indexes past the five-entry .Index (Flower/Main.asm:21-26). */
        ROUTINE_DISPATCH,
        /** Init with oSubtype zero: BSR.W ObjFlower_GetSavedFlags reads savedObjFlags/timeZone (:44-52,107-121). */
        SAVED_FLAGS_INIT,
        /** Seed landing with oSubtype zero: flowerCount and flowerPosBuf record the position (:69-80,107-144). */
        SAVED_FLAGS_LANDING,
        /** Reached finite selected-source animation reads, after their real reset/timer/speed prefixes. */
        ANIMATION_DIRECTORY_READ, ANIMATION_FRAME_READ
    }

    /**
     * ROUTINE_DISPATCH is thrown before any write. The saved-flag boundaries are thrown at their unowned call after
     * committing exactly the native writes that precede it; nothing is rolled back and no later write is invented.
     */
    public static final class UnsupportedFlower extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedFlower(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final CdPlayerTerrain terrain;
    private final DrawObjectCall drawObject;

    /** Ephemeral source observations; equal records are never private World authority. */
    public enum StoreKind { INIT_BLOCKED, SEED_READY, SEED_MOVE, GROW_READY, DONE_READY, ANIMATION_RESULT, FAILURE_RESET, FAILURE_TIMER, FAILURE_SPEED }
    public record Publication(long ordinal, StoreKind kind, CdObjectPlacementRuntime.Snapshot cut,
                              CdObjectState source, CdObjectState output) { }
    public record FloorCall(long ordinal, CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) { }
    public record PrimaryAngleCall(long ordinal, CdObjectPlacementRuntime.Snapshot cut, CdObjectState source, int value) { }
    public record DrawCall(long ordinal, CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) { }
    public interface DispatchObserver {
        void beforePublication(Publication call);
        void afterPublication(Publication call);
        void beforeCall(Object call);
        void afterCall(Object call);
    }
    public interface ReceiptGuard { void prepared(Object call); void completed(Object call); }
    @FunctionalInterface public interface PrimaryAngleCommit { void commit(PrimaryAngleCall call); }
    @FunctionalInterface public interface SourceDraw { void draw(DrawCall call); }
    private DispatchObserver observer;
    private ReceiptGuard receiptGuard;
    private PrimaryAngleCommit angleCommit;
    private SourceDraw sourceDraw;
    private boolean dispatching;
    private long operationOrdinal;
    private Publication pendingPublication;
    private FloorCall pendingFloor;
    private PrimaryAngleCall pendingAngle;
    private DrawCall pendingDraw;
    private CdObjectCollisionCheck.FloorDistance floorResult;

    public boolean isCurrentCall(Object call) {
        return call != null && dispatching && (call == pendingPublication || call == pendingFloor || call == pendingAngle || call == pendingDraw);
    }
    /** Only the actual completed terrain return while its original call is still pending. */
    public CdObjectCollisionCheck.FloorDistance currentFloorResult(FloorCall call) {
        if (!isCurrentCall(call) || call != pendingFloor || floorResult == null)
            throw new IllegalStateException("No actual completed Flower floor result");
        return floorResult;
    }
    private void observedWrite(CdObjectState output, StoreKind kind) {
        if (observer == null) { runtime.pool().write(output); return; }
        var call = new Publication(++operationOrdinal, kind, runtime.capture(), runtime.pool().state(output.identity().wholeIndex()), output);
        if (pendingPublication != null || pendingFloor != null || pendingAngle != null || pendingDraw != null)
            throw new IllegalStateException("Nested Flower publication");
        pendingPublication = call;
        try {
            observer.beforePublication(call); receiptGuard.prepared(call);
            runtime.pool().write(output);
            observer.afterPublication(call); receiptGuard.completed(call);
        } finally { pendingPublication = null; }
    }
    private CdObjectState sourcePrefix(CdObjectState state, StoreKind kind) {
        if (observer != null) observedWrite(state, kind);
        return state;
    }
    private CdObjectCollisionCheck.FloorDistance observedFloor(CdObjectState source) {
        if (observer == null) return CdObjectCollisionCheck.objGetFloorDist(terrain, source);
        var call = new FloorCall(++operationOrdinal, runtime.capture(), source);
        if (pendingFloor != null || pendingPublication != null || pendingAngle != null || pendingDraw != null)
            throw new IllegalStateException("Nested Flower floor probe");
        pendingFloor = call;
        try {
            observer.beforeCall(call); receiptGuard.prepared(call);
            floorResult = CdObjectCollisionCheck.objGetFloorDist(terrain, source, value -> observedAngle(source, value));
            observer.afterCall(call); receiptGuard.completed(call);
            return floorResult;
        } finally { pendingFloor = null; floorResult = null; }
    }
    private void observedAngle(CdObjectState source, int value) {
        var call = new PrimaryAngleCall(++operationOrdinal, runtime.capture(), source, value);
        if (pendingFloor == null || pendingAngle != null || pendingPublication != null || pendingDraw != null)
            throw new IllegalStateException("Flower angle outside its actual floor probe");
        pendingAngle = call;
        try {
            observer.beforeCall(call); receiptGuard.prepared(call); angleCommit.commit(call);
            observer.afterCall(call); receiptGuard.completed(call);
        } finally { pendingAngle = null; }
    }
    private void observedDraw(CdObjectPool pool, CdObjectState.Identity identity) {
        if (observer == null) { drawObject.drawObject(pool, identity); return; }
        var call = new DrawCall(++operationOrdinal, runtime.capture(), pool.state(identity.wholeIndex()));
        if (pendingFloor != null || pendingPublication != null || pendingAngle != null || pendingDraw != null)
            throw new IllegalStateException("Nested Flower Draw");
        pendingDraw = call;
        try {
            observer.beforeCall(call); receiptGuard.prepared(call); sourceDraw.draw(call);
            observer.afterCall(call); receiptGuard.completed(call);
        } finally { pendingDraw = null; }
    }

    /** The runtime supplies the single live pool; the terrain is the production floor owner. */
    public CdFlower(CdObjectPlacementRuntime runtime, Assets assets, CdPlayerTerrain terrain, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    /** Same native kernel with actual paired R12A data, terrain and private source receipts. */
    public static CdFlower fromAct2Assets(CdObjectPlacementRuntime runtime, CdR12aObjectAssets owner,
                                          CdPlayerTerrain terrain, DispatchObserver observer,
                                          PrimaryAngleCommit angle, SourceDraw draw, ReceiptGuard guard) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(owner, "owner");
        if (!runtime.sameAct2Source(owner)) throw new IllegalArgumentException("Flower placement/assets source differs");
        var body = new CdFlower(runtime, Assets.fromAct2Assets(owner), terrain, (pool, identity) -> {
            throw new IllegalStateException("R12A Flower requires its actual source Draw");
        });
        body.observer = Objects.requireNonNull(observer); body.angleCommit = Objects.requireNonNull(angle);
        body.sourceDraw = Objects.requireNonNull(draw); body.receiptGuard = Objects.requireNonNull(guard); return body;
    }

    public Assets assets() { return assets; }

    /** One ObjFlower dispatch for the whole-pool dynamic slot currently holding ID $1F. */
    public Result run(int wholeIndex) {
        if (observer != null && dispatching) throw new IllegalStateException("Reentrant actual Flower dispatch");
        dispatching = true; operationOrdinal = 0;
        try { return runSelected(wholeIndex); }
        finally { dispatching = false; }
    }

    private Result runSelected(int wholeIndex) {
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjFlower $1F");
        }
        var effects = new ArrayList<Effect>();
        switch (state.routine()) {                                             // MOVE.W .Index(pc,d0.w),d0 / JSR .Index(pc,d0.w)
            case 0 -> {
                state = init(state, pool);
                effects.add(new CallReturned(NativeCall.INIT));
                state = seed(state, pool, effects);                            // .NoFlags falls into ObjFlower_Seed
                effects.add(new CallReturned(NativeCall.SEED));
            }
            case 2 -> {
                state = seed(state, pool, effects);
                effects.add(new CallReturned(NativeCall.SEED));
            }
            case 4 -> {
                state = animate(state);                                        // ObjFlower_Animate
                effects.add(new CallReturned(NativeCall.ANIMATE));
            }
            case 6 -> {
                state = growing(state);
                effects.add(new CallReturned(NativeCall.GROWING));
            }
            case 8 -> {
                state = done(state);
                effects.add(new CallReturned(NativeCall.DONE));
            }
            default -> throw new UnsupportedFlower(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + state.routine() + " is not an ObjFlower .Index entry");
        }
        observedWrite(state, StoreKind.ANIMATION_RESULT);
        observedDraw(pool, state.identity());                         // JMP DrawObject (Flower/Main.asm:17)
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        return new Result(pool.state(wholeIndex), effects);
    }

    /** ObjFlower_Init (Flower/Main.asm:32-57); no routine increment exists, .NoFlags writes routine 2 directly. */
    private CdObjectState init(CdObjectState state, CdObjectPool pool) {
        var next = state.moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)       // ORI.B #%100,oSprFlags(a0): flips and bit 7 stay
                .moveByte(PRIORITY, 1)                                         // MOVE.B #1,oPriority(a0)
                .moveByte(Y_RADIUS, 0)                                         // MOVE.B #0,oYRadius(a0)
                .moveWord(TILE, INIT_TILE_WORD);                               // MOVE.W #$A6D7,oTile(a0)
        if (next.unsignedByte(SUBTYPE2) != 0) {                                // TST.B oFlowerLoPrio(a0) / BEQ.S .GotPriority
            next = next.moveWord(TILE, next.word(TILE) & 0x7FFF);              // ANDI.B #$7F,oTile(a0): the high byte only
        }
        next = next.moveLong(MAPPINGS, assets.mappingsAddress());               // .GotPriority: MOVE.L #MapSpr_Flower,oMap(a0)
        if (next.subtype() == 0) {                                             // TST.B oSubtype(a0) / BNE.S .NoFlags
            observedWrite(next, StoreKind.INIT_BLOCKED);
            throw new UnsupportedFlower(UnsupportedBoundary.SAVED_FLAGS_INIT, state.identity().wholeIndex(),
                    "BSR.W ObjFlower_GetSavedFlags reads the unowned saved-flags bank and timeZone");
        }
        // .NoFlags overwrites the init tile: the low-priority ANDI above is lost for every respawn-free flower.
        return sourcePrefix(next.moveWord(ANIMATION_WORD, 0x0002)                           // MOVE.W #2,oAnim(a0): oAnim 0, oPrevAnim 2
                .moveByte(ROUTINE, 2)                                          // MOVE.B #2,oRoutine(a0)
                .moveWord(TILE, SEED_TILE_WORD), StoreKind.SEED_READY);         // MOVE.W #$6D7,oTile(a0)
    }

    /** ObjFlower_Seed (Flower/Main.asm:63-89). */
    private CdObjectState seed(CdObjectState state, CdObjectPool pool, List<Effect> effects) {
        var floor = observedFloor(state);    // JSR ObjGetFloorDist (oYRadius 0 after Init)
        effects.add(new GlobalWrite(GlobalField.PRIMARY_ANGLE, floor.primaryAngle()));
        int d1 = floor.distance();
        if (d1 >= 0) {                                                         // TST.W d1 / BPL.S .Fall
            return animate(sourcePrefix(state.addWord(Y, 2), StoreKind.SEED_MOVE));                               // .Fall: ADDQ.W #2,oY(a0), then ObjFlower_Animate
        }
        var landed = state.addWord(Y, d1);                                     // ADD.W d1,oY(a0): integer word only
        if (landed.subtype() == 0) {                                           // TST.B oSubtype(a0) / BNE.S .TouchDown
            observedWrite(landed, StoreKind.SEED_MOVE);
            throw new UnsupportedFlower(UnsupportedBoundary.SAVED_FLAGS_LANDING, state.identity().wholeIndex(),
                    "flowerCount/flowerPosBuf record the landing in unowned saved storage");
        }
        return animate(sourcePrefix(landed.moveByte(ROUTINE, 4)                             // .TouchDown: MOVE.B #4,oRoutine(a0)
                .moveByte(ANIMATION, 1)                                        // MOVE.B #1,oAnim(a0)
                .moveByte(Y_RADIUS, 0x30), StoreKind.SEED_MOVE));               // MOVE.B #$30,oYRadius(a0) / BRA.W ObjFlower_Animate
    }

    /** ObjFlower_Growing (Flower/Main.asm:150-153). */
    private CdObjectState growing(CdObjectState state) {
        return animate(sourcePrefix(state.moveWord(TILE, GROWING_TILE_WORD)                 // MOVE.W #$26D7,oTile(a0)
                .moveByte(ANIMATION, 2), StoreKind.GROW_READY));                // MOVE.B #2,oAnim(a0) / BRA.S ObjFlower_Animate
    }

    /** ObjFlower_Done (Flower/Main.asm:159-162): the grown animation loops in routine 4 forever. */
    private CdObjectState done(CdObjectState state) {
        return animate(sourcePrefix(state.moveByte(ANIMATION, 3)                            // MOVE.B #3,oAnim(a0)
                .moveByte(ROUTINE, 4), StoreKind.DONE_READY));                  // MOVE.B #4,oRoutine(a0) / BRA.S ObjFlower_Animate
    }

    /** ObjFlower_Animate (Flower/Main.asm:95-97): LEA Ani_Flower,a1 / BRA.W AnimateObject. */
    private CdObjectState animate(CdObjectState state) {
        var visual = assets.flower();
        var table = new CdObjectAnimation.AnimationTable(visual.animationTable(), 0, visual.animations().size());
        CdObjectAnimation.State out;
        try { out = CdObjectAnimation.step(new CdObjectAnimation.State(
                state.unsignedByte(ANIMATION), state.unsignedByte(PREVIOUS_ANIMATION),
                state.unsignedByte(ANIMATION_FRAME), state.unsignedByte(ANIMATION_TIME),
                state.unsignedByte(MAP_FRAME), state.spriteFlags(), state.flags(),
                state.routine(), state.unsignedByte(ROUTINE_2)), table);
        } catch (UnsupportedOperationException selectedRead) {
            if (observer == null) throw selectedRead;
            animationFailurePrefix(state, table, selectedRead);
            throw new IllegalStateException("Shared Flower failure differs from its finite selected read", selectedRead);
        }
        return state.moveByte(ANIMATION, out.animationId())
                .moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex())
                .moveByte(ANIMATION_TIME, out.timer())
                .moveByte(MAP_FRAME, out.mapFrame())
                .moveByte(SPRITE_FLAGS, out.spriteFlags())
                .moveByte(FLAGS, out.statusFlags())
                .moveByte(ROUTINE, out.routine())
                .moveByte(ROUTINE_2, out.routine2());
    }

    /** Only reached failure stores; successful animation still runs the existing shared kernel once. */
    private void animationFailurePrefix(CdObjectState state, CdObjectAnimation.AnimationTable table, RuntimeException cause) {
        if (state.unsignedByte(ANIMATION) != state.unsignedByte(PREVIOUS_ANIMATION)) {
            state = state.moveByte(PREVIOUS_ANIMATION, state.unsignedByte(ANIMATION)).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
            observedWrite(state, StoreKind.FAILURE_RESET);
        }
        state = state.addByte(ANIMATION_TIME, -1); observedWrite(state, StoreKind.FAILURE_TIMER);
        int animation = state.unsignedByte(ANIMATION);
        if (animation >= table.scriptCount()) throw animationMissing(state, UnsupportedBoundary.ANIMATION_DIRECTORY_READ, cause);
        int script = table.tableOffset() + (short) table.bytes().unsignedWord(table.tableOffset() + 2 * animation);
        state = state.moveByte(ANIMATION_TIME, table.bytes().unsignedByte(script)); observedWrite(state, StoreKind.FAILURE_SPEED);
        if (script + 1 + state.unsignedByte(ANIMATION_FRAME) >= table.bytes().length())
            throw animationMissing(state, UnsupportedBoundary.ANIMATION_FRAME_READ, cause);
    }
    private static UnsupportedFlower animationMissing(CdObjectState state, UnsupportedBoundary boundary, RuntimeException cause) {
        var failure = new UnsupportedFlower(boundary, state.identity().wholeIndex(), "reached selected Flower animation extent");
        failure.initCause(cause); return failure;
    }
}
