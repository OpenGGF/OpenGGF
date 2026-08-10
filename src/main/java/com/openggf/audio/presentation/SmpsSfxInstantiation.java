package com.openggf.audio.presentation;

import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.AdmissionResult;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.RejectionReason;
import com.openggf.audio.driver.SmpsRequestAdmissionPolicy.SmpsAdmissionContext;
import com.openggf.audio.smps.SmpsSequencer;
import java.util.Objects;

public interface SmpsSfxInstantiation {
    record Admission(
            SmpsAdmissionContext context, AdmissionResult result) {
        public Admission {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(result, "result");
        }
    }

    SmpsSequencer instantiateCached(ResolvedSmpsSfxSource source,
                                    SmpsDriver currentOwner);

    /**
     * Creates an empty standalone composite. The registry applies current
     * channel controls before constructing and attaching the first sequencer.
     */
    SmpsCompositeVoice instantiateStandaloneCached(ResolvedSmpsSfxSource source);

    /** Evaluates one whole request before any driver or continuous-SFX mutation. */
    default Admission evaluateAdmission(
            ResolvedSmpsSfxSource source, SmpsDriver currentOwner) {
        SmpsAdmissionContext context = admissionContext(source, false);
        return new Admission(context,
                SmpsRequestAdmissionPolicy.PERMISSIVE.evaluate(context));
    }

    /** Builds an engine-owned rejection which bypasses the game policy. */
    default Admission rejectedAdmission(
            ResolvedSmpsSfxSource source, RejectionReason reason) {
        SmpsAdmissionContext context = admissionContext(source, false);
        return new Admission(context, new AdmissionResult(false, reason,
                context.priorityBefore(), context.priorityBefore(),
                context.resolvedSoundId()));
    }

    /** Reports a completed decision; the observer has no mutation authority. */
    default void observeAdmission(Admission admission) { }

    /** Reports a completed registry lifecycle mutation. */
    default void observeLifecycle(
            SmpsDriverServiceObserver.LifecycleKind kind) { }

    private static SmpsAdmissionContext admissionContext(
            ResolvedSmpsSfxSource source, boolean specialSfx) {
        Objects.requireNonNull(source, "source");
        int id = source.assetKey().sfxId();
        return new SmpsAdmissionContext(id, id, source.priority(),
                SmpsRequestAdmissionPolicy.NO_PRIORITY, specialSfx, false);
    }
}
