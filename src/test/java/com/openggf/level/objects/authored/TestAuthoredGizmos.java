package com.openggf.level.objects.authored;

import com.openggf.camera.Camera;
import com.openggf.game.MusicReference;
import com.openggf.game.PlayableEntity;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.stageplay.StageplayControl;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.*;

class TestAuthoredGizmos {
    @Test void typedPlacementValidationAndMovementRetainNativeIdentityAndProperties() {
        var properties = new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(0x81), true);
        var spawn = spawn(100, 100, 7, properties);
        var moved = spawn.withPosition(200, 300);
        assertEquals(properties, moved.authoredProperties());
        assertEquals(7, moved.layoutIndex());
        assertEquals("openggf", moved.ownerModId());
        assertEquals(properties.objectKey(), moved.objectKey());
        assertNull(new ObjectSpawn(1, 2, 3, 4, 0, false, 2).authoredProperties());
        assertFalse(AuthoredGizmos.handlesKey(null));
        assertDoesNotThrow(() -> AuthoredGizmos.validatePlacements(List.of(new ObjectSpawn(1, 2, 3, 4, 0, false, 2))));
        assertThrows(IllegalArgumentException.class, () -> new ObjectSpawn(0, 0, 0, 0, 0, false, 0, 0,
                "openggf", "openggf:warp", properties));
        assertThrows(IllegalArgumentException.class, () -> spawn(0xffff, 100, 1, properties));
        assertThrows(IllegalArgumentException.class, () -> new AuthoredObjectProperties.Warp(0, 32, "a", false));
        assertThrows(IllegalArgumentException.class, () -> new AuthoredObjectProperties.Warp(32, 32, "../a", false));
        assertThrows(IllegalArgumentException.class, () -> new AuthoredObjectProperties.StageplayTrigger(
                32, 32, "../scene.stageplay", AuthoredObjectProperties.Trigger.PROXIMITY, true, true, false));
        assertThrows(IllegalArgumentException.class, () -> new AuthoredObjectProperties.CameraZone(
                32, 32, 50, 10, 0, 500, AuthoredObjectProperties.ReleaseCondition.NONE, 0));
    }

    @Test void musicEntryOnceStateAndSpawnRecreationRoundTripThroughGenericRewind() {
        var requests = new ArrayList<Integer>();
        var services = new StubObjectServices() { @Override public void playMusic(int id) { requests.add(id); } };
        var spawn = spawn(100, 100, 1, new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(0x81), true));
        var object = AuthoredGizmos.create(spawn); object.setServices(services);
        var before = object.captureRewindState();
        var player = new Player(110, 110);
        object.update(0, player.entity);
        var entered = object.captureRewindState();
        player.x.set(200); object.update(1, player.entity);
        player.x.set(110); object.update(2, player.entity);
        assertEquals(List.of(0x81), requests);
        var restored = object.recreateForRewind(new RewindRecreateContext(spawn, entered, services));
        restored.setServices(services); restored.restoreRewindState(entered); restored.update(3, player.entity);
        assertEquals(List.of(0x81), requests, "recreated inside/once state must not emit a second cue");
        restored.restoreRewindState(before); restored.update(4, player.entity);
        assertEquals(List.of(0x81, 0x81), requests);
        assertTrue(restored.isPersistent());
        assertFalse(restored.publishesTouchResponseListEntryThisFrame());
    }

    @Test void repeatingMusicZoneOnlyRetriggersAfterExitAndReentry() {
        var requests = new ArrayList<Integer>();
        var object = AuthoredGizmos.create(spawn(100, 100, 1,
                new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(7), false)));
        object.setServices(new StubObjectServices() { @Override public void playMusic(int id) { requests.add(id); } });
        var player = new Player(110, 110);
        object.update(0, player.entity); object.update(1, player.entity);
        player.x.set(200); object.update(2, player.entity);
        player.x.set(110); object.update(3, player.entity);
        assertEquals(List.of(7, 7), requests);
    }

    @Test void cameraBoundaryLeaseReleasesExactCurrentAndTargetBoundsAndRewindsItsLatch() {
        var camera = mock(Camera.class);
        when(camera.getMinX()).thenReturn((short) 0); when(camera.getMaxX()).thenReturn((short) 2000);
        when(camera.getMinY()).thenReturn((short) 0); when(camera.getMaxY()).thenReturn((short) 1000);
        when(camera.getMinXTarget()).thenReturn((short) 10); when(camera.getMaxXTarget()).thenReturn((short) 1990);
        when(camera.getMinYTarget()).thenReturn((short) 20); when(camera.getMaxYTarget()).thenReturn((short) 990);
        var object = AuthoredGizmos.create(spawn(100, 100, 1, new AuthoredObjectProperties.CameraZone(
                64, 64, 100, 500, 50, 300, AuthoredObjectProperties.ReleaseCondition.CROSS_X, 200)));
        object.setServices(new StubObjectServices() { @Override public Camera camera() { return camera; } });
        var before = object.captureRewindState();
        var player = new Player(110, 110);
        object.update(0, player.entity);
        verify(camera).setMinX((short) 100); verify(camera).setMaxY((short) 300);
        player.x.set(201); object.update(1, player.entity);
        verify(camera).setMaxXCurrent((short) 2000); verify(camera).setMinYCurrent((short) 0);
        verify(camera).setMinXTarget((short) 10); verify(camera).setMaxYTarget((short) 990);
        player.x.set(110); object.update(2, player.entity);
        verify(camera, times(1)).setMinX((short) 100);
        object.restoreRewindState(before); object.update(3, player.entity);
        verify(camera, times(2)).setMinX((short) 100);
    }

    @Test void warpValidationRequiresAnUnambiguousPair() {
        var a = spawn(100, 100, 1, new AuthoredObjectProperties.Warp(32, 64, "door-a", true));
        var b = spawn(500, 100, 2, new AuthoredObjectProperties.Warp(32, 64, "door-a", true));
        assertDoesNotThrow(() -> AuthoredGizmos.validatePlacements(List.of(a, b)));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmos.validatePlacements(List.of(a)));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmos.validatePlacements(List.of(a, b,
                spawn(700, 100, 3, new AuthoredObjectProperties.Warp(32, 64, "door-a", true)))));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmos.validatePlacements(List.of(a,
                spawn(500, 100, 2, new AuthoredObjectProperties.Warp(32, 64, "door-a", false)))));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmos.validatePlacements(List.of(
                new ObjectSpawn(0, 0, 0, 0, 0, false, 0, 1, "openggf", "openggf:music-zone"))));
    }

    @Test void authoredRegionsAndWarpExitPixelsStayInsideCanvasWithoutChangingStockPlacementRules() {
        var edge = spawn(480, 192, 1, new AuthoredObjectProperties.MusicZone(32, 64, MusicReference.stock(7), true));
        assertDoesNotThrow(() -> AuthoredGizmos.validateBounds(List.of(edge), 512, 256));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmos.validateBounds(List.of(edge), 511, 256));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmos.validateBounds(List.of(edge), 512, 255));
        var exit = spawn(480, 100, 2, new AuthoredObjectProperties.Warp(32, 64, "door", true));
        assertThrows(IllegalArgumentException.class, () -> AuthoredGizmos.validateBounds(List.of(exit), 512, 256));
        assertDoesNotThrow(() -> AuthoredGizmos.validateBounds(List.of(exit), 513, 256));
        assertDoesNotThrow(() -> AuthoredGizmos.validateBounds(List.of(new ObjectSpawn(65000, 3000, 1, 0, 0, false, 3000)), 512, 256));
    }

    @Test void linkedWarpNeverBouncesRegardlessOfDestinationUpdateOrderAndRewindsCooldown() {
        var a = spawn(100, 100, 1, new AuthoredObjectProperties.Warp(32, 64, "door", true));
        var b = spawn(500, 100, 2, new AuthoredObjectProperties.Warp(32, 64, "door", true));
        var level = mock(Level.class); when(level.getObjects()).thenReturn(List.of(a, b));
        var services = new StubObjectServices() { @Override public Level currentLevel() { return level; } };
        var source = AuthoredGizmos.create(a); source.setServices(services);
        var destination = AuthoredGizmos.create(b); destination.setServices(services);
        var before = source.captureRewindState();
        var player = new Player(110, 110);
        source.update(0, player.entity); destination.update(0, player.entity);
        assertEquals(532, player.x.get()); assertEquals(132, player.y.get());
        source.update(1, player.entity); destination.update(1, player.entity);
        assertEquals(532, player.x.get());
        player.x.set(510); destination.update(2, player.entity); source.update(2, player.entity);
        assertEquals(132, player.x.get(), "a deliberate later entry may use the reverse endpoint");
        source.restoreRewindState(before); player.x.set(110); source.update(3, player.entity);
        assertEquals(532, player.x.get(), "rewind must restore the source cooldown and entry latch");
    }

    @Test void oneWayWarpExitIsNotAnEntrance() {
        var a = spawn(100, 100, 1, new AuthoredObjectProperties.Warp(32, 64, "door", false));
        var b = spawn(500, 100, 2, new AuthoredObjectProperties.Warp(32, 64, "door", false));
        var level = mock(Level.class); when(level.getObjects()).thenReturn(List.of(a, b));
        var object = AuthoredGizmos.create(b);
        object.setServices(new StubObjectServices() { @Override public Level currentLevel() { return level; } });
        var player = new Player(510, 110); object.update(0, player.entity);
        assertEquals(510, player.x.get());
    }

    @Test void stageplayButtonEdgeConsumptionAndRewindUseOnlyInjectedPublicPlaybackService() {
        var calls = new ArrayList<String>();
        AuthoredStageplayService playback = (resource, blocking) -> { calls.add(resource + ":" + blocking); return true; };
        var object = AuthoredGizmos.create(spawn(100, 100, 1, new AuthoredObjectProperties.StageplayTrigger(
                32, 64, "scenes/intro.stageplay", AuthoredObjectProperties.Trigger.BUTTON, true, true, true)));
        object.setServices(new StubObjectServices() {
            @Override public <T> T gameService(Class<T> type) { return type == AuthoredStageplayService.class ? type.cast(playback) : null; }
        });
        var player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 110); when(player.getCentreY()).thenReturn((short) 110);
        var before = object.captureRewindState();
        object.update(0, player); assertTrue(calls.isEmpty());
        when(player.isUpPressed()).thenReturn(true); object.update(1, player); object.update(2, player);
        assertEquals(List.of("scenes/intro.stageplay:true"), calls); assertTrue(object.isDestroyed());
        object.restoreRewindState(before); object.update(3, player);
        assertEquals(2, calls.size());
    }

    @Test void publicBlockingAdapterRefusesUnsupportedModeAndDoesNotReplaceActivePlayback() {
        var control = mock(StageplayControl.class);
        when(control.status()).thenReturn(StageplayControl.Status.IDLE);
        var service = com.openggf.mods.code.AuthoredStageplayServices.blocking(resource -> resource.equals("intro.stageplay") ? control : null);
        assertTrue(service.play("intro.stageplay", true)); verify(control).play();
        when(control.status()).thenReturn(StageplayControl.Status.PLAYING);
        assertFalse(service.play("intro.stageplay", true)); verify(control, times(1)).play();
        assertThrows(IllegalArgumentException.class, () -> service.play("intro.stageplay", false));
        assertThrows(IllegalArgumentException.class, () -> service.play("missing.stageplay", true));
    }

    @Test void busyStageplayRequestRetriesWhileInsideAndItsPendingStateRewinds() {
        var calls = new AtomicInteger();
        AuthoredStageplayService playback = (resource, blocking) -> calls.incrementAndGet() >= 2;
        var object = AuthoredGizmos.create(spawn(100, 100, 1, new AuthoredObjectProperties.StageplayTrigger(
                32, 64, "intro.stageplay", AuthoredObjectProperties.Trigger.PROXIMITY, true, true, false)));
        object.setServices(new StubObjectServices() {
            @Override public <T> T gameService(Class<T> type) { return type == AuthoredStageplayService.class ? type.cast(playback) : null; }
        });
        var player = new Player(110, 110);
        object.update(0, player.entity);
        var waiting = object.captureRewindState();
        object.update(1, player.entity); object.update(2, player.entity);
        assertEquals(2, calls.get());
        object.restoreRewindState(waiting); object.update(3, player.entity);
        assertEquals(3, calls.get(), "rewind restores an accepted-but-pending activation");
    }

    @Test void eagerHostAdmissionRunsRegionsWhoseOriginIsOutsideNativePlacementWindows() {
        var requests = new ArrayList<Integer>();
        var holder = new com.openggf.level.objects.ObjectManager[1];
        var services = new StubObjectServices() {
            @Override public com.openggf.level.objects.ObjectManager objectManager() { return holder[0]; }
            @Override public void playMusic(int id) { requests.add(id); }
        };
        var registry = new com.openggf.game.sonic2.objects.Sonic2ObjectRegistry();
        var manager = new com.openggf.level.objects.ObjectManager(List.of(), registry, 0, null, null, null, null, services);
        holder[0] = manager;
        AuthoredGizmos.install(List.of(new ObjectSpawn(1, 2, 1, 0, 0, false, 2, 1),
                spawn(4000, 0, 2, new AuthoredObjectProperties.MusicZone(64, 64, MusicReference.stock(7), true))), manager::addDynamicObject);
        var active = manager.objectQuery().activeObjectsOfType(AuthoredGizmoInstance.class);
        assertEquals(1, active.size());
        assertTrue(active.getFirst().isPersistent());
        active.getFirst().update(0, new Player(4010, 10).entity);
        assertEquals(List.of(7), requests);
    }

    private static ObjectSpawn spawn(int x, int y, int id, AuthoredObjectProperties properties) {
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, id, "openggf", properties.objectKey(), properties);
    }

    private static final class Player {
        final AtomicInteger x = new AtomicInteger(), y = new AtomicInteger();
        final PlayableEntity entity = mock(PlayableEntity.class);
        Player(int initialX, int initialY) {
            x.set(initialX); y.set(initialY);
            when(entity.getCentreX()).thenAnswer(invocation -> (short) x.get());
            when(entity.getCentreY()).thenAnswer(invocation -> (short) y.get());
            doAnswer(invocation -> { x.addAndGet(invocation.<Integer>getArgument(0)); return null; }).when(entity).shiftX(anyInt());
            doAnswer(invocation -> { y.set(invocation.<Short>getArgument(0) & 0xffff); return null; })
                    .when(entity).setCentreYPreserveSubpixel(anyShort());
        }
    }
}
