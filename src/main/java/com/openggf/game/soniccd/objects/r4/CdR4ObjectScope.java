package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerMath;
import java.util.Objects;
import java.util.function.Supplier;
import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.*;

/**
 * One synchronous family invocation over the caller's original placement. Guard is mandatory;
 * it is Root's full retained-state/environment boundary, not authentication supplied by this class.
 * A completed operation is never current. Failed calls keep reached writes and original throwable.
 * No ordinary dispatch, capture schema, replay, adoption from results, or child scheduler lives here.
 */
public final class CdR4ObjectScope {
    private final CdObjectPlacementRuntime placement;
    private final CdR4ObjectPorts ports;
    private final Guard guard;
    private int actor = -1;
    private Operation pending;
    public CdR4ObjectScope(CdObjectPlacementRuntime placement, CdR4ObjectPorts ports, Guard guard) {
        this.placement=Objects.requireNonNull(placement); this.ports=Objects.requireNonNull(ports);
        this.guard=Objects.requireNonNull(guard);
    }
    public boolean isCurrent(Operation operation) { return operation!=null && actor>=0 && pending==operation; }
    public CdObjectPlacementRuntime placement() { return placement; }
    CdObjectState run(int index, int id, Runnable body) {
        if(actor>=0) throw new IllegalStateException("R4 family reentry");
        if(s(index).id()!=id) throw new IllegalArgumentException("Wrong native handler ID");
        actor=index;
        try { body.run(); return s(index); } finally {pending=null;actor=-1;}
    }
    private <T> T call(Kind kind,int target,CdObjectState output,Object operand,Supplier<T> action) {
        if(actor<0||pending!=null) throw new IllegalStateException("Outside original family invocation");
        var operation=new Operation(kind,actor,target,placement.capture(),output,operand);
        pending=operation; Throwable original=null;
        try { guard.before(this,operation); return action.get(); }
        catch(RuntimeException|Error error){original=error;throw error;}
        finally {
            try {guard.after(this,operation);}
            catch(RuntimeException|Error check){if(original==null)throw check;if(check!=original)original.addSuppressed(check);}
            finally {pending=null;}
        }
    }
    private <T> T readCall(Kind kind,Object operand,Supplier<T> action) {
        var before=placement.capture(); Throwable original=null;
        try {return call(kind,actor,null,operand,action);}
        catch(RuntimeException|Error error){original=error;throw error;}
        finally {
            try {same(before);}
            catch(RuntimeException|Error check){if(original==null)throw check;if(check!=original)original.addSuppressed(check);}
        }
    }
    private void same(CdObjectPlacementRuntime.Snapshot expected) {
        placement.validateRestore(expected);var actual=placement.capture();
        if(!actual.status().equals(expected.status())||!java.util.Arrays.equals(actual.savedFlags().copy(),expected.savedFlags().copy())
                ||!actual.pool().slots().equals(expected.pool().slots()))
            throw new IllegalStateException("Read changed R4 placement cut");
    }
    CdObjectState s(int n){return placement.pool().state(n);}
    int b(int n,int o){return s(n).copyBytes()[o]&255;}
    int w(int n,int o){return (b(n,o)<<8)|b(n,o+1);}
    int l(int n,int o){return (w(n,o)<<16)|w(n,o+2);}
    void put(CdObjectState out){call(Kind.STORE,out.identity().wholeIndex(),out,null,()->{placement.pool().write(out);return null;});}
    void b(int n,int o,int value){
        for(var field:CdObjectState.ByteField.values()) if(field.byteOffset()==o){put(s(n).moveByte(field,value));return;}
        int at=o&~1,old=w(n,at);w(n,at,(o&1)==0?((value&255)<<8)|(old&255):(old&0xff00)|(value&255));
    }
    void w(int n,int o,int value){
        for(var field:CdObjectState.WordField.values()) if(field.byteOffset()==o){put(s(n).moveWord(field,value));return;}
        throw new IllegalArgumentException("Unowned R4 word "+o);
    }
    void l(int n,int o,int value){
        for(var field:CdObjectState.LongField.values()) if(field.byteOffset()==o){put(s(n).moveLong(field,value));return;}
        // Prepare both words before one native LONG publication; retain pool owner and exact identity.
        CdObjectState out=s(n);for(int k=0;k<2;k++){
            boolean found=false;for(var field:CdObjectState.WordField.values())if(field.byteOffset()==o+2*k){out=out.moveWord(field,k==0?value>>>16:value);found=true;break;}
            if(!found)throw new IllegalArgumentException("Unowned R4 long "+o);
        }put(out);
    }
    void ab(int n,int o,int d){b(n,o,b(n,o)+d);} void aw(int n,int o,int d){w(n,o,w(n,o)+d);}
    void al(int n,int o,int d){l(n,o,l(n,o)+d);}
    void flip(int n){b(n,1,b(n,1)^1);b(n,0x22,b(n,0x22)^1);}
    void delete(int n){call(Kind.DELETE,n,null,null,()->{placement.pool().deleteObject(n);return null;});}
    int spawn(int n,boolean after){return after?placement.pool().findNextObjSlotAfterWholeIndex(n).orElse(-1):placement.pool().findObjSlot().orElse(-1);}
    int raw(int n){return s(n).identity().nativeAddress24()&65535;}
    int link(int low){
        int address=(short)low, delta=address-(short)0xd000;
        if((delta&63)!=0||delta<32*64||delta>=128*64)
            throw new UnsupportedOperationException("Reached R4 raw link outside owned aligned dynamic pool: "+Integer.toHexString(low));
        return delta/64;
    }
    int read(Read field){int value=readCall(Kind.READ,field,()->ports.read(field));
        int mask=switch(field){case WATER_Y,STATIC_WATER_Y,PLAYER_X,PLAYER_Y,PLAYER_HURT_TIMER,PLAYER_Y_SPEED->65535;default->255;};
        if((value&~mask)!=0)throw new IllegalArgumentException("Native read width: "+field);return value;}
    int pointer(Data field){return readCall(Kind.POINTER,field,()->ports.pointer(field));}
    int tile(int offset){int tile=readCall(Kind.ANIMAL_TILE,offset,()->ports.animalTile(offset));if((tile&~65535)!=0)throw new IllegalArgumentException("Native tile WORD");return tile;}
    CdPlayerMath.SineCosine sine(int phase){
        var out=Objects.requireNonNull(readCall(Kind.SINE,phase&255,()->ports.sine(phase&255)));
        if(out.sine()!=(short)out.sine()||out.cosine()!=(short)out.cosine())throw new IllegalArgumentException("SineCosine requires signed native WORDs");
        return out;
    }
    void animate(int n,Data table){int ptr=pointer(table);animate(n,table,ptr);}
    void animate(int n,Data table,int ptr){call(Kind.ANIMATE,n,null,new AnimationOperand(table,ptr),()->{ports.animate(n,table,ptr);return null;});}
    void draw(int n){call(Kind.DRAW,n,null,null,()->{ports.draw(n);return null;});}
    void despawn(int n,Integer anchor){call(anchor==null?Kind.DESPAWN_CURRENT:Kind.DESPAWN_ANCHOR,n,null,anchor,()->{ports.despawn(n,anchor);return null;});}
    int floor(int n){int v=call(Kind.FLOOR,n,null,null,()->ports.floor(n));return (short)v;}
    int wall(int n){boolean right=(b(n,1)&1)!=0;return (short)(int)call(right?Kind.WALL_RIGHT:Kind.WALL_LEFT,n,null,b(n,0x19),()->ports.wall(n,right,b(n,0x19)));}
    boolean solid(int n){return call(Kind.SOLID,n,null,null,()->ports.solid(n));}
    void hurt(int n){call(Kind.HURT_WITH_Y_REWIND,n,null,null,()->{ports.hurtWithYRewind(n);return null;});}
    void fm(int sound){call(Kind.FM,actor,null,sound,()->{ports.fm(sound);return null;});}
    void test(int n){call(Kind.TEST_SPAWN_POINTS,n,null,null,()->{ports.testSpawnPoints(n);return null;});}
    boolean future(int n){
        if(read(Read.GOOD_FUTURE)==0)return false;
        if(read(Read.TIME_ZONE)==1&&b(n,0x28)==0)return false;
        int x=w(n,8),y=w(n,12);delete(n);w(n,8,x);w(n,12,y);b(n,0,0x18);
        // Native tests the now-cleared sprite flags, so the FM9E branch is not reached.
        if((byte)b(n,1)<0)fm(0x9e);return true;
    }
    void routine(int n,int maximum){int value=b(n,0x24);if((value&1)!=0||value>maximum)throw new UnsupportedOperationException("Native R4 routine table index "+value);}
    boolean range(int value,int start,int length){return ((value-start)&65535)<length;}
}
