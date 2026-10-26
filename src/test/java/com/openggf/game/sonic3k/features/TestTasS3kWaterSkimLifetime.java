package com.openggf.game.sonic3k.features;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.objects.HCZWaterSkimSlotObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 6655S HCZ water-record reuse prerequisite. Runs the participant owner and
 * slot execution/unload phases, including a real skim activation and jump exit.
 * Native Obj_HCZWaterSplash's range removal can free the water record; the
 * engine's persistent owner never becomes available to a later allocator. */
class TestTasS3kWaterSkimLifetime {
    @AfterEach void cleanup() { HCZWaterSkimHandler.reset(); }

    @Test void currentLimitationWaterSkimOwnerSurvivesParticipantExitAndRangeDeparture() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        HCZWaterSkimHandler.reset();
        var camera=GameServices.camera();camera.setX((short)0);
        var services=new TestObjectServices().withCamera(camera);
        var manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,
                GameServices.graphics(),camera,services);
        services.withDirectObjectManager(manager);
        manager.enableExecThenLoadPlacement();manager.reset(0);
        var skim=new HCZWaterSkimSlotObjectInstance();
        var control=new RangeControl();
        manager.addDynamicObjectAtSlot(skim,5);manager.addDynamicObjectAtSlot(control,6);
        var p=new TestablePlayableSprite("sonic",(short)0,(short)0);
        p.setCentreX((short)0x100);p.setCentreY((short)(0x200-p.getYRadius()-1));
        p.setAir(false);p.setXSpeed((short)0x800);p.setGSpeed((short)0x800);p.setYSpeed((short)0);
        var query=new ObjectPlayerQuery(()->p,List::of);
        HCZWaterSkimHandler.update(query,0x200,1);
        manager.update(0,p,List.of(),1);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());assertTrue(control.updates>0);
        assertTrue(manager.getActiveObjects().contains(control));
        p.setLogicalInputState(false,false,false,false,true,true);
        HCZWaterSkimHandler.update(query,0x200,2);
        manager.update(0,p,List.of(),2);
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());assertTrue(p.getAir());assertEquals(-0x680,p.getYSpeed());
        p.setLogicalInputState(false,false,false,false,false,false);
        camera.setX((short)0x3000);
        for(int frame=3;frame<=10;frame++) {
            p.move(p.getXSpeed(),p.getYSpeed());
            HCZWaterSkimHandler.update(query,0x200,frame);
            manager.update(0x3000,p,List.of(),frame);
        }
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());
        assertFalse(manager.getActiveObjects().contains(control),"nonpersistent control proves range-unload phase ran");
        assertTrue(manager.getActiveObjects().contains(skim),"confirmed persistent water slot after coordinated departure");
        assertFalse(skim.isDestroyed());assertEquals(5,skim.getSlotIndex());
        var replacement=new RangeControl();manager.addDynamicObject(replacement);
        assertNotEquals(5,replacement.getSlotIndex());
    }

    private static final class RangeControl extends AbstractObjectInstance {
        int updates;
        RangeControl() { super(new ObjectSpawn(0,0,0,0,0,false,0),"range control"); }
        @Override public void update(int frame,PlayableEntity p) { updates++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
