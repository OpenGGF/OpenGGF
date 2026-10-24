package com.openggf.game.sonic3k.objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openggf.camera.Camera;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectDirectionAccess;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Native draw counters must not advance while a directed template observes the current picture. */
class TestStageplayNativeCapturePurity {
    @Test
    void mgzBodyCapturePreservesHeadAnimationPhaseAndOrdinaryDrawStillAdvances() throws Exception {
        Fixture fixture = new Fixture();
        var body =
                fixture.objects.createDynamicObject(() -> new MgzDrillingRobotnikInstance(spawn()));
        // Inspect a visible initialized appearance, independent of fight physics and admission
        // timing.
        field(MgzDrillingRobotnikInstance.class, "waitTimer").setInt(body, 0);
        field(MgzDrillingRobotnikInstance.class, "renderTick").setInt(body, 9);
        fixture.captureTwice(body);
        assertEquals(9, field(MgzDrillingRobotnikInstance.class, "renderTick").getInt(body));
        body.appendRenderCommands(new ArrayList<>());
        assertEquals(10, field(MgzDrillingRobotnikInstance.class, "renderTick").getInt(body));
    }

    @Test
    void mgzDefeatPartCapturePreservesFlickerAndOrdinaryDrawStillConsumesOnePhase()
            throws Exception {
        Fixture fixture = new Fixture();
        var part = fixture.objects.createDynamicObject(() -> new MgzEndBossKnuxDefeatPart(spawn()));
        field(MgzEndBossKnuxDefeatPart.class, "flicker").setInt(part, 1);
        fixture.captureTwice(part);
        assertEquals(1, field(MgzEndBossKnuxDefeatPart.class, "flicker").getInt(part));
        part.appendRenderCommands(new ArrayList<>());
        assertEquals(2, field(MgzEndBossKnuxDefeatPart.class, "flicker").getInt(part));
    }

    @Test
    void pachinkoCapturePreservesRenderDiagnosticAndOrdinaryDrawStillCounts() {
        Fixture fixture = new Fixture();
        var trap =
                fixture.objects.createDynamicObject(
                        () -> new PachinkoEnergyTrapObjectInstance(spawn()));
        fixture.captureTwice(trap);
        assertEquals(0, trap.getRenderCount());
        trap.appendRenderCommands(new ArrayList<>());
        assertEquals(1, trap.getRenderCount());
    }

    @Test
    void snowCapturePreservesBackgroundDiagnosticAndOrdinaryAppendStillClearsIt() throws Exception {
        Fixture fixture = new Fixture();
        var pile =
                fixture.objects.createDynamicObject(
                        () -> new IczBigSnowPileInstance(spawn(), null));
        field(IczBigSnowPileInstance.class, "lastRenderedTileCount").setInt(pile, 12);
        fixture.captureTwice(pile);
        assertEquals(12, pile.getLastRenderedTileCountForTests());
        pile.appendRenderCommands(new ArrayList<>());
        assertEquals(0, pile.getLastRenderedTileCountForTests());
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(100, 80, 1, 0, 0, false, 80);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class Fixture {
        final ObjectManager objects;

        Fixture() {
            GraphicsManager graphics = new GraphicsManager();
            graphics.setHeadlessMode(true);
            ObjectServices services = mock(ObjectServices.class);
            when(services.graphicsManager()).thenReturn(graphics);
            when(services.renderManager()).thenReturn(mock(ObjectRenderManager.class));
            when(services.solidExecutionRegistry()).thenReturn(SolidExecutionRegistry.inert());
            objects =
                    new ObjectManager(
                            List.of(), null, 0, null, null, graphics, new Camera(), services);
            when(services.objectManager()).thenReturn(objects);
        }

        void captureTwice(AbstractObjectInstance object) {
            var direction = ObjectDirectionAccess.forManager(objects);
            // This fixture deliberately has no decoded renderer. Even a rejected
            // observation must leave the native state untouched so Free can resume.
            var first = assertThrows(IllegalArgumentException.class, () -> direction.captureDynamic(object));
            var second = assertThrows(IllegalArgumentException.class, () -> direction.captureDynamic(object));
            assertEquals(first.getClass(), second.getClass());
            assertEquals(first.getMessage(), second.getMessage());
            assertTrue(first.getMessage().contains("no initialized native visual"));
        }
    }
}
