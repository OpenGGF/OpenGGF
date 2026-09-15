package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.SozMinibossInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.rules.*;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Camera-threshold start, followed by native arena admission; no boss/state injection. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named="soz.act1.capture",matches=".+")
class TestSozAct1ArenaCapture {
    @Test void naturalArenaAdmission() throws Exception {
        Path output=Path.of(System.getProperty("soz.act1.capture"));Files.createDirectories(output.resolve("frames"));
        var settings=new GameplayCaptureSession.Settings(320,"sonic","tails","off",null,0x43B0,0x9D4);
        try(var session=new GameplayCaptureSession(settings);var csv=Files.newBufferedWriter(output.resolve("state.csv"))){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),8,0,settings);
            assertEquals(0,GameServices.level().getCurrentAct());session.player().setRingCount(99);
            csv.write(GameplayCaptureSession.stateHeader()+",background_routine,arena_height,shake,door_signal,boss_present,bg_x,bg_y,art_job,block_job");csv.newLine();
            boolean bossSeen=false;
            for(int frame=0;frame<1600;frame++){
                session.step(null);var events=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().events();
                boolean boss=!GameServices.level().getObjectManager().activeObjectsOfType(SozMinibossInstance.class).isEmpty();bossSeen|=boss;
                csv.write(session.stateLine(frame,null)+","+events.backgroundRoutine()+","+events.sandHeight()+","+events.screenShakeFlag()+","+events.doorSignal()+","+boss+","+GameServices.parallax().getBgCameraX()+","+GameServices.parallax().getVscrollFactorBG()+","+events.artJobOrdinal()+","+events.blockJobOrdinal());csv.newLine();
                if(frame%4==0)ScreenshotCapture.savePNG(session.render(),output.resolve("frames").resolve(String.format("%05d.png",frame)));
            }
            assertTrue(bossSeen);assertFalse(session.player().getDead());
            assertTrue(GameServices.level().getZoneFeatureProvider().bgWrapsHorizontally());
            assertTrue(GameServices.level().getZoneFeatureProvider().useLinearBackgroundLayoutOverflow(8));
            byte[] art=new com.openggf.level.resources.ResourceLoader(GameServices.rom().getRom())
                    .loadSingle(com.openggf.level.resources.LoadOp.kosinskiMBase(0x1AD24C));
            for(int offset=0;offset<art.length;offset++){
                var pattern=GameServices.level().getCurrentLevel().getPattern(0x315+offset/32);
                int pixel=(offset%32)*2;
                int actual=(pattern.getPixel(pixel%8,pixel/8)<<4)|pattern.getPixel((pixel+1)%8,(pixel+1)/8);
                assertEquals(art[offset]&255,actual,"queued native arena art byte "+offset);
            }
        }
    }
}
