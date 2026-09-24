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
        assertEquals(1f/45, transition.sample().opacityAt(150), 0.0001);
        var saved = transition.capture();
        for(int tick=2;tick<=45;tick++) transition.advance(new ArenaMaskState(240,560,tick),0,800);
        assertEquals(1,transition.sample().opacityAt(150),0.0001);
        transition.restore(saved);
        assertEquals(1f/45,transition.sample().opacityAt(150),0.0001);
        transition.advance(new ArenaMaskState(240,560,1),0,800);
        assertEquals(saved,transition.capture(),"pause/draw repeats must not advance fade");
        for(int tick=2;tick<=45;tick++) transition.advance(new ArenaMaskState(240,560,tick),0,800);
        assertEquals(1,transition.sample().opacityAt(150),0.0001);
        for(int tick=46;tick<=90;tick++) transition.advance(new ArenaMaskState(0,800,tick),0,800);
        assertFalse(transition.sample().visible(800));
    }
    @Test void newlyExposedWorldPixelsAreMaskedImmediatelyAndMovementPreservesHistory() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0,800,0),0,800);
        transition.advance(new ArenaMaskState(100,800,1),-100,800);
        assertEquals(1,transition.sample().opacityAt(20),"new pixels were never visible unmasked");
        assertEquals(0,transition.sample().opacityAt(120),"same world point remains clear");
        transition.resetForMissingSnapshot();
        assertNull(transition.sample());
        var fresh = transition.capture();
        assertNotNull(fresh, "registry captures before the first prepared scene must be valid");
        transition.advance(new ArenaMaskState(100,700,2),0,800);
        transition.restore(fresh);
        assertNull(transition.sample());
    }
}
