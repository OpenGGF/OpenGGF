package com.openggf.game.sonic2.titlecard;

import com.openggf.game.titlecard.TitleCardElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestTitleCardManagerNativeExitTiming {

    @Test
    void nativeLeftExitWaitsForSwooshToFinishBeforeStartingBottomBar() throws Exception {
        TitleCardManager manager = new TitleCardManager(() -> null, () -> false);
        TitleCardElement swoosh = TitleCardElement.createLeftSwoosh();
        for (int frame = 0; frame < 1000 && !swoosh.isAtTarget(); frame++) {
            swoosh.updateSlideIn();
        }

        setField(manager, "state", TitleCardState.EXIT_LEFT_SWOOSH);
        setField(manager, "leftSwooshElement", swoosh);

        manager.update();

        assertEquals(TitleCardState.EXIT_LEFT_SWOOSH, manager.getState(),
                "native S2 title cards must wait while the left swoosh is still exiting");

        while (!swoosh.hasExited()) {
            manager.update();
        }
        assertEquals(TitleCardState.EXIT_BOTTOM_BAR, manager.getState(),
                "the bottom bar transition must occur after the swoosh exits");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
