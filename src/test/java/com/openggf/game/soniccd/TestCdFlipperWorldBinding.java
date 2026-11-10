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

/** ALL JAVA UNRUN. Public Boot/World/controller obligations with a labelled constructed slot127.
 * Earlier frontiers are failures; no completed-Ga, return-count, natural route/row/native/pixel claim. */
@Isolated class TestCdFlipperWorldBinding {
    @TempDir Path directory;
    private final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
    @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void actualPrimaryOnlyInitReachesFlipperAndRetainsReservedSecondaryBytes() throws Exception {
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);
            var registry=b.mode.getRewindRegistry();var before=registry.capture();
            TestSonicCdR31aSelectedProfileLoading.step(b,8);var control=b.module.r31LoadingVisit().state().reserved();registry.restore(before);
            var p=b.module.r31WorldHost().placement();assertEquals(0,p.pool().state(127).id());
            p.pool().write(p.pool().state(127).moveByte(ID,0x1e).moveByte(ROUTINE,0).moveByte(ANIMATION,1)
                .moveLong(X_POSITION,0x0100cafe).moveLong(Y_POSITION,0x0100beef));
            TestSonicCdR31aSelectedProfileLoading.step(b,8);var s=p.pool().state(127);
            assertEquals(2,s.routine());assertEquals(0x20b0ea,s.longBits(MAPPINGS));assertEquals(0x49d,s.word(TILE));
            assertEquals(0x0100cafe,s.longBits(X_POSITION));assertEquals(0x0100beef,s.longBits(Y_POSITION));assertArrayEquals(control.copy(),b.module.r31LoadingVisit().state().reserved().copy());
        }
    }
    @Test void fullAndCourseRestoreRunFreshOriginalInvocation() throws Exception {
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);
            var p=b.module.r31WorldHost().placement();assertEquals(0,p.pool().state(127).id());
            p.pool().write(p.pool().state(127).moveByte(ID,0x1e).moveByte(ROUTINE,0).moveByte(ANIMATION,1).moveWord(X,0x100).moveWord(Y,0x100));
            var r=b.mode.getRewindRegistry();var full=r.capture();var course=r.captureCourse();TestSonicCdR31aSelectedProfileLoading.step(b,8);var first=TestSonicCdR31aSelectedProfileLoading.values(b);
            r.restore(full);TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(first,TestSonicCdR31aSelectedProfileLoading.values(b));
            r.restoreCourse(course);TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(first,TestSonicCdR31aSelectedProfileLoading.values(b));
        }
    }
}
