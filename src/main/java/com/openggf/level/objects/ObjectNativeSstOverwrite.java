package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/** Registration-only code replacement: no delete, respawn mutation or onUnload. */
final class ObjectNativeSstOverwrite {
    private ObjectNativeSstOverwrite() {
    }

    record Surface(
            ObjectServices services,
            ObjectSlotLayout layout,
            SlotAllocator allocator,
            Map<ObjectSpawn, ObjectInstance> active,
            Map<ObjectInstance, ObjectSpawn> inverse,
            List<ObjectInstance> dynamic,
            ObjectCallbackRouter callbacks,
            DynamicObjectOwnership ownership,
            Map<ObjectInstance, ObjectRefId> identities,
            Set<ObjectInstance> deferred,
            ObjectInstance[] exec,
            IntUnaryOperator execIndex,
            boolean updating,
            int currentExec,
            Consumer<ObjectInstance> add,
            Runnable dirty,
            ObjectNativeSstWord2C word2C) {
    }

    static <T extends AbstractObjectInstance> T create(
            Surface surface, Function<NativeSstState, T> writer) {
        int free = surface.allocator().firstFreeSlot();
        int slot = free >= 0 ? free : surface.layout().lastDynamicSlotExclusive() - 1;
        ObjectInstance old = free >= 0 ? null : occupant(surface, slot);
        NativeSstState residue = old == null
                ? NativeSstState.CLEARED
                : surface.callbacks().call(old, () -> residue(old));
        T next = ObjectConstructionContext.with(surface.services(), slot, () -> writer.apply(residue));
        if (next == null) {
            throw new IllegalStateException("Native partial writer returned no dispatch owner");
        }
        surface.word2C().preserve(slot);
        ObjectSpawn placed = old == null ? null : surface.inverse().remove(old);
        ObjectRefId identity = old == null ? null : surface.identities().remove(old);
        if (old != null) {
            surface.dynamic().remove(old);
            surface.ownership().remove(old);
            surface.deferred().remove(old);
            surface.callbacks().unregister(old);
        }
        if (identity != null) {
            surface.identities().put(next, identity);
        }
        surface.add().accept(next);
        if (placed != null) {
            surface.dynamic().remove(next);
            surface.active().put(placed, next);
            surface.inverse().put(next, placed);
        }
        int exec = surface.execIndex().applyAsInt(slot);
        if (surface.updating() && exec <= surface.currentExec()) {
            surface.exec()[exec] = next;
        }
        // Earlier/current slots wait; add() publishes later slots in the current walk.
        surface.dirty().run();
        return next;
    }

    /** Native fixed-address write; construction succeeds before the old code owner is retired. */
    static <T extends AbstractObjectInstance> T replaceFixed(
            Surface surface, ObjectCollisionResponseList collisions, int slot, Supplier<T> factory) {
        if (slot < surface.layout().lastDynamicSlotExclusive()
                || slot >= surface.layout().lastProcessSlotExclusive()) {
            throw new IllegalArgumentException("Native fixed write requires a processed fixed SST slot: " + slot);
        }
        return replaceValidated(surface, collisions, slot, factory);
    }

    /** Native fixed prefix write, excluding both player SSTs and the allocatable window. */
    static <T extends AbstractObjectInstance> T replacePrefix(
            Surface surface, ObjectCollisionResponseList collisions, int slot, Supplier<T> factory) {
        if (slot < 2 || slot >= surface.layout().firstDynamicSlot()) {
            throw new IllegalArgumentException("Native prefix write requires a non-player prefix SST slot: " + slot);
        }
        return replaceValidated(surface, collisions, slot, factory);
    }

    private static <T extends AbstractObjectInstance> T replaceValidated(
            Surface surface, ObjectCollisionResponseList collisions, int slot, Supplier<T> factory) {
        java.util.Objects.requireNonNull(factory, "factory");
        AbstractObjectInstance old = registeredOwner(surface, slot);
        T next = ObjectConstructionContext.with(surface.services(), slot, factory);
        if (next == null) {
            throw new IllegalStateException("Native fixed writer returned no dispatch owner");
        }
        if (surface.identities().containsKey(next)) {
            throw new IllegalArgumentException("Native fixed writer must construct a new dispatch owner");
        }
        surface.word2C().preserve(slot);
        next.setSlotIndex(slot);
        ObjectRefId identity = old == null ? null : surface.identities().remove(old);
        if (old != null) {
            ObjectSpawn placed = surface.inverse().remove(old);
            if (placed != null) {
                surface.active().remove(placed);
            }
            surface.dynamic().remove(old);
            surface.ownership().remove(old);
            surface.deferred().remove(old);
            surface.callbacks().unregister(old);
            for (int i = 0; i < surface.exec().length; i++) {
                if (surface.exec()[i] == old) {
                    surface.exec()[i] = null;
                }
            }
        }
        if (identity != null) {
            surface.identities().put(next, identity);
        }
        // Preassigned fixed SSTs bypass allocation. Existing registration publishes
        // ahead of the cursor; at/before it waits, without invoking the new routine.
        surface.add().accept(next);
        int exec = surface.execIndex().applyAsInt(slot);
        if (surface.updating() && exec <= surface.currentExec()) {
            surface.exec()[exec] = next;
        }
        // The canonical list contains RAM addresses. Rebind its Java aliases,
        // retaining duplicates/order/capacity and the already-captured identity.
        // Also update restored bindings so a later late-bind cannot resurrect old.
        if (identity != null) {
            collisions.bindRestoredObject(identity, next);
        }
        collisions.refreshNativePointerOwners();
        surface.dirty().run();
        return next;
    }

    private static AbstractObjectInstance registeredOwner(Surface surface, int slot) {
        AbstractObjectInstance owner = null;
        for (var group : List.of(surface.active().values(), surface.dynamic())) {
            for (ObjectInstance instance : group) {
                if (instance instanceof AbstractObjectInstance object
                        && surface.callbacks().call(object, object::getSlotIndex) == slot) {
                    if (owner != null && owner != object) {
                        throw new IllegalStateException("Multiple native SST dispatch owners: " + slot);
                    }
                    owner = object;
                }
            }
        }
        return owner;
    }

    static AbstractObjectInstance currentOwner(
            java.util.Collection<ObjectInstance> placed,
            List<ObjectInstance> dynamic,
            ObjectCallbackRouter callbacks,
            int slot) {
        AbstractObjectInstance owner = null;
        for (var group : List.of(placed, dynamic)) {
            for (var instance : group) {
                if (instance instanceof AbstractObjectInstance object
                        && callbacks.call(instance, object::getSlotIndex) == slot
                        && !callbacks.call(instance, object::isDestroyed)) {
                    if (owner != null && owner != object) {
                        throw new IllegalStateException("Multiple native SST dispatch owners: " + slot);
                    }
                    owner = object;
                }
            }
        }
        return owner;
    }

    private static ObjectInstance occupant(Surface surface, int slot) {
        var owner = currentOwner(surface.active().values(), surface.dynamic(), surface.callbacks(), slot);
        if (owner == null) {
            throw new IllegalStateException("Occupied native SST has no live field owner: " + slot);
        }
        return owner;
    }

    private static NativeSstState residue(ObjectInstance old) {
        if (!(old instanceof NativeSstStateSource source)) {
            throw new IllegalStateException("Unchecked native overwrite needs a live projection: " + old.getClass().getName());
        }
        return java.util.Objects.requireNonNull(source.nativeSstState(), "native SST projection");
    }
}
