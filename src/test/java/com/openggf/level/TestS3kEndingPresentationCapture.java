package com.openggf.level;

import com.openggf.tools.GameplayCaptureSession;

import com.openggf.camera.NativeViewportFraming;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.ending.EndingIslandLiftObjectInstance;
import com.openggf.game.sonic3k.ending.EndingMasterPaletteObjectInstance;
import com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance;
import com.openggf.game.sonic3k.ending.S3kEndingResources;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native scene admission and the actual GPU/ROM footprint of the three repaired presentation owners. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingPresentationCapture {
    @ParameterizedTest @CsvSource({"320,1111111,0","800,1111111,0","320,3333333,2","800,3333333,2"})
    void nativeRescueAllocatesBothIslandGroupsAndKeepsTheirArtAndPhaseReplay(int width,String emeralds,int progress) throws Exception {
        var settings=new GameplayCaptureSession.Settings(width,"sonic","","off",null,null,null,emeralds,false,false);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            assertEquals(width,GameServices.camera().getWidth());
            boolean early=false,pixels=false,later=false;
            for(int input=0;input<5000;input++){
                session.step(null);session.renderFrame();
                var manager=GameServices.level().getObjectManager();var state=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();
                assertEquals(progress,state.ending().progress());
                var graph=manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class);
                if(!early&&graph.size()==2){early=true;assertTrue(graph.stream().allMatch(o->o.getSpawn().subtype()==0x42));
                    assertEquals(14,manager.activeObjectsOfType(EndingRescueDirectorObjectInstance.class).getFirst().routine());}
                if(!pixels){
                    var visible=graph.stream().filter(o->o.getSpawn().subtype()==0x42&&o.drawAdmitted()&&o.getX()>=0x90&&o.getX()<0x1A0&&o.getY()>=0x90&&o.getY()<0x150).findFirst();
                    if(visible.isPresent()&&transparentFootprint(visible.orElseThrow(),width)){
                        var sample=sample(visible.orElseThrow());
                        // VInt publishes the preceding loop's prepared SAT/CRAM;
                        // compare that producer generation, not the new CPU object.
                        session.step(null);input++;var picture=session.render();assertPixels(picture,sample,width,true);pixels=true;
                    }
                }
                if(!later&&graph.size()==20){later=true;
                    int[] subtypes={0x41,0x42,0x43,0x44,0x45,0x46,0x47},counts={6,2,2,4,2,2,2};
                    for(int i=0;i<subtypes.length;i++){int subtype=subtypes[i];assertEquals(counts[i],graph.stream().filter(o->o.getSpawn().subtype()==subtype).count());}
                    var registry=com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();var saved=registry.capture();
                    for(int i=0;i<30;i++){session.step(null);session.renderFrame();}var expected=registry.capture();registry.restore(saved);same(saved,registry.capture());
                    for(int i=0;i<30;i++){session.step(null);session.renderFrame();}same(expected,registry.capture());
                }
                if(early&&pixels&&later)return;
            }
            fail("native IslandLift phases not completed: early="+early+" pixels="+pixels+" late="+later);
        }
    }
    /* Subtype $41 is low priority (loc_5EDFE: bclr #7,art_tile). In this Sonic/0
     * declared scene, native polls 1279-1535 (engine input 1400 is poll 1399) hold
     * Plane B high-priority opaque sea on screen lines 160-223 and low-priority sky
     * above, so the VDP shows the wrapped particle only above the sea. */
    @ParameterizedTest @ValueSource(ints={320,800})
    void wrappedNativeParticleYRemainsVisibleAfterTheCameraCopyThreshold(int width) throws Exception {
        wrappedParticleScene(width,0x1F8,0x40,0xB8,0xFEB0,true);
    }
    @ParameterizedTest @ValueSource(ints={320,800})
    void wrappedLowPriorityParticleIsCoveredByTheHighPrioritySea(int width) throws Exception {
        wrappedParticleScene(width,0x2B0,0xD4,0x170,0xFF44,false);
    }
    private void wrappedParticleScene(int width,int baseX,int baseY,int screenX,int screenY,boolean visible) throws Exception {
        var settings=new GameplayCaptureSession.Settings(width,"sonic","","off",null,null,null,"1111111",false,false);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            for(int i=0;i<1400;i++){session.step(null);if(i%4==0)session.renderFrame();}
            var camera=GameServices.camera();assertEquals(width,camera.getWidth());camera.setXCopy((short)0x1C0);camera.setYCopy((short)0x200);
            var manager=GameServices.level().getObjectManager();var particle=manager.createDynamicObject(()->new EndingIslandLiftObjectInstance(0x41,0,baseX,baseY));
            particle.update(2,session.player());camera.setYCopy((short)0x210);particle.update(4,session.player());
            assertFalse(particle.worldCoordinates());assertEquals(screenX,particle.getX());assertEquals(screenY,particle.getY());
            assertFalse(particle.isHighPriority());
            assertEquals(1,particle.mappingFrame());assertTrue(particle.drawAdmitted());
            // Declared threshold vector after the production loc_5DB76 module upload;
            // this is independent of the full rescue scene pixel composition.
            // Manual dispatch has not prepared or published a table. Admit the
            // particle through its ordinary even-VInt pass, then the next VInt.
            for(int attempt=0;attempt<4;attempt++){
                session.step(null);
                if(!particle.drawAdmitted())continue;
                var sample=sample(particle);session.step(null);var picture=session.render();assertPixels(picture,sample,width,visible);return;
            }
            fail("native even-VInt particle table was not prepared");
        }
    }
    @ParameterizedTest @ValueSource(ints={320,800})
    void nativeSuperAizHoldOwnsWaterfallTorchClockAndIndependentMasterPaletteWithReplay(int width) throws Exception {
        var settings=new GameplayCaptureSession.Settings(width,"tails","","off",null,null,null,"3333333",false,false);
        try(var session=new GameplayCaptureSession(settings)){
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            assertEquals(width,GameServices.camera().getWidth());
            for(int input=0;input<6000;input++){
                session.step(null);session.renderFrame();var state=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();
                if(state.screen().foregroundRoutine!=32)continue;
                assertEquals(2,state.ending().progress());assertEquals(-1,state.screen().masterPaletteMode);
                assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(EndingMasterPaletteObjectInstance.class).size());
                assertEquals(0xEE8,word(2,11));assertEquals(0xE64,word(2,14));assertEquals(0xEA4,word(3,12));assertEquals(0xEE2,word(3,14));
                assertFalse(GameServices.camera().isLevelStarted(),"explicit loc_5AD56 cycle does not use ordinary AIZ LEVEL_STARTED");
                var registry=com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();var saved=registry.capture();
                for(int i=0;i<16;i++){session.step(null);session.renderFrame();}var expected=registry.capture();registry.restore(saved);same(saved,registry.capture());
                for(int i=0;i<16;i++){session.step(null);session.renderFrame();}same(expected,registry.capture());
                return;
            }
            fail("native Super AIZ hold not reached");
        }
    }
    private int word(int line,int color){return com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(GameServices.level().getCurrentLevel().getPalette(line).getColor(color));}
    /** A wide-edge crossfade changes RGB by design. Select an actual unmasked
     * prepared generation, then let the following VInt publish its SAT/CRAM. */
    private boolean transparentFootprint(EndingIslandLiftObjectInstance owner,int width) throws Exception {
        var mask=GameServices.level().spritePresentationRenderer().boundsMask.sample();
        var frame=S3kSpriteDataLoader.loadMappingFrames(com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom()),S3kEndingResources.MAP_ISLAND_LIFT).get(owner.mappingFrame());
        var piece=frame.pieces().getFirst();int satX=(owner.getX()+piece.xOffset())&0x1FF;if(satX==0)satX=1;
        int left=NativeViewportFraming.inset(width)+satX-0x80,right=left+piece.widthTiles()*8;
        if(left<NativeViewportFraming.inset(width)||right>NativeViewportFraming.inset(width)+320)return false;
        for(int x=left;x<right;x++)if(mask!=null&&mask.opacityAt(x)!=0)return false;
        return true;
    }
    private record Sample(int x,int y,int frame,int[][] palette) { }
    private Sample sample(EndingIslandLiftObjectInstance owner){
        int[][] colors=new int[4][16];var level=GameServices.level().getCurrentLevel();
        for(int line=0;line<4;line++)for(int n=0;n<16;n++){
            var c=level.getPalette(line).getColor(n);colors[line][n]=(c.r&255)<<16|(c.g&255)<<8|(c.b&255);
        }
        return new Sample(owner.getX(),owner.getY(),owner.mappingFrame(),colors);
    }
    private void assertPixels(com.openggf.graphics.RgbaImage picture,Sample owner,int width,boolean visible) throws Exception {
        var level=GameServices.level().getCurrentLevel();var patterns=com.openggf.util.PatternDecompressor.kosinskiModuled(GameServices.rom().getRom(),S3kEndingResources.ISLAND_LIFT);
        var frame=S3kSpriteDataLoader.loadMappingFrames(com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom()),S3kEndingResources.MAP_ISLAND_LIFT).get(owner.frame());
        assertEquals(1,frame.pieces().size());var piece=frame.pieces().getFirst();
        int rawX=(owner.x()+piece.xOffset())&0x1FF;if(rawX==0)rawX=1;
        int rawY=(owner.y()+piece.yOffset())&0x1FF;
        int samples=0,matches=0;
        for(int column=0;column<piece.widthTiles();column++)for(int row=0;row<piece.heightTiles();row++){
            int tile=piece.tileIndex()+column*piece.heightTiles()+row;var pattern=patterns[tile];var resident=level.getPattern(0x2FF+tile);
            for(int y=0;y<8;y++)for(int x=0;x<8;x++){
                int nibble=pattern.getPixel(x,y)&15;assertEquals(nibble,resident.getPixel(x,y)&15,"native IslandLift DMA tile "+tile);
                if(nibble==0)continue;
                int rgb=owner.palette()[(3+piece.paletteIndex())&3][nibble];
                int px=NativeViewportFraming.inset(width)+rawX-0x80+(piece.hFlip()?piece.widthTiles()*8-1-column*8-x:column*8+x);
                int py=rawY-0x80+(piece.vFlip()?piece.heightTiles()*8-1-row*8-y:row*8+y);
                if(px<NativeViewportFraming.inset(width)||px>=NativeViewportFraming.inset(width)+320||py<0||py>=224)continue;
                samples++;if((picture.argb(px,py)&0xFFFFFF)==rgb)matches++;
            }
        }
        assertTrue(samples>8,"the actual ROM IslandLift frame has a visible footprint");
        if(visible)assertTrue(matches>samples/2,"native IslandLift missing/displaced: "+matches+"/"+samples);
        else assertEquals(0,matches,"low-priority IslandLift must stay behind opaque Plane B high-priority pixels");
    }
    private void same(com.openggf.game.rewind.CompositeSnapshot a,com.openggf.game.rewind.CompositeSnapshot b){assertEquals(a.entries().keySet(),b.entries().keySet());
        for(String key:a.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)).isEmpty(),key);}
}
