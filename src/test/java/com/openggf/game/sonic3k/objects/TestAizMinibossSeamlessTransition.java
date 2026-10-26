package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A live normal fight and its physical component SSTs cannot ride an act reload. */
class TestAizMinibossSeamlessTransition {
    @Test void minibossAndChildrenDoNotSurviveSeamlessActReload() {
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        try {
            var camera=new Camera();
            var services=new TestObjectServices().withCamera(camera)
                    .withGameModule(new Sonic3kGameModule()).withGraphicsManager(graphics);
            var manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,graphics,camera,services);
            services.withDirectObjectManager(manager);
            var boss=manager.createDynamicObject(()->new AizMinibossInstance(
                    new ObjectSpawn(0x11F0,0x289,0x91,0,0,false,0x289)));
            var barrel=manager.createDynamicObject(()->new AizMinibossFlameBarrelChild(boss,0,false));
            boss.onCarriedAcrossSeamlessTransition(-0x2F00,-0x80);
            assertTrue(boss.isDestroyed());
            assertTrue(barrel.isDestroyed(),"actual registered child SST must not survive the fight reload");
        } finally {
            AbstractObjectInstance.resetCameraBoundsForTests();graphics.resetState();
        }
    }
}
