package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.presentation.AudioPresentationCommandResolver.AppliedOutcome;
import com.openggf.audio.presentation.AudioPresentationCommandResolver.OutcomeReservation;

import java.util.function.Consumer;

/** Optional game-owned participant in one forward presentation transaction. */
public interface AudioPresentationForwardService {
    Snapshot snapshot();

    void restore(Snapshot snapshot);

    ForwardBoundary beginForwardBoundary();

    interface Snapshot { }

    interface ForwardBoundary {
        void service(Consumer<AudioCommand> commandSink);

        void reserveOutcome(OutcomeReservation reservation);

        void applyOutcome(AppliedOutcome outcome);

        void prepareCommit();

        CommittedReceipt commit();

        void publishDiagnostics(CommittedReceipt receipt);

        void rollback();
    }

    interface CommittedReceipt {
        boolean sealsOutcome(AppliedOutcome outcome);
    }
}
