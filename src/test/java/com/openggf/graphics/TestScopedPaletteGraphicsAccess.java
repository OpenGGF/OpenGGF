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
    @Test void fullScopedBankBudgetDoesNotCapOrdinaryContextsAndReleasedRowsRemainReusable() {
        RenderContext.reset();var graphics=new GraphicsManager();graphics.initHeadless();
        try {
            Object[] owners=new Object[15];var banks=new ScopedPaletteGraphicsAccess.Bank[15];
            for(int i=0;i<15;i++) {
                owners[i]=new Object();banks[i]=ScopedPaletteGraphicsAccess.acquire(graphics,owners[i],palettes());
                assertEquals((i+1)*4,ScopedPaletteGraphicsAccess.base(banks[i],graphics,owners[i]));
            }
            var donor=RenderContext.getOrCreateDonor(GameId.S1);
            var buddy=RenderContext.createSidekickContext(GameId.S3K);
            assertEquals(64,donor.getPaletteLineBase());assertEquals(68,buddy.getPaletteLineBase());
            assertEquals(72,RenderContext.getTotalPaletteLines());
            assertThrows(IllegalStateException.class,()->ScopedPaletteGraphicsAccess.acquire(graphics,new Object(),palettes()));
            for(int i=0;i<15;i++)assertTrue(ScopedPaletteGraphicsAccess.valid(banks[i],graphics,owners[i]));

            ScopedPaletteGraphicsAccess.release(banks[0],owners[0]);
            Object replacementOwner=new Object();var replacement=ScopedPaletteGraphicsAccess.acquire(graphics,replacementOwner,palettes());
            assertEquals(4,ScopedPaletteGraphicsAccess.base(replacement,graphics,replacementOwner));
            assertFalse(ScopedPaletteGraphicsAccess.valid(banks[0],graphics,owners[0]));
            assertEquals(72,RenderContext.getTotalPaletteLines());
            RenderContext.clearSidekickContexts();
            assertEquals(68,RenderContext.createSidekickContext(GameId.S2).getPaletteLineBase());
            assertTrue(ScopedPaletteGraphicsAccess.valid(replacement,graphics,replacementOwner));
            assertThrows(IllegalStateException.class,()->ScopedPaletteGraphicsAccess.acquire(graphics,new Object(),palettes()));
            ScopedPaletteGraphicsAccess.release(replacement,replacementOwner);
            for(int i=1;i<15;i++)ScopedPaletteGraphicsAccess.release(banks[i],owners[i]);
        } finally { graphics.cleanup();RenderContext.reset(); }
    }

}
