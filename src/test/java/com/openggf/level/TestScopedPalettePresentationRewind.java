package com.openggf.level;

import com.openggf.data.Rom;
import com.openggf.graphics.*;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestScopedPalettePresentationRewind {
    @Test void capturedNativeRowsSurviveTablesRestoreAndBankChangesAfterDeferredSubmission() throws Exception {
        RenderContext.reset();var context=com.openggf.tools.PresentationOffscreenTestContext.open(320,224);
        var graphics=new GraphicsManager();var rom=new Rom();
        try {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            graphics.init("shaders/shader_the_hedgehog.glsl");graphics.setBatchingEnabled(true);glDisable(GL_DITHER);
            graphics.setViewport(0,0,320,224);graphics.setProjectionWidth(320);
            graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0,320,0,224,-1,1).get(new float[16]));
            var sentinel=new Palette();for(int i=1;i<16;i++)sentinel.colors[i]=new Palette.Color((byte)0,(byte)255,(byte)0);
            for(int line=0;line<4;line++)graphics.cachePaletteTexture(sentinel,line);
            Palette[] nativeGhost=new Palette[4];
            for(int line=0;line<4;line++) {
                nativeGhost[line]=new Palette();nativeGhost[line].fromSegaFormat(rom.readBytes(line==0?0xA8A3C:0xA955C+(line-1)*32,32));
            }
            Object owner=new Object();var handle=new AtomicReference<>(ScopedPaletteGraphicsAccess.acquire(graphics,owner,nativeGhost));
            int initial=ScopedPaletteGraphicsAccess.base(handle.get(),graphics,owner);
            SpritePresentation.PaletteReplay replay=(host,line)->ScopedPaletteGraphicsAccess.base(handle.get(),host,owner)+(line&3);
            var subject=new SpritePresentation.Subject("owned-ghost",SpritePresentation.Part.OBJECT_BODY,false,100,null,
                    new SpritePresentation.Body(SpritePresentation.Role.BADNIK,24,34),replay);
            var pattern=new Pattern();for(int y=0;y<8;y++)for(int x=0;x<8;x++)pattern.setPixel(x,y,(byte)2);
            int id=PatternAtlasRange.OBJECTS.base();graphics.cachePatternTexture(pattern,id);
            var nativeDesc=new PatternDesc();nativeDesc.setPaletteIndex(1);
            var prepared=SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentation.withSubject(graphics,subject,
                    ()->graphics.renderPatternWithId(id,nativeDesc,20,30)));
            assertEquals(1,prepared.tiles().getFirst().palette());assertNotNull(prepared.tiles().getFirst().subject().paletteReplay());
            var first=prepared.tiles().getFirst();
            var clipped=new SpritePresentation.Tile(first.layer(),id,1,false,false,true,40,30,8,8,
                    false,0,false,1,2,6,subject);
            var scaled=new SpritePresentation.Tile(first.layer(),id,1,false,false,true,60,30,16,16,
                    false,0,false,1,0,8,subject);
            var frame=new SpritePresentation.Frame(List.of(first,clipped,scaled),List.of(),Map.of(id,SpritePresentationRenderer.version(pattern)));
            var tables=new LevelSpritePresentation.Tables();tables.prepare(frame);tables.publish();var saved=tables.capture();
            var stale=handle.get();ScopedPaletteGraphicsAccess.release(stale,owner);
            Object blockerOwner=new Object();var blocker=ScopedPaletteGraphicsAccess.acquire(graphics,blockerOwner,new Palette[]{sentinel,sentinel,sentinel,sentinel});
            assertEquals(initial,ScopedPaletteGraphicsAccess.base(blocker,graphics,blockerOwner));
            handle.set(ScopedPaletteGraphicsAccess.acquire(graphics,owner,nativeGhost));
            assertNotEquals(initial,ScopedPaletteGraphicsAccess.base(handle.get(),graphics,owner));
            tables.reset();tables.restore(saved);assertEquals(1,tables.published().tiles().getFirst().palette());
            glViewport(0,0,320,224);glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
            var deferredBlocker=new AtomicReference<ScopedPaletteGraphicsAccess.Bank>();Object secondBlocker=new Object();
            graphics.executeCapturedCommands(()->{
                SpritePresentationRenderer.draw(graphics,tables.published(),0,0,layer->true);
                // Commands are queued, then allocation changes again before their actual GL execution.
                var submitted=handle.get();int submittedBase=ScopedPaletteGraphicsAccess.base(submitted,graphics,owner);
                ScopedPaletteGraphicsAccess.release(submitted,owner);
                deferredBlocker.set(ScopedPaletteGraphicsAccess.acquire(graphics,secondBlocker,new Palette[]{sentinel,sentinel,sentinel,sentinel}));
                assertEquals(submittedBase,ScopedPaletteGraphicsAccess.base(deferredBlocker.get(),graphics,secondBlocker));
                handle.set(ScopedPaletteGraphicsAccess.acquire(graphics,owner,nativeGhost));
                assertNotEquals(submittedBase,ScopedPaletteGraphicsAccess.base(handle.get(),graphics,owner));
            },0,0,320,224);
            glFinish();ByteBuffer pixels=MemoryUtil.memAlloc(320*224*4);
            try {
                glReadPixels(0,0,320,224,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                for(int[] point:new int[][]{{22,33},{42,33},{65,35}}) {
                    int at=((223-point[1])*320+point[0])*4;
                    assertEquals(219,pixels.get(at)&255);assertEquals(219,pixels.get(at+1)&255);assertEquals(255,pixels.get(at+2)&255);
                }
                assertEquals(0,pixels.get(((223-30)*320+42)*4)&255,"clipped rows must remain hidden");
            } finally { MemoryUtil.memFree(pixels); }
            assertFalse(ScopedPaletteGraphicsAccess.valid(stale,graphics,owner));assertEquals(GL_NO_ERROR,glGetError());
            ScopedPaletteGraphicsAccess.release(handle.get(),owner);ScopedPaletteGraphicsAccess.release(blocker,blockerOwner);
            ScopedPaletteGraphicsAccess.release(deferredBlocker.get(),secondBlocker);
        } finally { try { graphics.cleanup();rom.close(); } finally { context.close();RenderContext.reset(); } }
    }
    @Test void priorityWaterKeepsScopedRomRowsAndPublishedHostRowsAcrossDeferredRelocation() throws Exception {
        priorityWater(false);
    }
    @Test void lateScopedReallocationKeepsPriorityWaterBindingsForLaterDrySprites() throws Exception {
        priorityWater(true);
    }
    private void priorityWater(boolean lateReallocation) throws Exception {
        RenderContext.reset();var context=com.openggf.tools.PresentationOffscreenTestContext.open(320,224);
        var graphics=new GraphicsManager();var rom=new Rom();
        try {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            graphics.init("shaders/shader_the_hedgehog.glsl");graphics.setBatchingEnabled(true);glDisable(GL_DITHER);
            graphics.setViewport(0,0,320,224);graphics.setProjectionWidth(320);
            graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0,320,0,224,-1,1).get(new float[16]));
            graphics.setUseSpritePriorityShader(true);graphics.setWaterEnabled(true);
            graphics.setWaterlineScreenY(40);graphics.setWindowHeight(224);graphics.setScreenHeight(224);
            Palette[] water=new Palette[4];Palette normal=new Palette();
            for(int colour=1;colour<16;colour++)normal.colors[colour]=new Palette.Color((byte)200,(byte)200,(byte)200);
            for(int line=0;line<4;line++) {
                graphics.cachePaletteTexture(normal,line);water[line]=new Palette();
                for(int colour=1;colour<16;colour++)water[line].colors[colour]=new Palette.Color((byte)100,(byte)100,(byte)200);
            }
            water[1].colors[2]=new Palette.Color((byte)20,(byte)40,(byte)120);
            graphics.cacheUnderwaterPaletteTexture(water,normal); // Only native rows exist at this publication.
            Object world=new Object();ScopedPaletteGraphicsAccess.bindWorld(graphics,world);
            Palette[] donor=new Palette[4];
            for(int line=0;line<4;line++) {
                donor[line]=new Palette();donor[line].fromSegaFormat(rom.readBytes(line==0?0xA8A3C:0xA955C+(line-1)*32,32));
            }
            Object owner=new Object();var bank=new AtomicReference<>(ScopedPaletteGraphicsAccess.acquire(graphics,owner,donor));
            var replayCalls=new java.util.concurrent.atomic.AtomicInteger();
            Object lateBlockerOwner=new Object();var lateBlocker=new AtomicReference<ScopedPaletteGraphicsAccess.Bank>();
            var subject=new SpritePresentation.Subject("ghost-water",SpritePresentation.Part.OBJECT_BODY,false,100,null,null,
                    (host,line)->{
                        if(lateReallocation && replayCalls.incrementAndGet()==2) {
                            // Another owned native subject can reacquire after an earlier pattern has drawn.
                            ScopedPaletteGraphicsAccess.release(bank.get(),owner);
                            lateBlocker.set(ScopedPaletteGraphicsAccess.acquire(host,lateBlockerOwner,new Palette[]{normal,normal,normal,normal}));
                            bank.set(ScopedPaletteGraphicsAccess.acquire(host,owner,donor));
                        }
                        return ScopedPaletteGraphicsAccess.base(bank.get(),host,owner)+(line&3);
                    });
            var pattern=new Pattern();for(int y=0;y<8;y++)for(int x=0;x<8;x++)pattern.setPixel(x,y,(byte)2);
            int id=PatternAtlasRange.OBJECTS.base();graphics.cachePatternTexture(pattern,id);
            var hostTile=new SpritePresentation.Tile(SpritePresentation.Layer.PLAYER,id,1,false,false,true,100,60,8,8,true,0,false,1);
            var top=new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT,id,1,false,false,true,20,20,8,8,true,0,false,1,0,8,subject);
            var below=new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT,id,1,false,false,true,20,60,8,8,true,0,false,1,0,8,subject);
            var laterDry=new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT,id,1,false,false,true,40,20,8,8,true,0,false,1,0,8,subject);
            var frame=new SpritePresentation.Frame(List.of(hostTile,top,below,laterDry),List.of(),Map.of(id,SpritePresentationRenderer.version(pattern)));
            // A pending native row must stay pending while private residency grows for a deferred draw.
            PaletteUploadPresentation.publishAndLatch(graphics);
            water[1].colors[2]=new Palette.Color((byte)255,(byte)0,(byte)0);
            graphics.cacheUnderwaterPaletteTexture(water,normal);
            Object blockerOwner=new Object();var blocker=new AtomicReference<ScopedPaletteGraphicsAccess.Bank>();
            glViewport(0,0,320,224);glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
            graphics.executeCapturedCommands(()->{
                SpritePresentationRenderer.draw(graphics,frame,0,0,layer->true);
                ScopedPaletteGraphicsAccess.release(bank.get(),owner);
                blocker.set(ScopedPaletteGraphicsAccess.acquire(graphics,blockerOwner,new Palette[]{normal,normal,normal,normal}));
                bank.set(ScopedPaletteGraphicsAccess.acquire(graphics,owner,donor));
            },0,0,320,224);
            assertPixel(graphics,22,23,219,219,255);
            assertPixel(graphics,22,63,109,109,255);
            assertPixel(graphics,42,23,219,219,255);
            assertPixel(graphics,102,63,20,40,120);
            PaletteUploadPresentation.publishAndLatch(graphics);
            glClear(GL_COLOR_BUFFER_BIT);graphics.executeCapturedCommands(()->SpritePresentationRenderer.draw(graphics,frame,0,0,layer->true),0,0,320,224);
            assertPixel(graphics,102,63,255,0,0);
            assertPixel(graphics,22,63,109,109,255);
            assertEquals(GL_NO_ERROR,glGetError());
            ScopedPaletteGraphicsAccess.leaveAssembly(graphics,world);
            ScopedPaletteGraphicsAccess.release(bank.get(),owner);ScopedPaletteGraphicsAccess.release(blocker.get(),blockerOwner);
            ScopedPaletteGraphicsAccess.release(lateBlocker.get(),lateBlockerOwner);
        } finally { try { graphics.cleanup();rom.close(); } finally { context.close();RenderContext.reset(); } }
    }
    private static void assertPixel(GraphicsManager graphics,int x,int y,int red,int green,int blue) {
        glFinish();ByteBuffer pixel=MemoryUtil.memAlloc(4);
        try {
            glReadPixels(x,223-y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
            assertEquals(red,pixel.get(0)&255,"red at "+x+","+y);
            assertEquals(green,pixel.get(1)&255,"green at "+x+","+y);
            assertEquals(blue,pixel.get(2)&255,"blue at "+x+","+y);
        } finally { MemoryUtil.memFree(pixel); }
    }
}
