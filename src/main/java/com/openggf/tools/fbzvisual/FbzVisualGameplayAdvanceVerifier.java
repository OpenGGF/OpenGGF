package com.openggf.tools.fbzvisual;

import java.util.Map;
import java.util.Objects;

/** Fail-closed proof that a visual recipe reached the required gameplay frame state. */
final class FbzVisualGameplayAdvanceVerifier {

    private FbzVisualGameplayAdvanceVerifier() {
    }

    /** Compatibility overload kept fail-closed while callers migrate to a hash-bound amendment. */
    static void verify(FbzVisualScenarioDriver.ScenarioPlan plan,
                       FbzVisualStateProbe.Snapshot pre,
                       FbzVisualStateProbe.Snapshot post) {
        throw new IllegalStateException("FBZ gameplay evidence requires an independently reviewed amendment");
    }

    static void verify(FbzVisualScenarioDriver.ScenarioPlan plan,
                       FbzVisualStateProbe.Snapshot pre,
                       FbzVisualStateProbe.Snapshot post,
                       FbzVisualEvidenceAmendment amendment) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(pre, "pre");
        Objects.requireNonNull(post, "post");
        verify(plan.checkpointId(), plan.framesToAdvance(), pre.values(), post.values(), amendment);
    }

    static void verify(String checkpoint, int recipeFrames,
                       Map<String, Object> pre, Map<String, Object> post,
                       FbzVisualEvidenceAmendment amendment) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(pre, "pre");
        Objects.requireNonNull(post, "post");
        Objects.requireNonNull(amendment, "amendment");
        int preFrame = requiredInt(pre, "level_frame_counter");
        int postFrame = requiredInt(post, "level_frame_counter");

        if (checkpoint.startsWith("fbz1-aniplc-") && postFrame <= preFrame) {
            throw new IllegalStateException("FBZ " + checkpoint
                    + " cadence did not advance production gameplay: pre=" + preFrame
                    + ", post=" + postFrame);
        } else if (!"fbz1-start-outdoor".equals(checkpoint) && postFrame <= preFrame) {
            throw new IllegalStateException("FBZ " + checkpoint
                    + " level_frame_counter did not advance in gameplay: pre="
                    + preFrame + ", post=" + postFrame);
        }

        if ("fbz1-start-outdoor".equals(checkpoint)) {
            amendment.verifyInitialization(pre);
            amendment.verifyAcceptedVisibleFrame(post);
        } else if (checkpoint.startsWith("fbz1-aniplc-")) {
            amendment.requireApprovedCadenceSeries(checkpoint);
        }
    }

    private static int requiredInt(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("FBZ gameplay advance proof is missing numeric " + key);
        }
        return number.intValue();
    }
}
