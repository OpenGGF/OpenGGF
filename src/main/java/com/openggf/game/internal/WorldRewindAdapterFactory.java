package com.openggf.game.internal;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSession;
import java.lang.invoke.MethodHandles;

/** Engine-only construction of one world's native DATA owner; no ROM or work submission. */
@FunctionalInterface
public interface WorldRewindAdapterFactory {
    RewindSnapshottable<?> open(MethodHandles.Lookup worldConstruction, WorldSession world);
}
