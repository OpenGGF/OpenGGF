package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestLrzBossLavaSurface {
    @Test void slopeGrowthDecaySignedSamplesAndFlatModeFollowNativeDispatch() {
        var state = new LrzZoneRuntimeState(22, 0, PlayerCharacter.SONIC_ALONE);
        var services = new TestObjectServices().withIsolatedObjectManager();
        services.zoneRuntimeRegistry().install(state);
        var lava = new LrzBossLavaSurfaceObjectInstance(); lava.setServices(services);
        lava.update(0, null);
        assertNull(lava.getSlopeData()); assertEquals(0xB80, lava.getX());
        assertEquals(0x180, lava.getSolidParams().halfWidth());
        assertEquals(0x40, lava.getSolidParams().airHalfHeight());
        assertEquals(0x30, lava.getSolidParams().groundHalfHeight());
        state.setBackgroundRoutine(12); state.bossAct().setLavaDirection(1);
        for (int i=1;i<=140;i++) {
            lava.update(i, null);
            assertEquals(Math.min(i,128), state.bossAct().lavaAmplitude());
            assertEquals(-Math.min(i,128)*768, state.bossAct().lavaFlow());
        }
        assertEquals(0xAA0, lava.getX()); assertEquals(0x640, lava.getY());
        assertEquals(0xA0, lava.getSolidParams().halfWidth());
        assertEquals(48, lava.sampleSlopeByte(0));
        assertEquals(-121, lava.sampleSlopeByte(175), "native ext.w sign-extends the wrapped sample");
        state.bossAct().setLavaDirection(0xFF00);
        lava.update(141,null);
        assertEquals(127,state.bossAct().lavaAmplitude()); assertEquals(127*768,state.bossAct().lavaFlow());
        assertEquals(48+159*127/256,lava.sampleSlopeByte(0));
        byte[] retained=lava.getSlopeData().clone();
        state.setBackgroundRoutine(16); lava.update(142,null);
        assertNull(lava.getSlopeData()); assertEquals(127,state.bossAct().lavaAmplitude());
        for(int i=0;i<retained.length;i++) assertEquals(retained[i],(byte)state.bossAct().lavaHeight(16+i));
        state.setBackgroundRoutine(12);
        for(int i=0;i<140;i++) lava.update(i,null);
        assertEquals(0,state.bossAct().lavaAmplitude()); assertEquals(0,state.bossAct().lavaFlow());
    }
}
