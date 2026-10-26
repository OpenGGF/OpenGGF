package com.openggf.game.sonic1.specialstage;

import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.reel.S1SpecialStageReel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

/** Each original ROM maze's emerald can actually be collected and its animation
 * can initiate exit. Starts at the emerald cell, not a replay of its maze route. */
@RequiresRom(SonicGame.SONIC_1)
class TestTasS1EmeraldVerdicts {
    Sonic1SpecialStageManager manager;
    S1SpecialStageReel reel;
    @BeforeEach void setup() throws Exception {TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_1);GraphicsManager.getInstance().initHeadless();reel=S1SpecialStageReel.open();manager=new Sonic1SpecialStageManager();}
    @AfterEach void close(){try {manager.reset();GraphicsManager.getInstance().cleanup();GraphicsManager.getInstance().resetState();TestEnvironment.resetEngineOnly();} finally {if(reel!=null)reel.close();}}
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5})
    void eachNativeMazeEmeraldIsCollectedAndItsSparkleStartsExit(int stage) throws Exception {
        manager.initialize(stage);S1SpecialStageReel.advanceToEntryPresentation(manager);S1SpecialStageReel.update(manager);
        var layout=(byte[])field("layout").get(manager);
        int emerald=-1;
        for(int i=0;i<layout.length;i++)if((layout[i]&255)>=0x3b&&(layout[i]&255)<=0x40){emerald=i;break;}
        assertTrue(emerald>=0,"Native maze must contain an emerald");
        int x=(emerald%128)*24-0x20+12, y=(emerald/128)*24-0x50+12;
        field("sonicPosX").setLong(manager,(long)x<<16);field("sonicPosY").setLong(manager,(long)y<<16);
        field("sonicVelX").setInt(manager,0);field("sonicVelY").setInt(manager,0);field("sonicInertia").setInt(manager,0);
        field("ssAngle").setInt(manager,0);field("ssRotate").setInt(manager,0);field("sonicAirborne").setBoolean(manager,true);
        assertFalse(manager.isEmeraldCollected());assertFalse(field("exitTriggered").getBoolean(manager));
        S1SpecialStageReel.update(manager);
        assertTrue(manager.isEmeraldCollected());
        assertFalse(field("exitTriggered").getBoolean(manager),"Collection does not directly freeze gameplay");
        for(int i=0;i<30&&!field("exitTriggered").getBoolean(manager);i++)S1SpecialStageReel.update(manager);
        assertTrue(field("exitTriggered").getBoolean(manager),"The native sparkle queue starts exit after collection");
        assertEquals(stage,manager.getCurrentStage());
    }
    private static Field field(String name)throws Exception{var f=Sonic1SpecialStageManager.class.getDeclaredField(name);f.setAccessible(true);return f;}
}
