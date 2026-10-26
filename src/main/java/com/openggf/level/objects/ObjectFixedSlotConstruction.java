package com.openggf.level.objects;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Fixed and reserved-slot construction beside native slot-write admission. */
final class ObjectFixedSlotConstruction {
    private ObjectFixedSlotConstruction() { }
    static <T extends ObjectInstance> T create(ObjectServices services,SlotAllocator allocator,
            IntPredicate managed,IntConsumer release,ObjectCallbackRouter callbacks,
            Consumer<ObjectInstance> register,Consumer<ObjectInstance> add,Supplier<T> factory,int slot) {
        if(!managed.test(slot)||!allocator.reserve(slot))return null;
        return ObjectConstructionContext.construct(services,()->{
            T object;
            try{object=factory.get();}catch(RuntimeException|Error failure){release.accept(slot);throw failure;}
            if(object==null){release.accept(slot);return null;}
            if(!(object instanceof AbstractObjectInstance aoi)){
                release.accept(slot);throw new IllegalArgumentException("Fixed-slot dynamic objects must extend AbstractObjectInstance");
            }
            register.accept(object);callbacks.run(aoi,()->aoi.setSlotIndex(slot));add.accept(object);return object;
        });
    }

    /** Reserve before factory side effects, preserving the manager's virtual callback dispatch. */
    static <T extends ObjectInstance> T createWithReservedSlot(ObjectServices services, ObjectCallbackRouter callbacks,
            IntSupplier allocateSlot, IntConsumer releaseSlot, Consumer<ObjectInstance> registerInheritedCallbackOwner,
            Consumer<ObjectInstance> addDynamicObject, Supplier<T> factory) {
        return ObjectConstructionContext.construct(services, () -> {
            int reservedSlot = allocateSlot.getAsInt();
            if (reservedSlot < 0) {
                return null;
            }
            T object;
            try {
                object = factory.get();
            } catch (RuntimeException | Error ex) {
                releaseSlot.accept(reservedSlot);
                throw ex;
            }
            if (object == null) {
                releaseSlot.accept(reservedSlot);
                return null;
            }
            registerInheritedCallbackOwner.accept(object);
            if (object instanceof AbstractObjectInstance aoi
                    && callbacks.call(object, aoi::getSlotIndex) < 0) {
                callbacks.run(object, () -> aoi.setSlotIndex(reservedSlot));
            } else {
                releaseSlot.accept(reservedSlot);
            }
            addDynamicObject.accept(object);
            return object;
        });
    }
}
