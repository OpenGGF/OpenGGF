package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioCommand;

import java.util.List;
import java.util.function.Consumer;

/** Optional game-owned participant in one forward presentation transaction. */
public interface AudioPresentationForwardService {
    Snapshot snapshot();

    void restore(Snapshot snapshot);

    ForwardBoundary beginForwardBoundary();

    interface Snapshot { }

    interface ForwardBoundary {
        void service(Consumer<AudioCommand> commandSink);

        /**
         * Applies request-owner state consequences only after the private
         * presentation batch has applied the matching commands.
         */
        void applyPreparedConsequences(List<AudioCommand> appliedCommands);

        /** Performs every fallible receipt and lifecycle preparation pre-seal. */
        void prepareCommit();

        /** Seals precomputed state only; observers are deliberately excluded. */
        void commit();

        /** Runs comparison-only observers after the enclosing transaction seals. */
        void publishDiagnostics();

        void rollback();
    }
}
