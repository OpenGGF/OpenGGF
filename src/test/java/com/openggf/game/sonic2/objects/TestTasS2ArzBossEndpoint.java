package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic2.objects.bosses.Sonic2ARZBossInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Final-hit endpoint overwrite, 1779M/3101S and 3325M/5373S; native pin 34fc7f13. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2ArzBossEndpoint {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void currentLimitationEndpointFinalHitRestartsAttackInsteadOfSkippingExplosions(boolean atEndpoint) throws Exception {
        AbstractObjectInstance.resetCameraBoundsForTests();
        var camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x2A00);
        when(camera.getY()).thenReturn((short) 0x400);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        var services = new TestObjectServices().withCamera(camera).withGameState(new GameStateManager())
                .withDebugOverlay(mock(DebugOverlayManager.class));
        var table = GameServices.module().createTouchResponseTable(RomByteReader.fromRom(TestEnvironment.currentRom()));
        var objects = new ObjectManager(List.of(), null, 0, null, table, null, camera, services);
        services.withDirectObjectManager(objects);
        var boss = new Sonic2ARZBossInstance(new ObjectSpawn(0x2AE0, 0x430, 0x89, 0, 0, false, 0));
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        objects.addDynamicObject(boss);
        boss.update(0, player); // real initialization, including child creation
        int x = atEndpoint ? 0x2AB1 : 0x2AC0;
        set(boss, "bossXPos", x << 16);
        set(boss, "bossYPos", 0x430 << 16);
        set(boss, "bossXVel", -0xC8);
        set(boss, "bossYVel", 0);
        set(boss, "targetFlag", true);
        boss.getState().routine = 2;
        boss.getState().x = x;
        boss.getState().y = 0x430;
        boss.getState().hitCount = 1;
        player.setCentreX((short) x);
        player.setCentreY((short) 0x430);
        player.setRolling(true);
        player.setAnimationId(2);
        player.setAir(true);
        player.setYSpeed((short) 0x200);
        objects.snapshotTouchResponseState();
        objects.runTouchResponsesForPlayer(player, 1);
        assertEquals(0, boss.getState().hitCount, "actual player touch must deal final hit");
        assertFalse(boss.getState().defeated, "death is deferred to the boss's own later slot");
        boss.update(1, player);
        assertTrue(boss.getState().defeated);
        assertEquals(1000, services.gameState().getScore());
        // Obj89_Main_KillBoss writes 8, then Obj89_Main_Sub2_AtTarget adds 2.
        assertEquals(atEndpoint ? 4 : 8, boss.getState().routine,
                "Confirmed limitation: native endpoint adds 2 to defeat routine 8 (A), current assignment overwrites it with 4");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
