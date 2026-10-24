package com.openggf.graphics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TestObjectVisualArtBanks {
    @Test
    void actualSizeReservationsReuseNonLifoHoleAndOldReleaseCannotRemoveReplacement() {
        GraphicsManager graphics = new GraphicsManager();
        Object a = new Object(),
                b = new Object(),
                c = new Object(),
                replacementOwner = new Object();
        var first = ObjectVisualGraphicsAccess.acquireArt(graphics, a, 8);
        var middle = ObjectVisualGraphicsAccess.acquireArt(graphics, b, 16);
        var last = ObjectVisualGraphicsAccess.acquireArt(graphics, c, 8);
        assertEquals(
                ObjectVisualGraphicsAccess.artBase(first, graphics, a) + 8,
                ObjectVisualGraphicsAccess.artBase(middle, graphics, b));
        int reusable = ObjectVisualGraphicsAccess.artBase(middle, graphics, b);
        ObjectVisualGraphicsAccess.releaseArt(middle, b);
        var replacement = ObjectVisualGraphicsAccess.acquireArt(graphics, replacementOwner, 16);
        assertEquals(
                reusable,
                ObjectVisualGraphicsAccess.artBase(replacement, graphics, replacementOwner));
        ObjectVisualGraphicsAccess.releaseArt(middle, b);
        assertTrue(ObjectVisualGraphicsAccess.validArt(replacement, graphics, replacementOwner));
        ObjectVisualGraphicsAccess.releaseArt(first, a);
        assertTrue(ObjectVisualGraphicsAccess.validArt(last, graphics, c));
        ObjectVisualGraphicsAccess.releaseArt(last, c);
        ObjectVisualGraphicsAccess.releaseArt(replacement, replacementOwner);
    }

    @Test
    void resetInvalidatesOldLeaseAndLateCloseCannotReleaseNewGeneration() {
        GraphicsManager graphics = new GraphicsManager();
        graphics.setHeadlessMode(true);
        Object owner = new Object();
        var old = ObjectVisualGraphicsAccess.acquireArt(graphics, owner, 8);
        graphics.resetPatternAndPaletteState();
        assertFalse(ObjectVisualGraphicsAccess.validArt(old, graphics, owner));
        var current = ObjectVisualGraphicsAccess.acquireArt(graphics, owner, 8);
        ObjectVisualGraphicsAccess.releaseArt(old, owner);
        assertTrue(ObjectVisualGraphicsAccess.validArt(current, graphics, owner));
        ObjectVisualGraphicsAccess.releaseArt(current, owner);
    }

    @Test
    void objectAndOrdinaryTrailReservationsNeverOverlapAndDoNotReleaseEachOther() {
        GraphicsManager graphics = new GraphicsManager();
        Object objectOwner = new Object(), trailOwner = new Object();
        var object = ObjectVisualGraphicsAccess.acquireArt(graphics, objectOwner, 8);
        var trail = PresentationGraphicsAccess.acquire(graphics, trailOwner);
        int objectBase = ObjectVisualGraphicsAccess.artBase(object, graphics, objectOwner);
        int trailBase = PresentationGraphicsAccess.base(trail, graphics, trailOwner);
        assertTrue(objectBase + 8 <= trailBase || trailBase + 6144 <= objectBase);
        PresentationGraphicsAccess.release(trail, trailOwner);
        assertTrue(ObjectVisualGraphicsAccess.validArt(object, graphics, objectOwner));
        ObjectVisualGraphicsAccess.releaseArt(object, objectOwner);
        Object nextOwner = new Object();
        var next = PresentationGraphicsAccess.acquire(graphics, nextOwner);
        assertEquals(
                PatternAtlasRange.PRESENTATION_TRAILS.base(),
                PresentationGraphicsAccess.base(next, graphics, nextOwner));
        PresentationGraphicsAccess.release(next, nextOwner);
    }
}
