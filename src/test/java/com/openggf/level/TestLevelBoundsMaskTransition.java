package com.openggf.level;

import com.openggf.graphics.ArenaMaskState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelBoundsMaskTransition {
    @Test void visiblePixelsFadeOnLockWhileExistingWingsStayOpaque() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(100, 700, 0), 0, 800);
        assertEquals(1, transition.sample().opacityAt(20));
        transition.advance(new ArenaMaskState(240, 560, 1), 0, 800);
        assertEquals(1, transition.sample().opacityAt(20));
        assertEquals(1f/23, transition.sample().opacityAt(150), 0.0001);
        var saved = transition.capture();
        for(int tick=2;tick<=23;tick++) transition.advance(new ArenaMaskState(240,560,tick),0,800);
        assertEquals(1,transition.sample().opacityAt(150),0.0001);
        transition.restore(saved);
        assertEquals(1f/23,transition.sample().opacityAt(150),0.0001);
        transition.advance(new ArenaMaskState(240,560,1),0,800);
        assertEquals(saved,transition.capture(),"pause/draw repeats must not advance fade");
        for(int tick=2;tick<=23;tick++) transition.advance(new ArenaMaskState(240,560,tick),0,800);
        assertEquals(1,transition.sample().opacityAt(150),0.0001);
        for(int tick=46;tick<=135;tick++) transition.advance(new ArenaMaskState(0,800,tick),0,800);
        assertFalse(transition.sample().visible(800));
    }
    @Test void movingCameraDoesNotTurnActivationFadeIntoASpatialWipe() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0, 800, 0), 1000, 800);
        for (int tick = 1; tick <= 23; tick++) {
            transition.advance(new ArenaMaskState(240 + tick * 8, 560 + tick * 8, tick), 1000 - tick * 8, 800);
            for (int x = 0; x < 228; x++) {
                assertEquals(Math.min(1f, tick / 23f), transition.sample().opacityAt(x), 0.000001,
                        "all already-visible left columns must fade together despite camera movement");
            }
            assertEquals(0, transition.sample().opacityAt(240 + tick * 8));
        }
    }

    @Test void movingCurrentBoundAndDestinationShareOneCompletionDeadline() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0,800,0),0,800);
        for (int tick=1; tick<=10; tick++) {
            transition.advance(new ArenaMaskState(tick*4,800,tick),0,800);
        }
        float existing = transition.sample().opacityAt(0);
        transition.advance(new ArenaMaskState(240,800,11),0,800);
        assertTrue(transition.sample().opacityAt(0) >= existing);
        assertEquals(1f/13, transition.sample().opacityAt(239), 0.000001);
        for (int tick=12; tick<=23; tick++) {
            transition.advance(new ArenaMaskState(240,800,tick),0,800);
        }
        for (int x=0; x<240; x++) assertEquals(1,transition.sample().opacityAt(x));
        assertEquals(0,transition.sample().opacityAt(240));
    }

    @Test void reversalUsesDisplayedOpacityAndEdgesHaveIndependentDeadlines() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0,800,0),0,800);
        for (int tick=1; tick<=10; tick++) transition.advance(new ArenaMaskState(240,560,tick),0,800);
        float displayed=transition.sample().opacityAt(20);
        transition.advance(new ArenaMaskState(0,560,11),0,800);
        assertEquals(displayed*22/23,transition.sample().opacityAt(20),0.000001);
        float releasing=transition.sample().opacityAt(20);
        transition.advance(new ArenaMaskState(240,560,12),0,800);
        assertEquals(releasing+(1-releasing)/23,transition.sample().opacityAt(20),0.000001);
        for(int tick=13;tick<=23;tick++) transition.advance(new ArenaMaskState(240,560,tick),0,800);
        assertEquals(1,transition.sample().opacityAt(799));
        assertTrue(transition.sample().opacityAt(20)<1);
    }

    @Test void worldProjectionRewindAndTeleportPreserveTheirContracts() {
        var transition=new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0,800,0),0,800);
        for(int tick=1;tick<=10;tick++) transition.advance(new ArenaMaskState(240,560,tick),0,800);
        var saved=transition.capture();
        transition.advance(new ArenaMaskState(232,552,11),8,800);
        var replay=transition.sample();
        assertEquals(11f/23,replay.opacityAt(231),0.000001);
        assertEquals(0,replay.opacityAt(232));
        transition.restore(saved);
        transition.advance(new ArenaMaskState(232,552,11),8,800);
        assertEquals(replay,transition.sample());
        transition.advance(new ArenaMaskState(0,800,12),10000,800);
        assertFalse(transition.sample().visible(800));
    }

    @Test void newlyExposedWorldColumnsInheritFadeRatherThanArrivingOpaque() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0,800,0),0,800);
        transition.advance(new ArenaMaskState(100,800,1),-100,800);
        assertEquals(1f/23,transition.sample().opacityAt(20),0.0001,
                "a newly covered world column fades instead of sweeping in opaque");
        assertEquals(0,transition.sample().opacityAt(120),"unmasked viewport column remains clear");
        transition.resetForMissingSnapshot();
        assertNull(transition.sample());
        var fresh = transition.capture();
        assertNotNull(fresh, "registry captures before the first prepared scene must be valid");
        transition.advance(new ArenaMaskState(100,700,2),0,800);
        transition.restore(fresh);
        assertNull(transition.sample());
    }
}
