package com.openggf.graphics;

import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

@Isolated
@EnabledIfSystemProperty(named="openggf.headless.gl", matches="surfaceless-egl")
class TestPaletteDrawProjectionPixels {
    private Palette row(int r,int g,int b) {
        var row=new Palette();
        for(int i=0;i<16;i++) row.setColor(i,new Palette.Color((byte)r,(byte)g,(byte)b));
        return row;
    }
    private void pixel(int x,int y,int red,int green,int blue) {
        var bytes=ByteBuffer.allocateDirect(4);
        glReadPixels(x,223-y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
        assertArrayEquals(new int[]{red,green,blue,255},new int[]{bytes.get(0)&255,bytes.get(1)&255,bytes.get(2)&255,bytes.get(3)&255});
    }
    @Test void displayedColorsHoldTheirGenerationUntilPublicationAndAreDefensive() throws Exception {
        RenderContext.reset();
        try (var context = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            var graphics = new GraphicsManager();
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                var live = row(255, 0, 0);
                graphics.cachePaletteTexture(live, 1);
                PaletteUploadPresentation.publishAndLatch(graphics);
                live.setColor(1, new Palette.Color((byte) 0, (byte) 0, (byte) 255));
                PaletteUploadPresentation.cacheLevelPalette(graphics, live, 1);
                var displayed = PaletteUploadPresentation.copyDisplayedPaletteArgb(graphics, 1);
                assertEquals(0xFFFF0000, displayed[1], "pending CPU colors must not replace displayed colors");
                displayed[1] = 0;
                assertEquals(0xFFFF0000, PaletteUploadPresentation.copyDisplayedPaletteArgb(graphics, 1)[1]);
                PaletteUploadPresentation.publishAndLatch(graphics);
                assertEquals(0xFF0000FF, PaletteUploadPresentation.copyDisplayedPaletteArgb(graphics, 1)[1]);
                assertNull(PaletteUploadPresentation.copyDisplayedPaletteArgb(graphics, 3));
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }
    @Test void normalWaterAndPrivateRowsUseTheProjectionAndRestoreAfterFailure() throws Exception {
        RenderContext.reset();
        try(var context=PresentationOffscreenTestContext.openConfigured(320,224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            var graphics=new GraphicsManager();
            try {
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setBatchingEnabled(false);
                graphics.setViewport(0,0,320,224); graphics.setProjectionWidth(320);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0,320,0,224,-1,1).get(new float[16]));
                graphics.setUseSpritePriorityShader(true); graphics.setWaterEnabled(true);
                graphics.setWaterlineScreenY(40); graphics.setWindowHeight(224); graphics.setScreenHeight(224);
                glDisable(GL_DITHER);
                var nativeRow=row(255,0,0); var waterRow=row(0,0,255);
                var water=new Palette[]{waterRow,waterRow,waterRow,waterRow};
                for(int line=0;line<4;line++) graphics.cachePaletteTexture(nativeRow,line);
                var normalTexture=graphics.getCombinedPaletteTextureId();
                var waterTexture=graphics.cacheUnderwaterPaletteTexture(water,nativeRow);
                var fade=PaletteUploadPresentation.captureDisplayedFade(graphics);
                var pattern=new Pattern();
                for(int y=0;y<8;y++) for(int x=0;x<8;x++) pattern.setPixel(x,y,(byte)1);
                int patternId=PatternAtlasRange.OBJECTS.base();
                graphics.cachePatternTexture(pattern,patternId);
                var white=row(255,255,255);
                var projected=new Palette[]{white,white,white,white};
                projected[2]=row(255,255,255);
                projected[2].setColor(0,new Palette.Color((byte)18,(byte)36,(byte)54));
                glViewport(0,0,320,224); glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
                Object owner=new Object();
                assertThrows(IllegalStateException.class,()->PaletteDrawPresentation.withRows(graphics,projected,()->{
                    int privateBase=graphics.paletteTextures().scopedPaletteBank(owner,
                            new Palette[]{nativeRow,nativeRow,nativeRow,nativeRow});
                    assertNotEquals(normalTexture,graphics.getCombinedPaletteTextureId());
                    assertEquals(graphics.getCombinedPaletteTextureId(),graphics.cacheUnderwaterPaletteTexture(water,nativeRow),
                            "the water shader's ordinary cache must not overwrite the projection");
                    graphics.executeCapturedCommands(()->{
                        graphics.renderPatternRowsWithId(patternId,new PatternDesc(),privateBase,false,false,true,20,20,0,8);
                        graphics.renderPatternRowsWithId(patternId,new PatternDesc(),privateBase,false,false,true,20,60,0,8);
                    },0,0,320,224);
                    glFinish();pixel(22,23,255,255,255);pixel(22,63,255,255,255);
                    throw new IllegalStateException("negative control: draw failed");
                }));
                assertEquals(normalTexture,graphics.getCombinedPaletteTextureId());
                assertEquals(waterTexture,graphics.getUnderwaterPaletteTextureId());
                assertEquals(fade,PaletteUploadPresentation.captureDisplayedFade(graphics));
                assertEquals(255,nativeRow.getColor(1).r&255);assertEquals(0,nativeRow.getColor(1).b&255);
                graphics.paletteTextures().releaseScopedPaletteBank(owner);
                graphics.cacheUnderwaterPaletteTexture(water,nativeRow);
                graphics.executeCapturedCommands(()->{
                    graphics.renderPatternRowsWithId(patternId,new PatternDesc(),0,false,false,true,20,20,0,8);
                    graphics.renderPatternRowsWithId(patternId,new PatternDesc(),0,false,false,true,20,60,0,8);
                },0,0,320,224);
                glFinish();pixel(22,23,255,0,0);pixel(22,63,0,0,255);
                assertEquals(GL_NO_ERROR,glGetError());
            } finally { graphics.cleanup();RenderContext.reset(); }
        }
    }
}
