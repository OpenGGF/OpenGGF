package com.openggf.game.internal;

import com.openggf.game.GameModule;
import java.lang.reflect.Proxy;

/** Engine-owned fixed object RAM that runs independently of campaign zone events. */
@FunctionalInterface
public interface NativeFixedLevelObjectPass {
    void beforeDynamicObjects();

    static NativeFixedLevelObjectPass forModule(GameModule module) {
        NativeFixedLevelObjectPass owner = module.getGameService(NativeFixedLevelObjectPass.class);
        return owner != null && !Proxy.isProxyClass(owner.getClass())
                && owner.getClass().getClassLoader() == NativeFixedLevelObjectPass.class.getClassLoader()
                ? owner : null;
    }
}
