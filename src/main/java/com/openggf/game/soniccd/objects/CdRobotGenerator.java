package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * One selected ObjRobotGenerator ($2D) physical dispatch. Native source pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/_Objects/Robot Generator/Main.asm:25-229;
 * Palmtree Panic/Objects/Robot Generator/Tile IDs.asm:7-10; Object Animate.asm:14-95;
 * Object Functions.asm:137-196; Object Spawner.asm:329-340,385-446.
 * There is no FixBugs alternative in this body. Shared Solid retains shipped FixBugs=0 behavior.
 *
 * <p>Pool writes are real, ordered prefixes. Synchronous ports carry a fresh full-placement capture
 * and actual source image; those values are not invocation authority. Root checks its private phase,
 * generation, complete owners and expected globals before one existing-owner publication. Completed
 * effects are observations, never commands to replay. Every port preserves the original exception;
 * failed placement verification is suppressed without adopting or repairing unexpected state.
 * This body installs no frame, clock, scanner, art, global bank or audio owner.</p>
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdRobotGenerator {
    public static final int OBJECT_ID = 0x2D;
    /** Finite dispatch identity only; source operands remain independently supplied/authenticated. */
    public enum Profile { PALMTREE(0x2D), COLLISION_CHAOS(0x37);
        private final int id; Profile(int id) { this.id = id; } public int objectId() { return id; }
    }
    private Profile profile = Profile.PALMTREE;
    public static CdRobotGenerator collisionChaos(CdObjectPlacementRuntime placement, Assets assets,
            CdSolidObject solid, SolidCommit commit, GoodFutureStore future, AddPointsCall points,
            FmCall fm, DrawObjectCall draw, DispatchObserver observer, ReceiptGuard guard) {
        var body = new CdRobotGenerator(placement, assets, solid, commit, future, points, fm, draw);
        body.profile = Profile.COLLISION_CHAOS;
        body.observer = Objects.requireNonNull(observer); body.receiptGuard = Objects.requireNonNull(guard);
        return body;
    }
    public static final int EXPLOSION_ID = 0x18;
    public static final int HIT_POINTS = 0x96;
    public static final int FM_EXPLODE = 0x9E;
    public static final int FM_D9 = 0xD9;

    /** Raw finite format inputs; construction neither reads selected operands nor authenticates media. */
    public record Assets(CdAssetBytes baseTiles, CdObjectVisualAssets visual, int mappingsAddress,
                         CdAssetBytes explosionSchedule, int explosionScheduleAddress) {
        public Assets {
            Objects.requireNonNull(baseTiles, "baseTiles"); Objects.requireNonNull(visual, "visual");
            Objects.requireNonNull(explosionSchedule, "explosionSchedule");
            if (visual.mappingFrames().size() != 8 || visual.animations().size() != 1)
                throw new IllegalArgumentException("Robot Generator requires eight mapping frames and one script");
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces())
                if (piece.tileOffset() > 23 - piece.tileCount())
                    throw new IllegalArgumentException("Robot Generator mapping exceeds its twenty-three source tiles");
        }
        /** Actual R12B act/period table, maps and schedule only; no current art or programme writer. */
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12B object assets");
            var identity=Objects.requireNonNull(owner.identity(), "R12B identity");
            if(identity.variant()!=CdReleaseVariant.USA || identity.file()!=CdNativeFile.R12B
                    || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM || !identity.stage().equals(CdStagePayload.R12B.stage()))
                throw new IllegalArgumentException("Robot Generator requires actual original USA R12B metadata");
            return new Assets(owner.view(CdR12bObjectAssets.View.ROBOT_GENERATOR_BASE_TILES),owner.robotGenerator(),
                    CdR12bObjectAssets.View.MAP_SPR_ROBOT_GENERATOR.range().nativeAddress(),owner.view(CdR12bObjectAssets.View.ROBOT_GENERATOR_EXPLOSION_SCHEDULE),
                    CdR12bObjectAssets.View.ROBOT_GENERATOR_EXPLOSION_SCHEDULE.range().nativeAddress());
        }

        /** Original USA R12A finite source views; this binds metadata, never art decoding or residency. */
        public static Assets fromAct2Assets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12A object assets");
            var identity = Objects.requireNonNull(owner.identity(), "R12A source identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12A
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12A.stage())) {
                throw new IllegalArgumentException("Robot Generator requires authenticated original USA R12A Word RAM");
            }
            return new Assets(owner.view(CdR12aProfile.Asset.ROBOT_GENERATOR_BASE_TILES), owner.robotGenerator(),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_ROBOT_GENERATOR).nativeAddress(),
                    owner.view(CdR12aProfile.Asset.ROBOT_GENERATOR_EXPLOSION_SCHEDULE),
                    owner.profile().range(CdR12aProfile.Asset.ROBOT_GENERATOR_EXPLOSION_SCHEDULE).nativeAddress());
        }
        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner, "actual Past assets");
            var map = owner.view(CdR11bAssets.Asset.MAP_SPR_ROBOT_GENERATOR);
            var animation = owner.view(CdR11bAssets.Asset.ANI_ROBOT_GENERATOR);
            var schedule = owner.view(CdR11bAssets.Asset.ROBOT_GENERATOR_EXPLOSION_SCHEDULE);
            return new Assets(owner.view(CdR11bAssets.Asset.ROBOT_GENERATOR_BASE_TILES).bytes(),
                    CdObjectVisualAssets.decode(map.bytes().copy(), 8, animation.bytes().copy(), 1, 23),
                    map.range().nativeAddress(), schedule.bytes(), schedule.range().nativeAddress());
        }
    }
    /** Native raw bytes plus camera WORD; timeZone is deliberately not normalized to a period. */
    public interface Reads {int goodFuture();int timeZone();int act();int timeWarpDirection();int cameraXWord();}
    public record Globals(int goodFuture, int timeZone, int act, int timeWarpDirection, int cameraXWord) implements Reads {
        public Globals {
            byteValue(goodFuture); byteValue(timeZone); byteValue(act); byteValue(timeWarpDirection); wordValue(cameraXWord);
        }
    }
    /** Read only if an actual Solid/GetOff call is reached; later calls use the preceding native result. */
    public record Inputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public Inputs { Objects.requireNonNull(player); Objects.requireNonNull(gates); Objects.requireNonNull(globals); }
    }
    public enum Site { ACTIVE_SOLID, HIT_SOLID, HIT_GET_OFF }
    public enum NativeRoutine { INIT, ACTIVE_PAST, EXPLODING, REBUILD_DELAY }
    public enum NativeCall { SOLID_OBJECT, GET_OFF_OBJECT, ANIMATE_OBJECT, DRAW_OBJECT, CHECK_OBJ_DESPAWN }

    public record SolidCall(Site site, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource,
                            CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
                            CdSolidObject.Outcome outcome) {
        public SolidCall {
            Objects.requireNonNull(site); requireSource(actualCut, expectedSource);
            Objects.requireNonNull(expectedPlayer); Objects.requireNonNull(expectedGlobals); Objects.requireNonNull(outcome);
            var actual = outcome instanceof CdSolidObject.Completed done ? done.completedResult().entry()
                    : ((CdSolidObject.NativeStopped) outcome).prefix().entry();
            if (actual != entryFor(site)) throw new IllegalArgumentException("Wrong Robot Generator Solid entry");
        }
        public CdSolidObject.Entry entry() { return entryFor(site); }
    }
    public record GoodFutureCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource,
                                 int expectedGoodFuture, int value) {
        public GoodFutureCall {
            requireSource(actualCut, expectedSource); byteValue(expectedGoodFuture);
            if (value != 1) throw new IllegalArgumentException("Native Robot Generator stores goodFuture=1");
        }
    }
    /** Root invokes its shared native AddPoints value kernel and adopts score/ring/sound outputs immediately. */
    public record PointsCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource, int points) {
        public PointsCall {
            requireSource(actualCut, expectedSource);
            if (points != HIT_POINTS) throw new IllegalArgumentException("Robot Generator AddPoints d0.l is $96");
        }
    }
    public record FmRequest(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource, int nativeId) {
        public FmRequest {
            requireSource(actualCut, expectedSource);
            if (nativeId != FM_EXPLODE && nativeId != FM_D9) throw new IllegalArgumentException("Wrong Robot Generator FM request");
        }
    }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public DrawCall { requireSource(actualCut, expectedSource); }
    }
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }
    @FunctionalInterface public interface GoodFutureStore { void store(GoodFutureCall call); }
    @FunctionalInterface public interface AddPointsCall { void addPoints(PointsCall call); }
    @FunctionalInterface public interface FmCall { void playFm(FmRequest call); }
    /** Native collecting DrawObject: exactly source bit7 clear, queue pointer work outside placement. */
    @FunctionalInterface public interface DrawObjectCall { void drawObject(DrawCall call); }

    public sealed interface Effect permits RoutineEntered, CallReturned, SolidReturned, GoodFutureStored,
            PointsAdded, FmRequested, ChildCreated, SlotUnavailable, SavedFlagChanged, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect { }
    public record CallReturned(NativeCall call) implements Effect { }
    public record SolidReturned(Site site, CdSolidObject.Result result) implements Effect { }
    public record GoodFutureStored(int previousValue, int value) implements Effect { }
    public record PointsAdded(int points) implements Effect { }
    public record FmRequested(int nativeId) implements Effect { }
    public record ChildCreated(CdObjectState state) implements Effect { }
    public record SlotUnavailable() implements Effect { }
    public record SavedFlagChanged(int byteIndex, int previousValue, int value) implements Effect { }
    public record DespawnChecked(boolean deleted) implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(e -> e instanceof DespawnChecked d && d.deleted()); }
    }
    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, BASE_TILE_READ, SCHEDULE_TICK_READ, SCHEDULE_X_READ, SCHEDULE_Y_READ,
        ANIMATION_DIRECTORY_READ, ANIMATION_SPEED_READ, ANIMATION_FRAME_READ, ANIMATION_OPERAND_READ, SAVED_FLAGS_ADDRESS
    }
    /** Only a reached finite operand fails; every preceding committed prefix is retained. */
    public static final class UnsupportedRobotGenerator extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final long value;
        private UnsupportedRobotGenerator(UnsupportedBoundary boundary, int wholeIndex, long value) {
            super(boundary + " at Robot Generator slot " + wholeIndex + ", reached value " + value);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public long value() { return value; }
    }

    /** Completed operation metadata only; equal records carry no authority to invoke or adopt a store. */
    public enum StoreKind { SOURCE_STORE, CHILD_STORE, SAVED_FLAG_CLEAR, DELETE }
    public record Publication(long ordinal, StoreKind kind, CdObjectPlacementRuntime.Snapshot actualCut,
                              CdObjectState expectedSource, CdObjectState before, CdObjectState output,
                              int savedByteIndex, int previousValue, int value) { }
    public interface DispatchObserver {
        void beforePublication(Publication call);
        void afterPublication(Publication call);
        void beforeCall(Object call);
        void afterCall(Object call);
    }
    /** The private World checks its prepared/completed receipt identity synchronously. */
    public interface ReceiptGuard { void prepared(Object call); void completed(Object call); }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final CdSolidObject sharedSolid;
    private final SolidCommit solidCommit;
    private final GoodFutureStore goodFutureStore;
    private final AddPointsCall addPoints;
    private final FmCall fmCall;
    private final DrawObjectCall drawObject;
    private boolean running;
    private DispatchObserver observer;
    private ReceiptGuard receiptGuard;
    private Object pendingOperation;
    private long operationOrdinal, pendingOrdinal;

    public CdRobotGenerator(CdObjectPlacementRuntime placement, Assets assets, CdSolidObject sharedSolid,
                            SolidCommit solidCommit, GoodFutureStore goodFutureStore, AddPointsCall addPoints,
                            FmCall fmCall, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.sharedSolid = Objects.requireNonNull(sharedSolid); this.solidCommit = Objects.requireNonNull(solidCommit);
        this.goodFutureStore = Objects.requireNonNull(goodFutureStore); this.addPoints = Objects.requireNonNull(addPoints);
        this.fmCall = Objects.requireNonNull(fmCall); this.drawObject = Objects.requireNonNull(drawObject);
    }
    /** Actual R12B source pairing with the existing shared Solid, programme ports and receipt protocol. */
    public static CdRobotGenerator fromAct2PastAssets(CdObjectPlacementRuntime placement,CdR12bTerrainAssets owner,
                                                      CdSolidObject solid,SolidCommit commit,GoodFutureStore goodFuture,AddPointsCall points,
                                                      FmCall fm,DrawObjectCall draw,DispatchObserver observer,ReceiptGuard guard) {
        Objects.requireNonNull(placement, "actual R12B placement"); Objects.requireNonNull(owner, "actual R12B terrain");
        if(!placement.sameAct2PastSource(owner))throw new IllegalArgumentException("Robot Generator requires the same retained R12B placement/source");
        var body=new CdRobotGenerator(placement,Assets.fromAct2PastAssets(owner.objects()),solid,commit,goodFuture,points,fm,draw);
        body.observer=Objects.requireNonNull(observer);body.receiptGuard=Objects.requireNonNull(guard);return body;
    }

    /** Retains the constructor's native kernel and callbacks, with a paired original R12A source. */
    public static CdRobotGenerator fromAct2Assets(CdObjectPlacementRuntime placement, CdR12aObjectAssets owner,
                                                CdSolidObject sharedSolid, SolidCommit solidCommit,
                                                GoodFutureStore goodFutureStore, AddPointsCall addPoints,
                                                FmCall fmCall, DrawObjectCall drawObject,
                                                DispatchObserver observer, ReceiptGuard guard) {
        Objects.requireNonNull(placement); Objects.requireNonNull(owner);
        if (!placement.sameAct2Source(owner)) throw new IllegalArgumentException("Robot Generator placement/assets source differs");
        var body = new CdRobotGenerator(placement, Assets.fromAct2Assets(owner), sharedSolid, solidCommit,
                goodFutureStore, addPoints, fmCall, drawObject);
        body.observer = Objects.requireNonNull(observer); body.receiptGuard = Objects.requireNonNull(guard);
        return body;
    }
    public Assets assets() { return assets; }
    public boolean isCurrentCall(Object call) { return call != null && running && call == pendingOperation; }
    /** Positive only for the original operation during its synchronous observation/port lifetime. */
    public long currentCallOrdinal(Object call) {
        if (!isCurrentCall(call)) throw new IllegalStateException("Not the original current Robot Generator operation");
        return pendingOrdinal;
    }

    public Result run(int wholeIndex, Globals globals, Inputs inputs) {return runWithReads(wholeIndex,globals,inputs);}
    public Result runWithReads(int wholeIndex, Reads globals, Inputs inputs) {
        if (running) throw new IllegalStateException("Reentrant ObjRobotGenerator dispatch");
        var state = placement.pool().state(wholeIndex);
        if (state.id() != profile.objectId()) throw new IllegalArgumentException("Selected slot is not Robot Generator $2D");
        if ((state.routine() & 1) != 0 || state.routine() > 6)
            throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, state.routine());
        running = true; operationOrdinal = 0;
        try {
            var c = new Cursor(state, globals, inputs);
            switch (state.routine()) {
                case 0 -> { init(c); active(c); }
                case 2 -> active(c);
                case 4 -> exploding(c);
                case 6 -> rebuild(c);
            }
            draw(c); // ALWAYS after a returned body; signed live routine reload owns the despawn gate.
            if (c.state.signedByte(ROUTINE) <= 2) despawn(c);
            return new Result(placement.pool().state(wholeIndex), c.effects);
        } finally { running = false; pendingOperation = null; pendingOrdinal = 0; }
    }
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.write(c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveByte(PRIORITY, 4).moveByte(X_RADIUS, 0x22).moveByte(WIDTH, 0x22).moveByte(Y_RADIUS, 0x20));
        var g = c.globals(); int d0 = g.act() << 2;
        d0 = (d0 & 0xFF00) | ((d0 + g.timeZone()) & 0xFF); // ADD.B preserves the ASL.W high byte.
        int at = (short) ((d0 + d0) & 0xFFFF);
        if (at < 0 || at > assets.baseTiles().length() - 2) throw unsupported(UnsupportedBoundary.BASE_TILE_READ, c.whole, at);
        c.write(c.state.moveWord(TILE, assets.baseTiles().unsignedWord(at)).moveLong(MAPPINGS, assets.mappingsAddress())
                .moveLong(ROBOT_GENERATOR_SCHEDULE, assets.explosionScheduleAddress())
                .moveWord(ROBOT_GENERATOR_BASE_Y, c.state.centreY()).moveWord(ROBOT_GENERATOR_TIMER, 4)
                .moveWord(ROBOT_GENERATOR_FLOAT_STEP, 1));
        int frame = (g.goodFuture() == 0 ? 2 : 0) + (g.timeZone() == 0 ? 1 : 0);
        c.write(c.state.moveByte(MAP_FRAME, frame));
        if (g.goodFuture() == 0 && g.timeZone() == 0)
            c.write(c.state.moveByte(COLLISION_TYPE, 0xFA).addWord(Y, -0x10));
    }
    private void active(Cursor c) {
        c.enter(NativeRoutine.ACTIVE_PAST);
        int expectedGoodFuture = c.globals().goodFuture();
        if (expectedGoodFuture != 0 || c.globals().timeZone() != 0) return;
        c.write(c.state.addWord(ROBOT_GENERATOR_TIMER, 1));
        if ((c.state.word(ROBOT_GENERATOR_TIMER) & 7) == 0)
            c.write(c.state.addWord(Y, c.state.signedWord(ROBOT_GENERATOR_FLOAT_STEP)));
        if ((c.state.word(ROBOT_GENERATOR_TIMER) & 31) == 0)
            c.write(c.state.moveWord(ROBOT_GENERATOR_FLOAT_STEP, -c.state.word(ROBOT_GENERATOR_FLOAT_STEP)));
        if (c.state.unsignedByte(COLLISION_STATUS) == 0) {
            solid(c, Site.ACTIVE_SOLID); animate(c); return;
        }
        c.write(c.state.moveByte(COLLISION_TYPE, 0).moveByte(COLLISION_STATUS, 0)
                .moveWord(ROBOT_GENERATOR_TIMER, 0).moveByte(MAP_FRAME, 7).addByte(ROUTINE, 2));
        var cut = capture(c); var good = new GoodFutureCall(cut, c.state, expectedGoodFuture, 1);
        observedCall(good, () -> readOnly(c, good.actualCut(), () -> goodFutureStore.store(good)));
        c.effects.add(new GoodFutureStored(good.expectedGoodFuture(), 1));
        cut = capture(c); var points = new PointsCall(cut, c.state, HIT_POINTS);
        observedCall(points, () -> readOnly(c, points.actualCut(), () -> addPoints.addPoints(points))); c.effects.add(new PointsAdded(HIT_POINTS));
        if (solid(c, Site.HIT_SOLID).d0().orElseThrow() != 0) solid(c, Site.HIT_GET_OFF);
    }
    private void exploding(Cursor c) {
        c.enter(NativeRoutine.EXPLODING);
        int pointer = c.state.longBits(ROBOT_GENERATOR_SCHEDULE);
        int tick = scheduleByte(c, pointer, UnsupportedBoundary.SCHEDULE_TICK_READ);
        if ((byte) tick < 0) {
            c.write(c.state.addByte(ROUTINE, 2).moveByte(ROBOT_GENERATOR_TIMER_HIGH, 8)); return;
        }
        c.write(c.state.addByte(ROBOT_GENERATOR_TIMER_HIGH, 1));
        if (tick != c.state.unsignedByte(ROBOT_GENERATOR_TIMER_HIGH)) return;
        int x = (byte) scheduleByte(c, pointer + 1, UnsupportedBoundary.SCHEDULE_X_READ);
        int y = (byte) scheduleByte(c, pointer + 2, UnsupportedBoundary.SCHEDULE_Y_READ);
        c.write(c.state.moveLong(ROBOT_GENERATOR_SCHEDULE, pointer + 3));
        var found = placement.pool().findObjSlot();
        if (found.isEmpty()) { c.effects.add(new SlotUnavailable()); return; }
        var child = placement.pool().state(found.getAsInt()).moveByte(ID, EXPLOSION_ID).moveByte(ROUTINE_2, 1)
                .moveWord(X, c.state.centreX()).moveWord(Y, c.state.centreY()).addWord(X, x).addWord(Y, y);
        nativeStore(c, child, StoreKind.CHILD_STORE); c.effects.add(new ChildCreated(child));
        fm(c, FM_EXPLODE);
    }
    private void rebuild(Cursor c) {
        c.enter(NativeRoutine.REBUILD_DELAY);
        c.write(c.state.addByte(ROBOT_GENERATOR_TIMER_HIGH, -1));
        if (c.state.unsignedByte(ROBOT_GENERATOR_TIMER_HIGH) != 0) return;
        c.write(c.state.addByte(ROUTINE, -6).moveWord(Y, c.state.word(ROBOT_GENERATOR_BASE_Y)));
        fm(c, FM_D9);
    }
    private int scheduleByte(Cursor c, int pointer, UnsupportedBoundary boundary) {
        long at = (Integer.toUnsignedLong(pointer) - Integer.toUnsignedLong(assets.explosionScheduleAddress())) & 0xFFFFFFFFL;
        if (at >= assets.explosionSchedule().length()) throw unsupported(boundary, c.whole, Integer.toUnsignedLong(pointer));
        return assets.explosionSchedule().unsignedByte((int) at);
    }
    private void fm(Cursor c, int nativeId) {
        var cut = capture(c); var request = new FmRequest(cut, c.state, nativeId);
        observedCall(request, () -> readOnly(c, cut, () -> fmCall.playFm(request))); c.effects.add(new FmRequested(nativeId));
    }
    private CdSolidObject.Result solid(Cursor c, Site site) {
        c.prepareInputs(); var cut = capture(c);
        var outcome = sharedSolid.runWithPrefix(entryFor(site), c.state, c.player, c.inputs.gates(), c.solidGlobals, cut.pool());
        var output = output(outcome, cut.pool()); validateOutput(c, cut, output); assertPlacement(cut, cut.pool());
        var call = new SolidCall(site, cut, c.state, c.player, c.solidGlobals, outcome);
        observedCall(call, () -> {
            try { solidCommit.commit(call); assertPlacement(cut, output.pool()); }
            catch (RuntimeException | Error original) {
                suppress(original, () -> {
                    if (samePlacement(cut, cut.pool())) return;
                    assertPlacement(cut, output.pool());
                });
                throw original;
            }
        });
        c.state = placement.pool().state(c.whole); c.player = output.player(); c.solidGlobals = output.globals();
        if (outcome instanceof CdSolidObject.NativeStopped stopped) throw stopped.stop().originalException();
        var result = ((CdSolidObject.Completed) outcome).completedResult();
        c.effects.add(new SolidReturned(site, result));
        c.effects.add(new CallReturned(site == Site.HIT_GET_OFF ? NativeCall.GET_OFF_OBJECT : NativeCall.SOLID_OBJECT));
        return result;
    }
    private record SolidOutput(CdObjectState object, List<CdObjectState> others, CdSolidObject.Player player,
                               CdSolidObject.Globals globals, CdObjectPool.Snapshot pool) { }
    private static SolidOutput output(CdSolidObject.Outcome outcome, CdObjectPool.Snapshot cut) {
        if (outcome instanceof CdSolidObject.Completed done) {
            var r = done.completedResult(); return new SolidOutput(r.object(), r.otherSlots(), r.player(), r.globals(), outputPool(cut, r.object(), r.otherSlots()));
        }
        var p = ((CdSolidObject.NativeStopped) outcome).prefix();
        return new SolidOutput(p.object(), p.otherSlots(), p.player(), p.globals(), outputPool(cut, p.object(), p.otherSlots()));
    }
    private static CdObjectPool.Snapshot outputPool(CdObjectPool.Snapshot cut, CdObjectState object, List<CdObjectState> others) {
        var images = new ArrayList<CdObjectState>(); images.add(object); images.addAll(others);
        return CdObjectPool.overlay(cut, images);
    }
    private void validateOutput(Cursor c, CdObjectPlacementRuntime.Snapshot cut, SolidOutput output) {
        if (!output.object().identity().equals(c.state.identity())) throw new IllegalArgumentException("Solid changed a0 identity");
        var images = new ArrayList<CdObjectState>(); images.add(output.object()); images.addAll(output.others());
        var seen = new HashSet<Integer>();
        for (var image : images) {
            placement.pool().validateWrite(image);
            if (!seen.add(image.identity().wholeIndex())) throw new IllegalArgumentException("Duplicate Solid output identity");
        }
    }
    private void animate(Cursor c) {
        var original = c.state;
        CdObjectAnimation.State out;
        try {
            out = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME), original.unsignedByte(ANIMATION_TIME),
                original.unsignedByte(MAP_FRAME), original.spriteFlags(), original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)),
                new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, 1));
        } catch (UnsupportedOperationException selectedRead) {
            try { animationFailurePrefixes(c); }
            catch (UnsupportedRobotGenerator missing) { missing.initCause(selectedRead); throw missing; }
            throw new IllegalStateException("Shared animation failure did not match a reached read", selectedRead);
        }
        c.write(original.moveByte(ANIMATION, out.animationId()).moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex()).moveByte(ANIMATION_TIME, out.timer()).moveByte(MAP_FRAME, out.mapFrame())
                .moveByte(SPRITE_FLAGS, out.spriteFlags()).moveByte(FLAGS, out.statusFlags()).moveByte(ROUTINE, out.routine())
                .moveByte(ROUTINE_2, out.routine2()));
        c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
    }
    /** Only a declared shared reached-read failure publishes its native prefix; no successful step is replayed. */
    private void animationFailurePrefixes(Cursor c) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION))
            c.write(c.state.moveByte(PREVIOUS_ANIMATION, animation).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0));
        c.write(c.state.addByte(ANIMATION_TIME, -1));
        if (c.state.signedByte(ANIMATION_TIME) >= 0) return;
        int directory = animation * 2;
        if (animation >= assets.visual().animations().size()) throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_READ, c.whole, directory);
        int script = (short) assets.visual().animationTable().unsignedWord(directory);
        c.write(c.state.moveByte(ANIMATION_TIME, animationByte(c, script, UnsupportedBoundary.ANIMATION_SPEED_READ)));
        int index = c.state.unsignedByte(ANIMATION_FRAME);
        int frame = animationByte(c, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ);
        if (frame == 0xFF) {
            c.write(c.state.moveByte(ANIMATION_FRAME, 0)); animationByte(c, script + 1, UnsupportedBoundary.ANIMATION_FRAME_READ);
        } else if (frame == 0xFE) {
            int back = animationByte(c, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ);
            index = (index - back) & 0xFF; c.write(c.state.moveByte(ANIMATION_FRAME, index));
            animationByte(c, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ);
        } else if (frame == 0xFD) animationByte(c, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ);
    }
    private int animationByte(Cursor c, int at, UnsupportedBoundary boundary) {
        if (at < 0 || at >= assets.visual().animationTable().length()) throw unsupported(boundary, c.whole, at);
        return assets.visual().animationTable().unsignedByte(at);
    }
    private void draw(Cursor c) {
        var cut = capture(c); var call = new DrawCall(cut, c.state);
        var expected = CdObjectPool.overlay(cut.pool(), List.of(c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() & 0x7F)));
        observedCall(call, () -> {
            try { drawObject.drawObject(call); assertPlacement(cut, expected); }
            catch (RuntimeException | Error original) {
                suppress(original, () -> { if (!samePlacement(cut, cut.pool())) assertPlacement(cut, expected); });
                throw original;
            }
        });
        c.state = placement.pool().state(c.whole); c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }
    private void despawn(Cursor c) {
        var g = c.globals(); int x = c.state.centreX();
        boolean outside = c.state.signedByte(SPRITE_FLAGS) >= 0
                && (((x & 0xFF80) - ((g.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean deleted = outside || (g.timeZone() & 0x80) != 0;
        if (deleted) {
            int id = c.state.savedFlagsId();
            if (id != 0) {
                int rawPeriod = g.timeZone(), period = rawPeriod & 0x7F;
                if ((rawPeriod & 0x80) != 0) { int adjusted = (period - (byte) g.timeWarpDirection()) & 0xFFFF;
                    period = (short) adjusted < 0 ? 0 : adjusted < 3 ? adjusted : 2; }
                int index = 2 + (short) ((3 * id + period) & 0xFFFF);
                if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES)
                    throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c.whole, index);
                int canonicalId = (index - 2) / 3, canonicalPeriod = (index - 2) % 3;
                int before = placement.savedFlag(canonicalId, canonicalPeriod);
                nativeSavedClear(c, canonicalId, canonicalPeriod, index, before);
                c.effects.add(new SavedFlagChanged(index, before, placement.savedFlag(canonicalId, canonicalPeriod)));
            }
            nativeDelete(c); c.state = placement.pool().state(c.whole);
        }
        c.effects.add(new DespawnChecked(deleted)); c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
    }
    private void nativeStore(Cursor c, CdObjectState output, StoreKind kind) {
        if (observer == null) { placement.pool().write(output); return; }
        var call = new Publication(nextOrdinal(), kind, capture(c), c.state,
                placement.pool().state(output.identity().wholeIndex()), output, -1, -1, -1);
        observedPublication(call, () -> placement.pool().write(output));
    }
    private void nativeSavedClear(Cursor c, int savedId, int period, int index, int before) {
        if (observer == null) {
            placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED); return;
        }
        var call = new Publication(nextOrdinal(), StoreKind.SAVED_FLAG_CLEAR, capture(c), c.state,
                null, null, index, before, before & 0x7F);
        observedPublication(call, () -> placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED));
    }
    private void nativeDelete(Cursor c) {
        if (observer == null) { placement.pool().deleteObject(c.whole); return; }
        var call = new Publication(nextOrdinal(), StoreKind.DELETE, capture(c), c.state, c.state, null, -1, -1, -1);
        observedPublication(call, () -> placement.pool().deleteObject(c.whole));
    }
    private long nextOrdinal() {
        if (pendingOperation != null) throw new IllegalStateException("Nested Robot Generator producer operation");
        return ++operationOrdinal;
    }
    private void observedPublication(Publication call, Runnable nativeStore) {
        pendingOperation = call; pendingOrdinal = call.ordinal();
        try {
            readOnlyCallback(() -> observer.beforePublication(call)); readOnlyCallback(() -> receiptGuard.prepared(call));
            nativeStore.run();
            readOnlyCallback(() -> observer.afterPublication(call)); readOnlyCallback(() -> receiptGuard.completed(call));
        } finally { pendingOperation = null; pendingOrdinal = 0; }
    }
    private void observedCall(Object call, Runnable nativeCall) {
        if (observer == null) { nativeCall.run(); return; }
        long ordinal = nextOrdinal(); pendingOperation = call; pendingOrdinal = ordinal;
        try {
            readOnlyCallback(() -> observer.beforeCall(call)); readOnlyCallback(() -> receiptGuard.prepared(call));
            nativeCall.run();
            readOnlyCallback(() -> observer.afterCall(call)); readOnlyCallback(() -> receiptGuard.completed(call));
        } finally { pendingOperation = null; pendingOrdinal = 0; }
    }
    /** Observer/receipt callbacks preserve every placement byte on both returned and throwing paths. */
    private void readOnlyCallback(Runnable callback) {
        var cut = placement.capture();
        try { callback.run(); assertPlacement(cut, cut.pool()); }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(cut, cut.pool())); throw original; }
    }

    private CdObjectPlacementRuntime.Snapshot capture(Cursor c) {
        var cut = placement.capture(); requireSource(cut, c.state); assertPlacement(cut, cut.pool()); return cut;
    }
    private void readOnly(Cursor c, CdObjectPlacementRuntime.Snapshot cut, Runnable callback) {
        assertPlacement(cut, cut.pool());
        try { callback.run(); assertPlacement(cut, cut.pool()); }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(cut, cut.pool())); throw original; }
    }
    private boolean samePlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPool.Snapshot expectedPool) {
        var now = placement.capture();
        return now.status().equals(cut.status()) && Arrays.equals(now.savedFlags().copy(), cut.savedFlags().copy())
                && now.pool().slots().equals(expectedPool.slots());
    }
    private void assertPlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPool.Snapshot expectedPool) {
        placement.validateRestore(cut); placement.pool().validateRestore(expectedPool);
        if (!samePlacement(cut, expectedPool)) throw new IllegalStateException("Robot Generator callback changed its actual full placement cut");
    }
    private static void requireSource(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if (!cut.pool().slots().get(source.identity().dynamicIndex()).equals(source))
            throw new IllegalArgumentException("Robot Generator source is not this full placement capture's actual image");
    }
    private static void suppress(Throwable original, Runnable verification) {
        try { verification.run(); } catch (RuntimeException | Error failed) { if (failed != original) original.addSuppressed(failed); }
    }
    private static CdSolidObject.Entry entryFor(Site site) {
        return site == Site.HIT_GET_OFF ? CdSolidObject.Entry.GET_OFF_OBJECT : CdSolidObject.Entry.SOLID_OBJECT;
    }
    private static UnsupportedRobotGenerator unsupported(UnsupportedBoundary boundary, int whole, long value) {
        return new UnsupportedRobotGenerator(boundary, whole, value);
    }
    private final class Cursor {
        final int whole; final Reads sourceGlobals; final Inputs inputs; final List<Effect> effects = new ArrayList<>();
        CdObjectState state; CdSolidObject.Player player; CdSolidObject.Globals solidGlobals;
        Cursor(CdObjectState state, Reads globals, Inputs inputs) {
            this.state = state; whole = state.identity().wholeIndex(); sourceGlobals = globals; this.inputs = inputs;
        }
        Reads globals() {
            var g=Objects.requireNonNull(sourceGlobals,"reached native globals");
            return new Reads(){public int goodFuture(){return read(g::goodFuture,false);}public int timeZone(){return read(g::timeZone,false);}
                public int act(){return read(g::act,false);}public int timeWarpDirection(){return read(g::timeWarpDirection,false);}
                public int cameraXWord(){return read(g::cameraXWord,true);}};
        }
        int read(java.util.function.IntSupplier getter,boolean word){
            var cut=capture(this);try{int v=getter.getAsInt();if(word)wordValue(v);else byteValue(v);assertPlacement(cut,cut.pool());return v;}
            catch(RuntimeException|Error original){suppress(original,()->assertPlacement(cut,cut.pool()));throw original;}
        }
        void prepareInputs() {
            if (player == null) { var in = Objects.requireNonNull(inputs, "reached native Solid inputs");
                player = in.player(); solidGlobals = in.globals(); }
        }
        void write(CdObjectState next) { nativeStore(this, next, StoreKind.SOURCE_STORE); state = next; }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
    }
    private static void byteValue(int value) { if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected native unsigned BYTE"); }
    private static void wordValue(int value) { if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected native unsigned WORD"); }
}
