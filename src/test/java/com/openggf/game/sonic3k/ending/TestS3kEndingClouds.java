package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingClouds {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private HeadlessTestFixture boot(){
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        return HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
    }
    @Test void emitterReadsVIntForCadenceAndConsumesRandomOnlyAtAdmittedEmission(){
        var f=boot();var manager=GameServices.level().getObjectManager();
        var emitter=manager.createDynamicObject(EndingCloudObjectInstance::new);
        var clouds=((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).ending().clouds();
        clouds.configure(0x1F,-0x100,-0x100);emitter.update(0x120,f.sprite());
        assertEquals(0x120,GameServices.rng().getSeed());
        for(int vint=0x121;vint<0x140;vint++)emitter.update(vint,f.sprite());
        assertEquals(0x120,GameServices.rng().getSeed());
        emitter.update(0x140,f.sprite());
        var expected=new GameRng(GameServices.rng().flavour(),0x120);expected.nextRaw();
        assertEquals(expected.getSeed(),GameServices.rng().getSeed());
        assertEquals(1,manager.activeObjectsOfType(EndingCloudObjectInstance.class).stream().filter(c->!c.emitter()).count());
        clouds.configure(0,-0x100,-0x100);emitter.update(0x160,f.sprite());assertTrue(emitter.isDestroyed());
        assertEquals(expected.getSeed(),GameServices.rng().getSeed());
    }
    @Test void emissionRestoreReplaysSharedRandomCloudRamAndManagedCloudSlots(){
        var f=boot();var manager=GameServices.level().getObjectManager();
        var emitter=manager.createDynamicObject(EndingCloudObjectInstance::new);
        ((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).ending().clouds().configure(3,0x200,0x100);
        emitter.update(100,f.sprite());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var saved=registry.capture();
        emitter.update(104,f.sprite());emitter.update(108,f.sprite());
        long seed=GameServices.rng().getSeed();int slots=manager.getActiveObjects().size();
        byte[] ram=((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes();
        registry.restore(saved);emitter=GameServices.level().getObjectManager()
                .activeObjectsOfType(EndingCloudObjectInstance.class).stream().filter(EndingCloudObjectInstance::emitter).findFirst().orElseThrow();
        emitter.update(104,f.sprite());emitter.update(108,f.sprite());
        assertEquals(seed,GameServices.rng().getSeed());
        assertEquals(slots,GameServices.level().getObjectManager().getActiveObjects().size());
        assertArrayEquals(ram,((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes());
    }
}
