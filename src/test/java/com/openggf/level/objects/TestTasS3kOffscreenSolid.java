package com.openggf.level.objects;

import com.openggf.game.*;
import com.openggf.graphics.GLCommand;
import com.openggf.tests.*;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 4087S/4514S: outrun the camera to pass an ordinary full solid. The declared
 * persistent solid supplies geometry only. Camera bounds and actual inline
 * SolidObjectFull resolution decide whether horizontal motion is stopped. */
class TestTasS3kOffscreenSolid {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cameraVisibilityControlsPassageThroughTheSameFullSolid(boolean offscreen) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var camera=GameServices.camera();camera.setX((short)(offscreen?0x400:0));camera.setY((short)0);
        var services=new TestObjectServices().withCamera(camera);
        var manager=new ObjectManager(List.of(),new com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry(),0,
                null,null,null,camera,services);
        services.withDirectObjectManager(manager);
        var object=new Solid();manager.addDynamicObject(object);
        var p=new TestablePlayableSprite("sonic",(short)0,(short)0);
        p.setWidth(20);p.setHeight(20);p.setCentreX((short)60);p.setCentreY((short)100);p.setAir(false);
        p.setXSpeed((short)0x400);p.setGSpeed((short)0x400);
        for(int frame=1;frame<=20;frame++) {
            p.move(p.getXSpeed(),(short)0);
            manager.update(camera.getX(),p,List.of(),frame,false,true,false);
        }
        assertEquals(!offscreen,object.isWithinSolidContactBounds(),"production camera bounds select the gate");
        assertTrue(manager.getActiveObjects().contains(object),"passage cannot be explained by despawn");
        if(offscreen) {assertEquals(140,p.getCentreX());assertEquals(0x400,p.getXSpeed());}
        else {assertTrue(p.getCentreX()<100);assertEquals(0,p.getXSpeed());}
    }
    private static final class Solid extends AbstractObjectInstance implements SolidObjectProvider {
        Solid(){super(new ObjectSpawn(100,100,0,0,0,false,0),"declared full solid");}
        @Override public boolean isPersistent(){return true;}
        @Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(16,32,32);}
        @Override public void update(int frame,PlayableEntity p) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
