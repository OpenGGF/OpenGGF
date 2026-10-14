package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated loc_5AC12/5ADCE resource boundaries; complete rescue routes remain separate. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingSceneResources {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();}
    private void boot(){
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
        // This component entry starts after the preceding module FIFO has retired.
        var s=TestEnvironment.objectServices();
        var queue=S3kRuntimeArtCoordinator.from(s).moduleQueue();
        for(int i=0;i<512&&queue.hasPendingPhysicalModules();i++)service();
        assertFalse(queue.hasPendingPhysicalModules());
    }
    private void service(){
        HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
        HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        HardwareBoundaryPump.service(HardwareServiceBoundary.VINT_SERVICE);
    }
    private void ready(){var s=TestEnvironment.objectServices();
        for(int i=0;i<512&&!EndingSceneResourceHandoff.ready(s);i++)service();
        assertTrue(EndingSceneResourceHandoff.ready(s),"native queues must retire without fixture completion admission");
    }
    @Test void queueAizInstallsVerifiedTerrainAndNativeChunkPatchesWithoutChangingD01OrHistory(){
        boot();var s=TestEnvironment.objectServices();var level=(Sonic3kLevel)s.currentLevel();
        assertEquals(9,level.getMap().getWidth());
        EndingSceneResourceHandoff.queueAiz(s);assertEquals(3,S3kEndingCoordinator.state(s).scene().directCount());
        ready();var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var snapshot=registry.capture();
        EndingSceneResourceHandoff.installAiz(s);
        assertEquals(13,GameServices.level().getCurrentZone());assertEquals(1,GameServices.level().getCurrentAct());
        assertEquals(97,level.getMap().getWidth());assertEquals(97,level.getLayerWidthBlocks((byte)0));
        assertEquals(0x402,level.getBlock(0x3430/128).getChunkDesc((0x3430%128)/2%8,(0x3430%128)/16).get());
        for(int i=0;i<7;i++)assertFalse(EndingSceneResourceHandoff.drawDelayedRow(s,false));
        assertTrue(EndingSceneResourceHandoff.drawDelayedRow(s,false));
        byte[] expected=((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes();
        registry.restore(snapshot);assertEquals(9,level.getMap().getWidth());
        EndingSceneResourceHandoff.installAiz(s);
        for(int i=0;i<8;i++)EndingSceneResourceHandoff.drawDelayedRow(s,false);
        assertArrayEquals(expected,((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).captureBytes());
        EndingSceneResourceHandoff.queueSsz(s);ready();EndingSceneResourceHandoff.installSsz(s);
        assertEquals(9,level.getMap().getWidth());assertEquals(13,GameServices.level().getCurrentZone());
        assertArrayEquals(new byte[]{4,0x62,0xE,(byte)0xEE},
                s.paletteOwnershipRegistryOrNull().targetSegaData(1,0,2),"sub_5B11C replaces the island's sky target");
        assertArrayEquals(new byte[]{0xA,(byte)0xEE,6,(byte)0x8E},
                s.paletteOwnershipRegistryOrNull().targetSegaData(2,2,2),"Pal_SSZ2+$20 replaces the restored island target");
    }
}
