package com.openggf.game.sonic3k.ending;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.internal.NativeArenaCameraFraming;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual final-fight input through the native camera reset and bad ending. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kKnucklesTerminalPixels {
    @Test
    void wideTerminalDisplaysRomMechaPixelsAfterCameraResetAndStopsCloudEmission() throws Exception {
        var movie=new Bk2MovieLoader().loadMovieOrInputLog(
                Path.of("src/test/resources/routes/s3k/ssz2-final-defeat.bk2"));
        var settings=new GameplayCaptureSession.Settings(800,"knuckles","","off",null,null,null,
                "0000000",false,false,410766,null);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),10,1,settings);
            for(int input=0;input<30_000;input++) {
                session.step(input<movie.getFrameCount()?movie.getFrame(input):null);
                if(input>=8700||input%32==0)session.renderFrame();
                assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
                assertFalse(session.player().getDead(),"actual final route at input "+input);
                var level=GameServices.level();
                var state=(SszZoneRuntimeState)GameServices.zoneRuntimeState();
                var post=level.getObjectManager().activeObjectsOfType(S3kPostCreditsObjectInstance.class)
                        .stream().filter(p->p.phase()==7&&p.terminalInputReady()).findFirst().orElse(null);
                if(post==null)continue;
                assertEquals(0,GameServices.camera().getX(),"loc_58B7C resets the native camera");
                assertEquals(0x1CB,GameServices.camera().getMinX(),"native arena words survive the reset");
                assertEquals(0x1CB,GameServices.camera().getMaxX());
                var framing=(NativeArenaCameraFraming)level.getZoneFeatureProvider();
                assertEquals(240,framing.presentationNativeHorizontalOrigin().orElseThrow());
                assertEquals(0,state.ending().clouds().mask(),"loc_5B2CE disables new clouds");
                assertTrue(level.getObjectManager().activeObjectsOfType(EndingCloudObjectInstance.class)
                        .stream().noneMatch(EndingCloudObjectInstance::emitter));
                var provider=(Sonic3kObjectArtProvider)level.getGameModule().getObjectArtProvider();
                var sheet=provider.getSheet(Sonic3kObjectArtKeys.MECHA_SONIC);
                var mapping=sheet.getFrame(post.mappingFrame());
                var picture=session.render();
                int xOrigin=240+post.getX()-0x80,yOrigin=post.getY()-0x80;
                int samples=0,matches=0;
                for(var piece:mapping.pieces())for(int column=0;column<piece.widthTiles();column++)
                    for(int row=0;row<piece.heightTiles();row++) {
                        var pattern=sheet.getPatterns()[piece.tileIndex()+column*piece.heightTiles()+row];
                        for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                            int nibble=pattern.getPixel(x,y)&15;if(nibble==0)continue;
                            var color=level.getCurrentLevel().getPalette(sheet.getPaletteIndex()).colors[nibble];
                            int rgb=(color.r&255)<<16|(color.g&255)<<8|(color.b&255);
                            if(rgb==0)continue;
                            int px=xOrigin+piece.xOffset()+(piece.hFlip()?piece.widthTiles()*8-1-column*8-x:column*8+x);
                            int py=yOrigin+piece.yOffset()+(piece.vFlip()?piece.heightTiles()*8-1-row*8-y:row*8+y);
                            assertTrue(px>=240&&px<560,"ROM body belongs inside the native window");
                            assertTrue(py>=0&&py<picture.height());
                            samples++;if((picture.argb(px,py)&0xFFFFFF)==rgb)matches++;
                        }
                    }
                assertTrue(samples>300,"actual ROM Mecha body has a visible footprint");
                assertTrue(matches>samples*3/4,"terminal body hidden/displaced: "+matches+" of "+samples);
                System.out.println("Knuckles terminal pixel witness input="+input+" samples="+samples+" matches="+matches);
                return;
            }
            fail("Actual final fight did not reach the bad Knuckles terminal");
        }
    }
}
