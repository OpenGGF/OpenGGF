package com.openggf.sprites.managers;

import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeDustOrdinaryControl {
    @Test void ordinaryS2AndS3kKeepTheirExistingFrameSequenceDrawingAndCapturedCounters() {
        try {
            for(var game:new SonicGame[]{SonicGame.SONIC_2,SonicGame.SONIC_3K}) {
                TestEnvironment.configureGameModuleFixture(game);
                var player=new TestablePlayableSprite("sonic",(short)256,(short)512);
                player.setSpindash(true);player.setAir(false);
                var frames=new ArrayList<Integer>();var renderer=mock(PlayerSpriteRenderer.class);
                doAnswer(inv->{frames.add(inv.getArgument(0));return null;}).when(renderer)
                        .drawFrame(anyInt(),anyInt(),anyInt(),anyBoolean(),anyBoolean());
                var dust=new SpindashDustController(player,renderer,game==SonicGame.SONIC_2?132:98);
                player.setSpindashDustController(dust);
                for(int pass=0;pass<40;pass++) {
                    dust.update();var saved=dust.captureRewindState();dust.draw();
                    assertEquals(new SpindashDustController.RewindState((pass/2+1)%7,pass%2==0?1:0,
                            10+(pass+1)/2%7,true),saved,"existing ordinary "+game+" pass "+pass);
                    assertEquals(10+(pass+1)/2%7,frames.getLast());
                    assertEquals(saved,dust.captureRewindState(),"drawing advances no animation counter");
                }
                var unchanged=dust.captureRewindState();
                NativeDustDisplayInternalAccess.chargeStarted(player);NativeDustDisplayInternalAccess.chargeReleased(player);
                assertEquals(unchanged,dust.captureRewindState(),"callbacks have no receiver in ordinary worlds");
                player.setSpindash(false);dust.update();
                assertEquals(new SpindashDustController.RewindState(0,0,10,false),dust.captureRewindState());
            }
        } finally {TestEnvironment.resetAll();}
    }
}
