package com.openggf.game.soniccd.zones.qqz;

import com.openggf.level.scroll.BgTilemapUpdateMode;
import com.openggf.level.scroll.ScrollProjection;
import com.openggf.level.scroll.ScrollProjectionContext;
import com.openggf.level.scroll.ScrollView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TestQqzFrozenProjection {
    @Test
    void nativePublicationAndVerticalBandsRemainFrozen() throws Exception {
        for (var program : QqzScrollHandler.Program.values()) {
            for (int swapPlayer : new int[] {0, 0x1000, 0x2400}) {
                int[] player = {swapPlayer};
                var h = new QqzScrollHandler(program, () -> player[0]);
                h.init(0, 512, 128);
                int[] nativeWords = new int[224];
                h.rebuildOutput(nativeWords, 512, 128, 7, 0);
                var output = new ScrollProjection(nativeWords, null, null, null, (short)128,
                        h.getVscrollFactorBG(), 0, 512, 0, 0, BgTilemapUpdateMode.STATIC_WINDOW);
                var phase = h.captureProjection(new ScrollProjectionContext(512,128,7,0,320,224,output));
                var nativeSample = phase.sample(new ScrollView(512,128,320,224));
                assertEquals(output, nativeSample);
                int frozenChange = h.backgroundChange();
                Object frozenState = h.captureRewindState();
                var tall = phase.sample(new ScrollView(512,128,320,448));
                assertArrayEquals(nativeWords, java.util.Arrays.copyOf(tall.horizontal(),224));
                var shifted = phase.sample(new ScrollView(512,160,320,448));
                int amount = (short)(shifted.backgroundY() - tall.backgroundY());
                int expectedAmount = switch (program) {
                    case A_ACT1 -> (frozenChange & 1) != 0 ? 8 : 17;
                    case A_ACT2 -> (frozenChange & 1) != 0 ? 8 : 22;
                    case B -> 6;
                    case C, C_ACT3 -> 8;
                    case D, D_ACT3 -> 7;
                };
                assertEquals(expectedAmount, amount);
                int changes = 0;
                for (int i=0; i<200; i++) {
                    assertEquals((short)tall.horizontal()[i+amount], (short)shifted.horizontal()[i]);
                    if ((short)tall.horizontal()[i] != (short)shifted.horizontal()[i]) changes++;
                }
                assertTrue(changes > 0, "bands must move relative to the screen");
                player[0] = 0x2500; h.setSwapListener(state -> fail("sampling dispatched a swap"));
                assertEquals(nativeSample, phase.sample(new ScrollView(512,128,320,224)));
                int[] after = new int[224];
                h.rebuildOutput(after,512,128,7,0);
                assertArrayEquals(nativeWords,after);
                assertEquals(frozenChange, h.backgroundChange());
                Object afterState = h.captureRewindState();
                for (var component : frozenState.getClass().getRecordComponents()) {
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    Object expected = accessor.invoke(frozenState);
                    Object actual = accessor.invoke(afterState);
                    if (expected instanceof short[] words) assertArrayEquals(words, (short[]) actual);
                    else assertEquals(expected, actual, component.getName());
                }
            }
        }

    }
}
