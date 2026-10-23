package com.openggf.tools.audio.completerun.s1;

import com.openggf.tools.audio.completerun.CompleteRunAudioFiles;
import com.openggf.tools.audio.completerun.CompleteRunAudioCaptureStore;
import com.openggf.tools.audio.completerun.CompleteRunAudioProducer;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import java.nio.file.Path;
import java.util.Objects;

/** Fixed S1 raw.v1 importer; native launch/publication awaits reviewed installed identities. */
public final class S1CompleteRunReferenceProducer implements CompleteRunAudioProducer {
    @Override public void capture(Request request) throws Exception {
        fixedRequest(request);
        var profile = S1CompleteRunAudioProfile.profile();
        if (profile.producerBindings().get(CompleteRunAudioTrace.ProducerKind.REFERENCE)
                instanceof CompleteRunAudioTrace.UnavailableProducerBinding unavailable) {
            throw new IllegalStateException("S1 reference producer is unavailable: " + unavailable.reason());
        }
        // The source owns S1 through run.sh --mode trace --trace-profile
        // complete_run_audio_reference; run-complete-audio.sh is S2/S3K only.
        // Its fixed Java process adapter is not installed, and raw.v1 cannot
        // authenticate the canonical buffered-native terminal frontier.
        throw new IllegalStateException("S1 reference publication requires reviewed duplicate emerald-run "
                + "captures, installed identities, a fixed Java S1 launch adapter, and complete native frontier evidence");
    }

    /**
     * Reusable observation-only importer for source-owned, already captured raw output.
     * Kept behind the public unavailable gate until runtime/install evidence is reviewed.
     */
    void captureStaged(Request request, Path raw, CompleteRunAudioCaptureStore.Writer writer) throws Exception {
        Objects.requireNonNull(writer, "S1 staged writer");
        try {
            fixedRequest(request);
            var fixture = S1CompleteRunAudioProfile.profile().fixture();
            CompleteRunAudioFiles.requireDigest(CompleteRunAudioFiles.file(request.rom(), "S1 ROM"),
                    "SHA-1", fixture.romSha1(), "S1 ROM");
            CompleteRunAudioFiles.requireDigest(CompleteRunAudioFiles.file(request.bk2(), "S1 emerald BK2"),
                    "SHA-256", fixture.bk2Sha256(), "S1 emerald BK2");
            CompleteRunAudioFiles.requireDigest(CompleteRunAudioFiles.file(request.runManifest(), "S1 manifest"),
                    "SHA-256", fixture.runManifestSha256(), "S1 manifest");
            new S1CompleteRunReferenceProjector().project(raw, writer);
        } catch (Exception | Error failure) {
            try { writer.abort(); }
            catch (Exception | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Exercises the same raw conversion with a bounded prefix, without installing runtime pins. */
    S1CompleteRunReferenceProjector.Projection capturePipelineForTesting(Path raw) throws Exception {
        return new S1CompleteRunReferenceProjector().projectPrefixForTesting(raw);
    }

    private static void fixedRequest(Request request) {
        Objects.requireNonNull(request, "S1 reference request");
        if (request.producerKind() != CompleteRunAudioTrace.ProducerKind.REFERENCE)
            throw new IllegalArgumentException("S1 reference producer requires REFERENCE kind");
        if (!S1CompleteRunAudioProfile.ID.equals(request.profileId()))
            throw new IllegalArgumentException("S1 reference profile does not match its fixed binding");
    }
}
