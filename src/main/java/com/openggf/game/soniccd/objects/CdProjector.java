package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
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
 * Original Projector2E and same-ID holograms; N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Palmtree Panic/Objects/Projector/Main.asm:36-281; non-legacy ABI, shipped FixBugs=0 shared Solid.
 * Real source-ordered pool stores/FindObjSlot and the existing Solid/animation kernels only.
 * PLC requests are submissions, never queue service/readiness/current publication. Programme, Frame,
 * World, sound and queue owners must bind the original synchronous ports/receipts themselves.
 * Public records/Assets are descriptive values, never invocation authority or replay commands.
 * No retained native image, extra pool/bank/clock/current owner or rewind key is introduced.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdProjector {
    public static final int OBJECT_ID=0x2E,ANIMAL_ID=0x24,EXPLOSION_ID=0x18,FM_EXPLODE=0x9E;
    /** Source-selected ABI. Never rewrites the live ID to enter the shared body. */
    public enum Profile { PALMTREE(0x2E,0x24), COLLISION_CHAOS(0x38,0x39);
        final int parent, animal; Profile(int parent,int animal){this.parent=parent;this.animal=animal;}
    }
    private Profile profile=Profile.PALMTREE;
    public static CdProjector collisionChaos(CdObjectPlacementRuntime placement,Assets assets,CdSolidObject solid,
            SolidCommit commit,ProjectorStore store,PlcCall plc,FmCall fm,DrawObjectCall draw,
            DispatchObserver observer,ReceiptGuard guard) {
        var body=new CdProjector(placement,assets,solid,commit,store,plc,fm,draw,observer,guard);
        body.profile=Profile.COLLISION_CHAOS;return body;
    }
    private int tile(Cursor c){return profile==Profile.COLLISION_CHAOS?0x33E:read(c,c.reads::act,false)==0?0x403:0x3AF;}
    // Existing immutable slot aliases describe identical storage widths/offsets, not those other bodies.
    private static final CdObjectState.WordField TIMER=ROBOT_GENERATOR_TIMER,PARENT=EXPLOSION_POINTS_FRAME;
    private static final CdObjectState.LongField SCHEDULE=ROBOT_GENERATOR_SCHEDULE;
    private static final CdObjectState.ByteField TIMER_HIGH=ROBOT_GENERATOR_TIMER_HIGH,CHILDREN_INVALID=TAMABBOH_MISSILE_DIRECTION;
    public record Assets(CdObjectVisualAssets visual,int mappingsAddress,CdAssetBytes explosionSchedule,int explosionScheduleAddress) {
        public Assets {
            Objects.requireNonNull(visual);Objects.requireNonNull(explosionSchedule);
            if(visual.mappingFrames().size()!=6||visual.animations().size()!=2)throw new IllegalArgumentException("Projector requires six frames and two scripts");
            for(var frame:visual.mappingFrames())for(var piece:frame.pieces())if(piece.tileOffset()>51-piece.tileCount())throw new IllegalArgumentException("Projector mapping exceeds its51-tile source");
        }
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner,"actual R12B source");var id=Objects.requireNonNull(owner.identity());
            if(id.variant()!=CdReleaseVariant.USA||id.file()!=CdNativeFile.R12B||id.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM||!id.stage().equals(CdStagePayload.R12B.stage()))throw new IllegalArgumentException("Projector requires original USA R12B metadata");
            return new Assets(owner.projector(),CdR12bObjectAssets.View.MAP_SPR_PROJECTOR.range().nativeAddress(),owner.view(CdR12bObjectAssets.View.PROJECTOR_EXPLOSION_SCHEDULE),CdR12bObjectAssets.View.PROJECTOR_EXPLOSION_SCHEDULE.range().nativeAddress());
        }
    }
    /** Conditional reached operands, checked read-only against the complete actual placement cut. */
    public interface Reads {int projDestroyed();int act();int timeZone();int timeWarpDirection();int cameraXWord();default int parentByte(int nativeAddress){throw new UnsupportedOperationException("Unowned raw Projector parent byte at "+Integer.toHexString(nativeAddress));}}
    public record Globals(int projDestroyed,int act,int timeZone,int timeWarpDirection,int cameraXWord) implements Reads {
        public Globals {byteValue(projDestroyed);byteValue(act);byteValue(timeZone);byteValue(timeWarpDirection);wordValue(cameraXWord);}
    }
    public record Inputs(CdSolidObject.Player player,CdSolidObject.Gates gates,CdSolidObject.Globals globals) {
        public Inputs {Objects.requireNonNull(player);Objects.requireNonNull(gates);Objects.requireNonNull(globals);}
    }
    public enum Site {ACTIVE_SOLID,HIT_SOLID,HIT_GET_OFF}
    public enum NativeCall {SOLID_OBJECT,GET_OFF_OBJECT,ANIMATE_OBJECT,DRAW_OBJECT,CHECK_OBJ_DESPAWN}
    public enum PlcRequest {LOAD_HOLOGRAM_ANIMALS(5),RESTORE_ANIMALS(4);private final int id;PlcRequest(int id){this.id=id;}public int nativeId(){return id;}}
    public record SolidCall(Site site,CdObjectPlacementRuntime.Snapshot actualCut,CdObjectState expectedSource,CdSolidObject.Player expectedPlayer,CdSolidObject.Globals expectedGlobals,CdSolidObject.Outcome outcome) {
        public SolidCall {Objects.requireNonNull(site);requireSource(actualCut,expectedSource);Objects.requireNonNull(expectedPlayer);Objects.requireNonNull(expectedGlobals);Objects.requireNonNull(outcome);var entry=outcome instanceof CdSolidObject.Completed d?d.completedResult().entry():((CdSolidObject.NativeStopped)outcome).prefix().entry();if(entry!=entryFor(site))throw new IllegalArgumentException("Wrong Projector Solid entry");}
        public CdSolidObject.Entry entry(){return entryFor(site);}
    }
    public record ProjectorDestroyedCall(CdObjectPlacementRuntime.Snapshot actualCut,CdObjectState expectedSource,int value) {public ProjectorDestroyedCall {requireSource(actualCut,expectedSource);if(value!=0xFF)throw new IllegalArgumentException("Native ST stores FF");}}
    public record PlcCallRequest(CdObjectPlacementRuntime.Snapshot actualCut,CdObjectState expectedSource,PlcRequest request) {public PlcCallRequest {requireSource(actualCut,expectedSource);Objects.requireNonNull(request);}}
    public record FmRequest(CdObjectPlacementRuntime.Snapshot actualCut,CdObjectState expectedSource,int nativeId) {public FmRequest {requireSource(actualCut,expectedSource);if(nativeId!=FM_EXPLODE)throw new IllegalArgumentException("Projector requests FM9E only");}}
    public record DrawCall(CdObjectPlacementRuntime.Snapshot actualCut,CdObjectState expectedSource) {public DrawCall {requireSource(actualCut,expectedSource);}}
    @FunctionalInterface public interface SolidCommit {void commit(SolidCall call);}
    @FunctionalInterface public interface ProjectorStore {void store(ProjectorDestroyedCall call);}
    @FunctionalInterface public interface PlcCall {void submit(PlcCallRequest call);}
    @FunctionalInterface public interface FmCall {void playFm(FmRequest call);}
    @FunctionalInterface public interface DrawObjectCall {void drawObject(DrawCall call);}
    public enum StoreKind {SOURCE_STORE,CHILD_STORE,SAVED_FLAG_CLEAR,DELETE}
    public record Publication(long ordinal,StoreKind kind,CdObjectPlacementRuntime.Snapshot actualCut,CdObjectState expectedSource,CdObjectState before,CdObjectState output,int savedByteIndex,int previousValue,int value) { }
    public interface DispatchObserver {void beforePublication(Publication call);void afterPublication(Publication call);void beforeCall(Object call);void afterCall(Object call);}
    public interface ReceiptGuard {void prepared(Object call);void completed(Object call);}
    public sealed interface Effect permits CallReturned,SolidReturned,PlcSubmitted,ProjectorDestroyed,FmRequested,ChildCreated,SlotUnavailable,Deleted,SavedFlagChanged { }
    public record CallReturned(NativeCall call) implements Effect { }
    public record SolidReturned(Site site,CdSolidObject.Result result) implements Effect { }
    public record PlcSubmitted(PlcRequest request) implements Effect { }
    public record ProjectorDestroyed() implements Effect { }
    public record FmRequested(int nativeId) implements Effect { }
    public record ChildCreated(CdObjectState state) implements Effect { }
    public record SlotUnavailable() implements Effect { }
    public record Deleted() implements Effect { }
    public record SavedFlagChanged(int byteIndex,int previousValue,int value) implements Effect { }
    public record Result(CdObjectState state,List<Effect> effects) {public Result {Objects.requireNonNull(state);effects=List.copyOf(effects);}public boolean deleted(){return state.id()==0;}}
    public enum UnsupportedBoundary {ROUTINE_DISPATCH,PARENT_READ,SCHEDULE_TICK_READ,SCHEDULE_X_READ,SCHEDULE_Y_READ,ANIMATION_DIRECTORY_READ,ANIMATION_SPEED_READ,ANIMATION_FRAME_READ,ANIMATION_OPERAND_READ,SAVED_FLAGS_ADDRESS,SAVED_PERIOD_READ}
    public static final class UnsupportedProjector extends UnsupportedOperationException {
        private static final long serialVersionUID=1L;private final UnsupportedBoundary boundary;private final int wholeIndex;private final long value;
        private UnsupportedProjector(UnsupportedBoundary boundary,int wholeIndex,long value){super(boundary+" at Projector whole"+wholeIndex+", value "+value);this.boundary=boundary;this.wholeIndex=wholeIndex;this.value=value;}
        public UnsupportedBoundary boundary(){return boundary;}public int wholeIndex(){return wholeIndex;}public long value(){return value;}
    }
    private final CdObjectPlacementRuntime placement;private final Assets assets;private final CdSolidObject sharedSolid;
    private final SolidCommit solidCommit;private final ProjectorStore projectorStore;private final PlcCall plcCall;private final FmCall fmCall;private final DrawObjectCall drawObject;
    private final DispatchObserver observer;private final ReceiptGuard receiptGuard;private boolean running;private Object pendingOperation;private long operationOrdinal,pendingOrdinal;
    public CdProjector(CdObjectPlacementRuntime placement,Assets assets,CdSolidObject sharedSolid,SolidCommit solidCommit,ProjectorStore projectorStore,PlcCall plcCall,FmCall fmCall,DrawObjectCall drawObject,DispatchObserver observer,ReceiptGuard receiptGuard) {
        this.placement=Objects.requireNonNull(placement);this.assets=Objects.requireNonNull(assets);this.sharedSolid=Objects.requireNonNull(sharedSolid);this.solidCommit=Objects.requireNonNull(solidCommit);this.projectorStore=Objects.requireNonNull(projectorStore);this.plcCall=Objects.requireNonNull(plcCall);this.fmCall=Objects.requireNonNull(fmCall);this.drawObject=Objects.requireNonNull(drawObject);this.observer=Objects.requireNonNull(observer);this.receiptGuard=Objects.requireNonNull(receiptGuard);
    }
    /** Retained source gate; shared Solid is an ordinary helper whose source is bound by the private World. */
    public static CdProjector fromAct2PastAssets(CdObjectPlacementRuntime placement,CdR12bTerrainAssets owner,CdSolidObject solid,SolidCommit commit,ProjectorStore store,PlcCall plc,FmCall fm,DrawObjectCall draw,DispatchObserver observer,ReceiptGuard guard) {
        Objects.requireNonNull(placement,"actual placement");Objects.requireNonNull(owner,"actual R12B source");if(!placement.sameAct2PastSource(owner))throw new IllegalArgumentException("Projector requires the same retained R12B placement/source");return new CdProjector(placement,Assets.fromAct2PastAssets(owner.objects()),solid,commit,store,plc,fm,draw,observer,guard);
    }
    public Assets assets(){return assets;}
    public boolean isCurrentCall(Object call){return running&&call!=null&&call==pendingOperation;}
    public long currentCallOrdinal(Object call){if(!isCurrentCall(call))throw new IllegalStateException("Not original current Projector call");return pendingOrdinal;}
    public Result run(int wholeIndex,Reads reads,Inputs inputs) {
        if(running)throw new IllegalStateException("Projector cannot reenter physical dispatch");running=true;operationOrdinal=0;
        try {var state=placement.pool().state(wholeIndex);if(state.id()!=profile.parent)throw new IllegalArgumentException("Not Projector2E");var c=new Cursor(state,Objects.requireNonNull(reads),inputs);
            if(state.subtype()!=0){hologram(c);return new Result(placement.pool().state(wholeIndex),c.effects);}
            switch(state.routine()){case 0->init(c);case 2->active(c);case 4->startExploding(c);case 6->exploding(c);case 8->finalDelay(c);default->throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH,wholeIndex,state.routine());}
            draw(c);if(c.state.signedByte(ROUTINE)<=2){despawn(c);if(c.state.id()==0)plc(c,PlcRequest.RESTORE_ANIMALS);}return new Result(placement.pool().state(wholeIndex),c.effects);
        } finally {pendingOperation=null;pendingOrdinal=0;running=false;}
    }
    private void init(Cursor c) {
        if(read(c,c.reads::projDestroyed,false)!=0){delete(c);return;}
        plc(c,PlcRequest.LOAD_HOLOGRAM_ANIMALS);
        c.write(c.state.addByte(ROUTINE,2));c.write(c.state.moveByte(SPRITE_FLAGS,c.state.spriteFlags()|4));c.write(c.state.moveByte(PRIORITY,4));c.write(c.state.moveByte(X_RADIUS,12));c.write(c.state.moveByte(WIDTH,12));c.write(c.state.moveByte(Y_RADIUS,12));c.write(c.state.moveByte(COLLISION_TYPE,0xFB));
        c.write(c.state.moveWord(TILE,tile(c)));c.write(c.state.moveLong(MAPPINGS,assets.mappingsAddress()));c.write(c.state.moveLong(SCHEDULE,assets.explosionScheduleAddress()));
        if(!child(c,c.state.id(),0xFF,-0x15,-7)||!child(c,c.state.id(),1,-0x58,-4)||!child(c,profile.animal,0x80,-0x58,-0x18)||!child(c,profile.animal,0x81,profile==Profile.COLLISION_CHAOS?-0x62:-0x64,profile==Profile.COLLISION_CHAOS?0:4)){delete(c);return;}
        active(c);
    }
    private boolean child(Cursor c,int id,int subtype,int dx,int dy) {
        var found=placement.pool().findObjSlot();if(found.isEmpty()){c.effects.add(new SlotUnavailable());return false;}
        var s=placement.pool().state(found.getAsInt());s=s.moveByte(ID,id);nativeStore(c,s,StoreKind.CHILD_STORE);s=s.moveWord(X,c.state.centreX());nativeStore(c,s,StoreKind.CHILD_STORE);s=s.moveWord(Y,c.state.centreY());nativeStore(c,s,StoreKind.CHILD_STORE);s=s.addWord(X,dx);nativeStore(c,s,StoreKind.CHILD_STORE);s=s.addWord(Y,dy);nativeStore(c,s,StoreKind.CHILD_STORE);s=s.moveByte(SUBTYPE,subtype);nativeStore(c,s,StoreKind.CHILD_STORE);s=s.moveWord(PARENT,c.state.identity().nativeAddress24());nativeStore(c,s,StoreKind.CHILD_STORE);c.effects.add(new ChildCreated(s));return true;
    }
    private void active(Cursor c) {
        if(c.state.unsignedByte(COLLISION_STATUS)!=0){c.write(c.state.moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0));c.write(c.state.addByte(ROUTINE,2));}solid(c,Site.ACTIVE_SOLID);
    }
    private void startExploding(Cursor c) {
        c.write(c.state.addByte(ROUTINE,2));c.write(c.state.moveByte(MAP_FRAME,1));c.write(c.state.moveByte(CHILDREN_INVALID,0xFF));plc(c,PlcRequest.RESTORE_ANIMALS);
        if(solid(c,Site.HIT_SOLID).d0().orElseThrow()!=0)solid(c,Site.HIT_GET_OFF);exploding(c);
    }
    private void exploding(Cursor c) {
        int pointer=c.state.longBits(SCHEDULE),tick=scheduleByte(c,pointer,UnsupportedBoundary.SCHEDULE_TICK_READ);
        if((byte)tick<0){c.write(c.state.addByte(ROUTINE,2));c.write(c.state.moveWord(TIMER,60));return;}
        c.write(c.state.addByte(TIMER_HIGH,1));if(tick!=c.state.unsignedByte(TIMER_HIGH))return;
        int x=(byte)scheduleByte(c,pointer+1,UnsupportedBoundary.SCHEDULE_X_READ),y=(byte)scheduleByte(c,pointer+2,UnsupportedBoundary.SCHEDULE_Y_READ);c.write(c.state.moveLong(SCHEDULE,pointer+3));
        var found=placement.pool().findObjSlot();if(found.isEmpty()){c.effects.add(new SlotUnavailable());return;}
        var s=placement.pool().state(found.getAsInt());s=s.moveByte(ID,EXPLOSION_ID);nativeStore(c,s,StoreKind.CHILD_STORE);s=s.moveByte(ROUTINE_2,1);nativeStore(c,s,StoreKind.CHILD_STORE);s=s.moveWord(X,c.state.centreX());nativeStore(c,s,StoreKind.CHILD_STORE);s=s.moveWord(Y,c.state.centreY());nativeStore(c,s,StoreKind.CHILD_STORE);s=s.addWord(X,x);nativeStore(c,s,StoreKind.CHILD_STORE);s=s.addWord(Y,y);nativeStore(c,s,StoreKind.CHILD_STORE);c.effects.add(new ChildCreated(s));fm(c);
    }
    private void finalDelay(Cursor c) {c.write(c.state.addWord(TIMER,-1));if(c.state.word(TIMER)!=0)return;var call=new ProjectorDestroyedCall(capture(c),c.state,0xFF);observedCall(call,()->readOnly(c,call.actualCut(),()->projectorStore.store(call)));c.effects.add(new ProjectorDestroyed());delete(c);}
    private void hologram(Cursor c) {
        int low=c.state.word(PARENT);
        if(profile==Profile.COLLISION_CHAOS){
            int address=((short)low)&0xFFFFFF;
            if(parentByte(c,address)!=profile.parent||parentByte(c,(address+0x3F)&0xFFFFFF)!=0){delete(c);return;}
        }else{
            if(low<0xD800||low>=0xF000||(low&63)!=0)throw unsupported(UnsupportedBoundary.PARENT_READ,c.whole,low);
            var parent=placement.pool().state((low-0xD000)>>>6);if(parent.id()!=profile.parent||parent.unsignedByte(CHILDREN_INVALID)!=0){delete(c);return;}
        }
        if(c.state.routine()==0){c.write(c.state.addByte(ROUTINE,2));c.write(c.state.moveByte(SPRITE_FLAGS,c.state.spriteFlags()|4));c.write(c.state.moveByte(PRIORITY,4));c.write(c.state.moveWord(TILE,tile(c)));c.write(c.state.moveLong(MAPPINGS,assets.mappingsAddress()));boolean small=c.state.signedByte(SUBTYPE)<0;c.write(c.state.moveByte(X_RADIUS,small?8:20));c.write(c.state.moveByte(WIDTH,small?8:20));c.write(c.state.moveByte(Y_RADIUS,small?4:24));c.write(c.state.moveByte(ANIMATION,small?0:1));}
        animate(c);draw(c);
    }
    private int parentByte(Cursor c,int address){
        if(address>=0xFFD800&&address<0xFFF000){int low=address&65535;return Byte.toUnsignedInt(placement.pool().state((low-0xD000)>>>6).copyBytes()[low&63]);}
        return read(c,()->c.reads.parentByte(address),false);
    }
    private int scheduleByte(Cursor c,int pointer,UnsupportedBoundary boundary){long at=(Integer.toUnsignedLong(pointer)-Integer.toUnsignedLong(assets.explosionScheduleAddress()))&0xFFFFFFFFL;if(at>=assets.explosionSchedule().length())throw unsupported(boundary,c.whole,Integer.toUnsignedLong(pointer));return assets.explosionSchedule().unsignedByte((int)at);}
    private void plc(Cursor c,PlcRequest request){var call=new PlcCallRequest(capture(c),c.state,request);observedCall(call,()->readOnly(c,call.actualCut(),()->plcCall.submit(call)));c.effects.add(new PlcSubmitted(request));}
    private void fm(Cursor c){var call=new FmRequest(capture(c),c.state,FM_EXPLODE);observedCall(call,()->readOnly(c,call.actualCut(),()->fmCall.playFm(call)));c.effects.add(new FmRequested(FM_EXPLODE));}
    private int read(Cursor c,IntSupplier value,boolean word){var cut=capture(c);final int answer;try{answer=value.getAsInt();if(word)wordValue(answer);else byteValue(answer);assertPlacement(cut,cut.pool());}catch(RuntimeException|Error original){suppress(original,()->assertPlacement(cut,cut.pool()));throw original;}return answer;}
    private void delete(Cursor c){nativeDelete(c);c.state=placement.pool().state(c.whole);c.effects.add(new Deleted());}
    private void despawn(Cursor c) {
        if(c.state.signedByte(SPRITE_FLAGS)>=0){int camera=read(c,c.reads::cameraXWord,true);if((((c.state.centreX()&0xFF80)-((camera-128)&0xFF80))&65535)>0x280){despawnDelete(c);c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));return;}}
        if((read(c,c.reads::timeZone,false)&128)!=0)despawnDelete(c);c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
    }
    private void despawnDelete(Cursor c){int id=c.state.savedFlagsId();if(id!=0){int raw=read(c,c.reads::timeZone,false),period=raw&127;if((raw&128)!=0){int adjusted=(short)((period-(byte)read(c,c.reads::timeWarpDirection,false))&65535);period=adjusted<0?0:adjusted<3?adjusted:2;}if(period>2)throw unsupported(UnsupportedBoundary.SAVED_PERIOD_READ,c.whole,period);int index=2+3*id+period;if(index>=CdObjectPlacementRuntime.SAVED_BANK_BYTES)throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS,c.whole,index);int before=placement.savedFlag(id,period);nativeSavedClear(c,id,period,index,before);c.effects.add(new SavedFlagChanged(index,before,before&127));}delete(c);}
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
                new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, 2));
        } catch (UnsupportedOperationException selectedRead) {
            try { animationFailurePrefixes(c); }
            catch (UnsupportedProjector missing) { missing.initCause(selectedRead); throw missing; }
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
        if (pendingOperation != null) throw new IllegalStateException("Nested Projector producer operation");
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
        if (!samePlacement(cut, expectedPool)) throw new IllegalStateException("Projector callback changed its actual full placement cut");
    }
    private static void requireSource(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if (!cut.pool().slots().get(source.identity().dynamicIndex()).equals(source))
            throw new IllegalArgumentException("Projector source is not this full placement capture's actual image");
    }
    private static void suppress(Throwable original, Runnable verification) {
        try { verification.run(); } catch (RuntimeException | Error failed) { if (failed != original) original.addSuppressed(failed); }
    }
    private static CdSolidObject.Entry entryFor(Site site) {
        return site == Site.HIT_GET_OFF ? CdSolidObject.Entry.GET_OFF_OBJECT : CdSolidObject.Entry.SOLID_OBJECT;
    }
    private static UnsupportedProjector unsupported(UnsupportedBoundary boundary, int whole, long value) {
        return new UnsupportedProjector(boundary, whole, value);
    }
    private final class Cursor {
        final int whole;final Reads reads;final Inputs inputs;final List<Effect> effects=new ArrayList<>();CdObjectState state;CdSolidObject.Player player;CdSolidObject.Globals solidGlobals;
        Cursor(CdObjectState state,Reads reads,Inputs inputs){this.state=state;whole=state.identity().wholeIndex();this.reads=reads;this.inputs=inputs;}
        void prepareInputs(){if(player==null){var in=Objects.requireNonNull(inputs,"reached Projector Solid inputs");player=in.player();solidGlobals=in.globals();}}
        void write(CdObjectState next){nativeStore(this,next,StoreKind.SOURCE_STORE);state=next;}
    }
    private static void byteValue(int value){if((value&~255)!=0)throw new IllegalArgumentException("Expected native BYTE");}
    private static void wordValue(int value){if((value&~65535)!=0)throw new IllegalArgumentException("Expected native WORD");}
}
