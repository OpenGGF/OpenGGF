package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestLrzBossPlatforms {
    private TestObjectServices setup() {
        var camera=new Camera(); camera.setX((short)0xA00); camera.setY((short)0x300);
        var services=new TestObjectServices().withCamera(camera).withIsolatedObjectManager();
        services.zoneRuntimeRegistry().install(new LrzZoneRuntimeState(22,0,PlayerCharacter.SONIC_ALONE));
        return services;
    }
    private List<LrzBossPlatformObjectInstance> platforms(ObjectManager manager) {
        return manager.getActiveObjects().stream().filter(o->o instanceof LrzBossPlatformObjectInstance)
                .map(o->(LrzBossPlatformObjectInstance)o).toList();
    }
    @Test void initialGeneratorCreatesTwoChildrenThatMoveInTheSameSweepAndReverseAfter32Steps() {
        var services=setup(); var manager=services.objectManager();
        var root=manager.createDynamicObject(()->new LrzBossPlatformObjectInstance(new ObjectSpawn(0xA80,0x3B0,0xAD,0,0,false,0)));
        manager.update(0xA00,null,List.of(),1);
        var all=platforms(manager); assertEquals(3,all.size());
        var upper=all.stream().filter(p->p!=root && p.getY()<0x400).findFirst().orElseThrow();
        var lower=all.stream().filter(p->p!=root && p.getY()>0x400).findFirst().orElseThrow();
        assertEquals(0x3AF,upper.getY()); assertEquals(0x46F,lower.getY());
        assertEquals(0x79D44,upper.getRomCodePointer());
        for(int i=2;i<=32;i++) manager.update(0xA00,null,List.of(),i);
        assertEquals(0x3A0,upper.getY()); assertEquals(0x460,lower.getY());
        assertEquals(3,platforms(manager).size());
        manager.update(0xA00,null,List.of(),33);
        assertEquals(5,platforms(manager).size()); assertEquals(0x79D6E,upper.getRomCodePointer());
        assertEquals(3,upper.getPriorityBucket());
        var undersides=platforms(manager).stream().filter(p->p.getRomCodePointer()==0x79E7C).toList();
        assertEquals(2,undersides.size()); assertTrue(undersides.stream().allMatch(p->p.getPriorityBucket()==2));
        manager.update(0xA00,null,List.of(),34); assertEquals(0x3A1,upper.getY());
    }
    @ParameterizedTest @ValueSource(ints={0,1,2})
    void eachIndependentInitialAllocationHonoursExhaustionWithoutRetry(int available) {
        var services=setup(); var manager=services.objectManager();
        manager.createDynamicObject(()->new LrzBossPlatformObjectInstance(new ObjectSpawn(0xA80,0x3B0,0xAD,0,0,false,0)));
        manager.reserveAllButNFreeSlots(available);
        manager.update(0xA00,null,List.of(),1);
        assertEquals(1+available,platforms(manager).size());
        var occupied=platforms(manager).stream().map(p->p.getSlotIndex()).toList();
        int freed=java.util.stream.IntStream.range(0,manager.getLastDynamicSlotExclusive())
                .filter(slot->slot>occupied.getFirst() && !occupied.contains(slot)).findFirst().orElseThrow();
        manager.releaseDynamicSlot(freed);
        for(int i=2;i<32;i++) manager.update(0xA00,null,List.of(),i);
        assertEquals(1+available,platforms(manager).size(),"failed children do not heal");
    }
}
