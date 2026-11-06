package com.openggf.physics;

import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Probe-result component controls complement the real ROM terrain/input trajectory. */
class TestGroundWallResponseAxis {
    @ParameterizedTest
    @CsvSource({
        "8,32,768,-2,false,33,545,0",
        "8,32,768,-2,true,33,545,0",
        "1,32,768,-2,false,545,33,768",
        "8,31,768,-2,false,33,545,0",
        "8,33,768,-2,false,545,33,768",
        "8,224,-768,-2,false,1057,545,0",
        "8,32,768,0,false,545,545,768",
        "8,32,768,2,false,545,545,768"
    })
    void responseBiasChangesOnlyNegativeSozQuadrantBoundary(int zone,int angle,int groundSpeed,
            int distance,boolean tails,int vx,int vy,int g) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var mode=TestEnvironment.activeGameplayMode();
        var terrain=mode.getTerrainCollisionManager();
        var collision=new CollisionSystem(terrain);
        var level=mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(mock(Level.class));
        var feature=new Sonic3kZoneFeatureProvider(){
            @Override protected int getFeatureZoneId(){return zone;}
        };
        when(level.getZoneFeatureProvider()).thenReturn(feature);
        mode.attachLevelManagers(mode.getWaterSystem(),mode.getParallaxManager(),terrain,
            collision,mode.getSpriteManager(),level);
        AbstractPlayableSprite player=tails?new Tails("tails",(short)0,(short)0):new Sonic("sonic",(short)0,(short)0);
        player.setCentreX((short)0x400);player.setCentreY((short)0x400);
        player.setAngle((byte)angle);player.setAir(false);
        player.setGSpeed((short)groundSpeed);player.setXSpeed((short)545);player.setYSpeed((short)545);
        var probe=mock(GroundSensor.class);
        when(probe.scanWorld(any(Direction.class),anyShort(),anyShort(),anyShort(),anyShort(),anyInt()))
            .thenReturn(new SensorResult((byte)angle,(byte)distance,1,Direction.DOWN));
        var field=CollisionSystem.class.getDeclaredField("calcRoomProbe");field.setAccessible(true);field.set(collision,probe);
        var declaredProbe=CollisionSystem.describeCalcRoomInFrontProbe(angle,(short)groundSpeed);
        var before=player.captureRewindState();
        for(int replay=0;replay<2;replay++){
            if(replay!=0)player.restoreRewindState(before);
            collision.resolveGroundWallCollision(player);
            assertEquals(vx,player.getXSpeed());assertEquals(vy,player.getYSpeed());assertEquals(g,player.getGSpeed());
        }
        verify(probe,times(2)).scanWorld(eq(declaredProbe.globalDirection()),
            eq(declaredProbe.offsetX()),eq(declaredProbe.offsetY()),anyShort(),anyShort(),anyInt());
    }
}
