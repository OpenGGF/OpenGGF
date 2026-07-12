package com.openggf.game.sonic2.titlecard;

import com.openggf.game.ZoneKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.openggf.game.sonic2.Sonic2ZoneRegistry;

class TestModZoneTitleCardPolicy {
    @Test
    void configuredFallbackSkipsOnlyModZones() {
        assertTrue(TitleCardManager.shouldSkipTitleCard(ZoneKey.mod("owner", "zone"), true));
        assertFalse(TitleCardManager.shouldSkipTitleCard(ZoneKey.stock(3), true));
        assertFalse(TitleCardManager.shouldSkipTitleCard(ZoneKey.mod("owner", "zone"), false));
    }

    @Test
    void productionInitializationCompletesModCardBeforeAnyArtLoadAndReadsStockRegistryName() {
        var stock = new Sonic2ZoneRegistry();
        com.openggf.game.ZoneRegistry decorated = new com.openggf.game.ZoneRegistry() {
            public int getZoneCount() { return 12; }
            public int getActCount(int zone) { return 1; }
            public String getZoneName(int zone) { return zone == 0 ? "REGISTRY EHZ" : zone == 11 ? "MOD ZONE" : stock.getZoneName(zone); }
            public int[] getStartPosition(int zone, int act) { return new int[]{0, 0}; }
            public java.util.List<com.openggf.level.LevelDescriptor> getLevelDataForZone(int zone) { return java.util.List.of(); }
            public java.util.List<java.util.List<com.openggf.level.LevelDescriptor>> getAllZones() { return stock.getAllZones(); }
            public int getMusicId(int zone, int act) { return 0; }
            public ZoneKey zoneKey(int zone) { return zone == 11 ? ZoneKey.mod("owner", "zone") : ZoneKey.stock(zone); }
        };
        TitleCardManager manager = new TitleCardManager(() -> decorated, () -> true);
        manager.initialize(11, 0);
        assertTrue(manager.isComplete());
        assertEquals("REGISTRY EHZ", manager.registryZoneName(0));
    }
}
