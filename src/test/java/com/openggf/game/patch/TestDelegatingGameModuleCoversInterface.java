package com.openggf.game.patch;

import com.openggf.game.GameModule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps patch decorators honest as {@link GameModule} evolves. Default methods
 * are deliberately included: inheriting one would bypass the wrapped module.
 */
class TestDelegatingGameModuleCoversInterface {

    @Test
    void delegatingGameModuleDeclaresEveryGameModuleMethod() {
        List<String> missing = new ArrayList<>();
        for (Method method : GameModule.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            try {
                DelegatingGameModule.class.getDeclaredMethod(
                        method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                missing.add(method.getName() + Arrays.toString(method.getParameterTypes()));
            }
        }
        assertTrue(missing.isEmpty(),
                "DelegatingGameModule must forward these GameModule methods to the base module:\n"
                        + String.join("\n", missing));
    }
}
