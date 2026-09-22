package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzBossCameraHeadless {
    @AfterEach void reset() {
        CrossGameFeatureProvider.getInstance().resetState();
        var config=SonicConfigurationService.getInstance(); config.clearSessionOverrides();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest(name="checkpoint camera {0}px {1} {2}+{3}")
    @CsvSource({"320,off,sonic,tails", "352,off,sonic,tails", "400,off,sonic,tails", "528,off,sonic,tails", "800,off,sonic,tails",
            "320,s1,sonic,none", "352,s1,sonic,none", "400,s1,sonic,none", "528,s1,sonic,none", "800,s1,sonic,none",
            "320,s2,sonic,tails", "352,s2,sonic,tails", "400,s2,sonic,tails", "528,s2,sonic,tails", "800,s2,sonic,tails",
            "320,off,tails,none", "320,off,sonic,none"})
    void checkpointBranchDelaysThenMovesCameraAndReplaysWorld(int width,String donor,String main,String side) throws Exception {
        var config=SonicConfigurationService.getInstance(); config.clearSessionOverrides();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,main);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,side.equals("none")?"":side);
        var aspect=java.util.Arrays.stream(WidescreenAspect.values()).filter(a->a.pixelWidth()==width).findFirst().orElseThrow();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,aspect.name()); config.resolveDisplayAspect();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle()
                .startPosition((short)0x480,(short)0x400).startPositionIsCentre()
                .withCrossGameDonation(donor.equals("off")?null:donor).build();
        var camera=GameServices.camera();
        var state=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct();
        assertEquals(width,camera.getWidth()); assertEquals(main,fixture.sprite().getCode());
        assertEquals(!donor.equals("off"),CrossGameFeatureProvider.isActive());
        assertEquals(0x9C0,fixture.sprite().getCentreX()); assertEquals(0x36C,fixture.sprite().getCentreY());
        assertEquals(0x920,camera.getX()); assertEquals(0x2F0,camera.getY());
        assertEquals(0xC,state.foregroundRoutine()); assertEquals(0x10,state.autoscrollRoutine());
        assertEquals(45,state.autoscrollDelay());
        for(int i=0;i<45;i++) fixture.stepFrame(false,false,false,false,false);
        assertEquals(0x920,camera.getX()); assertEquals(0x2F0,camera.getY());
        fixture.stepFrame(false,false,false,false,false);
        assertEquals(0x921,camera.getX()); assertEquals(0x2F0,camera.getY());
        assertEquals(0xD900,state.cameraFractionX()); assertEquals(0xC400,state.cameraFractionY());
        var registry=fixture.gameplayMode().getRewindRegistry(); var before=registry.capture();
        fixture.stepFrame(false,false,false,true,false); var after=registry.capture();
        fixture.stepFrame(false,false,false,false,true); registry.restore(before);
        fixture.stepFrame(false,false,false,true,false); var replay=registry.capture();
        for(String key:after.entries().keySet()) assertTrue(RewindSnapshotDiff.diffKey(key,after.get(key),replay.get(key)).isEmpty(),()->key+RewindSnapshotDiff.diffKey(key,after.get(key),replay.get(key)));
    }

    @Test void freshEntryConsumesTwoIndependentSignalsAndRewindsTerrainEdits() throws Exception {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle().build();
        var events=((Sonic3kLevelEventManager)GameServices.module().getLevelEventProvider()).getLrzEventsForTest();
        var state=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct();
        assertEquals(0,GameServices.camera().getMaxX()); assertEquals(-1,state.autoscrollRoutine());
        fixture.stepFrame(false,false,false,false,false); assertEquals(4,state.foregroundRoutine());
        state.requestForegroundAdvance();
        var registry=fixture.gameplayMode().getRewindRegistry(); var before=registry.capture();
        fixture.stepFrame(false,false,false,false,false); assertEquals(8,state.foregroundRoutine()); assertEquals(-1,state.autoscrollRoutine());
        var map=GameServices.level().getCurrentLevel().getMap();
        for(int x=0;x<5;x++) assertEquals(x%2==0?0x16:0x15,map.getValue(0,x,9)&255);
        var after=registry.capture(); registry.restore(before); fixture.stepFrame(false,false,false,false,false); var replay=registry.capture();
        for(String key:after.entries().keySet()) assertTrue(RewindSnapshotDiff.diffKey(key,after.get(key),replay.get(key)).isEmpty(),()->key+RewindSnapshotDiff.diffKey(key,after.get(key),replay.get(key)));
        events.update(0,2); assertEquals(8,state.foregroundRoutine(),"one signal cannot skip the flash wait");
        state.requestForegroundAdvance(); state.setCameraFractions(0x1234,0x5678);
        events.update(0,3); assertEquals(12,state.foregroundRoutine()); assertEquals(0,state.autoscrollRoutine());
        assertEquals(0,state.cameraFractionX()); assertEquals(0,state.cameraFractionY());
        state.requestChunkEdit(0xC8,0x480); events.update(0,4);
        assertEquals(0x17,map.getValue(0,1,9)&255); assertEquals(0x17,map.getValue(0,2,9)&255);
        assertEquals(0,state.chunkEditX());
    }
    @Test void lavaSupportsProtectedP1BurnsP2AndRestoresFractionalCurrent() throws Exception {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle().build();
        var runtime=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var objects=GameServices.level().getObjectManager();
        assertEquals(1,objects.getActiveObjects().stream().filter(
                o->o instanceof com.openggf.game.sonic3k.objects.LrzBossLavaSurfaceObjectInstance).count());
        runtime.setBackgroundRoutine(12);
        var player=fixture.sprite(); player.giveShield(com.openggf.game.ShieldType.FIRE);
        var camera=GameServices.camera(); camera.setX((short)0xA00); camera.setY((short)0x560);
        camera.setMinX((short)0xA00); camera.setMaxX((short)0xA00);
        camera.setMinY((short)0x560); camera.setMaxY((short)0x560); camera.setMaxYTarget((short)0x560);
        player.setCentreX((short)0xA40); player.setCentreY((short)(0x610-player.getYRadius()-2));
        player.setAir(true); player.setYSpeed((short)0x100); player.setXSpeed((short)0);
        fixture.stepFrame(false,false,false,false,false);
        var lava=(com.openggf.game.sonic3k.objects.LrzBossLavaSurfaceObjectInstance) objects.getActiveObjects().stream()
                .filter(o->o instanceof com.openggf.game.sonic3k.objects.LrzBossLavaSurfaceObjectInstance).findFirst().orElseThrow();
        assertTrue(objects.isRidingObject(player,lava),"real sloped solid establishes contact");
        assertFalse(player.isHurt()); assertFalse(player.getDead());
        runtime.bossAct().setLavaDirection(1);
        var registry=fixture.gameplayMode().getRewindRegistry(); var before=registry.capture();
        fixture.stepFrame(false,false,false,false,false); var after=registry.capture();
        assertEquals(1,runtime.bossAct().lavaAmplitude());
        assertEquals(0xFD00,player.getXSubpixelRaw(),"negative 768 current retains its fraction");
        objects.removeDynamicObject(lava);
        registry.restore(before);
        var restoredLava=(com.openggf.game.sonic3k.objects.LrzBossLavaSurfaceObjectInstance) objects.getActiveObjects().stream()
                .filter(o->o instanceof com.openggf.game.sonic3k.objects.LrzBossLavaSurfaceObjectInstance).findFirst().orElseThrow();
        assertNotSame(lava,restoredLava); lava=restoredLava;
        fixture.stepFrame(false,false,false,false,false); var replay=registry.capture();
        for(String key:after.entries().keySet()) assertTrue(RewindSnapshotDiff.diffKey(key,after.get(key),replay.get(key)).isEmpty(),
                ()->key+RewindSnapshotDiff.diffKey(key,after.get(key),replay.get(key)));
        var side=GameServices.sprites().getSidekicks().getFirst();
        side.giveShield(com.openggf.game.ShieldType.FIRE);
        var contact=new com.openggf.level.objects.SolidContact(true,false,false,true,false);
        lava.onSolidContact(side,contact,123);
        assertTrue(side.isHurt(),"native P2 has no fire-shield exemption");
        side.setHurt(false); runtime.bossAct().setCapsuleOpened(true);
        int x=(player.getCentreX()<<16)|player.getXSubpixelRaw();
        lava.onSolidContact(player,contact,124);
        assertEquals(x-768,(player.getCentreX()<<16)|player.getXSubpixelRaw(),"capsule gate still applies current");
    }

}
