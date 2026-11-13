package com.openggf.game.sonic1.objects;

import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic1EndingLogoFallthrough {
    @Test void reachingTheLogoDestinationAlsoConsumesTheFirstDelayTick() throws Exception {
        int[] transitions = {0};
        var logo = new Sonic1EndingSTHObjectInstance();
        logo.setServices(new StubObjectServices() {
            @Override public void requestCreditsTransition() { transitions[0]++; }
        });
        for (int pass = 0; pass < 15; pass++) logo.update(pass, null);
        var timer = Sonic1EndingSTHObjectInstance.class.getDeclaredField("timer"); timer.setAccessible(true);
        assertEquals(299, timer.getInt(logo), "ESth_Delay falls into ESth_GotoCredits");
        for (int pass = 0; pass < 299; pass++) logo.update(pass, null);
        assertEquals(0, transitions[0]);
        logo.update(0, null);
        assertEquals(1, transitions[0]);
        assertFalse(logo.isDestroyed());
    }
}
