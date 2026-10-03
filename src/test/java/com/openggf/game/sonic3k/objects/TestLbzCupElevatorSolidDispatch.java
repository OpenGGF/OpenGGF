package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestLbzCupElevatorSolidDispatch {
    @AfterEach void resetSession() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void airbornePlayerStopsAtCupSideInItsOwnDispatch(boolean nativeP2) {
        TestEnvironment.resetAll();
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setRolling(true);
        player.setAir(true);
        player.setCentreX((short) 0x11CA);
        player.setCentreY((short) 0x0884);
        player.setXSpeed((short) -0x048F);
        player.setYSpeed((short) 0x03B0);
        player.setGSpeed((short) -0x0294);
        var main = nativeP2
                ? new TestablePlayableSprite("sonic", (short) 0x1200, (short) 0x0800)
                : player;
        var sidekicks = nativeP2 ? List.of(player) : List.<TestablePlayableSprite>of();
        player.setCpuControlled(nativeP2);
        var camera = GameServices.camera();
        camera.setX((short) 0x113A);
        camera.setY((short) 0x0822);
        var solids = new DefaultSolidExecutionRegistry();
        var services = new TestObjectServices().withCamera(camera).withSidekicks(sidekicks)
                .withSolidExecutionRegistry(solids);
        var manager = new ObjectManager(List.of(), null, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        services.withDirectObjectManager(manager);
        manager.reset(0x113A);
        var cup = new LbzCupElevatorInstance(new ObjectSpawn(
                0x11E0, 0x0888, Sonic3kObjectIds.LBZ_CUP_ELEVATOR, 9, 0, false, 0));
        manager.addDynamicObject(cup);

        var objectAdapter = manager.rewindSnapshottable();
        var objectBefore = objectAdapter.capture();
        var solidBefore = solids.capture();
        var playerBefore = player.captureRewindState();
        var mainBefore = main.captureRewindState();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                objectAdapter.restore(objectBefore);
                solids.restore(solidBefore);
                main.restoreRewindState(mainBefore);
                player.restoreRewindState(playerBefore);
            }
            manager.update(0x113A, main, sidekicks, 0, false, true, false);

            // loc_26EEA calls SolidObjectFull2_1P with d1=$20+$0B.
            assertEquals(0x11CB, player.getCentreX() & 0xFFFF);
            assertEquals(0, player.getXSpeed());
            assertEquals(0, player.getGSpeed());
            assertEquals(0x03B0, player.getYSpeed());
            assertTrue(player.getAir());
            assertFalse(player.isObjectControlled());
        }
    }
}
