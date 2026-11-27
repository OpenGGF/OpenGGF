package com.openggf.game.soniccd;

import com.openggf.game.modzone.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCdModZones {
    @Test void ownedFlatTerrainUsesTheCdCollisionShape() throws Exception {
        var data = new ModZoneLevelData(1,64,16,1,1,0,255,0,255,
                new byte[32],new byte[8],new byte[1024],new byte[]{1},new byte[]{0},
                new byte[32],new byte[32],new byte[]{0,0},new int[]{1},new int[]{1},
                new byte[][]{new byte[32],new byte[32],new byte[32],new byte[32]},
                Optional.empty(),List.of(),List.of(),List.of(),1,1,2);
        var adapter = new SonicCdGameModule().getModZoneAdapter();
        assertDoesNotThrow(() -> adapter.validate("cd-example", data));
        var level = adapter.load("cd-example", data);
        assertEquals(64, level.getZoneIndex());
        assertEquals(ModZoneRuntimeProfile.flatEmpty(), adapter.runtimeProfile("cd-example", data));
    }

    @Test void ringFreeAuthoredObjectsReachTheStoredOrderSpawnerSortedByX() throws Exception {
        var codec = new CdObjectPlacementEncoding();
        var right = codec.create(200, 100, 0x0A, 0, 0, false, 0);
        var left = codec.create(40, 100, 0x0A, 0, 0, false, 1);
        var middle = codec.create(120, 100, 0x0A, 0, 0, false, 2);
        var data = new ModZoneLevelData(1,64,16,1,1,0,255,0,255,
                new byte[32],new byte[8],new byte[1024],new byte[]{1},new byte[]{0},
                new byte[32],new byte[32],new byte[]{0,0},new int[]{1},new int[]{1},
                new byte[][]{new byte[32],new byte[32],new byte[32],new byte[32]},
                Optional.empty(),List.of(),List.of(right, left, middle),List.of(),1,1,2);
        var level = new SonicCdGameModule().getModZoneAdapter().load("cd-example", data);
        assertEquals(List.of(left, middle, right), level.getObjects(),
                "without rings, the spawner would stop at the first entry past its window and never reach the others");
    }
}
