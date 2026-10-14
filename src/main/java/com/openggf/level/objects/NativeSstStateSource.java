package com.openggf.level.objects;

/** Internal live-native field projection; unknown consumed residue has no zero fallback. */
public interface NativeSstStateSource {
    NativeSstState nativeSstState();
}
