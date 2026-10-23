package com.openggf.game.rewind;

import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unexpected failures in an already constructed object are failures, not fixture exemptions. */
class TestRewindRoundTripHarnessFailures {
    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void unexpectedCaptureFailureCannotBecomeAnUnprobedSuccess() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> RewindRoundTripHarness.probeClass(CaptureFailure.class.getName()));
        assertTrue(failure.getMessage().contains("capture"), failure.toString());
        assertTrue(causeContains(failure, "capture regression"), failure.toString());
    }

    @Test
    void unexpectedRestoreFailureCannotBecomeAnUnprobedSuccess() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> RewindRoundTripHarness.probeClass(RestoreFailure.class.getName()));
        assertTrue(failure.getMessage().contains("restore"), failure.toString());
        assertTrue(causeContains(failure, "restore regression"), failure.toString());
    }

    @Test
    void invalidatedJavaTransientCacheDoesNotBecomeAnAuthoritativeScalarMismatch() {
        var fields = GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit(DerivedCacheFixture.class);
        assertTrue(fields.stream().anyMatch(field -> field.getName().equals("authoritative")));
        assertFalse(fields.stream().anyMatch(field -> field.getName().equals("derivedCache")));
        assertInstanceOf(RewindRoundTripHarness.RoundTripSweepResult.Passed.class,
                RewindRoundTripHarness.probeClass(DerivedCacheFixture.class.getName()));
    }

    @Test
    void missingAuthoritativeNonTransientScalarStillFailsWithTransientCacheExcluded() {
        var result = assertInstanceOf(RewindRoundTripHarness.RoundTripSweepResult.ScalarMismatch.class,
                RewindRoundTripHarness.probeClass(MissingAuthoritativeScalar.class.getName()));
        assertEquals(1, result.diffs().size());
        var diff = result.diffs().getFirst();
        assertEquals("MissingAuthoritativeScalar.authoritative", diff.fieldName());
        assertEquals("37", diff.before());
        assertEquals("0", diff.after());
        assertFalse(diff.isFinal());
    }

    private static boolean causeContains(Throwable failure, String text) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(text)) return true;
        }
        return false;
    }

    public static class CaptureFailure extends AbstractObjectInstance implements RewindRecreatable {
        public CaptureFailure(ObjectSpawn spawn) { super(spawn, "capture failure fixture"); }
        @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
            throw new IllegalStateException("capture regression");
        }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new CaptureFailure(context.spawn());
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    public static class RestoreFailure extends AbstractObjectInstance implements RewindRecreatable {
        public RestoreFailure(ObjectSpawn spawn) { super(spawn, "restore failure fixture"); }
        @Override public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
            throw new IllegalStateException("restore regression");
        }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new RestoreFailure(context.spawn());
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
    public static class DerivedCacheFixture extends AbstractObjectInstance implements RewindRecreatable {
        private int authoritative = 37;
        private transient int derivedCache = 96;
        public DerivedCacheFixture(ObjectSpawn spawn) { super(spawn, "transient cache fixture"); }
        @Override protected void afterGenericRewindStateRestored(RewindCaptureContext context) {
            derivedCache = -1;
        }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new DerivedCacheFixture(context.spawn());
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    /** Deliberately drops the authoritative sidecar, then restores a zeroed scalar shell. */
    public static class MissingAuthoritativeScalar extends AbstractObjectInstance implements RewindRecreatable {
        private int authoritative = 37;
        private transient int derivedCache = 96;
        public MissingAuthoritativeScalar(ObjectSpawn spawn) { super(spawn, "missing scalar fixture"); }
        @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
            return super.captureRewindState(context).withGenericState(null).withCompactGenericState(null);
        }
        @Override public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
            authoritative = 0;
            super.restoreRewindState(snapshot, context);
        }
        @Override protected void afterGenericRewindStateRestored(RewindCaptureContext context) {
            derivedCache = -1;
        }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new MissingAuthoritativeScalar(context.spawn());
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

}
