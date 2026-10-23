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

    @Test void failedSecondRowReleaseRetainsTheExactBankAndRetryCleansAllFourRows() throws Exception {
        RenderContext.reset();var graphics=new FailingReleaseGraphics();graphics.initHeadless();
        try {
            Object owner=new Object(),otherOwner=new Object();Palette[] owned=palettes(),other=palettes();
            for(int row=0;row<4;row++)owned[row].setColor(1,new Palette.Color((byte)(row+1),(byte)17,(byte)33));
            var bank=ScopedPaletteGraphicsAccess.acquire(graphics,owner,owned);
            int base=ScopedPaletteGraphicsAccess.base(bank,graphics,owner);
            var otherBank=ScopedPaletteGraphicsAccess.acquire(graphics,otherOwner,other);
            int otherBase=ScopedPaletteGraphicsAccess.base(otherBank,graphics,otherOwner);
            var cache=paletteCache(graphics);
            var rows=(PaletteView[])field(GraphicsManager.class,"lastCachedPaletteLines").get(graphics);
            var pending=(boolean[])field(GraphicsManager.class,"pendingPaletteUploads").get(graphics);
            for(int row=base;row<base+4;row++)pending[row]=true;
            pending[otherBase]=true;
            graphics.failOnceAt(base+1);
            assertThrows(IllegalArgumentException.class,()->ScopedPaletteGraphicsAccess.release(bank,otherOwner));
            assertTrue(graphics.attempted.isEmpty(),"wrong-owner guard performs no cleanup");
            primeInvalidation(graphics);
            assertSame(graphics.failure,assertThrows(IllegalStateException.class,()->ScopedPaletteGraphicsAccess.release(bank,owner)));
            assertEquals(java.util.List.of(base,base+1),graphics.attempted);assertBlankAttempts(graphics);
            assertTrue(ScopedPaletteGraphicsAccess.valid(bank,graphics,owner),"failed cleanup retains a retryable public handle");
            assertEquals(base,ScopedPaletteGraphicsAccess.base(bank,graphics,owner));
            assertNull(rows[base]);assertFalse(pending[base]);assertFalse(cache.containsKey("palette_"+base));
            for(int row=1;row<4;row++) {
                assertSame(owned[row],rows[base+row]);assertTrue(pending[base+row]);
                assertTrue(cache.containsKey("palette_"+(base+row)));
            }
            assertInvalidated(graphics);
            assertTrue(ScopedPaletteGraphicsAccess.valid(otherBank,graphics,otherOwner));
            for(int row=0;row<4;row++)assertSame(other[row],rows[otherBase+row]);
            assertTrue(cache.containsKey("palette_"+otherBase));assertTrue(pending[otherBase]);
            // Another owner cannot claim the partly cleared bank while its retry is pending.
            Object thirdOwner=new Object();var third=ScopedPaletteGraphicsAccess.acquire(graphics,thirdOwner,palettes());
            assertNotEquals(base,ScopedPaletteGraphicsAccess.base(third,graphics,thirdOwner));
            rows=(PaletteView[])field(GraphicsManager.class,"lastCachedPaletteLines").get(graphics);
            graphics.attempted.clear();graphics.attemptedPalettes.clear();primeInvalidation(graphics);
            ScopedPaletteGraphicsAccess.release(bank,owner);
            assertEquals(java.util.List.of(base,base+1,base+2,base+3),graphics.attempted,"retry starts from row zero");assertBlankAttempts(graphics);
            assertFalse(ScopedPaletteGraphicsAccess.valid(bank,graphics,owner));
            for(int row=base;row<base+4;row++) {
                assertNull(rows[row]);assertFalse(pending[row]);assertFalse(cache.containsKey("palette_"+row));
            }
            assertInvalidated(graphics);
            Object reusedOwner=new Object();var reused=ScopedPaletteGraphicsAccess.acquire(graphics,reusedOwner,palettes());
            assertEquals(base,ScopedPaletteGraphicsAccess.base(reused,graphics,reusedOwner));
            assertTrue(ScopedPaletteGraphicsAccess.valid(otherBank,graphics,otherOwner));
            for(int row=0;row<4;row++)assertSame(other[row],rows[otherBase+row]);
            assertTrue(cache.containsKey("palette_"+otherBase));assertTrue(pending[otherBase]);
            ScopedPaletteGraphicsAccess.release(reused,reusedOwner);
            ScopedPaletteGraphicsAccess.release(third,thirdOwner);
            ScopedPaletteGraphicsAccess.release(otherBank,otherOwner);
        } finally { graphics.cleanup();RenderContext.reset(); }
    }

    @Test void staleUnreleasedAliasCannotReleaseReplacementLeaseForTheSameOwner() {
        RenderContext.reset();var graphics=new FailingReleaseGraphics();graphics.initHeadless();
        try {
            Object owner=new Object();var first=ScopedPaletteGraphicsAccess.acquire(graphics,owner,palettes());
            var alias=ScopedPaletteGraphicsAccess.acquire(graphics,owner,palettes());
            int base=ScopedPaletteGraphicsAccess.base(first,graphics,owner);
            ScopedPaletteGraphicsAccess.release(first,owner);
            var replacement=ScopedPaletteGraphicsAccess.acquire(graphics,owner,palettes());
            assertEquals(base,ScopedPaletteGraphicsAccess.base(replacement,graphics,owner));
            graphics.attempted.clear();
            ScopedPaletteGraphicsAccess.release(alias,owner);
            assertTrue(graphics.attempted.isEmpty(),"an unreleased stale generation never reaches row cleanup");
            assertTrue(ScopedPaletteGraphicsAccess.valid(replacement,graphics,owner));
            ScopedPaletteGraphicsAccess.release(replacement,owner);
            assertEquals(java.util.List.of(base,base+1,base+2,base+3),graphics.attempted);
        } finally { graphics.cleanup();RenderContext.reset(); }
    }

    private static final class FailingReleaseGraphics extends GraphicsManager {
        final java.util.List<Integer> attempted=new java.util.ArrayList<>();
        final java.util.List<PaletteView> attemptedPalettes=new java.util.ArrayList<>();
        final IllegalStateException failure=new IllegalStateException("injected second palette row failure");
        private int failLine=-1;
        void failOnceAt(int line) { attempted.clear();attemptedPalettes.clear();failLine=line; }
        @Override void uploadPaletteLine(PaletteView palette,int line) {
            attempted.add(line);attemptedPalettes.add(palette);
            if(line==failLine) { failLine=-1;throw failure; }
            super.uploadPaletteLine(palette,line);
        }
    }

    private static void assertBlankAttempts(FailingReleaseGraphics graphics) {
        for(var palette:graphics.attemptedPalettes)for(int color=0;color<16;color++) {
            assertEquals(0,palette.red(color));assertEquals(0,palette.green(color));assertEquals(0,palette.blue(color));
        }
    }
    @SuppressWarnings("unchecked")
    private static java.util.Map<String,Integer> paletteCache(GraphicsManager graphics) throws Exception {
        return (java.util.Map<String,Integer>)field(GraphicsManager.class,"paletteTextureMap").get(graphics);
    }
    private static java.lang.reflect.Field field(Class<?> owner,String name) throws Exception {
        var field=owner.getDeclaredField(name);field.setAccessible(true);return field;
    }
    private static void primeInvalidation(GraphicsManager graphics) throws Exception {
        field(GraphicsManager.class,"underwaterPaletteContentKeyValid").setBoolean(graphics,true);
        field(GraphicsManager.class,"scopedUnderwaterDirty").setBoolean(graphics,false);
        field(PatternRenderCommand.class,"stateInitialized").setBoolean(null,true);
        field(PatternRenderCommand.class,"lastAtlasTextureId").setInt(null,37);
    }
    private static void assertInvalidated(GraphicsManager graphics) throws Exception {
        assertFalse(field(GraphicsManager.class,"underwaterPaletteContentKeyValid").getBoolean(graphics));
        assertTrue(field(GraphicsManager.class,"scopedUnderwaterDirty").getBoolean(graphics));
        assertFalse(field(PatternRenderCommand.class,"stateInitialized").getBoolean(null));
        assertEquals(-1,field(PatternRenderCommand.class,"lastAtlasTextureId").getInt(null));
    }

}
