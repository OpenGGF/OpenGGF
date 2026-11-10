package com.openggf.game.soniccd.objects;
import java.util.*;
import java.util.function.*;
/** Stack-only fences for five existing PP kernels. No scheduler, gameplay state or capture owner.
 * Only package-private body code can create an occurrence. Public observations cannot invoke it.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdOriginalMechanismProtocol {
 public enum NativeSite { DISC_READ,DISC_DRAW,DISC_SOLID_OWNER,HOLE_DRAW,FLOOR_OWNER,WALL_SOLID_OWNER }
 public enum Site { STORE, DELETE, SAVED, CALLBACK, ANIMATION_READ }
 public static final class Call {
  private final CdOriginalMechanismProtocol issuer;private final long ordinal;
  private final Site site;private final Object payload;private final CdObjectPlacementRuntime.Snapshot cut;
  private final CdObjectState source,output;private final int savedIndex,savedValue;private boolean completed;
  private Call(CdOriginalMechanismProtocol i,Site s,Object p,CdObjectState o,int at,int value){issuer=i;ordinal=++i.ordinal;site=s;payload=p;cut=i.placement.capture();source=i.placement.pool().state(i.whole);output=o;savedIndex=at;savedValue=value;}
  public long ordinal(){return ordinal;}public Site site(){return site;}public Object payload(){return payload;}
  public CdObjectPlacementRuntime.Snapshot cut(){return cut;}public CdObjectState source(){return source;}public CdObjectState output(){return output;}
  public int savedIndex(){return savedIndex;}public int savedValue(){return savedValue;}public boolean completed(){return completed;}
 }
 public interface Observer {void before(Call c);void after(Call c);void failed(Call c,Throwable original);}
 private final CdObjectPlacementRuntime placement;private final Observer observer;
 private Thread thread;private int whole=-1;private long ordinal;private Call pending;
 CdOriginalMechanismProtocol(CdObjectPlacementRuntime p,Observer o){placement=Objects.requireNonNull(p);observer=Objects.requireNonNull(o);}
 void begin(int w){if(whole!=-1)throw new IllegalStateException("Mechanism reentry");placement.pool().state(w);whole=w;thread=Thread.currentThread();ordinal=0;}
 void end(){pending=null;whole=-1;thread=null;}
 public boolean current(Call c){return c!=null&&whole>=32&&Thread.currentThread()==thread&&pending==c&&c.issuer==this;}
 private Call open(Site site,Object payload,CdObjectState output,int index,int value){
  if(whole<32||Thread.currentThread()!=thread||pending!=null)throw new IllegalStateException("No unique current mechanism primitive");
  var c=new Call(this,site,payload,output,index,value);pending=c;
  try{observer.before(c);return c;}catch(RuntimeException|Error fault){failed(c,fault);pending=null;throw fault;}
 }
 private void failed(Call c,Throwable original){try{observer.failed(c,original);}catch(RuntimeException|Error fault){if(fault!=original)original.addSuppressed(fault);}}
 private <T>T invoke(Site s,Object p,CdObjectState out,int at,int value,Supplier<T> action){
  var c=open(s,p,out,at,value);try{T result=action.get();c.completed=true;observer.after(c);return result;}
  catch(RuntimeException|Error fault){failed(c,fault);throw fault;}finally{pending=null;}
 }
 void store(CdObjectState output,Runnable action){invoke(Site.STORE,null,output,-1,0,()->{action.run();return null;});}
 void delete(Runnable action){invoke(Site.DELETE,null,null,-1,0,()->{action.run();return null;});}
 void saved(int at,int value,Runnable action){invoke(Site.SAVED,null,null,at,value,()->{action.run();return null;});}
 <T>T callback(Object payload,Supplier<T> action){return invoke(Site.CALLBACK,payload,null,-1,0,action);}
 void callback(Object payload,Runnable action){callback(payload,()->{action.run();return null;});}
 void animate(CdObjectAnimation.AnimationTable table,BooleanSupplier active,Consumer<CdObjectState> refresh,
              Function<CdObjectAnimationBinding.ReadFailure,? extends RuntimeException> missing){
  CdObjectAnimationBinding.run(placement,whole,table,active,refresh,new CdObjectAnimationBinding.Guard(){
   public void before(CdObjectAnimation.Prefix p,CdObjectAnimation.Operation op){
    CdObjectState output=op.kind()==CdObjectAnimation.Kind.STORE?placement.pool().state(whole).moveByte(op.field(),op.value()):null;
    open(output==null?Site.ANIMATION_READ:Site.STORE,op,output,-1,0);
   }
   public void after(CdObjectAnimation.Prefix p,CdObjectAnimation.Operation op){
    var c=pending;try{if(!current(c)||c.payload()!=op||!p.isCurrent(op))throw new IllegalStateException("Changed actual animation primitive");c.completed=op.completed();observer.after(c);}
    catch(RuntimeException|Error fault){failed(c,fault);throw fault;}finally{pending=null;}
   }
  },op->{},missing);
 }
}
