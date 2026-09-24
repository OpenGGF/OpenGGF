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
        for(int tick=46;tick<=135;tick++) transition.advance(new ArenaMaskState(0,800,tick),0,800);
        assertFalse(transition.sample().visible(800));
    }
    @Test void movingCameraDoesNotTurnActivationFadeIntoASpatialWipe() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0, 800, 0), 1000, 800);
        for (int tick = 1; tick <= 45; tick++) {
            transition.advance(new ArenaMaskState(240 + tick * 8, 560 + tick * 8, tick), 1000 - tick * 8, 800);
            for (int x = 0; x < 228; x++) {
                assertEquals(Math.min(1f, tick / 45f), transition.sample().opacityAt(x), 0.000001,
                        "all already-visible left columns must fade together despite camera movement");
            }
            assertEquals(0, transition.sample().opacityAt(240 + tick * 8));
        }
    }

    @Test void twelvePixelFeatherLagsActivationAndReleaseWithoutLeavingAPermanentGap() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0, 800, 0), 0, 800);
        for (int tick = 1; tick <= 45; tick++) {
            transition.advance(new ArenaMaskState(240, 560, tick), 0, 800);
        }
        var halfway = transition.capture();
        assertEquals(1, transition.sample().opacityAt(227));
        assertTrue(transition.sample().opacityAt(233) < transition.sample().opacityAt(228));
        assertTrue(transition.sample().opacityAt(239) < transition.sample().opacityAt(233));
        assertEquals(transition.sample().opacityAt(239), transition.sample().opacityAt(560));
        assertEquals(0, transition.sample().opacityAt(240));
        halfway.fadeRates()[239] = 1; // Captured history cannot be mutated by a consumer.
        for (int tick = 46; tick <= 90; tick++) {
            transition.advance(new ArenaMaskState(240, 560, tick), 0, 800);
        }
        assertEquals(1, transition.sample().opacityAt(239));
        for (int tick = 91; tick <= 135; tick++) {
            transition.advance(new ArenaMaskState(0, 800, tick), 0, 800);
        }
        assertEquals(0, transition.sample().opacityAt(227));
        assertTrue(transition.sample().opacityAt(239) > transition.sample().opacityAt(233));
        for (int tick = 136; tick <= 180; tick++) {
            transition.advance(new ArenaMaskState(0, 800, tick), 0, 800);
        }
        assertFalse(transition.sample().visible(800));
        transition.restore(halfway);
        transition.advance(new ArenaMaskState(0, 800, 46), 0, 800);
        assertTrue(transition.sample().opacityAt(239) > 0.45,
                "rewind restores the slow release rate as well as opacity");
    }

    @Test void fadeAndFeatherFollowWorldCoordinatesAndTeleportDoesNotCarryOldMask() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0, 800, 0), 0, 800);
        transition.advance(new ArenaMaskState(240, 560, 1), 0, 800);
        float edge = transition.sample().opacityAt(239);
        transition.advance(new ArenaMaskState(232, 552, 2), 8, 800);
        assertEquals(2 * edge, transition.sample().opacityAt(231), 0.000001,
                "the slow feather belongs to world column 239, now viewport column 231");
        transition.advance(new ArenaMaskState(0, 800, 3), 10000, 800);
        assertFalse(transition.sample().visible(800), "a distant new view has no overlapping fade history");
    }

    @Test void briefLockReversesImmediatelyAndCanReverseAgainFromCurrentOpacity() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0, 800, 0), 0, 800);
        for (int tick = 1; tick <= 10; tick++) {
            transition.advance(new ArenaMaskState(240, 560, tick), 0, 800);
        }
        float outer = transition.sample().opacityAt(20);
        float edge = transition.sample().opacityAt(239);
        transition.advance(new ArenaMaskState(0, 800, 11), 0, 800);
        assertTrue(transition.sample().opacityAt(20) < outer);
        assertTrue(transition.sample().opacityAt(239) < edge);
        float interruptedEdge = transition.sample().opacityAt(239);
        transition.advance(new ArenaMaskState(240, 560, 12), 0, 800);
        assertTrue(transition.sample().opacityAt(239) > interruptedEdge);
        assertEquals(edge, transition.sample().opacityAt(239), 0.000001,
                "resuming starts at current opacity, not zero or the previous destination");
        for (int tick = 13; tick <= 23; tick++) {
            transition.advance(new ArenaMaskState(0, 800, tick), 0, 800);
        }
        assertFalse(transition.sample().visible(800));
    }

    @Test void newlyExposedWorldColumnsInheritFadeRatherThanArrivingOpaque() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0,800,0),0,800);
        transition.advance(new ArenaMaskState(100,800,1),-100,800);
        assertEquals(1f/45,transition.sample().opacityAt(20),0.0001,
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
