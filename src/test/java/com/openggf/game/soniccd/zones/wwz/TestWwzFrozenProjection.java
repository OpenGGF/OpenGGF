package com.openggf.game.soniccd.zones.wwz;

import com.openggf.level.scroll.BgTilemapUpdateMode;
import com.openggf.level.scroll.ScrollProjection;
import com.openggf.level.scroll.ScrollProjectionContext;
import com.openggf.level.scroll.ScrollView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestWwzFrozenProjection {
    @Test
    void nativePublicationAndVerticalBandsRemainFrozen() throws Exception {
        try (var disc = com.openggf.game.soniccd.CdDiscDataSource.open(com.openggf.game.soniccd.CdTestMedia.usaCue())) {
        var loader = new com.openggf.game.soniccd.level.CdStageLoader(disc);
        for (var payload : new com.openggf.game.soniccd.CdStagePayload[] {
                com.openggf.game.soniccd.CdStagePayload.R61A, com.openggf.game.soniccd.CdStagePayload.R61B,
                com.openggf.game.soniccd.CdStagePayload.R62B, com.openggf.game.soniccd.CdStagePayload.R63D}) {
            int[] sine = WwzScrollHandler.readSine(payload, loader.readStage(payload));
            var h = new WwzScrollHandler(payload, sine);
            h.init(0, 512, 128);
            int[] nativeWords = new int[224];
            h.rebuildOutput(nativeWords, 512, 128, 7, 0);
            var output = new ScrollProjection(nativeWords, null, null, null, (short)128,
                    h.getVscrollFactorBG(), 0, 512, 0, 0, BgTilemapUpdateMode.STATIC_WINDOW);
            var phase = h.captureProjection(new ScrollProjectionContext(512,128,7,0,320,224,output));
            var nativeSample = phase.sample(new ScrollView(512,128,320,224));
            assertEquals(output, nativeSample);
            var before = h.captureRewindState();
            var tall = phase.sample(new ScrollView(512,128,320,448));
            assertArrayEquals(nativeWords, java.util.Arrays.copyOf(tall.horizontal(),224));
            var shifted = phase.sample(new ScrollView(512,160,320,448));
            int amount = (short)(shifted.backgroundY() - tall.backgroundY());
            assertEquals(8, amount);
            int changes = 0;
            for (int i=0; i<200; i++) {
                assertEquals((short)tall.horizontal()[i+amount], (short)shifted.horizontal()[i]);
                if ((short)tall.horizontal()[i] != (short)shifted.horizontal()[i]) changes++;
            }
            assertTrue(changes > 0, "bands must move relative to the screen");
            int[] originalSine = sine.clone();
            java.util.Arrays.fill(sine, 0);
            assertEquals(nativeSample, phase.sample(new ScrollView(512,128,320,224)));
            System.arraycopy(originalSine, 0, sine, 0, sine.length);
            int[] after = new int[224];
            h.rebuildOutput(after,512,128,7,0);
            assertArrayEquals(nativeWords,after);
            assertEquals(before, h.captureRewindState());
        }
        }
    }
}
