package com.openggf.game.sonic2.competition.scroll;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSwScrlEhz2P extends Scroll2PFixture {
    @Test void sameSeparatedAndReversedCameras() {
        // SwScrl_EHZ_2P .doBackground: at X=256, negX=-256; far=-4,
        // middle=-16,-24; slope=(-128+32)*256/48=-512, then *256.
        for (int[] pair : new int[][]{{256,256},{256,512},{512,256},{0,256}}) {
            scroll.update(Zone.EHZ, 0, camera(pair[0], 3), camera(pair[1], 227), 1);
            check(scroll.view(0), pair[0], 11, 36);
            check(scroll.view(1), pair[1], 15, 40);
            assertEquals(2, scroll.view(0).foregroundVScroll());
            assertEquals(2, scroll.view(1).foregroundVScroll());
            assertEquals(-224, scroll.view(1).backgroundVScroll());
        }
    }
    private void check(View view, int x, int sky, int slopeLines) {
        // Hand-derived complete words, ripple phase zero: 1,2,1,3,1,2,2,1,2,3,1.
        int[] expected = new int[view.rasterHeight()]; int p = 0;
        for (int i=0;i<sky;i++) expected[p++]=word(-x,0);
        for (int i=0;i<29;i++) expected[p++]=word(-x,-x/64);
        for (int r : new int[]{1,2,1,3,1,2,2,1,2,3,1}) expected[p++]=word(-x,-x/64+r);
        for (int i=0;i<5;i++) expected[p++]=word(-x,0);
        for (int i=0;i<8;i++) expected[p++]=word(-x,-x/16);
        for (int i=0;i<8;i++) expected[p++]=word(-x,-3*x/32);
        for (int i=0;i<slopeLines;i++) expected[p++]=word(-x,-x/8-i*x/128);
        assertArrayEquals(expected,view.lines());
    }
    @Test void phaseBoundaryBanksAndRewind() {
        var a = new BackgroundBank(0x123456,0x0003abcd,7,8,9,10);
        var b = new BackgroundBank(22,0x0009dcba,17,18,19,20);
        scroll.initialize(a,b);
        scroll.update(Zone.EHZ,0,camera(257,7),camera(511,229),7);
        var before = scroll.capture();
        scroll.update(Zone.EHZ,0,camera(257,7),camera(511,229),8);
        // TempArray decrements to -1: phase31 ripple starts 3,1,2,1...
        assertEquals(word(-257,-2),scroll.view(0).lines()[40]);
        var after = scroll.capture();
        scroll.restore(before);
        assertEquals(a,scroll.view(0).background()); assertEquals(b,scroll.view(1).background());
        scroll.update(Zone.EHZ,0,camera(257,7),camera(511,229),8);
        assertArrayEquals(after.horizontal(),scroll.capture().horizontal());
        assertArrayEquals(after.scratch(),scroll.capture().scratch());
        assertEquals(2,scroll.view(0).backgroundVScroll());
        assertEquals(-224,scroll.view(1).backgroundVScroll());
    }
}
