package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjCollapsePlatform ($20), scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Palmtree Panic/Objects/Collapsing Platform/Main.asm:7-308, Data (Present).asm:11-59.
 * A run is one physical RunObjects visit: Init/Main/Delay/Fall, unconditional wrapper DrawObject,
 * then the signed live-routine gate before CheckObjDespawn. Init enters Main in the same visit.
 * There is no outer visibility, timeStop, goodFuture or animation gate and no FixBugs conditional.
 * The shared Solid owner retains shipped FixBugs=0 behavior.
 *
 * <p>Every reached native TopSolidObject/GetOffObject publishes its source prefix, captures the actual
 * entire pool ONCE, invokes the shared owner once, and synchronously commits the genuine Outcome.
 * Completed metadata is descriptive and must never be replayed. NativeStopped is committed and checked
 * exactly like its real prefix, then its identical originalException is thrown; no d0 or successful
 * Result is manufactured for that cut. Subsequent actual calls receive the preceding call's player,
 * globals and current images, not the original inputs or a synthetic overlay.
 *
 * <p>Root owns private-phase/source/site/full-pool/Frame/FM validation and one publication. This body
 * verifies source and output images, but cannot prove Frame adoption or sound publication. It imports
 * no Frame and creates no state owner, art bank, decoder, selector, saved bank or capture partition.
 * The transient guard prevents synchronous reentry through any supplied port. The caller owns terminal
 * failure and completed capture eligibility; a finally-cleared guard is not authority to resume/retry.
 *
 * <p>All source stores preceding a reached read or host failure remain live. FindObjSlot tests actual
 * physical ID bytes, clears/reserves nothing, and precedes every breakup script read. A SingleRow child
 * header is written before that read. Partial children survive later exhaustion/failure. Opaque dirty
 * slot bytes and native fractions are preserved by the existing typed immutable writers.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdCollapsePlatform {
    public static final int OBJECT_ID = 0x20;
    /** Literal MOVE.W #$44BE,oTile, Main.asm:35,182,279; never SetObjectTileID. */
    public static final int TILE_WORD = 0x44BE;
    /** MOVE.W #FM_A3,d0 / JSR PlayFMSound, Main.asm:83-84. */
    public static final int COLLAPSE_FM = 0xA3;

    public enum NativeRoutine { INIT, MAIN, DELAY, FALL, MULTI_ROW, SINGLE_ROW }
    public enum NativeCall { DRAW_OBJECT }
    public enum DeleteSite { FALL_DISTANCE, MULTI_SPLIT, SINGLE_SPLIT }
    public enum Site {
        MAIN_TOP, MAIN_GET_OFF, DELAY_TOP, DELAY_GET_OFF,
        MULTI_CAPACITY_TOP, MULTI_CAPACITY_GET_OFF, SINGLE_CAPACITY_TOP, SINGLE_CAPACITY_GET_OFF
    }

    /** Ordered, completed native observations only; values confer no execution/publication authority. */
    public sealed interface Effect permits RoutineEntered, Solid, SlotFound, ChildInitialized, FmRequested,
            Deleted, CallReturned, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record Solid(Site site, CdSolidObject.Result result) implements Effect {
        public Solid {
            Objects.requireNonNull(site, "site"); Objects.requireNonNull(result, "result");
            if (result.entry() != entryFor(site)) throw new IllegalArgumentException("Wrong completed Solid entry/site");
        }
    }
    /** FindObjSlot returned this physical slot; a negative MultiRow cell does not reserve or write it. */
    public record SlotFound(int wholeIndex) implements Effect {
        public SlotFound { Objects.checkIndex(wholeIndex - 32, 96); }
    }
    public record ChildInitialized(CdObjectState state) implements Effect {
        public ChildInitialized { Objects.requireNonNull(state, "state"); }
    }
    public record FmRequested(int soundId) implements Effect {
        public FmRequested {
            if (soundId != COLLAPSE_FM) throw new IllegalArgumentException("Collapse directly requests only FM_A3");
        }
    }
    public record Deleted(DeleteSite site) implements Effect {
        public Deleted { Objects.requireNonNull(site, "site"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record DespawnChecked(boolean deleted) implements Effect { }

    /** Raw widths only. Period/address interpretation is deferred until the native tail reaches that read. */
    public record DespawnGlobals(int timeZone, int timeWarpDirection, int cameraXWord) {
        public DespawnGlobals {
            byteValue(timeZone, "timeZone"); byteValue(timeWarpDirection, "timeWarpDirection");
            wordValue(cameraXWord, "cameraXWord");
        }
    }
    /** First reached primary inputs. Later helper calls use the actual prior outcome's published values. */
    public record Inputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public Inputs {
            Objects.requireNonNull(player, "player"); Objects.requireNonNull(gates, "gates");
            Objects.requireNonNull(globals, "globals");
        }
    }

    /**
     * Declared component data; construction authenticates nothing. Raw sizes are NOT eagerly walked or
     * normalized: signed relative words, header bytes and script reads are bounded only when reached.
     * All four addresses are native full LONG values taken from authenticated views in production.
     */
    public record Assets(CdObjectVisualAssets collapsePlatform1, CdObjectVisualAssets collapsePlatform2,
                         CdObjectVisualAssets collapsePlatform3, CdObjectVisualAssets collapsePlatform4,
                         CdAssetBytes sizes1, CdAssetBytes sizes2,
                         int mappings1, int mappings2, int mappings3, int mappings4) {
        public Assets {
            Objects.requireNonNull(collapsePlatform1, "collapsePlatform1");
            Objects.requireNonNull(collapsePlatform2, "collapsePlatform2");
            Objects.requireNonNull(collapsePlatform3, "collapsePlatform3");
            Objects.requireNonNull(collapsePlatform4, "collapsePlatform4");
            Objects.requireNonNull(sizes1, "sizes1"); Objects.requireNonNull(sizes2, "sizes2");
        }
        /** Actual Act2Past's complete556-byte data family; no Present or Act1Past root substitution. */
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12B object assets");
            var identity=Objects.requireNonNull(owner.identity(), "R12B identity");
            if(identity.variant()!=CdReleaseVariant.USA || identity.file()!=CdNativeFile.R12B
                    || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM || !identity.stage().equals(CdStagePayload.R12B.stage()))
                throw new IllegalArgumentException("Collapse requires actual original USA R12B metadata");
            return new Assets(owner.collapsePlatform1(),owner.collapsePlatform2(),owner.collapsePlatform3(),owner.collapsePlatform4(),
                    owner.view(CdR12bObjectAssets.View.COLLAPSE_PLATFORM_SIZES_1),owner.view(CdR12bObjectAssets.View.COLLAPSE_PLATFORM_SIZES_2),
                    CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_1.range().nativeAddress(),CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_2.range().nativeAddress(),
                    CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_3.range().nativeAddress(),CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_4.range().nativeAddress());
        }

        public static Assets fromObjectAssets(CdR11aObjectAssets objects) {
            Objects.requireNonNull(objects, "objects");
            return new Assets(objects.collapsePlatform1(), objects.collapsePlatform2(),
                    objects.collapsePlatform3(), objects.collapsePlatform4(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_SIZES_1).bytes(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_SIZES_2).bytes(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_1).range().nativeAddress(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_2).range().nativeAddress(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_3).range().nativeAddress(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_4).range().nativeAddress());
        }

        /** Actual original-USA Act2 data only; no current patterns, Solid execution or World admission. */
        public static Assets fromAct2Assets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            var identity=Objects.requireNonNull(owner.identity(), "identity");
            if (identity.variant()!=CdReleaseVariant.USA || identity.file()!=CdNativeFile.R12A
                    || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12A.stage()))
                throw new IllegalArgumentException("Collapse Act2 assets require authenticated original USA R12A Word RAM");
            var map1=owner.collapsePlatform1();var map2=owner.collapsePlatform2();
            var map3=owner.collapsePlatform3();var map4=owner.collapsePlatform4();
            var maps=List.of(map1,map2,map3,map4);int[] counts={1,2,7,2},lengths={118,286,62,42};
            for(int i=0;i<maps.size();i++) {
                var map=maps.get(i);
                if(map.mappingTableOffset()!=0 || map.mappingFrames().size()!=counts[i]
                        || map.mappings().length()!=lengths[i] || !map.animations().isEmpty())
                    throw new IllegalArgumentException("Collapse Act2 requires its complete1/2/7/2 static mapping includes");
            }
            var sizes1=owner.view(CdR12aProfile.Asset.COLLAPSE_PLATFORM_SIZES_1);
            var sizes2=owner.view(CdR12aProfile.Asset.COLLAPSE_PLATFORM_SIZES_2);
            if(sizes1.length()!=24 || sizes2.length()!=24)
                throw new IllegalArgumentException("Collapse Act2 requires both actual24-byte size directories/scripts");
            return new Assets(map1,map2,map3,map4,sizes1,sizes2,
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_1).nativeAddress(),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_2).nativeAddress(),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_3).nativeAddress(),
                    owner.profile().range(CdR12aProfile.Asset.MAP_SPR_COLLAPSE_PLATFORM_4).nativeAddress());
        }

        /**
         * Named original-USA Past data binding; opens no art bank and creates no current owner.
         * Act1Past.asm:67-68 includes the same Main (tile44BE at35,182,279) and Data(Past).
         * Its four complete counted roots have 2/6/7/7 frames; the existing Platform source owns57 tiles.
         * The raw size scripts and genuine EVEN bytes remain unmodified and are read only when reached.
         */
        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner, "owner");
            var identity = Objects.requireNonNull(owner.identity(), "identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R11B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R11B.stage())) {
                throw new IllegalArgumentException("Collapse Past assets require original USA R11B Word RAM");
            }
            var map1 = owner.view(CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_1);
            var map2 = owner.view(CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_2);
            var map3 = owner.view(CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_3);
            var map4 = owner.view(CdR11bAssets.Asset.MAP_SPR_COLLAPSE_PLATFORM_4);
            return new Assets(CdObjectVisualAssets.decodeStatic(map1.bytes().copy(), 2, 57),
                    CdObjectVisualAssets.decodeStatic(map2.bytes().copy(), 6, 57),
                    CdObjectVisualAssets.decodeStatic(map3.bytes().copy(), 7, 57),
                    CdObjectVisualAssets.decodeStatic(map4.bytes().copy(), 7, 57),
                    owner.view(CdR11bAssets.Asset.COLLAPSE_PLATFORM_SIZES_1).bytes(),
                    owner.view(CdR11bAssets.Asset.COLLAPSE_PLATFORM_SIZES_2).bytes(),
                    map1.range().nativeAddress(), map2.range().nativeAddress(),
                    map3.range().nativeAddress(), map4.range().nativeAddress());
        }
    }

    /** The actual borrowed cut for ONE native call, with its exact current semantic Frame inputs. */
    public record SolidCall(Site site, CdSolidObject.Entry entry, CdObjectPool.Snapshot actualCut,
                            CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
                            CdSolidObject.Outcome outcome) {
        public SolidCall {
            Objects.requireNonNull(site, "site"); Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(actualCut, "actualCut"); Objects.requireNonNull(expectedPlayer, "expectedPlayer");
            Objects.requireNonNull(expectedGlobals, "expectedGlobals"); Objects.requireNonNull(outcome, "outcome");
            CdSolidObject.Entry actualEntry = outcome instanceof CdSolidObject.Completed completed
                    ? completed.completedResult().entry() : ((CdSolidObject.NativeStopped) outcome).prefix().entry();
            if (entry != entryFor(site) || actualEntry != entry) {
                throw new IllegalArgumentException("Wrong native Solid entry/site/outcome");
            }
        }
    }
    /**
     * Synchronous Root port. Preflight actual private phase, physical slot/source/site, all 96 current
     * images, exact Frame expected values, declared outputs/masks and ordered FM requests BEFORE any
     * returned-image publication. Then pool CAS, one Frame adoption and one FM append, with no callback
     * gap or replay. For NativeStopped use its actual Prefix, never a completed-result adapter.
     */
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }
    /** Source DrawObject over the actual live pool; its resulting image is always reloaded. */
    @FunctionalInterface public interface DrawObjectCall { void drawObject(CdObjectPool pool, CdObjectState.Identity slot); }
    /** One direct PlayFMSound request at the Main source edge. Shared Solid requests belong to SolidCommit. */
    @FunctionalInterface public interface FmCall { void playFm(int soundId); }

    public record FmRequest(CdObjectPool.Snapshot actualCut,CdObjectState expectedSource,int soundId) {
        public FmRequest {
            Objects.requireNonNull(actualCut);Objects.requireNonNull(expectedSource);
            if(soundId!=COLLAPSE_FM || !actualCut.slots().get(expectedSource.identity().wholeIndex()-32).equals(expectedSource))
                throw new IllegalArgumentException("Collapse FM requires its actual full96/source/A3 values");
        }
    }
    @FunctionalInterface public interface FmCommit { void play(FmRequest call); }
    /** Synchronous actual-owner receipt; checked before the next native store, never replayed. */
    @FunctionalInterface public interface PublicationObserver { void published(Object actualOperation); }

    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state, "state"); effects = List.copyOf(effects); }
        public boolean deleted() {
            return effects.stream().anyMatch(e -> e instanceof Deleted || e instanceof DespawnChecked d && d.deleted());
        }
    }

    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, SIZE_DIRECTORY_READ, SIZE_HEADER_READ, MULTI_FRAME_READ, SINGLE_FRAME_READ
    }
    public enum NativeTable { ROUTINE_INDEX, SIZES_1, SIZES_2 }
    /** Reached unauthenticated read/dispatch. Earlier stores remain live; there is no default byte or resume. */
    public static final class UnsupportedCollapsePlatform extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final NativeTable table;
        private final int byteOffset;
        private UnsupportedCollapsePlatform(UnsupportedBoundary boundary, int wholeIndex, NativeTable table, int byteOffset) {
            super(boundary + " at dynamic slot " + wholeIndex + ", " + table + " byte offset " + byteOffset);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.table = table; this.byteOffset = byteOffset;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public NativeTable table() { return table; }
        public int byteOffset() { return byteOffset; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final CdSolidObject sharedSolid;
    private final SolidCommit commit;
    private final DrawObjectCall drawObject;
    private final FmCall fmCall;
    private boolean running;
    private FmCommit act2Fm;
    private final PublicationObserver publicationObserver;
    private transient Cursor currentCursor;
    private transient SolidCall pendingSolid;
    private transient FmRequest pendingFm;
    private transient CdObjectPool.Snapshot pendingDrawCut;
    private transient CdObjectState.Identity pendingDrawIdentity;

    /** Actual pending identity and completed prefix are separate from plain public call values. */
    public boolean isCurrentSolidCall(SolidCall call){return running&&call!=null&&pendingSolid==call;}
    public boolean isCurrentFmRequest(FmRequest call){return running&&call!=null&&pendingFm==call;}
    public boolean matchesCurrentPrefix(List<Solid> completed,boolean fmPlayed){
        return running&&currentCursor!=null&&currentCursor.effects.stream().filter(Solid.class::isInstance).toList().equals(completed)
                &&currentCursor.effects.stream().anyMatch(FmRequested.class::isInstance)==fmPlayed;
    }
    public boolean isCurrentDrawCall(CdObjectPool pool,CdObjectState.Identity identity){
        return running&&pool==placement.pool()&&identity!=null&&pendingDrawIdentity==identity&&pendingDrawCut!=null
                &&pool.capture().slots().equals(pendingDrawCut.slots());
    }
    public boolean isCurrentOperation(Object operation) {
        return running && operation != null && (pendingSolid == operation || pendingFm == operation || pendingDrawIdentity == operation);
    }
    private void observePublication(Object operation) {
        if (publicationObserver != null) publicationObserver.published(operation);
    }
    /** Bind the actual R12B relation before metadata; native prefix observers/callbacks stay caller-owned. */
    public static CdCollapsePlatform fromAct2PastAssets(CdObjectPlacementRuntime placement,CdR12bTerrainAssets owner,
                                                        CdSolidObject solid,SolidCommit commit,DrawObjectCall draw,FmCommit fm,PublicationObserver observer) {
        Objects.requireNonNull(placement, "actual R12B placement"); Objects.requireNonNull(owner, "actual R12B terrain");
        if(!placement.sameAct2PastSource(owner))throw new IllegalArgumentException("Collapse requires the same retained R12B placement/source");
        return new CdCollapsePlatform(placement,Assets.fromAct2PastAssets(owner.objects()),solid,commit,draw,fm,observer);
    }

    public static CdCollapsePlatform fromAct2Assets(CdObjectPlacementRuntime placement,CdR12aObjectAssets owner,
                                                    CdSolidObject solid,SolidCommit commit,DrawObjectCall draw,FmCommit fm,PublicationObserver observer){
        Objects.requireNonNull(placement);Objects.requireNonNull(owner);
        if(!placement.sameAct2Source(owner))throw new IllegalArgumentException("Collapse requires the same retained R12A placement/source");
        return new CdCollapsePlatform(placement,Assets.fromAct2Assets(owner),solid,commit,draw,fm,observer);
    }
    private CdCollapsePlatform(CdObjectPlacementRuntime placement,Assets assets,CdSolidObject sharedSolid,
                               SolidCommit commit,DrawObjectCall draw,FmCommit fm,PublicationObserver observer){
        this.placement=Objects.requireNonNull(placement);this.assets=Objects.requireNonNull(assets);this.sharedSolid=Objects.requireNonNull(sharedSolid);
        this.commit=Objects.requireNonNull(commit);this.drawObject=Objects.requireNonNull(draw);this.fmCall=null;this.act2Fm=Objects.requireNonNull(fm);this.publicationObserver=Objects.requireNonNull(observer);
    }

    public CdCollapsePlatform(CdObjectPlacementRuntime placement, Assets assets, CdSolidObject sharedSolid,
                              SolidCommit commit, DrawObjectCall drawObject, FmCall fmCall) {
        this.placement = Objects.requireNonNull(placement, "placement"); this.assets = Objects.requireNonNull(assets, "assets");
        this.sharedSolid = Objects.requireNonNull(sharedSolid, "sharedSolid"); this.commit = Objects.requireNonNull(commit, "commit");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject"); this.fmCall = Objects.requireNonNull(fmCall, "fmCall");
        this.publicationObserver = null;
    }

    /** No blanket Inputs/DespawnGlobals dereference: each belongs only to the source instruction that reads it. */
    public Result run(int wholeIndex, DespawnGlobals globals, Inputs inputs) {
        if (running) throw new IllegalStateException("Reentrant ObjCollapsePlatform dispatch");
        var pool = placement.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("RunObjects requires ID $20 at slot " + wholeIndex);
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 6) {
            throw new UnsupportedCollapsePlatform(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, NativeTable.ROUTINE_INDEX, routine);
        }
        running = true;
        try {
            var c = new Cursor(pool, state, inputs);currentCursor=c;
            switch (routine) {
                case 0 -> { init(c); main(c); }
                case 2 -> main(c);
                case 4 -> delay(c);
                case 6 -> fall(c);
            }
            // ObjCollapsePlatform:12-16: ALWAYS Draw, including after DeleteObject, then reload signed BYTE.
            pendingDrawCut=pool.capture();pendingDrawIdentity=c.state.identity();
            try{drawObject.drawObject(pool,pendingDrawIdentity);observePublication(pendingDrawIdentity);}finally{pendingDrawCut=null;pendingDrawIdentity=null;}
            c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT)); c.state = pool.state(wholeIndex);
            if (c.state.signedByte(ROUTINE) < 4) c.effects.add(new DespawnChecked(checkObjDespawn(c.state, globals)));
            return new Result(pool.state(wholeIndex), c.effects);
        } finally {currentCursor=null;running = false;}
    }

    /** Init:31-69. Every prefix before a reached size read is already in the real pool. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.write(c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveByte(PRIORITY, 3).moveWord(TILE, TILE_WORD));
        int subtype = c.state.subtype(); boolean single = (subtype & 0x80) != 0;
        NativeTable table = single ? NativeTable.SIZES_2 : NativeTable.SIZES_1;
        CdAssetBytes sizes = single ? assets.sizes2() : assets.sizes1();
        c.write(c.state.moveLong(MAPPINGS, single ? assets.mappings2() : assets.mappings1()));
        if ((subtype & 0x10) != 0) {
            c.write(c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 1).moveByte(FLAGS, c.state.flags() | 1));
        }
        int frame = subtype & 15; c.write(c.state.moveByte(MAP_FRAME, frame));
        int at = directory(c, sizes, table, frame);
        int dx = read(c, sizes, table, at, UnsupportedBoundary.SIZE_HEADER_READ);
        dx = ((dx + 1) << 3) & 0xFF;
        c.write(c.state.moveByte(X_RADIUS, dx).moveByte(WIDTH, dx));
        int dy = (byte) read(c, sizes, table, at + 1, UnsupportedBoundary.SIZE_HEADER_READ);
        if (dy < 0) dy = (byte) -dy; // NEG.B: $80 remains negative, then the following BYTE arithmetic wraps.
        dy = ((((dy + 1) & 0xFF) << 3) + 2) & 0xFF;
        c.write(c.state.moveByte(Y_RADIUS, dy));
    }

    /** Main:73-88. Top is unconditional; only its genuine completed MOVEQ d0 selects breakup. */
    private void main(Cursor c) {
        c.enter(NativeRoutine.MAIN);
        if (solid(c, Site.MAIN_TOP).d0().orElseThrow() == 0) return;
        solid(c, Site.MAIN_GET_OFF); // GetOffObject defines NO d0.
        requestFm(c);
        c.effects.add(new FmRequested(COLLAPSE_FM));
        c.state = c.pool.state(c.wholeIndex); // Native ADDQ and subtype test use current memory after PlayFMSound.
        c.write(c.state.addByte(ROUTINE, 2));
        if (c.state.signedByte(SUBTYPE) >= 0) multiRow(c); else singleRow(c);
    }

    private void requestFm(Cursor c){
        if(act2Fm==null){fmCall.playFm(COLLAPSE_FM);return;}
        var cut=c.pool.capture();var call=new FmRequest(cut,c.state,COLLAPSE_FM);pendingFm=call;
        try{act2Fm.play(call);assertSourceCut(c.pool,cut);observePublication(call);}
        catch(RuntimeException|Error original){try{assertSourceCut(c.pool,cut);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;}
        finally{pendingFm=null;}
    }

    /** Delay:93-109. The transition visit remains Delay and may call Top/GetOff with routine6. */
    private void delay(Cursor c) {
        c.enter(NativeRoutine.DELAY);
        c.write(c.state.addWord(COLLAPSE_DELAY, -1));
        if (c.state.word(COLLAPSE_DELAY) == 0) c.write(c.state.addByte(ROUTINE, 2));
        if (c.state.unsignedByte(COLLAPSE_SOLID_MARKER) == 0) return;
        if (solid(c, Site.DELAY_TOP).d0().orElseThrow() != 0 && c.state.word(COLLAPSE_DELAY) == 0) {
            solid(c, Site.DELAY_GET_OFF);
        }
    }

    /** Fall:114-129. Full LONG Y/add-delta precede the reached primary Y WORD read. */
    private static void fall(Cursor c) {
        c.enter(NativeRoutine.FALL);
        c.write(c.state.addLong(Y_POSITION, c.state.longBits(COLLAPSE_FALL_DELTA))
                .addLong(COLLAPSE_FALL_DELTA, 0x4000));
        int primaryY = c.player().actor().motion().centreY16_16() >>> 16;
        if ((short) (c.state.centreY() - primaryY) > 0x200) delete(c, DeleteSite.FALL_DISTANCE);
    }

    /** MultiRow:134-223. Actual Find BEFORE each signed frame read, including both FF cells. */
    private void multiRow(Cursor c) {
        c.enter(NativeRoutine.MULTI_ROW);
        int subtype = c.state.subtype(); boolean flipped = (subtype & 0x10) != 0;
        int at = directory(c, assets.sizes1(), NativeTable.SIZES_1, subtype & 15);
        int columnsMinus1 = read(c, assets.sizes1(), NativeTable.SIZES_1, at++, UnsupportedBoundary.SIZE_HEADER_READ);
        int startX = (c.state.centreX() + (flipped ? 1 : -1) * (columnsMinus1 << 3)) & 0xFFFF;
        int xStep = flipped ? -16 : 16;
        int rowsMinus1 = read(c, assets.sizes1(), NativeTable.SIZES_1, at++, UnsupportedBoundary.SIZE_HEADER_READ);
        int y = (c.state.centreY() + (rowsMinus1 << 3)) & 0xFFFF;
        int rowDelay = 9;
        c.write(c.state.moveByte(COLLAPSE_SAVED_SOURCE_ID, c.state.id()));
        for (int rowCounter = rowsMinus1; rowCounter >= 0; rowCounter--) {
            int x = startX, timer = rowDelay;
            for (int columnCounter = columnsMinus1; columnCounter >= 0; columnCounter--) {
                var found = c.pool.findObjSlot();
                if (found.isEmpty()) {
                    capacity(c, Site.MULTI_CAPACITY_TOP, Site.MULTI_CAPACITY_GET_OFF);
                    delete(c, DeleteSite.MULTI_SPLIT); return;
                }
                int whole = found.getAsInt(); c.effects.add(new SlotFound(whole));
                int frame = (byte) read(c, assets.sizes1(), NativeTable.SIZES_1, at++, UnsupportedBoundary.MULTI_FRAME_READ);
                if (frame >= 0) {
                    var child = c.pool.state(whole);
                    child = child.moveByte(MAP_FRAME, frame).moveByte(SPRITE_FLAGS, child.spriteFlags() | 4)
                            .moveByte(PRIORITY, 3).moveWord(TILE, TILE_WORD).moveLong(MAPPINGS, assets.mappings3())
                            .moveLong(COLLAPSE_FALL_DELTA, 0x20000)
                            .moveByte(ID, c.state.unsignedByte(COLLAPSE_SAVED_SOURCE_ID)).moveByte(ROUTINE, c.state.routine());
                    if (flipped) child = child.moveByte(SPRITE_FLAGS, child.spriteFlags() | 1).moveByte(FLAGS, child.flags() | 1);
                    if (rowCounter == 0) child = child.moveByte(COLLAPSE_SOLID_MARKER, 0xFF)
                            .moveByte(X_RADIUS, 8).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 9);
                    child = child.moveWord(Y, y).moveWord(X, x).moveWord(COLLAPSE_DELAY, timer);
                    c.pool.write(child); c.effects.add(new ChildInitialized(child));
                }
                // .Endxt is reached even for a negative cell; it consumes position and timer, not the slot.
                x = (x + xStep) & 0xFFFF; timer = (timer + 12) & 0xFFFF;
            }
            y = (y - 16) & 0xFFFF; rowDelay = (rowDelay + 5) & 0xFFFF;
        }
        delete(c, DeleteSite.MULTI_SPLIT); // Complete split goes straight to Delete, with NO cleanup Top.
    }

    /** SingleRow:227-308. Low FIVE bits here; speed is actual post-GetOff primary XV if bit6 selects it. */
    private void singleRow(Cursor c) {
        c.enter(NativeRoutine.SINGLE_ROW);
        int subtype = c.state.subtype();
        int at = directory(c, assets.sizes2(), NativeTable.SIZES_2, subtype & 31);
        int columnsMinus1 = read(c, assets.sizes2(), NativeTable.SIZES_2, at++, UnsupportedBoundary.SIZE_HEADER_READ);
        int dy = read(c, assets.sizes2(), NativeTable.SIZES_2, at++, UnsupportedBoundary.SIZE_HEADER_READ);
        dy = ((((dy + 1) & 0xFF) << 3) + 2) & 0xFF; // No Init absolute-value branch here.
        boolean reverse;
        if ((subtype & 0x40) == 0) {
            reverse = (subtype & 0x20) != 0; // LSL.B #2 N comes from original bit5.
        } else {
            int speed = (short) c.player().actor().motion().xVelocity();
            if ((subtype & 0x20) != 0) speed = (short) -speed; // NEG.W $8000 remains negative.
            reverse = speed < 0;
        }
        int offset = -(columnsMinus1 << 3), xStep = 16, scriptStep = 1;
        if (reverse) { at += columnsMinus1; offset = -offset; xStep = -16; scriptStep = -1; }
        int x = (c.state.centreX() + offset) & 0xFFFF, timer = 9;
        c.write(c.state.moveByte(COLLAPSE_SAVED_SOURCE_ID, c.state.id()));
        for (int count = columnsMinus1; count >= 0; count--) {
            var found = c.pool.findObjSlot();
            if (found.isEmpty()) {
                capacity(c, Site.SINGLE_CAPACITY_TOP, Site.SINGLE_CAPACITY_GET_OFF);
                delete(c, DeleteSite.SINGLE_SPLIT); return;
            }
            int whole = found.getAsInt(); c.effects.add(new SlotFound(whole));
            var child = c.pool.state(whole);
            // Native :278-289 stores precede the actual script read. Dirty X/frame/delay survive a read stop.
            child = child.moveByte(PRIORITY, 3).moveWord(TILE, TILE_WORD).moveByte(SPRITE_FLAGS, child.spriteFlags() | 4)
                    .moveLong(MAPPINGS, assets.mappings4()).moveLong(COLLAPSE_FALL_DELTA, 0x20000)
                    .moveByte(ID, c.state.unsignedByte(COLLAPSE_SAVED_SOURCE_ID)).moveByte(ROUTINE, c.state.routine())
                    .moveWord(Y, c.state.centreY()).moveByte(COLLAPSE_SOLID_MARKER, 0xFF)
                    .moveByte(X_RADIUS, 8).moveByte(WIDTH, 8).moveByte(Y_RADIUS, dy);
            c.pool.write(child);
            int frame = read(c, assets.sizes2(), NativeTable.SIZES_2, at, UnsupportedBoundary.SINGLE_FRAME_READ);
            child = child.moveByte(MAP_FRAME, frame); at += scriptStep;
            child = child.moveWord(X, x); x = (x + xStep) & 0xFFFF;
            child = child.moveWord(COLLAPSE_DELAY, timer); timer = (timer + 12) & 0xFFFF;
            c.pool.write(child); c.effects.add(new ChildInitialized(child));
        }
        delete(c, DeleteSite.SINGLE_SPLIT);
    }

    private void capacity(Cursor c, Site top, Site getOff) {
        if (solid(c, top).d0().orElseThrow() != 0) solid(c, getOff);
    }

    /** One actual ephemeral full-pool cut, one genuine outcome, synchronous commit, full output-image check. */
    private CdSolidObject.Result solid(Cursor c, Site site) {
        c.prepareInputs();
        var entry = entryFor(site); var cut = c.pool.capture();
        if (!cut.slots().get(c.wholeIndex - 32).equals(c.state)) throw new IllegalStateException("Stale Solid source image");
        var expectedPlayer = c.player; var expectedGlobals = c.solidGlobals;
        var outcome = sharedSolid.runWithPrefix(entry, c.state, expectedPlayer, c.gates, expectedGlobals, cut);
        CdSolidObject.Result completed = null; CdSolidObject.NativeStopped stopped = null;
        CdObjectState object; List<CdObjectState> others; CdSolidObject.Player player; CdSolidObject.Globals globals;
        if (outcome instanceof CdSolidObject.Completed done) {
            completed = done.completedResult(); object = completed.object(); others = completed.otherSlots();
            player = completed.player(); globals = completed.globals();
        } else {
            stopped = (CdSolidObject.NativeStopped) outcome; var prefix = stopped.prefix();
            object = prefix.object(); others = prefix.otherSlots(); player = prefix.player(); globals = prefix.globals();
        }
        validateImages(c, cut, object, others);
        assertSourceCut(c.pool, cut); // Pure shared owner must not have changed ANY live source image.
        var call=new SolidCall(site,entry,cut,expectedPlayer,expectedGlobals,outcome);pendingSolid=call;
        try{commit.commit(call);assertPublished(c.pool,cut,object,others);observePublication(call);}
        catch(RuntimeException|Error original){
            try{if(!c.pool.capture().slots().equals(cut.slots()))assertPublished(c.pool,cut,object,others);}
            catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;
        }finally{pendingSolid=null;}
        assertPublished(c.pool, cut, object, others); // Includes unchanged ID-zero images and all 64 bytes.
        c.state = c.pool.state(c.wholeIndex); c.player = player; c.solidGlobals = globals;
        if (stopped != null) throw stopped.stop().originalException();
        c.effects.add(new Solid(site, completed)); return completed;
    }

    private static void validateImages(Cursor c, CdObjectPool.Snapshot cut, CdObjectState object, List<CdObjectState> others) {
        c.pool.validateRestore(cut); c.pool.validateWrite(object);
        if (!object.identity().equals(c.state.identity())) throw new IllegalArgumentException("Solid changed a0 physical identity");
        var seen = new HashSet<Integer>(); seen.add(c.wholeIndex);
        for (var other : others) {
            c.pool.validateWrite(other);
            if (!seen.add(other.identity().wholeIndex())) throw new IllegalArgumentException("Duplicate/aliased Solid output slot");
        }
    }
    private static void assertSourceCut(CdObjectPool pool, CdObjectPool.Snapshot cut) {
        for (var source : cut.slots()) {
            if (!source.equals(pool.state(source.identity().wholeIndex()))) throw new IllegalStateException("Solid changed its live source cut");
        }
    }
    private static void assertPublished(CdObjectPool pool, CdObjectPool.Snapshot cut, CdObjectState object, List<CdObjectState> others) {
        for (var source : cut.slots()) {
            int whole = source.identity().wholeIndex(); CdObjectState expected = source;
            if (whole == object.identity().wholeIndex()) expected = object;
            else for (var other : others) if (whole == other.identity().wholeIndex()) { expected = other; break; }
            if (!expected.equals(pool.state(whole))) throw new IllegalStateException("Solid commit output image mismatch at slot " + whole);
        }
    }

    /** Signed relative WORD, read raw at the actual index; no eager admission, modulo, padding or fallback. */
    private static int directory(Cursor c, CdAssetBytes bytes, NativeTable table, int index) {
        int at = index + index;
        if (at < 0 || at > bytes.length() - 2) {
            throw new UnsupportedCollapsePlatform(UnsupportedBoundary.SIZE_DIRECTORY_READ, c.wholeIndex, table, at);
        }
        return (short) bytes.unsignedWord(at);
    }
    private static int read(Cursor c, CdAssetBytes bytes, NativeTable table, int at, UnsupportedBoundary boundary) {
        if (at < 0 || at >= bytes.length()) throw new UnsupportedCollapsePlatform(boundary, c.wholeIndex, table, at);
        return bytes.unsignedByte(at);
    }
    private static void delete(Cursor c, DeleteSite site) {
        c.pool.deleteObject(c.wholeIndex); c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted(site));
    }

    /** Existing saved-bank primitives; Object Spawner.asm:385-446, reached only after the live signed tail gate. */
    private boolean checkObjDespawn(CdObjectState state, DespawnGlobals globals) {
        int x = state.centreX(); // Native reads live X before the sprite sign branch.
        Objects.requireNonNull(globals, "reached CheckObjDespawn globals");
        boolean outside = state.signedByte(SPRITE_FLAGS) >= 0
                && (((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean warp = (globals.timeZone() & 0x80) != 0;
        if (!outside && !warp) return false;
        int savedId = state.savedFlagsId();
        if (savedId != 0) {
            int period = globals.timeZone() & 0x7F;
            if (warp) {
                int remapped = (period - (byte) globals.timeWarpDirection()) & 0xFFFF;
                period = (short) remapped < 0 ? 0 : remapped < 3 ? remapped : 2;
            }
            // The existing bounded bank owns reached address rejection. No early raw-period validation/reset.
            placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED);
        }
        placement.pool().deleteObject(state.identity().wholeIndex()); return true;
    }

    private static CdSolidObject.Entry entryFor(Site site) {
        return switch (site) {
            case MAIN_TOP, DELAY_TOP, MULTI_CAPACITY_TOP, SINGLE_CAPACITY_TOP -> CdSolidObject.Entry.TOP_SOLID_OBJECT;
            case MAIN_GET_OFF, DELAY_GET_OFF, MULTI_CAPACITY_GET_OFF, SINGLE_CAPACITY_GET_OFF -> CdSolidObject.Entry.GET_OFF_OBJECT;
        };
    }

    /** Invocation-local values only; no retained captures, completed outcomes or independent Snapshot owner. */
    private static final class Cursor {
        final CdObjectPool pool; final int wholeIndex; final Inputs inputs;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state; CdSolidObject.Player player; CdSolidObject.Gates gates; CdSolidObject.Globals solidGlobals;
        Cursor(CdObjectPool pool, CdObjectState state, Inputs inputs) {
            this.pool = pool; this.state = state; this.inputs = inputs; wholeIndex = state.identity().wholeIndex();
        }
        void write(CdObjectState next) { pool.write(next); state = next; }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        CdSolidObject.Player player() {
            if (player == null) player = Objects.requireNonNull(inputs, "reached primary player inputs").player();
            return player;
        }
        void prepareInputs() {
            player();
            if (gates == null) gates = inputs.gates();
            if (solidGlobals == null) solidGlobals = inputs.globals();
        }
    }
    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException(name + " must be an unsigned native byte");
    }
    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException(name + " must be an unsigned native word");
    }
}
