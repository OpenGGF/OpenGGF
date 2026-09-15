package com.openggf.level.objects;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestPlacementLowerRespawnBits {
    @Test void twoAxisEntriesAboveByteIndexRangeStayIndependentThroughCullAndRewind() {
        var spawns=new ArrayList<ObjectSpawn>();
        for(int i=0;i<600;i++)spawns.add(new ObjectSpawn(i*16,0x100,0x47,0,0,false,i));
        var placement=new ObjectPlacementController(spawns,()->320);
        placement.setTwoAxisCursorPlacement(true);
        placement.setCounterStateBit(spawns.get(1),0);
        placement.setCounterStateBit(spawns.get(257),2);
        placement.setCounterStateBit(spawns.get(599),6);
        placement.removeFromActiveForUnload(spawns.get(599));
        assertTrue(placement.isCounterStateBitSet(spawns.get(599),6));
        assertFalse(placement.isCounterStateBitSet(spawns.get(1),2));
        assertFalse(placement.isCounterStateBitSet(spawns.get(257),0));
        var saved=placement.captureRewindState(0,Integer.MIN_VALUE);
        placement.setCounterStateBit(spawns.get(1),1);
        placement.restoreRewindState(saved);
        assertFalse(placement.isCounterStateBitSet(spawns.get(1),1));
        assertTrue(placement.isCounterStateBitSet(spawns.get(1),0));
        assertTrue(placement.isCounterStateBitSet(spawns.get(257),2));
        assertTrue(placement.isCounterStateBitSet(spawns.get(599),6));
        placement.reset(0);
        assertFalse(placement.isCounterStateBitSet(spawns.get(599),6));
    }
    @Test void untrackedCounterPlacementStillIgnoresLowerBitWrites() {
        var spawn=new ObjectSpawn(0x100,0x100,1,0,0,false,0);
        var placement=new ObjectPlacementController(List.of(spawn),()->320);
        placement.enableCounterBasedRespawn();
        placement.setCounterStateBit(spawn,0);
        assertFalse(placement.isCounterStateBitSet(spawn,0));
        assertEquals(256,placement.captureRewindState(0,Integer.MIN_VALUE).objState().length);
    }
}
