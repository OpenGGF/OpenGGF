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

/** Actual cold GameLoop/World/FULL/Host/Frame/registry; no mocked module or forged visit coupling.
 * Cases explicitly label any owned public-pool component ingress, distinct from the natural controller case.
 * N1ecc5f3 Projector/Main36-281; Animal/Main17-216. All Java UNRUN by author.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@Isolated
class TestSonicCdR12bProjectorPass {
    @org.junit.jupiter.api.BeforeAll static void configuredOriginalMedia(){String root=System.getProperty("soniccd.media.root");org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Explicit Sonic CD media root");}
    final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}@AfterEach void cleanup(){fixture.cleanup();}
    record A(Harness h,SonicCdAct2PastLoadingVisit core,SonicCdAct2PastWorldHost host,CdPlayerFrameRuntime frame,int call){}
    @Test void realNaturalNextServiceInitializesProjectorAndItsFourLaterPhysicalChildren()throws Exception{
        var p=ready();var h=p.harness();for(int call=p.returned()+1;call<=14515;call++)step(h,controller(call));var m=h.module();var host=m.act2PastWorldHost();var frame=m.requirePlayerFrame();var core=m.act2PastLoadingVisit();var placement=host.placement();var world=h.mode().getWorldSession();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        step(h,controller(14516));assertSame(world,h.mode().getWorldSession());assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());assertSame(frame,m.requirePlayerFrame());assertFalse(frame.closed());var parent=placement.pool().state(32);assertEquals(0x2E,parent.id());assertEquals(2,parent.routine());assertEquals(0x20E9F0,parent.longBits(MAPPINGS));assertEquals(0x3AF,parent.word(TILE));
        var children=placement.pool().capture().slots().stream().filter(s->(s.id()==0x2E||s.id()==0x24)&&s.word(EXPLOSION_POINTS_FRAME)==0xD800&&s.identity().wholeIndex()!=32).toList();assertEquals(4,children.size());assertEquals(List.of(0xFF,1,0x80,0x81),children.stream().map(CdObjectState::subtype).toList());assertEquals(List.of(2,2,4,4),children.stream().map(CdObjectState::routine).toList());assertTrue(children.stream().allMatch(s->s.identity().wholeIndex()>32));assertEquals(0,host.globals().projectorByte());assertFalse(core.state().section().stopped());
    }
    @Test void actualPublicPoolIngressRetainsNativeFindOrderAndFullCourseReplayAcrossRealQueueSubmission()throws Exception{
        var a=loaded();seed(a,1,false);var registry=a.h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var beforeNative=captureNative(a.h);var beforeFull=registry.capture();var beforeCourse=registry.captureCourse();
        step(a.h,right());var afterNative=captureNative(a.h);var afterFull=registry.capture();var afterCourse=registry.captureCourse();assertFalse(a.frame.closed());assertEquals(2,a.host.placement().pool().state(32).routine());assertTrue(a.host.placement().pool().capture().slots().stream().anyMatch(s->s.id()==0x24&&s.subtype()==0x80&&s.routine()==4));
        registry.restore(full);assertNativeCapture(beforeNative,a.h);values(beforeFull,registry.capture(),"full before");values(beforeCourse,registry.captureCourse(),"course before");step(a.h,right());assertNativeCapture(afterNative,a.h);values(afterFull,registry.capture(),"full replay");values(afterCourse,registry.captureCourse(),"course replay");
        registry.restoreCourse(course);assertNativeCapture(beforeNative,a.h);step(a.h,right());assertNativeCapture(afterNative,a.h);values(afterFull,registry.capture(),"course→full");values(afterCourse,registry.captureCourse(),"course→course");
    }
    @Test void actualParentFinalDelayAdoptsSoleProjectorByteBeforeDeleteAndRestoresOnlyRealAnimalCue()throws Exception{
        var a=loaded();seed(a,1,true);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(ROUTINE,8).moveWord(ROBOT_GENERATOR_TIMER,1));var saved=p.capture().savedFlags();var g=a.host.globals();int good=a.core.state().program().goodFutureByte();step(a.h,right());assertFalse(a.frame.closed());assertEquals(0xFF,a.host.globals().projectorByte());assertEquals(g.ring().advanceGlobalRingAnimation(),a.host.globals().ring());assertEquals(g.score(),a.host.globals().score());assertEquals(good,a.core.state().program().goodFutureByte());assertArrayEquals(new byte[64],p.pool().state(32).copyBytes());var afterSaved=p.capture().savedFlags();for(int i=0;i<saved.length();i++)assertTrue(afterSaved.unsignedByte(i)==saved.unsignedByte(i)||i>=2&&afterSaved.unsignedByte(i)==(saved.unsignedByte(i)|128),"Only genuine Spawn loaded-bit sets may differ, byte"+i);assertEquals(0x9EE0,a.core.state().section().queue().headDestination());assertTrue(a.core.state().section().remainingTilesWord()>0);assertEquals(20,a.core.state().section().remainingTilesWord());
    }
    @Test void fullRealDescriptorQueueStopsAtSeventeenthOriginalLoadBeforeItsInitAndRetainsAllNativePrefixes()throws Exception{
        var a=loaded();seed(a,9,true);var placement=a.host.placement();var clock=a.frame.vIntClock();var before=a.core.state().section();assertEquals(0,before.queue().headSource());var failure=assertThrows(CdR12bSectionArt.SectionArtException.class,()->step(a.h,right()));assertEquals(CdR12bSectionArt.Reason.DESCRIPTOR_BUFFER,failure.reason());assertTrue(a.frame.closed());var live=a.core.state().section();assertFalse(live.stopped());assertTrue(failure.state().stopped());assertArrayEquals(live.queue().bytes().copy(),failure.state().queue().bytes().copy());assertSame(live.residency(),failure.state().residency());assertSame(live.codeTable(),failure.state().codeTable());assertNotSame(clock,a.frame.vIntClock());assertEquals(0,placement.pool().state(40).routine());assertEquals(0x2E,placement.pool().state(40).id());for(int row=0;row<16;row++)assertEquals(0x9EE0,live.queue().bytes().unsignedWord(row*6+4));assertInstanceOf(CdR11aPlcBuffer.BufferBoundary.class,failure.getCause());assertEquals(CdR11aPlcBuffer.Access.SOURCE_SCAN,((CdR11aPlcBuffer.BufferBoundary)failure.getCause()).access());
    }
    @Test void actualExplosionPrefixRetainsRealFmAndRunsItsLaterPhysicalChild()throws Exception{
        var a=loaded();seed(a,1,false);var placement=a.host.placement();placement.pool().write(placement.pool().state(32).moveByte(ROUTINE,4).moveLong(MAPPINGS,0x20E9F0).moveLong(ROBOT_GENERATOR_SCHEDULE,0x20EA34).moveByte(WIDTH,12).moveByte(Y_RADIUS,12).moveByte(PRIORITY,4).moveWord(TILE,0x3AF));
        step(a.h,right());assertFalse(a.frame.closed());var children=placement.pool().capture().slots().stream().filter(v->v.id()==0x18).toList();assertEquals(1,children.size());var child=children.getFirst();assertTrue(child.identity().wholeIndex()>32);assertEquals(2,child.routine());assertEquals(0x21D714,child.longBits(MAPPINGS));assertEquals(1,child.unsignedByte(ROUTINE_2));assertEquals(6,placement.pool().state(32).routine());assertEquals(0x20EA37,placement.pool().state(32).longBits(ROBOT_GENERATOR_SCHEDULE));
        var image=actualField(a.host,"image");var sounds=assertInstanceOf(List.class,actualField(image,"sounds"));assertEquals(1,sounds.stream().filter(s->s instanceof SonicCdGameModule.PastProjectorFm fm&&fm.nativeId()==0x9E).count());assertTrue(sounds.stream().noneMatch(s->s instanceof CdProjector.PlcCallRequest));assertThrows(IllegalArgumentException.class,()->new SonicCdGameModule.PastProjectorFm(0xD9));
    }
    @Test void actualFullObjectPoolInitFailureLeavesNativeOuterDrawAndPlcRestorationInTheSameWorld()throws Exception{
        var a=loaded();seed(a,1,true);var pool=a.host.placement().pool();var saved=a.host.placement().capture().savedFlags();step(a.h,right());assertFalse(a.frame.closed());assertArrayEquals(new byte[64],pool.state(32).copyBytes());for(int whole=33;whole<128;whole++)assertEquals(0,pool.state(whole).id());assertEquals(0x9EE0,a.core.state().section().queue().bytes().unsignedWord(10));assertTrue(a.core.state().section().queue().bytes().unsignedLong(6)!=0);assertArrayEquals(saved.copy(),a.host.placement().capture().savedFlags().copy());
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
