package com.openggf.widescreen;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardState;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardTeardownModel;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTitleCardWidescreenBounds {

    @Test
    void s2ExitTailsKeepRomPassTimingAtEveryViewportWidth() {
        var graphics = GameServices.graphics();
        int previousWidth = graphics.getProjectionWidth();
        var baseline = new java.util.ArrayList<com.openggf.game.sonic2.titlecard.TitleCardNativeDisplayAccess.Display>();
        try {
            for (int width : new int[]{320, 424, 528}) {
                graphics.setProjectionWidth(width);
                var manager = new com.openggf.game.sonic2.titlecard.TitleCardManager();
                manager.beginOmittedPresentationLeave(0, 0);
                manager.completeLockedIteration();
                for (int wait = 1; wait <= 25; wait++) {
                    manager.update();
                    manager.completeLockedIteration();
                    var parts = com.openggf.game.sonic2.titlecard.TitleCardNativeDisplayAccess.read(manager);
                    if (width == 320) baseline.add(parts);
                    else assertEquals(baseline.get(wait - 1), parts, "native slots are independent of viewport width");
                    // Obj34_LeftPartOut / BottomPartOut / BackgroundOut
                    // (s2.asm:27518-27604): native slot order delays each handoff.
                    if (wait == 5) {
                        assertFalse(parts.parts().get(5).alive());
                        assertEquals(16, parts.parts().get(4).routine());
                    }
                    if (wait == 16) {
                        assertFalse(parts.parts().get(4).alive());
                        assertEquals(18, parts.parts().get(3).routine());
                    }
                    assertEquals(wait == 25, manager.shouldReleaseControl(), "native background deletion releases control");
                }
            }
        } finally {
            graphics.setProjectionWidth(previousWidth);
        }
    }

    @Test
    void s3kManagerRetiresAnElementAgainstTheLiveViewport() throws Exception {
        GraphicsManager graphics = GameServices.graphics();
        int previousWidth = graphics.getProjectionWidth();
        try {
            graphics.setProjectionWidth(320);
            Sonic3kTitleCardManager nativeManager = stagedExitManager(500);
            nativeManager.update();
            assertTrue(elementOutside(nativeManager, 1),
                    "the element is outside the native viewport after its first exit step");

            graphics.setProjectionWidth(528);
            Sonic3kTitleCardManager wideManager = stagedExitManager(500);
            wideManager.update();
            assertFalse(elementOutside(wideManager, 1),
                    "the same element remains visible inside the live wide viewport");
            wideManager.update();
            assertTrue(elementOutside(wideManager, 1),
                    "the element retires only after its rendered wide bound clears");
        } finally {
            graphics.setProjectionWidth(previousWidth);
        }
    }

    @Test
    void s3kTeardownCompletesLaterAtTheLiveWideViewport() {
        GraphicsManager graphics = GameServices.graphics();
        int previousWidth = graphics.getProjectionWidth();
        try {
            graphics.setProjectionWidth(320);
            Sonic3kTitleCardTeardownModel nativeModel = new Sonic3kTitleCardTeardownModel();
            while (!nativeModel.isComplete()) {
                nativeModel.tick();
            }

            graphics.setProjectionWidth(528);
            Sonic3kTitleCardTeardownModel wideModel = new Sonic3kTitleCardTeardownModel();
            while (!wideModel.isComplete()) {
                wideModel.tick();
            }

            assertEquals(35, nativeModel.ticksElapsed());
            assertEquals(39, wideModel.ticksElapsed(),
                    "wide teardown must wait for the live right edge before owner release");
        } finally {
            graphics.setProjectionWidth(previousWidth);
        }
    }

    private static Sonic3kTitleCardManager stagedExitManager(int elementX) throws Exception {
        Sonic3kTitleCardManager manager = new Sonic3kTitleCardManager();
        setField(manager, "state", Sonic3kTitleCardState.EXIT);
        setField(manager, "phaseCounter", 100);
        setField(manager, "actNumberVisible", true);

        int[] x = (int[]) getField(manager, "elemX");
        int[] y = (int[]) getField(manager, "elemY");
        boolean[] exiting = (boolean[]) getField(manager, "elemExiting");
        boolean[] outside = (boolean[]) getField(manager, "elemOutsideViewport");
        boolean[] exited = (boolean[]) getField(manager, "elemExited");
        Arrays.fill(exited, true);
        exited[1] = false;
        x[1] = elementX;
        y[1] = 96;
        exiting[1] = true;
        outside[1] = false;
        return manager;
    }

    private static boolean elementOutside(Sonic3kTitleCardManager manager, int index)
            throws Exception {
        return ((boolean[]) getField(manager, "elemOutsideViewport"))[index];
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
