package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.*;
import java.util.function.Supplier;
/** Original enemy primitive fence. No walk, queue, gameplay algorithm or captured lifecycle.
 * Instances stay inside authenticated cached bodies. Call values cannot invoke or resume anything.
 * World must prove the exact current issuer and full caller cut before/after every primitive. */
public final class CdEnemyPrimitiveScope {
 public enum Kind { ENTER, RETURN, STORE, DELETE, SAVED_CLEAR, DRAW, ANGLE, PLAYER, FM, FIND, ANIMATION }
 public interface Observer {void before(Call call);void after(Call call);}
 public static final class Call {
  private final Kind kind;private final long generation,ordinal;private final int whole,id,savedIndex;
  private final CdObjectPlacementRuntime.Snapshot before;private final CdObjectState output;
  private boolean completed;
  private Call(Kind k,long g,long o,int w,int id,int saved,CdObjectPlacementRuntime.Snapshot b,CdObjectState out){kind=k;generation=g;ordinal=o;whole=w;this.id=id;savedIndex=saved;before=b;output=out;}
  public Kind kind(){return kind;}public long generation(){return generation;}public long ordinal(){return ordinal;}
  public int whole(){return whole;}public int id(){return id;}public int savedIndex(){return savedIndex;}
  public CdObjectPlacementRuntime.Snapshot before(){return before;}public CdObjectState output(){return output;}public boolean completed(){return completed;}
 }
 private final CdObjectPlacementRuntime placement;private final Observer observer;private final Thread thread=Thread.currentThread();
 private long generation,ordinal;private int whole,id;private boolean running;private Call current;
 private CdObjectAnimation.Prefix animator;private CdObjectAnimation.Operation animationOperation;
 CdEnemyPrimitiveScope(CdObjectPlacementRuntime p,Observer o){placement=Objects.requireNonNull(p);observer=Objects.requireNonNull(o);}
 public boolean isCurrent(Call c){return Thread.currentThread()==thread&&running&&c!=null&&current==c&&c.generation==generation&&(animationOperation==null||animator.isCurrent(animationOperation));}
 void enter(int w,int id){if(running||Thread.currentThread()!=thread)throw new IllegalStateException("Enemy original thread/reentry");placement.pool().state(w);whole=w;this.id=id;generation++;ordinal=0;running=true;call(Kind.ENTER,null,-1,()->null);}
 void returned(){call(Kind.RETURN,null,-1,()->null);}
 void close(){current=null;animationOperation=null;animator=null;running=false;}
 void write(CdObjectState out){call(Kind.STORE,out,-1,()->{placement.pool().write(out);return null;});}
 void delete(int w){if(w!=whole)throw new IllegalArgumentException("Enemy DeleteObject must retain its current physical identity");call(Kind.DELETE,null,-1,()->{placement.pool().deleteObject(w);return null;});}
 void saved(int savedId,int period){
  int index=2+(short)((3*savedId+period)&65535);
  if(index<2||index>=CdObjectPlacementRuntime.SAVED_BANK_BYTES)throw new UnsupportedOperationException("Native enemy saved BYTE outside actual bank: "+index);
  call(Kind.SAVED_CLEAR,null,index,()->{placement.bclrSavedFlag((index-2)/3,(index-2)%3,CdObjectPlacementRuntime.SavedBit.LOADED);return null;});
 }
 <T>T invoke(Kind k,Supplier<T> action){return call(k,null,-1,action);}
 void draw(Runnable action){var s=placement.pool().state(whole);call(Kind.DRAW,s.moveByte(CdObjectState.ByteField.SPRITE_FLAGS,s.spriteFlags()&127),-1,()->{action.run();return null;});}
 private Call begin(Kind k,CdObjectState out,int saved){
  if(!running||Thread.currentThread()!=thread||current!=null)throw new IllegalStateException("No current original enemy primitive");
  return current=new Call(k,generation,++ordinal,whole,id,saved,placement.capture(),out);
 }
 private <T>T call(Kind k,CdObjectState out,int saved,Supplier<T> action){
  var c=begin(k,out,saved);Throwable failure=null;T result=null;
  try{
   try{observer.before(c);result=action.get();c.completed=true;}catch(RuntimeException|Error e){failure=e;}
   try{observer.after(c);}catch(RuntimeException|Error e){failure=retain(failure,e);}
   if(failure instanceof Error e)throw e;if(failure!=null)throw (RuntimeException)failure;return result;
  }finally{current=null;}
 }
 CdObjectAnimation.State animate(CdObjectAnimation.AnimationTable table){return animate(table,op->{});}
 CdObjectAnimation.State animate(CdObjectAnimation.AnimationTable table,java.util.function.Consumer<CdObjectAnimation.Operation> selected){
  animator=new CdObjectAnimation.Prefix(placement.pool(),whole,new CdObjectAnimation.Guard(){
   public void before(CdObjectAnimation.Operation op){
    animationOperation=op;CdObjectState out=op.kind()==CdObjectAnimation.Kind.STORE?placement.pool().state(whole).moveByte(op.field(),op.value()):null;
    var c=begin(Kind.ANIMATION,out,-1);observer.before(c);selected.accept(op);
   }
   public void after(CdObjectAnimation.Operation op){
    var c=current;if(c==null)throw new IllegalStateException("Missing enemy animation occurrence");
    try{c.completed=op.completed();observer.after(c);}finally{current=null;animationOperation=null;}
   }
  });
  try{return animator.step(table);}finally{current=null;animator=null;animationOperation=null;}
 }
 private static Throwable retain(Throwable first,Throwable next){if(first==null)return next;if(first!=next&&Arrays.stream(first.getSuppressed()).noneMatch(s->s==next))first.addSuppressed(next);return first;}
}
