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
        // loc_1E5F6 (sonic3k.asm:42215-42222) copies the score selector.
        // loc_2C940 (sonic3k.asm:61097-61131) allocates the popup on the animal's own dispatch.
        return new DestructionConfig(Sonic3kSfx.BREAK.id,
                (spawn, services) -> AnimalObjectInstance.deferredArtVariant(
                        spawn, services, Sonic3kPointsObjectInstance::new),
                false, null, null, false);
    }
}
