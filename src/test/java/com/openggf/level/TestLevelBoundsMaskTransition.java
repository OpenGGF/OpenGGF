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

    @Test void borderColumnsWaitForTheirOuterNeighbourThenFadeIn() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0, 800, 0), 0, 800);
        for (int tick = 1; tick <= 45; tick++) {
            transition.advance(new ArenaMaskState(240, 560, tick), 0, 800);
        }
        assertEquals(1, transition.sample().opacityAt(227));
        for (int x = 228; x < 240; x++) assertEquals(0, transition.sample().opacityAt(x));
        for (int column = 0; column < 12; column++) {
            for (int frame = 1; frame <= 4; frame++) {
                int tick = 45 + column * 4 + frame;
                transition.advance(new ArenaMaskState(240, 560, tick), 0, 800);
                assertEquals(frame / 4f, transition.sample().opacityAt(228 + column));
                assertEquals(frame / 4f, transition.sample().opacityAt(571 - column));
                for (int inner = 229 + column; inner < 240; inner++) {
                    assertEquals(0, transition.sample().opacityAt(inner), "inner columns wait, not merely fade slower");
                }
            }
        }
        assertEquals(1, transition.sample().opacityAt(239));
        assertEquals(0, transition.sample().opacityAt(240));
        var saved = transition.capture();
        for (int tick = 94; tick <= 138; tick++) {
            transition.advance(new ArenaMaskState(0, 800, tick), 0, 800);
        }
        assertFalse(transition.sample().visible(800));
        transition.restore(saved);
        assertEquals(1, transition.sample().opacityAt(239));
    }

    @Test void fadeAndFeatherFollowWorldCoordinatesAndTeleportDoesNotCarryOldMask() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0, 800, 0), 0, 800);
        for (int tick = 1; tick <= 47; tick++) {
            transition.advance(new ArenaMaskState(240, 560, tick), 0, 800);
        }
        var saved = transition.capture();
        transition.advance(new ArenaMaskState(232, 552, 48), 8, 800);
        var replay = transition.sample();
        transition.restore(saved);
        transition.advance(new ArenaMaskState(232, 552, 48), 8, 800);
        assertEquals(replay, transition.sample(), "rewind resumes the pending border progression exactly");
        assertEquals(0.75f, transition.sample().opacityAt(220),
                "partially fading world column 228 follows the camera shift");
        assertEquals(0, transition.sample().opacityAt(221));
        transition.advance(new ArenaMaskState(0, 800, 3), 10000, 800);
        assertFalse(transition.sample().visible(800), "a distant new view has no overlapping fade history");
    }

    @Test void briefLockReversesImmediatelyAndCanReverseAgainFromCurrentOpacity() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0, 800, 0), 0, 800);
        for (int tick = 1; tick <= 47; tick++) {
            transition.advance(new ArenaMaskState(240, 560, tick), 0, 800);
        }
        float outer = transition.sample().opacityAt(20);
        float edge = transition.sample().opacityAt(228);
        transition.advance(new ArenaMaskState(0, 800, 48), 0, 800);
        assertTrue(transition.sample().opacityAt(20) < outer);
        assertTrue(transition.sample().opacityAt(228) < edge);
        float interruptedEdge = transition.sample().opacityAt(228);
        transition.advance(new ArenaMaskState(240, 560, 49), 0, 800);
        assertTrue(transition.sample().opacityAt(228) > interruptedEdge);
        assertEquals(interruptedEdge + 0.25f, transition.sample().opacityAt(228), 0.000001,
                "resuming starts at current opacity, not zero or the previous destination");
        for (int tick = 50; tick <= 94; tick++) {
            transition.advance(new ArenaMaskState(0, 800, tick), 0, 800);
        }
        assertFalse(transition.sample().visible(800));
    }

    @Test void cancellingBeforeTheBorderStartsNeverRevealsQueuedColumns() {
        var transition = new LevelBoundsMaskTransition();
        transition.advance(new ArenaMaskState(0, 800, 0), 0, 800);
        for (int tick = 1; tick <= 10; tick++) {
            transition.advance(new ArenaMaskState(240, 560, tick), 0, 800);
        }
        for (int tick = 11; tick <= 110; tick++) {
            transition.advance(new ArenaMaskState(0, 800, tick), 0, 800);
            for (int x = 228; x < 240; x++) assertEquals(0, transition.sample().opacityAt(x));
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
