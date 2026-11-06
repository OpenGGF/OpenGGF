package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectTileIds;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;

/**
 * ObjTunnelDoorSplash ($0B), scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/_Objects/Spin Tunnel/Main (Door).asm:135-177. Init (:149-163) falls into Main (:168-171);
 * animation FC advances routine to four and still reaches DrawObject. Only the following dispatch reaches
 * Destroy (:176-177) and clears all 64 source bytes. The inspected source has no FixBugs alternative.
 *
 * <p>This body owns no allocation, movement, despawn, saved-bank, player, art service or queue. All native
 * nonwrites survive, including fractions, radii, dirty animation state and saved ID. Init's flags STORE is
 * MOVE.B #4, not OR. One real shared animation call consumes the original slot image at Main. Publications
 * describe stores already performed; source Draw/FM ports invoke their existing owners synchronously.</p>
 */
public final class CdTunnelDoorSplash {
    public static final int OBJECT_ID = 0x0B, TILE_SELECTOR = 0x0D;
    public static final int MAPPING_ADDRESS = 0x21D652, MAPPING_BYTES = 100, ANIMATION_BYTES = 24, ART_TILES = 86;

    public enum NativeCall { INIT, MAIN, DRAW_OBJECT, DESTROY }
    public enum FmSound { FM_A1, FM_A2 }
    public sealed interface Effect permits CallReturned, PlayedFm, Deleted { }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    public record PlayedFm(FmSound sound) implements Effect {
        public PlayedFm { Objects.requireNonNull(sound); }
    }
    public record Deleted() implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    /** Complete bounded format values; construction authenticates neither media nor a World dispatch. */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets visual, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(objectTileIds); Objects.requireNonNull(visual);
            if ((objectTileIds.length() != 74 && objectTileIds.length() != 72) || visual.mappingFrames().size() != 7
                    || visual.mappingTableOffset() != 0 || visual.mappings().length() != MAPPING_BYTES
                    || visual.animations().size() != 3 || visual.animationTable().length() != ANIMATION_BYTES
                    || mappingsAddress != MAPPING_ADDRESS) {
                throw new IllegalArgumentException("Door Splash needs original R12A tile74 or R12B tile72, map100/animation24 views");
            }
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces()) {
                if (piece.tileOffset() > ART_TILES - piece.tileCount()) {
                    throw new IllegalArgumentException("Door Splash mapping exceeds the existing 86-tile source");
                }
            }
        }

        /** Actual original USA R12A source views; no cache availability or art residency is inferred. */
        public static Assets fromAct2Assets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12A object assets");
            var identity = Objects.requireNonNull(owner.identity(), "R12A source identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12A
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12A.stage())) {
                throw new IllegalArgumentException("Door Splash requires authenticated original USA R12A Word RAM");
            }
            return new Assets(owner.view(CdR12aProfile.Asset.OBJECT_TILE_IDS), owner.tunnelDoorSplash(),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_TUNNEL_DOOR_SPLASH).nativeAddress());
        }

        /** Actual original USA R12B operands select these complete views; no equal-R12A address inference. */
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12B object assets");
            var identity = Objects.requireNonNull(owner.identity(), "R12B source identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12B.stage())) {
                throw new IllegalArgumentException("Door Splash requires authenticated original USA R12B Word RAM");
            }
            return new Assets(owner.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS), owner.tunnelDoorSplash(),
                    CdR12bObjectAssets.View.MAP_SPR_TUNNEL_DOOR_SPLASH.range().nativeAddress());
        }

        /** SetObjectTileID (:7-17): only selector D's declared one-WORD body, never its next neighbour. */
        private int tileWord(CdObjectState state) {
            int target = (short) objectTileIds.unsignedWord(TILE_SELECTOR * 2);
            int end = CdObjectTileIds.endOfBody(objectTileIds, TILE_SELECTOR);
            int selected = target + state.subtype2() * 2;
            if (selected < target || selected > end - 2 || end - target != 2) {
                throw unsupported(UnsupportedBoundary.TILE_ID_SUBTYPE2, state, state.subtype2());
            }
            return objectTileIds.unsignedWord(selected);
        }
    }

    /** Actual DrawObject on this live physical pool; only source sprite bit 7 may change here. */
    @FunctionalInterface public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity identity);
    }
    /** Actual PlayFMSound; object/placement mutation and dispatch reentry are forbidden. */
    @FunctionalInterface public interface PlayFmCall { void playFm(FmSound sound); }

    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, TILE_ID_SUBTYPE2, ANIMATION_DIRECTORY_READ, ANIMATION_FRAME_READ
    }
    /** The live pool retains every store completed before this reached native finite-read boundary. */
    public static final class UnsupportedTunnelDoorSplash extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedTunnelDoorSplash(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Door Splash whole " + wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        /** Reached routine/subtype2 byte or finite animation view byte offset. */
        public int value() { return value; }
    }

    /** Ephemeral observations of actual producer operations; equal copies confer no invocation authority. */
    public enum StoreKind { INIT_HEADER, INIT_TILE, ANIMATION_RESULT, FAILURE_RESET, FAILURE_TIMER, FAILURE_SPEED, DELETE }
    public record Publication(long ordinal, StoreKind kind, CdObjectPlacementRuntime.Snapshot cut,
                              CdObjectState source, CdObjectState before, CdObjectState output) { }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) { }
    public record FmCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source, FmSound sound) { }
    public interface DispatchObserver {
        void beforePublication(Publication call);
        void afterPublication(Publication call);
        void beforeCall(Object call);
        void afterCall(Object call);
    }
    public interface ReceiptGuard { void prepared(Object call); void completed(Object call); }
    @FunctionalInterface public interface SourceDraw { void draw(DrawCall call); }
    @FunctionalInterface public interface SourceSound { void play(FmCall call); }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final CdObjectAnimation.AnimationTable animation;
    private final DrawObjectCall drawObject;
    private final PlayFmCall playFm;
    private DispatchObserver observer;
    private ReceiptGuard receiptGuard;
    private SourceDraw sourceDraw;
    private SourceSound sourceSound;
    private boolean dispatching;
    private int activeWhole;
    private long publicationOrdinal;
    private Publication pendingPublication;
    private Object pendingCall;
    private boolean operationCompleted;

    /** Constructed source behavior only; production uses the paired authenticated factory below. */
    public CdTunnelDoorSplash(CdObjectPlacementRuntime runtime, Assets assets,
                              DrawObjectCall drawObject, PlayFmCall playFm) {
        this.runtime = Objects.requireNonNull(runtime); this.assets = Objects.requireNonNull(assets);
        this.drawObject = Objects.requireNonNull(drawObject); this.playFm = Objects.requireNonNull(playFm);
        animation = new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, 3);
    }

    /** Same native body, real paired R12A source, and transient World publication/completion receipts. */
    public static CdTunnelDoorSplash fromAct2Assets(CdObjectPlacementRuntime runtime, CdR12aObjectAssets owner,
                                                   DispatchObserver observer, SourceDraw draw, SourceSound sound,
                                                   ReceiptGuard guard) {
        Objects.requireNonNull(runtime); Objects.requireNonNull(owner);
        if (!runtime.sameAct2Source(owner)) throw new IllegalArgumentException("Door Splash placement/assets source differs");
        var body = new CdTunnelDoorSplash(runtime, Assets.fromAct2Assets(owner), (pool, identity) -> {
            throw new IllegalStateException("R12A Door Splash requires its actual source Draw");
        }, soundId -> { throw new IllegalStateException("R12A Door Splash requires its actual source FM"); });
        body.observer = Objects.requireNonNull(observer); body.sourceDraw = Objects.requireNonNull(draw);
        body.sourceSound = Objects.requireNonNull(sound); body.receiptGuard = Objects.requireNonNull(guard);
        return body;
    }

    /** Same body and original pending-operation receipts over the paired actual original-USA R12B source. */
    public static CdTunnelDoorSplash fromAct2PastAssets(CdObjectPlacementRuntime runtime, CdR12bTerrainAssets terrain,
                                                       DispatchObserver observer, SourceDraw draw, SourceSound sound,
                                                       ReceiptGuard guard) {
        Objects.requireNonNull(runtime); Objects.requireNonNull(terrain);
        if (!runtime.sameAct2PastSource(terrain)) throw new IllegalArgumentException("Door Splash placement/assets source differs");
        var body = new CdTunnelDoorSplash(runtime, Assets.fromAct2PastAssets(terrain.objects()), (pool, identity) -> {
            throw new IllegalStateException("R12B Door Splash requires its actual source Draw");
        }, soundId -> { throw new IllegalStateException("R12B Door Splash requires its actual source FM"); });
        body.observer = Objects.requireNonNull(observer); body.sourceDraw = Objects.requireNonNull(draw);
        body.sourceSound = Objects.requireNonNull(sound); body.receiptGuard = Objects.requireNonNull(guard);
        return body;
    }

    public Assets assets() { return assets; }
    public boolean isCurrentCall(Object call) {
        return call != null && dispatching && (call == pendingPublication || call == pendingCall);
    }

    /** Original active identity only, after its actual store/helper/port returned; never a retained receipt. */
    public boolean isCurrentCallCompleted(Object call) {
        return isCurrentCall(call) && operationCompleted;
    }

    /** One physical RunObjects dispatch of the dynamic slot currently holding ID $0B. */
    public Result run(int wholeIndex) {
        if (dispatching) throw new IllegalStateException("Door Splash dispatch is not reentrant");
        dispatching = true; activeWhole = wholeIndex; publicationOrdinal = 0;
        try {
            var state = runtime.pool().state(wholeIndex);
            if (state.id() != OBJECT_ID) {
                throw new IllegalArgumentException("RunObjects slot " + wholeIndex + " is not Door Splash $0B");
            }
            var effects = new ArrayList<Effect>();
            switch (state.routine()) {
                case 0 -> {
                    state = state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 1)
                            .moveLong(MAPPINGS, assets.mappingsAddress()).moveByte(ANIMATION, state.subtype());
                    observedWrite(state, StoreKind.INIT_HEADER);
                    state = state.moveWord(TILE, assets.tileWord(state));
                    observedWrite(state, StoreKind.INIT_TILE);
                    var sound = state.subtype() < 2 ? FmSound.FM_A2 : FmSound.FM_A1;
                    observedSound(sound);
                    effects.add(new PlayedFm(sound)); effects.add(new CallReturned(NativeCall.INIT));
                }
                case 2 -> { }
                case 4 -> {
                    observedDelete(state); effects.add(new Deleted()); effects.add(new CallReturned(NativeCall.DESTROY));
                    return new Result(runtime.pool().state(wholeIndex), effects);
                }
                default -> throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, state, state.routine());
            }
            state = animate(state); observedWrite(state, StoreKind.ANIMATION_RESULT);
            effects.add(new CallReturned(NativeCall.MAIN));
            observedDraw(); effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
            return new Result(runtime.pool().state(wholeIndex), effects);
        } finally { dispatching = false; }
    }

    /** One actual shared call; successful transitions are never independently reconstructed. */
    private CdObjectState animate(CdObjectState state) {
        CdObjectAnimation.State out;
        try {
            out = CdObjectAnimation.step(new CdObjectAnimation.State(state.unsignedByte(ANIMATION),
                    state.unsignedByte(PREVIOUS_ANIMATION), state.unsignedByte(ANIMATION_FRAME),
                    state.unsignedByte(ANIMATION_TIME), state.unsignedByte(MAP_FRAME), state.spriteFlags(),
                    state.flags(), state.routine(), state.unsignedByte(ROUTINE_2)), animation);
        } catch (UnsupportedOperationException original) {
            var failure = animationFailurePrefix(state, original);
            throw failure;
        }
        return state.moveByte(ANIMATION, out.animationId()).moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex()).moveByte(ANIMATION_TIME, out.timer())
                .moveByte(MAP_FRAME, out.mapFrame()).moveByte(SPRITE_FLAGS, out.spriteFlags())
                .moveByte(FLAGS, out.statusFlags()).moveByte(ROUTINE, out.routine()).moveByte(ROUTINE_2, out.routine2());
    }

    /** Only reset/timer/speed stores preceding a failed directory/frame read; native scripts have frames and FC. */
    private RuntimeException animationFailurePrefix(CdObjectState state, UnsupportedOperationException original) {
        if (state.unsignedByte(ANIMATION) != state.unsignedByte(PREVIOUS_ANIMATION)) {
            state = state.moveByte(PREVIOUS_ANIMATION, state.unsignedByte(ANIMATION))
                    .moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
            observedWrite(state, StoreKind.FAILURE_RESET);
        }
        state = state.addByte(ANIMATION_TIME, -1); observedWrite(state, StoreKind.FAILURE_TIMER);
        int id = state.unsignedByte(ANIMATION);
        if (id >= animation.scriptCount()) {
            return animationMissing(state, UnsupportedBoundary.ANIMATION_DIRECTORY_READ, 2 * id, original);
        }
        int script = animation.tableOffset() + (short) animation.bytes().unsignedWord(animation.tableOffset() + 2 * id);
        if (script < 0 || script >= animation.bytes().length()) {
            return animationMissing(state, UnsupportedBoundary.ANIMATION_FRAME_READ, script, original);
        }
        state = state.moveByte(ANIMATION_TIME, animation.bytes().unsignedByte(script));
        observedWrite(state, StoreKind.FAILURE_SPEED);
        int at = script + 1 + state.unsignedByte(ANIMATION_FRAME);
        if (at < 0 || at >= animation.bytes().length()) {
            return animationMissing(state, UnsupportedBoundary.ANIMATION_FRAME_READ, at, original);
        }
        return new IllegalStateException("Shared Door Splash failure was not a reached declared script read", original);
    }

    private static UnsupportedTunnelDoorSplash unsupported(UnsupportedBoundary boundary, CdObjectState state, int value) {
        return new UnsupportedTunnelDoorSplash(boundary, state.identity().wholeIndex(), value);
    }
    private static UnsupportedTunnelDoorSplash animationMissing(CdObjectState state, UnsupportedBoundary boundary,
                                                               int at, RuntimeException original) {
        var failure = unsupported(boundary, state, at); failure.initCause(original); return failure;
    }

    private void observedWrite(CdObjectState output, StoreKind kind) {
        if (observer == null) { runtime.pool().write(output); return; }
        var call = new Publication(++publicationOrdinal, kind, runtime.capture(), runtime.pool().state(activeWhole),
                runtime.pool().state(output.identity().wholeIndex()), output);
        observePublication(call, () -> runtime.pool().write(output));
    }
    private void observedDelete(CdObjectState source) {
        if (observer == null) { runtime.pool().deleteObject(activeWhole); return; }
        var call = new Publication(++publicationOrdinal, StoreKind.DELETE, runtime.capture(), source, source, null);
        observePublication(call, () -> runtime.pool().deleteObject(activeWhole));
    }
    private void observePublication(Publication call, Runnable nativeStore) {
        requireNoPending(); pendingPublication = call; operationCompleted = false;
        try {
            readOnly(() -> observer.beforePublication(call)); readOnly(() -> receiptGuard.prepared(call));
            nativeStore.run(); operationCompleted = true;
            readOnly(() -> observer.afterPublication(call)); readOnly(() -> receiptGuard.completed(call));
        } finally { pendingPublication = null; operationCompleted = false; }
    }
    private void observedSound(FmSound sound) {
        if (observer == null) { readOnly(() -> playFm.playFm(sound)); return; }
        var call = new FmCall(runtime.capture(), runtime.pool().state(activeWhole), sound);
        observedCall(call, () -> readOnly(() -> sourceSound.play(call)));
    }
    private void observedDraw() {
        if (observer == null) {
            checkedDraw(() -> drawObject.drawObject(runtime.pool(), runtime.pool().state(activeWhole).identity()));
            return;
        }
        var call = new DrawCall(runtime.capture(), runtime.pool().state(activeWhole));
        observedCall(call, () -> checkedDraw(() -> sourceDraw.draw(call)));
    }
    private void observedCall(Object call, Runnable nativeCall) {
        requireNoPending(); pendingCall = call; operationCompleted = false;
        try {
            readOnly(() -> observer.beforeCall(call)); readOnly(() -> receiptGuard.prepared(call)); nativeCall.run();
            operationCompleted = true;
            readOnly(() -> observer.afterCall(call)); readOnly(() -> receiptGuard.completed(call));
        } finally { pendingCall = null; operationCompleted = false; }
    }
    private void requireNoPending() {
        if (pendingPublication != null || pendingCall != null) throw new IllegalStateException("Nested Door Splash producer operation");
    }

    /** Every observer/receipt/FM callback is read-only over all placement state, also when it throws. */
    private void readOnly(Runnable callback) {
        var before = runtime.capture();
        try { callback.run(); }
        catch (RuntimeException | Error original) {
            verifyFailure(original, () -> requirePlacement(before, false, false)); throw original;
        }
        requirePlacement(before, false, false);
    }
    private void checkedDraw(Runnable callback) {
        var before = runtime.capture();
        try { callback.run(); }
        catch (RuntimeException | Error original) {
            verifyFailure(original, () -> requirePlacement(before, true, false)); throw original;
        }
        requirePlacement(before, true, true);
    }
    private static void verifyFailure(Throwable original, Runnable verification) {
        try { verification.run(); }
        catch (RuntimeException | Error failure) { if (failure != original) original.addSuppressed(failure); }
    }
    private void requirePlacement(CdObjectPlacementRuntime.Snapshot before, boolean draw, boolean completed) {
        var after = runtime.capture();
        if (!before.status().equals(after.status()) || !Arrays.equals(before.savedFlags().copy(), after.savedFlags().copy())) {
            throw new IllegalStateException("Door Splash callback changed placement/saved state");
        }
        for (var old : before.pool().slots()) {
            var expected = old;
            var actual = runtime.pool().state(old.identity().wholeIndex());
            if (draw && old.identity().wholeIndex() == activeWhole) {
                expected = old.moveByte(SPRITE_FLAGS, old.spriteFlags() & 0x7F);
                if (!completed && actual.equals(old)) continue;
            }
            if (!expected.equals(actual)) {
                throw new IllegalStateException("Door Splash callback changed forbidden bytes at whole " + old.identity().wholeIndex());
            }
        }
    }
}
