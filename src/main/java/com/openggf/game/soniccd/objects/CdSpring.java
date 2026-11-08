package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectTileIds;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.*;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/**
 * ObjSpring $0A, ordinary primary up/side/down/diagonal and ObjSpring2 player mirror. Native source:
 * Level/_Objects/Spring/Main.asm:98-481, scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Shipped FixBugs=0 ordering; these routines have no FixBugs alternative. The diagonal init dimensions
 * follow the USA/non-demo retail branch (:209-214); diagonal art/residency remains an external owner.
 * MovingSpring $08 itself is outside this owner.
 *
 * <p>One run is one RunObjects dispatch. Routine0 runs even before any onscreen flag exists and returns
 * without Main. Nonzero routines with sprite bit7 clear skip the control table, including its lookup.
 * Normal control returns to DrawObject BEFORE following a parent and checking the anchor's $280 window.
 * DrawObject queues a physical slot reference: later SAT construction reads the final live slot, not
 * the earlier culling position. The routine2==5 mirror bypasses that entire ordinary wrapper.</p>
 *
 * <p>Each source Solid wrapper publishes working a0, captures ONE live pool cut, calls the shared pure
 * Solid owner and synchronously commits its result through Root's S4/S3 port. No Solid result or effect
 * is replayed. Spring's following action has a separate typed LaunchCommit after its object prefix is
 * live, then the dedicated FM_SPRING port. Down and diagonal fall through from the returning sound call into
 * AnimateObject in the same dispatch. Root owns phase/CAS validation and sound forwarding.</p>
 *
 * <p>The additive original R31 entry uses finite199/14 DATA and the existing shared
 * animation Prefix. Its opaque original occurrences bracket source stores/reads/Solid/launch/FM/
 * Draw/delete, with all live authority retained by the actual World caller. Legacy APIs stay intact.</p>
 *
 * <p>No Player, Globals or Snapshot is retained between invocations. Source/port failures propagate
 * with already committed prefixes intact. There is no rollback, physics/probe/kill duplication,
 * route workaround, art fallback, snapshot hydration or World/Frame dependency.</p>
 */
public final class CdSpring {
    public static final int OBJECT_ID = 0x0A;

    public enum NativeRoutine {
        INIT, MAIN_UP, ANIM_UP, RESET_UP, MAIN_SIDE, ANIM_SIDE, RESET_SIDE,
        MAIN_DOWN, ANIM_DOWN, RESET_DOWN, MAIN_DIAG, ANIM_DIAG, RESET_DIAG, MIRROR_INIT, MIRROR_MAIN
    }
    public enum NativeCall { ANIMATE_OBJECT, DRAW_OBJECT }
    /** Semantic sound request; Root's sound owner supplies the release-specific native ID. */
    public enum FmSound { FM_SPRING }

    /** Native launch stores only; CENTRE_X/Y are INTEGER WORD stores, never their fraction words. */
    public enum LaunchPlayerField {
        CENTRE_X(0x08), CENTRE_Y(0x0C), X_VELOCITY(0x10), Y_VELOCITY(0x12), GROUND_VELOCITY(0x14),
        ANIMATION(0x1C), FLAGS(0x22), ANGLE(0x26), MOVE_LOCK(0x3E);
        private final int offset;
        LaunchPlayerField(int offset) { this.offset = offset; }
        public int byteOffset() { return offset; }
    }

    /** Completed observations only. All publication/sound work has already occurred; never replay these. */
    public sealed interface Effect permits RoutineEntered, Solid, Launched, PlayedFm, CallReturned,
            FollowedParent, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    public record Solid(CdSolidObject.Result result) implements Effect {
        public Solid { Objects.requireNonNull(result); }
    }
    public record Launched(LaunchCall call) implements Effect {
        public Launched { Objects.requireNonNull(call); }
    }
    public record PlayedFm(FmSound sound) implements Effect {
        public PlayedFm { Objects.requireNonNull(sound); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    public record FollowedParent(int parentAddress24) implements Effect { }
    /** Plain DeleteObject at the wrapper's BHI; no saved-placement flag is cleared. */
    public record Deleted() implements Effect { }

    /** Pre-scroll native cameraX word; no camera/picture/route policy belongs here. */
    public record Globals(int cameraXWord) {
        public Globals { wordValue(cameraXWord); }
    }
    /** Explicit post-player invocation cut. Each completed call's outputs replace these staged values. */
    public record SolidInputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public SolidInputs { Objects.requireNonNull(player); Objects.requireNonNull(gates); Objects.requireNonNull(globals); }
    }

    /**
     * Three authentic visual families retain the complete 200-byte include and real 14-byte script.
     * Roots0/6/80 and three-frame indices remain distinct. Speed4 and the genuine ObjectTileIDs72 Past
     * or ObjectTileIDs74 Present includes remain native source bytes, never padding or fitted selector results.
     * Declaration alone authenticates no media; the named aggregate factories own authenticated ingress.
     */
    public record Assets(CdObjectVisualAssets vertical, CdObjectVisualAssets horizontal,
                         CdObjectVisualAssets diagonal, int mappingsAddress, CdAssetBytes speedWords,
                         CdAssetBytes objectTileIds) {
        public Assets {
            Objects.requireNonNull(vertical); Objects.requireNonNull(horizontal); Objects.requireNonNull(diagonal);
            Objects.requireNonNull(speedWords); Objects.requireNonNull(objectTileIds);
            if (vertical.mappingTableOffset() != 0 || horizontal.mappingTableOffset() != 6
                    || diagonal.mappingTableOffset() != 80
                    || vertical.mappingFrames().size() != 3 || horizontal.mappingFrames().size() != 3
                    || diagonal.mappingFrames().size() != 3
                    || vertical.animations().size() != 1 || horizontal.animations().size() != 1
                    || diagonal.animations().size() != 1
                    || vertical.mappings().length() != 200 || horizontal.mappings().length() != 200
                    || diagonal.mappings().length() != 200
                    || vertical.animationTable().length() != 14 || horizontal.animationTable().length() != 14
                    || diagonal.animationTable().length() != 14
                    || !Arrays.equals(vertical.mappings().copy(), horizontal.mappings().copy())
                    || !Arrays.equals(vertical.mappings().copy(), diagonal.mappings().copy())
                    || !Arrays.equals(vertical.animationTable().copy(), horizontal.animationTable().copy())
                    || !Arrays.equals(vertical.animationTable().copy(), diagonal.animationTable().copy())
                    || speedWords.length() != 4 || (objectTileIds.length() != 72 && objectTileIds.length() != 74)) {
                throw new IllegalArgumentException("Spring needs three independent roots, full includes, speed4 and native ObjectTileIDs72/74");
            }
            if (mappingsAddress < 0 || mappingsAddress > 0xFFFFFF - 199) {
                throw new IllegalArgumentException("Spring mapping source exceeds its native 24-bit address extent");
            }
        }

        /** Authenticated USA owner ingress. No raw media, source file, script synthesis or decoded-art dependency. */
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner);
            return new Assets(owner.springVertical(), owner.springHorizontal(), owner.springDiagonal(),
                    owner.view(CdR11aObjectAssets.Table.SPRING_MAPPINGS).range().nativeAddress(),
                    owner.view(CdR11aObjectAssets.Table.SPRING_SPEEDS).bytes(),
                    owner.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes());
        }

        /**
         * Actual authenticated USA Past aggregate ingress. All four views belong to the original R11B;
         * the genuine 72-byte tile include ends before Animal code. No Present facade or art decode is used.
         */
        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner);
            var identity = Objects.requireNonNull(owner.identity());
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R11B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R11B.stage())) {
                throw new IllegalArgumentException("Spring Past assets require authenticated original USA R11B Word RAM");
            }
            var mappings = owner.view(CdR11bAssets.Asset.MAP_SPRING);
            var scripts = owner.view(CdR11bAssets.Asset.ANI_SPRING).bytes();
            return new Assets(CdObjectVisualAssets.decodeIndexed(mappings.bytes().copy(), 0, 3, 12, 46,
                            scripts.copy(), 1, 36),
                    CdObjectVisualAssets.decodeIndexed(mappings.bytes().copy(), 6, 3, 46, 80,
                            scripts.copy(), 1, 36),
                    CdObjectVisualAssets.decodeIndexed(mappings.bytes().copy(), 80, 3, 86, 200,
                            scripts.copy(), 1, 46),
                    mappings.range().nativeAddress(), owner.view(CdR11bAssets.Asset.SPRING_SPEEDS).bytes(),
                    owner.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes());
        }

        /**
         * Genuine original-USA R12B metadata only. Its retained 200-byte parent span, independent
         * roots0/6/80, real script and unpadded Past72 tile table use the existing pure decoders.
         * Native Spring/Main.asm:186-244,492-495 at N1ecc5f3a; no mapping/art service or current bank.
         */
        public static Assets fromAct2PastAssets(CdR12bTerrainAssets owner) {
            Objects.requireNonNull(owner, "actual R12B terrain");
            var identity = Objects.requireNonNull(owner.identity());
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12B.stage())) {
                throw new IllegalArgumentException("Spring Act2 Past assets require authenticated original USA R12B Word RAM");
            }
            var objects = owner.objects();
            return new Assets(objects.springVertical(), objects.springHorizontal(), objects.springDiagonal(),
                    CdR12bObjectAssets.View.MAP_SPRING.range().nativeAddress(),
                    objects.view(CdR12bObjectAssets.View.SPRING_SPEEDS),
                    objects.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS));
        }

        /** Only primary diagonal selector $F/subtype2 zero is owned for the genuine Past72 format. */
        private int diagonalTileWord(int subtype2) {
            if (objectTileIds.length() == 74) {
                return CdR11aObjectAssets.decodeTileWord(objectTileIds.copy(), 0xF, subtype2);
            }
            if (subtype2 != 0) {
                throw new UnsupportedOperationException("Only primary diagonal Spring selector $F at subtype2 0 is reviewed");
            }
            int end = CdObjectTileIds.endOfBody(objectTileIds, 0xF);
            int target = (short) objectTileIds.unsignedWord(0xF * 2);
            if (end - target < 2) {
                throw new IllegalArgumentException("Primary diagonal Spring tile word crosses its selected body");
            }
            return objectTileIds.unsignedWord(target);
        }

        /** ANDI.W #2 / MOVE.W ObjSpring_Speeds(pc,d0.w), Spring Main:237-244. */
        public int speedWord(int subtypeByte) {
            if ((subtypeByte & ~0xFF) != 0) throw new IllegalArgumentException("Spring subtype exceeds a native byte");
            return (short) speedWords.unsignedWord(subtypeByte & 2);
        }
    }

    /** One real call cut and its pure result, not a capture retained by the body or a deferred command. */
    public record SolidCall(CdObjectPool.Snapshot callCut, CdSolidObject.Player expectedPlayer,
                            CdSolidObject.Globals expectedGlobals, CdSolidObject.Result result) {
        public SolidCall {
            Objects.requireNonNull(callCut); Objects.requireNonNull(expectedPlayer);
            Objects.requireNonNull(expectedGlobals); Objects.requireNonNull(result);
        }
    }
    /**
     * Root synchronously validates the expected player/globals and every affected live slot against callCut,
     * publishes result.object/otherSlots through S4, applies exactly result's player/global write sets through
     * S3, and forwards its sound calls once. Reject stale or closed phases. Exceptions close composition;
     * a callback must not dispatch another body or retain this call for later replay.
     */
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }

    /**
     * Native action patch after Solid: exact native stores INCLUDING same-value stores. The object prefix
     * is already live at identity. This is deliberately separate from Solid metadata; no invented Result.
     * Preserve both position fractions and every field outside the declared sets. Globals are unchanged
     * by all four actions, with an empty global write set. Sets are immutable detached values.
     */
    public record LaunchCall(CdObjectState.Identity identity, CdSolidObject.Player expectedPlayer,
                             CdSolidObject.Globals expectedGlobals, CdSolidObject.Player finalPlayer,
                             CdSolidObject.Globals finalGlobals, Set<LaunchPlayerField> playerWrites,
                             Set<CdSolidObject.GlobalField> globalWrites) {
        public LaunchCall {
            Objects.requireNonNull(identity); Objects.requireNonNull(expectedPlayer); Objects.requireNonNull(expectedGlobals);
            Objects.requireNonNull(finalPlayer); Objects.requireNonNull(finalGlobals);
            playerWrites = Set.copyOf(playerWrites); globalWrites = Set.copyOf(globalWrites);
        }
    }
    /**
     * Root's narrow guarded value projection: validate current S3 against both expected values, require
     * the open object phase/identity, then publish exactly the declared native stores once. MOVE_LOCK is
     * motion.moveLock at $3E (a WORD), not the player's unrelated invincibility timer at $32. The existing
     * shared Solid PlayerField lacks this store, hence this Spring-local typed set. No Frame helper required.
     */
    @FunctionalInterface public interface LaunchCommit { void commit(LaunchCall call); }
    /** DrawObject's synchronous cull/enqueue cut. Reload any flag writes, then follow the parent. */
    @FunctionalInterface public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity identity);
    }
    /** Source PlayFMSound call after a successful launch commit; failure prevents subsequent native work. */
    @FunctionalInterface public interface PlayFmCall { void playFm(FmSound sound); }

    public record Result(CdObjectState state, CdSolidObject.Player finalPlayer, CdSolidObject.Globals finalGlobals,
                         List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state); Objects.requireNonNull(finalPlayer); Objects.requireNonNull(finalGlobals);
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, MIRROR_ROUTINE_DISPATCH,
        /** Init prefix is already live; reached selector $F/subtype2 is outside the owned tile-table contract. */
        DIAGONAL_TILE_SELECTOR,
        /** First actual parent oX WORD read addresses an unowned reserved/out-of-pool/unaligned image. */
        PARENT_READ
    }
    public static final class UnsupportedSpring extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private final OptionalInt readAddress24;
        private UnsupportedSpring(UnsupportedBoundary boundary, int wholeIndex, int value, OptionalInt readAddress24) {
            super(boundary + " at Spring whole slot " + wholeIndex + ", reached value $" + Integer.toHexString(value)
                    + (readAddress24.isPresent() ? ", native read $" + Integer.toHexString(readAddress24.getAsInt()) : ""));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value; this.readAddress24 = readAddress24;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
        public OptionalInt readAddress24() { return readAddress24; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final SourceProfile assets;
    private final CdPlayerMath math;
    private final CdSolidObject solid;
    private final SolidCommit solidCommit;
    private final LaunchCommit launchCommit;
    private final DrawObjectCall drawObject;
    private final PlayFmCall playFm;

    public CdSpring(CdObjectPlacementRuntime runtime, Assets assets, CdPlayerMath math, CdSolidObject solid,
                    SolidCommit solidCommit, LaunchCommit launchCommit, DrawObjectCall drawObject, PlayFmCall playFm) {
        this.runtime = Objects.requireNonNull(runtime); this.assets = new SourceProfile(Objects.requireNonNull(assets));
        this.math = Objects.requireNonNull(math);
        this.solid = Objects.requireNonNull(solid); this.solidCommit = Objects.requireNonNull(solidCommit);
        this.launchCommit = Objects.requireNonNull(launchCommit); this.drawObject = Objects.requireNonNull(drawObject);
        this.playFm = Objects.requireNonNull(playFm);
    }

    /**
     * Explicit R12B component ingress. Pair the actual retained layout before obtaining metadata or
     * touching any caller port. Root still owns the real player/math/Solid bindings, invocation and draw.
     * Every existing constructor and native body algorithm retains its original contract unchanged.
     */
    public static CdSpring fromAct2PastAssets(CdObjectPlacementRuntime runtime, CdR12bTerrainAssets owner,
                                              CdPlayerMath math, CdSolidObject solid, SolidCommit solidCommit,
                                              LaunchCommit launchCommit, DrawObjectCall drawObject, PlayFmCall playFm) {
        Objects.requireNonNull(runtime, "placement"); Objects.requireNonNull(owner, "actual R12B terrain");
        if (!runtime.sameAct2PastSource(owner)) {
            throw new IllegalArgumentException("Spring/placement retained R12B source mismatch or constructed placement");
        }
        return new CdSpring(runtime, Assets.fromAct2PastAssets(owner), math, solid, solidCommit, launchCommit, drawObject, playFm);
    }

    // BEGIN R13 FINITE SPRING PROFILE 20261106
    /** Private format split: the public legacy Assets constructor remains strict200/72-or74. */
    /** Uniform lazy declarative metadata; retains every old selected-read boundary. */
    private static final class SourceProfile {
        final Supplier<CdObjectVisualAssets> v,h,d;final IntSupplier address;final IntUnaryOperator speed,tile;
        SourceProfile(Assets legacy){v=legacy::vertical;h=legacy::horizontal;d=legacy::diagonal;address=legacy::mappingsAddress;speed=legacy::speedWord;tile=legacy::diagonalTileWord;}
        SourceProfile(com.openggf.game.soniccd.level.CdR13ObjectAssets future){
            Objects.requireNonNull(future);
            if(future.springMappings().length()!=199||future.view(com.openggf.game.soniccd.level.CdR13ObjectAssets.View.OBJECT_TILE_IDS).length()!=68||future.view(com.openggf.game.soniccd.level.CdR13ObjectAssets.View.SPRING_SPEEDS).length()!=4)
                throw new IllegalArgumentException("R13 Spring requires genuine199/68/4 metadata");
            v=future::springVertical;h=future::springHorizontal;d=future::springDiagonal;
            address=()->com.openggf.game.soniccd.level.CdR13ObjectAssets.View.MAP_SPRING.range(future.identity().file()).nativeAddress();
            speed=subtype->{if((subtype&~255)!=0)throw new IllegalArgumentException("Spring subtype exceeds a native byte");return(short)future.view(com.openggf.game.soniccd.level.CdR13ObjectAssets.View.SPRING_SPEEDS).unsignedWord(subtype&2);};
            tile=subtype->{if(subtype!=0)throw new UnsupportedOperationException("Only primary diagonal Spring selector $F at subtype2 0 is reviewed");var bytes=future.view(com.openggf.game.soniccd.level.CdR13ObjectAssets.View.OBJECT_TILE_IDS);int target=(short)bytes.unsignedWord(30);if(CdObjectTileIds.endOfBody(bytes,15)-target<2)throw new IllegalArgumentException("R13 diagonal tile read exceeds its actual selected body");return bytes.unsignedWord(target);};
        }
        SourceProfile(FiniteProfile p){v=p::vertical;h=p::horizontal;d=p::diagonal;address=p::mappingsAddress;speed=subtype->{if((subtype&~255)!=0)throw new IllegalArgumentException("Spring subtype exceeds a native byte");return(short)p.speedWords().unsignedWord(subtype&2);};tile=subtype->p.diagonalTileWord();}
        CdObjectVisualAssets vertical(){return v.get();}CdObjectVisualAssets horizontal(){return h.get();}CdObjectVisualAssets diagonal(){return d.get();}
        int mappingsAddress(){return address.getAsInt();}int speedWord(int subtype){return speed.applyAsInt(subtype);}int diagonalTileWord(int subtype){return tile.applyAsInt(subtype);}
    }
    /** Semantic finite199/14/speed4 metadata with an actual constant tile-helper result.
     * DATA equality authenticates neither media nor an original invocation. Legacy Assets stays200. */
    public record FiniteProfile(CdObjectVisualAssets vertical,CdObjectVisualAssets horizontal,CdObjectVisualAssets diagonal,
            int mappingsAddress,CdAssetBytes speedWords,int diagonalTileWord){
        public FiniteProfile{Objects.requireNonNull(vertical);Objects.requireNonNull(horizontal);Objects.requireNonNull(diagonal);Objects.requireNonNull(speedWords);
            if(vertical.mappingTableOffset()!=0||horizontal.mappingTableOffset()!=6||diagonal.mappingTableOffset()!=80||speedWords.length()!=4||mappingsAddress<0||mappingsAddress>0xFFFFFF-198||(diagonalTileWord&~65535)!=0)
                throw new IllegalArgumentException("Finite Spring roots/words differ");
            for(var visual:List.of(vertical,horizontal,diagonal))if(visual.mappings().length()!=199||visual.animationTable().length()!=14||visual.mappingFrames().size()!=3||visual.animations().size()!=1||!Arrays.equals(vertical.mappings().copy(),visual.mappings().copy())||!Arrays.equals(vertical.animationTable().copy(),visual.animationTable().copy()))throw new IllegalArgumentException("Finite Spring needs actual199/14 includes");
        }
    }
    private CdSpring(CdObjectPlacementRuntime runtime, SourceProfile profile, CdPlayerMath math, CdSolidObject solid,
                     SolidCommit solidCommit, LaunchCommit launchCommit, DrawObjectCall drawObject, PlayFmCall playFm, boolean actualFuture) {
        if(!actualFuture)throw new IllegalArgumentException("Private Spring future profile");
        this.runtime=Objects.requireNonNull(runtime);this.assets=Objects.requireNonNull(profile);this.math=Objects.requireNonNull(math);this.solid=Objects.requireNonNull(solid);
        this.solidCommit=Objects.requireNonNull(solidCommit);this.launchCommit=Objects.requireNonNull(launchCommit);this.drawObject=Objects.requireNonNull(drawObject);this.playFm=Objects.requireNonNull(playFm);
    }
    public static CdSpring fromAct3FutureAssets(CdObjectPlacementRuntime runtime, com.openggf.game.soniccd.level.CdR13FutureAssets owner,
            com.openggf.game.soniccd.level.CdR13ObjectAssets objects, CdPlayerMath math, CdSolidObject solid,
            SolidCommit solidCommit, LaunchCommit launchCommit, DrawObjectCall drawObject, PlayFmCall playFm) {
        Objects.requireNonNull(runtime);Objects.requireNonNull(owner);Objects.requireNonNull(objects);
        if(!runtime.ownsAct3FutureAssets(owner)||!objects.ownsAssets(owner))throw new IllegalArgumentException("Spring requires the exact retained R13 source/placement");
        return new CdSpring(runtime,new SourceProfile(objects),math,solid,solidCommit,launchCommit,drawObject,playFm,true);
    }
    public record PrefixSolidCall(CdObjectPool.Snapshot cut,CdSolidObject.Player player,CdSolidObject.Globals globals,CdSolidObject.Outcome outcome){public PrefixSolidCall{Objects.requireNonNull(cut);Objects.requireNonNull(player);Objects.requireNonNull(globals);Objects.requireNonNull(outcome);}}
    public enum Site { STORE,DELETE,SOLID,LAUNCH,SOUND,DRAW,PARENT_X,PARENT_Y,CAMERA_X,ANIMATION,SOLID_OWNER }
    /** Only this cached producer issues an occurrence; completed values cannot issue another. */
    public static final class OriginalCall {
        private final CdSpring owner;private final Thread thread=Thread.currentThread();private final long ordinal;private final Site site;
        private final CdObjectPlacementRuntime.Snapshot cut;private final CdObjectState source,output;private final Object payload;private boolean completed;
        private OriginalCall(CdSpring owner,long ordinal,Site site,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,CdObjectState output,Object payload){this.owner=owner;this.ordinal=ordinal;this.site=site;this.cut=cut;this.source=source;this.output=output;this.payload=payload;}
        public long ordinal(){return ordinal;}public Site site(){return site;}public CdObjectPlacementRuntime.Snapshot cut(){return cut;}
        public CdObjectState source(){return source;}public CdObjectState output(){return output;}public Object payload(){return payload;}public boolean completed(){return completed;}
    }
    public interface LivePorts {void checkSolid(OriginalCall call,CdSolidObject.Entry entry);int read(OriginalCall call);void solid(OriginalCall call,PrefixSolidCall solid);void launch(OriginalCall call,LaunchCall launch);void sound(OriginalCall call,FmSound sound);void draw(OriginalCall call);}
    public interface NativeObserver {void before(OriginalCall call);void after(OriginalCall call);}
    public interface ReceiptGuard {void prepared(OriginalCall call);void completed(OriginalCall call);}
    private LivePorts livePorts;private NativeObserver observer;private ReceiptGuard receiptGuard;
    private boolean running;private long ordinal;private OriginalCall pending;private CdObjectAnimation.Prefix liveAnimation;
    public boolean isCurrentCall(OriginalCall call){return livePorts!=null&&running&&call!=null&&pending==call&&call.owner==this&&call.thread==Thread.currentThread()&&(call.site!=Site.ANIMATION||liveAnimation!=null&&liveAnimation.isCurrent((CdObjectAnimation.Operation)call.payload));}
    public static CdSpring fromR31aAssets(CdObjectPlacementRuntime runtime,com.openggf.game.soniccd.level.CdR31aAssets source,
            com.openggf.game.soniccd.level.CdR31aObjectAssets objects,CdPlayerMath math,CdSolidObject solid,LivePorts ports,NativeObserver observer,ReceiptGuard guard){
        Objects.requireNonNull(runtime);Objects.requireNonNull(source);Objects.requireNonNull(objects);
        if(!runtime.ownsR31aAssets(source)||!objects.ownsAssets(source))throw new IllegalArgumentException("Spring requires actual retained R31A source/placement");
        var body=new CdSpring(runtime,new SourceProfile(objects.springAssets()),math,solid,c->{throw new IllegalStateException("Original prefix Solid required");},c->{throw new IllegalStateException("Original launch required");},(p,id)->{throw new IllegalStateException("Original Draw required");},sound->{throw new IllegalStateException("Original FM required");},true);
        body.livePorts=Objects.requireNonNull(ports);body.observer=Objects.requireNonNull(observer);body.receiptGuard=Objects.requireNonNull(guard);return body;
    }
    private int original(Cursor c,Site site,CdObjectState output,Object payload,IntSupplier action){
        if(pending!=null)throw new IllegalStateException("Nested original Spring call");
        var call=new OriginalCall(this,++ordinal,site,runtime.capture(),c.pool.state(c.wholeIndex),output,payload);pending=call;
        try{observer.before(call);receiptGuard.prepared(call);int result=action.getAsInt();call.completed=true;observer.after(call);receiptGuard.completed(call);return result;}finally{pending=null;}
    }
    private void store(Cursor c){if(livePorts==null)c.pool.write(c.state);else original(c,Site.STORE,c.state,null,()->{c.pool.write(c.state);return 0;});}
    private void delete(Cursor c){if(livePorts==null)c.pool.deleteObject(c.wholeIndex);else original(c,Site.DELETE,null,null,()->{c.pool.deleteObject(c.wholeIndex);return 0;});}
    private int read(Cursor c,Site site){int value=original(c,site,null,null,()->livePorts.read(pending));if((value&~65535)!=0)throw new IllegalArgumentException("Spring native WORD read");return value;}
    // END R13 FINITE SPRING PROFILE 20261106

    /** ObjSpring (:130-169), including the reached control-table lookup and the special ObjSpring2 branch. */
    public Result run(int wholeIndex, Globals globals, SolidInputs inputs) {
        if(livePorts!=null&&running)throw new IllegalStateException("Reentrant original Spring dispatch");running=true;ordinal=0;
        try{return runSelected(wholeIndex,globals,inputs);}finally{running=false;pending=null;liveAnimation=null;}
    }
    private Result runSelected(int wholeIndex,Globals globals,SolidInputs inputs){
        Objects.requireNonNull(globals); Objects.requireNonNull(inputs);
        var pool = runtime.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Spring dispatch requires live ID $0A, slot " + wholeIndex);
        var c = new Cursor(this,pool, state, inputs);
        if (state.unsignedByte(ROUTINE_2) == 5) {
            mirror(c); // CMPI.B #5,oRoutine2 / BEQ ObjSpring2: no ordinary wrapper or offscreen gate.
        } else {
            if (state.routine() == 0 || (state.spriteFlags() & 0x80) != 0) control(c);
            draw(c);                                                     // BSR.W DrawObject BEFORE following a1.
            followParent(c);
            int d0 = c.state.word(SPRING_ANCHOR_X) & 0xFF80;
            int d1 = (((livePorts==null?globals.cameraXWord():read(c,Site.CAMERA_X)) - 0x80) & 0xFFFF) & 0xFF80;
            if (((d0 - d1) & 0xFFFF) > 0x280) {                           // CMPI.W #$280 / BHI, equality keeps.
                delete(c);
                c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted());
            }
        }
        return new Result(c.state, c.player, c.globals, c.effects);
    }

    private void control(Cursor c) {
        switch (c.state.routine()) {
            case 0 -> init(c);
            case 2 -> mainUp(c);
            case 4 -> animate(c, NativeRoutine.ANIM_UP);
            case 6 -> reset(c, NativeRoutine.RESET_UP, true);
            case 8 -> mainSide(c);
            case 10 -> animate(c, NativeRoutine.ANIM_SIDE);
            case 12 -> reset(c, NativeRoutine.RESET_SIDE, false);
            case 14 -> mainDown(c);
            case 16 -> animate(c, NativeRoutine.ANIM_DOWN);
            case 18 -> reset(c, NativeRoutine.RESET_DOWN, false);
            case 20 -> mainDiag(c);
            case 22 -> animate(c, NativeRoutine.ANIM_DIAG);
            case 24 -> reset(c, NativeRoutine.RESET_DIAG, false);
            default -> throw c.unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c.state.routine());
        }
    }

    /** ObjSpring_Init (:186-239). RTS is before the speed table: no fall-through into Main_Up. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveLong(MAPPINGS, assets.mappingsAddress()).moveWord(TILE, 0x520)
                .moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4).moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 8)
                .moveWord(SPRING_ANCHOR_X, c.state.centreX()).moveByte(PRIORITY, 4);
        int subtype = c.state.subtype();
        if ((subtype & 4) != 0) {                                        // bit2 has priority over bit3 and vertical flip.
            c.state = c.state.moveByte(ROUTINE, 8).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 0x10)
                    .moveLong(MAPPINGS, assets.mappingsAddress() + 6);
        } else if ((subtype & 8) != 0) {
            c.state = c.state.moveByte(ROUTINE, 0x14).moveByte(WIDTH, 0x18).moveByte(Y_RADIUS, 0x0C)
                    .moveLong(MAPPINGS, assets.mappingsAddress() + 80);
            c.publish();                                                 // MOVEQ #$F / JSR SetObjectTileID is reached NOW.
            int tile;
            try {
                tile = assets.diagonalTileWord(c.state.unsignedByte(SUBTYPE2));
            } catch (UnsupportedOperationException unsupported) {
                throw c.unsupported(UnsupportedBoundary.DIAGONAL_TILE_SELECTOR, 0xF);
            }
            c.state = c.state.moveWord(TILE, tile);                       // Selector return precedes palette/speed.
        } else if ((c.state.spriteFlags() & 2) != 0) {
            c.state = c.state.moveByte(ROUTINE, 0x0E).moveByte(FLAGS, c.state.flags() | 2);
        }
        if ((subtype & 2) != 0) c.state = c.state.moveWord(TILE, c.state.word(TILE) | 0x2000); // BSET high byte bit5.
        c.state = c.state.moveWord(SPRING_SPEED, assets.speedWord(subtype));
        c.publish();
    }

    /** ObjSpring_SolidObject/2/3 (:248-252,296-300,362-368): d3/d4 loads are unused by shared Solid. */
    private CdSolidObject.Result solid(Cursor c, CdSolidObject.Entry entry) {
        c.publish();
        // Owner admission for this reached original Solid call; no native value or alternate collision body.
        if(livePorts!=null)original(c,Site.SOLID_OWNER,null,entry,()->{livePorts.checkSolid(pending,entry);return 0;});
        var cut = c.pool.capture();
        CdSolidObject.Result result;
        if(livePorts==null){result=solid.run(entry,c.state,c.player,c.gates,c.globals,cut);solidCommit.commit(new SolidCall(cut,c.player,c.globals,result));}
        else{var outcome=solid.runWithPrefix(entry,c.state,c.player,c.gates,c.globals,cut);
            var nativeStop=outcome instanceof CdSolidObject.NativeStopped stopped?stopped.stop().originalException():null;
            // Retain the reached native cause before any fallible DTO/receipt/preparation/publication work.
            try{var call=new PrefixSolidCall(cut,c.player,c.globals,outcome);
                original(c,Site.SOLID,null,call,()->{livePorts.solid(pending,call);return 0;});
            }catch(RuntimeException|Error fault){if(nativeStop!=null){if(fault!=nativeStop)nativeStop.addSuppressed(fault);throw nativeStop;}throw fault;}
            if(nativeStop!=null)throw nativeStop;result=((CdSolidObject.Completed)outcome).completedResult();}
        if (!c.pool.state(c.wholeIndex).equals(result.object())) {
            throw new IllegalStateException("Spring SolidCommit did not publish a0");
        }
        for (var other : result.otherSlots()) {
            if (!c.pool.state(other.identity().wholeIndex()).equals(other)) {
                throw new IllegalStateException("Spring SolidCommit did not publish a2 slot " + other.identity().wholeIndex());
            }
        }
        c.state = result.object(); c.player = result.player(); c.globals = result.globals();
        c.effects.add(new Solid(result));
        return result;
    }

    /** ObjSpring_Main_Up/.Action (:257-277). Native BNE consumes Solid's final MOVEQ condition/d0. */
    private void mainUp(Cursor c) {
        c.enter(NativeRoutine.MAIN_UP);
        if ((c.state.spriteFlags() & 0x80) == 0) return;
        if (solid(c, CdSolidObject.Entry.SOLID_OBJECT).d0().orElseThrow() == 0) return;
        c.state = c.state.moveByte(ROUTINE, 4);
        var p = new PlayerPatch(c.player);
        p.yWord(p.yWord() + 8);
        p.yVelocity(c.state.signedWord(SPRING_SPEED));
        p.flags(p.flags | 2); p.flags(p.flags & ~8);
        p.animation(0x10);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~8);
        launch(c, p);
        sound(c);                                                        // JMP PlayFMSound; return into the wrapper.
    }

    /** ObjSpring_Main_Side/.Action (:305-343). Its trigger is object flag5, even when d0==0. */
    private void mainSide(Cursor c) {
        c.enter(NativeRoutine.MAIN_SIDE);
        if ((c.state.spriteFlags() & 0x80) == 0) return;
        solid(c, CdSolidObject.Entry.SOLID_OBJECT);
        if ((c.state.flags() & 0x20) == 0) return;
        c.state = c.state.moveByte(ROUTINE, 0x0A);
        var p = new PlayerPatch(c.player);
        p.xVelocity(c.state.signedWord(SPRING_SPEED)); p.xWord(p.xWord() + 8); p.flags(p.flags | 1);
        if ((c.state.flags() & 1) == 0) {
            p.xWord(p.xWord() - 0x10); p.xVelocity(-p.xv); p.flags(p.flags & ~1);
        }
        p.moveLock(0x0F); p.groundVelocity(p.xv);
        if ((p.flags & 4) == 0) p.animation(0);
        p.angle(0);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~0x20);
        p.flags(p.flags & ~0x20);
        launch(c, p);
        sound(c);                                                        // JMP, no same-dispatch AnimateObject.
    }

    /** ObjSpring_Main_Down/.Action (:373-396), THEN falls through to ObjSpring_Anim_Down (:400-402). */
    private void mainDown(Cursor c) {
        c.enter(NativeRoutine.MAIN_DOWN);
        if ((c.state.spriteFlags() & 0x80) == 0) return;
        if (solid(c, CdSolidObject.Entry.BTM_SOLID_OBJECT).d0().orElseThrow() == 0) return;
        c.state = c.state.moveByte(ROUTINE, 0x10);
        var p = new PlayerPatch(c.player);
        p.yWord(p.yWord() - 8); p.yVelocity(c.state.signedWord(SPRING_SPEED)); p.yVelocity(-p.yv);
        p.flags(p.flags | 2); p.flags(p.flags & ~8);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~8);
        launch(c, p);
        sound(c);                                                        // JSR PlayFMSound, then native fall-through.
        animate(c, NativeRoutine.ANIM_DOWN);
    }

    /**
     * ObjSpring_Main_Diag/.Action (:416-465): ONE shared SOLID, then d0 OR returned object flag5.
     * CalcSine comes from the injected declared player assets (Functions (Misc).asm:69-76).
     * MULU.W zero-extends both words; LSR.L #8 precedes each low-word store. No normalized trig.
     */
    private void mainDiag(Cursor c) {
        c.enter(NativeRoutine.MAIN_DIAG);
        if ((c.state.spriteFlags() & 0x80) == 0) return;
        var result = solid(c, CdSolidObject.Entry.SOLID_OBJECT);
        if (result.d0().orElseThrow() == 0 && (c.state.flags() & 0x20) == 0) return;
        c.state = c.state.moveByte(ROUTINE, 0x16);
        var sine = math.calcSine(0xE0);
        int magnitude = (-c.state.word(SPRING_SPEED)) & 0xFFFF;             // NEG.W d2.
        var p = new PlayerPatch(c.player);
        p.yVelocity((int) (((long) (sine.sine() & 0xFFFF) * magnitude) >>> 8));
        p.xVelocity((int) (((long) (sine.cosine() & 0xFFFF) * magnitude) >>> 8));
        p.yWord(p.yWord() + 8);
        if ((c.state.spriteFlags() & 2) != 0) {
            p.yWord(p.yWord() - 0x10); p.yVelocity(-p.yv);
        }
        p.flags(p.flags & ~1); p.xWord(p.xWord() - 8);
        if ((c.state.flags() & 1) != 0) {
            p.xWord(p.xWord() + 0x10); p.flags(p.flags | 1); p.xVelocity(-p.xv);
        }
        p.flags(p.flags | 2); p.flags(p.flags & ~8); p.flags(p.flags & ~0x20);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~8);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~0x20);
        launch(c, p);                                                     // Exactly X/Y integer words, XV/YV, FLAGS.
        sound(c);                                                        // JSR, so same-dispatch Ani_Spring follows.
        animate(c, NativeRoutine.ANIM_DIAG);
    }

    /** No snapshot recapture or fabricated Solid metadata: a distinct synchronous patch at its source cut. */
    private void launch(Cursor c, PlayerPatch p) {
        c.publish();
        var call = new LaunchCall(c.state.identity(), c.player, c.globals, p.player(), c.globals, p.writes, Set.of());
        if(livePorts==null)launchCommit.commit(call);else original(c,Site.LAUNCH,null,call,()->{livePorts.launch(pending,call);return 0;});
        c.player = call.finalPlayer(); c.globals = call.finalGlobals();
        c.effects.add(new Launched(call));
    }

    private void sound(Cursor c) {
        if(livePorts==null)playFm.playFm(FmSound.FM_SPRING);else original(c,Site.SOUND,null,FmSound.FM_SPRING,()->{livePorts.sound(pending,FmSound.FM_SPRING);return 0;});
        c.effects.add(new PlayedFm(FmSound.FM_SPRING));
    }

    /** All ordinary orientations use the SAME real Ani_Spring; shared AnimateObject owns FC and flip semantics. */
    private void animate(Cursor c, NativeRoutine routine) {
        c.enter(routine);
        var visual = switch (routine) {
            case ANIM_SIDE -> assets.horizontal();
            case ANIM_DIAG -> assets.diagonal();
            default -> assets.vertical();
        };
        var table = new CdObjectAnimation.AnimationTable(visual.animationTable(), 0, visual.animations().size());
        if(livePorts!=null){animateOriginal(c,table);c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));return;}
        var s = c.state;
        var out = CdObjectAnimation.step(new CdObjectAnimation.State(s.unsignedByte(ANIMATION),
                s.unsignedByte(PREVIOUS_ANIMATION), s.unsignedByte(ANIMATION_FRAME), s.unsignedByte(ANIMATION_TIME),
                s.unsignedByte(MAP_FRAME), s.spriteFlags(), s.flags(), s.routine(), s.unsignedByte(ROUTINE_2)), table);
        c.state = s.moveByte(ANIMATION, out.animationId()).moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex()).moveByte(ANIMATION_TIME, out.timer())
                .moveByte(MAP_FRAME, out.mapFrame()).moveByte(SPRITE_FLAGS, out.spriteFlags())
                .moveByte(FLAGS, out.statusFlags()).moveByte(ROUTINE, out.routine()).moveByte(ROUTINE_2, out.routine2());
        c.publish();
        c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
    }

    private void animateOriginal(Cursor c,CdObjectAnimation.AnimationTable table){
        if(pending!=null||liveAnimation!=null)throw new IllegalStateException("Nested original Spring animation");
        liveAnimation=new CdObjectAnimation.Prefix(c.pool,c.wholeIndex,new CdObjectAnimation.Guard(){
            public void before(CdObjectAnimation.Operation op){if(pending!=null||!liveAnimation.isCurrent(op))throw new IllegalStateException("Foreign animation occurrence");var source=c.pool.state(c.wholeIndex);var out=op.kind()==CdObjectAnimation.Kind.STORE?source.moveByte(op.field(),op.value()):null;
                var call=new OriginalCall(CdSpring.this,++ordinal,Site.ANIMATION,runtime.capture(),source,out,op);pending=call;observer.before(call);receiptGuard.prepared(call);}
            public void after(CdObjectAnimation.Operation op){try{var call=pending;if(call==null||call.payload!=op||!liveAnimation.isCurrent(op))throw new IllegalStateException("Foreign animation return");if(op.completed()){call.completed=true;observer.after(call);receiptGuard.completed(call);}}finally{pending=null;}}
        });
        try{liveAnimation.step(table);}finally{c.state=c.pool.state(c.wholeIndex);liveAnimation=null;}
    }
    /** ObjSpring_Reset_Up/Side/Down/Diag (:287-292,353-357,406-409,477-481); only Up clears object flag3. */
    private static void reset(Cursor c, NativeRoutine routine, boolean clearStanding) {
        c.enter(routine);
        if (clearStanding) c.state = c.state.moveByte(FLAGS, c.state.flags() & ~8);
        c.state = c.state.moveByte(PREVIOUS_ANIMATION, 1).addByte(ROUTINE, -4).moveByte(MAP_FRAME, 0);
        c.publish();
    }

    /** ObjSpring2 (:98-125), reached only through the ordinary entry's routine2==5 branch. */
    private void mirror(Cursor c) {
        int routine = c.state.routine();
        if (routine != 0 && routine != 2) throw c.unsupported(UnsupportedBoundary.MIRROR_ROUTINE_DISPATCH, routine);
        if (routine == 0) {
            c.enter(NativeRoutine.MIRROR_INIT);
            c.state = c.state.moveLong(MAPPINGS, assets.mappingsAddress()).moveWord(TILE, 0x8520)
                    .moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4).moveByte(WIDTH, 0x10)
                    .moveByte(Y_RADIUS, 8).moveByte(PRIORITY, 4).addByte(ROUTINE, 2);
        }
        c.enter(NativeRoutine.MIRROR_MAIN);
        c.state = c.state.moveWord(X, c.player.actor().motion().centreX16_16() >>> 16)
                .moveWord(Y, c.player.actor().motion().centreY16_16() >>> 16);
        draw(c);                                                         // JMP DrawObject, no ordinary wrapper tail.
    }

    private void draw(Cursor c) {
        c.publish();
        if(livePorts==null)drawObject.drawObject(c.pool,c.state.identity());else original(c,Site.DRAW,null,null,()->{livePorts.draw(pending);return 0;});
        c.state = c.pool.state(c.wholeIndex);
        c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }

    /** ObjSpring .DisplayOnly (:149-159). Native a1 = $FFFF0000 | parentWord, then signed byte offsets. */
    private static void followParent(Cursor c) {
        int parent=c.state.word(SPRING_PARENT);if(parent==0)return;
        int x=parentWord(c,parent,Site.PARENT_X);c.state=c.state.moveWord(X,x);c.publish();
        int y=parentWord(c,parent,Site.PARENT_Y);c.state=c.state.moveWord(Y,y);c.publish();
        c.state=c.state.addWord(X,c.state.signedByte(SPRING_PARENT_X_OFFSET));c.publish();
        c.state=c.state.addWord(Y,c.state.signedByte(SPRING_PARENT_Y_OFFSET));c.publish();
        c.effects.add(new FollowedParent(0xFF0000|parent));
    }
    private static int parentWord(Cursor c,int parent,Site site){
        int address=0xFF0000|parent;if(c.owner.livePorts!=null){
            if(address<0xFFD000||address>=0xFFF000||((address-0xFFD000)&63)!=0)throw new UnsupportedSpring(UnsupportedBoundary.PARENT_READ,c.wholeIndex,parent,OptionalInt.of((address+(site==Site.PARENT_X?8:12))&0xFFFFFF));
            return c.owner.read(c,site);
        }
        if(address==0xFFD000)return(site==Site.PARENT_X?c.player.actor().motion().centreX16_16():c.player.actor().motion().centreY16_16())>>>16;
        if(address>=0xFFD800&&address<0xFFF000&&((address-0xFFD000)&63)==0){var live=c.pool.state((address-0xFFD000)>>>6);return site==Site.PARENT_X?live.centreX():live.centreY();}
        throw new UnsupportedSpring(UnsupportedBoundary.PARENT_READ,c.wholeIndex,parent,OptionalInt.of((address+(site==Site.PARENT_X?8:12))&0xFFFFFF));
    }
    /** Invocation-local values only; not retained in this owner or reused after an exception. */
    private static final class Cursor {
        final CdSpring owner;final CdObjectPool pool;
        final int wholeIndex;
        final CdSolidObject.Gates gates;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        CdSolidObject.Player player;
        CdSolidObject.Globals globals;
        Cursor(CdSpring owner,CdObjectPool pool, CdObjectState state, SolidInputs inputs) {
            this.owner=owner;this.pool = pool; this.state = state; wholeIndex = state.identity().wholeIndex();
            player = inputs.player(); gates = inputs.gates(); globals = inputs.globals();
        }
        void publish() { owner.store(this); }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        UnsupportedSpring unsupported(UnsupportedBoundary boundary, int value) {
            return new UnsupportedSpring(boundary, wholeIndex, value, OptionalInt.empty());
        }
    }

    /**
     * Native action stores in source order, after the shared Solid output. Only nine potential launch fields
     * are mutable; reconstruct every other actor/slot field from the exact before value. WORD centre setters
     * retain all 16 fraction bits, speed/move-lock setters truncate/sign-extend as native words.
     */
    private static final class PlayerPatch {
        final CdSolidObject.Player before;
        final Set<LaunchPlayerField> writes = EnumSet.noneOf(LaunchPlayerField.class);
        int x, y, xv, yv, gv, flags, anim, angle, lock;
        PlayerPatch(CdSolidObject.Player before) {
            this.before = before; var m = before.actor().motion();
            x = m.centreX16_16(); y = m.centreY16_16(); xv = m.xVelocity(); yv = m.yVelocity();
            gv = m.groundVelocity(); flags = m.flags(); anim = m.animation(); angle = m.angle(); lock = m.moveLock();
        }
        int xWord() { return x >>> 16; }
        int yWord() { return y >>> 16; }
        void xWord(int word) { x = ((word & 0xFFFF) << 16) | (x & 0xFFFF); writes.add(LaunchPlayerField.CENTRE_X); }
        void yWord(int word) { y = ((word & 0xFFFF) << 16) | (y & 0xFFFF); writes.add(LaunchPlayerField.CENTRE_Y); }
        void xVelocity(int word) { xv = (short) word; writes.add(LaunchPlayerField.X_VELOCITY); }
        void yVelocity(int word) { yv = (short) word; writes.add(LaunchPlayerField.Y_VELOCITY); }
        void groundVelocity(int word) { gv = (short) word; writes.add(LaunchPlayerField.GROUND_VELOCITY); }
        void moveLock(int word) { lock = (short) word; writes.add(LaunchPlayerField.MOVE_LOCK); }
        void flags(int value) { flags = value & 0xFF; writes.add(LaunchPlayerField.FLAGS); }
        void animation(int value) { anim = value & 0xFF; writes.add(LaunchPlayerField.ANIMATION); }
        void angle(int value) { angle = value & 0xFF; writes.add(LaunchPlayerField.ANGLE); }
        CdSolidObject.Player player() {
            var a = before.actor(); var m = a.motion();
            var next = new CdPlayerMotionState(x, y, xv, yv, gv, m.routine(), angle, flags, m.spriteFlags(),
                    m.xRadius(), m.yRadius(), anim, m.previousAnimation(), m.playerJump(), m.charge(), lock,
                    m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(next, a.tile(), a.priority(), a.hurt(),
                    a.rotationDistance()), before.slotId(), before.pushObject(), before.standObject());
        }
    }

    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
