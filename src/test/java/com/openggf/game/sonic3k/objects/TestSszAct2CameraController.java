package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszAct2CameraController {
    private HeadlessTestFixture boot() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(10,1).build();
        fixture.sprite().setDebugMode(true);
        return fixture;
    }
    private SszZoneRuntimeState state() { return (SszZoneRuntimeState)GameServices.zoneRuntimeState(); }
    private SszAct2CameraController controller() {
        return GameServices.level().getObjectManager().activeObjectsOfType(SszAct2CameraController.class).getFirst();
    }
    @Test void screenInitAllocatesCameraAfterArrivalAndGatesFractionalDrift() {
        var fixture=boot(); fixture.stepIdleFrames(1);
        var manager=GameServices.level().getObjectManager();
        var arrival=manager.activeObjectsOfType(SszArrivalControllerObjectInstance.class).getFirst();
        var controller=controller();
        assertTrue(controller.getSlotIndex()>arrival.getSlotIndex());
        controller.update(0,null); assertEquals(0,state().cloudOffsetFixed());
        state().setSpecialVIntRoutine(4);
        controller.update(1,null); assertEquals(0x11B,state().cloudOffsetFixed());
        assertEquals(0,state().eventsBgWord(0));
        controller.update(2,null); controller.update(3,null);
        assertEquals(3*0x11B,state().cloudOffsetFixed());
        assertEquals(0xFFFF,state().eventsBgWord(0),"$30=1 preseed followed by native swing reset");
    }
    @Test void fractionalOffsetAndSwingReplayTogetherAcrossRestore() {
        var fixture=boot(); fixture.stepIdleFrames(1); state().setSpecialVIntRoutine(4);
        fixture.stepIdleFrames(120);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        fixture.stepIdleFrames(80); int offset=state().cloudOffsetFixed(),swing=state().eventsBgWord(0);
        registry.restore(saved); fixture.stepIdleFrames(80);
        assertEquals(offset,state().cloudOffsetFixed()); assertEquals(swing,state().eventsBgWord(0));
        assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(SszAct2CameraController.class).size());
    }
    @Test void negativeEndingSignalRequiresTwoChangedZeroCrossings() {
        var fixture=boot(); fixture.stepIdleFrames(1); state().setSpecialVIntRoutine(4); state().setEventsFg4(0xFF00);
        int zeroCrossings=0,previous=0;
        for(int pass=0;pass<2000&&state().specialVIntRoutine()!=0xC;pass++) {
            controller().update(pass,null);
            int next=(short)state().eventsBgWord(0);
            if(next==0&&previous!=0)zeroCrossings++;
            if(zeroCrossings<2)assertEquals(4,state().specialVIntRoutine());
            previous=next;
        }
        assertEquals(2,zeroCrossings); assertEquals(0xC,state().specialVIntRoutine()); assertEquals(0,state().eventsFg4());
    }
}
