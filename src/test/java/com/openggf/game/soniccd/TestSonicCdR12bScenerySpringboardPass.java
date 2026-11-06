package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
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

/** Actual cold GameLoop/FULL/World/Host/Frame. Every pool/bank seed is explicit public-owner
 * component ingress, never a natural-route or pixel proof. Read-only reflection obtains actual
 * cached original ports only for detached/idle rejection; no private invocation state is forged.
 * Source-only specifications: all Java UNRUN. N1ecc5f3 Scenery/Main7-55; Springboard/Main13-373.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@Isolated
class TestSonicCdR12bScenerySpringboardPass {
    @org.junit.jupiter.api.BeforeAll static void configuredOriginalMedia(){String root=System.getProperty("soniccd.media.root");org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Explicit Sonic CD media root");}
    final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}@AfterEach void cleanup(){fixture.cleanup();}
    record A(Harness h,SonicCdAct2PastLoadingVisit core,SonicCdAct2PastWorldHost host,CdPlayerFrameRuntime frame,int call){}

    @Test void actualSceneryInitPreservesPriorityAndEveryUnwrittenSourceByte()throws Exception{
        var a=loaded();seed(a,CdScenery.OBJECT_ID,0);var p=a.host.placement();
        p.pool().write(p.pool().state(32).moveByte(SUBTYPE,1).moveByte(PRIORITY,5).moveByte(FLAGS,0xA5)
                .moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678).moveWord(X_VELOCITY,0x9ABC)
                .moveWord(Y_VELOCITY,0xDEF0).moveByte(ANIMATION,7).moveByte(RAW_CONTROL_2C,0x72)
                .moveWord(SWING_STEP,0x5317));
        var before=p.pool().state(32);var world=a.h.mode().getWorldSession();long generation=a.h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        step(a.h,right());assertFalse(a.frame.closed());assertSame(world,a.h.mode().getWorldSession());assertEquals(generation,a.h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        var actual=p.pool().state(32);var expected=before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,before.spriteFlags()|4)
                .moveLong(MAPPINGS,0x2086D8).moveByte(MAP_FRAME,1).moveByte(WIDTH,16).moveByte(Y_RADIUS,24)
                .moveWord(TILE,0x446E);
        assertDrawBitOnly(expected,actual);assertEquals(5,actual.unsignedByte(PRIORITY));assertTrue(a.host.sounds().isEmpty());
    }
    @Test void actualSceneryMainIsRtsBeforeOneRealDrawAndFullCourseReplay()throws Exception{
        var a=loaded();seed(a,CdScenery.OBJECT_ID,2);var p=a.host.placement();
        p.pool().write(p.pool().state(32).moveLong(MAPPINGS,0x2086D8).moveWord(TILE,0x446E)
                .moveByte(MAP_FRAME,2).moveByte(PRIORITY,7).moveByte(WIDTH,16).moveByte(Y_RADIUS,24).moveByte(SPRITE_FLAGS,0x84)
                .moveLong(X_POSITION,(p.pool().state(32).centreX()<<16)|0x7219).moveWord(Y_FRACTION,0x4613));
        var before=p.pool().state(32);var registry=a.h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
        step(a.h,right());assertFalse(a.frame.closed());assertDrawBitOnly(before,p.pool().state(32));var nativeAfter=captureNative(a.h);var afterFull=registry.capture();var afterCourse=registry.captureCourse();
        registry.restore(full);step(a.h,right());assertNativeCapture(nativeAfter,a.h);values(afterFull,registry.capture(),"Scenery full replay");
        registry.restoreCourse(course);step(a.h,right());assertNativeCapture(nativeAfter,a.h);values(afterFull,registry.capture(),"Scenery course→full");values(afterCourse,registry.captureCourse(),"Scenery course replay");
    }
    @Test void actualSceneryTailDeletesAll64AndClearsOnlyItsReachedLoadedBit()throws Exception{
        var a=loaded();seed(a,CdScenery.OBJECT_ID,0);var p=a.host.placement();
        p.pool().write(p.pool().state(32).moveWord(X,(a.host.scroll().foreground().cameraX()+0x1000)&65535).moveByte(SAVED_FLAGS_ID,7).moveWord(SWING_STEP,0xC1D2));
        // Deliberate public saved-bank component ingress; the real native tail owns its later BCLR.
        assertEquals(0,a.frame.environment().movement().gates().timeZone(),"Actual target is Past");
        p.bsetSavedFlag(7,0,CdObjectPlacementRuntime.SavedBit.LOADED);p.bsetSavedFlag(7,0,CdObjectPlacementRuntime.SavedBit.MEMBER_2);
        var expectedSaved=p.capture().savedFlags().copy();expectedSaved[2+3*7]&=0x7F;
        step(a.h,right());assertFalse(a.frame.closed());assertArrayEquals(new byte[64],p.pool().state(32).copyBytes());assertArrayEquals(expectedSaved,p.capture().savedFlags().copy());
        assertTrue(a.host.sounds().isEmpty());assertTrue(a.host.video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==32));
    }
    @Test void actualNormalSpringboardInitReturnsWithoutAnimateOrDrawAndKeepsFull64Nonwrites()throws Exception{
        var a=loaded();seed(a,CdSpringboard.OBJECT_ID,0);var p=a.host.placement();
        p.pool().write(p.pool().state(32).moveByte(MAP_FRAME,5).moveByte(ANIMATION_FRAME,0x17).moveByte(ANIMATION_TIME,0x39)
                .moveByte(PREVIOUS_ANIMATION,0x61).moveByte(FLAGS,0x82).moveByte(SPRITE_FLAGS,0x80)
                .moveWord(X_FRACTION,0x1526).moveWord(Y_FRACTION,0x3748).moveWord(X_VELOCITY,0x596A).moveWord(Y_VELOCITY,0x7B8C).moveWord(SWING_STEP,0x9DAE));
        var before=p.pool().state(32);var table=objects(a).view(CdR12bObjectAssets.View.OBJECT_TILE_IDS);int tile=table.unsignedWord((short)table.unsignedWord(14));
        step(a.h,right());assertFalse(a.frame.closed());var expected=before.moveLong(MAPPINGS,0x20BE7C).moveByte(SPRITE_FLAGS,before.spriteFlags()|4)
                .moveByte(PRIORITY,3).moveByte(WIDTH,16).moveByte(X_RADIUS,24).moveByte(Y_RADIUS,4).moveWord(TILE,tile).moveByte(ANIMATION,3).moveByte(ROUTINE,2);
        assertArrayEquals(expected.copyBytes(),p.pool().state(32).copyBytes());assertTrue(a.host.video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==32));assertTrue(a.host.sounds().isEmpty());
    }
    @Test void actualFlippedSpringboardInitClearsOnlyTheTwoNativeFlipBits()throws Exception{
        var a=loaded();seed(a,CdSpringboard.OBJECT_ID,0);var p=a.host.placement();
        p.pool().write(p.pool().state(32).moveByte(SUBTYPE,1).moveByte(SPRITE_FLAGS,0x81).moveByte(FLAGS,0xF7));
        step(a.h,right());assertFalse(a.frame.closed());var s=p.pool().state(32);assertEquals(4,s.routine());assertEquals(4,s.unsignedByte(ANIMATION));assertEquals(0x84,s.spriteFlags());assertEquals(0xF6,s.flags());assertEquals(0,s.unsignedByte(ANIMATION_FRAME));
        assertTrue(a.host.video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==32));
    }
    @Test void actualSpringboardInitStillRunsItsOuterOffscreenDespawn()throws Exception{
        var a=loaded();seed(a,CdSpringboard.OBJECT_ID,0);var p=a.host.placement();p.pool().write(p.pool().state(32).moveWord(X,(a.host.scroll().foreground().cameraX()+0x1000)&65535).moveWord(SWING_STEP,0x8A9B));
        step(a.h,right());assertFalse(a.frame.closed());assertArrayEquals(new byte[64],p.pool().state(32).copyBytes());assertTrue(a.host.sounds().isEmpty());
    }
    @Test void actualSpringboardMainAlignsTheWholePrimaryYLongAgainstARealNoBoardControl()throws Exception{
        var a=primaryReady();seedContact(a);var p=a.host.placement();var full=a.h.mode().getRewindRegistry().capture();int boardY=p.pool().state(32).longBits(Y_POSITION);
        // A second real World service from the same FULL cut provides the control primary result.
        p.pool().deleteObject(32);step(a.h,right());var control=a.frame.state();assertFalse(a.frame.closed());
        a.h.mode().getRewindRegistry().restore(full);step(a.h,right());assertFalse(a.frame.closed());var board=p.pool().state(32);assertEquals(10,board.routine());assertEquals(8,board.flags()&8);
        var m=control.motion();int aligned=boardY-(m.yRadius()<<16);var expectedMotion=new CdPlayerMotionState(m.centreX16_16(),aligned,m.xVelocity(),m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var expected=new CdPlayerBody.State(expectedMotion,control.animation(),control.header(),control.timers(),control.history(),control.rotationDistance(),control.objectLinks(),control.rotationAngle());
        values(expected,a.frame.state(),"actual Springboard one LONG store");assertEquals(0x2468,a.frame.state().motion().centreY16_16()&65535);assertTrue(a.host.sounds().isEmpty());
    }
    @Test void actualSpringboardPositiveFlingPublishesOnceAndReplaysFullAndCourse()throws Exception{
        var a=primaryReady();seedContact(a);step(a.h,right());assertFalse(a.frame.closed());assertEquals(10,a.host.placement().pool().state(32).routine());
        var registry=a.h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var frame=a.frame;
        // The control is another genuine World service, never a privately invoked Body/Solid result.
        a.host.placement().pool().deleteObject(32);step(a.h,right());var control=frame.state();registry.restore(full);
        step(a.h,right());assertSame(frame,a.h.module().requirePlayerFrame());assertFalse(frame.closed());assertTrue(frame.state().motion().yVelocity()<0);
        var m=control.motion();int boosted=(m.yVelocity()+0x100)&65535;if(((boosted-0xA00)&0x8000)==0)boosted=0xA00;
        int flags=(m.flags()|2)&0xDF,jump=(m.flags()&2)!=0?0:m.playerJump(),y=m.centreY16_16(),yr=19,xr=9,anim=m.animation();
        if((flags&4)==0){yr=14;xr=7;y=((((y>>>16)+5)&65535)<<16)|(y&65535);flags|=4;anim=2;}else flags|=0x10;
        var expectedMotion=new CdPlayerMotionState(m.centreX16_16(),y,m.xVelocity(),(short)-boosted,m.groundVelocity(),m.routine(),m.angle(),flags,m.spriteFlags(),xr,yr,anim,m.previousAnimation(),jump,m.charge(),m.moveLock(),0,m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        var expected=new CdPlayerBody.State(expectedMotion,control.animation(),control.header(),control.timers(),control.history(),control.rotationDistance(),control.objectLinks(),control.rotationAngle());
        values(expected,frame.state(),"actual Springboard native fling stores/nonwrites");assertEquals(0x3F,a.host.placement().pool().state(32).unsignedByte(SPRINGBOARD_FLING));assertTrue(a.host.sounds().isEmpty());
        var nativeAfter=captureNative(a.h);var afterFull=registry.capture();var afterCourse=registry.captureCourse();registry.restore(full);step(a.h,right());assertNativeCapture(nativeAfter,a.h);values(afterFull,registry.capture(),"Springboard full replay");registry.restoreCourse(course);step(a.h,right());assertNativeCapture(nativeAfter,a.h);values(afterFull,registry.capture(),"Springboard course→full");values(afterCourse,registry.captureCourse(),"Springboard course replay");
    }
    @Test void reachedSpringboardTileBoundaryRetainsItsSixRealInitStoresAndClosesWorld()throws Exception{
        var a=loaded();seed(a,CdSpringboard.OBJECT_ID,0);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(SUBTYPE2,255).moveWord(TILE,0x7159).moveByte(ANIMATION,0x26));var before=p.pool().state(32);
        var error=assertThrows(CdSpringboard.UnsupportedSpringboard.class,()->step(a.h,right()));assertEquals(CdSpringboard.UnsupportedBoundary.TILE_ADDRESS,error.boundary());assertTrue(a.frame.closed());
        var expected=before.moveLong(MAPPINGS,0x20BE7C).moveByte(SPRITE_FLAGS,before.spriteFlags()|4).moveByte(PRIORITY,3).moveByte(WIDTH,16).moveByte(X_RADIUS,24).moveByte(Y_RADIUS,4);assertArrayEquals(expected.copyBytes(),p.pool().state(32).copyBytes());
        assertEquals(0,error.getSuppressed().length);
    }
    @Test void publicReceiptsCannotEnterActualCachedPortsOrRunAnIdleWorldPass()throws Exception{
        var a=loaded();seed(a,CdScenery.OBJECT_ID,0);var ordinary=actualField(a.core,"ordinary");var pass=(SonicCdAct2PastObjectPass)actualField(ordinary,"objects");
        var scenery=(CdScenery)actualField(pass,"scenery");var board=(CdSpringboard)actualField(pass,"springboard");var p=a.host.placement();var before=a.h.mode().getRewindRegistry().capture();
        var draw=new CdScenery.DrawCall(p.capture(),p.pool().state(32));assertFalse(scenery.isCurrentCall(draw));
        var sourceDraw=(CdScenery.SourceDraw)actualField(scenery,"sourceDraw");assertThrows(IllegalStateException.class,()->sourceDraw.draw(draw));
        var m=a.frame.state().motion();var primary=new CdSpringboard.PrimaryCall(1,p.capture(),p.pool().state(32),a.frame.state(),a.frame.environment(),a.frame.state(),CdSpringboard.PrimaryField.FLAGS,m.flags(),m.flags());assertFalse(board.isCurrentCall(primary));
        var commit=(CdSpringboard.PrimaryCommit)actualField(board,"primaryCommit");assertThrows(IllegalStateException.class,()->commit.commit(primary));assertThrows(IllegalStateException.class,()->pass.runWalk(1));
        values(before,a.h.mode().getRewindRegistry().capture(),"detached/idle origins leave all owners unchanged");assertFalse(a.frame.closed());step(a.h,right());assertFalse(a.frame.closed());assertEquals(2,p.pool().state(32).routine());
    }
    @Test void deferredMappingFailurePreservesActualSceneryPrefixAndClosesTheSameFrame()throws Exception{
        var a=loaded();seed(a,CdScenery.OBJECT_ID,0);var p=a.host.placement();p.pool().write(p.pool().state(32).moveByte(SUBTYPE,255).moveByte(PRIORITY,5).moveWord(SWING_STEP,0x7A6B));var before=p.pool().state(32);
        assertThrows(RuntimeException.class,()->step(a.h,right()));assertTrue(a.frame.closed());var s=p.pool().state(32);assertEquals(2,s.routine());assertEquals(0x2086D8,s.longBits(MAPPINGS));assertEquals(255,s.unsignedByte(MAP_FRAME));assertEquals(0x446E,s.word(TILE));assertEquals(before.word(SWING_STEP),s.word(SWING_STEP));assertEquals(5,s.unsignedByte(PRIORITY));
        // This is the deferred CPU mapping consumer, not an injected or fabricated source callback failure.
    }
    static CdR12bObjectAssets objects(A a){return ((SonicCdAct2PastLevel)a.h.mode().getLevelManager().getCurrentLevel()).assets().objects();}
    /** Deliberate public Host-pool component ingress; no Frame/Core/clock/queue/current/cursor hydration. */
    static void seed(A a,int id,int routine){var p=a.host.placement();p.pool().clearDynamicSlots();int x=(a.host.scroll().foreground().cameraX()+160)&65535,y=(a.host.scroll().foreground().cameraYWord()+96)&65535;p.pool().write(p.pool().state(32).moveByte(ID,id).moveByte(ROUTINE,routine).moveWord(X,x).moveWord(Y,y));}
    static A primaryReady()throws Exception{var a=loaded();a.host.placement().pool().clearDynamicSlots();step(a.h,right());assertFalse(a.frame.closed());assertEquals(2,a.frame.state().motion().routine());return a;}
    /** Public pool seed near the already initialized real primary; no primary write or mid-call capture. */
    static void seedContact(A a){seed(a,CdSpringboard.OBJECT_ID,2);var m=a.frame.state().motion();var p=a.host.placement();p.pool().write(p.pool().state(32).moveWord(X,((m.centreX16_16()>>>16)-12)&65535).moveLong(Y_POSITION,((((m.centreY16_16()>>>16)+m.yRadius())&65535)<<16)|0x2468).moveLong(MAPPINGS,0x20BE7C).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3).moveByte(WIDTH,16).moveByte(X_RADIUS,24).moveByte(Y_RADIUS,4).moveByte(ANIMATION,3));}
    static void assertDrawBitOnly(CdObjectState expected,CdObjectState actual){assertArrayEquals(expected.moveByte(SPRITE_FLAGS,(expected.spriteFlags()&127)|(actual.spriteFlags()&128)).copyBytes(),actual.copyBytes());}
    /** Actual cold controller only until target loading's first BEGIN_LEVEL; no component body or test invocation. */
    static A loaded()throws Exception{var p=ready();var h=p.harness();for(int call=p.returned()+1;call<=14142;call++){step(h,controller(call));var m=h.module();if(m.act2PastLoadingSelected()&&m.act2PastLoadingVisit().state().phase()==SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL){var f=m.requirePlayerFrame();assertEquals(0,f.environment().globals().levelFrames());assertEquals(0,f.state().motion().routine());assertFalse(f.closed());var host=m.act2PastWorldHost();assertTrue(host.geometryReady());assertEquals(0,m.act2PastLoadingVisit().state().section().queue().headSource());step(h,LogicalInputSnapshot.neutral());step(h,LogicalInputSnapshot.neutral());assertFalse(f.closed());assertEquals(2,f.state().motion().routine());assertEquals(host.scroll().foreground().cameraX()&0xFF80,host.placement().status().previousChunkWord(),"Fixed completed Init/Main fixture must keep next spawner in its retained chunk");return new A(h,m.act2PastLoadingVisit(),host,f,call+2);}}throw new AssertionError("Actual target loading did not finish within inherited finite bound");}
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
