package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * Native ID36, PP Objects/Breakable Wall/Main.asm:7-159 at1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Init falls into Main; broken Main creates six fragments INCLUDING a0, then enters a0's Fall.
 * FindObjSlot failure branches to Fall after every already-published fragment. No local FixBugs
 * branch exists; shared Solid retains shipped FixBugs=0 behavior. Source bytes come from the actual
 * R12A object owner, never disassembly, art defaults or a separate pattern bank.
 *
 * <p>One invocation is one physical dispatch. Solid uses the existing pure plain-Solid prefix owner
 * and synchronous publication. FM_B0 precedes the fixed primary ASR.W X-velocity publication; its
 * typed value is not a generic player mutation. Root owns actual Frame/full-source/World/generation,
 * callback order and exactly-once authority. Component cuts/effects are public values and never
 * create that authority. Draw uses a live physical slot and clears only its native bit7.
 *
 * <p>Every reached object write remains live on failure. A failed external callback is rethrown
 * unchanged; unrelated pool/status/bank corruption is suppressed onto it, never repaired. No Frame,
 * service clock, mutable globals, second pool or persistent capture owner is created here.
 */
public final class CdBreakableWall {
    public static final int OBJECT_ID = 0x36, BREAK_FM = 0xB0;
    // Existing enums describe overlapping STORAGE only. Reuse their exact $2A/$2E locations;
    // this component adds no shared enum, owner, byte bank or unrelated object's behavior.
    private static final CdObjectState.LongField X_DELTA = TAMABBOH_MISSILE_X_DELTA;
    private static final CdObjectState.LongField Y_DELTA = TAMABBOH_MISSILE_Y_DELTA;
    private static final CdObjectState.WordField SAVED_X_VELOCITY = TITLE_CARD_DEST_X;
    private static final CdObjectState.WordField SAVED_Y_VELOCITY = TITLE_CARD_DEST_Y;

    public record Assets(CdAssetBytes pieceFrames, CdAssetBytes pieceDeltas, CdAssetBytes pieceSpeeds,
                         int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(pieceFrames); Objects.requireNonNull(pieceDeltas); Objects.requireNonNull(pieceSpeeds);
            if (pieceFrames.length() != 64 || pieceDeltas.length() != 24 || pieceSpeeds.length() != 48)
                throw new IllegalArgumentException("Wall requires complete64/24/48-byte native tables");
        }
        /** Original-USA R12B complete native tables and its actual used mapping root; no art or World admission. */
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12B object assets");var identity=Objects.requireNonNull(owner.identity(), "R12B identity");
            if (identity.variant()!=CdReleaseVariant.USA || identity.file()!=CdNativeFile.R12B
                    || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM || !identity.stage().equals(CdStagePayload.R12B.stage()))
                throw new IllegalArgumentException("Wall requires actual original USA R12B metadata");
            var visual=owner.breakableWall();
            if (visual.mappingFrames().size()!=14 || !visual.animations().isEmpty())
                throw new IllegalArgumentException("Wall requires fourteen counted frames and no scripts");
            for (var frame:visual.mappingFrames())for (var piece:frame.pieces())
                if (piece.tileOffset()>57-piece.tileCount())throw new IllegalArgumentException("Wall mapping exceeds its57-tile Platform source");
            return new Assets(owner.view(CdR12bObjectAssets.View.BREAKABLE_WALL_PIECE_FRAMES),
                    owner.view(CdR12bObjectAssets.View.BREAKABLE_WALL_PIECE_DELTAS),owner.view(CdR12bObjectAssets.View.BREAKABLE_WALL_PIECE_SPEEDS),
                    CdR12bObjectAssets.View.MAP_SPR_BREAKABLE_WALL.range().nativeAddress());
        }
        public static Assets fromObjectAssets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12A object source");
            owner.breakableWall(); // Existing counted mapping parser,14 roots/57 shared Platform tiles.
            return new Assets(owner.view(CdR12aProfile.Asset.BREAKABLE_WALL_PIECE_FRAMES),
                    owner.view(CdR12aProfile.Asset.BREAKABLE_WALL_PIECE_DELTAS),
                    owner.view(CdR12aProfile.Asset.BREAKABLE_WALL_PIECE_SPEEDS),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_BREAKABLE_WALL).nativeAddress());
        }
    }
    /** Raw native values, never defaults or a route/camera entitlement. */
    public record Globals(int timeZone, int timeWarpDirection, int cameraXWord) {
        public Globals {
            byteValue(timeZone); byteValue(timeWarpDirection);
            if ((cameraXWord & ~0xFFFF) != 0)
                throw new IllegalArgumentException("Wall camera requires a native WORD");
        }
    }
    public record Inputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public Inputs { Objects.requireNonNull(player); Objects.requireNonNull(gates); Objects.requireNonNull(globals); }
    }
    /** Fresh selected-full64 and full96/status/bank observation, not an invocation certificate. */
    public record SourceCut(CdObjectState source, CdObjectPlacementRuntime.Snapshot placement) {
        public SourceCut {
            Objects.requireNonNull(source); Objects.requireNonNull(placement);
            if (!source.equals(placement.pool().slots().get(source.identity().dynamicIndex())))
                throw new IllegalArgumentException("Wall source differs from its actual pool cut");
        }
    }
    public record SolidCall(SourceCut cut, CdSolidObject.Player expectedPlayer,
                            CdSolidObject.Globals expectedGlobals, CdSolidObject.Outcome outcome) {
        public SolidCall {
            Objects.requireNonNull(cut); Objects.requireNonNull(expectedPlayer); Objects.requireNonNull(expectedGlobals); Objects.requireNonNull(outcome);
            var entry = outcome instanceof CdSolidObject.Completed done ? done.completedResult().entry()
                    : ((CdSolidObject.NativeStopped) outcome).prefix().entry();
            if (entry != CdSolidObject.Entry.SOLID_OBJECT) throw new IllegalArgumentException("Wall calls plain SolidObject");
        }
    }
    public record HalveXVelocityCall(SourceCut cut, CdSolidObject.Player expectedPlayer, int finalXVelocity) {
        public HalveXVelocityCall {
            Objects.requireNonNull(cut); Objects.requireNonNull(expectedPlayer);
            if (finalXVelocity != (expectedPlayer.actor().motion().xVelocity() >> 1))
                throw new IllegalArgumentException("Wall publication is the reached signed ASR.W by1 only");
        }
    }
    public record FmCall(SourceCut cut, int nativeId) {
        public FmCall { Objects.requireNonNull(cut); if (nativeId != BREAK_FM) throw new IllegalArgumentException("Wall emits only FM_B0"); }
    }
    /** Adopt the actual shared result/prefix across pool/Frame/FM once; no helper/effect replay. */
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }
    /** Root validates complete Frame, prepares the fixed X-only publication, then adopts it once. */
    @FunctionalInterface public interface HalveXVelocity { void halve(HalveXVelocityCall call); }
    @FunctionalInterface public interface DrawObject { void draw(CdObjectPool pool, CdObjectState.Identity identity); }
    @FunctionalInterface public interface PlayFm { void play(FmCall call); }

    public sealed interface Effect permits RoutineEntered, SolidReturned, PlayedFm, VelocityHalved,
            SlotSearchReturned, PieceInitialized, DrawReturned, DespawnChecked, Deleted { }
    public record RoutineEntered(int routine) implements Effect { }
    public record SolidReturned(CdSolidObject.Outcome outcome) implements Effect { }
    public record PlayedFm() implements Effect { }
    public record VelocityHalved(int finalXVelocity) implements Effect { }
    public record SlotSearchReturned(OptionalInt wholeIndex) implements Effect { }
    public record PieceInitialized(int row, int wholeIndex) implements Effect { }
    public record DrawReturned() implements Effect { }
    public record DespawnChecked(boolean deleted) implements Effect { }
    public record Deleted() implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return state.id() == 0; }
    }
    public enum Boundary { ROUTINE, SOLID_OWNER, FM_OWNER, HALVE_OWNER, DRAW_OWNER, PIECE_FRAME_READ, SAVED_FLAGS_ADDRESS }
    public static final class UnsupportedWall extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final Boundary boundary;
        private UnsupportedWall(Boundary boundary) { super("Wall reached " + boundary); this.boundary = boundary; }
        public Boundary boundary() { return boundary; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final CdSolidObject solid;
    private final SolidCommit solidCommit;
    private final HalveXVelocity halve;
    private final DrawObject draw;
    private final PlayFm fm;
    private boolean running;
    private DispatchCursor dispatch;
    private DispatchObserver observer;

    /** Synchronous observation of this cached producer, not an effect-replay or capture owner. */
    public interface DispatchObserver {
        void published(Object actualOperation);
        void completed(Result result);
        void failed(Throwable original);
    }
    private final class DispatchCursor {
        final int whole;
        final CdObjectPlacementRuntime.Snapshot origin = placement.capture();
        List<CdObjectState> images = origin.pool().slots();
        CdAssetBytes saved = origin.savedFlags();
        Object pending;
        boolean acknowledged;
        DispatchCursor(int whole) { this.whole = whole; }
    }
    /** Exact pending producer identity; equal public records do not qualify. */
    public boolean matchesPendingOperation(Object operation) {
        return operation != null && running && dispatch != null && dispatch.pending == operation;
    }
    public void validateCurrentCall(Object call) {
        if (!running || dispatch == null || dispatch.pending != Objects.requireNonNull(call))
            throw new IllegalStateException("Wall call is not its actual pending cached producer operation");
        validateCurrentDispatch();
    }
    public void validateCurrentDraw(CdObjectPool pool, CdObjectState.Identity identity) {
        if (pool != placement.pool() || dispatch == null || identity.wholeIndex() != dispatch.whole)
            throw new IllegalStateException("Wall Draw has another physical/source pool");
        validateCurrentCall(identity);
    }
    /** Verify the producer's own reached stores; no comparison data is used as gameplay input. */
    public void validateCurrentDispatch() {
        if (!running || dispatch == null) throw new IllegalStateException("No actual Wall producer dispatch");
        placement.validateRestore(dispatch.origin); var now = placement.capture();
        if (!now.status().equals(dispatch.origin.status()) || !now.pool().slots().equals(dispatch.images)
                || !Arrays.equals(now.savedFlags().copy(), dispatch.saved.copy()))
            throw new IllegalStateException("Wall producer full96/status/saved prefix differs from its reached native stores");
    }
    /** Called only after the actual World owner adopted/read back this exact operation. */
    public void acknowledgeCurrentCall(Object call) {
        if (!running || dispatch == null || dispatch.pending != Objects.requireNonNull(call) || dispatch.acknowledged)
            throw new IllegalStateException("Wall acknowledgement lacks its unique actual pending call");
        dispatch.acknowledged = true;
    }
    private void pending(Object call, Runnable action) {
        if (dispatch.pending != null) throw new IllegalStateException("Nested Wall owner publication");
        dispatch.pending = call; dispatch.acknowledged = false;
        try {
            action.run();
            if (observer != null && (call instanceof SolidCall || call instanceof FmCall
                    || call instanceof HalveXVelocityCall || call instanceof CdObjectState.Identity) && !dispatch.acknowledged)
                throw new IllegalStateException("Wall owner omitted its actual pending publication");
            if (observer != null && (call instanceof SolidCall || call instanceof FmCall
                    || call instanceof HalveXVelocityCall || call instanceof CdObjectState.Identity)) observer.published(call);
        } finally { dispatch.pending = null; dispatch.acknowledged = false; }
    }
    private void publish(CdObjectState state) {
        validateCurrentDispatch();
        var next = new ArrayList<>(dispatch.images); next.set(state.identity().dynamicIndex(), state);
        var ready = java.util.Collections.unmodifiableList(next);
        placement.pool().write(state); dispatch.images = ready;
    }
    private void deleteNative(int whole) {
        validateCurrentDispatch();
        var next = new ArrayList<>(dispatch.images); var ready = java.util.Collections.unmodifiableList(next);
        placement.pool().deleteObject(whole);
        next.set(whole - CdObjectPool.FIRST_WHOLE_INDEX, placement.pool().state(whole)); dispatch.images = ready;
    }
    private void allowCallbackPrefix(CdObjectPool.Snapshot before, CdObjectPool.Snapshot after) {
        var actual = placement.pool().capture().slots();
        if (actual.equals(after.slots())) dispatch.images = after.slots();
        else if (actual.equals(before.slots())) dispatch.images = before.slots();
        else throw new IllegalStateException("Wall callback changed an unowned full96 prefix");
    }


    /** Constructed component ingress only; it cannot select an authenticated placement by equal bytes. */
    public CdBreakableWall(CdObjectPlacementRuntime placement, Assets assets, CdSolidObject solid,
                          SolidCommit solidCommit, HalveXVelocity halve, DrawObject draw, PlayFm fm) {
        this(placement, assets, solid, solidCommit, halve, draw, fm, false);
    }
    public static CdBreakableWall fromAssets(CdObjectPlacementRuntime placement, CdR12aObjectAssets owner,
                                             CdSolidObject solid, SolidCommit solidCommit,
                                             HalveXVelocity halve, DrawObject draw, PlayFm fm) {
        Objects.requireNonNull(placement); Objects.requireNonNull(owner);
        if (!placement.sameAct2Source(owner))
            throw new IllegalArgumentException("Wall requires the same retained original R12A placement/source");
        return new CdBreakableWall(placement, Assets.fromObjectAssets(owner), solid, solidCommit, halve, draw, fm, true);
    }
    /** Actual World construction retains the same authenticated assets and transient producer cursor. */
    public static CdBreakableWall fromAssets(CdObjectPlacementRuntime placement, CdR12aObjectAssets owner,
                                             CdSolidObject solid, SolidCommit solidCommit, HalveXVelocity halve,
                                             DrawObject draw, PlayFm fm, DispatchObserver observer) {
        var body = fromAssets(placement, owner, solid, solidCommit, halve, draw, fm);
        body.observer = Objects.requireNonNull(observer); return body;
    }
    /** Same actual R12B relation with the existing completed/stopped Solid and private pending-operation protocol. */
    public static CdBreakableWall fromAct2PastAssets(CdObjectPlacementRuntime placement, CdR12bTerrainAssets owner,
                                                    CdSolidObject solid, SolidCommit solidCommit, HalveXVelocity halve,
                                                    DrawObject draw, PlayFm fm, DispatchObserver observer) {
        Objects.requireNonNull(placement, "actual R12B placement");Objects.requireNonNull(owner, "actual R12B terrain");
        if (!placement.sameAct2PastSource(owner))
            throw new IllegalArgumentException("Wall requires the same retained R12B placement/source");
        var body=new CdBreakableWall(placement,Assets.fromAct2PastAssets(owner.objects()),solid,solidCommit,halve,draw,fm,true);
        body.observer=Objects.requireNonNull(observer);return body;
    }
    private CdBreakableWall(CdObjectPlacementRuntime placement, Assets assets, CdSolidObject solid,
                            SolidCommit solidCommit, HalveXVelocity halve, DrawObject draw, PlayFm fm, boolean authenticated) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.solid = Objects.requireNonNull(solid); this.solidCommit = solidCommit; this.halve = halve; this.draw = draw; this.fm = fm;
        if (!authenticated && placement.hasAuthenticatedSource())
            throw new IllegalArgumentException("Authenticated Wall construction requires its R12A factory");
    }

    public Result run(int wholeIndex, Globals globals, Inputs inputs) {
        if (running) throw new IllegalStateException("Reentrant native Wall dispatch");
        var pool = placement.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Wall requires actual dynamic ID36");
        int routine = state.routine();
        var preparedDispatch = new DispatchCursor(wholeIndex); running = true; dispatch = preparedDispatch;
        try {
            if ((routine & 1) != 0 || routine > 4) throw new UnsupportedWall(Boundary.ROUTINE);
            var effects = new ArrayList<Effect>(); effects.add(new RoutineEntered(routine));
            if (routine == 0) {
                state = state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)
                        .moveByte(PRIORITY, 1).moveByte(X_RADIUS, 0x10).moveByte(WIDTH, 0x10)
                        .moveByte(Y_RADIUS, 0x18).moveByte(COLLISION_TYPE, 0xEF).moveWord(TILE, 0x44BE)
                        .moveLong(MAPPINGS, assets.mappingsAddress()).moveByte(MAP_FRAME, state.subtype());
                publish(state); effects.add(new RoutineEntered(2));
            }
            if (routine == 4) fall(wholeIndex, Objects.requireNonNull(inputs).player(), effects);
            else if (state.unsignedByte(COLLISION_STATUS) == 0) {
                publishSolid(state, Objects.requireNonNull(inputs), effects);
                draw(wholeIndex, effects);
                effects.add(new DespawnChecked(despawn(pool.state(wholeIndex), Objects.requireNonNull(globals))));
            } else {
                // Main39 is ONE CLR.W over type/status. Only these two bytes clear.
                state = state.moveByte(COLLISION_TYPE, 0).moveByte(COLLISION_STATUS, 0).addByte(ROUTINE, 2);
                publish(state);
                var player = Objects.requireNonNull(inputs).player(); var motion = player.actor().motion();
                state = state.moveWord(SAVED_X_VELOCITY, motion.xVelocity()); publish(state);
                state = state.moveWord(SAVED_Y_VELOCITY, motion.yVelocity()); publish(state);
                if (fm == null) throw new UnsupportedWall(Boundary.FM_OWNER);
                var soundCut = cut(wholeIndex); var soundCall = new FmCall(soundCut, BREAK_FM);
                pending(soundCall, () -> readOnly(soundCut, () -> fm.play(soundCall)));
                effects.add(new PlayedFm());
                if (halve == null) throw new UnsupportedWall(Boundary.HALVE_OWNER);
                int finalX = motion.xVelocity() >> 1;
                var halveCut = cut(wholeIndex);
                var halveCall = new HalveXVelocityCall(halveCut, player, finalX);
                pending(halveCall, () -> readOnly(halveCut, () -> halve.halve(halveCall)));
                effects.add(new VelocityHalved(finalX));
                breakUp(wholeIndex, finalX, effects);
                fall(wholeIndex, player, effects);
            }
            validateCurrentDispatch();
            var result = new Result(pool.state(wholeIndex), effects);
            if (observer != null) pending(result, () -> observer.completed(result));
            validateCurrentDispatch(); return result;
        } catch (RuntimeException | Error original) {
            if (observer != null) suppress(original, () -> pending(original, () -> observer.failed(original)));
            throw original;
        } finally { dispatch = null; running = false; }
    }

    private void breakUp(int whole, int finalPlayerX, List<Effect> effects) {
        var pool = placement.pool(); int selected = whole;
        // a0 itself is row0. Later child centres copy the ALREADY-OFFSET live a0.
        for (int row = 0; row < 6; row++) {
            if (row != 0) {
                var found = pool.findObjSlot(); effects.add(new SlotSearchReturned(found));
                if (found.isEmpty()) return; // Native BNE enters a0 Fall; all earlier writes remain live.
                selected = found.getAsInt(); var parent = pool.state(whole);
                var child = pool.state(selected).moveByte(ID, parent.id()).moveByte(ROUTINE, parent.routine())
                        .moveWord(X, parent.centreX()).moveWord(Y, parent.centreY())
                        .moveByte(SPRITE_FLAGS, parent.spriteFlags()).moveByte(PRIORITY, parent.unsignedByte(PRIORITY))
                        .moveLong(MAPPINGS, parent.longBits(MAPPINGS)).moveWord(TILE, parent.word(TILE));
                publish(child);
            }
            var piece = pool.state(selected).moveByte(X_RADIUS, 8).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8);
            publish(piece);
            int frameAt = pool.state(whole).subtype() * 8 + row;
            if (frameAt >= assets.pieceFrames().length()) throw new UnsupportedWall(Boundary.PIECE_FRAME_READ);
            piece = piece.moveByte(MAP_FRAME, assets.pieceFrames().unsignedByte(frameAt)); publish(piece);
            int dx = (short) assets.pieceDeltas().unsignedWord(row * 4);
            int dy = (short) assets.pieceDeltas().unsignedWord(row * 4 + 2);
            piece = piece.addWord(X, dx); publish(piece);
            piece = piece.addWord(Y, dy); publish(piece);
            int xv = (int) assets.pieceSpeeds().unsignedLong(row * 8);
            piece = piece.moveLong(Y_DELTA, (int) assets.pieceSpeeds().unsignedLong(row * 8 + 4)); publish(piece);
            piece = piece.moveLong(X_DELTA, finalPlayerX < 0 ? -xv : xv); publish(piece);
            effects.add(new PieceInitialized(row, selected));
        }
    }
    private void fall(int whole, CdSolidObject.Player player, List<Effect> effects) {
        var pool = placement.pool(); var state = pool.state(whole).addLong(Y_DELTA, 0x4000); publish(state);
        state = state.addLong(X_POSITION, state.longBits(X_DELTA)); publish(state);
        state = state.addLong(Y_POSITION, state.longBits(Y_DELTA)); publish(state);
        int distance = (short) ((player.actor().motion().centreY16_16() >>> 16) - state.centreY());
        if (distance <= -0xE0) { deleteNative(whole); effects.add(new Deleted()); }
        else draw(whole, effects);
    }

    private void publishSolid(CdObjectState state, Inputs inputs, List<Effect> effects) {
        var cut = cut(state.identity().wholeIndex()); var pool = placement.pool();
        var outcome = solid.runWithPrefix(CdSolidObject.Entry.SOLID_OBJECT, state, inputs.player(), inputs.gates(), inputs.globals(), cut.placement().pool());
        CdObjectState object; List<CdObjectState> others; CdSolidObject.NativeStopped stopped = null;
        if (outcome instanceof CdSolidObject.Completed done) { object = done.completedResult().object(); others = done.completedResult().otherSlots(); }
        else { stopped = (CdSolidObject.NativeStopped) outcome; object = stopped.prefix().object(); others = stopped.prefix().otherSlots(); }
        var images = new ArrayList<CdObjectState>(); images.add(object); images.addAll(others);
        var ready = CdObjectPool.overlay(cut.placement().pool(), images); pool.validateRestore(ready); verify(cut);
        if (solidCommit == null) throw new UnsupportedWall(Boundary.SOLID_OWNER);
        var call = new SolidCall(cut, inputs.player(), inputs.globals(), outcome);
        try { pending(call, () -> solidCommit.commit(call)); }
        catch (RuntimeException | Error original) {
            suppress(original, () -> {
                verifyStatusBank(cut.placement());
                allowCallbackPrefix(cut.placement().pool(), ready);
                var actual = pool.capture().slots();
                if (!actual.equals(cut.placement().pool().slots()) && !actual.equals(ready.slots()))
                    throw new IllegalStateException("Wall failed Solid changed its declared full96 source/output");
            }); throw original;
        }
        verifyStatusBank(cut.placement());
        if (!pool.capture().slots().equals(ready.slots())) throw new IllegalStateException("Wall Solid commit omitted/changed its full96 output");
        dispatch.images = ready.slots();
        if (stopped != null) throw stopped.stop().originalException();
        effects.add(new SolidReturned(outcome));
    }
    private void draw(int whole, List<Effect> effects) {
        if (draw == null) throw new UnsupportedWall(Boundary.DRAW_OWNER);
        var before = cut(whole); var after = before.source().moveByte(SPRITE_FLAGS, before.source().spriteFlags() & 0x7F);
        var ready = CdObjectPool.overlay(before.placement().pool(), List.of(after));
        try { pending(before.source().identity(), () -> draw.draw(placement.pool(), before.source().identity())); }
        catch (RuntimeException | Error original) {
            suppress(original, () -> {
                verifyStatusBank(before.placement()); allowCallbackPrefix(before.placement().pool(), ready);
                var actual = placement.pool().capture().slots();
                if (!actual.equals(before.placement().pool().slots()) && !actual.equals(ready.slots()))
                    throw new IllegalStateException("Wall failed Draw changed an unowned full96 byte");
            }); throw original;
        }
        verifyStatusBank(before.placement());
        if (!placement.pool().capture().slots().equals(ready.slots())) throw new IllegalStateException("Wall Draw must only clear its own sprite bit7");
        dispatch.images = ready.slots();
        effects.add(new DrawReturned());
    }
    /** Object Spawner.asm:385-446; Fall bypasses this helper entirely. */
    private boolean despawn(CdObjectState state, Globals globals) {
        int x = state.centreX();
        boolean outside = (state.spriteFlags() & 0x80) == 0
                && (((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean warp = (globals.timeZone() & 0x80) != 0;
        if (!outside && !warp) return false;
        int saved = state.savedFlagsId();
        if (saved != 0) {
            int period = globals.timeZone() & 0x7F;
            if (warp) { int remapped = (period - (byte) globals.timeWarpDirection()) & 0xFFFF; period = (short) remapped < 0 ? 0 : remapped < 3 ? remapped : 2; }
            // Native positive raw periods are linear byte offsets, not modulo-three periods.
            // Adapt the exact byte through the existing checked bank owner; only the warp arm
            // above clamps. Saved0 skips this read, and Fall never enters this helper.
            int address = 2 + 3 * saved + period;
            if (address >= placement.capture().savedFlags().length())
                throw new UnsupportedWall(Boundary.SAVED_FLAGS_ADDRESS);
            int relative = address - 2;
            validateCurrentDispatch(); var bank = dispatch.saved.copy(); bank[address] &= 0x7F;
            var readyBank = CdAssetBytes.copyOf(bank);
            placement.bclrSavedFlag(relative / 3, relative % 3, CdObjectPlacementRuntime.SavedBit.LOADED);
            dispatch.saved = readyBank;
        }
        deleteNative(state.identity().wholeIndex()); return true;
    }
    private SourceCut cut(int whole) { return new SourceCut(placement.pool().state(whole), placement.capture()); }
    private void verifyStatusBank(CdObjectPlacementRuntime.Snapshot cut) {
        placement.validateRestore(cut); var now = placement.capture();
        if (!now.status().equals(cut.status()) || !Arrays.equals(now.savedFlags().copy(), cut.savedFlags().copy()))
            throw new IllegalStateException("Wall callback changed placement status/saved bank");
    }
    private void verify(SourceCut cut) {
        verifyStatusBank(cut.placement());
        if (!placement.pool().capture().slots().equals(cut.placement().pool().slots()))
            throw new IllegalStateException("Wall callback changed its borrowed full96 source");
    }
    private void readOnly(SourceCut cut, Runnable action) {
        verify(cut);
        try { action.run(); verify(cut); }
        catch (RuntimeException | Error original) { suppress(original, () -> verify(cut)); throw original; }
    }
    private static void suppress(Throwable original, Runnable check) {
        try { check.run(); } catch (RuntimeException | Error problem) { if (problem != original) original.addSuppressed(problem); }
    }
    private static void byteValue(int value) { if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Native unsigned BYTE required"); }
}
