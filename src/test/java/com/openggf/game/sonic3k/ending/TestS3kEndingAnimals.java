package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingAnimals {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(){
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        return HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
    }
    @Test void nativeFiveFlickyPositionsAndChildSubtypeDelays(){
        var f=boot();int[][] positions={{0x170,0xB0},{0x150,0xC0},{0x190,0xD0},{0x140,0xE0},{0x168,0xF0}};
        for(int i=0;i<5;i++){
            int index=i;var animal=GameServices.level().getObjectManager().createDynamicObject(
                    ()->new EndingAnimalObjectInstance(false,index,0xC0,0xE0));
            animal.update(0,f.sprite());assertEquals(positions[i][0],animal.getX());assertEquals(positions[i][1],animal.getY());
            for(int n=0;n<i*16;n++)animal.update(n,f.sprite());assertEquals(2,animal.routine());
            animal.update(i*16,f.sprite());assertEquals(4,animal.routine());
        }
    }
    @Test void rabbitUsesNativeNinetySevenPassWaitThenSixPixelLaunchAndGravity(){
        var f=boot();var animal=GameServices.level().getObjectManager().createDynamicObject(
                ()->new EndingAnimalObjectInstance(true,0,0xC0,0xE0));
        animal.update(0,f.sprite());assertEquals(0xC0,animal.getX());assertEquals(0xE0,animal.getY());
        for(int i=0;i<96;i++)animal.update(i,f.sprite());assertEquals(2,animal.routine());
        long before=GameServices.rng().getSeed();animal.update(96,f.sprite());assertEquals(4,animal.routine());
        assertNotEquals(before,GameServices.rng().getSeed());assertTrue(animal.getX()>=0x180&&animal.getX()<=0x1BF);
        assertEquals(0x180,animal.getY());int startX=animal.getX();animal.update(97,f.sprite());
        assertEquals(startX-3,animal.getX());assertEquals(0x17A,animal.getY());
    }
    @Test void liveAnimalGraphRecreatesAnimationAndFractionalSwingThenRepeatsCleanup(){
        var f=boot();var manager=GameServices.level().getObjectManager();
        var animal=manager.createDynamicObject(()->new EndingAnimalObjectInstance(false,2,0xC0,0xE0));
        for(int i=0;i<64;i++)animal.update(i,f.sprite());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        for(int i=0;i<45;i++)animal.update(i,f.sprite());int[] expected={animal.getX(),animal.getY(),animal.routine()};
        var ending=S3kEndingCoordinator.state(manager.getObjectServices());ending.setFlag(3);animal.update(45,f.sprite());
        assertTrue(animal.isDestroyed());registry.restore(saved);
        animal=GameServices.level().getObjectManager().activeObjectsOfType(EndingAnimalObjectInstance.class).getFirst();
        for(int i=0;i<45;i++)animal.update(i,f.sprite());
        assertArrayEquals(expected,new int[]{animal.getX(),animal.getY(),animal.routine()});
        S3kEndingCoordinator.state(GameServices.level().getObjectManager().getObjectServices()).setFlag(3);
        animal.update(45,f.sprite());assertTrue(animal.isDestroyed());
    }
}
