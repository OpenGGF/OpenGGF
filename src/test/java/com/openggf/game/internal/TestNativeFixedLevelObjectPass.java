package com.openggf.game.internal;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeFixedLevelObjectPass {
    @Test void engineOwnedFixedPassSurvivesModuleDecorationWithoutCampaignEvents() {
        var owner = new Owner();
        var module = module(owner);
        var decorated = new DelegatingGameModule(module, "authored") {
            @Override public com.openggf.game.LevelEventProvider getLevelEventProvider() { return null; }
        };
        assertSame(owner, NativeFixedLevelObjectPass.forModule(decorated));
        NativeFixedLevelObjectPass.forModule(decorated).beforeDynamicObjects();
        assertEquals(1, owner.calls);
    }

    @Test void modulesWithoutNativeFixedRamNeedNoPass() {
        assertNull(NativeFixedLevelObjectPass.forModule(mock(GameModule.class)));
    }

    @Test void callbackProjectionsCannotGrantNativeFixedObjectAuthority() {
        var proxy = (NativeFixedLevelObjectPass) Proxy.newProxyInstance(
                NativeFixedLevelObjectPass.class.getClassLoader(),
                new Class<?>[]{NativeFixedLevelObjectPass.class}, (object, method, args) -> null);
        assertNull(NativeFixedLevelObjectPass.forModule(module(proxy)));
    }

    @Test void concreteForeignLoaderCannotGrantNativeFixedObjectAuthority() throws Exception {
        byte[] bytes;
        try (var input = Owner.class.getResourceAsStream("/" + Owner.class.getName().replace('.', '/') + ".class")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        var loader = new ClassLoader(NativeFixedLevelObjectPass.class.getClassLoader()) {
            Class<?> define(byte[] input) { return defineClass(null, input, 0, input.length); }
        };
        var foreign = (NativeFixedLevelObjectPass) loader.define(bytes).getConstructor().newInstance();
        assertNull(NativeFixedLevelObjectPass.forModule(module(foreign)));
    }

    private static GameModule module(NativeFixedLevelObjectPass owner) {
        var module = mock(GameModule.class);
        when(module.getGameService(NativeFixedLevelObjectPass.class)).thenReturn(owner);
        return module;
    }

    public static final class Owner implements NativeFixedLevelObjectPass {
        public int calls;
        public Owner() { }
        @Override public void beforeDynamicObjects() { calls++; }
    }
}
