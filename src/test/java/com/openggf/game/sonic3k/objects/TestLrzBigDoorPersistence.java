package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestLrzBigDoorPersistence {
    private static final ObjectSpawn FIRST=new ObjectSpawn(0x1000,0x600,0x1A,0,0,false,0);
    private static final ObjectSpawn SECOND=new ObjectSpawn(0x1000,0x700,0x1A,0,0,false,1);
    private static TestObjectServices setup() {
        var services=new TestObjectServices().withCamera(new Camera());
        services.zoneRuntimeRegistry().install(new LrzZoneRuntimeState(9,0,PlayerCharacter.SONIC_ALONE));
        var registry=new ObjectRegistry() {
            public ObjectInstance create(ObjectSpawn spawn) { return new LrzBigDoorObjectInstance(spawn); }
            public void reportCoverage(List<ObjectSpawn> spawns) { }
            public String getPrimaryName(int id) { return "LRZBigDoor"; }
            public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
        };
        var manager=new ObjectManager(List.of(FIRST,SECOND),registry,0,null,null,null,services.camera(),services);
        return services.withDirectObjectManager(manager);
    }
    private static LrzBigDoorObjectInstance create(TestObjectServices services,ObjectSpawn spawn) {
        return services.objectManager().createDynamicObject(()->new LrzBigDoorObjectInstance(spawn));
    }
    private static void open(LrzBigDoorObjectInstance door,ObjectSpawn spawn) {
        var player=new TestablePlayableSprite("sonic",(short)0,(short)0);
        player.setCentreX((short)(spawn.x()+0x60));player.setCentreY((short)(spawn.y()+0x60));
        door.update(1,player);
    }
    @Test void sameXPlacementsKeepIndependentBitsAndReloadAtTheRenderedOpenPosition() {
        var services=setup();var manager=services.objectManager();
        var first=create(services,FIRST);open(first,FIRST);
        assertTrue(manager.isSpawnStateBitSet(FIRST,0));
        assertFalse(manager.isSpawnStateBitSet(SECOND,0));
        manager.removeDynamicObject(first);
        var reloaded=create(services,FIRST);
        assertTrue(reloaded.isFullyOpen());assertEquals(0x680,reloaded.getY());
        assertEquals(reloaded.getY(),reloaded.getCentreY());
        var second=create(services,SECOND);
        assertFalse(second.isFullyOpen());assertEquals(0x700,second.getY());
    }
    @Test void openedPlacementSurvivesPersistentReturnButNotFreshLevelReset() {
        var services=setup();var manager=services.objectManager();
        open(create(services,FIRST),FIRST);
        var carry=manager.capturePersistentRespawn();
        var returned=setup();returned.objectManager().restorePersistentRespawn(carry);
        assertEquals(0x680,create(returned,FIRST).getY());
        assertFalse(create(returned,SECOND).isFullyOpen());
        returned.objectManager().reset(0);
        assertFalse(create(returned,FIRST).isFullyOpen());
    }
    @Test void rewindRestoresThePlacementBitWithTheMovingDoor() {
        var services=setup();var manager=services.objectManager();
        open(create(services,FIRST),FIRST);
        var rewind=manager.rewindSnapshottable();var saved=rewind.capture();
        open(create(services,SECOND),SECOND);
        assertTrue(manager.isSpawnStateBitSet(SECOND,0));
        rewind.restore(saved);
        assertTrue(manager.isSpawnStateBitSet(FIRST,0));assertFalse(manager.isSpawnStateBitSet(SECOND,0));
        var restored=manager.activeObjectsOfType(LrzBigDoorObjectInstance.class).getFirst();
        assertTrue(restored.isOpening());assertEquals(1,restored.openTimer());
        manager.removeDynamicObject(restored);
        assertEquals(0x680,create(services,FIRST).getY());
    }
}
