package com.openggf.game.timing;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static com.openggf.game.timing.HardwareServiceBoundary.POST_OBJECTS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHardwareTimingRewind {

    @Test
    void returnedSnapshotCannotExposeOrAdvanceTheLivePreparation() throws Exception {
        HardwareTimingService service = new HardwareTimingService();
        HardwareWorkHandle handle = service.submit(submission(2, 11));
        HardwareTimingSnapshot snapshot = service.capture();

        boolean exposedLivePreparation = false;
        Object jobSnapshot = snapshot.jobs().getFirst();
        for (Method accessor : jobSnapshot.getClass().getMethods()) {
            if (accessor.getParameterCount() != 0) {
                continue;
            }
            Object exposed;
            if (accessor.getReturnType() == HardwareWorkSubmission.class) {
                exposed = accessor.invoke(jobSnapshot);
                ((HardwareWorkSubmission) exposed).preparation().stepOneWorkUnit();
                exposedLivePreparation = true;
            } else if (accessor.getReturnType() == HardwareWorkPreparation.class) {
                exposed = accessor.invoke(jobSnapshot);
                ((HardwareWorkPreparation) exposed).stepOneWorkUnit();
                exposedLivePreparation = true;
            }
        }

        service.service(POST_OBJECTS);

        assertFalse(exposedLivePreparation,
                "a rewind snapshot must not publish the live submission or preparation");
        assertFalse(service.isReady(handle),
                "inspecting the returned snapshot must not advance live preparation");
        service.service(POST_OBJECTS);
        assertTrue(service.isReady(handle));
        byte[] exposedPayload =
                service.capture().jobs().getFirst().preparedPayload();
        exposedPayload[0] = 99;
        assertArrayEquals(new byte[] {11}, service.claim(handle),
                "mutating snapshot output must not mutate the service payload");
    }

    @Test
    void historicalSnapshotRecreatesItsOwnPreparationAfterIdenticalHandleResubmission() {
        HardwareTimingService service = new HardwareTimingService();
        HardwareTimingSnapshot beforeSubmission = service.capture();
        HardwareWorkHandle historicalHandle = service.submit(canonicalSubmission(
                new TestPreparation(2, new byte[] {31})));
        HardwareTimingSnapshot historicalBranch = service.capture();
        HardwareWorkPreparation inspectedCopy = historicalBranch.jobs().getFirst()
                .preparationSnapshot().recreatePreparation();
        inspectedCopy.stepOneWorkUnit();

        service.restore(beforeSubmission);
        HardwareWorkHandle replacementHandle = service.submit(canonicalSubmission(
                new IncompatiblePreparation(new byte[] {99})));
        assertEquals(historicalHandle, replacementHandle,
                "the adversarial replacement must collide on canonical handle identity");

        service.restore(historicalBranch);
        service.service(POST_OBJECTS);
        assertFalse(service.isReady(historicalHandle));
        service.service(POST_OBJECTS);

        assertArrayEquals(new byte[] {31}, service.claim(historicalHandle),
                "restoring the historical branch must recreate its original preparation");
    }

    @Test
    void restoreImmediatelyBeforeCompletionRepeatsServiceClaimAndOrdinalAllocation() {
        HardwareTimingService service = new HardwareTimingService();
        HardwareWorkHandle first = service.submit(submission(1, 12));
        HardwareTimingSnapshot beforeCompletion = service.capture();

        service.service(POST_OBJECTS);
        assertTrue(service.isReady(first));
        assertArrayEquals(new byte[] {12}, service.claim(first));
        HardwareWorkHandle future = service.submit(submission(1, 13));
        assertEquals(1, future.ordinal());

        service.restore(beforeCompletion);

        assertTrue(service.isPending(first));
        assertFalse(service.isReady(first));
        service.service(POST_OBJECTS);
        assertArrayEquals(new byte[] {12}, service.claim(first));
        assertEquals(future.ordinal(), service.submit(submission(1, 13)).ordinal());
    }

    @Test
    void restoreOnCompletionRepeatsExactlyOneClaimAndOrdinalAllocation() {
        HardwareTimingService service = new HardwareTimingService();
        HardwareWorkHandle first = service.submit(submission(1, 14));
        service.service(POST_OBJECTS);
        HardwareTimingSnapshot onCompletion = service.capture();

        assertArrayEquals(new byte[] {14}, service.claim(first));
        HardwareWorkHandle future = service.submit(submission(1, 15));

        service.restore(onCompletion);

        assertTrue(service.isReady(first));
        assertArrayEquals(new byte[] {14}, service.claim(first));
        assertThrows(IllegalStateException.class, () -> service.claim(first));
        assertEquals(future.ordinal(), service.submit(submission(1, 15)).ordinal());
    }

    @Test
    void restoreAfterCompletionPreservesClaimAndReplaysEarlierSnapshot() {
        HardwareTimingService service = new HardwareTimingService();
        HardwareWorkHandle first = service.submit(submission(1, 16));
        HardwareTimingSnapshot beforeCompletion = service.capture();
        service.service(POST_OBJECTS);
        service.claim(first);
        HardwareTimingSnapshot afterCompletion = service.capture();
        HardwareWorkHandle future = service.submit(submission(1, 17));

        service.restore(beforeCompletion);
        service.service(POST_OBJECTS);
        assertArrayEquals(new byte[] {16}, service.claim(first));

        service.restore(afterCompletion);
        assertFalse(service.isPending(first));
        assertFalse(service.isReady(first));
        assertThrows(IllegalStateException.class, () -> service.claim(first));
        assertEquals(future.ordinal(), service.submit(submission(1, 17)).ordinal());
    }

    @Test
    void recordedPreparationAndAdmissionStateAreRewindSafe() {
        HardwareTimingService service = new HardwareTimingService();
        RecordedCompletionAuthority authority = service.beginRecordedAdmission();
        HardwareWorkHandle handle = service.submit(submission(1, 18));
        HardwareTimingSnapshot beforePreparation = service.capture();

        service.service(POST_OBJECTS);
        HardwareTimingSnapshot preparedHeld = service.capture();
        authority.admitRecordedCompletion(
                POST_OBJECTS, handle.kind(), handle.ordinal(),
                handle.submissionFingerprint());
        HardwareTimingSnapshot admitted = service.capture();

        service.restore(beforePreparation);
        service.service(POST_OBJECTS);
        assertFalse(service.isReady(handle));

        service.restore(preparedHeld);
        assertFalse(service.isReady(handle));
        authority.admitRecordedCompletion(
                POST_OBJECTS, handle.kind(), handle.ordinal(),
                handle.submissionFingerprint());
        assertTrue(service.isReady(handle));

        service.restore(admitted);
        assertTrue(service.isReady(handle));
        assertArrayEquals(new byte[] {18}, service.claim(handle));
    }

    private static HardwareWorkSubmission submission(int workUnits, int payloadByte) {
        return new HardwareWorkSubmission(
                HardwareWorkKind.KOS_MODULE_QUEUE,
                0x2000 + payloadByte,
                0x80,
                0x5000,
                1,
                "KosM",
                1,
                false,
                new TestPreparation(workUnits, new byte[] {(byte) payloadByte}));
    }

    private static HardwareWorkSubmission canonicalSubmission(
            HardwareWorkPreparation preparation) {
        return new HardwareWorkSubmission(
                HardwareWorkKind.KOS_MODULE_QUEUE,
                0x2800,
                0x80,
                0x5000,
                1,
                "KosM",
                1,
                false,
                preparation);
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

    private static final class IncompatiblePreparation
            implements HardwareWorkPreparation {
        private final byte[] payload;

        private IncompatiblePreparation(byte[] payload) {
            this.payload = payload.clone();
        }

        @Override
        public boolean stepOneWorkUnit() {
            return true;
        }

        @Override
        public boolean isPrepared() {
            return true;
        }

        @Override
        public byte[] preparedPayload() {
            return payload.clone();
        }

        @Override
        public HardwareWorkPreparationSnapshot snapshot() {
            return new IncompatibleSnapshot(payload);
        }

        @Override
        public void restore(HardwareWorkPreparationSnapshot snapshot) {
            throw new AssertionError(
                    "historical snapshots must not restore into replacement preparations");
        }
    }

    private record IncompatibleSnapshot(byte[] payload)
            implements HardwareWorkPreparationSnapshot {
        private IncompatibleSnapshot {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        @Override
        public HardwareWorkPreparation recreatePreparation() {
            return new IncompatiblePreparation(payload);
        }
    }
}
