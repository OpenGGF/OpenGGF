package com.openggf.game.presentation;

import com.openggf.game.CharacterPresentation;
import com.openggf.graphics.*;
import com.openggf.io.PngCodec;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Real production slot shader witnesses, no window/audio or gameplay state edits. */
class TestReelPortraitRendering {
    @Test @RequiresRom(SonicGame.SONIC_2) void s2() throws Exception {
        capture("s2", "sonic2.rom.path",0x4EEFE,6,0x29E2);
    }
    @Test @RequiresRom(SonicGame.SONIC_3K) void s3k() throws Exception {
        capture("s3k", "s3k.rom.path",0x158CAE,8,0xA9C7C);
    }
    private static ReelCharacterPresentation.Portrait portrait(String character,String file) throws Exception {
        var png=PngCodec.decodeIndexed(Files.readAllBytes(Path.of("examples/mica-and-talon/art/"+character+"/reels/"+file+".png")),256);
        return new ReelCharacterPresentation.Portrait(new CharacterPresentation.Image(png.width(),png.height(),png.indices()),png.rgb());
    }
    private void capture(String host,String property,int offset,int count,int paletteOffset) throws Exception {
        byte[] rom=Files.readAllBytes(Path.of(System.getProperty(property)));
        try(var context=PresentationOffscreenTestContext.openConfigured(320,224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            var shader=new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER,"shaders/shader_"+(host.equals("s2")?"cnz":"s3k")+"_slots.glsl");
            var pass=new SlotWindowGpuPass();pass.setShader(shader);pass.init();
            int pal=glGenTextures(),tex=glGenTextures();
            var indices=MemoryUtil.memAlloc(count*1024);var pixels=MemoryUtil.memAlloc(320*224*4);var colors=MemoryUtil.memAlloc(64);
            try {
                for(int f=0;f<count;f++) for(int y=0;y<32;y++) for(int x=0;x<32;x++) {
                    int v=rom[offset+f*512+(x/8*4+y/8)*32+y%8*4+x%8/2]&255;
                    indices.put(f*1024+y*32+x,(byte)((v>>(x%2==0?4:0))&15));
                }
                for(int i=0;i<16;i++) {
                    int word=((rom[paletteOffset+i*2]&255)<<8)|(rom[paletteOffset+i*2+1]&255);
                    for(int shift:new int[]{0,4,8})colors.put((byte)Math.round(((word>>shift)&14)*255f/14));
                    colors.put((byte)255);
                }
                colors.flip();glBindTexture(GL_TEXTURE_2D,pal);nearest();
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,16,1,0,GL_RGBA,GL_UNSIGNED_BYTE,colors);
                int main=host.equals("s2")?0:1,side=host.equals("s2")?1:2;
                var mica=portrait("mica","normal");var talon=portrait("talon","normal");
                var team=new HashMap<Integer,ReelCharacterPresentation.Portrait>();team.put(main,mica);team.put(side,talon);
                if(count==8)team.put(7,portrait("mica","powered"));
                for(String mode:List.of("native","team","solo","scroll")) {
                    Map<Integer,ReelCharacterPresentation.Portrait> replacements=mode.equals("native")?Map.of():mode.equals("solo")?(count==8?Map.of(main,talon,side,talon,7,portrait("talon","powered")):Map.of(main,talon,side,talon)):team;
                    var atlas=ReelCharacterPresentation.atlas(indices,count,replacements);
                    try {
                        glBindTexture(GL_TEXTURE_2D,tex);nearest();
                        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,32,32*count,0,GL_RGBA,GL_UNSIGNED_BYTE,atlas);
                    } finally {MemoryUtil.memFree(atlas);}
                    glViewport(0,0,320,224);glClearColor(.15f,.15f,.15f,1);glClear(GL_COLOR_BUFFER_BIT);
                    for(int row=0;row<3;row++) {
                        int f=row*3;
                        pass.draw(tex,pal,40,24+row*56,0,f%count,(f+1)%count,(f+2)%count,
                                (f+1)%count,(f+2)%count,(f+3)%count,
                                mode.equals("scroll")?.5f:0,mode.equals("scroll")?.5f:0,mode.equals("scroll")?.5f:0, true);
                    }
                    glReadPixels(0,0,320,224,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                    if(mode.equals("native")) {
                        for(int f=0;f<count;f++) for(int y=0;y<32;y++) for(int x=0;x<32;x++) {
                            int index=indices.get(f*1024+y*32+x)&15;
                            if(index==0) continue;
                            int target=((223-(24+f/3*56+y))*320+40+f%3*32+x)*4;
                            for(int channel=0;channel<3;channel++)
                                assertEquals(colors.get(index*4+channel)&255,pixels.get(target+channel)&255,
                                        "native symbol="+f+" pixel="+x+","+y);
                        }
                    }
                    if(mode.equals("team")) {
                        byte[] face=mica.image().pixels();int[] rgb=mica.rgb();int checked=0;
                        for(int y=0;y<32;y++)for(int x=0;x<32;x++)if(face[y*32+x]!=0) {
                            int actual=((pixels.get(((223-24-y)*320+40+main*32+x)*4)&255)<<16)
                                    |((pixels.get(((223-24-y)*320+40+main*32+x)*4+1)&255)<<8)
                                    |(pixels.get(((223-24-y)*320+40+main*32+x)*4+2)&255);
                            assertEquals(rgb[face[y*32+x]],actual,"authored portrait pixel "+x+","+y);checked++;
                        }
                        assertTrue(checked>600);
                    }
                    String output=System.getProperty("reel.capture.output");
                    if(output!=null) {
                        Path dest=Path.of(output);Files.createDirectories(dest);
                        BufferedImage image=new BufferedImage(320,224,BufferedImage.TYPE_INT_ARGB);
                        for(int y=0;y<224;y++)for(int x=0;x<320;x++) {
                            int i=((223-y)*320+x)*4;
                            image.setRGB(x,y,0xFF000000|((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));
                        }
                        ImageIO.write(image,"png",dest.resolve(host+"-"+mode+".png").toFile());
                    }
                    assertEquals(GL_NO_ERROR,glGetError());
                }
            } finally {
                MemoryUtil.memFree(indices);MemoryUtil.memFree(pixels);MemoryUtil.memFree(colors);
                glDeleteTextures(tex);glDeleteTextures(pal);pass.cleanup();shader.cleanup();
            }
        }
    }
    private static void nearest() {
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    }
}
