package com.openggf.mods.objects;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestAuxiliaryObjectCollection {
    private record Host(ObjectManager manager, ObjectServices services, List<ObjectInstance> live) { }

    private Host host() {
        var manager = mock(ObjectManager.class);
        var services = mock(ObjectServices.class);
        List<ObjectInstance> live = new ArrayList<>();
        when(services.objectManager()).thenReturn(manager);
        when(manager.getActiveObjects()).thenReturn(live);
        doAnswer(call -> { live.add(call.getArgument(0)); return null; })
                .when(manager).addAuxiliaryDynamicObject(any());
        doAnswer(call -> { live.remove(call.getArgument(0)); return null; })
                .when(manager).removeDynamicObject(any());
        return new Host(manager, services, live);
    }

    @Test void checkpointRemovalRecreatesWithoutAnExplicitRestoreNotification() {
        Host host = host();
        var collection = new AuxiliaryObjectCollection<Object>();
        var model = new Object();
        List<ObjectInstance> created = new ArrayList<>();
        BiFunction<ObjectServices, Object, ObjectInstance> factory = (services, value) -> {
            assertSame(host.services(), services);
            assertSame(model, value);
            var object = mock(ObjectInstance.class); created.add(object); return object;
        };
        collection.synchronize(host.services(), List.of(model), factory);
        collection.synchronize(host.services(), List.of(model), factory);
        assertEquals(1, created.size());
        // Object checkpoint restore drops non-rewindable presentation objects.
        host.live().clear();
        collection.synchronize(host.services(), List.of(model), factory);
        assertEquals(2, created.size());
        assertEquals(List.of(created.get(1)), host.live());
        collection.close();
        assertTrue(host.live().isEmpty());
    }

    @Test void levelReplacementAndRemovalRetireOnlyOwnedObjectsThroughTheirOriginalManager() {
        Host before = host(), after = host();
        var collection = new AuxiliaryObjectCollection<Object>();
        Object first = new Object(), second = new Object();
        ObjectInstance unrelated = mock(ObjectInstance.class);
        before.live().add(unrelated);
        BiFunction<ObjectServices, Object, ObjectInstance> factory = (services, value) -> mock(ObjectInstance.class);
        collection.synchronize(before.services(), List.of(first, second), factory);
        ObjectInstance oldFirst = before.live().get(1), oldSecond = before.live().get(2);
        collection.synchronize(after.services(), List.of(second), factory);
        verify(before.manager()).removeDynamicObject(oldFirst);
        verify(before.manager()).removeDynamicObject(oldSecond);
        assertEquals(List.of(unrelated), before.live());
        assertEquals(1, after.live().size());
        collection.synchronize(after.services(), List.of(), factory);
        assertTrue(after.live().isEmpty());
        collection.close();
        assertEquals(List.of(unrelated), before.live());
    }

    @Test void destroyedObjectsAreReplacedAndEqualModelsRetainSeparateIdentity() {
        Host host = host();
        var collection = new AuxiliaryObjectCollection<String>();
        String first = new String("same"), second = new String("same");
        BiFunction<ObjectServices, String, ObjectInstance> factory = (services, value) -> mock(ObjectInstance.class);
        collection.synchronize(host.services(), List.of(first, second), factory);
        assertEquals(2, host.live().size());
        ObjectInstance destroyed = host.live().getFirst();
        when(destroyed.isDestroyed()).thenReturn(true);
        collection.synchronize(host.services(), List.of(first, second), factory);
        assertEquals(2, host.live().size());
        assertFalse(host.live().contains(destroyed));
        assertThrows(IllegalArgumentException.class,
                () -> collection.synchronize(host.services(), List.of(first, first), factory));
        collection.close();
    }
}
