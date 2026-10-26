package com.openggf.game.sonic1.objects;

import com.openggf.game.GameStateManager;
import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Sign_Touch uses only unsigned X distance: an offscreen Y is not an exclusion. */
class TestTasS1FinishVerdicts {
    @ParameterizedTest @CsvSource({"-1,false","0,true","31,true","32,false"})
    void offscreenSignpostTriggersOnlyInsideItsUnsignedHorizontalWindow(int offset,boolean triggers){
        var state=new GameStateManager();
        var s=new TestablePlayableSprite("sonic",(short)0,(short)0);
        s.setCentreX((short)(0x1000+offset));s.setCentreY((short)0x100);
        var sign=new Sonic1SignpostObjectInstance(new ObjectSpawn(0x1000,0x300,0x0d,0,0,false,0));
        sign.setServices(new StubObjectServices(){public GameStateManager gameState(){return state;}});
        AbstractObjectInstance.updateCameraBounds(0xf60,0x80,0xf60+320,0x80+224,0);
        assertFalse(sign.isWithinSolidContactBounds(),"Signpost is below the screen, as in the LZ2 finish trick");
        sign.update(0,s);
        assertEquals(triggers,state.isActCompletionSignalActive());
        assertFalse(state.isEndOfLevelActive(),"Native Sign_Touch does not activate the strict level-end movement clamp");
    }
}
