package com.openggf.game.sonic2.competition.scroll;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSwScrlCnz2P extends Scroll2PFixture {
    @Test void sameSeparatedAndReversedCamerasShippedP2Segments() {
        for (int[] pair : new int[][]{{256,256},{256,512},{512,256},{0,256}}) {
            scroll.update(Zone.CNZ,0,camera(pair[0],0),camera(pair[1],0),0);
            check(scroll.view(0),pair[0],false);
            check(scroll.view(1),pair[1],true);
        }
    }
    private void check(View v,int x,boolean p2) {
        // X256: seven gradient words 256,228,200,172,144,116,88;
        // then 16,16,32. Shipped P2 consumes index9 for ripple, index10
        // (unwritten, initially zero) for its final band.
        int n=x/256;
        if (!p2) bands(v,-x,8,-256*n,8,-228*n,8,-200*n,8,-172*n,8,-144*n,
                8,-116*n,8,-88*n,8,-16*n,
                1,-16*n+1,1,-16*n+2,1,-16*n+1,1,-16*n+3,
                1,-16*n+1,1,-16*n+2,1,-16*n+2,1,-16*n+1,36,-32*n);
        else bands(v,-x,4,-256*n,8,-228*n,8,-200*n,8,-172*n,8,-144*n,
                8,-116*n,8,-88*n,8,-16*n,8,-16*n,
                1,-32*n+1,1,-32*n+2,1,-32*n+1,1,-32*n+3,
                1,-32*n+1,1,-32*n+2,1,-32*n+2,1,-32*n+1,40,0);
    }
    @Test void verticalBoundaryMidScrollAndRetainedScratchRewind() {
        scroll.update(Zone.MCZ,0,camera(160,960),camera(160,960),1);
        // MCZ left scratch[10]=128: CNZ must retain it, including at level load.
        scroll.initializeLevel(Zone.CNZ,BackgroundBank.ZERO,BackgroundBank.ZERO);
        scroll.update(Zone.CNZ,0,camera(257,1024),camera(512,512),8);
        assertEquals(word(-257,-228),scroll.view(0).lines()[0]);
        assertEquals(word(-512,-456),scroll.view(1).lines()[0]);
        assertEquals(word(-512,-128),scroll.view(1).lines()[115]);
        assertEquals(word(-257,-13),scroll.view(0).lines()[56]);
        assertEquals(word(-512,-61),scroll.view(1).lines()[64]);
        assertEquals(16,scroll.view(0).backgroundVScroll());
        assertEquals(-216,scroll.view(1).backgroundVScroll());
        var saved=scroll.capture();
        scroll.update(Zone.CNZ,0,camera(0,2048),camera(0,0),248);
        scroll.restore(saved);
        assertArrayEquals(saved.horizontal(),scroll.capture().horizontal());
        assertArrayEquals(saved.scratch(),scroll.capture().scratch());
        assertEquals(16<<16,scroll.view(0).background().y());
        assertEquals(8<<16,scroll.view(1).background().y());
        // At the zero-height marker the initial search skips the ripple entirely.
        scroll.update(Zone.CNZ,0,camera(256,8192),camera(512,8704),248);
        bands(scroll.view(0),-256,108,-32);
        bands(scroll.view(1),-512,116,-128);
        scroll.initializeLevel(Zone.EHZ,BackgroundBank.ZERO,BackgroundBank.ZERO);
        assertArrayEquals(new short[6],java.util.Arrays.copyOf(scroll.capture().scratch(),6));
        assertEquals(128,scroll.capture().scratch()[10]);
    }
}
