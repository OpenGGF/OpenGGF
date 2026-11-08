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

/** SOURCE-AUTHORED / ALL JAVA UNRUN. Genuine Boot/controller/World/Host/Frame/public rewind
 * ingress and original assets. Enemy vectors are explicitly CONSTRUCTED physical slot images
 * after the first ordinary visit; no natural reach, route, reward, audio or pixel claim. */
@Isolated class TestSonicCdR31aKamaSickleWorld {
    @TempDir Path directory;
    private final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
    @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void genuinePublicWorldWalkDispatchesParentAndLaterDirtyChildrenOnce() throws Exception {
        try(var b=fixture.start(1,0x80)) {
            TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);
            var frame=b.module.requirePlayerFrame();var host=b.module.r31WorldHost();var core=b.module.r31LoadingVisit();var pool=host.placement().pool();
            constructedParent(b,32);var before=host.placement().capture();long clock=frame.vIntClock().absoluteValue().orElseThrow();
            TestSonicCdR31aSelectedProfileLoading.step(b,8);
            assertSame(frame,b.module.requirePlayerFrame());assertSame(core,b.module.r31LoadingVisit());assertSame(host,b.module.r31WorldHost());assertFalse(frame.closed());
            assertEquals(clock+1,frame.vIntClock().absoluteValue().orElseThrow());assertEquals(6,pool.state(32).routine());assertEquals(0x77,pool.state(32).word(TITLE_CARD_START_X));
            assertEquals(2,pool.state(33).routine());assertEquals(0x40,pool.state(33).word(TITLE_CARD_START_X));
            assertEquals(2,pool.state(34).routine());assertEquals(0x14,pool.state(34).word(TITLE_CARD_START_X));
            assertEquals(0xCAFE,pool.state(33).word(X_FRACTION));assertEquals(0xBEEF,pool.state(34).word(Y_FRACTION));
            assertEquals(before.status(),host.placement().capture().status());assertArrayEquals(before.savedFlags().copy(),host.placement().capture().savedFlags().copy());
            assertTrue(host.video().completedCpu().isPresent());
        }
    }
    @Test void earlierChildrenWaitAndFullCourseRestoreUseTheSameLiveOwners() throws Exception {
        try(var b=fixture.start(1,0x80)) {
            TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);constructedParent(b,40);
            var frame=b.module.requirePlayerFrame();var host=b.module.r31WorldHost();var registry=b.mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            TestSonicCdR31aSelectedProfileLoading.step(b,8);var after=TestSonicCdR31aSelectedProfileLoading.values(b);
            assertEquals(0x25,host.placement().pool().state(32).id());assertEquals(0,host.placement().pool().state(32).routine());
            for(boolean c:new boolean[]{false,true}) {
                if(c)registry.restoreCourse(course);else registry.restore(full);
                TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(after,TestSonicCdR31aSelectedProfileLoading.values(b));
                assertSame(frame,b.module.requirePlayerFrame());assertSame(host,b.module.r31WorldHost());
            }
        }
    }
    @Test void unsupportedExplosionIsReachedOnTheNextPhysicalVisitWithoutRewardReplay() throws Exception {
        try(var b=fixture.start(1,0x80)) {
            TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);constructedParent(b,32);
            var host=b.module.r31WorldHost();var frame=b.module.requirePlayerFrame();var core=b.module.r31LoadingVisit();var pool=host.placement().pool();
            pool.write(pool.state(32).moveByte(ID,0x18));var explosion=pool.state(32);var placed=host.placement().capture();var score=host.globals().score();
            assertEquals(0x18,explosion.id());assertEquals(4,explosion.routine());assertEquals(0,explosion.unsignedByte(ROUTINE_2));
            // Existing constructed enemy vector now reaches the real cached Explosion Done.
            // Its ID/routine stores return; completed metadata cannot redispatch Flower here.
            TestSonicCdR31aSelectedProfileLoading.step(b,8);
            var expected=explosion.copyBytes();expected[0]=0x1F;expected[0x24]=0;
            assertArrayEquals(expected,pool.state(32).copyBytes());assertFalse(frame.closed());
            assertSame(frame,b.module.requirePlayerFrame());assertSame(host,b.module.r31WorldHost());assertSame(core,b.module.r31LoadingVisit());
            assertEquals(SonicCdR31aLoadingVisit.Phase.READY,core.state().phase());assertEquals(score,host.globals().score());
            assertEquals(placed.status(),host.placement().status());assertArrayEquals(placed.savedFlags().copy(),host.placement().capture().savedFlags().copy());
            // Labelled constructed bank control, introduced only after all historical
            // conversion/full-bank/score assertions above: skip floor via the genuine bit6.
            host.placement().bsetSavedFlag(pool.state(32).savedFlagsId(),1,com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.SavedBit.MEMBER_7);
            TestSonicCdR31aSelectedProfileLoading.step(b,8);
            assertEquals(0x1F,pool.state(32).id());assertEquals(4,pool.state(32).routine());assertEquals(3,pool.state(32).unsignedByte(ANIMATION));
            assertFalse(frame.closed());assertSame(frame,b.module.requirePlayerFrame());assertEquals(score,host.globals().score());
        }
    }
    private static void constructedParent(TestSonicCdR31aSelectedProfileLoading.Booted b,int whole) {
        var pool=b.module.r31WorldHost().placement().pool();
        // Deliberate complete CONSTRUCTED vector, using existing public pool stores after loading.
        // No private World/Core snapshot or receipt is built or supplied to a producer.
        for(int w=32;w<128;w++)pool.deleteObject(w);
        var m=b.module.requirePlayerFrame().state().motion();int x=(m.centreX16_16()>>>16)+4,y=m.centreY16_16()>>>16;
        pool.write(pool.state(whole).moveByte(ID,0x24).moveByte(ROUTINE,4).moveByte(SPRITE_FLAGS,0x84).moveByte(PRIORITY,3).moveByte(WIDTH,16).moveByte(Y_RADIUS,20)
            .moveWord(X,x).moveWord(Y,y).moveLong(MAPPINGS,0x2358CA).moveWord(TILE,0x23F2).moveByte(ANIMATION,2).moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_TIME,5));
        for(int w:new int[]{whole==32?33:32,whole==32?34:33})pool.write(pool.state(w).moveWord(X_FRACTION,0xCAFE).moveWord(Y_FRACTION,0xBEEF));
    }
}
