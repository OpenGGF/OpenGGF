package com.openggf;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.net.identity.PlayerAppearance;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The hosted run's own character draws through private rows only while a look is set. */
@Isolated
class TestLocalPlayerLook {
    private static final PlayerAppearance RED = new PlayerAppearance(0x00E,
            List.of(new PlayerAppearance.Entry(2, 0x00E)));

    @Test
    void lookPointsOnlyThePlayersRenderersAtPrivateRowsAndRestoresThem() {
        RenderContext.reset();
        GraphicsManager graphics = new GraphicsManager();
        graphics.initHeadless();
        try {
            Palette stock = new Palette();
            stock.getColor(2).fromSegaFormat(0x0E66);
            graphics.cachePaletteTexture(stock, 0);
            PlayerSpriteRenderer body = new PlayerSpriteRenderer(SpriteArtSet.EMPTY, graphics);
            AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
            when(player.getSpriteRenderer()).thenReturn(body);
            LocalPlayerLook look = new LocalPlayerLook();
            int[] stockReads = {0};
            java.util.function.Function<String, Palette> stockLines = code -> {
                stockReads[0]++;
                assertEquals("sonic", code);
                return stock;
            };

            look.refresh(player, "sonic", graphics, stockLines);
            assertNull(body.getRenderContext(), "the stock look keeps the shared rows");

            look.request(RED);
            look.refresh(player, "sonic", graphics, stockLines);
            RenderContext context = body.getRenderContext();
            assertNotNull(context);
            assertTrue(context.getPaletteLineBase() >= 4, "private rows, not the shared line 0");
            look.refresh(player, "sonic", graphics, stockLines);
            assertSame(context, body.getRenderContext(), "an unchanged look keeps its rows");
            assertEquals(1, stockReads[0], "the stock line is read once per acquisition");

            // A reload builds a new renderer: the look follows it.
            PlayerSpriteRenderer reloaded = new PlayerSpriteRenderer(SpriteArtSet.EMPTY, graphics);
            when(player.getSpriteRenderer()).thenReturn(reloaded);
            look.refresh(player, "sonic", graphics, stockLines);
            assertNotNull(reloaded.getRenderContext());
            assertNull(body.getRenderContext(), "the old renderer gets its own context back");

            look.request(PlayerAppearance.STOCK);
            look.refresh(player, "sonic", graphics, stockLines);
            assertNull(reloaded.getRenderContext());
        } finally {
            graphics.cleanup();
            RenderContext.reset();
        }
    }

    @Test
    void anExistingContextIsTheSourceAndIsRestored() {
        RenderContext.reset();
        GraphicsManager graphics = new GraphicsManager();
        graphics.initHeadless();
        try {
            RenderContext donor = RenderContext.createSidekickContext(null);
            Palette donorLine = new Palette();
            donorLine.getColor(2).fromSegaFormat(0x0E66);
            donor.setPalette(0, donorLine);
            graphics.cachePaletteTexture(donorLine, donor.getPaletteLineBase());
            PlayerSpriteRenderer body = new PlayerSpriteRenderer(SpriteArtSet.EMPTY, graphics);
            body.setRenderContext(donor);
            AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
            when(player.getSpriteRenderer()).thenReturn(body);
            LocalPlayerLook look = new LocalPlayerLook();
            look.request(RED);
            look.refresh(player, "sonic", graphics, code -> fail("a context supplies its own stock line"));
            assertNotSame(donor, body.getRenderContext());
            assertNotEquals(donor.getPaletteLineBase(), body.getRenderContext().getPaletteLineBase());
            look.clear();
            assertSame(donor, body.getRenderContext());
        } finally {
            graphics.cleanup();
            RenderContext.reset();
        }
    }
}
