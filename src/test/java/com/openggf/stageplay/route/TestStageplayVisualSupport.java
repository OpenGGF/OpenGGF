package com.openggf.stageplay.route;

import com.openggf.physics.RouteTerrainProbe;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayVisualSupport {
    @Test void restingUsesFinalRecheckAndPassesTheUnchangedRouteStartRule() {
        var terrain = new RouteTerrainProbe() {
            public Floor floor(int x, int y, int path) {
                return new Floor(true, 100 + x / 2, x & 255);
            }
            public boolean clear(int l, int t, int r, int b, int path) { return b <= 104; }
            public boolean dynamicCollision() { return false; }
        };
        var f = new StageplayRouteFootprint(2, 20, 14, 0);
        var rest = assertInstanceOf(StageplayVisualSupport.Resting.class,
                StageplayVisualSupport.rest(terrain, f, 10, 105));
        var support = rest.support();
        assertEquals(90, support.anchorY());
        assertEquals(support, StageplayVisualSupport.support(terrain, f, 10, support.anchorY()));
        assertTrue(StageplayVisualSupport.clear(terrain, f, 10, support.anchorY()));
        assertEquals("", StageplayObjectRoutePlanner.validateSample(terrain, f, 10, support.anchorY()));
    }

    @Test void aSeedDependentSecondAnchorIsRefusedInsteadOfClaimingSupport() {
        var terrain = new RouteTerrainProbe() {
            public Floor floor(int x, int y, int path) { return new Floor(true, y - 1, 0); }
            public boolean clear(int l, int t, int r, int b, int path) { return true; }
            public boolean dynamicCollision() { return false; }
        };
        assertInstanceOf(StageplayVisualSupport.Unstable.class, StageplayVisualSupport.rest(terrain,
                new StageplayRouteFootprint(2, 20, 14, 0), 10, 100));
    }
}
