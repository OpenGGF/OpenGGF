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
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.Pattern;
import com.openggf.level.Palette;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Native draw counters must not advance while a directed template observes the current picture. */
class TestStageplayNativeCapturePurity {
    @Test
    void mgzBodyCaptureAndOrdinaryDrawPreserveHeadAnimationPhase() throws Exception {
        Fixture fixture = new Fixture();
        var body =
                fixture.objects.createDynamicObject(() -> new MgzDrillingRobotnikInstance(spawn()));
        // Inspect a visible initialized appearance, independent of fight physics and admission
        // timing.
        field(MgzDrillingRobotnikInstance.class, "waitTimer").setInt(body, 0);
        field(MgzDrillingRobotnikInstance.class, "renderTick").setInt(body, 9);
        fixture.captureTwice(body,
                () -> assertEquals(9, field(MgzDrillingRobotnikInstance.class, "renderTick").getInt(body)));
        body.appendRenderCommands(new ArrayList<>());
        body.appendRenderCommands(new ArrayList<>());
        assertEquals(9, field(MgzDrillingRobotnikInstance.class, "renderTick").getInt(body));
    }

    @Test
    void mgzDefeatPartCapturePreservesFlickerAndOrdinaryDrawStillConsumesOnePhase()
            throws Exception {
        Fixture fixture = new Fixture();
        var part = fixture.objects.createDynamicObject(() -> new MgzEndBossKnuxDefeatPart(spawn()));
        field(MgzEndBossKnuxDefeatPart.class, "flicker").setInt(part, 1);
        fixture.captureTwice(part,
                () -> assertEquals(1, field(MgzEndBossKnuxDefeatPart.class, "flicker").getInt(part)));
        part.appendRenderCommands(new ArrayList<>());
        assertEquals(2, field(MgzEndBossKnuxDefeatPart.class, "flicker").getInt(part));
    }

    @Test
    void pachinkoCapturePreservesRenderDiagnosticAndOrdinaryDrawStillCounts() {
        Fixture fixture = new Fixture();
        var trap =
                fixture.objects.createDynamicObject(
                        () -> new PachinkoEnergyTrapObjectInstance(spawn()));
        fixture.captureTwice(trap, () -> assertEquals(0, trap.getRenderCount()));
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
        fixture.captureTwice(pile, () -> assertEquals(12, pile.getLastRenderedTileCountForTests()));
        pile.appendRenderCommands(new ArrayList<>());
        assertEquals(0, pile.getLastRenderedTileCountForTests());
    }

    @Test
    void initializedMgzDefeatPartHasNativePoseOwnershipAndCaptureDoesNotConsumeFlicker() throws Exception {
        Fixture fixture = new Fixture();
        fixture.provideMgzRenderer();
        var part = fixture.objects.createDynamicObject(() -> new MgzEndBossKnuxDefeatPart(spawn()));
        Field flicker = field(MgzEndBossKnuxDefeatPart.class, "flicker");
        flicker.setInt(part, 1);
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        var first = direction.captureDynamic(part);
        assertEquals(1, flicker.getInt(part));
        var second = direction.captureDynamic(part);
        assertEquals(1, flicker.getInt(part));
        assertEquals(first, second);
        assertEquals(ObjectVisualFacing.Basis.NON_DIRECTIONAL, first.facing());
        assertEquals(1, first.parts().size());
        assertEquals(0x2E, first.parts().getFirst().defaultPose());
        part.appendRenderCommands(new ArrayList<>());
        assertEquals(2, flicker.getInt(part));
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
        final GraphicsManager graphics = new GraphicsManager();
        final ObjectRenderManager renderers = mock(ObjectRenderManager.class);
        final ObjectManager objects;

        Fixture() {
            graphics.setHeadlessMode(true);
            ObjectServices services = mock(ObjectServices.class);
            when(services.graphicsManager()).thenReturn(graphics);
            when(services.renderManager()).thenReturn(renderers);
            when(services.solidExecutionRegistry()).thenReturn(SolidExecutionRegistry.inert());
            objects =
                    new ObjectManager(
                            List.of(), null, 0, null, null, graphics, new Camera(), services);
            when(services.objectManager()).thenReturn(objects);
        }

        void provideMgzRenderer() {
            graphics.cachePaletteTexture(new Palette(), 0);
            Pattern pattern = new Pattern();
            pattern.setPixel(0, 0, (byte) 1);
            var piece = new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0);
            var frames = java.util.stream.IntStream.range(0, 0x31)
                    .mapToObj(index -> new SpriteMappingFrame(index == 0x2E ? List.of(piece) : List.of()))
                    .toList();
            var renderer = new PatternSpriteRenderer(new ObjectSpriteSheet(
                    new Pattern[] {pattern}, frames, 0, 1), graphics);
            renderer.ensurePatternsCached(graphics, 512);
            when(renderers.getRenderer(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MGZ_ENDBOSS))
                    .thenReturn(renderer);
        }

        void captureTwice(AbstractObjectInstance object, Executable unchanged) {
            var direction = ObjectDirectionAccess.forManager(objects);
            // This fixture deliberately has no decoded renderer. Even a rejected
            // observation must leave the native state untouched so Free can resume.
            var first = assertThrows(IllegalArgumentException.class, () -> direction.captureDynamic(object));
            assertAll("Native state after first rejected capture", unchanged);
            var second = assertThrows(IllegalArgumentException.class, () -> direction.captureDynamic(object));
            assertAll("Native state after second rejected capture", unchanged);
            assertEquals(first.getClass(), second.getClass());
            assertEquals(first.getMessage(), second.getMessage());
            assertTrue(first.getMessage().contains("no initialized native visual"), first.getMessage());
        }
    }
}
