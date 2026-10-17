package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.mods.objects.AizLogBridgeFactory;
import com.openggf.game.sonic3k.difficulty.AizLogBridgeRecipe;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TestAizBrittleLogBridge {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(0x300, 0x200, 0x2C, 8, 0, true, 19);
    private static final PlayerSolidContactResult STANDING = new PlayerSolidContactResult(
            ContactKind.TOP, true, false, false, false, null, null, 0);

    @Test
    void brittleSupportLastsTenUpdatesAcrossBothRidersAndEverySegment() throws Exception {
        for (int flip = 0; flip < 2; flip++) {
            for (int relativeX : new int[] {-80, -48, -16, 16, 48, 80}) {
                ObjectSpawn spawn = new ObjectSpawn(0x300, 0x200, 0x2C, 8, flip, true, 19);
                var bridge = (AizCollapsingLogBridgeObjectInstance) AizLogBridgeFactory.create(
                        spawn, new AizLogBridgeRecipe(10));
                installServices(bridge);
                invoke(bridge, "startCollapse");
                var sonic = rider("sonic", relativeX);
                var tails = rider("tails", -relativeX);
                bridge.recordStandingPlayer(sonic, STANDING);
                bridge.recordStandingPlayer(tails, STANDING);
                for (int tick = 1; tick < 10; tick++) {
                    bridge.update(tick, null);
                    assertFalse(sonic.getAir(), "P1 support ended before update10 at x=" + relativeX);
                    assertFalse(tails.getAir(), "P2 support ended before update10 at x=" + -relativeX);
                }
                bridge.update(10, null);
                assertTrue(sonic.getAir());
                assertTrue(tails.getAir());
                assertFalse(bridge.isSolidFor(sonic));
                assertSame(spawn, bridge.getSpawn(), "the original placement identity is retained");
            }
        }
    }

    @Test
    void absentRecipeKeepsNativeFirstAndLastSegmentRelease() throws Exception {
        var bridge = new AizCollapsingLogBridgeObjectInstance(SPAWN);
        installServices(bridge);
        invoke(bridge, "startCollapse");
        var first = rider("sonic", -80);
        var last = rider("tails", 80);
        bridge.recordStandingPlayer(first, STANDING);
        bridge.recordStandingPlayer(last, STANDING);
        for (int tick = 1; tick < 8; tick++) bridge.update(tick, null);
        assertFalse(first.getAir());
        bridge.update(8, null);
        assertTrue(first.getAir(), "native base8 drops the first segment at update8");
        assertFalse(last.getAir());
        for (int tick = 9; tick < 48; tick++) bridge.update(tick, null);
        assertFalse(last.getAir());
        bridge.update(48, null);
        assertTrue(last.getAir(), "native base8 + five8-update intervals drops the last at48");
    }

    @Test
    void recipeRejectsUnboundedSupportAndScriptedBurningDrawbridge() {
        assertThrows(IllegalArgumentException.class, () -> new AizLogBridgeRecipe(7));
        assertThrows(IllegalArgumentException.class, () -> new AizLogBridgeRecipe(25));
        assertThrows(IllegalArgumentException.class, () -> AizLogBridgeFactory.create(
                new ObjectSpawn(0x300, 0x200, 0x2C, 0x88, 0, true, 19), new AizLogBridgeRecipe(10)));
        for (int unrelatedId : new int[] {0, 0x2B, 0x91, 0x92}) {
            assertThrows(IllegalArgumentException.class, () -> AizLogBridgeFactory.create(
                    new ObjectSpawn(0x300, 0x200, unrelatedId, 8, 0, true, 19), new AizLogBridgeRecipe(10)));
        }
    }

    @Test
    void idleBrittleLogPresentsLooseRomSegmentsBeforeAnyContactWhileStockStaysLevel() {
        var renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        var renders = mock(ObjectRenderManager.class);
        when(renders.getRenderer(anyString())).thenReturn(renderer);
        var services = new StubObjectServices() {
            @Override public ObjectRenderManager renderManager() { return renders; }
        };
        var stock = new AizCollapsingLogBridgeObjectInstance(SPAWN);
        stock.setServices(services);
        // The parent has no Draw_Sprite; its display SST (loc_2AEB4) draws the logs.
        stock.appendRenderCommands(new java.util.ArrayList<>());
        verify(renderer, never()).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
        stock.renderLogs(new java.util.ArrayList<>());
        verify(renderer, times(6)).drawFrameIndex(anyInt(), anyInt(), eq(SPAWN.y()), eq(false), eq(false));
        clearInvocations(renderer);
        var brittle = AizLogBridgeFactory.create(SPAWN, new AizLogBridgeRecipe(10));
        brittle.setServices(services);
        ((AizCollapsingLogBridgeObjectInstance) brittle).renderLogs(new java.util.ArrayList<>());
        verify(renderer, times(3)).drawFrameIndex(anyInt(), anyInt(), eq(SPAWN.y() + 2), eq(false), eq(false));
        verify(renderer, times(3)).drawFrameIndex(anyInt(), anyInt(), eq(SPAWN.y()), eq(false), eq(false));
        assertEquals(stock.getY(), brittle.getY());
        assertEquals(stock.getSolidParams(), ((AizCollapsingLogBridgeObjectInstance) brittle).getSolidParams());
    }

    private static TestablePlayableSprite rider(String name, int relativeX) {
        var player = new TestablePlayableSprite(name, (short) 0, (short) 0);
        player.setCentreX((short) (SPAWN.x() + relativeX));
        player.setAir(false);
        player.setOnObject(true);
        return player;
    }

    private static void installServices(AizCollapsingLogBridgeObjectInstance bridge) {
        var camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x280);
        bridge.setServices(new StubObjectServices() {
            @Override public Camera camera() { return camera; }
        });
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
}
