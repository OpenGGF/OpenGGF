package com.openggf.game.sonic2.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded ROM-free regressions for native SS RAM allocation and RunObjects ordering. */
class TestSonic2SpecialStageNativeSlots {
    @TempDir
    Path tempDir;

    @Test
    void fullDynamicRamStopsBeforeReadingEvenACheckpointMarker() throws Exception {
        Fixture f = fixture("sonic", "");
        List<Sonic2SpecialStageSlotPool.Handle> reservations = fillDynamicRam(f.pool());
        stream(f.objects(), (byte) 0xFE);

        assertTrue(f.objects().processSegment(0, 4).isEmpty());
        assertEquals(0, f.objects().captureRewindSnapshot().currentPosition());
        assertEquals(0, f.objects().getCurrentSpecialAct());
        assertNull(f.objects().checkpointMarker());

        Sonic2SpecialStageSlotPool.Handle last = reservations.getLast();
        assertTrue(f.pool().release(last));
        f.objects().processSegment(1, 4);

        assertEquals(1, f.objects().captureRewindSnapshot().currentPosition());
        assertEquals(1, f.objects().getCurrentSpecialAct());
        assertEquals(last.slot(), f.objects().checkpointMarker().slot());
        assertEquals(CHECKPOINT_MARKER, f.pool().entryAt(last.slot()).kind());
        assertFalse(f.pool().hasFreeDynamicSlot());
    }

    @Test
    void streamPointerCommitsAtMarkerRatherThanAfterAnAllocatedObject() throws Exception {
        Fixture f = fixture("sonic", "");
        List<Sonic2SpecialStageSlotPool.Handle> reservations = fillDynamicRam(f.pool());
        f.pool().release(reservations.getLast());
        stream(f.objects(), (byte) 0x05, (byte) 0x80, (byte) 0xFF);

        List<Sonic2SpecialStageObject> first = f.objects().processSegment(0, 4);
        assertEquals(1, first.size());
        assertEquals(127, first.getFirst().getNativeSlot());
        assertEquals(0, f.objects().captureRewindSnapshot().currentPosition(),
                "SSObjectsManager writes its global pointer only when it reaches a marker");

        // Leave room for the repeated object AND the allocator probe before $FF.
        f.pool().release(reservations.get(0));
        f.pool().release(reservations.get(1));
        List<Sonic2SpecialStageObject> repeated = f.objects().processSegment(1, 4);
        assertEquals(1, repeated.size());
        assertEquals(8, repeated.getFirst().getNativeSlot());
        assertEquals(3, f.objects().captureRewindSnapshot().currentPosition());
        assertEquals(2, f.objects().getPerfectRingsTotal());
        assertTrue(f.pool().hasFreeDynamicSlot(), "$FF consumes no object record");
    }

    @Test
    void earlierDeferredMarkerConsumesTheGlobalFlagSetByALaterFcMarker() throws Exception {
        Fixture f = fixture("sonic", "");
        Sonic2SpecialStageCheckpoint checkpoint = new Sonic2SpecialStageCheckpoint();
        checkpoint.attachNativeSlotPool(f.pool());
        set(f.manager(), "checkpoint", checkpoint);
        Sonic2TrackAnimator track = new Sonic2TrackAnimator(null);
        track.initializeWithMockLayout();
        set(f.manager(), "trackAnimator", track);
        set(f.manager(), "pendingCheckpoint", true);
        stream(f.objects(), (byte) 0xFE, (byte) 0xFC);
        f.objects().processSegment(0, 4);
        var lower = f.pool().entryAt(8).handle();
        f.objects().processSegment(1, 4);
        var upper = f.pool().entryAt(9).handle();
        assertTrue(f.objects().captureRewindSnapshot().noCheckpointFlag());

        invoke(f.manager(), "executeActiveSpecialStageObjects");

        assertFalse(f.pool().isLive(lower), "lower Obj5A consumes the current global flag regardless of $FE provenance");
        assertTrue(f.pool().isLive(upper), "later $FC now follows the ordinary straight-track gate");
        assertEquals(upper, checkpoint.getNativeMarkerHandle());
        assertFalse(f.objects().captureRewindSnapshot().noCheckpointFlag());
        assertTrue(f.objects().isRingsToGoEnabled());
    }

    @Test
    void reusedLowerSlotExecutesBeforeOlderOwnerAndInitFallsThroughOnlyOnce() throws Exception {
        Fixture f = fixture("sonic", "");
        Sonic2SpecialStageRing removed = addRing(f, 64, 0x80);
        Sonic2SpecialStageRing older = addRing(f, 64, 0x80);
        f.pool().release(removed.getNativeHandle());
        f.objects().getActiveObjects().remove(removed);
        Sonic2SpecialStageRing newer = addRing(f, 64, 0x80);
        List<Sonic2SpecialStageObject> execution = new ArrayList<>();

        f.objects().update(0, false, 12, true, f.manager(), entry -> { }, execution::add);

        assertEquals(List.of(newer, older), execution);
        assertEquals(List.of(8, 9), f.objects().getActiveObjects().stream()
                .map(Sonic2SpecialStageObject::getNativeSlot).toList());
        assertEquals(0x003F3334L, newer.captureRewindSnapshot().base().depthFixed());
        assertEquals(0x003F3334L, older.captureRewindSnapshot().base().depthFixed());
        tick(f);
        assertEquals(0x003E6667L, newer.captureRewindSnapshot().base().depthFixed());
        assertAccountedOwners(f);
    }

    @Test
    void teamFixedSlotsKeepPlayerChildrenAndObj88BodyMinusOnePriority() throws Exception {
        Fixture f = fixture("sonic", "tails");
        assertEntry(f.pool(), 0, SONIC, 0x09, 3);
        assertEntry(f.pool(), 1, TAILS, 0x10, 2);
        assertEntry(f.pool(), 5, SONIC_SHADOW, 0x63, 4);
        assertEntry(f.pool(), 6, TAILS_SHADOW, 0x63, 4);
        assertEntry(f.pool(), 7, TAILS_TAIL, 0x88, 1);
        assertEquals(f.pool().entryAt(0).handle(), f.pool().entryAt(5).parent());
        assertEquals(f.pool().entryAt(1).handle(), f.pool().entryAt(6).parent());
        assertEquals(f.pool().entryAt(1).handle(), f.pool().entryAt(7).parent());

        set(f.manager().getTailsPlayer(), "priority", 3);
        invoke(f.manager(), "updateNativePlayerPriorities");
        assertEntry(f.pool(), 1, TAILS, 0x10, 3);
        assertEntry(f.pool(), 7, TAILS_TAIL, 0x88, 2);
        assertEquals(List.of(0, 1, 5, 6, 7), f.pool().entries().stream()
                .map(Sonic2SpecialStageSlotPool.Entry::slot).toList());
    }

    @Test
    void tailsAloneRemainsInSidekickSlotWithShadowSixAndTailSeven() throws Exception {
        Fixture f = fixture("tails", "");
        assertNull(f.pool().entryAt(0));
        assertNull(f.pool().entryAt(5));
        assertEntry(f.pool(), 1, TAILS, 0x10, 3);
        assertEntry(f.pool(), 6, TAILS_SHADOW, 0x63, 4);
        assertEntry(f.pool(), 7, TAILS_TAIL, 0x88, 2);
        assertEquals(0xB040, f.pool().entryAt(1).address());
        assertEquals(f.pool().entryAt(1).handle(), f.pool().entryAt(7).parent());
        assertEquals(List.of(1, 6, 7), f.pool().entries().stream()
                .map(Sonic2SpecialStageSlotPool.Entry::slot).toList());
    }

    @Test
    void spillNeedsAnInitialSlotButDebitsTenWhenOnlyItsFirstOwnerFits() throws Exception {
        Fixture f = fixture("sonic", "");
        collect(f, 12);
        List<Sonic2SpecialStageSlotPool.Handle> reservations = fillDynamicRam(f.pool());
        f.objects().queueRingSpill(f.pool().entryAt(0).handle());
        tick(f);
        assertEquals(12, f.manager().getSonicPlayer().getRings());
        assertEquals(12, f.objects().getRingsCollected());
        assertTrue(f.objects().captureRewindSnapshot().spilledRings().isEmpty());

        f.pool().release(reservations.getLast());
        f.objects().queueRingSpill(f.pool().entryAt(0).handle());
        assertEquals(12, f.manager().getSonicPlayer().getRings(), "allocation itself does not debit");
        tick(f);

        assertEquals(2, f.manager().getSonicPlayer().getRings());
        assertEquals(2, f.objects().getRingsCollected());
        List<Sonic2SpecialStageSnapshot.SpilledRingSnapshot> spills =
                f.objects().captureRewindSnapshot().spilledRings();
        assertEquals(1, spills.size(), "later allocation failures do not refund the debit");
        assertEquals(127, spills.getFirst().handle().slot());
        assertEquals((long) f.manager().getSonicPlayer().getXPos() << 16, spills.getFirst().xFixed());
        assertEquals((long) f.manager().getSonicPlayer().getYPos() << 16, spills.getFirst().yFixed());
        assertEquals(5, f.pool().entry(spills.getFirst().handle()).priorityBucket());
        assertFalse(f.pool().hasFreeDynamicSlot());
    }

    @Test
    void spillInitializerStaysStillWhileItsHigherSlotChildrenRunTheirMainRoutine() throws Exception {
        Fixture f = fixture("sonic", "");
        collect(f, 10);
        f.objects().queueRingSpill(f.pool().entryAt(0).handle());
        tick(f);

        List<Sonic2SpecialStageSnapshot.SpilledRingSnapshot> spills =
                f.objects().captureRewindSnapshot().spilledRings();
        assertEquals(10, spills.size());
        long x = (long) f.manager().getSonicPlayer().getXPos() << 16;
        long y = (long) f.manager().getSonicPlayer().getYPos() << 16;
        assertEquals(x, spills.getFirst().xFixed());
        assertEquals(y, spills.getFirst().yFixed());
        assertEquals(0, spills.getFirst().mappingFrame(), "routine zero ends with RTS before AnimateSprite");
        assertFalse(spills.getFirst().displayed(), "Obj5B_Init does not call DisplaySprite");
        for (int i = 1; i < spills.size(); i++) {
            var spill = spills.get(i);
            assertEquals(8 + i, spill.handle().slot());
            assertEquals((x + ((long) spill.xVelocity() << 8)) & 0xFFFFFFFFL, spill.xFixed());
            assertEquals((y + ((long) (short) (spill.yVelocity() - 0xB8) << 8)) & 0xFFFFFFFFL,
                    spill.yFixed(), "ObjectMoveAndFall moves before adding $38 + $80 gravity");
            assertTrue(spill.mappingFrame() >= 8, "later owners execute AnimateSprite this pass");
            assertTrue(spill.displayed());
        }
        assertAccountedOwners(f);
    }

    @Test
    void spillPriorityChangesAtNonnegativeVelocityAndOwnerFreesAtScreenBoundary() throws Exception {
        Fixture f = fixture("sonic", "");
        collect(f, 1);
        f.objects().queueRingSpill(f.pool().entryAt(0).handle());
        tick(f);
        Object spill = ((List<?>) get(f.objects(), "spilledRings")).getFirst();
        var handle = f.objects().captureRewindSnapshot().spilledRings().getFirst().handle();
        set(spill, "xVelocity", 0);
        set(spill, "yVelocity", -0xB9);
        tick(f);
        assertEquals(-1, f.objects().captureRewindSnapshot().spilledRings().getFirst().yVelocity());
        assertEquals(5, f.pool().entry(handle).priorityBucket());
        tick(f);
        assertEquals(0xB7, f.objects().captureRewindSnapshot().spilledRings().getFirst().yVelocity());
        assertEquals(0, f.pool().entry(handle).priorityBucket());
        assertEquals(9, f.objects().captureRewindSnapshot().spilledRings().getFirst().mappingFrame(),
                "AnimateSprite resets to anim9 immediately at the velocity edge");

        set(spill, "xFixed", 256L << 16);
        tick(f);
        assertFalse(f.pool().isLive(handle));
        assertTrue(f.objects().nativeSpilledRings().isEmpty());
        assertTrue(f.objects().captureRewindSnapshot().spilledRings().isEmpty());
        assertAccountedOwners(f);
    }

    @Test
    void ascendingSpillAnimationLoopsFourFramesWithoutDuplicatingItsFirstFrame() throws Exception {
        Fixture f = fixture("sonic", "");
        collect(f, 1);
        f.objects().queueRingSpill(f.pool().entryAt(0).handle());
        tick(f);
        Object spill = ((List<?>) get(f.objects(), "spilledRings")).getFirst();
        set(spill, "xVelocity", 0);
        List<Integer> frames = new ArrayList<>();
        for (int frame = 0; frame < 5; frame++) {
            set(spill, "yVelocity", -0x100);
            set(spill, "animationTimer", 0);
            tick(f);
            frames.add(f.objects().captureRewindSnapshot().spilledRings().getFirst().mappingFrame());
        }
        assertEquals(List.of(8, 18, 28, 18, 8), frames,
                "byte_36318 has four frame bytes before its $FF terminator");
    }

    @Test
    void rewindRecreatesObjectAndSpillOwnersAndReplaysIdenticalSlotState() throws Exception {
        Fixture f = fixture("sonic", "");
        Sonic2SpecialStageRing original = addRing(f, 32, 0x40);
        collect(f, 3);
        f.objects().queueRingSpill(f.pool().entryAt(0).handle());
        tick(f);
        var playerBefore = f.manager().getSonicPlayer().captureRewindSnapshot();
        var before = f.objects().captureRewindSnapshot();
        List<Sonic2SpecialStageSnapshot.ObjectManagerSnapshot> expected = new ArrayList<>();
        for (int frame = 0; frame < 5; frame++) {
            tick(f);
            expected.add(f.objects().captureRewindSnapshot());
        }
        original.markForRemoval();
        f.manager().getSonicPlayer().restoreRewindSnapshot(playerBefore);
        f.objects().restoreRewindSnapshot(before, f.manager());
        Sonic2SpecialStageObject restored = f.objects().getActiveObjects().getFirst();
        assertNotSame(original, restored);
        assertEquals(original.getNativeHandle(), restored.getNativeHandle());
        assertEquals(before.nativeSlots().entries(), f.pool().entries());
        assertAccountedOwners(f);
        for (int frame = 0; frame < expected.size(); frame++) {
            tick(f);
            var actual = f.objects().captureRewindSnapshot();
            assertEquals(expected.get(frame).activeObjects(), actual.activeObjects());
            assertEquals(expected.get(frame).spilledRings(), actual.spilledRings());
            assertEquals(expected.get(frame).nativeSlots().entries(), actual.nativeSlots().entries());
            assertArrayEquals(expected.get(frame).nativeSlots().generations(), actual.nativeSlots().generations());
            assertAccountedOwners(f);
        }
    }

    @Test
    void immutableRenderQueryAccountsForFixedFoldedAndDynamicOwnersAcrossRestore() throws Exception {
        Fixture f = fixture("sonic", "tails");
        Sonic2SpecialStageIntro intro = new Sonic2SpecialStageIntro();
        intro.attachNativeSlotPool(f.pool());
        intro.initialize(0, 30);
        intro.initializeNativeFixedOwners(true, false);
        intro.initializeRingsToGoOwners();
        intro.initializeNativeFixedOwnerChildren();
        set(f.manager(), "intro", intro);
        Sonic2SpecialStageRing original = addRing(f, 32, 0x40);
        f.objects().update(0, false, 12, true, f.manager(), intro::executeNativeChildSlot, object -> { });

        var render = f.manager().getNativeRenderStates();
        assertEquals(f.pool().entries(), render.stream().map(Sonic2SpecialStageSlotPool.RenderState::identity).toList());
        assertEquals(11, render.stream().filter(state -> state.identity().kind() == RINGS_TO_GO_NUMBER
                || state.identity().kind() == RINGS_TO_GO_LETTER).count());
        assertEquals(1, render.stream().filter(state -> state.identity().kind() == TOTAL_COUNTER).count());
        assertEquals(1, render.stream().filter(state -> state.identity().kind() == OBJECT_SHADOW).count());
        assertThrows(UnsupportedOperationException.class, render::clear);
        var snapshot = f.manager().captureRewindSnapshot();
        original.markForRemoval();
        tick(f);
        f.manager().restoreRewindSnapshot(snapshot);
        assertNotSame(original, f.objects().getActiveObjects().getFirst());
        assertEquals(render, f.manager().getNativeRenderStates());
        assertEquals(snapshot.nativeSlots.entries(), f.pool().entries());
    }

    @Test
    void streamClearsMessageSuppressionBeforeTheEarlierNumberOwnersTurn() throws Exception {
        Fixture f = fixture("sonic", "");
        Sonic2SpecialStageIntro intro = new Sonic2SpecialStageIntro();
        intro.attachNativeSlotPool(f.pool());
        intro.initialize(0, 101);
        intro.initializeNativeFixedOwners(false, false);
        intro.initializeRingsToGoOwners();
        intro.initializeNativeFixedOwnerChildren();
        intro.setNativeCheckpointMessageHidden(true);
        set(f.manager(), "intro", intro);
        set(f.manager(), "nativeRingRequirement", 101);
        set(f.manager(), "currentRingRequirement", 200);
        set(f.objects(), "ringsToGoEnabled", true);
        set(f.objects(), "noCheckpointMsgFlag", true);
        Sonic2TrackAnimator track = new Sonic2TrackAnimator(null);
        track.initializeWithMockLayout();
        set(f.manager(), "trackAnimator", track);
        stream(f.objects(), (byte) 0xFC);
        f.objects().processSegment(0, 4);
        assertFalse(f.objects().captureRewindSnapshot().noCheckpointMsgFlag());

        invoke(f.manager(), "executeActiveSpecialStageObjects");

        assertFalse(intro.captureRewindSnapshot().nativeCheckpointMessageHidden());
        assertEquals(0x02F7, intro.getNativeRingsToGoBcd(),
                "number owner uses captured native requirement101 until HAND advances it, preserving retail BCD");
        assertEquals(3, intro.getNativeRingsToGoChildCount());
    }

    @Test
    void successfulHandExpiryOwnsNativeRequirementAndTriggerWrites() throws Exception {
        Fixture f = fixture("sonic", "");
        Sonic2SpecialStageIntro intro = new Sonic2SpecialStageIntro();
        intro.attachNativeSlotPool(f.pool());
        intro.initialize(0, 30);
        intro.initializeNativeFixedOwners(false, false);
        intro.initializeRingsToGoOwners();
        intro.initializeNativeFixedOwnerChildren();
        set(f.manager(), "intro", intro);
        set(f.manager(), "nativeRingRequirement", 30);
        set(f.manager(), "currentRingRequirement", 50);
        set(f.objects(), "ringsToGoEnabled", true);
        Sonic2TrackAnimator track = new Sonic2TrackAnimator(null);
        track.initializeWithMockLayout();
        set(f.manager(), "trackAnimator", track);
        set(f.manager(), "drawingIndex", 4);
        Sonic2SpecialStageCheckpoint checkpoint = new Sonic2SpecialStageCheckpoint();
        set(f.manager(), "checkpoint", checkpoint);
        invoke(f.manager(), "bindNativeCheckpointTopology");
        var marker = f.pool().allocate(CHECKPOINT_MARKER, 0x5A, 0, 0xFE, null);
        checkpoint.queueNativeMarker(marker);
        checkpoint.beginCheckpoint(1, 0, 0, false);
        for (int frame = 0; frame < 10; frame++) {
            invoke(f.manager(), "executeActiveSpecialStageObjects");
        }
        var hand = checkpoint.getNativeHandHandle();
        assertTrue(f.pool().isLive(hand));
        assertEquals(30, get(f.manager(), "nativeRingRequirement"));
        assertTrue(f.objects().isRingsToGoEnabled());
        for (int frame = 0; frame < 100 && f.pool().isLive(hand); frame++) {
            invoke(f.manager(), "executeActiveSpecialStageObjects");
        }
        assertFalse(f.pool().isLive(hand));
        assertEquals(50, get(f.manager(), "nativeRingRequirement"));
        assertFalse(f.objects().isRingsToGoEnabled(), "HAND writes SS_TriggerRingsToGo on its expiry");
        assertEquals(11, intro.getMessageLetters().size());
    }

    private Fixture fixture(String main, String sidekick) throws Exception {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager(
                new Sonic2SpecialStageSpriteDebug(), config, null);
        manager.setupPlayersForTest();
        Sonic2SpecialStageSlotPool pool = (Sonic2SpecialStageSlotPool) get(manager, "nativeSlotPool");
        var objects = new Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager(null, pool);
        set(manager, "objectManager", objects);
        return new Fixture(manager, objects, pool);
    }

    private static Sonic2SpecialStageRing addRing(Fixture f, int depth, int angle) {
        Sonic2SpecialStageRing ring = new Sonic2SpecialStageRing();
        ring.initialize(depth, angle);
        ring.attachNativeSlot(f.pool(), f.pool().allocate(RING, 0x60, 0, 0, null));
        f.objects().getActiveObjects().add(ring);
        return ring;
    }

    private static void stream(Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager objects,
                               byte... bytes) throws Exception {
        set(objects, "objectLocationData", bytes);
        set(objects, "currentPosition", 0);
    }

    private static List<Sonic2SpecialStageSlotPool.Handle> fillDynamicRam(Sonic2SpecialStageSlotPool pool) {
        List<Sonic2SpecialStageSlotPool.Handle> handles = new ArrayList<>();
        while (pool.hasFreeDynamicSlot()) {
            handles.add(pool.allocate(MESSAGE_LETTER, 0x5A, 1, 0, null));
        }
        return handles;
    }

    private static void collect(Fixture f, int rings) {
        for (int i = 0; i < rings; i++) {
            f.objects().collectRing(f.manager().getSonicPlayer());
        }
    }

    private static void tick(Fixture f) {
        f.objects().update(0, false, 12, false, f.manager(), entry -> { }, object -> { });
    }

    private static void assertEntry(Sonic2SpecialStageSlotPool pool, int slot,
                                    Sonic2SpecialStageSlotPool.Kind kind, int objectId, int priority) {
        var entry = pool.entryAt(slot);
        assertNotNull(entry);
        assertEquals(kind, entry.kind());
        assertEquals(objectId, entry.objectId());
        assertEquals(priority, entry.priorityBucket());
    }

    private static void assertAccountedOwners(Fixture f) {
        Set<Sonic2SpecialStageSlotPool.Handle> owners = new HashSet<>();
        for (var entry : f.pool().entries()) {
            if (entry.slot() < 8) {
                assertTrue(owners.add(entry.handle()));
            }
        }
        for (var object : f.objects().getActiveObjects()) {
            assertTrue(f.pool().isLive(object.getNativeHandle()));
            assertTrue(owners.add(object.getNativeHandle()));
            if (object.getNativeShadowHandle() != null) {
                assertTrue(f.pool().isLive(object.getNativeShadowHandle()));
                assertEquals(object.getNativeHandle(), f.pool().entry(object.getNativeShadowHandle()).parent());
                assertTrue(owners.add(object.getNativeShadowHandle()));
            }
        }
        for (var spill : f.objects().captureRewindSnapshot().spilledRings()) {
            assertTrue(f.pool().isLive(spill.parent()));
            assertTrue(f.pool().isLive(spill.handle()));
            assertTrue(owners.add(spill.handle()));
        }
        assertEquals(new HashSet<>(f.pool().entries().stream()
                .map(Sonic2SpecialStageSlotPool.Entry::handle).toList()), owners,
                "every occupied record must belong to one live native owner");
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private record Fixture(Sonic2SpecialStageManager manager,
                           Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager objects,
                           Sonic2SpecialStageSlotPool pool) {
    }
}
