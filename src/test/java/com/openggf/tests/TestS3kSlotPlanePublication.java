package com.openggf.tests;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.scroll.SwScrlSlots;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Logical Slot entry; real ROM event, VBlank profile and retained Plane B, with no reference hydration. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSlotPlanePublication {
    private TraceReplaySessionBootstrap.ConfigSnapshot saved;
    @AfterEach void cleanup() {
        TestEnvironment.resetAll();
        if (saved != null) TraceReplaySessionBootstrap.restoreGameplayConfig(saved);
    }

    @Test
    void queuedAndPublishedPlaneRowsReplayTheCompleteRecreatedWorldTwice() throws Exception {
        saved = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try (var session = enterSlot()) {
        var mode = SessionManager.getCurrentGameplayMode();
        var level = GameServices.level();
        level.getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        var scroll = assertInstanceOf(SwScrlSlots.class, GameServices.parallax().getHandler(level.getCurrentZone()));
        publish(PlcLifecyclePhase.ORDINARY_LEVEL);
        var registry = mode.getRewindRegistry();
        CompositeSnapshot before = null;
        for (int event = 0; event < 32; event++) {
            before = registry.capture();
            event(scroll);
            if (pending(scroll)) break;
            publish(PlcLifecyclePhase.ORDINARY_LEVEL);
        }
        assertTrue(pending(scroll), "the ROM band event must prepare a real row transfer");
        var queued = registry.capture();
        var retained = publishedValue(scroll.captureRewindState());
        assertSame(retained, publishedValue(scroll.captureRewindState()), "unchanged captures share the immutable plane");
        byte[] leaked = publishedBytes(retained);
        byte original = leaked[0];leaked[0] ^= 0x7F;
        assertEquals(original, publishedBytes(retained)[0], "snapshot accessors cannot mutate retained plane bytes");
        byte[] queuedPlane = plane();
        Runnable queue = () -> {
            byte[] beforePlane = plane();
            event(scroll);
            assertTrue(pending(scroll));
            assertArrayEquals(beforePlane, plane(), "event preparation must not change visible Plane B");
        };
        // Expected queue boundary was reached by the same production event above.
        replayTwice(before, queued, queue, "before queue -> live pending");
        Runnable publication = () -> {
            byte[] oldPlane = plane();
            publish(PlcLifecyclePhase.LAG);
            assertArrayEquals(oldPlane, plane(), "genuine lag holds Plane B");
            assertTrue(pending(scroll));
            scroll.rebuildOutput(new int[224], GameServices.camera().getX(), GameServices.camera().getY(), 0, 0);
            scroll.rebuildOutput(new int[224], GameServices.camera().getX(), GameServices.camera().getY(), 0, 0);
            assertArrayEquals(oldPlane, plane(), "rebuild is not publication");
            publish(PlcLifecyclePhase.ORDINARY_LEVEL);
            assertFalse(pending(scroll));
            assertFalse(Arrays.equals(oldPlane, plane()), "VInt_DrawLevel commits actual ROM-backed rows");
        };
        registry.restore(queued);
        publication.run();
        var published = registry.capture();
        byte[] publishedPlane = plane();
        replayTwice(queued, published, publication, "live pending -> after VInt publication");
        registry.restore(queued);assertArrayEquals(queuedPlane, plane());
        registry.restore(published);assertArrayEquals(publishedPlane, plane());
        }
    }

    private static com.openggf.tools.GameplayCaptureSession enterSlot() throws Exception {
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        var session = new com.openggf.tools.GameplayCaptureSession(settings);
        try {
            GameServices.configuration().setSessionOverride(com.openggf.configuration.SonicConfiguration.LIVE_REWIND_ENABLED, true);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "Slot publication fixture");
            for (int frame = 0; frame < 60; frame++) { session.step(neutral);session.renderFrame(); }
            GameServices.level().requestBonusStageEntry(com.openggf.game.BonusStageType.SLOT_MACHINE);
            for (int frame = 0; frame < 600; frame++) {
                session.step(neutral);session.renderFrame();
                if (session.loop().getCurrentGameMode() == com.openggf.game.GameMode.BONUS_STAGE
                        && !GameServices.fade().isActive() && !GameServices.fade().hasPendingCompletion()) break;
            }
            assertEquals(com.openggf.game.GameMode.BONUS_STAGE, session.loop().getCurrentGameMode());
            assertFalse(GameServices.fade().isActive());assertFalse(GameServices.fade().hasPendingCompletion());
            return session;
        } catch (Exception | Error e) { session.close();throw e; }
    }

    private static void event(SwScrlSlots scroll) {
        var camera = GameServices.camera();
        scroll.update(new int[224], camera.getX(), camera.getY(), GameServices.level().getFrameCounter(), 0);
    }

    private static void publish(PlcLifecyclePhase phase) {
        var coordinator = new PlcFrameLifecycleCoordinator((PlcLifecycleService) null);
        var frame = coordinator.latchBeforeFadeUpdate();
        assertTrue(frame.claim(phase));
        LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(SessionManager.getCurrentGameplayMode()), frame);
        byte[] first = plane();
        LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(SessionManager.getCurrentGameplayMode()), frame);
        assertArrayEquals(first, plane(), "one physical token cannot publish twice");
        frame.finish();
    }

    private static Object publishedValue(Object state) throws Exception {
        var accessor = state.getClass().getDeclaredMethod("publishedPlane");accessor.setAccessible(true);
        return accessor.invoke(state);
    }
    private static byte[] publishedBytes(Object value) throws Exception {
        var accessor = value.getClass().getDeclaredMethod("bytes");accessor.setAccessible(true);
        return (byte[]) accessor.invoke(value);
    }
    private static byte[] plane() { return GameServices.level().getTilemapManager().captureRetainedBackgroundVdpRing(); }
    private static boolean pending(SwScrlSlots scroll) {
        try {
            var field = SwScrlSlots.class.getDeclaredField("pendingBgPlaneRowUpdates");
            field.setAccessible(true);return !((java.util.List<?>) field.get(scroll)).isEmpty();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void replayTwice(CompositeSnapshot before, CompositeSnapshot after, Runnable action, String label) {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);same(before, registry.capture(), label + " before");
            action.run();same(after, registry.capture(), label + " forward");
            registry.restore(before);same(before, registry.capture(), label + " reverse");
            registry.restore(after);same(after, registry.capture(), label + " after");
        }
    }
    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        TestS3kCanonicalEntryLifecycle.assertSameWorld(expected, actual, label);
    }
}
