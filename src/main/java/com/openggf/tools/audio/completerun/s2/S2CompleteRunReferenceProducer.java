package com.openggf.tools.audio.completerun.s2;

import static com.openggf.tools.audio.completerun.CompleteRunAudioFiles.file;
import static com.openggf.tools.audio.completerun.CompleteRunAudioFiles.directory;
import static com.openggf.tools.audio.completerun.CompleteRunAudioFiles.absolute;
import static com.openggf.tools.audio.completerun.CompleteRunAudioFiles.requireDigest;

import com.openggf.tools.audio.completerun.CompleteRunAudioProducer;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Fixed S2 raw-v2 producer; publication awaits reviewed capture and installed identities. */
public final class S2CompleteRunReferenceProducer implements CompleteRunAudioProducer {
    private static final Path LAUNCHER = Path.of("bizhawk-headless/run-complete-audio.sh");
    private static final Path SERVICE_MANIFEST =
            Path.of("bizhawk-headless/fixtures/gpgx-audio-service-manifests-v1.json");
    private static final Path CAPABILITY =
            Path.of("bizhawk-headless/fixtures/gpgx-audio-capability-v1.json");

    @Override public void capture(Request request) throws Exception {
        validate(request);
        var binding = S2CompleteRunAudioProfile.profile().producerBindings()
                .get(CompleteRunAudioTrace.ProducerKind.REFERENCE);
        if (binding instanceof CompleteRunAudioTrace.UnavailableProducerBinding unavailable) {
            throw new IllegalStateException("S2 reference producer is unavailable: " + unavailable.reason());
        }
        throw new IllegalStateException(
                "S2 reference publication awaits reviewed duplicate capture and installed identities");
    }

    void capturePipelineForTesting(Request request, CompleteRunAudioTrace.Metadata syntheticMetadata)
            throws Exception {
        validate(request);
        try (var raw = new com.openggf.tools.audio.completerun.TraceChaserAudioProcess()
                .capture(request, com.openggf.tools.audio.completerun.TraceChaserAudioProcess.Game.S2)) {
            new S2CompleteRunReferenceProjector().project(raw.raw(), request.rom(), request.output(),
                    syntheticMetadata, null);
        }
    }

    private static void validate(Request request) throws Exception {
        Objects.requireNonNull(request, "S2 reference request");
        if (request.producerKind() != CompleteRunAudioTrace.ProducerKind.REFERENCE) {
            throw new IllegalArgumentException("S2 reference producer requires REFERENCE kind");
        }
        if (!S2CompleteRunAudioProfile.ID.equals(request.profileId())) {
            throw new IllegalArgumentException("S2 reference profile is not fixed");
        }
        var fixture = S2CompleteRunAudioProfile.profile().fixture();
        requireDigest(file(request.rom(), "S2 ROM"), "SHA-1", fixture.romSha1(), "S2 ROM");
        requireDigest(file(request.bk2(), "S2 BK2"), "SHA-256", fixture.bk2Sha256(), "S2 BK2");
        requireDigest(file(request.runManifest(), "S2 run manifest"), "SHA-256",
                fixture.runManifestSha256(), "S2 run manifest");
        Path root = directory(request.referenceHome(), "TraceChaser root");
        file(root.resolve(LAUNCHER), "TraceChaser complete-audio launcher");
        file(root.resolve(SERVICE_MANIFEST), "TraceChaser service manifest");
        file(root.resolve(CAPABILITY), "TraceChaser S2 capability");
        Path output = absolute(request.output(), "S2 capture output");
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(output.toString());
        }
        if (!Files.isDirectory(output.getParent(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("S2 capture output parent does not exist");
        }
    }

}
