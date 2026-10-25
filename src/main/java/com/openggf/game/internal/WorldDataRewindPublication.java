package com.openggf.game.internal;

import com.openggf.game.rewind.RewindSnapshottable;

/** Engine composition projects a durable DATA service through its separately owned adapter. */
public interface WorldDataRewindPublication {
    RewindSnapshottable<?> dataRewindState();
}
