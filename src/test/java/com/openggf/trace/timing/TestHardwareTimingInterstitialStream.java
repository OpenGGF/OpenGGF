package com.openggf.trace.timing;

import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.timing.HardwareWorkPreparation;
import com.openggf.game.timing.HardwareWorkPreparationSnapshot;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.game.timing.HardwareSubmissionFingerprint;
import com.openggf.game.timing.RecordedCompletionAuthority;
import com.openggf.game.timing.RecordedOrdinalSpan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Map;

import static com.openggf.game.timing.HardwareServiceBoundary.POST_OBJECTS;
import static com.openggf.game.timing.HardwareServiceBoundary.PRE_MAIN_LOOP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run-level interstitial stream and the ordinal rebase it drives.
 *
 * <p>The stream exists because a recorder observes hardware completions across
 * the whole movie while a run fixture only represents the segments it compares.
 * Crossing one of those unrepresented spans must move production's identity
 * cursor and nothing else: no completion, no release, no work.
 */
class TestHardwareTimingInterstitialStream {

    @TempDir
    Path runDirectory;

    @Test
    void missingSidecarLeavesEveryHandoffUnchanged() throws IOException {
        HardwareTimingInterstitialSpans spans =
                HardwareTimingInterstitialStreamLoader.load(runDirectory);

        assertTrue(spans.isEmpty());
        assertEquals(Map.of(), spans.spansAfterSegment(0));
    }

    @Test
    void spansAreIndexedByBoundaryAndKind() throws IOException {
        write(
                record(1, "ss", POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 14),
                record(1, "ss", POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 15),
                record(1, "ss", PRE_MAIN_LOOP,
                        HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, 27),
                record(3, "ss_2", POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 36));

        HardwareTimingInterstitialSpans spans =
                HardwareTimingInterstitialStreamLoader.load(runDirectory);

        assertEquals(
                Map.of(
                        HardwareWorkKind.KOS_MODULE_QUEUE, new RecordedOrdinalSpan(14, 15, List.of(fingerprint('a'), fingerprint('a'))),
                        HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, new RecordedOrdinalSpan(27, 27, List.of(fingerprint('a')))),
                spans.spansAfterSegment(1));
        assertEquals(
                Map.of(HardwareWorkKind.KOS_MODULE_QUEUE, new RecordedOrdinalSpan(36, 36, List.of(fingerprint('a')))),
                spans.spansAfterSegment(3));
        assertEquals(Map.of(), spans.spansAfterSegment(2));
    }

    /**
     * The recorder emits the pre-run span too, but the run's one-time identity
     * base already accounts for it. Rebasing over it again would double-count
     * the same gap, so it must never reach a handoff.
     */
    @Test
    void preRunSpanIsNeverOfferedToAHandoff() throws IOException {
        write(
                record(-1, null, POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 0),
                record(-1, null, POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 1));

        HardwareTimingInterstitialSpans spans =
                HardwareTimingInterstitialStreamLoader.load(runDirectory);

        assertTrue(spans.isEmpty());
        assertEquals(Map.of(), spans.spansAfterSegment(-1));
    }

    @Test
    void rejectsAHoleInsideOneBoundarySpan() throws IOException {
        write(
                record(1, "ss", POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 14),
                record(1, "ss", POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 16));

        IOException error = assertThrows(IOException.class,
                () -> HardwareTimingInterstitialStreamLoader.load(runDirectory));

        assertTrue(error.getMessage().contains("leaves a hole"), error::getMessage);
    }

    @Test
    void rejectsAPerSegmentRecordShape() throws IOException {
        Files.writeString(
                runDirectory.resolve("hardware_timing_interstitial.jsonl"),
                "{\"event\":\"hardware_work_completed\",\"raw_frame\":12,"
                        + "\"boundary\":\"post_objects\",\"kind\":\"kos_module_queue\","
                        + "\"ordinal\":14,\"submission_fingerprint\":\"" + fingerprint('a')
                        + "\"}\n",
                StandardCharsets.UTF_8);

        IOException error = assertThrows(IOException.class,
                () -> HardwareTimingInterstitialStreamLoader.load(runDirectory));

        assertTrue(error.getMessage().contains("unknown or missing field"), error::getMessage);
    }

    @Test
    void rejectsARecordThatNamesNoOrigin() throws IOException {
        write(record(1, "ss", POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 14)
                .replace("\"origin\":\"interstitial\"", "\"origin\":\"segment\""));

        IOException error = assertThrows(IOException.class,
                () -> HardwareTimingInterstitialStreamLoader.load(runDirectory));

        assertTrue(error.getMessage().contains("invalid origin"), error::getMessage);
    }

    @Test
    void rejectsABoundaryIndexThatMovesBackward() throws IOException {
        write(
                record(3, "ss_2", POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 14),
                record(1, "ss", POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 15));

        IOException error = assertThrows(IOException.class,
                () -> HardwareTimingInterstitialStreamLoader.load(runDirectory));

        assertTrue(error.getMessage().contains("moved backward"), error::getMessage);
    }

    @Test
    void crossingASpanRenumbersOnlyTheNextProductionSubmission() {
        HardwareTimingService service = new HardwareTimingService();
        RecordedCompletionAuthority authority = service.beginRecordedAdmission();
        HardwareTimingReplayPort port = new HardwareTimingReplayPort(authority);
        port.install(new HardwareTimingSchedule(List.of()),
                Map.of(HardwareWorkKind.KOS_MODULE_QUEUE, 13L));

        port.handoffTo(
                new HardwareTimingSchedule(List.of(new HardwareCompletionEdge(
                        36, POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 24,
                        fingerprint('a')))),
                Map.of(HardwareWorkKind.KOS_MODULE_QUEUE, new RecordedOrdinalSpan(13, 23)));

        HardwareWorkHandle handle = service.submit(submission(1, 7));
        assertEquals(24, handle.ordinal(),
                "the next submission resumes on the recording's ordinal axis");
    }

    /**
     * The rebase is not permission to accept any skew: it only crosses the gap
     * the recording actually documented, verified against both the production
     * cursor behind it and the next segment's first edge ahead of it.
     */
    @Test
    void rejectsASpanThatDoesNotMeetTheNextSegment() {
        HardwareTimingService service = new HardwareTimingService();
        RecordedCompletionAuthority authority = service.beginRecordedAdmission();
        HardwareTimingReplayPort port = new HardwareTimingReplayPort(authority);
        port.install(new HardwareTimingSchedule(List.of()),
                Map.of(HardwareWorkKind.KOS_MODULE_QUEUE, 13L));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> port.handoffTo(
                        new HardwareTimingSchedule(List.of(new HardwareCompletionEdge(
                                36, POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 30,
                                fingerprint('a')))),
                        Map.of(HardwareWorkKind.KOS_MODULE_QUEUE,
                                new RecordedOrdinalSpan(13, 23))));

        assertTrue(error.getMessage().contains("does not meet the next segment"),
                error::getMessage);
    }

    @Test
    void rejectsASpanThatDoesNotBeginAtTheProductionCursor() {
        HardwareTimingService service = new HardwareTimingService();
        RecordedCompletionAuthority authority = service.beginRecordedAdmission();
        HardwareTimingReplayPort port = new HardwareTimingReplayPort(authority);
        port.install(new HardwareTimingSchedule(List.of()),
                Map.of(HardwareWorkKind.KOS_MODULE_QUEUE, 13L));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> port.handoffTo(
                        new HardwareTimingSchedule(List.of(new HardwareCompletionEdge(
                                36, POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, 24,
                                fingerprint('a')))),
                        Map.of(HardwareWorkKind.KOS_MODULE_QUEUE,
                                new RecordedOrdinalSpan(14, 23))));

        assertTrue(error.getMessage().contains("does not begin at the production cursor"),
                error::getMessage);
    }

    /**
     * The cursor allocates the next handle, so moving it while production still
     * holds an unclaimed one would strand that handle on the old axis with no
     * completion able to reach it.
     */
    @Test
    void refusesToCrossASpanWhileProductionHoldsPendingWork() {
        HardwareTimingService service = new HardwareTimingService();
        RecordedCompletionAuthority authority = service.beginRecordedAdmission();
        authority.configureAdmissionPolicies(
                new HardwareTimingSchedule(List.of()).admissionPolicies());
        HardwareWorkHandle pending = service.submit(submission(1, 3));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> authority.advanceOrdinalCursorAcrossRecordedSpan(Map.of(
                        HardwareWorkKind.KOS_MODULE_QUEUE, new RecordedOrdinalSpan(1, 9))));

        assertTrue(error.getMessage().contains("holds pending submissions"), error::getMessage);
        assertFalse(service.isReady(pending), "nothing was released");
    }

    @Test
    void completeClaimedSpanOnlyVerifiesReceiptsAndReplaysAfterRestore() {
        var fixture = claimedSpan(2);
        var before = fixture.service().capture();
        var portBefore = fixture.port().capture();
        var span = fixture.span();
        fixture.port().handoffTo(nextSchedule(15), Map.of(HardwareWorkKind.KOS_MODULE_QUEUE, span));
        assertEquals(before, fixture.service().capture(), "verification must not mutate production");
        assertEquals(Set.of("KOS_MODULE_QUEUE#13", "KOS_MODULE_QUEUE#14"),
                fixture.port().capture().consumedIdentities());

        fixture.service().restore(before);
        fixture.port().restore(portBefore);
        fixture.port().handoffTo(nextSchedule(15), Map.of(HardwareWorkKind.KOS_MODULE_QUEUE, span));
        assertEquals(before, fixture.service().capture());
        assertEquals(2, fixture.port().capture().consumedIdentities().size());
    }

    @Test
    void completedCursorWithoutFingerprintsIsNotProof() {
        var fixture = claimedSpan(2);
        var before = fixture.service().capture();
        assertThrows(IllegalStateException.class, () -> fixture.port().handoffTo(
                nextSchedule(15), Map.of(HardwareWorkKind.KOS_MODULE_QUEUE,
                        new RecordedOrdinalSpan(13, 14))));
        assertEquals(before, fixture.service().capture());
    }

    @Test
    void fingerprintsMustMatchTheirOwnOrdinalsRatherThanTheSameSet() {
        var fixture = claimedSpan(2);
        var before = fixture.service().capture();
        assertThrows(IllegalStateException.class, () -> fixture.port().handoffTo(
                nextSchedule(15), Map.of(HardwareWorkKind.KOS_MODULE_QUEUE,
                        new RecordedOrdinalSpan(13, 14, List.of(
                                fixture.handles().get(1).submissionFingerprint(),
                                fixture.handles().get(0).submissionFingerprint())))));
        assertEquals(before, fixture.service().capture());
        assertEquals(Set.of(), fixture.port().capture().consumedIdentities());
    }

    @Test
    void partialProductionAndExtraProductionBothFailWithoutCursorChanges() {
        var partial = claimedSpan(1);
        var partialBefore = partial.service().capture();
        assertThrows(IllegalStateException.class, () -> partial.port().handoffTo(
                nextSchedule(15), Map.of(HardwareWorkKind.KOS_MODULE_QUEUE,
                        new RecordedOrdinalSpan(13, 14, List.of(
                                partial.handles().getFirst().submissionFingerprint(), fingerprint('a'))))));
        assertEquals(partialBefore, partial.service().capture());

        var extra = claimedSpan(3);
        var extraBefore = extra.service().capture();
        assertThrows(IllegalStateException.class, () -> extra.port().handoffTo(
                nextSchedule(15), Map.of(HardwareWorkKind.KOS_MODULE_QUEUE,
                        new RecordedOrdinalSpan(13, 14, extra.handles().subList(0, 2).stream()
                                .map(HardwareWorkHandle::submissionFingerprint).toList()))));
        assertEquals(extraBefore, extra.service().capture());
    }

    @Test
    void readyButUnclaimedWorkCannotCertifyACompletedSpan() {
        var fixture = claimedSpan(1);
        var pending = fixture.service().submit(submission(1, 9));
        fixture.service().service(POST_OBJECTS);
        assertTrue(fixture.service().isReady(pending));
        var before = fixture.service().capture();
        assertThrows(IllegalStateException.class, () -> fixture.port().handoffTo(
                nextSchedule(15), Map.of(HardwareWorkKind.KOS_MODULE_QUEUE,
                        new RecordedOrdinalSpan(13, 14, List.of(
                                fixture.handles().getFirst().submissionFingerprint(),
                                pending.submissionFingerprint())))));
        assertEquals(before.nextOrdinals(), fixture.service().capture().nextOrdinals());
        assertTrue(fixture.service().isReady(pending), "failed proof cannot claim or release work");
        org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[] {9}, fixture.service().claim(pending));
    }

    @Test
    void failedKindCannotPartiallyAdvanceAnotherKindsUntouchedCursor() {
        var fixture = claimedSpan(1);
        var before = fixture.service().capture();
        Map<HardwareWorkKind, RecordedOrdinalSpan> spans = new LinkedHashMap<>();
        spans.put(HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, new RecordedOrdinalSpan(0, 1));
        spans.put(HardwareWorkKind.KOS_MODULE_QUEUE,
                new RecordedOrdinalSpan(13, 13, List.of(fingerprint('a'))));
        var next = new HardwareTimingSchedule(List.of(
                new HardwareCompletionEdge(36, POST_OBJECTS,
                        HardwareWorkKind.KOS_MODULE_QUEUE, 14, fingerprint('b')),
                new HardwareCompletionEdge(36, PRE_MAIN_LOOP,
                        HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, 2, fingerprint('c'))));
        assertThrows(IllegalStateException.class, () -> fixture.port().handoffTo(next, spans));
        assertEquals(before, fixture.service().capture());
        assertEquals(Set.of(), fixture.port().capture().consumedIdentities());
    }

    @Test
    void nextScheduleCannotReuseAnInterstitialIdentityAfterItsFirstEdge() {
        var fixture = claimedSpan(2);
        var before = fixture.service().capture();
        var next = new HardwareTimingSchedule(List.of(
                new HardwareCompletionEdge(36, POST_OBJECTS,
                        HardwareWorkKind.KOS_MODULE_QUEUE, 15, fingerprint('a')),
                new HardwareCompletionEdge(37, POST_OBJECTS,
                        HardwareWorkKind.KOS_MODULE_QUEUE, 14,
                        fixture.handles().get(1).submissionFingerprint())));
        var error = assertThrows(IllegalArgumentException.class, () -> fixture.port().handoffTo(
                next, Map.of(HardwareWorkKind.KOS_MODULE_QUEUE, fixture.span())));
        // Schedule validation rejects this backwards identity before span proof.
        assertTrue(error.getMessage().contains("noncontiguous hardware completion ordinals"),
                error::getMessage);
        assertEquals(before, fixture.service().capture());
    }

    @Test
    void anAlreadyConsumedRepresentedEdgeCannotBeReclassifiedAsInterstitial() {
        var service = new HardwareTimingService();
        var authority = service.beginRecordedAdmission();
        var port = new HardwareTimingReplayPort(authority);
        var work = submission(1, 7);
        var edge = new HardwareCompletionEdge(0, POST_OBJECTS,
                HardwareWorkKind.KOS_MODULE_QUEUE, 13, HardwareSubmissionFingerprint.compute(work));
        port.install(new HardwareTimingSchedule(List.of(edge)));
        var handle = service.submit(work);
        port.beginRawFrame(0);
        service.service(POST_OBJECTS);
        port.apply(POST_OBJECTS);
        service.claim(handle);
        var before = service.capture();
        var portBefore = port.capture();
        var error = assertThrows(IllegalStateException.class, () -> port.handoffTo(
                nextSchedule(14), Map.of(HardwareWorkKind.KOS_MODULE_QUEUE,
                        new RecordedOrdinalSpan(13, 13, List.of(handle.submissionFingerprint())))));
        assertTrue(error.getMessage().contains("already-consumed identity"), error::getMessage);
        assertEquals(before, service.capture());
        assertEquals(portBefore, port.capture());
    }

    @Test
    void fingerprintsCannotDescribeOnlyPartOfAnOrdinalSpan() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecordedOrdinalSpan(13, 14, List.of(fingerprint('a'))));
    }

    private static HardwareTimingSchedule nextSchedule(long ordinal) {
        return new HardwareTimingSchedule(List.of(new HardwareCompletionEdge(
                36, POST_OBJECTS, HardwareWorkKind.KOS_MODULE_QUEUE, ordinal, fingerprint('a'))));
    }

    private static ClaimedSpan claimedSpan(int count) {
        var service = new HardwareTimingService();
        var authority = service.beginRecordedAdmission();
        var port = new HardwareTimingReplayPort(authority);
        port.install(new HardwareTimingSchedule(List.of()), Map.of(HardwareWorkKind.KOS_MODULE_QUEUE, 13L));
        port.enterUnrepresentedGap();
        List<HardwareWorkHandle> handles = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            var handle = service.submit(submission(1, index + 1));
            service.service(POST_OBJECTS);
            assertTrue(service.isReady(handle));
            service.claim(handle);
            handles.add(handle);
        }
        return new ClaimedSpan(service, port, handles);
    }

    private record ClaimedSpan(HardwareTimingService service, HardwareTimingReplayPort port,
                               List<HardwareWorkHandle> handles) {
        RecordedOrdinalSpan span() {
            return new RecordedOrdinalSpan(handles.getFirst().ordinal(), handles.getLast().ordinal(),
                    handles.stream().map(HardwareWorkHandle::submissionFingerprint).toList());
        }
    }

    private void write(String... records) throws IOException {
        Files.writeString(
                runDirectory.resolve("hardware_timing_interstitial.jsonl"),
                String.join("\n", records) + "\n",
                StandardCharsets.UTF_8);
    }

    private static String record(
            int boundaryIndex,
            String segmentName,
            HardwareServiceBoundary boundary,
            HardwareWorkKind kind,
            long ordinal) {
        return "{\"event\":\"hardware_work_completed\",\"origin\":\"interstitial\","
                + "\"after_segment\":"
                + (segmentName == null ? "null" : "\"" + segmentName + "\"")
                + ",\"after_segment_index\":" + boundaryIndex
                + ",\"bk2_frame\":" + (1000 + ordinal)
                + ",\"boundary\":\"" + wireName(boundary) + "\""
                + ",\"kind\":\"" + wireName(kind) + "\""
                + ",\"ordinal\":" + ordinal
                + ",\"submission_fingerprint\":\"" + fingerprint('a') + "\"}";
    }

    private static String wireName(HardwareServiceBoundary boundary) {
        return boundary == POST_OBJECTS ? "post_objects" : "pre_main_loop";
    }

    private static String wireName(HardwareWorkKind kind) {
        return kind == HardwareWorkKind.KOS_MODULE_QUEUE
                ? "kos_module_queue" : "kos_decompression_queue";
    }

    private static String fingerprint(char digit) {
        return "sha256:" + String.valueOf(digit).repeat(64);
    }

    private static HardwareWorkSubmission submission(int workUnits, int payloadByte) {
        return new HardwareWorkSubmission(
                HardwareWorkKind.KOS_MODULE_QUEUE,
                0x3000 + payloadByte,
                0x100,
                0x5000,
                1,
                "KosM",
                1,
                false,
                new TestPreparation(workUnits, new byte[] {(byte) payloadByte}));
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
