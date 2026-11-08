package com.openggf.game.presentation;

import com.openggf.game.*;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.RenderContext;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import java.io.IOException;

/** Character-owned stage presentation; native stage palettes remain untouched. */
public final class StageCharacterPresentation {
    private StageCharacterPresentation() { }

    public static CharacterDefinition activeDefinition() {
        var world = SessionManager.getCurrentWorldSession();
        if (world == null) return null;
        String code = ActiveGameplayTeamResolver.resolveMainCharacterCode(GameServices.configuration());
        if (code == null) return null;
        var definition = world.getPlayableCharacterRegistry().find(CharacterKey.parsePersisted(code)).orElse(null);
        return definition == null || definition.key().isBuiltin() ? null : definition;
    }

    public static void bindPalette(PlayerSpriteRenderer renderer, CharacterDefinition definition) throws IOException {
        if (definition == null || definition.paletteSupplier() == null) return;
        var palette = definition.paletteSupplier().load(definition.key().persisted());
        if (palette == null) return;
        var context = RenderContext.createSidekickContext(GameServices.module().getGameId());
        palette = palette.deepCopy();
        context.setPalette(renderer.paletteLine(), palette);
        renderer.setRenderContext(context);
        GameServices.graphics().cachePaletteTexture(palette, context.getEffectivePaletteLine(renderer.paletteLine()));
    }

    public static StageSpriteSet stage(StageSpriteSet.Surface surface) throws IOException {
        var definition = activeDefinition();
        if (definition == null || definition.presentationSupplier() == null) return null;
        var presentation = definition.presentationSupplier().load(definition.key().persisted());
        return presentation == null ? null : presentation.stages().get(surface);
    }

    public static PlayerSpriteRenderer renderer(StageSpriteSet.Surface surface) throws IOException {
        var set = stage(surface);
        if (set == null) return null;
        var renderer = new PlayerSpriteRenderer(StageSpriteCompiler.compile(set, null));
        bindPalette(renderer, activeDefinition());
        return renderer;
    }

    public static SpriteArtSet playable(SpriteArtSet nativeArt) throws IOException {
        var definition = activeDefinition();
        if (definition == null) return nativeArt;
        var art = definition.artSupplier().load(definition.key().persisted());
        if (com.openggf.level.objects.PlayableSheetAnimationBinding.requiresBinding(art)) {
            art = com.openggf.level.objects.PlayableSheetAnimationBinding.bind(art,
                    GameServices.module().getDonorCapabilities(),
                    (com.openggf.sprites.animation.ScriptedVelocityAnimationProfile) nativeArt.animationProfile());
        }
        return art;
    }
}
