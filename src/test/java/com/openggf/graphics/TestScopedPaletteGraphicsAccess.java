package com.openggf.graphics;

import com.openggf.game.GameId;
import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestScopedPaletteGraphicsAccess {
    private static Palette[] palettes() { return new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()}; }
    @Test void scopedOwnersDonorsAndSidekicksNeverShareRowsAndReleasedHandlesCannotFollowReuse() {
        RenderContext.reset();var graphics=new GraphicsManager();graphics.initHeadless();
        try {
            Object a=new Object(),b=new Object();var first=ScopedPaletteGraphicsAccess.acquire(graphics,a,palettes());
            int x=ScopedPaletteGraphicsAccess.base(first,graphics,a);
            var donor=RenderContext.getOrCreateDonor(GameId.S1);var buddy=RenderContext.createSidekickContext(GameId.S2);
            var second=ScopedPaletteGraphicsAccess.acquire(graphics,b,palettes());int y=ScopedPaletteGraphicsAccess.base(second,graphics,b);
            assertNotEquals(x,y);assertNotEquals(x,donor.getPaletteLineBase());assertNotEquals(x,buddy.getPaletteLineBase());
            assertNotEquals(y,donor.getPaletteLineBase());assertNotEquals(y,buddy.getPaletteLineBase());
            assertThrows(IllegalArgumentException.class,()->ScopedPaletteGraphicsAccess.base(first,graphics,b));
            assertThrows(IllegalArgumentException.class,()->ScopedPaletteGraphicsAccess.base(first,new GraphicsManager(),a));
            assertThrows(IllegalArgumentException.class,()->ScopedPaletteGraphicsAccess.release(first,b));
            ScopedPaletteGraphicsAccess.release(first,a);assertFalse(ScopedPaletteGraphicsAccess.valid(first,graphics,a));
            Object c=new Object();var reused=ScopedPaletteGraphicsAccess.acquire(graphics,c,palettes());
            assertEquals(x,ScopedPaletteGraphicsAccess.base(reused,graphics,c));
            assertFalse(ScopedPaletteGraphicsAccess.valid(first,graphics,a));assertTrue(ScopedPaletteGraphicsAccess.valid(second,graphics,b));
            RenderContext.clearSidekickContexts();assertTrue(ScopedPaletteGraphicsAccess.valid(second,graphics,b));
            var laterBuddy=RenderContext.createSidekickContext(GameId.S3K);assertNotEquals(y,laterBuddy.getPaletteLineBase());
            ScopedPaletteGraphicsAccess.release(second,b);ScopedPaletteGraphicsAccess.release(reused,c);
        } finally { graphics.cleanup();RenderContext.reset(); }
    }
    @Test void loadGraphicsResetInvalidatesLiveHandlesAndBoundsAllScopedReservations() {
        RenderContext.reset();var graphics=new GraphicsManager();graphics.initHeadless();
        try {
            Object owner=new Object();var old=ScopedPaletteGraphicsAccess.acquire(graphics,owner,palettes());
            int base=ScopedPaletteGraphicsAccess.base(old,graphics,owner);graphics.clearPaletteTextures();
            assertFalse(ScopedPaletteGraphicsAccess.valid(old,graphics,owner));
            var replacement=ScopedPaletteGraphicsAccess.acquire(graphics,owner,palettes());
            assertEquals(base,ScopedPaletteGraphicsAccess.base(replacement,graphics,owner));
            assertFalse(ScopedPaletteGraphicsAccess.valid(old,graphics,owner));
            for(int index=1;index<15;index++)ScopedPaletteGraphicsAccess.acquire(graphics,new Object(),palettes());
            assertEquals(64,RenderContext.getTotalPaletteLines());
            assertThrows(IllegalStateException.class,()->ScopedPaletteGraphicsAccess.acquire(graphics,new Object(),palettes()));
        } finally { graphics.cleanup();RenderContext.reset(); }
    }
}
