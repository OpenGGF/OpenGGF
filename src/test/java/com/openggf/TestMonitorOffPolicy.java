package com.openggf;

import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.game.sonic2.objects.MonitorObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.level.objects.*;
import com.openggf.mods.run.MonitorRule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestMonitorOffPolicy {
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
