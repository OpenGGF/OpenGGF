package com.openggf;

import org.junit.jupiter.api.Test;

import static com.openggf.StageplayWindowController.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_DONT_CARE;

class TestStageplayWindowController {
    @Test void minimumIsWidescreenAndLargerWindowsKeepTheirUsableSize() {
        assertEquals(new Fit(new Size(1280,720),new Size(1280,720)),fit(new Size(640,448),new Size(1920,1040)));
        assertEquals(new Size(1600,900),fit(new Size(1600,900),new Size(1920,1040)).requested());
        assertEquals(new Size(1280,720),fit(new Size(640,448),new Size(0,0)).requested());
    }

    @Test void smallMonitorGetsTheLargestFittingWidescreenFallback() {
        var fit = fit(new Size(640,448),new Size(1024,700));
        assertEquals(new Size(1024,576),fit.minimum());
        assertEquals(fit.minimum(),fit.requested());
        assertEquals(new Size(992,558),fit(new Size(1920,1080),new Size(1000,600)).requested());
    }

    @Test void entryAppliesOnceAndExitRestoresWindowAndPreviousUnrestrictedPolicy() {
        var window = new FakeWindow(); var controller = new StageplayWindowController(window);
        controller.sync(true,true); controller.sync(true,true);
        assertEquals(1,window.resizes);
        assertEquals(new Size(1280,720),window.size);
        assertEquals(new Size(1280,720),window.minimum);
        assertEquals(new Size(16,9),window.aspect);
        controller.sync(false,true);
        assertEquals(new Size(640,448),window.size);
        assertEquals(new Size(GLFW_DONT_CARE,GLFW_DONT_CARE),window.minimum);
        assertEquals(window.minimum,window.aspect);
        controller.sync(false,true); assertEquals(2,window.resizes);
    }

    @Test void fullscreenAndMaximizedWindowsKeepTheirPlacement() {
        var window = new FakeWindow(); var controller = new StageplayWindowController(window);
        controller.sync(true,false); assertEquals(0,window.resizes);
        window.maximized = true; controller.sync(true,true); assertEquals(0,window.resizes);
        window.maximized = false; controller.sync(true,true); assertEquals(1,window.resizes);
        controller.sync(true,false); assertEquals(1,window.resizes);
        assertEquals(new Size(GLFW_DONT_CARE,GLFW_DONT_CARE),window.minimum);
    }

    private static final class FakeWindow implements Window {
        Size size = new Size(640,448), minimum, aspect;
        boolean maximized; int resizes;
        public Size size() { return size; }
        public Size available() { return new Size(1920,1040); }
        public boolean maximized() { return maximized; }
        public void limits(int width,int height) { minimum = new Size(width,height); }
        public void aspect(int numerator,int denominator) { aspect = new Size(numerator,denominator); }
        public void resize(Size value) { size = value; resizes++; }
    }
}
