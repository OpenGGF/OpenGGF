package com.openggf.editor;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.MutableLevel;

import java.util.Objects;

public final class EditorMouseTransform {
    private static final int NATIVE_WIDTH = 320;
    private static final int NATIVE_HEIGHT = 224;

    private EditorMouseTransform() {
    }

    public static Result toWorldTile(InputHandler inputHandler,
                                     Camera camera,
                                     GraphicsManager graphicsManager,
                                     MutableLevel level) {
        Objects.requireNonNull(inputHandler, "inputHandler");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(graphicsManager, "graphicsManager");
        Objects.requireNonNull(level, "level");
        int[] point=logicalPoint(inputHandler,graphicsManager);
        if(point[2]==0)return Result.outside();
        int worldX=Short.toUnsignedInt(camera.getX())+point[0];
        int worldY=Short.toUnsignedInt(camera.getY())+point[1];
        return new Result(true,worldX,worldY,
                clamp(worldX/level.getBlockPixelSize(),0,level.getMap().getWidth()-1),
                clamp(worldY/level.getBlockPixelSize(),0,level.getMap().getHeight()-1));
    }
    public static int[] logicalPoint(InputHandler input,GraphicsManager graphics) {
        int width=graphics.getProjectionWidth(),height=Math.max(224,(int)graphics.getScreenHeight());
        if(graphics.isGlInitialized()) {
            int[] point=com.openggf.graphics.LogicalMouse.map(org.lwjgl.glfw.GLFW.glfwGetCurrentContext(),
                    graphics,input.getMouseX(),input.getMouseY(),width,height);
            if(point!=null)return point;
        }
        var mapped=toWorldTile(input.getMouseX(),input.getMouseY(),0,0,graphics.getViewportX(),graphics.getViewportY(),
                graphics.getViewportWidth(),graphics.getViewportHeight(),1,width,height,width,height);
        return new int[]{mapped.worldX(),mapped.worldY(),mapped.inViewport()?1:0};
    }

    public static Result toWorldTile(double mouseX,
                                     double mouseY,
                                     int cameraX,
                                     int cameraY,
                                     int viewportX,
                                     int viewportY,
                                     int viewportWidth,
                                     int viewportHeight,
                                     int blockPixelSize,
                                     int mapWidth,
                                     int mapHeight) {
        return toWorldTile(mouseX,mouseY,cameraX,cameraY,viewportX,viewportY,viewportWidth,viewportHeight,
                blockPixelSize,mapWidth,mapHeight,NATIVE_WIDTH,NATIVE_HEIGHT);
    }

    public static Result toWorldTile(double mouseX,double mouseY,int cameraX,int cameraY,
            int viewportX,int viewportY,int viewportWidth,int viewportHeight,int blockPixelSize,
            int mapWidth,int mapHeight,int logicalWidth,int logicalHeight) {
        if (logicalWidth <= 0 || logicalHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0 || blockPixelSize <= 0
                || mapWidth <= 0 || mapHeight <= 0) {
            return Result.outside();
        }
        if (mouseX < viewportX || mouseY < viewportY
                || mouseX >= viewportX + viewportWidth
                || mouseY >= viewportY + viewportHeight) {
            return Result.outside();
        }

        double scaleX = viewportWidth / (double) logicalWidth;
        double scaleY = viewportHeight / (double) logicalHeight;
        int gameX = (int) ((mouseX - viewportX) / scaleX);
        int gameY = (int) ((mouseY - viewportY) / scaleY);
        int worldX = Short.toUnsignedInt((short)cameraX) + gameX;
        int worldY = Short.toUnsignedInt((short)cameraY) + gameY;
        int tileX = clamp(worldX / blockPixelSize, 0, mapWidth - 1);
        int tileY = clamp(worldY / blockPixelSize, 0, mapHeight - 1);
        return new Result(true, worldX, worldY, tileX, tileY);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Result(boolean inViewport, int worldX, int worldY, int tileX, int tileY) {
        private static Result outside() {
            return new Result(false, 0, 0, -1, -1);
        }
    }
}
