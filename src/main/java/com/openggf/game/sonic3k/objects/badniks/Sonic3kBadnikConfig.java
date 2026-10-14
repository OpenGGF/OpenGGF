package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.ModApi;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;

/** Native S3K badnik destruction recipe shared by native and creator-owned enemies. */
@ModApi
public final class Sonic3kBadnikConfig {
    private Sonic3kBadnikConfig() { }

    /** Shipped Touch_EnemyNormal recipe, including the native animal and points child order. */
    public static DestructionConfig destruction() {
        return new DestructionConfig(Sonic3kSfx.BREAK.id,
                (spawn, services) -> AnimalObjectInstance.deferredArtVariant(spawn, services, null), false,
                (spawn, services, points) -> new Sonic3kPointsObjectInstance(spawn, services, points), null, false);
    }
}
