package com.openggf.game.sonic1.objects;

import com.openggf.tests.reel.S1ReelFrames;

import com.openggf.game.GameServices;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

/** S1_OBJECT_PHASE: actual LZ1 rising-platform landing and early jump undo its sink offset. */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1PlatformVerdicts {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void steppingOffRisingBlockBeforeTimerExpiryRemovesItsSinkOffset(boolean leave) throws Exception {
        var level=SharedLevel.load(SonicGame.SONIC_1,3,0);
        try {
            var f=HeadlessTestFixture.builder().withSharedLevel(level).startPosition((short)0xa20,(short)0x2da).startPositionIsCentre().build();
            var s=f.sprite();s.setAir(true);s.setYSpeed((short)0x100);
            var manager=GameServices.level().getObjectManager();
            Sonic1LabyrinthBlockObjectInstance block=null;
            for(int i=0;i<40&&!s.isOnObject();i++) {
                S1ReelFrames.step(f,false,false,false,false,false);
                block=manager.getActiveObjects().stream().filter(o->o instanceof Sonic1LabyrinthBlockObjectInstance && o.getX()==0xa20).map(o->(Sonic1LabyrinthBlockObjectInstance)o).findFirst().orElse(null);
            }
            assertNotNull(block);assertSame(block,manager.getRidingObject(s),"Actual SolidObject landing must acquire the native rising platform");
            assertEquals(3,field(block,"moveType"));
            for(int i=0;i<16;i++)S1ReelFrames.step(f,false,false,false,false,false);
            assertEquals(0x310,block.getY(),"Standing reaches native +4 sine sink maximum");
            if(leave){S1ReelFrames.step(f,false,false,false,false,true);assertTrue(s.getAir());assertFalse(s.isOnObject());}
            for(int i=0;i<30&&field(block,"moveType")==3;i++)S1ReelFrames.step(f,false,false,false,false,leave);
            assertEquals(4,field(block,"moveType"),"The stand-triggered timer enters the rising routine");
            assertEquals(leave?0x30c:0x310,block.getY(),"Early jump reverses sink before the same rise gate");
        } finally {level.dispose();}
    }
    private static int field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.getInt(o);}
}
