package com.openggf.game.sonic3k.objects;

import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 4515S/8501S missile alignment: actual stock launch reaches homing; target
 * position and V-int phase distinguish native steering. No seeded flight state. */
class TestTasS3kDdzMissileSteering {
    @AfterEach void cleanup() { SessionManager.clear(); }
    @ParameterizedTest @ValueSource(ints={4,5})
    void globalClockPhaseChangesHomingButNotTheMovementTick(int vIntPhase) throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        var p=new TestablePlayableSprite("sonic",(short)0x600,(short)0x100);
        var sprites=mock(SpriteManager.class);when(sprites.getMainPlayable()).thenReturn(p);
        var camera=GameServices.camera();var services=new TestObjectServices().withCamera(camera)
                .withGameState(new GameStateManager()).withGameModule(new Sonic3kGameModule()).withSpriteManager(sprites);
        services.zoneRuntimeRegistry().install(new DdzZoneRuntimeState(0,PlayerCharacter.SONIC_ALONE));
        var manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,null,camera,services);
        services.withDirectObjectManager(manager);manager.reset(0);
        var boss=manager.createDynamicObject(()->new DdzEndBossObjectInstance(new ObjectSpawn(0x200,0x80,0xB6,0,0,false,0)));
        var launcher=manager.createDynamicObject(()->new DdzEndBossLauncherObjectInstance(boss));
        launcher.update(0,p);
        var missiles=manager.activeObjectsOfType(DdzMissileObjectInstance.class);
        assertEquals(3,missiles.size());var missile=missiles.getFirst();
        assertEquals(0,number(missile,"index"));
        for(int i=0;i<100&&number(missile,"state")!=DdzMissileObjectInstance.STATE_HOME;i++) missile.update(i+1,p);
        assertEquals(DdzMissileObjectInstance.STATE_HOME,number(missile,"state"));
        assertEquals(0xC0,missile.angleForExhaust(),"first missile completes its native half-turn");
        p.setCentreX((short)missile.getX());p.setCentreY((short)(missile.getY()+0x100));
        int oldX=number(missile,"xPos"),oldY=number(missile,"yPos");
        missile.update(vIntPhase,p);
        assertEquals(vIntPhase==4?0xC0:0xC2,missile.angleForExhaust(),"sub_82A82 skips turns at V-int &3 ==0");
        assertNotEquals(oldX,number(missile,"xPos"),"both phases still move");
        if(vIntPhase==4) assertEquals(oldY,number(missile,"yPos"));
        else assertTrue(number(missile,"yPos")>oldY,"two-angle turn curves toward Player 1");
        assertFalse(missile.isDestroyed());
    }
    private static int number(Object owner,String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return ((Number)field.get(owner)).intValue();
    }
}
