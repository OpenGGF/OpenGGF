package com.openggf.game.sonic3k.specialstage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 5639S's one/two-tick delayed start changes later turn admission.
 * Declared collision-free path; actual unstarted player -> Up -> native first
 * speedup -> held Right -> completed turn. No speed, turn or position seeding
 * after initialize. This pins the timing mechanism, not the author's optimal
 * route or the author's tentative 14–16-frame turning explanation.
 * Native loc_903E, loc_95BE, loc_9600, loc_964A and loc_9704 at 8b2cf3c. */
class TestTasS3kBlueSphereStartDelay {
    @ParameterizedTest @ValueSource(ints={0,1,2})
    void delayingInitialUpShiftsTurnAdmissionAfterTheFirstSpeedup(int delay) {
        var player=new Sonic3kSpecialStagePlayer();
        player.initialize(0,0x1000,0x1000,false);
        for(int tick=0;tick<1800;tick++)player.update(tick==delay?1:0,0);
        assertTrue(player.isStarted());assertFalse(player.isFailed());
        assertEquals(0x1400,player.getRate());assertEquals(0x1200,player.getVelocity());
        // Seven initial increments move 2+4+...+14; subsequent ticks move
        // 16, except the first speedup tick moves 18. Each delayed Up loses
        // one 16-unit displacement while the rate timer continues to tick.
        assertEquals(0x9FB6+16*delay,player.getYPos());
        int turnTicks=0;
        while(player.getAngle()!=0xC0 && turnTicks<40) {
            player.update(8,0);turnTicks++;
        }
        // $E0 cell alignment admits rotation after 8/9/10 forward samples;
        // then sixteen four-unit rotations complete the right turn.
        assertEquals(24+delay,turnTicks);
        assertEquals(0xC0,player.getAngle());assertEquals(0,player.getTurning());
        assertEquals(0x1014,player.getXPos(),"completed turn moves on the same update");
        assertEquals(0x1400,player.getVelocity());
    }
}
