package com.openggf.stageplay.runtime;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.RenderContext;
import com.openggf.level.LevelManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayableRuntimeContext;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.stageplay.StageplayDocument.Actor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ROM-backed native donor roster over one existing Stageplay world. Each body and
 * accessory has a separate virtual pattern bank and palette owner. Origin: the
 * Stageplay complete-cast task, 2026-10-22.
 */
final class StageplayNativeCast {
    private final SceneRomLibrary library;
    private final GameplayModeContext world;
    private final StageplayDonorAudio audio;
    private int nextPattern = LevelManager.SIDEKICK_PATTERN_BASE;

    private StageplayNativeCast(SceneRomLibrary library, GameplayModeContext world) {
        this.library = library;
        this.world = world;
        audio = new StageplayDonorAudio(library, EngineServices.current().audio(),
                world.getWorldSession().getGameModule().getGameId().code());
    }

    static List<AbstractPlayableSprite> replaceLoadedTeam(SceneRomLibrary library, GameplayModeContext world,
            List<Actor> actors) throws IOException {
        if (actors.isEmpty()) return List.of();
        var owner = new StageplayNativeCast(library, world);
        var prepared = new ArrayList<Prepared>();
        // Prepare everything before publishing the replacement roster. Failure closes the
        // disposable world at its caller; no half-roster is admitted to a native tick.
        try {
            for (int i = 0; i < actors.size(); i++) prepared.add(owner.create(actors.get(i), i));
            var sprites = world.getSpriteManager();
            for (var sprite : List.copyOf(sprites.getAllSprites())) {
                if (sprite instanceof AbstractPlayableSprite playable) retirePlayerEffects(world, playable);
                sprites.removeSprite(sprite.getCode());
            }
            for (int i = 0; i < prepared.size(); i++) {
                var entry = prepared.get(i);
                var sprite = entry.sprite();
                sprite.setCpuControlled(i != 0);
                sprite.setCpuController(null);
                sprites.addSprite(sprite, actors.get(i).character());
                // Persistent effects are admitted only after the old team is retired and
                // this exact body has its final primary/follower role and registry identity.
                sprite.setPowerUpSpawner(entry.effects());
            }
            RenderContext.uploadDonorPalettes(EngineServices.current().graphics());
            return prepared.stream().map(Prepared::sprite).toList();
        } catch (IOException | RuntimeException | Error failure) {
            try { owner.audio.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void retirePlayerEffects(GameplayModeContext world, AbstractPlayableSprite player) {
        var effects = new com.openggf.game.PowerUpObject[] {player.getShieldObject(), player.getInvincibilityObject(),
                player.getInstaShieldObject()};
        player.clearPowerUps();
        for (var effect : effects) if (effect != null) {
            effect.destroy();
            if (effect instanceof com.openggf.level.objects.ObjectInstance object)
                world.getLevelManager().getObjectManager().removeDynamicObject(object);
        }
        player.setInstaShieldObject(null);
        player.setPowerUpSpawner(null);
    }

    static void releaseLoadedTeam(GameplayModeContext world) {
        for (var sprite : world.getSpriteManager().getAllSprites())
            if (sprite instanceof AbstractPlayableSprite player
                    && player.getPowerUpSpawner() instanceof StageplayDonorEffects effects) effects.close();
    }

    private Prepared create(Actor actor, int ordinal) throws IOException {
        GameModule module = library.module(actor.gameId());
        var donor = module.getCrossGameDonorProvider();
        if (donor == null) throw new IllegalArgumentException("Native donor has no ROM resource provider: " + actor.gameId());
        var reader = RomByteReader.fromRom(library.sourceRom(actor.gameId()));
        var key = actor.keys().getFirst();
        // Codes are runtime-local; portable actor IDs remain in the Stageplay document.
        String code = ordinal == 0 ? actor.character() : actor.character() + "_p" + (ordinal + 1);
        var sprite = PlayableRuntimeContext.constructShared(module, world, actor.character(), code,
                (int) Math.round(key.x()), (int) Math.round(key.y()));
        audio.validate(actor.gameId(),sprite);
        var art = library.characterArt(actor.gameId(), actor.character());
        var palette = donor.loadCharacterPalette(reader, actor.character());
        if (palette == null) throw new IllegalArgumentException("Missing native donor palette: " + actor.id());
        var context = RenderContext.createSidekickContext(module.getGameId());
        context.setPalette(art.paletteIndex(), palette);
        var underwater = donor.loadUnderwaterCharacterPalette(reader, actor.character());
        if (underwater != null) context.setUnderwaterPalette(art.paletteIndex(), underwater);
        var renderer = renderer(art, context);
        sprite.setSpriteRenderer(renderer);
        sprite.setAnimationProfile(art.animationProfile());
        sprite.setAnimationSet(art.animationSet());
        sprite.setAnimationFrameCount(art.mappingFrames().size());
        sprite.getAnimationManager().setDynamicArtDecisionOwner(null);
        if (sprite instanceof Tails) {
            boolean separate = donor.hasSeparateTailsTailArt();
            var tails = separate ? donor.loadTailsTailArt(reader) : art;
            if (tails == null || tails.isEmpty()) throw new IllegalArgumentException("Missing native tail art: " + actor.id());
            sprite.setTailsTailsController(new TailsTailsController(sprite, renderer(tails, context), separate));
        }
        var dustProvider = donor.createSpindashDustArtProvider(reader);
        if (dustProvider != null) {
            var dust = dustProvider.loadSpindashDustArt(actor.character());
            if (dust != null && !dust.isEmpty())
                sprite.setSpindashDustController(new SpindashDustController(sprite, renderer(dust, context)));
        }
        var objects = world.getLevelManager().getObjectManager();
        var factory = module.getInstaShieldFactory();
        StageplayDonorEffects effects;
        if (module.getRules().playerCapability().instaShieldEnabled()
                && sprite.getSecondaryAbility() == com.openggf.sprites.playable.SecondaryAbility.INSTA_SHIELD) {
            if (factory == null) throw new IllegalArgumentException("Native performer has no ability effect factory: " + actor.id());
            var shieldArt = donor.loadInstaShieldArt(reader);
            if (shieldArt == null || shieldArt.isEmpty())
                throw new IllegalArgumentException("Missing native ability effect art: " + actor.id());
            effects = new StageplayDonorEffects(objects, sprite, factory, shieldArt,
                    renderer(shieldArt, context),audio,actor.gameId());
        } else effects = new StageplayDonorEffects(objects,sprite,null,null,null,audio,actor.gameId());
        return new Prepared(sprite,effects);
    }

    private record Prepared(AbstractPlayableSprite sprite, StageplayDonorEffects effects) { }

    private PlayerSpriteRenderer renderer(SpriteArtSet source, RenderContext context) {
        if (source == null || source.bankSize() <= 0 || source.mappingFrames().isEmpty())
            throw new IllegalArgumentException("Missing native performer art");
        int base = nextPattern;
        nextPattern = Math.addExact(nextPattern, source.bankSize());
        if (nextPattern > PatternAtlasRange.SIDEKICK_BANKS.endExclusive())
            throw new IllegalArgumentException("Stageplay performer art exceeds the reserved native pattern banks");
        var shifted = new SpriteArtSet(source.artTiles(), source.mappingFrames(), source.dplcFrames(),
                source.paletteIndex(), base, source.frameDelay(), source.bankSize(), source.animationProfile(), source.animationSet());
        var renderer = new PlayerSpriteRenderer(shifted, EngineServices.current().graphics());
        renderer.setRenderContext(context);
        renderer.ensureCached(EngineServices.current().graphics());
        return renderer;
    }
}
