package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Continuous production GameLoop capture of the controller-only positioned victory route. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named="soz.act1.victory.capture",matches=".+")
class TestSozAct1VictoryCapture {
    @Test void captureVictoryAndHandoff() throws Exception {
        Path output=Path.of(System.getProperty("soz.act1.victory.capture"));
        Files.createDirectories(output.resolve("frames"));
        var settings=new GameplayCaptureSession.Settings(Integer.getInteger("soz.act1.victory.width",320),"sonic","tails","off",null,0x43B0,0x8E0);
        try(var session=new GameplayCaptureSession(settings);var csv=Files.newBufferedWriter(output.resolve("state.csv"))) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),8,0,settings);
            assertEquals(settings.width(),GameServices.camera().getWidth());
            assertEquals("sonic",session.player().getCode());
            var followers=GameServices.sprites().getRegisteredSidekicks();
            assertEquals(1,followers.size());
            assertEquals("tails",GameServices.sprites().getSidekickCharacterName(followers.getFirst()));
            session.player().setRingCount(99);var route=new SozAct1VictoryRoute();boolean ready=false;int readyFrame=-1;
            csv.write(GameplayCaptureSession.stateHeader()+",act,boss_phase,boss_routine,background_routine,p1_mask,p2_mask,p1_high,p2_high");csv.newLine();
            for(int frame=0;frame<6500;frame++) {
                var input=GameServices.level().getCurrentAct()==0?route.input(frame,session.player()):null;
                session.step(input);var boss=SozAct1VictoryRoute.boss();
                var events=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow().events();
                csv.write(session.stateLine(frame,input)+","+GameServices.level().getCurrentAct()+","+(boss==null?-1:boss.phase())+","+(boss==null?-1:boss.routine())+","+events.backgroundRoutine()+","+(input==null?0:input.p1InputMask())+","+(input==null?0:input.p2InputMask())+","+session.player().isHighPriority()+","+followers.getFirst().isHighPriority());csv.newLine();
                if(frame%Integer.getInteger("soz.act1.victory.every",4)==0)ScreenshotCapture.savePNG(session.render(),output.resolve("frames").resolve(String.format("%05d.png",frame)));
                assertFalse(session.player().getDead(),"player died at "+frame);
                if(GameServices.level().getCurrentAct()==1&&!events.seamlessEntry()&&!session.player().isControlLocked()) {ready=true;if(readyFrame<0)readyFrame=frame;}
                if(readyFrame>=0&&frame-readyFrame>=180)break;
            }
            for(int line:new int[]{0,2,3}) {
                int total=0;for(int color=0;color<16;color++)total|=com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(GameServices.level().getCurrentLevel().getPalette(line).getColor(color));
                assertNotEquals(0,total,"destination palette line "+line+" target="+java.util.Arrays.toString(GameServices.paletteOwnershipRegistryOrNull().targetSegaData(line,0,16)));
            }
            var image=session.render();int visible=0;
            for(int y=48;y<190;y++)for(int x=0;x<image.width();x++)if((image.argb(x,y)&0xFFFFFF)!=0)visible++;
            assertTrue(visible>1000,"destination world must be visible outside the HUD");
            assertTrue(route.sinking());assertTrue(ready,"native victory must reach playable Act2");
        }
    }
}
