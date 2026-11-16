package com.openggf.game.soniccd.zones.ppz;

import com.openggf.game.soniccd.*;
import com.openggf.level.scroll.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestPpzFrozenProjectionPhase {
    @Test void everyVariantProjectsWithoutAdvancingNativeRam() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            boolean bandsChanged = false;
            for (var payload : CdStagePayload.values()) {
                if (!payload.name().startsWith("R1")) continue;
                var h = new PpzScrollHandler(payload);
                h.init(0, 1024, 800);
                int[] lines = new int[224];
                h.update(lines, 1024, 800, 7, 0);
                var output = new ScrollProjection(lines, null, null, null, (short)800,
                        h.getVscrollFactorBG(), Integer.MIN_VALUE, 2048, 0, 0, h.getBgTilemapUpdateMode());
                var state = h.captureRewindState();
                var phase = h.captureProjection(new ScrollProjectionContext(1024,800,7,0,320,224,output));
                assertEquals(output, phase.sample(new ScrollView(1024,800,320,224)), payload.toString());
                var shifted = phase.sample(new ScrollView(1024,816,320,224));
                int verticalStep = payload.stage().actNumber() == 1
                        ? (payload.stage().period() == CdPeriod.PRESENT ? 16 : 2)
                        : (payload.stage().period() == CdPeriod.BAD_FUTURE && payload.stage().actNumber() == 2 ? 2 : 3);
                assertEquals(output.backgroundY() + verticalStep, shifted.backgroundY(), payload.toString());
                bandsChanged |= !java.util.Arrays.equals(lines, shifted.horizontal());
                var wide = phase.sample(new ScrollView(1040,816,640,448));
                assertEquals(448, wide.horizontal().length);
                assertEquals(wide.horizontal()[223], wide.horizontal()[447]);
                assertEquals(state,h.captureRewindState(), payload + " sampling must preserve clocks and RAM");
                assertEquals(output, phase.sample(new ScrollView(1024,800,320,224)));
            }
            assertTrue(bandsChanged, "vertical selection must change some bands");
        }
    }
    @Test void rampFactIsFrozenAndVerticalBandsMoveExactly() {
        int[] playerX = {0x900};
        var h = new PpzScrollHandler(CdPeriod.PRESENT, new PpzScrollHandler.PlayerView() {
            public int centreX() { return playerX[0]; }
            public int xSpeed() { throw new AssertionError("Sampling must not read velocity"); }
            public boolean on3dRamp() { throw new AssertionError("Sampling must not read control"); }
        });
        h.init(0,1024,800);
        int[] lines = new int[224];
        h.rebuildOutput(lines,1024,800,7,0);
        var output = new ScrollProjection(lines,null,null,null,(short)800,h.getVscrollFactorBG(),
                Integer.MIN_VALUE,2048,0,0,h.getBgTilemapUpdateMode());
        var phase = h.captureProjection(new ScrollProjectionContext(1024,800,7,0,320,224,output));
        playerX[0] = 0;
        var moved = phase.sample(new ScrollView(1024,816,320,224));
        assertEquals(output.backgroundY()+8,moved.backgroundY());
        assertArrayEquals(java.util.Arrays.copyOfRange(lines,8,224),
                java.util.Arrays.copyOf(moved.horizontal(),216));
        assertNotEquals(output.backgroundY()+16,moved.backgroundY());
        h.init(0,0,0);
        assertEquals(output,phase.sample(new ScrollView(1024,800,320,224)));
    }

}
