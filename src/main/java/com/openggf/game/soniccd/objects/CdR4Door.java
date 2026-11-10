package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.objects.r4.CdR4SwitchDoor;
import java.util.*;
/** Actual original Door occurrence adapter over the one CdR4SwitchDoor body.
 * N1ecc r4/object_index_1a.asm ID28 -> DoorObject; no children or secondary reads.
 * Public data/ports are never World authority. Each actual World supplies its private
 * current physical/source/Frame/generation owner before every primitive. No effects replay. */
public final class CdR4Door {
 private CdR4Door(){}
 public interface Data {int mapping();int shape(int type,int field);}
 public record Inputs(CdObjectPool pool,int wholeIndex,Data data){public Inputs{Objects.requireNonNull(pool);Objects.requireNonNull(data);Objects.checkIndex(wholeIndex-32,96);}}
 public enum Kind { ENTRY,OBJECT_READ,STORE,MAPPING_READ,SHAPE_READ,PRIMARY_READ,SWITCH_READ,SOLID,DRAW,DESPAWN }
 public interface Owner {
  long generation();void before(Call c);void after(Call c,Throwable failure);
  int primary(ReadCall c);int switches(ReadCall c);void solid(Call c);void draw(Call c);void despawn(ReadCall c);
 }
 public static class Call {
  private final Invocation issuer;private final Kind kind;private final long ordinal;
  private final CdObjectPool.Snapshot cut;private final CdObjectState source;private boolean completed;
  private Call(Invocation i,Kind k){issuer=i;kind=k;ordinal=++i.ordinal;cut=i.in.pool().capture();source=i.in.pool().state(i.in.wholeIndex());}
  public final void requireCurrent(Owner owner){if(owner!=issuer.owner||!issuer.active||issuer.current!=this||issuer.thread!=Thread.currentThread()||issuer.generation!=owner.generation())throw new IllegalStateException("Foreign/retired/copied original TT Door occurrence");}
  public final Kind kind(){requireCurrent(issuer.owner);return kind;}public final long ordinal(){return ordinal;}
  public final boolean completed(){return completed;}public final CdObjectPool pool(){requireCurrent(issuer.owner);return issuer.in.pool();}
  public final Data data(){requireCurrent(issuer.owner);return issuer.in.data();}public final int wholeIndex(){requireCurrent(issuer.owner);return issuer.in.wholeIndex();}
  public final CdObjectState source(){requireCurrent(issuer.owner);return source;}public final CdObjectPool.Snapshot beforePool(){requireCurrent(issuer.owner);return cut;}
 }
 public static final class ReadCall extends Call {
  private final int offset,bytes,index;
  private ReadCall(Invocation i,Kind k,int offset,int bytes,int index){super(i,k);this.offset=offset;this.bytes=bytes;this.index=index;}
  public int offset(){requireCurrent(((Call)this).issuer.owner);return offset;}public int bytes(){requireCurrent(((Call)this).issuer.owner);return bytes;}public int index(){requireCurrent(((Call)this).issuer.owner);return index;}
 }
 public static final class StoreCall extends Call {
  private final int offset,bytes,value;private final CdObjectState output;
  private StoreCall(Invocation i,int at,int size,int v){super(i,Kind.STORE);offset=at;bytes=size;value=v;output=CdR4SolidMover.put(((Call)this).source,at,size,v);}
  public int offset(){requireCurrent(((Call)this).issuer.owner);return offset;}public int bytes(){requireCurrent(((Call)this).issuer.owner);return bytes;}public int value(){requireCurrent(((Call)this).issuer.owner);return value;}
  public CdObjectState output(){requireCurrent(((Call)this).issuer.owner);return output;}
 }
 private static final class Invocation {
  final Inputs in;final Owner owner;final Thread thread=Thread.currentThread();final long generation;boolean active=true;long ordinal;Call current;
  Invocation(Inputs in,Owner owner){this.in=Objects.requireNonNull(in);this.owner=Objects.requireNonNull(owner);generation=owner.generation();}
  <T>T perform(Call c,java.util.function.Supplier<T> body){if(current!=null||!active)throw new IllegalStateException("Nested original Door primitive");current=c;boolean entered=false;Throwable original=null;try{owner.before(c);entered=true;T value=body.get();c.completed=true;return value;}catch(RuntimeException|Error x){original=x;throw x;}finally{try{if(entered)owner.after(c,original);}catch(RuntimeException|Error guard){if(original==null)throw guard;if(guard!=original)original.addSuppressed(guard);}finally{current=null;}}}
  int read(Kind k,int at,int size,int index,java.util.function.ToIntFunction<ReadCall> body){var c=new ReadCall(this,k,at,size,index);return perform(c,()->body.applyAsInt(c));}
  void action(Kind k,java.util.function.Consumer<Call> body){var c=new Call(this,k);perform(c,()->{body.accept(c);return null;});}
  void store(int at,int size,int v){var c=new StoreCall(this,at,size,v);perform(c,()->{in.pool().write(c.output);return null;});}
  CdR4SwitchDoor.DoorImage image(boolean primary){return new CdR4SwitchDoor.DoorImage(){
   private int r(int at,int size){return read(primary?Kind.PRIMARY_READ:Kind.OBJECT_READ,at,size,0,c->{if(primary)return owner.primary(c);var a=in.pool().state(in.wholeIndex());return size==1?CdR4SolidMover.u8(a,at):size==2?CdR4SolidMover.u16(a,at):CdR4SolidMover.i32(a,at);});}
   private void w0(int at,int size,int v){if(primary)throw new UnsupportedOperationException("Door does not write a supplied primary image");store(at,size,v);}
   public int b(int at){return r(at,1);}public int w(int at){return r(at,2);}public int l(int at){return r(at,4);}
   public void b(int at,int v){w0(at,1,v);}public void w(int at,int v){w0(at,2,v);}public void l(int at,int v){w0(at,4,v);}
  };}
 }
 public static void run(Inputs in,Owner owner){var i=new Invocation(in,owner);try{
  i.action(Kind.ENTRY,c->{});
  CdR4SwitchDoor.runDoor(i.image(false),i.image(true),new CdR4SwitchDoor.DoorPorts(){
   public int mapping(){return i.read(Kind.MAPPING_READ,0,4,0,c->in.data().mapping());}
   public int shape(int type,int field){return i.read(Kind.SHAPE_READ,field,1,type,c->in.data().shape(type,field));}
   public int switchByte(int index){return i.read(Kind.SWITCH_READ,0,1,index,owner::switches);}
   public void solid(){i.action(Kind.SOLID,owner::solid);}public void draw(){i.action(Kind.DRAW,owner::draw);}
   public void checkDespawn(int x){i.read(Kind.DESPAWN,0,0,x,c->{owner.despawn(c);return 0;});}
  });
 }finally{i.active=false;i.current=null;}}
}
