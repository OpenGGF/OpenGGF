package com.openggf.level.objects;

import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PowerUpObject;
import com.openggf.game.PowerUpSpawner;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Internal reconstruction of an already-captured native Obj35 owner graph. */
public final class PlayerBoundPowerUpRestoreAccess {
    private PlayerBoundPowerUpRestoreAccess() { }

    /** Rebuilds visual ownership, publishing each cached-field write before its existing callbacks. */
    public static void refreshInvincibilityStarsAfterRewindRestore(
            ObjectManager manager, Supplier<PowerUpSpawner> spawner, PlayableEntity owner, int invincibleFrames,
            boolean preserveExisting, Supplier<PowerUpObject> currentStars, Supplier<PowerUpObject> shield,
            Consumer<PowerUpObject> publishStars) {
        // Callbacks can rebind these private player fields. Reread them where
        // the original player method did, rather than freezing entry values.
        if (invincibleFrames <= 0 && preserveExisting) {
            // Obj35_Layer0Update, FixBugs=0 (s2.asm:42494-42503), reads
            // secondary invincibility, not the cleared timer. Never mint a graph.
            PowerUpObject liveStars = null;
            if (manager != null) {
                for (ObjectInstance object : manager.getActiveObjects()) {
                    if (object.getClass() == InvincibilityStarsObjectInstance.class
                            && object instanceof PowerUpObject candidate && candidate.boundPlayer() == owner
                            && !ObjectCallbackDispatch.call(manager, object, object::isDestroyed)) {
                        liveStars = candidate;
                        break;
                    }
                }
                if (liveStars == null) liveStars = restoreCapturedInvincibilityStars(manager, spawner.get(), owner);
            }
            publishStars.accept(liveStars);
            if (liveStars != null) liveStars.refreshArtAfterRewindRestore();
            if (shield.get() != null) shield.get().setVisible(liveStars == null);
            return;
        }
        if (invincibleFrames <= 0) {
            if (currentStars.get() != null) {
                currentStars.get().destroy();
                publishStars.accept(null);
            }
            if (shield.get() != null) shield.get().setVisible(true);
            return;
        }
        PowerUpObject liveStars = resolveLiveInvincibilityObjectAfterRewindRestore(manager, currentStars);
        if (liveStars != null) {
            publishStars.accept(liveStars);
            currentStars.get().refreshArtAfterRewindRestore();
        } else {
            if (currentStars.get() != null && !currentStars.get().isDestroyed()) currentStars.get().destroy();
            publishStars.accept(null);
            if (spawner.get() != null) {
                publishStars.accept(spawner.get().spawnInvincibilityStars(owner));
                if (currentStars.get() != null) currentStars.get().refreshArtAfterRewindRestore();
            }
        }
        if (shield.get() != null) shield.get().setVisible(false);
    }

    private static PowerUpObject resolveLiveInvincibilityObjectAfterRewindRestore(
            ObjectManager manager, Supplier<PowerUpObject> currentStars) {
        if (currentStars.get() != null
                && (currentStars.get() instanceof ObjectInstance instance && manager != null
                        ? !ObjectCallbackDispatch.call(manager, instance, instance::isDestroyed)
                        : !currentStars.get().isDestroyed())
                && (manager == null || currentStars.get() instanceof ObjectInstance instance
                        && manager.getActiveObjects().contains(instance))) {
            return currentStars.get();
        }
        return null;
    }

    public static PowerUpObject restoreCapturedInvincibilityStars(
            ObjectManager manager, PowerUpSpawner spawner, PlayableEntity owner) {
        if (manager == null || owner == null || spawner == null
                || spawner.getClass() != DefaultPowerUpSpawner.class
                || !usesStockStarsFactory(manager.services().gameModule())) {
            return null;
        }
        var captured = manager.consumePendingPlayerBoundEntry(InvincibilityStarsObjectInstance.class,
                entry -> entry.playerOwner() == owner
                        && entry.className().equals(InvincibilityStarsObjectInstance.class.getName()));
        if (captured == null) return null;
        // Never substitute a foreign live SST occupant with the captured graph.
        for (var object : manager.getActiveObjects()) {
            if (object instanceof AbstractObjectInstance occupant
                    && occupant.getSlotIndex() == captured.slotIndex()
                    && !ObjectCallbackDispatch.call(manager, object, object::isDestroyed)) {
                manager.enqueuePendingPlayerBoundEntry(InvincibilityStarsObjectInstance.class, captured);
                return null;
            }
        }
        var before = Collections.newSetFromMap(new IdentityHashMap<ObjectInstance, Boolean>());
        before.addAll(manager.getActiveObjects());
        manager.enqueuePendingPlayerBoundEntry(InvincibilityStarsObjectInstance.class, captured);
        try {
            // The existing trusted path consumes this same owner/class entry,
            // retaining callback owner, identity, slot and captured scalar state.
            PowerUpObject restored = spawner.spawnInvincibilityStars(owner);
            if (!(restored instanceof InvincibilityStarsObjectInstance stars)
                    || stars.getClass() != InvincibilityStarsObjectInstance.class
                    || stars.boundPlayer() != owner || stars.getSlotIndex() != captured.slotIndex()
                    || !manager.getActiveObjects().contains(stars)) {
                throw new IllegalStateException("captured native invincibility owner was not restored");
            }
            return restored;
        } catch (RuntimeException | Error failure) {
            // Construction/registration can fail after consuming the entry.
            // Remove only this attempt's native owner and retain the original
            // pending entry exactly once; callers must not fallback-spawn.
            manager.consumePendingPlayerBoundEntry(InvincibilityStarsObjectInstance.class, entry -> entry == captured);
            manager.enqueuePendingPlayerBoundEntry(InvincibilityStarsObjectInstance.class, captured);
            for (var object : new java.util.ArrayList<>(manager.getActiveObjects())) {
                if (!before.contains(object) && object.getClass() == InvincibilityStarsObjectInstance.class
                        && object instanceof InvincibilityStarsObjectInstance stars
                        && stars.boundPlayer() == owner) {
                    manager.removeDynamicObject(object);
                }
            }
            throw failure;
        }
    }

    private static boolean usesStockStarsFactory(GameModule module) {
        if (module == null) return true; // DefaultPowerUpSpawner's stock fallback.
        try {
            // The interface default is exactly InvincibilityStarsObjectInstance::new.
            // Reject S3K/custom overrides BEFORE invoking any factory or consuming
            // an entry; a factory probe could itself have creator side effects.
            return module.getClass().getMethod("getInvincibilityStarsFactory")
                    .getDeclaringClass() == GameModule.class;
        } catch (ReflectiveOperationException unsupported) {
            return false;
        }
    }
}
