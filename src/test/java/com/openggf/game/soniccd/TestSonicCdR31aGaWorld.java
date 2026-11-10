package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** All Java UNRUN. Genuine selected-profile Boot/controller/World/Frame and public rewind ingress.
 * Slot vectors are explicitly constructed after the first ordinary service. No natural $2D visit,
 * actual R161 continuation, new Ready, regional coverage, native execution or pixel claim. */
@Isolated class TestSonicCdR31aGaWorld {
    @TempDir Path directory;
    private final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
    @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void originalSharedWorldVisitsGaInitThroughItsCurrentFrameAndPlacement()throws Exception{
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);
            var frame=b.module.requirePlayerFrame();var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();
            construct(b,0);var before=host.placement().capture();long clock=frame.vIntClock().absoluteValue().orElseThrow();
            TestSonicCdR31aSelectedProfileLoading.step(b,8);var ga=host.placement().pool().state(32);
            assertEquals(0x2D,ga.id());assertEquals(2,ga.routine());assertEquals(0x20B4F2,ga.longBits(MAPPINGS));assertEquals(0x3C,ga.word(PATA_BATA_ANCHOR_X));
            assertSame(frame,b.module.requirePlayerFrame());assertSame(host,b.module.r31WorldHost());assertSame(core,b.module.r31LoadingVisit());assertFalse(frame.closed());
            assertEquals(clock+1,frame.vIntClock().absoluteValue().orElseThrow());assertEquals(before.status(),host.placement().status());assertArrayEquals(before.savedFlags().copy(),host.placement().capture().savedFlags().copy());
        }
    }
    @Test void activeGaPublishesBothOriginalQueueOccurrencesIntoTheExistingWorldCpuSat()throws Exception{
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);construct(b,4);
            var frame=b.module.requirePlayerFrame();var host=b.module.r31WorldHost();var terrain=host.currentTerrain();
            TestSonicCdR31aSelectedProfileLoading.step(b,8);
            assertEquals(2,host.video().completedCpu().orElseThrow().provenance().stream().filter(p->p.slotRef().wholeIndex()==32).count());
            assertSame(frame,b.module.requirePlayerFrame());assertSame(terrain,host.currentTerrain());assertFalse(frame.closed());
        }
    }
    @Test void completeAndCourseRestoreReplayGaThroughTheSameLiveOwners()throws Exception{
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);construct(b,4);
            var frame=b.module.requirePlayerFrame();var host=b.module.r31WorldHost();var registry=b.mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            TestSonicCdR31aSelectedProfileLoading.step(b,8);var expected=TestSonicCdR31aSelectedProfileLoading.values(b);
            for(boolean c:new boolean[]{false,true}){
                if(c)registry.restoreCourse(course);else registry.restore(full);
                TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(expected,TestSonicCdR31aSelectedProfileLoading.values(b));
                assertSame(frame,b.module.requirePlayerFrame());assertSame(host,b.module.r31WorldHost());assertFalse(frame.closed());
            }
        }
    }
    private static void construct(TestSonicCdR31aSelectedProfileLoading.Booted b,int routine){
        var pool=b.module.r31WorldHost().placement().pool();for(int w=32;w<128;w++)pool.deleteObject(w);
        var m=b.module.requirePlayerFrame().state().motion();int x=(m.centreX16_16()>>>16)+64,y=(m.centreY16_16()>>>16)-48;
        pool.write(pool.state(32).moveByte(ID,0x2D).moveByte(ROUTINE,routine).moveByte(SUBTYPE,0).moveWord(X,x).moveWord(Y,y)
            .moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,1).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveLong(MAPPINGS,0x20B4F2).moveWord(TILE,0xA421)
            .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,5).moveByte(MAP_FRAME,0).moveWord(PATA_BATA_ANCHOR_X,2));
    }
}
