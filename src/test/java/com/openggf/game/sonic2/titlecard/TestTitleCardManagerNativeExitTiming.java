package com.openggf.game.sonic2.titlecard;

import com.openggf.game.titlecard.TitleCardElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTitleCardManagerNativeExitTiming {

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
        setField(manager, "state", TitleCardState.EXIT_BACKGROUND);
        setField(manager, "leavePass", 25);
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
        TitleCardElement swoosh = TitleCardElement.createLeftSwoosh();
        for (int frame = 0; frame < 1000 && !swoosh.isAtTarget(); frame++) {
            swoosh.updateSlideIn();
        }
        swoosh.setEdgeMargin(208);

        setField(manager, "state", TitleCardState.EXIT_LEFT_SWOOSH);
        setField(manager, "leftSwooshElement", swoosh);
        setField(manager, "leavePass", 1);

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
