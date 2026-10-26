package com.openggf.game.internal;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeDynamicObjectPass {
    @Test void trustedOwnerSurvivesModuleDecoration() {
        var owner = new Owner();
        var decorated = new DelegatingGameModule(module(owner), "authored") { };
        var resolved = NativeDynamicObjectPass.forModule(decorated);
        assertSame(owner, resolved);
        resolved.spawnBeforeReservedSlots();
        resolved.runDynamicSlotsAfterReservedSlots();
        assertEquals(2, owner.calls);
    }

    @Test void absentOwnerRetainsTheOrdinaryManagerPath() {
        assertNull(NativeDynamicObjectPass.forModule(mock(GameModule.class)));
    }

    @Test void callbackProjectionCannotAcquireNativeRamExecution() {
        var proxy = (NativeDynamicObjectPass) Proxy.newProxyInstance(NativeDynamicObjectPass.class.getClassLoader(),
                new Class<?>[]{NativeDynamicObjectPass.class}, (object, method, args) -> null);
        assertNull(NativeDynamicObjectPass.forModule(module(proxy)));
    }

    @Test void foreignConcreteLoaderCannotAcquireNativeRamExecution() throws Exception {
        byte[] bytes;
        try (var stream = Owner.class.getResourceAsStream("/" + Owner.class.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        var loader = new ClassLoader(NativeDynamicObjectPass.class.getClassLoader()) {
            Class<?> define(byte[] input) { return defineClass(null, input, 0, input.length); }
        };
        var owner = (NativeDynamicObjectPass) loader.define(bytes).getConstructor().newInstance();
        assertNull(NativeDynamicObjectPass.forModule(module(owner)));
    }

    private static GameModule module(NativeDynamicObjectPass owner) {
        var module = mock(GameModule.class);
        when(module.getGameService(NativeDynamicObjectPass.class)).thenReturn(owner);
        return module;
    }

    public static final class Owner implements NativeDynamicObjectPass {
        public int calls;
        public Owner() { }
        @Override public void spawnBeforeReservedSlots() { calls++; }
        @Override public void runDynamicSlotsAfterReservedSlots() { calls++; }
    }
}
