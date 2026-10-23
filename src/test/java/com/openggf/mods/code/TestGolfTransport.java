package com.openggf.mods.code;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class TestGolfTransport {
    @TempDir static Path temp;
    private static Path classes;

    @BeforeAll
    static void compileExternalProbes() throws Exception {
        classes = TestGolfProtocol.compileProbes(temp);
    }

    @Test void reconnectCandidateCountOverflowCannotReviveTerminalStateOrAppendControls() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "reconnectCandidateCountOverflowKeepsTerminalStateAndOrderingFinal");
    }
    @Test void inFlightSendFailureCannotPublishAfterCountOverflowEndsRoom() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "sendFailureCountOverflowCannotPublishAfterTerminal");
    }

    @Test void roomPreservesViewsAcrossOrderedWireBarriers() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "roomCoalescesOnlyAdjacentSameShotViewsAcrossWireDelivery");
    }
    @Test void downstreamRoomByteOverflowClosesAndDeliversTerminalOnce() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "roomByteOverflowClosesAndReleasesScenesWithTerminalDelivery");
    }
    @Test void roomEndReleasesScenesButPreservesOrderedSoundAndConcession() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "roomEndReleasesScenesAndPreservesOrderedSoundAndConcession");
    }

    @Test void inboundViewsHaveBoundedRetentionAndNewestSurvives() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "inboundViewsAreByteBoundedAndCoalesced");
    }
    @Test void defaultHostOnlyBindsLoopback() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "defaultListenerBindsLiteralLoopback");
    }
    @Test void inboundByteCapPreservesOrderedBarriersAndReleasesCharges() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "inboundBytesBoundViewsSeparatedByOrderedPackets");
    }
    @Test void lanInviteRejectsWrongAndReplayedProofBeforeRoomSeatAdmission() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "lanInviteRejectsWrongAndReplayedProofBeforePublishingPeer");
    }
    @Test void inviteTimeoutAndListenerCloseReleaseUnauthenticatedSockets() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "idleInviteExpiresAndListenerCloseOwnsPendingSockets");
    }
    @Test void lanRoomInvitationPrecedesSeatAndSurvivesReconnect() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "lanInvitationPrecedesSeatAndSurvivesReconnect");
    }

    @Test void localhostOrderingBoundsDeadlineAndCleanup() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "run");
    }
    @Test void incomingOverflowEventPublicationFollowsClosedState() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "incomingOverflowPublishesEventAfterClosed");
    }
    @Test void undrainedAcceptedWorkersRemainListenerOwned() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "undrainedAcceptedWorkersRemainOwned");
    }
    @Test void terminalFlushRetainsWorkerAccountingUntilDeadline() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "terminalFlushRetainsWorkers");
    }
    @Test void creatorLoaderCanCloseImmediatelyWhileWorkersRetire() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "creatorLoaderCanCloseImmediately");
    }
    @Test void bufferedPacketsDecodeAfterFreshCreatorLoaderCloses() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "TransportProbe", "bufferedPacketsRemainReadableAfterLoaderClose");
    }
    @Test void productionRoomHandshakeExactlyOnceReconnectAndHostExit() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "run");
    }
    @Test void rewindControlAndSpentBudgetsReplayAfterReconnect() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "rewindControlsAndBudgetsSurviveReconnect");
    }
    @Test void guestResumeRetainsHostPause() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "guestResumeRetainsHostPause");
    }
    @Test void hostResumeRetainsGuestPause() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "hostResumeRetainsGuestPause");
    }
    @Test void pausedPendingRequestIsReofferedOnce() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "pausedPendingRequestIsReofferedOnce");
    }
    @Test void concessionsPreserveOwnerAndScores() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "concessionsPreserveOwnerAndScores");
    }
    @Test void pauseIntentResynchronizesAfterReconnect() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "pauseIntentResynchronizesAfterReconnect");
    }
    @Test void handshakePauseIntentPrecedesReady() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "handshakePauseIntentPrecedesReady");
    }
    @Test void readyHandoffIsOwnerBoundTurnScopedAndHeldSafe() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "readyHandoffIsOwnerBoundAndTurnScoped");
    }
    @Test void incompatibleProtocolGenerationsAreNamedOnBothSides() throws Exception {
        TestGolfProtocol.runCompiledProbe(classes, "RoomProbe", "incompatibleVersionsAreNamed");
    }
}
