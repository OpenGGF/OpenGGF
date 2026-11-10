package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.objects.r4.CdR4SwitchDoor;
import java.util.*;
/** Original Switch27 adapter over the one shared linked-Switch body. DATA/calls do not
 * grant live authority: each private World checks its current physical/source/Frame cut. */
public final class CdR4Switch {
 private CdR4Switch(){}
 public interface Data{int mapping();int switchBaseWord();}
 public record Inputs(CdObjectPool pool,int wholeIndex,Data data){public Inputs{Objects.requireNonNull(pool);Objects.requireNonNull(data);Objects.checkIndex(wholeIndex-32,96);}}
 public enum Kind{ENTRY,OBJECT_READ,STORE,MAPPING,PRIMARY_READ,PRIMARY_Y,PARENT_READ,SWITCH_READ,SWITCH_STORE,SOLID,SOUND,DRAW,DESPAWN,DESPAWN_LINKED,BASE_READ}
 public interface Owner{
  long generation();void before(Call c);void after(Call c,Throwable original);
  int primary(ReadCall c);void primaryY(PrimaryYCall c);int parent(ReadCall c);
  int switchByte(ReadCall c);void switchByte(SwitchStoreCall c);
  boolean solid(Call c);void sound(ReadCall c);void draw(Call c);void despawn(ReadCall c);
 }
 public static class Call{
  private final Invocation issuer;private final Kind kind;private final long ordinal;private final CdObjectPool.Snapshot pool;private final CdObjectState source;private boolean completed;
  private Call(Invocation i,Kind k){issuer=i;kind=k;ordinal=++i.ordinal;pool=i.in.pool().capture();source=i.in.pool().state(i.in.wholeIndex());}
  public final void requireCurrent(Owner owner){if(owner!=issuer.owner||!issuer.active||issuer.current!=this||issuer.thread!=Thread.currentThread()||issuer.generation!=owner.generation())throw new IllegalStateException("Foreign/retired original TT Switch occurrence");}
  public final Kind kind(){requireCurrent(issuer.owner);return kind;}public final long ordinal(){return ordinal;}public final boolean completed(){return completed;}
  public final CdObjectPool pool(){requireCurrent(issuer.owner);return issuer.in.pool();}public final int wholeIndex(){requireCurrent(issuer.owner);return issuer.in.wholeIndex();}
  public final Data data(){requireCurrent(issuer.owner);return issuer.in.data();}public final CdObjectState source(){requireCurrent(issuer.owner);return source;}public final CdObjectPool.Snapshot beforePool(){requireCurrent(issuer.owner);return pool;}
 }
 public static final class ReadCall extends Call{
  private final int offset,bytes,index;private ReadCall(Invocation i,Kind k,int at,int bytes,int index){super(i,k);this.offset=at;this.bytes=bytes;this.index=index;}
  public int offset(){requireCurrent(((Call)this).issuer.owner);return offset;}public int bytes(){requireCurrent(((Call)this).issuer.owner);return bytes;}public int index(){requireCurrent(((Call)this).issuer.owner);return index;}
 }
 public static final class StoreCall extends Call{
  private final CdObjectState output;private StoreCall(Invocation i,int at,int bytes,int v){super(i,Kind.STORE);output=CdR4SolidMover.put(((Call)this).source,at,bytes,v);}
  public CdObjectState output(){requireCurrent(((Call)this).issuer.owner);return output;}
 }
 public static final class PrimaryYCall extends Call{
  private final int word;private PrimaryYCall(Invocation i,int value){super(i,Kind.PRIMARY_Y);word=value&65535;}
  public int word(){requireCurrent(((Call)this).issuer.owner);return word;}
 }
 public static final class SwitchStoreCall extends Call{
  private final int index,value;private SwitchStoreCall(Invocation i,int index,int value){super(i,Kind.SWITCH_STORE);this.index=index;this.value=value&255;}
  public int index(){requireCurrent(((Call)this).issuer.owner);return index;}public int value(){requireCurrent(((Call)this).issuer.owner);return value;}
 }
 private static final class Invocation{
  final Inputs in;final Owner owner;final Thread thread=Thread.currentThread();final long generation;boolean active=true;long ordinal;Call current;
  Invocation(Inputs in,Owner owner){this.in=Objects.requireNonNull(in);this.owner=Objects.requireNonNull(owner);generation=owner.generation();}
  <T>T perform(Call c,java.util.function.Supplier<T> body){if(current!=null||!active)throw new IllegalStateException("Nested Switch primitive");current=c;boolean entered=false;Throwable first=null;try{owner.before(c);entered=true;var value=body.get();c.completed=true;return value;}catch(RuntimeException|Error x){first=x;throw x;}finally{try{if(entered)owner.after(c,first);}catch(RuntimeException|Error guard){if(first==null)throw guard;if(first!=guard)first.addSuppressed(guard);}finally{current=null;}}}
  int read(Kind k,int at,int bytes,int index,java.util.function.ToIntFunction<ReadCall> body){var c=new ReadCall(this,k,at,bytes,index);return perform(c,()->body.applyAsInt(c));}
  void action(Kind k,java.util.function.Consumer<Call> body){var c=new Call(this,k);perform(c,()->{body.accept(c);return null;});}
  CdR4SwitchDoor.DoorImage image(boolean primary){return new CdR4SwitchDoor.DoorImage(){
   private int r(int at,int n){return read(primary?Kind.PRIMARY_READ:Kind.OBJECT_READ,at,n,0,c->{if(primary)return owner.primary(c);var a=in.pool().state(in.wholeIndex());return n==1?CdR4SolidMover.u8(a,at):n==2?CdR4SolidMover.u16(a,at):CdR4SolidMover.i32(a,at);});}
   private void w0(int at,int n,int v){if(primary){if(at!=12||n!=2)throw new UnsupportedOperationException("Switch owns only primary Y WORD");var c=new PrimaryYCall(Invocation.this,v);perform(c,()->{owner.primaryY(c);return null;});}else{var c=new StoreCall(Invocation.this,at,n,v);perform(c,()->{in.pool().write(c.output);return null;});}}
   public int b(int at){return r(at,1);}public int w(int at){return r(at,2);}public int l(int at){return r(at,4);}
   public void b(int at,int v){w0(at,1,v);}public void w(int at,int v){w0(at,2,v);}public void l(int at,int v){w0(at,4,v);}
  };}
 }
 public static void run(Inputs in,Owner owner){
  if(in.pool().state(in.wholeIndex()).id()!=0x27)throw new IllegalArgumentException("Original Switch requires ID27");
  var i=new Invocation(in,owner);try{i.action(Kind.ENTRY,c->{});CdR4SwitchDoor.runSwitch(i.image(false),i.image(true),new CdR4SwitchDoor.SwitchPorts(){
   public int mapping(){return i.read(Kind.MAPPING,0,4,0,c->in.data().mapping());}public int switchBaseWord(){return i.read(Kind.BASE_READ,0,2,0,c->in.data().switchBaseWord());}
   public int parentWord(int link,int at){return i.read(Kind.PARENT_READ,at,2,link,owner::parent);}
   public int switchByte(int index){return i.read(Kind.SWITCH_READ,0,1,index,owner::switchByte);}
   public void switchByte(int index,int value){var c=new SwitchStoreCall(i,index,value);i.perform(c,()->{owner.switchByte(c);return null;});}
   public boolean solid(){var c=new Call(i,Kind.SOLID);return i.perform(c,()->owner.solid(c));}
   public void sound(int id){i.read(Kind.SOUND,0,2,id,c->{owner.sound(c);return 0;});}public void draw(){i.action(Kind.DRAW,owner::draw);}
   public void checkDespawn(int x){i.read(Kind.DESPAWN,0,0,x,c->{owner.despawn(c);return 0;});}
   public void despawnLinked(int x){i.read(Kind.DESPAWN_LINKED,0,0,x,c->{owner.despawn(c);return 0;});}
  });}finally{i.active=false;i.current=null;}
 }
}
