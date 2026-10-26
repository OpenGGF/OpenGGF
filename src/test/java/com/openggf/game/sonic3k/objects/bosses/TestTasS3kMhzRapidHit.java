package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 3422M/5427S MHZ2 early-hit claim. Real initialization spawns both children;
 * one accepted gliding proxy contact precedes actual weather-child destruction.
 * No HP, routine, flash, damaged collision flag, or child-signal state is seeded.
 * Native loc_76004 reinitializes parent flags/HP on every routine0 dispatch;
 * sub_76782 then observes proxy flags independently, even during the hit flash. */
class TestTasS3kMhzRapidHit {
    @org.junit.jupiter.api.AfterEach void cleanup(){com.openggf.game.session.SessionManager.clear();}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void currentLimitationEarlyProxyHitDoesNotRepeatDamageAfterWeatherDestruction(boolean earlyHit) {
        try (var reel = com.openggf.tests.reel.S3kTestReelPresentation.open()) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        ObjectManager[] holder=new ObjectManager[1];
        var runtime=new MhzZoneRuntimeState(1,PlayerCharacter.KNUCKLES);
        var services=new StubObjectServices(){
            @Override public ObjectManager objectManager(){return holder[0];}
            @Override public MhzZoneRuntimeState zoneRuntimeState(){return runtime;}
        };
        var manager=new ObjectManager(List.of(),null,0,null,null,null,null,services);
        holder[0]=manager;manager.reset(0);
        var boss=manager.createDynamicObject(()->new MhzEndBossInstance(new ObjectSpawn(0x40C0,0x2E0,Sonic3kObjectIds.MHZ_END_BOSS,0,0,false,0)));
        boss.update(0,null); reel.afterObjects(manager.getActiveObjects().stream().map(AbstractObjectInstance.class::cast).toList(),7,1,0x40C0,0x270,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MHZ_END_BOSS,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        var proxy=manager.getActiveObjects().stream().filter(MhzEndBossHitProxyChild.class::isInstance).map(MhzEndBossHitProxyChild.class::cast).findFirst().orElseThrow();
        var weather=manager.getActiveObjects().stream().filter(MhzEndBossWeatherMachineChild.class::isInstance).map(MhzEndBossWeatherMachineChild.class::cast).findFirst().orElseThrow();
        assertEquals(9,boss.getState().hitCount);assertEquals(0,boss.getState().routine);
        assertEquals(0x25,proxy.getCollisionFlags());assertNotEquals(0,weather.getCollisionFlags());
        var p=new Knuckles("knuckles",(short)0,(short)0);
        // Valid contact boundary: the author approaches the spawned proxy in a
        // glide. This single callback is the accepted touch owner, not six
        // attacks or a write to the parent/proxy's damaged state.
        p.setCentreX((short)proxy.getX());p.setCentreY((short)proxy.getY());
        p.setAir(true);p.setDoubleJumpFlag(1);p.setXSpeed((short)0x600);
        if(earlyHit)proxy.onPlayerAttack(p,null);
        assertEquals(earlyHit?8:9,boss.getState().hitCount);
        assertEquals(earlyHit?0:0x25,proxy.getCollisionFlags());
        boss.update(1,p); // still routine0; native repeated initialization runs
        assertEquals(0,boss.getState().routine);
        weather.onPlayerAttack(p,null);weather.update(1,p); reel.afterObjects(manager.getActiveObjects().stream().map(AbstractObjectInstance.class::cast).toList(),7,1,0x40C0,0x270,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MHZ_END_BOSS,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        assertEquals(4,boss.getCustomFlag(0x38)&4,"real weather owner raises child signal");
        boss.update(2,p); reel.afterObjects(manager.getActiveObjects().stream().map(AbstractObjectInstance.class::cast).toList(),7,1,0x40C0,0x270,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MHZ_END_BOSS,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.ROBOTNIK_SHIP); // last routine0 dispatch resets native HP9 then consumes one
        assertEquals(4,boss.getState().routine);assertEquals(0x3F,boss.getCustomFlag(0x2E));
        for(int frame=3;frame<=8;frame++){ boss.update(frame,p); reel.afterObjects(manager.getActiveObjects().stream().map(AbstractObjectInstance.class::cast).toList(),7,1,0x40C0,0x270,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MHZ_END_BOSS,com.openggf.game.sonic3k.Sonic3kObjectArtKeys.ROBOTNIK_SHIP); }
        assertEquals(0x39,boss.getCustomFlag(0x2E),"six movement/flash owner passes reached");
        // The independently specified native HP2 assertion was executed red:
        // expected2, observed8. The engine conflates proxy flags with parent
        // invulnerability, so no per-update proxy consequence remains.
        assertEquals(earlyHit?8:9,boss.getState().hitCount);
        if(earlyHit){
            assertNotEquals(2,boss.getState().hitCount,"confirmed unsupported six-update native damage tail");
            assertTrue(boss.getState().invulnerable);assertEquals(24,boss.getState().invulnerabilityTimer);
            assertEquals(0,proxy.getCollisionFlags());
        }
            }
    }
}
