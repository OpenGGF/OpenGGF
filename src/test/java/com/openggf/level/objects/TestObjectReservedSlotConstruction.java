package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Reserved-slot construction order and rollback without ROM, rendering, or a live object pass. */
class TestObjectReservedSlotConstruction {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(0, 0, 0, 0, 0, false, 0);

    @AfterEach void clearConstructionContext() {
        AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();
        AbstractObjectInstance.PRE_ALLOCATED_SLOT.remove();
    }

    @Test void ownerRegistrationPrecedesVirtualSlotAccessAndAddition() {
        ObjectServices services = new TestObjectServices();
        List<String> events = new ArrayList<>();
        AtomicReference<String> activeOwner = new AtomicReference<>();
        ObjectCallbackBoundary boundary = new ObjectCallbackBoundary() {
            @Override public <T> T call(String owner, Supplier<T> callback) {
                events.add("enter:" + owner);
                String previous = activeOwner.getAndSet(owner);
                try { return callback.get(); }
                finally { activeOwner.set(previous); events.add("exit:" + owner); }
            }
        };
        ObjectCallbackRouter callbacks = new ObjectCallbackRouter(new CallbackRegistry(boundary));
        ObservedObject object = ObjectFixedSlotConstruction.createWithReservedSlot(services, callbacks,
                () -> { assertContext(services); events.add("allocate"); return 41; },
                slot -> fail("assigned reservation must not be released"),
                instance -> {
                    assertContext(services);
                    events.add("register");
                    callbacks.registerOwnerIfAbsent(instance, "creator");
                },
                instance -> {
                    assertContext(services);
                    events.add("add");
                    assertEquals(41, ((ObservedObject) instance).rawSlot());
                },
                () -> {
                    assertContext(services);
                    events.add("factory");
                    return new ObservedObject(events, () -> assertEquals("creator", activeOwner.get()));
                });

        assertSame(services, object.constructorServices);
        assertEquals(41, object.rawSlot());
        assertEquals(List.of("allocate", "factory", "register", "enter:creator", "get", "exit:creator",
                "enter:creator", "set:41", "exit:creator", "add"), events);
        assertNull(AbstractObjectInstance.CONSTRUCTION_CONTEXT.get());
        assertNull(AbstractObjectInstance.PRE_ALLOCATED_SLOT.get());
    }

    @Test void factoryThrowAndNullReleaseExactlyOnceAndRestoreOuterContext() {
        ObjectServices outer = new TestObjectServices();
        ObjectServices inner = new TestObjectServices();
        ObjectCallbackRouter callbacks = new ObjectCallbackRouter(null);
        for (Throwable failure : new Throwable[]{null, new IllegalStateException("factory"), new AssertionError("factory")}) {
            List<String> events = new ArrayList<>();
            ObjectConstructionContext.with(outer, 19, () -> {
                Supplier<ObjectInstance> create = () -> ObjectFixedSlotConstruction.createWithReservedSlot(inner, callbacks,
                        () -> { assertContext(inner); events.add("allocate"); return 42; },
                        slot -> { assertContext(inner); events.add("release:" + slot); },
                        instance -> fail("failed construction cannot register an owner"),
                        instance -> fail("failed construction cannot add an object"),
                        () -> {
                            assertContext(inner);
                            events.add("factory");
                            if (failure instanceof RuntimeException runtime) throw runtime;
                            if (failure instanceof Error error) throw error;
                            return null;
                        });
                if (failure == null) assertNull(create.get());
                else assertSame(failure, assertThrows(failure.getClass(), create::get));
                assertSame(outer, AbstractObjectInstance.CONSTRUCTION_CONTEXT.get());
                assertEquals(19, AbstractObjectInstance.PRE_ALLOCATED_SLOT.get());
            });
            assertEquals(List.of("allocate", "factory", "release:42"), events);
            assertNull(AbstractObjectInstance.CONSTRUCTION_CONTEXT.get());
            assertNull(AbstractObjectInstance.PRE_ALLOCATED_SLOT.get());
        }
    }

    @Test void preSlottedFactoryReleasesOnlyItsUnusedReservation() {
        ObjectServices services = new TestObjectServices();
        List<String> events = new ArrayList<>();
        ObjectCallbackRouter callbacks = new ObjectCallbackRouter(null);
        ObservedObject object = ObjectFixedSlotConstruction.createWithReservedSlot(services, callbacks,
                () -> { assertContext(services); events.add("allocate"); return 42; },
                slot -> { assertContext(services); events.add("release:" + slot); },
                instance -> events.add("register"), instance -> events.add("add"),
                () -> {
                    assertContext(services);
                    events.add("factory");
                    return ObjectConstructionContext.with(services, 73, () -> new ObservedObject(events, () -> { }));
                });
        assertEquals(73, object.rawSlot(), "the factory's existing slot must survive");
        assertEquals(List.of("allocate", "factory", "register", "get", "release:42", "add"), events);
    }

    @Test void exhaustedAllocationSkipsFactoryAndCallbacks() {
        ObjectServices services = new TestObjectServices();
        ObjectInstance result = ObjectFixedSlotConstruction.createWithReservedSlot(services, new ObjectCallbackRouter(null),
                () -> { assertContext(services); return -1; },
                slot -> fail("no reservation exists to release"),
                instance -> fail("no object exists to register"), instance -> fail("no object exists to add"),
                () -> fail("exhausted allocation must not call the factory"));
        assertNull(result);
        assertNull(AbstractObjectInstance.CONSTRUCTION_CONTEXT.get());
    }

    @Test void managerDelegateRetainsConstructionContextAndVirtualAddDispatch() {
        ObjectServices services = new TestObjectServices();
        List<String> events = new ArrayList<>();
        AtomicReference<ObjectInstance> added = new AtomicReference<>();
        ObjectManager manager = new ObjectManager(List.of(), null, -1, null, null, null, null, services) {
            @Override public void addDynamicObject(ObjectInstance instance) {
                assertContext(services);
                events.add("virtual-add");
                assertTrue(((ObservedObject) instance).rawSlot() >= 0);
                added.set(instance);
            }
        };
        ObservedObject object = manager.createDynamicObjectWithReservedSlot(() -> {
            assertContext(services);
            events.add("factory");
            return new ObservedObject(events, () -> { });
        });
        assertSame(object, added.get(), "the delegate must bind the manager's virtual add method");
        assertSame(services, object.constructorServices);
        assertEquals(List.of("factory", "get", "set:" + object.rawSlot(), "virtual-add"), events);
        assertNull(AbstractObjectInstance.CONSTRUCTION_CONTEXT.get());
        manager.releaseSlot(object);
    }

    private static void assertContext(ObjectServices services) {
        assertSame(services, AbstractObjectInstance.CONSTRUCTION_CONTEXT.get());
        assertNull(AbstractObjectInstance.PRE_ALLOCATED_SLOT.get(),
                "reserved-slot construction keeps construct(), rather than injecting a constructor slot");
    }

    private static final class ObservedObject extends AbstractObjectInstance {
        private final List<String> events;
        private final Runnable checkBoundary;
        private final ObjectServices constructorServices;

        ObservedObject(List<String> events, Runnable checkBoundary) {
            super(SPAWN, "ReservedSlotFixture");
            this.events = events;
            this.checkBoundary = checkBoundary;
            constructorServices = services();
        }

        @Override public int getSlotIndex() {
            checkBoundary.run();
            events.add("get");
            return super.getSlotIndex();
        }

        @Override public void setSlotIndex(int slot) {
            checkBoundary.run();
            events.add("set:" + slot);
            super.setSlotIndex(slot);
        }

        int rawSlot() { return super.getSlotIndex(); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private record CallbackRegistry(ObjectCallbackBoundary boundary) implements ObjectRegistry, Supplier<Object> {
        @Override public Object get() { return Map.entry(boundary, (Supplier<String>) () -> null); }
        @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "fixture"; }
    }
}
