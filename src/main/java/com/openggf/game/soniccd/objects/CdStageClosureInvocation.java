package com.openggf.game.soniccd.objects;
import java.util.Objects;
import java.util.function.Supplier;
/** Private issuer/current callback lifetime for the original R11 stage-closure adapters.
 * Arguments and full cuts describe values only. Only a synchronous cached issuer's current Call
 * can borrow the World. No invocation or completion token can be constructed/replayed publicly. */
public final class CdStageClosureInvocation {
 private CdStageClosureInvocation(){}
 public interface Observer {void before(Call call);void after(Call call,boolean returned);}
 public static final class Call {
  private final Issuer owner;private final Object argument;private final CdObjectPlacementRuntime.Snapshot cut;private final CdObjectState source;
  private final Thread thread=Thread.currentThread();private boolean completed;
  private Call(Issuer o,Object a,CdObjectPlacementRuntime.Snapshot c,CdObjectState s){owner=o;argument=a;cut=c;source=s;}
  public Object argument(){return argument;}public CdObjectPlacementRuntime.Snapshot cut(){return cut;}public CdObjectState source(){return source;}
  public boolean completed(){return completed;}
 }
 static final class Issuer {
  private final Observer observer;private Call current;
  Issuer(Observer observer){this.observer=Objects.requireNonNull(observer);}
  boolean current(Call call){return call!=null&&call.owner==this&&current==call&&call.thread==Thread.currentThread();}
  <T>T invoke(Object argument,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,Supplier<T> primitive){
   if(current!=null)throw new IllegalStateException("Stage closure callback reentry");
   var call=new Call(this,argument,cut,source);current=call;
   boolean beforeReturned=false,afterStarted=false;
   try{observer.before(call);beforeReturned=true;T result=primitive.get();call.completed=true;afterStarted=true;observer.after(call,true);return result;}
   catch(RuntimeException|Error original){try{if(beforeReturned&&!afterStarted)observer.after(call,false);}catch(RuntimeException|Error verification){if(verification!=original)original.addSuppressed(verification);}throw original;}
   finally{current=null;}
  }
 }
}
