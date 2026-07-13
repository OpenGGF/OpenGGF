package com.openggf.mods.code;

import com.openggf.level.objects.ObjectInstance;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Supplier;

/** Internal handoff from a creator registry callback to ObjectManager registration. */
final class ObjectCallbackOwnerLookup
        implements Supplier<String>, Function<ObjectInstance, String> {
    private final ArrayList<PendingOwner> pendingOwners = new ArrayList<>();

    synchronized <T extends ObjectInstance> T remember(String owner, T instance) {
        if (instance == null) return null;
        pendingOwners.removeIf(pending -> pending.instance().get() == null);
        pendingOwners.add(new PendingOwner(new WeakReference<>(instance), owner));
        return instance;
    }

    @Override public String get() {
        return OwnerCallbackScope.current();
    }

    @Override public synchronized String apply(ObjectInstance instance) {
        for (Iterator<PendingOwner> iterator = pendingOwners.iterator(); iterator.hasNext(); ) {
            PendingOwner pending = iterator.next();
            ObjectInstance candidate = pending.instance().get();
            if (candidate == null) {
                iterator.remove();
            } else if (candidate == instance) {
                iterator.remove();
                return pending.owner();
            }
        }
        return null;
    }

    private record PendingOwner(WeakReference<ObjectInstance> instance, String owner) {}
}
