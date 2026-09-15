package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;

/** Positioned production capture for the SOZ completion task; no trace state is consumed. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named="soz.miniboss.capture",matches=".+")
class TestSozMinibossCapture {
    @Test void positionedAwakening() throws Exception {
        Path output=Path.of(System.getProperty("soz.miniboss.capture"));Files.createDirectories(output.resolve("frames"));
        var settings=new GameplayCaptureSession.Settings(528,"sonic","tails","off",null,0x4380,0x980);
        try(var session=new GameplayCaptureSession(settings);var state=Files.newBufferedWriter(output.resolve("state.csv"))){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),8,0,settings);
            org.junit.jupiter.api.Assertions.assertEquals(0,GameServices.level().getCurrentAct(),"session act is zero-based");
            org.junit.jupiter.api.Assertions.assertNotNull(GameServices.level().getObjectRenderManager()
                    .getRenderer(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.SOZ_MINIBOSS));
            session.player().setRingCount(99);
            // Positioned art capture: native arena event terrain/background admission is separate.
            var camera=GameServices.camera();
            camera.setMinY((short)0x980);camera.setMaxY((short)0x980);camera.setY((short)0x980);
            camera.setFrozen(true);camera.setVerticalWrapEnabled(false);
            var boss=GameServices.level().getObjectManager().createDynamicObject(()->new SozMinibossInstance(new ObjectSpawn(0x439D,0x9F7,0x97,0,0,false,0)));
            state.write(GameplayCaptureSession.stateHeader()+",boss_x,boss_y,boss_phase,boss_routine");state.newLine();
            for(int frame=0;frame<700;frame++){
                session.step(null);state.write(session.stateLine(frame,null)+","+boss.getX()+","+boss.getY()+","+boss.phase()+","+boss.routine());state.newLine();
                if(frame%2==0)ScreenshotCapture.savePNG(session.render(),output.resolve("frames").resolve(String.format("%05d.png",frame)));
            }
        }
    }
}
