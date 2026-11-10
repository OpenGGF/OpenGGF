package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Proposed source obligations only. Java/discovery/runtime UNRUN; unsupported/death never passes. */
@Isolated
class TestCdOriginalR11EnemyFamilyWorld extends TestCdOriginalR11LoadingWorld {
 @Test void actualSourcePlacedPataWhole52MustReturnAndReplayFullAndCourse()throws Exception{route(0x3E,52);}
 @Test void actualAntonMustReturnAndReplayFullAndCourse()throws Exception{route(0x3F,-1);}
 @Test void actualTamabbohMustReturnAndReplayFullAndCourse()throws Exception{route(0x22,-1);}
 private void route(int id,int requiredWhole)throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);for(int i=0;i<256&&m.originalR11LoadingPrefix().main().levelStartedByte()==0;i++)step(loop,0);
   assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());var f=m.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
   var full=registry.capture();var course=registry.captureCourse();var services=m.programServicesState();int rows=0;boolean reached=false,mosqui=false,plant=false;
   while(rows<4096&&!reached){
    step(loop,8);rows++;assertFalse(f.closed());assertTrue(f.state().motion().routine()<6,"Earlier death cannot certify enemy service");
    var slots=m.originalR11PlacementImage().pool().slots();
    mosqui|=slots.stream().anyMatch(s->s.id()==CdMosqui.OBJECT_ID&&s.routine()!=0);
    plant|=slots.stream().anyMatch(s->s.id()==Cd3dPlant.OBJECT_ID&&s.routine()!=0);
    reached=slots.stream().anyMatch(s->s.id()==id&&s.routine()!=0&&(requiredWhole<0||s.identity().wholeIndex()==requiredWhole));
   }
   assertTrue(reached,"Actual original public Right route must return the source-placed enemy");assertTrue(mosqui);assertTrue(plant);
   var expected=image(m);int returnedRows=rows;
   registry.restore(full);for(int i=0;i<returnedRows;i++)step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   registry.restoreCourse(course);for(int i=0;i<returnedRows;i++)step(loop,8);CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   assertSame(f,m.originalR11EntryFrame());assertSame(services.loads(),m.programServicesState().loads());assertEquals(services.completedCalls(),m.programServicesState().completedCalls());
  }
 }
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var w=m.originalR11WorldImage().orElseThrow();return List.of(p.status(),p.pool().slots(),p.savedFlags(),f.captureActor(),f.captureShared(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),w.cpu(),w.sounds(),m.selectedProfileState(),m.programServicesState(),m.originalR11FirstLoad().selection().inputs());}
 @Test void originalFactoriesRejectConstructedPlacementBeforeCallingAnything()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var p=new CdObjectPlacementRuntime(a.placements());
  assertThrows(IllegalArgumentException.class,()->CdPataBata.fromOriginalPresentAssets(p,a,a.openingObjects(),null,null));
  assertThrows(IllegalArgumentException.class,()->CdAnton.fromOriginalPresentAssets(p,a,a.openingObjects(),null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdTamabboh.fromOriginalPresentAssets(p,a,a.openingObjects(),null,null,null,null,null));
 }
 @Test void pataActualSourceFormsDrawReloadRetirementAndExpiredIssuers()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();
  for(int subtype:new int[]{0,255}){
   var p=CdObjectPlacementRuntime.fromAssets(a);var pool=p.pool();
   pool.write(pool.state(32).moveByte(CdObjectState.ByteField.ID,0x3E).moveByte(CdObjectState.ByteField.SUBTYPE,subtype).moveWord(CdObjectState.WordField.X,0x800).moveWord(CdObjectState.WordField.Y,0x300));
   var seen=new ArrayList<CdEnemyPrimitiveScope.Call>();var bodies=new CdPataBata[1];
   var observer=new CdEnemyPrimitiveScope.Observer(){public void before(CdEnemyPrimitiveScope.Call c){assertTrue(bodies[0].isCurrentOriginalPrimitive(c));seen.add(c);}public void after(CdEnemyPrimitiveScope.Call c){assertTrue(bodies[0].isCurrentOriginalPrimitive(c));}};
   bodies[0]=CdPataBata.fromOriginalPresentAssets(p,a,a.openingObjects(),(q,id)->{assertSame(pool,q);assertTrue(bodies[0].isCurrentDrawCall(q,id));q.write(q.state(id.wholeIndex()).moveByte(CdObjectState.ByteField.SPRITE_FLAGS,q.state(id.wholeIndex()).spriteFlags()&127));},observer);
   var r=bodies[0].run(32,new CdPataBata.Globals(0,1,0,0x800));
   assertEquals(2,r.state().routine());assertEquals(0x2A,r.state().unsignedByte(CdObjectState.ByteField.COLLISION_TYPE));
   assertEquals(subtype==0?-0x8000:-0x4000,r.state().longBits(CdObjectState.LongField.PATA_BATA_X_DELTA));
   assertEquals(subtype==0?0x20B52E:0x20B550,r.state().longBits(CdObjectState.LongField.MAPPINGS));
   assertTrue(seen.stream().anyMatch(c->c.kind()==CdEnemyPrimitiveScope.Kind.ANIMATION));assertTrue(seen.stream().anyMatch(c->c.kind()==CdEnemyPrimitiveScope.Kind.DRAW));
   for(var c:seen)assertFalse(bodies[0].isCurrentOriginalPrimitive(c));
   // Own saved ID1 + raw period4 aliases ID2/period1. Draw reload clears bit7 before anchor retirement.
   pool.write(pool.state(32).moveByte(CdObjectState.ByteField.SAVED_FLAGS_ID,1));p.bsetSavedFlag(2,1,CdObjectPlacementRuntime.SavedBit.LOADED);
   bodies[0].run(32,new CdPataBata.Globals(0,4,0,0x2000));assertEquals(0,pool.state(32).id());assertEquals(0,p.savedFlag(2,1)&128);
  }
 }
 @Test void pataDrawFaultKeepsOriginalAndCompletedMotionWithoutRetry()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var p=CdObjectPlacementRuntime.fromAssets(a);p.pool().write(p.pool().state(32).moveByte(CdObjectState.ByteField.ID,0x3E).moveWord(CdObjectState.WordField.X,0x800).moveWord(CdObjectState.WordField.Y,0x300));
  var first=new IllegalStateException("draw");var after=new AssertionError("after");int[] draws={0};var bodies=new CdPataBata[1];
  bodies[0]=CdPataBata.fromOriginalPresentAssets(p,a,a.openingObjects(),(pool,id)->{draws[0]++;throw first;},new CdEnemyPrimitiveScope.Observer(){public void before(CdEnemyPrimitiveScope.Call c){assertTrue(bodies[0].isCurrentOriginalPrimitive(c));}public void after(CdEnemyPrimitiveScope.Call c){if(c.kind()==CdEnemyPrimitiveScope.Kind.DRAW)throw after;}});
  assertSame(first,assertThrows(IllegalStateException.class,()->bodies[0].run(32,new CdPataBata.Globals(0,1,0,0x800))));assertEquals(1,draws[0]);assertArrayEquals(new Throwable[]{after},first.getSuppressed());
  assertEquals(2,p.pool().state(32).routine());assertEquals(0x07FF8000,p.pool().state(32).longBits(CdObjectState.LongField.X_POSITION));
 }

}
