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
    /** Atlas slots, in a fixed order: several of these surfaces are on screen in the same frame. */
    private static final List<EndingSpriteSet.Surface> BANKS = List.of(
            EndingSpriteSet.Surface.S3K_TORNADO_PILOT, EndingSpriteSet.Surface.S3K_NORMAL_POSE,
            EndingSpriteSet.Surface.S3K_POWERED_POSE, EndingSpriteSet.Surface.S3K_PASSENGER,
            EndingSpriteSet.Surface.S3K_EYECATCH, EndingSpriteSet.Surface.S3K_PLAYER,
            EndingSpriteSet.Surface.S3K_POWERED_PLAYER, EndingSpriteSet.Surface.S3K_MINI_TORNADO_PILOT,
            EndingSpriteSet.Surface.S3K_SOLO_EYECATCH);
    private static final int BANK_SLOT = 0x1000;
    /**
     * Each compiled set is a DPLC bank that holds only its current frame. Sets that share an
     * address overwrite one another's tiles whenever two of them are drawn in one picture (the
     * rider and the cockpit, the float sprite and the passenger), so every surface owns a slot.
     */
    private static int bankBase(EndingSpriteSet.Surface surface, EndingSpriteSet set) {
        int slot = BANKS.indexOf(surface);
        if (slot < 0) throw new IllegalArgumentException("Not an S3K ending draw surface: " + surface);
        int largest = set.sprites().frames().values().stream().mapToInt(frame ->
                ((frame.image().width() + 7) / 8) * ((frame.image().height() + 7) / 8)).max().orElse(0);
        if (largest > BANK_SLOT) throw new IllegalArgumentException("Ending frame exceeds its pattern slot: " + surface);
        return com.openggf.graphics.PatternAtlasRange.CHARACTER_STAGE_PRESENTATION.base() + slot * BANK_SLOT;
    }
    /**
     * Whose art a draw key shows. The two cockpits belong to the partner. {@code S3K_SOLO_EYECATCH}
     * as a draw key is the final card's other hero seat: the first sidekick's own eyecatch set,
     * or the lead's companion set on a solo team. Every other surface is the lead's own.
     */
    static EndingSpriteSet resolve(EndingSpriteSet.Surface surface) throws IOException {
        return switch (surface) {
            case S3K_TORNADO_PILOT, S3K_MINI_TORNADO_PILOT ->
                    com.openggf.game.presentation.EndingCharacterPresentation.pilot(surface);
            case S3K_SOLO_EYECATCH -> com.openggf.game.presentation.EndingCharacterPresentation.companion(
                    EndingSpriteSet.Surface.S3K_EYECATCH, EndingSpriteSet.Surface.S3K_SOLO_EYECATCH);
            default -> com.openggf.game.presentation.EndingCharacterPresentation.get(surface);
        };
    }
    /** Whether the resolved set for {@code surface} authors {@code frame}; a frame with no surface is native. */
    static boolean supplies(EndingSpriteSet.Surface surface, int frame) {
        if (surface == null) return false;
        try {
            var set = resolve(surface);
            return set != null && set.sprites().frames().containsKey(frame);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    static boolean draw(ObjectServices services, EndingSpriteSet.Surface surface, int frame,
            int x, int y, boolean flipX, boolean flipY, boolean high, int bucket) {
        try {
            var set = resolve(surface);
            if (set == null || !set.sprites().frames().containsKey(frame)) return false;
            var renderers = CACHE.computeIfAbsent(services.currentLevel(), ignored -> new HashMap<>());
            var renderer = renderers.get(surface);
            if (renderer == null) {
                renderer = new PlayerSpriteRenderer(StageSpriteCompiler.compile(set.sprites(), null, bankBase(surface, set)),
                        services.graphicsManager());
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
