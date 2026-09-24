package com.openggf.graphics;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

class TestArenaMaskRenderer {
    @Test void nativeWidthCentrePixelsFrameCadenceAndGlStateSurviveCaptureFbo() {
        boolean nativeDisplay=Boolean.getBoolean("openggf.test.gl.native");
        glfwInitHint(GLFW_PLATFORM,nativeDisplay?GLFW_ANY_PLATFORM:GLFW_PLATFORM_NULL);
        boolean initialized=glfwInit();
        if(nativeDisplay) assertTrue(initialized); else assumeTrue(initialized);
        long window=0; int fbo=0,texture=0;
        var renderer=new ArenaMaskRenderer();
        try {
            glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,1);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
            if(!nativeDisplay) glfwWindowHint(GLFW_CONTEXT_CREATION_API,GLFW_EGL_CONTEXT_API);
            window=glfwCreateWindow(64,64,"arena mask regression",0,0);
            if(nativeDisplay) assertNotEquals(0,window); else assumeTrue(window!=0);
            glfwMakeContextCurrent(window); GL.createCapabilities();
            fbo=glGenFramebuffers(); texture=glGenTextures();
            glBindTexture(GL_TEXTURE_2D,texture);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1640,480,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
            glDisable(GL_DITHER);
            for(int width:new int[]{320,352,400,528,800}) for(int scale:new int[]{1,2}) {
                int w=width*scale,h=224*scale;
                glViewport(13,11,w,h);
                glDisable(GL_BLEND); glEnable(GL_DEPTH_TEST);
                glEnable(GL_SCISSOR_TEST);glScissor(13,11,w,h);
                glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
                renderer.draw(width,224,320,1,100);
                assertFalse(glIsEnabled(GL_BLEND));assertTrue(glIsEnabled(GL_DEPTH_TEST));
                assertTrue(glIsEnabled(GL_SCISSOR_TEST)); assertEquals(fbo,glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
                assertEquals(0,glGetInteger(GL_CURRENT_PROGRAM));assertEquals(0,glGetInteger(GL_VERTEX_ARRAY_BINDING));
                byte[] first=pixels(w,h);
                int left=(width-320)/2*scale,right=left+320*scale;
                for(int y=0;y<h;y++) for(int x=left;x<right;x++) {
                    assertEquals(255,first[(y*w+x)*4]&255);assertEquals(0,first[(y*w+x)*4+1]&255);
                }
                if(width>320) {
                    assertTrue((first[0]&255)<60,"outer wing is opaque dark grain");
                    glClear(GL_COLOR_BUFFER_BIT);renderer.draw(width,224,320,1,101);
                    assertFalse(Arrays.equals(first,pixels(w,h)),"fresh noise on immediately next frame");
                    glClear(GL_COLOR_BUFFER_BIT);renderer.draw(width,224,320,1,100);
                    assertArrayEquals(first,pixels(w,h),"rewind restores exact noise");
                }
                assertEquals(GL_NO_ERROR,glGetError());
            }
        } finally {
            if(window!=0) { renderer.cleanup();if(texture!=0)glDeleteTextures(texture);if(fbo!=0)glDeleteFramebuffers(fbo);glfwDestroyWindow(window);GL.setCapabilities(null); }
            glfwTerminate();
        }
    }
    private static byte[] pixels(int w,int h) {
        ByteBuffer b=org.lwjgl.BufferUtils.createByteBuffer(w*h*4);
        glReadPixels(13,11,w,h,GL_RGBA,GL_UNSIGNED_BYTE,b);
        byte[] data=new byte[b.remaining()];b.get(data);return data;
    }
}
