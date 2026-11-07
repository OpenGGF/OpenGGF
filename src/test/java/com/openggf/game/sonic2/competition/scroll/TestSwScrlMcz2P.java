package com.openggf.game.sonic2.competition.scroll;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static com.openggf.game.sonic2.competition.scroll.Sonic2CompetitionScroll.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSwScrlMcz2P extends Scroll2PFixture {
    @Test void sameSeparatedAndReversedCameras() {
        // X=160: quotient=256, fixed increment=0x100000; row values
        // 144,128,112,80,64,48,32,16,80,112,128,144,...
        for (int[] pair : new int[][]{{160,160},{160,320},{320,160},{0,160}}) {
            scroll.update(Zone.MCZ,0,camera(pair[0],960),camera(pair[1],960),7);
            check(scroll.view(0),pair[0],19,4);
            check(scroll.view(1),pair[1],23,8);
        }
    }
    private void check(View v,int x,int first,int last) {
        int n=x/10;
        bands(v,-x,first,-9*n,11,-8*n,9,-7*n,4,-5*n,3,-4*n,1,-3*n,
                1,-2*n,24,-n,6,-5*n,10,-7*n,16,-8*n,last,-9*n);
    }
    @Test void rowBoundariesBothActsAndFractionalBanksRewind() {
        scroll.initialize(new BackgroundBank(3,0xabcd,5,6,7,8),BackgroundBank.ZERO);
        scroll.update(Zone.MCZ,0,camera(161,1074),camera(320,1098),1);
        // bgY=38,46 => first visible row1 in both banks (P2 extends row0 by4).
        assertEquals(word(-161,-128),scroll.view(0).lines()[0]);
        assertEquals(word(-320,-256),scroll.view(1).lines()[0]);
        assertEquals(0x0026abcd,scroll.view(0).background().y());
        var state=scroll.capture();
        scroll.update(Zone.MCZ,1,camera(320,102),camera(160,114),1);
        assertEquals(0,scroll.view(0).backgroundVScroll()); // 102/6-16=1 -> even0
        assertEquals(-222,scroll.view(1).backgroundVScroll());
        assertEquals(word(-320,-288),scroll.view(0).lines()[0]);
        scroll.restore(state);
        assertEquals(0x0026abcd,scroll.view(0).background().y());
        assertEquals(46<<16,scroll.view(1).background().y());
        assertArrayEquals(state.horizontal(),scroll.capture().horizontal());
    }
}
