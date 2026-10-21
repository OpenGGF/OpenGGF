package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The S3 eyecatch's Tails child loc_5F3AA (044fa46 sonic3k.asm:125959) ends its initialization with
 * CreateChild1_Normal(ChildObjDat_60204): one loc_5F3EA at (-$10,+8). loc_5F3EA takes the parent's
 * mappings and art (SetUp_ObjAttributes3, word_60170: priority $100, frame $A) and falls into
 * loc_5F400, which runs Animate_RawNoSST(AniRaw_6026F: delay $B, frames 5-9) and Draw_Sprite on the
 * same pass. The native Tails/+2 NTSC and Sonic/+2 PAL terminals show this tail at every spot.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingS3EyecatchTail {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();}

    @Test void tailsChildTailIsDrawnFromTheNativeAnimationOnTheEyecatch() throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"tails","","off",null,null,null,"3333333",false,false);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            int init=-1;
            for(int input=0;input<40_000;input++) {
                session.step(new Bk2FrameInput(input,0,0,false,0,0,false,"neutral"));
                assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode(),"terminal reached before the exit");
                var post=GameServices.level().getObjectManager().activeObjectsOfType(S3kPostCreditsObjectInstance.class)
                        .stream().findFirst().orElse(null);
                if(post==null||post.phase()!=6)continue;
                if(init<0)init=input;
                int k=input-init;
                if(k!=1&&k!=76){session.renderFrame();continue;}
                // The picture after step k shows the sprite table prepared by pass j = k-1 (the
                // engine and native both publish it at the following V-int).
                var picture=session.render();int j=k-1;
                // AniRaw_6026F from anim_frame 0: the init pass already advances to frame 6;
                // each frame holds $B+1 passes and $FC reloads frame 5.
                var tail=footprint(new int[]{6,7,8,9,5}[(j/12)%5],0x120+8-0x10,0xF0+0x40+8);
                // loc_5F3AA returns before Draw_Sprite on its init pass and loc_5F3DA animates
                // AniRaw_6026B ($F: frames $A,$B) from the next pass.
                var tails=footprint(j==0?0xA:new int[]{0xB,0xA}[((j-1)/16)%2],0x120+8,0xF0+0x40);
                int tailSamples=0,tailMatches=0,tailsOnly=0,tailsOnlyBlank=0,overlap=0,overlapTails=0;
                for(var e:tail.entrySet()){
                    tailSamples++;if(argb(picture,e.getKey())==e.getValue())tailMatches++;
                    if(tails.containsKey(e.getKey())){overlap++;if(argb(picture,e.getKey())==tails.get(e.getKey()))overlapTails++;}
                }
                for(var e:tails.entrySet())if(!tail.containsKey(e.getKey())){
                    tailsOnly++;if(argb(picture,e.getKey())==0)tailsOnlyBlank++;
                }
                assertTrue(tailSamples>20,"ROM tail has a footprint at pass "+j);
                if(j==0) {
                    // Only loc_5F400 reaches Draw_Sprite on the initialization pass; every line is
                    // one level above black, so the tail is drawn and Tails' own pixels stay empty.
                    assertTrue(tailMatches>tailSamples*3/4,"tail drawn on the init pass: "+tailMatches+" of "+tailSamples);
                    assertEquals(tailsOnly,tailsOnlyBlank,"loc_5F3AA draws nothing on its init pass");
                    continue;
                }
                // Native SAT (Tails/+2 NTSC poll 14400/15359): Sonic, Tails, Knuckles, then the tail.
                assertTrue(overlap>0,"the tail and Tails overlap");
                assertEquals(overlap,overlapTails,"Tails (earlier slot, earlier SAT link) covers the tail");
                assertTrue(tailMatches>(tailSamples-overlap)*3/4,"tail drawn: "+tailMatches+" of "+tailSamples);
                assertTrue(((EndingZoneRuntimeState)GameServices.zoneRuntimeState()).ending().progress()>0);
                return;
            }
            fail("Tails/+2 ending did not reach the S3 eyecatch");
        }
    }

    /**
     * Declared SSZ2 final-fight inlet, seven Super Emeralds, accepted authoring clock/input.
     * This independently checks the shipped Knuckles route's eyecatch, not Knuckles in D01.
     * Native 044fa46 loc_5F26C's children have priority $100 and SST order Sonic, Tails,
     * Knuckles, tail; $F800 at native input433872 confirms the displayed frames1/A/DC/8.
     * The current CPU SST has tail9 there: compare the displayed table, one V-int earlier.
     */
    @Test void knucklesEyecatchKeepsEarlierNativeChildrenInFrontAfterSatPublication() throws Exception {
        var movie=new com.openggf.debug.playback.Bk2MovieLoader().loadMovieOrInputLog(
                java.nio.file.Path.of("src/test/resources/routes/s3k/ssz2-final-defeat.bk2"));
        var settings=new GameplayCaptureSession.Settings(320,"knuckles","","off",null,null,null,
                "3333333",false,false,410766,null);
        try(var session=new GameplayCaptureSession(settings)) {
            var config=GameServices.configuration();
            config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_EXPANSION,"LIMITED");
            config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_PROFILE,"RAW_RGB");
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),10,1,settings);
            int init=-1;
            for(int input=0;input<40_000;input++) {
                session.step(input<movie.getFrameCount()?movie.getFrame(input):null);
                assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
                assertFalse(session.player().getDead(),"accepted SSZ2 inlet at input "+input);
                // Render before and throughout the whole terminal, preserving publication state.
                if(input>=8700||input%32==0)session.renderFrame();
                var post=GameServices.level().getObjectManager().activeObjectsOfType(S3kPostCreditsObjectInstance.class)
                        .stream().findFirst().orElse(null);
                if(post==null||post.phase()!=6)continue;
                if(init<0)init=input;
                if(input-init!=576)continue;
                assertEquals(com.openggf.graphics.color.DisplayColorExpansion.LIMITED,
                        GameServices.graphics().getDisplayColorExpansion());
                assertEquals(com.openggf.graphics.color.DisplayColorProfile.RAW_RGB,
                        GameServices.graphics().getDisplayColorProfile());
                var groups=java.util.List.of(
                        footprint(1,0x104,0x11C,S3kEndingArt.S3_GRAPHICS,0),
                        footprint(0xA,0x144,0x11C,S3kEndingArt.S3_GRAPHICS,0),
                        footprint(0xDC,0x120,0x130,S3kEndingArt.EYECATCH_KNUCKLES,2),
                        footprint(8,0x134,0x124,S3kEndingArt.S3_GRAPHICS,0));
                var expected=new java.util.HashMap<Long,Integer>();
                for(var group:groups)group.forEach(expected::putIfAbsent);
                var picture=session.render();
                String capture=System.getProperty("openggf.endingOrder.capture");
                if(capture!=null)com.openggf.graphics.ScreenshotCapture.savePNG(picture,java.nio.file.Path.of(capture));
                int mismatches=0,distinguishableOverlaps=0;
                for(var e:expected.entrySet()) {
                    if(argb(picture,e.getKey())!=e.getValue())mismatches++;
                    var colors=new java.util.HashSet<Integer>();
                    for(var group:groups)if(group.containsKey(e.getKey()))colors.add(group.get(e.getKey()));
                    if(colors.size()>1)distinguishableOverlaps++;
                }
                assertTrue(expected.size()>800,"all four actual ROM actors have a visible footprint");
                assertTrue(distinguishableOverlaps>50,"different palettes expose opaque overlap priority");
                System.out.println("Eyecatch native-order input="+input+" init="+init+" samples="+expected.size()
                        +" distinguishableOverlaps="+distinguishableOverlaps+" mismatches="+mismatches);
                assertEquals(0,mismatches,"native first-opaque child order; distinguishable overlaps="+distinguishableOverlaps);
                return;
            }
            fail("accepted Knuckles SSZ2 product did not reach the eyecatch checkpoint");
        }
    }

    private static int argb(com.openggf.graphics.RgbaImage picture,long key){
        return picture.argb((int)(key>>>16),(int)(key&0xFFFF))&0xFFFFFF;
    }
    /** Opaque screen pixels of one Map_S3EndingGraphics frame at a screen-space x_pos/y_pos, palette line 0. */
    private static java.util.Map<Long,Integer> footprint(int frame,int xPos,int yPos){
        return footprint(frame,xPos,yPos,S3kEndingArt.S3_GRAPHICS,0);
    }
    private static java.util.Map<Long,Integer> footprint(int frame,int xPos,int yPos,String art,int palette){
        var level=GameServices.level();
        var provider=(Sonic3kObjectArtProvider)level.getGameModule().getObjectArtProvider();
        var sheet=provider.getSheet(art);
        var mapping=sheet.getFrame(frame);
        var pixels=new java.util.HashMap<Long,Integer>();
        for(var piece:mapping.pieces())for(int column=0;column<piece.widthTiles();column++)
            for(int row=0;row<piece.heightTiles();row++) {
                var pattern=sheet.getPatterns()[piece.tileIndex()+column*piece.heightTiles()+row];
                for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                    int nibble=pattern.getPixel(x,y)&15;if(nibble==0)continue;
                    var color=level.getCurrentLevel().getPalette(palette).colors[nibble];
                    int px=xPos-0x80+piece.xOffset()+(piece.hFlip()?piece.widthTiles()*8-1-column*8-x:column*8+x);
                    int py=yPos-0x80+piece.yOffset()+(piece.vFlip()?piece.heightTiles()*8-1-row*8-y:row*8+y);
                    if(px<0||py<0||px>=320||py>=224)continue;
                    // Palette stores canonical Full RGB; the tested picture uses the selected display range.
                    int rgb=com.openggf.graphics.color.DisplayColorConverter.toPackedRgb(
                            color.r&255,color.g&255,color.b&255,GameServices.graphics().getDisplayColorProfile(),
                            GameServices.graphics().getDisplayColorExpansion());
                    pixels.put((long)px<<16|py,rgb);
                }
            }
        return pixels;
    }
}
