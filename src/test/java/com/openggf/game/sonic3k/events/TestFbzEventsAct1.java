package com.openggf.game.sonic3k.events;

import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestFbzEventsAct1 {
    @Test
    void screenInitAtStageStartSelectsOutdoorRegionSixWithoutCopying() {
        Sonic3kFBZEvents events = fresh();
        assertTrue(events.initializeAct1Screen(0x017F).isEmpty());
        assertEquals(0x18, events.getForegroundLayoutRegion());
        assertTrue(events.isForegroundOutdoor());
    }

    @Test
    void screenInitPastStageStartCopiesFiveByThreeIndoorPlaneAWindow() {
        Sonic3kFBZEvents events = fresh();
        assertEquals(
                java.util.List.of(new Sonic3kFBZEvents.LayoutCopy(0, 18, 0, 13, 5, 3)),
                events.initializeAct1Screen(0x0180));
        assertEquals(0, events.getForegroundLayoutRegion());
        assertFalse(events.isForegroundOutdoor());
    }

    @Test
    void allSixRomLayoutRangesAreInclusiveAndExact() {
        assertArrayEquals(new int[][]{
                        {0x400, 0xF00, 0x880, 0xA80},
                        {0x880, 0x1100, 0x180, 0x300},
                        {0x1400, 0x1B80, 0x900, 0xB00},
                        {0x1A80, 0x2100, 0x80, 0x200},
                        {0x2080, 0x2680, 0x100, 0x280},
                        {0, 0x180, 0x580, 0x780}
                }, Sonic3kFBZEvents.act1LayoutRanges());
    }

    @Test
    void deathGateFreezesScreenAndNormalBackgroundChecks() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct1Screen(0x180);
        events.initializeAct1Background(0x180);
        events.updateAct1Frame(0x500, 0x900, true, 0);
        assertEquals(0, events.getForegroundLayoutRegion());
        assertFalse(events.isBackgroundOutdoor());
        assertEquals(Sonic3kFBZEvents.RedrawDirection.NONE, events.getBackgroundRedrawDirection());
    }

    @Test
    void activeRedrawStillProgressesWhilePlayerIsDying() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct1Screen(0x180);
        events.initializeAct1Background(0x180);
        events.updateAct1Frame(0x500, 0x9D0, false, 0);
        assertEquals(1, events.getBackgroundRedrawProgress());
        events.updateAct1Frame(0x500, 0x9D0, true, 1);
        assertEquals(2, events.getBackgroundRedrawProgress());
    }

    @Test
    void foregroundAndBackgroundOutdoorFlagsTransitionIndependently() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct1Screen(0x180);
        events.initializeAct1Background(0x180);
        events.updateAct1Frame(0x500, 0x900, false, 0);
        assertEquals(4, events.getForegroundLayoutRegion());
        assertFalse(events.isForegroundOutdoor());
        assertFalse(events.isBackgroundOutdoor());
        events.updateAct1Frame(0x70B, 0x900, false, 1);
        assertTrue(events.isForegroundOutdoor());
        assertFalse(events.isBackgroundOutdoor());
    }

    @Test
    void eachOfSixScreenEventRegionsExecutesItsRomCopyShape() {
        assertScreenCopy(1, 0x70B, 0x900, 2);
        assertScreenCopy(2, 0x900, 0x1F1, 2);
        assertScreenCopy(3, 0x158B, 0x980, 2);
        assertScreenCopy(4, 0x1C0B, 0x100, 2);
        assertScreenCopy(5, 0x2080, 0x171, 2);
        assertScreenCopy(6, 0x100, 0x70F, 1);
    }

    @Test
    void allBackgroundDirectionsStartDrawingOnTriggerFrameAndFinishAfterSixteen() {
        assertDirection(1, 0x500, 0x9D0, Sonic3kFBZEvents.RedrawDirection.BOTTOM_UP);
        assertDirection(2, 0x900, 0x2B0, Sonic3kFBZEvents.RedrawDirection.TOP_DOWN);
        assertDirection(4, 0x1B10, 0xFF, Sonic3kFBZEvents.RedrawDirection.RIGHT_TO_LEFT);

        Sonic3kFBZEvents events = fresh();
        events.initializeAct1Background(0x17F);
        events.setForegroundLayoutRegion(16);
        events.updateAct1BackgroundEvent(0x1AF0, 0xFF, false);
        assertEquals(Sonic3kFBZEvents.RedrawDirection.LEFT_TO_RIGHT, events.getBackgroundRedrawDirection());
        assertEquals(1, events.getBackgroundRedrawProgress());
    }

    @Test
    void redrawCadenceUsesRomPositionsIndependentOfViewportWidth() {
        assertRedrawPositions(1, 0x500, 0x9D0, 0x100, 0xF0);
        assertRedrawPositions(2, 0x900, 0x2B0, 0x10, 0x20);
        assertRedrawPositions(4, 0x1B10, 0xFF, 0x3F0, 0x3D0);

        Sonic3kFBZEvents left = fresh();
        left.initializeAct1Background(0x17F);
        left.setForegroundLayoutRegion(16);
        left.updateAct1BackgroundEvent(0x1AF0, 0xFF, false);
        assertEquals(0, left.getBackgroundRedrawPosition());
        left.updateAct1BackgroundEvent(0x1AF0, 0xFF, false);
        assertEquals(0x20, left.getBackgroundRedrawPosition());
    }

    @Test
    void backgroundInitSelectsTargetPaletteAndCorrectDeformMode() {
        Sonic3kFBZEvents outdoor = fresh();
        assertEquals(Sonic3kFBZEvents.PaletteTarget.TARGET,
                outdoor.initializeAct1Background(0x17F));
        assertTrue(outdoor.isBackgroundOutdoor());
        assertEquals(Sonic3kFBZEvents.DeformMode.OUTDOOR, outdoor.getDeformMode());

        Sonic3kFBZEvents indoor = fresh();
        assertEquals(Sonic3kFBZEvents.PaletteTarget.NONE,
                indoor.initializeAct1Background(0x180));
        assertFalse(indoor.isBackgroundOutdoor());
        assertEquals(Sonic3kFBZEvents.DeformMode.INDOOR, indoor.getDeformMode());
    }

    @Test
    void outdoorMotionAllocationAttemptIsDistinctFromSuccessAndRestorable() {
        Sonic3kFBZEvents events = fresh();
        assertFalse(events.isOutdoorMotionAllocationAttempted());
        events.restoreOutdoorMotionAllocationState(true, false);
        assertTrue(events.isOutdoorMotionAllocationAttempted());
        assertFalse(events.isOutdoorMotionSpawned());
    }

    @Test
    void normalPalettePatchIsLatchedOnTransitionAndReconciled() {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct1Background(0x180);
        events.setForegroundLayoutRegion(4);
        events.updateAct1BackgroundEvent(0x500, 0x9C0, false);
        assertEquals(Sonic3kFBZEvents.PaletteVariant.OUTDOOR, events.getPaletteVariant());
        assertEquals(Sonic3kFBZEvents.PaletteTarget.NORMAL, events.getPaletteTarget());
        events.reconcileAct1State();
        assertEquals(Sonic3kFBZEvents.PaletteVariant.OUTDOOR, events.getPaletteVariant());
        assertEquals(Sonic3kFBZEvents.PaletteTarget.NORMAL, events.getPaletteTarget());
    }

    private static void assertDirection(int region, int x, int y,
                                        Sonic3kFBZEvents.RedrawDirection direction) {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct1Background(0x180);
        events.setForegroundLayoutRegion(region * 4);
        events.updateAct1BackgroundEvent(x, y, false);
        assertEquals(direction, events.getBackgroundRedrawDirection());
        assertEquals(1, events.getBackgroundRedrawProgress(), "first redraw must occur on trigger frame");
        for (int i = 1; i < 16; i++) events.updateAct1BackgroundEvent(x, y, false);
        assertEquals(Sonic3kFBZEvents.RedrawDirection.NONE, events.getBackgroundRedrawDirection());
        assertEquals(0, events.getBackgroundRedrawProgress());
    }

    private static void assertScreenCopy(int region, int x, int y, int copyCount) {
        Sonic3kFBZEvents events = fresh();
        events.setForegroundLayoutRegion(region * 4);
        assertEquals(copyCount, events.updateAct1ScreenEvent(x, y).size(), "region " + region);
        assertTrue(events.isForegroundOutdoor(), "region " + region);
    }

    private static void assertRedrawPositions(int region, int x, int y, int first, int second) {
        Sonic3kFBZEvents events = fresh();
        events.initializeAct1Background(0x180);
        events.setForegroundLayoutRegion(region * 4);
        events.updateAct1BackgroundEvent(x, y, false);
        assertEquals(first, events.getBackgroundRedrawPosition());
        events.updateAct1BackgroundEvent(x, y, false);
        assertEquals(second, events.getBackgroundRedrawPosition());
    }

    private static Sonic3kFBZEvents fresh() {
        Sonic3kFBZEvents events = new Sonic3kFBZEvents();
        events.init(0);
        return events;
    }
}
