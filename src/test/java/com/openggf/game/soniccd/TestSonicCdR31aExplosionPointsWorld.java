package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** SOURCE-AUTHORED / ALL JAVA UNRUN. Real Boot/controller/World/Host/Frame/media and public
 * placement/pool/rewind ingress. Dynamic vectors are explicitly CONSTRUCTED after the first
 * ordinary visit; never natural encounter, original enemy kill, native execution or pixel proof. */
@Isolated class TestSonicCdR31aExplosionPointsWorld {
    @TempDir Path directory;
    private final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
    @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void actualWorldRunsLaterPointsOnceThroughItsActualPhysicalVisit() throws Exception {
        try(var b=fixture.start(1,0x80)){ready(b);seed(b,32,0,0);var host=b.module.r31WorldHost();var frame=b.module.requirePlayerFrame();var core=b.module.r31LoadingVisit();var score=host.globals().score();var bank=host.placement().capture();long clock=frame.vIntClock().absoluteValue().orElseThrow();
            TestSonicCdR31aSelectedProfileLoading.step(b,8);var p=host.placement().pool();assertEquals(2,p.state(32).routine());assertEquals(0x1C,p.state(33).id());assertEquals(2,p.state(33).routine());assertEquals(23,p.state(33).unsignedByte(POINTS_TIMER));
            assertSame(frame,b.module.requirePlayerFrame());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertEquals(clock+1,frame.vIntClock().absoluteValue().orElseThrow());assertEquals(score,host.globals().score());assertEquals(bank.status(),host.placement().status());assertArrayEquals(bank.savedFlags().copy(),host.placement().capture().savedFlags().copy());assertFalse(frame.closed());}
    }
    @Test void earlierChildAndFullCourseReplayKeepTheSameWorldAndNativeBanks() throws Exception {
        try(var b=fixture.start(1,0x80)){ready(b);seed(b,40,0,0);var host=b.module.r31WorldHost();var frame=b.module.requirePlayerFrame();var r=b.mode.getRewindRegistry();var full=r.capture();var course=r.captureCourse();
            TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(0x1C,host.placement().pool().state(32).id());assertEquals(0,host.placement().pool().state(32).routine());var after=TestSonicCdR31aSelectedProfileLoading.values(b);
            for(boolean c:new boolean[]{false,true}){if(c)r.restoreCourse(course);else r.restore(full);TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(after,TestSonicCdR31aSelectedProfileLoading.values(b));assertSame(host,b.module.r31WorldHost());assertSame(frame,b.module.requirePlayerFrame());}}
    }
    @Test void actualDoneConversionStopsAtTheGenuineUnownedFlowerOnItsNextVisit() throws Exception {
        try(var b=fixture.start(1,0x80)){ready(b);seed(b,32,4,0);var pool=b.module.r31WorldHost().placement().pool();TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(0x1F,pool.state(32).id());assertEquals(0,pool.state(32).routine());
            var e=assertThrows(SonicCdR31aObjectPass.Unowned.class,()->TestSonicCdR31aSelectedProfileLoading.step(b,8));assertEquals(SonicCdR31aObjectPass.Boundary.OBJECT_FAMILY,e.boundary());assertEquals(32,e.wholeIndex());assertEquals(0x1F,e.value());}
    }
    private static void ready(TestSonicCdR31aSelectedProfileLoading.Booted b){TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);}
    private static void seed(TestSonicCdR31aSelectedProfileLoading.Booted b,int whole,int routine,int origin){var p=b.module.r31WorldHost().placement().pool();for(int w=32;w<128;w++)p.deleteObject(w);var m=b.module.requirePlayerFrame().state().motion();p.write(p.state(whole).moveByte(ID,0x18).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,origin).moveByte(SPRITE_FLAGS,4).moveWord(X,(m.centreX16_16()>>>16)+100).moveWord(Y,m.centreY16_16()>>>16));}
}
