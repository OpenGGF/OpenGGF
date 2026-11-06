package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual World/Host/GameLoop component ingress. Public pool writes below are deliberately constructed,
 * never natural-route or full-act proof. No pending Frame capture, component dispatch, or effect replay.
 * All Java UNRUN by author. Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@Isolated
class TestSonicCdR12bEffectsMosquiPass {
    @org.junit.jupiter.api.BeforeAll static void configuredOriginalMedia(){String root=System.getProperty("soniccd.media.root");org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Explicit Sonic CD media root");}
    final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}@AfterEach void cleanup(){fixture.cleanup();}
    record A(Harness h,SonicCdAct2PastLoadingVisit core,SonicCdAct2PastWorldHost host,CdPlayerFrameRuntime frame,int call){}
    @Test void projectorExplosionRunsAtItsRealLaterWholeWithoutRedispatchingParent()throws Exception{
        var a=loaded();seed(a,1,false);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(ROUTINE,4).moveLong(MAPPINGS,0x20E9F0).moveLong(ROBOT_GENERATOR_SCHEDULE,0x20EA34).moveByte(WIDTH,12).moveByte(Y_RADIUS,12).moveByte(PRIORITY,4).moveWord(TILE,0x3AF));
        step(a.h,right());assertFalse(a.frame.closed());assertEquals(6,p.pool().state(32).routine());var children=p.pool().capture().slots().stream().filter(s->s.id()==0x18).toList();assertEquals(1,children.size());var child=children.getFirst();assertTrue(child.identity().wholeIndex()>32);assertEquals(2,child.routine());assertEquals(0x21D714,child.longBits(MAPPINGS));assertEquals(1,child.unsignedByte(ROUTINE_2));assertEquals(1,a.host.sounds().stream().filter(s->s instanceof SonicCdGameModule.PastProjectorFm).count());
    }
    @Test void explosionPointsLaterPhysicalInitAndFullCourseReplayHaveOneRealOwner()throws Exception{
        var a=loaded();seed(a,1,false);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(ID,0x18).moveWord(EXPLOSION_POINTS_FRAME,2));var registry=a.h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=registry.capture();step(a.h,right());assertFalse(a.frame.closed());var children=p.pool().capture().slots().stream().filter(v->v.id()==0x1C).toList();assertEquals(1,children.size());var child=children.getFirst();assertTrue(child.identity().wholeIndex()>32);assertEquals(0x1C,child.id());assertEquals(2,child.routine());assertEquals(1,child.unsignedByte(MAP_FRAME));assertEquals(0x20AA1A,child.longBits(MAPPINGS));var nativeAfter=captureNative(a.h);var afterFull=registry.capture();var afterCourse=registry.captureCourse();registry.restore(full);values(before,registry.capture(),"full restore");step(a.h,right());assertNativeCapture(nativeAfter,a.h);values(afterFull,registry.capture(),"full replay");values(afterCourse,registry.captureCourse(),"full course replay");registry.restoreCourse(course);step(a.h,right());assertNativeCapture(nativeAfter,a.h);values(afterFull,registry.capture(),"course replay");values(afterCourse,registry.captureCourse(),"course course replay");
    }
    @Test void explosionFirstFreeEarlierWholeWaitsForNextPhysicalWalk()throws Exception{
        var a=loaded();seed(a,1,false);var p=a.host.placement();var x=p.pool().state(32).word(X);var y=p.pool().state(32).word(Y);p.pool().clearDynamicSlots();p.pool().write(p.pool().state(33).moveByte(ID,0x18).moveWord(X,x).moveWord(Y,y).moveWord(EXPLOSION_POINTS_FRAME,0));step(a.h,right());assertFalse(a.frame.closed());assertEquals(0x1C,p.pool().state(32).id());assertEquals(0,p.pool().state(32).routine());assertEquals(0,p.pool().state(32).longBits(MAPPINGS));assertEquals(2,p.pool().state(33).routine());
    }
    @Test void flowerSubtypeZeroStopsAfterExactRealInitPrefixWithoutGrantingSavedStorage()throws Exception{
        var a=loaded();seed(a,1,false);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(ID,0x1F));var before=p.capture();var ring=a.host.globals().ring();var error=assertThrows(CdFlower.UnsupportedFlower.class,()->step(a.h,right()));assertEquals(CdFlower.UnsupportedBoundary.SAVED_FLAGS_INIT,error.boundary());assertTrue(a.frame.closed());var s=p.pool().state(32);assertEquals(0,s.routine());assertEquals(0x236AB0,s.longBits(MAPPINGS));assertEquals(0xA6D7,s.word(TILE));assertEquals(4,s.spriteFlags()&4);assertEquals(1,s.unsignedByte(PRIORITY));var after=(SonicCdAct2PastWorldHost.Globals)actualField(actualField(a.host,"image"),"globals");assertEquals(ring.rings(),after.ring().rings());assertEquals(ring.lives(),after.ring().lives());assertEquals(ring.livesFlags(),after.ring().livesFlags());assertEquals(before.pool().slots().get(1),p.pool().state(33));
    }
    @Test void respawnFreeFlowerRunsActualNestedFloorAngleAndKeepsOneCompleteFrame()throws Exception{
        var a=loaded();seed(a,1,false);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(ID,0x1F).moveByte(SUBTYPE,1));var clock=a.frame.vIntClock();step(a.h,right());assertFalse(a.frame.closed());assertNotSame(clock,a.frame.vIntClock());var s=p.pool().state(32);assertEquals(0x1F,s.id());assertEquals(0x236AB0,s.longBits(MAPPINGS));assertTrue(s.routine()==2||s.routine()==4);assertTrue(a.host.selected() instanceof SonicCdAct2PastWorldHost.Published);
    }
    @Test void actualMonitorRingRewardRetainsNativeUnsupportedBoundaryAndRewardPrefix()throws Exception{
        var a=loaded();seed(a,1,false);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(ID,0x1A).moveByte(ROUTINE,2).moveByte(ANIMATION,1).moveWord(Y_VELOCITY,0));var ring=a.host.globals().ring();var error=assertThrows(CdMonitorItem.UnsupportedMonitorItem.class,()->step(a.h,right()));assertEquals(CdMonitorItem.UnsupportedBoundary.REWARD_RINGS,error.boundary());assertTrue(a.frame.closed());assertEquals(4,p.pool().state(32).routine());assertEquals(29,p.pool().state(32).word(MONITOR_ITEM_DELETE));var g=(SonicCdAct2PastWorldHost.Globals)actualField(actualField(a.host,"image"),"globals");assertEquals(ring.rings(),g.ring().rings());assertEquals(ring.lives(),g.ring().lives());assertEquals(ring.livesFlags(),g.ring().livesFlags());
    }
    @Test void actualShieldRewardCallbackStopsAtUnownedReservedOwnerAfterRewardPrefix()throws Exception{
        var a=loaded();seed(a,1,false);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(ID,0x1A).moveByte(ROUTINE,2).moveByte(ANIMATION,2).moveWord(Y_VELOCITY,0));var error=assertThrows(SonicCdAct2PastObjectPass.Unowned.class,()->step(a.h,right()));assertEquals(SonicCdAct2PastObjectPass.Boundary.MONITOR_REWARD_OWNER,error.boundary());assertEquals(2,error.value());assertTrue(a.frame.closed());assertEquals(4,p.pool().state(32).routine());assertEquals(29,p.pool().state(32).word(MONITOR_ITEM_DELETE));
    }
    @Test void lostRingCollectUsesSharedRewardOnceAndRetainsSoleSpillOwner()throws Exception{
        var a=loaded();seed(a,1,false);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(ID,0x11).moveByte(ROUTINE,4).moveLong(MAPPINGS,0x209EB0).moveWord(TILE,0xA7AE));var before=a.host.globals().ring();step(a.h,right());assertFalse(a.frame.closed());assertEquals((before.rings()+1)&65535,a.host.globals().ring().rings());assertEquals(6,p.pool().state(32).routine());assertEquals(1,a.host.sounds().stream().filter(s->s instanceof SonicCdGameModule.PastRingFm).count());assertTrue(a.host.globals().animations().isPresent());
    }
    @Test void mosquiUsesActualClearedFlagConversionOrNativeInitWithoutInventedFm()throws Exception{
        var a=loaded();seed(a,1,false);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(ID,0x3D).moveByte(SPRITE_FLAGS,0x80));int good=a.core.state().program().goodFutureByte();int x=p.pool().state(32).word(X),y=p.pool().state(32).word(Y);step(a.h,right());assertFalse(a.frame.closed());var s=p.pool().state(32);if(good!=0&&a.frame.environment().movement().gates().timeZone()!=1){assertEquals(0x18,s.id());assertEquals(0,s.routine());assertEquals(x,s.word(X));assertEquals(y,s.word(Y));assertEquals(0,s.spriteFlags());assertEquals(0,s.longBits(MAPPINGS));assertTrue(a.host.sounds().stream().noneMatch(v->v instanceof SonicCdGameModule.PastMosquiFm));}else{assertEquals(0x3D,s.id());assertTrue(s.routine()==2||s.routine()==4);assertEquals(0x20B49C,s.longBits(MAPPINGS));}
    }
    /** Actual cold controller only until target loading's first BEGIN_LEVEL; no component body or test invocation. */
    static A loaded()throws Exception{var p=ready();var h=p.harness();for(int call=p.returned()+1;call<=14142;call++){step(h,controller(call));var m=h.module();if(m.act2PastLoadingSelected()&&m.act2PastLoadingVisit().state().phase()==SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL){var f=m.requirePlayerFrame();assertEquals(0,f.environment().globals().levelFrames());assertEquals(0,f.state().motion().routine());assertFalse(f.closed());var host=m.act2PastWorldHost();assertTrue(host.geometryReady());assertEquals(0,m.act2PastLoadingVisit().state().section().queue().headSource());step(h,LogicalInputSnapshot.neutral());step(h,LogicalInputSnapshot.neutral());assertFalse(f.closed());assertEquals(2,f.state().motion().routine());assertEquals(host.scroll().foreground().cameraX()&0xFF80,host.placement().status().previousChunkWord(),"Fixed completed Init/Main fixture must keep next spawner in its retained chunk");return new A(h,m.act2PastLoadingVisit(),host,f,call+2);}}throw new AssertionError("Actual target loading did not finish within inherited finite bound");}
    /** Explicit owned public-Host pool component ingress only. No Frame/Core/queue/current/clock/placement-cursor restore. */
    static void seed(A a,int count,boolean full){var p=a.host.placement();p.pool().clearDynamicSlots();int x=(a.host.scroll().foreground().cameraX()+160)&65535,y=(a.host.scroll().foreground().cameraYWord()+96)&65535;for(int i=0;i<count;i++){int whole=32+i;p.pool().write(p.pool().state(whole).moveByte(ID,0x2E).moveWord(X,x).moveWord(Y,y));}if(full)for(int whole=32+count;whole<128;whole++)p.pool().write(p.pool().state(whole).moveByte(ID,0x37));assertEquals(0x203D4AL,((SonicCdAct2PastLevel)a.h.mode().getLevelManager().getCurrentLevel()).assets().objects().view(CdR12bObjectAssets.View.OBJECT_INDEX).unsignedLong((0x37-1)*4));}
    static LogicalInputSnapshot right(){return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),PlayerInputState.neutral());}
    static void values(Object expected,Object actual,String label)throws Exception{var m=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("assertNaturalR12bSnapshotValue",Object.class,Object.class,String.class);m.setAccessible(true);try{m.invoke(null,expected,actual,label);}catch(InvocationTargetException e){if(e.getCause() instanceof Error error)throw error;if(e.getCause() instanceof Exception error)throw error;throw e;}}
    private static LogicalInputSnapshot controller(int call) {
        if (call < 1 || call > 14516) throw new IllegalArgumentException("Fixed ordinary controller recipe is1..14516; later input exploration has its own finite bound");
        if (call > 10419) {
            // Exact old prefix through13098; then Left96, Left+B4, Left96, Right256.
            // The established held-Right suffix continues unchanged through the WARP return;
            // controller bounds are fixture input selection, never gameplay behavior.
            int directions = call >= 13099 && call <= 13294 ? 4 : 8;
            int actions = call <= 13098 ? (call >= 11214 && (call - 11214) % 128 < 32 ? 2 : 0)
                    : (call >= 13195 && call <= 13198 ? 2 : 0);
            if(call>=14144&&call<=14151)actions=2; // Fixed native jump input, never a state/camera write.
            if(call>=14408&&call<=14423){directions=2;actions=2;} // Fixed Down+B16 followed by Right; no state-dependent input choice.
            return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions, 0, actions, 0, false, false),
                    PlayerInputState.neutral());
        }
        if (call <= 8276) return route(call);
        int directions = 8, actions = call <= 8308 ? 2 : 0;
        if (call >= 9001 && call <= 9203) directions = 4;
        else if (call >= 9206 && call <= 9217) directions = 0;
        else if (call >= 9218 && call <= 9263) { directions = 2; actions = call == 9219 ? 2 : 0; }
        else if (call >= 9336 && call <= 9367) actions = 2;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions, 0, actions, 0, false, false),
                PlayerInputState.neutral());
    }
}
