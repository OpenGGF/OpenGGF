package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** SOURCE candidates, all Java UNRUN. Actual R31 data/Frame/placement/pass with the existing
 * explicitly CONSTRUCTED Ports fixture. Raw slot images below are controls, never route evidence.
 * Existing names for $2A/$2C/$30 are overlapping storage aliases, not new gameplay owners. */
class TestSonicCdR31aGaPass {
    @Test void normalInitUsesActualGaMappingAndOnlyNativeStores()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            h.dynamic(32,0x2D,0,0);var d=h.placement.pool().state(32).moveWord(X,100).moveWord(Y,100).moveByte(FLAGS,0x52).moveWord(X_FRACTION,0xCAFE);
            h.placement.pool().write(d);var cut=h.placement.capture();var f=h.frame.captureActor();var g=h.frame.captureShared();h.walk();
            var expected=d.copyBytes();expected[0x24]=2;expected[1]=4;expected[0x1A]=0;expected[0x18]=1;expected[0x19]=16;expected[0x16]=8;
            word(expected,2,0xA421);word(expected,0x2A,0x3C);longWord(expected,4,0x20B4F2);expected[0x3F]=(byte)255;
            assertArrayEquals(expected,h.placement.pool().state(32).copyBytes());nonwrites(h,cut,Set.of(32));assertEquals(f,h.frame.captureActor());assertEquals(g,h.frame.captureShared());h.finish();
        }
    }
    @Test void damagedParentInitPreservesRawVar3fAndSelectsSeparateRoot()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            h.dynamic(32,0x2D,0,0x81);var d=h.placement.pool().state(32).moveWord(X,100).moveWord(Y,100).moveByte(TAMABBOH_MISSILE_DIRECTION,0x27);
            h.placement.pool().write(d);h.walk();var after=h.placement.pool().state(32);
            assertEquals(2,after.routine());assertEquals(0x20B4B0,after.longBits(MAPPINGS));assertEquals(d.copyBytes()[0x3F],after.copyBytes()[0x3F]);h.finish();
        }
    }
    @Test void dustInitUsesExistingRingAndFallsThroughMainOnce()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            h.dynamic(32,0x2D,0,1);h.placement.pool().write(h.placement.pool().state(32).moveWord(X,100).moveWord(Y,100).moveWord(Y_FRACTION,0x1234));
            var f=h.frame.captureActor();var g=h.frame.captureShared();h.walk();var s=h.placement.pool().state(32);
            assertEquals(2,s.routine());assertEquals(101,s.centreY());assertEquals(0x1234,s.word(Y_FRACTION));assertEquals(0xB3,s.word(PATA_BATA_ANCHOR_X));
            assertEquals(0x209BE4,s.longBits(MAPPINGS));assertEquals(0xA7AE,s.word(TILE));assertEquals(4,s.unsignedByte(MAP_FRAME));assertEquals(f,h.frame.captureActor());assertEquals(g,h.frame.captureShared());h.finish();
        }
    }
    @Test void activeParentQueuesBothNativeDrawSitesWithoutRelaxingOtherFamilies()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            active(h,32,4);var f=h.frame.captureActor();var g=h.frame.captureShared();h.walk();h.finish();
            var cpu=h.cut.completedCpu().orElseThrow();assertEquals(2,cpu.provenance().stream().filter(p->p.slotRef().wholeIndex()==32).count());
            assertEquals(List.of(0,1),cpu.provenance().stream().filter(p->p.slotRef().wholeIndex()==32).map(CdObjectDrawQueue.Provenance::queueOrdinal).toList());
            assertEquals(f,h.frame.captureActor());assertEquals(g,h.frame.captureShared());
        }
    }
    @Test void reachedLaunchReadsGenuineCurrentPrimaryXAfterNativeVelocityStores()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            active(h,32,6);h.placement.pool().write(h.placement.pool().state(32).moveWord(PATA_BATA_ANCHOR_X,1));
            int x=h.frame.state().motion().centreX16_16()>>>16;var f=h.frame.captureActor();var g=h.frame.captureShared();h.walk();var s=h.placement.pool().state(32);
            assertEquals(8,s.routine());assertEquals(0x26,s.unsignedByte(COLLISION_TYPE));assertEquals(0x8000,s.longBits(MOSQUI_ANIMATION_SCRIPT));
            assertEquals((short)(100-x)<0?0x8000:-0x8000,s.longBits(MOSQUI_X_DELTA));assertEquals(0x20,s.word(PATA_BATA_ANCHOR_X));
            assertEquals(f,h.frame.captureActor());assertEquals(g,h.frame.captureShared());h.finish();
        }
    }
    @Test void laterDustChildRunsItsInitAtTheSameIncreasingPhysicalWalk()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flight(h,32);h.walk();var parent=h.placement.pool().state(32);var child=h.placement.pool().state(33);
            assertEquals(0x2D,child.id());assertEquals(1,child.subtype());assertEquals(2,child.routine());assertEquals(parent.centreX(),child.centreX());
            assertEquals(parent.centreY()+1,child.centreY());assertEquals(parent.word(Y_FRACTION),child.word(Y_FRACTION));assertEquals(0xB3,child.word(PATA_BATA_ANCHOR_X));h.finish();
        }
    }
    @Test void earlierDustChildWaitsForTheNextPhysicalWalk()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flight(h,40);h.walk();assertEquals(0x2D,h.placement.pool().state(32).id());assertEquals(0,h.placement.pool().state(32).routine());h.finish();
            h.walk();assertEquals(2,h.placement.pool().state(32).routine());h.finish();
        }
    }
    @Test void fullPoolDoesNotAllocateOrSkipTheRemainingNativeParentTail()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            flight(h,32);for(int w=33;w<128;w++)h.dynamic(w,0x2D,4,1);
            h.walk();assertEquals(0x41,h.placement.pool().state(32).word(PATA_BATA_ANCHOR_X));assertEquals(8,h.placement.pool().state(32).routine());
            for(int w=33;w<128;w++)assertEquals(0,h.placement.pool().state(w).id());h.finish();
        }
    }
    @Test void dustDeleteStillDrawsTheClearedSlotWithoutMappingOrFrameMutation()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            h.dynamic(32,0x2D,4,1);var f=h.frame.captureActor();var g=h.frame.captureShared();h.walk();
            assertArrayEquals(new byte[64],h.placement.pool().state(32).copyBytes());assertEquals(f,h.frame.captureActor());assertEquals(g,h.frame.captureShared());h.finish();assertTrue(h.cut.completedCpu().orElseThrow().provenance().isEmpty());
        }
    }
    @Test void unsupportedRoutineFaultRetainsEarlierWorkAndSkipsLaterPhysicalSlots()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            h.dynamic(32,0x2D,0,0);h.placement.pool().write(h.placement.pool().state(32).moveWord(X,100).moveWord(Y,100));h.dynamic(33,0x2D,0xEE,0);h.dynamic(34,0x2D,0,0);
            var failing=h.placement.pool().state(33);var later=h.placement.pool().state(34);assertThrows(UnsupportedOperationException.class,h::walk);
            assertEquals(2,h.placement.pool().state(32).routine());assertEquals(failing,h.placement.pool().state(33));assertEquals(later,h.placement.pool().state(34));assertThrows(IllegalStateException.class,h::walk);
        }
    }
    @Test void constructedMainUnknownMappingFailsOnlyWhenTheActualCpuQueueSelectsIt()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            active(h,32,4);h.placement.pool().write(h.placement.pool().state(32).moveLong(MAPPINGS,0x12345678));h.walk();var before=h.cut.cpuSat();
            assertThrows(UnsupportedOperationException.class,h::finish);assertSame(before,h.cut.cpuSat());assertEquals(0x12345678,h.placement.pool().state(32).longBits(MAPPINGS));
        }
    }
    @Test void sameCachedGaAndRealPoolReplayAfterCompletePlacementRestore()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){
            active(h,32,4);var receiver=field(h.pass,"ga");var before=h.placement.capture();h.walk();var after=h.placement.capture();h.finish();
            h.placement.restore(before);h.walk();assertEquals(after.pool().slots(),h.placement.capture().pool().slots());assertSame(receiver,field(h.pass,"ga"));h.finish();
        }
    }
    private static void active(TestSonicCdR31aObjectPass.Harness h,int w,int routine){
        h.dynamic(w,0x2D,routine,0);h.placement.pool().write(h.placement.pool().state(w).moveWord(X,100).moveWord(Y,100).moveByte(SPRITE_FLAGS,4)
            .moveByte(PRIORITY,1).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveLong(MAPPINGS,0x20B4F2).moveWord(TILE,0xA421).moveByte(MAP_FRAME,0)
            .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,5).moveWord(PATA_BATA_ANCHOR_X,2));
    }
    private static void flight(TestSonicCdR31aObjectPass.Harness h,int w){active(h,w,8);h.placement.pool().write(h.placement.pool().state(w).moveWord(PATA_BATA_ANCHOR_X,1).moveByte(TAMABBOH_MISSILE_DIRECTION,255).moveLong(MOSQUI_X_DELTA,-0x8000).moveLong(MOSQUI_ANIMATION_SCRIPT,0x8000));}
    private static Object field(Object v,String name)throws Exception{var f=v.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(v);}
    private static void nonwrites(TestSonicCdR31aObjectPass.Harness h,CdObjectPlacementRuntime.Snapshot before,Set<Integer> changed){var after=h.placement.capture();assertEquals(before.status(),after.status());assertArrayEquals(before.savedFlags().copy(),after.savedFlags().copy());for(int w=32;w<128;w++)if(!changed.contains(w))assertEquals(before.pool().slots().get(w-32),h.placement.pool().state(w));}
    private static void word(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
    private static void longWord(byte[] b,int at,int value){word(b,at,value>>>16);word(b,at+2,value);}
}
