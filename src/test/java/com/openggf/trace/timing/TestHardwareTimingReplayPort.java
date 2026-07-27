package com.openggf.trace.timing;

import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareTimingSnapshot;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.HardwareWorkPreparation;
import com.openggf.game.timing.HardwareWorkPreparationSnapshot;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.game.timing.RecordedCompletionAuthority;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.game.timing.HardwareServiceBoundary.POST_OBJECTS;
import static com.openggf.game.timing.HardwareServiceBoundary.PRE_MAIN_LOOP;
import static com.openggf.game.timing.HardwareServiceBoundary.VINT_SERVICE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHardwareTimingReplayPort {

    @Test
    void matchingPreparedHeadReleasesAtBoundary() {
        ReplayHarness harness = harness(false, 1, 11);
        harness.service.service(POST_OBJECTS);
        HardwareTimingReplayPort port = port(
                harness.authority, edge(17, POST_OBJECTS, harness.handle));

        port.beginRawFrame(17);
        port.apply(POST_OBJECTS);

        assertTrue(harness.service.isReady(harness.handle));
    }

    @Test
    void earlyPreparedJobIsHeldUntilEdge() {
        ReplayHarness harness = harness(false, 1, 12);
        HardwareTimingReplayPort port = port(
                harness.authority, edge(23, POST_OBJECTS, harness.handle));

        harness.service.service(POST_OBJECTS);
        port.beginRawFrame(22);

        assertTrue(harness.service.isPending(harness.handle));
        assertFalse(harness.service.isReady(harness.handle));
    }

    @Test
    void unrepresentedGapDeactivatesThePreviousRawFrameLatch() {
        ReplayHarness harness = harness(false, 0, 12);
        HardwareTimingReplayPort port = port(
                harness.authority, edge(23, POST_OBJECTS, harness.handle));

        port.beginRawFrame(23);
        port.enterUnrepresentedGap();
        harness.service.service(POST_OBJECTS);
        port.apply(POST_OBJECTS);

        assertFalse(harness.service.isReady(harness.handle),
                "production may keep servicing during a BK2 gap, but the stale row "
                        + "must not release its recorded edge");
        assertEquals(null, port.capture().rawFrameLatch());
        assertThrows(IllegalStateException.class, port::verifySegmentEdges);
    }

    @Test
    void edgeCannotPrepareAJob() {
        ReplayHarness harness = harness(false, 1, 13);
        HardwareTimingReplayPort port = port(
                harness.authority, edge(4, PRE_MAIN_LOOP, harness.handle));
        harness.service.service(PRE_MAIN_LOOP);
        port.beginRawFrame(4);

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> port.apply(PRE_MAIN_LOOP));

        assertTrue(error.getMessage().contains("not prepared"), error::getMessage);
        assertFalse(harness.service.isReady(harness.handle));
    }

    @Test
    void wrongKindOrdinalFingerprintOrBoundaryFails() {
        ReplayHarness ordinalHarness = harness(false, 1, 14);
        ordinalHarness.service.service(POST_OBJECTS);
        HardwareCompletionEdge wrongOrdinal = new HardwareCompletionEdge(
                8,
                POST_OBJECTS,
                ordinalHarness.handle.kind(),
                ordinalHarness.handle.ordinal() + 1,
                ordinalHarness.handle.submissionFingerprint());
        IllegalStateException ordinalError =
                applyFailure(ordinalHarness, wrongOrdinal, POST_OBJECTS);
        assertExpectedAndEngineIdentities(
                ordinalError, wrongOrdinal, ordinalHarness.handle);

        ReplayHarness fingerprintHarness = harness(false, 1, 15);
        fingerprintHarness.service.service(POST_OBJECTS);
        HardwareCompletionEdge wrongFingerprint = new HardwareCompletionEdge(
                9,
                POST_OBJECTS,
                fingerprintHarness.handle.kind(),
                fingerprintHarness.handle.ordinal(),
                fingerprint('f'));
        IllegalStateException fingerprintError =
                applyFailure(fingerprintHarness, wrongFingerprint, POST_OBJECTS);
        assertExpectedAndEngineIdentities(
                fingerprintError, wrongFingerprint, fingerprintHarness.handle);

        ReplayHarness boundaryHarness = harness(false, 0, 16);
        boundaryHarness.service.service(VINT_SERVICE);
        HardwareCompletionEdge wrongBoundary =
                edge(10, POST_OBJECTS, boundaryHarness.handle);
        IllegalStateException boundaryError =
                applyFailure(boundaryHarness, wrongBoundary, POST_OBJECTS);
        assertTrue(boundaryError.getMessage().contains(
                "expected POST_OBJECTS, production serviced VINT_SERVICE"),
                boundaryError::getMessage);
    }

    @Test
    void duplicateAndReorderedEdgesFail() {
        ReplayHarness harness = harness(false, 0, 17);
        HardwareCompletionEdge first = edge(2, PRE_MAIN_LOOP, harness.handle);
        HardwareCompletionEdge duplicate = edge(3, POST_OBJECTS, harness.handle);

        IllegalArgumentException duplicateError = assertThrows(
                IllegalArgumentException.class,
                () -> new HardwareTimingReplayPort(harness.authority)
                        .install(new HardwareTimingSchedule(List.of(first, duplicate))));
        assertTrue(duplicateError.getMessage().contains("duplicate"),
                duplicateError::getMessage);
        assertTrue(duplicateError.getMessage().contains(describe(harness.handle)),
                duplicateError::getMessage);

        HardwareCompletionEdge later = new HardwareCompletionEdge(
                5, VINT_SERVICE, harness.handle.kind(), 1, fingerprint('a'));
        HardwareCompletionEdge earlier = new HardwareCompletionEdge(
                4, POST_OBJECTS, harness.handle.kind(), 2, fingerprint('b'));
        IllegalArgumentException reorderedError = assertThrows(
                IllegalArgumentException.class,
                () -> new HardwareTimingReplayPort(harness.authority)
                        .install(new HardwareTimingSchedule(List.of(later, earlier))));
        assertTrue(reorderedError.getMessage().contains("canonical order"),
                reorderedError::getMessage);
        assertTrue(reorderedError.getMessage().contains("raw_frame=4"),
                reorderedError::getMessage);
    }

    @Test
    void nonExportablePendingSubmissionFailsAtSegmentEnd() {
        ReplayHarness harness = harness(false, 1, 18);
        harness.service.service(POST_OBJECTS);
        HardwareTimingReplayPort port =
                port(harness.authority, new HardwareTimingSchedule(List.of()));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> port.handoffTo(HardwareTimingSchedule.empty()));

        assertTrue(error.getMessage().contains("non-exportable"),
                error::getMessage);
        assertTrue(error.getMessage().contains(describe(harness.handle)),
                error::getMessage);
    }

    @Test
    void exportablePendingSubmissionRequiresMatchingNextSegmentEdge() {
        ReplayHarness harness = harness(true, 1, 19);
        harness.service.service(POST_OBJECTS);
        HardwareTimingReplayPort port =
                port(harness.authority, HardwareTimingSchedule.empty());
        HardwareCompletionEdge mismatch = new HardwareCompletionEdge(
                0,
                POST_OBJECTS,
                harness.handle.kind(),
                harness.handle.ordinal(),
                fingerprint('c'));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> port.handoffTo(new HardwareTimingSchedule(List.of(mismatch))));

        assertTrue(error.getMessage().contains("matching next-segment edge"),
                error::getMessage);
        assertTrue(error.getMessage().contains(describe(harness.handle)),
                error::getMessage);
        assertTrue(error.getMessage().contains(mismatch.submissionFingerprint()),
                error::getMessage);
    }

    @Test
    void validExportPreservesOrdinalAndPreparationAcrossHandoff() {
        ReplayHarness harness = harness(true, 1, 20);
        harness.service.service(POST_OBJECTS);
        HardwareTimingReplayPort port =
                port(harness.authority, HardwareTimingSchedule.empty());
        HardwareTimingSchedule next = new HardwareTimingSchedule(
                List.of(edge(0, POST_OBJECTS, harness.handle)));

        port.handoffTo(next);
        port.beginRawFrame(0);
        harness.service.service(POST_OBJECTS);
        port.apply(POST_OBJECTS);

        assertTrue(harness.service.isReady(harness.handle));
        assertArrayEquals(new byte[] {20}, harness.service.claim(harness.handle));
        HardwareWorkHandle nextHandle =
                harness.service.submit(submission(false, 1, 21));
        assertEquals(harness.handle.ordinal() + 1, nextHandle.ordinal());
    }

    @Test
    void unconsumedEdgeFailsAtSegmentEnd() {
        ReplayHarness harness = harness(false, 0, 22);
        HardwareTimingReplayPort port = port(
                harness.authority, edge(6, POST_OBJECTS, harness.handle));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> port.handoffTo(HardwareTimingSchedule.empty()));

        assertTrue(error.getMessage().contains("unconsumed hardware completion edge"),
                error::getMessage);
        assertTrue(error.getMessage().contains("raw_frame=6"),
                error::getMessage);
        assertTrue(error.getMessage().contains(describe(harness.handle)),
                error::getMessage);
    }

    @Test
    void rewindRestoresEdgeCursorAndConsumedLedger() {
        ReplayHarness harness = harness(false, 1, 23);
        HardwareTimingReplayPort port = port(
                harness.authority, edge(31, POST_OBJECTS, harness.handle));
        harness.service.service(POST_OBJECTS);
        port.beginRawFrame(31);
        HardwareTimingSnapshot serviceBeforeEdge = harness.service.capture();
        HardwareTimingReplaySnapshot portBeforeEdge = port.capture();

        port.apply(POST_OBJECTS);
        assertTrue(harness.service.isReady(harness.handle));

        harness.service.restore(serviceBeforeEdge);
        port.restore(portBeforeEdge);
        port.apply(POST_OBJECTS);

        assertTrue(harness.service.isReady(harness.handle));
        assertArrayEquals(new byte[] {23}, harness.service.claim(harness.handle));
        assertEquals(1, port.capture().consumedIdentities().size());
    }

    private static IllegalStateException applyFailure(
            ReplayHarness harness,
            HardwareCompletionEdge edge,
            HardwareServiceBoundary boundary) {
        HardwareTimingReplayPort port = port(harness.authority, edge);
        port.beginRawFrame(edge.rawFrame());
        return assertThrows(IllegalStateException.class, () -> port.apply(boundary));
    }

    private static void assertExpectedAndEngineIdentities(
            IllegalStateException error,
            HardwareCompletionEdge expected,
            HardwareWorkHandle engine) {
        assertTrue(error.getMessage().contains(
                expected.kind() + "#" + expected.ordinal()
                        + " " + expected.submissionFingerprint()),
                error::getMessage);
        assertTrue(error.getMessage().contains(describe(engine)),
                error::getMessage);
    }

    private static HardwareTimingReplayPort port(
            RecordedCompletionAuthority authority,
            HardwareCompletionEdge... edges) {
        return port(authority, new HardwareTimingSchedule(List.of(edges)));
    }

    private static HardwareTimingReplayPort port(
            RecordedCompletionAuthority authority,
            HardwareTimingSchedule schedule) {
        HardwareTimingReplayPort port = new HardwareTimingReplayPort(authority);
        port.install(schedule);
        return port;
    }

    private static ReplayHarness harness(
            boolean exportable, int workUnits, int payloadByte) {
        HardwareTimingService service = new HardwareTimingService();
        RecordedCompletionAuthority authority = service.beginRecordedAdmission();
        HardwareWorkHandle handle =
                service.submit(submission(exportable, workUnits, payloadByte));
        return new ReplayHarness(service, authority, handle);
    }

    private static HardwareCompletionEdge edge(
            int rawFrame,
            HardwareServiceBoundary boundary,
            HardwareWorkHandle handle) {
        return new HardwareCompletionEdge(
                rawFrame,
                boundary,
                handle.kind(),
                handle.ordinal(),
                handle.submissionFingerprint());
    }

    private static HardwareWorkSubmission submission(
            boolean exportable, int workUnits, int payloadByte) {
        return new HardwareWorkSubmission(
                HardwareWorkKind.KOS_MODULE_QUEUE,
                0x3000 + payloadByte,
                0x100,
                0x5000,
                1,
                "KosM",
                1,
                exportable,
                new TestPreparation(workUnits, new byte[] {(byte) payloadByte}));
    }

    private static String describe(HardwareWorkHandle handle) {
        return handle.kind() + "#" + handle.ordinal()
                + " " + handle.submissionFingerprint();
    }

    private static String fingerprint(char digit) {
        return "sha256:" + String.valueOf(digit).repeat(64);
    }

    private record ReplayHarness(
            HardwareTimingService service,
            RecordedCompletionAuthority authority,
            HardwareWorkHandle handle) {
    }

    private record PreparationSnapshot(int remainingUnits, byte[] payload)
            implements HardwareWorkPreparationSnapshot {
        private PreparationSnapshot {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public HardwareWorkPreparation recreatePreparation() {
            return new TestPreparation(remainingUnits, payload);
        }
    }

    private static final class TestPreparation implements HardwareWorkPreparation {
        private int remainingUnits;
        private final byte[] payload;

        private TestPreparation(int remainingUnits, byte[] payload) {
            this.remainingUnits = remainingUnits;
            this.payload = payload.clone();
        }

        @Override
        public boolean stepOneWorkUnit() {
            if (remainingUnits == 0) {
                return false;
            }
            remainingUnits--;
            return true;
        }

        @Override
        public boolean isPrepared() {
            return remainingUnits == 0;
        }

        @Override
        public byte[] preparedPayload() {
            if (!isPrepared()) {
                throw new IllegalStateException("payload requested before preparation");
            }
            return payload.clone();
        }

        @Override
        public HardwareWorkPreparationSnapshot snapshot() {
            return new PreparationSnapshot(remainingUnits, payload);
        }

        @Override
        public void restore(HardwareWorkPreparationSnapshot snapshot) {
            remainingUnits = ((PreparationSnapshot) snapshot).remainingUnits();
        }
    }
}
