package com.openggf.tools;

import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;

/** Reuses the production display-free EGL pbuffer for picture tests; never connects to a desktop. */
public final class PresentationOffscreenTestContext implements AutoCloseable {
    private final SurfacelessEglContext context;
    private PresentationOffscreenTestContext(SurfacelessEglContext context) { this.context=context; }
    public static boolean requested() { return SurfacelessEglContext.requested(); }
    public static PresentationOffscreenTestContext open(int width,int height) {
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_NULL);
        if(!glfwInit()) {
            glfwInitHint(GLFW_PLATFORM,GLFW_ANY_PLATFORM);
            throw new IllegalStateException("GLFW null platform unavailable; no display fallback");
        }
        SurfacelessEglContext context=null;
        try {
            context=SurfacelessEglContext.create(width,height,true);GL.createCapabilities();
            return new PresentationOffscreenTestContext(context);
        } catch(RuntimeException|Error failure) {
            if(context!=null)context.close();glfwTerminate();glfwInitHint(GLFW_PLATFORM,GLFW_ANY_PLATFORM);throw failure;
        }
    }
    @Override public void close() {
        try { GL.setCapabilities(null);context.close(); }
        finally { glfwTerminate();glfwInitHint(GLFW_PLATFORM,GLFW_ANY_PLATFORM); }
    }
}
