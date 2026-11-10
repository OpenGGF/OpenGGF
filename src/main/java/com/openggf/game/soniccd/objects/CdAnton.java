package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR11bObjectData;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * One selected ID $3F Anton dispatch, never a frame, enemy scanner or pool walk.
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * src/Level/Palmtree Panic/Objects/Anton/Main.asm:7-103;
 * Object Time Check.asm:12-44; Object Tile ID Set.asm:7-16;
 * Collision Check.asm:157-179; Collision Floor.asm:685-856;
 * Object Animate.asm:14-95; Object Spawner.asm:388-446;
 * Object Functions.asm:137-196,228-237. These Anton branches have no FixBugs alternative.
 *
 * <p>Real slot writes precede synchronous primary-angle callbacks. Each callback
 * carries a freshly captured actual same-pool/source cut and the immediately
 * preceding expected byte. The body checks the full pool before and after the
 * callback without adopting or overwriting changed images. Those values cannot
 * authenticate the callback or prove a same-value store occurred: Root owns the
 * actual phase/full-owner/source/Frame expected-byte check and exactly one store.
 * The invocation-local byte and returned effects are completed-work metadata,
 * never another global owner, capture partition, deferred command or effect replay.
 *
 * <p>The bound terrain is immutable. Dirty input can make native Main reverse
 * forever. Repeating the same full Main image and local angle is an explicit
 * HOST non-return support stop, retaining the completed prefix; it never returns
 * a successful Result, DrawObject or despawn tail and has no iteration budget.
 * Callback, terrain, animation and draw failures similarly escape immediately.
 * Root owns failed-loop closure, fresh capture/replay, later visits and integration.
 * No art decode, sound, child, player read, debug/time-stop gate or region claim.
 * All Java in this external source packet is authored UNRUN, including compilation.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdAnton {
    public static final int OBJECT_ID = 0x3F;
    public static final int TILE_SELECTOR = 2;

    public enum NativeRoutine { INIT, PLACE, MAIN }
    public enum NativeCall {
        DESTROY_ON_GOOD_FUTURE, SET_OBJECT_TILE_ID, OBJ_GET_FLOOR_DIST,
        ANIMATE_OBJECT, DRAW_OBJECT, CHECK_OBJ_DESPAWN
    }
    public sealed interface Effect permits RoutineEntered, CallReturned, FloorReturned,
            PrimaryAngleCommitted, SavedFlagChanged, Deleted, ConvertedToExplosion { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record FloorReturned(CdObjectCollisionCheck.FloorDistance result) implements Effect {
        public FloorReturned { Objects.requireNonNull(result, "result"); }
    }
    /** A synchronous callback already returned; this record never requests another store. */
    public record PrimaryAngleCommitted(PrimaryAngleCall call) implements Effect {
        public PrimaryAngleCommitted { Objects.requireNonNull(call, "call"); }
    }
    public record SavedFlagChanged(int byteIndex, int previousValue, int value) implements Effect { }
    public record Deleted() implements Effect { }
    public record ConvertedToExplosion() implements Effect { }

    /**
     * Constructed values are format inputs only. Production uses the actual retained
     * authenticated object owner; neither mappings nor tile words promise residency.
     * Four frames/two scripts and the twenty-two-tile bound include damaged head tiles 18..21.
     */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets visual, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(objectTileIds, "objectTileIds"); Objects.requireNonNull(visual, "visual");
            if (objectTileIds.length() != 74 || visual.mappingFrames().size() != 4 || visual.animations().size() != 2) {
                throw new IllegalArgumentException("Anton requires 74 tile-table bytes, four frames and two scripts");
            }
            for (var frame : visual.mappingFrames()) {
                for (var piece : frame.pieces()) {
                    if (piece.tileOffset() > 22 - piece.tileCount()) {
                        throw new IllegalArgumentException("Anton mapping exceeds its twenty-two-tile source");
                    }
                }
            }
        }
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            return new Assets(owner.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(), owner.anton(),
                    CdR11aObjectAssets.Table.ANTON_MAPPINGS.range().nativeAddress());
        }

        /** Original-USA Act2 data relation only; no current patterns, body or World admission. */
        public static Assets fromAct2Assets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            var identity = Objects.requireNonNull(owner.identity(), "identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12A
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12A.stage()))
                throw new IllegalArgumentException("Anton Act2 assets require the same authenticated original USA R12A source");
            return new Assets(owner.view(CdR12aProfile.Asset.OBJECT_TILE_IDS), owner.anton(),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_ANTON).nativeAddress());
        }
    }

    /** Raw native widths. The timeZone byte is not normalized to a period at entry. */
    public record Globals(int goodFuture, int timeZone, int timeWarpDirection, int cameraXWord, int primaryAngle) {
        public Globals {
            byteValue(goodFuture); byteValue(timeZone); byteValue(timeWarpDirection); wordValue(cameraXWord); byteValue(primaryAngle);
        }
    }
    /**
     * Plain values, never a publication token. Only the actual adapter authenticates
     * phase/current full-pool/source and expected Frame byte before publishing once.
     * An independently constructed/derived same-owner Snapshot confers no authority.
     */
    public record PrimaryAngleCall(CdObjectPool.Snapshot callCut, CdObjectState expectedSource,
                                   int expectedPrimaryAngle, int value) {
        public PrimaryAngleCall {
            Objects.requireNonNull(callCut, "callCut"); Objects.requireNonNull(expectedSource, "expectedSource");
            byteValue(expectedPrimaryAngle); byteValue(value);
            if (!callCut.slots().get(expectedSource.identity().dynamicIndex()).equals(expectedSource)) {
                throw new IllegalArgumentException("Anton angle source is not the supplied cut's actual slot image");
            }
        }
    }
    @FunctionalInterface public interface PrimaryAngleCommit { void commit(PrimaryAngleCall call); }
    @FunctionalInterface public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity identity);
    }
    public record Result(CdObjectState state, int primaryAngle, List<Effect> effects) {
        public Result { Objects.requireNonNull(state, "state"); byteValue(primaryAngle); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, TILE_ENTRY, ANIMATION_DIRECTORY_ENTRY, ANIMATION_SPEED_ADDRESS,
        ANIMATION_FRAME_ADDRESS, ANIMATION_OPERAND_ADDRESS, SAVED_FLAGS_ADDRESS, NATIVE_NON_RETURNING_CYCLE
    }
    /** Finite supported-source boundary; a host cycle stop is not a native call completion. */
    public static final class UnsupportedAnton extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedAnton(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super((boundary == UnsupportedBoundary.NATIVE_NON_RETURNING_CYCLE ? "host non-return support stop: " : "")
                    + boundary + " at Anton whole slot " + wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime placement;
    private final NativeAssets assets;
    private final CdPlayerTerrain terrain;
    private final PrimaryAngleCommit angleCommit;
    private final DrawObjectCall drawObject;
    private boolean running;
    private CdEnemyPrimitiveScope originalPrimitives;
    private transient PrimaryAngleCall pendingPrimaryAngleCall;

    /** True only for the cached body's currently suspended synchronous floor-store call. */
    public boolean isCurrentPrimaryAngleCall(PrimaryAngleCall call) {
        return running && call != null && pendingPrimaryAngleCall == call;
    }

    public CdAnton(CdObjectPlacementRuntime placement, Assets assets, CdPlayerTerrain terrain,
                   PrimaryAngleCommit angleCommit, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement, "placement"); this.assets = presentAssets(Objects.requireNonNull(assets, "assets"));
        this.terrain = Objects.requireNonNull(terrain, "terrain"); this.angleCommit = Objects.requireNonNull(angleCommit, "angleCommit");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    @FunctionalInterface private interface TileOperand { int read(int secondary); }
    private record NativeAssets(TileOperand tileOperand, CdObjectVisualAssets visual, int mappingsAddress) { }
    private static NativeAssets presentAssets(Assets assets) {
        return new NativeAssets(secondary -> CdR11aObjectAssets.decodeTileWord(
                assets.objectTileIds().copy(), TILE_SELECTOR, secondary), assets.visual(), assets.mappingsAddress());
    }

    /**
     * Actual original-USA B tables; ObjAnton_Init/Animate (Anton/Main.asm:27-34,85,98-103).
     * The unchanged kernel reaches B72 selector2 only after its Init header. Actual raw subtype2=1
     * aliases the next genuine WORD; it is not replaced with Present's second Anton tile entry.
     * Assets3 and Present's A74 selector policy remain unchanged. No new cross-owner port is added.
     */
    public static CdAnton fromPastAssets(CdObjectPlacementRuntime placement, CdR11bAssets owner,
                                         CdPlayerTerrain terrain, PrimaryAngleCommit angleCommit, DrawObjectCall drawObject) {
        return new CdAnton(placement, pastAssets(owner), terrain, angleCommit, drawObject);
    }
    private static NativeAssets pastAssets(CdR11bAssets owner) {
        Objects.requireNonNull(owner, "owner");
        var tiles = owner.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes();
        if (tiles.length() != 72) throw new IllegalArgumentException("Past Anton requires the actual72-byte tile directory");
        var map = owner.view(CdR11bAssets.Asset.MAP_SPR_ANTON);
        var animation = owner.view(CdR11bAssets.Asset.ANI_ANTON);
        var visual = CdObjectVisualAssets.decode(map.bytes().copy(), 4, animation.bytes().copy(), 2, 22);
        return new NativeAssets(secondary -> readPastTileWord(tiles, secondary), visual, map.range().nativeAddress());
    }
    /** Package format seam only; no fabricated authenticated owner is needed to test a reached WORD read. */
    static int readPastTileWord(CdAssetBytes bytes, int secondary) {
        Objects.requireNonNull(bytes, "tile bytes");
        if ((secondary & ~0xFF) != 0) throw new IllegalArgumentException("secondary must be an unsigned native byte");
        int target = (short) bytes.unsignedWord(TILE_SELECTOR * 2);
        return bytes.unsignedWord(target + secondary * 2);
    }
    private CdAnton(CdObjectPlacementRuntime placement, NativeAssets assets, CdPlayerTerrain terrain,
                    PrimaryAngleCommit angleCommit, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement, "placement"); this.assets = Objects.requireNonNull(assets, "assets");
        this.terrain = Objects.requireNonNull(terrain, "terrain"); this.angleCommit = Objects.requireNonNull(angleCommit, "angleCommit");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }


    /**
     * Genuine original-USA R12B relation only. Public Assets3 retains its A74 contract.
     * N1ecc5f3: Anton/Main.asm:24-63; Object Tile ID Set.asm:7-17.
     * The actual B72 selector operand stays lazy; subtype2=1 reads the genuine next WORD.
     * Detached source binding executes no body/callback/art/current or World work.
     */
    public static CdAnton fromAct2PastAssets(CdObjectPlacementRuntime placement, CdR12bTerrainAssets owner,
                                             PrimaryAngleCommit angleCommit, DrawObjectCall drawObject) {
        Objects.requireNonNull(placement, "actual placement"); Objects.requireNonNull(owner, "actual R12B source");
        if (!placement.sameAct2PastSource(owner)) throw new IllegalArgumentException("Anton requires the same retained R12B placement/source");
        var objects = owner.objects();
        var tiles = objects.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS);
        if (tiles.length() != 72) throw new IllegalArgumentException("Anton R12B requires its actual72-byte tile directory");
        var bound = new NativeAssets(secondary -> readPastTileWord(tiles, secondary), objects.anton(),
                CdR12bObjectAssets.View.MAP_SPR_ANTON.range().nativeAddress());
        return new CdAnton(placement, bound, CdPlayerTerrain.fromStageTerrain(owner.terrain()), angleCommit, drawObject);
    }

    public static CdAnton fromOriginalPresentAssets(CdObjectPlacementRuntime placement,
            com.openggf.game.soniccd.level.CdR11aAssets owner,CdR11aObjectAssets objects,PrimaryAngleCommit angle,
            DrawObjectCall draw,CdEnemyPrimitiveScope.Observer observer){
        CdOriginalPresentObjectSource.require(placement,owner);
        if(!Objects.requireNonNull(objects,"actual original object source").identity().equals(owner.identity()))throw new IllegalArgumentException("Different original enemy object source");
        var o=objects;var tiles=o.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes();
        var data=new NativeAssets(secondary->tiles.unsignedWord((short)tiles.unsignedWord(TILE_SELECTOR*2)+secondary*2),o.anton(),CdR11aObjectAssets.Table.ANTON_MAPPINGS.range().nativeAddress());
        var body=new CdAnton(placement,data,new CdPlayerTerrain(owner.terrain()),angle,draw);
        body.originalPrimitives=new CdEnemyPrimitiveScope(placement,observer);return body;
    }
    /** One cached original B binding over its actual retained Reader/placement pair.
     * Native ObjAnton is the same included body for A/B; the B72 tile operand and
     * distinct animation/mapping/terrain products are supplied here without an A owner.
     * No slot/current art/service/World authority is created by these DATA relations. */
    public static CdAnton fromOriginalStandardPastSource(CdObjectPlacementRuntime placement,
            CdR11bObjectData owner,PrimaryAngleCommit angle,DrawObjectCall draw,
            CdEnemyPrimitiveScope.Observer observer)throws java.io.IOException{
        Objects.requireNonNull(owner,"actual retained original B object source").requirePlacement(placement);
        var tiles=owner.view(CdR11bObjectData.Table.OBJECT_TILE_IDS).bytes();
        if(tiles.length()!=72)throw new IllegalArgumentException("Original Past Anton requires its actual72-byte tile directory");
        var data=new NativeAssets(secondary->readPastTileWord(tiles,secondary),owner.anton(),
                CdR11bObjectData.Table.MAP_SPR_ANTON.nativeAddress());
        var body=new CdAnton(placement,data,CdPlayerTerrain.fromStageTerrain(owner.terrain()),angle,draw);
        body.originalPrimitives=new CdEnemyPrimitiveScope(placement,observer);return body;
    }
    public boolean isCurrentOriginalPrimitive(CdEnemyPrimitiveScope.Call c){return running&&originalPrimitives!=null&&originalPrimitives.isCurrent(c);}
    private void originalWrite(CdObjectState s){if(originalPrimitives==null)placement.pool().write(s);else originalPrimitives.write(s);}

    /** ObjAnton (:7-15): good-future check FIRST, then raw byte-offset dispatch and wrapper. */
    public Result run(int wholeIndex, Globals globals) {
        if (running) throw new IllegalStateException("Anton body cannot reenter its synchronous dispatch");
        running = true;
        try {
            if(originalPrimitives!=null)originalPrimitives.enter(wholeIndex,OBJECT_ID);
            Objects.requireNonNull(globals, "globals");
            var pool = placement.pool(); var state = pool.state(wholeIndex);
            if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Selected slot is not Anton ID $3F");
            var c = new Cursor(pool, state, globals.primaryAngle());
            if (destroyOnGoodFuture(c, globals)) {if(originalPrimitives!=null)originalPrimitives.returned();return c.finish();}
            switch (c.state.routine()) {
                case 0 -> { init(c); place(c); }
                case 2 -> place(c);
                case 4 -> main(c);
                default -> throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c, c.state.routine());
            }
            draw(c);
            despawn(c, globals);
            if(originalPrimitives!=null)originalPrimitives.returned();return c.finish();
        } finally {
            if(originalPrimitives!=null)originalPrimitives.close();running = false;
        }
    }

    /** DestroyOnGoodFuture (:12-44): full DeleteObject, integer-position restore, skip caller. */
    private boolean destroyOnGoodFuture(Cursor c, Globals globals) {
        if (globals.goodFuture() == 0 || (globals.timeZone() == 1 && c.state.subtype() == 0)) {
            c.effects.add(new CallReturned(NativeCall.DESTROY_ON_GOOD_FUTURE));
            return false;
        }
        int x = c.state.centreX(), y = c.state.centreY();
        delete(c);
        c.state = c.state.moveWord(X, x).moveWord(Y, y).moveByte(ID, 0x18); c.publish();
        // DeleteObject cleared sprite flags: the subsequent TST.B is nonnegative,
        // so FM_EXPLODE is unreachable, even when the entry image had bit 7 set.
        c.effects.add(new ConvertedToExplosion());
        return true; // Native ADDQ.L #4,sp skips the rest of this Anton wrapper.
    }

    /** ObjAnton_Init (:24-49); no routine increment, map frame/reset or velocity write. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4).moveByte(PRIORITY, 4)
                .moveLong(MAPPINGS, assets.mappingsAddress()).moveByte(X_RADIUS, 0x18).moveByte(WIDTH, 0x18)
                .moveByte(Y_RADIUS, 0x13).moveByte(COLLISION_TYPE, 0x29).moveWord(ANTON_ANCHOR_X, c.state.centreX());
        c.publish();
        int secondary = c.state.subtype2();
        if (originalPrimitives==null&&secondary > 1) throw unsupported(UnsupportedBoundary.TILE_ENTRY, c, secondary);
        int tile = assets.tileOperand().read(secondary);
        c.state = c.state.moveWord(TILE, tile); c.publish();
        c.effects.add(new CallReturned(NativeCall.SET_OBJECT_TILE_ID));
        boolean damaged = c.state.subtype() != 0;
        c.state = c.state.moveLong(ANTON_X_DELTA, damaged ? 0xFFFF8000 : 0xFFFF0000).moveByte(ANIMATION, damaged ? 1 : 0);
        c.publish();
    }

    /** ObjAnton_Place (:54-63): whole-long Y+1, floor, negative-only routine byte +2. */
    private void place(Cursor c) {
        c.enter(NativeRoutine.PLACE);
        c.state = c.state.addLong(Y_POSITION, 0x10000); c.publish();
        var floor = floor(c);
        if (floor.distance() < 0) { c.state = c.state.addByte(ROUTINE, 2); c.publish(); }
    }

    /** ObjAnton_Main/.TurnAround (:68-94): same-dispatch native back edge, not a reversal cap. */
    private void main(Cursor c) {
        Set<MainCut> seen = new HashSet<>();
        while (true) {
            if (!seen.add(new MainCut(c.state, c.primaryAngle))) {
                throw unsupported(UnsupportedBoundary.NATIVE_NON_RETURNING_CYCLE, c, c.state.routine());
            }
            c.enter(NativeRoutine.MAIN);
            c.state = c.state.addLong(X_POSITION, c.state.longBits(ANTON_X_DELTA)); c.publish();
            int distance = (short) (c.state.centreX() - c.state.word(ANTON_ANCHOR_X));
            if (distance < 0) distance = (short) -distance; // NEG.W $8000 stays negative and passes BGE.
            if (distance >= 0x80) { turn(c); continue; }
            var floor = floor(c);
            if (floor.distance() < -7 || floor.distance() > 7) { turn(c); continue; }
            c.state = c.state.addWord(Y, floor.distance()); c.publish(); // WORD only: retain fraction.
            animate(c);
            return;
        }
    }
    private record MainCut(CdObjectState state, int primaryAngle) { }
    private void turn(Cursor c) {
        c.state = c.state.moveLong(ANTON_X_DELTA, -c.state.longBits(ANTON_X_DELTA))
                .moveByte(SPRITE_FLAGS, c.state.spriteFlags() ^ 1).moveByte(FLAGS, c.state.flags() ^ 1);
        c.publish();
    }

    /** The accepted helper3 directly forwards every native store, including its pre-zero. */
    private CdObjectCollisionCheck.FloorDistance floor(Cursor c) {
        var result = CdObjectCollisionCheck.objGetFloorDist(terrain, c.state, value -> primaryAngle(c, value));
        c.effects.add(new FloorReturned(result)); c.effects.add(new CallReturned(NativeCall.OBJ_GET_FLOOR_DIST));
        return result;
    }
    private void primaryAngle(Cursor c, int value) {
        var cut = c.pool.capture();
        var call = new PrimaryAngleCall(cut, c.state, c.primaryAngle, value);
        requireCurrentCut(c, call);
        pendingPrimaryAngleCall = call;
        try { if(originalPrimitives==null)angleCommit.commit(call);else originalPrimitives.invoke(CdEnemyPrimitiveScope.Kind.ANGLE,()->{angleCommit.commit(call);return null;}); }
        finally { pendingPrimaryAngleCall = null; }
        requireCurrentCut(c, call); // Never adopt/overwrite any unexpected callback pool image.
        c.primaryAngle = value; // Invocation-local expected byte for the NEXT reached store only.
        c.effects.add(new PrimaryAngleCommitted(call));
    }
    private static void requireCurrentCut(Cursor c, PrimaryAngleCall call) {
        c.pool.validateRestore(call.callCut());
        if (!c.pool.state(c.wholeIndex).equals(call.expectedSource())
                || !c.pool.capture().slots().equals(call.callCut().slots())) {
            throw new IllegalStateException("Anton angle callback changed its actual full pool or source cut");
        }
    }

    /** Shared animator owns successful transitions; original call image is supplied exactly once. */
    private void animate(Cursor c) {
        if(originalPrimitives!=null){originalPrimitives.animate(new CdObjectAnimation.AnimationTable(assets.visual().animationTable(),0,assets.visual().animations().size()));c.state=c.pool.state(c.wholeIndex);c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));return;}
        var original = c.state;
        animationReadPrefixes(c);
        var result = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME),
                original.unsignedByte(ANIMATION_TIME), original.unsignedByte(MAP_FRAME), original.spriteFlags(),
                original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)),
                new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, assets.visual().animations().size()));
        c.state = original.moveByte(ANIMATION, result.animationId()).moveByte(PREVIOUS_ANIMATION, result.previousId())
                .moveByte(ANIMATION_FRAME, result.scriptIndex()).moveByte(ANIMATION_TIME, result.timer())
                .moveByte(MAP_FRAME, result.mapFrame()).moveByte(SPRITE_FLAGS, result.spriteFlags())
                .moveByte(FLAGS, result.statusFlags()).moveByte(ROUTINE, result.routine()).moveByte(ROUTINE_2, result.routine2());
        c.publish(); c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
    }
    /**
     * NEWJ Checkpoint/TimePost reached-read prefix pattern, Object Animate.asm:20-33,59-76.
     * Publish reset/SUBQ/speed/index only when reached, before a finite read can fail.
     * No fallback script, prevalidation of a held timer, new animation algorithm or owner.
     */
    private void animationReadPrefixes(Cursor c) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION)) {
            c.state = c.state.moveByte(PREVIOUS_ANIMATION, animation).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
        }
        c.state = c.state.addByte(ANIMATION_TIME, -1); c.publish();
        if (c.state.signedByte(ANIMATION_TIME) >= 0) return;
        int directoryByte = 2 * animation;
        if (animation >= assets.visual().animations().size()) {
            throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY, c, directoryByte);
        }
        int script = (short) assets.visual().animationTable().unsignedWord(directoryByte);
        int speed = animationByte(c, UnsupportedBoundary.ANIMATION_SPEED_ADDRESS, script);
        c.state = c.state.moveByte(ANIMATION_TIME, speed); c.publish();
        int index = c.state.unsignedByte(ANIMATION_FRAME);
        int frame = animationByte(c, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        if (frame == 0xFF) {
            c.state = c.state.moveByte(ANIMATION_FRAME, 0); c.publish();
            animationByte(c, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1);
        } else if (frame == 0xFE) {
            int back = animationByte(c, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
            index = (index - back) & 0xFF;
            c.state = c.state.moveByte(ANIMATION_FRAME, index); c.publish();
            animationByte(c, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        } else if (frame == 0xFD) {
            animationByte(c, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
        }
    }
    private int animationByte(Cursor c, UnsupportedBoundary boundary, int address) {
        var bytes = assets.visual().animationTable();
        if (address < 0 || address >= bytes.length()) throw unsupported(boundary, c, address);
        return bytes.unsignedByte(address);
    }

    /** Wrapper DrawObject (:13): actual callback queue, then live reload before anchor load. */
    private void draw(Cursor c) {
        c.publish();if(originalPrimitives==null)drawObject.drawObject(c.pool,c.state.identity());else originalPrimitives.draw(()->drawObject.drawObject(c.pool,c.state.identity()));
        c.state = c.pool.state(c.wholeIndex); c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }
    /** Wrapper :14-15 / CheckObjDespawn2: the freshly loaded ANCHOR, never moved X. */
    private void despawn(Cursor c, Globals globals) {
        int anchor = c.state.word(ANTON_ANCHOR_X);
        boolean outside = (c.state.spriteFlags() & 0x80) == 0
                && (((anchor & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        if (outside || (globals.timeZone() & 0x80) != 0) {
            if (c.state.savedFlagsId() != 0) {
                var at = savedAddress(c, globals);
                int previous = placement.savedFlag(at.id(), at.period());
                if(originalPrimitives==null)placement.bclrSavedFlag(at.id(),at.period(),CdObjectPlacementRuntime.SavedBit.LOADED);else originalPrimitives.saved(at.id(),at.period());
                c.effects.add(new SavedFlagChanged(at.byteIndex(), previous, placement.savedFlag(at.id(), at.period())));
            }
            delete(c);
        }
        c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
    }
    private record SavedAddress(int id, int period, int byteIndex) { }
    /** Keep raw linear address aliases; canonical API arguments are derived ONLY after real bounds. */
    private static SavedAddress savedAddress(Cursor c, Globals globals) {
        int period = globals.timeZone() & 0x7F;
        if ((globals.timeZone() & 0x80) != 0) {
            int adjusted = (period - (byte) globals.timeWarpDirection()) & 0xFFFF;
            period = (short) adjusted < 0 ? 0 : adjusted >= 3 ? 2 : adjusted;
        }
        int offset = (3 * c.state.savedFlagsId() + period) & 0xFFFF;
        int index = 2 + (short) offset;
        if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
            throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c, index);
        }
        return new SavedAddress((index - 2) / 3, (index - 2) % 3, index);
    }
    private void delete(Cursor c) {
        if(originalPrimitives==null)c.pool.deleteObject(c.wholeIndex);else originalPrimitives.delete(c.wholeIndex);c.state = c.pool.state(c.wholeIndex);c.effects.add(new Deleted());
    }
    private static UnsupportedAnton unsupported(UnsupportedBoundary boundary, Cursor c, int value) {
        return new UnsupportedAnton(boundary, c.wholeIndex, value);
    }
    private final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        int primaryAngle;
        Cursor(CdObjectPool pool, CdObjectState state, int primaryAngle) {
            this.pool = pool; this.state = state; this.primaryAngle = primaryAngle; wholeIndex = state.identity().wholeIndex();
        }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        void publish() { originalWrite(state); }
        Result finish() { return new Result(state, primaryAngle, effects); }
    }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
