package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.recording.RecordedFrameInput;
import com.openggf.game.recording.UserRecordingWriter;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.*;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Continuous ordinary-input battle/capsule/LRZ movie, with replayable input log. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named="soz.endboss.victory.capture",matches=".+")
class TestSozEndBossVictoryCapture {
    @Test void captureBattleAndLrz() throws Exception {
        Path output=Path.of(System.getProperty("soz.endboss.victory.capture"));
        Files.createDirectories(output.resolve("frames"));
        var settings=new GameplayCaptureSession.Settings(320,"sonic","","off",null,0x51C0,0x620);
        var inputs=new java.util.ArrayList<RecordedFrameInput>();
        try(var session=new GameplayCaptureSession(settings);var csv=Files.newBufferedWriter(output.resolve("state.csv"))) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),8,1,settings);
            assertTrue(GameServices.level().getObjectManager().getObjectServices().playerQuery().sidekicks().isEmpty());
            session.player().setRingCount(99);var route=new SozEndBossVictoryRoute();int readyFrame=-1,hits=0,lastHp=8;
            csv.write(GameplayCaptureSession.stateHeader()+",zone,act,boss_hp,p1_mask");csv.newLine();
            for(int frame=0;frame<8500;frame++) {
                var input=GameServices.level().getCurrentZone()==8?route.input(frame,session.player()):null;
                int mask=input==null?0:input.p1InputMask();
                inputs.add(new RecordedFrameInput(frame,mask,(mask&16)!=0?1:0,false,0,0,false));
                session.step(input);var boss=SozEndBossVictoryRoute.boss();
                if(boss!=null&&boss.getCollisionProperty()<lastHp){hits+=lastHp-boss.getCollisionProperty();lastHp=boss.getCollisionProperty();}
                csv.write(session.stateLine(frame,input)+","+GameServices.level().getCurrentZone()+","+GameServices.level().getCurrentAct()+","+(boss==null?-1:boss.getCollisionProperty())+","+mask);csv.newLine();
                if(frame%4==0)ScreenshotCapture.savePNG(session.render(),output.resolve("frames").resolve(String.format("%05d.png",frame)));
                assertFalse(session.player().getDead(),"player died at "+frame);
                if(GameServices.level().getCurrentZone()==9&&!session.player().isControlLocked()&&readyFrame<0)readyFrame=frame;
                if(readyFrame>=0&&frame-readyFrame>=180)break;
            }
            Files.writeString(output.resolve("Input Log.txt"),UserRecordingWriter.inputLogText(inputs));
            assertEquals(8,hits);assertTrue(readyFrame>=0,"LRZ playable controls");
            assertEquals(0,GameServices.level().getCurrentAct());
            var image=session.render();int visible=0;
            for(int y=48;y<190;y++)for(int x=0;x<image.width();x++)if((image.argb(x,y)&0xFFFFFF)!=0)visible++;
            assertTrue(visible>1000,"destination world visible outside HUD");
        }
    }
}
