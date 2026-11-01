package com.openggf;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.DisplayWindowController.*;

class TestDisplayWindowController {
    static class DesktopStub implements Desktop {
        Placement normal = new Placement(-1700, 80, 960, 672, true);
        Monitor screen = new Monitor(2, -1920, 0, 1920, 1080, 60);
        Placement restored;
        Mode mode;
        int saves, switches;
        public Placement placement() { saves++; return normal; }
        public Monitor monitor() { return screen; }
        public void windowed(Placement value) { restored = value; switches++; }
        public void fullscreen(Mode value, Monitor monitor) { mode = value; switches++; }
    }

    @Test void preservesOriginalPlacementAcrossBothFullscreenModes() {
        var desktop = new DesktopStub();
        var controller = new DisplayWindowController(desktop);
        assertFalse(controller.apply("WINDOWED"));
        assertTrue(controller.apply("BORDERLESS"));
        assertFalse(controller.isWindowed());
        assertEquals(Mode.BORDERLESS, desktop.mode);
        assertFalse(controller.apply("BORDERLESS"));
        assertTrue(controller.apply("FULLSCREEN"));
        assertEquals(Mode.FULLSCREEN, desktop.mode);
        assertTrue(controller.apply("WINDOWED"));
        assertEquals(desktop.normal, desktop.restored);
        assertTrue(desktop.restored.maximized());
        assertEquals(1, desktop.saves);
        assertEquals(3, desktop.switches);
    }

    @Test void absentMonitorDoesNotLoseWindowedPlacement() {
        var desktop = new DesktopStub();
        desktop.screen = null;
        var controller = new DisplayWindowController(desktop);
        assertFalse(controller.apply("FULLSCREEN"));
        assertTrue(controller.isWindowed());
        assertEquals(0, desktop.saves);
        assertEquals(0, desktop.switches);
    }

    @Test void invalidSettingReturnsToWindowed() {
        var desktop = new DesktopStub();
        var controller = new DisplayWindowController(desktop);
        controller.apply("FULLSCREEN");
        assertTrue(controller.apply("invalid"));
        assertEquals(desktop.normal, desktop.restored);
    }

    @Test void outputEpochFollowsMonitorAndModeChangesButNotUnchangedReapplication() {
        var desktop = new DesktopStub();
        var controller = new DisplayWindowController(desktop);
        assertEquals(DisplayOutput.UNKNOWN, controller.output());
        controller.apply("WINDOWED");
        var first = controller.output();
        assertEquals(60, first.advertisedRefreshHz());
        for (int poll = 0; poll < 3; poll++) controller.apply("WINDOWED");
        assertEquals(first, controller.output(), "per-loop re-application keeps the epoch");
        desktop.screen = new Monitor(3, 0, 0, 1920, 1080, 60);
        controller.apply("WINDOWED");
        assertTrue(controller.output().epoch() > first.epoch(), "moving to another monitor is a new output");
        var moved = controller.output();
        controller.apply("BORDERLESS");
        assertTrue(controller.output().epoch() > moved.epoch(), "a window mode change is a new output");
        desktop.screen = null;
        controller.apply("BORDERLESS");
        assertEquals(0, controller.output().advertisedRefreshHz(), "no monitor reports an unknown refresh");
    }

    @Test void monitorOverlapSupportsNegativeDesktopCoordinates() {
        var left = new Monitor(2, -1920, 0, 1920, 1080, 60);
        assertEquals(960L * 672, overlap(-1700, 80, 960, 672, left));
        assertEquals(100L * 600, overlap(-100, 0, 800, 600, left));
        assertEquals(0, overlap(0, 0, 800, 600, left));
    }
}
