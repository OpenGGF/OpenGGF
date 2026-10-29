package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.ending.EndingAnimalObjectInstance;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native D01 VDP B-high>A-low and reunion's explicit low-sea layout owner. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingScenePriorityPixels {
    private GameplayCaptureSession.Settings settings(int width){
        return new GameplayCaptureSession.Settings(width,"sonic","","off",null,null,null,"1111111",false,false,0,null);
    }
    @ParameterizedTest @ValueSource(ints={320,800})
    void departingIslandLowColumnsAreCoveredByNativeHighSeaPixels(int width)throws Exception{
        var settings=settings(width);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            GameServices.rng().setSeed(0); // Independently declared scene authoring setup, only before setup dispatch.
            advanceToProducer(session,3071);
            var zone=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();
            assertEquals(4,zone.screen().backgroundRoutine);
            var scroll=GameServices.parallax().getHScrollForShader();int bgY=GameServices.parallax().getVscrollFactorBG();
            var level=GameServices.level().getCurrentLevel();int samples=0,matches=0;
            int inset=(width-320)/2;int expected=rgb(3,10);
            boolean[] producerFootprint=new boolean[width*224];
            for(int y=164;y<200;y++)for(int x=inset+120;x<inset+220;x++){
                int fgX=x-(short)(scroll[y]>>>16),fgY=y+GameServices.parallax().getVscrollFactorFG();
                int fg=zone.ending().scene().foreground().descriptor(fgX,fgY);
                if((fg&0x8000)!=0){
                    int px=fgX&7,py=fgY&7;if((fg&0x800)!=0)px=7-px;if((fg&0x1000)!=0)py=7-py;
                    if((level.getPattern(fg&0x7FF).getPixel(px,py)&15)!=0)continue; // A-high legitimately wins.
                }
                int sourceX=x-(short)scroll[y],sourceY=y+bgY;
                int descriptor=zone.ending().scene().background().descriptor(sourceX,sourceY);
                if((descriptor&0x8000)==0)continue;
                var pattern=level.getPattern(descriptor&0x7FF);
                int px=(sourceX&7),py=(sourceY&7);
                if((descriptor&0x800)!=0)px=7-px;if((descriptor&0x1000)!=0)py=7-py;
                if((pattern.getPixel(px,py)&15)!=10)continue;
                producerFootprint[y*width+x]=true;
            }
            session.step(null);var image=session.render();
            for(int y=164;y<200;y++)for(int x=inset+120;x<inset+220;x++)if(producerFootprint[y*width+x]){
                samples++;if((image.argb(x,y)&0xFFFFFF)==expected)matches++;
            }
            assertTrue(samples>2000,"ROM sea has a substantial opaque high-priority footprint");
            assertTrue(matches>samples*0.99,"low island columns leak over B-high sea: "+matches+"/"+samples);
        }
    }
    @ParameterizedTest @ValueSource(ints={320,800})
    void reunionNativeLowSeaLayoutAllowsItsExistingDolphinSpritesToDraw(int width)throws Exception{
        var settings=settings(width);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);GameServices.rng().setSeed(0);
            advanceToProducer(session,5119);
            int[][] palette=new int[4][16];for(int line=0;line<4;line++)for(int c=0;c<16;c++)palette[line][c]=rgb(line,c);
            // The next VInt publishes the SAT prepared by this completed object pass.
            // Geometry and CRAM must come from that same producer generation.
            int[] expected=dolphinProducerPixels(width,palette);
            session.step(null);var image=session.render();
            var zone=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();assertEquals(4,zone.screen().backgroundRoutine);
            var map=GameServices.level().getCurrentLevel().getMap();
            for(int i=0;i<4;i++)assertEquals((i&1)==0?0x1A:0x1B,map.getValue(1,4+i,7)&255,
                    "loc_5B03A changes only these four authored layout bytes before delayed redraw");
            int samples=0,matches=0;
            for(int y=160;y<224;y++)for(int x=0;x<width;x++){
                int rgb=expected[y*width+x];if(rgb<0)continue;
                samples++;if((image.argb(x,y)&0xFFFFFF)==rgb)matches++;
            }
            assertTrue(samples>500,"actual native dolphin mappings have a substantial visible footprint");
            assertEquals(samples,matches,"published dolphin art/palette must match its producer generation");
        }
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"320,1536","800,1536","320,3072","800,3072"})
    void highSeaReplayUsesTheSamePublishedScrollAsTheRearPass(int width,int setupAndOrdinaryPasses)throws Exception{
        var settings=settings(width);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);GameServices.rng().setSeed(0);
            advanceToProducer(session,setupAndOrdinaryPasses);
            var zone=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();assertEquals(4,zone.screen().backgroundRoutine);
            int[] producerScroll=GameServices.parallax().getHScrollForShader().clone();
            int bgY=GameServices.parallax().getVscrollFactorBG(),fgY=GameServices.parallax().getVscrollFactorFG();
            int[] expected=new int[width*224];java.util.Arrays.fill(expected,-1);
            var level=GameServices.level().getCurrentLevel();int inset=(width-320)/2;
            for(int y=210;y<224;y++)for(int x=inset;x<inset+320;x++){
                int fx=x-(short)(producerScroll[y]>>>16),fy=y+fgY;
                int foreground=zone.ending().scene().foreground().descriptor(fx,fy);
                if((foreground&0x8000)!=0&&patternNibble(level,foreground,fx,fy)!=0)continue;
                int bx=x-(short)producerScroll[y],by=y+bgY;
                int background=zone.ending().scene().background().descriptor(bx,by);
                if((background&0x8000)==0)continue;
                int nibble=patternNibble(level,background,bx,by);if(nibble==0)continue;
                expected[y*width+x]=rgb((background>>>13)&3,nibble);
            }
            // The next VInt displays this completed producer, not the CPU deformation
            // calculated later in the same step. No comparison-derived offset is used.
            session.step(null);var image=session.render();
            assertFalse(java.util.Arrays.equals(producerScroll,GameServices.parallax().getHScrollForShader()),
                    "the ordinary owner must advance a distinguishable CPU scroll generation");
            int samples=0;
            for(int y=210;y<224;y++)for(int x=inset;x<inset+320;x++){
                int color=expected[y*width+x];if(color<0)continue;samples++;
                assertEquals(color,image.argb(x,y)&0xFFFFFF,"high sea replay used another scroll at "+x+","+y);
            }
            assertTrue(samples>3000,"native high sea owns a substantial unobstructed lower footprint");
        }
    }
    @ParameterizedTest @ValueSource(ints={320,800})
    void reunionPublishesTheRomSonicPoseWithItsExplicitNativeRenderFlip(int width)throws Exception{
        var settings=settings(width);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);GameServices.rng().setSeed(0);
            advanceToProducer(session,5119);
            var player=session.player();assertEquals(0xBA,player.getMappingFrame());
            assertTrue(player.getRenderHFlip(),"loc_5E1F0 writes render_flags independently of status");
            var art=new com.openggf.game.sonic3k.Sonic3kPlayerArt(
                    com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom())).loadSonic();
            var bank=new com.openggf.level.Pattern[art.bankSize()];int destination=0;
            for(var request:art.dplcFrames().get(0xBA).requests())for(int i=0;i<request.count();i++)
                bank[destination++]=art.artTiles()[request.startTile()+i];
            int[] expected=new int[width*224];java.util.Arrays.fill(expected,-1);
            boolean[] earlierPlane=earlierPlaneProducerOpacity(width);
            int ox=player.getCentreX()-128+(width-320)/2,oy=player.getCentreY()-128;
            for(var piece:art.mappingFrames().get(0xBA).pieces())
                for(int col=0;col<piece.widthTiles();col++)for(int row=0;row<piece.heightTiles();row++){
                    var pattern=bank[piece.tileIndex()+col*piece.heightTiles()+row];
                    for(int py=0;py<8;py++)for(int px=0;px<8;px++){
                        int nibble=pattern.getPixel(px,py)&15;if(nibble==0)continue;
                        int x=ox-piece.xOffset()-piece.widthTiles()*8
                                +(piece.hFlip()?col*8+px:piece.widthTiles()*8-1-col*8-px);
                        int y=oy+piece.yOffset()+(piece.vFlip()?piece.heightTiles()*8-1-row*8-py:row*8+py);
                        if(x<0||x>=width||y<0||y>=224||earlierPlane[y*width+x])continue;
                        if(expected[y*width+x]<0)expected[y*width+x]=rgb(piece.paletteIndex(),nibble);
                    }
                }
            session.step(null);var image=session.render();int samples=0,matches=0;
            for(int y=0;y<224;y++)for(int x=0;x<width;x++){
                int color=expected[y*width+x];if(color<0)continue;samples++;
                if((image.argb(x,y)&0xFFFFFF)==color)matches++;
            }
            assertTrue(samples>500,"ROM Map_Sonic BA has a substantial visible footprint");
            assertEquals(samples,matches,"native scripted facing must reach the published player sprite");
        }
    }
    @ParameterizedTest @ValueSource(ints={320,800})
    void tornadoMultipartKeepsMainSatPiecesAheadOfItsNoseAndPropeller(int width)throws Exception{
        var settings=settings(width);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);GameServices.rng().setSeed(0);
            int completedPasses=0;
            for(int producerPasses:new int[]{1535,5119}){
                advanceToProducer(session,producerPasses-completedPasses);
                completedPasses=producerPasses;
                var plane=GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.Actor.class).stream()
                        .filter(actor->actor.flightFrame()==1).findFirst().orElseThrow();
                var header=plane.nativeSstState();boolean flip=(header.renderFlags()&1)!=0;
                assertEquals(producerPasses==5119,flip,"native rescue and reunion exercise both main/subsprite facings");
                var reader=com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom());
                var mappings=S3kSpriteDataLoader.loadMappingFrames(reader,
                        com.openggf.game.sonic3k.ending.S3kEndingResources.MAP_ENDING_PLANE,13);
                var method=plane.getClass().getDeclaredMethod("subsprites");method.setAccessible(true);
                var parts=(java.util.List<?>)method.invoke(plane);assertEquals(2,parts.size());
                int[][] colors=new int[3][];
                colors[0]=planeMappingPixels(width,mappings.get(1),plane.getX(),plane.getY(),header.artTile(),flip);
                int index=0;
                var counter=plane.getClass().getDeclaredField("noseCounter");counter.setAccessible(true);
                var vInt=plane.getClass().getDeclaredField("nativeVInt");vInt.setAccessible(true);
                for(var part:parts){
                    var frameMethod=part.getClass().getDeclaredMethod("frame");frameMethod.setAccessible(true);
                    var xMethod=part.getClass().getDeclaredMethod("x");xMethod.setAccessible(true);
                    var yMethod=part.getClass().getDeclaredMethod("y");yMethod.setAccessible(true);
                    int frame=(int)frameMethod.invoke(part),x=(int)xMethod.invoke(part),y=(int)yMethod.invoke(part);
                    int offset=com.openggf.game.sonic3k.ending.S3kEndingResources.TORNADO_SUBSPRITE_OFFSETS+index*2;
                    assertEquals(plane.getX()+(flip?-1:1)*(byte)reader.readU8(offset),x,"sub_86458 signed X offset");
                    assertEquals(plane.getY()+(byte)reader.readU8(offset+1),y,"sub_86458 signed Y offset");
                    assertEquals(index==0?reader.readU8(com.openggf.game.sonic3k.ending.S3kEndingResources.TORNADO_NOSE_FRAMES
                            +counter.getInt(plane)):6+(vInt.getInt(plane)&1),frame,"sub_60038 raw frame owner");
                    colors[++index]=planeMappingPixels(width,mappings.get(frame),x,y,header.artTile(),flip);
                }
                // loc_1AEE4 emits the main mapping first; loc_1AF2A then emits sub2/sub3.
                // Reconstruct only genuine different-color opaque overlaps from that producer,
                // rather than choosing coordinates from an observed image difference.
                session.step(null);var image=session.render();completedPasses++;
                int overlaps=0,matches=0;
                for(int y=0;y<224;y++)for(int x=0;x<width;x++){
                    int i=y*width+x,main=colors[0][i];if(main<0)continue;
                    int later=colors[1][i]>=0?colors[1][i]:colors[2][i];
                    if(later<0||later==main)continue;overlaps++;
                    if((image.argb(x,y)&0xFFFFFF)==main)matches++;
                }
                assertTrue(overlaps>50,"ROM main and propeller have a substantial independently reconstructed overlap");
                assertEquals(overlaps,matches,"later multipart mappings overwrite the first native SAT pieces at pass "+producerPasses);
            }
        }
    }
    /** Advance the real ending clock; only unobserved prefix drawing is omitted. */
    private static void advanceToProducer(GameplayCaptureSession session,int passes){
        var drawing=new RouteFrameDrawing();
        advance(session,passes,drawing);
        drawing.checkpoint(session);
    }

    private static void advance(GameplayCaptureSession session,int passes,RouteFrameDrawing drawing){
        for(int pass=0;pass<passes;pass++){
            session.step(null);
            drawing.afterStep(session);
        }
    }

    private record PublishedEnding(int[] pixels,byte[] nativeState,int[] scroll){}

    @Test
    void checkpointDrawingMatchesContinuousReunionPixelsAndNativeState()throws Exception{
        var continuous=captureReunion(true);
        var checkpoint=captureReunion(false);
        assertArrayEquals(continuous.nativeState(),checkpoint.nativeState(),
                "omitting unobserved drawing must retain the complete native ending state");
        assertArrayEquals(continuous.scroll(),checkpoint.scroll(),
                "the same completed producer must own the published scroll");
        assertArrayEquals(continuous.pixels(),checkpoint.pixels(),
                "reconstructed ending pixels must match continuous real drawing");
    }

    private PublishedEnding captureReunion(boolean everyFrame)throws Exception{
        var settings=settings(320);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            GameServices.rng().setSeed(0);
            var drawing=new RouteFrameDrawing(everyFrame);
            advance(session,5119,drawing);
            drawing.checkpoint(session);
            assertEquals(everyFrame?5119:1,drawing.drawnFrames());
            assertEquals(everyFrame?0:5119,drawing.skippedFrames());
            session.step(null);
            var image=session.render();
            long colors=java.util.Arrays.stream(image.pixels()).distinct().count();
            assertTrue(colors>1,"the captured scene must be nonblank; colors="+colors+", everyFrame="+everyFrame);
            var zone=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();
            assertEquals(4,zone.screen().backgroundRoutine);
            var player=session.player();
            var logicalFacing=player.getDirection();
            try{
                // This native pose writes render_flags separately from logical status.
                // Force the two inputs apart so an accidental alias cannot pass both paths.
                player.setDirection(com.openggf.physics.Direction.RIGHT);
                assertTrue(player.getRenderHFlip(),"native render flip must not follow logical facing");
            }finally{
                player.setDirection(logicalFacing);
            }
            return new PublishedEnding(image.pixels().clone(),zone.captureBytes(),
                    GameServices.parallax().getHScrollForShader().clone());
        }
    }

    private static int[] planeMappingPixels(int width,com.openggf.level.render.SpriteMappingFrame frame,
            int nativeX,int nativeY,int artTile,boolean flip){
        int[] colors=new int[width*224];java.util.Arrays.fill(colors,-1);
        int ox=nativeX-128+(width-320)/2,oy=nativeY-128;
        for(var piece:frame.pieces())for(int col=0;col<piece.widthTiles();col++)for(int row=0;row<piece.heightTiles();row++){
            var pattern=GameServices.level().getCurrentLevel().getPattern((artTile&0x7FF)+piece.tileIndex()+col*piece.heightTiles()+row);
            for(int py=0;py<8;py++)for(int px=0;px<8;px++){
                int nibble=pattern.getPixel(px,py)&15;if(nibble==0)continue;
                int x=ox+(flip?-piece.xOffset()-piece.widthTiles()*8:piece.xOffset())
                        +((flip^piece.hFlip())?piece.widthTiles()*8-1-col*8-px:col*8+px);
                int y=oy+piece.yOffset()+(piece.vFlip()?piece.heightTiles()*8-1-row*8-py:row*8+py);
                if(x<0||x>=width||y<0||y>=224)continue;
                int i=y*width+x;if(colors[i]<0)colors[i]=rgb(((artTile>>>13)+piece.paletteIndex())&3,nibble);
            }
        }
        return colors;
    }
    private static boolean[] earlierPlaneProducerOpacity(int width)throws Exception{
        var plane=GameServices.level().getObjectManager().activeObjectsOfType(
                com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.Actor.class).stream()
                .filter(actor->actor.getPriorityBucket()==0).findFirst().orElseThrow();
        var state=plane.nativeSstState();
        var frame=S3kSpriteDataLoader.loadMappingFrames(com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom()),
                com.openggf.game.sonic3k.ending.S3kEndingResources.MAP_ENDING_PLANE,13).get(state.mappingFrame());
        boolean flip=(state.renderFlags()&1)!=0;boolean[] opaque=new boolean[width*224];
        int ox=plane.getX()-128+(width-320)/2,oy=plane.getY()-128;
        for(var piece:frame.pieces())for(int col=0;col<piece.widthTiles();col++)for(int row=0;row<piece.heightTiles();row++){
            var pattern=GameServices.level().getCurrentLevel().getPattern((state.artTile()&0x7FF)+piece.tileIndex()+col*piece.heightTiles()+row);
            for(int py=0;py<8;py++)for(int px=0;px<8;px++){
                if((pattern.getPixel(px,py)&15)==0)continue;
                int x=ox+(flip?-piece.xOffset()-piece.widthTiles()*8:piece.xOffset())
                        +((flip^piece.hFlip())?piece.widthTiles()*8-1-col*8-px:col*8+px);
                int y=oy+piece.yOffset()+(piece.vFlip()?piece.heightTiles()*8-1-row*8-py:row*8+py);
                if(x>=0&&x<width&&y>=0&&y<224)opaque[y*width+x]=true;
            }
        }
        // The actual bucket0 plane is earlier in native SAT than P1. Its
        // nonzero wing pixels win overlap; no observed mismatch coordinates enter this mask.
        return opaque;
    }
    @ParameterizedTest @ValueSource(ints={320,800})
    void tailsEntryPoseKeepsEarlierOpaqueMappingPiecesInFront(int width)throws Exception{
        var settings=new GameplayCaptureSession.Settings(width,"tails","","off",null,null,null,"3333333",false,false,0,null);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);GameServices.rng().setSeed(0);
            // Declared native setup + ordinary controller polls. This completed
            // producer is published by the following VInt, with no fitted shift.
            advanceToProducer(session,63);
            var player=session.player();assertEquals(0xC4,player.getMappingFrame());
            var art=new com.openggf.game.sonic3k.Sonic3kPlayerArt(
                    com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom())).loadTails();
            var bank=new com.openggf.level.Pattern[art.bankSize()];int destination=0;
            for(var request:art.dplcFrames().get(0xC4).requests())for(int i=0;i<request.count();i++)
                bank[destination++]=art.artTiles()[request.startTile()+i];
            int[] first=new int[width*224],last=new int[width*224];java.util.Arrays.fill(first,-1);java.util.Arrays.fill(last,-1);
            int ox=player.getCentreX()-128+(width-320)/2,oy=player.getCentreY()-128;
            for(var piece:art.mappingFrames().get(0xC4).pieces())
                for(int col=0;col<piece.widthTiles();col++)for(int row=0;row<piece.heightTiles();row++){
                    var pattern=bank[piece.tileIndex()+col*piece.heightTiles()+row];
                    for(int py=0;py<8;py++)for(int px=0;px<8;px++){
                        int nibble=pattern.getPixel(px,py)&15;if(nibble==0)continue;
                        int x=ox+piece.xOffset()+(piece.hFlip()?piece.widthTiles()*8-1-col*8-px:col*8+px);
                        int y=oy+piece.yOffset()+(piece.vFlip()?piece.heightTiles()*8-1-row*8-py:row*8+py);
                        if(x<0||x>=width||y<0||y>=224)continue;
                        int index=y*width+x,color=rgb(piece.paletteIndex(),nibble);
                        if(first[index]<0)first[index]=color;last[index]=color;
                    }
                }
            var registry=com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var before=registry.capture();
            session.step(null);var image=session.render();var after=registry.capture();int overlaps=0,matches=0;
            for(int y=0;y<224;y++)for(int x=0;x<width;x++){
                int index=y*width+x;if(first[index]<0||first[index]==last[index])continue;
                overlaps++;if((image.argb(x,y)&0xFFFFFF)==first[index])matches++;
            }
            assertTrue(overlaps>0,"the actual ROM pose must admit nontransparent overlap with distinct current colors");
            assertEquals(overlaps,matches,"the first native SAT piece must win, including the shoe over the final leg piece");
            for(int cycle=0;cycle<2;cycle++){
                registry.restore(before);sameWorld(before,registry.capture());session.step(null);var replay=session.render();
                sameWorld(after,registry.capture());
                for(int y=0;y<224;y++)for(int x=0;x<width;x++)assertEquals(image.argb(x,y),replay.argb(x,y),
                        "restored published player order at "+x+","+y);
                registry.restore(before);sameWorld(before,registry.capture());registry.restore(after);sameWorld(after,registry.capture());
            }
        }
    }

    private static void sameWorld(com.openggf.game.rewind.CompositeSnapshot expected,com.openggf.game.rewind.CompositeSnapshot actual){
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(var key:expected.entries().keySet())assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                key,expected.get(key),actual.get(key)).isEmpty(),"published order world restore "+key);
    }
    private static int patternNibble(com.openggf.level.Level level,int descriptor,int x,int y){
        int px=x&7,py=y&7;if((descriptor&0x800)!=0)px=7-px;if((descriptor&0x1000)!=0)py=7-py;
        return level.getPattern(descriptor&0x7FF).getPixel(px,py)&15;
    }
    private static int[] dolphinProducerPixels(int width,int[][] palette)throws Exception{
        int[] expected=new int[width*224];java.util.Arrays.fill(expected,-1);
        var frames=S3kSpriteDataLoader.loadMappingFrames(com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom()),0x60796);
        var animals=new java.util.ArrayList<>(GameServices.level().getObjectManager().activeObjectsOfType(EndingAnimalObjectInstance.class));
        animals.sort(java.util.Comparator.comparingInt(EndingAnimalObjectInstance::getSlotIndex));
        int dolphinCount=0;
        for(var animal:animals){
            var field=animal.getClass().getDeclaredField("rabbit");field.setAccessible(true);
            if(!field.getBoolean(animal)||animal.routine()!=4)continue;
            dolphinCount++;
            field=animal.getClass().getDeclaredField("mappingFrame");field.setAccessible(true);int frame=field.getInt(animal);
            int ox=animal.getX()-128+(width-320)/2,oy=animal.getY()-128;
            for(var piece:frames.get(frame).pieces())for(int col=0;col<piece.widthTiles();col++)for(int row=0;row<piece.heightTiles();row++){
                var pattern=GameServices.level().getCurrentLevel().getPattern(0x2FF+piece.tileIndex()+col*piece.heightTiles()+row);
                for(int py=0;py<8;py++)for(int px=0;px<8;px++){
                    int nibble=pattern.getPixel(px,py)&15;if(nibble==0)continue;
                    int x=ox+piece.xOffset()+(piece.hFlip()?piece.widthTiles()*8-1-col*8-px:col*8+px);
                    int y=oy+piece.yOffset()+(piece.vFlip()?piece.heightTiles()*8-1-row*8-py:row*8+py);
                    if(x<0||x>=width||y<160||y>=224)continue;
                    // Lower SST and earlier mapping pieces win opaque sprite overlap.
                    if(expected[y*width+x]<0)expected[y*width+x]=palette[(3+piece.paletteIndex())&3][nibble];
                }
            }
        }
        assertEquals(2,dolphinCount,"loc_5E612 admits the actual two dolphin owners");
        return expected;
    }
    private static int rgb(int line,int cell){var c=GameServices.level().getCurrentLevel().getPalette(line).getColor(cell);
        return(c.r&255)<<16|(c.g&255)<<8|(c.b&255);}
}
