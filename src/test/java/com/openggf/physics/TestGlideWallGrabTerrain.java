package com.openggf.physics;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestGlideWallGrabTerrain {
    @Test
    void verticalClimbProbesUseTheNativeOffsetsAndSolidityBits() {
        var player = org.mockito.Mockito.mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        org.mockito.Mockito.when(player.getLrbSolidBit()).thenReturn((byte) 13);
        org.mockito.Mockito.when(player.getTopSolidBit()).thenReturn((byte) 12);
        org.mockito.Mockito.when(player.getXRadius()).thenReturn((short) 10);
        for (boolean reversed : List.of(false, true)) {
            for (boolean ledge : List.of(false, true)) {
                try (var sensors = org.mockito.Mockito.mockConstruction(GroundSensor.class)) {
                    GlideWallGrabTerrain.climbVerticalDistance(player, ledge, reversed);
                    boolean up = ledge != reversed;
                    // WithRadius applies XR after the caller's literal 8/9.
                    int offset = reversed ? (ledge ? 18 : -19) : (ledge ? -18 : 19);
                    org.mockito.Mockito.verify(sensors.constructed().getFirst()).scanWorld(
                            up ? Direction.UP : Direction.DOWN, (short) 0, (short) offset,
                            (short) 0, (short) 0, (byte) (ledge ? 13 : 12));
                }
            }
        }
    }

    @Test
    void verticalRadiusIsSignExtendedFromTheNativeByte() {
        var player = org.mockito.Mockito.mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        org.mockito.Mockito.when(player.getXRadius()).thenReturn((short) 0x80);
        org.mockito.Mockito.when(player.getLrbSolidBit()).thenReturn((byte) 15);
        org.mockito.Mockito.when(player.getTopSolidBit()).thenReturn((byte) 14);
        try (var sensors = org.mockito.Mockito.mockConstruction(GroundSensor.class)) {
            GlideWallGrabTerrain.climbVerticalDistance(player, true, false);
            org.mockito.Mockito.verify(sensors.constructed().getFirst()).scanWorld(
                    Direction.UP, (short) 0, (short) 120, (short) 0, (short) 0, 15);
            GlideWallGrabTerrain.climbVerticalDistance(player, false, false);
            org.mockito.Mockito.verify(sensors.constructed().getLast()).scanWorld(
                    Direction.DOWN, (short) 0, (short) -119, (short) 0, (short) 0, 14);
        }
    }

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
