package com.openggf.game.sonic3k.mutators;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.ScopedPaletteGraphicsAccess;
import com.openggf.level.Palette;
import com.openggf.level.render.PlaneRasterizer;
import com.openggf.level.render.SpriteMappingRasterizer;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestNativeEncounterPalettes {
    private static Rom rom() {
        var file=RomTestUtils.ensureSonic3kRomAvailable();assertNotNull(file);
        var rom=new Rom();assertTrue(rom.open(file.getAbsolutePath()));return rom;
    }
    @Test void exactDonorColoursDoNotFollowHostLightingAndDerivedBanksRebuildAfterReset() throws Exception {
        RenderContext.reset();var graphics=new GraphicsManager();graphics.initHeadless();
        var donor=new NativeEncounterPalettes();
        try(var rom=rom()) {
            var cnz=NativeEncounterPalettes.load(rom,Sonic3kObjectArtKeys.CNZ_BUMPER);
            var ghost=NativeEncounterPalettes.load(rom,Sonic3kObjectArtKeys.SOZ_GHOSTS);
            // Direct ROM witnesses: Pal_CNZ line 2 colour 3=$0CE; Pal_S0Z2 line 1 colour 2=$ECC.
            assertEquals(0xFFFFDB00,PlaneRasterizer.argb(cnz[2].getColor(3)));
            assertEquals(0xFFDBDBFF,PlaneRasterizer.argb(ghost[1].getColor(2)));
            donor.prepare(rom,graphics,Set.of(Sonic3kObjectArtKeys.CNZ_BUMPER,Sonic3kObjectArtKeys.SOZ_GHOSTS));
            int bumper=donor.base(Sonic3kObjectArtKeys.CNZ_BUMPER,graphics),ghosts=donor.base(Sonic3kObjectArtKeys.SOZ_GHOSTS,graphics);
            assertTrue(bumper>=4);assertTrue(ghosts>=4);assertNotEquals(bumper,ghosts);
            donor.prepare(rom,graphics,Set.of(Sonic3kObjectArtKeys.SOZ_GHOSTS));
            assertEquals(-1,donor.base(Sonic3kObjectArtKeys.CNZ_BUMPER,graphics));assertEquals(ghosts,donor.base(Sonic3kObjectArtKeys.SOZ_GHOSTS,graphics));
            graphics.clearPaletteTextures();assertEquals(-1,donor.base(Sonic3kObjectArtKeys.SOZ_GHOSTS,graphics));
            donor.prepare(rom,graphics,Set.of(Sonic3kObjectArtKeys.SOZ_GHOSTS));assertTrue(donor.base(Sonic3kObjectArtKeys.SOZ_GHOSTS,graphics)>=4);
            donor.clear();assertEquals(-1,donor.base(Sonic3kObjectArtKeys.SOZ_GHOSTS,graphics));
        } finally { donor.clear();graphics.cleanup();RenderContext.reset(); }
    }
    @Test void actualBumperAndGhostRomPixelsKeepScopedColoursThroughSatAt320400And800() throws Exception {
        RenderContext.reset();GraphicsManager graphics=null;
        var offscreen=com.openggf.tools.PresentationOffscreenTestContext.open(800,224);
        try(var rom=rom()) {
            graphics=new GraphicsManager();graphics.init("shaders/shader_the_hedgehog.glsl");graphics.setBatchingEnabled(false);glDisable(GL_DITHER);
            var host=new Palette();for(int color=1;color<16;color++)host.setColor(color,new Palette.Color((byte)0,(byte)255,(byte)0));
            for(int line=0;line<4;line++)graphics.cachePaletteTexture(host,line);
            var provider=new Sonic3kObjectArtProvider();
            for(String key:List.of(Sonic3kObjectArtKeys.CNZ_BUMPER,Sonic3kObjectArtKeys.SOZ_GHOSTS)) {
                assertTrue(provider.ensureEncounterArtLoaded(rom,graphics,key));var sheet=provider.getSheet(key);assertNotNull(sheet);
                // Native provider renderers bind EngineServices.graphics; this isolated fixture owns a separate host.
                var renderer=new com.openggf.level.render.PatternSpriteRenderer(sheet,graphics);
                renderer.ensurePatternsCached(graphics,com.openggf.graphics.PatternAtlasRange.OBJECTS.base());
                var lines=NativeEncounterPalettes.load(rom,key);Object owner=new Object();var bank=ScopedPaletteGraphicsAccess.acquire(graphics,owner,lines);
                int base=ScopedPaletteGraphicsAccess.base(bank,graphics,owner);int[] argb=new int[64];
                for(int line=0;line<4;line++)for(int color=0;color<16;color++)argb[line*16+color]=PlaneRasterizer.argb(lines[line].getColor(color));
                var expected=SpriteMappingRasterizer.rasterize(sheet.getFrame(0),sheet.getPatterns(),argb,sheet.getPaletteIndex(),0);
                for(int width:new int[]{320,400,800}) {
                    graphics.setViewport(0,0,width,224);graphics.setProjectionWidth(width);
                    graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0,width,0,224,-1,1).get(new float[16]));
                    glViewport(0,0,width,224);glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
                    var actualGraphics=graphics;
                    actualGraphics.executeCapturedCommands(()->{
                        actualGraphics.beginSpriteSatCollection();
                        var subject=new com.openggf.graphics.SpritePresentation.Subject("native-colours",com.openggf.graphics.SpritePresentation.Part.OBJECT_BODY,
                                false,100,null,null,(hostGraphics,nativePalette)->ScopedPaletteGraphicsAccess.base(bank,hostGraphics,owner)+(nativePalette&3));
                        com.openggf.graphics.SpritePresentation.withSubject(actualGraphics,subject,
                                ()->renderer.drawFrameIndexForcedPriority(0,80,80,false,false,-1,true));
                        renderer.drawFrameIndexForcedPriority(0,160,80,false,false,-1,true);
                        actualGraphics.endSpriteSatCollectionAndReplay();
                    },0,0,width,224);
                    glFinish();ByteBuffer pixels=MemoryUtil.memAlloc(width*224*4);
                    try {
                        glReadPixels(0,0,width,224,GL_RGBA,GL_UNSIGNED_BYTE,pixels);int checked=0;
                        for(int y=0;y<expected.height();y++)for(int x=0;x<expected.width();x++) {
                            int wanted=expected.argb()[y*expected.width()+x];if((wanted>>>24)==0)continue;
                            int sx=80-expected.originX()+x,sy=80-expected.originY()+y,at=((223-sy)*width+sx)*4;
                            for(int channel=0;channel<3;channel++)assertEquals((wanted>>>(16-channel*8))&255,pixels.get(at+channel)&255,key+" channel "+channel+" at "+sx+","+sy+" width="+width);
                            int nativeAt=((223-sy)*width+sx+80)*4;
                            assertEquals(0,pixels.get(nativeAt)&255);assertEquals(255,pixels.get(nativeAt+1)&255);assertEquals(0,pixels.get(nativeAt+2)&255);
                            checked++;
                        }
                        assertTrue(checked>32,"the ROM frame must actually contain coloured sprite pixels");
                    } finally { MemoryUtil.memFree(pixels); }
                    assertEquals(GL_NO_ERROR,glGetError());
                }
                ScopedPaletteGraphicsAccess.release(bank,owner);assertFalse(ScopedPaletteGraphicsAccess.valid(bank,graphics,owner));
            }
        } finally { try { if(graphics!=null)graphics.cleanup(); } finally { offscreen.close();RenderContext.reset(); } }
    }
}
