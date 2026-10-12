package com.openggf.game.internal;

import java.lang.reflect.Proxy;

/** Native scheduling accepts concrete engine-loaded owners, never creator callback projections. */
public final class NativeStagePacingOwners {
    private NativeStagePacingOwners() { }

    public static NativeSpecialStagePacing special(Object provider) {
        return trusted(provider) && provider instanceof NativeSpecialStagePacing owner ? owner : null;
    }

    public static NativeBonusStagePacing bonus(Object provider) {
        return trusted(provider) && provider instanceof NativeBonusStagePacing owner ? owner : null;
    }

    private static boolean trusted(Object provider) {
        return provider != null && !Proxy.isProxyClass(provider.getClass())
                && provider.getClass().getClassLoader() == NativeStagePacingOwners.class.getClassLoader();
    }
}
