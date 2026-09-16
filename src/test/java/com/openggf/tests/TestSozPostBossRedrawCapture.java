package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.SozEndBossVictoryRoute;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.rules.*;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Controller-driven BG2C capture; the counterfactual changes only the render descriptor owner. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named="soz.postboss.redraw.capture", matches=".+")
class TestSozPostBossRedrawCapture {
    @ParameterizedTest @ValueSource(ints={320,528,800})
    void capture(int width) throws Exception {
        Path out=Path.of(System.getProperty("soz.postboss.redraw.capture"),"width-"+width);
        Files.createDirectories(out.resolve("frames"));
        var settings=new GameplayCaptureSession.Settings(width,"sonic","","off",null,0x51C0,0x620);
        try(var session=new GameplayCaptureSession(settings);var csv=Files.newBufferedWriter(out.resolve("state.csv"))) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),8,1,settings);
            assertTrue(GameServices.level().getObjectManager().getObjectServices().playerQuery().sidekicks().isEmpty());
            session.player().setRingCount(99);var route=new SozEndBossVictoryRoute();
            int frames=0,after=0;long changed=0,afterEntryChanged=0;boolean entered=false,restored=false;
            csv.write("frame,routine,revision,remaining,pixel_difference,cache_width\n");
            for(int frame=0;frame<7000;frame++) {
                session.step(route.input(frame,session.player()));
                assertFalse(session.player().getDead(),"frame "+frame);
                var state=S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry()).orElseThrow();
                var e=state.events();
                if(e.backgroundRoutine()==0x2C || entered) {
                    boolean firstEntry=!entered; entered=true;
                    session.render();var phased=session.render();int difference=0;
                    if(e.postBossPlane().revision()==4 && !restored) {
                        var registry=com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
                        var saved=registry.capture();
                        byte[] expected=GameServices.level().getTilemapManager().getBackgroundTilemapData().clone();
                        // Different descriptor history, identical generation: restore must invalidate
                        // the render cache independently of the source owner's numeric revision.
                        e.postBossPlane().begin(0,0,0x340,band->0,(x,y)->0);
                        for(int i=0;i<3;i++)e.postBossPlane().advance(0x340,(x,y)->0);
                        assertEquals(4,e.postBossPlane().revision());
                        GameServices.level().getTilemapManager().setBackgroundTilemapDirty(true);
                        session.render();
                        registry.restore(saved);
                        var replayed=session.render();
                        assertArrayEquals(expected,GameServices.level().getTilemapManager().getBackgroundTilemapData());
                        for(int y=0;y<phased.height();y++)for(int x=0;x<phased.width();x++)assertEquals(phased.argb(x,y),replayed.argb(x,y),"same-revision restore pixel");
                        restored=true;
                    }
                    if(e.postBossPlane().revision()!=0) {
                        byte[] snapshot=state.captureBytes();
                        e.postBossPlane().clear();
                        var whole=session.render();
                        state.restoreBytes(snapshot);
                        session.render();
                        for(int y=0;y<phased.height();y++)for(int x=0;x<phased.width();x++)
                            if(phased.argb(x,y)!=whole.argb(x,y))difference++;
                        ScreenshotCapture.savePNG(whole,out.resolve(String.format("%05d-whole.png",frame)));
                    }
                    changed+=difference; if(!firstEntry)afterEntryChanged+=difference;
                    ScreenshotCapture.savePNG(phased,out.resolve("frames").resolve(String.format("%05d.png",frames++)));
                    csv.write(frame+","+e.backgroundRoutine()+","+e.postBossPlane().revision()+","+e.redrawRemaining()+","+difference+","+GameServices.level().getTilemapManager().getBackgroundTilemapWidthTiles()+"\n");
                    if(e.backgroundRoutine()!=0x2C) {
                        assertEquals(0,e.postBossPlane().revision());
                        var tm=GameServices.level().getTilemapManager();
                        byte[] bytes=tm.getBackgroundTilemapData();
                        for(int y=0;y<tm.getBackgroundTilemapHeightTiles();y++)for(int x=0;x<tm.getBackgroundTilemapWidthTiles();x++) {
                            int offset=(y*tm.getBackgroundTilemapWidthTiles()+x)*4,g=bytes[offset+1]&255;
                            int actual=(bytes[offset]&255)|((g&7)<<8)|(((g>>>3)&3)<<13)|((g&32)!=0?0x800:0)|((g&64)!=0?0x1000:0)|((g&128)!=0?0x8000:0);
                            assertEquals(GameServices.level().getBackgroundTileDescriptorAtWorld(tm.getBgTilemapBaseX()+x*8,y*8),actual,"normal source cache after clear");
                        }
                        if(++after==16)break;
                    }
                }
            }
            assertTrue(entered);assertEquals(16,after);assertTrue(restored);
            if(width==320)assertEquals(0,afterEntryChanged,"native foreground masks rows after the entry art-admission boundary");
            else assertTrue(changed>0,"wide margins expose retained rows");
        }
    }
}
