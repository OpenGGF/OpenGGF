package com.openggf.game.sonic2.titlecard;


import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTitleCardManagerNativeExitTiming {

    @Test
    void omittedNativeLeaveRetainsTheLeadingPassAndExactBackgroundHandoff() {
        TitleCardManager manager = new TitleCardManager(() -> null, () -> false);
        manager.beginOmittedPresentationLeave(3, 1);
        assertTrue(manager.shouldRunPlayerPhysics());
        assertFalse(manager.shouldReleaseControl());
        assertEquals(TitleCardState.EXIT_LEFT_SWOOSH, manager.getState());
        // The leading RunObjects has no update/WaitForVint of its own.
        manager.completeLockedIteration();
        int deformations = 0;
        for (int wait = 1; wait <= 25; wait++) {
            if (manager.isFirstBackgroundLeavePass()) {
                deformations++;
                assertEquals(17, wait, "five left and eleven bottom passes precede BackgroundOutInit");
                var before = manager.capture();
                manager.update();
                assertFalse(manager.isFirstBackgroundLeavePass());
                manager.restore(before);
                assertTrue(manager.isFirstBackgroundLeavePass(), "the handoff is part of the title owner's snapshot");
            }
            manager.update();
            assertTrue(manager.shouldRunPlayerPhysics());
            manager.completeLockedIteration();
            assertEquals(wait == 25, manager.shouldReleaseControl());
        }
        assertEquals(1, deformations);
        assertFalse(manager.shouldRunPlayerPhysics());
        assertEquals(TitleCardState.TEXT_WAIT, manager.getState());
        assertEquals(3, manager.capture().currentZone());
        assertEquals(1, manager.capture().currentAct());
    }

    @Test
    void onlyTheBackgroundOutInitIterationRunsTheLevelScrollAndEventStep() {
        TitleCardManager manager = new TitleCardManager(() -> null, () -> false);
        manager.beginOmittedPresentationLeave(3, 0);
        // The pass after the load (s2.asm:5006) runs objects only.
        assertTrue(manager.shouldRunPlayerPhysics());
        assertFalse(manager.runsLevelScrollAndEventsThisLockedPass());
        manager.completeLockedIteration();
        int scrollPasses = 0;
        for (int wait = 1; wait <= 25; wait++) {
            manager.update();
            boolean scrolls = manager.runsLevelScrollAndEventsThisLockedPass();
            assertEquals(wait == 17, scrolls, "leave iteration " + wait);
            if (scrolls) scrollPasses++;
            manager.completeLockedIteration();
        }
        assertEquals(1, scrollPasses, "the leave loop reaches DeformBgLayer once");
    }

    @Test
    void theArrivalLoopSpendsOneMoreVblankBeforeTheZoneTileUpload() throws Exception {
        TitleCardManager manager = new TitleCardManager(() -> null, () -> false);
        setField(manager, "state", TitleCardState.DISPLAY);

        // The pattern cue is empty: Level_TtlCard's re-loop test passes on this
        // iteration and the routine waits for one more VBlank (s2.asm:4925-4926).
        manager.update();
        assertEquals(TitleCardState.ZONE_TILE_UPLOAD, manager.getState());
        assertFalse(manager.shouldRunPlayerPhysics());

        // With no zone art loaded the upload itself costs nothing, so the next
        // VBlank's iteration is the s2.asm:5006 object pass.
        manager.update();
        assertEquals(TitleCardState.EXIT_LEFT_SWOOSH, manager.getState());
        assertTrue(manager.shouldRunPlayerPhysics());
    }

    @Test
    void releaseFollowsTheTwentySixthObjectPassWithoutAnotherVblank() throws Exception {
        TitleCardManager manager = new TitleCardManager(() -> null, () -> false);
        setField(manager, "state", TitleCardState.ZONE_TILE_UPLOAD);
        setField(manager, "zoneTileUploadFramesLeft", 1);

        for (int pass = 1; pass <= 26; pass++) {
            manager.update();
            assertTrue(manager.shouldRunPlayerPhysics(), "locked object pass " + pass);
            assertFalse(manager.shouldReleaseControl(), "control stays locked during the object pass");
            manager.completeLockedIteration();
            assertEquals(pass == 26, manager.shouldReleaseControl(), "exit test after pass " + pass);
        }
        assertEquals(TitleCardState.TEXT_WAIT, manager.getState());
        assertFalse(manager.shouldRunPlayerPhysics());
    }

    @Test
    void rewindRestoresTheFinalLockedPassAndItsSameIterationRelease() throws Exception {
        TitleCardManager manager = new TitleCardManager(() -> null, () -> false);
        manager.beginOmittedPresentationLeave(0, 0);
        manager.completeLockedIteration();
        for (int pass = 0; pass < 24; pass++) {
            manager.update();
            manager.completeLockedIteration();
        }
        var before = manager.capture();
        manager.update();
        manager.completeLockedIteration();
        assertTrue(manager.shouldReleaseControl());

        manager.restore(before);
        assertFalse(manager.shouldReleaseControl());
        manager.update();
        assertTrue(manager.shouldRunPlayerPhysics());
        manager.completeLockedIteration();
        assertTrue(manager.shouldReleaseControl());
        assertEquals(TitleCardState.TEXT_WAIT, manager.getState());
    }

    @Test
    void nativeLeftExitUsesTheRomFivePassHandoffAtEveryViewportMargin() throws Exception {
        TitleCardManager manager = new TitleCardManager(() -> null, () -> false);
        manager.beginOmittedPresentationLeave(0, 0);
        // Native positions do not depend on viewport margins.
        assertEquals(0xF0, TitleCardNativeDisplayAccess.read(manager).parts().get(5).x());

        for (int pass = 1; pass < 5; pass++) {
            manager.update();
            assertEquals(TitleCardState.EXIT_LEFT_SWOOSH, manager.getState(),
                    "the ROM handoff must not occur before leave-loop pass five");
        }

        manager.update();
        assertEquals(TitleCardState.EXIT_BOTTOM_BAR, manager.getState(),
                "the ROM hands the bottom piece routine $10 on pass five, "
                        + "independent of the presentation-width exit margin");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
