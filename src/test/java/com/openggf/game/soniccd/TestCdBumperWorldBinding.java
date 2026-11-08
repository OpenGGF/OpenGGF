package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerBody;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** UNRUN POSITIVE original primary-only Bumper/World obligations.
 * Real public Boot/Frame/controller/placement/complete-restore ingress. Only the selected
 * dynamic slot is a CONSTRUCTED raw Bumper vector; no natural contact/route claim.
 * An earlier owner frontier is a failure, never an accepted substitute or skip.
 */
@Isolated class TestCdBumperWorldBinding {
    @TempDir Path directory;
    private final TestSonicCdR31aSelectedProfileLoading fixture = new TestSonicCdR31aSelectedProfileLoading();
    @BeforeEach void setup() { fixture.directory = directory; fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void actualWorldBumperInitRetainsZeroRangeDirtyVelocityAndFullWordAxis() throws Exception {
        try (var b = fixture.start(1, 0x80)) {
            TestSonicCdR31aSelectedProfileLoading.ready(b);
            TestSonicCdR31aSelectedProfileLoading.step(b, 8); // Genuine primary Init, not a synthetic actor.
            var control = controlAndRestore(b);
            var placement = b.module.r31WorldHost().placement();
            assertEquals(0, placement.pool().state(127).id());
            var raw = placement.pool().state(127).moveByte(ID, 0x1D).moveByte(ROUTINE, 0)
                    .moveByte(SUBTYPE, 0x80).moveByte(SUBTYPE2, 0).moveByte(COLLISION_STATUS, 0)
                    .moveLong(X_POSITION, 0x0100CAFE).moveLong(Y_POSITION, 0x0100BEEF)
                    .moveLong(ANTON_X_DELTA, 0x00018000);
            placement.pool().write(raw); // Labelled constructed contact-free slot, through public placement.
            TestSonicCdR31aSelectedProfileLoading.step(b, 8);
            var actual = placement.pool().state(127);
            assertEquals(2, actual.routine());
            assertEquals(0x01024AFE, actual.longBits(X_POSITION));
            assertEquals(0x0100BEEF, actual.longBits(Y_POSITION));
            assertEquals(0x00018000, actual.longBits(ANTON_X_DELTA));
            assertEquals(0xFFFF, actual.word(BOSS_PARENT)); // Native oVar2E WORD, not a new link.
            assertEquals(0, actual.word(SWING_ANGLE)); // oVar30 range.
            assertEquals(5, actual.word(SWING_MAX)); // oVar34 award budget.
            assertEquals(0x20B0AA, actual.longBits(MAPPINGS));
            assertEquals(0x4CE, actual.word(TILE));
            assertOtherOwners(control, b, 127);
        }
    }

    @Test void actualWorldUsesSharedRangeOnlyDespawnAndKeepsOtherOwners() throws Exception {
        try (var b = fixture.start(1, 0x80)) {
            TestSonicCdR31aSelectedProfileLoading.ready(b);
            TestSonicCdR31aSelectedProfileLoading.step(b, 8);
            var control = controlAndRestore(b);
            var placement = b.module.r31WorldHost().placement();
            assertEquals(0, placement.pool().state(127).id());
            placement.pool().write(placement.pool().state(127).moveByte(ID, 0x1D).moveByte(ROUTINE, 0)
                    .moveByte(SUBTYPE, 0).moveByte(SUBTYPE2, 0).moveByte(COLLISION_STATUS, 0)
                    .moveByte(SAVED_FLAGS_ID, 0).moveLong(X_POSITION, 0x4000CAFE).moveLong(Y_POSITION, 0x0100BEEF));
            TestSonicCdR31aSelectedProfileLoading.step(b, 8);
            assertArrayEquals(new byte[64], placement.pool().state(127).copyBytes());
            assertOtherOwners(control, b, 127);
        }
    }

    @Test void actualFullAndCourseRestoreReenterFreshOriginalBumperInvocation() throws Exception {
        try(var b=fixture.start(1,0x80)){
            TestSonicCdR31aSelectedProfileLoading.ready(b);TestSonicCdR31aSelectedProfileLoading.step(b,8);
            var placement=b.module.r31WorldHost().placement();assertEquals(0,placement.pool().state(127).id());
            placement.pool().write(placement.pool().state(127).moveByte(ID,0x1d).moveByte(ROUTINE,0)
                    .moveWord(X,0x100).moveWord(Y,0x100)); // Labelled constructed vector, not natural placement.
            var registry=b.mode.getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            TestSonicCdR31aSelectedProfileLoading.step(b,8);var first=TestSonicCdR31aSelectedProfileLoading.values(b);
            for(boolean useCourse:new boolean[]{false,true}){
                if(useCourse)registry.restoreCourse(course);else registry.restore(full);
                TestSonicCdR31aSelectedProfileLoading.step(b,8);assertEquals(first,TestSonicCdR31aSelectedProfileLoading.values(b));
            }
        }
    }

    private record Control(CdPlayerBody.State primary, CdPlayerBody.Environment environment,
                           Object globals, List<SonicCdGameModule.PastSound> sounds, byte[] reserved,
                           CdObjectPlacementRuntime.Snapshot placement) { }

    private static Control controlAndRestore(TestSonicCdR31aSelectedProfileLoading.Booted b) {
        var registry = b.mode.getRewindRegistry();
        var original = registry.capture(); // Genuine complete snapshot; never forged or projected into authority.
        TestSonicCdR31aSelectedProfileLoading.step(b, 8);
        var frame = b.module.requirePlayerFrame(); var host = b.module.r31WorldHost();
        var result = new Control(frame.state(), frame.environment(), host.globals(), List.copyOf(host.sounds()),
                b.module.r31LoadingVisit().state().reserved().copy(), host.placement().capture());
        assertEquals(0, result.placement().pool().slots().get(95).id());
        registry.restore(original);
        return result;
    }

    private static void assertOtherOwners(Control c, TestSonicCdR31aSelectedProfileLoading.Booted b, int selected) {
        var frame = b.module.requirePlayerFrame(); var host = b.module.r31WorldHost();
        var actual = host.placement().capture();
        assertEquals(c.primary(), frame.state()); assertEquals(c.environment(), frame.environment());
        assertEquals(c.globals(), host.globals()); assertEquals(c.sounds(), host.sounds());
        assertArrayEquals(c.reserved(), b.module.r31LoadingVisit().state().reserved().copy());
        assertEquals(c.placement().status(), actual.status());
        assertArrayEquals(c.placement().savedFlags().copy(), actual.savedFlags().copy());
        assertEquals(96, actual.pool().slots().size());
        for (int i = 0; i < 96; i++) if (i + 32 != selected)
            assertArrayEquals(c.placement().pool().slots().get(i).copyBytes(), actual.pool().slots().get(i).copyBytes(), "whole=" + (i + 32));
    }
}
