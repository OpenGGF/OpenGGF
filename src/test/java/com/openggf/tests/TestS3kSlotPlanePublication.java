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

    @Test
    void encodedSlotLayoutRestoreKeepsPublishedPlaneThroughTheRealWorldRegistrationOrder() throws Exception {
        saved = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        // Normal ROM scene loading, with the existing headless fixture: no GPU,
        // bonus-driver copy, playback rows or manually installed rewind adapter.
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(21, 0).build();
        var manager = GameServices.level();
        var level = assertInstanceOf(com.openggf.game.sonic3k.Sonic3kLevel.class, manager.getCurrentLevel());
        assertTrue(level.hasEncodedCollisionLayout());
        // The production parallax entry must select the active handler before
        // retained history is captured by the world registry.
        var camera = GameServices.camera();
        GameServices.parallax().initZone(manager.getCurrentZone(), manager.getCurrentAct(),
                camera.getX(), camera.getY());
        var scroll = assertInstanceOf(SwScrlSlots.class,
                GameServices.parallax().getHandler(manager.getCurrentZone()));
        var registry = fixture.gameplayMode().getRewindRegistry();
        assertNotNull(registry);
        manager.seedBackgroundVdpPlaneFromWorld(0);
        byte[] cold = manager.getTilemapManager().getBackgroundTilemapData().clone();
        manager.copyBackgroundTileRowFromWorldToVdpPlane(0xD8, 0, 0xE400, 0x19);
        byte[] retained = manager.getTilemapManager().getBackgroundTilemapData().clone();
        assertFalse(Arrays.equals(cold, retained), "actual ROM row publication must leave retained history");
        var before = registry.capture();
        var keys = new java.util.ArrayList<>(before.entries().keySet());
        var collision = assertInstanceOf(com.openggf.game.sonic3k.S3kCollisionLayoutRewindAdapter.State.class,
                before.get("s3k-collision-layout"));
        assertNotNull(collision.encoded(), "actual Slot registry must carry encoded layout, not the empty branch");
        assertTrue(keys.indexOf("parallax") >= 0 && keys.indexOf("parallax") < keys.indexOf("level"));
        assertTrue(keys.indexOf("level") < keys.indexOf("s3k-collision-layout"),
                "production extra restores after the parallax and shared level owners");
        var parallax = assertInstanceOf(com.openggf.game.rewind.snapshot.ParallaxSnapshot.class,
                before.get("parallax"));
        assertNotNull(parallax.handlerRewindState(), "the actual world snapshot must carry the active Slot handler");
        assertArrayEquals(retained, publishedBytes(publishedValue(parallax.handlerRewindState())),
                "the stored world snapshot must own this exact physical image");
        var animator = assertInstanceOf(com.openggf.game.sonic3k.Sonic3kLevelAnimationManager.class,
                manager.getAnimatedPatternManager());
        var animation = animator.capture();
        assertSame(animator, manager.getAnimatedPaletteManager());
        var mapBefore = level.getMap();
        // The missing-encoded branch is an actual no-op, including map identity,
        // animation owner and the complete physical image.
        new com.openggf.game.sonic3k.S3kCollisionLayoutRewindAdapter().restore(
                new com.openggf.game.sonic3k.S3kCollisionLayoutRewindAdapter.State(null, null));
        assertSame(mapBefore, level.getMap());
        assertSame(animator, manager.getAnimatedPatternManager());
        assertArrayEquals(retained, manager.getTilemapManager().getBackgroundTilemapData());

        manager.copyBackgroundTileRowFromWorldToVdpPlane(0xD8, 0x10, 0xE000, 0x19);
        byte[] expectedAfterRow = manager.getTilemapManager().getBackgroundTilemapData().clone();
        registry.restore(before);

        assertSame(level, manager.getCurrentLevel(), "this is a same-level metadata restore, not a scene load");
        assertNotSame(mapBefore, level.getMap(), "actual encoded layout restore must install its new map owner");
        assertArrayEquals(collision.scene().mapData(), level.getMap().getData());
        assertEquals(collision.scene().width(), level.getMap().getWidth());
        assertEquals(collision.scene().height(), level.getMap().getHeight());
        assertEquals(collision.scene().fgWidth(), level.getLayerWidthBlocks(0));
        assertEquals(collision.scene().bgWidth(), level.getLayerWidthBlocks(1));
        assertSame(animator, manager.getAnimatedPatternManager(), "same scene must retain its captured animator");
        assertSame(animator, manager.getAnimatedPaletteManager());
        var restoredAnimation = animator.capture();
        assertArrayEquals(animation.scriptCounters(), restoredAnimation.scriptCounters());
        assertArrayEquals(animation.handlerCounters(), restoredAnimation.handlerCounters());
        assertArrayEquals(animation.extra(), restoredAnimation.extra(), "logical animation owner restores independently");
        assertEquals(manager.getForegroundTileDescriptorAtWorld(0x80, 0),
                manager.getForegroundTileDescriptorFromTilemapAtWorld(0x80, 0),
                "actual FG cache must resolve the restored geometry");
        // One legitimate native row publication must not reconstruct unrelated
        // rows from the source map after the later collision-layout restore.
        manager.copyBackgroundTileRowFromWorldToVdpPlane(0xD8, 0x10, 0xE000, 0x19);
        assertArrayEquals(expectedAfterRow, manager.getTilemapManager().getBackgroundTilemapData(),
                "same-level encoded layout rebind must preserve all untouched physical Plane B bytes");
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
