package com.openggf;

import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.sonic2.objects.MonitorObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.level.objects.*;
import com.openggf.mods.run.MonitorRule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.parallel.Isolated
class TestMonitorOffPolicy {
    @org.junit.jupiter.api.AfterEach void releaseWorld() {
        com.openggf.game.session.SessionManager.clear();
        com.openggf.game.GameModuleRegistry.reset();
    }

    @org.junit.jupiter.api.Nested
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    class Sonic2 {
        @Test void romPlacementReadsTheLiveOffPolicyWithoutRememberingBreakage() {
            nativePlacementOff(com.openggf.game.sonic2.constants.Sonic2ObjectIds.MONITOR,false,
                    spawn -> new MonitorObjectInstance(spawn,"Monitor"));
        }
    }
    @org.junit.jupiter.api.Nested
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    class Sonic3k {
        @Test void romPlacementReadsTheLiveOffPolicyWithoutRememberingBreakage() {
            nativePlacementOff(com.openggf.game.sonic3k.constants.Sonic3kObjectIds.MONITOR,true,
                    Sonic3kMonitorObjectInstance::new);
        }
    }

    private void nativePlacementOff(int objectId,boolean skipIntro,
            java.util.function.Function<ObjectSpawn,AbstractMonitorObjectInstance> create) {
        var builder = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(0,0);
        if (skipIntro) builder.withSkippedZoneIntro();
        builder.build();
        var level = com.openggf.game.GameServices.level();
        var manager = level.getObjectManager();
        var spawn = level.getCurrentLevel().getObjects().stream()
                .filter(candidate -> candidate.objectId()==objectId).findFirst().orElseThrow();
        var services = manager.getObjectServices();
        var player = (com.openggf.sprites.playable.AbstractPlayableSprite)
                com.openggf.game.GameServices.camera().getFocusedSprite();
        var normal = create.apply(spawn); normal.setServices(services); normal.update(0,player);
        assertFalse(normal.isDestroyed(),"the same native placement is present under NORMAL");
        assertFalse(manager.isRemembered(spawn));
        var stock = GameplayRunPolicy.stock();
        var off = new GameplayRunPolicy(stock.specialStageEntry(),stock.bonusStageEntry(),stock.actCompletion(),
                stock.liveRewind(),stock.editorEntry(),stock.deathRestart(),stock.livesCounter(),false,MonitorRule.OFF);
        com.openggf.game.session.SessionManager.getCurrentGameplayMode().beginGameplayRun(off,0,0);
        var disabled = create.apply(spawn); disabled.setServices(services); disabled.update(0,player);
        assertTrue(disabled.isDestroyed());
        assertFalse(manager.isRemembered(spawn),"OFF must not mark the native placement broken");
    }

    @Test void offDeletesBothGamesWithoutMarkingPlacementRemembered() {
        var stock = GameplayRunPolicy.stock();
        var off = new GameplayRunPolicy(stock.specialStageEntry(),stock.bonusStageEntry(),stock.actCompletion(),
                stock.liveRewind(),stock.editorEntry(),stock.deathRestart(),stock.livesCounter(),stock.zoneIntros(),MonitorRule.OFF);
        var manager = mock(ObjectManager.class);
        var services = new StubObjectServices() {
            @Override public GameplayRunPolicy runPolicy() { return off; }
            @Override public ObjectManager objectManager() { return manager; }
        };
        var spawn = new ObjectSpawn(256,256,0x26,3,0,false,0);
        var s2 = new MonitorObjectInstance(spawn,"Monitor");
        var s3 = new Sonic3kMonitorObjectInstance(spawn);
        s2.setServices(services); s3.setServices(services);
        s2.update(1,null); s3.update(1,null);
        assertTrue(s2.isDestroyed()); assertTrue(s3.isDestroyed());
        verify(manager,never()).markRemembered(any());
        assertFalse(stock.monitors() == MonitorRule.OFF);
    }
}
