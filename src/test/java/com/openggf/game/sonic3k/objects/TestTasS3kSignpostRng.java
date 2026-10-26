package com.openggf.game.sonic3k.objects;

import com.openggf.game.*;
import com.openggf.level.objects.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Native Obj_EndSignFall: choosing V-int phase determines whether the real
 * sparkle owner consumes Random_Number before any boss can consume it. */
class TestTasS3kSignpostRng {
    @ParameterizedTest @ValueSource(ints={4,5})
    void fallingSignpostConsumesRngOnlyOnSparklePhase(int frame) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var camera=GameServices.camera();camera.setX((short)0);camera.setY((short)0x100);
        var rng=new GameRng(GameRng.Flavour.S3K,0x1234L);
        var services=new TestObjectServices().withCamera(camera).withRng(rng);
        var manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,null,camera,services);
        services.withDirectObjectManager(manager);
        var sign=manager.createDynamicObject(()->new S3kSignpostInstance(0x200,0));
        var p=new TestablePlayableSprite("sonic",(short)0,(short)0);
        sign.update(1,p);
        assertEquals(0xE0,sign.getY());assertEquals(0x1234L,rng.getSeed());
        int owners=manager.getActiveObjects().size();
        sign.update(frame,p);
        assertEquals(frame==4?0xEA56EA54L:0x1234L,rng.getSeed());
        assertEquals(owners+(frame==4?1:0),manager.getActiveObjects().size(),"real sparkle allocation witnesses RNG consumer");
        assertFalse(sign.isLanded());
    }
}
