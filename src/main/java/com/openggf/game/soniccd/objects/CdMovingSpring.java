package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectTileIds;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjMovingSpring ($0F), scddisasm1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Spring/Main.asm:7-96; Object Functions.asm:91-128,137-196,228-237;
 * Collision Check.asm:157-179; Object Spawner.asm:329-340; Object Animate.asm:14-95.
 * No FixBugs alternative exists in the wheel body; shared ObjMove retains shipped FixBugs=0.
 *
 * <p>Init creates the real dirty ID0A child, then falls into Align, never Main or Draw. The original
 * increasing RunObjects pass owns when that child runs. Every returned routine reaches the wrapper's
 * plain live-anchor cull, including an already-zeroed source after failed allocation. No saved flag,
 * time-zone, player/Solid, launch, FM, score, art, Frame, clock or capture owner is installed here.
 * Operations expose original transient identity; public records/effects describe observations and
 * confer no World invocation or publication authority. All expected placement images are prebuilt
 * before original stores. Readonly ports retain all96/status/saved on return AND throw; verification
 * faults are suppressed on the original exception, without rollback or repair.</p>
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdMovingSpring {
    public static final int OBJECT_ID = 0x0F, CHILD_ID = 0x0A, TILE_SELECTOR = 0x0E, MAPPING_ADDRESS = 0x209A96;

    /** Finite format data; construction is not source authentication or an art-residency statement. */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets visual, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(objectTileIds); Objects.requireNonNull(visual);
            if (objectTileIds.length() != 74 || mappingsAddress != MAPPING_ADDRESS
                    || visual.mappingTableOffset() != 0 || visual.mappings().length() != 16
                    || visual.mappingFrames().size() != 2 || visual.animations().size() != 1
                    || visual.animationTable().length() != 6) {
                throw new IllegalArgumentException("Moving Spring needs R12A tile74/map16/animation6 views");
            }
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces()) {
                if (piece.tileOffset() > 8 - piece.tileCount()) {
                    throw new IllegalArgumentException("Moving Spring mapping exceeds eight existing source tiles");
                }
            }
        }
        public static Assets fromAct2Assets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12A object assets"); var identity = Objects.requireNonNull(owner.identity());
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12A
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12A.stage())) {
                throw new IllegalArgumentException("Moving Spring requires original USA R12A Word RAM");
            }
            return new Assets(owner.view(CdR12aProfile.Asset.OBJECT_TILE_IDS), owner.movingSpring(),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_MOVING_SPRING).nativeAddress());
        }
        /** Source-local selectorE only: never widens the older global selector whitelist. */
        private int tileWord(CdObjectState source) {
            int end = CdObjectTileIds.endOfBody(objectTileIds, TILE_SELECTOR);
            int target = (short) objectTileIds.unsignedWord(TILE_SELECTOR * 2);
            int at = target + source.subtype2() * 2;
            if (end - target != 2 || at < target || at > end - 2) {
                throw unsupported(UnsupportedBoundary.TILE_ID_SUBTYPE2, source, source.subtype2());
            }
            return objectTileIds.unsignedWord(at);
        }
    }
    /** Read only at the reached TST.W Main and wrapper camera read; native unsigned WORD widths. */
    public record Globals(int cameraXWord, int timeStopTimerWord) {
        public Globals { wordValue(cameraXWord); wordValue(timeStopTimerWord); }
    }
    public record PrimaryAngleCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource, int value) {
        public PrimaryAngleCall { requireSource(actualCut, expectedSource); byteValue(value); }
    }
    public record SupportCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource, int wholeIndex) {
        public SupportCall {
            requireSource(actualCut, expectedSource);
            if (wholeIndex != 0 && (wholeIndex < 32 || wholeIndex >= 128)) throw new IllegalArgumentException("Unowned support slot");
        }
    }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public DrawCall { requireSource(actualCut, expectedSource); }
    }
    @FunctionalInterface public interface PrimaryAngleCommit { void commit(PrimaryAngleCall call); }
    /** Lazily reads the ACTUAL primary0/dynamic32..127 ID/flags; empty remains a typed shared-mover stop. */
    @FunctionalInterface public interface SupportRead { Optional<CdPlayerMovement.RawObjectSupport> read(SupportCall call); }
    @FunctionalInterface public interface DrawObjectCall { void drawObject(DrawCall call); }

    public enum NativeRoutine { INIT, ALIGN, MAIN }
    public enum NativeCall { GET_FLOOR_DIST, OBJ_MOVE, ANIMATE_OBJECT, DRAW_OBJECT }
    public enum DeleteSite { INIT_CAPACITY, ANCHOR_DISTANCE }
    public sealed interface Effect permits RoutineEntered, ChildCreated, SlotUnavailable, PrimaryAngleStored,
            FloorReturned, SupportReturned, CallReturned, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect { }
    public record ChildCreated(CdObjectState state) implements Effect { }
    public record SlotUnavailable() implements Effect { }
    public record PrimaryAngleStored(int value) implements Effect { }
    public record FloorReturned(CdObjectCollisionCheck.FloorDistance result) implements Effect { }
    public record SupportReturned(SupportCall call, Optional<CdPlayerMovement.RawObjectSupport> support) implements Effect {
        public SupportReturned { Objects.requireNonNull(call); Objects.requireNonNull(support); }
    }
    public record CallReturned(NativeCall call) implements Effect { }
    public record Deleted(DeleteSite site) implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }
    public enum UnsupportedBoundary { ROUTINE_DISPATCH, TILE_ID_SUBTYPE2, ANIMATION_DIRECTORY_READ,
        ANIMATION_SPEED_READ, ANIMATION_FRAME_READ, ANIMATION_OPERAND_READ }
    public static final class UnsupportedMovingSpring extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary; private final int wholeIndex, value;
        private UnsupportedMovingSpring(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Moving Spring whole " + wholeIndex + ", reached value " + value);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    public enum StoreKind { SOURCE_STORE, CHILD_STORE, DELETE }
    public record Publication(long ordinal, StoreKind kind, CdObjectPlacementRuntime.Snapshot actualCut,
                              CdObjectState expectedSource, CdObjectState before, CdObjectState output) { }
    public interface DispatchObserver {
        void beforePublication(Publication call); void afterPublication(Publication call);
        void beforeCall(Object call); void afterCall(Object call);
    }
    public interface ReceiptGuard { void prepared(Object call); void completed(Object call); }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets; private final CdPlayerTerrain terrain;
    private final PrimaryAngleCommit angleCommit; private final SupportRead supportRead; private final DrawObjectCall drawObject;
    private DispatchObserver observer; private ReceiptGuard receiptGuard;
    private boolean running; private Object pendingOperation; private long ordinal, pendingOrdinal;
    private CdObjectCollisionCheck.FloorDistance floorResult;

    public CdMovingSpring(CdObjectPlacementRuntime placement, Assets assets, CdPlayerTerrain terrain,
                          PrimaryAngleCommit angleCommit, SupportRead supportRead, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.terrain = Objects.requireNonNull(terrain); this.angleCommit = Objects.requireNonNull(angleCommit);
        this.supportRead = Objects.requireNonNull(supportRead); this.drawObject = Objects.requireNonNull(drawObject);
    }
    public static CdMovingSpring fromAct2Assets(CdObjectPlacementRuntime placement, CdR12aObjectAssets owner,
                                               CdPlayerTerrain terrain, PrimaryAngleCommit angleCommit,
                                               SupportRead supportRead, DrawObjectCall drawObject,
                                               DispatchObserver observer, ReceiptGuard guard) {
        Objects.requireNonNull(placement); Objects.requireNonNull(owner);
        if (!placement.sameAct2Source(owner)) throw new IllegalArgumentException("Moving Spring placement/assets source differs");
        var body = new CdMovingSpring(placement, Assets.fromAct2Assets(owner), terrain, angleCommit, supportRead, drawObject);
        body.observer = Objects.requireNonNull(observer); body.receiptGuard = Objects.requireNonNull(guard); return body;
    }
    public Assets assets() { return assets; }
    public boolean isCurrentCall(Object call) { return call != null && running && call == pendingOperation; }
    public long currentCallOrdinal(Object call) {
        if (!isCurrentCall(call)) throw new IllegalStateException("Not the original current Moving Spring operation");
        return pendingOrdinal;
    }
    /** Actual once-returned shared-helper observation during this invocation, never a replay/input seam. */
    public Optional<CdObjectCollisionCheck.FloorDistance> currentFloorResult() {
        return running ? Optional.ofNullable(floorResult) : Optional.empty();
    }

    public Result run(int wholeIndex, Globals globals) {
        if (running) throw new IllegalStateException("Reentrant ObjMovingSpring dispatch");
        var source = placement.pool().state(wholeIndex);
        if (source.id() != OBJECT_ID) throw new IllegalArgumentException("Selected source is not Moving Spring $0F");
        if (source.routine() != 0 && source.routine() != 2 && source.routine() != 4) {
            throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, source, source.routine());
        }
        running = true; ordinal = 0; floorResult = null;
        try {
            var c = new Cursor(source, globals);
            switch (source.routine()) {
                case 0 -> init(c);
                case 2 -> align(c);
                case 4 -> main(c);
            }
            // Wrapper reloads the LIVE anchor even after Init's plain DeleteObject returned.
            c.state = placement.pool().state(wholeIndex);
            int d0 = c.state.word(MOVING_SPRING_ANCHOR_X) & 0xFF80;
            int d1 = (c.globals().cameraXWord() - 0x80) & 0xFF80;
            if (((d0 - d1) & 0xFFFF) > 0x280) delete(c, DeleteSite.ANCHOR_DISTANCE);
            return new Result(placement.pool().state(wholeIndex), c.effects);
        } finally { running = false; pendingOperation = null; pendingOrdinal = 0; floorResult = null; }
    }
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.write(c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4).moveByte(PRIORITY, 4)
                .moveLong(MAPPINGS, assets.mappingsAddress()).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 7)
                .moveWord(MOVING_SPRING_ANCHOR_X, c.state.centreX()).moveWord(X_VELOCITY, 0x180));
        c.write(c.state.moveWord(TILE, assets.tileWord(c.state)));
        var found = placement.pool().findObjSlot();
        if (found.isEmpty()) { c.effects.add(new SlotUnavailable()); delete(c, DeleteSite.INIT_CAPACITY); return; }
        var child = placement.pool().state(found.getAsInt()).moveByte(ID, CHILD_ID)
                .moveWord(X, c.state.centreX()).moveWord(Y, c.state.centreY()).addWord(Y, -0x10)
                .moveByte(SPRING_PARENT_Y_OFFSET, 0xF0).moveWord(SPRING_PARENT, c.state.identity().nativeAddress24())
                .moveByte(SUBTYPE, c.state.subtype());
        nativeStore(c, child, StoreKind.CHILD_STORE); c.effects.add(new ChildCreated(child));
        align(c);
    }
    private void align(Cursor c) {
        c.enter(NativeRoutine.ALIGN); var floor = floor(c);
        if (floor.distance() < 0) {
            var aligned = c.state.addWord(Y, floor.distance());
            c.write(aligned.moveWord(MOVING_SPRING_BASE_Y, aligned.centreY()).addByte(ROUTINE, 2));
        } else c.write(c.state.addWord(Y, 1));
    }
    private void main(Cursor c) {
        c.enter(NativeRoutine.MAIN);
        if (c.globals().timeStopTimerWord() == 0) {
            var floor = floor(c); c.write(c.state.addWord(Y, floor.distance()));
            if (((c.state.word(MOVING_SPRING_BASE_Y) - c.state.centreY()) & 0xFFFF) >= 0xC) {
                c.write(c.state.moveWord(X_VELOCITY, -c.state.word(X_VELOCITY)));
            }
            move(c); animate(c);
        }
        draw(c);
    }
    private CdObjectCollisionCheck.FloorDistance floor(Cursor c) {
        var result = CdObjectCollisionCheck.objGetFloorDist(terrain, c.state, value -> {
            var call = new PrimaryAngleCall(capture(c), c.state, value);
            observedCall(call, () -> { readOnly(() -> { angleCommit.commit(call); return null; }); return null; });
            c.effects.add(new PrimaryAngleStored(value));
        });
        floorResult = result; c.effects.add(new FloorReturned(result)); c.effects.add(new CallReturned(NativeCall.GET_FLOOR_DIST));
        return result;
    }
    private void move(Cursor c) {
        int link = c.state.unsignedByte(STAND_LINK_3D); Optional<CdPlayerMovement.RawObjectSupport> support = Optional.empty();
        if ((c.state.flags() & 8) != 0 && (link == 0 || link >= 32 && link < 128)) {
            var call = new SupportCall(capture(c), c.state, link);
            support = observedCall(call, () -> readOnly(() -> Objects.requireNonNull(supportRead.read(call), "actual support result")));
            if (link != 0 && support.isPresent() && support.orElseThrow().wholeIndex() == link) {
                var actual = placement.pool().state(link); var read = support.orElseThrow();
                if (read.id() != actual.id() || read.flags22() != actual.flags()) {
                    throw new IllegalStateException("Moving Spring support differs from its actual dynamic source");
                }
            }
            c.effects.add(new SupportReturned(call, support));
        }
        var moved = CdPlayerMovement.moveObject(new CdPlayerMovement.RawObjectMotion(c.state.longBits(X_POSITION),
                c.state.longBits(Y_POSITION), c.state.signedWord(X_VELOCITY), c.state.signedWord(Y_VELOCITY),
                c.state.flags(), c.state.unsignedByte(RAW_CONTROL_2C), link), support);
        c.write(c.state.moveLong(X_POSITION, moved.centreX16_16()).moveLong(Y_POSITION, moved.centreY16_16()));
        c.effects.add(new CallReturned(NativeCall.OBJ_MOVE));
    }
    private void animate(Cursor c) {
        var original = c.state; CdObjectAnimation.State out;
        var table = new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, 1);
        try {
            out = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION), original.unsignedByte(PREVIOUS_ANIMATION),
                    original.unsignedByte(ANIMATION_FRAME), original.unsignedByte(ANIMATION_TIME), original.unsignedByte(MAP_FRAME),
                    original.spriteFlags(), original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)), table);
        } catch (UnsupportedOperationException cause) {
            animationFailurePrefix(c, table, cause);
            throw new IllegalStateException("Shared Moving Spring animation failure did not match a reached read", cause);
        }
        c.write(original.moveByte(ANIMATION, out.animationId()).moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex()).moveByte(ANIMATION_TIME, out.timer()).moveByte(MAP_FRAME, out.mapFrame())
                .moveByte(SPRITE_FLAGS, out.spriteFlags()).moveByte(FLAGS, out.statusFlags()).moveByte(ROUTINE, out.routine()).moveByte(ROUTINE_2, out.routine2()));
        c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
    }
    /** Only completed stores before a declared failed finite read; never replays a successful step. */
    private void animationFailurePrefix(Cursor c, CdObjectAnimation.AnimationTable table, RuntimeException cause) {
        if (c.state.unsignedByte(ANIMATION) != c.state.unsignedByte(PREVIOUS_ANIMATION)) {
            c.write(c.state.moveByte(PREVIOUS_ANIMATION, c.state.unsignedByte(ANIMATION)).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0));
        }
        c.write(c.state.addByte(ANIMATION_TIME, -1)); if (c.state.signedByte(ANIMATION_TIME) >= 0) return;
        int id = c.state.unsignedByte(ANIMATION);
        if (id >= table.scriptCount()) throw animationMissing(c, UnsupportedBoundary.ANIMATION_DIRECTORY_READ, 2 * id, cause);
        int script = table.tableOffset() + (short) table.bytes().unsignedWord(table.tableOffset() + 2 * id);
        c.write(c.state.moveByte(ANIMATION_TIME, animationByte(c, table, script, UnsupportedBoundary.ANIMATION_SPEED_READ, cause)));
        int index = c.state.unsignedByte(ANIMATION_FRAME);
        int frame = animationByte(c, table, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ, cause);
        if (frame == 0xFF) {
            c.write(c.state.moveByte(ANIMATION_FRAME, 0)); animationByte(c, table, script + 1, UnsupportedBoundary.ANIMATION_FRAME_READ, cause);
        } else if (frame == 0xFE) {
            int back = animationByte(c, table, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ, cause);
            index = (index - back) & 0xFF; c.write(c.state.moveByte(ANIMATION_FRAME, index));
            animationByte(c, table, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ, cause);
        } else if (frame == 0xFD) animationByte(c, table, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ, cause);
    }
    private int animationByte(Cursor c, CdObjectAnimation.AnimationTable table, int at, UnsupportedBoundary boundary, RuntimeException cause) {
        if (at < 0 || at >= table.bytes().length()) throw animationMissing(c, boundary, at, cause);
        return table.bytes().unsignedByte(at);
    }
    private UnsupportedMovingSpring animationMissing(Cursor c, UnsupportedBoundary boundary, int at, RuntimeException cause) {
        var stopped = unsupported(boundary, c.state, at); stopped.initCause(cause); return stopped;
    }
    private void draw(Cursor c) {
        var cut = capture(c); var call = new DrawCall(cut, c.state);
        var expected = expected(cut, c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() & 0x7F), false);
        observedCall(call, () -> {
            try { drawObject.drawObject(call); requirePlacement(cut, expected); }
            catch (RuntimeException | Error original) {
                suppress(original, () -> { if (!samePlacement(cut, images(cut))) requirePlacement(cut, expected); }); throw original;
            }
            return null;
        });
        c.state = placement.pool().state(c.whole); c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }
    private void nativeStore(Cursor c, CdObjectState output, StoreKind kind) {
        var cut = capture(c); placement.pool().validateWrite(output); var expected = expected(cut, output, false);
        var call = new Publication(nextOrdinal(), kind, cut, c.state, placement.pool().state(output.identity().wholeIndex()), output);
        observedPublication(call, () -> { placement.pool().write(output); requirePlacement(cut, expected); });
    }
    private void delete(Cursor c, DeleteSite site) {
        var cut = capture(c); var expected = expected(cut, c.state, true);
        var call = new Publication(nextOrdinal(), StoreKind.DELETE, cut, c.state, c.state, null);
        observedPublication(call, () -> { placement.pool().deleteObject(c.whole); requirePlacement(cut, expected); });
        c.state = placement.pool().state(c.whole); c.effects.add(new Deleted(site));
    }
    private long nextOrdinal() {
        if (pendingOperation != null) throw new IllegalStateException("Nested Moving Spring producer operation"); return ++ordinal;
    }
    private void observedPublication(Publication call, Runnable nativeStore) {
        pendingOperation = call; pendingOrdinal = call.ordinal();
        try {
            if (observer != null) { readOnly(() -> { observer.beforePublication(call); return null; }); readOnly(() -> { receiptGuard.prepared(call); return null; }); }
            nativeStore.run();
            if (observer != null) { readOnly(() -> { observer.afterPublication(call); return null; }); readOnly(() -> { receiptGuard.completed(call); return null; }); }
        } finally { pendingOperation = null; pendingOrdinal = 0; }
    }
    private <T> T observedCall(Object call, Supplier<T> nativeCall) {
        long value = nextOrdinal(); pendingOperation = call; pendingOrdinal = value;
        try {
            if (observer != null) { readOnly(() -> { observer.beforeCall(call); return null; }); readOnly(() -> { receiptGuard.prepared(call); return null; }); }
            T result = nativeCall.get();
            if (observer != null) { readOnly(() -> { observer.afterCall(call); return null; }); readOnly(() -> { receiptGuard.completed(call); return null; }); }
            return result;
        } finally { pendingOperation = null; pendingOrdinal = 0; }
    }
    private <T> T readOnly(Supplier<T> callback) {
        var cut = placement.capture(); var expected = images(cut);
        T result;
        try { result = callback.get(); }
        catch (RuntimeException | Error original) { suppress(original, () -> requirePlacement(cut, expected)); throw original; }
        requirePlacement(cut, expected); return result;
    }
    private CdObjectPlacementRuntime.Snapshot capture(Cursor c) { var cut = placement.capture(); requireSource(cut, c.state); return cut; }
    private static List<byte[]> images(CdObjectPlacementRuntime.Snapshot cut) {
        var result = new ArrayList<byte[]>(); for (var state : cut.pool().slots()) result.add(state.copyBytes()); return result;
    }
    private static List<byte[]> expected(CdObjectPlacementRuntime.Snapshot cut, CdObjectState output, boolean delete) {
        if (!cut.pool().slots().get(output.identity().dynamicIndex()).identity().equals(output.identity())) throw new IllegalArgumentException("Foreign physical source");
        var images = images(cut); images.set(output.identity().dynamicIndex(), delete ? new byte[64] : output.copyBytes()); return images;
    }
    private boolean samePlacement(CdObjectPlacementRuntime.Snapshot cut, List<byte[]> expected) {
        var now = placement.capture();
        if (!now.status().equals(cut.status()) || !Arrays.equals(now.savedFlags().copy(), cut.savedFlags().copy())) return false;
        for (var old : cut.pool().slots()) {
            var current = placement.pool().state(old.identity().wholeIndex());
            if (!old.identity().equals(current.identity()) || !Arrays.equals(expected.get(old.identity().dynamicIndex()), current.copyBytes())) return false;
        }
        return true;
    }
    private void requirePlacement(CdObjectPlacementRuntime.Snapshot cut, List<byte[]> expected) {
        placement.validateRestore(cut);
        if (!samePlacement(cut, expected)) throw new IllegalStateException("Moving Spring callback changed its actual complete placement cut");
    }
    private static void requireSource(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if (!cut.pool().slots().get(source.identity().dynamicIndex()).equals(source)) throw new IllegalArgumentException("Moving Spring source is not the actual cut image");
    }
    private static void suppress(Throwable original, Runnable verification) {
        try { verification.run(); } catch (RuntimeException | Error fault) { if (fault != original) original.addSuppressed(fault); }
    }
    private static UnsupportedMovingSpring unsupported(UnsupportedBoundary boundary, CdObjectState source, int value) {
        return new UnsupportedMovingSpring(boundary, source.identity().wholeIndex(), value);
    }
    private final class Cursor {
        final int whole; final Globals sourceGlobals; final List<Effect> effects = new ArrayList<>(); CdObjectState state;
        Cursor(CdObjectState source, Globals globals) { whole = source.identity().wholeIndex(); state = source; sourceGlobals = globals; }
        Globals globals() { return Objects.requireNonNull(sourceGlobals, "reached native Moving Spring globals"); }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        void write(CdObjectState next) { nativeStore(this, next, StoreKind.SOURCE_STORE); state = next; }
    }
    private static void byteValue(int value) { if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected native unsigned BYTE"); }
    private static void wordValue(int value) { if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected native unsigned WORD"); }
}
