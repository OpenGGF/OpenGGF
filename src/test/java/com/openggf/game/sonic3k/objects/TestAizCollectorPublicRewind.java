package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestAizCollectorPublicRewind {
    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void publicEmeraldRoundTripsPreserveTheCurrentCollector(boolean explicitNone, boolean initiallyBound) throws Exception {
        var emerald = new AizEmeraldScatterInstance(spawn());
        var original = new CutsceneKnucklesAiz1Instance(spawn());
        if (initiallyBound) emerald.setKnuckles(original);
        PerObjectRewindSnapshot saved = assertDoesNotThrow(() -> capture(emerald, explicitNone));
        var divergent = new CutsceneKnucklesAiz1Instance(spawn());
        emerald.setKnuckles(divergent);
        emerald.landOnGround(0x500);
        restore(emerald, saved, explicitNone);
        assertSame(divergent, field(emerald, "knuckles").get(emerald));
        assertEquals(AizEmeraldScatterInstance.Phase.FALLING, emerald.getPhase(), "scalar rewind still executes");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void publicIntroBeforeInitRoundTripsWithoutACollector(boolean explicitNone) {
        var intro = new AizPlaneIntroInstance(spawn());
        var saved = assertDoesNotThrow(() -> capture(intro, explicitNone));
        intro.advanceRoutine();
        restore(intro, saved, explicitNone);
        assertEquals(0, intro.getRoutine(), "before-init is a non-discriminating null-link control");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void publicIntroDuringPlayerOwnershipStillRequiresPlayerIdentityContext(boolean explicitNone) {
        var config = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try {
            var fixture = fixture();
            fixture.stepFrame(false, false, false, false, false);
            var intro = parent();
            assertTrue(fixture.sprite().isObjectControlled());
            assertThrows(IllegalStateException.class, () -> capture(intro, explicitNone),
                    "the acquired player was already strict before the collector-policy change");
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(config);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void publicReleasedIntroWithLiveKnucklesPreservesTheCurrentCollector(boolean explicitNone) throws Exception {
        var config = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try {
            var fixture = fixture();
            AizPlaneIntroInstance intro = reach(fixture, 24);
            var collector = (CutsceneKnucklesAiz1Instance) field(intro, "knuckles").get(intro);
            assertNotNull(collector);
            assertFalse(collector.isDestroyed());
            intro.releasePlayersForVisualDirection();
            assertNull(field(intro, "ownedPlayer").get(intro));
            PerObjectRewindSnapshot saved = assertDoesNotThrow(() -> capture(intro, explicitNone));
            var divergent = new CutsceneKnucklesAiz1Instance(spawn());
            field(intro, "knuckles").set(intro, divergent);
            intro.advanceRoutine();
            restore(intro, saved, explicitNone);
            assertEquals(24, intro.getRoutine());
            assertSame(divergent, field(intro, "knuckles").get(intro));
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(config);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void collectorAdmissionIsTruthfulBeforeAnyChildDispatch(boolean exhaustSlots) throws Exception {
        var config = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try {
            var fixture = fixture();
            AizPlaneIntroInstance intro = reach(fixture, 22);
            ObjectManager manager = GameServices.level().getObjectManager();
            assertTrue(manager.activeObjectsOfType(CutsceneKnucklesAiz1Instance.class).isEmpty());
            manager.reserveAllButNFreeSlots(exhaustSlots ? 0 : 1);
            ObjectManagerSnapshot before = manager.rewindSnapshottable().capture();
            fixture.sprite().setCentreX((short) 0x918);
            int vIntRunCount = (int) field(intro, "lastFrameCounter").get(intro) + 1;
            intro.update(vIntRunCount, fixture.sprite());
            assertEquals(24, intro.getRoutine(), "allocation failure must still advance the native intro routine");
            assertFalse(intro.isDestroyed());
            assertTrue(fixture.sprite().isObjectControlled());
            var retained = (CutsceneKnucklesAiz1Instance) field(intro, "knuckles").get(intro);
            if (exhaustSlots) {
                assertTrue(manager.activeObjectsOfType(CutsceneKnucklesAiz1Instance.class).isEmpty());
                assertTrue(retained == null || retained.isDestroyed(), "failed real admission destroys the unregistered candidate");
                assertAll(
                        () -> assertNull(field(intro, "knuckles").get(intro), "the owner must not retain a rejected collector"),
                        () -> assertDoesNotThrow(() -> manager.rewindSnapshottable().capture(),
                                "capture before child dispatch must not contain an unregistered collector"));
            } else {
                assertNotNull(retained);
                assertFalse(retained.isDestroyed());
                assertEquals(0, retained.getRoutine(), "capture precedes this child's first dispatch");
                assertEquals(List.of(retained), manager.activeObjectsOfType(CutsceneKnucklesAiz1Instance.class));
                assertNotNull(manager.captureIdentityContext().requireIdentityTable().idFor(retained));
            }
            ObjectManagerSnapshot after = assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
            assertEquals(BitSet.valueOf(before.usedSlotsBits()).cardinality() + (exhaustSlots ? 0 : 1),
                    BitSet.valueOf(after.usedSlotsBits()).cardinality(), "admission consumes one slot on success and none on rejection");
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(config);
        }
    }

    private static HeadlessTestFixture fixture() {
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        GameServices.configuration().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        return HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
    }
    private static AizPlaneIntroInstance reach(HeadlessTestFixture fixture, int routine) {
        AizPlaneIntroInstance intro = null;
        for (int i = 0; i < 1800; i++) {
            fixture.stepFrame(false, false, false, false, false);
            intro = parent();
            if (intro.getRoutine() == routine) return intro;
        }
        fail("native intro never reached routine " + routine);
        return intro;
    }
    private static AizPlaneIntroInstance parent() {
        return GameServices.level().getObjectManager().activeObjectsOfType(AizPlaneIntroInstance.class).getFirst();
    }
    private static PerObjectRewindSnapshot capture(AbstractObjectInstance object, boolean explicitNone) {
        return explicitNone ? object.captureRewindState(RewindCaptureContext.none()) : object.captureRewindState();
    }
    private static void restore(AbstractObjectInstance object, PerObjectRewindSnapshot saved, boolean explicitNone) {
        if (explicitNone) object.restoreRewindState(saved, RewindCaptureContext.none());
        else object.restoreRewindState(saved);
    }
    private static ObjectSpawn spawn() { return new ObjectSpawn(0x100, 0x400, 0, 0, 0, false, 0); }
    private static Field field(Object object, String name) throws NoSuchFieldException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
