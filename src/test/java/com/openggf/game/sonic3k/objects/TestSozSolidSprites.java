package com.openggf.game.sonic3k.objects;

import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSozSolidSprites {
    @Test void allNonzeroSubtypesUseWideFrameAndCheckpointBeforeCull() {
        for(int subtype:new int[]{0,1,0x80,0xFF}) {
            var object=new SozSolidSpritesObjectInstance(new ObjectSpawn(0x400,0x300,0x49,subtype,3,false,0));
            var checkpoint=mock(ObjectSolidExecutionContext.class);
            object.setServices(new StubObjectServices() {
                @Override public ObjectSolidExecutionContext solidExecution() { return checkpoint; }
            });
            object.update(0,null); verify(checkpoint).resolveSolidNowAll();
            assertEquals(subtype==0 ? new SolidObjectParams(27,24,25) : new SolidObjectParams(43,8,9),object.getSolidParams());
            assertEquals(subtype==0 ? 16 : 32,object.getOnScreenHalfWidth());
            assertEquals(subtype==0 ? 24 : 8,object.getOnScreenHalfHeight());
            assertTrue(object.checksOutOfRangeAfterRoutine());
        }
    }
    @Test void cnzGiantWheelBindingRemainsDistinct() {
        var r=new Sonic3kObjectRegistry() { @Override protected int currentRomZoneId() { return 3; } };
        assertInstanceOf(CnzGiantWheelInstance.class,r.create(new ObjectSpawn(0,0,0x49,0,0,false,0)));
    }
}
