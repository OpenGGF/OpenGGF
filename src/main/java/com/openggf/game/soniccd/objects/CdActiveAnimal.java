package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.*;
import java.util.function.IntSupplier;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * Active Animal24 continuation reached only after CdAnimal's actual presence predicate returned.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Palmtree Panic/Objects/Animal/Main.asm:17-216;
 * Collision Check.asm:155-181; Object Spawner.asm:388-446. No FixBugs branch in these body routines.
 * Shared math, finite native terrain, animator, existing placement/bank and synchronous angle/draw ports.
 * Init returns without drawing. Hologram parent/phase branches are literal; effects never replay stores.
 * No Frame, clock, global bank, art/current owner, CPU queue or rewind payload is owned here.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdActiveAnimal {
    public static final int OBJECT_ID=0x24;
    // Existing enum fields alias these exact native bytes/words/longs. No public raw-offset writer is added.
    private static final CdObjectState.WordField ANCHOR_X=PATA_BATA_ANCHOR_X,ANCHOR_Y=CHECKPOINT_BALL_Y,PARENT=EXPLOSION_POINTS_FRAME;
    private static final CdObjectState.ByteField PHASE=CHECKPOINT_ACTIVE,STEP=SWING_COUNT,CHILDREN_INVALID=TAMABBOH_MISSILE_DIRECTION;
    private static final CdObjectState.LongField X_DELTA=ROBOT_GENERATOR_SCHEDULE,Y_DELTA=PATA_BATA_X_DELTA;
    public record Assets(CdAssetBytes baseTiles,CdObjectVisualAssets flying,int flyingMappingsAddress,CdObjectVisualAssets ground,int groundMappingsAddress) {
        public Assets {Objects.requireNonNull(baseTiles);Objects.requireNonNull(flying);Objects.requireNonNull(ground);if(flying.mappingFrames().size()!=3||flying.animations().size()!=2||ground.mappingFrames().size()!=5||ground.animations().size()!=1)throw new IllegalArgumentException("Animal requires actual flying3/2 and ground5/1 formats");for(var v:List.of(flying,ground))for(var frame:v.mappingFrames())for(var piece:frame.pieces())if(piece.tileOffset()>20-piece.tileCount())throw new IllegalArgumentException("Animal mapping exceeds20 source tiles");}
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner){Objects.requireNonNull(owner,"actual R12B object source");var id=Objects.requireNonNull(owner.identity());if(id.variant()!=CdReleaseVariant.USA||id.file()!=CdNativeFile.R12B||id.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM||!id.stage().equals(CdStagePayload.R12B.stage()))throw new IllegalArgumentException("Animal requires original USA R12B metadata");return new Assets(owner.view(CdR12bObjectAssets.View.ANIMAL_BASE_TILES),owner.flyingAnimal(),CdR12bObjectAssets.View.MAP_SPR_FLYING_ANIMAL.range().nativeAddress(),owner.groundAnimal(),CdR12bObjectAssets.View.MAP_SPR_GROUND_ANIMAL.range().nativeAddress());}
    }
    /** act is reached by Init; primaryAngle is existing-owner CAS observation only at the reached floor call. */
    public interface Reads extends CdAnimal.Reads {int act();int primaryAngle();}
    public record PrimaryAngleCall(CdObjectPlacementRuntime.Snapshot actualCut,CdObjectState expectedSource,int expectedPrimaryAngle,int value){public PrimaryAngleCall{requireSource(actualCut,expectedSource);byteValue(expectedPrimaryAngle);byteValue(value);}}
    public record DrawCall(CdObjectPlacementRuntime.Snapshot actualCut,CdObjectState expectedSource){public DrawCall{requireSource(actualCut,expectedSource);}}
    @FunctionalInterface public interface PrimaryAngleCommit {void commit(PrimaryAngleCall call);}
    @FunctionalInterface public interface DrawObjectCall {void drawObject(DrawCall call);}
    public enum StoreKind {SOURCE_STORE,CHILD_STORE,SAVED_FLAG_CLEAR,DELETE}
    public record Publication(long ordinal,StoreKind kind,CdObjectPlacementRuntime.Snapshot actualCut,CdObjectState expectedSource,CdObjectState before,CdObjectState output,int savedByteIndex,int previousValue,int value) { }
    public interface DispatchObserver {void beforePublication(Publication call);void afterPublication(Publication call);void beforeCall(Object call);void afterCall(Object call);}
    public interface ReceiptGuard {void prepared(Object call);void completed(Object call);}
    public enum NativeCall {ANIMATE_OBJECT,DRAW_OBJECT,OBJ_GET_FLOOR_DIST,CHECK_OBJ_DESPAWN}
    public sealed interface Effect permits CallReturned,FloorReturned,Deleted,SavedFlagChanged { }
    public record CallReturned(NativeCall call) implements Effect { }
    public record FloorReturned(CdObjectCollisionCheck.FloorDistance result) implements Effect { }
    public record Deleted() implements Effect { }
    public record SavedFlagChanged(int byteIndex,int previousValue,int value) implements Effect { }
    public record Result(CdObjectState state,List<Effect> effects){public Result{Objects.requireNonNull(state);effects=List.copyOf(effects);}}
    public enum UnsupportedBoundary {ROUTINE_DISPATCH,ACT_READER,PRIMARY_ANGLE_READER,BASE_TILE_READ,PARENT_READ,ANIMATION_DIRECTORY_READ,ANIMATION_SPEED_READ,ANIMATION_FRAME_READ,ANIMATION_OPERAND_READ,SAVED_FLAGS_ADDRESS,SAVED_PERIOD_READ}
    public static final class UnsupportedActiveAnimal extends UnsupportedOperationException {
        private static final long serialVersionUID=1L;private final UnsupportedBoundary boundary;private final int wholeIndex;private final long value;
        private UnsupportedActiveAnimal(UnsupportedBoundary boundary,int wholeIndex,long value){super(boundary+" at active Animal whole"+wholeIndex+", value "+value);this.boundary=boundary;this.wholeIndex=wholeIndex;this.value=value;}
        public UnsupportedBoundary boundary(){return boundary;}public int wholeIndex(){return wholeIndex;}public long value(){return value;}
    }
    private final CdObjectPlacementRuntime placement;private final Assets assets;private final CdPlayerMath math;private final CdPlayerTerrain terrain;
    private final PrimaryAngleCommit angleCommit;private final DrawObjectCall drawObject;private final DispatchObserver observer;private final ReceiptGuard receiptGuard;
    private boolean running;private Object pendingOperation;private long operationOrdinal,pendingOrdinal;
    public CdActiveAnimal(CdObjectPlacementRuntime placement,Assets assets,CdPlayerMath math,CdPlayerTerrain terrain,PrimaryAngleCommit angleCommit,DrawObjectCall drawObject,DispatchObserver observer,ReceiptGuard receiptGuard){this.placement=Objects.requireNonNull(placement);this.assets=Objects.requireNonNull(assets);this.math=Objects.requireNonNull(math);this.terrain=Objects.requireNonNull(terrain);this.angleCommit=Objects.requireNonNull(angleCommit);this.drawObject=Objects.requireNonNull(drawObject);this.observer=Objects.requireNonNull(observer);this.receiptGuard=Objects.requireNonNull(receiptGuard);}
    /** Shared Math/Terrain are ordinary immutable/helper values; their APIs expose no source-identity predicate.
     * The private World supplies its sole helpers from this actual pair, whose retained relation is checked here. */
    public static CdActiveAnimal fromAct2PastAssets(CdObjectPlacementRuntime placement,CdR12bTerrainAssets owner,CdR12bPlayerAssets player,CdPlayerMath math,CdPlayerTerrain terrain,PrimaryAngleCommit angle,DrawObjectCall draw,DispatchObserver observer,ReceiptGuard guard){Objects.requireNonNull(placement,"actual placement");Objects.requireNonNull(owner,"actual R12B terrain");if(!placement.sameAct2PastSource(owner))throw new IllegalArgumentException("Animal requires the same retained R12B placement/source");CdStagePlayerSource.fromAct2Past(owner,Objects.requireNonNull(player));return new CdActiveAnimal(placement,Assets.fromAct2PastAssets(owner.objects()),math,terrain,angle,draw,observer,guard);}
    public Assets assets(){return assets;}
    public boolean isCurrentCall(Object call){return running&&call!=null&&call==pendingOperation;}
    public long currentCallOrdinal(Object call){if(!isCurrentCall(call))throw new IllegalStateException("Not original current active Animal call");return pendingOrdinal;}
    /** Continuation only. Production enters through CdAnimal's presence gate, not this descriptive public method. */
    public Result run(int wholeIndex,CdAnimal.Reads reads){if(running)throw new IllegalStateException("Active Animal cannot reenter physical dispatch");running=true;operationOrdinal=0;try{var s=placement.pool().state(wholeIndex);if(s.id()!=OBJECT_ID)throw new IllegalArgumentException("Not Animal24");var c=new Cursor(s,Objects.requireNonNull(reads));int routine=s.routine();if((routine&1)!=0||routine>4)throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH,wholeIndex,routine);if(routine==0)init(c);else if(routine==2){if(c.flying)flying(c);else ground(c);}else if(c.flying)flyingHologram(c);else groundHologram(c);return new Result(placement.pool().state(wholeIndex),c.effects);}finally{pendingOperation=null;pendingOrdinal=0;running=false;}}
    private void init(Cursor c){c.write(c.state.addByte(ROUTINE,2));c.write(c.state.moveByte(SPRITE_FLAGS,4));c.write(c.state.moveByte(Y_RADIUS,8).moveByte(X_RADIUS,8).moveByte(PRIORITY,4).moveByte(WIDTH,8));c.write(c.state.moveLong(MAPPINGS,c.flying?assets.flyingMappingsAddress():assets.groundMappingsAddress()));c.write(c.state.moveWord(ANCHOR_X,c.state.centreX()));if(c.flying){c.write(c.state.moveWord(ANCHOR_Y,c.state.centreY()));xFlip(c);}baseTile(c);if(c.state.signedByte(SUBTYPE)<0){if(c.flying){c.write(c.state.addByte(ROUTINE,2));c.write(c.state.moveByte(ANIMATION,1));c.write(c.state.moveByte(PRIORITY,3));}else{c.write(c.state.moveByte(ROUTINE,4));xFlip(c);}return;}if(c.flying){c.write(c.state.moveByte(PRIORITY,4));c.write(c.state.moveWord(TILE,c.state.word(TILE)|0x8000));c.write(c.state.moveWord(ANTON_ANCHOR_X,0x101));}else{c.write(c.state.moveLong(X_DELTA,0x10000));c.write(c.state.moveLong(Y_DELTA,-0x40000));}}
    private void baseTile(Cursor c){if(!(c.reads instanceof Reads full))throw unsupported(UnsupportedBoundary.ACT_READER,c.whole,0);int act=read(c,full::act,false),d0=act<<2;int time=read(c,c.reads::timeZone,false);d0=(d0&0xFF00)|((d0+time)&255);int at=(short)((d0+d0)&65535);if(at<0||at>assets.baseTiles().length()-2)throw unsupported(UnsupportedBoundary.BASE_TILE_READ,c.whole,at);c.write(c.state.moveWord(TILE,assets.baseTiles().unsignedWord(at)));}
    private void moveFlying(Cursor c,int cosineShift,int sineShift){var values=math.calcSine(c.state.unsignedByte(PHASE));c.write(c.state.moveWord(X,(values.cosine()>>cosineShift)+c.state.word(ANCHOR_X)));c.write(c.state.moveWord(Y,(values.sine()>>sineShift)+c.state.word(ANCHOR_Y)));}
    private void flying(Cursor c){moveFlying(c,1,1);int phase=c.state.unsignedByte(PHASE),next=(phase+c.state.unsignedByte(STEP))&255;if(((next-1)&255)>=0x7F){next=phase;c.write(c.state.moveByte(STEP,-c.state.unsignedByte(STEP)));xFlip(c);}c.write(c.state.moveByte(PHASE,next));animate(c);draw(c);despawn(c,true);}
    private void flyingHologram(Cursor c){if(!parent(c))return;moveFlying(c,3,4);c.write(c.state.addByte(PHASE,4));if((c.state.unsignedByte(PHASE)&127)==0){xFlip(c);return;}animate(c);draw(c);}
    private void ground(Cursor c){c.write(c.state.addLong(X_POSITION,c.state.longBits(X_DELTA)));c.write(c.state.addLong(Y_POSITION,c.state.longBits(Y_DELTA)));c.write(c.state.addLong(Y_DELTA,0x2000));c.write(c.state.moveByte(MAP_FRAME,c.state.longBits(Y_DELTA)<0?0:1));var floor=floor(c);if(floor.distance()<0){c.write(c.state.addWord(Y,floor.distance()));c.write(c.state.moveLong(Y_DELTA,-0x40000));c.write(c.state.moveLong(X_DELTA,-c.state.longBits(X_DELTA)));xFlip(c);}draw(c);despawn(c,false);}
    private void groundHologram(Cursor c){if(!parent(c))return;animate(c);draw(c);}
    private boolean parent(Cursor c){int low=c.state.word(PARENT);if(low<0xD800||low>=0xF000||(low&63)!=0)throw unsupported(UnsupportedBoundary.PARENT_READ,c.whole,low);var p=placement.pool().state((low-0xD000)>>>6);if(p.id()!=0x2E||p.unsignedByte(CHILDREN_INVALID)!=0){delete(c);return false;}return true;}
    private void xFlip(Cursor c){c.write(c.state.moveByte(SPRITE_FLAGS,c.state.spriteFlags()^1));c.write(c.state.moveByte(FLAGS,c.state.flags()^1));}
    private CdObjectVisualAssets visual(Cursor c){return c.flying?assets.flying():assets.ground();}
    private CdObjectCollisionCheck.FloorDistance floor(Cursor c){if(!(c.reads instanceof Reads full))throw unsupported(UnsupportedBoundary.PRIMARY_ANGLE_READER,c.whole,0);c.primaryAngle=read(c,full::primaryAngle,false);var result=CdObjectCollisionCheck.objGetFloorDist(terrain,c.state,value->{var call=new PrimaryAngleCall(capture(c),c.state,c.primaryAngle,value);observedCall(call,()->readOnly(c,call.actualCut(),()->angleCommit.commit(call)));c.primaryAngle=value;});c.effects.add(new FloorReturned(result));c.effects.add(new CallReturned(NativeCall.OBJ_GET_FLOOR_DIST));return result;}
    private int read(Cursor c,IntSupplier value,boolean word){var cut=capture(c);final int answer;try{answer=value.getAsInt();if(word)wordValue(answer);else byteValue(answer);assertPlacement(cut,cut.pool());}catch(RuntimeException|Error original){suppress(original,()->assertPlacement(cut,cut.pool()));throw original;}return answer;}
    private void delete(Cursor c){nativeDelete(c);c.state=placement.pool().state(c.whole);c.effects.add(new Deleted());}
    private void despawn(Cursor c,boolean anchor){int x=anchor?c.state.word(ANCHOR_X):c.state.centreX();if(c.state.signedByte(SPRITE_FLAGS)>=0){int camera=read(c,c.reads::cameraXWord,true);if((((x&0xFF80)-((camera-128)&0xFF80))&65535)>0x280){despawnDelete(c);c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));return;}}if((read(c,c.reads::timeZone,false)&128)!=0)despawnDelete(c);c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));}
    private void despawnDelete(Cursor c){int id=c.state.savedFlagsId();if(id!=0){int raw=read(c,c.reads::timeZone,false),period=raw&127;if((raw&128)!=0){int adjusted=(short)((period-(byte)read(c,c.reads::timeWarpDir,false))&65535);period=adjusted<0?0:adjusted<3?adjusted:2;}if(period>2)throw unsupported(UnsupportedBoundary.SAVED_PERIOD_READ,c.whole,period);int index=2+3*id+period;if(index>=CdObjectPlacementRuntime.SAVED_BANK_BYTES)throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS,c.whole,index);int before=placement.savedFlag(id,period);nativeSavedClear(c,id,period,index,before);c.effects.add(new SavedFlagChanged(index,before,before&127));}delete(c);}
    private void animate(Cursor c) {
        var original = c.state;
        CdObjectAnimation.State out;
        try {
            out = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME), original.unsignedByte(ANIMATION_TIME),
                original.unsignedByte(MAP_FRAME), original.spriteFlags(), original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)),
                new CdObjectAnimation.AnimationTable(visual(c).animationTable(), 0, visual(c).animations().size()));
        } catch (UnsupportedOperationException selectedRead) {
            try { animationFailurePrefixes(c); }
            catch (UnsupportedActiveAnimal missing) { missing.initCause(selectedRead); throw missing; }
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
        if (animation >= visual(c).animations().size()) throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_READ, c.whole, directory);
        int script = (short) visual(c).animationTable().unsignedWord(directory);
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
        if (at < 0 || at >= visual(c).animationTable().length()) throw unsupported(boundary, c.whole, at);
        return visual(c).animationTable().unsignedByte(at);
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
        if (pendingOperation != null) throw new IllegalStateException("Nested active Animal producer operation");
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
        if (!samePlacement(cut, expectedPool)) throw new IllegalStateException("Active Animal callback changed its actual full placement cut");
    }
    private static void requireSource(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if (!cut.pool().slots().get(source.identity().dynamicIndex()).equals(source))
            throw new IllegalArgumentException("Active Animal source is not this full placement capture's actual image");
    }
    private static void suppress(Throwable original, Runnable verification) {
        try { verification.run(); } catch (RuntimeException | Error failed) { if (failed != original) original.addSuppressed(failed); }
    }
    private static UnsupportedActiveAnimal unsupported(UnsupportedBoundary boundary, int whole, long value) {
        return new UnsupportedActiveAnimal(boundary, whole, value);
    }
    private final class Cursor {final int whole;final CdAnimal.Reads reads;final boolean flying;final List<Effect> effects=new ArrayList<>();CdObjectState state;int primaryAngle;Cursor(CdObjectState state,CdAnimal.Reads reads){this.state=state;this.reads=reads;whole=state.identity().wholeIndex();flying=(state.subtype()&127)==0;}void write(CdObjectState next){nativeStore(this,next,StoreKind.SOURCE_STORE);state=next;}}
    private static void byteValue(int value){if((value&~255)!=0)throw new IllegalArgumentException("Expected native BYTE");}
    private static void wordValue(int value){if((value&~65535)!=0)throw new IllegalArgumentException("Expected native WORD");}
}
