package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Strict SOURCE obligations, never observed route evidence. All Java/discovery/runtime UNRUN. */
@Isolated
class TestCdOriginalR11TunnelFamilyWorld extends TestCdOriginalR11LoadingWorld {
 @Test void actualTagServiceMustReturnAndReplayFullAndCourse()throws Exception{family(0x34);}
 @Test void actualHorizontalDoorServiceMustReturnAndReplayFullAndCourse()throws Exception{family(0x0D);}
 @Test void actualVerticalDoorServiceMustReturnAndReplayFullAndCourse()throws Exception{family(0x35);}
 @Test void actualDoorSplashServiceMustReturnAndReplayFullAndCourse()throws Exception{family(0x0B);}
 @Test void actualTunnelPathServiceMustReturnAndReplayFullAndCourse()throws Exception{family(0x07);}
 private void family(int id)throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  try(var boot=boot()){
   var loop=boot.bootSonicCdSelectedProfile(source,attempt);for(int i=0;i<256&&m.originalR11LoadingPrefix().main().levelStartedByte()==0;i++)step(loop,0);
   assertEquals(1,m.originalR11LoadingPrefix().main().levelStartedByte());var f=m.originalR11EntryFrame();var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var service=m.programServicesState();
   int rows=0;boolean serviced=false;
   // A bounded proposed ordinary Right course, not a claimed/optimized route. Unsupported/death is failure.
   while(rows<4096&&!serviced){step(loop,8);rows++;assertFalse(f.closed());assertTrue(f.state().motion().routine()<6,"Death cannot certify a new family");serviced=returned(m,id);}
   assertTrue(serviced,"Required actual returned service receipt for family $"+Integer.toHexString(id));assertTrue(rows>0);
   assertTrue(m.originalR11WorldImage().orElseThrow().cpu().isPresent());var expected=image(m);int count=rows;
   registry.restore(full);for(int i=0;i<count;i++)step(loop,8);assertTrue(returned(m,id));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   registry.restoreCourse(course);for(int i=0;i<count;i++)step(loop,8);assertTrue(returned(m,id));CdCapturedValueAssertions.assertCapturedValueEquals(expected,image(m));
   assertSame(f,m.originalR11EntryFrame());assertSame(service.loads(),m.programServicesState().loads());assertEquals(service.completedCalls(),m.programServicesState().completedCalls());
  }
 }
 private static boolean returned(SonicCdGameModule m,int id){return m.originalR11WorldImage().orElseThrow().tunnelServices().stream().anyMatch(c->c.id()==id&&c.operations()>0);}
 private static Object image(SonicCdGameModule m){var f=m.originalR11EntryFrame();var p=m.originalR11PlacementImage();var w=m.originalR11WorldImage().orElseThrow();return List.of(p.status(),p.pool().slots(),p.savedFlags(),f.captureActor(),f.captureShared(),m.originalR11LoadingPrefix(),m.originalR11LoadingImage(),w.cpu(),w.sounds(),w.tunnelServices(),m.selectedProfileState(),m.programServicesState());}
 @Test void originalFactoriesRejectConstructedPlacementBeforeCallbacks()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var p=new CdObjectPlacementRuntime(a.placements());
  assertThrows(IllegalArgumentException.class,()->CdSpinTunnelTag.fromOriginalPresentAssets(p,a,null,null));
  assertThrows(IllegalArgumentException.class,()->CdTunnelDoor.fromOriginalPresentAssets(p,a,null,null,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdTunnelDoorVertical.fromOriginalPresentAssets(p,a,null,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdTunnelDoorSplash.fromOriginalPresentAssets(p,a,null,null,null,null));
  assertThrows(IllegalArgumentException.class,()->CdTunnelPath.fromOriginalPresentAssets(p,a,null,null,null,null));
 }
 @Test void actualMetadataRetainsOriginalSplashAndCurrentSourcePair()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();var o=a.openingObjects();
  var ani=o.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.TUNNEL_DOOR_SPLASH_ANIMATIONS);var map=o.view(com.openggf.game.soniccd.level.CdR11aObjectAssets.Table.TUNNEL_DOOR_SPLASH_MAPPINGS);
  assertEquals(24,ani.bytes().length());assertEquals(100,map.bytes().length());assertEquals(0x23EFC6,map.range().nativeAddress());assertEquals(7,o.tunnelDoorSplash().mappingFrames().size());
 }
 @Test void completeFramePairRejectsPendingAndStaleWhilePublicValuesCarryNoInvocation()throws Exception{
  var a=TestCdOriginalBootPrefix.source().assets();try(var f=CdPlayerFrameRuntime.terrainOnlyDevelopment(a);var other=CdPlayerFrameRuntime.terrainOnlyDevelopment(a)){
   var s=f.state();var e=f.environment();var value=new CdSpinTunnelTag.PlayerResult(s.motion(),s.motion(),Set.of(CdSpinTunnelTag.PlayerField.FLAGS));
   var p=f.prepareObjectSpinTunnelResult(s,e,value);var actor=f.captureActor();var shared=f.captureShared();
   // The existing public value port deliberately permits equal foreign values; it is no original receipt.
   other.applyPreparedObjectSolidPublication(p);assertEquals(actor,f.captureActor());assertEquals(shared,f.captureShared());
   var velocity=f.prepareObjectYVelocity(s,e,s.motion().yVelocity()&65535,0x800);f.applyPreparedObjectSolidPublication(velocity);
   assertThrows(IllegalArgumentException.class,()->f.applyPreparedObjectSolidPublication(p));var changed=f.state();
   f.prepareInput(8);assertThrows(IllegalStateException.class,()->f.prepareObjectSpinTunnelResult(f.state(),f.environment(),new CdSpinTunnelTag.PlayerResult(f.state().motion(),f.state().motion(),Set.of())));assertEquals(changed,f.state());
  }
 }
}
