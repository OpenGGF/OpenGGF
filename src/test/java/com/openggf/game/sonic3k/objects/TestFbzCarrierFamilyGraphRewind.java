package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestFbzCarrierFamilyGraphRewind {
    @BeforeEach void init(){GraphicsManager.getInstance().initHeadless();AbstractObjectInstance.updateCameraBounds(0,0,0x4000,0x1000,0);}
    @AfterEach void reset(){AbstractObjectInstance.resetCameraBoundsForTests();GraphicsManager.getInstance().resetState();}

    @Test void snakeAndRotatorFamiliesRestoreExactChildRolesWithoutDuplicatesAndRelinkLifetime(){
        ObjectSpawn snakeSpawn=new ObjectSpawn(0x1000,0x800,Sonic3kObjectIds.FBZ_SNAKE_PLATFORM,3,0,true,10);
        ObjectSpawn rotSpawn=new ObjectSpawn(0x1400,0x800,Sonic3kObjectIds.FBZ_ROTATING_PLATFORM,0x0C,0,true,11);
        ObjectManager[] holder=new ObjectManager[1];Camera camera=new Camera(){public short getX(){return 0x0C00;}public short getY(){return 0;}public short getWidth(){return 0x4000;}public short getHeight(){return 0x1000;}public boolean isVerticalWrapEnabled(){return false;}};
        ObjectServices services=new StubObjectServices(){@Override public ObjectManager objectManager(){return holder[0];}@Override public Camera camera(){return camera;}@Override public GraphicsManager graphicsManager(){return GraphicsManager.getInstance();}};
        ObjectManager manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,GraphicsManager.getInstance(),camera,services);holder[0]=manager;manager.reset(0);manager.setRewindInPlaceRestoreEnabledForTest(false);
        FbzSnakePlatformObjectInstance snake=manager.createDynamicObject(()->new FbzSnakePlatformObjectInstance(snakeSpawn));FbzRotatingPlatformObjectInstance rot=manager.createDynamicObject(()->new FbzRotatingPlatformObjectInstance(rotSpawn));snake.update(0,null);rot.update(0,null);
        assertEquals(List.of(1,0x19,0x31,0x49),live(manager,FbzSnakePlatformObjectInstance.class).stream().map(FbzSnakePlatformObjectInstance::segmentDelay).sorted().toList());
        assertEquals(List.of(0x2C,0x44),live(manager,FbzRotatingPlatformObjectInstance.class).stream().map(FbzRotatingPlatformObjectInstance::memberRadius).sorted().toList());
        RewindRegistry registry=new RewindRegistry();registry.register(manager.rewindSnapshottable());CompositeSnapshot snapshot=registry.capture();
        for(ObjectInstance object:new ArrayList<>(manager.getActiveObjects()))if(isChild(object))manager.removeDynamicObject(object);setBoolean(snake,"childrenSpawned",false);setBoolean(rot,"childrenSpawned",false);snake.update(1,null);rot.update(1,null);
        registry.restore(snapshot);
        List<FbzSnakePlatformObjectInstance> snakes=live(manager,FbzSnakePlatformObjectInstance.class);List<FbzRotatingPlatformObjectInstance> rots=live(manager,FbzRotatingPlatformObjectInstance.class);
        assertEquals(4,snakes.size());assertEquals(2,rots.size());assertEquals(List.of(1,0x19,0x31,0x49),snakes.stream().map(FbzSnakePlatformObjectInstance::segmentDelay).sorted().toList());assertEquals(List.of(0x2C,0x44),rots.stream().map(FbzRotatingPlatformObjectInstance::memberRadius).sorted().toList());assertEquals(1,rots.stream().filter(FbzRotatingPlatformObjectInstance::specialMember).count());
        FbzSnakePlatformObjectInstance restoredSnakeParent=snakes.stream().filter(v->!v.childMember()).findFirst().orElseThrow();FbzRotatingPlatformObjectInstance restoredRotParent=rots.stream().filter(v->!v.childMember()).findFirst().orElseThrow();
        for(FbzSnakePlatformObjectInstance child:snakes)if(child.childMember())assertSame(restoredSnakeParent,child.parentMember());for(FbzRotatingPlatformObjectInstance child:rots)if(child.childMember())assertSame(restoredRotParent,child.parentMember());
        restoredSnakeParent.update(2,null);restoredRotParent.update(2,null);assertEquals(4,live(manager,FbzSnakePlatformObjectInstance.class).size());assertEquals(2,live(manager,FbzRotatingPlatformObjectInstance.class).size());
        restoredSnakeParent.setDestroyed(true);restoredRotParent.setDestroyed(true);for(FbzSnakePlatformObjectInstance child:snakes)if(child.childMember())child.update(3,null);for(FbzRotatingPlatformObjectInstance child:rots)if(child.childMember())child.update(3,null);assertTrue(snakes.stream().filter(FbzSnakePlatformObjectInstance::childMember).allMatch(ObjectInstance::isDestroyed));assertTrue(rots.stream().filter(FbzRotatingPlatformObjectInstance::childMember).allMatch(ObjectInstance::isDestroyed));
    }
    private static boolean isChild(ObjectInstance o){return o instanceof FbzSnakePlatformObjectInstance s&&s.childMember()||o instanceof FbzRotatingPlatformObjectInstance r&&r.childMember();}
    private static <T extends ObjectInstance> List<T> live(ObjectManager m,Class<T> type){return m.getActiveObjects().stream().filter(o->o.getClass()==type&&!o.isDestroyed()).map(type::cast).toList();}
    private static void setBoolean(Object o,String name,boolean value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.setBoolean(o,value);}catch(Exception e){throw new AssertionError(e);}}
}
