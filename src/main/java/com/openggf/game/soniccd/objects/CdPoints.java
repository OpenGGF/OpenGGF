package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdR13ReusedObjectAssets;

import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjHUDPoints ($1C) points branch: Level/_Objects/HUD and Points/Main.asm:11-53,67-69 at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, executed over the live dynamic pool of one CdObjectPlacementRuntime.
 * One {@link #run} is one RunObjects dispatch of the slot: TST.B oSubtype / BMI.W ObjPoints selects this body
 * (bit 7 clear is the separately owned ObjHUD), then the routine selected by oRoutine through JSR, then JMP DrawObject
 * through the supplied port, always, including on the slot ObjPoints_Main has just deleted.
 *
 * <p>The points object is unscripted: Init copies oSubtype & $7F into oMapFrame and never animates. oPriority, oWidth,
 * oYRadius and every other unwritten byte keep the allocated slot's contents. No global, camera, score or player
 * state is read or written; nothing is keyed on a fixture, frame or route coordinate.</p>
 */
public final class CdPoints {
    public static final int OBJECT_ID = 0x1C;
    /** ObjPoints_Init: MOVE.W #$6C6,oTile(a0) - palette 0, tile $6C6 (Art_Points at VRAM $D8C0). */
    public static final int NATIVE_TILE_WORD = 0x06C6;
    /** ObjPoints_Init: MOVE.B #24,oPntsTimer(a0); Main deletes on the call that decrements it to zero. */
    public static final int NATIVE_LIFETIME = 24;

    /** INIT precedes MAIN in the same dispatch because Init falls through into ObjPoints_Main (:40-46). */
    public enum NativeCall { INIT, MAIN, DRAW_OBJECT }

    /** Ordered return metadata. Pool effects describe completed writes and must not be replayed. */
    public sealed interface Effect permits CallReturned, Deleted { }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /** ObjPoints_Main: the timer reached zero and JMP DeleteObject zeroed the slot; ObjPoints still draws it. */
    public record Deleted() implements Effect { }

    /**
     * Pure declared points data: the six-frame unscripted MapSpr_Points visual and the native long Init stores in
     * oMap. Production obtains it from the authenticated owner through {@link #fromObjectAssets}; a constructed record
     * never authenticates media and supplies no fallback bytes.
     */
    public record Assets(CdObjectVisualAssets points, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(points, "points");
            if (points.mappingFrames().size() != 6 || !points.animations().isEmpty()) {
                throw new IllegalArgumentException("MapSpr_Points declares six frames and ObjPoints has no script");
            }
        }

        /** Actual original USA R12A data only; no component dispatch, prefix or World authority. */
        public static Assets fromAct2Assets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            var identity = Objects.requireNonNull(owner.identity(), "identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12A
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12A.stage()))
                throw new IllegalArgumentException("Points Act2 assets require the authenticated original USA R12A source");
            return new Assets(owner.points(), owner.profile().range(CdR12aProfile.Asset.MAP_SPR_POINTS).nativeAddress());
        }

        /** Actual retained R12B views; values convey no dispatch or art authority. */
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            var identity = Objects.requireNonNull(owner.identity(), "identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12B.stage()))
                throw new IllegalArgumentException("Points Act2 assets require the authenticated original USA R12B source");
            return new Assets(owner.points(), CdR12bObjectAssets.View.MAP_SPR_POINTS.range().nativeAddress());
        }

        /** Original Word RAM address of the authenticated mapping view; this is the stored native long. */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(assets.points(),
                    assets.view(CdR11aObjectAssets.Table.POINTS_MAPPINGS).range().nativeAddress());
        }
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196) at the ObjPoints cut (HUD and Points/Main.asm:21): after the
     * routine returns. The shared CPU queue implements it over the live pool: clear bit 7 of the live slot first, then
     * cull/capacity, then append only the physical slot reference. It must not test oID: after the Main delete the slot
     * is already zeroed and native still enqueues it. Any bit-7 write it makes is reloaded into the Result. The caller
     * must keep this synchronous port non-reentrant: it must not dispatch another body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    /** Final live image of the dispatched slot after the whole dispatch (fully zero after the delete). */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        /** TST.B oSubtype / BMI.W ObjPoints (HUD and Points/Main.asm:68-69) not taken: the ObjHUD counter body. */
        HUD_OWNER,
        /** oRoutine outside {0,2} misaligns into or indexes past the two-entry .Index (HUD and Points/Main.asm:25-27). */
        ROUTINE_DISPATCH
    }

    /** Thrown before any write; the pool holds the last committed source state. */
    public static final class UnsupportedPoints extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedPoints(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final DrawObjectCall drawObject;
    private boolean dispatching;

    /** Ephemeral source observations; records and equal copies confer no World authority. */
    public enum StoreKind { MAIN_RESULT, DELETE }
    public record Publication(long ordinal, StoreKind kind, CdObjectPlacementRuntime.Snapshot cut,
                              CdObjectState source, CdObjectState before, CdObjectState output) { }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) { }
    public interface DispatchObserver {
        void beforePublication(Publication call);
        void afterPublication(Publication call);
        void beforeCall(Object call);
        void afterCall(Object call);
    }
    /** Separate private-World receipt checks run before native work and after its observer return. */
    public interface ReceiptGuard { void prepared(Object call); void completed(Object call); }
    @FunctionalInterface public interface SourceDraw { void draw(DrawCall call); }
    private DispatchObserver observer;
    private ReceiptGuard receiptGuard;
    private SourceDraw sourceDraw;
    private Publication pendingPublication;
    private DrawCall pendingDraw;
    private long publicationOrdinal;
    private int activeWhole;

    public boolean isCurrentCall(Object call) {
        return call != null && dispatching && (call == pendingPublication || call == pendingDraw);
    }
    private void observedWrite(CdObjectState output, StoreKind kind) {
        if (observer == null) { runtime.pool().write(output); return; }
        var pool = runtime.pool();
        var call = new Publication(++publicationOrdinal, kind, runtime.capture(), pool.state(activeWhole),
                pool.state(output.identity().wholeIndex()), output);
        observePublication(call, () -> pool.write(output));
    }
    private void observedDelete(CdObjectState source) {
        if (observer == null) { runtime.pool().deleteObject(source.identity().wholeIndex()); return; }
        var call = new Publication(++publicationOrdinal, StoreKind.DELETE, runtime.capture(),
                runtime.pool().state(activeWhole), source, null);
        observePublication(call, () -> runtime.pool().deleteObject(source.identity().wholeIndex()));
    }
    private void observePublication(Publication call, Runnable nativeStore) {
        if (pendingPublication != null || pendingDraw != null) throw new IllegalStateException("Nested child-effect publication");
        pendingPublication = call;
        try {
            observer.beforePublication(call); receiptGuard.prepared(call);
            nativeStore.run();
            observer.afterPublication(call); receiptGuard.completed(call);
        } finally { pendingPublication = null; }
    }
    private void observedDraw(CdObjectPool pool, CdObjectState.Identity identity) {
        if (observer == null) { drawObject.drawObject(pool, identity); return; }
        var call = new DrawCall(runtime.capture(), pool.state(identity.wholeIndex()));
        if (pendingPublication != null || pendingDraw != null) throw new IllegalStateException("Nested child-effect Draw");
        pendingDraw = call;
        try {
            observer.beforeCall(call); receiptGuard.prepared(call); sourceDraw.draw(call);
            observer.afterCall(call); receiptGuard.completed(call);
        } finally { pendingDraw = null; }
    }

    /** The runtime supplies the single live pool; no admitted branch touches its saved bank. */
    public CdPoints(CdObjectPlacementRuntime runtime, Assets assets, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    /** Same kernel, actual paired R12A sources, and transient private producer receipts only. */
    public static CdPoints fromAct2Assets(CdObjectPlacementRuntime runtime, CdR12aObjectAssets owner,
                                              DispatchObserver observer, SourceDraw draw, ReceiptGuard guard) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(owner, "owner");
        if (!runtime.sameAct2Source(owner)) throw new IllegalArgumentException("Child-effect placement/assets source differs");
        var body = new CdPoints(runtime, Assets.fromAct2Assets(owner), (pool, identity) -> {
            throw new IllegalStateException("R12A child effect requires its actual source Draw");
        });
        body.observer = Objects.requireNonNull(observer); body.sourceDraw = Objects.requireNonNull(draw);
        body.receiptGuard = Objects.requireNonNull(guard); return body;
    }

    // BEGIN R13 SOURCE FACTORY 20261106
    public static CdPoints fromAct3FutureAssets(CdObjectPlacementRuntime runtime,CdR13FutureAssets assets,CdR13ReusedObjectAssets owner,
            DispatchObserver observer,SourceDraw draw,ReceiptGuard guard) {
        Objects.requireNonNull(runtime,"actual placement");Objects.requireNonNull(assets,"actual original future source");Objects.requireNonNull(owner,"actual reuse metadata");
        if(!runtime.ownsAct3FutureAssets(assets)||!owner.ownsAssets(assets,owner.startup()))throw new IllegalArgumentException("R13 reused body requires exact retained source/placement/metadata");
        var data=new Assets(owner.points(),CdR13ReusedObjectAssets.View.MAP_SPR_POINTS.range(assets.identity().file()).nativeAddress());
        var body=new CdPoints(runtime,data,(pool,id)->{throw new IllegalStateException("Actual R13 effect requires original source Draw");});
        body.observer=Objects.requireNonNull(observer);body.sourceDraw=Objects.requireNonNull(draw);body.receiptGuard=Objects.requireNonNull(guard);return body;
    }
    // END R13 SOURCE FACTORY 20261106

    /** Explicit same-placement R12B ingress; the original source algorithm and receipts are reused. */
    public static CdPoints fromAct2PastAssets(CdObjectPlacementRuntime runtime, CdR12bTerrainAssets owner,
                                              DispatchObserver observer, SourceDraw draw, ReceiptGuard guard) {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(owner, "owner");
        if (!runtime.sameAct2PastSource(owner)) throw new IllegalArgumentException("Child-effect placement/assets source differs");
        var body = new CdPoints(runtime, Assets.fromAct2PastAssets(owner.objects()), (pool, identity) -> {
            throw new IllegalStateException("R12B child effect requires its actual source Draw");
        });
        body.observer = Objects.requireNonNull(observer); body.sourceDraw = Objects.requireNonNull(draw);
        body.receiptGuard = Objects.requireNonNull(guard); return body;
    }

    public Assets assets() { return assets; }

    /** One ObjHUDPoints dispatch for the whole-pool dynamic slot currently holding ID $1C with oSubtype bit 7 set. */
    public Result run(int wholeIndex) {
        if (observer != null && dispatching) throw new IllegalStateException("Reentrant R12A Points dispatch");
        dispatching = true;
        if (observer != null) { activeWhole = wholeIndex; publicationOrdinal = 0; }
        try { return runSelected(wholeIndex); }
        finally { dispatching = false; }
    }

    private Result runSelected(int wholeIndex) {
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjHUDPoints $1C");
        }
        if ((state.subtype() & 0x80) == 0) {                                   // TST.B oSubtype(a0) / BMI.W ObjPoints
            throw new UnsupportedPoints(UnsupportedBoundary.HUD_OWNER, wholeIndex,
                    "oSubtype $" + Integer.toHexString(state.subtype()) + " selects ObjHUD");
        }
        var effects = new ArrayList<Effect>();
        switch (state.routine()) {                                             // MOVE.W .Index(pc,d0.w),d0 / JSR .Index(pc,d0.w)
            case 0 -> {
                state = init(state);
                effects.add(new CallReturned(NativeCall.INIT));
                main(state, pool, effects);                                    // falls into ObjPoints_Main
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            case 2 -> {
                main(state, pool, effects);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            default -> throw new UnsupportedPoints(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + state.routine() + " is not an ObjPoints .Index entry");
        }
        // JMP DrawObject (HUD and Points/Main.asm:21) after every routine, including a slot DeleteObject just zeroed.
        observedDraw(pool, state.identity());
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        return new Result(pool.state(wholeIndex), effects);
    }

    /** ObjPoints_Init (HUD and Points/Main.asm:33-40). */
    private CdObjectState init(CdObjectState state) {
        return state.addByte(ROUTINE, 2)                                       // ADDQ.B #2,oRoutine(a0)
                .moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)               // ORI.B #%100,oSprFlags(a0): flips and bit 7 stay
                .moveWord(TILE, NATIVE_TILE_WORD)                              // MOVE.W #$6C6,oTile(a0)
                .moveLong(MAPPINGS, assets.mappingsAddress())                  // MOVE.L #MapSpr_Points,oMap(a0)
                .moveByte(MAP_FRAME, state.subtype())                          // MOVE.B oSubtype(a0),oMapFrame(a0)
                .moveByte(MAP_FRAME, state.subtype() & 0x7F)                   // ANDI.B #$7F,oMapFrame(a0)
                .moveByte(POINTS_TIMER, NATIVE_LIFETIME);                      // MOVE.B #24,oPntsTimer(a0)
    }

    /** ObjPoints_Main (HUD and Points/Main.asm:46-53); the delete's RTS returns into ObjPoints before DrawObject. */
    private void main(CdObjectState state, CdObjectPool pool, List<Effect> effects) {
        var next = state.addByte(POINTS_TIMER, -1);                            // SUBQ.B #1,oPntsTimer(a0): $00 wraps to $FF
        if (next.unsignedByte(POINTS_TIMER) == 0) {                            // BNE.S .Rise
            observedDelete(state);                  // JMP DeleteObject
            effects.add(new Deleted());
            return;
        }
        observedWrite(next.addWord(Y, -2), StoreKind.MAIN_RESULT);                                       // .Rise: SUBQ.W #2,oY(a0): integer word only
    }
}
