package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestSonic2SignpostSparkleRewind {
    @Test void destructiveRestoreRetainsNativeIdentitySpawnAndAnimation() {
        var graphics=GraphicsManager.getInstance();
        graphics.initHeadless();
        try {
            var manager=new ObjectManager(List.of(),null,0,null,null,graphics,
                    mock(Camera.class),new StubObjectServices());
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            var spawn=new ObjectSpawn(0x180,0x128,0x25,0,0,false,0);
            var original=manager.createDynamicObject(()->new Sonic2SignpostSparkleObjectInstance(spawn));
            for(int i=0;i<13;i++) original.update(i,null);
            var display=original.nativeDisplaySlot();
            assertEquals(6,display.mapping().frame());
            var registry=new RewindRegistry();
            registry.register(manager.rewindSnapshottable());
            var snapshot=registry.capture();
            int slot=original.getSlotIndex();
            manager.removeDynamicObject(original);
            var replacement=manager.createDynamicObject(()->new Sonic2SignpostSparkleObjectInstance(
                    new ObjectSpawn(8,16,0x25,0,0,false,0)));
            registry.restore(snapshot);
            var restored=manager.getActiveObjects().stream()
                    .filter(o->o instanceof Sonic2SignpostSparkleObjectInstance)
                    .map(o->(Sonic2SignpostSparkleObjectInstance)o).findFirst().orElseThrow();
            assertNotSame(original,restored);
            assertNotSame(replacement,restored);
            assertEquals(slot,restored.getSlotIndex());
            assertEquals(spawn,restored.getSpawn());
            assertEquals(0x25,restored.getSpawn().objectId());
            assertEquals(display,restored.nativeDisplaySlot());
            for(int i=13;i<27;i++) {
                original.update(i,null);
                restored.update(i,null);
                assertEquals(original.nativeDisplaySlot(),restored.nativeDisplaySlot(),"animation tick "+i);
                assertEquals(original.isDestroyed(),restored.isDestroyed());
            }
            assertTrue(restored.isDestroyed());
        } finally { graphics.resetState(); }
    }
}
