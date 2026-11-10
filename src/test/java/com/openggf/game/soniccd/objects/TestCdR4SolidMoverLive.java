package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.CdPlayerMath;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdR4SolidMover.*;

/** Constructed placement/probe/owner controls; not original TT source admission or World/route evidence.
 * TestCdR4SolidMovers supplies explicitly synthetic geometry, not runtime fallback assets. */
class TestCdR4SolidMoverLive {
 @Test void sameNativeBodyMatchesPureValuesForAllSevenSubtypePairsWithoutSecondaryInputs(){
  for(int subtype=0;subtype<7;subtype++){var h=new H();h.set(0x28,1,subtype);var old=h.place.capture();var pure=CdR4Block.run(h.pure());assertTrue(pure.stopped().isEmpty());h.run();assertEquals(pure.prefix().pool().slots(),h.place.capture().pool().slots());assertEquals(pure.prefix().primary(),h.primary);assertEquals(pure.prefix().globals(),h.globals);assertEquals(Tail.DRAW_DESPAWN_X,h.tail);assertEquals(1,h.solidCalls);assertEquals(1,h.tailCalls);assertArrayEquals(old.savedFlags().copy(),h.place.capture().savedFlags().copy());assertEquals(0xBEEF,CdR4SolidMover.u16(h.place.pool().state(40),10));assertEquals(0xABCD,CdR4SolidMover.u16(h.place.pool().state(40),14));}
 }
 @Test void pureEntryRemainsDetachedAndItsObservationsAreNeverLiveInvocations(){
  var h=new H();var before=h.place.capture();var pure=CdR4Block.run(h.pure());assertEquals(before.pool().slots(),h.place.capture().pool().slots());assertTrue(pure.prefix().observations().stream().anyMatch(v->v instanceof Solid));assertEquals(0,h.beforeCalls);assertEquals(0,h.solidCalls);assertEquals(0,h.tailCalls);
 }
 @Test void nonzeroRoutineUsesSameMainWithoutRepeatingAnyInitStoreOrDataBorrow(){
  var h=new H();h.set(0x24,1,2);h.set(0x19,1,16);h.set(0x16,1,16);h.run();assertEquals(0,h.stores);assertEquals(0,h.data.reads);assertEquals(1,h.solidCalls);assertEquals(1,h.tailCalls);
 }
 @Test void mappingReadFailureKeepsNativeEarlierInitStoresAndNoMappingDimensionOrTail(){
  var h=new H();h.data.mapFault=Mapping.BLOCK;var e=assertThrows(IllegalStateException.class,h::run);assertEquals("mapping read",e.getMessage());var o=h.place.pool().state(40);assertEquals(2,o.routine());assertEquals(1,CdR4SolidMover.u8(o,0x18));assertEquals(0x446A,CdR4SolidMover.u16(o,2));assertEquals(0,CdR4SolidMover.i32(o,4));assertEquals(0,h.solidCalls);assertEquals(0,h.tailCalls);
 }
 @Test void secondSizeByteFailureRetainsMappingAndFrameButNeitherDimensionStore(){
  var h=new H();h.data.tables.put(Table.BLOCK_SIZES,TestCdR4SolidMovers.bytes(31));assertThrows(IndexOutOfBoundsException.class,h::run);var o=h.place.pool().state(40);assertEquals(0x20ABCD,CdR4SolidMover.i32(o,4));assertEquals(0,CdR4SolidMover.u8(o,0x17));assertEquals(0,CdR4SolidMover.u8(o,0x19));assertEquals(0,h.solidCalls);assertEquals(0,h.tailCalls);
 }
 @Test void refusedBeforeStoreStillRunsAfterAndPreservesExactOriginalFault(){
  var h=new H();h.beforeStoreFault=new Fault("before");var old=h.place.capture();assertSame(h.beforeStoreFault,assertThrows(Fault.class,h::run));assertEquals(1,h.refusedAfter);assertEquals(0,h.stores);assertEquals(old.pool().slots(),h.place.capture().pool().slots());assertEquals(0,h.solidCalls);
 }
 @Test void landedStoreThenAfterFaultRetainsOnlyThatWriteAndOriginalIdentity(){
  var h=new H();h.afterStoreFault=new Fault("landed after");assertSame(h.afterStoreFault,assertThrows(Fault.class,h::run));assertEquals(2,h.place.pool().state(40).routine());assertEquals(1,h.stores);assertEquals(0,h.place.pool().state(40).spriteFlags());assertEquals(0,h.solidCalls);assertEquals(0,h.tailCalls);
 }
 @Test void originalAndDistinctAfterFaultsRemainDistinctWithoutSelfSuppression(){
  var h=new H();var first=new Fault("before");var second=new Fault("after");h.beforeStoreFault=first;h.afterAnyFault=second;assertSame(first,assertThrows(Fault.class,h::run));assertTrue(Arrays.asList(first.getSuppressed()).contains(second));
  var same=new H();same.beforeStoreFault=first=new Fault("same");same.afterAnyFault=first;assertSame(first,assertThrows(Fault.class,same::run));assertEquals(0,first.getSuppressed().length);
 }
 @Test void fullPoolMutationDuringReadIsDetectedAndNeverReclassifiedAsOwnedStore(){
  var h=new H();h.onRead=()->h.place.pool().write(CdR4SolidMover.put(h.place.pool().state(127),0x28,1,0x77));assertThrows(IllegalStateException.class,h::run);assertEquals(0,h.stores);assertEquals(0x77,h.place.pool().state(127).subtype());assertEquals(0,h.solidCalls);
 }
 @Test void eachStoreHasExactActualRowSourceAndNativeWidthInsteadOfByteDecomposition(){
  var h=new H();h.run();assertEquals(List.of("36/1","1/1","24/1","2/2","4/4","26/1","23/1","25/1","22/1"),h.storeFields);assertTrue(h.calls.stream().map(Call::ordinal).distinct().count()==h.calls.size());assertEquals(1,h.tailCalls);
 }
 @Test void actualSolidCallOccursOnceAndExistingPlainSolidPublishesOldSupportRelease(){
  var h=new H();h.set(1,1,128);h.place.pool().write(CdR4SolidMover.put(CdR4SolidMover.put(h.place.pool().state(41),0,1,0x34),0x22,1,8));h.primary=TestCdR4SolidMovers.player(1,0x1001234,0x1E05678,0x123,0x100,10,41);var pure=CdR4Block.run(h.pure());assertTrue(pure.stopped().isEmpty());h.run();assertEquals(1,h.solidCalls);assertEquals(pure.prefix().primary(),h.primary);assertEquals(0,CdR4SolidMover.u8(h.place.pool().state(41),0x22));assertEquals(40,h.primary.standObject());
 }
 @Test void solidRefusalStopsBeforeDrawAndRetainsAllEarlierInitFields(){
  var h=new H();h.solidFault=new Fault("actual Solid port refused");assertSame(h.solidFault,assertThrows(Fault.class,h::run));assertEquals(0x20ABCD,CdR4SolidMover.i32(h.place.pool().state(40),4));assertEquals(16,CdR4SolidMover.u8(h.place.pool().state(40),0x19));assertEquals(1,h.solidCalls);assertEquals(0,h.tailCalls);
 }
 @Test void throwingAfterActualSolidPublicationRetainsItsLandedPoolPrefix(){
  var h=new H();h.set(1,1,128);h.primary=TestCdR4SolidMovers.player(1,0x1001234,0x1E05678,0,0x100,0,0);var pure=CdR4Block.run(h.pure());assertTrue(pure.stopped().isEmpty());h.solidLandedFault=new Fault("landed Solid port");assertSame(h.solidLandedFault,assertThrows(Fault.class,h::run));assertEquals(pure.prefix().pool().slots(),h.place.capture().pool().slots());assertEquals(pure.prefix().primary(),h.primary);assertEquals(0,h.tailCalls);
 }
 @Test void actualTailFailureDoesNotRerunSolidOrInitAndExpiresEveryReceipt(){
  var h=new H();h.tailFault=new Fault("actual Draw/despawn fault");assertSame(h.tailFault,assertThrows(Fault.class,h::run));assertEquals(1,h.solidCalls);assertEquals(1,h.tailCalls);assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(h));assertEquals(9,h.stores);
 }
 @Test void caughtReentryDoesNotExpireHealthyOuterOccurrence(){
  var h=new H();h.onBefore=()->assertThrows(IllegalStateException.class,h::run);h.run();assertEquals(1,h.tailCalls);assertEquals(9,h.stores);
 }
 @Test void offThreadForeignAndRetiredReceiptsCannotAuthorizeAnyCallback(){
  var h=new H();var other=new H();h.onBefore=()->{var c=h.saved;assertThrows(IllegalStateException.class,()->c.requireCurrent(other));var fault=new AtomicReference<Throwable>();var t=new Thread(()->{try{c.requireCurrent(h);}catch(Throwable e){fault.set(e);}});t.start();try{t.join();}catch(InterruptedException e){throw new AssertionError(e);}assertInstanceOf(IllegalStateException.class,fault.get());};h.run();assertThrows(IllegalStateException.class,()->h.saved.requireCurrent(h));
 }
 @Test void wrongDispatchIdIsRejectedBeforeAnyNativeReadStoreOrCallback(){
  var h=new H();h.set(0,1,0x22);var before=h.place.capture();assertThrows(IllegalArgumentException.class,h::run);assertEquals(0,h.beforeCalls);assertEquals(before.pool().slots(),h.place.capture().pool().slots());assertEquals(0,h.data.reads);
 }
 @Test void noSecondaryWidthsWaterCallbacksOrPublicReceiptFactoryAreIntroduced(){
  assertEquals(List.of("placement","wholeIndex","primary","gates","globals","solid","data"),Arrays.stream(PrimaryInputs.class.getRecordComponents()).map(v->v.getName()).toList());assertEquals(0,Call.class.getConstructors().length);assertEquals(0,StoreCall.class.getConstructors().length);assertEquals(0,SolidCall.class.getConstructors().length);assertEquals(0,TailCall.class.getConstructors().length);var h=new H();h.run();assertEquals(1,h.tailCalls);
 }
 @Test void reusablePlainEntrySharesTheSameIssuedSolidPathWithoutBlockInitDataOrTail(){
  var h=new H();h.set(0,1,0x22);h.set(1,1,128);h.set(0x16,1,16);h.set(0x19,1,16);var before=h.place.pool().capture();var expected=h.kernel.runWithPrefix(CdSolidObject.Entry.SOLID_OBJECT,h.place.pool().state(40),h.primary,h.gates,h.globals,before);var ok=assertInstanceOf(CdSolidObject.Completed.class,expected);h.running=true;try{assertEquals(ok.completedResult().d0().orElse(0),CdR4SolidMover.runPlainSolid(h.inputs(),h));}finally{h.running=false;}assertEquals(1,h.solidCalls);assertEquals(0,h.data.reads);assertEquals(0,h.stores);assertEquals(0,h.tailCalls);assertEquals(ok.completedResult().player(),h.primary);
 }
 @Test void reusablePlainEntryRefusesBeforeItsActualSolidAndStillRunsAfter(){
  var h=new H();var first=new Fault("actual plain Solid before");h.onBefore=()->{throw first;};var old=h.place.capture();h.running=true;try{assertSame(first,assertThrows(Fault.class,()->CdR4SolidMover.runPlainSolid(h.inputs(),h)));}finally{h.running=false;}assertEquals(0,h.solidCalls);assertEquals(old.pool().slots(),h.place.capture().pool().slots());assertEquals(0,h.tailCalls);
 }
 @Test void originalNativeSolidStopSurvivesPrefixPublicationAndDistinctPublisherFault(){
  var h=new H();h.set(1,1,128);h.primary=TestCdR4SolidMovers.player(1,0x1001234,0x1E05678,0x123,0x100,10,1);var pure=CdR4Block.run(h.pure());assertTrue(pure.stopped().isPresent());var additional=new Fault("after original native prefix");h.solidLandedFault=additional;var first=assertThrows(RuntimeException.class,h::run);assertSame(h.nativeFirst,first);assertNotSame(additional,first);assertTrue(Arrays.asList(first.getSuppressed()).contains(additional));assertEquals(pure.prefix().pool().slots(),h.place.capture().pool().slots());assertEquals(pure.prefix().primary(),h.primary);assertEquals(0,h.tailCalls);
 }
 static final class Fault extends RuntimeException{Fault(String s){super(s);}}
 static final class H implements LiveOwner {
  final CdObjectPlacementRuntime place=placement();final TestCdR4SolidMovers.Data data=new TestCdR4SolidMovers.Data();final CdSolidObject kernel=TestCdR4SolidMovers.solid();
  CdSolidObject.Player primary=TestCdR4SolidMovers.player(0,0x1001234,0x1005678,0,0,0,0);final CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);CdSolidObject.Globals globals=new CdSolidObject.Globals(0,0,0x12,0x34);
  final List<Call> calls=new ArrayList<>();final List<String> storeFields=new ArrayList<>();long generation=7;Call saved;int beforeCalls,stores,solidCalls,tailCalls,refusedAfter;Tail tail;boolean running;
  Throwable nativeFirst;Fault beforeStoreFault,afterStoreFault,afterAnyFault,solidFault,solidLandedFault,tailFault;Runnable onRead=()->{},onBefore=()->{};
  H(){set(0,1,0x34);set(8,4,0x100BEEF);set(12,4,0x200ABCD);}
  void set(int at,int size,int value){place.pool().write(CdR4SolidMover.put(place.pool().state(40),at,size,value));}
  PrimaryInputs inputs(){return new PrimaryInputs(place,40,primary,gates,globals,kernel,data);}
  Inputs pure(){return new Inputs(place.pool().capture(),40,primary,TestCdR4SolidMovers.player(2,0x2222345,0x3333456,-13,17,2,0),gates,globals,kernel,data,a->new CdPlayerMath.SineCosine(0,256),new Terrain(){public Probe floor(int x,int y){return new Probe(16,OptionalInt.empty(),OptionalInt.empty());}public Probe wall(boolean right,int x,int y){return new Probe(-1,OptionalInt.empty(),OptionalInt.empty());}},place.capture().savedFlags(),1,new Water(0x780,0x780,0x780,0x1234),new ActorWidths(16,24));}
  void run(){if(running)throw new IllegalStateException("Actual fixture owner reentry");running=true;try{CdR4Block.run(inputs(),this);}finally{running=false;}}
  public long generation(){return generation;}
  public void requireCurrent(Call c){c.requireCurrent(this);assertTrue(running);assertSame(place,c.placement());assertEquals(40,c.wholeIndex());}
  public void before(Operation c){requireCurrent(c);saved=c;calls.add(c);beforeCalls++;onBefore.run();if(c instanceof ReadCall)onRead.run();if(c instanceof StoreCall&&beforeStoreFault!=null)throw beforeStoreFault;}
  public void after(Operation c){requireCurrent(c);if(c instanceof StoreCall store){if(c.completed()){stores++;storeFields.add(store.offset()+"/"+store.bytes());assertEquals(store.output(),place.pool().state(40));if(afterStoreFault!=null)throw afterStoreFault;}else refusedAfter++;}if(afterAnyFault!=null&&c instanceof StoreCall)throw afterAnyFault;}
  public void solid(SolidCall c){requireCurrent(c);solidCalls++;assertEquals(CdSolidObject.Entry.SOLID_OBJECT,c.entry());assertEquals(primary,c.primary());assertEquals(globals,c.globals());if(solidFault!=null)throw solidFault;var out=c.outcome();if(out instanceof CdSolidObject.Completed ok){var r=ok.completedResult();place.pool().write(r.object());for(var o:r.otherSlots())place.pool().write(o);primary=r.player();globals=r.globals();}else{nativeFirst=((CdSolidObject.NativeStopped)out).stop().originalException();var p=((CdSolidObject.NativeStopped)out).prefix();place.pool().write(p.object());for(var o:p.otherSlots())place.pool().write(o);primary=p.player();globals=p.globals();}if(solidLandedFault!=null)throw solidLandedFault;}
  public void tail(TailCall c){requireCurrent(c);tailCalls++;tail=c.tail();assertEquals(Tail.DRAW_DESPAWN_X,tail);if(tailFault!=null)throw tailFault;}
  private static CdObjectPlacementRuntime placement(){var b=ByteBuffer.allocate(26);b.putShort((short)12).putShort((short)20).putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0xFFFF).putInt(0);return new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(b.array()));}
 }
}
