package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import java.util.*;
import java.util.function.Supplier;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Native CCAnimal39. N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Level/Collision Chaos/Objects/Animal/Main.asm:38-259; Level/Object Time Check.asm:55-85;
 * common/object_spawn.asm:313-374. Flying/ground/hologram graphs differ from PPZ Animal24.
 * All bytes are borrowed finite media views; no fallback art or new lifecycle is installed.
 * Calls are original private synchronous operations. Root authenticates source, pool, invocation
 * and complete State/Environment before external adoption; completed effects confer no authority.
 * Faults retain reached stores. Raw parent WORD addresses are read at their native cuts, without
 * invented alignment, generation or lifetime checks. No FixBugs conditional occurs here. */
public final class CdCcAnimal {
    public static final int OBJECT_ID=0x39;
    // Immutable slot aliases describe offsets only, not other object behavior.
    private static final CdObjectState.WordField BASE_X=CHECKPOINT_BALL_X,BASE_Y=CHECKPOINT_BALL_Y,PARENT=EXPLOSION_POINTS_FRAME;
    private static final CdObjectState.ByteField PHASE=RAMP_TIMER,PHASE_SUB=SWING_COUNT;
    private static final CdObjectState.LongField VX=TAMABBOH_X_DELTA,VY=CHECKPOINT_PARENT;
    public record Assets(CdAssetBytes baseTiles,CdObjectVisualAssets flying,int flyingAddress,
                         CdObjectVisualAssets ground,int groundAddress) {
        public Assets {Objects.requireNonNull(baseTiles);Objects.requireNonNull(flying);Objects.requireNonNull(ground);}
    }
    public enum Field {TIME_ZONE,GOOD_FUTURE,PROJECTOR_DESTROYED,TIME_WARP_DIRECTION,CAMERA_X,ACT,PARENT_ID,PARENT_FLAG,PRIMARY_ANGLE}
    public enum Site {PRESENCE,FLYING_INIT,GROUND_INIT,BASE_TILE,PARENT,ORBIT,HOLOGRAM,BOUNCE,FLOOR,DRAW,DESPAWN}
    public record Effect(Site site,String operation) { }
    public record Result(CdObjectState state,List<Effect> effects){public Result{effects=List.copyOf(effects);}}
    public static final class MissingNativeOperand extends UnsupportedOperationException {
        private MissingNativeOperand(String message){super(message);}
    }
    private interface Receipt { }
    public static final class Value implements Receipt {private final int value;private Value(int v){value=v;}public int value(){return value;}}
    public static final class Done implements Receipt {private Done(){}}
    public abstract sealed class Call permits Read,Angle,Draw {
        private final Cursor cursor;private final long ordinal;private final CdObjectPlacementRuntime.Snapshot cut;
        private final CdObjectState source;private Receipt receipt;
        private Call(Cursor c){cursor=c;ordinal=++c.order;cut=placement.capture();source=state(c.whole);}
        public long ordinal(){return ordinal;}public CdObjectPlacementRuntime.Snapshot actualCut(){return cut;}
        public CdObjectState expectedSource(){return source;}public Site site(){return cursor.site;}
        final void guard(){if(!isCurrentCall(this)||receipt!=null)throw new IllegalStateException("Not original current CCAnimal operation");}
    }
    public final class Read extends Call {
        private final Field field;private final int nativeAddress;
        private Read(Cursor c,Field f,int address){super(c);field=f;nativeAddress=address;}
        public Field field(){return field;}public int nativeAddress(){return nativeAddress;}
        public Value complete(int v){guard();int mask=field==Field.CAMERA_X?65535:255;
            if((v&~mask)!=0)throw new IllegalArgumentException("Native CCAnimal operand width");
            var r=new Value(v);super.receipt=r;return r;}
    }
    public final class Angle extends Call {
        private final int value;private Angle(Cursor c,int v){super(c);value=v;}
        public int value(){return value;}public Done complete(){guard();var r=new Done();super.receipt=r;return r;}
    }
    public final class Draw extends Call {
        private Draw(Cursor c){super(c);}public Done complete(){guard();var r=new Done();super.receipt=r;return r;}
    }
    public interface Ports {
        default Value read(Read c){throw new MissingNativeOperand("Unowned CCAnimal read "+c.field());}
        default Done angle(Angle c){throw new MissingNativeOperand("Unowned CCAnimal floor angle");}
        default Done draw(Draw c){throw new MissingNativeOperand("Unowned CCAnimal Draw");}
    }
    private final CdObjectPlacementRuntime placement;private final Assets assets;
    private final CdPlayerMath math;private final CdPlayerTerrain terrain;private final Ports ports;
    private Cursor active;private Call pending;
    public CdCcAnimal(CdObjectPlacementRuntime placement,Assets assets,CdPlayerMath math,CdPlayerTerrain terrain,Ports ports){
        this.placement=Objects.requireNonNull(placement);this.assets=Objects.requireNonNull(assets);
        this.math=Objects.requireNonNull(math);this.terrain=Objects.requireNonNull(terrain);this.ports=Objects.requireNonNull(ports);
    }
    public boolean isCurrentCall(Call c){return active!=null&&c!=null&&pending==c&&c.cursor==active&&c.receipt==null;}
    public Result run(int whole){
        if(active!=null)throw new IllegalStateException("CCAnimal reentry");
        var c=new Cursor(whole);active=c;
        try {
            if(state(whole).id()!=OBJECT_ID)throw new IllegalArgumentException("Not CCAnimal39");
            if(!presence(c))return result(c);
            boolean ground=(state(whole).subtype()&127)!=0;int r=state(whole).routine();
            if(ground)switch(r){case 0->groundInit(c);case 2,4->bounce(c);case 6->turn(c);case 8->groundHologram(c);default->throw missing("ground routine",r);}
            else switch(r){case 0->flyingInit(c);case 2->orbit(c);case 4->flyingHologram(c);default->throw missing("flying routine",r);}
            return result(c);
        } finally {pending=null;active=null;}
    }
    private Result result(Cursor c){return new Result(state(c.whole),c.effects);}
    private boolean presence(Cursor c){
        c.site=Site.PRESENCE;if((state(c.whole).subtype()&128)!=0)return true;
        if((byte)read(c,Field.TIME_ZONE)>=2){if(read(c,Field.GOOD_FUTURE)!=0)return true;delete(c);return false;}
        if(read(c,Field.PROJECTOR_DESTROYED)!=0)return true;
        despawn(c,state(c.whole).centreX());return false;
    }
    private void flyingInit(Cursor c){
        c.site=Site.FLYING_INIT;write(c,state(c.whole).addByte(ROUTINE,2));write(c,state(c.whole).moveByte(SPRITE_FLAGS,4));
        write(c,state(c.whole).moveByte(Y_RADIUS,8).moveByte(X_RADIUS,8).moveByte(PRIORITY,1).moveByte(WIDTH,8));
        write(c,state(c.whole).moveLong(MAPPINGS,assets.flyingAddress()));write(c,state(c.whole).moveWord(BASE_X,state(c.whole).centreX()));
        write(c,state(c.whole).moveWord(BASE_Y,state(c.whole).centreY()));flip(c);tile(c);
        if(state(c.whole).signedByte(SUBTYPE)<0){write(c,state(c.whole).addByte(ROUTINE,2));write(c,state(c.whole).moveByte(ANIMATION,1));write(c,state(c.whole).moveByte(PRIORITY,3));return;}
        write(c,state(c.whole).moveByte(PRIORITY,1));write(c,state(c.whole).moveWord(TILE,state(c.whole).word(TILE)|0x8000));
        write(c,state(c.whole).moveWord(ANTON_ANCHOR_X,0x101)); // WORD at $2E: phase/subphase.
    }
    private void groundInit(Cursor c){
        c.site=Site.GROUND_INIT;write(c,state(c.whole).addByte(ROUTINE,2));write(c,state(c.whole).moveByte(SPRITE_FLAGS,4));
        write(c,state(c.whole).moveByte(Y_RADIUS,12).moveByte(X_RADIUS,8).moveByte(PRIORITY,4).moveByte(WIDTH,8));
        write(c,state(c.whole).moveLong(MAPPINGS,assets.groundAddress()));tile(c);
        if(state(c.whole).signedByte(SUBTYPE)<0){write(c,state(c.whole).moveByte(ROUTINE,8));flip(c);return;}
        write(c,state(c.whole).moveLong(VX,0x10000));write(c,state(c.whole).moveLong(VY,-0x40000));
    }
    private void tile(Cursor c){
        c.site=Site.BASE_TILE;int d=read(c,Field.ACT)<<2;d=(d&0xFF00)|((d+read(c,Field.TIME_ZONE))&255);
        int at=(short)((d+d)&65535);if(at<0||at>assets.baseTiles().length()-2)throw missing("base tile read",at);
        write(c,state(c.whole).moveWord(TILE,assets.baseTiles().unsignedWord(at)));
    }
    private void orbit(Cursor c){
        c.site=Site.ORBIT;fly(c,1,1);int phase=state(c.whole).unsignedByte(PHASE);
        int d=(phase+state(c.whole).unsignedByte(PHASE_SUB))&255;
        if(((d-1)&255)>=127){d=phase;write(c,state(c.whole).moveByte(PHASE_SUB,-state(c.whole).unsignedByte(PHASE_SUB)));flip(c);}
        write(c,state(c.whole).moveByte(PHASE,d));animate(c,assets.flying());draw(c);despawn(c,state(c.whole).word(BASE_X));
    }
    private boolean parent(Cursor c){
        c.site=Site.PARENT;int address=((short)state(c.whole).word(PARENT))&0xFFFFFF;
        if(read(c,Field.PARENT_ID,address)!=0x38){delete(c);return false;}
        if(read(c,Field.PARENT_FLAG,(address+0x3F)&0xFFFFFF)!=0){delete(c);return false;}return true;
    }
    private void flyingHologram(Cursor c){
        if(!parent(c))return;c.site=Site.HOLOGRAM;fly(c,3,4);write(c,state(c.whole).addByte(PHASE,4));
        if((state(c.whole).unsignedByte(PHASE)&127)==0){flip(c);return;}
        animate(c,assets.flying());draw(c);
    }
    private void groundHologram(Cursor c){if(!parent(c))return;c.site=Site.HOLOGRAM;animate(c,assets.ground());draw(c);}
    private void fly(Cursor c,int xs,int ys){
        var sine=math.calcSine(state(c.whole).unsignedByte(PHASE));
        write(c,state(c.whole).moveWord(X,(sine.cosine()>>xs)+state(c.whole).word(BASE_X)));
        write(c,state(c.whole).moveWord(Y,(sine.sine()>>ys)+state(c.whole).word(BASE_Y)));
    }
    private void bounce(Cursor c){
        c.site=Site.BOUNCE;write(c,state(c.whole).moveLong(X_POSITION,state(c.whole).longBits(X_POSITION)+state(c.whole).longBits(VX)));
        write(c,state(c.whole).moveLong(Y_POSITION,state(c.whole).longBits(Y_POSITION)+state(c.whole).longBits(VY)));
        write(c,state(c.whole).moveLong(VY,state(c.whole).longBits(VY)+0x2000));
        write(c,state(c.whole).moveByte(MAP_FRAME,state(c.whole).longBits(VY)<0?0:1));
        c.site=Site.FLOOR;var floor=CdObjectCollisionCheck.objGetFloorDist(terrain,state(c.whole),v->{var call=new Angle(c,v);invoke(call,()->ports.angle(call));});
        if(floor.distance()<0){write(c,state(c.whole).addByte(ROUTINE,2));write(c,state(c.whole).addWord(Y,floor.distance()));write(c,state(c.whole).moveLong(VY,-0x40000));}
        draw(c);despawn(c,state(c.whole).centreX());
    }
    private void turn(Cursor c){write(c,state(c.whole).moveByte(ROUTINE,2));write(c,state(c.whole).moveLong(VX,-state(c.whole).longBits(VX)));flip(c);draw(c);despawn(c,state(c.whole).centreX());}
    private void flip(Cursor c){write(c,state(c.whole).moveByte(SPRITE_FLAGS,state(c.whole).spriteFlags()^1));write(c,state(c.whole).moveByte(FLAGS,state(c.whole).flags()^1));}
    private void animate(Cursor c,CdObjectVisualAssets visual){
        var s=state(c.whole);var input=new CdObjectAnimation.State(s.unsignedByte(ANIMATION),s.unsignedByte(PREVIOUS_ANIMATION),s.unsignedByte(ANIMATION_FRAME),s.unsignedByte(ANIMATION_TIME),s.unsignedByte(MAP_FRAME),s.spriteFlags(),s.flags(),s.routine(),s.unsignedByte(ROUTINE_2));
        // Publish the native reset/timer/speed prefix before a later finite script read can fail.
        if(input.animationId()!=input.previousId()){write(c,state(c.whole).moveByte(PREVIOUS_ANIMATION,input.animationId()));write(c,state(c.whole).moveByte(ANIMATION_FRAME,0));write(c,state(c.whole).moveByte(ANIMATION_TIME,0));}
        write(c,state(c.whole).addByte(ANIMATION_TIME,-1));
        if(state(c.whole).signedByte(ANIMATION_TIME)<0){
            if(input.animationId()>=visual.animations().size())throw missing("animation directory",input.animationId());
            int script=(short)visual.animationTable().unsignedWord(input.animationId()*2);
            write(c,state(c.whole).moveByte(ANIMATION_TIME,visual.animationTable().unsignedByte(script)));
        }
        var out=CdObjectAnimation.step(input,new CdObjectAnimation.AnimationTable(visual.animationTable(),0,visual.animations().size()));
        write(c,s.moveByte(ANIMATION,out.animationId()).moveByte(PREVIOUS_ANIMATION,out.previousId()).moveByte(ANIMATION_FRAME,out.scriptIndex()).moveByte(ANIMATION_TIME,out.timer()).moveByte(MAP_FRAME,out.mapFrame()).moveByte(SPRITE_FLAGS,out.spriteFlags()).moveByte(FLAGS,out.statusFlags()).moveByte(ROUTINE,out.routine()).moveByte(ROUTINE_2,out.routine2()));
    }
    private void draw(Cursor c){c.site=Site.DRAW;var call=new Draw(c);invoke(call,()->ports.draw(call));}
    private void despawn(Cursor c,int anchor){
        c.site=Site.DESPAWN;
        if(state(c.whole).signedByte(SPRITE_FLAGS)>=0){int cam=read(c,Field.CAMERA_X);if((((anchor&0xFF80)-((cam-128)&0xFF80))&65535)>0x280){despawnDelete(c);return;}}
        if((read(c,Field.TIME_ZONE)&128)!=0)despawnDelete(c);
    }
    private void despawnDelete(Cursor c){
        int id=state(c.whole).savedFlagsId();if(id!=0){int raw=read(c,Field.TIME_ZONE),period=raw&127;
            if((raw&128)!=0){int adjusted=(short)((period-(byte)read(c,Field.TIME_WARP_DIRECTION))&65535);period=adjusted<0?0:adjusted<3?adjusted:2;}
            int index=2+3*id+period;
            if(index<2||index>=CdObjectPlacementRuntime.SAVED_BANK_BYTES)throw missing("saved flag read",index);
            placement.bclrSavedFlag((index-2)/3,(index-2)%3,CdObjectPlacementRuntime.SavedBit.LOADED);
        }delete(c);
    }
    private void delete(Cursor c){placement.pool().deleteObject(c.whole);c.effects.add(new Effect(c.site,"DeleteObject"));}
    private int read(Cursor c,Field field){return read(c,field,0);}
    private int read(Cursor c,Field field,int address){var call=new Read(c,field,address);return invoke(call,()->ports.read(call)).value();}
    private CdObjectState state(int whole){return placement.pool().state(whole);}
    private void write(Cursor c,CdObjectState state){placement.pool().write(state);}
    private <T extends Receipt>T invoke(Call call,Supplier<T> callback){
        if(pending!=null)throw new IllegalStateException("Nested CCAnimal callback");pending=call;
        try{T result=callback.get();if(result==null||result!=call.receipt)throw new IllegalStateException("Missing original CCAnimal completion");fence(call,false);return result;}
        catch(RuntimeException|Error original){try{fence(call,true);}catch(RuntimeException|Error f){if(f!=original)original.addSuppressed(f);}throw original;}
        finally{pending=null;}
    }
    private void fence(Call call,boolean failed){
        placement.validateRestore(call.cut);var now=placement.capture();
        if(!now.status().equals(call.cut.status())||!Arrays.equals(now.savedFlags().copy(),call.cut.savedFlags().copy()))throw new IllegalStateException("CCAnimal callback changed placement status/bank");
        var expected=call.cut.pool();if(call instanceof Draw){var out=CdObjectPool.overlay(expected,List.of(call.source.moveByte(SPRITE_FLAGS,call.source.spriteFlags()&127)));if(now.pool().slots().equals(out.slots())||(failed&&now.pool().slots().equals(expected.slots())))return;}
        else if(now.pool().slots().equals(expected.slots()))return;
        throw new IllegalStateException("CCAnimal callback changed full pool/source cut");
    }
    private static MissingNativeOperand missing(String what,int v){return new MissingNativeOperand(what+" "+v);}
    private static final class Cursor {final int whole;long order;Site site;final List<Effect> effects=new ArrayList<>();Cursor(int w){whole=w;}}
}
