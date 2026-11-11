package com.openggf.game.sonic3k.ending;

import com.openggf.game.*;
import com.openggf.game.presentation.StageCharacterPresentation;
import com.openggf.game.presentation.StageSpriteCompiler;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** Scene-local custom mappings with private palettes; native vehicles and companions keep their rows. */
final class EndingCharacterPresentation {
    private static final Map<Object, Map<EndingSpriteSet.Surface, PlayerSpriteRenderer>> CACHE = new WeakHashMap<>();
    private EndingCharacterPresentation() { }
    static boolean draw(ObjectServices services, EndingSpriteSet.Surface surface, int frame,
            int x, int y, boolean flipX, boolean flipY, boolean high, int bucket) {
        var definition = StageCharacterPresentation.activeDefinition();
        boolean sidekick = surface == EndingSpriteSet.Surface.S3K_TORNADO_PILOT;
        if (!sidekick && (definition == null || definition.presentationSupplier() == null)) return false;
        try {
            var presentation = sidekick ? null : definition.presentationSupplier().load(definition.key().persisted());
            var set = sidekick ? com.openggf.game.presentation.EndingCharacterPresentation.pilot(surface)
                    : presentation == null ? null : presentation.endings().get(surface);
            if (set == null || !set.sprites().frames().containsKey(frame)) return false;
            var renderers = CACHE.computeIfAbsent(services.currentLevel(), ignored -> new HashMap<>());
            var renderer = renderers.get(surface);
            if (renderer == null) {
                renderer = new PlayerSpriteRenderer(StageSpriteCompiler.compile(set.sprites(), null), services.graphicsManager());
                var context = RenderContext.createSidekickContext(GameServices.module().getGameId());
                var palette = new Palette();
                int[] rgb = set.rgb();
                for (int i = 0; i < 16; i++) palette.setColor(i, new Palette.Color(
                        (byte)(rgb[i] >> 16), (byte)(rgb[i] >> 8), (byte)rgb[i]));
                context.setPalette(0, palette);
                renderer.setRenderContext(context);
                renderers.put(surface, renderer);
            }
            if (surface == EndingSpriteSet.Surface.S3K_PLAYER || surface == EndingSpriteSet.Surface.S3K_POWERED_PLAYER
                    || surface == EndingSpriteSet.Surface.S3K_POWERED_POSE) {
                if (services.playerQuery().mainPlayerOrNull() instanceof com.openggf.sprites.playable.AbstractPlayableSprite player) {
                    var source = player.getSpriteRenderer().getRenderContext();
                    if (source != null) renderer.getRenderContext().setPalette(0,
                            source.getPalette(player.getSpriteRenderer().paletteLine()));
                }
            }
            var graphics = services.graphicsManager();
            if (graphics.isGlInitialized()) {
                graphics.cachePaletteTexture(renderer.getRenderContext().getPalette(0),
                        renderer.getRenderContext().getEffectivePaletteLine(0));
                renderer.ensureCached(graphics);
                if (bucket >= 0) graphics.setCurrentSpriteSatBucket(bucket);
                graphics.setCurrentSpriteHighPriority(high);
                renderer.drawFrame(frame, x, y, flipX, flipY);
            }
            return true;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
