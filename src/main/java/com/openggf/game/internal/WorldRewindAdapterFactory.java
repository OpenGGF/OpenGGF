package com.openggf.game.internal;

import com.openggf.game.session.WorldSession;
import java.lang.invoke.MethodHandles;

/**
 * Engine-only construction of one world's durable native DATA service.
 * Its separate rewind projection is reserved before return; no ROM or work submission.
 */
@FunctionalInterface
public interface WorldRewindAdapterFactory {
    NativePhysicalWorldData open(MethodHandles.Lookup worldConstruction, WorldSession world);
}
