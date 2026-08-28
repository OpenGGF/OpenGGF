package com.openggf.game.sonic2.titlecard;

import com.openggf.game.titlecard.TitleCardElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestTitleCardManagerNativeExitTiming {

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
