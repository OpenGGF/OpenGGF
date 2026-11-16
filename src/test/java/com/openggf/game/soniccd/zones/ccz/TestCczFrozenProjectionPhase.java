package com.openggf.game.soniccd.zones.ccz;

import com.openggf.game.soniccd.*;
import com.openggf.level.scroll.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCczFrozenProjectionPhase {
    @Test void everyVariantProjectsWithoutAdvancingNativeRam() throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            boolean bandsChanged = false;
            for (var payload : CdStagePayload.values()) {
                if (!payload.name().startsWith("R3")) continue;
                var image = new com.openggf.game.soniccd.level.CdStageImage(disc.readFile(payload.file()));
                boolean[] readable = {true};
                var h = new CczScrollHandler(payload, () -> {
                    assertTrue(readable[0], "sampling must not consult stage owner");
                    return image;
                });
                h.init(0, 1024, 800);
                int[] lines = new int[224];
                h.update(lines, 1024, 800, 7, 0);
                var output = new ScrollProjection(lines, null, null, null, (short)800,
                        h.getVscrollFactorBG(), Integer.MIN_VALUE, 2048, 0, 0, h.getBgTilemapUpdateMode());
                var state = h.captureRewindState();
                var phase = h.captureProjection(new ScrollProjectionContext(1024,800,7,0,320,224,output));
                readable[0] = false;
                assertEquals(output, phase.sample(new ScrollView(1024,800,320,224)), payload.toString());
                var shifted = phase.sample(new ScrollView(1024,816,320,224));
                assertEquals(output.backgroundY() + 8, shifted.backgroundY(), payload.toString());
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
}
