package com.openggf.game.sonic3k.titlecard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Title-card zone/act overrides for the shared $16/$17 slots (sonic3k.asm:62141-62146, 62336-62396). */
class TestSonic3kTitleCardSublevelMappings {

    @Test
    void hiddenPalaceUsesItsNameAndArtWithoutAnActNumber() {
        assertEquals(Sonic3kTitleCardMappings.FRAME_HPZ, Sonic3kTitleCardMappings.getZoneFrame(22, 1));
        assertEquals(13, Sonic3kTitleCardMappings.zoneArtIndex(22, 1));
        assertTrue(Sonic3kTitleCardMappings.isSingleActZone(22, 1));
    }

    @Test
    void lavaReefBossActUsesTheLavaReefCardWithAnActNumber() {
        assertEquals(Sonic3kTitleCardMappings.FRAME_LRZ, Sonic3kTitleCardMappings.getZoneFrame(22, 0));
        assertEquals(9, Sonic3kTitleCardMappings.zoneArtIndex(22, 0));
        assertFalse(Sonic3kTitleCardMappings.isSingleActZone(22, 0));
    }

    @Test
    void deathEggBossActUsesTheDeathEggCard() {
        assertEquals(Sonic3kTitleCardMappings.FRAME_DEZ, Sonic3kTitleCardMappings.getZoneFrame(23, 0));
        assertEquals(11, Sonic3kTitleCardMappings.zoneArtIndex(23, 0));
    }

    @Test
    void ordinaryZonesKeepApparentZoneAndSkyDoomsdayHideActs() {
        assertEquals(Sonic3kTitleCardMappings.FRAME_SOZ, Sonic3kTitleCardMappings.getZoneFrame(8, 1));
        assertEquals(8, Sonic3kTitleCardMappings.zoneArtIndex(8, 1));
        assertTrue(Sonic3kTitleCardMappings.isSingleActZone(10, 0));
        assertTrue(Sonic3kTitleCardMappings.isSingleActZone(12, 1));
        assertFalse(Sonic3kTitleCardMappings.isSingleActZone(8, 0));
    }
}
