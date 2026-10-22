package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native UI allocation/lifetime boundaries, independent of a complete special-stage route. */
class TestSonic2SpecialStageUiSlots {
    @Test
    void startupAllocatesPersistentElevenBeforeTheSeparateTeamCounter() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, true, false);
        var streamed = pool.allocate(RING, 0x60, 3, 0, null);
        intro.initializeRingsToGoOwners();
        intro.initializeNativeFixedOwnerChildren();

        assertEquals(8, streamed.slot());
        assertEquals(11, intro.getNativeRingsToGoHandles().size());
        assertEquals(9, intro.getNativeRingsToGoHandles().getFirst().slot());
        assertEquals(19, intro.getNativeRingsToGoHandles().getLast().slot());
        assertEquals(20, intro.getNativeCounterChildHandle().slot());
        assertEquals(TOTAL_COUNTER, pool.entry(intro.getNativeCounterChildHandle()).kind());
        assertEquals(0, pool.entry(intro.getNativeRingsToGoHandles().getFirst()).priorityBucket());
        assertEquals(0, pool.entry(intro.getNativePlayerCounterHandle()).priorityBucket());
        scan(pool, intro, null, false);
        assertEquals(11, intro.getNativeRingsToGoHandles().stream().filter(pool::isLive).count());
        assertTrue(intro.nativeRenderStates().stream()
                .filter(state -> state.identity().kind() == RINGS_TO_GO_LETTER)
                .noneMatch(Sonic2SpecialStageSlotPool.RenderState::visible));
        assertThrows(UnsupportedOperationException.class, () -> intro.getNativeRingsToGoHandles().clear());
    }

    @Test
    void soloHasNoExtraCounterAndVersusCreatesThirteenPhraseOwnersBeforeScore() {
        Sonic2SpecialStageSlotPool soloPool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro solo = intro(soloPool, false, false);
        solo.initializeRingsToGoOwners();
        solo.initializeNativeFixedOwnerChildren();
        assertNull(solo.getNativeCounterChildHandle());

        Sonic2SpecialStageSlotPool vsPool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro versus = intro(vsPool, true, true);
        versus.initializeRingsToGoOwners();
        versus.initializeNativeFixedOwnerChildren();
        assertEquals(13, versus.getMessageLetters().size());
        assertEquals(19, versus.getMessageLetters().getFirst().getNativeHandle().slot());
        assertEquals(32, versus.getNativeCounterChildHandle().slot());
        assertEquals(VS_SCORE, vsPool.entry(versus.getNativeCounterChildHandle()).kind());
        assertEquals(1, versus.nativeRenderStates().stream()
                .filter(state -> state.mappingFrame() == 19).count(), "VS is one native mapping owner");
    }

    @Test
    void fullPoolReturnsFromPersistentTextCreatorWithoutInventingOwnersOrRetrying() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, false, false);
        for (int i = 0; i < 118; i++) {
            assertNotNull(pool.allocate(RING, 0x60, 3, i, null));
        }
        intro.initializeRingsToGoOwners();
        assertEquals(List.of(126, 127), intro.getNativeRingsToGoHandles().stream()
                .map(Sonic2SpecialStageSlotPool.Handle::slot).toList());
        assertNull(pool.allocate(RING, 0x60, 3, 0, null));
        pool.release(pool.entryAt(8).handle());
        intro.initializeRingsToGoOwners();
        assertNull(pool.entryAt(8), "native creator does not restart after its failed allocation");
    }

    @Test
    void bannerPartialAllocationUsesRealOwnersAndCenterBasedDeletion() throws Exception {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, false, false);
        intro.initializeNativeFixedOwnerChildren();
        for (int i = 0; i < 118; i++) {
            pool.allocate(RING, 0x60, 3, i, null);
        }
        set(intro, "currentPhase", Sonic2SpecialStageIntro.Phase.WAIT1);
        set(intro, "phaseTimer", 14);
        intro.update();
        assertEquals(2, intro.getBannerLetters().size());
        assertEquals(List.of(126, 127), intro.getBannerLetters().stream()
                .map(letter -> letter.getNativeHandle().slot()).toList());
        assertEquals(0xA, pool.entry(intro.getNativeBannerHandle()).routine());
        assertEquals(List.of(128, 128), intro.nativeRenderStates().stream()
                .filter(state -> state.identity().kind() == BANNER_PIECE)
                .map(Sonic2SpecialStageSlotPool.RenderState::x).toList());

        Sonic2SpecialStageIntro.BannerLetter left = intro.getBannerLetters().getFirst();
        left.x = -0x48 - 128; // owner x=0, mapped piece is already well outside the screen.
        left.y = -72;
        set(left, "nativeAngle", 0);
        left.flyoutSpeed = 0;
        intro.executeNativeChildSlot(pool.entry(left.getNativeHandle()));
        assertTrue(pool.isLive(left.getNativeHandle()), "native comparisons include center x=0,y=0");
        left.x--;
        intro.executeNativeChildSlot(pool.entry(left.getNativeHandle()));
        assertFalse(pool.isLive(left.getNativeHandle()));
    }

    @Test
    void rainbowAllocationIntoEarlierHolesWaitsForTheNextAscendingScan() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        var hole8 = pool.allocate(RING, 0x60, 3, 0, null);
        var hole9 = pool.allocate(RING, 0x60, 3, 1, null);
        var marker = pool.allocate(CHECKPOINT_MARKER, 0x5A, 0, 0xFE, null);
        pool.release(hole8);
        pool.release(hole9);
        Sonic2SpecialStageCheckpoint checkpoint = checkpoint(pool, marker, 1, 1);
        assertEquals(List.of(6, 5, 4, 3, 2, 1, 0), checkpoint.captureRewindSnapshot().rainbowRings()
                .stream().map(Sonic2SpecialStageSnapshot.CheckpointRainbowRingSnapshot::baseIndex).toList());
        assertFalse(pool.isLive(marker));
        for (int slot = marker.slot() + 1; slot < 128; slot++) {
            checkpoint.executeNativeSlot(pool.entryAt(slot), true);
        }
        assertEquals(-1, checkpoint.getRainbowRings().getFirst().getMappingFrame());
        assertEquals(0, checkpoint.getRainbowRings().get(2).getMappingFrame());
        scan(pool, null, checkpoint, true);
        assertEquals(0, checkpoint.getRainbowRings().getFirst().getMappingFrame());
    }

    @Test
    void completingRainbowAllocatesWingsBelowCurrentAndHandAndTextAfterCurrent() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        var marker = pool.allocate(CHECKPOINT_MARKER, 0x5A, 0, 0xFE, null);
        Sonic2SpecialStageCheckpoint checkpoint = checkpoint(pool, marker, 1, 1);
        for (int i = 0; i < 10; i++) {
            scan(pool, null, checkpoint, true);
        }
        assertEquals(8, checkpoint.getNativeWingsHandle().slot());
        assertEquals(16, checkpoint.getNativeHandHandle().slot());
        assertEquals(17, checkpoint.getMessageLetters().getFirst().getNativeHandle().slot());
        var snapshot = checkpoint.captureRewindSnapshot();
        assertEquals(70, snapshot.nativeWingsTimer(), "earlier hole has not executed yet");
        assertEquals(69, snapshot.nativeHandTimer());
        assertTrue(snapshot.messageLetters().stream().allMatch(letter -> letter.nativeTimer() == 69));
        checkpoint.update(true);
        assertEquals(snapshot.nativeHandTimer(), checkpoint.captureRewindSnapshot().nativeHandTimer(),
                "group update cannot execute the child a second time");
        assertEquals(1, pool.entry(checkpoint.getNativeWingsHandle()).priorityBucket());
        assertEquals(0, pool.entry(checkpoint.getNativeHandHandle()).priorityBucket());
    }

    @Test
    void fullPoolCannotFabricateAHandOrAdvanceTheNextRequirement() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        var marker = pool.allocate(CHECKPOINT_MARKER, 0x5A, 0, 0xFE, null);
        Sonic2SpecialStageCheckpoint checkpoint = checkpoint(pool, marker, 1, 1);
        AtomicInteger nextRequirement = new AtomicInteger();
        checkpoint.setOnNativeRingRequirementMessage(source -> nextRequirement.incrementAndGet());
        while (pool.allocate(RING, 0x60, 3, 0, null) != null) {
            // Saturate RAM while the seven real rainbow owners are still live.
        }
        for (int i = 0; i < 10; i++) {
            scan(pool, null, checkpoint, true);
        }
        assertNull(checkpoint.getNativeWingsHandle());
        assertNull(checkpoint.getNativeHandHandle());
        assertTrue(checkpoint.getMessageLetters().isEmpty());
        assertFalse(checkpoint.update(false), "no hand ever reached the native completion routine");
        assertEquals(0, nextRequirement.get());
    }

    @Test
    void checkpointVisibilitySuppressesPersistentOwnersWithoutFreeingThem() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, false, false);
        intro.initializeRingsToGoOwners();
        intro.setNativeRingsToGoDisplay(2, true, 0);
        scan(pool, intro, null, false);
        assertEquals(11, intro.nativeRenderStates().stream()
                .filter(state -> state.identity().kind() == RINGS_TO_GO_NUMBER
                        || state.identity().kind() == RINGS_TO_GO_LETTER)
                .filter(Sonic2SpecialStageSlotPool.RenderState::visible).count());
        intro.setNativeCheckpointMessageHidden(true);
        scan(pool, intro, null, false);
        assertTrue(intro.nativeRenderStates().stream()
                .filter(state -> state.identity().kind() == RINGS_TO_GO_NUMBER
                        || state.identity().kind() == RINGS_TO_GO_LETTER)
                .noneMatch(Sonic2SpecialStageSlotPool.RenderState::visible));
        assertEquals(11, intro.getNativeRingsToGoHandles().stream().filter(pool::isLive).count());
    }

    @Test
    void nativeNumberCapturesRetailBcdAndChildCountOnlyWhenItsOwnSlotComputes() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, false, false);
        intro.initializeRingsToGoOwners();
        var number = intro.getNativeRingsToGoHandles().getFirst();
        assertEquals(0, intro.getNativeRingsToGoChildCount(), "creator leaves mainspr_childsprites zero");
        intro.setNativeRingsToGoDisplay(101, false, 0);
        intro.executeNativeChildSlot(pool.entry(number));
        assertEquals(0, intro.getNativeRingsToGoChildCount(), "trigger-hidden number returns before computing");
        intro.setNativeRingsToGoDisplay(101, true, 0);
        assertEquals(0, intro.getNativeRingsToGoChildCount(), "supplying inputs cannot execute the owner");
        intro.executeNativeChildSlot(pool.entry(number));
        // Retail subtract/bgt turns 101 into $0200 + unsigned $FF9D / 10;
        // divu/lsl.w/or.b produces $02F7, not the fixed-branch decimal $0101.
        assertEquals(0x02F7, intro.getNativeRingsToGoBcd());
        assertEquals(3, intro.getNativeRingsToGoChildCount());
        var snapshot = intro.captureRewindSnapshot();
        intro.setNativeRingsToGoDisplay(7, true, 0);
        intro.executeNativeChildSlot(pool.entry(number));
        assertEquals(7, intro.getNativeRingsToGoBcd());
        assertEquals(1, intro.getNativeRingsToGoChildCount());
        intro.restoreRewindSnapshot(snapshot);
        assertEquals(0x02F7, intro.getNativeRingsToGoBcd());
        assertEquals(3, intro.getNativeRingsToGoChildCount());
        intro.setNativeRingsToGoDisplay(10, true, 0);
        intro.executeNativeChildSlot(pool.entry(number));
        assertEquals(0x10, intro.getNativeRingsToGoBcd());
        assertEquals(2, intro.getNativeRingsToGoChildCount());
        intro.setNativeRingsToGoDisplay(0, true, 0);
        intro.executeNativeChildSlot(pool.entry(number));
        assertEquals(0, intro.getNativeRingsToGoBcd());
        assertEquals(1, intro.getNativeRingsToGoChildCount());
    }

    @Test
    void fcInitResetsTheTwelvePassZeroWindowWithoutExecutingAnEarlierNumberOwner() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, false, false);
        intro.initializeRingsToGoOwners();
        intro.setNativeRingsToGoDisplay(0, true, 0);
        for (int pass = 0; pass < 12; pass++) {
            scan(pool, intro, null, false);
        }
        assertEquals(12, intro.captureRewindSnapshot().nativeNoRingsToGoLifetime());
        assertTrue(intro.captureRewindSnapshot().nativeHideRingsToGo());
        intro.setNativeCheckpointMessageHidden(true);
        intro.executeNativeRingsMessageInit();
        var reset = intro.captureRewindSnapshot();
        assertEquals(0, reset.nativeNoRingsToGoLifetime(), "FC reset is a write, not another number dispatch");
        assertFalse(reset.nativeHideRingsToGo());
        assertTrue(reset.nativeCheckpointMessageHidden(),
                "SS_NoCheckpointMsg belongs to stream/rainbow writes, not Obj5A_RingsMessageInit");
        assertTrue(reset.nativeRingsEnabled());
        assertEquals(1, intro.getNativeRingsToGoChildCount(), "FC retains already captured number geometry");
        for (int pass = 1; pass <= 11; pass++) {
            scan(pool, intro, null, false);
            assertEquals(pass, intro.captureRewindSnapshot().nativeNoRingsToGoLifetime());
            assertFalse(intro.captureRewindSnapshot().nativeHideRingsToGo());
        }
        scan(pool, intro, null, false);
        assertTrue(intro.captureRewindSnapshot().nativeHideRingsToGo());
        intro.restoreRewindSnapshot(reset);
        assertEquals(0, intro.captureRewindSnapshot().nativeNoRingsToGoLifetime());
        scan(pool, intro, null, false);
        assertEquals(1, intro.captureRewindSnapshot().nativeNoRingsToGoLifetime());
        assertFalse(intro.captureRewindSnapshot().nativeHideRingsToGo());
    }

    @Test
    void successfulHandCreatesUnitsFirstRequirementBeforeItsOwnDeletion() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, false, false);
        var marker = pool.allocate(CHECKPOINT_MARKER, 0x5A, 0, 0xFE, null);
        Sonic2SpecialStageCheckpoint checkpoint = checkpoint(pool, marker, 1, 1);
        AtomicInteger calls = new AtomicInteger();
        checkpoint.setOnNativeRingRequirementMessage(source -> {
            assertTrue(pool.isLive(source));
            intro.showNativeRingRequirementMessage(123, source);
            calls.incrementAndGet();
        });
        for (int i = 0; i < 79; i++) {
            scan(pool, intro, checkpoint, i < 10);
        }
        assertEquals(1, calls.get());
        assertFalse(pool.isLive(checkpoint.getNativeHandHandle()));
        assertEquals(List.of(-4, -3, -2), intro.getMessageLetters().stream()
                .filter(letter -> letter.tileOffset < 0).map(letter -> letter.tileOffset).toList());
        assertEquals(12, intro.getMessageLetters().size(), "GET + three digits + RINGS + !");
    }

    @Test
    void failedHandKeepsItsSlotForTheFortyEightPassTriggerThenClearsAllRam() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, true, false);
        intro.initializeRingsToGoOwners();
        intro.initializeNativeFixedOwnerChildren();
        var marker = pool.allocate(CHECKPOINT_MARKER, 0x5A, 0, 0xFE, null);
        Sonic2SpecialStageCheckpoint checkpoint = checkpoint(pool, marker, 2, 1);
        for (int i = 0; i < 79; i++) {
            scan(pool, intro, checkpoint, i < 10);
        }
        assertTrue(pool.isLive(checkpoint.getNativeHandHandle()));
        assertEquals(0xC, pool.entry(checkpoint.getNativeHandHandle()).routine());
        for (int i = 0; i < 47; i++) {
            scan(pool, intro, checkpoint, false);
        }
        assertTrue(pool.isLive(checkpoint.getNativeHandHandle()));
        scan(pool, intro, checkpoint, false);
        assertEquals(0, pool.size());
        assertTrue(checkpoint.update(false));
    }

    @Test
    void restoredUiRecreatesHandlesAndRepeatsTheSameForwardAllocationAndExpiry() {
        Sonic2SpecialStageSlotPool pool = new Sonic2SpecialStageSlotPool();
        Sonic2SpecialStageIntro intro = intro(pool, true, false);
        intro.initializeRingsToGoOwners();
        intro.initializeNativeFixedOwnerChildren();
        var marker = pool.allocate(CHECKPOINT_MARKER, 0x5A, 0, 0xFE, null);
        Sonic2SpecialStageCheckpoint checkpoint = checkpoint(pool, marker, 2, 1);
        for (int i = 0; i < 12; i++) {
            scan(pool, intro, checkpoint, i < 10);
        }
        var poolSaved = pool.snapshot();
        var introSaved = intro.captureRewindSnapshot();
        var checkpointSaved = checkpoint.captureRewindSnapshot();
        for (int i = 0; i < 32; i++) {
            scan(pool, intro, checkpoint, false);
        }
        var expectedPool = pool.entries();
        var expectedIntro = intro.captureRewindSnapshot();
        var expectedCheckpoint = checkpoint.captureRewindSnapshot();
        pool.restore(poolSaved);
        intro.restoreRewindSnapshot(introSaved);
        checkpoint.restoreRewindSnapshot(checkpointSaved);
        for (int i = 0; i < 32; i++) {
            scan(pool, intro, checkpoint, false);
        }
        assertEquals(expectedPool, pool.entries());
        assertEquals(expectedCheckpoint, checkpoint.captureRewindSnapshot());
        assertEquals(expectedIntro.messageLetters(), intro.captureRewindSnapshot().messageLetters());
        assertEquals(expectedIntro.nativeRingsToGo(), intro.captureRewindSnapshot().nativeRingsToGo());
        assertEquals(expectedIntro.nativeRingsToGoX(), intro.captureRewindSnapshot().nativeRingsToGoX());
        assertEquals(expectedIntro.nativeRingsToGoVisible(), intro.captureRewindSnapshot().nativeRingsToGoVisible());
        assertEquals(pool.size(), intro.nativeRenderStates().size() + checkpoint.nativeRenderStates().size(),
                "every live UI owner has an accounted immutable state");
    }

    private static Sonic2SpecialStageIntro intro(Sonic2SpecialStageSlotPool pool,
                                                boolean team, boolean versus) {
        Sonic2SpecialStageIntro intro = new Sonic2SpecialStageIntro();
        intro.attachNativeSlotPool(pool);
        intro.initialize(0, 50);
        intro.initializeNativeFixedOwners(team, versus);
        return intro;
    }

    private static Sonic2SpecialStageCheckpoint checkpoint(Sonic2SpecialStageSlotPool pool,
                                                          Sonic2SpecialStageSlotPool.Handle marker,
                                                          int requirement, int rings) {
        Sonic2SpecialStageCheckpoint checkpoint = new Sonic2SpecialStageCheckpoint();
        checkpoint.attachNativeSlotPool(pool);
        checkpoint.queueNativeMarker(marker);
        checkpoint.beginCheckpoint(1, requirement, rings, false);
        return checkpoint;
    }

    private static void scan(Sonic2SpecialStageSlotPool pool, Sonic2SpecialStageIntro intro,
                             Sonic2SpecialStageCheckpoint checkpoint, boolean drawingIndex4) {
        for (int slot = 0; slot < 128; slot++) {
            var entry = pool.entryAt(slot);
            if (intro != null && intro.executeNativeChildSlot(entry)) {
                continue;
            }
            if (checkpoint != null) {
                checkpoint.executeNativeSlot(entry, drawingIndex4);
            }
        }
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }
}
