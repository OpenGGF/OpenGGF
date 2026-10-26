package com.openggf.audio.presentation;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.presentation.AudioPresentationCommandResolver.AppliedOutcome;
import com.openggf.audio.presentation.AudioPresentationCommandResolver.OutcomeReservation;
import com.openggf.audio.presentation.AudioPresentationCommandResolver.OutcomeSeal;

import java.util.function.Consumer;

/** Optional game-owned participant in one forward presentation transaction. */
public interface AudioPresentationForwardService {
    Snapshot snapshot();

    void restore(Snapshot snapshot);

    ForwardBoundary beginForwardBoundary();

    interface Snapshot { }

    interface ForwardBoundary {
        void service(Consumer<AudioCommand> commandSink);

        /** Native control selected by this bridge, independent of music resolution. */
        default AudioPresentationCommand.NativePause nativePauseRequest() { return null; }

        /** Acknowledges only the session's actual native pause/resume invocation. */
        default void onNativePauseConsumed() { }

        void reserveOutcome(OutcomeReservation reservation);

        void applyOutcome(AppliedOutcome outcome);

        /** Native SFX stop observed during this transaction's driver service. */
        default void onSfxTrackStop() { }

        void prepareCommit();

        CommittedReceipt commit();

        void publishDiagnostics(CommittedReceipt receipt);

        void rollback();
    }

    interface CommittedReceipt {
        OutcomeSeal sealFor(AppliedOutcome outcome);
    }
}
