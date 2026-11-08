package com.openggf.game.presentation;

import com.openggf.game.*;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import java.io.IOException;

/** Ending art is isolated from shared native palette lines (vehicles, animals and companions). */
public final class EndingCharacterPresentation {
    private EndingCharacterPresentation() { }
    public static EndingSpriteSet get(EndingSpriteSet.Surface surface) throws IOException {
        var definition = StageCharacterPresentation.activeDefinition();
        if (definition == null || definition.presentationSupplier() == null) return null;
        var presentation = definition.presentationSupplier().load(definition.key().persisted());
        return presentation == null ? null : presentation.endings().get(surface);
    }
    public static PlayerSpriteRenderer renderer(EndingSpriteSet set) {
        if (set == null) return null;
        var renderer = new PlayerSpriteRenderer(StageSpriteCompiler.compile(set.sprites(), null));
        var context = RenderContext.createSidekickContext(GameServices.module().getGameId());
        var palette = new Palette();
        int[] rgb = set.rgb();
        for (int i=0;i<16;i++) palette.setColor(i, new Palette.Color(
                (byte)(rgb[i] >> 16), (byte)(rgb[i] >> 8), (byte)rgb[i]));
        context.setPalette(0, palette);
        renderer.setRenderContext(context);
        GameServices.graphics().cachePaletteTexture(palette, context.getEffectivePaletteLine(0));
        return renderer;
    }
}
