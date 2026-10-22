package com.openggf.game.sonic2.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Short native Obj87 owner-turn regressions; no complete route or renderer is required. */
class TestSonic2SpecialStageCounterSlots {
    @TempDir
    Path tempDir;

    @Test
    void realRingCollisionBetweenFixedAndTotalTurnsLatchesDifferentCountsAndReplaysAfterRewind()
            throws Exception {
        Fixture f = collisionFixture();
        for (int i = 0; i < 9; i++) {
            f.objects().collectRing(f.manager().getSonicPlayer());
        }
        assertEquals(0x6E, f.manager().getSonicPlayer().getSSZPos());
        assertEquals(0x80, f.manager().getTailsPlayer().getSSZPos());
        assertEquals(8, f.ring().getNativeSlot());
        assertEquals(20, f.intro().getNativeCounterChildHandle().slot());
        var fixed = f.intro().getNativePlayerCounterHandle();
        var total = f.intro().getNativeCounterChildHandle();
        var beforePayloads = f.intro().getNativeCounterPayloads();
        Sonic2SpecialStageSnapshot before = f.manager().captureRewindSnapshot();

        // One production RunObjects pass, split at the existing fixed/dynamic boundary.
        invoke(f.manager(), "executeNativePlayerCounter");
        assertEquals(9, f.manager().getSonicPlayer().getRings());
        assertEquals(List.of(digit(0x48, 9), digit(0xE0, 0)),
                f.intro().getNativeCounterPayload(fixed).digits());
        invoke(f.manager(), "executeActiveSpecialStageObjects");

        assertEquals(Sonic2SpecialStageObject.State.COLLECTED, f.ring().getState());
        assertEquals(10, f.manager().getSonicPlayer().getRings());
        assertEquals(0, f.manager().getTailsPlayer().getRings());
        assertEquals(10, f.objects().getRingsCollected());
        assertEquals(List.of(digit(0x48, 9), digit(0xE0, 0)),
                f.intro().getNativeCounterPayload(fixed).digits(),
                "slot4 retains the two-player group captured before the ring owner ran");
        assertEquals(List.of(digit(0x7C, 1), digit(0x84, 0)),
                f.intro().getNativeCounterPayload(total).digits(),
                "later total20 consumes the ring collection from slot8 in this same pass");
        assertEquals(2, f.intro().getNativeCounterPayloads().size(),
                "digits remain pieces of two native owners rather than independent slots");
        assertTrue(f.intro().getNativeCounterPayload(fixed).displayed());
        assertTrue(f.intro().getNativeCounterPayload(total).displayed());
        var expectedPayloads = f.intro().getNativeCounterPayloads();
        var expectedObjects = f.objects().captureRewindSnapshot().activeObjects();
        var expectedSlots = f.pool().snapshot();

        f.objects().collectRing(f.manager().getSonicPlayer());
        f.intro().setNativeCounterInputs(hud(true, true, 777, 888, 1665));
        for (int query = 0; query < 3; query++) {
            assertEquals(expectedPayloads, f.intro().getNativeCounterPayloads(),
                    "queries must not derive digits from changed current counts or pending inputs");
            assertEquals(expectedPayloads.getFirst(), f.intro().getNativeCounterPayload(fixed));
            f.manager().getNativeRenderStates();
        }
        assertEquals(expectedSlots.entries(), f.pool().entries());
        assertArrayEquals(expectedSlots.generations(), f.pool().snapshot().generations());
        assertThrows(UnsupportedOperationException.class, () -> expectedPayloads.clear());
        assertThrows(UnsupportedOperationException.class, () -> expectedPayloads.getFirst().digits().clear());

        f.manager().restoreRewindSnapshot(before);
        assertEquals(beforePayloads, f.intro().getNativeCounterPayloads());
        assertEquals(9, f.manager().getSonicPlayer().getRings());
        Sonic2SpecialStageObject restoredRing = f.objects().getActiveObjects().getFirst();
        assertNotSame(f.ring(), restoredRing);
        assertEquals(Sonic2SpecialStageObject.State.ACTIVE, restoredRing.getState());
        invoke(f.manager(), "executeNativePlayerCounter");
        invoke(f.manager(), "executeActiveSpecialStageObjects");

        assertEquals(Sonic2SpecialStageObject.State.COLLECTED, restoredRing.getState());
        assertEquals(10, f.manager().getSonicPlayer().getRings());
        assertEquals(10, f.objects().getRingsCollected());
        assertEquals(expectedPayloads, f.intro().getNativeCounterPayloads());
        assertEquals(expectedObjects, f.objects().captureRewindSnapshot().activeObjects());
        assertEquals(expectedSlots.entries(), f.pool().entries());
        assertArrayEquals(expectedSlots.generations(), f.pool().snapshot().generations());
    }

    @Test
    void startupFixedCounterInitDoesNotDisplayButLaterTotalOwnerDoes() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, true);
        intro.setNativeCounterInputs(hud(true, true, 9, 1, 10));
        intro.initializeRingsToGoOwners();
        intro.initializeNativeFixedOwnerChildren();
        var fixed = intro.getNativePlayerCounterHandle();
        var total = intro.getNativeCounterChildHandle();
        assertEquals(4, fixed.slot());
        assertEquals(19, total.slot());
        assertFalse(intro.getNativeCounterPayload(fixed).displayed(), "Obj87_Init ends with RTS");
        assertFalse(intro.getNativeCounterPayload(total).displayed(), "the allocated child has not run yet");

        assertTrue(intro.executeNativeChildSlot(pool.entry(total)));

        assertFalse(intro.getNativeCounterPayload(fixed).displayed());
        assertTrue(intro.getNativeCounterPayload(total).displayed());
        assertEquals(List.of(digit(0x7C, 1), digit(0x84, 0)),
                intro.getNativeCounterPayload(total).digits());
        assertEquals(0, pool.entry(fixed).priorityBucket());
        assertEquals(0, pool.entry(total).priorityBucket());
        assertFalse(intro.nativeRenderStates().stream()
                .filter(state -> state.identity().handle().equals(fixed))
                .findFirst().orElseThrow().visible());
        assertTrue(intro.nativeRenderStates().stream()
                .filter(state -> state.identity().handle().equals(total))
                .findFirst().orElseThrow().visible());
    }

    @Test
    void eachCounterLatchesOnlyItsOwnTurnWithNativeLeadingZeroSuppressionAndPieceOrder() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, true);
        intro.initializeNativeFixedOwnerChildren();
        var fixed = intro.getNativePlayerCounterHandle();
        var total = intro.getNativeCounterChildHandle();
        intro.setNativeCounterInputs(hud(true, true, 0, 100, 100));
        intro.executeNativeChildSlot(pool.entry(fixed));
        intro.executeNativeChildSlot(pool.entry(total));
        var fixedBefore = intro.getNativeCounterPayload(fixed);
        assertEquals(List.of(digit(0x48, 0), digit(0xE0, 1), digit(0xE8, 0), digit(0xF0, 0)),
                fixedBefore.digits(), "Sonic pieces precede Tails pieces within the single slot4 owner");
        assertEquals(List.of(digit(0x78, 1), digit(0x80, 0), digit(0x88, 0)),
                intro.getNativeCounterPayload(total).digits());

        intro.setNativeCounterInputs(hud(true, true, 9, 1, 10));
        intro.executeNativeChildSlot(pool.entry(total));
        assertEquals(fixedBefore, intro.getNativeCounterPayload(fixed));
        assertEquals(List.of(digit(0x7C, 1), digit(0x84, 0)),
                intro.getNativeCounterPayload(total).digits());
        intro.executeNativeChildSlot(pool.entry(fixed));
        assertEquals(List.of(digit(0x48, 9), digit(0xE0, 1)),
                intro.getNativeCounterPayload(fixed).digits());
    }

    @Test
    void totalAddsCachedDecimalColumnsWithoutClampingRawHundredsToNine() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, true);
        intro.initializeNativeFixedOwnerChildren();
        // The unrelated aggregate deliberately differs: loc_753E reads the two cached player digit sets.
        intro.setNativeCounterInputs(hud(true, true, 999, 1, 17));
        var total = intro.getNativeCounterChildHandle();
        intro.executeNativeChildSlot(pool.entry(total));
        assertEquals(List.of(digit(0x78, 10), digit(0x80, 0), digit(0x88, 0)),
                intro.getNativeCounterPayload(total).digits());

        intro.setNativeCounterInputs(hud(true, true, 1000, 0, 1000));
        intro.executeNativeChildSlot(pool.entry(intro.getNativePlayerCounterHandle()));
        assertEquals(List.of(digit(0x48, 10), digit(0x50, 0), digit(0x58, 0), digit(0xE0, 0)),
                intro.getNativeCounterPayload(intro.getNativePlayerCounterHandle()).digits());
    }

    @Test
    void sonicSoloAndTailsAloneUseTheSameNativeSoloDigitAnchor() {
        for (boolean tailsAlone : new boolean[] {false, true}) {
            Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
            Sonic2SpecialStageIntro intro = intro(pool, false);
            intro.initializeNativeFixedOwnerChildren();
            intro.setNativeCounterInputs(tailsAlone
                    ? hud(false, true, 777, 42, 42) : hud(true, false, 42, 777, 42));
            var fixed = intro.getNativePlayerCounterHandle();
            intro.executeNativeChildSlot(pool.entry(fixed));
            assertEquals(List.of(digit(0x9C, 4), digit(0xA4, 2)),
                    intro.getNativeCounterPayload(fixed).digits());
            assertNull(intro.getNativeCounterChildHandle());
            assertEquals(1, intro.getNativeCounterPayloads().size());
        }
    }

    private Fixture collisionFixture() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        Sonic2SpecialStageManager manager = new Sonic2SpecialStageManager(
                new Sonic2SpecialStageSpriteDebug(), config, null);
        manager.setupPlayersForTest();
        Sonic2SpecialStageSlotPool pool = (Sonic2SpecialStageSlotPool) get(manager, "nativeSlotPool");
        var objects = new Sonic2SpecialStageManager.Sonic2SpecialStageObjectManager(null, pool);
        set(manager, "objectManager", objects);
        Sonic2SpecialStageRing ring = new Sonic2SpecialStageRing();
        ring.initialize(4, 0x40);
        ring.attachNativeSlot(pool, pool.allocate(RING, 0x60, 0, 0, null));
        objects.getActiveObjects().add(ring);
        Sonic2SpecialStageIntro intro = intro(pool, true);
        intro.initializeRingsToGoOwners();
        intro.initializeNativeFixedOwnerChildren();
        set(intro, "currentPhase", Sonic2SpecialStageIntro.Phase.GAMEPLAY);
        set(manager, "intro", intro);
        Sonic2TrackAnimator track = new Sonic2TrackAnimator(null);
        track.initializeWithMockLayout();
        set(manager, "trackAnimator", track);
        set(manager, "perspectiveData", new Sonic2PerspectiveData() {
            @Override
            public PerspectiveEntry getEntry(int trackFrame, int depth) {
                return new PerspectiveEntry(0x7F, 0x58, 8, 8, 0, 0);
            }
        });
        return new Fixture(manager, objects, intro, pool, ring);
    }

    private static Sonic2SpecialStageIntro intro(Sonic2SpecialStageSlotPool pool, boolean team) {
        Sonic2SpecialStageIntro intro = new Sonic2SpecialStageIntro();
        intro.attachNativeSlotPool(pool);
        intro.initialize(0, 50);
        intro.initializeNativeFixedOwners(team, false);
        return intro;
    }

    private static Sonic2SpecialStageIntro.NativeCounterDigit digit(int x, int frame) {
        return new Sonic2SpecialStageIntro.NativeCounterDigit(x, 0x20, frame);
    }

    private static Sonic2SpecialStageRenderer.RingHudState hud(boolean sonic, boolean tails,
                                                             int sonicRings, int tailsRings, int total) {
        return new Sonic2SpecialStageRenderer.RingHudState(sonic, tails, sonicRings, tailsRings, total);
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
                           Sonic2SpecialStageIntro intro, Sonic2SpecialStageSlotPool pool,
                           Sonic2SpecialStageRing ring) { }
}
