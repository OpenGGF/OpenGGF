package com.openggf.game.rewind;

import com.openggf.game.GameId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.MonitorContentsObjectInstance;
import com.openggf.game.sonic2.objects.MonitorObjectInstance;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic2.objects.badniks.BadnikProjectileInstance;
import com.openggf.game.sonic2.objects.badniks.BuzzerBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.CoconutsBadnikInstance;
import com.openggf.game.sonic2.objects.badniks.MasherBadnikInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Green-only inventory scaffold for Sonic 2 live-object rewind regressions.
 *
 * <p>The Sonic 2 badnik package currently has four legacy owners that override the
 * no-context {@code captureRewindState()} / {@code restoreRewindState()} pair:
 *
 * <ul>
 *   <li>{@link MasherBadnikInstance}: concrete badnik. It owns a second movement
 *       representation, a {@code SubpixelMotion.State}, plus the jump origin
 *       {@code initialYPos}; both are separate from the inherited
 *       {@code currentX/currentY/xVelocity/yVelocity} view.</li>
 *   <li>{@link BuzzerBadnikInstance}: concrete badnik. Its body has no second
 *       movement representation; it uses only the inherited
 *       {@code currentX/currentY/xVelocity/yVelocity} fields. Its nested flame child
 *       has child-local coordinates, but that is a separate rewind owner.</li>
 *   <li>{@link CoconutsBadnikInstance}: concrete badnik. It has no subpixel or
 *       anchor/origin position copy and uses inherited {@code currentX/currentY}; it
 *       does intentionally shadow the inherited {@code yVelocity} with a local field.</li>
 *   <li>{@link BadnikProjectileInstance}: projectile base for concrete children
 *       nested under their owning badniks; the concrete projectile kinds are selected
 *       by its {@code ProjectileType} enum. It owns
 *       projectile-local {@code currentX/currentY/xVelocity/yVelocity} and a second
 *       {@code SubpixelMotion.State} representation. Badniks create these concrete
 *       projectile children through this common class rather than Java subclasses.</li>
 * </ul>
 *
 * <p>The reported "Snapper fish" maps to Masher in this repository: a repository
 * search contains no Snapper object or class, while EHZ object ID {@code 0x5C} is
 * {@link Sonic2ObjectIds#MASHER} and {@link Sonic2ObjectRegistry} registers that ID as
 * {@link MasherBadnikInstance}. Masher is therefore the concrete Snapper-report
 * reproduction target.
 */
class TestSonic2LiveObjectRewindRegressions {

    private static final TouchResponseResult MONITOR_TOUCH =
            new TouchResponseResult(0, 0x0E, 0x0E, TouchCategory.SPECIAL);

    @Test
    void monitorBreakRoundTripRestoresIntactStateByForcedReconstruction() {
        verifyMonitorBreakRoundTrip(false);
    }

    @Test
    void monitorBreakRoundTripRestoresIntactStateByWarmedInPlaceReuse() {
        verifyMonitorBreakRoundTrip(true);
    }

    @Test
    void legacyOwnersAndSnapperMappingRemainConcrete() {
        List<Class<?>> legacyOwners = List.of(
                MasherBadnikInstance.class,
                BuzzerBadnikInstance.class,
                CoconutsBadnikInstance.class,
                BadnikProjectileInstance.class);

        assertEquals(0x5C, Sonic2ObjectIds.MASHER);
        ObjectSpawn masherSpawn = new ObjectSpawn(0x578, 0x2D0, Sonic2ObjectIds.MASHER,
                0, 0, false, 0);
        assertInstanceOf(MasherBadnikInstance.class, new Sonic2ObjectRegistry().create(masherSpawn));

        assertTrue(legacyOwners.subList(0, 3).stream()
                .allMatch(AbstractBadnikInstance.class::isAssignableFrom));
        assertTrue(legacyOwners.stream().allMatch(owner -> {
            try {
                owner.getDeclaredMethod("captureRewindState");
                owner.getDeclaredMethod("restoreRewindState", PerObjectRewindSnapshot.class);
                return true;
            } catch (NoSuchMethodException exception) {
                return false;
            }
        }));
    }

    private static void verifyMonitorBreakRoundTrip(boolean warmedReuse) {
        RewindRoundTripHarness harness = RewindRoundTripHarness.buildPlaced(GameId.S2, 0x26);
        var adapter = harness.objectManager().rewindSnapshottable();
        MonitorObjectInstance monitor = monitor(harness);
        monitor.update(0, null);

        if (warmedReuse) {
            ObjectManagerSnapshot preliminary = adapter.capture();
            adapter.restore(preliminary);
            monitor = monitor(harness);
            monitor.update(0, null);
        }

        ObjectManagerSnapshot intact = adapter.capture();
        MonitorObjectInstance intactIdentity = monitor;
        DummyPlayer player = rollingPlayer();
        monitor.onTouchResponse(player, MONITOR_TOUCH, 1);

        assertEquals(0, monitor.getCollisionFlags());
        assertTrue(isBroken(monitor));
        ObjectManagerSnapshot broken = adapter.capture();
        assertFalse(Arrays.equals(intact.placement().rememberedBits(),
                broken.placement().rememberedBits()));
        assertHasLive(harness, MonitorContentsObjectInstance.class);
        assertHasLive(harness, ExplosionObjectInstance.class);

        harness.objectManager().setRewindInPlaceRestoreEnabledForTest(warmedReuse);
        adapter.restore(intact);
        MonitorObjectInstance restored = monitor(harness);
        if (warmedReuse && com.openggf.level.objects.ObjectManager
                .isRewindInPlaceReuseSafeClass(MonitorObjectInstance.class)) {
            assertSame(intactIdentity, restored, "warmed eligible monitor should be reused in place");
        } else {
            assertNotSame(intactIdentity, restored, "forced restore should reconstruct the monitor");
        }

        assertIntactOracle(harness, restored);
        ObjectManagerSnapshot recaptured = adapter.capture();
        assertPlacementEquals(intact.placement(), recaptured.placement());
        assertArrayEquals(intact.usedSlotsBits(), recaptured.usedSlotsBits());
        assertEquals(intact.slots(), recaptured.slots());
        assertEquals(intact.dynamicObjects(), recaptured.dynamicObjects());
        assertTrue(RewindSnapshotDiff.diffKey("object-manager", intact, recaptured).isEmpty(),
                () -> RewindSnapshotDiff.diffKey("object-manager", intact, recaptured).toString());

        harness.objectManager().update(0, player, null, 2, false);
        assertIntactOracle(harness, monitor(harness));
    }

    private static DummyPlayer rollingPlayer() {
        DummyPlayer player = new DummyPlayer();
        player.setAnimationId(Sonic2AnimationIds.ROLL);
        player.setRolling(true);
        player.setYSpeed((short) 0x0120);
        return player;
    }

    private static MonitorObjectInstance monitor(RewindRoundTripHarness harness) {
        return harness.objectManager().getActiveObjects().stream()
                .filter(MonitorObjectInstance.class::isInstance)
                .map(MonitorObjectInstance.class::cast)
                .filter(object -> !object.isDestroyed())
                .findFirst().orElseThrow();
    }

    private static void assertIntactOracle(RewindRoundTripHarness harness,
            MonitorObjectInstance monitor) {
        assertFalse(isBroken(monitor));
        assertEquals(0x46, monitor.getCollisionFlags());
        assertNoLive(harness, MonitorContentsObjectInstance.class);
        assertNoLive(harness, ExplosionObjectInstance.class);
    }

    private static void assertHasLive(RewindRoundTripHarness harness, Class<?> type) {
        assertTrue(harness.objectManager().getActiveObjects().stream()
                .anyMatch(object -> type.isInstance(object) && !object.isDestroyed()));
    }

    private static void assertNoLive(RewindRoundTripHarness harness, Class<?> type) {
        assertFalse(harness.objectManager().getActiveObjects().stream()
                .anyMatch(object -> type.isInstance(object) && !object.isDestroyed()));
    }

    private static void assertPlacementEquals(ObjectManagerSnapshot.PlacementSnapshot expected,
            ObjectManagerSnapshot.PlacementSnapshot actual) {
        assertArrayEquals(expected.activeSpawnIndices(), actual.activeSpawnIndices());
        assertArrayEquals(expected.rememberedBits(), actual.rememberedBits());
        assertArrayEquals(expected.stayActiveBits(), actual.stayActiveBits());
        assertArrayEquals(expected.destroyedInWindowBits(), actual.destroyedInWindowBits());
        assertArrayEquals(expected.dormantBits(), actual.dormantBits());
        assertEquals(expected.cursorIndex(), actual.cursorIndex());
        assertEquals(expected.lastCameraX(), actual.lastCameraX());
        assertEquals(expected.lastCameraChunk(), actual.lastCameraChunk());
        assertEquals(expected.counterBasedRespawn(), actual.counterBasedRespawn());
        assertEquals(expected.execThenLoadPlacement(), actual.execThenLoadPlacement());
        assertEquals(expected.permanentDestroyLatch(), actual.permanentDestroyLatch());
        assertEquals(expected.maxDynamicSlots(), actual.maxDynamicSlots());
        assertEquals(expected.lastScrollBackward(), actual.lastScrollBackward());
        assertEquals(expected.leftCursorIndex(), actual.leftCursorIndex());
        assertEquals(expected.fwdCounter(), actual.fwdCounter());
        assertEquals(expected.bwdCounter(), actual.bwdCounter());
        assertArrayEquals(expected.objState(), actual.objState());
        assertEquals(expected.spawnCounters(), actual.spawnCounters());
        assertArrayEquals(expected.pendingCursorLoadBits(), actual.pendingCursorLoadBits());
        assertArrayEquals(expected.pendingCursorLoadOrder(), actual.pendingCursorLoadOrder());
        assertArrayEquals(expected.deferredVerticalLoadBits(), actual.deferredVerticalLoadBits());
        assertEquals(expected.twoAxisCameraYCoarse(), actual.twoAxisCameraYCoarse());
        assertEquals(expected.s2LatchedCameraX(), actual.s2LatchedCameraX());
    }

    private static boolean isBroken(MonitorObjectInstance monitor) {
        try {
            Field field = MonitorObjectInstance.class.getDeclaredField("broken");
            field.setAccessible(true);
            return field.getBoolean(monitor);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static final class DummyPlayer extends AbstractPlayableSprite {
        private DummyPlayer() {
            super("sonic", (short) 0x0100, (short) 0x0100);
        }

        @Override protected void defineSpeeds() {
            runAccel = 0;
            runDecel = 0;
            friction = 0;
            max = 0;
            jump = 0;
            angle = 0;
            slopeRunning = 0;
            slopeRollingDown = 0;
            slopeRollingUp = 0;
            rollDecel = 0;
            minStartRollSpeed = 0;
            minRollSpeed = 0;
            maxRoll = 0;
            rollHeight = 0;
            runHeight = 0;
        }

        @Override protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override public void draw() {
        }
    }
}
