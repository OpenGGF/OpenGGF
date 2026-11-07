package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.*;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2CompetitionObjectLifetimeGates {
    @Test @RequiresRom(SonicGame.SONIC_2)
    void sixNativeLayoutsEnumerateCustomLifetimeOwners() throws Exception {
        var parser = new Sonic2ObjectPlacement(RomByteReader.fromRom(TestEnvironment.currentRom()));
        var registry = new Sonic2ObjectRegistry();
        var customIds = new TreeSet<Integer>();
        int layouts = 0;
        for (int zone : new int[]{0, 11, 12}) {
            for (int act = 0; act < 2; act++) {
                var spawns = parser.load(new ZoneAct(zone, act), true);
                assertFalse(spawns.isEmpty());
                var ids = new TreeSet<Integer>();
                for (var spawn : spawns) {
                    ids.add(spawn.objectId());
                    var object = ObjectConstructionContext.construct(new StubObjectServices(), () -> registry.create(spawn));
                    assertNotNull(object, String.format("unregistered %02X", spawn.objectId()));
                    if (object.usesCustomOutOfRangeCheck()) customIds.add(spawn.objectId());
                }
                System.out.printf("native-layout zone=%d act=%d ids=%s%n", zone, act, ids);
                layouts++;
            }
        }
        assertEquals(6, layouts);
        assertEquals(Set.of(0x7A, 0xD7), customIds,
                "new custom owners need an explicit native lifetime audit");
    }

    @Test void customEndpointAndInlineMarkObjGone2RetainNativeObjectsOnly() throws Exception {
        var module = mock(GameModule.class);
        var policy = mock(WorldSessionPolicyState.class);
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policy);
        when(policy.getService(NativeCompetitionRoster.class)).thenReturn(mock(NativeCompetitionRoster.class));
        var world = new WorldSession(module);
        for (boolean nativeMode : new boolean[]{false, true}) {
            var camera = new Camera();
            var services = new StubObjectServices() {
                @Override public WorldSession worldSession() { return nativeMode ? world : null; }
                @Override public Camera camera() { return camera; }
            };
            var platform = new SidewaysPformObjectInstance(new ObjectSpawn(0, 0, 0x7A, 0, 0, false, 0), "platform");
            platform.setServices(services);
            assertEquals(!nativeMode, platform.isCustomOutOfRange(0x3000));
            var spikes = new SlidingSpikesObjectInstance(new ObjectSpawn(0x3000, 0, 0x76, 0, 0, false, 0), "spikes");
            spikes.setServices(services);
            var check = SlidingSpikesObjectInstance.class.getDeclaredMethod("isBasePositionOnScreen");
            check.setAccessible(true);
            assertEquals(nativeMode, check.invoke(spikes));
        }
    }
}
