package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

/** ROM timing contract for {@code Obj_LRZ3Autoscroll}'s flash and route signals. */
class TestLrz3AutoscrollController {
    @Test
    void flashDrivesPaletteGateTerrainAndShipTimers() {
        LrzZoneRuntimeState state = new LrzZoneRuntimeState(
                Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ, 0, PlayerCharacter.SONIC_ALONE);
        Lrz3AutoscrollControllerObjectInstance object = new Lrz3AutoscrollControllerObjectInstance(
                new ObjectSpawn(0, 0, 0x9E, 0, 0, false, 0));
        StubObjectServices services = new StubObjectServices() {
            @Override public ZoneRuntimeState zoneRuntimeState() { return state; }
        };
        services.withPlayerQuery(new ObjectPlayerQuery(() -> null, List::of));
        object.setServices(services);

        advance(object, 60);
        assertEquals(1, object.stageForTest(), "60-frame entry lock starts the flash animation");
        advance(object, 30);
        assertEquals(2, object.stageForTest());
        assertEquals(0x80, state.lrz3PaletteCycleGate(), "loc_79416 pauses palette cycling");
        advance(object, 29);
        assertEquals(3, object.stageForTest());
        assertEquals(1, state.lrz3PaletteCycleGate(), "loc_79486 enables the accent channel");
        assertTrue(state.cutsceneFlag(0));
        assertEquals(0xFFFF, state.lrz3TerrainRequest());

        state.setLrz3TerrainRequest(0);
        advance(object, 0x120);
        assertEquals(4, object.stageForTest());
        assertTrue(state.cutsceneFlag(1));
        advance(object, 0xC0);
        assertEquals(0xFFFF, state.lrz3TerrainRequest());
    }

    private static void advance(Lrz3AutoscrollControllerObjectInstance object, int frames) {
        for (int i = 0; i < frames; i++) object.update(i, null);
    }
}
