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

    @Test @RequiresRom(SonicGame.SONIC_2)
    void leftoverObj3CIsUnplacedAcrossEveryShippedDirectoryEntry() throws Exception {
        var rom = RomByteReader.fromRom(TestEnvironment.currentRom());
        var addresses = new TreeSet<Integer>();
        // Off_Objects (s2.asm:91270-91328) includes all 34 entries, including
        // null zones, MTZ3's alias, HPZ and spare single-act lists.
        for (int index = 0; index < 34; index++)
            addresses.add(rom.readPointer16(Sonic2ObjectPlacement.OFF_OBJECTS_REV01, index));
        // ObjectsManager_Init (s2.asm:32943-32952) selects the two lists.
        // Objects_CNZ1_2P / Objects_CNZ2_2P (s2.asm:33780-33798).
        addresses.add(0x1802A);
        addresses.add(0x18492);
        assertEquals(28, addresses.size(), "all distinct 1P and competition placement lists");
        for (int address : addresses) {
            var spawns = com.openggf.game.common.CommonPlacementParser.parseObjectRecords(rom, address);
            assertTrue(spawns.stream().noneMatch(spawn -> spawn.objectId() == 0x3C),
                    String.format("Obj3C placed at list 0x%06X", address));
            System.out.printf("Obj3C-placement-audit list=0x%06X records=%d matches=0%n", address, spawns.size());
        }
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
