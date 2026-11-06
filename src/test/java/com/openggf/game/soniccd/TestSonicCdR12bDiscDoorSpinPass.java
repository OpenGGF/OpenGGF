package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Cold production World/Host/Frame, followed by explicitly constructed PUBLIC-POOL COMPONENT
 * INGRESS. These are bounded object-publication specifications, not natural route/media/pixel parity.
 * Genuine loading, Sonic Init and one genuine Sonic Main complete before any ingress. The next
 * SpawnObjects must see its retained equal chunk. No camera, placement cursor, physics or primary
 * state is written. Read-only reflection obtains actual ports for idle rejection, the retired
 * fault ledger, and the existing
 * checkpoint-aware FULL/course comparator. Java UNRUN.
 * N1ecc5f3: PP Spinning Disc/Main15-223; PP Tunnel Door/Main7-94; Spin Tunnel/Main (Door)7-92.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
@Isolated
class TestSonicCdR12bDiscDoorSpinPass {
    final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){String root=System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root!=null,"Explicit soniccd.media.root media opt-in is required");
        assertFalse(root.isBlank(),"A configured blank media root is an error");assertTrue(Files.isDirectory(Path.of(root)),"A configured bad media root must fail");fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    record A(Harness h,SonicCdAct2PastLoadingVisit core,SonicCdAct2PastWorldHost host,CdPlayerFrameRuntime frame){}

    @Test void discInitReadsActual72ByteTileDirectoryAndDrawsItsNativeFirstAnimationFrame()throws Exception{
        var a=loaded();seed(a,9,0);var pool=a.host.placement().pool();pool.write(pool.state(32).moveByte(FLAGS,0xA0).moveByte(PRIORITY,7).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678).moveWord(X_VELOCITY,0x2345).moveWord(Y_VELOCITY,0x6789).moveWord(SWING_STEP,0xBEEF));
        assertEquals(0,a.host.globals().ring().timeStopTimer());var before=pool.state(32);forward(a);var expected=before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x20937C).moveWord(TILE,0x0372).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveByte(ANIMATION_TIME,1).moveByte(ANIMATION_FRAME,1).moveByte(MAP_FRAME,0);
        drawBitOnly(expected,pool.state(32));assertTrue(sounds(a).isEmpty());assertTrue(a.host.video().completedCpu().orElseThrow().provenance().stream().anyMatch(v->v.slotRef().wholeIndex()==32));assertFalse(a.frame.closed());
    }
    @Test void visibleDiscCallsRealPlainSolidBeforeAnimationWithoutTouchingUnownedSourceBytes()throws Exception{
        var a=loaded();seed(a,9,2);var pool=a.host.placement().pool();pool.write(pool.state(32).moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveLong(MAPPINGS,0x20937C).moveWord(TILE,0x0372).moveByte(ANIMATION_TIME,0x7F).moveByte(SOLID_TYPE,0x79).moveWord(X_FRACTION,0xA123).moveWord(Y_FRACTION,0xB456));
        var before=pool.state(32);forward(a);drawBitOnly(before.moveByte(SOLID_TYPE,0).moveByte(ANIMATION_TIME,0x7E),pool.state(32));assertTrue(sounds(a).isEmpty());assertFalse(a.frame.closed());
    }
    @Test void actualDiscSideContactReturnsZeroWithoutAcquisitionAndReplays()throws Exception{
        var a=loaded();var registry=a.h.mode().getRewindRegistry();var beforeControl=registry.capture();sameChunk(a);step(a.h,right());assertFalse(a.frame.closed());var control=a.frame.state();registry.restore(beforeControl);
        // One explicit pool ingress. Geometry is based on a genuine next-Main control, never a primary write.
        seed(a,9,2);var pool=a.host.placement().pool();var m=control.motion();pool.write(pool.state(32).moveWord(X,((m.centreX16_16()>>>16)+20)&65535).moveWord(Y,m.centreY16_16()>>>16).moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x20937C).moveWord(TILE,0x0372).moveByte(ANIMATION_TIME,0x7F));
        // Native plain Solid horizontal paths return d0=0 (Object Solid.asm:314-324); Disc Main:54 exits before acquisition.
        var full=registry.capture();var course=registry.captureCourse();forward(a);var side=a.frame.state();
        assertEquals(control.motion().playerControl(),side.motion().playerControl());assertEquals(0,side.motion().playerControl()&1);
        assertEquals(control.motion().animation(),side.motion().animation());assertEquals(control.rotationAngle(),side.rotationAngle());assertEquals(control.rotationDistance(),side.rotationDistance());
        assertEquals((pool.state(32).centreX()-26)&65535,side.motion().centreX16_16()>>>16,"Width16+native9+1 side clamp");
        assertEquals(control.motion().centreX16_16()&65535,side.motion().centreX16_16()&65535);assertEquals(control.motion().centreY16_16(),side.motion().centreY16_16());
        assertEquals(0,side.motion().xVelocity());assertEquals(0,pool.state(32).unsignedByte(SOLID_TYPE));assertTrue(sounds(a).isEmpty());assertFalse(a.frame.closed());
        var after=registry.capture();var afterCourse=registry.captureCourse();var nativeAfter=captureNative(a.h);registry.restore(full);forward(a);assertNativeCapture(nativeAfter,a.h);values(after,registry.capture(),"Disc FULL replay");registry.restoreCourse(course);forward(a);assertNativeCapture(nativeAfter,a.h);values(after,registry.capture(),"Disc course->FULL");values(afterCourse,registry.captureCourse(),"Disc course replay");
    }
    @Test void actualDiscJumpPublishesA0AfterItsVelocityPrefixBeforeTheHitboxStores()throws Exception{
        var a=grounded();var registry=a.h.mode().getRewindRegistry();var controlCut=registry.capture();sameChunk(a);step(a.h,right());assertFalse(a.frame.closed());var control=a.frame.state();
        assertEquals(0,control.motion().flags()&2);assertTrue(control.motion().yVelocity()>=0,"Native top Solid rejects upward Y velocity, not a downward ground-slope component");registry.restore(controlCut);
        // Native top contact: object Y minus (disc radius8 + actual player radius) equals the genuine grounded centre.
        // This is declared public-pool geometry from Object Solid:344-353, not a fitted primary position or velocity.
        seed(a,9,2);var pool=a.host.placement().pool();var m=control.motion();int topCentre=((m.centreY16_16()>>>16)+8+(byte)m.yRadius())&65535;
        pool.write(pool.state(32).moveWord(X,((m.centreX16_16()>>>16)+20)&65535).moveWord(Y,topCentre).moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x20937C).moveWord(TILE,0x0372).moveByte(ANIMATION_TIME,0x7F));
        forward(a);assertFalse(a.frame.closed());assertEquals(1,a.frame.state().motion().playerControl()&1,"Only actual nonzero top Solid may acquire Disc control");
        assertEquals(8,a.frame.state().motion().flags()&10);assertEquals(32,a.frame.state().objectLinks().standObject());assertEquals(0x2D,a.frame.state().motion().animation());
        assertEquals(0x88,a.frame.state().rotationAngle());assertEquals(19,a.frame.state().rotationDistance(),"Native left distance20 then held-Right shrink");
        assertEquals(0,a.frame.state().motion().playerJump());assertEquals(0,a.frame.environment().controls().rawHeld()&0x70);assertEquals(0,a.frame.environment().controls().rawTap()&0x70);
        assertEquals(0x80,pool.state(32).spriteFlags()&0x80,"Actual preceding DrawObjects must retain native visibility");assertTrue(sounds(a).stream().noneMatch(SonicCdGameModule.PastDiscFm.class::isInstance),"Native Disc emits A0 only after the later CheckJump velocity prefix; other real Sonic/owner sounds remain independent");
        // A fresh ordinary Right+B edge now enters controlled Main, so the actual Disc CheckJump owns FM_A0 and its stores.
        sameChunk(a);step(a.h,jump());assertFalse(a.frame.closed());assertTrue((a.frame.environment().controls().rawTap()&0x70)!=0);assertEquals(0,a.frame.state().motion().playerControl());assertEquals(1,a.frame.state().motion().playerJump());assertEquals(2,a.frame.state().motion().animation());assertEquals(4,a.frame.state().motion().flags()&4);
        var fm=sounds(a).stream().filter(SonicCdGameModule.PastDiscFm.class::isInstance).map(SonicCdGameModule.PastDiscFm.class::cast).toList();assertEquals(List.of(new SonicCdGameModule.PastDiscFm(CdSpinningDisc.FmSound.FM_A0)),fm);
    }
    @Test void reachedDiscTileBoundaryRetainsOnlyTheActualPreTileInitPrefix()throws Exception{
        var a=loaded();seed(a,9,0);var pool=a.host.placement().pool();pool.write(pool.state(32).moveByte(SUBTYPE2,1).moveWord(TILE,0x1234).moveByte(WIDTH,0x27).moveByte(Y_RADIUS,0x31));var before=pool.state(32);
        var failure=assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class,()->forward(a));assertEquals(CdSpinningDisc.UnsupportedBoundary.TILE_ENTRY,failure.boundary());assertEquals(0,failure.getSuppressed().length);
        assertArrayEquals(before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x20937C).copyBytes(),pool.state(32).copyBytes());assertTrue(a.frame.closed());assertTrue(sounds(a).isEmpty());
    }
    @Test void reachedDiscAnimationDirectoryFailureKeepsNativeReadPrefixesAndOriginalFault()throws Exception{
        var a=loaded();seed(a,9,0);var pool=a.host.placement().pool();pool.write(pool.state(32).moveByte(ANIMATION,7).moveByte(PREVIOUS_ANIMATION,0).moveWord(SWING_STEP,0xAABB));var before=pool.state(32);
        var failure=assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class,()->forward(a));assertEquals(CdSpinningDisc.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY,failure.boundary());assertEquals(0,failure.getSuppressed().length);
        var expected=before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x20937C).moveWord(TILE,0x0372).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveByte(PREVIOUS_ANIMATION,7).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255);assertArrayEquals(expected.copyBytes(),pool.state(32).copyBytes());assertTrue(a.frame.closed());
    }
    @Test void horizontalDoorInitRunsBothRealTopHelpersWithActualReservedWhole1AndReplays()throws Exception{
        var a=loaded();seed(a,13,0);var pool=a.host.placement().pool();pool.write(pool.state(32).moveByte(SUBTYPE,2).moveByte(PRIORITY,6).moveWord(TUNNEL_DOOR_TIMER,0xABCD).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678));var before=pool.state(32);var secondary=a.core.state().hudSlots().slice(0,64).copy();assertArrayEquals(new byte[64],secondary,"Actual ClearObjects reserved physical1, not a supplied ZERO image");
        var registry=a.h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();forward(a);var expected=before.addByte(ROUTINE,2).moveLong(MAPPINGS,0x21D6C4).moveByte(PRIORITY,1).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,24).moveByte(Y_RADIUS,8).moveWord(TILE,0x8340).moveByte(SOLID_TYPE,0);drawBitOnly(expected,pool.state(32));assertArrayEquals(secondary,a.core.state().hudSlots().slice(0,64).copy());assertFalse(a.frame.closed());assertTrue(sounds(a).isEmpty());
        var after=registry.capture();var afterCourse=registry.captureCourse();var nativeAfter=captureNative(a.h);registry.restore(full);forward(a);assertNativeCapture(nativeAfter,a.h);values(after,registry.capture(),"Door FULL replay");registry.restoreCourse(course);forward(a);assertNativeCapture(nativeAfter,a.h);values(after,registry.capture(),"Door course->FULL");values(afterCourse,registry.captureCourse(),"Door course replay");
    }
    @Test void horizontalOpeningRunsItsRealSplashChildAfterTheA4Prefix()throws Exception{
        var a=grounded();var registry=a.h.mode().getRewindRegistry();var controlCut=registry.capture();sameChunk(a);step(a.h,jump());assertFalse(a.frame.closed());var control=a.frame.state();
        assertTrue((a.frame.environment().controls().logicalTap()&0x70)!=0,"Fresh native A/B/C tap reaches CheckJump");assertEquals(1,control.motion().playerJump());assertEquals(2,control.motion().flags()&2);
        assertTrue(control.motion().yVelocity()<0,"A real native rising Main is the opening prerequisite");registry.restore(controlCut);
        seed(a,13,2);var pool=a.host.placement().pool();int x=control.motion().centreX16_16()>>>16,y=control.motion().centreY16_16()>>>16;pool.write(pool.state(32).moveWord(X,x).moveWord(Y,y).moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,44).moveByte(Y_RADIUS,8).moveByte(PRIORITY,1).moveLong(MAPPINGS,0x21D6C4).moveWord(TILE,0x8340).moveWord(TUNNEL_DOOR_TIMER,0x4567));
        sameChunk(a);step(a.h,jump());assertFalse(a.frame.closed());assertEquals(4,pool.state(32).routine());assertEquals(0x4567,pool.state(32).word(TUNNEL_DOOR_TIMER));assertEquals(11,pool.state(33).id());assertEquals(x,pool.state(33).centreX());assertEquals((y-4)&65535,pool.state(33).centreY());assertFalse(a.frame.closed());assertEquals(2,pool.state(33).routine());assertEquals(0x21D652,pool.state(33).longBits(MAPPINGS));assertEquals(0,pool.state(33).word(TILE));
        var fm=sounds(a).stream().filter(SonicCdGameModule.PastTunnelDoorFm.class::isInstance).map(SonicCdGameModule.PastTunnelDoorFm.class::cast).toList();assertEquals(List.of(new SonicCdGameModule.PastTunnelDoorFm(CdTunnelDoor.FmSound.FM_A4)),fm);
        var splash=new SonicCdGameModule.PastTunnelDoorSplashFm(CdTunnelDoorSplash.FmSound.FM_A2);var ledger=sounds(a);
        assertEquals(List.of(splash),ledger.stream().filter(SonicCdGameModule.PastTunnelDoorSplashFm.class::isInstance).toList());
        assertTrue(ledger.indexOf(new SonicCdGameModule.PastTunnelDoorFm(CdTunnelDoor.FmSound.FM_A4))<ledger.indexOf(splash),"Actual parent A4 precedes its later-slot Splash A2");
    }
    @Test void horizontalDoorReachedTileFaultPreservesItsPriorSourceStores()throws Exception{
        var a=loaded();seed(a,13,0);var pool=a.host.placement().pool();pool.write(pool.state(32).moveByte(SUBTYPE2,1).moveWord(TILE,0xBEEF).moveWord(TUNNEL_DOOR_TIMER,0x2345));var before=pool.state(32);
        var failure=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,()->forward(a));assertEquals(CdTunnelDoor.UnsupportedBoundary.TILE_ID_SUBTYPE2,failure.boundary());assertEquals(0,failure.getSuppressed().length);assertTrue(a.frame.closed());
        var expected=before.addByte(ROUTINE,2).moveLong(MAPPINGS,0x21D6C4).moveByte(PRIORITY,1).moveByte(SPRITE_FLAGS,before.spriteFlags()|4).moveByte(WIDTH,44).moveByte(Y_RADIUS,8);assertArrayEquals(expected.copyBytes(),pool.state(32).copyBytes());assertTrue(sounds(a).isEmpty());
    }
    @Test void verticalDoorInitUsesActualPlainPrefixFactoryAnd72ByteTileOperand()throws Exception{
        var a=loaded();seed(a,53,0);var pool=a.host.placement().pool();pool.write(pool.state(32).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678).moveWord(TUNNEL_DOOR_TIMER,0xCDEF));var before=pool.state(32);forward(a);
        var expected=before.addByte(ROUTINE,2).moveLong(MAPPINGS,0x20CB52).moveByte(PRIORITY,1).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,4).moveByte(Y_RADIUS,24).moveWord(TILE,0x8340).moveByte(SOLID_TYPE,0);drawBitOnly(expected,pool.state(32));assertFalse(a.frame.closed());assertTrue(sounds(a).isEmpty());
    }
    @Test void visibleVerticalOpeningPublishesA4BeforeFrame1AndItsRealPlainSolid()throws Exception{
        var a=loaded();var registry=a.h.mode().getRewindRegistry();var controlCut=registry.capture();sameChunk(a);step(a.h,right());assertFalse(a.frame.closed());var control=a.frame.state();registry.restore(controlCut);
        seed(a,53,2);var pool=a.host.placement().pool();pool.write(pool.state(32).moveWord(X,((control.motion().centreX16_16()>>>16)+8)&65535).moveWord(Y,control.motion().centreY16_16()>>>16).moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,4).moveByte(Y_RADIUS,24).moveByte(PRIORITY,1).moveLong(MAPPINGS,0x20CB52).moveWord(TILE,0x8340).moveWord(TUNNEL_DOOR_TIMER,0xABCD));
        var full=registry.capture();var course=registry.captureCourse();forward(a);assertFalse(a.frame.closed());assertEquals(4,pool.state(32).routine());assertEquals(0,pool.state(32).word(TUNNEL_DOOR_TIMER));assertEquals(1,pool.state(32).unsignedByte(MAP_FRAME));
        var fm=sounds(a).stream().filter(SonicCdGameModule.PastVerticalDoorFm.class::isInstance).map(SonicCdGameModule.PastVerticalDoorFm.class::cast).toList();assertEquals(List.of(new SonicCdGameModule.PastVerticalDoorFm(CdTunnelDoorVertical.FmSound.FM_A4)),fm);
        var after=registry.capture();var afterCourse=registry.captureCourse();registry.restore(full);forward(a);values(after,registry.capture(),"DoorV opening FULL replay");registry.restoreCourse(course);forward(a);values(after,registry.capture(),"DoorV opening course->FULL");values(afterCourse,registry.captureCourse(),"DoorV opening course replay");
    }
    @Test void verticalResetCarryTouchesOnlyNativeHighTimerByteRoutineFrameAndDrawBit()throws Exception{
        var a=loaded();seed(a,53,4);var pool=a.host.placement().pool();pool.write(pool.state(32).moveLong(MAPPINGS,0x20CB52).moveWord(TILE,0x8340).moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,4).moveByte(Y_RADIUS,24).moveByte(PRIORITY,1).moveByte(MAP_FRAME,1).moveWord(TUNNEL_DOOR_TIMER,0xFD73).moveWord(SWING_STEP,0x4567));var before=pool.state(32);forward(a);
        drawBitOnly(before.moveByte(TUNNEL_DOOR_TIMER_HIGH,5).addByte(ROUTINE,-2).moveByte(MAP_FRAME,0),pool.state(32));assertEquals(0x0573,pool.state(32).word(TUNNEL_DOOR_TIMER));assertTrue(sounds(a).isEmpty());assertFalse(a.frame.closed());
    }
    @Test void verticalDoorReachedTileFaultRetainsOnlyItsNativeHeaderPrefix()throws Exception{
        var a=loaded();seed(a,53,0);var pool=a.host.placement().pool();pool.write(pool.state(32).moveByte(SUBTYPE2,1).moveWord(TILE,0xBA98));var before=pool.state(32);
        var failure=assertThrows(CdTunnelDoorVertical.UnsupportedTunnelDoorVertical.class,()->forward(a));assertEquals(CdTunnelDoorVertical.UnsupportedBoundary.TILE_ID_SUBTYPE2,failure.boundary());assertEquals(0,failure.getSuppressed().length);assertTrue(a.frame.closed());
        assertArrayEquals(before.addByte(ROUTINE,2).moveLong(MAPPINGS,0x20CB52).moveByte(PRIORITY,1).moveByte(SPRITE_FLAGS,before.spriteFlags()|4).moveByte(WIDTH,4).moveByte(Y_RADIUS,24).copyBytes(),pool.state(32).copyBytes());assertTrue(sounds(a).isEmpty());
    }
    @Test void publicMatchingReceiptsCannotEnterIdleActualCachedBodyPorts()throws Exception{
        var a=loaded();seed(a,9,2);var ordinary=actualField(a.core,"ordinary");var pass=(SonicCdAct2PastObjectPass)actualField(ordinary,"objects");var disc=(CdSpinningDisc)actualField(pass,"spinningDisc");var door=(CdTunnelDoor)actualField(pass,"tunnelDoor");var vertical=(CdTunnelDoorVertical)actualField(pass,"verticalDoor");
        var before=a.h.mode().getRewindRegistry().capture();var pair=new CdSpinningDisc.PlayerCut(a.frame.state(),a.frame.environment());var fm=new CdSpinningDisc.FmCall(a.host.placement().pool().capture(),a.host.placement().pool().state(32),pair,CdSpinningDisc.FmSound.FM_A0);
        assertThrows(IllegalStateException.class,()->((CdSpinningDisc.PlayerOwner)actualField(disc,"playerOwner")).read());assertThrows(IllegalStateException.class,()->((CdSpinningDisc.PlayFmCall)actualField(disc,"playFm")).play(fm));
        var cut=new CdTunnelDoor.PrefixSolidCut(CdTunnelDoor.Participant.PRIMARY,a.host.placement().pool().state(32).identity(),a.host.placement().pool().capture(),primary(a),gates(a),globals(a),Optional.empty());assertFalse(door.isCurrentCall(cut));assertThrows(IllegalStateException.class,()->((CdTunnelDoor.PrefixSolidOwner)actualField(door,"prefixSolidOwner")).preflight(cut));
        assertFalse(vertical.isCurrentDrawCall(a.host.placement().pool(),a.host.placement().pool().state(32).identity()));assertThrows(IllegalStateException.class,()->((CdTunnelDoorVertical.PlayFmCall)actualField(vertical,"playFm")).playFm(CdTunnelDoorVertical.FmSound.FM_A4));assertThrows(IllegalStateException.class,()->pass.runWalk(1));values(before,a.h.mode().getRewindRegistry().capture(),"idle/public receipts preserve every retained owner");assertFalse(a.frame.closed());
    }
    @Test void spinTunnelActualInitUsesItsPreparedFramePortAndRetainsTheNativeSourcePrefix()throws Exception{
        var a=loaded();seed(a,52,0);var pool=a.host.placement().pool();var before=pool.state(32);assertEquals(0,a.frame.environment().globals().debugCheat()>>>8);forward(a);
        assertArrayEquals(before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,before.spriteFlags()|4).moveWord(TILE,0x0544).moveLong(MAPPINGS,0x21D528).moveByte(MAP_FRAME,1).copyBytes(),pool.state(32).copyBytes());assertFalse(a.frame.closed());assertTrue(sounds(a).isEmpty());assertTrue(a.host.video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==32));
    }
    // BEGIN R12B SpinTunnel R2 World specifications. Every ingress is explicit public-pool data.
    @Test void spinInitPreservesDirtyPriorityFractionsVelocitiesAndAllOtherNativeNonwrites()throws Exception{
        var a=loaded();seed(a,52,0);var pool=a.host.placement().pool();pool.write(pool.state(32).moveByte(PRIORITY,7).moveByte(SUBTYPE,4).moveByte(MAP_FRAME,0x73).moveByte(SPRITE_FLAGS,0x81).moveByte(FLAGS,0xA5).moveByte(WIDTH,0x27).moveByte(Y_RADIUS,0x31).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678).moveWord(X_VELOCITY,0x9ABC).moveWord(Y_VELOCITY,0xDEF0).moveWord(SWING_STEP,0x2468));var before=pool.state(32);forward(a);
        var expected=before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,before.spriteFlags()|4).moveWord(TILE,0x0544).moveLong(MAPPINGS,0x21D528).moveByte(MAP_FRAME,5);assertArrayEquals(expected.copyBytes(),pool.state(32).copyBytes());assertFalse(a.frame.closed());assertTrue(sounds(a).isEmpty());assertTrue(a.host.video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==32));
    }
    @Test void spinMainOutsideRangeWithNativeBit7SetKeepsTheSourceAndSkipsDebugDraw()throws Exception{
        var a=loaded();seed(a,52,2);var pool=a.host.placement().pool();pool.write(pool.state(32).moveWord(X,(a.host.scroll().foreground().cameraX()+0x1000)&65535).moveByte(SPRITE_FLAGS,0x84).moveLong(MAPPINGS,0x21D528).moveWord(TILE,0x0544).moveByte(PRIORITY,7).moveByte(MAP_FRAME,1).moveWord(SWING_STEP,0x7654));var before=pool.state(32);forward(a);
        assertArrayEquals(before.copyBytes(),pool.state(32).copyBytes(),"No debug Draw clears bit7; native despawn therefore retains even this distant source");assertFalse(a.frame.closed());assertTrue(sounds(a).isEmpty());assertTrue(a.host.video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==32));
    }
    @Test void actualSpinSubtypeZeroPublishesPreparedSpeedRollAndWholePairThenReplays()throws Exception{
        var a=loaded();var registry=a.h.mode().getRewindRegistry();var controlCut=registry.capture();sameChunk(a);step(a.h,right());assertFalse(a.frame.closed());var control=a.frame.state();registry.restore(controlCut);var m=control.motion();assertTrue(m.routine()<6);assertNotEquals(0x2B,m.animation());
        seed(a,52,0);var pool=a.host.placement().pool();pool.write(pool.state(32).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16).moveByte(PRIORITY,7).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678));var source=pool.state(32);
        int magnitude=m.xVelocity()<0?(short)-m.xVelocity():m.xVelocity();if((magnitude&65535)<0xA00)magnitude=0xA00;int xv=m.xVelocity()<0?(short)-magnitude:(short)magnitude,gv=(((m.angle()+0x20)&255)&0xC0)==0x80?(short)-xv:xv;
        var expected=spinExpected(control,xv,m.yVelocity(),gv,m.flags());var full=registry.capture();var course=registry.captureCourse();forward(a);values(expected,a.frame.state(),"actual Spin0 typed Frame product");assertArrayEquals(source.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveWord(TILE,0x0544).moveLong(MAPPINGS,0x21D528).moveByte(MAP_FRAME,1).copyBytes(),pool.state(32).copyBytes());assertFalse(a.frame.closed());assertTrue(sounds(a).isEmpty());
        var after=registry.capture();var afterCourse=registry.captureCourse();var nativeAfter=captureNative(a.h);registry.restore(full);forward(a);assertNativeCapture(nativeAfter,a.h);values(after,registry.capture(),"Spin0 FULL replay");registry.restoreCourse(course);forward(a);assertNativeCapture(nativeAfter,a.h);values(after,registry.capture(),"Spin0 course->FULL");values(afterCourse,registry.captureCourse(),"Spin0 course replay");
    }
    @Test void actualSpinSubtypeFourReadsNativeRawUpAndPublishesTheNegativeImmediate()throws Exception{
        var a=loaded();var input=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(1,0,0,0,false,false),PlayerInputState.neutral());var registry=a.h.mode().getRewindRegistry();var controlCut=registry.capture();sameChunk(a);step(a.h,input);assertFalse(a.frame.closed());var control=a.frame.state();registry.restore(controlCut);var m=control.motion();assertTrue(m.xVelocity()>=0,"Actual returning Main must take the subtype4 nonnegative-X native entrance");assertTrue(m.routine()<6);assertNotEquals(0x2B,m.animation());
        seed(a,52,2);var pool=a.host.placement().pool();pool.write(pool.state(32).moveByte(SUBTYPE,4).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16).moveLong(MAPPINGS,0x21D528).moveWord(TILE,0x0544).moveByte(MAP_FRAME,5));var source=pool.state(32);sameChunk(a);step(a.h,input);
        values(spinExpected(control,m.xVelocity(),-0xA00,-0xA00,m.flags()|2),a.frame.state(),"actual Spin4 raw-Up native branch");assertArrayEquals(source.copyBytes(),pool.state(32).copyBytes());assertTrue(sounds(a).isEmpty());assertFalse(a.frame.closed());
    }
    @Test void spinTailClearsOnlyItsReachedSavedLoadedBitBeforeRealZero64Delete()throws Exception{
        var a=loaded();seed(a,52,0);var placement=a.host.placement();var pool=placement.pool();pool.write(pool.state(32).moveWord(X,(a.host.scroll().foreground().cameraX()+0x1000)&65535).moveByte(SAVED_FLAGS_ID,7).moveWord(SWING_STEP,0xBEEF));placement.bsetSavedFlag(7,0,CdObjectPlacementRuntime.SavedBit.LOADED);placement.bsetSavedFlag(7,0,CdObjectPlacementRuntime.SavedBit.MEMBER_2);var saved=placement.capture().savedFlags().copy();saved[2+3*7]&=127;forward(a);
        assertArrayEquals(new byte[64],pool.state(32).copyBytes());assertArrayEquals(saved,placement.capture().savedFlags().copy());assertFalse(a.frame.closed());assertTrue(sounds(a).isEmpty());assertTrue(a.host.video().completedCpu().orElseThrow().provenance().stream().noneMatch(v->v.slotRef().wholeIndex()==32));
    }
    @Test void spinReachedMissingSavedAddressRetainsPreparedInitBeforeAnyBankClearOrDelete()throws Exception{
        var a=loaded();seed(a,52,0);var placement=a.host.placement();var pool=placement.pool();pool.write(pool.state(32).moveWord(X,(a.host.scroll().foreground().cameraX()+0x1000)&65535).moveByte(SAVED_FLAGS_ID,255).moveWord(SWING_STEP,0xABCD));var before=pool.state(32);var saved=placement.capture().savedFlags().copy();var failure=assertThrows(SonicCdAct2PastObjectPass.Unowned.class,()->forward(a));assertEquals(SonicCdAct2PastObjectPass.Boundary.SPIN_SAVED_FLAGS,failure.boundary());assertEquals(767,failure.value());assertEquals(32,failure.wholeIndex());assertEquals(0,failure.getSuppressed().length);
        assertArrayEquals(before.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,before.spriteFlags()|4).moveWord(TILE,0x0544).moveLong(MAPPINGS,0x21D528).moveByte(MAP_FRAME,1).copyBytes(),pool.state(32).copyBytes());assertArrayEquals(saved,placement.capture().savedFlags().copy());assertTrue(a.frame.closed());assertTrue(sounds(a).isEmpty());
    }
    @Test void spinNativeInvalidRoutinePreservesItsOriginalFaultAndEverySourceByte()throws Exception{
        var a=loaded();seed(a,52,3);var pool=a.host.placement().pool();pool.write(pool.state(32).moveWord(SWING_STEP,0xABCD));var before=pool.state(32);var failure=assertThrows(CdSpinTunnelTag.UnsupportedSpinTunnelTag.class,()->forward(a));assertEquals(CdSpinTunnelTag.UnsupportedBoundary.ROUTINE_DISPATCH,failure.boundary());assertEquals(3,failure.value());assertEquals(32,failure.wholeIndex());assertEquals(0,failure.getSuppressed().length);assertArrayEquals(before.copyBytes(),pool.state(32).copyBytes());assertTrue(a.frame.closed());assertTrue(sounds(a).isEmpty());
    }
    @Test void publicPureSpinResultCannotPublishOrEnterTheActualIdleWorldInvocation()throws Exception{
        var a=loaded();seed(a,52,0);var ordinary=actualField(a.core,"ordinary");var pass=(SonicCdAct2PastObjectPass)actualField(ordinary,"objects");var body=(CdSpinTunnelTag)actualField(pass,"spinTunnel");var placement=a.host.placement();var before=a.h.mode().getRewindRegistry().capture();var source=placement.pool().state(32);var e=a.frame.environment();
        var detached=body.step(source,a.frame.state().motion(),new CdSpinTunnelTag.Gates(e.movement().gates().zoneAct()>>>8,e.globals().debugMode(),e.globals().debugCheat(),e.controls().rawHeld()));assertSame(source,detached.expectedObject());assertEquals(2,detached.object().routine());assertArrayEquals(source.copyBytes(),placement.pool().state(32).copyBytes());
        var invoke=SonicCdAct2PastObjectPass.class.getDeclaredMethod("runSpinTunnel",int.class);invoke.setAccessible(true);var failure=assertThrows(InvocationTargetException.class,()->invoke.invoke(pass,32));assertInstanceOf(IllegalStateException.class,failure.getCause());values(before,a.h.mode().getRewindRegistry().capture(),"pure/idle Spin result carries no World authority");assertFalse(a.frame.closed());forward(a);assertFalse(a.frame.closed());
    }
    static CdPlayerBody.State spinExpected(CdPlayerBody.State state,int xv,int yv,int gv,int flags){var m=state.motion();boolean firstRoll=(flags&4)==0;int y=m.centreY16_16();if(firstRoll)y=((((y>>>16)+5)&65535)<<16)|(y&65535);
        var next=new CdPlayerMotionState(m.centreX16_16(),y,xv,yv,gv,m.routine(),m.angle(),flags|4,m.spriteFlags(),firstRoll?7:m.xRadius(),firstRoll?14:m.yRadius(),firstRoll?2:m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());return new CdPlayerBody.State(next,state.animation(),state.header(),state.timers(),state.history(),state.rotationDistance(),state.objectLinks(),state.rotationAngle());}
    // END R12B SpinTunnel R2 World specifications.
    @SuppressWarnings("unchecked")
    static List<SonicCdGameModule.PastSound> sounds(A a)throws Exception{
        if(!a.frame.closed())return a.host.sounds();
        // A terminal World intentionally retires Host's public source guard. Inspect its retained
        // immutable Image only; no retired owner method, setter, invocation or new state is used.
        var image=actualField(a.host,"image");var accessor=image.getClass().getDeclaredMethod("sounds");accessor.setAccessible(true);
        return (List<SonicCdGameModule.PastSound>)accessor.invoke(image);
    }
    static CdSolidObject.Player primary(A a){var s=a.frame.state();var h=s.header();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());}
    static CdSolidObject.Gates gates(A a){return new CdSolidObject.Gates(a.frame.environment().globals().debugMode(),a.frame.environment().movement().gates().miniSonic());}
    static CdSolidObject.Globals globals(A a){var e=a.frame.environment();var g=e.movement().globals();return new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());}
    static void sameChunk(A a){assertEquals(a.host.scroll().foreground().cameraX()&0xFF80,a.host.placement().status().previousChunkWord(),"The next real SpawnObjects must retain its equal chunk; no cursor/camera writes");}
    static void forward(A a){sameChunk(a);step(a.h,right());}
    /** Explicit constructed PUBLIC-POOL COMPONENT INGRESS after complete native loading/Init/Main. */
    static void seed(A a,int id,int routine){sameChunk(a);var p=a.host.placement();p.pool().clearDynamicSlots();var m=a.frame.state().motion();p.pool().write(p.pool().state(32).moveByte(ID,id).moveByte(ROUTINE,routine).moveWord(X,((m.centreX16_16()>>>16)-100)&65535).moveWord(Y,m.centreY16_16()>>>16));}
    static void drawBitOnly(CdObjectState expected,CdObjectState actual){assertArrayEquals(expected.moveByte(SPRITE_FLAGS,(expected.spriteFlags()&127)|(actual.spriteFlags()&128)).copyBytes(),actual.copyBytes());}
    static LogicalInputSnapshot jump(){return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,2,0,false,false),PlayerInputState.neutral());}
    static LogicalInputSnapshot right(){return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),PlayerInputState.neutral());}
    static void values(Object expected,Object actual,String label)throws Exception{var method=TestSonicCdAct2ControllerRoute.class.getDeclaredMethod("assertNaturalR12bSnapshotValue",Object.class,Object.class,String.class);method.setAccessible(true);try{method.invoke(null,expected,actual,label);}catch(InvocationTargetException failure){var cause=failure.getCause();if(cause instanceof Error error)throw error;if(cause instanceof Exception error)throw error;throw failure;}}
    /** Fixed ordinary settling/release prefix; no search, retry, coordinate/physics write or component call.
     * Init/Main completion alone establishes neither grounded mode nor a fresh jump prerequisite.
     * N1ecc5f3 Sonic/Main:713-735 services air; :697,1779-1831 checks a new jump only from grounded modes. */
    static A grounded()throws Exception{
        var a=loaded();var source=a.frame.stageSource();var world=a.h.mode().getWorldSession();long generation=a.h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        int firstLevelFrame=a.frame.environment().globals().levelFrames();var neutral=LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),PlayerInputState.neutral());
        for(int call=0;call<64;call++){
            step(a.h,neutral);assertFalse(a.frame.closed(),"Fixed64 ordinary neutral services must return");assertSame(a.frame,a.h.module().requirePlayerFrame());assertSame(source,a.frame.stageSource());
            assertSame(world,a.h.mode().getWorldSession());assertEquals(generation,a.h.mode().getLevelManager().getCompletedProductionLoadGeneration());
        }
        assertEquals((firstLevelFrame+64)&65535,a.frame.environment().globals().levelFrames());var m=a.frame.state().motion();
        assertEquals(2,m.routine());assertEquals(0,m.flags()&10,"Fixed native prefix must end grounded without a stood-object alias");
        // Native Sonic CalcXYVels (Main.asm:1165-1174) retains sin(angle)*groundVelocity in Y while grounded.
        assertEquals(0,m.playerControl()&1);assertEquals(0,m.playerJump());assertEquals(0,m.charge());assertEquals(0,a.frame.environment().controls().controlLocked());
        assertEquals(0,a.frame.environment().controls().rawHeld()&0x70);assertEquals(0,a.frame.environment().controls().rawTap()&0x70);sameChunk(a);return a;
    }
    /** Real cold controller until BEGIN_LEVEL, then genuine primary Init AND Main with no ingress. */
    static A loaded()throws Exception{var prepared=ready();var h=prepared.harness();for(int call=prepared.returned()+1;call<=14142;call++){step(h,controller(call));var module=h.module();
        if(module.act2PastLoadingSelected()&&module.act2PastLoadingVisit().state().phase()==SonicCdAct2PastLoadingVisit.Phase.BEGIN_LEVEL){var core=module.act2PastLoadingVisit();var frame=module.requirePlayerFrame();var host=module.act2PastWorldHost();var world=h.mode().getWorldSession();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();assertTrue(host.geometryReady());assertEquals(0,core.state().section().queue().headSource());
            for(int next=0;next<8;next++){step(h,right());assertFalse(frame.closed(),"Genuine native primary initialization/Main prerequisite");assertSame(world,h.mode().getWorldSession());assertSame(frame,module.requirePlayerFrame());assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
                if(frame.environment().globals().levelFrames()>=2&&frame.state().motion().routine()==2&&frame.display().isPresent()&&host.placement().status().previousChunkWord()==(host.scroll().foreground().cameraX()&0xFF80)){return new A(h,core,host,frame);}}
            throw new AssertionError("No genuine Main at an equal retained spawner chunk within8 ordinary services");}}
        throw new AssertionError("Actual target loading did not finish within inherited finite bound");}
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
