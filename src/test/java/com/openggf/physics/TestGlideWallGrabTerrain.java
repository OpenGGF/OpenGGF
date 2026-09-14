package com.openggf.physics;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestGlideWallGrabTerrain {
    @Test
    void bothWallEndsMustFitExactlyAndOnlyLeftAddsOnePixel() {
        for (boolean right : List.of(false, true)) {
            var probes = new ArrayList<String>();
            var fit = GlideWallGrabTerrain.findAlignment(10, 10, right, false, (d, x, y) -> {
                probes.add(d + ":" + x + ":" + y);
                return 0;
            });
            assertEquals(new GlideWallGrabTerrain.Alignment(right ? 0 : 1, 0), fit);
            assertEquals(right ? List.of("RIGHT:10:-10", "RIGHT:10:10")
                    : List.of("LEFT:-10:-10", "LEFT:-10:10"), probes);
        }
    }

    @Test
    void ledgeFitAcceptsZeroThroughElevenButRejectsPenetrationAndTwelve() {
        for (int floor : new int[] {-1, 0, 11, 12, Integer.MAX_VALUE}) {
            for (boolean right : List.of(false, true)) {
                var probes = new ArrayList<String>();
                var fit = GlideWallGrabTerrain.findAlignment(10, 10, right, false, (d, x, y) -> {
                    probes.add(d + ":" + x + ":" + y);
                    return d == Direction.DOWN ? floor : 1;
                });
                assertEquals("DOWN:" + (right ? 11 : -11) + ":-11", probes.get(2));
                if (floor >= 0 && floor < 12) assertEquals(new GlideWallGrabTerrain.Alignment(0, floor), fit);
                else assertNull(fit);
            }
        }
    }

    @Test
    void oneFittingWallEndStillRequiresTheLedgeProbeAndReverseGravityMirrorsIt() {
        var probes = new ArrayList<String>();
        var fit = GlideWallGrabTerrain.findAlignment(9, 19, false, true, (d, x, y) -> {
            probes.add(d + ":" + x + ":" + y);
            if (d == Direction.UP) return 7;
            return y < 0 ? 0 : 1;
        });
        assertEquals(List.of("LEFT:-19:-9", "LEFT:-19:9", "UP:-20:11"), probes);
        assertEquals(new GlideWallGrabTerrain.Alignment(0, -7), fit);
    }
}
