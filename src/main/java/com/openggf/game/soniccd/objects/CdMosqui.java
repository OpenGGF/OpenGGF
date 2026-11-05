package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjMosqui ($3D): Level/Palmtree Panic/Objects/Mosqui/Main.asm:7-151 at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, executed over the live dynamic pool and saved bank of
 * one CdObjectPlacementRuntime. One {@link #run} is one RunObjects dispatch of the slot:
 * DestroyOnGoodFuture, the routine selected by oRoutine, DrawObject through the supplied port,
 * then CheckObjDespawn2 against the original X anchor (oVar2A). The named Past path commits pool
 * and scratch/FM ports at reached native cuts; the legacy A74 path retains its deferred routine
 * adoption. Returned effects describe completed work on Past and must not be replayed. The private
 * running guard supplies no World/Frame authority, retained capture, player, art or sound owner.
 *
 * <p>The good-future Explosion conversion stops before writes. Unknown routines stop before writes;
 * Past selected-read failures retain the completed native prefix. The native infinite reversal loop
 * of a corrupt slot stops instead of hanging. Nothing is keyed on a fixture, frame or route coordinate.</p>
 */
public final class CdMosqui {
    public static final int OBJECT_ID = 0x3D;
    /** ObjMosqui_Init colType; ObjColSizes entry 43 is ($C,$C) for the player touch owner. */
    public static final int NATIVE_COLLISION_TYPE = 0x2B;

    public enum NativeCall { DESTROY_ON_GOOD_FUTURE, INIT, MAIN, ANIMATE, DIVE, WAIT, DRAW_OBJECT, CHECK_OBJ_DESPAWN2 }
    /** ObjMosqui_Dive requests FM_A7 (Sound Drivers/FM/_Sound IDs.inc:33) through PlayFMSound. */
    public enum Sound { FM_A7 }
    /** ObjGetFloorDist zeroes and then FindLevelFloor may write the primaryAngle byte. */
    public enum GlobalField { PRIMARY_ANGLE }
    /** Which source branch entered DespawnObject (Object Spawner.asm:403-438). */
    public enum DespawnEntry { WAIT_OFFSCREEN, ANCHOR_OFFSCREEN, TIME_WARP }

    /**
     * Ordered return metadata. Pool/bank effects describe completed writes and must not be replayed.
     * The legacy A74 caller adopts sound and PRIMARY_ANGLE after return. On the named Past
     * path they describe completed synchronous ports and must not be replayed.
     */
    public sealed interface Effect permits CallReturned, SoundRequest, GlobalWrite, Despawn { }
    /** INIT precedes MAIN in the same dispatch because Init falls through into Main (Mosqui/Main.asm:55-59). */
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record SoundRequest(Sound sound) implements Effect {
        public SoundRequest { Objects.requireNonNull(sound, "sound"); }
    }
    public record GlobalWrite(GlobalField field, int value) implements Effect {
        public GlobalWrite {
            Objects.requireNonNull(field, "field");
            byteValue(value, "global byte");
        }
    }
    /**
     * DespawnObject executed: for a nonzero saved ID the saved bank's loaded bit 7 was cleared at
     * the selected native period (-1 means no bank write), then the slot was fully deleted.
     */
    public record Despawn(DespawnEntry entry, int savedId, int period) implements Effect {
        public Despawn {
            Objects.requireNonNull(entry, "entry");
            byteValue(savedId, "saved ID");
            if (period < -1 || period > 2 || (savedId == 0) != (period == -1)) {
                throw new IllegalArgumentException("Despawn period must be -1 exactly when the saved ID is 0");
            }
        }
    }

    /**
     * Explicit immutable globals at the call cut: goodFuture/timeZone/timeWarpDir bytes, the full
     * debugMode word, the pre-scroll cameraX word and the player slot's integer oX/oY words after
     * the player's own dispatch. The time zone's low seven bits must name a native period.
     */
    public record Globals(int goodFuture, int timeZone, int timeWarpDir, int debugMode,
                          int cameraXWord, int playerXWord, int playerYWord) {
        public Globals {
            byteValue(goodFuture, "goodFuture");
            byteValue(timeZone, "timeZone");
            byteValue(timeWarpDir, "timeWarpDir");
            wordValue(debugMode, "debugMode");
            wordValue(cameraXWord, "cameraX");
            wordValue(playerXWord, "player oX");
            wordValue(playerYWord, "player oY");
            if ((timeZone & 0x7F) > 2) {
                throw new IllegalArgumentException("Native CD period must be 0, 1 or 2");
            }
        }
    }

    /**
     * Pure declared Mosqui data: the 74-byte ObjectTileIDs view decoded by the existing tile owner,
     * both finite visual tables, and the four native pointer values Init stores in oMap/oVar30.
     * Production obtains it from the authenticated owner through {@link #fromObjectAssets}; a
     * constructed record never authenticates media and supplies no fallback bytes.
     */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets normal, CdObjectVisualAssets damaged,
                         int normalMappingsAddress, int normalScriptAddress,
                         int damagedMappingsAddress, int damagedScriptAddress) {
        public Assets {
            Objects.requireNonNull(objectTileIds, "objectTileIds");
            Objects.requireNonNull(normal, "normal");
            Objects.requireNonNull(damaged, "damaged");
            if (objectTileIds.length() != 74) {
                throw new IllegalArgumentException("ObjectTileIDs extent");
            }
            if (normal.animations().size() != 2 || damaged.animations().size() != 2) {
                throw new IllegalArgumentException("Mosqui script tables declare exactly two animations");
            }
            if (normalScriptAddress == damagedScriptAddress || normalMappingsAddress == damagedMappingsAddress) {
                throw new IllegalArgumentException("Normal and damaged Mosqui pointers must be distinct");
            }
        }

        /** Retains actual original USA R12A source values only; grants no producer or World authority. */
        public static Assets fromAct2Assets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            var identity = Objects.requireNonNull(owner.identity(), "identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12A
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12A.stage()))
                throw new IllegalArgumentException("Mosqui Act2 assets require the authenticated original USA R12A source");
            return new Assets(owner.view(CdR12aProfile.Asset.OBJECT_TILE_IDS), owner.normalMosqui(), owner.damagedMosqui(),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_MOSQUI_NORMAL).nativeAddress(),
                    owner.profile().range(CdR12aProfile.Asset.ANI_MOSQUI_NORMAL).nativeAddress(),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_MOSQUI_DAMAGED).nativeAddress(),
                    owner.profile().range(CdR12aProfile.Asset.ANI_MOSQUI_DAMAGED).nativeAddress());
        }

        /** Original Word RAM addresses of the authenticated views; these are the stored native longs. */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(assets.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(),
                    assets.normalMosqui(), assets.damagedMosqui(),
                    address(assets, CdR11aObjectAssets.Table.MOSQUI_NORMAL_MAPPINGS),
                    address(assets, CdR11aObjectAssets.Table.MOSQUI_NORMAL_ANIMATIONS),
                    address(assets, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_MAPPINGS),
                    address(assets, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_ANIMATIONS));
        }

        private static int address(CdR11aObjectAssets assets, CdR11aObjectAssets.Table table) {
            return assets.view(table).range().nativeAddress();
        }
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196) at the ObjMosqui cut (Mosqui/Main.asm:13):
     * after the routine's pool writes and before CheckObjDespawn2. The shared CPU queue implements it
     * over the live pool: clear bit 7 of the live slot first, then cull/capacity, then append only the
     * physical slot reference. It must not test oID: after a Wait despawn the slot is already zeroed,
     * and native still enqueues that zeroed slot in bucket 0 without a camera test. Any bit-7 write
     * it makes is reloaded afterwards; this body never writes back its pre-call image.
     * The caller must keep this synchronous port non-reentrant: it must not dispatch another body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    /** Final live image of the slot after the whole dispatch (fully zero after a despawn). */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Despawn.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        /** DestroyOnGoodFuture .Destroy (Object Time Check.asm:20-41): conversion into the unowned Explosion. */
        GOOD_FUTURE_CONVERSION,
        /** oRoutine outside {0,2,4,6,8} indexes past ObjMosqui_Index (Mosqui/Main.asm:19-23). */
        ROUTINE_DISPATCH,
        /** oVar30 selects neither bound Mosqui script table (Mosqui/Main.asm:85,117). */
        ANIMATION_SCRIPT_POINTER,
        /** .SkipRange would reverse twice in one call: the native loop never terminates. */
        PATROL_REVERSAL_LOOP,
        TILE_ADDRESS, ANIMATION_DIRECTORY_READ, ANIMATION_SPEED_READ, ANIMATION_FRAME_READ, ANIMATION_OPERAND_READ
    }

    /** The pool and bank hold the last committed native prefix; the missing action has not run. */
    public static final class UnsupportedMosqui extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedMosqui(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    /** Read-only source cut; values alone grant no private World invocation or Frame authority. */
    public record PrimaryAngleCall(CdObjectPool.Snapshot actualCut, CdObjectState expectedSource, int value) {
        public PrimaryAngleCall { sourceInCut(actualCut, expectedSource); byteValue(value, "primary angle"); }
    }
    @FunctionalInterface public interface PrimaryAngleCommit { void commit(PrimaryAngleCall call); }
    /** The genuine completed Dive prefix precedes this synchronous FM cut. */
    public record FmCall(CdObjectPool.Snapshot actualCut, CdObjectState expectedSource, Sound sound) {
        public FmCall { sourceInCut(actualCut, expectedSource); Objects.requireNonNull(sound, "sound"); }
    }
    @FunctionalInterface public interface PlayFmSound { void play(FmCall call); }

    @FunctionalInterface private interface TileOperand { int read(int secondary); }
    private record NativeAssets(TileOperand tileOperand, CdObjectVisualAssets normal, CdObjectVisualAssets damaged,
                                int normalMappingsAddress, int normalScriptAddress,
                                int damagedMappingsAddress, int damagedScriptAddress) { }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final CdPlayerTerrain terrain;
    private final DrawObjectCall drawObject;
    private final NativeAssets nativeAssets;
    private final PrimaryAngleCommit angleCommit;
    private final PlayFmSound playFm;
    private final boolean prefixMode;
    private boolean running;
    private PrimaryAngleCall pendingAngle;
    private FmCall pendingFm;

    /** Narrow producer provenance for one actual World dispatch; values alone grant no invocation. */
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
    private DispatchObserver observer;
    private SourceDraw sourceDraw;
    private Publication pendingPublication;
    private DrawCall pendingDraw;
    private long publicationOrdinal;

    /** Identity of the producer's currently pending operation, never equality of public records. */
    public boolean isCurrentCall(Object call) {
        return call != null && running && (call == pendingPublication || call == pendingDraw || call == pendingAngle || call == pendingFm);
    }
    private void observedWrite(CdObjectState output) {
        if (observer == null) { runtime.pool().write(output); return; }
        var call = new Publication(++publicationOrdinal, runtime.capture(),
                runtime.pool().state(output.identity().wholeIndex()), output, -1);
        observePublication(call, () -> runtime.pool().write(output));
    }
    private void observedDelete(CdObjectState source) {
        if (observer == null) { runtime.pool().deleteObject(source.identity().wholeIndex()); return; }
        var call = new Publication(++publicationOrdinal, runtime.capture(), source, null, -1);
        observePublication(call, () -> runtime.pool().deleteObject(source.identity().wholeIndex()));
    }
    private void observedSaved(CdObjectState source, int savedId, int period, int index) {
        if (observer == null) { runtime.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED); return; }
        var call = new Publication(++publicationOrdinal, runtime.capture(), source, source, index);
        observePublication(call, () -> runtime.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED));
    }
    private void observePublication(Publication call, Runnable nativeStore) {
        if (pendingPublication != null) throw new IllegalStateException("Nested family publication");
        pendingPublication = call;
        try { observer.beforePublication(call); nativeStore.run(); observer.afterPublication(call); }
        finally { pendingPublication = null; }
    }
    private void observedDraw(CdObjectPool pool, CdObjectState.Identity identity) {
        if (observer == null) { drawObject.drawObject(pool, identity); return; }
        var call = new DrawCall(runtime.capture(), pool.state(identity.wholeIndex()));
        if (pendingDraw != null) throw new IllegalStateException("Nested family DrawObject");
        pendingDraw = call;
        try { observer.beforeCall(call); sourceDraw.draw(call); observer.afterCall(call); }
        finally { pendingDraw = null; }
    }


    /** The runtime supplies the single live pool and saved bank; the terrain is the production floor owner. */
    public CdMosqui(CdObjectPlacementRuntime runtime, Assets assets, CdPlayerTerrain terrain, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
        this.nativeAssets = new NativeAssets(secondary -> CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), 0, secondary),
                assets.normal(), assets.damaged(), assets.normalMappingsAddress(), assets.normalScriptAddress(),
                assets.damagedMappingsAddress(), assets.damagedScriptAddress());
        this.angleCommit = null; this.playFm = null; this.prefixMode = false;
    }

    private CdMosqui(CdObjectPlacementRuntime runtime, NativeAssets assets, CdPlayerTerrain terrain,
                     PrimaryAngleCommit angleCommit, PlayFmSound playFm, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = null; this.nativeAssets = Objects.requireNonNull(assets, "native assets");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.angleCommit = Objects.requireNonNull(angleCommit, "angleCommit");
        this.playFm = Objects.requireNonNull(playFm, "playFm");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject"); this.prefixMode = true;
    }

    /** A74 R12A source views, reusing the existing synchronous-prefix algorithm and terrain owner. */
    public static CdMosqui fromAct2Assets(CdObjectPlacementRuntime runtime, CdR12aObjectAssets owner,
                                         CdPlayerTerrain terrain, PrimaryAngleCommit angle, PlayFmSound fm,
                                         DispatchObserver observer, SourceDraw draw) {
        var a = Assets.fromAct2Assets(owner);
        var nativeAssets = new NativeAssets(secondary -> CdR11aObjectAssets.decodeTileWord(a.objectTileIds().copy(), 0, secondary),
                a.normal(), a.damaged(), a.normalMappingsAddress(), a.normalScriptAddress(),
                a.damagedMappingsAddress(), a.damagedScriptAddress());
        var body = new CdMosqui(runtime, nativeAssets, terrain, angle, fm, (pool, identity) -> {
            throw new IllegalStateException("R12A Mosqui requires its actual source Draw");
        });
        body.observer = Objects.requireNonNull(observer); body.sourceDraw = Objects.requireNonNull(draw); return body;
    }

    /** Actual B72 tile selector and the four complete B views; no new art or reader owner. */
    public static CdMosqui fromPastAssets(CdObjectPlacementRuntime runtime, CdR11bAssets owner, CdPlayerTerrain terrain,
                                         PrimaryAngleCommit angleCommit, PlayFmSound playFm, DrawObjectCall drawObject) {
        Objects.requireNonNull(owner, "owner");
        var tiles = owner.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes();
        if (tiles.length() != 72) throw new IllegalArgumentException("Past ObjectTileIDs extent");
        var normalMap = owner.view(CdR11bAssets.Asset.MAP_SPR_MOSQUI_NORMAL);
        var damagedMap = owner.view(CdR11bAssets.Asset.MAP_SPR_MOSQUI_DAMAGED);
        var normalAni = owner.view(CdR11bAssets.Asset.ANI_MOSQUI_NORMAL);
        var damagedAni = owner.view(CdR11bAssets.Asset.ANI_MOSQUI_DAMAGED);
        var nativeAssets = new NativeAssets(secondary -> tiles.unsignedWord((short) tiles.unsignedWord(0) + 2 * secondary),
                CdObjectVisualAssets.decode(normalMap.bytes().copy(), 5, normalAni.bytes().copy(), 2, 70),
                CdObjectVisualAssets.decode(damagedMap.bytes().copy(), 5, damagedAni.bytes().copy(), 2, 70),
                normalMap.range().nativeAddress(), normalAni.range().nativeAddress(),
                damagedMap.range().nativeAddress(), damagedAni.range().nativeAddress());
        return new CdMosqui(runtime, nativeAssets, terrain, angleCommit, playFm, drawObject);
    }

    /** The legacy A74 aggregate is intentionally unavailable on a named Past instance. */
    public Assets assets() {
        if (assets == null) throw new IllegalStateException("Named Past Mosqui has no legacy A74 Assets");
        return assets;
    }

    /** One ObjMosqui dispatch for the whole-pool dynamic slot currently holding ID $3D. */
    public Result run(int wholeIndex, Globals globals) {
        if (!prefixMode) return execute(wholeIndex, globals);
        if (running) throw new IllegalStateException("Named Past Mosqui cannot reenter its active dispatch");
        running = true; publicationOrdinal = 0;
        try { return execute(wholeIndex, globals); }
        finally { running = false; }
    }

    private Result execute(int wholeIndex, Globals globals) {
        Objects.requireNonNull(globals, "globals");
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjMosqui $3D");
        }
        var effects = new ArrayList<Effect>();
        destroyOnGoodFuture(state, globals);
        effects.add(new CallReturned(NativeCall.DESTROY_ON_GOOD_FUTURE));
        switch (state.routine()) {
            case 0 -> {
                state = init(state);
                effects.add(new CallReturned(NativeCall.INIT));
                state = main(state, globals);
                if (!prefixMode) pool.write(state);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            case 2 -> {
                state = main(state, globals);
                if (!prefixMode) pool.write(state);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            case 4 -> {
                state = animate(state);
                if (!prefixMode) pool.write(state);
                effects.add(new CallReturned(NativeCall.ANIMATE));
            }
            case 6 -> {
                state = dive(state, effects);
                if (!prefixMode) pool.write(state);
                effects.add(new CallReturned(NativeCall.DIVE));
            }
            case 8 -> {
                waitRoutine(state, globals, effects);
                effects.add(new CallReturned(NativeCall.WAIT));
            }
            default -> throw new UnsupportedMosqui(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + state.routine() + " indexes past ObjMosqui_Index");
        }
        observedDraw(pool, state.identity());
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        // MOVE.W oVar2A(a0),d0 / JMP CheckObjDespawn2 read the live slot after DrawObject, including a zeroed one.
        var reloaded = pool.state(wholeIndex);
        checkObjDespawn2(reloaded, reloaded.word(MOSQUI_ANCHOR_X), globals, effects);
        effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN2));
        return new Result(pool.state(wholeIndex), effects);
    }

    /** Object Time Check.asm:12-44. Only the predicate is owned; .Destroy converts into the unowned Explosion. */
    private static void destroyOnGoodFuture(CdObjectState state, Globals globals) {
        if (globals.goodFuture() == 0) { return; }                        // TST.B goodFuture / BEQ .End
        if (globals.timeZone() == 1 && state.subtype() == 0) { return; } // CMPI.B #1,timeZone / TST.B oSubtype / BEQ .End
        // .Destroy: DeleteObject, restore oX/oY words, oID := $18, FM_EXPLODE tested on the already
        // cleared flags (never plays), ADDQ.L #4,SP skips the rest of ObjMosqui. Stop before those writes.
        throw new UnsupportedMosqui(UnsupportedBoundary.GOOD_FUTURE_CONVERSION, state.identity().wholeIndex(),
                "goodFuture " + globals.goodFuture() + ", timeZone $" + Integer.toHexString(globals.timeZone())
                        + ", oSubtype $" + Integer.toHexString(state.subtype()));
    }

    /** Init prefix precedes the actual tile operand on B; the legacy A read remains at its old cut. */
    private CdObjectState init(CdObjectState state) {
        int tile = 0;
        if (!prefixMode) tile = nativeAssets.tileOperand().read(state.subtype2());
        state = state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, state.spriteFlags() | 4).moveByte(PRIORITY, 3)
                .moveByte(X_RADIUS, 0x10).moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 0x10)
                .moveByte(COLLISION_TYPE, NATIVE_COLLISION_TYPE).moveWord(MOSQUI_ANCHOR_X, state.centreX());
        if (prefixMode) {
            publish(state);
            try { tile = nativeAssets.tileOperand().read(state.subtype2()); }
            catch (IndexOutOfBoundsException missing) {
                throw new UnsupportedMosqui(UnsupportedBoundary.TILE_ADDRESS, state.identity().wholeIndex(),
                        "selector0/secondary " + state.subtype2() + " leaves its actual B72 WORD view");
            }
        }
        boolean damaged = state.subtype() != 0;
        state = state.moveWord(TILE, tile)
                .moveLong(MAPPINGS, damaged ? nativeAssets.damagedMappingsAddress() : nativeAssets.normalMappingsAddress())
                .moveLong(MOSQUI_ANIMATION_SCRIPT, damaged ? nativeAssets.damagedScriptAddress() : nativeAssets.normalScriptAddress())
                .moveLong(MOSQUI_X_DELTA, damaged ? -0x8000 : -0x10000);
        publish(state); return state;
    }

    /** ObjMosqui_Main (Mosqui/Main.asm:59-94). */
    private CdObjectState main(CdObjectState state, Globals globals) {
        // TST.W debugMode: the full word. BSR ObjMosqui_CheckInRange / BCS .StartDive.
        if (globals.debugMode() == 0 && checkInRange(state, globals)) {
            state = state.addByte(ROUTINE, 2).moveByte(ANIMATION, 1);       // .StartDive: RTS, no movement or animation
            publish(state); return state;
        }
        int reversals = 0;
        while (true) {                                                     // .SkipRange
            int delta = state.longBits(MOSQUI_X_DELTA);
            state = state.addLong(X_POSITION, delta);                      // ADD.L oVar2C(a0),oX(a0): 16:16 add
            publish(state);
            int d0 = (state.centreX() - state.word(MOSQUI_ANCHOR_X)) & 0xFFFF;
            if ((short) d0 < 0) { d0 = (-d0) & 0xFFFF; }                   // BPL .ChkTurn / NEG.W: $8000 stays negative
            if ((short) d0 < 0x80) { break; }                              // CMPI.W #$80 / BLT .Animate (signed)
            if (++reversals > 1) {
                throw new UnsupportedMosqui(UnsupportedBoundary.PATROL_REVERSAL_LOOP, state.identity().wholeIndex(),
                        "oVar2C $" + Integer.toHexString(delta) + " cannot leave |oX-oVar2A| >= $80");
            }
            state = state.moveLong(MOSQUI_X_DELTA, -delta)                 // NEG.L oVar2C(a0)
                    .moveByte(SPRITE_FLAGS, state.spriteFlags() ^ 1)       // BCHG #0,oSprFlags(a0)
                    .moveByte(FLAGS, state.flags() ^ 1);                   // BCHG #0,oFlags(a0); BRA .SkipRange adds again
            publish(state);
        }
        return animate(state);                                             // .Animate: MOVEA.L oVar30,a1 / JMP AnimateObject
    }

    /** ObjMosqui_CheckInRange (Mosqui/Main.asm:98-112): carry out of the second SUBI.W on each axis. */
    private static boolean checkInRange(CdObjectState state, Globals globals) {
        int d0 = (globals.playerYWord() - state.centreY()) & 0xFFFF;       // MOVE.W oY(a1),d0 / SUB.W oY(a0),d0
        d0 = (d0 + 0x30) & 0xFFFF;                                         // SUBI.W #-$30,d0
        if (d0 >= 0x70) { return false; }                                  // SUBI.W #$70,d0 borrows below $70 / BCC .End
        int d1 = (globals.playerXWord() - state.centreX()) & 0xFFFF;       // MOVE.W oX(a1),d0 / SUB.W oX(a0),d0
        d1 = (d1 + 0x30) & 0xFFFF;                                         // SUBI.W #-$30,d1
        return d1 < 0x60;                                                  // SUBI.W #$60,d1 borrow -> BCS .StartDive
    }

    /** ObjMosqui_Animate / .Animate: the stored oVar30 long selects the script table, not the subtype. */
    private CdObjectState animate(CdObjectState state) {
        if (prefixMode) return animatePast(state);
        int pointer = state.longBits(MOSQUI_ANIMATION_SCRIPT);
        CdObjectVisualAssets visual;
        if (pointer == nativeAssets.normalScriptAddress()) { visual = nativeAssets.normal(); }
        else if (pointer == nativeAssets.damagedScriptAddress()) { visual = nativeAssets.damaged(); }
        else {
            throw new UnsupportedMosqui(UnsupportedBoundary.ANIMATION_SCRIPT_POINTER, state.identity().wholeIndex(),
                    "oVar30 $" + Integer.toHexString(pointer) + " is not a bound Mosqui script table");
        }
        var table = new CdObjectAnimation.AnimationTable(visual.animationTable(), 0, visual.animations().size());
        var out = CdObjectAnimation.step(new CdObjectAnimation.State(
                state.unsignedByte(ANIMATION), state.unsignedByte(PREVIOUS_ANIMATION),
                state.unsignedByte(ANIMATION_FRAME), state.unsignedByte(ANIMATION_TIME),
                state.unsignedByte(MAP_FRAME), state.spriteFlags(), state.flags(),
                state.routine(), state.unsignedByte(ROUTINE_2)), table);
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

    /** ObjMosqui_Dive (Mosqui/Main.asm:123-138). */
    private CdObjectState dive(CdObjectState state, List<Effect> effects) {
        state = state.addWord(Y, 6);                                       // ADDQ.W #6,oY(a0): integer word only
        publish(state);
        final var probeSource = state;
        var floor = prefixMode ? CdObjectCollisionCheck.objGetFloorDist(terrain, state,
                value -> commitPastAngle(probeSource, value)) : CdObjectCollisionCheck.objGetFloorDist(terrain, state);
        effects.add(new GlobalWrite(GlobalField.PRIMARY_ANGLE, floor.primaryAngle()));
        int d1 = floor.distance();
        if (d1 > -8) { return state; }                                     // CMPI.W #-8,d1 / BGT .End
        state = state.addWord(Y, d1 + 8)                                   // SUBI.W #-8,d1 / ADD.W d1,oY(a0)
                .addByte(ROUTINE, 2);                                      // ADDQ.B #2,oRoutine(a0)
        publish(state);
        if ((state.spriteFlags() & 0x80) != 0) {                           // TST.B oSprFlags / BPL .End: last build's bit 7
            if (prefixMode) playPastFm(state);
            effects.add(new SoundRequest(Sound.FM_A7));                    // MOVE.W #FM_A7,d0 / JSR PlayFMSound
        }
        return state;
    }

    /** ObjMosqui_Wait (Mosqui/Main.asm:142-151). */
    private void waitRoutine(CdObjectState state, Globals globals, List<Effect> effects) {
        if ((state.spriteFlags() & 0x80) != 0) { return; }                 // TST.B oSprFlags / BMI .End
        despawnObject(state, DespawnEntry.WAIT_OFFSCREEN, globals, effects); // JMP DespawnObject
    }

    /** CheckObjDespawn2 / DespawnObjTimeWarp (Object Spawner.asm:388-399,442-446) with d0 = oVar2A. */
    private void checkObjDespawn2(CdObjectState state, int anchorX, Globals globals, List<Effect> effects) {
        if ((state.spriteFlags() & 0x80) == 0) {                           // TST.B oSprFlags / BMI DespawnObjTimeWarp
            int d0 = anchorX & 0xFF80;
            int d1 = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;   // SUBI.W #$80 / ANDI.W #$FF80
            d0 = (d0 - d1) & 0xFFFF;
            if (d0 > 0x80 + (320 + 0x40) + 0x80) {                         // CMPI.W #$280 / BLS: unsigned, equality keeps
                despawnObject(state, DespawnEntry.ANCHOR_OFFSCREEN, globals, effects);
                return;
            }
        }
        if ((globals.timeZone() & 0x80) != 0) {                            // DespawnObjTimeWarp: BTST #7,timeZone
            despawnObject(state, DespawnEntry.TIME_WARP, globals, effects);
        }
    }

    /** DespawnObject (Object Spawner.asm:403-438): bank loaded bit, then the full 64-byte delete. */
    private void despawnObject(CdObjectState state, DespawnEntry entry, Globals globals, List<Effect> effects) {
        int savedId = state.savedFlagsId();                                // MOVE.B oSavedFlagsID / BEQ .Delete
        int period = -1;
        if (savedId != 0) {
            period = despawnPeriod(globals);
            observedSaved(state, savedId, period, 2 + 3 * savedId + period); // BCLR #7,2(a1,d0.w)
        }
        observedDelete(state);                                           // JSR DeleteObject
        effects.add(new Despawn(entry, savedId, period));
    }

    /** Object Spawner.asm:413-431: the old period during a warp, clamped to Past..Future. */
    private static int despawnPeriod(Globals globals) {
        int d1 = globals.timeZone() & 0x7F;                                // MOVE.B timeZone,d1 / BCLR #7,d1
        if ((globals.timeZone() & 0x80) == 0) { return d1; }               // BEQ .MarkUnloaded
        d1 = (short) ((d1 - (byte) globals.timeWarpDir()) & 0xFFFF);       // EXT.W / NEG.W d2 / ADD.W d2,d1
        if (d1 < 0) { return 0; }                                          // BPL .CheckFuture; MOVEQ #TIME_PAST
        return d1 < 3 ? d1 : 2;                                            // CMPI.W #TIME_FUTURE+1 / BCS; MOVEQ #TIME_FUTURE
    }

    private void publish(CdObjectState state) {
        if (prefixMode) observedWrite(state);
    }

    /** Values only. The actual private World invocation must authenticate the producer and complete Frame CAS. */
    private static void sourceInCut(CdObjectPool.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut, "actual cut"); Objects.requireNonNull(source, "actual source");
        int whole = source.identity().wholeIndex(); Objects.checkIndex(whole - 32, 96);
        if (cut.slots().size() != 96 || !cut.slots().get(whole - 32).equals(source))
            throw new IllegalArgumentException("Mosqui source differs from its supplied full96 cut");
    }
    private void verifyPastPortCut(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        if (!prefixMode || !running) throw new IllegalStateException("No active named Past Mosqui call");
        runtime.pool().validateRestore(cut.pool()); sourceInCut(cut.pool(), source);
        var actual = runtime.capture();
        if (!actual.status().equals(cut.status()) || !actual.pool().slots().equals(cut.pool().slots())
                || !java.util.Arrays.equals(actual.savedFlags().copy(), cut.savedFlags().copy()))
            throw new IllegalStateException("Mosqui read-only port changed full96/source/placement/saved state");
    }
    private void readOnlyPastPort(CdObjectState source, java.util.function.Consumer<CdObjectPool.Snapshot> port) {
        var cut = runtime.capture(); verifyPastPortCut(cut, source); Throwable original = null;
        try { port.accept(cut.pool()); }
        catch (RuntimeException | Error failure) { original = failure; throw failure; }
        finally {
            try { verifyPastPortCut(cut, source); }
            catch (RuntimeException | Error verification) {
                if (original == null) throw verification;
                if (verification != original) original.addSuppressed(verification);
            }
        }
    }
    private void commitPastAngle(CdObjectState source, int value) {
        readOnlyPastPort(source, cut -> {
            var call = new PrimaryAngleCall(cut, source, value); pendingAngle = call;
            try { if (observer != null) observer.beforeCall(call); angleCommit.commit(call);
                if (observer != null) observer.afterCall(call); }
            finally { pendingAngle = null; }
        });
    }
    private void playPastFm(CdObjectState source) {
        readOnlyPastPort(source, cut -> {
            var call = new FmCall(cut, source, Sound.FM_A7); pendingFm = call;
            try { if (observer != null) observer.beforeCall(call); playFm.play(call);
                if (observer != null) observer.afterCall(call); }
            finally { pendingFm = null; }
        });
    }

    /** Only the genuine shared owner computes successful animation, once on the original full image. */
    private CdObjectState animatePast(CdObjectState original) {
        int animation = original.unsignedByte(ANIMATION);
        int timer = animation == original.unsignedByte(PREVIOUS_ANIMATION) ? original.unsignedByte(ANIMATION_TIME) : 0;
        boolean held = ((timer - 1) & 255) < 0x80;
        CdObjectVisualAssets visual;
        if (held) {
            // Explicitly unread argument: shared step returns before directory access. This is
            // not a resolution/substitution of the raw foreign live oVar30 pointer.
            visual = nativeAssets.normal();
        } else {
            int pointer = original.longBits(MOSQUI_ANIMATION_SCRIPT);
            if (pointer == nativeAssets.normalScriptAddress()) visual = nativeAssets.normal();
            else if (pointer == nativeAssets.damagedScriptAddress()) visual = nativeAssets.damaged();
            else {
                animationFailureHead(original);
                throw new UnsupportedMosqui(UnsupportedBoundary.ANIMATION_SCRIPT_POINTER, original.identity().wholeIndex(),
                        "reached full oVar30 $" + Integer.toHexString(pointer));
            }
        }
        var table = new CdObjectAnimation.AnimationTable(visual.animationTable(), 0, visual.animations().size());
        CdObjectAnimation.State out;
        try {
            out = CdObjectAnimation.step(new CdObjectAnimation.State(animation, original.unsignedByte(PREVIOUS_ANIMATION),
                    original.unsignedByte(ANIMATION_FRAME), original.unsignedByte(ANIMATION_TIME), original.unsignedByte(MAP_FRAME),
                    original.spriteFlags(), original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)), table);
        } catch (UnsupportedOperationException selectedRead) {
            animationFailurePrefix(original, table, selectedRead);
            throw new IllegalStateException("Shared animation failure did not match a reached read", selectedRead);
        }
        var result = original.moveByte(ANIMATION, out.animationId()).moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex()).moveByte(ANIMATION_TIME, out.timer()).moveByte(MAP_FRAME, out.mapFrame())
                .moveByte(SPRITE_FLAGS, out.spriteFlags()).moveByte(FLAGS, out.statusFlags())
                .moveByte(ROUTINE, out.routine()).moveByte(ROUTINE_2, out.routine2());
        publish(result); return result;
    }
    private CdObjectState animationFailureHead(CdObjectState state) {
        if (state.unsignedByte(ANIMATION) != state.unsignedByte(PREVIOUS_ANIMATION)) {
            state = state.moveByte(PREVIOUS_ANIMATION, state.unsignedByte(ANIMATION)).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
            publish(state);
        }
        state = state.addByte(ANIMATION_TIME, -1); publish(state); return state;
    }
    /** Reached read-failure stores only; never a second animation call or a successful transition. */
    private void animationFailurePrefix(CdObjectState original, CdObjectAnimation.AnimationTable table, RuntimeException cause) {
        var state = animationFailureHead(original); int animation = state.unsignedByte(ANIMATION);
        if (animation >= table.scriptCount()) throw animationMissing(state, UnsupportedBoundary.ANIMATION_DIRECTORY_READ, 2 * animation, cause);
        int script = table.tableOffset() + (short) table.bytes().unsignedWord(table.tableOffset() + animation * 2);
        int speed = animationFailureByte(state, table, script, UnsupportedBoundary.ANIMATION_SPEED_READ, cause);
        state = state.moveByte(ANIMATION_TIME, speed); publish(state);
        int index = state.unsignedByte(ANIMATION_FRAME);
        int frame = animationFailureByte(state, table, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ, cause);
        if (frame == 0xFF) {
            state = state.moveByte(ANIMATION_FRAME, 0); publish(state);
            animationFailureByte(state, table, script + 1, UnsupportedBoundary.ANIMATION_FRAME_READ, cause);
        } else if (frame == 0xFE) {
            int back = animationFailureByte(state, table, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ, cause);
            index = (index - back) & 255; state = state.moveByte(ANIMATION_FRAME, index); publish(state);
            animationFailureByte(state, table, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ, cause);
        } else if (frame == 0xFD) {
            animationFailureByte(state, table, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ, cause);
        }
    }
    private int animationFailureByte(CdObjectState state, CdObjectAnimation.AnimationTable table, int at,
                                     UnsupportedBoundary boundary, RuntimeException cause) {
        if (at < 0 || at >= table.bytes().length()) throw animationMissing(state, boundary, at, cause);
        return table.bytes().unsignedByte(at);
    }
    private UnsupportedMosqui animationMissing(CdObjectState state, UnsupportedBoundary boundary, int at, RuntimeException cause) {
        var stopped = new UnsupportedMosqui(boundary, state.identity().wholeIndex(), "reached animation byte " + at);
        stopped.initCause(cause); return stopped;
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }

    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
