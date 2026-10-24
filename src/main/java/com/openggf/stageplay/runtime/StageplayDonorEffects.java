package com.openggf.stageplay.runtime;

import com.openggf.game.InstaShieldHandle;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DefaultPowerUpSpawner;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerAttachedEffectArt;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.Objects;
import java.util.function.Function;

/**
 * One disposable performer's donor ability factory/art. Pickups retain world semantics;
 * the existing spawner still owns registration, slots and rewind reconstruction.
 */
final class StageplayDonorEffects extends DefaultPowerUpSpawner implements PlayerAttachedEffectArt,
        com.openggf.sprites.playable.PlayerAbilityAudio, AutoCloseable {
    private final ObjectManager objects;
    private final AbstractPlayableSprite owner;
    private final Function<AbstractPlayableSprite, AbstractObjectInstance> factory;
    private final SpriteArtSet art;
    private final PlayerSpriteRenderer renderer;
    private final StageplayDonorAudio audio;
    private final String game;

    StageplayDonorEffects(ObjectManager objects, AbstractPlayableSprite owner,
            Function<AbstractPlayableSprite, AbstractObjectInstance> factory, SpriteArtSet art,
            PlayerSpriteRenderer renderer, StageplayDonorAudio audio, String game) {
        super(objects);
        this.objects = Objects.requireNonNull(objects);
        this.owner = Objects.requireNonNull(owner);
        this.factory = factory;
        this.art = art;
        this.renderer = renderer;
        this.audio = Objects.requireNonNull(audio);
        this.game = Objects.requireNonNull(game);
    }

    @Override public InstaShieldHandle createInstaShield(PlayableEntity player) {
        if (player != owner) throw new IllegalArgumentException("Donor effect belongs to another performer");
        if (factory == null) return super.createInstaShield(player);
        var effect = constructWithServices(() -> factory.apply(owner));
        if (!(effect instanceof InstaShieldHandle handle))
            throw new IllegalStateException("Donor insta-shield factory returned an incompatible effect");
        return handle;
    }

    @Override public SpriteArtSet instaShieldArt() { return art; }
    @Override public PlayerSpriteRenderer instaShieldRenderer() { return renderer; }
    @Override public void playAbilitySound(com.openggf.audio.GameSound sound) { audio.play(game,sound); }
    @Override public void close() { audio.close(); }

    @Override public void registerObject(com.openggf.game.PowerUpObject effect) {
        if (effect instanceof InstaShieldHandle && effect instanceof AbstractObjectInstance object && owner.isCpuControlled()) {
            com.openggf.level.objects.ObjectLifetimeOps.clearPreviousManagerSlot(object);
            objects.addRewindableAuxiliaryDynamicObject(object);
        } else super.registerObject(effect);
    }
}
