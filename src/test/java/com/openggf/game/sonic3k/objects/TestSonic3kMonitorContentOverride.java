package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit native contents must preserve the placement key, child graph and remembered shell. */
class TestSonic3kMonitorContentOverride {
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() { SessionManager.clear(); GameModuleRegistry.reset(); }

    private static final class RobotnikMonitor extends Sonic3kMonitorObjectInstance {
        RobotnikMonitor(ObjectSpawn spawn) { super(spawn, 0); }
        @Override public RobotnikMonitor recreateForRewind(RewindRecreateContext context) {
            return new RobotnikMonitor(context.spawn());
        }
    }

    private static AbstractPlayableSprite breaker() {
        var player = mock(AbstractPlayableSprite.class);
        when(player.getCode()).thenReturn("sonic");
        when(player.getAnimationId()).thenReturn(Sonic3kAnimationIds.ROLL.id());
        when(player.getRingCount()).thenReturn(1);
        return player;
    }
    private static void breakAndRun(Sonic3kMonitorObjectInstance monitor, AbstractPlayableSprite player) {
        monitor.onTouchResponse(player, new TouchResponseResult(0, 14, 14, TouchCategory.SPECIAL), 0);
        for (int tick = 1; tick <= 34; tick++) monitor.update(tick, player);
    }

    @Test void originalConstructorStillPaysRingsWhileExplicitRobotnikUsesNativeHurt() {
        var spawn = new ObjectSpawn(100, 100, 1, 3, 0, true, 0x8064, 0);
        var ordinary = new Sonic3kMonitorObjectInstance(spawn);
        var hostile = new RobotnikMonitor(spawn);
        ordinary.setServices(new TestObjectServices());
        hostile.setServices(new TestObjectServices());
        var regularPlayer = breaker(); var hurtPlayer = breaker();
        breakAndRun(ordinary, regularPlayer); breakAndRun(hostile, hurtPlayer);
        assertSame(spawn, ordinary.getSpawn()); assertSame(spawn, hostile.getSpawn());
        assertEquals(3, hostile.getSpawn().subtype(), "The canonical native placement must not be rewritten");
        verify(regularPlayer).addRings(10);
        verify(hurtPlayer).applyHurtOrDeath(0, false, true);
        verify(hurtPlayer, never()).addRings(anyInt());
    }

    @Test void robotnikUsesItsRealIconAndRespectsNativeInvulnerability() {
        var monitor = new RobotnikMonitor(new ObjectSpawn(100, 100, 1, 9, 1, true, 0x8064, 0));
        var render = mock(ObjectRenderManager.class); var renderer = mock(PatternSpriteRenderer.class);
        var sheet = mock(ObjectSpriteSheet.class);
        var icon = new SpriteMappingPiece(-8, -13, 2, 2, 0x18, false, false, 0);
        when(renderer.isReady()).thenReturn(true); when(render.getMonitorRenderer()).thenReturn(renderer);
        when(render.getMonitorSheet()).thenReturn(sheet); when(sheet.getFrameCount()).thenReturn(12);
        when(sheet.getFrame(1)).thenReturn(new SpriteMappingFrame(List.of(icon)));
        monitor.setServices(new TestObjectServices() { @Override public ObjectRenderManager renderManager() { return render; } });
        var player = breaker(); when(player.getInvulnerableFrames()).thenReturn(10);
        monitor.onTouchResponse(player, new TouchResponseResult(0, 14, 14, TouchCategory.SPECIAL), 0);
        monitor.appendRenderCommands(List.of());
        verify(sheet).getFrame(1);
        verify(renderer).drawFrameIndex(11, 100, 100, true, false);
        for (int tick = 1; tick <= 34; tick++) monitor.update(tick, player);
        verify(player, never()).applyHurtOrDeath(anyInt(), anyBoolean(), anyBoolean());
        verify(player, never()).addRings(anyInt());
    }

    @Test void contentsGraphRewindsAndRememberedShellSurvivesNativeRespawn() {
        var spawn = new ObjectSpawn(100, 100, 1, 3, 0, false, 100, 0);
        var player = breaker(); var sprites = mock(SpriteManager.class);
        when(sprites.getSprite("sonic")).thenReturn(player);
        var camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320); when(camera.getHeight()).thenReturn((short) 224);
        var services = new TestObjectServices().withSpriteManager(sprites).withCamera(camera);
        var registry = new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn source) { return new RobotnikMonitor(source); }
            @Override public void reportCoverage(List<ObjectSpawn> sources) { }
            @Override public String getPrimaryName(int id) { return "Monitor"; }
            @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
        };
        var manager = new ObjectManager(List.of(spawn), registry, 0, null, null, null, camera, services);
        services.withDirectObjectManager(manager);
        manager.reset(0); manager.update(0, player, List.of(), 0);
        var monitor = manager.getActiveObjects().stream().filter(RobotnikMonitor.class::isInstance)
                .map(RobotnikMonitor.class::cast).findFirst().orElseThrow();
        monitor.onTouchResponse(player, new TouchResponseResult(0, 14, 14, TouchCategory.SPECIAL), 1);
        assertTrue(manager.isRemembered(spawn), "Exact original spawn must own the native remembered bit");
        for (int tick = 2; tick <= 12; tick++) manager.update(0, player, List.of(), tick);
        var adapter = manager.rewindSnapshottable(); var captured = adapter.capture();
        assertEquals(1, captured.dynamicObjects().stream().filter(e -> e.className().endsWith("$MonitorContentsSlot")).count());
        for (int tick = 13; tick <= 65; tick++) manager.update(0, player, List.of(), tick);
        var finished = adapter.capture();
        verify(player).applyHurtOrDeath(0, false, true);
        clearInvocations(player);
        adapter.restore(captured);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", captured, adapter.capture()));
        for (int tick = 13; tick <= 65; tick++) manager.update(0, player, List.of(), tick);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", finished, adapter.capture()));
        verify(player).applyHurtOrDeath(0, false, true);
        verify(player, never()).addRings(anyInt());
        var persistence = manager.capturePersistentRespawn();
        manager.reset(0, persistence); manager.update(0, player, List.of(), 66);
        var shell = manager.getActiveObjects().stream().filter(RobotnikMonitor.class::isInstance)
                .map(RobotnikMonitor.class::cast).findFirst().orElseThrow();
        assertEquals(0, shell.getCollisionFlags(), "Remembered reload is the broken shell, never a fresh payout");
    }
    @Test void colocatedFactoryShellsRestoreTheirExactContentsOwnersAndReplayIndependently() throws Exception {
        var player = breaker();
        var camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn((short) 320); when(camera.getHeight()).thenReturn((short) 224);
        var sprites = mock(SpriteManager.class); when(sprites.getSprite("sonic")).thenReturn(player);
        var services = new TestObjectServices().withCamera(camera).withSpriteManager(sprites);
        var registry = new Sonic3kGameModule().createObjectRegistry();
        var manager = new ObjectManager(List.of(), registry, 0, null, null, null, camera, services);
        services.withDirectObjectManager(manager);
        var spawnA = new ObjectSpawn(100, 100, 1, 3, 0, false, 100, 40);
        var spawnB = new ObjectSpawn(100, 100, 1, 9, 0, false, 100, 41);
        var a = (Sonic3kMonitorObjectInstance) Sonic3kMonitorFactory.eggman(spawnA);
        var b = (Sonic3kMonitorObjectInstance) Sonic3kMonitorFactory.eggman(spawnB);
        manager.addDynamicObject(a); manager.addDynamicObject(b);
        var hit = new TouchResponseResult(0, 14, 14, TouchCategory.SPECIAL);
        a.onTouchResponse(player, hit, 0); b.onTouchResponse(player, hit, 0);
        var context = manager.captureIdentityContext().requireIdentityTable();
        var aId = context.idFor(a); var bId = context.idFor(b);
        var captured = manager.rewindSnapshottable().capture();
        for (var object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
        manager.rewindSnapshottable().restore(captured);
        var restoredA = (Sonic3kMonitorObjectInstance) manager.objectQuery().resolve(aId).orElseThrow();
        var restoredB = (Sonic3kMonitorObjectInstance) manager.objectQuery().resolve(bId).orElseThrow();
        var ownerField = Sonic3kMonitorObjectInstance.class.getDeclaredField("monitorContentsSlot");
        ownerField.setAccessible(true);
        var aSlot = ownerField.get(restoredA); var bSlot = ownerField.get(restoredB);
        assertNotNull(aSlot); assertNotNull(bSlot); assertNotSame(aSlot, bSlot);
        var parentField = aSlot.getClass().getDeclaredField("parent"); parentField.setAccessible(true);
        assertSame(restoredA, parentField.get(aSlot), "colocated icon belongs to captured shell A");
        assertSame(restoredB, parentField.get(bSlot), "colocated icon belongs to captured shell B");
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", captured, manager.rewindSnapshottable().capture()));
        manager.removeDynamicObject(restoredA);
        assertTrue(((ObjectInstance) aSlot).isDestroyed());
        assertFalse(((ObjectInstance) bSlot).isDestroyed());
        assertDoesNotThrow(() -> manager.rewindSnapshottable().capture(), "retired contents cannot retain an unregistered shell");
        for (int tick = 1; tick <= 34; tick++) restoredB.update(tick, player);
        verify(player).applyHurtOrDeath(0, false, true);
        verify(player, never()).addRings(anyInt());
    }

}
