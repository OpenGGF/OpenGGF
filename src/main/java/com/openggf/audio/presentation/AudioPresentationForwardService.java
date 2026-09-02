package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioCommand;

import java.util.function.Consumer;

/** Optional game-owned participant in one forward presentation transaction. */
public interface AudioPresentationForwardService {
    Snapshot snapshot();

    void restore(Snapshot snapshot);

    ForwardBoundary beginForwardBoundary();

    interface Snapshot { }

    interface ForwardBoundary {
        void service(Consumer<AudioCommand> commandSink);

        void commit();

        void rollback();
    }
}
