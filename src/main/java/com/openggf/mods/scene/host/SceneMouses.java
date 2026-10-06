package com.openggf.mods.scene.host;

import com.openggf.mods.scene.SceneMouse;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Builds {@link SceneMouse} values for the host. The API type keeps its constructor private so
 * that only the engine makes mice (mods read them, or use {@code SceneMouse.none()}); this is the
 * engine's one route to it, resolved once. Engine-internal.
 */
final class SceneMouses {
    private static final MethodHandle CONSTRUCTOR;

    static {
        try {
            MethodType type = MethodType.methodType(void.class, int.class, int.class, boolean.class, boolean.class,
                    boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, boolean.class, int.class,
                    boolean.class);
            CONSTRUCTOR = MethodHandles.privateLookupIn(SceneMouse.class, MethodHandles.lookup())
                    .findConstructor(SceneMouse.class, type);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private SceneMouses() {
    }

    static SceneMouse create(int x, int y, boolean inside, boolean moved, boolean leftDown, boolean leftPressed,
            boolean leftReleased, boolean rightDown, boolean rightPressed, boolean rightReleased, int wheel,
            boolean lastInputWasMouse) {
        try {
            return (SceneMouse) CONSTRUCTOR.invokeExact(x, y, inside, moved, leftDown, leftPressed, leftReleased,
                    rightDown, rightPressed, rightReleased, wheel, lastInputWasMouse);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
}
