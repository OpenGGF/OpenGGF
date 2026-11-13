package com.openggf.game.sonic1.objects;

import com.openggf.game.GameStateManager;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic1EndingAnimationAdmission {
    @Test void animationSelectionWaitsForTheActualAnimatePass() {
        var state = mock(GameStateManager.class);
        when(state.getEmeraldCount()).thenReturn(6);
        var sonic = new Sonic1EndingSonicObjectInstance();
        sonic.setServices(new StubObjectServices() {
            @Override public GameStateManager gameState() { return state; }
        });
        for (int pass = 0; pass < 80; pass++) sonic.update(pass, null);
        assertEquals(0, sonic.getCurrentFrame(), "obAnim write must retain the old mapping");
        sonic.update(80, null);
        assertEquals(1, sonic.getCurrentFrame());
        for (int pass = 81; pass < 84; pass++) sonic.update(pass, null);
        assertEquals(1, sonic.getCurrentFrame(), "script delay 3 includes four object passes");
        sonic.update(84, null);
        assertEquals(0, sonic.getCurrentFrame());
    }
}
