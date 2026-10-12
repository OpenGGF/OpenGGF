package com.openggf.level.objects;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Reproduces ObjectManager's constructor context followed by persistent service injection. */
public final class BoundMutatorObjectTestAccess {
    private BoundMutatorObjectTestAccess() { }

    public static <T extends AbstractObjectInstance> T construct(ObjectServices services, Supplier<T> factory) {
        T object = ObjectConstructionContext.construct(services, factory);
        object.setServices(services);
        assertSame(services, object.tryServices());
        assertNotNull(object.services().worldSession());
        assertSame(services.worldSession(), object.services().worldSession());
        return object;
    }
}
