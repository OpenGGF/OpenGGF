package com.openggf.game.mutators;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectInstance;

/** World-owned semantic object hooks; ordinary native bookkeeping remains with its owners. */
public interface NativeObjectPolicyPort {
    boolean permitsRoutine(ObjectInstance object, PlayableEntity player);
    boolean permitsContact(ObjectInstance object, PlayableEntity player);
    void afterRoutine(ObjectInstance object, PlayableEntity player);
    void ringAcquired(PlayableEntity player);
    void projectileImpact(ObjectInstance projectile, PlayableEntity player);
    boolean playerProjectileStep(ObjectInstance projectile, PlayableEntity player);
    default void childSpawned(ObjectInstance parent,ObjectInstance child) { }
    default void replacementSpawned(int transferredSlot,ObjectInstance replacement) { }
    void badnikDefeated(ObjectInstance object, PlayableEntity player);
}
