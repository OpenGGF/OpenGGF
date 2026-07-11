package com.openggf.mods.code;

/** Entrypoint implemented by compiled mods. A fresh instance is created per session. */
@FunctionalInterface
public interface GgfMod {
    void register(ModContext context);
}
