package com.openggf.game.sonic3k.specialstage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic3kSpecialStageBanner {
    @Test
    void automaticStartTestsOffsetBeforeIncrementingIt() {
        Sonic3kSpecialStageBanner banner = new Sonic3kSpecialStageBanner();
        banner.initialize();
        for (int tick = 0; tick < 180; tick++) {
            assertFalse(banner.update());
            assertEquals(0, banner.getSlideOffset());
        }
        for (int offset = 0x10; offset <= 0xC0; offset += 0x10) {
            assertFalse(banner.update(), "loc_8ECA moves without starting the player");
            assertEquals(offset, banner.getSlideOffset());
        }
        assertTrue(banner.update(), "loc_8EA4 sees the previous update's $C0 offset");
        assertEquals(Sonic3kSpecialStageBanner.Phase.IDLE, banner.getPhase());
        assertFalse(banner.update(), "start is signalled only once");
    }
    @Test
    void bannerStartBecomesVisibleToMovementOnTheNextDispatch() throws Exception {
        var manager = new Sonic3kSpecialStageManager();
        for (String name : new String[]{"initialized", "stagePalettesUploaded"}) {
            var field = Sonic3kSpecialStageManager.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(manager, true);
        }
        manager.getBanner().initialize();
        manager.getPlayer().initialize(0, 0x1000, 0x1000, false);
        for (int tick = 0; tick < 193; tick++) manager.update();
        assertTrue(manager.getPlayer().isStarted());
        assertEquals(0, manager.getPlayer().getVelocity(), "Player_1 already ran before banner start");
        manager.update();
        assertEquals(0x200, manager.getPlayer().getVelocity());
    }
}
