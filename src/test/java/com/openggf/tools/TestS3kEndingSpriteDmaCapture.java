package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance;
import com.openggf.game.sonic3k.ending.S3kEndingResources;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** GPU integration witness: a sheet first cached blank must display later native module DMA. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingSpriteDmaCapture {
    @ParameterizedTest @ValueSource(ints={320,800})
    void rescuePlanePixelsFollowInPlaceVramWritesAtTheNativeScreenOrigin(int width) throws Exception {
        var settings=new GameplayCaptureSession.Settings(width,"sonic","","off",null,null,null);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            session.step(null);session.renderFrame(); // Register and cache the still-blank plane sheet.
            var level=GameServices.level().getCurrentLevel();
            var resident=level.getPattern(0x1E3);
            for(int i=1;i<600;i++){session.step(null);session.renderFrame();}
            session.step(null);var picture=session.render();
            assertSame(resident,level.getPattern(0x1E3),"native module DMA updates resident Pattern in place");
            assertPlaneBodyPixels(picture,width);
        }
    }
    @ParameterizedTest
    @CsvSource({"2000,4","3000,8","3700,12"})
    void tailsSuperDepartureUsesRenderCopiesForItsNativeScreenPlane(int input,int foreground) throws Exception {
        var settings=new GameplayCaptureSession.Settings(800,"tails","","off",null,null,null,"3333333",false,false);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            for(int i=0;i<input;i++){session.step(null);if(i%8==0)session.renderFrame();}
            // VInt latches the preceding loop's CRAM with its sprite table.
            // During native FG12 fade, current CPU colours can be one dispatch ahead.
            int[][] displayedPalette=paletteRgb();
            session.step(null);var picture=session.render();var camera=GameServices.camera();
            var zone=(com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState)GameServices.zoneRuntimeState();
            assertEquals(2,zone.ending().progress());assertEquals(foreground,zone.screen().foregroundRoutine);
            // Ending_ScreenInit retains physical $200; foregroundPosition updates only the copy.
            assertEquals(0x200,camera.getX());assertEquals(608,camera.getXCopy());
            var framing=(com.openggf.game.internal.NativeArenaCameraFraming)GameServices.level().getZoneFeatureProvider();
            int origin=framing.presentationNativeHorizontalOrigin().orElseThrow();
            var mask=com.openggf.graphics.ArenaMaskState.fromBounds(origin,origin,camera.getXWithShake(),800,input,false);
            assertEquals(240,mask.left());assertEquals(560,mask.right());assertEquals(0,mask.opacityAt(400));
            var player=GameServices.sprites().getMainPlayable();
            if(foreground!=12){
                assertEquals(player.getCentreX()+112,player.getRenderCentreX()-camera.getXWithShake(),
                        "P1's derived offsets refresh after ScreenEvents publishes the copies");
                assertEquals(player.getCentreY()-128,player.getRenderCentreY()-camera.getYWithShake());
            }
            var director=GameServices.level().getObjectManager().activeObjectsOfType(EndingRescueDirectorObjectInstance.class)
                    .stream().filter(o->o.sequence()==EndingRescueDirectorObjectInstance.RESCUE).findFirst();
            // The global8 large Tornado natively retires before this departure.
            // FG4 uses the extra plane before its animation threshold; FG8 uses
            // Tails' reversed small plane. FG12's aircraft has already exited.
            if(foreground==4){var owner=director.orElseThrow();assertEquals(4,owner.getSlotIndex());
                assertEquals(24,owner.routine());assertTrue(owner.getX()<0x140);
                assertDeparturePlanePixels(picture,owner,1,0,false,displayedPalette);}
            else if(foreground==8){var owner=director.orElseThrow();assertEquals(4,owner.getSlotIndex());
                assertEquals(30,owner.routine());
                assertDeparturePlanePixels(picture,owner,6,1,true,displayedPalette);}
            else {
                assertTrue(director.isEmpty(),"loc_5DE3A signals FG12 and deletes the rescue owner");
                java.util.Set<Integer> colors=new java.util.HashSet<>();
                for(var line:displayedPalette)for(int rgb:line)colors.add(rgb);
                int samples=0,matches=0;
                for(int y=0;y<224;y+=4)for(int x=240;x<560;x+=4){
                    samples++;if(colors.contains(picture.argb(x,y)&0xFFFFFF))matches++;
                }
                assertTrue(matches>samples*0.99,"native fade pixels replaced by mask grain: "+matches+"/"+samples);
            }
            // Projection must not repair native RAM into artificial equality.
            assertEquals(0x200,camera.getX());assertEquals(608,camera.getXCopy());
        }
    }

    @org.junit.jupiter.api.Test
    void initialNativeStarfieldDoesNotSampleSeaInLeftRingColumns() throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"sonic","","off",null,null,null,"1111111",false,false,0,null);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            GameServices.rng().setSeed(0); // Declared common authoring setup, before initial Process_Sprites only.
            for(int outer=0;outer<65;outer++){session.step(null);session.renderFrame();}
            var image=session.render();
            var state=(com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState)GameServices.zoneRuntimeState();
            assertEquals(0,state.screen().foregroundRoutine);assertEquals(0,state.screen().backgroundRoutine);
            assertEquals(65,GameServices.level().getObjectManager().getFrameCounter());
            // Native declared-spec-v2, published input63: this unobstructed left
            // footprint is black starfield with blue/cyan stars, not the sea rows.
            // Its native pixels have no red component and3056/3136 are black.
            // Keep exact engine RGB; the core DAC transfer is a separate limit.
            int black=0;
            for(int y=35;y<133;y++)for(int x=0;x<32;x++){
                int rgb=image.argb(x,y)&0xFFFFFF;
                assertEquals(0,(rgb>>>16)&255,"sea leaked into native starfield at "+x+","+y);
                if(rgb==0)black++;
            }
            assertTrue(black>=3000,"native starfield footprint lost: black="+black);
            assertTrue(black<3136,"ROM stars must still be visible");
        }
    }

    private static void assertDeparturePlanePixels(com.openggf.graphics.RgbaImage picture,
            EndingRescueDirectorObjectInstance owner,int frameIndex,int paletteBase,boolean flip,int[][] palette) throws Exception {
        var level=GameServices.level().getCurrentLevel();
        var frame=S3kSpriteDataLoader.loadMappingFrames(com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom()),
                S3kEndingResources.MAP_ENDING_PLANE_EXTRA).get(frameIndex);
        int originX=owner.getX()+112,originY=owner.getY()-128,samples=0,matches=0;
        for(var piece:frame.pieces())for(int column=0;column<piece.widthTiles();column++)for(int row=0;row<piece.heightTiles();row++){
            var pattern=level.getPattern(0x26E+piece.tileIndex()+column*piece.heightTiles()+row);
            for(int y=0;y<8;y++)for(int x=0;x<8;x++){
                int nibble=pattern.getPixel(x,y)&15;if(nibble==0)continue;
                int px=originX+(flip?-piece.xOffset()-piece.widthTiles()*8:piece.xOffset())
                        +((piece.hFlip()^flip)?piece.widthTiles()*8-1-column*8-x:column*8+x);
                int py=originY+piece.yOffset()+(piece.vFlip()?piece.heightTiles()*8-1-row*8-y:row*8+y);
                if(px<240||px>=560||py<0||py>=224)continue;
                samples++;if((picture.argb(px,py)&0xFFFFFF)==palette[(paletteBase+piece.paletteIndex())&3][nibble])matches++;
            }
        }
        assertTrue(samples>8,"the native departure frame has a visible footprint");
        assertTrue(matches>samples/2,"native departure plane absent/displaced: "+matches+"/"+samples);
    }

    private static void assertPlaneBodyPixels(com.openggf.graphics.RgbaImage picture,int width) throws Exception {
        assertPlaneBodyPixels(picture,width,paletteRgb());
    }
    private static int[][] paletteRgb(){
        int[][] colors=new int[4][16];var level=GameServices.level().getCurrentLevel();
        for(int line=0;line<4;line++)for(int n=0;n<16;n++){
            var color=level.getPalette(line).colors[n];
            colors[line][n]=(color.r&255)<<16|(color.g&255)<<8|(color.b&255);
        }
        return colors;
    }
    private static void assertPlaneBodyPixels(com.openggf.graphics.RgbaImage picture,int width,int[][] palette) throws Exception {
        var level=GameServices.level().getCurrentLevel();
        var plane=GameServices.level().getObjectManager()
                .activeObjectsOfType(EndingRescueDirectorObjectInstance.Actor.class).stream()
                .filter(actor->actor.getSlotIndex()==8).findFirst().orElseThrow();
        var frame=S3kSpriteDataLoader.loadMappingFrames(com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom()),
                S3kEndingResources.MAP_ENDING_PLANE,13).get(1);
        int nativeX=plane.getX()-0x80+(width-320)/2,nativeY=plane.getY()-0x80;
        int background=picture.argb(0,picture.height()-1)&0xFFFFFF;
        int samples=0,matches=0;
        for(var piece:frame.pieces())for(int column=0;column<piece.widthTiles();column++)
            for(int row=0;row<piece.heightTiles();row++){
                var pattern=level.getPattern(0x1E3+piece.tileIndex()+column*piece.heightTiles()+row);
                for(int y=0;y<8;y++)for(int x=0;x<8;x++){
                    int nibble=pattern.getPixel(x,y)&15;if(nibble==0)continue;
                    int rgb=palette[piece.paletteIndex()][nibble];
                    if(rgb==background)continue;
                    int px=nativeX+piece.xOffset()+(piece.hFlip()?piece.widthTiles()*8-1-column*8-x:column*8+x);
                    int py=nativeY+piece.yOffset()+(piece.vFlip()?piece.heightTiles()*8-1-row*8-y:row*8+y);
                    if(px<0||px>=width||py<0||py>=picture.height())continue;
                    samples++;if((picture.argb(px,py)&0xFFFFFF)==rgb)matches++;
                }
            }
        assertTrue(samples>500,"the actual ROM body has a substantial visible footprint");
        // Pilot and propeller pieces overlap part of the body. Its remaining
        // pixels must still match the ROM pattern/palette at native coordinates.
        assertTrue(matches>samples/2,"plane body missing or displaced: matched "+matches+" of "+samples);
    }
}
